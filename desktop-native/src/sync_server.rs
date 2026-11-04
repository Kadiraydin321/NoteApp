use crate::backup::BackupManager;
use crate::db::Database;
use crate::models::{Category, Note};
use rand::RngExt;
use serde::{Deserialize, Serialize};
use std::io::Read;
use std::net::UdpSocket;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use tiny_http::{Header, Method, Response, Server, StatusCode};

#[derive(Serialize, Deserialize)]
pub struct SyncPayload {
    pub notes: Vec<Note>,
    pub categories: Vec<Category>,
    pub timestamp: i64,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct ServerState {
    pub is_running: bool,
    pub local_ip: String,
    pub port: u16,
    pub pairing_pin: String,
    pub last_message: String,
}

pub struct SyncServer {
    is_running: Arc<AtomicBool>,
    pairing_pin: Arc<Mutex<String>>,
    last_message: Arc<Mutex<String>>,
    db: Arc<Database>,
}

impl SyncServer {
    pub fn new(db: Arc<Database>) -> Self {
        let pin = format!("{:04}", rand::rng().random_range(1000..=9999));
        SyncServer {
            is_running: Arc::new(AtomicBool::new(false)),
            pairing_pin: Arc::new(Mutex::new(pin)),
            last_message: Arc::new(Mutex::new("Hazır".to_string())),
            db,
        }
    }

    pub fn get_local_ip() -> String {
        let socket = match UdpSocket::bind("0.0.0.0:0") {
            Ok(s) => s,
            Err(_) => return "127.0.0.1".to_string(),
        };
        if socket.connect("8.8.8.8:80").is_ok() {
            if let Ok(addr) = socket.local_addr() {
                return addr.ip().to_string();
            }
        }
        "127.0.0.1".to_string()
    }

    pub fn get_state(&self) -> ServerState {
        ServerState {
            is_running: self.is_running.load(Ordering::SeqCst),
            local_ip: Self::get_local_ip(),
            port: 8765,
            pairing_pin: self.pairing_pin.lock().unwrap_or_else(|e| e.into_inner()).clone(),
            last_message: self.last_message.lock().unwrap_or_else(|e| e.into_inner()).clone(),
        }
    }

    pub fn refresh_pin(&self) -> String {
        let new_pin = format!("{:04}", rand::rng().random_range(1000..=9999));
        let mut p = self.pairing_pin.lock().unwrap_or_else(|e| e.into_inner());
        *p = new_pin.clone();
        new_pin
    }

    pub fn start(&self) {
        if self.is_running.load(Ordering::SeqCst) {
            return;
        }

        self.is_running.store(true, Ordering::SeqCst);
        let is_running_clone = self.is_running.clone();
        let pin_clone = self.pairing_pin.clone();
        let msg_clone = self.last_message.clone();
        let db_clone = self.db.clone();

        thread::spawn(move || {
            let server = match Server::http("0.0.0.0:8765") {
                Ok(s) => s,
                Err(e) => {
                    if let Ok(mut m) = msg_clone.lock() {
                        *m = format!("Sunucu hatası: {}", e);
                    }
                    is_running_clone.store(false, Ordering::SeqCst);
                    return;
                }
            };

            if let Ok(mut m) = msg_clone.lock() {
                *m = "Sunucu 8765 portunda dinleniyor".to_string();
            }

            while is_running_clone.load(Ordering::SeqCst) {
                match server.recv_timeout(std::time::Duration::from_millis(500)) {
                    Ok(Some(mut request)) => {
                        let url = request.url().to_string();
                        let method = request.method().clone();
                        let current_pin = pin_clone.lock().unwrap_or_else(|e| e.into_inner()).clone();

                        // Standart CORS Başlıkları
                        let cors_header = match Header::from_bytes(&b"Access-Control-Allow-Origin"[..], &b"*"[..]) {
                            Ok(h) => h,
                            Err(_) => continue,
                        };
                        let json_header = match Header::from_bytes(&b"Content-Type"[..], &b"application/json; charset=utf-8"[..]) {
                            Ok(h) => h,
                            Err(_) => continue,
                        };

                        // CORS Preflight (OPTIONS) Desteği
                        if method == Method::Options {
                            let allow_methods = match Header::from_bytes(&b"Access-Control-Allow-Methods"[..], &b"GET, POST, OPTIONS"[..]) {
                                Ok(h) => h,
                                Err(_) => continue,
                            };
                            let allow_headers = match Header::from_bytes(&b"Access-Control-Allow-Headers"[..], &b"*"[..]) {
                                Ok(h) => h,
                                Err(_) => continue,
                            };
                            let resp = Response::empty(StatusCode(204))
                                .with_header(cors_header)
                                .with_header(allow_methods)
                                .with_header(allow_headers);
                            request.respond(resp).ok();
                            continue;
                        }

                        // URL Path Ayrıştırma (Query string öncesini al)
                        let path = url.split('?').next().unwrap_or(&url);

                        // 1. Durum / Health Kontrolü (/status ve /api/status)
                        // Android istemcisi '/api/status' yanıtında '{ "status": "ok", "appName": "NotDefterim Masaüstü", "notesCount": X, "requiresPin": true }' bekliyor.
                        let is_status_url = path == "/" || path == "/status" || path == "/api/status" || path == "/status/" || path == "/api/status/";
                        if is_status_url && method == Method::Get {
                            let notes_count = db_clone.get_all_notes().map(|n| n.len()).unwrap_or(0);
                            let status_obj = serde_json::json!({
                                "status": "ok",
                                "appName": "NotDefterim Masaüstü",
                                "notesCount": notes_count,
                                "requiresPin": true,
                                "app": "NotDefterim Desktop",
                                "version": "1.0"
                            });
                            let resp = Response::from_string(status_obj.to_string())
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            continue;
                        }

                        // PIN Doğrulama: X-Sync-PIN, X-Pairing-PIN ve URL sorgusundaki 'pin=...' parametresini büyük/küçük harf duyarsız kontrol et
                        let mut pin_valid = false;
                        for h in request.headers() {
                            let field_str = h.field.as_str();
                            if field_str.eq_ignore_ascii_case("X-Sync-PIN") || field_str.eq_ignore_ascii_case("X-Pairing-PIN") {
                                if h.value.as_str().trim() == current_pin.trim() {
                                    pin_valid = true;
                                    break;
                                }
                            }
                        }

                        if !pin_valid {
                            let url_lower = url.to_ascii_lowercase();
                            let pin_target = current_pin.to_ascii_lowercase();
                            if url_lower.contains(&format!("pin={}", pin_target)) {
                                pin_valid = true;
                            }
                        }

                        // Durum rotaları haricinde PIN doğrulanamadıysa 401 Unauthorized dön
                        if !pin_valid {
                            let resp = Response::from_string(r#"{"error":"Geçersiz veya eksik PIN kodu"}"#)
                                .with_status_code(StatusCode(401))
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            continue;
                        }

                        // 2. ZIP İndirme (Download / Export)
                        // Android DesktopSyncClient '/api/sync/download' rotasını çağırır.
                        // Ayrıca 'Accept: application/zip' veya '/sync/download' istekleri de ZIP olarak döndürülür.
                        let is_zip_accept = request.headers().iter().any(|h| {
                            h.field.as_str().eq_ignore_ascii_case("Accept") && h.value.as_str().contains("application/zip")
                        });

                        if method == Method::Get && (path == "/api/sync/download" || path == "/sync/download" || (path.starts_with("/sync/export") && is_zip_accept)) {
                            match BackupManager::create_backup_zip_buffer(&db_clone) {
                                Ok(zip_bytes) => {
                                    let zip_header = match Header::from_bytes(&b"Content-Type"[..], &b"application/zip"[..]) {
                                        Ok(h) => h,
                                        Err(_) => continue,
                                    };
                                    let disposition_header = match Header::from_bytes(
                                        &b"Content-Disposition"[..],
                                        &b"attachment; filename=\"NoteApp_Sync.zip\""[..],
                                    ) {
                                        Ok(h) => h,
                                        Err(_) => continue,
                                    };

                                    let notes_count = db_clone.get_all_notes().map(|n| n.len()).unwrap_or(0);
                                    let resp = Response::from_data(zip_bytes)
                                        .with_header(cors_header)
                                        .with_header(zip_header)
                                        .with_header(disposition_header);
                                    request.respond(resp).ok();

                                    if let Ok(mut m) = msg_clone.lock() {
                                        *m = format!("{} not ZIP olarak telefona aktarıldı", notes_count);
                                    }
                                    continue;
                                }
                                Err(e) => {
                                    let err_body = serde_json::json!({
                                        "error": format!("ZIP yedeği oluşturulamadı: {}", e)
                                    }).to_string();
                                    let resp = Response::from_string(err_body)
                                        .with_status_code(StatusCode(500))
                                        .with_header(cors_header)
                                        .with_header(json_header);
                                    request.respond(resp).ok();
                                    continue;
                                }
                            }
                        }

                        // 3. Klasik JSON Not Dışa Aktarma (Export)
                        // /sync/export veya /api/notes rotası JSON olarak notları ve kategorileri döner
                        if method == Method::Get && (path.starts_with("/sync/export") || path.starts_with("/api/notes")) {
                            let notes = db_clone.get_all_notes().unwrap_or_default();
                            let categories = db_clone.get_categories().unwrap_or_default();
                            let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as i64;
                            let payload = SyncPayload {
                                notes,
                                categories,
                                timestamp: now,
                            };
                            let body = serde_json::to_string(&payload).unwrap_or_default();
                            let resp = Response::from_string(body)
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            if let Ok(mut m) = msg_clone.lock() {
                                *m = "Notlar telefona aktarıldı (JSON)".to_string();
                            }
                            continue;
                        }

                        // 4. Veri İçe Aktarma / Yükleme (Upload / Import)
                        // Hem ZIP (/api/sync/upload, /sync/upload) hem de JSON (/sync/import, /api/sync) desteklenir
                        if method == Method::Post && (path.starts_with("/sync/import") || path.starts_with("/api/sync") || path == "/api/sync/upload" || path == "/sync/upload") {
                            const MAX_BODY_SIZE: u64 = 5 * 1024 * 1024; // Maksimum 5 MB Güvenlik Limiti

                            // Güvenli İstek Gövdesi Okuma (DDoS / Memory Exhaustion Koruması)
                            let mut body_bytes = Vec::new();
                            let read_res = request.as_reader().take(MAX_BODY_SIZE + 1).read_to_end(&mut body_bytes);
                            if read_res.is_err() {
                                let resp = Response::from_string(r#"{"error":"İstek gövdesi okunamadı"}"#)
                                    .with_status_code(StatusCode(400))
                                    .with_header(cors_header)
                                    .with_header(json_header);
                                request.respond(resp).ok();
                                continue;
                            }

                            if body_bytes.len() > MAX_BODY_SIZE as usize {
                                let resp = Response::from_string(r#"{"error":"Payload too large (maksimum 5MB)"}"#)
                                    .with_status_code(StatusCode(413))
                                    .with_header(cors_header)
                                    .with_header(json_header);
                                request.respond(resp).ok();
                                continue;
                            }

                            // İçerik tipini tespit et: ZIP mi yoksa JSON mı?
                            let is_zip_content = request.headers().iter().any(|h| {
                                h.field.as_str().eq_ignore_ascii_case("Content-Type") && h.value.as_str().contains("application/zip")
                            }) || path.ends_with("/upload") || (body_bytes.len() >= 4 && &body_bytes[0..4] == b"PK\x03\x04");

                            if is_zip_content {
                                match BackupManager::restore_backup_zip_buffer(&db_clone, &body_bytes) {
                                    Ok((notes_count, cats_count)) => {
                                        if let Ok(mut m) = msg_clone.lock() {
                                            *m = format!("Telefondan {} not ve {} kategori ZIP ile eşitlendi", notes_count, cats_count);
                                        }
                                        let resp_body = serde_json::json!({
                                            "success": true,
                                            "notesCount": notes_count,
                                            "categoriesCount": cats_count,
                                            "message": "Eşitleme başarıyla tamamlandı"
                                        }).to_string();
                                        let resp = Response::from_string(resp_body)
                                            .with_header(cors_header)
                                            .with_header(json_header);
                                        request.respond(resp).ok();
                                        continue;
                                    }
                                    Err(e) => {
                                        let err_body = serde_json::json!({
                                            "success": false,
                                            "error": format!("ZIP yedeği geri yüklenemedi: {}", e)
                                        }).to_string();
                                        let resp = Response::from_string(err_body)
                                            .with_status_code(StatusCode(400))
                                            .with_header(cors_header)
                                            .with_header(json_header);
                                        request.respond(resp).ok();
                                        continue;
                                    }
                                }
                            } else {
                                // JSON Ayrıştırma Denemesi
                                if let Ok(payload) = serde_json::from_slice::<SyncPayload>(&body_bytes) {
                                    let count_notes = payload.notes.len();
                                    let count_cats = payload.categories.len();
                                    if db_clone.replace_all_notes(&payload.notes, &payload.categories).is_ok() {
                                        if let Ok(mut m) = msg_clone.lock() {
                                            *m = format!("Telefondan {} not ve {} kategori eşitlendi", count_notes, count_cats);
                                        }
                                        let resp = Response::from_string(r#"{"success":true,"message":"Eşitleme tamamlandı"}"#)
                                            .with_header(cors_header)
                                            .with_header(json_header);
                                        request.respond(resp).ok();
                                        continue;
                                    }
                                }

                                let resp = Response::from_string(r#"{"error":"Geçersiz veri paketi (JSON veya ZIP bekleniyor)"}"#)
                                    .with_status_code(StatusCode(400))
                                    .with_header(cors_header)
                                    .with_header(json_header);
                                request.respond(resp).ok();
                                continue;
                            }
                        }

                        // Bilinmeyen / Eşleşmeyen Rotalar
                        let resp = Response::from_string(r#"{"error":"Bilinmeyen istek"}"#)
                            .with_status_code(StatusCode(404))
                            .with_header(cors_header)
                            .with_header(json_header);
                        request.respond(resp).ok();
                    }
                    Ok(None) => {}
                    Err(_) => break,
                }
            }

            if let Ok(mut m) = msg_clone.lock() {
                *m = "Durduruldu".to_string();
            }
        });
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::SeqCst);
    }
}
