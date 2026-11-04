use crate::crypto::{constant_time_eq, zeroize_bytes};
use crate::db::Database;
use crate::models::{Category, Note};
use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use rand::RngExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::{Cursor, Read, Write};
use std::path::{Component, Path};
use zip::write::SimpleFileOptions;
use zip::{ZipArchive, ZipWriter};

/// .notevault ikili dosya standardı sabitleri
pub const NOTEVLT_MAGIC: [u8; 8] = [0x4E, 0x4F, 0x54, 0x45, 0x56, 0x4C, 0x54, 0x31]; // "NOTEVLT1"
pub const SALT_LEN: usize = 16;
pub const IV_LEN: usize = 12;
pub const HASH_LEN: usize = 32;
pub const HEADER_LEN: usize = 8 + SALT_LEN + IV_LEN + HASH_LEN; // 68 bytes

pub const DEFAULT_PBKDF2_ITERATIONS: u32 = 65536;
pub const PBKDF2_ITERATION_CANDIDATES: [u32; 8] = [
    65536, 12000, 100000, 10000, 50000, 1000, 5000, 20000,
];

pub const MAX_ENTRY_SIZE: u64 = 50 * 1024 * 1024; // 50MB max per entry (Zip bomb / OOM koruması)
pub const MAX_TOTAL_EXTRACTED_SIZE: u64 = 250 * 1024 * 1024; // 250MB kümülatif açılmış veri sınırı
pub const MAX_ARCHIVE_ENTRIES: usize = 10_000;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupInfo {
    #[serde(default = "default_backup_version")]
    pub version: i32,
    #[serde(rename = "appName", default = "default_app_name")]
    pub app_name: String,
    #[serde(default)]
    pub timestamp: i64,
    #[serde(rename = "exportDate", default)]
    pub export_date: String,
    #[serde(rename = "notesCount", default)]
    pub notes_count: usize,
    #[serde(rename = "categoriesCount", default)]
    pub categories_count: usize,
}

fn default_backup_version() -> i32 {
    2
}

fn default_app_name() -> String {
    "NotDefterim".to_string()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupRestoreStats {
    pub notes_count: usize,
    pub categories_count: usize,
    pub attachments_count: usize,
    pub is_vault: bool,
}

pub struct BackupManager;

impl BackupManager {
    /// .notevault başlığını hızlıca tespit eder (MAGIC kontrolü)
    pub fn is_notevault(buffer: &[u8]) -> bool {
        buffer.len() >= HEADER_LEN && &buffer[0..8] == NOTEVLT_MAGIC
    }

    /// Kasa parolasını SHA-256(salt + password) ile doğrular (sabit zamanlı)
    pub fn verify_vault_password(buffer: &[u8], password: &str) -> Result<bool, String> {
        if buffer.len() < HEADER_LEN {
            return Err("Dosya çok küçük, geçerli .notevault başlığı içermiyor".to_string());
        }
        if &buffer[0..8] != NOTEVLT_MAGIC {
            return Err("Geçersiz dosya: 'NOTEVLT1' başlığı bulunamadı".to_string());
        }
        let salt = &buffer[8..24];
        let stored_hash = &buffer[36..68];

        let mut hasher = Sha256::new();
        hasher.update(salt);
        hasher.update(password.as_bytes());
        let computed = hasher.finalize();

        Ok(constant_time_eq(computed.as_slice(), stored_hash))
    }

    /// ZIP yedekleme arşivi oluşturur:
    /// `backup_info.json`, `categories.json`, `notes.json`, `note_history.json`, `templates.json`, `attachments/`
    pub fn create_backup_zip_buffer(db: &Database) -> Result<Vec<u8>, String> {
        let notes = db.get_all_notes().map_err(|e| e.to_string())?;
        let categories = db.get_categories().map_err(|e| e.to_string())?;

        let buffer = Cursor::new(Vec::new());
        let mut zip = ZipWriter::new(buffer);

        let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);

        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;

        // 1. backup_info.json (ve geriye uyumluluk için metadata.json)
        let info = BackupInfo {
            version: 2,
            app_name: "NotDefterim".to_string(),
            timestamp: now,
            export_date: format!("Unix-{}", now),
            notes_count: notes.len(),
            categories_count: categories.len(),
        };
        let info_json = serde_json::to_string_pretty(&info).map_err(|e| e.to_string())?;

        zip.start_file("backup_info.json", options)
            .map_err(|e| e.to_string())?;
        zip.write_all(info_json.as_bytes())
            .map_err(|e| e.to_string())?;

        zip.start_file("metadata.json", options)
            .map_err(|e| e.to_string())?;
        zip.write_all(info_json.as_bytes())
            .map_err(|e| e.to_string())?;

        // 2. categories.json
        zip.start_file("categories.json", options)
            .map_err(|e| e.to_string())?;
        let cats_json = serde_json::to_string_pretty(&categories).map_err(|e| e.to_string())?;
        zip.write_all(cats_json.as_bytes())
            .map_err(|e| e.to_string())?;

        // 3. notes.json
        zip.start_file("notes.json", options)
            .map_err(|e| e.to_string())?;
        let notes_json = serde_json::to_string_pretty(&notes).map_err(|e| e.to_string())?;
        zip.write_all(notes_json.as_bytes())
            .map_err(|e| e.to_string())?;

        // 4. note_history.json
        zip.start_file("note_history.json", options)
            .map_err(|e| e.to_string())?;
        zip.write_all(b"[]").map_err(|e| e.to_string())?;

        // 5. templates.json
        zip.start_file("templates.json", options)
            .map_err(|e| e.to_string())?;
        zip.write_all(b"[]").map_err(|e| e.to_string())?;

        // 6. attachments/ dizini ve mevcut dosyalar
        let attachments_dir = Database::get_app_dir().join("attachments");
        if attachments_dir.exists() && attachments_dir.is_dir() {
            if let Ok(entries) = std::fs::read_dir(&attachments_dir) {
                for entry in entries.flatten() {
                    let path = entry.path();
                    if path.is_file() {
                        if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                            let zip_entry_name = format!("attachments/{}", file_name);
                            if let Ok(data) = std::fs::read(&path) {
                                if (data.len() as u64) <= MAX_ENTRY_SIZE {
                                    zip.start_file(zip_entry_name, options).ok();
                                    zip.write_all(&data).ok();
                                }
                            }
                        }
                    }
                }
            }
        }

        let cursor = zip.finish().map_err(|e| e.to_string())?;
        Ok(cursor.into_inner())
    }

    /// ZIP baytlarını .notevault ikili standardıyla şifreler:
    /// MAGIC (8B: NOTEVLT1) + SALT (16B) + IV (12B) + PASSWORD HASH (32B: SHA-256(SALT + password)) + CIPHERTEXT (AES-256-GCM)
    pub fn create_notevault_buffer(db: &Database, password: &str) -> Result<Vec<u8>, String> {
        if password.is_empty() {
            return Err("Kasa şifresi boş olamaz".to_string());
        }

        let plain_zip = Self::create_backup_zip_buffer(db)?;

        // 1. Rastgele Salt (16B) ve IV (12B)
        let mut salt = [0u8; SALT_LEN];
        rand::rng().fill(&mut salt[..]);

        let mut iv = [0u8; IV_LEN];
        rand::rng().fill(&mut iv[..]);

        // 2. Parola kontrol hash'i: SHA-256(salt + password) (32B)
        let mut hasher = Sha256::new();
        hasher.update(&salt);
        hasher.update(password.as_bytes());
        let password_hash: [u8; HASH_LEN] = hasher.finalize().into();

        // 3. PBKDF2-HMAC-SHA256 (65.536 iterasyon) ile 256-bit anahtar türetme
        let mut key = [0u8; 32];
        let pbkdf2_res = pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
            password.as_bytes(),
            &salt,
            DEFAULT_PBKDF2_ITERATIONS,
            &mut key,
        );
        if let Err(e) = pbkdf2_res {
            zeroize_bytes(&mut key);
            return Err(format!("PBKDF2 anahtar türetme başarısız: {:?}", e));
        }

        // 4. AES-256-GCM ile ZIP baytlarını şifreleme
        let cipher = Aes256Gcm::new_from_slice(&key).map_err(|e| {
            zeroize_bytes(&mut key);
            format!("AES-GCM başlatılamadı: {:?}", e)
        })?;
        zeroize_bytes(&mut key);

        let nonce = Nonce::try_from(iv.as_slice())
            .map_err(|e| format!("Geçersiz IV / Nonce: {:?}", e))?;

        let ciphertext = cipher
            .encrypt(&nonce, plain_zip.as_slice())
            .map_err(|e| format!("AES-256-GCM şifreleme hatası: {:?}", e))?;

        // 5. İkili başlık oluşturma (toplam 68 bayt başlık + ciphertext)
        let mut output = Vec::with_capacity(HEADER_LEN + ciphertext.len());
        output.extend_from_slice(&NOTEVLT_MAGIC);
        output.extend_from_slice(&salt);
        output.extend_from_slice(&iv);
        output.extend_from_slice(&password_hash);
        output.extend_from_slice(&ciphertext);

        Ok(output)
    }

    /// .notevault ikili verisini deşifre eder
    pub fn decrypt_notevault(buffer: &[u8], password: &str) -> Result<Vec<u8>, String> {
        if buffer.len() < HEADER_LEN {
            return Err(format!(
                "Dosya çok küçük ({} bayt). Geçerli .notevault başlığı en az {} bayt olmalıdır.",
                buffer.len(),
                HEADER_LEN
            ));
        }

        if &buffer[0..8] != NOTEVLT_MAGIC {
            return Err("Geçersiz dosya formatı: 'NOTEVLT1' sihirli başlığı bulunamadı.".to_string());
        }

        let salt = &buffer[8..24];
        let iv = &buffer[24..36];
        let stored_hash = &buffer[36..68];
        let ciphertext = &buffer[68..];

        // 1. SHA-256(salt + password) ile Parola Doğrulama
        let mut hasher = Sha256::new();
        hasher.update(salt);
        hasher.update(password.as_bytes());
        let computed_hash = hasher.finalize();

        if !constant_time_eq(computed_hash.as_slice(), stored_hash) {
            return Err("Hatalı Kasa Kilidi! Girilen şifre yedek dosyası ile eşleşmiyor.".to_string());
        }

        // 2. PBKDF2 ve AES-256-GCM ile Şifre Çözme (Olası iterasyonları dene)
        let nonce = Nonce::try_from(iv)
            .map_err(|e| format!("Geçersiz IV / Nonce: {:?}", e))?;

        let mut decrypted_opt: Option<Vec<u8>> = None;
        let mut last_error = String::new();

        for &iters in &PBKDF2_ITERATION_CANDIDATES {
            let mut key = [0u8; 32];
            let res = pbkdf2::pbkdf2::<hmac::Hmac<Sha256>>(
                password.as_bytes(),
                salt,
                iters,
                &mut key,
            );
            if res.is_ok() {
                if let Ok(cipher) = Aes256Gcm::new_from_slice(&key) {
                    match cipher.decrypt(&nonce, ciphertext) {
                        Ok(decrypted) => {
                            zeroize_bytes(&mut key);
                            decrypted_opt = Some(decrypted);
                            break;
                        }
                        Err(e) => {
                            last_error = format!("{:?}", e);
                        }
                    }
                }
            }
            zeroize_bytes(&mut key);
        }

        decrypted_opt.ok_or_else(|| {
            format!("AES-GCM şifre çözme hatası (veri bozulmuş olabilir): {}", last_error)
        })
    }

    /// Hem düz ZIP hem de şifreli .notevault yedeklerini güvenli ve atomik biçimde geri yükler
    pub fn restore_backup_buffer(
        db: &Database,
        buffer: &[u8],
        password: Option<&str>,
    ) -> Result<BackupRestoreStats, String> {
        let is_vault = Self::is_notevault(buffer);

        let zip_bytes = if is_vault {
            let pwd = password.ok_or_else(|| {
                "Bu yedek dosyası şifreli bir .notevault kasasıdır. Lütfen kasa şifresini girin.".to_string()
            })?;
            Self::decrypt_notevault(buffer, pwd)?
        } else if (buffer.len() >= 4 && &buffer[0..4] == b"PK\x03\x04") || buffer.starts_with(b"PK") {
            buffer.to_vec()
        } else {
            return Err("Geçersiz yedek dosyası: ZIP veya NOTEVLT1 başlığı bulunamadı".to_string());
        };

        // Geriye dönük uyumluluk: Eğer çözülen veri ZIP değil tekil JSON VaultPayload ise
        if !zip_bytes.starts_with(b"PK\x03\x04") && !zip_bytes.starts_with(b"PK") {
            if let Ok(payload) = serde_json::from_slice::<serde_json::Value>(&zip_bytes) {
                if let Some(notes_arr) = payload.get("notes").and_then(|v| v.as_array()) {
                    let notes: Vec<Note> = serde_json::from_value(serde_json::Value::Array(notes_arr.clone()))
                        .map_err(|e| format!("JSON notları okunamadı: {}", e))?;
                    let notes_count = notes.len();
                    db.replace_all_notes(&notes, &[])
                        .map_err(|e| format!("Veritabanı güncellenemedi: {}", e))?;
                    return Ok(BackupRestoreStats {
                        notes_count,
                        categories_count: 0,
                        attachments_count: 0,
                        is_vault,
                    });
                }
            }
        }

        let cursor = Cursor::new(zip_bytes);
        let mut archive = ZipArchive::new(cursor)
            .map_err(|e| format!("Geçersiz zip arşivi: {}", e))?;

        if archive.len() > MAX_ARCHIVE_ENTRIES {
            return Err(format!(
                "Zip arşivi çok fazla girdi içeriyor (maksimum {})",
                MAX_ARCHIVE_ENTRIES
            ));
        }

        let mut notes_json = String::new();
        let mut cats_json = String::new();
        let mut total_extracted_size: u64 = 0;
        let mut restored_attachments = 0usize;

        let attachments_dir = Database::get_app_dir().join("attachments");

        for i in 0..archive.len() {
            let mut file = archive.by_index(i).map_err(|e| e.to_string())?;

            // Zip Slip / Path Traversal koruması:
            // 1. enclosed_name() None dönerse '..' veya mutlak yol vardır
            let enclosed = match file.enclosed_name() {
                Some(p) => p.to_path_buf(),
                None => continue, // Güvensiz path traversal girişlerini yoksay
            };

            // 2. Ek savunma: ParentDir, RootDir, Prefix bileşenleri engellenir
            if enclosed.components().any(|c| {
                matches!(
                    c,
                    Component::ParentDir | Component::RootDir | Component::Prefix(_)
                )
            }) {
                continue;
            }

            let file_name = enclosed.to_string_lossy().to_string();

            // 3. Decompression Bomb koruması: Tekil dosya boyutu sınırı
            if file.size() > MAX_ENTRY_SIZE {
                return Err(format!(
                    "Dosya çok büyük: {} ({} bayt, sınır {} bayt)",
                    file_name,
                    file.size(),
                    MAX_ENTRY_SIZE
                ));
            }

            // 4. Kümülatif açılmış boyut sınırı
            total_extracted_size = total_extracted_size.saturating_add(file.size());
            if total_extracted_size > MAX_TOTAL_EXTRACTED_SIZE {
                return Err(format!(
                    "Toplam açılmış arşiv boyutu güvenlik sınırını aşıyor (maksimum {} bayt)",
                    MAX_TOTAL_EXTRACTED_SIZE
                ));
            }

            match file_name.as_str() {
                "notes.json" => {
                    let mut reader = (&mut file).take(MAX_ENTRY_SIZE + 1);
                    reader.read_to_string(&mut notes_json).map_err(|e| e.to_string())?;
                    if notes_json.len() as u64 > MAX_ENTRY_SIZE {
                        return Err("notes.json izin verilen 50MB sınırını aşıyor".to_string());
                    }
                }
                "categories.json" => {
                    let mut reader = (&mut file).take(MAX_ENTRY_SIZE + 1);
                    reader.read_to_string(&mut cats_json).map_err(|e| e.to_string())?;
                    if cats_json.len() as u64 > MAX_ENTRY_SIZE {
                        return Err("categories.json izin verilen 50MB sınırını aşıyor".to_string());
                    }
                }
                name if name.starts_with("attachments/") && !file.is_dir() => {
                    // attachments/ klasöründeki dosyayı güvenle diskteki attachments klasörüne çıkar
                    if let Ok(rel) = enclosed.strip_prefix("attachments") {
                        if let Some(clean_name) = rel.file_name() {
                            let dest = attachments_dir.join(clean_name);
                            if dest.starts_with(&attachments_dir) {
                                std::fs::create_dir_all(&attachments_dir).ok();
                                if let Ok(mut out) = File::create(&dest) {
                                    let mut reader = (&mut file).take(MAX_ENTRY_SIZE + 1);
                                    if std::io::copy(&mut reader, &mut out).is_ok() {
                                        restored_attachments += 1;
                                    }
                                }
                            }
                        }
                    }
                }
                _ => {}
            }
        }

        if notes_json.is_empty() {
            return Err("Yedek dosyası içinde 'notes.json' bulunamadı!".to_string());
        }

        let notes: Vec<Note> = serde_json::from_str(&notes_json)
            .map_err(|e| format!("Notlar okunamadı: {}", e))?;
        let categories: Vec<Category> = if cats_json.is_empty() {
            Vec::new()
        } else {
            serde_json::from_str(&cats_json).unwrap_or_default()
        };

        let notes_count = notes.len();
        let cats_count = categories.len();

        // Atomik SQLite transaction aktarımı
        db.replace_all_notes(&notes, &categories)
            .map_err(|e| format!("Veritabanı geri yüklenemedi: {}", e))?;

        Ok(BackupRestoreStats {
            notes_count,
            categories_count: cats_count,
            attachments_count: restored_attachments,
            is_vault,
        })
    }

    /// Geriye dönük uyumluluk: Sadece ZIP tamponunu açar ve (notes_count, cats_count) döndürür
    pub fn restore_backup_zip_buffer(db: &Database, buffer: &[u8]) -> Result<(usize, usize), String> {
        let stats = Self::restore_backup_buffer(db, buffer, None)?;
        Ok((stats.notes_count, stats.categories_count))
    }

    /// Yedek dosyasını diske yazar (parola varsa .notevault, yoksa .zip)
    pub fn export_backup(
        db: &Database,
        target_path: &Path,
        password: Option<&str>,
    ) -> Result<String, String> {
        let bytes = match password {
            Some(pwd) if !pwd.is_empty() => Self::create_notevault_buffer(db, pwd)?,
            _ => Self::create_backup_zip_buffer(db)?,
        };

        if let Some(parent) = target_path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }

        let mut file = File::create(target_path)
            .map_err(|e| format!("Dosya oluşturulamadı: {}", e))?;
        file.write_all(&bytes)
            .map_err(|e| format!("Dosyaya yazılamadı: {}", e))?;

        let format_desc = if password.is_some() {
            ".notevault (AES-256-GCM Şifreli Kasa)"
        } else {
            ".zip (Düz Arşiv)"
        };
        Ok(format!(
            "Yedek başarıyla alındı ({} bayt, format: {})",
            bytes.len(),
            format_desc
        ))
    }

    /// Geriye dönük uyumluluk: Düz ZIP yedek dosyası oluşturur
    pub fn export_backup_zip(db: &Database, target_path: &Path) -> Result<String, String> {
        Self::export_backup(db, target_path, None)
    }

    /// Diskteki yedek dosyasını (şifreli veya düz) içe aktarır
    pub fn import_backup(
        db: &Database,
        source_path: &Path,
        password: Option<&str>,
    ) -> Result<BackupRestoreStats, String> {
        let mut file = File::open(source_path)
            .map_err(|e| format!("Dosya açılamadı: {}", e))?;
        let mut buffer = Vec::new();
        file.read_to_end(&mut buffer)
            .map_err(|e| format!("Dosya okunamadı: {}", e))?;
        Self::restore_backup_buffer(db, &buffer, password)
    }

    /// Geriye dönük uyumluluk: Diskteki düz ZIP yedeğini içe aktarır
    pub fn import_backup_zip(db: &Database, source_path: &Path) -> Result<String, String> {
        let stats = Self::import_backup(db, source_path, None)?;
        Ok(format!(
            "Yedek başarıyla yüklendi: {} not, {} kategori aktarıldı",
            stats.notes_count,
            stats.categories_count
        ))
    }
}
