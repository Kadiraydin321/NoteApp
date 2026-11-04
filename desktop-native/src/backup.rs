use crate::db::Database;
use crate::models::{Category, Note};
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::{Read, Write};
use std::path::Path;
use zip::write::SimpleFileOptions;
use zip::ZipWriter;

#[derive(Serialize, Deserialize)]
struct BackupMetadata {
    version: i32,
    #[serde(rename = "appName")]
    app_name: String,
    timestamp: i64,
    #[serde(rename = "notesCount")]
    notes_count: usize,
    #[serde(rename = "categoriesCount")]
    categories_count: usize,
}

pub struct BackupManager;

impl BackupManager {
    pub fn create_backup_zip_buffer(db: &Database) -> Result<Vec<u8>, String> {
        let notes = db.get_all_notes().map_err(|e| e.to_string())?;
        let categories = db.get_categories().map_err(|e| e.to_string())?;

        let buffer = std::io::Cursor::new(Vec::new());
        let mut zip = ZipWriter::new(buffer);

        let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);

        // 1. notes.json
        zip.start_file("notes.json", options)
            .map_err(|e| e.to_string())?;
        let notes_json = serde_json::to_string_pretty(&notes).map_err(|e| e.to_string())?;
        zip.write_all(notes_json.as_bytes())
            .map_err(|e| e.to_string())?;

        // 2. categories.json
        zip.start_file("categories.json", options)
            .map_err(|e| e.to_string())?;
        let cats_json = serde_json::to_string_pretty(&categories).map_err(|e| e.to_string())?;
        zip.write_all(cats_json.as_bytes())
            .map_err(|e| e.to_string())?;

        // 3. metadata.json
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;
        let meta = BackupMetadata {
            version: 1,
            app_name: "NotDefterim".to_string(),
            timestamp: now,
            notes_count: notes.len(),
            categories_count: categories.len(),
        };
        zip.start_file("metadata.json", options)
            .map_err(|e| e.to_string())?;
        let meta_json = serde_json::to_string_pretty(&meta).map_err(|e| e.to_string())?;
        zip.write_all(meta_json.as_bytes())
            .map_err(|e| e.to_string())?;

        let cursor = zip.finish().map_err(|e| e.to_string())?;
        Ok(cursor.into_inner())
    }

    pub fn restore_backup_zip_buffer(db: &Database, buffer: &[u8]) -> Result<(usize, usize), String> {
        let cursor = std::io::Cursor::new(buffer);
        let mut archive = zip::ZipArchive::new(cursor).map_err(|e| format!("Geçersiz zip arşivi: {}", e))?;

        let mut notes_json = String::new();
        let mut cats_json = String::new();

        const MAX_ENTRY_SIZE: u64 = 50 * 1024 * 1024; // 50MB max per entry
        const MAX_ARCHIVE_ENTRIES: usize = 10_000;

        if archive.len() > MAX_ARCHIVE_ENTRIES {
            return Err("Zip arşivi çok fazla girdi içeriyor (maksimum 10000)".to_string());
        }

        for i in 0..archive.len() {
            let mut file = archive.by_index(i).map_err(|e| e.to_string())?;
            
            // Zip Slip / Path Traversal koruması: enclosed_name() None dönerse '..' veya mutlak yol vardır
            let enclosed = match file.enclosed_name() {
                Some(p) => p.to_path_buf(),
                None => continue, // Güvensiz path traversal girişlerini yoksay
            };

            let file_name = enclosed.to_string_lossy().to_string();
            if file.size() > MAX_ENTRY_SIZE {
                return Err(format!("Dosya çok büyük: {} ({} bytes)", file_name, file.size()));
            }

            match file_name.as_str() {
                "notes.json" => {
                    // Zip bomb koruması: Açılmış veriyi de MAX_ENTRY_SIZE ile sınırla
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
                _ => {}
            }
        }

        if notes_json.is_empty() {
            return Err("Yedek dosyası içinde 'notes.json' bulunamadı!".to_string());
        }

        let notes: Vec<Note> = serde_json::from_str(&notes_json).map_err(|e| format!("Notlar okunamadı: {}", e))?;
        let categories: Vec<Category> = if cats_json.is_empty() {
            Vec::new()
        } else {
            serde_json::from_str(&cats_json).unwrap_or_default()
        };

        let notes_count = notes.len();
        let cats_count = categories.len();

        db.replace_all_notes(&notes, &categories)
            .map_err(|e| format!("Veritabanı geri yüklenemedi: {}", e))?;

        Ok((notes_count, cats_count))
    }

    pub fn export_backup_zip(db: &Database, target_path: &Path) -> Result<String, String> {
        let bytes = Self::create_backup_zip_buffer(db)?;
        let mut file = File::create(target_path).map_err(|e| format!("Dosya oluşturulamadı: {}", e))?;
        file.write_all(&bytes).map_err(|e| format!("Dosyaya yazılamadı: {}", e))?;
        Ok(format!("Yedek başarıyla alındı ({} bayt)", bytes.len()))
    }

    #[allow(dead_code)]
    pub fn import_backup_zip(db: &Database, source_path: &Path) -> Result<String, String> {
        let mut file = File::open(source_path).map_err(|e| format!("Dosya açılamadı: {}", e))?;
        let mut buffer = Vec::new();
        file.read_to_end(&mut buffer).map_err(|e| format!("Dosya okunamadı: {}", e))?;
        let (notes_count, cats_count) = Self::restore_backup_zip_buffer(db, &buffer)?;
        Ok(format!("Yedek başarıyla yüklendi: {} not, {} kategori aktarıldı", notes_count, cats_count))
    }
}
