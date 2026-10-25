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
            .unwrap()
            .as_millis() as i64;

        let config = VaultConfig {
            salt: BASE64_STANDARD.encode(&salt),
            hash: BASE64_STANDARD.encode(&hash),
            created_at: now,
        };

        if let Ok(json) = serde_json::to_string_pretty(&config) {
            let path = Self::config_path();
            fs::create_dir_all(path.parent().unwrap()).ok();
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

                    if computed_hash.as_slice() == stored_hash.as_slice() {
                        let mut key = [0u8; 32];
                        pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
                            password.as_bytes(),
                            &salt,
                            PBKDF2_ITERATIONS,
                            &mut key,
                        )
                        .ok();

                        let mut lock = self.session_key.write().unwrap();
                        *lock = Some(key);
                        return true;
                    }
                }
            }
        }
        false
    }

    pub fn lock_vault(&self) {
        let mut lock = self.session_key.write().unwrap();
        *lock = None;
    }

    pub fn is_unlocked(&self) -> bool {
        self.session_key.read().unwrap().is_some()
    }

    #[allow(dead_code)]
    pub fn encrypt_content(&self, plain_text: &str) -> String {
        if plain_text.is_empty() {
            return String::new();
        }
        let lock = self.session_key.read().unwrap();
        let key_bytes = match *lock {
            Some(k) => k,
            None => return plain_text.to_string(),
        };

        let mut iv = [0u8; IV_LEN];
        rand::rng().fill(&mut iv[..]);

        let cipher = Aes256Gcm::new_from_slice(&key_bytes).unwrap();
        let nonce = Nonce::try_from(iv.as_slice()).unwrap();

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
        let lock = self.session_key.read().unwrap();
        let key_bytes = match *lock {
            Some(k) => k,
            None => return "[🔒 Kilitli Not - Kasayı Açınız]".to_string(),
        };

        let base64_data = &cipher_text[PREFIX.len()..];
        if let Ok(combined) = BASE64_STANDARD.decode(base64_data) {
            if combined.len() < IV_LEN {
                return "[Bozuk Şifreli İçerik]".to_string();
            }
            let (iv, ciphertext) = combined.split_at(IV_LEN);
            let cipher = Aes256Gcm::new_from_slice(&key_bytes).unwrap();
            let nonce = Nonce::try_from(iv).unwrap();

            match cipher.decrypt(&nonce, ciphertext) {
                Ok(decrypted_bytes) => {
                    String::from_utf8(decrypted_bytes).unwrap_or_else(|_| "[Geçersiz UTF-8]".to_string())
                }
                Err(_) => "[Şifre Çözülemedi - Hatalı Anahtar]".to_string(),
            }
        } else {
            "[Bozuk Base64 Verisi]".to_string()
        }
    }
}
