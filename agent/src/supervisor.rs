//! Collector supervisor with isolated failure handling and backoff (NFR-07).
//!
//! Tracks health across collectors (CPU, memory, CPU temp, GPU, battery, disks, docker).
//! Failing collectors are restarted with exponential backoff (2 s to 60 s).
//! Health status ("ok" | "degraded" | "failed") is reported in summaries (docs/Database.md).

use std::collections::BTreeMap;
use std::time::{Duration, Instant};

const MIN_BACKOFF_SECS: u64 = 2;
const MAX_BACKOFF_SECS: u64 = 60;
const FAILURES_FOR_FAILED: u32 = 3;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CollectorStatus {
    Ok,
    Degraded,
    Failed,
}

impl CollectorStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            CollectorStatus::Ok => "ok",
            CollectorStatus::Degraded => "degraded",
            CollectorStatus::Failed => "failed",
        }
    }
}

pub struct CollectorEntry {
    #[allow(dead_code)]
    pub name: &'static str,
    pub status: CollectorStatus,
    pub consecutive_failures: u32,
    pub next_retry: Instant,
    pub backoff_secs: u64,
}

impl CollectorEntry {
    pub fn new(name: &'static str) -> Self {
        Self {
            name,
            status: CollectorStatus::Ok,
            consecutive_failures: 0,
            next_retry: Instant::now(),
            backoff_secs: MIN_BACKOFF_SECS,
        }
    }

    pub fn can_run(&self) -> bool {
        Instant::now() >= self.next_retry
    }

    pub fn record_success(&mut self) {
        self.status = CollectorStatus::Ok;
        self.consecutive_failures = 0;
        self.backoff_secs = MIN_BACKOFF_SECS;
    }

    pub fn record_failure(&mut self) {
        self.consecutive_failures += 1;
        self.next_retry = Instant::now() + Duration::from_secs(self.backoff_secs);
        self.backoff_secs = (self.backoff_secs * 2).clamp(MIN_BACKOFF_SECS, MAX_BACKOFF_SECS);

        if self.consecutive_failures >= FAILURES_FOR_FAILED {
            self.status = CollectorStatus::Failed;
        } else {
            self.status = CollectorStatus::Degraded;
        }
    }
}

#[derive(Default)]
pub struct Supervisor {
    collectors: BTreeMap<&'static str, CollectorEntry>,
}

impl Supervisor {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn register(&mut self, name: &'static str) {
        self.collectors
            .entry(name)
            .or_insert_with(|| CollectorEntry::new(name));
    }

    pub fn can_run(&self, name: &'static str) -> bool {
        self.collectors
            .get(name)
            .is_none_or(|entry| entry.can_run())
    }

    pub fn record_success(&mut self, name: &'static str) {
        self.register(name);
        if let Some(entry) = self.collectors.get_mut(name) {
            entry.record_success();
        }
    }

    pub fn record_failure(&mut self, name: &'static str) {
        self.register(name);
        if let Some(entry) = self.collectors.get_mut(name) {
            entry.record_failure();
        }
    }

    #[allow(dead_code)]
    pub fn status(&self, name: &'static str) -> Option<CollectorStatus> {
        self.collectors.get(name).map(|e| e.status)
    }

    pub fn status_map(&self) -> BTreeMap<String, String> {
        self.collectors
            .iter()
            .map(|(k, v)| (k.to_string(), v.status.as_str().to_string()))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn records_success_as_ok() {
        let mut sup = Supervisor::new();
        sup.register("cpu");
        assert_eq!(sup.status("cpu"), Some(CollectorStatus::Ok));
        sup.record_success("cpu");
        assert_eq!(sup.status("cpu"), Some(CollectorStatus::Ok));
    }

    #[test]
    fn transitions_to_degraded_then_failed_with_backoff() {
        let mut sup = Supervisor::new();
        sup.register("gpu");

        sup.record_failure("gpu");
        assert_eq!(sup.status("gpu"), Some(CollectorStatus::Degraded));

        sup.record_failure("gpu");
        assert_eq!(sup.status("gpu"), Some(CollectorStatus::Degraded));

        sup.record_failure("gpu");
        assert_eq!(sup.status("gpu"), Some(CollectorStatus::Failed));

        let map = sup.status_map();
        assert_eq!(map.get("gpu").map(|s| s.as_str()), Some("failed"));
    }

    #[test]
    fn recovers_to_ok_on_success() {
        let mut sup = Supervisor::new();
        sup.register("battery");
        sup.record_failure("battery");
        sup.record_failure("battery");
        sup.record_failure("battery");
        assert_eq!(sup.status("battery"), Some(CollectorStatus::Failed));

        sup.record_success("battery");
        assert_eq!(sup.status("battery"), Some(CollectorStatus::Ok));
    }

    #[test]
    fn can_run_respects_backoff() {
        let mut sup = Supervisor::new();
        sup.register("docker");
        assert!(sup.can_run("docker"));

        sup.record_failure("docker");
        // Immediately after failure, backoff is at least 2 s in the future
        assert!(!sup.can_run("docker"));
    }
}
