#!/bin/sh
# Packages the Linux installer as one runnable file, like the Windows setup .exe:
#   dist/nexus-<version>-linux-<arch>.run   (users run: sudo sh <file>.run)
#
#   sh installer/linux/make-run.sh <arch> <path/to/nexus>
#
# <arch> is x86_64 or aarch64. The binary comes from the "Linux build" GitHub workflow
# (gh run download -n nexus-linux-<arch>). The Convex URL is read from
# installer/convex-url.txt here, on this computer; it is never uploaded to GitHub.
set -eu

arch=${1:?usage: make-run.sh <x86_64|aarch64> <binary>}
bin=${2:?usage: make-run.sh <x86_64|aarch64> <binary>}
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
version=$(sed -n 's/^version = "\(.*\)"/\1/p' "$root/agent/Cargo.toml" | head -1)
url_file="$root/installer/convex-url.txt"
[ -f "$url_file" ] || { echo "installer/convex-url.txt is missing" >&2; exit 1; }
[ -f "$bin" ] || { echo "binary not found: $bin" >&2; exit 1; }

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
cp "$bin" "$stage/nexus"
service_src="$here/nexus.service"
[ -f "$service_src" ] || service_src="$here/pc-monitor-agent.service"
cp "$here/install.sh" "$here/uninstall.sh" "$service_src" "$stage/"
tr -d ' \r\n' < "$url_file" > "$stage/convex-url.txt"
chmod 755 "$stage/nexus" "$stage/install.sh" "$stage/uninstall.sh"

out="$root/dist/nexus-$version-linux-$arch.run"
mkdir -p "$root/dist"
cat > "$out" <<EOF
#!/bin/sh
# Nexus agent $version for Linux ($arch, systemd). Run: sudo sh \$(basename "\$out")
set -eu
case "\$(uname -m)" in
  $arch|$( [ "$arch" = aarch64 ] && echo arm64 || echo amd64 )) ;;
  *) echo "This installer is for $arch; this computer is \$(uname -m)." >&2; exit 1 ;;
esac
[ "\$(id -u)" -eq 0 ] || { echo "Run with sudo: sudo sh \$0" >&2; exit 1; }
tmp=\$(mktemp -d)
trap 'rm -rf "\$tmp"' EXIT
line=\$(awk '/^__PAYLOAD_BELOW__\$/ { print NR + 1; exit }' "\$0")
tail -n +"\$line" "\$0" | tar -xzf - -C "\$tmp"
chmod 755 "\$tmp/nexus" # file modes are not kept when packaged on Windows
sh "\$tmp/install.sh" "\$tmp/nexus"
exit 0
__PAYLOAD_BELOW__
EOF
tar -czf - -C "$stage" . >> "$out"
chmod 755 "$out"
echo "Built $out ($(wc -c < "$out") bytes)"

cp "$here/uninstall.sh" "$root/dist/nexus-uninstall.sh"
chmod 755 "$root/dist/nexus-uninstall.sh"
echo "Generated $root/dist/nexus-uninstall.sh"
