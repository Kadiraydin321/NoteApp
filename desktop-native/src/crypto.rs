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

#[allow(dead_code)]
const PREFIX: &str = "ENC_GCM_V1:";
const SALT_LEN: usize = 16;
#[allow(dead_code)]
const IV_LEN: usize = 12;
const PBKDF2_ITERATIONS: u32 = 65536;

fn zeroize_bytes(slice: &mut [u8]) {
    for b in slice.iter_mut() {
        unsafe { std::ptr::write_volatile(b, 0) };
    }
    std::sync::atomic::compiler_fence(std::sync::atomic::Ordering::SeqCst);
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
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

    fn config_path() -> std::path::PathBuf {
        crate::db::Database::get_app_dir().join("vault_config.json")
    }

    pub fn has_master_password(&self) -> bool {
        let path = Self::config_path();
        if !path.exists() {
            return false;
        }
        if let Ok(content) = fs::read_to_string(path) {
            serde_json::from_str::<VaultConfig>(&content).is_ok()
        } else {
            false
        }
    }

    pub fn setup_master_password(&self, password: &str) -> bool {
        if password.len() < 4 {
            return false;
        }

        let mut salt = [0u8; SALT_LEN];
        rand::rng().fill(&mut salt[..]);

        let mut hasher = Sha256::new();
        hasher.update(&salt);
        hasher.update(password.as_bytes());
        let hash = hasher.finalize();

        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;

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

    pub fn unlock_vault(&self, password: &str) -> bool {
        let path = Self::config_path();
        if let Ok(content) = fs::read_to_string(path) {
            if let Ok(config) = serde_json::from_str::<VaultConfig>(&content) {
                if let (Ok(salt), Ok(stored_hash)) = (
                    BASE64_STANDARD.decode(&config.salt),
                    BASE64_STANDARD.decode(&config.hash),
                ) {
                    let mut hasher = Sha256::new();
                    hasher.update(&salt);
                    hasher.update(password.as_bytes());
                    let computed_hash = hasher.finalize();

                    if constant_time_eq(computed_hash.as_slice(), stored_hash.as_slice()) {
                        let mut key = [0u8; 32];
                        let pbkdf2_res = pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
                            password.as_bytes(),
                            &salt,
                            PBKDF2_ITERATIONS,
                            &mut key,
                        );

                        if pbkdf2_res.is_err() {
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
                        return true;
                    }
                }
            }
        }
        false
    }

    pub fn lock_vault(&self) {
        if let Ok(mut lock) = self.session_key.write() {
            if let Some(ref mut key) = *lock {
                zeroize_bytes(key);
            }
            *lock = None;
        }
    }

    pub fn is_unlocked(&self) -> bool {
        self.session_key.read().map(|k| k.is_some()).unwrap_or(false)
    }

    #[allow(dead_code)]
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

        let mut iv = [0u8; IV_LEN];
        rand::rng().fill(&mut iv[..]);

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
        let nonce = match Nonce::try_from(iv.as_slice()) {
            Ok(n) => n,
            Err(_) => return plain_text.to_string(),
        };

        match cipher.encrypt(&nonce, plain_text.as_bytes()) {
            Ok(ciphertext) => {
                let mut combined = Vec::with_capacity(iv.len() + ciphertext.len());
                combined.extend_from_slice(&iv);
                combined.extend_from_slice(&ciphertext);
                format!("{}{}", PREFIX, BASE64_STANDARD.encode(&combined))
            }
            Err(_) => plain_text.to_string(),
        }
    }

    #[allow(dead_code)]
    pub fn decrypt_content(&self, cipher_text: &str) -> String {
        if !cipher_text.starts_with(PREFIX) {
            return cipher_text.to_string();
        }
        let lock = match self.session_key.read() {
            Ok(l) => l,
            Err(_) => return "[🔒 Kilitli Not - Kasayı Açınız]".to_string(),
        };
        let mut key_bytes = match *lock {
            Some(k) => k,
            None => return "[🔒 Kilitli Not - Kasayı Açınız]".to_string(),
        };

        let base64_data = &cipher_text[PREFIX.len()..];
        if let Ok(combined) = BASE64_STANDARD.decode(base64_data) {
            if combined.len() < IV_LEN + 16 {
                zeroize_bytes(&mut key_bytes);
                return "[Bozuk Şifreli İçerik]".to_string();
            }
            let (iv, ciphertext) = combined.split_at(IV_LEN);
            let cipher = match Aes256Gcm::new_from_slice(&key_bytes) {
                Ok(c) => {
                    zeroize_bytes(&mut key_bytes);
                    c
                }
                Err(_) => {
                    zeroize_bytes(&mut key_bytes);
                    return "[Şifre Çözülemedi - Hatalı Anahtar]".to_string();
                }
            };
            let nonce = match Nonce::try_from(iv) {
                Ok(n) => n,
                Err(_) => return "[Bozuk Şifreli İçerik]".to_string(),
            };

            match cipher.decrypt(&nonce, ciphertext) {
                Ok(decrypted_bytes) => {
                    String::from_utf8(decrypted_bytes).unwrap_or_else(|_| "[Geçersiz UTF-8]".to_string())
                }
                Err(_) => "[Şifre Çözülemedi - Hatalı Anahtar]".to_string(),
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
