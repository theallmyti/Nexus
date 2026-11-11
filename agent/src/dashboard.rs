//! Container counts from the home-server dashboard (`GET /api/system`, its `docker_summary`).
//! Used so the agent does not need the Docker socket, which is root-equivalent.

use serde::Deserialize;
use std::time::Duration;

#[derive(Debug, Deserialize, Clone, Copy)]
pub struct DockerSummary {
    pub running: u32,
    pub stopped: u32,
    /// The dashboard calls this `error`.
    pub error: u32,
}

#[derive(Deserialize)]
struct SystemSnapshot {
    docker_summary: DockerSummary,
}

/// Validate dashboard URL to prevent SSRF against cloud metadata or non-HTTP protocols.
pub fn is_allowed_dashboard_url(url: &str) -> bool {
    let lower = url.trim().to_lowercase();
    if !lower.starts_with("http://") && !lower.starts_with("https://") {
        return false;
    }
    // Block cloud instance metadata services (AWS, GCP, Azure, DigitalOcean)
    if lower.contains("169.254.169.254")
        || lower.contains("metadata.google.internal")
        || lower.contains("fd00:ec2::254")
    {
        return false;
    }
    true
}

/// Fetch the container counts. Short timeout: the caller is the live loop.
pub fn docker_summary(url: &str) -> Result<DockerSummary, String> {
    if !is_allowed_dashboard_url(url) {
        return Err("disallowed dashboard URL (SSRF protection)".to_string());
    }
    let mut resp = ureq::get(url)
        .config()
        .timeout_global(Some(Duration::from_secs(2)))
        .build()
        .call()
        .map_err(|e| e.to_string())?;
    let snap: SystemSnapshot = resp.body_mut().read_json().map_err(|e| e.to_string())?;
    Ok(snap.docker_summary)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_allowed_dashboard_url() {
        assert!(is_allowed_dashboard_url("http://localhost:8000/api/system"));
        assert!(is_allowed_dashboard_url("http://127.0.0.1:8000/api/system"));
        assert!(is_allowed_dashboard_url("https://my-nas.local/api/system"));
        // Metadata / SSRF blocked
        assert!(!is_allowed_dashboard_url(
            "http://169.254.169.254/latest/meta-data"
        ));
        assert!(!is_allowed_dashboard_url(
            "http://metadata.google.internal/computeMetadata/v1/"
        ));
        assert!(!is_allowed_dashboard_url("file:///etc/passwd"));
        assert!(!is_allowed_dashboard_url("gopher://127.0.0.1:25/"));
    }
}
