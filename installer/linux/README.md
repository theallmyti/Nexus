# Nexus Linux Installer & Service

Production installer and hardened systemd service for the Nexus Hardware Monitoring Agent on Linux (`x86_64` and `aarch64` / ARM64).

## Quick Install

```bash
# Standalone run package
sudo sh dist/nexus-0.2.5-linux-x86_64.run
# Or using install.sh directly
sudo sh installer/linux/install.sh [path/to/nexus]
```

## What `install.sh` Does

1. **Binary Placement**: Copies the static musl binary to `/opt/nexus/nexus` (mode 755).
2. **Terminal Command Symlink**: Creates `/usr/local/bin/nexus -> /opt/nexus/nexus` so `nexus` commands run in any shell without specifying the full path.
3. **Secret Security**: Generates `/etc/nexus/config.toml` with `chmod 600` (root-only read/write) to ensure the 256-bit device secret cannot be read by unprivileged users.
4. **Permissive IPC**: Pre-initializes `/etc/nexus/control.json` and `/etc/nexus/status.json` with `chmod 666` so standard users can inspect status and pause/resume metrics collection from user terminals without needing `sudo`.
5. **Interactive Pairing**: Displays an ASCII QR code and 6-character pairing code in the terminal to pair with your mobile app or web dashboard.
6. **Hardened systemd Service**: Deploys and enables `nexus.service`.

## Security Sandboxing (`nexus.service`)

The systemd service enforces strict process isolation:

| Security Setting | Purpose |
|------------------|---------|
| `NoNewPrivileges=yes` | Disallows gaining new privileges via setuid/setgid binaries |
| `ProtectHome=read-only` | Prevents modifying `/home` or `/root` user directories |
| `ProtectSystem=full` | Mounts `/usr`, `/boot`, and `/etc` read-only for the agent |
| `ReadWritePaths=/etc/nexus` | Explicit write-whitelist restricted only to `/etc/nexus` |
| `PrivateTmp=yes` | Isolated `/tmp` and `/var/tmp` directory |
| `ProtectControlGroups=yes` | Read-only control groups hierarchy |
| `ProtectKernelModules=yes` | Prevents kernel module loading/unloading |
| `RestrictRealtime=yes` | Prevents real-time scheduling priority escalation |
| `RestrictSUIDSGID=yes` | Blocks creation of setuid/setgid files |
| `MemoryMax=100M` | Hard memory budget to prevent memory leaks |
| `CPUQuota=10%` | CPU cap to ensure background monitoring never degrades user tasks |

## CLI Terminal Commands

Once installed, standard users and administrators can manage Nexus from the command line:

```bash
# Show live agent status, CPU/RAM %, temperature, and backend link
nexus status

# Temporarily pause metrics collection (default 24h or specify minutes)
nexus pause 30

# Resume live metrics collection
nexus resume

# Restart the background service (via systemctl)
nexus restart

# Pair this machine with your Nexus account
nexus pair

# Remove device secret and unlink from Nexus
nexus unpair

# Inspect detected hardware sensors
nexus sensors

# View command help
nexus help
```

## Uninstallation

```bash
sudo sh /opt/nexus/uninstall.sh
```
This safely disables the systemd service, unpairs the device from the online backend, deletes `/usr/local/bin/nexus`, and purges `/opt/nexus` and `/etc/nexus`.
