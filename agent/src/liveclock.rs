//! Live CPU clock on Windows.
//!
//! sysinfo on Windows reports each core's *base* clock (from the registry), so a laptop that
//! boosts to 4 GHz still reads "2.9 GHz". Task Manager shows the real speed as
//! base clock × "% Processor Performance" (a PDH counter that goes above 100 % while boosting).
//! This does the same. Linux already gets live clocks from cpufreq through sysinfo.
//!
//! PDH API: PdhOpenQueryW, PdhAddEnglishCounterW, PdhCollectQueryData,
//! PdhGetFormattedCounterValue (learn.microsoft.com, Win32/Performance Counters).

pub struct LiveClock {
    #[cfg(windows)]
    inner: Option<imp::Query>,
}

impl LiveClock {
    pub fn open() -> Self {
        #[cfg(windows)]
        {
            let inner = imp::Query::open();
            if inner.is_none() {
                crate::say!("live clock: performance counter unavailable, sending base clock");
            }
            Self { inner }
        }
        #[cfg(not(windows))]
        Self {}
    }

    /// Scale per-core base clocks (MHz) to live clocks. Unchanged when the counter is missing.
    pub fn apply(&self, clocks: &mut [f64]) {
        #[cfg(windows)]
        if let Some(factor) = self.inner.as_ref().and_then(imp::Query::factor) {
            for c in clocks.iter_mut() {
                *c = (*c * factor).round();
            }
        }
        #[cfg(not(windows))]
        let _ = clocks;
    }
}

#[cfg(windows)]
mod imp {
    use windows_sys::Win32::System::Performance::{
        PDH_FMT_COUNTERVALUE, PDH_FMT_DOUBLE, PdhAddEnglishCounterW, PdhCloseQuery,
        PdhCollectQueryData, PdhGetFormattedCounterValue, PdhOpenQueryW,
    };

    const COUNTER: &str = r"\Processor Information(_Total)\% Processor Performance";

    pub struct Query {
        query: isize,
        counter: isize,
    }

    impl Query {
        pub fn open() -> Option<Self> {
            let path: Vec<u16> = COUNTER.encode_utf16().chain(std::iter::once(0)).collect();
            let mut query: isize = 0;
            let mut counter: isize = 0;
            unsafe {
                if PdhOpenQueryW(std::ptr::null(), 0, &mut query) != 0 {
                    return None;
                }
                if PdhAddEnglishCounterW(query, path.as_ptr(), 0, &mut counter) != 0 {
                    PdhCloseQuery(query);
                    return None;
                }
                // Rate counters need two samples; this is the first.
                PdhCollectQueryData(query);
            }
            Some(Self { query, counter })
        }

        /// "% Processor Performance" / 100 since the previous call, e.g. 1.22 while boosting.
        pub fn factor(&self) -> Option<f64> {
            unsafe {
                if PdhCollectQueryData(self.query) != 0 {
                    return None;
                }
                let mut value: PDH_FMT_COUNTERVALUE = std::mem::zeroed();
                if PdhGetFormattedCounterValue(self.counter, PDH_FMT_DOUBLE, std::ptr::null_mut(), &mut value) != 0 {
                    return None;
                }
                let pct = value.Anonymous.doubleValue;
                // Sanity: 5 %–300 % of base clock.
                (5.0..=300.0).contains(&pct).then_some(pct / 100.0)
            }
        }
    }

    impl Drop for Query {
        fn drop(&mut self) {
            unsafe {
                PdhCloseQuery(self.query);
            }
        }
    }
}
