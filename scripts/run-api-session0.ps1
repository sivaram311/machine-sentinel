#Requires -Version 5.1
<#
.SYNOPSIS
  Run machine-sentinel DEV API in Session 0 context (SYSTEM).

.NOTES
  This script is intended to be started by Windows Task Scheduler at startup.
  It loads MACHINE_SENTINEL_ROLE_DEV_PASSWORD / MACHINE_SENTINEL_ROLE_DEV from
  MyAgent secrets and starts the pre-built Spring Boot jar.
#>

$ErrorActionPreference = 'Stop'

$repoRoot = 'E:\MyWorkspace\machine-sentinel'
$backendDir = Join-Path $repoRoot 'backend'
$targetDir = Join-Path $backendDir 'target'
$secretsFile = 'E:\MyAgent\workflow\db\secrets\postgres.env'
$apiPort = 3350

if (-not (Test-Path $secretsFile)) {
  throw "Missing secrets file: $secretsFile"
}

if (-not (Test-Path $backendDir)) {
  throw "Missing backend dir: $backendDir"
}

$conn = Get-NetTCPConnection -LocalPort $apiPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
  Write-Host "API port $apiPort already in use (PID $($conn.OwningProcess)); not starting duplicate."
  exit 0
}

$s = @{}
Get-Content $secretsFile | ForEach-Object {
  if ($_ -match '^\s*([A-Z0-9_]+)\s*=\s*(.+?)\s*$') {
    $s[$Matches[1]] = $Matches[2].Trim().Trim('"')
  }
}

if (-not $s.ContainsKey('MACHINE_SENTINEL_ROLE_DEV_PASSWORD')) {
  throw "Secrets missing MACHINE_SENTINEL_ROLE_DEV_PASSWORD"
}

$env:MACHINE_SENTINEL_ROLE_DEV_PASSWORD = $s['MACHINE_SENTINEL_ROLE_DEV_PASSWORD']
$env:MACHINE_SENTINEL_ROLE_DEV = $s['MACHINE_SENTINEL_ROLE_DEV']
$env:SPRING_PROFILES_ACTIVE = 'dev'

# Clear inherited Spring datasource vars to avoid cross-env contamination (INC-05)
@(
  'SPRING_DATASOURCE_URL',
  'SPRING_DATASOURCE_USERNAME',
  'SPRING_DATASOURCE_PASSWORD',
  'SPRING_DATASOURCE_DRIVER_CLASS_NAME'
) | ForEach-Object {
  Remove-Item "Env:$_" -ErrorAction SilentlyContinue
}

function FindJar {
  Get-ChildItem $targetDir -Filter 'machine-sentinel-backend-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
}

$jar = FindJar
if (-not $jar) {
  # Cold-start safety: build once if the jar is missing.
  Set-Location $backendDir
  & mvn -q -DskipTests package
  $jar = FindJar
}

if (-not $jar) {
  throw "Could not locate machine-sentinel-backend jar under $targetDir"
}

Write-Host "Starting machine-sentinel DEV API from jar: $($jar.FullName)"

Set-Location $backendDir
& java -jar $jar.FullName
exit $LASTEXITCODE

