//! CPU package temperature through the PawnIO driver ("full mode", F8, docs/Architecture.md §2.3).
//! Needs admin: the agent runs as a Windows service (LocalSystem) when this is enabled.
//!
//! PawnIOLib API (github.com/namazso/PawnIO, PawnIOLib.h): pawnio_open, pawnio_load,
//! pawnio_execute, pawnio_close. Signed modules (github.com/namazso/PawnIO.Modules, LGPL-2.1):
//! - AMDFamily17 (families 17h–1Ah): `ioctl_read_smn` (in: SMN address, out: value)
//! - IntelMSR: `ioctl_read_msr` (in: MSR, out: value)
//!
//! Without PawnIO (or on Linux) `CpuTemp::read` returns None and the agent stays in no-driver mode.
//! The register decoding below is used by the Windows reader and tested on every platform.
#![cfg_attr(not(windows), allow(dead_code))]

/// AMD Zen: THM_TCON_CUR_TMP register in SMN space.
pub const AMD_SMN_TEMP: u64 = 0x0005_9800;
/// Intel: IA32_TEMPERATURE_TARGET (TjMax) and IA32_PACKAGE_THERM_STATUS.
pub const INTEL_MSR_TEMP_TARGET: u64 = 0x1A2;
pub const INTEL_MSR_PACKAGE_THERM_STATUS: u64 = 0x1B1;
/// Intel: IA32_THERM_STATUS, the temperature of the core the reading thread runs on.
pub const INTEL_MSR_THERM_STATUS: u64 = 0x19C;

/// AMD: bits 31:21 hold the temperature in 1/8 °C; bit 19 selects the −49 °C range.
pub fn amd_temp(raw: u64) -> f64 {
    let mut t = ((raw >> 21) & 0x7FF) as f64 / 8.0;
    if raw & 0x8_0000 != 0 {
        t -= 49.0;
    }
    t
}

/// Intel: TjMax (target bits 23:16) minus the distance to TjMax (status bits 22:16).
pub fn intel_temp(target: u64, status: u64) -> f64 {
    let tjmax = ((target >> 16) & 0xFF) as f64;
    tjmax - ((status >> 16) & 0x7F) as f64
}

/// Byte offset of the per-core temperature floats in the Ryzen SMU metrics ("PM") table, by
/// table version. Source: RyzenAdj lib/api.c `get_core_temp` (github.com/FlyGoat/RyzenAdj).
pub fn ryzen_core_temp_offset(version: u64) -> Option<usize> {
    match version {
        0x0037_0000..=0x0037_0004 => Some(0x340), // Renoir (Ryzen 4000 mobile)
        0x0037_0005 => Some(0x35C),               // Renoir, newer SMU firmware
        0x0040_0004 | 0x0040_0005 => Some(0x360), // Cezanne (Ryzen 5000 mobile)
        _ => None,
    }
}

/// Per-core temperatures from a metrics table read as little-endian 64-bit words.
/// None for an unknown table version or any implausible value (a disabled core reads 0).
pub fn ryzen_core_temps(version: u64, words: &[u64], cores: usize) -> Option<Vec<f64>> {
    let off = ryzen_core_temp_offset(version)?;
    if cores == 0 || cores > 16 {
        return None;
    }
    let bytes: Vec<u8> = words.iter().flat_map(|w| w.to_le_bytes()).collect();
    let temps = (0..cores)
        .map(|c| {
            let b = bytes.get(off + c * 4..off + c * 4 + 4)?;
            Some(f64::from(f32::from_le_bytes([b[0], b[1], b[2], b[3]])))
        })
        .collect::<Option<Vec<f64>>>()?;
    temps.iter().all(|t| (1.0..=125.0).contains(t)).then_some(temps)
}

/// Spread per-core values over logical processors. Windows numbers SMT siblings next to each
/// other (logical 0 and 1 are core 0), so logical i belongs to core i / threads-per-core.
pub fn per_logical(core_temps: &[f64], logical: usize) -> Option<Vec<f64>> {
    if core_temps.is_empty() || logical == 0 || !logical.is_multiple_of(core_temps.len()) {
        return None;
    }
    let per = logical / core_temps.len();
    Some((0..logical).map(|i| core_temps[i / per]).collect())
}

pub struct CpuTemp {
    #[cfg(windows)]
    inner: Option<imp::Reader>,
    /// AMD Ryzen per-core temperatures through PawnIO's RyzenSMU module (full mode only).
    #[cfg(windows)]
    smu: Option<imp::Smu>,
}

impl CpuTemp {
    /// Try to open PawnIO and load the module for this CPU. Never fails: without PawnIO,
    /// `available()` is false and the reason is logged.
    pub fn open() -> Self {
        #[cfg(windows)]
        {
            let inner = match imp::Reader::open() {
                Ok(r) => Some(r),
                Err(e) => {
                    crate::say!("cpu temperature: no-driver mode ({e})");
                    None
                }
            };
            // Only worth trying when PawnIO works (full mode) on an AMD CPU.
            let smu = if inner.as_ref().is_some_and(imp::Reader::is_amd) {
                match imp::Smu::open() {
                    Ok(s) => Some(s),
                    Err(e) => {
                        crate::say!("per-core temperatures: unavailable ({e})");
                        None
                    }
                }
            } else {
                None
            };
            Self { inner, smu }
        }
        #[cfg(not(windows))]
        Self {}
    }

    pub fn available(&self) -> bool {
        #[cfg(windows)]
        return self.inner.is_some();
        #[cfg(not(windows))]
        false
    }

    pub fn read(&self) -> Option<f64> {
        #[cfg(windows)]
        return self.inner.as_ref().and_then(imp::Reader::read);
        #[cfg(not(windows))]
        None
    }

    /// One temperature per logical processor (hyper-threads share their core's value).
    /// Intel only: AMD Zen exposes one die temperature, not per-core sensors.
    pub fn read_cores(&self, logical: usize) -> Option<Vec<f64>> {
        #[cfg(windows)]
        return self
            .inner
            .as_ref()
            .and_then(|r| r.read_cores(logical))
            .or_else(|| self.smu.as_ref().and_then(|s| s.read_cores(logical)));
        #[cfg(not(windows))]
        {
            let _ = logical;
            None
        }
    }
}

#[cfg(windows)]
mod imp {
    use std::ffi::{CString, c_char, c_void};
    use std::path::PathBuf;

    use libloading::Library;

    use super::*;

    type Handle = *mut c_void;
    type OpenFn = unsafe extern "system" fn(*mut Handle) -> i32;
    type LoadFn = unsafe extern "system" fn(Handle, *const u8, usize) -> i32;
    type ExecFn = unsafe extern "system" fn(
        Handle,
        *const c_char,
        *const u64,
        usize,
        *mut u64,
        usize,
        *mut usize,
    ) -> i32;
    type CloseFn = unsafe extern "system" fn(Handle) -> i32;

    enum Vendor {
        Amd,
        Intel { target: u64 },
    }

    pub struct Reader {
        _lib: Library, // keeps the function pointers below valid
        handle: Handle,
        exec: ExecFn,
        close: CloseFn,
        vendor: Vendor,
    }

    fn check(hr: i32, what: &str) -> Result<(), String> {
        if hr < 0 {
            Err(format!("{what} failed: 0x{:08X}", hr as u32))
        } else {
            Ok(())
        }
    }

    /// CPU vendor from CPUID leaf 0.
    fn is_amd() -> Result<bool, String> {
        #[cfg(target_arch = "x86_64")]
        {
            let r = std::arch::x86_64::__cpuid(0);
            let mut v = Vec::with_capacity(12);
            for reg in [r.ebx, r.edx, r.ecx] {
                v.extend_from_slice(&reg.to_le_bytes());
            }
            match v.as_slice() {
                b"AuthenticAMD" => Ok(true),
                b"GenuineIntel" => Ok(false),
                other => Err(format!(
                    "unsupported CPU vendor {}",
                    String::from_utf8_lossy(other)
                )),
            }
        }
        #[cfg(not(target_arch = "x86_64"))]
        Err("unsupported CPU architecture".to_string())
    }

    fn pawnio_lib() -> Result<PathBuf, String> {
        let installed = std::env::var_os("ProgramFiles")
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from(r"C:\Program Files"))
            .join("PawnIO")
            .join("PawnIOLib.dll");
        if installed.is_file() {
            Ok(installed)
        } else {
            Err("PawnIOLib.dll not found in Program Files".to_string())
        }
    }

    /// Modules ship next to the agent in a `pawnio` folder (installer).
    fn module_dir() -> PathBuf {
        std::env::current_exe()
            .ok()
            .and_then(|p| p.parent().map(|d| d.join("pawnio")))
            .unwrap_or_else(|| PathBuf::from("pawnio"))
    }

    impl Reader {
        pub fn open() -> Result<Self, String> {
            let amd = is_amd()?;
            let lib_path = pawnio_lib()?;
            // SAFETY: loading the official PawnIOLib from Program Files; its exports match PawnIOLib.h.
            let lib = unsafe { Library::new(lib_path) }
                .map_err(|e| format!("PawnIO not installed ({e})"))?;
            let (open, load, exec, close) = unsafe {
                (
                    *lib.get::<OpenFn>(b"pawnio_open")
                        .map_err(|e| e.to_string())?,
                    *lib.get::<LoadFn>(b"pawnio_load")
                        .map_err(|e| e.to_string())?,
                    *lib.get::<ExecFn>(b"pawnio_execute")
                        .map_err(|e| e.to_string())?,
                    *lib.get::<CloseFn>(b"pawnio_close")
                        .map_err(|e| e.to_string())?,
                )
            };
            let mut handle: Handle = std::ptr::null_mut();
            check(unsafe { open(&mut handle) }, "pawnio_open (needs admin)")?;
            let mut reader = Reader {
                _lib: lib,
                handle,
                exec,
                close,
                vendor: Vendor::Amd,
            };
            let file = if amd {
                "AMDFamily17.bin"
            } else {
                "IntelMSR.bin"
            };
            let blob = std::fs::read(module_dir().join(file))
                .map_err(|e| format!("module {file} missing ({e})"))?;
            check(
                unsafe { load(reader.handle, blob.as_ptr(), blob.len()) },
                "pawnio_load",
            )?;
            if !amd {
                let target = reader.execute("ioctl_read_msr", INTEL_MSR_TEMP_TARGET)?;
                reader.vendor = Vendor::Intel { target };
            }
            reader
                .read()
                .ok_or_else(|| "first reading out of range".to_string())?;
            Ok(reader)
        }

        fn execute(&self, name: &str, arg: u64) -> Result<u64, String> {
            let fname = CString::new(name).map_err(|e| e.to_string())?;
            let input = [arg];
            let mut out = [0u64; 1];
            let mut written = 0usize;
            check(
                unsafe {
                    (self.exec)(
                        self.handle,
                        fname.as_ptr(),
                        input.as_ptr(),
                        1,
                        out.as_mut_ptr(),
                        1,
                        &mut written,
                    )
                },
                name,
            )?;
            if written < 1 {
                return Err(format!("{name} returned nothing"));
            }
            Ok(out[0])
        }

        pub fn read(&self) -> Option<f64> {
            let t = match self.vendor {
                Vendor::Amd => amd_temp(self.execute("ioctl_read_smn", AMD_SMN_TEMP).ok()?),
                Vendor::Intel { target } => intel_temp(
                    target,
                    self.execute("ioctl_read_msr", INTEL_MSR_PACKAGE_THERM_STATUS)
                        .ok()?,
                ),
            };
            // FR-28: impossible values are dropped.
            (0.0..=125.0).contains(&t).then_some(t)
        }
    }

    impl Reader {
        pub fn is_amd(&self) -> bool {
            matches!(self.vendor, Vendor::Amd)
        }

        /// Pins this thread to each logical processor in turn and reads that core's thermal
        /// status MSR, then restores the thread's previous affinity. Up to 64 processors
        /// (one processor group), which covers consumer CPUs.
        pub fn read_cores(&self, logical: usize) -> Option<Vec<f64>> {
            use windows_sys::Win32::System::Threading::{GetCurrentThread, SetThreadAffinityMask};
            let Vendor::Intel { target } = self.vendor else {
                return None;
            };
            if logical == 0 || logical > 64 {
                return None;
            }
            let thread = unsafe { GetCurrentThread() };
            let mut temps = Vec::with_capacity(logical);
            let mut previous = 0usize;
            for cpu in 0..logical {
                let prev = unsafe { SetThreadAffinityMask(thread, 1usize << cpu) };
                if prev == 0 {
                    break; // processor not available to this process
                }
                if cpu == 0 {
                    previous = prev;
                }
                std::thread::yield_now(); // make sure we are running on the new processor
                let Ok(status) = self.execute("ioctl_read_msr", INTEL_MSR_THERM_STATUS) else {
                    break;
                };
                let t = intel_temp(target, status);
                if !(0.0..=125.0).contains(&t) {
                    break;
                }
                temps.push(t);
            }
            if previous != 0 {
                unsafe { SetThreadAffinityMask(thread, previous) };
            }
            (temps.len() == logical).then_some(temps)
        }
    }

    /// PawnIO RyzenSMU module: reads the SMU metrics table, which holds per-core temperatures.
    /// Every SMU access holds the system-wide "Access_PCI" mutex (as the module requires), the
    /// same lock HWiNFO and LibreHardwareMonitor take, so tools never talk to the SMU at once.
    pub struct Smu {
        _lib: Library,
        handle: Handle,
        exec: ExecFn,
        close: CloseFn,
        mutex: windows_sys::Win32::Foundation::HANDLE,
        version: u64,
    }

    impl Smu {
        pub fn open() -> Result<Self, String> {
            use windows_sys::Win32::System::Threading::CreateMutexW;
            let lib = unsafe { Library::new(pawnio_lib()?) }.map_err(|e| e.to_string())?;
            let (open, load, exec, close) = unsafe {
                (
                    *lib.get::<OpenFn>(b"pawnio_open").map_err(|e| e.to_string())?,
                    *lib.get::<LoadFn>(b"pawnio_load").map_err(|e| e.to_string())?,
                    *lib.get::<ExecFn>(b"pawnio_execute").map_err(|e| e.to_string())?,
                    *lib.get::<CloseFn>(b"pawnio_close").map_err(|e| e.to_string())?,
                )
            };
            let mut handle: Handle = std::ptr::null_mut();
            check(unsafe { open(&mut handle) }, "pawnio_open")?;
            let name: Vec<u16> = r"Global\Access_PCI".encode_utf16().chain(std::iter::once(0)).collect();
            let mutex = unsafe { CreateMutexW(std::ptr::null(), 0, name.as_ptr()) };
            let mut smu = Smu { _lib: lib, handle, exec, close, mutex, version: 0 };
            let blob = std::fs::read(module_dir().join("RyzenSMU.bin"))
                .map_err(|e| format!("module RyzenSMU.bin missing ({e})"))?;
            check(unsafe { load(smu.handle, blob.as_ptr(), blob.len()) }, "pawnio_load RyzenSMU")?;
            let mut out = [0u64; 2];
            smu.locked(|s| s.call("ioctl_resolve_pm_table", &[], &mut out))
                .ok_or("SMU busy")??;
            smu.version = out[0];
            if ryzen_core_temp_offset(smu.version).is_none() {
                return Err(format!("metrics table version 0x{:X} not known", smu.version));
            }
            Ok(smu)
        }

        fn call(&self, name: &str, input: &[u64], out: &mut [u64]) -> Result<usize, String> {
            let fname = CString::new(name).map_err(|e| e.to_string())?;
            let mut written = 0usize;
            check(
                unsafe {
                    (self.exec)(
                        self.handle,
                        fname.as_ptr(),
                        input.as_ptr(),
                        input.len(),
                        out.as_mut_ptr(),
                        out.len(),
                        &mut written,
                    )
                },
                name,
            )?;
            Ok(written)
        }

        /// Runs `f` while holding Access_PCI; None if another tool holds it for more than 200 ms.
        fn locked<T>(&self, f: impl FnOnce(&Self) -> T) -> Option<T> {
            use windows_sys::Win32::Foundation::{WAIT_ABANDONED, WAIT_OBJECT_0};
            use windows_sys::Win32::System::Threading::{ReleaseMutex, WaitForSingleObject};
            if self.mutex == 0 {
                return Some(f(self));
            }
            let w = unsafe { WaitForSingleObject(self.mutex, 200) };
            if w != WAIT_OBJECT_0 && w != WAIT_ABANDONED {
                return None;
            }
            let r = f(self);
            unsafe { ReleaseMutex(self.mutex) };
            Some(r)
        }

        pub fn read_cores(&self, logical: usize) -> Option<Vec<f64>> {
            let off = ryzen_core_temp_offset(self.version)?;
            let mut words = vec![0u64; (off + 16 * 4) / 8 + 1];
            self.locked(|s| {
                s.call("ioctl_update_pm_table", &[], &mut [])?;
                s.call("ioctl_read_pm_table", &[], &mut words)
            })?
            .ok()?;
            // SMT parts have half as many cores as logical processors; otherwise one each.
            [logical / 2, logical]
                .into_iter()
                .find_map(|cores| ryzen_core_temps(self.version, &words, cores))
                .and_then(|t| per_logical(&t, logical))
        }
    }

    impl Drop for Smu {
        fn drop(&mut self) {
            unsafe {
                (self.close)(self.handle);
                windows_sys::Win32::Foundation::CloseHandle(self.mutex);
            }
        }
    }

    impl Drop for Reader {
        fn drop(&mut self) {
            unsafe {
                (self.close)(self.handle);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn amd_temperature_decoding() {
        // 60 °C = 480 eighths in bits 31:21.
        assert_eq!(amd_temp(480 << 21), 60.0);
        // Same raw value with the −49 °C range bit (19) set.
        assert_eq!(amd_temp((480 << 21) | 0x8_0000), 11.0);
        // Fractions: 60.125 °C.
        assert_eq!(amd_temp(481 << 21), 60.125);
    }

    #[test]
    fn ryzen_per_core_temperatures() {
        // Renoir table 0x370003: 8 floats at byte 0x340.
        let mut bytes = vec![0u8; 0x400];
        let temps = [61.5f32, 62.0, 60.25, 63.0, 59.5, 60.0, 61.0, 62.5];
        for (i, t) in temps.iter().enumerate() {
            bytes[0x340 + i * 4..0x344 + i * 4].copy_from_slice(&t.to_le_bytes());
        }
        let words: Vec<u64> = bytes
            .chunks(8)
            .map(|c| u64::from_le_bytes(c.try_into().unwrap()))
            .collect();
        let got = ryzen_core_temps(0x370003, &words, 8).unwrap();
        assert_eq!(got[0], 61.5);
        assert_eq!(got[7], 62.5);
        // 16 "cores" would read zeros past the 8 real ones: rejected, so the caller tries 8.
        assert!(ryzen_core_temps(0x370003, &words, 16).is_none());
        // Unknown table version: nothing.
        assert!(ryzen_core_temps(0x123456, &words, 8).is_none());
        // Newer Renoir firmware uses a different offset.
        assert_eq!(ryzen_core_temp_offset(0x370005), Some(0x35C));
    }

    #[test]
    fn spread_over_logical_processors() {
        assert_eq!(per_logical(&[50.0, 60.0], 4), Some(vec![50.0, 50.0, 60.0, 60.0]));
        assert_eq!(per_logical(&[50.0, 60.0], 2), Some(vec![50.0, 60.0]));
        assert_eq!(per_logical(&[50.0, 60.0], 3), None);
    }

    #[test]
    fn intel_temperature_decoding() {
        // TjMax 100 °C, 40 °C below it → 60 °C.
        assert_eq!(intel_temp(100 << 16, 40 << 16), 60.0);
        // Other bits in the registers are ignored.
        assert_eq!(
            intel_temp((100 << 16) | 0xFF, (40 << 16) | 0x8000_0001),
            60.0
        );
    }
}
