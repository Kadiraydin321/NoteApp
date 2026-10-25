#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod shortcuts;

use serde::{Deserialize, Serialize};
use std::borrow::Cow;
use std::fs;
use std::path::PathBuf;
use std::sync::Arc;
use tao::{
    event::{Event, WindowEvent},
    event_loop::{ControlFlow, EventLoop, EventLoopBuilder},
    window::WindowBuilder,
};
use wry::{
    http::{header::CONTENT_TYPE, Method, Response, StatusCode},
    WebViewBuilder,
};

const APP_EXE: &[u8] = include_bytes!("../../desktop-native/target/x86_64-pc-windows-gnu/release/notdefterim.exe");
const WEBVIEW2_DLL: &[u8] = include_bytes!("../target/x86_64-pc-windows-gnu/release/build/webview2-com-sys-aa76598537bcf4ff/out/x64/WebView2Loader.dll");
const ICON_PNG: &[u8] = include_bytes!("../ui/icon.png");
const INDEX_HTML: &str = include_str!("../ui/index.html");
const STYLE_CSS: &str = include_str!("../ui/style.css");
const APP_JS: &str = include_str!("../ui/app.js");

#[derive(Debug, Clone)]
enum UserEvent {
    Exit,
}

#[derive(Deserialize)]
struct ApiRequest {
    cmd: String,
    payload: Option<serde_json::Value>,
}

#[derive(Serialize)]
struct ApiResponse<T> {
    result: T,
}

#[derive(Serialize)]
struct ApiError {
    error: String,
}

#[derive(Serialize)]
struct InfoResponse {
    #[serde(rename = "isUninstall")]
    is_uninstall: bool,
    #[serde(rename = "installDir")]
    install_dir: String,
}

pub fn get_default_install_dir() -> PathBuf {
    if let Ok(local) = std::env::var("LOCALAPPDATA") {
        if !local.trim().is_empty() {
            return PathBuf::from(local).join("Programs").join("NotDefterim");
        }
    }
    if let Ok(user) = std::env::var("USERPROFILE") {
        if !user.trim().is_empty() {
            return PathBuf::from(user)
                .join("AppData")
                .join("Local")
                .join("Programs")
                .join("NotDefterim");
        }
    }
    PathBuf::from(r"C:\NotDefterim")
}

pub fn get_desktop_dir() -> PathBuf {
    if let Ok(user) = std::env::var("USERPROFILE") {
        if !user.trim().is_empty() {
            return PathBuf::from(user).join("Desktop");
        }
    }
    PathBuf::from(".")
}

pub fn get_start_menu_dir() -> PathBuf {
    if let Ok(appdata) = std::env::var("APPDATA") {
        if !appdata.trim().is_empty() {
            return PathBuf::from(appdata)
                .join("Microsoft")
                .join("Windows")
                .join("Start Menu")
                .join("Programs");
        }
    }
    PathBuf::from(".")
}

#[cfg(windows)]
pub fn register_uninstaller(
    app_dir: &std::path::Path,
    exe_path: &std::path::Path,
    uninstall_exe: &std::path::Path,
) -> Result<(), String> {
    use winreg::enums::*;
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let (key, _) = hkcu
        .create_subkey(r"Software\Microsoft\Windows\CurrentVersion\Uninstall\NotDefterim")
        .map_err(|e| format!("Kayıt oluşturulamadı: {}", e))?;

    key.set_value("DisplayName", &"Not Defterim")
        .map_err(|e| e.to_string())?;
    key.set_value("DisplayIcon", &exe_path.to_string_lossy().as_ref())
        .map_err(|e| e.to_string())?;
    key.set_value("DisplayVersion", &"1.0.0")
        .map_err(|e| e.to_string())?;
    key.set_value("Publisher", &"Not Defterim")
        .map_err(|e| e.to_string())?;
    key.set_value("InstallLocation", &app_dir.to_string_lossy().as_ref())
        .map_err(|e| e.to_string())?;
    key.set_value(
        "UninstallString",
        &format!("\"{}\" --uninstall", uninstall_exe.display()),
    )
    .map_err(|e| e.to_string())?;
    key.set_value("NoModify", &1u32).ok();
    key.set_value("NoRepair", &1u32).ok();
    key.set_value("EstimatedSize", &4096u32).ok();

    Ok(())
}

#[cfg(not(windows))]
pub fn register_uninstaller(
    _app_dir: &std::path::Path,
    _exe_path: &std::path::Path,
    _uninstall_exe: &std::path::Path,
) -> Result<(), String> {
    Ok(())
}

#[cfg(windows)]
pub fn unregister_uninstaller() -> Result<(), String> {
    use winreg::enums::*;
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    hkcu.delete_subkey_all(r"Software\Microsoft\Windows\CurrentVersion\Uninstall\NotDefterim")
        .map_err(|e| format!("Kayıt silinemedi: {}", e))?;
    Ok(())
}

#[cfg(not(windows))]
pub fn unregister_uninstaller() -> Result<(), String> {
    Ok(())
}

fn handle_api(
    body: &[u8],
    is_uninstall: bool,
    proxy: &tao::event_loop::EventLoopProxy<UserEvent>,
) -> Vec<u8> {
    let req: ApiRequest = match serde_json::from_slice(body) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::to_vec(&ApiError {
                error: format!("JSON Parse: {}", e),
            })
            .unwrap_or_default();
        }
    };

    let payload = req.payload.unwrap_or(serde_json::Value::Null);

    match req.cmd.as_str() {
        "getInfo" => {
            let resp = InfoResponse {
                is_uninstall,
                install_dir: get_default_install_dir().display().to_string(),
            };
            serde_json::to_vec(&ApiResponse { result: resp }).unwrap()
        }
        "install" => {
            let target_dir = get_default_install_dir();
            if let Err(e) = fs::create_dir_all(&target_dir) {
                return serde_json::to_vec(&ApiError {
                    error: format!("Kurulum dizini oluşturulamadı: {}", e),
                })
                .unwrap();
            }

            let exe_path = target_dir.join("NotDefterim.exe");
            if let Err(e) = fs::write(&exe_path, APP_EXE) {
                return serde_json::to_vec(&ApiError {
                    error: format!("Uygulama dosyası yazılamadı: {}", e),
                })
                .unwrap();
            }

            let icon_path = target_dir.join("icon.png");
            fs::write(&icon_path, ICON_PNG).ok();

            let dll_path = target_dir.join("WebView2Loader.dll");
            fs::write(&dll_path, WEBVIEW2_DLL).ok();

            // Kendi çalışan dosyamızı (setup) kaldırıcı (uninstall.exe) olarak kopyala
            let uninstall_path = target_dir.join("uninstall.exe");
            if let Ok(current_exe) = std::env::current_exe() {
                fs::copy(&current_exe, &uninstall_path).ok();
            }

            // Kısayollar
            let desktop_sc = payload.get("desktopShortcut").and_then(|v| v.as_bool()).unwrap_or(true);
            let start_menu_sc = payload.get("startMenuShortcut").and_then(|v| v.as_bool()).unwrap_or(true);
            let launch_after = payload.get("launchAfter").and_then(|v| v.as_bool()).unwrap_or(true);

            if desktop_sc {
                let lnk_path = get_desktop_dir().join("Not Defterim.lnk");
                shortcuts::create_shortcut(&exe_path, &lnk_path, "Not Defterim", Some(&exe_path)).ok();
            }

            if start_menu_sc {
                let start_dir = get_start_menu_dir();
                fs::create_dir_all(&start_dir).ok();
                let lnk_path = start_dir.join("Not Defterim.lnk");
                shortcuts::create_shortcut(&exe_path, &lnk_path, "Not Defterim", Some(&exe_path)).ok();
            }

            // Windows Program Ekle/Kaldır Kaydı
            register_uninstaller(&target_dir, &exe_path, &uninstall_path).ok();

            // İsteğe bağlı olarak hemen başlat
            if launch_after {
                std::process::Command::new(&exe_path).spawn().ok();
            }

            serde_json::to_vec(&ApiResponse { result: true }).unwrap()
        }
        "uninstall" => {
            // Kısayolları sil
            shortcuts::remove_shortcut(&get_desktop_dir().join("Not Defterim.lnk"));
            shortcuts::remove_shortcut(&get_start_menu_dir().join("Not Defterim.lnk"));

            // Registry kaydını sil
            unregister_uninstaller().ok();

            // Dosyaları sil
            if let Ok(curr) = std::env::current_exe() {
                if let Some(target_dir) = curr.parent() {
                    fs::remove_file(target_dir.join("NotDefterim.exe")).ok();
                    fs::remove_file(target_dir.join("icon.png")).ok();

                    // Uninstall.exe kendini silemeyeceği için arka planda 2 saniye sonra klasörü kaldıran küçük bir cmd tetikle
                    #[cfg(windows)]
                    {
                        let dir_str = target_dir.to_string_lossy().to_string();
                        std::process::Command::new("cmd.exe")
                            .args(&["/c", &format!("timeout /t 2 /nobreak > NUL & rmdir /s /q \"{}\"", dir_str)])
                            .spawn()
                            .ok();
                    }
                }
            }

            serde_json::to_vec(&ApiResponse { result: true }).unwrap()
        }
        "exit" => {
            proxy.send_event(UserEvent::Exit).ok();
            serde_json::to_vec(&ApiResponse { result: true }).unwrap()
        }
        _ => serde_json::to_vec(&ApiError {
            error: "Bilinmeyen komut".to_string(),
        })
        .unwrap(),
    }
}

fn main() -> wry::Result<()> {
    let is_uninstall = std::env::args().any(|a| a == "--uninstall")
        || std::env::current_exe()
            .map(|p| {
                p.file_stem()
                    .unwrap_or_default()
                    .to_string_lossy()
                    .to_lowercase()
                    .contains("uninstall")
            })
            .unwrap_or(false);

    let event_loop: EventLoop<UserEvent> = EventLoopBuilder::<UserEvent>::with_user_event().build();
    let proxy = Arc::new(event_loop.create_proxy());

    let icon = if let Ok(img) = image::load_from_memory(ICON_PNG) {
        let rgba = img.to_rgba8();
        let (width, height) = rgba.dimensions();
        tao::window::Icon::from_rgba(rgba.into_raw(), width, height).ok()
    } else {
        None
    };

    let title = if is_uninstall {
        "Not Defterim Kaldırma Sihirbazı"
    } else {
        "Not Defterim Kurulum Sihirbazı"
    };

    let mut window_builder = WindowBuilder::new()
        .with_title(title)
        .with_inner_size(tao::dpi::LogicalSize::new(560.0, 430.0))
        .with_resizable(false);

    if let Some(ic) = icon {
        window_builder = window_builder.with_window_icon(Some(ic));
    }

    let window = window_builder.build(&event_loop).unwrap();

    let proxy_ref = proxy.clone();
    let _webview = WebViewBuilder::new()
        .with_custom_protocol("setup".into(), move |_id, request| {
            let path = request.uri().path();
            let method = request.method();

            if path == "/api" && *method == Method::POST {
                let resp = handle_api(request.body(), is_uninstall, &proxy_ref);
                Response::builder()
                    .status(StatusCode::OK)
                    .header(CONTENT_TYPE, "application/json")
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type")
                    .body(Cow::Owned(resp))
                    .unwrap()
            } else if *method == Method::OPTIONS {
                Response::builder()
                    .status(StatusCode::OK)
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type")
                    .body(Cow::Borrowed(b"".as_slice()))
                    .unwrap()
            } else if path == "" || path == "/" || path == "/index.html" {
                Response::builder()
                    .status(StatusCode::OK)
                    .header(CONTENT_TYPE, "text/html; charset=utf-8")
                    .body(Cow::Borrowed(INDEX_HTML.as_bytes()))
                    .unwrap()
            } else if path == "/style.css" {
                Response::builder()
                    .status(StatusCode::OK)
                    .header(CONTENT_TYPE, "text/css; charset=utf-8")
                    .body(Cow::Borrowed(STYLE_CSS.as_bytes()))
                    .unwrap()
            } else if path == "/app.js" {
                Response::builder()
                    .status(StatusCode::OK)
                    .header(CONTENT_TYPE, "application/javascript; charset=utf-8")
                    .body(Cow::Borrowed(APP_JS.as_bytes()))
                    .unwrap()
            } else if path == "/icon.png" {
                Response::builder()
                    .status(StatusCode::OK)
                    .header(CONTENT_TYPE, "image/png")
                    .body(Cow::Borrowed(ICON_PNG))
                    .unwrap()
            } else {
                Response::builder()
                    .status(StatusCode::NOT_FOUND)
                    .body(Cow::Borrowed(b"404 Not Found".as_slice()))
                    .unwrap()
            }
        })
        .with_url("setup://localhost")
        .build(&window)?;

    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        match event {
            Event::UserEvent(UserEvent::Exit) => {
                *control_flow = ControlFlow::Exit;
            }
            Event::WindowEvent {
                event: WindowEvent::CloseRequested,
                ..
            } => {
                *control_flow = ControlFlow::Exit;
            }
            _ => {}
        }
    });
}
