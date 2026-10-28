param([ValidateSet('demo','mysql')][string]$Profile = 'demo', [switch]$WithContainers, [switch]$WithLocalServices)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if ($WithContainers -and $WithLocalServices) { throw 'Choose WithContainers or WithLocalServices.' }
if ($WithLocalServices) {
    & (Join-Path $projectRoot 'scripts\start-local-services.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Local service startup failed.' }
    $Profile = 'mysql'
}
if ($WithContainers) {
    & docker compose -p insurance_portfolio -f compose.yml up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Container startup failed. Check Docker Desktop and port 3307/6380.' }
    $Profile = 'mysql'
}
foreach ($port in @(8080,5173)) {
    $client = New-Object Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1',$port); throw "Port $port is already in use. Stop that application before starting this project." }
    catch [Net.Sockets.SocketException] { }
    finally { $client.Dispose() }
}
$logDirectory = Join-Path $projectRoot 'logs'
$runtimeDirectory = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force -Path $logDirectory,$runtimeDirectory | Out-Null
$backendScript = Join-Path $projectRoot 'scripts\start-backend.ps1'
$frontendScript = Join-Path $projectRoot 'scripts\start-frontend.ps1'
$backendProcess = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$backendScript`"",'-Profile',$Profile) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'backend.log') -RedirectStandardError (Join-Path $logDirectory 'backend-error.log')
$frontendProcess = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$frontendScript`"") -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'frontend.log') -RedirectStandardError (Join-Path $logDirectory 'frontend-error.log')
@{backendPid=$backendProcess.Id;frontendPid=$frontendProcess.Id;workspace=$projectRoot;profile=$Profile} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeDirectory 'app-processes.json') -Encoding UTF8
Write-Host 'Starting application. First build may take a few minutes.'
Write-Host 'Logs: logs/backend.log and logs/frontend.log'
for ($attempt=0;$attempt -lt 150;$attempt++) {
    $backendProcess.Refresh()
    $frontendProcess.Refresh()
    if ($backendProcess.HasExited -or $frontendProcess.HasExited) { throw 'A service exited during startup. Inspect logs/backend-error.log and logs/frontend-error.log.' }
    $backendReady = $false
    $frontendReady = $false
    try { $backendReady = (Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/auth/captcha' -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { }
    try { $frontendReady = (Invoke-WebRequest -Uri 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { }
    if ($backendReady -and $frontendReady) { break }
    if ($attempt -eq 149) { throw 'Startup timed out. Inspect logs/ for details.' }
    Start-Sleep -Seconds 2
}
Write-Host 'Application ready: http://127.0.0.1:5173'
Write-Host 'Demo accounts: admin / doctor / reviewer. Password: Demo@2026'
Write-Host 'Stop the application with .\stop.ps1'

