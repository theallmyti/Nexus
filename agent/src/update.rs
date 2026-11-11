//! Autonomous Auto-Update Engine for the Nexus Agent.
//!
//! Handles:
//! 1. Checking GitHub Releases for newer versions (FR-62).
//! 2. Downloading release assets over HTTPS and verifying SHA-256 checksums against SHA256SUMS (FR-63).
//! 3. Atomic executable replacement with backup (FR-64).
//! 4. Crash detection and automatic rollback after 3 failed startup attempts (FR-65).
//! 5. Cross-platform support (Windows SCM & Linux systemd).

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Notify;

const RELEASES_API_URL: &str = "https://api.github.com/repos/theallmyti/Nexus/releases/latest";
const USER_AGENT: &str = concat!("Nexus-Agent/", env!("CARGO_PKG_VERSION"));
const STATE_FILE_NAME: &str = "update_state.json";
const MAX_STARTUP_ATTEMPTS: u32 = 3;

#[derive(Deserialize, Debug, Clone)]
pub struct ReleaseAsset {
    pub name: String,
    pub browser_download_url: String,
}

#[derive(Deserialize, Debug, Clone)]
pub struct ReleaseInfo {
    pub tag_name: String,
    pub assets: Vec<ReleaseAsset>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct UpdateState {
    pub status: String, // "pending_verification", "verified", "rolled_back"
    pub target_version: String,
    pub previous_version: String,
    pub attempts: u32,
    pub updated_at: u64,
}

/// Parses "x.y.z" version into numerical components.
pub fn version_parts(v: &str) -> Vec<u64> {
    v.trim_start_matches('v')
        .split('.')
        .map(|p| p.parse().unwrap_or(0))
        .collect()
}

/// Returns true if `remote` is strictly newer than `current`.
pub fn is_newer(remote: &str, current: &str) -> bool {
    version_parts(remote) > version_parts(current)
}

fn to_hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Parses a standard SHA256SUMS file (`<hash>  <filename>`).
pub fn parse_checksums(content: &str) -> BTreeMap<String, String> {
    let mut map = BTreeMap::new();
    for line in content.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() >= 2 {
            let hash = parts[0].to_lowercase();
            let name = parts[1].trim_start_matches('*');
            map.insert(name.to_string(), hash);
        }
    }
    map
}

/// Returns priority asset prefixes/names for the current operating system and architecture.
pub fn target_asset_matches(asset_name: &str) -> bool {
    #[cfg(all(windows, target_arch = "x86_64"))]
    {
        asset_name.eq_ignore_ascii_case("nexus.exe")
            || asset_name.contains("windows-x86_64")
            || (asset_name.starts_with("nexus-setup-") && asset_name.ends_with(".exe"))
    }
    #[cfg(all(target_os = "linux", target_arch = "x86_64"))]
    {
        asset_name == "nexus-linux-x86_64"
            || (asset_name.contains("linux-x86_64") && asset_name.ends_with(".run"))
            || asset_name == "nexus"
    }
    #[cfg(all(target_os = "linux", target_arch = "aarch64"))]
    {
        asset_name == "nexus-linux-aarch64"
            || (asset_name.contains("linux-aarch64") && asset_name.ends_with(".run"))
            || asset_name == "nexus-arm64"
    }
    #[cfg(not(any(
        all(windows, target_arch = "x86_64"),
        all(target_os = "linux", target_arch = "x86_64"),
        all(target_os = "linux", target_arch = "aarch64")
    )))]
    {
        asset_name == "nexus"
    }
}

/// Fetch latest release metadata from GitHub Releases API.
pub fn fetch_latest_release() -> Result<ReleaseInfo, String> {
    let res = ureq::get(RELEASES_API_URL)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/vnd.github.v3+json")
        .config()
        .timeout_global(Some(Duration::from_secs(15)))
        .build()
        .call();

    let mut resp = match res {
        Ok(r) => r,
        Err(ureq::Error::StatusCode(404)) => {
            return Err("no published releases found yet on GitHub (404 Not Found)".to_string());
        }
        Err(e) => return Err(format!("failed to query GitHub Releases: {e}")),
    };

    let release: ReleaseInfo = resp
        .body_mut()
        .read_json()
        .map_err(|e| format!("failed to parse GitHub Releases JSON: {e}"))?;

    Ok(release)
}

/// Fetch raw text from a given URL (e.g. SHA256SUMS).
pub fn fetch_text(url: &str) -> Result<String, String> {
    let mut resp = ureq::get(url)
        .header("User-Agent", USER_AGENT)
        .config()
        .timeout_global(Some(Duration::from_secs(15)))
        .build()
        .call()
        .map_err(|e| format!("failed to download text from {url}: {e}"))?;

    resp.body_mut()
        .read_to_string()
        .map_err(|e| format!("failed to read response body: {e}"))
}

/// Download an asset to a destination path, computing and verifying its SHA-256 against expected hash.
pub fn download_and_verify(url: &str, expected_hash: &str, dest: &Path) -> Result<(), String> {
    let tmp_dest = dest.with_extension("update.tmp");
    let mut resp = ureq::get(url)
        .header("User-Agent", USER_AGENT)
        .config()
        .timeout_global(Some(Duration::from_secs(60)))
        .build()
        .call()
        .map_err(|e| format!("failed to download binary from {url}: {e}"))?;

    let mut file = File::create(&tmp_dest).map_err(|e| {
        format!(
            "failed to create temporary file {}: {e}",
            tmp_dest.display()
        )
    })?;

    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 8192];
    let mut reader = resp.body_mut().as_reader();

    loop {
        let bytes_read = reader
            .read(&mut buffer)
            .map_err(|e| format!("error reading download stream: {e}"))?;
        if bytes_read == 0 {
            break;
        }
        hasher.update(&buffer[..bytes_read]);
        file.write_all(&buffer[..bytes_read])
            .map_err(|e| format!("error writing to temporary file: {e}"))?;
    }

    file.flush()
        .map_err(|e| format!("error flushing file: {e}"))?;
    drop(file);

    let actual_hash = to_hex(&hasher.finalize());
    let expected = expected_hash.to_lowercase();

    if actual_hash != expected {
        let _ = fs::remove_file(&tmp_dest);
        return Err(format!(
            "SHA-256 mismatch: expected {}, calculated {}",
            expected, actual_hash
        ));
    }

    // Move tmp to destination
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(&tmp_dest, fs::Permissions::from_mode(0o755));
    }

    if dest.exists() {
        let _ = fs::remove_file(dest);
    }
    fs::rename(&tmp_dest, dest).map_err(|e| {
        format!(
            "failed to rename verified download to {}: {e}",
            dest.display()
        )
    })?;

    Ok(())
}

/// Replaces the currently running binary with the new binary file, creating a backup `.old`.
pub fn apply_binary_swap(target_exe: &Path, new_binary: &Path) -> Result<(), String> {
    let old_backup = target_exe.with_extension("old");
    if old_backup.exists() {
        let _ = fs::remove_file(&old_backup);
    }

    // Windows supports renaming a running executable within the same filesystem
    if target_exe.exists() {
        fs::rename(target_exe, &old_backup).map_err(|e| {
            format!(
                "failed to backup current binary to {}: {e}",
                old_backup.display()
            )
        })?;
    }

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(new_binary, fs::Permissions::from_mode(0o755));
    }

    if let Err(e) = fs::rename(new_binary, target_exe) {
        // If placing new binary fails, restore backup
        if old_backup.exists() && !target_exe.exists() {
            let _ = fs::rename(&old_backup, target_exe);
        }
        return Err(format!("failed to move new binary into place: {e}"));
    }

    Ok(())
}

/// Triggers service restart across Windows (NexusAgent) or Linux (systemd).
pub fn restart_service() -> Result<(), String> {
    #[cfg(windows)]
    {
        let status = std::process::Command::new("powershell")
            .args([
                "-NoProfile",
                "-WindowStyle",
                "Hidden",
                "-Command",
                "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -WindowStyle Hidden -Command Restart-Service NexusAgent, PCMonitorAgent -ErrorAction SilentlyContinue; Start-Service NexusAgent -ErrorAction SilentlyContinue'",
            ])
            .status()
            .map_err(|e| format!("failed to run service restart command: {e}"))?;

        if status.success() {
            Ok(())
        } else {
            Err("service restart command returned non-zero exit status".to_string())
        }
    }
    #[cfg(not(windows))]
    {
        let status = std::process::Command::new("systemctl")
            .args(["restart", "nexus"])
            .status();

        match status {
            Ok(s) if s.success() => Ok(()),
            _ => {
                let sudo_status = std::process::Command::new("sudo")
                    .args(["systemctl", "restart", "nexus"])
                    .status()
                    .map_err(|e| format!("failed to restart nexus systemd service: {e}"))?;

                if sudo_status.success() {
                    Ok(())
                } else {
                    Err("failed to restart service via systemctl or sudo".to_string())
                }
            }
        }
    }
}

/// Reads the update state file from the data directory.
pub fn read_state(data_dir: &Path) -> Option<UpdateState> {
    let path = data_dir.join(STATE_FILE_NAME);
    let bytes = fs::read(path).ok()?;
    serde_json::from_slice(&bytes).ok()
}

/// Writes the update state file to the data directory.
pub fn write_state(data_dir: &Path, state: &UpdateState) {
    let path = data_dir.join(STATE_FILE_NAME);
    if let Ok(bytes) = serde_json::to_vec_pretty(state) {
        let _ = fs::write(path, bytes);
    }
}

/// Validates startup health and handles automatic rollback if startup fails 3 times (FR-65).
pub fn check_startup_health(data_dir: &Path, current_exe: &Path, _own_version: &str) {
    let Some(mut state) = read_state(data_dir) else {
        return;
    };

    if state.status == "pending_verification" {
        state.attempts += 1;
        if state.attempts >= MAX_STARTUP_ATTEMPTS {
            eprintln!(
                "Nexus: Update to {} failed {} times. Initiating rollback to {}...",
                state.target_version, state.attempts, state.previous_version
            );
            let old_backup = current_exe.with_extension("old");
            if old_backup.exists() {
                let _ = fs::remove_file(current_exe);
                if fs::rename(&old_backup, current_exe).is_ok() {
                    eprintln!("Nexus: Successfully restored previous executable. Restarting...");
                    state.status = "rolled_back".to_string();
                    write_state(data_dir, &state);
                    let _ = restart_service();
                    std::process::exit(0);
                } else {
                    eprintln!("Nexus: Critical - failed to restore backup executable.");
                }
            }
        } else {
            write_state(data_dir, &state);
        }
    } else if state.status == "verified" {
        // Clean up backup if verified
        let old_backup = current_exe.with_extension("old");
        if old_backup.exists() {
            let _ = fs::remove_file(old_backup);
        }
    }
}

/// Marks the current update as verified and stabilized after successful operation.
pub fn mark_verified(data_dir: &Path, current_exe: &Path) {
    if let Some(mut state) = read_state(data_dir)
        && state.status == "pending_verification"
    {
        state.status = "verified".to_string();
        write_state(data_dir, &state);
        let old_backup = current_exe.with_extension("old");
        if old_backup.exists() {
            let _ = fs::remove_file(old_backup);
        }
    }
}

/// Executes a complete check, download, verification, and atomic swap cycle.
pub async fn check_and_apply_update(
    data_dir: &Path,
    current_version: &str,
    target_exe: &Path,
    restart_after: bool,
) -> Result<Option<String>, String> {
    let data_dir = data_dir.to_path_buf();
    let current_version = current_version.to_string();
    let target_exe = target_exe.to_path_buf();

    tokio::task::spawn_blocking(move || {
        let release = fetch_latest_release()?;
        let latest_ver = release.tag_name.trim_start_matches('v');

        if !is_newer(latest_ver, &current_version) {
            return Ok(None);
        }

        // Find SHA256SUMS asset
        let checksums_asset = release
            .assets
            .iter()
            .find(|a| a.name.eq_ignore_ascii_case("SHA256SUMS"))
            .ok_or_else(|| "release is missing SHA256SUMS asset".to_string())?;

        let checksums_text = fetch_text(&checksums_asset.browser_download_url)?;
        let checksums = parse_checksums(&checksums_text);

        // Find matching binary asset for current platform
        let target_asset = release
            .assets
            .iter()
            .find(|a| target_asset_matches(&a.name))
            .ok_or_else(|| {
                format!(
                    "no matching asset for current platform in release {}",
                    release.tag_name
                )
            })?;

        let expected_hash = checksums
            .get(&target_asset.name)
            .ok_or_else(|| format!("hash for {} not found in SHA256SUMS", target_asset.name))?;

        let downloaded_file = data_dir.join(&target_asset.name);
        download_and_verify(
            &target_asset.browser_download_url,
            expected_hash,
            &downloaded_file,
        )?;

        // Set pending verification state before swap
        let state = UpdateState {
            status: "pending_verification".to_string(),
            target_version: latest_ver.to_string(),
            previous_version: current_version.clone(),
            attempts: 0,
            updated_at: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
        };
        write_state(&data_dir, &state);

        apply_binary_swap(&target_exe, &downloaded_file)?;

        if restart_after {
            let _ = restart_service();
        }

        Ok(Some(latest_ver.to_string()))
    })
    .await
    .map_err(|e| format!("update task failed: {e}"))?
}

/// Spawns the recurring background auto-update worker (every 6 hours).
pub fn spawn_background_updater(
    data_dir: PathBuf,
    current_version: String,
    current_exe: PathBuf,
    stop: Arc<Notify>,
) {
    tokio::spawn(async move {
        // Initial delay of 3 minutes after service boot before checking updates
        tokio::time::sleep(Duration::from_secs(180)).await;

        let mut interval = tokio::time::interval(Duration::from_secs(6 * 3600)); // Every 6 hours
        loop {
            tokio::select! {
                _ = stop.notified() => break,
                _ = interval.tick() => {
                    match check_and_apply_update(&data_dir, &current_version, &current_exe, true).await {
                        Ok(Some(ver)) => {
                            eprintln!("Nexus: Successfully updated to v{ver} and triggered service restart.");
                            break;
                        }
                        Ok(None) => {
                            // Agent is up to date
                        }
                        Err(e) => {
                            eprintln!("Nexus: Auto-update check error: {e}");
                        }
                    }
                }
            }
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version_parts_and_is_newer() {
        assert!(is_newer("0.2.2", "0.2.1"));
        assert!(is_newer("v0.10.0", "0.9.5"));
        assert!(is_newer("1.0.0", "0.99.9"));
        assert!(!is_newer("0.2.1", "0.2.1"));
        assert!(!is_newer("0.2.0", "0.2.1"));
        assert!(!is_newer("v0.2.1", "0.2.1"));
    }

    #[test]
    fn test_parse_checksums() {
        let content = r#"
# Release SHA-256 Checksums
b24e52c42319ebb5ac39cce807b7bced3ae247e7f187cb471f19adfc423a85d2  nexus-0.2.1-linux-aarch64.run
02a462389f54a06ce01d14f2474eae79615dc39b37f9100a95f085f7d4997798 *nexus-0.2.1-linux-x86_64.run
D3F329206E305BCE0F0E4F3EC85020D1E508D800A4D8F24283FFA0B09607C2A3  nexus-setup-0.2.1.exe
"#;
        let map = parse_checksums(content);
        assert_eq!(map.len(), 3);
        assert_eq!(
            map.get("nexus-0.2.1-linux-aarch64.run").unwrap(),
            "b24e52c42319ebb5ac39cce807b7bced3ae247e7f187cb471f19adfc423a85d2"
        );
        assert_eq!(
            map.get("nexus-0.2.1-linux-x86_64.run").unwrap(),
            "02a462389f54a06ce01d14f2474eae79615dc39b37f9100a95f085f7d4997798"
        );
        assert_eq!(
            map.get("nexus-setup-0.2.1.exe").unwrap(),
            "d3f329206e305bce0f0e4f3ec85020d1e508d800a4d8f24283ffa0b09607c2a3"
        );
    }

    #[test]
    fn test_target_asset_matches() {
        #[cfg(all(windows, target_arch = "x86_64"))]
        {
            assert!(target_asset_matches("nexus.exe"));
            assert!(target_asset_matches("nexus-windows-x86_64.exe"));
            assert!(target_asset_matches("nexus-setup-0.2.1.exe"));
            assert!(!target_asset_matches("nexus-linux-x86_64"));
        }
        #[cfg(all(target_os = "linux", target_arch = "x86_64"))]
        {
            assert!(target_asset_matches("nexus-linux-x86_64"));
            assert!(target_asset_matches("nexus-0.2.1-linux-x86_64.run"));
            assert!(!target_asset_matches("nexus-linux-aarch64"));
        }
    }

    #[test]
    fn test_state_serialization() {
        let temp = std::env::temp_dir().join(format!("nexus_test_state_{}", std::process::id()));
        let _ = fs::create_dir_all(&temp);

        let state = UpdateState {
            status: "pending_verification".to_string(),
            target_version: "0.2.2".to_string(),
            previous_version: "0.2.1".to_string(),
            attempts: 1,
            updated_at: 123456789,
        };

        write_state(&temp, &state);
        let read = read_state(&temp).expect("must read back state");
        assert_eq!(state, read);

        let _ = fs::remove_dir_all(&temp);
    }
}
