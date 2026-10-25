use crate::models::{Category, Note};
use rusqlite::{params, Connection, Result};
use std::fs;
use std::path::PathBuf;

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
        Connection::open(&self.db_path)
    }

    fn init_schema(&self) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute(
            "CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                color INTEGER NOT NULL DEFAULT 0
            );",
            [],
        )?;

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
                isDeleted INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER,
                isLocked INTEGER NOT NULL DEFAULT 0,
                reminderTime INTEGER,
                attachments TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                backgroundImage TEXT,
                FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE SET NULL
            );",
            [],
        )?;

        Ok(())
    }

    pub fn get_active_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let mut stmt = conn.prepare(
            "SELECT id, title, content, timestamp, color, categoryId, isPinned, isArchived,
                    isDeleted, deletedAt, isLocked, reminderTime, attachments, createdAt, updatedAt, backgroundImage
             FROM notes
             WHERE isDeleted = 0 AND isArchived = 0
             ORDER BY isPinned DESC, updatedAt DESC, timestamp DESC",
        )?;

        let note_iter = stmt.query_map([], |row| Self::row_to_note(row))?;
        let mut notes = Vec::new();
        for n in note_iter {
            notes.push(n?);
        }
        Ok(notes)
    }

    pub fn get_archived_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let mut stmt = conn.prepare(
            "SELECT id, title, content, timestamp, color, categoryId, isPinned, isArchived,
                    isDeleted, deletedAt, isLocked, reminderTime, attachments, createdAt, updatedAt, backgroundImage
             FROM notes
             WHERE isArchived = 1 AND isDeleted = 0
             ORDER BY updatedAt DESC, timestamp DESC",
        )?;

        let note_iter = stmt.query_map([], |row| Self::row_to_note(row))?;
        let mut notes = Vec::new();
        for n in note_iter {
            notes.push(n?);
        }
        Ok(notes)
    }

    pub fn get_trash_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let mut stmt = conn.prepare(
            "SELECT id, title, content, timestamp, color, categoryId, isPinned, isArchived,
                    isDeleted, deletedAt, isLocked, reminderTime, attachments, createdAt, updatedAt, backgroundImage
             FROM notes
             WHERE isDeleted = 1
             ORDER BY deletedAt DESC, timestamp DESC",
        )?;

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
        let mut stmt = conn.prepare(
            "SELECT id, title, content, timestamp, color, categoryId, isPinned, isArchived,
                    isDeleted, deletedAt, isLocked, reminderTime, attachments, createdAt, updatedAt, backgroundImage
             FROM notes WHERE id = ?1",
        )?;

        let mut rows = stmt.query(params![id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(Self::row_to_note(row)?))
        } else {
            Ok(None)
        }
    }

    pub fn insert_note(&self, note: &Note) -> Result<i64> {
        let conn = self.get_conn()?;
        let attachments_str = note.attachments.join(";;;");
        conn.execute(
            "INSERT INTO notes (
                title, content, timestamp, color, categoryId,
                isPinned, isArchived, isDeleted, deletedAt, isLocked,
                reminderTime, attachments, createdAt, updatedAt, backgroundImage
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
            params![
                note.title,
                note.content,
                note.timestamp,
                note.color,
                note.category_id,
                if note.is_pinned { 1 } else { 0 },
                if note.is_archived { 1 } else { 0 },
                if note.is_deleted { 1 } else { 0 },
                note.deleted_at,
                if note.is_locked { 1 } else { 0 },
                note.reminder_time,
                attachments_str,
                note.created_at,
                note.updated_at,
                note.background_image
            ],
        )?;
        Ok(conn.last_insert_rowid())
    }

    pub fn update_note(&self, note: &Note) -> Result<()> {
        let conn = self.get_conn()?;
        let attachments_str = note.attachments.join(";;;");
        conn.execute(
            "UPDATE notes SET
                title = ?1, content = ?2, timestamp = ?3, color = ?4, categoryId = ?5,
                isPinned = ?6, isArchived = ?7, isDeleted = ?8, deletedAt = ?9, isLocked = ?10,
                reminderTime = ?11, attachments = ?12, createdAt = ?13, updatedAt = ?14, backgroundImage = ?15
             WHERE id = ?16",
            params![
                note.title,
                note.content,
                note.timestamp,
                note.color,
                note.category_id,
                if note.is_pinned { 1 } else { 0 },
                if note.is_archived { 1 } else { 0 },
                if note.is_deleted { 1 } else { 0 },
                note.deleted_at,
                if note.is_locked { 1 } else { 0 },
                note.reminder_time,
                attachments_str,
                note.created_at,
                note.updated_at,
                note.background_image,
                note.id
            ],
        )?;
        Ok(())
    }

    pub fn move_to_trash(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_millis() as i64;
        conn.execute(
            "UPDATE notes SET isDeleted = 1, deletedAt = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    pub fn restore_from_trash(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute(
            "UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id = ?1",
            params![id],
        )?;
        Ok(())
    }

    pub fn delete_permanently(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute("DELETE FROM notes WHERE id = ?1", params![id])?;
        Ok(())
    }

    pub fn empty_trash(&self) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute("DELETE FROM notes WHERE isDeleted = 1", [])?;
        Ok(())
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

    pub fn delete_category(&self, id: i64) -> Result<()> {
        let conn = self.get_conn()?;
        conn.execute("DELETE FROM categories WHERE id = ?1", params![id])?;
        Ok(())
    }

    pub fn get_all_notes(&self) -> Result<Vec<Note>> {
        let conn = self.get_conn()?;
        let mut stmt = conn.prepare(
            "SELECT id, title, content, timestamp, color, categoryId, isPinned, isArchived,
                    isDeleted, deletedAt, isLocked, reminderTime, attachments, createdAt, updatedAt, backgroundImage
             FROM notes ORDER BY id ASC",
        )?;

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
            let att_str = note.attachments.join(";;;");
            tx.execute(
                "INSERT INTO notes (
                    id, title, content, timestamp, color, categoryId,
                    isPinned, isArchived, isDeleted, deletedAt, isLocked,
                    reminderTime, attachments, createdAt, updatedAt, backgroundImage
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
                params![
                    note.id,
                    note.title,
                    note.content,
                    note.timestamp,
                    note.color,
                    note.category_id,
                    if note.is_pinned { 1 } else { 0 },
                    if note.is_archived { 1 } else { 0 },
                    if note.is_deleted { 1 } else { 0 },
                    note.deleted_at,
                    if note.is_locked { 1 } else { 0 },
                    note.reminder_time,
                    att_str,
                    note.created_at,
                    note.updated_at,
                    note.background_image
                ],
            )?;
        }

        tx.commit()?;
        Ok(())
    }

    fn row_to_note(row: &rusqlite::Row) -> rusqlite::Result<Note> {
        let att_raw: String = row.get(12)?;
        let attachments = if att_raw.trim().is_empty() {
            Vec::new()
        } else {
            att_raw.split(";;;").map(|s| s.to_string()).collect()
        };

        let is_pin: i32 = row.get(6)?;
        let is_arch: i32 = row.get(7)?;
        let is_del: i32 = row.get(8)?;
        let is_lock: i32 = row.get(10)?;

        Ok(Note {
            id: row.get(0)?,
            title: row.get(1)?,
            content: row.get(2)?,
            timestamp: row.get(3)?,
            color: row.get(4)?,
            category_id: row.get(5)?,
            is_pinned: is_pin == 1,
            is_archived: is_arch == 1,
            is_deleted: is_del == 1,
            deleted_at: row.get(9)?,
            is_locked: is_lock == 1,
            reminder_time: row.get(11)?,
            attachments,
            created_at: row.get(13)?,
            updated_at: row.get(14)?,
            background_image: row.get(15)?,
        })
    }
}
