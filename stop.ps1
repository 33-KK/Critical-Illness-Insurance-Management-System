$ErrorActionPreference = 'Stop'
$stateFile = Join-Path $PSScriptRoot '.tools\app-processes.json'
if (-not (Test-Path -LiteralPath $stateFile)) { Write-Host 'No application process record found.'; exit 0 }
$runtimeState = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if ($runtimeState.workspace -ne $PSScriptRoot) { throw 'Process record belongs to another workspace.' }
foreach ($processId in @($runtimeState.backendPid,$runtimeState.frontendPid)) {
    $targetProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    if (-not $targetProcess) { continue }
    if (-not $targetProcess.CommandLine.Contains($PSScriptRoot)) { throw "Process $processId does not belong to this workspace; refusing to stop it." }
    & taskkill.exe /PID $processId /T /F
}
Write-Host 'Application stopped. Database containers and saved data are retained.'

