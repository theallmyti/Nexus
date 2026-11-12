//! Native Windows Pairing GUI Window.
//! Opens when "Pair Device..." is clicked in the system tray, or via `nexus pair --gui`.
//! Displays a crisp QR code, readable 6-character code, expiration countdown,
//! live confirmation status, and direct "Open in Browser" / "Copy Code" buttons.

#![cfg(windows)]

use std::ffi::OsStr;
use std::os::windows::ffi::OsStrExt;
use std::path::PathBuf;
use std::ptr::null;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use qrcode::QrCode;
use windows_sys::Win32::Foundation::{HWND, LPARAM, LRESULT, WPARAM};
use windows_sys::Win32::Graphics::Gdi::{
    CreateBitmap, CreateFontW, DeleteObject, HBITMAP, HFONT, UpdateWindow,
};
use windows_sys::Win32::System::Console::FreeConsole;
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::UI::WindowsAndMessaging::{
    CreateWindowExW, DefWindowProcW, DestroyWindow, DispatchMessageW, GetMessageW, MSG,
    PostQuitMessage, RegisterClassExW, SW_SHOW, SendMessageW, SetForegroundWindow, SetTimer,
    ShowWindow, TranslateMessage, WM_COMMAND, WM_CREATE, WM_DESTROY, WM_TIMER, WM_USER,
    WNDCLASSEXW, WS_CAPTION, WS_CHILD, WS_MINIMIZEBOX, WS_OVERLAPPED, WS_SYSMENU, WS_VISIBLE,
};

const WM_PAIR_UPDATE: u32 = WM_USER + 201;
const TIMER_COUNTDOWN_ID: usize = 101;

const ID_BTN_OPEN: usize = 3001;
const ID_BTN_COPY: usize = 3002;
const ID_BTN_CLOSE: usize = 3003;

// Windows control styles
const SS_CENTER: u32 = 0x0000_0001;
const SS_BITMAP: u32 = 0x0000_000E;
const SS_CENTERIMAGE: u32 = 0x0000_0200;
const BS_PUSHBUTTON: u32 = 0x0000_0000;
const STM_SETIMAGE: u32 = 0x0172;
const IMAGE_BITMAP: WPARAM = 0;
const WM_SETFONT: u32 = 0x0030;
const WM_SETTEXT: u32 = 0x000C;

fn to_wide(s: &str) -> Vec<u16> {
    OsStr::new(s)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect()
}

#[derive(Default, Clone)]
pub struct GuiPairData {
    pub url: String,
    pub code: String,
    pub state: String,
    pub left_secs: u64,
    pub message: String,
    pub is_paired: bool,
    pub qr_bitmap: Option<HBITMAP>,
}

pub struct GuiHandle {
    pub _hwnd: HWND,
    pub data: Arc<Mutex<GuiPairData>>,
    pub cancelled: Arc<AtomicBool>,
}

unsafe impl Send for GuiHandle {}
unsafe impl Sync for GuiHandle {}

static GLOBAL_GUI: Mutex<Option<GuiHandle>> = Mutex::new(None);

/// Generate an RGBA bitmap of the QR code with a clean quiet zone.
pub fn generate_qr_hbitmap(payload: &str, target_size: usize) -> Option<HBITMAP> {
    let qr = QrCode::new(payload).ok()?;
    let modules = qr.width();
    let quiet = 4;
    let total_modules = modules + quiet * 2;
    let px_per_mod = (target_size / total_modules).max(1);
    let actual_size = total_modules * px_per_mod;

    let colors = qr.to_colors();
    let mut pixels = vec![0xFFFFFFFFu32; actual_size * actual_size];

    for my in 0..modules {
        for mx in 0..modules {
            let is_dark = colors[my * modules + mx] == qrcode::Color::Dark;
            let color = if is_dark { 0xFF111111 } else { 0xFFFFFFFF };

            let start_y = (my + quiet) * px_per_mod;
            let start_x = (mx + quiet) * px_per_mod;

            for dy in 0..px_per_mod {
                for dx in 0..px_per_mod {
                    let py = start_y + dy;
                    let px = start_x + dx;
                    if py < actual_size && px < actual_size {
                        pixels[py * actual_size + px] = color;
                    }
                }
            }
        }
    }

    unsafe {
        let hbm = CreateBitmap(
            actual_size as i32,
            actual_size as i32,
            1,
            32,
            pixels.as_ptr() as *const _,
        );
        if hbm == 0 { None } else { Some(hbm) }
    }
}

#[allow(dead_code)]
struct WindowControls {
    h_qr: HWND,
    h_header: HWND,
    h_code: HWND,
    h_timer: HWND,
    h_status: HWND,
    h_btn_open: HWND,
    h_btn_copy: HWND,
    h_btn_close: HWND,
    font_large: HFONT,
    font_normal: HFONT,
    font_small: HFONT,
}

static CONTROLS: Mutex<Option<WindowControls>> = Mutex::new(None);

unsafe extern "system" fn wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    unsafe {
        match msg {
            WM_CREATE => {
                let hinstance = GetModuleHandleW(null());

                // Fonts
                let font_large = CreateFontW(
                    28,
                    0,
                    0,
                    0,
                    700,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    to_wide("Segoe UI").as_ptr(),
                );
                let font_normal = CreateFontW(
                    16,
                    0,
                    0,
                    0,
                    600,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    to_wide("Segoe UI").as_ptr(),
                );
                let font_small = CreateFontW(
                    14,
                    0,
                    0,
                    0,
                    400,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    to_wide("Segoe UI").as_ptr(),
                );

                let h_header = CreateWindowExW(
                    0,
                    to_wide("STATIC").as_ptr(),
                    to_wide("Pair This Computer with Nexus").as_ptr(),
                    WS_CHILD | WS_VISIBLE | SS_CENTER,
                    20,
                    16,
                    380,
                    24,
                    hwnd,
                    0,
                    hinstance,
                    null(),
                );
                SendMessageW(h_header, WM_SETFONT, font_normal as WPARAM, 1);

                let h_subtitle = CreateWindowExW(
                    0,
                    to_wide("STATIC").as_ptr(),
                    to_wide("Scan the QR code with your phone camera or Nexus app.").as_ptr(),
                    WS_CHILD | WS_VISIBLE | SS_CENTER,
                    20,
                    42,
                    380,
                    20,
                    hwnd,
                    0,
                    hinstance,
                    null(),
                );
                SendMessageW(h_subtitle, WM_SETFONT, font_small as WPARAM, 1);

                // Centered QR image container
                let h_qr = CreateWindowExW(
                    0,
                    to_wide("STATIC").as_ptr(),
                    null(),
                    WS_CHILD | WS_VISIBLE | SS_BITMAP | SS_CENTERIMAGE,
                    105,
                    68,
                    210,
                    210,
                    hwnd,
                    0,
                    hinstance,
                    null(),
                );

                // Spaced Code label
                let h_code = CreateWindowExW(
                    0,
                    to_wide("STATIC").as_ptr(),
                    to_wide("Connecting...").as_ptr(),
                    WS_CHILD | WS_VISIBLE | SS_CENTER,
                    20,
                    290,
                    380,
                    36,
                    hwnd,
                    0,
                    hinstance,
                    null(),
                );
                SendMessageW(h_code, WM_SETFONT, font_large as WPARAM, 1);

                // Expiration timer label
                let h_timer = CreateWindowExW(
                    0,
                    to_wide("STATIC").as_ptr(),
                    to_wide("Generating pairing code...").as_ptr(),
                    WS_CHILD | WS_VISIBLE | SS_CENTER,
                    20,
                    332,
                    380,
                    20,
                    hwnd,
                    0,
                    hinstance,
                    null(),
                );
                SendMessageW(h_timer, WM_SETFONT, font_small as WPARAM, 1);

                // Status message label
                let h_status = CreateWindowExW(
                    0,
                    to_wide("STATIC").as_ptr(),
                    to_wide("Waiting for confirmation...").as_ptr(),
                    WS_CHILD | WS_VISIBLE | SS_CENTER,
                    20,
                    354,
                    380,
                    24,
                    hwnd,
                    0,
                    hinstance,
                    null(),
                );
                SendMessageW(h_status, WM_SETFONT, font_small as WPARAM, 1);

                // Action buttons
                let h_btn_open = CreateWindowExW(
                    0,
                    to_wide("BUTTON").as_ptr(),
                    to_wide("Open Web Pairing").as_ptr(),
                    WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                    30,
                    395,
                    170,
                    36,
                    hwnd,
                    ID_BTN_OPEN as isize,
                    hinstance,
                    null(),
                );
                SendMessageW(h_btn_open, WM_SETFONT, font_normal as WPARAM, 1);

                let h_btn_copy = CreateWindowExW(
                    0,
                    to_wide("BUTTON").as_ptr(),
                    to_wide("Copy Code").as_ptr(),
                    WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                    220,
                    395,
                    170,
                    36,
                    hwnd,
                    ID_BTN_COPY as isize,
                    hinstance,
                    null(),
                );
                SendMessageW(h_btn_copy, WM_SETFONT, font_normal as WPARAM, 1);

                let h_btn_close = CreateWindowExW(
                    0,
                    to_wide("BUTTON").as_ptr(),
                    to_wide("Close").as_ptr(),
                    WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                    150,
                    445,
                    120,
                    32,
                    hwnd,
                    ID_BTN_CLOSE as isize,
                    hinstance,
                    null(),
                );
                SendMessageW(h_btn_close, WM_SETFONT, font_small as WPARAM, 1);

                *CONTROLS.lock().unwrap() = Some(WindowControls {
                    h_qr,
                    h_header,
                    h_code,
                    h_timer,
                    h_status,
                    h_btn_open,
                    h_btn_copy,
                    h_btn_close,
                    font_large,
                    font_normal,
                    font_small,
                });

                SetTimer(hwnd, TIMER_COUNTDOWN_ID, 1000, None);
                0
            }

            WM_TIMER => {
                if wparam == TIMER_COUNTDOWN_ID {
                    refresh_display(hwnd);
                }
                0
            }

            WM_PAIR_UPDATE => {
                refresh_display(hwnd);
                0
            }

            WM_COMMAND => {
                let cmd_id = wparam & 0xFFFF;
                if cmd_id == ID_BTN_OPEN {
                    let url = {
                        let lock = GLOBAL_GUI.lock().unwrap();
                        lock.as_ref()
                            .map(|g| g.data.lock().unwrap().url.clone())
                            .unwrap_or_default()
                    };
                    if !url.is_empty() {
                        let _ = std::process::Command::new("explorer").arg(&url).spawn();
                    }
                } else if cmd_id == ID_BTN_COPY {
                    let code = {
                        let lock = GLOBAL_GUI.lock().unwrap();
                        lock.as_ref()
                            .map(|g| g.data.lock().unwrap().code.clone())
                            .unwrap_or_default()
                    };
                    if !code.is_empty() {
                        let clean_code = code.replace(' ', "");
                        let _ = std::process::Command::new("powershell")
                            .args([
                                "-NoProfile",
                                "-Command",
                                &format!("Set-Clipboard -Value '{clean_code}'"),
                            ])
                            .spawn();
                        if let Some(ctrl) = CONTROLS.lock().unwrap().as_ref() {
                            let copied_text = to_wide("Code Copied!");
                            SendMessageW(
                                ctrl.h_btn_copy,
                                WM_SETTEXT,
                                0,
                                copied_text.as_ptr() as LPARAM,
                            );
                        }
                    }
                } else if cmd_id == ID_BTN_CLOSE {
                    DestroyWindow(hwnd);
                }
                0
            }

            WM_DESTROY => {
                if let Some(ctrl) = CONTROLS.lock().unwrap().take() {
                    DeleteObject(ctrl.font_large as _);
                    DeleteObject(ctrl.font_normal as _);
                    DeleteObject(ctrl.font_small as _);
                }
                if let Some(gui) = GLOBAL_GUI.lock().unwrap().as_ref() {
                    gui.cancelled.store(true, Ordering::SeqCst);
                    if let Some(hbm) = gui.data.lock().unwrap().qr_bitmap.take() {
                        DeleteObject(hbm as _);
                    }
                }
                PostQuitMessage(0);
                0
            }

            _ => DefWindowProcW(hwnd, msg, wparam, lparam),
        }
    }
}

fn refresh_display(_hwnd: HWND) {
    let (code, state, left, msg, is_paired, new_hbm) = {
        let lock = GLOBAL_GUI.lock().unwrap();
        let gui = match lock.as_ref() {
            Some(g) => g,
            None => return,
        };
        let d = gui.data.lock().unwrap();
        let hbm = d.qr_bitmap;
        (
            d.code.clone(),
            d.state.clone(),
            d.left_secs,
            d.message.clone(),
            d.is_paired,
            hbm,
        )
    };

    let ctrl_lock = CONTROLS.lock().unwrap();
    let ctrl = match ctrl_lock.as_ref() {
        Some(c) => c,
        None => return,
    };

    unsafe {
        if let Some(hbm) = new_hbm {
            SendMessageW(ctrl.h_qr, STM_SETIMAGE, IMAGE_BITMAP, hbm as LPARAM);
        }

        if is_paired {
            let success_text = to_wide("Successfully Paired!");
            let status_text = to_wide("✓ This computer is linked to your account.");
            let timer_text = to_wide("Monitoring is active.");
            SendMessageW(ctrl.h_code, WM_SETTEXT, 0, success_text.as_ptr() as LPARAM);
            SendMessageW(ctrl.h_status, WM_SETTEXT, 0, status_text.as_ptr() as LPARAM);
            SendMessageW(ctrl.h_timer, WM_SETTEXT, 0, timer_text.as_ptr() as LPARAM);
        } else {
            let code_wide = to_wide(&code);
            SendMessageW(ctrl.h_code, WM_SETTEXT, 0, code_wide.as_ptr() as LPARAM);

            let timer_str = if left > 0 {
                format!(
                    "Code expires in {:02}:{:02} (auto-refreshes)",
                    left / 60,
                    left % 60
                )
            } else if state == "connecting" {
                "Connecting to Nexus...".to_string()
            } else {
                "Refreshing code...".to_string()
            };
            let timer_wide = to_wide(&timer_str);
            SendMessageW(ctrl.h_timer, WM_SETTEXT, 0, timer_wide.as_ptr() as LPARAM);

            let status_str = if !msg.is_empty() {
                msg
            } else {
                "Waiting for confirmation from phone or app...".to_string()
            };
            let status_wide = to_wide(&status_str);
            SendMessageW(ctrl.h_status, WM_SETTEXT, 0, status_wide.as_ptr() as LPARAM);
        }
    }
}

/// Spawns and manages the pairing window on a dedicated UI thread.
pub fn launch_pairing_gui(_data_dir: PathBuf, config_path: PathBuf) {
    unsafe {
        FreeConsole();
    }

    let shared_data = Arc::new(Mutex::new(GuiPairData {
        code: "Generating...".to_string(),
        state: "connecting".to_string(),
        ..Default::default()
    }));
    let cancelled = Arc::new(AtomicBool::new(false));

    // Register class and create window
    let hinstance = unsafe { GetModuleHandleW(null()) };
    let class_name = to_wide("NexusPairingWindowClass");

    let wnd_class = WNDCLASSEXW {
        cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
        style: 0,
        lpfnWndProc: Some(wnd_proc),
        cbClsExtra: 0,
        cbWndExtra: 0,
        hInstance: hinstance,
        hIcon: 0,
        hCursor: 0,
        hbrBackground: 0, // default background
        lpszMenuName: null(),
        lpszClassName: class_name.as_ptr(),
        hIconSm: 0,
    };

    unsafe {
        RegisterClassExW(&wnd_class);
    }

    let width = 440;
    let height = 530;

    let hwnd = unsafe {
        CreateWindowExW(
            0,
            class_name.as_ptr(),
            to_wide("Nexus - Pair Device").as_ptr(),
            WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
            100,
            100,
            width,
            height,
            0,
            0,
            hinstance,
            null(),
        )
    };

    if hwnd == 0 {
        return;
    }

    let gui_handle = GuiHandle {
        _hwnd: hwnd,
        data: Arc::clone(&shared_data),
        cancelled: Arc::clone(&cancelled),
    };

    *GLOBAL_GUI.lock().unwrap() = Some(gui_handle);

    // Spawn async pairing runner on Tokio background thread
    let shared_clone = Arc::clone(&shared_data);
    let cancelled_clone = Arc::clone(&cancelled);

    std::thread::spawn(move || {
        let rt = match tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
        {
            Ok(r) => r,
            Err(_) => return,
        };

        rt.block_on(async {
            crate::pair::run_gui_mode(&config_path, shared_clone, cancelled_clone, hwnd).await;
        });
    });

    unsafe {
        ShowWindow(hwnd, SW_SHOW);
        UpdateWindow(hwnd);
        SetForegroundWindow(hwnd);

        let mut msg: MSG = std::mem::zeroed();
        while GetMessageW(&mut msg, 0, 0, 0) > 0 {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }
}
