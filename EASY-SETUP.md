# Easy Setup Guide — Nexus Agent

This guide covers running and pairing the Nexus PC Monitoring Agent.

---

## Quick Start (Automated Installer)

1. **Download**: Obtain `nexus-setup-0.2.1.exe` from GitHub Releases.
2. **Run**: Double-click `nexus-setup-0.2.1.exe` to automatically install and launch the agent.
3. **Pair**:
   - The agent displays a 6-character pairing code in the console / system tray.
   - Open the **Nexus Android App**.
   - Tap **Add Device** → Enter the 6-character code (or scan the QR code).
   - Your PC will appear in your device list with live telemetry.

---

## Service Management
- **Start Agent**: Double-click `Start-PC-Monitor.bat` or run `nexus.exe` / `pc-monitor-agent.exe`.
- **Stop Agent**: Right-click the tray icon and select **Exit**, or double-click `Stop-PC-Monitor.bat`.
