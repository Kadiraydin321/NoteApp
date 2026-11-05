#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod android_client;
mod backup;
mod crypto;
mod db;
mod models;
mod sync_server;

#[cfg(test)]
mod tests;

use backup::BackupManager;
use crypto::CryptoManager;
use db::Database;
use models::{Category, IpcError, IpcRequest, IpcResponse, Note, VaultStatus};
use serde::{Deserialize, Serialize};
use std::borrow::Cow;
use std::path::PathBuf;
use std::sync::Arc;
use sync_server::SyncServer;
use tao::{
    event::{Event, WindowEvent},
    event_loop::{ControlFlow, EventLoopBuilder, EventLoopProxy},
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

/// Web sayfasÄ±nÄ±n yÃ¼kleniÅŸinde yerel IPC ve Response Bridge saÄŸlayÄ±cÄ±sÄ±
const INIT_SCRIPT: &str = r#"
window.__pendingIpcRequests = window.__pendingIpcRequests || new Map();
window.__IPC_RESOLVE__ = function(response) {
    try {
        if (typeof response === 'string') {
            response = JSON.parse(response);
        }
    } catch(e) {}
    if (!response || !response.requestId) return;
    const handler = window.__pendingIpcRequests.get(response.requestId);
    if (handler) {
        window.__pendingIpcRequests.delete(response.requestId);
        if (response.success) {
            handler.resolve(response.data);
        } else {
            handler.reject(new Error(response.error ? response.error.message : 'IPC HatasÄ±'));
        }
    }
};
window.callNativeIpc = function(action, payload) {
    return new Promise((resolve, reject) => {
        const requestId = 'req_' + Date.now() + '_' + Math.random().toString(36).substring(2, 8);
        window.__pendingIpcRequests.set(requestId, { resolve, reject });
        if (window.ipc && window.ipc.postMessage) {
            window.ipc.postMessage(JSON.stringify({ action, payload: payload || {}, requestId }));
        } else {
            reject(new Error('window.ipc.postMessage mevcut deÄŸil'));
        }
    });
};
"#;

/// Tao Event Loop iÃ§in KullanÄ±cÄ± OlaylarÄ±
#[derive(Debug)]
pub enum UserEvent {
    EvaluateScript(String),
    SetAntiScreenshot(bool),
    Minimize,
    Maximize,
    Close,
}

#[derive(Deserialize)]
struct LegacyApiRequest {
    cmd: String,
    payload: Option<serde_json::Value>,
}

#[derive(Serialize)]
struct LegacyApiResponse<T> {
    result: T,
}

#[derive(Serialize)]
struct LegacyApiError {
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

/// TÃ¼m IPC ve REST API eylemlerini merkezi olarak yÃ¼rÃ¼ten Ã§ekirdek iÅŸ mantÄ±ÄŸÄ± daÄŸÄ±tÄ±cÄ±sÄ± (Dispatcher)
fn execute_ipc_action(
    action: &str,
    payload: serde_json::Value,
    request_id: &str,
    db: &Database,
    crypto: &CryptoManager,
    sync: &SyncServer,
    proxy: &EventLoopProxy<UserEvent>,
) -> IpcResponse<serde_json::Value> {
    match action {
        // --- db:* (VeritabanÄ± ve Not Ä°ÅŸlemleri) ---
        "db:getNotes" | "getNotes" => {
            let active = db.get_active_notes().unwrap_or_default();
            let archived = db.get_archived_notes().unwrap_or_default();
            let trash = db.get_trash_notes().unwrap_or_default();
            let categories = db.get_categories().unwrap_or_default();
            let is_vault_unlocked = crypto.is_unlocked();
            let bundle = NotesBundle {
                active,
                archived,
                trash,
                categories,
                is_vault_unlocked,
            };
            IpcResponse::ok(request_id, serde_json::to_value(&bundle).unwrap_or_default())
        }
        "db:createNote" | "createNote" => {
            if let Ok(mut note) = serde_json::from_value::<Note>(payload.get("note").cloned().unwrap_or_default()) {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as i64;
                note.timestamp = now;
                note.created_at = now;
                note.updated_at = now;
                match db.insert_note(&note) {
                    Ok(id) => {
                        note.id = id;
                        IpcResponse::ok(request_id, serde_json::to_value(&note).unwrap_or_default())
                    }
                    Err(e) => IpcResponse::err(request_id, "DB_INSERT_ERROR", e.to_string()),
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "GeÃ§ersiz not verisi")
            }
        }
        "db:saveNote" | "saveNote" => {
            if let Ok(note) = serde_json::from_value::<Note>(payload.get("note").cloned().unwrap_or_default()) {
                if note.id == 0 {
                    match db.insert_note(&note) {
                        Ok(id) => IpcResponse::ok(request_id, serde_json::json!({ "id": id, "updatedAt": note.updated_at })),
                        Err(e) => IpcResponse::err(request_id, "DB_INSERT_ERROR", e.to_string()),
                    }
                } else {
                    match db.update_note(&note) {
                        Ok(_) => IpcResponse::ok(request_id, serde_json::json!({ "id": note.id, "updatedAt": note.updated_at, "success": true })),
                        Err(e) => IpcResponse::err(request_id, "DB_UPDATE_ERROR", e.to_string()),
                    }
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "GeÃ§ersiz not verisi")
            }
        }
        "db:moveToTrash" | "moveToTrash" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                match db.move_to_trash(id) {
                    Ok(_) => IpcResponse::ok(request_id, serde_json::json!({ "success": true })),
                    Err(e) => IpcResponse::err(request_id, "DB_ERROR", e.to_string()),
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "GeÃ§ersiz ID")
            }
        }
        "db:restoreFromTrash" | "restoreFromTrash" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                match db.restore_from_trash(id) {
                    Ok(_) => IpcResponse::ok(request_id, serde_json::json!({ "success": true })),
                    Err(e) => IpcResponse::err(request_id, "DB_ERROR", e.to_string()),
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "GeÃ§ersiz ID")
            }
        }
        "db:deletePermanently" | "deletePermanently" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                match db.delete_permanently(id) {
                    Ok(_) => IpcResponse::ok(request_id, serde_json::json!({ "success": true })),
                    Err(e) => IpcResponse::err(request_id, "DB_ERROR", e.to_string()),
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "GeÃ§ersiz ID")
            }
        }
        "db:emptyTrash" | "emptyTrash" => {
            match db.empty_trash() {
                Ok(_) => IpcResponse::ok(request_id, serde_json::json!({ "success": true })),
                Err(e) => IpcResponse::err(request_id, "DB_ERROR", e.to_string()),
            }
        }
        "db:getCategories" | "getCategories" => {
            match db.get_categories() {
                Ok(cats) => IpcResponse::ok(request_id, serde_json::to_value(&cats).unwrap_or_default()),
                Err(e) => IpcResponse::err(request_id, "DB_ERROR", e.to_string()),
            }
        }
        "db:saveCategory" | "saveCategory" | "db:addCategory" | "addCategory" => {
            let name = payload.get("name").and_then(|v| v.as_str()).unwrap_or("Yeni Kategori");
            let color = payload.get("color").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
            match db.insert_category(name, color) {
                Ok(id) => IpcResponse::ok(request_id, serde_json::json!({ "id": id })),
                Err(e) => IpcResponse::err(request_id, "DB_ERROR", e.to_string()),
            }
        }
        "db:deleteCategory" | "deleteCategory" => {
            if let Some(id) = payload.get("id").and_then(|v| v.as_i64()) {
                match db.delete_category(id) {
                    Ok(_) => IpcResponse::ok(request_id, serde_json::json!({ "success": true })),
                    Err(e) => IpcResponse::err(request_id, "DB_ERROR", e.to_string()),
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "GeÃ§ersiz ID")
            }
        }

        // --- vault:* (GÃ¼venli Kasa Ä°ÅŸlemleri) ---
        "vault:getStatus" | "vaultStatus" => {
            let status = VaultStatus {
                has_password: crypto.has_master_password(),
                is_unlocked: crypto.is_unlocked(),
            };
            IpcResponse::ok(request_id, serde_json::to_value(&status).unwrap_or_default())
        }
        "vault:setup" | "vaultSetup" => {
            if let Some(pwd) = payload.get("password").and_then(|v| v.as_str()) {
                let ok = crypto.setup_master_password(pwd);
                if ok {
                    IpcResponse::ok(request_id, serde_json::json!({ "success": true }))
                } else {
                    IpcResponse::err(request_id, "VAULT_SETUP_FAILED", "Kasa parolasÄ± oluÅŸturulamadÄ±.")
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "Åifre gerekli")
            }
        }
        "vault:unlock" | "vaultUnlock" => {
            if let Some(pwd) = payload.get("password").and_then(|v| v.as_str()) {
                let ok = crypto.unlock_vault(pwd);
                if ok {
                    IpcResponse::ok(request_id, serde_json::json!({ "success": true }))
                } else {
                    IpcResponse::err(request_id, "VAULT_INVALID_PASSWORD", "Kasa ÅŸifresi hatalÄ± veya doÄŸrulanamadÄ±.")
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "Åifre gerekli")
            }
        }
        "vault:lock" | "vaultLock" => {
            crypto.lock_vault();
            IpcResponse::ok(request_id, serde_json::json!({ "success": true }))
        }

        // --- sync:* (LAN Senkronizasyonu) ---
        "sync:getState" | "syncGetState" => {
            let s = sync.get_state();
            IpcResponse::ok(request_id, serde_json::to_value(&s).unwrap_or_default())
        }
        "sync:start" | "syncStart" => {
            sync.start();
            let s = sync.get_state();
            IpcResponse::ok(request_id, serde_json::to_value(&s).unwrap_or_default())
        }
        "sync:stop" | "syncStop" => {
            sync.stop();
            IpcResponse::ok(request_id, serde_json::json!({ "isRunning": false }))
        }
        "sync:refreshPin" | "syncRefreshPin" => {
            let new_pin = sync.refresh_pin();
            IpcResponse::ok(request_id, serde_json::json!({ "pin": new_pin }))
        }
        "sync:createPairingToken" | "createPairingToken" => match sync.create_pairing_token() {
            Ok(pairing) => IpcResponse::ok(request_id, serde_json::to_value(pairing).unwrap_or_default()),
            Err(_) => IpcResponse::err(request_id, "PAIRING_UNAVAILABLE", "QR eşleştirme kodu oluşturulamadı."),
        },

        "androidSync:preview" => {
            let now_ms = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as i64;
            let qr = payload.get("qr").and_then(|v| v.as_str()).unwrap_or_default();
            if let Err(e) = android_client::parse_qr(qr, now_ms) {
                return IpcResponse::err(request_id, "INVALID_QR", e);
            }
            if payload.get("direction").and_then(|v| v.as_str()) == Some("send") {
                let count = db.get_all_notes().map(|n| n.len()).unwrap_or(0);
                match BackupManager::create_backup_zip_buffer(db) {
                    Ok(zip) if zip.len() <= android_client::MAX_ZIP_BYTES => IpcResponse::ok(
                        request_id,
                        serde_json::json!({ "noteCount": count, "sizeBytes": zip.len() }),
                    ),
                    Ok(_) => IpcResponse::err(request_id, "ARCHIVE_TOO_LARGE", "ZIP paketi 50 MiB sınırını aşıyor."),
                    Err(e) => IpcResponse::err(request_id, "ARCHIVE_FAILED", e),
                }
            } else {
                IpcResponse::ok(request_id, serde_json::json!({ "noteCount": null, "sizeBytes": null }))
            }
        }
        "androidSync:transfer" => {
            use std::sync::atomic::{AtomicBool, Ordering};
            static BUSY: AtomicBool = AtomicBool::new(false);
            let now_ms = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as i64;
            let qr = payload.get("qr").and_then(|v| v.as_str()).unwrap_or_default();
            let pairing = match android_client::parse_qr(qr, now_ms) {
                Ok(p) => p,
                Err(e) => return IpcResponse::err(request_id, "INVALID_QR", e),
            };
            let send = match payload.get("direction").and_then(|v| v.as_str()) {
                Some("send") => true,
                Some("receive") => false,
                _ => return IpcResponse::err(request_id, "INVALID_PAYLOAD", "direction gerekli"),
            };
            if payload.get("confirmed").and_then(|v| v.as_bool()) != Some(true) {
                return IpcResponse::err(request_id, "NOT_CONFIRMED", "Aktarım açık onay olmadan başlatılamaz.");
            }
            if BUSY.swap(true, Ordering::SeqCst) {
                return IpcResponse::err(request_id, "BUSY", "Başka bir aktarım sürüyor.");
            }
            let emit = |proxy: &EventLoopProxy<UserEvent>, data: serde_json::Value| {
                let json = serde_json::json!({ "event": "androidSync:event", "data": data });
                let _ = proxy.send_event(UserEvent::EvaluateScript(format!(
                    "if (window.__IPC_EMIT__) {{ window.__IPC_EMIT__({}); }}",
                    json
                )));
            };
            // Network work runs inline on this IPC worker thread; progress is pushed as events.
            let progress_proxy = proxy.clone();
            let mut last_pct = -1i64;
            let progress = |done: u64, total: u64| {
                let pct = if total > 0 { (done * 100 / total) as i64 } else { 0 };
                if pct != last_pct {
                    last_pct = pct;
                    emit(&progress_proxy, serde_json::json!({ "type": "progress", "done": done, "total": total }));
                }
            };
            let outcome: Result<(String, u64), String> = if send {
                BackupManager::create_backup_zip_buffer(db)
                    .and_then(|zip| android_client::upload(&pairing, &zip, progress))
                    .map(|n| ("Notlar Android cihaza gönderildi.".to_string(), n))
            } else {
                android_client::download(&pairing, progress).and_then(|rec| {
                    if !rec.zip.starts_with(b"PK\x03\x04") {
                        return Err("Android geçerli bir ZIP yedeği göndermedi.".to_string());
                    }
                    // Restore replaces local notes, so keep a local safety copy first.
                    let safety = Database::get_app_dir().join("pre_android_sync_backup.zip");
                    let current = BackupManager::create_backup_zip_buffer(db)?;
                    std::fs::write(&safety, current).map_err(|e| format!("Yerel güvenlik yedeği yazılamadı: {e}"))?;
                    let (notes, _) = BackupManager::restore_backup_zip_buffer(db, &rec.zip)?;
                    Ok(("Android notları bu bilgisayara aktarıldı.".to_string(), notes as u64))
                })
            };
            BUSY.store(false, Ordering::SeqCst);
            match outcome {
                Ok((message, notes)) => {
                    emit(proxy, serde_json::json!({ "type": "done", "ok": true, "message": message, "notes": notes, "reload": !send }));
                    IpcResponse::ok(request_id, serde_json::json!({ "ok": true }))
                }
                Err(e) => {
                    emit(proxy, serde_json::json!({ "type": "done", "ok": false, "message": e }));
                    IpcResponse::err(request_id, "TRANSFER_FAILED", e)
                }
            }
        }

        // --- backup:* (Yedekleme & Geri YÃ¼kleme) ---
        "backup:export" | "backupExport" => {
            let password = payload.get("password").and_then(|v| v.as_str());
            let is_vault = password.is_some() || payload.get("isVault").and_then(|v| v.as_bool()).unwrap_or(false);
            let ext = if is_vault { "notevault" } else { "zip" };
            let target_path = if let Some(p) = payload.get("targetPath").and_then(|v| v.as_str()) {
                PathBuf::from(p)
            } else {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_secs();
                Database::get_app_dir().join(format!("backup_notdefterim_{}.{}", now, ext))
            };
            match BackupManager::export_backup(db, &target_path, password) {
                Ok(msg) => IpcResponse::ok(request_id, serde_json::json!({
                    "filePath": target_path.display().to_string(),
                    "message": msg
                })),
                Err(e) => IpcResponse::err(request_id, "BACKUP_EXPORT_FAILED", e),
            }
        }
        "backup:import" | "backupImport" => {
            if let Some(source_path) = payload.get("sourcePath").and_then(|v| v.as_str()) {
                let password = payload.get("password").and_then(|v| v.as_str());
                match BackupManager::import_backup(db, &PathBuf::from(source_path), password) {
                    Ok(stats) => IpcResponse::ok(request_id, serde_json::json!({
                        "message": format!("Yedek baÅŸarÄ±yla yÃ¼klendi: {} not, {} kategori aktarÄ±ldÄ±", stats.notes_count, stats.categories_count),
                        "stats": stats
                    })),
                    Err(e) => IpcResponse::err(request_id, "BACKUP_IMPORT_FAILED", e),
                }
            } else {
                IpcResponse::err(request_id, "INVALID_PAYLOAD", "sourcePath gerekli")
            }
        }

        // --- window:* (Sistem & GÃ¼venlik Entegrasyonu) ---
        "window:setAntiScreenshot" | "setAntiScreenshot" => {
            let enable = payload.get("enable").and_then(|v| v.as_bool()).unwrap_or(true);
            let _ = proxy.send_event(UserEvent::SetAntiScreenshot(enable));
            IpcResponse::ok(request_id, serde_json::json!({ "enabled": enable }))
        }
        "window:minimize" => {
            let _ = proxy.send_event(UserEvent::Minimize);
            IpcResponse::ok(request_id, serde_json::json!({ "success": true }))
        }
        "window:maximize" => {
            let _ = proxy.send_event(UserEvent::Maximize);
            IpcResponse::ok(request_id, serde_json::json!({ "success": true }))
        }
        "window:close" => {
            let _ = proxy.send_event(UserEvent::Close);
            IpcResponse::ok(request_id, serde_json::json!({ "success": true }))
        }

        _ => IpcResponse::err(request_id, "UNKNOWN_ACTION", format!("Bilinmeyen IPC eylemi: {}", action)),
    }
}

/// Geriye dÃ¶nÃ¼k uyumluluk: /api POST endpoint'i Ã¼zerinden gelen eski istekleri yanÄ±tlar
fn handle_legacy_http_request(
    body: &[u8],
    db: &Database,
    crypto: &CryptoManager,
    sync: &SyncServer,
    proxy: &EventLoopProxy<UserEvent>,
) -> Vec<u8> {
    let api_req: LegacyApiRequest = match serde_json::from_slice(body) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::to_vec(&LegacyApiError {
                error: format!("JSON Parse HatasÄ±: {}", e),
            })
            .unwrap_or_default();
        }
    };

    let payload = api_req.payload.unwrap_or(serde_json::Value::Null);
    let ipc_res = execute_ipc_action(&api_req.cmd, payload, "http_api", db, crypto, sync, proxy);

    if ipc_res.success {
        serde_json::to_vec(&LegacyApiResponse {
            result: ipc_res.data.unwrap_or(serde_json::Value::Null),
        })
        .unwrap_or_default()
    } else {
        let err_msg = ipc_res
            .error
            .map(|e| e.message)
            .unwrap_or_else(|| "Bilinmeyen hata".to_string());
        serde_json::to_vec(&LegacyApiError { error: err_msg }).unwrap_or_default()
    }
}

fn main() -> wry::Result<()> {
    let db = Arc::new(Database::new());
    let crypto = Arc::new(CryptoManager::new());
    let sync_server = Arc::new(SyncServer::new(db.clone()));

    // Tao Event Loop (UserEvent desteÄŸiyle)
    let event_loop = EventLoopBuilder::<UserEvent>::with_user_event().build();
    let proxy = event_loop.create_proxy();

    // Uygulama Ä°konunu YÃ¼kle
    let icon = if let Ok(img) = image::load_from_memory(ICON_PNG) {
        let rgba = img.to_rgba8();
        let (width, height) = rgba.dimensions();
        tao::window::Icon::from_rgba(rgba.into_raw(), width, height).ok()
    } else {
        None
    };

    let mut window_builder = WindowBuilder::new()
        .with_title("Not Defterim - MasaÃ¼stÃ¼")
        .with_inner_size(tao::dpi::LogicalSize::new(1200.0, 800.0))
        .with_min_inner_size(tao::dpi::LogicalSize::new(880.0, 560.0));

    if let Some(ic) = icon {
        window_builder = window_builder.with_window_icon(Some(ic));
    }

    let window = window_builder.build(&event_loop).unwrap();

    // Windows Native Anti-Screenshot (AÃ§Ä±lÄ±ÅŸta varsayÄ±lan olarak WDA_EXCLUDEFROMCAPTURE etkinleÅŸtirilir)
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

    #[cfg(windows)]
    let window_hwnd = {
        use tao::platform::windows::WindowExtWindows;
        window.hwnd() as isize
    };
    #[cfg(not(windows))]
    let window_hwnd: isize = 0;

    let db_http = db.clone();
    let crypto_http = crypto.clone();
    let sync_http = sync_server.clone();
    let proxy_http = proxy.clone();

    let db_ipc = db.clone();
    let crypto_ipc = crypto.clone();
    let sync_ipc = sync_server.clone();
    let proxy_ipc = proxy.clone();

    // sync:statusChanged olaylarÄ±nÄ± Wry WebView'e push eden dinleyici
    let proxy_sync = proxy.clone();
    sync_server.on_status_changed(move |event| {
        let json_str = serde_json::to_string(event).unwrap_or_default();
        let script = format!("if (window.__IPC_EMIT__) {{ window.__IPC_EMIT__({}); }}", json_str);
        let _ = proxy_sync.send_event(UserEvent::EvaluateScript(script));
    });

    // Wry WebView2 BaÅŸlatma YapÄ±landÄ±rmasÄ±
    let webview = WebViewBuilder::new()
        .with_initialization_script(INIT_SCRIPT)
        // 1. with_ipc_handler ile window.ipc.postMessage isteklerini dinle
        .with_ipc_handler(move |request: wry::Request<String>| {
            let body = request.body().clone();
            let db = db_ipc.clone();
            let crypto = crypto_ipc.clone();
            let sync = sync_ipc.clone();
            let proxy = proxy_ipc.clone();

            // 2. Ä°stekleri arka plan thread havuzuna devret (std::thread::spawn)
            std::thread::spawn(move || {
                let req_result: Result<IpcRequest, _> = serde_json::from_str(&body);
                let response = match req_result {
                    Ok(req) => execute_ipc_action(
                        &req.action,
                        req.payload,
                        &req.request_id,
                        &db,
                        &crypto,
                        &sync,
                        &proxy,
                    ),
                    Err(e) => {
                        // Geriye dÃ¶nÃ¼k uyumluluk: eski { cmd, payload } zarfÄ±
                        if let Ok(legacy) = serde_json::from_str::<LegacyApiRequest>(&body) {
                            execute_ipc_action(
                                &legacy.cmd,
                                legacy.payload.unwrap_or(serde_json::Value::Null),
                                "legacy_req",
                                &db,
                                &crypto,
                                &sync,
                                &proxy,
                            )
                        } else {
                            IpcResponse::err(
                                "unknown",
                                "INVALID_JSON",
                                format!("JSON parse hatasÄ±: {}", e),
                            )
                        }
                    }
                };

                let resp_json = serde_json::to_string(&response).unwrap_or_else(|_| "{}".to_string());
                let script = format!(
                    "if (window.__IPC_RESOLVE__) {{ window.__IPC_RESOLVE__({}); }}",
                    resp_json
                );

                // UI thread'ine ilet
                let _ = proxy.send_event(UserEvent::EvaluateScript(script));
            });
        })
        // 4. notdefterim:// Ã¶zel protokolÃ¼ ve yerel dosya sunucusu yapÄ±landÄ±rmasÄ±
        .with_custom_protocol("notdefterim".into(), move |_id, request| {
            let path = request.uri().path();
            let method = request.method();

            if path == "/api" && *method == Method::POST {
                let resp_bytes = handle_legacy_http_request(
                    request.body(),
                    &db_http,
                    &crypto_http,
                    &sync_http,
                    &proxy_http,
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

    let mut webview_opt = Some(webview);

    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        match event {
            Event::UserEvent(user_event) => match user_event {
                UserEvent::EvaluateScript(script) => {
                    if let Some(ref wv) = webview_opt {
                        let _ = wv.evaluate_script(&script);
                    }
                }
                UserEvent::SetAntiScreenshot(enable) => {
                    #[cfg(windows)]
                    {
                        use windows::Win32::Foundation::HWND;
                        use windows::Win32::UI::WindowsAndMessaging::{
                            SetWindowDisplayAffinity, WDA_EXCLUDEFROMCAPTURE, WDA_NONE,
                        };
                        let hwnd = HWND(window_hwnd as _);
                        let affinity = if enable {
                            WDA_EXCLUDEFROMCAPTURE
                        } else {
                            WDA_NONE
                        };
                        unsafe {
                            SetWindowDisplayAffinity(hwnd, affinity).ok();
                        }
                    }
                }
                UserEvent::Minimize => {
                    window.set_minimized(true);
                }
                UserEvent::Maximize => {
                    window.set_maximized(!window.is_maximized());
                }
                UserEvent::Close => {
                    sync_server.stop();
                    *control_flow = ControlFlow::Exit;
                }
            },
            Event::WindowEvent {
                event: WindowEvent::CloseRequested,
                ..
            } => {
                sync_server.stop();
                *control_flow = ControlFlow::Exit;
            }
            _ => {}
        }
    });
}
