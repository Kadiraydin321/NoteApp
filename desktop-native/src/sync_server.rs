use crate::backup::BackupManager;
use crate::db::Database;
use crate::models::{Category, Note};
use rand::RngExt;
use serde::{Deserialize, Serialize};
use std::io::Read;
use std::net::{IpAddr, SocketAddr, UdpSocket};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{Receiver, Sender};
use std::sync::{Arc, Mutex};
use std::thread;
use tiny_http::{Header, Method, Response, Server, StatusCode};

#[derive(Serialize, Deserialize)]
pub struct SyncPayload {
    pub notes: Vec<Note>,
    pub categories: Vec<Category>,
    pub timestamp: i64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ServerState {
    pub is_running: bool,
    pub local_ip: String,
    pub port: u16,
    pub pairing_pin: String,
    pub last_message: String,
    #[serde(default)]
    pub connected_clients: usize,
    #[serde(default)]
    pub notes_count: usize,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SyncStatusData {
    #[serde(rename = "isRunning")]
    pub is_running: bool,
    pub port: u16,
    pub pin: String,
    #[serde(rename = "connectedClients")]
    pub connected_clients: usize,
    #[serde(rename = "lastMessage")]
    pub last_message: String,
    #[serde(rename = "notesCount")]
    pub notes_count: usize,
    #[serde(rename = "localIp")]
    pub local_ip: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SyncStatusEvent {
    pub event: String,
    pub data: SyncStatusData,
}

pub type StatusChangeCallback = Box<dyn Fn(&SyncStatusEvent) + Send + Sync + 'static>;

pub struct SyncServer {
    is_running: Arc<AtomicBool>,
    pairing_pin: Arc<Mutex<String>>,
    last_message: Arc<Mutex<String>>,
    connected_clients: Arc<Mutex<usize>>,
    event_listeners: Arc<Mutex<Vec<StatusChangeCallback>>>,
    event_senders: Arc<Mutex<Vec<Sender<SyncStatusEvent>>>>,
    db: Arc<Database>,
}

/// Zamanlama saldırılarını (timing attack) önlemek için sabit zamanlı (constant-time) PIN doğrulaması
pub fn constant_time_pin_match(input: &str, expected: &str) -> bool {
    let in_bytes = input.trim().as_bytes();
    let exp_bytes = expected.trim().as_bytes();

    if in_bytes.len() != exp_bytes.len() {
        return false;
    }

    let mut diff = 0u8;
    for (a, b) in in_bytes.iter().zip(exp_bytes.iter()) {
        diff |= a ^ b;
    }
    diff == 0
}

/// Yalnızca yerel alt ağdan (RFC 1918) ve loopback adreslerden gelen isteklere izin verir
pub fn is_allowed_client_ip(remote_addr: &SocketAddr) -> bool {
    match remote_addr.ip() {
        IpAddr::V4(v4) => {
            let octets = v4.octets();
            // Loopback (127.0.0.0/8)
            octets[0] == 127
            // RFC 1918 (10.0.0.0/8)
            || octets[0] == 10
            // RFC 1918 (172.16.0.0/12 -> 172.16.0.0 - 172.31.255.255)
            || (octets[0] == 172 && (16..=31).contains(&octets[1]))
            // RFC 1918 (192.168.0.0/16)
            || (octets[0] == 192 && octets[1] == 168)
            // RFC 3927 Link-local (169.254.0.0/16)
            || (octets[0] == 169 && octets[1] == 254)
        }
        IpAddr::V6(v6) => {
            v6.is_loopback()
        }
    }
}

/// İstek başlıklarından (X-Sync-PIN, X-Pairing-PIN) veya URL sorgu parametresinden (?pin=...) PIN kodunu çeker
fn extract_pin_from_request(request: &tiny_http::Request, url: &str) -> Option<String> {
    for h in request.headers() {
        let field = h.field.as_str();
        if field.eq_ignore_ascii_case("X-Sync-PIN") || field.eq_ignore_ascii_case("X-Pairing-PIN") {
            let val = h.value.as_str().trim();
            if !val.is_empty() {
                return Some(val.to_string());
            }
        }
    }

    if let Some(pos) = url.find("pin=") {
        let query_part = &url[pos + 4..];
        let pin = query_part.split('&').next().unwrap_or("").trim();
        if !pin.is_empty() {
            return Some(pin.to_string());
        }
    }
    None
}

impl SyncServer {
    pub fn new(db: Arc<Database>) -> Self {
        let pin = format!("{:04}", rand::rng().random_range(1000..=9999));
        SyncServer {
            is_running: Arc::new(AtomicBool::new(false)),
            pairing_pin: Arc::new(Mutex::new(pin)),
            last_message: Arc::new(Mutex::new("Hazır".to_string())),
            connected_clients: Arc::new(Mutex::new(0)),
            event_listeners: Arc::new(Mutex::new(Vec::new())),
            event_senders: Arc::new(Mutex::new(Vec::new())),
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
        let notes_count = self.db.get_all_notes().map(|n| n.len()).unwrap_or(0);
        ServerState {
            is_running: self.is_running.load(Ordering::SeqCst),
            local_ip: Self::get_local_ip(),
            port: 8765,
            pairing_pin: self.pairing_pin.lock().unwrap_or_else(|e| e.into_inner()).clone(),
            last_message: self.last_message.lock().unwrap_or_else(|e| e.into_inner()).clone(),
            connected_clients: self.connected_clients.lock().unwrap_or_else(|e| e.into_inner()).clone(),
            notes_count,
        }
    }

    /// Ana iş parçacığı veya UI için durum değişim olay kanalı (mpsc receiver) oluşturur
    pub fn create_event_channel(&self) -> Receiver<SyncStatusEvent> {
        let (tx, rx) = std::sync::mpsc::channel();
        if let Ok(mut senders) = self.event_senders.lock() {
            senders.push(tx);
        }
        rx
    }

    /// Durum değiştiğinde çağrılacak closure dinleyicisi ekler
    pub fn on_status_changed<F>(&self, callback: F)
    where
        F: Fn(&SyncStatusEvent) + Send + Sync + 'static,
    {
        if let Ok(mut listeners) = self.event_listeners.lock() {
            listeners.push(Box::new(callback));
        }
    }

    /// sync:statusChanged olayını tüm kayıtlı dinleyicilere ve mpsc kanallarına iletir
    fn emit_status_event(
        is_running: bool,
        pin: &str,
        last_message: &str,
        connected_clients: usize,
        notes_count: usize,
        listeners: &Arc<Mutex<Vec<StatusChangeCallback>>>,
        senders: &Arc<Mutex<Vec<Sender<SyncStatusEvent>>>>,
    ) {
        let event = SyncStatusEvent {
            event: "sync:statusChanged".to_string(),
            data: SyncStatusData {
                is_running,
                port: 8765,
                pin: pin.to_string(),
                connected_clients,
                last_message: last_message.to_string(),
                notes_count,
                local_ip: Self::get_local_ip(),
            },
        };

        if let Ok(cbs) = listeners.lock() {
            for cb in cbs.iter() {
                cb(&event);
            }
        }

        if let Ok(mut txs) = senders.lock() {
            txs.retain(|tx| tx.send(event.clone()).is_ok());
        }
    }

    /// Mevcut durumla olay fırlatır
    pub fn emit_status(&self) {
        let is_running = self.is_running.load(Ordering::SeqCst);
        let pin = self.pairing_pin.lock().unwrap_or_else(|e| e.into_inner()).clone();
        let msg = self.last_message.lock().unwrap_or_else(|e| e.into_inner()).clone();
        let clients = self.connected_clients.lock().unwrap_or_else(|e| e.into_inner()).clone();
        let notes_count = self.db.get_all_notes().map(|n| n.len()).unwrap_or(0);

        Self::emit_status_event(
            is_running,
            &pin,
            &msg,
            clients,
            notes_count,
            &self.event_listeners,
            &self.event_senders,
        );
    }

    /// Dinamik 4 haneli PIN üretimi ve yenilenmesi
    pub fn refresh_pin(&self) -> String {
        let new_pin = format!("{:04}", rand::rng().random_range(1000..=9999));
        {
            let mut p = self.pairing_pin.lock().unwrap_or_else(|e| e.into_inner());
            *p = new_pin.clone();
        }
        {
            if let Ok(mut m) = self.last_message.lock() {
                *m = format!("PIN yenilendi: {}", new_pin);
            }
        }
        self.emit_status();
        new_pin
    }

    pub fn start(&self) {
        if self.is_running.load(Ordering::SeqCst) {
            return;
        }

        self.is_running.store(true, Ordering::SeqCst);
        {
            if let Ok(mut m) = self.last_message.lock() {
                *m = "Sunucu 8765 portunda başlatılıyor...".to_string();
            }
        }
        self.emit_status();

        let is_running_clone = self.is_running.clone();
        let pin_clone = self.pairing_pin.clone();
        let msg_clone = self.last_message.clone();
        let clients_clone = self.connected_clients.clone();
        let listeners_clone = self.event_listeners.clone();
        let senders_clone = self.event_senders.clone();
        let db_clone = self.db.clone();

        thread::spawn(move || {
            let server = match Server::http("0.0.0.0:8765") {
                Ok(s) => s,
                Err(e) => {
                    let err_msg = format!("Sunucu hatası: {}", e);
                    if let Ok(mut m) = msg_clone.lock() {
                        *m = err_msg.clone();
                    }
                    is_running_clone.store(false, Ordering::SeqCst);
                    let current_pin = pin_clone.lock().unwrap_or_else(|e| e.into_inner()).clone();
                    let notes_count = db_clone.get_all_notes().map(|n| n.len()).unwrap_or(0);
                    Self::emit_status_event(
                        false,
                        &current_pin,
                        &err_msg,
                        0,
                        notes_count,
                        &listeners_clone,
                        &senders_clone,
                    );
                    return;
                }
            };

            let start_msg = "Sunucu 8765 portunda dinleniyor".to_string();
            if let Ok(mut m) = msg_clone.lock() {
                *m = start_msg.clone();
            }
            let initial_pin = pin_clone.lock().unwrap_or_else(|e| e.into_inner()).clone();
            let initial_notes = db_clone.get_all_notes().map(|n| n.len()).unwrap_or(0);
            Self::emit_status_event(
                true,
                &initial_pin,
                &start_msg,
                0,
                initial_notes,
                &listeners_clone,
                &senders_clone,
            );

            while is_running_clone.load(Ordering::SeqCst) {
                match server.recv_timeout(std::time::Duration::from_millis(500)) {
                    Ok(Some(mut request)) => {
                        let remote_addr = *request.remote_addr();
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

                        // Güvenlik Kuralı: Yerel alt ağ (LAN) ve loopback haricindeki erişimleri engelle
                        if !is_allowed_client_ip(&remote_addr) {
                            let resp = Response::from_string(r#"{"error":"Yalnızca yerel alt ağdan (LAN) erişime izin verilir"}"#)
                                .with_status_code(StatusCode(403))
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            continue;
                        }

                        // URL Path Ayrıştırma (Query string öncesini al)
                        let path = url.split('?').next().unwrap_or(&url);

                        // 1. Durum / Health Kontrolü (/status ve /api/status)
                        // Android istemcisi '/api/status' yanıtında '{ "status": "ok", "appName": "NotDefterim Masaüstü", "notesCount": X, "requiresPin": true, "version": "1.4.4", "port": 8765 }' bekliyor.
                        let is_status_url = path == "/" || path == "/status" || path == "/api/status" || path == "/status/" || path == "/api/status/";
                        if is_status_url && method == Method::Get {
                            let notes_count = db_clone.get_all_notes().map(|n| n.len()).unwrap_or(0);
                            let status_obj = serde_json::json!({
                                "status": "ok",
                                "appName": "NotDefterim Masaüstü",
                                "version": "1.4.4",
                                "notesCount": notes_count,
                                "port": 8765,
                                "requiresPin": true
                            });
                            let resp = Response::from_string(status_obj.to_string())
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            continue;
                        }

                        // PIN Doğrulama: X-Sync-PIN, X-Pairing-PIN ve URL sorgusundaki 'pin=...' parametresini sabit zamanlı (constant-time) kontrol et
                        let maybe_pin = extract_pin_from_request(&request, &url);
                        let pin_valid = match maybe_pin {
                            Some(ref p) => constant_time_pin_match(p, &current_pin),
                            None => false,
                        };

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

                                    let active_clients = {
                                        let mut c = clients_clone.lock().unwrap_or_else(|e| e.into_inner());
                                        *c = (*c).saturating_add(1);
                                        *c
                                    };
                                    let msg = format!("{} not ve veritabanı ZIP olarak telefona aktarıldı", notes_count);
                                    if let Ok(mut m) = msg_clone.lock() {
                                        *m = msg.clone();
                                    }
                                    Self::emit_status_event(
                                        true,
                                        &current_pin,
                                        &msg,
                                        active_clients,
                                        notes_count,
                                        &listeners_clone,
                                        &senders_clone,
                                    );
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

                        // 3. Klasik JSON Not Dışa Aktarma (Export Fallback)
                        if method == Method::Get && (path.starts_with("/sync/export") || path.starts_with("/api/notes")) {
                            let notes = db_clone.get_all_notes().unwrap_or_default();
                            let categories = db_clone.get_categories().unwrap_or_default();
                            let count = notes.len();
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

                            let active_clients = {
                                let mut c = clients_clone.lock().unwrap_or_else(|e| e.into_inner());
                                *c = (*c).saturating_add(1);
                                *c
                            };
                            let msg = format!("{} not JSON formatında telefona aktarıldı", count);
                            if let Ok(mut m) = msg_clone.lock() {
                                *m = msg.clone();
                            }
                            Self::emit_status_event(
                                true,
                                &current_pin,
                                &msg,
                                active_clients,
                                count,
                                &listeners_clone,
                                &senders_clone,
                            );
                            continue;
                        }

                        // 4. Veri İçe Aktarma / Yükleme (Upload / Import)
                        // Hem ZIP (/api/sync/upload, /sync/upload) hem de JSON (/sync/import, /api/sync) desteklenir
                        if method == Method::Post && (path.starts_with("/sync/import") || path.starts_with("/api/sync") || path == "/api/sync/upload" || path == "/sync/upload") {
                            const MAX_BODY_SIZE: u64 = 50 * 1024 * 1024; // Maksimum 50 MB Güvenlik Limiti (ekli fotoğraflar ve medya için)

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
                                let resp = Response::from_string(r#"{"error":"Payload too large (maksimum 50MB)"}"#)
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
                                        let active_clients = {
                                            let mut c = clients_clone.lock().unwrap_or_else(|e| e.into_inner());
                                            *c = (*c).saturating_add(1);
                                            *c
                                        };
                                        let msg = format!("Telefondan {} not ve {} kategori ZIP ile eşitlendi", notes_count, cats_count);
                                        if let Ok(mut m) = msg_clone.lock() {
                                            *m = msg.clone();
                                        }
                                        Self::emit_status_event(
                                            true,
                                            &current_pin,
                                            &msg,
                                            active_clients,
                                            notes_count,
                                            &listeners_clone,
                                            &senders_clone,
                                        );

                                        let resp_body = serde_json::json!({
                                            "success": true,
                                            "status": "success",
                                            "importedNotes": notes_count,
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
                                            "status": "error",
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
                                        let active_clients = {
                                            let mut c = clients_clone.lock().unwrap_or_else(|e| e.into_inner());
                                            *c = (*c).saturating_add(1);
                                            *c
                                        };
                                        let msg = format!("Telefondan {} not ve {} kategori eşitlendi", count_notes, count_cats);
                                        if let Ok(mut m) = msg_clone.lock() {
                                            *m = msg.clone();
                                        }
                                        Self::emit_status_event(
                                            true,
                                            &current_pin,
                                            &msg,
                                            active_clients,
                                            count_notes,
                                            &listeners_clone,
                                            &senders_clone,
                                        );

                                        let resp_body = serde_json::json!({
                                            "success": true,
                                            "status": "success",
                                            "importedNotes": count_notes,
                                            "notesCount": count_notes,
                                            "categoriesCount": count_cats,
                                            "message": "Eşitleme tamamlandı"
                                        }).to_string();
                                        let resp = Response::from_string(resp_body)
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

            let stop_msg = "Sunucu durduruldu".to_string();
            if let Ok(mut m) = msg_clone.lock() {
                *m = stop_msg.clone();
            }
            let final_pin = pin_clone.lock().unwrap_or_else(|e| e.into_inner()).clone();
            let final_notes = db_clone.get_all_notes().map(|n| n.len()).unwrap_or(0);
            Self::emit_status_event(
                false,
                &final_pin,
                &stop_msg,
                0,
                final_notes,
                &listeners_clone,
                &senders_clone,
            );
        });
    }

    pub fn stop(&self) {
        if !self.is_running.load(Ordering::SeqCst) {
            return;
        }
        self.is_running.store(false, Ordering::SeqCst);
        let stop_msg = "Sunucu durduruldu".to_string();
        if let Ok(mut m) = self.last_message.lock() {
            *m = stop_msg.clone();
        }
        if let Ok(mut c) = self.connected_clients.lock() {
            *c = 0;
        }
        self.emit_status();
    }
}
