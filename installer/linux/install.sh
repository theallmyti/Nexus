#!/bin/sh
# Nexus agent installer for Linux (x86-64 and ARM64, systemd).
#
#   sudo sh install.sh [path/to/nexus]
#
# Binary: the argument, else ./nexus or ./pc-monitor-agent next to this script, else agent/target/release.
# Convex URL: $CONVEX_URL, else convex-url.txt next to this script (kept out of the repo).
# Steps: copy the binary to /opt/nexus, write /etc/nexus/config.toml (root only),
# show the pairing QR code in this terminal, then enable and start the service.
set -eu

here=$(cd "$(dirname "$0")" && pwd)
[ "$(id -u)" -eq 0 ] || { echo "Run with sudo: sudo sh $0" >&2; exit 1; }
has_systemd=0
if command -v systemctl >/dev/null 2>&1; then
  has_systemd=1
fi

bin=${1:-}
[ -n "$bin" ] || for c in "$here/nexus" "$here/pc-monitor-agent" "$here/../../agent/target/release/nexus" "$here/../../agent/target/x86_64-unknown-linux-musl/release/nexus" "$here/../../agent/target/aarch64-unknown-linux-musl/release/nexus"; do
  [ -f "$c" ] && { bin=$c; break; }
done
[ -n "$bin" ] && [ -f "$bin" ] || { echo "Agent binary not found. Pass its path: sudo sh $0 /path/to/nexus" >&2; exit 1; }

url=${CONVEX_URL:-}
[ -n "$url" ] || { [ -f "$here/convex-url.txt" ] && url=$(tr -d ' \r\n' < "$here/convex-url.txt"); }
case "$url" in https://*.convex.cloud) ;; *) echo "Set CONVEX_URL or put the Convex URL in $here/convex-url.txt" >&2; exit 1 ;; esac

if [ "$has_systemd" -eq 1 ]; then
  systemctl stop nexus 2>/dev/null || true
  systemctl stop pc-monitor-agent 2>/dev/null || true
fi
install -d -m 755 /opt/nexus
install -m 755 "$bin" /opt/nexus/nexus
[ -f "$here/uninstall.sh" ] && install -m 755 "$here/uninstall.sh" /opt/nexus/uninstall.sh

install -d -m 700 /etc/nexus
cfg=/etc/nexus/config.toml
legacy_cfg=/etc/pc-monitor/config.toml
if [ ! -f "$cfg" ] && [ -f "$legacy_cfg" ]; then
  cp "$legacy_cfg" "$cfg"
fi

if [ -f "$cfg" ]; then
  # Upgrade: keep the device secret, point at this build's backend.
  sed -i "s|^convex_url *=.*|convex_url = \"$url\"|" "$cfg"
else
  umask 077
  printf '# Nexus agent config, written by install.sh.\nconvex_url = "%s"\nlive_interval_secs = 1\n' "$url" > "$cfg"
fi
chmod 600 "$cfg"

echo
echo "Pair this computer: scan the QR code below with your camera or Google Lens to open Nexus (or download the APK),"
echo "or type the 6-character code in the app. Press Ctrl+C to skip; the agent then waits until it is paired."
echo
/opt/nexus/nexus pair "$cfg" || echo "Not paired yet. Pair later with: sudo /opt/nexus/nexus pair $cfg"

if [ "$has_systemd" -eq 1 ]; then
  service_src="$here/nexus.service"
  [ -f "$service_src" ] || service_src="$here/pc-monitor-agent.service"
  install -m 644 "$service_src" /etc/systemd/system/nexus.service
  systemctl disable --now pc-monitor-agent 2>/dev/null || true
  rm -f /etc/systemd/system/pc-monitor-agent.service
  systemctl daemon-reload
  systemctl enable --now nexus
  echo
  echo "Installed. Status: systemctl status nexus    Log: journalctl -u nexus -f"
else
  echo
  echo "Installed to /opt/nexus/nexus."
  echo "Systemd not detected on this machine. To run the agent, start: sudo /opt/nexus/nexus /etc/nexus/config.toml"
fi
echo "Uninstall: sudo sh /opt/nexus/uninstall.sh"
