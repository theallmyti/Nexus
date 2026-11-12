Write-Host "Running Nexus PC Monitor Agent..."
if (Test-Path ".\nexus.exe") {
    Start-Process ".\nexus.exe"
} elseif (Test-Path ".\pc-monitor-agent.exe") {
    Start-Process ".\pc-monitor-agent.exe"
} else {
    Set-Location "agent"
    cargo run --release
}
