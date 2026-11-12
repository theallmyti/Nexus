@echo off
echo Stopping Nexus PC Monitor Agent...
taskkill /F /IM nexus.exe 2>nul
taskkill /F /IM pc-monitor-agent.exe 2>nul
echo Done.
pause
