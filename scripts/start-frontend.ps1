$ErrorActionPreference = 'Stop'
$frontendRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend'
Set-Location -LiteralPath $frontendRoot
if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules'))) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
}
& npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
exit $LASTEXITCODE

