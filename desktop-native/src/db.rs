use crate::models::{Category, Note};
use rusqlite::{params, Connection, Result};
use std::collections::BTreeSet;
use std::fs;
use std::path::PathBuf;

const NOTE_COLUMNS: &str = "id, title, content, timestamp, color, categoryId, isPinned, isArchived, \
    isInTrash, deletedAt, isLocked, reminderDateTime, tags, attachments, createdAt, updatedAt, \
    backgroundImage, folderId";

fn parse_json_array(raw: &str) -> Vec<String> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Vec::new();
    }
    if let Ok(vec) = serde_json::from_str::<Vec<String>>(trimmed) {
        return vec;
    }
    // Geriye dönük uyumluluk: eski masaüstü ';;;' ayracı
    if trimmed.contains(";;;") {
        return trimmed
            .split(";;;")
            .map(|s| s.trim())
            .filter(|s| !s.is_empty())
            .map(|s| s.to_string())
            .collect();
    }
    vec![trimmed.to_string()]
}

fn to_json_array(vec: &[String]) -> String {
    serde_json::to_string(vec).unwrap_or_else(|_| "[]".to_string())
}

fn now_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

pub struct Database {
    db_path: PathBuf,
}

impl Database {
    pub fn new() -> Self {
        let app_dir = Self::get_app_dir();
        fs::create_dir_all(&app_dir).ok();
        let db_path = app_dir.join("notes_desktop.db");

        let db = Database { db_path };
        db.init_schema().expect("Veritabanı başlatılamadı");
        db
    }

    /// Özel bir dosya yolundan veya test SQLite veritabanından başlatır
    pub fn with_path(db_path: PathBuf) -> Self {
        if let Some(parent) = db_path.parent() {
            fs::create_dir_all(parent).ok();
        }
        let db = Database { db_path };
        db.init_schema().expect("Veritabanı başlatılamadı");
        db
    }

    /// Bellek içi izole SQLite veritabanı (URI shared cache ile testler için)
    pub fn new_in_memory() -> Self {
        let rand_id: u32 = rand::random();
        let uri = format!("file:memdb_{}?mode=memory&cache=shared", rand_id);
        let db = Database {
            db_path: PathBuf::from(uri),
        };
        db.init_schema().expect("Bellek içi veritabanı başlatılamadı");
        db
    }

    pub fn get_app_dir() -> PathBuf {
        if let Ok(appdata) = std::env::var("APPDATA") {
            if !appdata.trim().is_empty() {
                return PathBuf::from(appdata).join("NotDefterim");
            }
        }
        if let Ok(home) = std::env::var("USERPROFILE") {
            if !home.trim().is_empty() {
                return PathBuf::from(home)
                    .join("AppData")
                    .join("Roaming")
                    .join("NotDefterim");
            }
        }
        if let Ok(home) = std::env::var("HOME") {
            return PathBuf::from(home).join(".notdefterim");
        }
        PathBuf::from(".notdefterim")
    }

    fn get_conn(&self) -> Result<Connection> {
        let conn = Connection::open(&self.db_path)?;
        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA busy_timeout = 5000;
             PRAGMA foreign_keys = ON;",
        )?;
        Ok(conn)
    }

    fn init_schema(&self) -> Result<()> {
        let conn = self.get_conn()?;

        // 1. Android Room ile %100 uyumlu categories tablosu
        conn.execute(
            "CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                color INTEGER NOT NULL DEFAULT 0
            );",
            [],
        )?;

        // 2. Android Room v1.4.4 ile %100 uyumlu notes tablosu
        conn.execute(
            "CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                color INTEGER NOT NULL DEFAULT 0,
                categoryId INTEGER,
                isPinned INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0,
                isInTrash INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER,
                isLocked INTEGER NOT NULL DEFAULT 0,
                reminderDateTime INTEGER,
                tags TEXT NOT NULL DEFAULT '[]',
                attachments TEXT NOT NULL DEFAULT '[]',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                backgroundImage TEXT,
                folderId INTEGER,
                FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE SET NULL
            );",
            [],
        )?;

        // 3. Şema ve sütun migrasyonu (Eski sürümlerden gelen isDeleted, reminderTime sütunları)
        Self::migrate_schema(&conn)?;

        // 4. Hızlı arama, filtreleme ve çöp kutusu indeksleri
        conn.execute_batch(
            "CREATE INDEX IF NOT EXISTS idx_notes_status ON notes(isInTrash, isArchived, isPinned, updatedAt DESC);
             CREATE INDEX IF NOT EXISTS idx_notes_category ON notes(categoryId);
             CREATE INDEX IF NOT EXISTS idx_notes_reminder ON notes(reminderDateTime) WHERE reminderDateTime IS NOT NULL;
             CREATE INDEX IF NOT EXISTS idx_notes_locked ON notes(isLocked);
             CREATE INDEX IF NOT EXISTS idx_notes_trash ON notes(isInTrash, deletedAt);",
        )?;

        Ok(())
    }

    fn migrate_schema(conn: &Connection) -> Result<()> {
        let mut stmt = conn.prepare("PRAGMA table_info(notes)")?;
        let col_iter = stmt.query_map([], |row| {
            let col_name: String = row.get(1)?;
            Ok(col_name)
        })?;

        let mut columns = BTreeSet::new();
        for col in col_iter {
            columns.insert(col?);
        }

        // isDeleted -> isInTrash dönüşümü
        if columns.contains("isDeleted") && !columns.contains("isInTrash") {
            let _ = conn.execute("ALTER TABLE notes RENAME COLUMN isDeleted TO isInTrash;", []);
        }

        // reminderTime -> reminderDateTime dönüşümü
        if columns.contains("reminderTime") && !columns.contains("reminderDateTime") {
            let _ = conn.execute("ALTER TABLE notes RENAME COLUMN reminderTime TO reminderDateTime;", []);
        }

        // tags sütunu kontrolü
        if !columns.contains("tags") {
            let _ = conn.execute("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT '[]';", []);
        }

        // folderId sütunu kontrolü
        if !columns.contains("folderId") {
            let _ = conn.execute("ALTER TABLE notes ADD COLUMN folderId INTEGER;", []);
        }

        Ok(())
    }

    pub fn get_active_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let query = format!(
            "SELECT {} FROM notes WHERE isInTrash = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC, timestamp DESC",
            NOTE_COLUMNS
        );
        let mut stmt = conn.prepare(&query)?;
        let note_iter = stmt.query_map([], |row| Self::row_to_note(row))?;
        let mut notes = Vec::new();
        for n in note_iter {
            notes.push(n?);
        }
        Ok(notes)
    }

    pub fn get_archived_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let query = format!(
            "SELECT {} FROM notes WHERE isInTrash = 0 AND isArchived = 1 ORDER BY updatedAt DESC, timestamp DESC",
            NOTE_COLUMNS
        );
        let mut stmt = conn.prepare(&query)?;
        let note_iter = stmt.query_map([], |row| Self::row_to_note(row))?;
        let mut notes = Vec::new();
        for n in note_iter {
            notes.push(n?);
        }
        Ok(notes)
    }

    pub fn get_trash_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let query = format!(
            "SELECT {} FROM notes WHERE isInTrash = 1 ORDER BY deletedAt DESC, timestamp DESC",
            NOTE_COLUMNS
        );
        let mut stmt = conn.prepare(&query)?;
        let note_iter = stmt.query_map([], |row| Self::row_to_note(row))?;
        let mut notes = Vec::new();
        for n in note_iter {
            notes.push(n?);
        }
        Ok(notes)
    }

    #[allow(dead_code)]
    pub fn get_note_by_id(&self, id: i64) -> Result<Option<Note>> {
        let conn = self.get_conn()?;
        let query = format!("SELECT {} FROM notes WHERE id = ?1", NOTE_COLUMNS);
        let mut stmt = conn.prepare(&query)?;
        let mut rows = stmt.query(params![id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(Self::row_to_note(row)?))
        } else {
            Ok(None)
        }
    }

    pub fn insert_note(&self, note: &Note) -> Result<i64> {
        let conn = self.get_conn()?;
        let tags_json = to_json_array(&note.tags);
        let att_json = to_json_array(&note.attachments);
        let now = now_millis();
        let created_at = if note.created_at == 0 { now } else { note.created_at };
        let updated_at = if note.updated_at == 0 { now } else { note.updated_at };
        let timestamp = if note.timestamp == 0 { now } else { note.timestamp };

        conn.execute(
            "INSERT INTO notes (
                title, content, timestamp, color, categoryId,
                isPinned, isArchived, isInTrash, deletedAt, isLocked,
                reminderDateTime, tags, attachments, createdAt, updatedAt,
                backgroundImage, folderId
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17)",
            params![
                note.title,
                note.content,
                timestamp,
                note.color,
                note.category_id,
                if note.is_pinned { 1 } else { 0 },
                if note.is_archived { 1 } else { 0 },
                if note.is_in_trash { 1 } else { 0 },
                note.deleted_at,
                if note.is_locked { 1 } else { 0 },
                note.reminder_date_time,
                tags_json,
                att_json,
                created_at,
                updated_at,
                note.background_image,
                note.folder_id
            ],
        )?;
        Ok(conn.last_insert_rowid())
    }

    pub fn update_note(&self, note: &Note) -> Result<()> {
        let conn = self.get_conn()?;
        let tags_json = to_json_array(&note.tags);
        let att_json = to_json_array(&note.attachments);
        let now = now_millis();
        let updated_at = if note.updated_at == 0 { now } else { note.updated_at };

        conn.execute(
            "UPDATE notes SET
                title = ?1, content = ?2, timestamp = ?3, color = ?4, categoryId = ?5,
                isPinned = ?6, isArchived = ?7, isInTrash = ?8, deletedAt = ?9, isLocked = ?10,
                reminderDateTime = ?11, tags = ?12, attachments = ?13, createdAt = ?14,
                updatedAt = ?15, backgroundImage = ?16, folderId = ?17
             WHERE id = ?18",
            params![
                note.title,
                note.content,
                note.timestamp,
                note.color,
                note.category_id,
                if note.is_pinned { 1 } else { 0 },
                if note.is_archived { 1 } else { 0 },
                if note.is_in_trash { 1 } else { 0 },
                note.deleted_at,
                if note.is_locked { 1 } else { 0 },
                note.reminder_date_time,
                tags_json,
                att_json,
                note.created_at,
                updated_at,
                note.background_image,
                note.folder_id,
                note.id
            ],
        )?;
        Ok(())
    }

    pub fn move_to_trash(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        let now = now_millis();
        conn.execute(
            "UPDATE notes SET isInTrash = 1, deletedAt = ?1, updatedAt = ?1, isPinned = 0 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    pub fn restore_from_trash(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        let now = now_millis();
        conn.execute(
            "UPDATE notes SET isInTrash = 0, deletedAt = NULL, updatedAt = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    pub fn delete_permanently(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute("DELETE FROM notes WHERE id = ?1", params![id])?;
        Ok(())
    }

    pub fn empty_trash(&self) -> Result<usize> {
        let conn = self.get_conn()?;
        let deleted = conn.execute("DELETE FROM notes WHERE isInTrash = 1", [])?;
        Ok(deleted)
    }

    pub fn toggle_pin(&self, id: i64) -> Result<bool> {
        let conn = self.get_conn()?;
        let now = now_millis();
        conn.execute(
            "UPDATE notes SET isPinned = 1 - isPinned, updatedAt = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        let mut stmt = conn.prepare("SELECT isPinned FROM notes WHERE id = ?1")?;
        let is_pin: i32 = stmt.query_row(params![id], |r| r.get(0))?;
        Ok(is_pin == 1)
    }

    pub fn toggle_archive(&self, id: i64) -> Result<bool> {
        let conn = self.get_conn()?;
        let now = now_millis();
        conn.execute(
            "UPDATE notes SET isArchived = 1 - isArchived, updatedAt = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        let mut stmt = conn.prepare("SELECT isArchived FROM notes WHERE id = ?1")?;
        let is_arch: i32 = stmt.query_row(params![id], |r| r.get(0))?;
        Ok(is_arch == 1)
    }

    /// Wikilinks: Belirtilen not başlığına referans veren (`[[Başlık]]` veya `[[Başlık|Etiket]]`) diğer notları döner.
    pub fn get_notes_linking_to(&self, title: &str, exclude_id: Option<i64>) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let p1 = format!("%[[{}]]%", title);
        let p2 = format!("%[[{}|%", title);

        let mut notes = Vec::new();
        if let Some(ex_id) = exclude_id {
            let query = format!(
                "SELECT {} FROM notes WHERE isInTrash = 0 AND id != ?1 AND (content LIKE ?2 OR content LIKE ?3) ORDER BY isPinned DESC, updatedAt DESC",
                NOTE_COLUMNS
            );
            let mut stmt = conn.prepare(&query)?;
            let rows = stmt.query_map(params![ex_id, p1, p2], |r| Self::row_to_note(r))?;
            for item in rows {
                notes.push(item?);
            }
        } else {
            let query = format!(
                "SELECT {} FROM notes WHERE isInTrash = 0 AND (content LIKE ?1 OR content LIKE ?2) ORDER BY isPinned DESC, updatedAt DESC",
                NOTE_COLUMNS
            );
            let mut stmt = conn.prepare(&query)?;
            let rows = stmt.query_map(params![p1, p2], |r| Self::row_to_note(r))?;
            for item in rows {
                notes.push(item?);
            }
        }
        Ok(notes)
    }

    /// Başlık, içerik veya etiketlerde arama yapar.
    pub fn search_notes(&self, query: &str) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let pattern = format!("%{}%", query);
        let sql = format!(
            "SELECT {} FROM notes WHERE isInTrash = 0 AND (title LIKE ?1 OR content LIKE ?1 OR tags LIKE ?1) ORDER BY isPinned DESC, updatedAt DESC",
            NOTE_COLUMNS
        );
        let mut stmt = conn.prepare(&sql)?;
        let rows = stmt.query_map(params![pattern], |r| Self::row_to_note(r))?;
        let mut notes = Vec::new();
        for item in rows {
            notes.push(item?);
        }
        Ok(notes)
    }

    /// Kategoriye göre aktif notları listeler.
    pub fn get_notes_by_category(&self, category_id: Option<i64>) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let mut notes = Vec::new();
        if let Some(cat_id) = category_id {
            let sql = format!(
                "SELECT {} FROM notes WHERE isInTrash = 0 AND isArchived = 0 AND categoryId = ?1 ORDER BY isPinned DESC, updatedAt DESC",
                NOTE_COLUMNS
            );
            let mut stmt = conn.prepare(&sql)?;
            let rows = stmt.query_map(params![cat_id], |r| Self::row_to_note(r))?;
            for item in rows {
                notes.push(item?);
            }
        } else {
            let sql = format!(
                "SELECT {} FROM notes WHERE isInTrash = 0 AND isArchived = 0 AND categoryId IS NULL ORDER BY isPinned DESC, updatedAt DESC",
                NOTE_COLUMNS
            );
            let mut stmt = conn.prepare(&sql)?;
            let rows = stmt.query_map([], |r| Self::row_to_note(r))?;
            for item in rows {
                notes.push(item?);
            }
        }
        Ok(notes)
    }

    /// Etikete göre aktif notları listeler.
    pub fn get_notes_by_tag(&self, tag: &str) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let pattern = format!("%\"{}\"%", tag);
        let sql = format!(
            "SELECT {} FROM notes WHERE isInTrash = 0 AND isArchived = 0 AND tags LIKE ?1 ORDER BY isPinned DESC, updatedAt DESC",
            NOTE_COLUMNS
        );
        let mut stmt = conn.prepare(&sql)?;
        let rows = stmt.query_map(params![pattern], |r| Self::row_to_note(r))?;
        let mut notes = Vec::new();
        for item in rows {
            notes.push(item?);
        }
        Ok(notes)
    }

    /// Sistemdeki tüm aktif notların etiketlerini alfabetik sıralı liste olarak döner.
    pub fn get_all_tags(&self) -> Result<Vec<String>> {
        let conn = self.get_conn()?;
        let mut stmt = conn.prepare("SELECT tags FROM notes WHERE isInTrash = 0")?;
        let rows = stmt.query_map([], |r| {
            let raw: String = r.get(0)?;
            Ok(raw)
        })?;

        let mut set = BTreeSet::new();
        for row in rows {
            let raw = row?;
            for tag in parse_json_array(&raw) {
                let trimmed = tag.trim();
                if !trimmed.is_empty() {
                    set.insert(trimmed.to_string());
                }
            }
        }
        Ok(set.into_iter().collect())
    }

    /// Klasöre göre notları filtreler.
    pub fn get_notes_by_folder(&self, folder_id: Option<i64>) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let mut notes = Vec::new();
        if let Some(f_id) = folder_id {
            let sql = format!(
                "SELECT {} FROM notes WHERE isInTrash = 0 AND isArchived = 0 AND folderId = ?1 ORDER BY isPinned DESC, updatedAt DESC",
                NOTE_COLUMNS
            );
            let mut stmt = conn.prepare(&sql)?;
            let rows = stmt.query_map(params![f_id], |r| Self::row_to_note(r))?;
            for item in rows {
                notes.push(item?);
            }
        } else {
            let sql = format!(
                "SELECT {} FROM notes WHERE isInTrash = 0 AND isArchived = 0 AND folderId IS NULL ORDER BY isPinned DESC, updatedAt DESC",
                NOTE_COLUMNS
            );
            let mut stmt = conn.prepare(&sql)?;
            let rows = stmt.query_map([], |r| Self::row_to_note(r))?;
            for item in rows {
                notes.push(item?);
            }
        }
        Ok(notes)
    }

    pub fn get_categories(&self) -> Result<Vec<Category>> {
        let conn = self.get_conn()?;
        let mut stmt = conn.prepare("SELECT id, name, color FROM categories ORDER BY name ASC")?;
        let cat_iter = stmt.query_map([], |row| {
            Ok(Category {
                id: row.get(0)?,
                name: row.get(1)?,
                color: row.get(2)?,
            })
        })?;

        let mut cats = Vec::new();
        for c in cat_iter {
            cats.push(c?);
        }
        Ok(cats)
    }

    pub fn insert_category(&self, name: &str, color: i32) -> Result<i64> {
        let conn = self.get_conn()?;
        conn.execute(
            "INSERT INTO categories (name, color) VALUES (?1, ?2)",
            params![name, color],
        )?;
        Ok(conn.last_insert_rowid())
    }

    pub fn update_category(&self, category: &Category) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute(
            "UPDATE categories SET name = ?1, color = ?2 WHERE id = ?3",
            params![category.name, category.color, category.id],
        )?;
        Ok(())
    }

    pub fn delete_category(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute("DELETE FROM categories WHERE id = ?1", params![id])?;
        Ok(())
    }

    pub fn get_all_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let query = format!("SELECT {} FROM notes ORDER BY id ASC", NOTE_COLUMNS);
        let mut stmt = conn.prepare(&query)?;
        let note_iter = stmt.query_map([], |row| Self::row_to_note(row))?;
        let mut notes = Vec::new();
        for n in note_iter {
            notes.push(n?);
        }
        Ok(notes)
    }

    pub fn replace_all_notes(&self, notes: &[Note], categories: &[Category]) -> Result<()> {
        let mut conn = self.get_conn()?;
        let tx = conn.transaction()?;

        tx.execute("DELETE FROM notes", [])?;
        tx.execute("DELETE FROM categories", [])?;

        for cat in categories {
            tx.execute(
                "INSERT INTO categories (id, name, color) VALUES (?1, ?2, ?3)",
                params![cat.id, cat.name, cat.color],
            )?;
        }

        for note in notes {
            let tags_json = to_json_array(&note.tags);
            let att_json = to_json_array(&note.attachments);
            tx.execute(
                "INSERT INTO notes (
                    id, title, content, timestamp, color, categoryId,
                    isPinned, isArchived, isInTrash, deletedAt, isLocked,
                    reminderDateTime, tags, attachments, createdAt, updatedAt,
                    backgroundImage, folderId
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18)",
                params![
                    note.id,
                    note.title,
                    note.content,
                    note.timestamp,
                    note.color,
                    note.category_id,
                    if note.is_pinned { 1 } else { 0 },
                    if note.is_archived { 1 } else { 0 },
                    if note.is_in_trash { 1 } else { 0 },
                    note.deleted_at,
                    if note.is_locked { 1 } else { 0 },
                    note.reminder_date_time,
                    tags_json,
                    att_json,
                    note.created_at,
                    note.updated_at,
                    note.background_image,
                    note.folder_id
                ],
            )?;
        }

        tx.commit()?;
        Ok(())
    }

    fn row_to_note(row: &rusqlite::Row) -> rusqlite::Result<Note> {
        let is_pin: i32 = row.get(6)?;
        let is_arch: i32 = row.get(7)?;
        let is_trash: i32 = row.get(8)?;
        let is_lock: i32 = row.get(10)?;
        let tags_raw: String = row.get(12)?;
        let att_raw: String = row.get(13)?;

        Ok(Note {
            id: row.get(0)?,
            title: row.get(1)?,
            content: row.get(2)?,
            timestamp: row.get(3)?,
            color: row.get(4)?,
            category_id: row.get(5)?,
            is_pinned: is_pin == 1,
            is_archived: is_arch == 1,
            is_in_trash: is_trash == 1,
            deleted_at: row.get(9)?,
            is_locked: is_lock == 1,
            reminder_date_time: row.get(11)?,
            tags: parse_json_array(&tags_raw),
            attachments: parse_json_array(&att_raw),
            created_at: row.get(14)?,
            updated_at: row.get(15)?,
            background_image: row.get(16)?,
            folder_id: row.get(17)?,
        })
    }
}

