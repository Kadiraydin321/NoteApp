#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod backup;
mod crypto;
mod db;
mod models;
mod sync_server;

use backup::BackupManager;
use crypto::CryptoManager;
use db::Database;
use models::{Category, Note};
use serde::{Deserialize, Serialize};
use std::borrow::Cow;
use std::sync::Arc;
use sync_server::SyncServer;
use tao::{
    event::{Event, WindowEvent},
    event_loop::{ControlFlow, EventLoop},
    window::WindowBuilder,
};
use wry::{
    http::{header::CONTENT_TYPE, Method, Response, StatusCode},
    WebViewBuilder,
};

const INDEX_HTML: &str = include_str!("../ui/index.html");
const STYLE_CSS: &str = include_str!("../ui/style.css");
const APP_JS: &str = include_str!("../ui/app.js");
const ICON_PNG: &[u8] = include_bytes!("../ui/icon.png");

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
struct NotesBundle {
    active: Vec<Note>,
    archived: Vec<Note>,
    trash: Vec<Note>,
    categories: Vec<Category>,
    #[serde(rename = "isVaultUnlocked")]
    is_vault_unlocked: bool,
}

#[derive(Serialize)]
struct VaultStatus {
    #[serde(rename = "hasPassword")]
    has_password: bool,
    #[serde(rename = "isUnlocked")]
    is_unlocked: bool,
}

fn handle_api_request(
    body: &[u8],
    db: &Database,
    crypto: &CryptoManager,
    sync: &SyncServer,
    #[allow(unused_variables)] window_hwnd: isize,
) -> Vec<u8> {
    let api_req: ApiRequest = match serde_json::from_slice(body) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::to_vec(&ApiError {
                error: format!("JSON Parse Hatası: {}", e),
            })
            .unwrap_or_default();
        }
    };

    let payload = api_req.payload.unwrap_or(serde_json::Value::Null);

    match api_req.cmd.as_str() {
        "getNotes" => {
            let active = db.get_active_notes().unwrap_or_default();
            let archived = db.get_archived_notes().unwrap_or_default();
            let trash = db.get_trash_notes().unwrap_or_default();
            let categories = db.get_categories().unwrap_or_default();
            let bundle = NotesBundle {
                active,
                archived,
                trash,
                categories,
                is_vault_unlocked: crypto.is_unlocked(),
            };
            serde_json::to_vec(&ApiResponse { result: bundle }).unwrap()
        }
        "createNote" => {
            if let Ok(mut note) = serde_json::from_value::<Note>(payload.get("note").cloned().unwrap_or_default()) {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap()
                    .as_millis() as i64;
                note.timestamp = now;
                note.created_at = now;
                note.updated_at = now;
                if let Ok(id) = db.insert_note(&note) {
                    note.id = id;
                    serde_json::to_vec(&ApiResponse { result: note }).unwrap()
                } else {
                    serde_json::to_vec(&ApiError {
                        error: "Not oluşturulamadı".to_string(),
                    })
                    .unwrap()
                }
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Geçersiz parametre".to_string(),
                })
                .unwrap()
            }
        }
        "saveNote" => {
            if let Ok(note) = serde_json::from_value::<Note>(payload.get("note").cloned().unwrap_or_default()) {
                if note.id == 0 {
                    let id = db.insert_note(&note).unwrap_or(0);
                    serde_json::to_vec(&ApiResponse { result: id }).unwrap()
                } else {
                    db.update_note(&note).ok();
                    serde_json::to_vec(&ApiResponse { result: true }).unwrap()
                }
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Geçersiz parametre".to_string(),
                })
                .unwrap()
            }
        }
        "moveToTrash" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                db.move_to_trash(id).ok();
                serde_json::to_vec(&ApiResponse { result: true }).unwrap()
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Geçersiz ID".to_string(),
                })
                .unwrap()
            }
        }
        "restoreFromTrash" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                db.restore_from_trash(id).ok();
                serde_json::to_vec(&ApiResponse { result: true }).unwrap()
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Geçersiz ID".to_string(),
                })
                .unwrap()
            }
        }
        "deletePermanently" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                db.delete_permanently(id).ok();
                serde_json::to_vec(&ApiResponse { result: true }).unwrap()
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Geçersiz ID".to_string(),
                })
                .unwrap()
            }
        }
        "emptyTrash" => {
            db.empty_trash().ok();
            serde_json::to_vec(&ApiResponse { result: true }).unwrap()
        }
        "addCategory" => {
            let name = payload.get("name").and_then(|v| v.as_str()).unwrap_or("Yeni Kategori");
            let color = payload.get("color").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
            let id = db.insert_category(name, color).unwrap_or(0);
            serde_json::to_vec(&ApiResponse { result: id }).unwrap()
        }
        "deleteCategory" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                db.delete_category(id).ok();
                serde_json::to_vec(&ApiResponse { result: true }).unwrap()
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Geçersiz ID".to_string(),
                })
                .unwrap()
            }
        }
        "syncGetState" => {
            let s = sync.get_state();
            serde_json::to_vec(&ApiResponse { result: s }).unwrap()
        }
        "syncStart" => {
            sync.start();
            serde_json::to_vec(&ApiResponse { result: true }).unwrap()
        }
        "syncStop" => {
            sync.stop();
            serde_json::to_vec(&ApiResponse { result: true }).unwrap()
        }
        "syncRefreshPin" => {
            let new_pin = sync.refresh_pin();
            serde_json::to_vec(&ApiResponse { result: new_pin }).unwrap()
        }
        "backupExport" => {
            let now = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs();
            let target_path = Database::get_app_dir().join(format!("backup_notdefterim_{}.zip", now));
            match BackupManager::export_backup_zip(db, &target_path) {
                Ok(msg) => serde_json::to_vec(&ApiResponse {
                    result: format!("{}\nKonum: {}", msg, target_path.display()),
                })
                .unwrap(),
                Err(e) => serde_json::to_vec(&ApiError { error: e }).unwrap(),
            }
        }
        "vaultStatus" => {
            let status = VaultStatus {
                has_password: crypto.has_master_password(),
                is_unlocked: crypto.is_unlocked(),
            };
            serde_json::to_vec(&ApiResponse { result: status }).unwrap()
        }
        "vaultSetup" => {
            if let Some(pwd) = payload.get("password").and_then(|v| v.as_str()) {
                let ok = crypto.setup_master_password(pwd);
                serde_json::to_vec(&ApiResponse { result: ok }).unwrap()
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Şifre gerekli".to_string(),
                })
                .unwrap()
            }
        }
        "vaultUnlock" => {
            if let Some(pwd) = payload.get("password").and_then(|v| v.as_str()) {
                let ok = crypto.unlock_vault(pwd);
                serde_json::to_vec(&ApiResponse { result: ok }).unwrap()
            } else {
                serde_json::to_vec(&ApiError {
                    error: "Şifre gerekli".to_string(),
                })
                .unwrap()
            }
        }
        "vaultLock" => {
            crypto.lock_vault();
            serde_json::to_vec(&ApiResponse { result: true }).unwrap()
        }
        "setAntiScreenshot" => {
            let enable = payload.get("enable").and_then(|v| v.as_bool()).unwrap_or(true);
            #[cfg(windows)]
            {
                use windows::Win32::Foundation::HWND;
                use windows::Win32::UI::WindowsAndMessaging::{
                    SetWindowDisplayAffinity, WDA_EXCLUDEFROMCAPTURE, WDA_NONE,
                };
                let hwnd = HWND(window_hwnd as _);
                let affinity = if enable { WDA_EXCLUDEFROMCAPTURE } else { WDA_NONE };
                unsafe {
                    SetWindowDisplayAffinity(hwnd, affinity).ok();
                }
            }
            serde_json::to_vec(&ApiResponse { result: enable }).unwrap()
        }
        _ => serde_json::to_vec(&ApiError {
            error: "Bilinmeyen komut".to_string(),
        })
        .unwrap(),
    }
}

fn main() -> wry::Result<()> {
    let db = Arc::new(Database::new());
    let crypto = Arc::new(CryptoManager::new());
    let sync_server = Arc::new(SyncServer::new(db.clone()));

    let event_loop = EventLoop::new();

    // Uygulama İkonunu Yükle
    let icon = if let Ok(img) = image::load_from_memory(ICON_PNG) {
        let rgba = img.to_rgba8();
        let (width, height) = rgba.dimensions();
        tao::window::Icon::from_rgba(rgba.into_raw(), width, height).ok()
    } else {
        None
    };

    let mut window_builder = WindowBuilder::new()
        .with_title("Not Defterim - Masaüstü")
        .with_inner_size(tao::dpi::LogicalSize::new(1200.0, 800.0))
        .with_min_inner_size(tao::dpi::LogicalSize::new(880.0, 560.0));

    if let Some(ic) = icon {
        window_builder = window_builder.with_window_icon(Some(ic));
    }

    let window = window_builder.build(&event_loop).unwrap();

    // Windows Native Anti-Screenshot (Ekran Görüntüsü / Kaydı Engelleme)
    #[cfg(windows)]
    {
        use tao::platform::windows::WindowExtWindows;
        use windows::Win32::Foundation::HWND;
        use windows::Win32::UI::WindowsAndMessaging::{
            SetWindowDisplayAffinity, WDA_EXCLUDEFROMCAPTURE,
        };
        let hwnd = HWND(window.hwnd() as _);
        unsafe {
            SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE).ok();
        }
    }

    let db_ref = db.clone();
    let crypto_ref = crypto.clone();
    let sync_ref = sync_server.clone();

    #[cfg(windows)]
    let window_hwnd = {
        use tao::platform::windows::WindowExtWindows;
        window.hwnd() as isize
    };
    #[cfg(not(windows))]
    let window_hwnd: isize = 0;

    let _webview = WebViewBuilder::new()
        .with_custom_protocol("notdefterim".into(), move |_id, request| {
            let path = request.uri().path();
            let method = request.method();

            if path == "/api" && *method == Method::POST {
                let resp_bytes = handle_api_request(
                    request.body(),
                    &db_ref,
                    &crypto_ref,
                    &sync_ref,
                    window_hwnd,
                );
                Response::builder()
                    .status(StatusCode::OK)
                    .header(CONTENT_TYPE, "application/json")
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type")
                    .body(Cow::Owned(resp_bytes))
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
        .with_url("notdefterim://localhost")
        .build(&window)?;

    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        if let Event::WindowEvent {
            event: WindowEvent::CloseRequested,
            ..
        } = event
        {
            sync_server.stop();
            *control_flow = ControlFlow::Exit;
        }
    });
}
