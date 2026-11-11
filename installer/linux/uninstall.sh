#!/bin/sh
# Removes the Nexus agent: stops the service, removes this computer and its history from
# Nexus, deletes the program and config.   sudo sh uninstall.sh
set -eu
[ "$(id -u)" -eq 0 ] || { echo "Run with sudo: sudo sh $0" >&2; exit 1; }
systemctl disable --now pc-monitor-agent 2>/dev/null || true
if [ -x /opt/pc-monitor/pc-monitor-agent ] && [ -f /etc/pc-monitor/config.toml ]; then
  /opt/pc-monitor/pc-monitor-agent unpair /etc/pc-monitor/config.toml || echo "Could not reach Nexus; remove the device on the site."
fi
rm -f /etc/systemd/system/pc-monitor-agent.service
systemctl daemon-reload
rm -rf /opt/pc-monitor /etc/pc-monitor
echo "Nexus agent removed."
