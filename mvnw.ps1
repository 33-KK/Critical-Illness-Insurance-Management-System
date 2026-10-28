$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$mavenVersion = '3.9.11'
$mavenHome = Join-Path $projectRoot ".tools\apache-maven-$mavenVersion"
$mavenCommand = Join-Path $mavenHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $mavenCommand)) {
    $toolsDirectory = Join-Path $projectRoot '.tools'
    New-Item -ItemType Directory -Force -Path $toolsDirectory | Out-Null
    $archive = Join-Path $toolsDirectory "apache-maven-$mavenVersion-bin.zip"
    $downloadUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    Write-Host "Preparing Apache Maven $mavenVersion..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archive -UseBasicParsing
    $checksumResponse = Invoke-WebRequest -Uri "$downloadUrl.sha512" -UseBasicParsing
    $expectedChecksum = ($checksumResponse.Content.Trim() -split '\s+')[0]
    $hashStream = [IO.File]::OpenRead($archive)
    $hasher = [Security.Cryptography.SHA512]::Create()
    try { $actualChecksum = [BitConverter]::ToString($hasher.ComputeHash($hashStream)).Replace('-', '') }
    finally { $hashStream.Dispose(); $hasher.Dispose() }
    if ($actualChecksum -ine $expectedChecksum) { throw 'Maven archive checksum mismatch.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($archive, $toolsDirectory)
}
& $mavenCommand @args
exit $LASTEXITCODE

