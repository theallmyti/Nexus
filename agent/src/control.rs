//! Local control and status IPC via JSON files in data_dir (%ProgramData%\Nexus).
//! Bridges the background service (Session 0) and the user desktop tray (Session 1).
#![cfg_attr(not(windows), allow(dead_code))]

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
pub struct PauseState {
    pub paused: bool,
    pub until: Option<u64>,
    pub reason: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct AgentStatus {
    pub state: String, // "active" or "paused"
    pub cpu_util: f64,
    pub ram_pct: f64,
    pub temp_c: Option<f64>,
    pub paused_until: Option<u64>,
    pub last_tick: u64,
    pub convex_url: Option<String>,
}

pub fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

pub const MAX_PAUSE_SECS: u64 = 24 * 60 * 60; // Max 24 hours pause policy

/// Read control.json. If a timed pause has expired, automatically clears the pause.
pub fn read_control(data_dir: &Path) -> PauseState {
    let path = data_dir.join("control.json");
    let content = match fs::read_to_string(&path) {
        Ok(c) => c,
        Err(_) => return PauseState::default(),
    };

    let state: PauseState = match serde_json::from_str(&content) {
        Ok(s) => s,
        Err(_) => return PauseState::default(),
    };

    if state.paused
        && let Some(until) = state.until
        && now_secs() >= until
    {
        // Expired: auto-resume
        clear_pause(data_dir);
        return PauseState::default();
    }

    state
}

/// Set pause state with an optional duration in seconds (None or excessive values capped at MAX_PAUSE_SECS).
pub fn set_pause(data_dir: &Path, duration_secs: Option<u64>, reason: &str) {
    let duration = duration_secs.unwrap_or(MAX_PAUSE_SECS).min(MAX_PAUSE_SECS);
    let until = Some(now_secs() + duration);
    let state = PauseState {
        paused: true,
        until,
        reason: Some(reason.to_string()),
    };
    if let Ok(json) = serde_json::to_string_pretty(&state) {
        let _ = fs::write(data_dir.join("control.json"), json);
    }
}

/// Clear pause state (resume logging).
pub fn clear_pause(data_dir: &Path) {
    let state = PauseState {
        paused: false,
        until: None,
        reason: None,
    };
    if let Ok(json) = serde_json::to_string_pretty(&state) {
        let _ = fs::write(data_dir.join("control.json"), json);
    }
}

/// Write status.json for the tray icon to read.
pub fn write_status(data_dir: &Path, status: &AgentStatus) {
    if let Ok(json) = serde_json::to_string(status) {
        let _ = fs::write(data_dir.join("status.json"), json);
    }
}

/// Read status.json from data_dir.
pub fn read_status(data_dir: &Path) -> Option<AgentStatus> {
    let path = data_dir.join("status.json");
    let content = fs::read_to_string(path).ok()?;
    serde_json::from_str(&content).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pause_expiration() {
        let temp = std::env::temp_dir().join(format!("nexus_test_{}", now_secs()));
        let _ = fs::create_dir_all(&temp);

        // Pause for 2 seconds to avoid subsecond boundary rollover
        set_pause(&temp, Some(2), "test");
        let s = read_control(&temp);
        assert!(s.paused);
        assert!(s.until.is_some());

        // Wait 2.1s for expiration
        std::thread::sleep(std::time::Duration::from_millis(2100));
        let s2 = read_control(&temp);
        assert!(!s2.paused);

        let _ = fs::remove_dir_all(&temp);
    }

    #[test]
    fn test_clear_pause() {
        let temp = std::env::temp_dir().join(format!("nexus_test_clear_{}", now_secs()));
        let _ = fs::create_dir_all(&temp);

        set_pause(&temp, None, "indefinite");
        assert!(read_control(&temp).paused);

        clear_pause(&temp);
        assert!(!read_control(&temp).paused);

        let _ = fs::remove_dir_all(&temp);
    }
}
