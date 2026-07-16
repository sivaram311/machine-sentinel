#Requires -Version 5.1
<#
.SYNOPSIS
  Serve machine-sentinel thin ops UI on :3351
#>
$ErrorActionPreference = 'Stop'
$ui = Join-Path (Split-Path $PSScriptRoot -Parent) 'ui'
if (-not (Test-Path (Join-Path $ui 'index.html'))) { throw "Missing $ui\index.html" }

$conn = Get-NetTCPConnection -LocalPort 3351 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
  Write-Host "Port 3351 already in use by PID $($conn.OwningProcess)"
  exit 0
}

Write-Host "Serving machine-sentinel UI from $ui on http://127.0.0.1:3351/"
Set-Location $ui
python -m http.server 3351 --bind 127.0.0.1
