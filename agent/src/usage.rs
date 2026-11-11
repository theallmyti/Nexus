//! System usage tracking: uptime, active time today, boot/sleep log (FR-24).
//!
//! - Boot time: from `sysinfo::System::boot_time()`.
//! - Uptime: from `sysinfo::System::uptime()`.
//! - Active time today: accumulated seconds the system was awake today.
//!   Resets at midnight. During sleep/suspend (detected when elapsed wall clock
//!   jumps between ticks), sleep time is excluded from active time.
//! - Boot/sleep events: bounded ring buffer of the most recent 20 events
//!   ("boot", "sleep", "wake") with timestamps.

use std::collections::VecDeque;
use std::time::{Instant, SystemTime, UNIX_EPOCH};

pub const MAX_EVENTS: usize = 20;
/// Gap between ticks that indicates the computer went to sleep/suspended.
pub const SLEEP_THRESHOLD_SECS: u64 = 5;
pub const SECS_PER_DAY: u64 = 86_400;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UsageEvent {
    pub event: String,
    pub ts: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UsageStats {
    pub boot_time: Option<u64>,
    pub uptime_secs: u64,
    pub active_time_today_sec: u64,
    pub events: Vec<UsageEvent>,
}

pub struct UsageTracker {
    boot_time: Option<u64>,
    last_tick_mono: Instant,
    last_tick_wall: u64,
    current_day: u64,
    active_time_today_sec: u64,
    events: VecDeque<UsageEvent>,
}

pub fn now_wall_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

impl UsageTracker {
    pub fn new(boot_time: Option<u64>, uptime_secs: u64, now_wall: u64) -> Self {
        let current_day = now_wall / SECS_PER_DAY;
        let seconds_today = now_wall % SECS_PER_DAY;
        let initial_active = if let Some(bt) = boot_time {
            if bt / SECS_PER_DAY == current_day {
                now_wall.saturating_sub(bt).min(uptime_secs)
            } else {
                seconds_today.min(uptime_secs)
            }
        } else {
            seconds_today.min(uptime_secs)
        };

        let mut events = VecDeque::with_capacity(MAX_EVENTS);
        if let Some(bt) = boot_time {
            events.push_back(UsageEvent {
                event: "boot".to_string(),
                ts: bt,
            });
        }

        Self {
            boot_time,
            last_tick_mono: Instant::now(),
            last_tick_wall: now_wall,
            current_day,
            active_time_today_sec: initial_active,
            events,
        }
    }

    pub fn tick(&mut self, now_wall: u64) {
        let day = now_wall / SECS_PER_DAY;
        if day != self.current_day {
            self.current_day = day;
            self.active_time_today_sec = 0;
        }

        let wall_delta = now_wall.saturating_sub(self.last_tick_wall);
        if wall_delta > SLEEP_THRESHOLD_SECS {
            // Suspended/slept: record sleep at last tick, wake at now_wall
            self.record_event("sleep", self.last_tick_wall);
            self.record_event("wake", now_wall);
            self.active_time_today_sec = (self.active_time_today_sec + 1).min(SECS_PER_DAY);
        } else if wall_delta > 0 {
            let add = wall_delta.clamp(1, SLEEP_THRESHOLD_SECS);
            self.active_time_today_sec = (self.active_time_today_sec + add).min(SECS_PER_DAY);
        }

        self.last_tick_mono = Instant::now();
        self.last_tick_wall = now_wall;
    }

    pub fn record_event(&mut self, event: &str, ts: u64) {
        if self.events.len() >= MAX_EVENTS {
            self.events.pop_front();
        }
        self.events.push_back(UsageEvent {
            event: event.to_string(),
            ts,
        });
    }

    pub fn stats(&self, uptime_secs: u64) -> UsageStats {
        UsageStats {
            boot_time: self.boot_time,
            uptime_secs,
            active_time_today_sec: self.active_time_today_sec,
            events: self.events.iter().cloned().collect(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tracker_initialises_with_boot_event_and_active_time() {
        let boot = 1_700_000_000;
        let now = boot + 3600; // 1 hour after boot
        let uptime = 3600;
        let tracker = UsageTracker::new(Some(boot), uptime, now);
        let s = tracker.stats(uptime);
        assert_eq!(s.boot_time, Some(boot));
        assert_eq!(s.active_time_today_sec, 3600);
        assert_eq!(s.events.len(), 1);
        assert_eq!(s.events[0].event, "boot");
        assert_eq!(s.events[0].ts, boot);
    }

    #[test]
    fn tracker_detects_sleep_and_wake_on_tick_gap() {
        let now = 1_700_000_000;
        let mut tracker = UsageTracker::new(Some(now - 100), 100, now);
        let initial_active = tracker.stats(100).active_time_today_sec;

        // Sleep for 3600 seconds
        let wake_time = now + 3600;
        tracker.tick(wake_time);

        let s = tracker.stats(3700);
        // Only 1 second was added for the wake tick, not 3600
        assert_eq!(s.active_time_today_sec, initial_active + 1);
        assert_eq!(s.events.len(), 3); // boot, sleep, wake
        assert_eq!(s.events[1].event, "sleep");
        assert_eq!(s.events[1].ts, now);
        assert_eq!(s.events[2].event, "wake");
        assert_eq!(s.events[2].ts, wake_time);
    }

    #[test]
    fn tracker_resets_active_time_on_day_rollover() {
        let day1_end = 86_399;
        let mut tracker = UsageTracker::new(Some(0), 86_399, day1_end);
        assert!(tracker.stats(86_399).active_time_today_sec > 0);

        // Day 2 start
        tracker.tick(86_400);
        assert_eq!(tracker.stats(86_400).active_time_today_sec, 1);
    }

    #[test]
    fn tracker_caps_events_at_max_limit() {
        let mut tracker = UsageTracker::new(Some(100), 100, 100);
        for i in 0..30 {
            tracker.record_event("test", 1000 + i);
        }
        assert_eq!(tracker.events.len(), MAX_EVENTS);
        assert_eq!(tracker.events.back().unwrap().ts, 1029);
    }
}
