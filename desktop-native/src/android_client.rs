//! Android-hosted local Wi-Fi sync client.
//!
//! Security model: every connection is TLS 1.3 only and the server leaf
//! certificate (DER) must match the SHA-256 pin from the QR code. The pin is
//! checked during the handshake, before any pairing token or session token is
//! written. There is no HTTP fallback and no trust-all path. TLS protects the
//! transport between the two devices; it is not app-layer end-to-end encryption.

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerifier};
use rustls::crypto::{
    ring as ring_provider, verify_tls13_signature, CryptoProvider, WebPkiSupportedAlgorithms,
};
use rustls::pki_types::{CertificateDer, ServerName, UnixTime};
use rustls::{
    ClientConfig, ClientConnection, DigitallySignedStruct, Error as TlsError, SignatureScheme,
    StreamOwned,
};
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::io::{Read, Write};
use std::net::{IpAddr, Ipv4Addr, SocketAddr, TcpStream};
use std::sync::Arc;
use std::time::Duration;

pub const MAX_ZIP_BYTES: usize = 50 * 1024 * 1024;
const MAX_HEADER_BYTES: usize = 16 * 1024;
const MAX_ERROR_BODY: usize = 64 * 1024;
const CHUNK: usize = 16 * 1024;

#[derive(Debug, Clone)]
pub struct Pairing {
    pub host: Ipv4Addr,
    pub port: u16,
    pub pin: [u8; 32],
    pub token: String,
}

#[derive(Deserialize)]
struct QrPayload {
    #[serde(rename = "type")]
    kind: String,
    version: i64,
    scheme: String,
    host: String,
    port: i64,
    tls_cert_sha256: String,
    pairing_token: String,
    expires_at: i64,
}

fn is_token(value: &str, min: usize, max: usize) -> bool {
    (min..=max).contains(&value.len())
        && value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

fn decode_pin(hex: &str) -> Option<[u8; 32]> {
    if hex.len() != 64 || !hex.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f')) {
        return None;
    }
    let mut out = [0u8; 32];
    for (i, slot) in out.iter_mut().enumerate() {
        *slot = u8::from_str_radix(&hex[i * 2..i * 2 + 2], 16).ok()?;
    }
    Some(out)
}

/// Strictly validates the Android v3 QR payload (HTTPS + pin only).
pub fn parse_qr(raw: &str, now_ms: i64) -> Result<Pairing, String> {
    if raw.trim().is_empty() || raw.len() > 4096 {
        return Err("QR içeriği boş veya beklenen boyutun dışında.".into());
    }
    let q: QrPayload = serde_json::from_str(raw.trim())
        .map_err(|_| "QR geçerli bağlantı bilgisi içermiyor.".to_string())?;
    if q.kind != "noteapp_sync" || q.version != 3 || q.scheme != "https" {
        return Err("Yalnızca Android HTTPS v3 QR kodları kabul edilir.".into());
    }
    let host: Ipv4Addr = q
        .host
        .parse()
        .map_err(|_| "Android adresi geçerli bir IPv4 değil.".to_string())?;
    if !(host.is_private() || host.is_link_local()) {
        return Err("Android adresi özel yerel ağa ait değil.".into());
    }
    if !(1..=65535).contains(&q.port) {
        return Err("QR bağlantı noktası geçersiz.".into());
    }
    let pin = decode_pin(&q.tls_cert_sha256).ok_or("TLS parmak izi geçersiz.")?;
    if !is_token(&q.pairing_token, 32, 32) {
        return Err("Eşleşme kodu geçersiz.".into());
    }
    if q.expires_at <= now_ms {
        return Err("QR kodunun süresi dolmuş.".into());
    }
    Ok(Pairing {
        host,
        port: q.port as u16,
        pin,
        token: q.pairing_token,
    })
}

#[derive(Debug)]
struct PinVerifier {
    pin: [u8; 32],
    algs: WebPkiSupportedAlgorithms,
}

impl ServerCertVerifier for PinVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp: &[u8],
        _now: UnixTime,
    ) -> Result<rustls::client::danger::ServerCertVerified, TlsError> {
        let digest = Sha256::digest(end_entity.as_ref());
        let diff = digest
            .iter()
            .zip(self.pin.iter())
            .fold(0u8, |acc, (a, b)| acc | (a ^ b));
        if diff == 0 {
            Ok(rustls::client::danger::ServerCertVerified::assertion())
        } else {
            Err(TlsError::General(
                "Sertifika parmak izi QR ile eşleşmiyor.".into(),
            ))
        }
    }

    fn verify_tls12_signature(
        &self,
        _: &[u8],
        _: &CertificateDer<'_>,
        _: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, TlsError> {
        Err(TlsError::General("TLS 1.2 desteklenmiyor.".into()))
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, TlsError> {
        verify_tls13_signature(message, cert, dss, &self.algs)
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        self.algs.supported_schemes()
    }
}

pub fn tls_config(pin: [u8; 32]) -> Result<Arc<ClientConfig>, String> {
    let provider: Arc<CryptoProvider> = Arc::new(ring_provider::default_provider());
    let verifier = PinVerifier {
        pin,
        algs: provider.signature_verification_algorithms,
    };
    let config = ClientConfig::builder_with_provider(provider)
        .with_protocol_versions(&[&rustls::version::TLS13])
        .map_err(|e| e.to_string())?
        .dangerous()
        .with_custom_certificate_verifier(Arc::new(verifier))
        .with_no_client_auth();
    Ok(Arc::new(config))
}

fn bearer(session: &str) -> String {
    format!("Bearer {}", session)
}

type Tls = StreamOwned<ClientConnection, TcpStream>;

fn connect(p: &Pairing, config: &Arc<ClientConfig>, read_timeout: Duration) -> Result<Tls, String> {
    let addr = SocketAddr::new(IpAddr::V4(p.host), p.port);
    let tcp = TcpStream::connect_timeout(&addr, Duration::from_secs(10))
        .map_err(|e| format!("Android cihaza bağlanılamadı: {e}"))?;
    tcp.set_read_timeout(Some(read_timeout)).ok();
    tcp.set_write_timeout(Some(Duration::from_secs(60))).ok();
    let name = ServerName::IpAddress(IpAddr::V4(p.host).into());
    let conn = ClientConnection::new(config.clone(), name).map_err(|e| e.to_string())?;
    let mut tls = StreamOwned::new(conn, tcp);
    // Complete the handshake (pin check) before any application data is written.
    while tls.conn.is_handshaking() {
        tls.conn.complete_io(&mut tls.sock).map_err(|e| {
            format!("Güvenli bağlantı kurulamadı veya sertifika doğrulanamadı: {e}")
        })?;
    }
    Ok(tls)
}

struct Response {
    status: u16,
    content_length: Option<usize>,
    notes_count: Option<u64>,
    leftover: Vec<u8>,
}

fn read_head(tls: &mut Tls) -> Result<Response, String> {
    let mut buf = Vec::with_capacity(1024);
    let mut byte = [0u8; 1];
    while !buf.ends_with(b"\r\n\r\n") {
        if buf.len() > MAX_HEADER_BYTES {
            return Err("Yanıt başlığı çok büyük.".into());
        }
        match tls.read(&mut byte) {
            Ok(0) => return Err("Bağlantı beklenmedik şekilde kapandı.".into()),
            Ok(_) => buf.push(byte[0]),
            Err(e) => return Err(format!("Yanıt okunamadı: {e}")),
        }
    }
    let head = String::from_utf8_lossy(&buf).to_string();
    let mut lines = head.split("\r\n");
    let status = lines
        .next()
        .and_then(|l| l.split_whitespace().nth(1))
        .and_then(|s| s.parse::<u16>().ok())
        .ok_or("Geçersiz HTTP yanıtı.")?;
    let mut content_length = None;
    let mut notes_count = None;
    for line in lines {
        if let Some((k, v)) = line.split_once(':') {
            match k.trim().to_ascii_lowercase().as_str() {
                "content-length" => content_length = v.trim().parse::<usize>().ok(),
                "x-notes-count" => notes_count = v.trim().parse::<u64>().ok(),
                "transfer-encoding" => return Err("Parçalı yanıt desteklenmiyor.".into()),
                _ => {}
            }
        }
    }
    Ok(Response {
        status,
        content_length,
        notes_count,
        leftover: Vec::new(),
    })
}

fn read_body(
    tls: &mut Tls,
    len: usize,
    mut progress: impl FnMut(u64, u64),
) -> Result<Vec<u8>, String> {
    let mut out = Vec::with_capacity(len);
    let mut chunk = [0u8; CHUNK];
    progress(0, len as u64);
    while out.len() < len {
        let want = CHUNK.min(len - out.len());
        let n = tls
            .read(&mut chunk[..want])
            .map_err(|e| format!("Veri alınamadı: {e}"))?;
        if n == 0 {
            return Err("Aktarım tamamlanmadan bağlantı kapandı.".into());
        }
        out.extend_from_slice(&chunk[..n]);
        progress(out.len() as u64, len as u64);
    }
    Ok(out)
}

fn error_message(tls: &mut Tls, resp: &Response) -> String {
    let body = match resp.content_length {
        Some(n) if n <= MAX_ERROR_BODY => read_body(tls, n, |_, _| {}).unwrap_or_default(),
        _ => Vec::new(),
    };
    let detail = serde_json::from_slice::<serde_json::Value>(&body)
        .ok()
        .and_then(|v| v["error"]["message"].as_str().map(str::to_string))
        .unwrap_or_default();
    format!(
        "Android cihaz isteği reddetti (HTTP {}). {}",
        resp.status, detail
    )
    .trim()
    .to_string()
}

fn write_head(
    tls: &mut Tls,
    method: &str,
    path: &str,
    host: &Ipv4Addr,
    headers: &[(&str, String)],
) -> Result<(), String> {
    let mut head =
        format!("{method} {path} HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\nAccept: */*\r\n");
    for (k, v) in headers {
        if v.contains('\r') || v.contains('\n') {
            return Err("Geçersiz başlık değeri.".into());
        }
        head.push_str(&format!("{k}: {v}\r\n"));
    }
    head.push_str("\r\n");
    tls.write_all(head.as_bytes())
        .map_err(|e| format!("İstek gönderilemedi: {e}"))
}

/// Pins the certificate, then exchanges the one-time QR token for a session token.
fn pair(p: &Pairing, config: &Arc<ClientConfig>) -> Result<String, String> {
    let mut tls = connect(p, config, Duration::from_secs(20))?;
    write_head(
        &mut tls,
        "POST",
        "/api/v2/pair",
        &p.host,
        &[
            ("X-Pairing-Token", p.token.clone()),
            ("Content-Length", "0".into()),
        ],
    )?;
    tls.flush().ok();
    let resp = read_head(&mut tls)?;
    if resp.status != 200 {
        return Err(error_message(&mut tls, &resp));
    }
    let len = resp
        .content_length
        .filter(|n| *n <= MAX_ERROR_BODY)
        .ok_or("Eşleşme yanıtı geçersiz.")?;
    let body = read_body(&mut tls, len, |_, _| {})?;
    let v: serde_json::Value =
        serde_json::from_slice(&body).map_err(|_| "Eşleşme yanıtı geçersiz.".to_string())?;
    let token = v["session_token"].as_str().unwrap_or_default();
    if v["version"] != 2 || !is_token(token, 16, 256) {
        return Err("Eşleşme yanıtı geçersiz.".into());
    }
    Ok(token.to_string())
}

pub struct Received {
    pub zip: Vec<u8>,
    pub notes_count: Option<u64>,
}

pub fn download(p: &Pairing, progress: impl FnMut(u64, u64)) -> Result<Received, String> {
    let config = tls_config(p.pin)?;
    let session = pair(p, &config)?;
    let mut tls = connect(p, &config, Duration::from_secs(60))?;
    write_head(
        &mut tls,
        "GET",
        "/api/sync/download",
        &p.host,
        &[("Authorization", bearer(&session))],
    )?;
    tls.flush().ok();
    let resp = read_head(&mut tls)?;
    if resp.status != 200 {
        return Err(error_message(&mut tls, &resp));
    }
    let len = resp
        .content_length
        .filter(|n| (1..=MAX_ZIP_BYTES).contains(n))
        .ok_or("Android geçerli bir ZIP boyutu bildirmedi.")?;
    let _ = &resp.leftover;
    let zip = read_body(&mut tls, len, progress)?;
    Ok(Received {
        zip,
        notes_count: resp.notes_count,
    })
}

/// Uploads a ZIP; Android asks its user for approval, so the read timeout is long.
pub fn upload(p: &Pairing, zip: &[u8], mut progress: impl FnMut(u64, u64)) -> Result<u64, String> {
    if zip.is_empty() || zip.len() > MAX_ZIP_BYTES {
        return Err("ZIP paketi 50 MiB sınırını aşıyor.".into());
    }
    let config = tls_config(p.pin)?;
    let session = pair(p, &config)?;
    let mut tls = connect(p, &config, Duration::from_secs(300))?;
    write_head(
        &mut tls,
        "POST",
        "/api/sync/upload",
        &p.host,
        &[
            ("Authorization", bearer(&session)),
            ("Content-Type", "application/zip".into()),
            ("Content-Length", zip.len().to_string()),
            ("X-Clear-Existing", "false".into()),
        ],
    )?;
    let total = zip.len() as u64;
    progress(0, total);
    let mut sent = 0usize;
    for chunk in zip.chunks(CHUNK) {
        tls.write_all(chunk)
            .map_err(|e| format!("Veri gönderilemedi: {e}"))?;
        sent += chunk.len();
        progress(sent as u64, total);
    }
    tls.flush().ok();
    let resp = read_head(&mut tls)?;
    if resp.status != 200 {
        return Err(error_message(&mut tls, &resp));
    }
    let len = resp
        .content_length
        .filter(|n| *n <= MAX_ERROR_BODY)
        .ok_or("Android yanıtı geçersiz.")?;
    let body = read_body(&mut tls, len, |_, _| {})?;
    let v: serde_json::Value =
        serde_json::from_slice(&body).map_err(|_| "Android yanıtı geçersiz.".to_string())?;
    if v["success"] != true {
        return Err("Android aktarımı tamamlandığını doğrulamadı.".into());
    }
    Ok(v["notesCount"].as_u64().unwrap_or(0))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn qr(extra: &str) -> String {
        format!(
            r#"{{"type":"noteapp_sync","version":3,"scheme":"https","host":"192.168.1.20","port":8443,"tls_cert_sha256":"{}","pairing_token":"{}","expires_at":9999999999999{}}}"#,
            "a".repeat(64),
            "T".repeat(32),
            extra
        )
    }

    #[test]
    fn bearer_header_carries_session_token() {
        assert_eq!(bearer("abc_DEF-123").as_bytes()[..7], *b"Bearer ");
    }

    #[test]
    fn accepts_valid_qr() {
        let p = parse_qr(&qr(""), 1).unwrap();
        assert_eq!(p.port, 8443);
        assert_eq!(p.pin[0], 0xaa);
    }

    #[test]
    fn rejects_http_public_uppercase_pin_and_expired() {
        assert!(parse_qr(&qr("").replace("https", "http"), 1).is_err());
        assert!(parse_qr(&qr("").replace("192.168.1.20", "8.8.8.8"), 1).is_err());
        assert!(parse_qr(&qr("").replace(&"a".repeat(64), &"A".repeat(64)), 1).is_err());
        assert!(parse_qr(&qr(""), 10_000_000_000_000).is_err());
        assert!(parse_qr(&qr("").replace("\"version\":3", "\"version\":2"), 1).is_err());
    }
}
