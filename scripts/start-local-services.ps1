# Reuse the isolated services already prepared on this workstation.
# On another workstation, use compose.yml or configure existing services in .env.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
function Test-LocalPort([int]$Port) {
    $connection = New-Object Net.Sockets.TcpClient
    try { $connection.Connect('127.0.0.1',$Port); return $true }
    catch { return $false }
    finally { $connection.Dispose() }
}
if (-not (Test-LocalPort 3307)) {
    $mysqlCommand = Get-Command mysql.exe -ErrorAction Stop
    $mysqlBinary = Join-Path (Split-Path -Parent $mysqlCommand.Source) 'mysqld.exe'
    $mysqlBase = Split-Path -Parent (Split-Path -Parent $mysqlBinary)
    $dataDirectory = Join-Path $projectRoot '.tools\mysql-data'
    if (-not (Test-Path -LiteralPath (Join-Path $dataDirectory 'mysql.ibd'))) {
        throw 'Isolated MySQL data is not initialized. Use Docker Compose or your own database.'
    }
    # MySQL 8.0.21 on Windows needs an ASCII path for server startup.
    $runtimeParent = Join-Path $env:USERPROFILE '.codex\tmp'
    New-Item -ItemType Directory -Force -Path $runtimeParent | Out-Null
    $dataAlias = Join-Path $runtimeParent 'anlan-mysql-data'
    if (-not (Test-Path -LiteralPath $dataAlias)) {
        New-Item -ItemType Junction -Path $dataAlias -Target $dataDirectory | Out-Null
    } elseif ([IO.Path]::GetFullPath([string](Get-Item -LiteralPath $dataAlias).Target) -ne [IO.Path]::GetFullPath($dataDirectory)) {
        throw 'The MySQL data alias points to another workspace.'
    }
    $mysqlProcess = Start-Process -FilePath $mysqlBinary -ArgumentList @('--no-defaults',"--basedir=`"$mysqlBase`"","--datadir=`"$dataAlias`"",'--port=3307','--bind-address=127.0.0.1','--mysqlx=OFF',"--log-error=`"$dataAlias\mysql-error.log`"") -WindowStyle Hidden -PassThru
    [IO.File]::WriteAllText((Join-Path $projectRoot '.tools\mysql.pid'),[string]$mysqlProcess.Id)
}
if (-not (Test-LocalPort 6380)) {
    $redisBinary = Join-Path $projectRoot '.tools\redis-7.4.2\src\redis-server'
    if (-not (Test-Path -LiteralPath $redisBinary)) { throw 'Prepared WSL Redis binary was not found. Use Docker Compose instead.' }
    $redisConfig = Join-Path $projectRoot '.tools\redis-local.conf'
    [IO.File]::WriteAllText($redisConfig,"bind 127.0.0.1`nport 6380`nsave `"`"`nappendonly no`ndaemonize yes`npidfile /tmp/anlan-redis-6380.pid`n",(New-Object Text.UTF8Encoding $false))
    $linuxBinary = '/mnt/' + $redisBinary.Substring(0,1).ToLowerInvariant() + $redisBinary.Substring(2).Replace('\','/')
    $linuxConfig = '/mnt/' + $redisConfig.Substring(0,1).ToLowerInvariant() + $redisConfig.Substring(2).Replace('\','/')
    & wsl.exe -d Ubuntu --exec $linuxBinary $linuxConfig
    if ($LASTEXITCODE -ne 0) { throw 'WSL Redis startup failed.' }
}
for ($attempt=0;$attempt -lt 30;$attempt++) {
    if ((Test-LocalPort 3307) -and (Test-LocalPort 6380)) { Write-Host 'Isolated MySQL :3307 and Redis :6380 are ready.'; exit 0 }
    Start-Sleep -Milliseconds 500
}
throw 'Services did not become ready. Inspect .tools/mysql-data/mysql-error.log.'

