use crate::db::Database;
use crate::models::{Category, Note};
use rand::RngExt;
use serde::{Deserialize, Serialize};
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
            pairing_pin: self.pairing_pin.lock().unwrap().clone(),
            last_message: self.last_message.lock().unwrap().clone(),
        }
    }

    pub fn refresh_pin(&self) -> String {
        let new_pin = format!("{:04}", rand::rng().random_range(1000..=9999));
        let mut p = self.pairing_pin.lock().unwrap();
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
                    *msg_clone.lock().unwrap() = format!("Sunucu hatası: {}", e);
                    is_running_clone.store(false, Ordering::SeqCst);
                    return;
                }
            };

            *msg_clone.lock().unwrap() = "Sunucu 8765 portunda dinleniyor".to_string();

            while is_running_clone.load(Ordering::SeqCst) {
                match server.recv_timeout(std::time::Duration::from_millis(500)) {
                    Ok(Some(mut request)) => {
                        let url = request.url().to_string();
                        let method = request.method().clone();
                        let current_pin = pin_clone.lock().unwrap().clone();

                        let cors_header = Header::from_bytes(&b"Access-Control-Allow-Origin"[..], &b"*"[..]).unwrap();
                        let json_header = Header::from_bytes(&b"Content-Type"[..], &b"application/json; charset=utf-8"[..]).unwrap();

                        // 1. Info / Health
                        if url == "/" || url == "/status" {
                            let resp_body = r#"{"app":"NotDefterim Desktop","version":"1.0","status":"online"}"#;
                            let resp = Response::from_string(resp_body)
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            continue;
                        }

                        // Check PIN in header or query parameter
                        let mut pin_valid = false;
                        for h in request.headers() {
                            let field_str = h.field.as_str().as_str();
                            if field_str.eq_ignore_ascii_case("X-Pairing-PIN") {
                                if h.value.as_str().trim() == current_pin {
                                    pin_valid = true;
                                    break;
                                }
                            }
                        }
                        if url.contains(&format!("pin={}", current_pin)) {
                            pin_valid = true;
                        }

                        if !pin_valid && url != "/status" {
                            let resp = Response::from_string(r#"{"error":"Gecersiz veya eksik PIN"}"#)
                                .with_status_code(StatusCode(401))
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            continue;
                        }

                        // 2. Export Notes to Phone
                        if method == Method::Get && (url.starts_with("/sync/export") || url.starts_with("/api/notes")) {
                            let notes = db_clone.get_all_notes().unwrap_or_default();
                            let categories = db_clone.get_categories().unwrap_or_default();
                            let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() as i64;
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
                            *msg_clone.lock().unwrap() = "Notlar telefona aktarıldı".to_string();
                            continue;
                        }

                        // 3. Import Notes from Phone
                        if method == Method::Post && (url.starts_with("/sync/import") || url.starts_with("/api/sync")) {
                            let mut body = String::new();
                            if request.as_reader().read_to_string(&mut body).is_ok() {
                                if let Ok(payload) = serde_json::from_str::<SyncPayload>(&body) {
                                    let count_notes = payload.notes.len();
                                    let count_cats = payload.categories.len();
                                    if db_clone.replace_all_notes(&payload.notes, &payload.categories).is_ok() {
                                        *msg_clone.lock().unwrap() = format!("Telefondan {} not ve {} kategori eşitlendi", count_notes, count_cats);
                                        let resp = Response::from_string(r#"{"success":true,"message":"Eşitleme tamamlandı"}"#)
                                            .with_header(cors_header)
                                            .with_header(json_header);
                                        request.respond(resp).ok();
                                        continue;
                                    }
                                }
                            }
                            let resp = Response::from_string(r#"{"error":"Gecersiz veri paketi"}"#)
                                .with_status_code(StatusCode(400))
                                .with_header(cors_header)
                                .with_header(json_header);
                            request.respond(resp).ok();
                            continue;
                        }

                        let resp = Response::from_string(r#"{"error":"Bilinmeyen istek"}"#)
                            .with_status_code(StatusCode(404))
                            .with_header(cors_header);
                        request.respond(resp).ok();
                    }
                    Ok(None) => {}
                    Err(_) => break,
                }
            }

            *msg_clone.lock().unwrap() = "Durduruldu".to_string();
        });
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::SeqCst);
    }
}
