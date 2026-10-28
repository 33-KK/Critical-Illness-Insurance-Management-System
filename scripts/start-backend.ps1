param([ValidateSet('demo','mysql')][string]$Profile = 'demo', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if ($Profile -eq 'mysql') {
    $environmentFile = Join-Path $projectRoot '.env'
    if (-not (Test-Path -LiteralPath $environmentFile)) { throw 'Copy .env.example to .env and configure your database first.' }
    foreach ($line in [IO.File]::ReadAllLines($environmentFile)) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
    }
} else { $env:REDIS_ENABLED = 'false' }
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
if (-not $SkipBuild) {
    & (Join-Path $projectRoot 'mvnw.cmd') -f backend/pom.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
}
$jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'backend\target') -Filter '*.jar' | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw 'No backend JAR found. Run without -SkipBuild.' }
Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
& java '-Dfile.encoding=UTF-8' -jar $jar.FullName "--spring.profiles.active=$Profile" '--server.address=127.0.0.1'
exit $LASTEXITCODE

