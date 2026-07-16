#Requires -Version 5.1
<#
.SYNOPSIS
  Sample machine-sentinel ledger/ops for DEV soak closeout.
#>
param(
  [string]$Api = 'http://127.0.0.1:3350/api',
  [string]$Css = 'http://127.0.0.1:9000',
  [string]$ClientId = 'machine-sentinel'
)

$ErrorActionPreference = 'Stop'

try {
  $health = Invoke-RestMethod "$Api/health" -TimeoutSec 5
  Write-Host "health=$($health.status) mode=$($health.mode)"
} catch {
  Write-Host "SOAK_FAIL health unreachable: $($_.Exception.Message)"
  exit 2
}

$cssEnv = @('G:\apps\css\.env', 'F:\apps\css\.env') | Where-Object { Test-Path $_ } | Select-Object -First 1
$pw = $null
if ($cssEnv) {
  Get-Content $cssEnv | ForEach-Object {
    if ($_ -match '^\s*CSS_ADMIN_PASSWORD\s*=\s*(.+)\s*$') { $pw = $Matches[1].Trim().Trim('"') }
  }
}
if (-not $pw) { $pw = 'admin123' }

$tok = Invoke-RestMethod -Method Post -Uri "$Css/auth/login" -ContentType 'application/json' -Body (@{
  username = 'admin'; password = $pw; clientId = $ClientId
} | ConvertTo-Json)
$access = if ($tok.access_token) { $tok.access_token } else { $tok.accessToken }
$hdr = @{ Authorization = "Bearer $access" }

$ops = Invoke-RestMethod "$Api/ops/status" -Headers $hdr -TimeoutSec 60
$events = Invoke-RestMethod "$Api/events" -Headers $hdr -TimeoutSec 30

Write-Host "events=$($events.Count)"
$events | Group-Object category | ForEach-Object { Write-Host "  cat=$($_.Name) n=$($_.Count)" }
Write-Host "pg=$($ops.pg.level) app_conns=$($ops.pg.app_conns) backup=$($ops.backup_overall_level) probes_ok=$($ops.probes_ok) down=$($ops.probes_down)"

$cats = @($events | ForEach-Object { $_.category } | Select-Object -Unique)
$need = @('pg_pressure', 'fleet_probe', 'abandon_scan', 'backup_freshness')
$missing = @($need | Where-Object { $_ -notin $cats })
$crit = @($events | Where-Object { $_.severity -eq 'CRIT' })

$fail = $false
if ($missing.Count -gt 0) {
  Write-Host "SOAK_WARN missing categories: $($missing -join ',')"
  $fail = $true
}
if ($crit.Count -gt 0) {
  Write-Host "SOAK_WARN CRIT events=$($crit.Count)"
  $fail = $true
}
if ($ops.pg.level -eq 'CRIT') {
  Write-Host 'SOAK_FAIL pg CRIT'
  exit 2
}

if ($fail) {
  Write-Host 'soak_met=false (see warnings; confirm uptime before FAIL)'
  exit 1
}
Write-Host 'soak_snapshot=OK (set soak_met=true in docs/SOAK-0.1.md after >=4h preferred >=20h)'
exit 0
