use crate::backup::BackupManager;
use crate::db::Database;
use crate::models::{Category, Note};
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use rand::RngExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::io::Read;
use std::net::{IpAddr, SocketAddr, UdpSocket};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{Receiver, Sender};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;
use tiny_http::{Header, Method, Response, Server, StatusCode};

const PAIRING_TOKEN_TTL_MS: u64 = 5 * 60 * 1000;
const SESSION_TOKEN_TTL_MS: u64 = 30 * 24 * 60 * 60 * 1000;
const MAX_PENDING_PAIRINGS: usize = 8;
const MAX_SESSIONS: usize = 128;
const MDNS_SERVICE_TYPE: &str = "_notdefterim-sync._tcp.local.";

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

#[derive(Clone)]
struct HashedCredential {
    digest: [u8; 32],
    expires_at: u64,
}

#[derive(Default)]
struct PairingCredentials {
    pending_pairings: Vec<HashedCredential>,
    sessions: Vec<HashedCredential>,
}

impl PairingCredentials {
    fn clear(&mut self) {
        self.pending_pairings.clear();
        self.sessions.clear();
    }

    fn issue_pairing(&mut self, digest: [u8; 32], expires_at: u64, now: u64) {
        self.pending_pairings.retain(|entry| entry.expires_at > now);
        if self.pending_pairings.len() >= MAX_PENDING_PAIRINGS {
            self.pending_pairings.remove(0);
        }
        self.pending_pairings.push(HashedCredential { digest, expires_at });
    }

    fn exchange_pairing(
        &mut self,
        pairing_digest: [u8; 32],
        now: u64,
        session_digest: [u8; 32],
        session_expires_at: u64,
    ) -> bool {
        self.pending_pairings.retain(|entry| entry.expires_at > now);
        let mut matched_index = None;
        for (index, entry) in self.pending_pairings.iter().enumerate() {
            let matched = constant_time_digest_match(&entry.digest, &pairing_digest);
            if matched {
                matched_index = Some(index);
            }
        }
        let Some(index) = matched_index else {
            return false;
        };
        self.pending_pairings.remove(index);
        self.sessions.retain(|entry| entry.expires_at > now);
        if self.sessions.len() >= MAX_SESSIONS {
            let oldest = self.sessions.iter().enumerate().min_by_key(|(_, entry)| entry.expires_at).map(|(index, _)| index);
            if let Some(index) = oldest {
                self.sessions.remove(index);
            }
        }
        self.sessions.push(HashedCredential {
            digest: session_digest,
            expires_at: session_expires_at,
        });
        true
    }

    fn has_valid_session(&self, digest: &[u8; 32], now: u64) -> bool {
        let mut matched = false;
        for session in &self.sessions {
            matched |= session.expires_at > now
                && constant_time_digest_match(&session.digest, digest);
        }
        matched
    }
}

#[derive(Serialize)]
pub struct PairingTokenInfo {
    pub pairing_token: String,
    pub expires_at: u64,
}

#[derive(Serialize)]
struct SessionTokenInfo {
    version: u8,
    session_token: String,
    expires_at: u64,
}

pub struct SyncServer {
    is_running: Arc<AtomicBool>,
    pairing_pin: Arc<Mutex<String>>,
    last_message: Arc<Mutex<String>>,
    connected_clients: Arc<Mutex<usize>>,
    event_listeners: Arc<Mutex<Vec<StatusChangeCallback>>>,
    event_senders: Arc<Mutex<Vec<Sender<SyncStatusEvent>>>>,
    pairing_credentials: Arc<Mutex<PairingCredentials>>,
    server_thread: Mutex<Option<thread::JoinHandle<()>>>,
    db: Arc<Database>,
}

fn unix_time_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

fn token_digest(token: &str) -> [u8; 32] {
    Sha256::digest(token.as_bytes()).into()
}

fn constant_time_digest_match(a: &[u8; 32], b: &[u8; 32]) -> bool {
    let mut diff = 0u8;
    for (left, right) in a.iter().zip(b.iter()) {
        diff |= *left ^ *right;
    }
    diff == 0
}

fn generate_opaque_token() -> String {
    URL_SAFE_NO_PAD.encode(rand::rng().random::<[u8; 24]>())
}

fn start_mdns_advertisement(
) -> Result<(ServiceDaemon, String, mdns_sd::Receiver<mdns_sd::DaemonEvent>), String> {
    let ip = SyncServer::get_local_ip()
        .parse::<IpAddr>()
        .map_err(|_| "Yerel ağ IPv4 adresi bulunamadı".to_string())?;
    if ip.is_loopback() || !is_allowed_client_ip(&SocketAddr::new(ip, 8765)) {
        return Err("Yerel ağ IPv4 adresi kullanılamıyor; mDNS eşleştirmesi başlatılamadı".to_string());
    }

    let daemon = ServiceDaemon::new().map_err(|error| format!("mDNS servisi başlatılamadı: {error}"))?;
    let monitor = daemon
        .monitor()
        .map_err(|error| format!("mDNS durumu izlenemedi: {error}"))?;
    daemon
        .set_service_name_len_max(30)
        .map_err(|error| format!("mDNS hizmet adı yapılandırılamadı: {error}"))?;

    let suffix = std::process::id();
    let instance_name = format!("Not Defterim {suffix}");
    let host_name = format!("notdefterim-{suffix}.local.");
    let properties = [("protocol", "noteapp_sync"), ("version", "2")];
    let service = ServiceInfo::new(
        MDNS_SERVICE_TYPE,
        &instance_name,
        &host_name,
        ip,
        8765,
        &properties[..],
    )
    .map_err(|error| format!("mDNS hizmet kaydı oluşturulamadı: {error}"))?;
    let full_name = service.get_fullname().to_string();
    daemon
        .register(service)
        .map_err(|error| format!("mDNS hizmeti yayınlanamadı: {error}"))?;
    Ok((daemon, full_name, monitor))
}

fn mdns_startup_status<T>(result: Result<T, String>) -> (Option<T>, String) {
    match result {
        Ok(advertisement) => (
            Some(advertisement),
            "mDNS eşleştirmesi etkin".to_string(),
        ),
        Err(_) => (
            None,
            "mDNS kullanılamıyor; QR veya yerel ağ adresiyle bağlanabilirsiniz".to_string(),
        ),
    }
}

fn revoke_pairing_credentials(credentials: &Mutex<PairingCredentials>) {
    if let Ok(mut credentials) = credentials.lock() {
        credentials.clear();
    }
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
            pairing_credentials: Arc::new(Mutex::new(PairingCredentials::default())),
            server_thread: Mutex::new(None),
            db,
        }
    }

    pub fn create_pairing_token(&self) -> Result<PairingTokenInfo, String> {
        if !self.is_running.load(Ordering::SeqCst) {
            return Err("Wi-Fi senkronizasyon sunucusu çalışmıyor".to_string());
        }
        let token = generate_opaque_token();
        let expires_at = unix_time_ms().saturating_add(PAIRING_TOKEN_TTL_MS);
        let mut credentials = self
            .pairing_credentials
            .lock()
            .map_err(|_| "Eşleştirme bilgisi kullanılamıyor".to_string())?;
        credentials.issue_pairing(token_digest(&token), expires_at, unix_time_ms());
        Ok(PairingTokenInfo {
            pairing_token: token,
            expires_at,
        })
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
        let Ok(mut thread_slot) = self.server_thread.lock() else {
            return;
        };
        if self.is_running.load(Ordering::SeqCst) {
            return;
        }
        if let Some(previous_thread) = thread_slot.take() {
            let _ = previous_thread.join();
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
        let credentials_clone = self.pairing_credentials.clone();
        let db_clone = self.db.clone();

        let server_thread = thread::spawn(move || {
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

            let (mdns, mdns_status) = mdns_startup_status(start_mdns_advertisement());
            let start_msg = format!("Sunucu 8765 portunda dinleniyor; {mdns_status}.");
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
                if let Some((_, _, mdns_monitor)) = mdns.as_ref() {
                    while let Ok(event) = mdns_monitor.try_recv() {
                        if let mdns_sd::DaemonEvent::Error(error) = event {
                            let warning = format!("mDNS yayını kullanılamıyor; QR veya yerel ağ adresini kullanın: {error}");
                            if let Ok(mut message) = msg_clone.lock() {
                                *message = warning.clone();
                            }
                            let current_pin = pin_clone.lock().unwrap_or_else(|e| e.into_inner()).clone();
                            let notes_count = db_clone.get_all_notes().map(|n| n.len()).unwrap_or(0);
                            Self::emit_status_event(
                                true,
                                &current_pin,
                                &warning,
                                0,
                                notes_count,
                                &listeners_clone,
                                &senders_clone,
                            );
                        }
                    }
                }
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

                        if method == Method::Post && path == "/api/v2/pair" {
                            let pairing_token = request.headers().iter().find_map(|header| {
                                header
                                    .field
                                    .as_str()
                                    .eq_ignore_ascii_case("X-Pairing-Token")
                                    .then(|| header.value.as_str().trim().to_string())
                            });
                            let mut body_probe = Vec::new();
                            let body_read = request.as_reader().take(1).read_to_end(&mut body_probe);
                            let session = if body_read.is_ok() && body_probe.is_empty() {
                                pairing_token.and_then(|token| {
                                    if token.len() != 32 || !token.is_ascii() {
                                        return None;
                                    }
                                    let session_token = generate_opaque_token();
                                    let now = unix_time_ms();
                                    let expires_at = now.saturating_add(SESSION_TOKEN_TTL_MS);
                                    let mut credentials = credentials_clone.lock().ok()?;
                                    if credentials.exchange_pairing(
                                        token_digest(&token),
                                        now,
                                        token_digest(&session_token),
                                        expires_at,
                                    ) {
                                        Some(SessionTokenInfo {
                                            version: 2,
                                            session_token,
                                            expires_at,
                                        })
                                    } else {
                                        None
                                    }
                                })
                            } else {
                                None
                            };
                            let response = match session {
                                Some(session) => Response::from_string(
                                    serde_json::to_string(&session).unwrap_or_else(|_| "{}".to_string()),
                                )
                                .with_header(cors_header)
                                .with_header(json_header),
                                None => Response::from_string(r#"{"error":"Eşleştirme kodu geçersiz veya süresi dolmuş"}"#)
                                    .with_status_code(StatusCode(401))
                                    .with_header(cors_header)
                                    .with_header(json_header),
                            };
                            let response = match Header::from_bytes(&b"Connection"[..], &b"close"[..]) {
                                Ok(header) => response.with_header(header),
                                Err(_) => response,
                            };
                            request.respond(response).ok();
                            continue;
                        }

                        // PIN Doğrulama: X-Sync-PIN, X-Pairing-PIN ve URL sorgusundaki 'pin=...' parametresini sabit zamanlı (constant-time) kontrol et
                        let maybe_pin = extract_pin_from_request(&request, &url);
                        let pin_valid = match maybe_pin {
                            Some(ref p) => constant_time_pin_match(p, &current_pin),
                            None => false,
                        };
                        let bearer_valid = request.headers().iter().find_map(|header| {
                            header
                                .field
                                .as_str()
                                .eq_ignore_ascii_case("Authorization")
                                .then(|| header.value.as_str())
                        }).and_then(|value| {
                            let token = value.strip_prefix("Bearer ")?;
                            if token.len() != 32 || !token.is_ascii() {
                                return None;
                            }
                            let digest = token_digest(token);
                            let now = unix_time_ms();
                            credentials_clone
                                .lock()
                                .ok()
                                .map(|credentials| credentials.has_valid_session(&digest, now))
                        }).unwrap_or(false);

                        // Durum rotaları haricinde PIN doğrulanamadıysa 401 Unauthorized dön
                        if !pin_valid && !bearer_valid {
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
            if let Some((mdns_daemon, mdns_full_name, _)) = mdns {
                let _ = mdns_daemon.unregister(&mdns_full_name);
                if let Ok(shutdown) = mdns_daemon.shutdown() {
                    let _ = shutdown.recv_timeout(Duration::from_secs(2));
                }
            }
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
        *thread_slot = Some(server_thread);
    }

    pub fn stop(&self) {
        let (was_running, server_thread) = match self.server_thread.lock() {
            Ok(mut thread_slot) => (
                self.is_running.swap(false, Ordering::SeqCst),
                thread_slot.take(),
            ),
            Err(_) => (self.is_running.swap(false, Ordering::SeqCst), None),
        };
        revoke_pairing_credentials(&self.pairing_credentials);
        if was_running {
            let stop_msg = "Sunucu durduruldu".to_string();
            if let Ok(mut m) = self.last_message.lock() {
                *m = stop_msg;
            }
            if let Ok(mut c) = self.connected_clients.lock() {
                *c = 0;
            }
            self.emit_status();
        }
        if let Some(server_thread) = server_thread {
            let _ = server_thread.join();
        }
        revoke_pairing_credentials(&self.pairing_credentials);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pairing_tokens_expire_and_can_only_be_redeemed_once() {
        let mut credentials = PairingCredentials::default();
        let pairing = token_digest("pairing-secret");
        credentials.issue_pairing(pairing, 200, 100);

        assert!(!credentials.exchange_pairing(pairing, 201, token_digest("session-expired"), 500));

        credentials.issue_pairing(pairing, 300, 201);
        assert!(credentials.exchange_pairing(pairing, 202, token_digest("session-valid"), 500));
        assert!(!credentials.exchange_pairing(pairing, 202, token_digest("session-replay"), 500));
    }

    #[test]
    fn session_bearers_expire() {
        let mut credentials = PairingCredentials::default();
        credentials.sessions.push(HashedCredential {
            digest: token_digest("session-secret"),
            expires_at: 300,
        });

        assert!(credentials.has_valid_session(&token_digest("session-secret"), 299));
        assert!(!credentials.has_valid_session(&token_digest("session-secret"), 300));
    }

    #[test]
    fn stopping_server_revokes_pending_pairings_and_sessions() {
        let credentials = Mutex::new(PairingCredentials::default());
        {
            let mut active = credentials.lock().unwrap();
            active.issue_pairing(token_digest("pairing-secret"), 500, 100);
            active.sessions.push(HashedCredential {
                digest: token_digest("session-secret"),
                expires_at: 500,
            });
        }

        revoke_pairing_credentials(&credentials);

        let credentials = credentials.lock().unwrap();
        assert!(credentials.pending_pairings.is_empty());
        assert!(credentials.sessions.is_empty());
    }

    #[test]
    fn mdns_startup_failure_does_not_disable_http_server() {
        let (advertisement, status) = mdns_startup_status::<()>(Err("mDNS unavailable".to_string()));

        assert!(advertisement.is_none());
        assert!(status.contains("QR"));
    }

    #[test]
    fn legacy_pin_authentication_remains_compatible() {
        assert!(constant_time_pin_match("1234", "1234"));
        assert!(!constant_time_pin_match("1235", "1234"));
    }

    #[test]
    fn generated_pairing_tokens_fit_the_desktop_qr_payload() {
        let token = generate_opaque_token();
        assert_eq!(token.len(), 32);
        assert!(token.bytes().all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_'));
        let payload = serde_json::json!({
            "type": "noteapp_sync",
            "version": 2,
            "host": "255.255.255.255",
            "port": 8765,
            "pairing_token": token,
            "expires_at": 1_800_000_000_000_u64
        });
        assert!(payload.to_string().len() <= 153);
    }
}
