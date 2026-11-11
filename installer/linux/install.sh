#!/bin/sh
# Nexus agent installer for Linux (x86-64 and ARM64, systemd).
#
#   sudo sh install.sh [path/to/nexus]
#
# Binary: the argument, else ./nexus next to this script, else agent/target/release/nexus.
# Convex URL: $CONVEX_URL, else convex-url.txt next to this script (kept out of the repo).
# Steps: copy the binary to /opt/nexus, create /usr/local/bin/nexus symlink for CLI terminal commands,
# configure /etc/nexus/config.toml, initialize IPC control files, pair, and enable systemd service.
set -eu

here=$(cd "$(dirname "$0")" && pwd)
[ "$(id -u)" -eq 0 ] || { echo "Run with sudo: sudo sh $0" >&2; exit 1; }
has_systemd=0
if command -v systemctl >/dev/null 2>&1; then
  has_systemd=1
fi

bin=${1:-}
[ -n "$bin" ] || for c in "$here/nexus" "$here/../../agent/target/release/nexus" "$here/../../agent/target/x86_64-unknown-linux-musl/release/nexus" "$here/../../agent/target/aarch64-unknown-linux-musl/release/nexus" "$here/pc-monitor-agent"; do
  [ -f "$c" ] && { bin=$c; break; }
done
[ -n "$bin" ] && [ -f "$bin" ] || { echo "Agent binary not found. Pass its path: sudo sh $0 /path/to/nexus" >&2; exit 1; }

url=${CONVEX_URL:-}
[ -n "$url" ] || { [ -f "$here/convex-url.txt" ] && url=$(tr -d ' \r\n' < "$here/convex-url.txt"); }
case "$url" in https://*.convex.cloud) ;; *) echo "Set CONVEX_URL or put the Convex URL in $here/convex-url.txt" >&2; exit 1 ;; esac

echo "================================================================================"
echo "                     NEXUS END USER TERMS & PRIVACY NOTICE"
echo "================================================================================"
echo "Nexus is open-source hardware monitoring software licensed under the MIT License."
echo "By installing Nexus on this system, you confirm that:"
echo "  1. You are at least 18 years of age."
echo "  2. You own this computer or have explicit permission to monitor its hardware."
echo "     Unauthorized surveillance of individuals is strictly prohibited."
echo "  3. You accept the Nexus Terms of Service and Privacy Policy:"
echo "     https://github.com/theallmyti/Nexus/blob/main/docs/TERMS.md"
echo "     https://github.com/theallmyti/Nexus/blob/main/docs/PRIVACY.md"
echo "================================================================================"
echo

if [ "${ASSUME_YES:-0}" -ne 1 ] && [ -t 0 ]; then
  printf "Do you accept the Terms of Service and Privacy Policy? [y/N]: "
  read -r accept
  case "$accept" in
    [yY]|[yY][eE][sS]) ;;
    *) echo "Installation cancelled: Terms of Service not accepted." >&2; exit 1 ;;
  esac
fi

if [ "$has_systemd" -eq 1 ]; then
  systemctl stop nexus 2>/dev/null || true
  systemctl stop pc-monitor-agent 2>/dev/null || true
fi
install -d -m 755 /opt/nexus
install -m 755 "$bin" /opt/nexus/nexus
[ -f "$here/uninstall.sh" ] && install -m 755 "$here/uninstall.sh" /opt/nexus/uninstall.sh

# Documentation and legal terms
install -d -m 755 /opt/nexus/docs
[ -f "$here/LEGAL_AGREEMENT.txt" ] && install -m 644 "$here/LEGAL_AGREEMENT.txt" /opt/nexus/docs/LEGAL_AGREEMENT.txt
[ -f "$here/LEGAL.md" ] && install -m 644 "$here/LEGAL.md" /opt/nexus/docs/LEGAL.md
[ -f "$here/docs/TERMS.md" ] && install -m 644 "$here/docs/TERMS.md" /opt/nexus/docs/TERMS.md
[ -f "$here/docs/PRIVACY.md" ] && install -m 644 "$here/docs/PRIVACY.md" /opt/nexus/docs/PRIVACY.md
[ -f "$here/docs/COOKIE_POLICY.md" ] && install -m 644 "$here/docs/COOKIE_POLICY.md" /opt/nexus/docs/COOKIE_POLICY.md

# Symlink so `nexus` terminal commands work anywhere without full path
ln -sf /opt/nexus/nexus /usr/local/bin/nexus
rm -f /usr/local/bin/pc-monitor-agent 2>/dev/null || true

# Configuration directory and files
install -d -m 755 /etc/nexus
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

# Initialize IPC control and status files so standard users can inspect status and pause/resume
touch /etc/nexus/control.json /etc/nexus/status.json 2>/dev/null || true
chmod 666 /etc/nexus/control.json /etc/nexus/status.json 2>/dev/null || true

echo
echo "Pair this computer: scan the QR code below with your camera or Google Lens to open Nexus (or download the APK),"
echo "or type the 6-character code in the app. Press Ctrl+C to skip; the agent then waits until it is paired."
echo
/opt/nexus/nexus pair "$cfg" || echo "Not paired yet. Pair later with: sudo nexus pair"

if [ "$has_systemd" -eq 1 ]; then
  service_src="$here/nexus.service"
  install -m 644 "$service_src" /etc/systemd/system/nexus.service
  systemctl disable --now pc-monitor-agent 2>/dev/null || true
  rm -f /etc/systemd/system/pc-monitor-agent.service
  systemctl daemon-reload
  systemctl enable --now nexus
  echo
  echo "Installed and started Nexus background service."
  echo "Service status: systemctl status nexus"
  echo "Live logs:      journalctl -u nexus -f"
else
  echo
  echo "Installed to /opt/nexus/nexus."
  echo "Systemd not detected on this machine. To run the agent: sudo nexus"
fi

echo
echo "Nexus CLI Terminal Commands:"
echo "  nexus status       - View real-time status, CPU/RAM utilization, and temperature"
echo "  nexus pause [MINS] - Temporarily pause monitoring (default: 24h, or specify minutes)"
echo "  nexus resume       - Resume live monitoring immediately"
echo "  nexus restart      - Restart the background agent service"
echo "  nexus pair         - Pair this machine with your Nexus account"
echo "  nexus unpair       - Unlink this machine from Nexus"
echo "  nexus sensors      - List detected hardware sensors"
echo
echo "Uninstall: sudo sh /opt/nexus/uninstall.sh"
