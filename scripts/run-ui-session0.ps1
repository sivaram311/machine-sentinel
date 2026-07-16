#Requires -Version 5.1
<#
.SYNOPSIS
  Serve machine-sentinel thin ops UI in Session 0 context (SYSTEM).

.NOTES
  Designed for Windows Task Scheduler startup trigger.
  This UI is static HTML/JS served by python http.server.
#>

$ErrorActionPreference = 'Stop'

$repoRoot = 'E:\MyWorkspace\machine-sentinel'
$uiDir = Join-Path $repoRoot 'ui'
$port = 3351

if (-not (Test-Path $uiDir)) {
  throw "Missing UI dir: $uiDir"
}

$conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
  Write-Host "UI port $port already in use (PID $($conn.OwningProcess)); not starting duplicate."
  exit 0
}

$py = (Get-Command python.exe -ErrorAction SilentlyContinue)
if (-not $py) {
  $py = (Get-Command python -ErrorAction SilentlyContinue)
}
if (-not $py) {
  throw "python not found on PATH. UI service cannot start."
}

Set-Location $uiDir
Write-Host "Starting machine-sentinel UI on http://127.0.0.1:$port/ from $uiDir"
& $py.Source -m http.server $port --bind 127.0.0.1
exit $LASTEXITCODE

