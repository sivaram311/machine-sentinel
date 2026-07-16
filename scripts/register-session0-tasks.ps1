#Requires -Version 5.1
<#
.SYNOPSIS
  Register Windows startup tasks for machine-sentinel API/UI under Session 0 (SYSTEM).

.NOTES
  - Creates/overwrites Scheduled Tasks that start on system boot.
  - Runs as SYSTEM, so it survives unexpected restarts (and planned reboots).
  - Does not force immediate starts (to avoid duplicating currently-running instances).
#>

$ErrorActionPreference = 'Stop'

$repoRoot = 'E:\MyWorkspace\machine-sentinel'
$taskApi = 'MachineSentinelAPI'
$taskUi = 'MachineSentinelUI'

$apiPs1 = Join-Path $repoRoot 'scripts\run-api-session0.ps1'
$uiPs1 = Join-Path $repoRoot 'scripts\run-ui-session0.ps1'

if (-not (Test-Path $apiPs1)) { throw "Missing $apiPs1" }
if (-not (Test-Path $uiPs1)) { throw "Missing $uiPs1" }

function EnsureTask {
  param(
    [Parameter(Mandatory=$true)][string]$Name,
    [Parameter(Mandatory=$true)][string]$Tr
  )

  $exists = $false
  schtasks /Query /TN $Name /FO LIST /V | Out-Null
  if ($LASTEXITCODE -eq 0) {
    $exists = $true
  }

  if ($exists) {
    Write-Host "Updating scheduled task: $Name"
    schtasks /Delete /TN $Name /F | Out-Null
  } else {
    Write-Host "Creating scheduled task: $Name"
  }

  schtasks /Create /TN $Name /SC ONSTART /RU SYSTEM /RL HIGHEST /F /TR $Tr | Out-Null
}

$apiTr = 'powershell -NoProfile -ExecutionPolicy Bypass -File "' + $apiPs1 + '"'
$uiTr = 'powershell -NoProfile -ExecutionPolicy Bypass -File "' + $uiPs1 + '"'

EnsureTask -Name $taskApi -Tr $apiTr
EnsureTask -Name $taskUi -Tr $uiTr

Write-Host "Scheduled Tasks registered: $taskApi, $taskUi"

schtasks /Query /TN $taskApi /FO LIST /V | Select-Object -First 10
schtasks /Query /TN $taskUi /FO LIST /V | Select-Object -First 10

