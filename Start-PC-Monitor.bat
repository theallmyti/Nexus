@echo off
echo Starting Nexus PC Monitor Agent...
if exist "nexus.exe" (
    start "" "nexus.exe"
) else if exist "pc-monitor-agent.exe" (
    start "" "pc-monitor-agent.exe"
) else (
    echo Binary not found. Please ensure nexus.exe is in this directory or run via cargo run.
    pause
)
