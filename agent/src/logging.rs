//! Log lines go to the terminal, or to a file when running as a Windows service (no console).

#[cfg(windows)]
use std::fs::File;
#[cfg(windows)]
use std::fs::{self, OpenOptions};
#[cfg(windows)]
use std::io::Write;
#[cfg(windows)]
use std::path::{Path, PathBuf};
#[cfg(windows)]
use std::sync::{Mutex, OnceLock};
#[cfg(windows)]
use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(windows)]
const MAX_BYTES: u64 = 5 * 1024 * 1024;

#[cfg(windows)]
struct FileLogger {
    path: PathBuf,
    file: Option<File>,
    bytes_written: u64,
}

#[cfg(windows)]
impl FileLogger {
    fn write_line(&mut self, line: &str, is_error: bool) {
        let secs = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let prefix = if is_error { "ERROR " } else { "" };
        let formatted = format!("{secs} {prefix}{line}\n");
        let line_len = formatted.len() as u64;

        if self.bytes_written + line_len > MAX_BYTES {
            // Close file handle first so Windows allows rename
            if let Some(mut f) = self.file.take() {
                let _ = f.flush();
                drop(f);
            }
            let old_path = self.path.with_extension("log.old");
            let _ = fs::remove_file(&old_path);
            let _ = fs::rename(&self.path, &old_path);
            self.file = OpenOptions::new()
                .create(true)
                .append(true)
                .open(&self.path)
                .ok();
            self.bytes_written = self.path.metadata().map(|m| m.len()).unwrap_or(0);
        }

        if let Some(f) = self.file.as_mut()
            && f.write_all(formatted.as_bytes()).is_ok()
        {
            let _ = f.flush();
            self.bytes_written += line_len;
        }
    }
}

#[cfg(windows)]
static LOGGER: OnceLock<Mutex<FileLogger>> = OnceLock::new();

/// Send all further log lines to `path` (Windows service only; Linux logs to the terminal).
#[cfg(windows)]
pub fn to_file(path: &Path) -> std::io::Result<()> {
    let existing_len = fs::metadata(path).map(|m| m.len()).unwrap_or(0);
    if existing_len > MAX_BYTES {
        let old_path = path.with_extension("log.old");
        let _ = fs::remove_file(&old_path);
        let _ = fs::rename(path, &old_path);
    }
    let file = OpenOptions::new().create(true).append(true).open(path)?;
    let bytes_written = fs::metadata(path).map(|m| m.len()).unwrap_or(0);
    let _ = LOGGER.set(Mutex::new(FileLogger {
        path: path.to_path_buf(),
        file: Some(file),
        bytes_written,
    }));
    Ok(())
}

pub fn write(line: &str, is_error: bool) {
    #[cfg(windows)]
    if let Some(logger) = LOGGER.get()
        && let Ok(mut l) = logger.lock()
    {
        l.write_line(line, is_error);
        return;
    }
    if is_error {
        eprintln!("{line}");
    } else {
        println!("{line}");
    }
}

/// Normal log line.
#[macro_export]
macro_rules! say {
    ($($arg:tt)*) => { $crate::logging::write(&format!($($arg)*), false) };
}

/// Error log line.
#[macro_export]
macro_rules! warn {
    ($($arg:tt)*) => { $crate::logging::write(&format!($($arg)*), true) };
}

#[cfg(all(test, windows))]
mod tests {
    use super::*;

    #[test]
    #[cfg(windows)]
    fn test_file_logger_rotation() {
        let temp_dir = std::env::temp_dir().join(format!(
            "nexus_log_test_{}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let _ = fs::create_dir_all(&temp_dir);
        let log_file = temp_dir.join("test.log");

        let mut logger = FileLogger {
            path: log_file.clone(),
            file: OpenOptions::new()
                .create(true)
                .append(true)
                .open(&log_file)
                .ok(),
            bytes_written: MAX_BYTES - 10,
        };

        logger.write_line("exceeds", false);

        assert!(log_file.exists());
        assert!(temp_dir.join("test.log.old").exists());

        let _ = fs::remove_dir_all(&temp_dir);
    }
}
