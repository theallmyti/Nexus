$ErrorActionPreference = "Stop"
$iscc = Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe'
if (-not (Test-Path $iscc)) {
    $iscc = "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe"
}
if (-not (Test-Path $iscc)) {
    throw "ISCC.exe not found"
}
Write-Host "Compiling setup installer..."
& $iscc /Q installer\pc-monitor-agent.iss
Write-Host "Compiling uninstaller..."
& $iscc /Q installer\pc-monitor-agent-uninstall.iss
Write-Host "Inno Setup compilation complete."
