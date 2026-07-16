#Requires -Version 5.1
<#
.SYNOPSIS
  Start machine-sentinel DEV API on :3350 (observe-only).
#>
param(
  [string]$SecretsFile = 'E:\MyAgent\workflow\db\secrets\postgres.env'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not (Test-Path "$PSScriptRoot\..\pom.xml")) {
  $backend = Join-Path (Split-Path $PSScriptRoot -Parent) 'backend'
} else {
  $backend = Split-Path $PSScriptRoot -Parent
}
# Prefer app-relative: scripts/ is under machine-sentinel/
$appRoot = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $appRoot 'backend'

if (-not (Test-Path $SecretsFile)) { throw "Missing $SecretsFile" }
Get-Content $SecretsFile | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $p = $_ -split '=', 2
  $k = $p[0].Trim(); $v = $p[1].Trim().Trim('"')
  if ($k -like 'MACHINE_SENTINEL_*' -or $k -eq 'POSTGRES_HOST') {
    Set-Item -Path "Env:$k" -Value $v
  }
}

# Clear inherited Spring datasource pollution (INC-05)
@('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD',
  'SPRING_DATASOURCE_DRIVER_CLASS_NAME') | ForEach-Object {
  Remove-Item "Env:$_" -ErrorAction SilentlyContinue
}

$env:SPRING_PROFILES_ACTIVE = 'dev'
Set-Location $backend
Write-Host "Starting machine-sentinel DEV API :3350 (observe-only) from $backend"
mvn -q spring-boot:run "-Dspring-boot.run.profiles=dev"
