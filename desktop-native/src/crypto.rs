use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use base64::prelude::*;
use rand::RngExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs;
use std::sync::RwLock;

pub const PREFIX: &str = "ENC_GCM_V1:";
pub const SALT_LEN: usize = 16;
pub const IV_LEN: usize = 12;
pub const TAG_LEN: usize = 16;
pub const PBKDF2_ITERATIONS: u32 = 65536;

/// RAM'deki hassas anahtar veya parola baytlarÄ±nÄ± derleyici optimizasyonuna
/// takÄ±lmadan gÃ¼venli ÅŸekilde sÄ±fÄ±rlar (Dead Store Elimination korumasÄ±).
pub fn zeroize_bytes(slice: &mut [u8]) {
    for b in slice.iter_mut() {
        unsafe { std::ptr::write_volatile(b, 0) };
    }
    std::sync::atomic::compiler_fence(std::sync::atomic::Ordering::SeqCst);
}

/// Zamanlama saldÄ±rÄ±larÄ±nÄ± (timing attack) Ã¶nlemek amacÄ±yla iki bayt dizisini
/// sabit zamanlÄ± (constant-time) XOR algoritmasÄ±yla karÅŸÄ±laÅŸtÄ±rÄ±r.
pub fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (x, y) in a.iter().zip(b.iter()) {
        diff |= x ^ y;
    }
    diff == 0
}

#[derive(Serialize, Deserialize)]
struct VaultConfig {
    salt: String,
    hash: String,
    #[serde(rename = "createdAt")]
    created_at: i64,
}

pub struct CryptoManager {
    session_key: RwLock<Option<[u8; 32]>>,
}

impl CryptoManager {
    pub fn new() -> Self {
        CryptoManager {
            session_key: RwLock::new(None),
        }
    }

    /// Test ve oturum yönetimi için doğrudan oturum anahtarı ile başlatır
    pub fn with_session_key(key: [u8; 32]) -> Self {
        CryptoManager {
            session_key: RwLock::new(Some(key)),
        }
    }

    /// Oturum anahtarını programatik olarak atar
    pub fn set_session_key(&self, key: [u8; 32]) {
        if let Ok(mut lock) = self.session_key.write() {
            *lock = Some(key);
        }
    }

    /// PBKDF2-HMAC-SHA256 ile parola ve salt'tan 256-bit anahtar/hash türetir
    pub fn derive_key(password: &str, salt: &[u8; SALT_LEN], iterations: u32) -> Result<[u8; 32], ()> {
        let mut key = [0u8; 32];
        pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
            password.as_bytes(),
            salt,
            iterations,
            &mut key,
        ).map_err(|_| ())?;
        Ok(key)
    }

    /// Master key ikili dosya yolu (`master.key`)
    fn master_key_path() -> std::path::PathBuf {
        crate::db::Database::get_app_dir().join("master.key")
    }

    /// Vault konfigÃ¼rasyon JSON yolu (`vault_config.json`)
    fn config_path() -> std::path::PathBuf {
        crate::db::Database::get_app_dir().join("vault_config.json")
    }

    /// Master parola ayarlanmÄ±ÅŸ mÄ± kontrol eder (`master.key` veya `vault_config.json`).
    pub fn has_master_password(&self) -> bool {
        let key_path = Self::master_key_path();
        if key_path.exists() {
            if let Ok(meta) = fs::metadata(&key_path) {
                if meta.len() >= (SALT_LEN + 32) as u64 {
                    return true;
                }
            }
        }

        let config_path = Self::config_path();
        if config_path.exists() {
            if let Ok(content) = fs::read_to_string(config_path) {
                return serde_json::from_str::<VaultConfig>(&content).is_ok();
            }
        }

        false
    }

    /// Yeni bir Master parola belirler ve PBKDF2 (SHA-256, 65.536 iterasyon) ile hash'ler.
    /// `master.key` ve `vault_config.json` dosyalarÄ±na kaydeder ve kasayÄ± otomatik aÃ§ar.
    pub fn setup_master_password(&self, password: &str) -> bool {
        if password.len() < 4 {
            return false;
        }

        let mut salt = [0u8; SALT_LEN];
        rand::rng().fill(&mut salt[..]);

        // PBKDF2-HMAC-SHA256 (65.536 tur) ile parola hash'i tÃ¼retme
        let mut hash = [0u8; 32];
        let res = pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
            password.as_bytes(),
            &salt,
            PBKDF2_ITERATIONS,
            &mut hash,
        );
        if res.is_err() {
            return false;
        }

        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;

        // 1. `master.key` ikili dosyasÄ±na yaz (Salt 16B + Hash 32B)
        let key_path = Self::master_key_path();
        if let Some(parent) = key_path.parent() {
            fs::create_dir_all(parent).ok();
        }
        let mut key_data = Vec::with_capacity(SALT_LEN + 32);
        key_data.extend_from_slice(&salt);
        key_data.extend_from_slice(&hash);
        fs::write(&key_path, &key_data).ok();

        // 2. `vault_config.json` dosyasÄ±na yaz
        let config = VaultConfig {
            salt: BASE64_STANDARD.encode(&salt),
            hash: BASE64_STANDARD.encode(&hash),
            created_at: now,
        };

        if let Ok(json) = serde_json::to_string_pretty(&config) {
            let path = Self::config_path();
            if let Some(parent) = path.parent() {
                fs::create_dir_all(parent).ok();
            }
            if fs::write(path, json).is_ok() {
                return self.unlock_vault(password);
            }
        }

        false
    }

    /// Master parola ile kasayÄ± aÃ§ar:
    /// Hash kontrolÃ¼ yapar, geÃ§erli ise PBKDF2 ile 256-bit anahtar tÃ¼reterek RAM'e alÄ±r.
    pub fn unlock_vault(&self, password: &str) -> bool {
        let mut salt_opt: Option<Vec<u8>> = None;
        let mut stored_hash_opt: Option<Vec<u8>> = None;

        // 1. Ã–nce `master.key` dosyasÄ±ndan oku
        let key_path = Self::master_key_path();
        if key_path.exists() {
            if let Ok(bytes) = fs::read(&key_path) {
                if bytes.len() >= SALT_LEN + 32 {
                    salt_opt = Some(bytes[..SALT_LEN].to_vec());
                    stored_hash_opt = Some(bytes[SALT_LEN..SALT_LEN + 32].to_vec());
                }
            }
        }

        // 2. BulunamadÄ±ysa `vault_config.json` dosyasÄ±ndan oku
        if salt_opt.is_none() || stored_hash_opt.is_none() {
            let path = Self::config_path();
            if let Ok(content) = fs::read_to_string(path) {
                if let Ok(config) = serde_json::from_str::<VaultConfig>(&content) {
                    if let (Ok(s), Ok(h)) = (
                        BASE64_STANDARD.decode(&config.salt),
                        BASE64_STANDARD.decode(&config.hash),
                    ) {
                        salt_opt = Some(s);
                        stored_hash_opt = Some(h);
                    }
                }
            }
        }

        let (salt, stored_hash) = match (salt_opt, stored_hash_opt) {
            (Some(s), Some(h)) => (s, h),
            _ => return false,
        };

        // 1. PBKDF2 (SHA-256, 65.536 tur) ile Hash DoÄŸrulama
        let mut pbkdf2_hash = [0u8; 32];
        let pbkdf2_hash_res = pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
            password.as_bytes(),
            &salt,
            PBKDF2_ITERATIONS,
            &mut pbkdf2_hash,
        );

        let mut is_valid = false;
        if pbkdf2_hash_res.is_ok() && constant_time_eq(&pbkdf2_hash, &stored_hash) {
            is_valid = true;
        } else {
            // Android v1.4.4 geriye dÃ¶nÃ¼k uyumluluk: SHA-256(salt + password) kontrolÃ¼
            let mut hasher = Sha256::new();
            hasher.update(&salt);
            hasher.update(password.as_bytes());
            let sha_hash = hasher.finalize();
            if constant_time_eq(sha_hash.as_slice(), &stored_hash) {
                is_valid = true;
            }
        }

        if !is_valid {
            return false;
        }

        // 2. DoÄŸrulama BaÅŸarÄ±lÄ±: AES-256 Oturum AnahtarÄ±nÄ± TÃ¼ret (PBKDF2-HMAC-SHA256)
        let mut key = [0u8; 32];
        let key_res = pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
            password.as_bytes(),
            &salt,
            PBKDF2_ITERATIONS,
            &mut key,
        );

        if key_res.is_err() {
            zeroize_bytes(&mut key);
            return false;
        }

        let mut lock = match self.session_key.write() {
            Ok(l) => l,
            Err(_) => {
                zeroize_bytes(&mut key);
                return false;
            }
        };

        *lock = Some(key);
        zeroize_bytes(&mut key);
        true
    }

    /// Kasa kilitlendiÄŸinde RAM'deki tÃ¼retilmiÅŸ 256-bit anahtarÄ± `zeroize` ile
    /// gÃ¼venli ÅŸekilde sÄ±fÄ±rlar ve `None` yapar.
    pub fn lock_vault(&self) {
        if let Ok(mut lock) = self.session_key.write() {
            if let Some(ref mut key) = *lock {
                zeroize_bytes(key);
            }
            *lock = None;
        }
    }

    /// Kasa kilidinin aÃ§Ä±k olup olmadÄ±ÄŸÄ±nÄ± dÃ¶ner.
    pub fn is_unlocked(&self) -> bool {
        self.session_key.read().map(|k| k.is_some()).unwrap_or(false)
    }

    /// Not iÃ§eriÄŸini AES-256-GCM ile ÅŸifreler.
    /// Ã‡Ä±ktÄ± FormatÄ±: `ENC_GCM_V1:<base64-nonce>:<base64-tag>:<base64-ciphertext>`
    pub fn encrypt_content(&self, plain_text: &str) -> String {
        if plain_text.is_empty() {
            return String::new();
        }

        let lock = match self.session_key.read() {
            Ok(l) => l,
            Err(_) => return plain_text.to_string(),
        };
        let mut key_bytes = match *lock {
            Some(k) => k,
            None => return "[ENC_ERR: Vault locked]".to_string(),
        };

        let mut nonce_bytes = [0u8; IV_LEN];
        rand::rng().fill(&mut nonce_bytes[..]);

        let cipher = match Aes256Gcm::new_from_slice(&key_bytes) {
            Ok(c) => {
                zeroize_bytes(&mut key_bytes);
                c
            }
            Err(_) => {
                zeroize_bytes(&mut key_bytes);
                return plain_text.to_string();
            }
        };

        let nonce = match Nonce::try_from(nonce_bytes.as_slice()) {
            Ok(n) => n,
            Err(_) => return plain_text.to_string(),
        };

        match cipher.encrypt(&nonce, plain_text.as_bytes()) {
            Ok(encrypted_bytes) => {
                // aes-gcm crate'inde son 16 bayt authentication tag'dir
                if encrypted_bytes.len() < TAG_LEN {
                    return plain_text.to_string();
                }
                let split_idx = encrypted_bytes.len() - TAG_LEN;
                let actual_ciphertext = &encrypted_bytes[..split_idx];
                let tag = &encrypted_bytes[split_idx..];

                let nonce_b64 = BASE64_STANDARD.encode(&nonce_bytes);
                let tag_b64 = BASE64_STANDARD.encode(tag);
                let ct_b64 = BASE64_STANDARD.encode(actual_ciphertext);

                format!("{}{}:{}:{}", PREFIX, nonce_b64, tag_b64, ct_b64)
            }
            Err(_) => plain_text.to_string(),
        }
    }

    /// AES-256-GCM ile ÅŸifrelenmiÅŸ not iÃ§eriÄŸini deÅŸifre eder.
    /// Hem `ENC_GCM_V1:<base64-nonce>:<base64-tag>:<base64-ciphertext>` formatÄ±nÄ±
    /// hem de Android mobil `ENC_GCM_V1:<base64(iv+ciphertext+tag)>` formatÄ±nÄ± Ã§Ã¶zer.
    pub fn decrypt_content(&self, cipher_text: &str) -> String {
        if !cipher_text.starts_with(PREFIX) {
            return cipher_text.to_string();
        }

        let lock = match self.session_key.read() {
            Ok(l) => l,
            Err(_) => return "[ğŸ”’ Kilitli Not - KasayÄ± AÃ§Ä±nÄ±z]".to_string(),
        };
        let mut key_bytes = match *lock {
            Some(k) => k,
            None => return "[ğŸ”’ Kilitli Not - KasayÄ± AÃ§Ä±nÄ±z]".to_string(),
        };

        let raw_payload = &cipher_text[PREFIX.len()..];

        // Format 1: `ENC_GCM_V1:<base64-nonce>:<base64-tag>:<base64-ciphertext>` (MasaÃ¼stÃ¼ Standart)
        let parts: Vec<&str> = raw_payload.split(':').collect();
        if parts.len() == 3 {
            let nonce_res = BASE64_STANDARD.decode(parts[0]);
            let tag_res = BASE64_STANDARD.decode(parts[1]);
            let ct_res = BASE64_STANDARD.decode(parts[2]);

            if let (Ok(nonce_bytes), Ok(tag_bytes), Ok(ct_bytes)) = (nonce_res, tag_res, ct_res) {
                if nonce_bytes.len() == IV_LEN && tag_bytes.len() == TAG_LEN {
                    let cipher = match Aes256Gcm::new_from_slice(&key_bytes) {
                        Ok(c) => {
                            zeroize_bytes(&mut key_bytes);
                            c
                        }
                        Err(_) => {
                            zeroize_bytes(&mut key_bytes);
                            return "[Åifre Ã‡Ã¶zÃ¼lemedi - HatalÄ± Anahtar]".to_string();
                        }
                    };
                    let nonce = match Nonce::try_from(nonce_bytes.as_slice()) {
                        Ok(n) => n,
                        Err(_) => return "[Bozuk Åifreli Ä°Ã§erik]".to_string(),
                    };

                    let mut combined_ct = ct_bytes;
                    combined_ct.extend_from_slice(&tag_bytes);

                    return match cipher.decrypt(&nonce, combined_ct.as_slice()) {
                        Ok(decrypted_bytes) => {
                            String::from_utf8(decrypted_bytes).unwrap_or_else(|_| "[GeÃ§ersiz UTF-8]".to_string())
                        }
                        Err(_) => "[Åifre Ã‡Ã¶zÃ¼lemedi - HatalÄ± Anahtar]".to_string(),
                    };
                }
            }
        }

        // Format 2: `ENC_GCM_V1:<base64(iv + ciphertext + tag)>` (Android Mobil Uyumluluk)
        if let Ok(combined) = BASE64_STANDARD.decode(raw_payload) {
            if combined.len() < IV_LEN + TAG_LEN {
                zeroize_bytes(&mut key_bytes);
                return "[Bozuk Åifreli Ä°Ã§erik]".to_string();
            }
            let (iv, ciphertext_and_tag) = combined.split_at(IV_LEN);
            let cipher = match Aes256Gcm::new_from_slice(&key_bytes) {
                Ok(c) => {
                    zeroize_bytes(&mut key_bytes);
                    c
                }
                Err(_) => {
                    zeroize_bytes(&mut key_bytes);
                    return "[Åifre Ã‡Ã¶zÃ¼lemedi - HatalÄ± Anahtar]".to_string();
                }
            };
            let nonce = match Nonce::try_from(iv) {
                Ok(n) => n,
                Err(_) => return "[Bozuk Åifreli Ä°Ã§erik]".to_string(),
            };

            match cipher.decrypt(&nonce, ciphertext_and_tag) {
                Ok(decrypted_bytes) => {
                    String::from_utf8(decrypted_bytes).unwrap_or_else(|_| "[GeÃ§ersiz UTF-8]".to_string())
                }
                Err(_) => "[Åifre Ã‡Ã¶zÃ¼lemedi - HatalÄ± Anahtar]".to_string(),
            }
        } else {
            zeroize_bytes(&mut key_bytes);
            "[Bozuk Base64 Verisi]".to_string()
        }
    }
}

impl Drop for CryptoManager {
    fn drop(&mut self) {
        self.lock_vault();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_constant_time_eq() {
        let a = b"secret_password_123";
        let b = b"secret_password_123";
        let c = b"wrong_password_999";
        let d = b"short";

        assert!(constant_time_eq(a, b));
        assert!(!constant_time_eq(a, c));
        assert!(!constant_time_eq(a, d));
    }

    #[test]
    fn test_zeroize_bytes() {
        let mut key = [42u8; 32];
        assert_eq!(key[0], 42);
        zeroize_bytes(&mut key);
        for &b in key.iter() {
            assert_eq!(b, 0);
        }
    }

    #[test]
    fn test_encrypt_decrypt_format() {
        let crypto = CryptoManager::new();
        let pwd = "MySecureVaultPassword2026!";
        assert!(crypto.setup_master_password(pwd));
        assert!(crypto.is_unlocked());

        let secret_text = "Gizli Proje Mimarisi Notu - 12345";
        let encrypted = crypto.encrypt_content(secret_text);

        // Format doÄŸrula: ENC_GCM_V1:<base64-nonce>:<base64-tag>:<base64-ciphertext>
        assert!(encrypted.starts_with(PREFIX));
        let body = &encrypted[PREFIX.len()..];
        let parts: Vec<&str> = body.split(':').collect();
        assert_eq!(parts.len(), 3);

        // DeÅŸifrele
        let decrypted = crypto.decrypt_content(&encrypted);
        assert_eq!(decrypted, secret_text);

        // Kasa kilitlendiÄŸinde iÃ§eriÄŸin deÅŸifre edilemediÄŸini doÄŸrula
        crypto.lock_vault();
        assert!(!crypto.is_unlocked());
        let locked_output = crypto.decrypt_content(&encrypted);
        assert_eq!(locked_output, "[ğŸ”’ Kilitli Not - KasayÄ± AÃ§Ä±nÄ±z]");

        // Tekrar aÃ§Ä±p Ã§Ã¶z
        assert!(crypto.unlock_vault(pwd));
        let re_decrypted = crypto.decrypt_content(&encrypted);
        assert_eq!(re_decrypted, secret_text);
    }
}
