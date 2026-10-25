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
    pub fn export_backup_zip(db: &Database, target_path: &Path) -> Result<String, String> {
        let notes = db.get_all_notes().map_err(|e| e.to_string())?;
        let categories = db.get_categories().map_err(|e| e.to_string())?;

        let file = File::create(target_path).map_err(|e| format!("Dosya oluşturulamadı: {}", e))?;
        let mut zip = ZipWriter::new(file);

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
            .unwrap()
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

        zip.finish().map_err(|e| e.to_string())?;
        Ok(format!("Yedek başarıyla alındı: {} not, {} kategori", notes.len(), categories.len()))
    }

    #[allow(dead_code)]
    pub fn import_backup_zip(db: &Database, source_path: &Path) -> Result<String, String> {
        let file = File::open(source_path).map_err(|e| format!("Dosya açılamadı: {}", e))?;
        let mut archive = zip::ZipArchive::new(file).map_err(|e| format!("Geçersiz zip arşivi: {}", e))?;

        let mut notes_json = String::new();
        let mut cats_json = String::new();

        for i in 0..archive.len() {
            let mut file = archive.by_index(i).map_err(|e| e.to_string())?;
            let file_name = match file.name() {
                Ok(name) => name.to_string(),
                Err(_) => continue,
            };
            match file_name.as_str() {
                "notes.json" => {
                    file.read_to_string(&mut notes_json).map_err(|e| e.to_string())?;
                }
                "categories.json" => {
                    file.read_to_string(&mut cats_json).map_err(|e| e.to_string())?;
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

        db.replace_all_notes(&notes, &categories)
            .map_err(|e| format!("Veritabanı geri yüklenemedi: {}", e))?;

        Ok(format!("Yedek başarıyla yüklendi: {} not, {} kategori aktarıldı", notes.len(), categories.len()))
    }
}
