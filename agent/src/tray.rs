//! Windows system tray controller for Nexus Agent.
//! Runs in the user desktop session (Session 1), displays real-time tooltip stats,
//! provides pause/resume controls, opens the log viewer, and manages service state.

#![cfg(windows)]

use std::ffi::OsStr;
use std::os::windows::ffi::OsStrExt;
use std::path::{Path, PathBuf};
use std::ptr::null;
use std::sync::Mutex;
use std::sync::atomic::{AtomicU64, Ordering};

use windows_sys::Win32::Foundation::{HWND, LPARAM, LRESULT, POINT, WPARAM};
use windows_sys::Win32::Graphics::Gdi::{CreateBitmap, DeleteObject, HBITMAP};
use windows_sys::Win32::System::Console::FreeConsole;
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::UI::Shell::{
    NIF_ICON, NIF_MESSAGE, NIF_TIP, NIM_ADD, NIM_DELETE, NIM_MODIFY, NOTIFYICONDATAW,
    Shell_NotifyIconW,
};
use windows_sys::Win32::UI::WindowsAndMessaging::{
    AppendMenuW, CreateIconIndirect, CreatePopupMenu, CreateWindowExW, DefWindowProcW, DestroyIcon,
    DestroyMenu, DispatchMessageW, GetCursorPos, GetMessageW, HICON, ICONINFO, IDI_APPLICATION,
    LoadIconW, MF_DISABLED, MF_GRAYED, MF_POPUP, MF_SEPARATOR, MF_STRING, MSG, PostQuitMessage,
    RegisterClassExW, SetForegroundWindow, SetTimer, TPM_RIGHTBUTTON, TrackPopupMenu,
    TranslateMessage, WM_COMMAND, WM_DESTROY, WM_RBUTTONUP, WM_TIMER, WM_USER, WNDCLASSEXW,
    WS_POPUP,
};

use crate::control;

const WM_TRAYICON: u32 = WM_USER + 100;
const TRAY_TIMER_ID: usize = 1;
const TRAY_UID: u32 = 1001;

// Menu Command IDs
const ID_HEADER: usize = 2001;
const ID_STATUS: usize = 2002;
const ID_PAUSE_15M: usize = 2010;
const ID_PAUSE_30M: usize = 2011;
const ID_PAUSE_1H: usize = 2012;
const ID_PAUSE_2H: usize = 2013;
const ID_PAUSE_4H: usize = 2014;
const ID_PAUSE_8H: usize = 2015;
const ID_PAUSE_CUSTOM: usize = 2016;
const ID_PAUSE_INDEF: usize = 2017;
const ID_RESUME: usize = 2020;
const ID_PAIR: usize = 2025;
const ID_OPEN_LOGS: usize = 2040;
const ID_RESTART_SERVICE: usize = 2050;
const ID_UNPAIR: usize = 2060;
const ID_EXIT: usize = 2099;

static RESTARTING_UNTIL: AtomicU64 = AtomicU64::new(0);
static UNPAIRING_UNTIL: AtomicU64 = AtomicU64::new(0);

fn to_wide(s: &str) -> Vec<u16> {
    OsStr::new(s)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect()
}

/// Generate a crisp 16x16 32-bit RGBA icon in memory.
/// `is_paused`: false = Cyan/Green dot; true = Amber/Orange dot with pause bars.
fn create_tray_hicon(is_paused: bool) -> HICON {
    let width = 16;
    let height = 16;
    let mut pixels = vec![0u32; (width * height) as usize];

    let cx = 7.5f32;
    let cy = 7.5f32;
    let r = 6.5f32;

    for y in 0..height {
        for x in 0..width {
            let dx = x as f32 - cx;
            let dy = y as f32 - cy;
            let dist = (dx * dx + dy * dy).sqrt();
            let idx = (y * width + x) as usize;

            if dist <= r {
                if dist > r - 1.2 {
                    pixels[idx] = 0xFF101820; // Dark border
                } else if is_paused {
                    let is_bar1 = (x == 6 || x == 7) && (5..=10).contains(&y);
                    let is_bar2 = (x == 9 || x == 10) && (5..=10).contains(&y);
                    if is_bar1 || is_bar2 {
                        pixels[idx] = 0xFFFFFFFF; // White pause bars
                    } else {
                        pixels[idx] = 0xFFF59E0B; // Amber background
                    }
                } else {
                    pixels[idx] = 0xFF10B981; // Emerald / Cyan circle
                }
            } else {
                pixels[idx] = 0x00000000; // Transparent
            }
        }
    }

    unsafe {
        let hbm_color: HBITMAP = CreateBitmap(width, height, 1, 32, pixels.as_ptr() as *const _);
        let mask_bytes = vec![0u8; ((width * height) / 8) as usize];
        let hbm_mask: HBITMAP = CreateBitmap(width, height, 1, 1, mask_bytes.as_ptr() as *const _);

        let icon_info = ICONINFO {
            fIcon: 1, // TRUE = icon
            xHotspot: 0,
            yHotspot: 0,
            hbmMask: hbm_mask,
            hbmColor: hbm_color,
        };

        let hicon = CreateIconIndirect(&icon_info);
        DeleteObject(hbm_color as _);
        DeleteObject(hbm_mask as _);
        hicon
    }
}

struct TrayState {
    data_dir: PathBuf,
    hicon_active: HICON,
    hicon_paused: HICON,
}

static GLOBAL_STATE: Mutex<Option<TrayState>> = Mutex::new(None);

pub fn run() {
    // Immediately detach and close any terminal/console window so tray runs silently in background.
    unsafe {
        FreeConsole();
    }

    let data_dir = crate::service::data_dir();
    let _ = std::fs::create_dir_all(&data_dir);

    unsafe {
        let hinstance = GetModuleHandleW(null());
        let class_name = to_wide("NexusAgentTrayClass");

        let wnd_class = WNDCLASSEXW {
            cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
            style: 0,
            lpfnWndProc: Some(wnd_proc),
            cbClsExtra: 0,
            cbWndExtra: 0,
            hInstance: hinstance,
            hIcon: 0,
            hCursor: 0,
            hbrBackground: 0,
            lpszMenuName: null(),
            lpszClassName: class_name.as_ptr(),
            hIconSm: 0,
        };

        RegisterClassExW(&wnd_class);

        let hwnd = CreateWindowExW(
            0,
            class_name.as_ptr(),
            to_wide("Nexus Agent Tray").as_ptr(),
            WS_POPUP,
            0,
            0,
            0,
            0,
            0,
            0,
            hinstance,
            null(),
        );

        if hwnd == 0 {
            return;
        }

        let mut hicon_active = create_tray_hicon(false);
        if hicon_active == 0 {
            hicon_active = LoadIconW(0, IDI_APPLICATION);
        }
        let mut hicon_paused = create_tray_hicon(true);
        if hicon_paused == 0 {
            hicon_paused = LoadIconW(0, IDI_APPLICATION);
        }

        {
            let mut state = GLOBAL_STATE.lock().unwrap();
            *state = Some(TrayState {
                data_dir: data_dir.clone(),
                hicon_active,
                hicon_paused,
            });
        }

        let mut nid: NOTIFYICONDATAW = std::mem::zeroed();
        nid.cbSize = std::mem::size_of::<NOTIFYICONDATAW>() as u32;
        nid.hWnd = hwnd;
        nid.uID = TRAY_UID;
        nid.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
        nid.uCallbackMessage = WM_TRAYICON;
        nid.hIcon = hicon_active;

        let initial_tip = to_wide("Nexus Agent: Active\nInitializing stats...");
        let copy_len = initial_tip.len().min(nid.szTip.len() - 1);
        nid.szTip[..copy_len].copy_from_slice(&initial_tip[..copy_len]);

        for _ in 0..5 {
            if Shell_NotifyIconW(NIM_ADD, &nid) != 0 {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(500));
        }

        SetTimer(hwnd, TRAY_TIMER_ID, 2000, None);

        let mut msg: MSG = std::mem::zeroed();
        while GetMessageW(&mut msg, 0, 0, 0) > 0 {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }

        Shell_NotifyIconW(NIM_DELETE, &nid);
        if let Some(state) = GLOBAL_STATE.lock().unwrap().take() {
            DestroyIcon(state.hicon_active);
            DestroyIcon(state.hicon_paused);
        }
    }
}

unsafe extern "system" fn wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    match msg {
        WM_TIMER => {
            if wparam == TRAY_TIMER_ID {
                update_tray(hwnd);
            }
            0
        }
        WM_TRAYICON => {
            let event = (lparam & 0xFFFF) as u32;
            if event == WM_RBUTTONUP {
                show_context_menu(hwnd);
            }
            0
        }
        WM_COMMAND => {
            handle_menu_command(hwnd, wparam & 0xFFFF);
            0
        }
        WM_DESTROY => {
            unsafe { PostQuitMessage(0) };
            0
        }
        _ => unsafe { DefWindowProcW(hwnd, msg, wparam, lparam) },
    }
}

fn update_tray(hwnd: HWND) {
    let (hicon, tip_text) = {
        let lock = GLOBAL_STATE.lock().unwrap();
        let state = match lock.as_ref() {
            Some(s) => s,
            None => return,
        };

        let ctrl = control::read_control(&state.data_dir);
        let status = control::read_status(&state.data_dir);
        let is_restarting = control::now_secs() < RESTARTING_UNTIL.load(Ordering::SeqCst);
        let is_unpairing = control::now_secs() < UNPAIRING_UNTIL.load(Ordering::SeqCst);

        let icon = if ctrl.paused {
            state.hicon_paused
        } else {
            state.hicon_active
        };

        let tip = if is_restarting {
            "Nexus Agent: Restarting service...\nPlease wait...".to_string()
        } else if is_unpairing {
            "Nexus Agent: Unpairing device...\nPlease wait...".to_string()
        } else if ctrl.paused {
            if let Some(until) = ctrl.until {
                let now = control::now_secs();
                let rem_min = if until > now {
                    (until - now) / 60 + 1
                } else {
                    0
                };
                format!(
                    "Nexus Agent: Paused\nLogging paused (resumes in ~{}m)",
                    rem_min
                )
            } else {
                "Nexus Agent: Paused\nLogging paused indefinitely".to_string()
            }
        } else if let Some(s) = status {
            if s.state == "stopped" {
                "Nexus Agent: Stopped\nService is idle".to_string()
            } else if s.state == "unpaired" {
                "Nexus Agent: Unpaired\nRight-click to pair".to_string()
            } else if s.state == "connecting" {
                "Nexus Agent: Connecting...".to_string()
            } else {
                let temp_str = match s.temp_c {
                    Some(t) => format!(" | {:.0}°C", t),
                    None => String::new(),
                };
                format!(
                    "Nexus Agent: Active\nCPU: {:.0}% | RAM: {:.0}%{}",
                    s.cpu_util, s.ram_pct, temp_str
                )
            }
        } else {
            "Nexus Agent: Active\nConnected".to_string()
        };

        (icon, tip)
    }; // Mutex lock is explicitly dropped here before any Win32 calls

    unsafe {
        let mut nid: NOTIFYICONDATAW = std::mem::zeroed();
        nid.cbSize = std::mem::size_of::<NOTIFYICONDATAW>() as u32;
        nid.hWnd = hwnd;
        nid.uID = TRAY_UID;
        nid.uFlags = NIF_ICON | NIF_TIP;
        nid.hIcon = hicon;

        let wide_tip = to_wide(&tip_text);
        let copy_len = wide_tip.len().min(nid.szTip.len() - 1);
        nid.szTip[..copy_len].copy_from_slice(&wide_tip[..copy_len]);

        Shell_NotifyIconW(NIM_MODIFY, &nid);
    }
}

fn show_context_menu(hwnd: HWND) {
    let data_dir = {
        let lock = GLOBAL_STATE.lock().unwrap();
        match lock.as_ref() {
            Some(s) => s.data_dir.clone(),
            None => return,
        }
    }; // Mutex lock is dropped here before opening the modal context menu!

    let ctrl = control::read_control(&data_dir);
    let status = control::read_status(&data_dir);
    let is_paused = ctrl.paused;
    let is_unpaired = status.as_ref().is_some_and(|s| s.state == "unpaired");
    let is_stopped = status.as_ref().is_some_and(|s| s.state == "stopped");
    let is_restarting = control::now_secs() < RESTARTING_UNTIL.load(Ordering::SeqCst);
    let is_unpairing = control::now_secs() < UNPAIRING_UNTIL.load(Ordering::SeqCst);

    unsafe {
        let hmenu = CreatePopupMenu();
        let hsubmenu = CreatePopupMenu();

        AppendMenuW(
            hsubmenu,
            MF_STRING,
            ID_PAUSE_15M,
            to_wide("15 minutes").as_ptr(),
        );
        AppendMenuW(
            hsubmenu,
            MF_STRING,
            ID_PAUSE_30M,
            to_wide("30 minutes").as_ptr(),
        );
        AppendMenuW(hsubmenu, MF_STRING, ID_PAUSE_1H, to_wide("1 hour").as_ptr());
        AppendMenuW(
            hsubmenu,
            MF_STRING,
            ID_PAUSE_2H,
            to_wide("2 hours").as_ptr(),
        );
        AppendMenuW(
            hsubmenu,
            MF_STRING,
            ID_PAUSE_4H,
            to_wide("4 hours").as_ptr(),
        );
        AppendMenuW(
            hsubmenu,
            MF_STRING,
            ID_PAUSE_8H,
            to_wide("8 hours").as_ptr(),
        );
        AppendMenuW(hsubmenu, MF_SEPARATOR, 0, null());
        AppendMenuW(
            hsubmenu,
            MF_STRING,
            ID_PAUSE_CUSTOM,
            to_wide("Custom duration...").as_ptr(),
        );
        AppendMenuW(
            hsubmenu,
            MF_STRING,
            ID_PAUSE_INDEF,
            to_wide("Pause indefinitely").as_ptr(),
        );

        AppendMenuW(
            hmenu,
            MF_STRING | MF_DISABLED | MF_GRAYED,
            ID_HEADER,
            to_wide("Nexus Agent").as_ptr(),
        );

        let status_label = if is_restarting {
            "Status: ⏳ Restarting service...".to_string()
        } else if is_unpairing {
            "Status: ⏳ Unpairing device...".to_string()
        } else if is_paused {
            if let Some(until) = ctrl.until {
                let now = control::now_secs();
                let rem_min = if until > now {
                    (until - now) / 60 + 1
                } else {
                    0
                };
                format!("Status: ⏸ Paused (~{}m remaining)", rem_min)
            } else {
                "Status: ⏸ Paused indefinitely".to_string()
            }
        } else if is_stopped {
            "Status: ⏹ Stopped".to_string()
        } else if is_unpaired {
            "Status: ⚠ Unpaired".to_string()
        } else {
            "Status: ● Active (Logging)".to_string()
        };
        AppendMenuW(
            hmenu,
            MF_STRING | MF_DISABLED | MF_GRAYED,
            ID_STATUS,
            to_wide(&status_label).as_ptr(),
        );
        AppendMenuW(hmenu, MF_SEPARATOR, 0, null());

        AppendMenuW(
            hmenu,
            MF_STRING,
            ID_PAIR,
            to_wide("Pair Device...").as_ptr(),
        );
        AppendMenuW(hmenu, MF_SEPARATOR, 0, null());

        let pause_flags = if is_stopped || is_restarting || is_unpairing {
            MF_POPUP | MF_DISABLED | MF_GRAYED
        } else {
            MF_POPUP
        };
        AppendMenuW(
            hmenu,
            pause_flags,
            hsubmenu as usize,
            to_wide("Pause Monitoring").as_ptr(),
        );

        let resume_flags = if is_paused && !is_stopped && !is_restarting && !is_unpairing {
            MF_STRING
        } else {
            MF_STRING | MF_DISABLED | MF_GRAYED
        };
        AppendMenuW(
            hmenu,
            resume_flags,
            ID_RESUME,
            to_wide("Resume Monitoring").as_ptr(),
        );

        AppendMenuW(hmenu, MF_SEPARATOR, 0, null());
        AppendMenuW(
            hmenu,
            MF_STRING,
            ID_OPEN_LOGS,
            to_wide("Open Logs...").as_ptr(),
        );

        AppendMenuW(hmenu, MF_SEPARATOR, 0, null());
        if is_restarting {
            AppendMenuW(
                hmenu,
                MF_STRING | MF_DISABLED | MF_GRAYED,
                ID_RESTART_SERVICE,
                to_wide("Restarting Service...").as_ptr(),
            );
        } else {
            let restart_label = if is_stopped {
                "Start Service"
            } else {
                "Restart Service"
            };
            AppendMenuW(
                hmenu,
                MF_STRING,
                ID_RESTART_SERVICE,
                to_wide(restart_label).as_ptr(),
            );
        }

        let unpair_flags = if is_unpaired || is_unpairing {
            MF_STRING | MF_DISABLED | MF_GRAYED
        } else {
            MF_STRING
        };
        let unpair_label = if is_unpairing {
            "Unpairing Device..."
        } else {
            "Unpair Device"
        };
        AppendMenuW(
            hmenu,
            unpair_flags,
            ID_UNPAIR,
            to_wide(unpair_label).as_ptr(),
        );

        AppendMenuW(hmenu, MF_STRING, ID_EXIT, to_wide("Exit Tray").as_ptr());

        let mut pt: POINT = std::mem::zeroed();
        GetCursorPos(&mut pt);
        SetForegroundWindow(hwnd);
        TrackPopupMenu(hmenu, TPM_RIGHTBUTTON, pt.x, pt.y, 0, hwnd, null());
        DestroyMenu(hmenu);
    }
}

fn handle_menu_command(hwnd: HWND, cmd: usize) {
    let data_dir = {
        let lock = GLOBAL_STATE.lock().unwrap();
        match lock.as_ref() {
            Some(s) => s.data_dir.clone(),
            None => return,
        }
    }; // Mutex lock is dropped here!

    match cmd {
        ID_PAUSE_15M => {
            control::set_pause(&data_dir, Some(15 * 60), "tray_15m");
            update_tray(hwnd);
        }
        ID_PAUSE_30M => {
            control::set_pause(&data_dir, Some(30 * 60), "tray_30m");
            update_tray(hwnd);
        }
        ID_PAUSE_1H => {
            control::set_pause(&data_dir, Some(60 * 60), "tray_1h");
            update_tray(hwnd);
        }
        ID_PAUSE_2H => {
            control::set_pause(&data_dir, Some(2 * 60 * 60), "tray_2h");
            update_tray(hwnd);
        }
        ID_PAUSE_4H => {
            control::set_pause(&data_dir, Some(4 * 60 * 60), "tray_4h");
            update_tray(hwnd);
        }
        ID_PAUSE_8H => {
            control::set_pause(&data_dir, Some(8 * 60 * 60), "tray_8h");
            update_tray(hwnd);
        }
        ID_PAUSE_INDEF => {
            control::set_pause(&data_dir, None, "tray_indefinite");
            update_tray(hwnd);
        }
        ID_PAUSE_CUSTOM => {
            std::thread::spawn(move || {
                prompt_custom_pause(&data_dir);
            });
        }
        ID_RESUME => {
            control::clear_pause(&data_dir);
            update_tray(hwnd);
        }
        ID_PAIR => {
            let exe_path = std::env::current_exe().unwrap_or_else(|_| PathBuf::from("nexus.exe"));
            let config_path = data_dir.join("config.toml");
            std::thread::spawn(move || {
                #[cfg(windows)]
                {
                    // If config is readable, launch the pairing GUI directly without console flash.
                    // If locked by older admin ACLs, elevate via RunAs so UAC prompt opens the GUI with full access.
                    let is_accessible = std::fs::File::open(&config_path).is_ok();
                    if is_accessible {
                        use std::os::windows::process::CommandExt;
                        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
                        let _ = std::process::Command::new(&exe_path)
                            .args(["pair", "--gui", &config_path.to_string_lossy()])
                            .creation_flags(CREATE_NO_WINDOW)
                            .spawn();
                    } else {
                        let _ = std::process::Command::new("powershell")
                            .args([
                                "-NoProfile",
                                "-WindowStyle",
                                "Hidden",
                                "-Command",
                                &format!(
                                    "Start-Process -FilePath '{}' -ArgumentList 'pair --gui \"{}\"' -Verb RunAs",
                                    exe_path.display(),
                                    config_path.display()
                                ),
                            ])
                            .status();
                    }
                }
                #[cfg(not(windows))]
                {
                    let _ = std::process::Command::new(&exe_path)
                        .args(["pair", &config_path.to_string_lossy()])
                        .spawn();
                }
            });
        }
        ID_OPEN_LOGS => {
            let log_path = data_dir.join("agent.log");
            let _ = std::fs::create_dir_all(&data_dir);
            if !log_path.exists() {
                let _ = std::fs::write(&log_path, "=== Nexus Agent Log ===\r\n");
            }
            let _ = std::process::Command::new("notepad.exe")
                .arg(&log_path)
                .spawn();
        }
        ID_RESTART_SERVICE => {
            let now = control::now_secs();
            let restarting_until = RESTARTING_UNTIL.load(Ordering::SeqCst);
            if now < restarting_until {
                return; // Guard against rapid duplicate clicks
            }
            // 10-second cooldown and visual indicator
            RESTARTING_UNTIL.store(now + 10, Ordering::SeqCst);
            update_tray(hwnd);

            std::thread::spawn(|| {
                // Elevate via UAC runas verb so the service restart doesn't silently fail from a standard user session
                let _ = std::process::Command::new("powershell")
                    .args([
                        "-NoProfile",
                        "-WindowStyle",
                        "Hidden",
                        "-Command",
                        "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -WindowStyle Hidden -Command Restart-Service NexusAgent, PCMonitorAgent -ErrorAction SilentlyContinue; Start-Service NexusAgent -ErrorAction SilentlyContinue'",
                    ])
                    .status();
            });
        }
        ID_UNPAIR => {
            let now = control::now_secs();
            let unpairing_until = UNPAIRING_UNTIL.load(Ordering::SeqCst);
            if now < unpairing_until {
                return; // Guard against rapid duplicate clicks
            }
            UNPAIRING_UNTIL.store(now + 10, Ordering::SeqCst);
            update_tray(hwnd);

            let exe_path = std::env::current_exe().unwrap_or_else(|_| PathBuf::from("nexus.exe"));
            let config_path = data_dir.join("config.toml");
            let data_dir_clone = data_dir.clone();

            std::thread::spawn(move || {
                // 1. Run unpair online via agent exe
                let _ = std::process::Command::new(&exe_path)
                    .args(["unpair", &config_path.to_string_lossy()])
                    .output();

                // 2. Mark local status as unpaired immediately
                let current_status = control::read_status(&data_dir_clone);
                let convex_url = current_status.as_ref().and_then(|s| s.convex_url.clone());
                control::write_status(
                    &data_dir_clone,
                    &control::AgentStatus {
                        state: "unpaired".to_string(),
                        cpu_util: 0.0,
                        ram_pct: 0.0,
                        temp_c: None,
                        paused_until: None,
                        last_tick: control::now_secs(),
                        convex_url,
                    },
                );

                // 3. Restart the background service to enter the unpaired waiting loop
                #[cfg(windows)]
                {
                    let _ = std::process::Command::new("powershell")
                        .args([
                            "-NoProfile",
                            "-WindowStyle",
                            "Hidden",
                            "-Command",
                            "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -WindowStyle Hidden -Command Restart-Service NexusAgent, PCMonitorAgent -ErrorAction SilentlyContinue'",
                        ])
                        .status();
                }
            });
        }
        ID_EXIT => unsafe {
            PostQuitMessage(0);
        },
        _ => {}
    }
}

/// Prompt the user for custom minutes using a lightweight native input box.
fn prompt_custom_pause(data_dir: &Path) {
    let script = r#"
        Add-Type -AssemblyName Microsoft.VisualBasic;
        $val = [Microsoft.VisualBasic.Interaction]::InputBox(
            'Enter pause duration in minutes (e.g. 45):',
            'Nexus Agent - Custom Pause',
            '30'
        );
        if ($val) { [Console]::WriteLine($val.Trim()) }
    "#;

    if let Ok(output) = std::process::Command::new("powershell")
        .args(["-NoProfile", "-WindowStyle", "Hidden", "-Command", script])
        .output()
    {
        let text = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if let Ok(minutes) = text.parse::<u64>()
            && minutes > 0
        {
            control::set_pause(data_dir, Some(minutes * 60), "custom_tray");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hicon_creation() {
        let h1 = create_tray_hicon(false);
        let h2 = create_tray_hicon(true);
        assert_ne!(h1, 0, "hicon_active must not be null");
        assert_ne!(h2, 0, "hicon_paused must not be null");
    }

    #[test]
    fn test_restart_cooldown_logic() {
        let now = control::now_secs();
        RESTARTING_UNTIL.store(now + 10, Ordering::SeqCst);
        let cooldown = RESTARTING_UNTIL.load(Ordering::SeqCst);
        assert!(cooldown >= now + 9);
        RESTARTING_UNTIL.store(0, Ordering::SeqCst);
        assert_eq!(RESTARTING_UNTIL.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn test_unpair_cooldown_logic() {
        let now = control::now_secs();
        UNPAIRING_UNTIL.store(now + 10, Ordering::SeqCst);
        let cooldown = UNPAIRING_UNTIL.load(Ordering::SeqCst);
        assert!(cooldown >= now + 9);
        UNPAIRING_UNTIL.store(0, Ordering::SeqCst);
        assert_eq!(UNPAIRING_UNTIL.load(Ordering::SeqCst), 0);
    }
}
