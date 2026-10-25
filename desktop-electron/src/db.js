const initSqlJs = require('sql.js');
const fs = require('fs');
const path = require('path');

class DatabaseManager {
  constructor(appDir) {
    this.appDir = appDir;
    this.dbFile = path.join(appDir, 'notes_electron.db');
    this.attachmentsDir = path.join(appDir, 'attachments');
    this.db = null;
    this.SQL = null;

    if (!fs.existsSync(this.appDir)) {
      fs.mkdirSync(this.appDir, { recursive: true });
    }
    if (!fs.existsSync(this.attachmentsDir)) {
      fs.mkdirSync(this.attachmentsDir, { recursive: true });
    }
  }

  async init() {
    this.SQL = await initSqlJs();

    // Check if electron db exists or desktop db exists to inherit
    if (fs.existsSync(this.dbFile)) {
      const fileBuffer = fs.readFileSync(this.dbFile);
      this.db = new this.SQL.Database(fileBuffer);
    } else {
      const desktopDbPath = path.join(this.appDir, 'notes_desktop.db');
      if (fs.existsSync(desktopDbPath)) {
        try {
          const fileBuffer = fs.readFileSync(desktopDbPath);
          this.db = new this.SQL.Database(fileBuffer);
          this.save();
        } catch {
          this.db = new this.SQL.Database();
        }
      } else {
        this.db = new this.SQL.Database();
      }
    }

    this.createTables();
    this.cleanUpOldTrashNotes(Date.now() - 7 * 24 * 60 * 60 * 1000);
    this.save();
  }

  save() {
    if (!this.db) return;
    try {
      const data = this.db.export();
      const buffer = Buffer.from(data);
      fs.writeFileSync(this.dbFile, buffer);
    } catch (err) {
      console.error('Error saving SQLite database:', err);
    }
  }

  createTables() {
    this.db.run(`
      CREATE TABLE IF NOT EXISTS categories (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        color INTEGER NOT NULL DEFAULT 0
      );
    `);

    this.db.run(`
      CREATE TABLE IF NOT EXISTS notes (
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
        attachments TEXT NOT NULL DEFAULT '[]',
        createdAt INTEGER NOT NULL DEFAULT 0,
        updatedAt INTEGER NOT NULL DEFAULT 0,
        backgroundImage TEXT,
        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE SET NULL
      );
    `);
  }

  rowToNote(row) {
    if (!row) return null;
    let attachments = [];
    try {
      if (row.attachments && row.attachments.startsWith('[')) {
        attachments = JSON.parse(row.attachments);
      } else if (row.attachments && row.attachments.trim().length > 0) {
        attachments = row.attachments.split(',').map(s => s.trim()).filter(Boolean);
      }
    } catch {
      attachments = [];
    }

    return {
      id: Number(row.id),
      title: row.title || '',
      content: row.content || '',
      timestamp: Number(row.timestamp || 0),
      color: Number(row.color || 0),
      categoryId: row.categoryId !== null && row.categoryId !== undefined ? Number(row.categoryId) : null,
      isPinned: Boolean(row.isPinned),
      isArchived: Boolean(row.isArchived),
      isDeleted: Boolean(row.isDeleted),
      deletedAt: row.deletedAt ? Number(row.deletedAt) : null,
      isLocked: Boolean(row.isLocked),
      reminderTime: row.reminderTime ? Number(row.reminderTime) : null,
      attachments: attachments,
      createdAt: Number(row.createdAt || row.timestamp || 0),
      updatedAt: Number(row.updatedAt || row.timestamp || 0),
      backgroundImage: row.backgroundImage || null
    };
  }

  queryRows(sql, params = []) {
    const stmt = this.db.prepare(sql);
    if (params && params.length > 0) {
      stmt.bind(params);
    }
    const results = [];
    while (stmt.step()) {
      results.push(stmt.getAsObject());
    }
    stmt.free();
    return results;
  }

  getActiveNotes() {
    const rows = this.queryRows(`
      SELECT * FROM notes 
      WHERE isDeleted = 0 AND isArchived = 0 
      ORDER BY isPinned DESC, updatedAt DESC
    `);
    return rows.map(r => this.rowToNote(r));
  }

  getArchivedNotes() {
    const rows = this.queryRows(`
      SELECT * FROM notes 
      WHERE isDeleted = 0 AND isArchived = 1 
      ORDER BY updatedAt DESC
    `);
    return rows.map(r => this.rowToNote(r));
  }

  getTrashNotes() {
    const rows = this.queryRows(`
      SELECT * FROM notes 
      WHERE isDeleted = 1 
      ORDER BY deletedAt DESC
    `);
    return rows.map(r => this.rowToNote(r));
  }

  getReminderNotes() {
    const rows = this.queryRows(`
      SELECT * FROM notes 
      WHERE isDeleted = 0 AND reminderTime IS NOT NULL AND reminderTime > 0 
      ORDER BY reminderTime ASC
    `);
    return rows.map(r => this.rowToNote(r));
  }

  getAllNotes() {
    const rows = this.queryRows('SELECT * FROM notes ORDER BY id ASC');
    return rows.map(r => this.rowToNote(r));
  }

  getNoteById(id) {
    const rows = this.queryRows('SELECT * FROM notes WHERE id = ? LIMIT 1', [id]);
    return rows.length > 0 ? this.rowToNote(rows[0]) : null;
  }

  getNoteByTitle(title) {
    if (!title || !title.trim()) return null;
    const rows = this.queryRows('SELECT * FROM notes WHERE title = ? AND isDeleted = 0 LIMIT 1', [title.trim()]);
    return rows.length > 0 ? this.rowToNote(rows[0]) : null;
  }

  getNotesLinkingTo(noteTitle, excludeId = 0) {
    if (!noteTitle || !noteTitle.trim()) return [];
    const pattern = `%[[${noteTitle.trim()}]]%`;
    const rows = this.queryRows(`
      SELECT * FROM notes 
      WHERE content LIKE ? AND id != ? AND isDeleted = 0
      ORDER BY updatedAt DESC
    `, [pattern, excludeId]);
    return rows.map(r => this.rowToNote(r));
  }

  insertNote(note) {
    const now = Date.now();
    const ts = note.timestamp || now;
    const cat = note.categoryId !== undefined ? note.categoryId : null;
    const attStr = JSON.stringify(note.attachments || []);

    this.db.run(`
      INSERT INTO notes (
        title, content, timestamp, color, categoryId,
        isPinned, isArchived, isDeleted, deletedAt, isLocked,
        reminderTime, attachments, createdAt, updatedAt, backgroundImage
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      note.title || '',
      note.content || '',
      ts,
      note.color || 0,
      cat,
      note.isPinned ? 1 : 0,
      note.isArchived ? 1 : 0,
      note.isDeleted ? 1 : 0,
      note.deletedAt || null,
      note.isLocked ? 1 : 0,
      note.reminderTime || null,
      attStr,
      note.createdAt || ts,
      note.updatedAt || ts,
      note.backgroundImage || null
    ]);

    const res = this.db.exec('SELECT last_insert_rowid() as id');
    const newId = res[0].values[0][0];
    this.save();
    return newId;
  }

  updateNote(note) {
    const now = Date.now();
    const cat = note.categoryId !== undefined ? note.categoryId : null;
    const attStr = JSON.stringify(note.attachments || []);

    this.db.run(`
      UPDATE notes SET
        title = ?,
        content = ?,
        color = ?,
        categoryId = ?,
        isPinned = ?,
        isArchived = ?,
        isDeleted = ?,
        deletedAt = ?,
        isLocked = ?,
        reminderTime = ?,
        attachments = ?,
        updatedAt = ?,
        backgroundImage = ?
      WHERE id = ?
    `, [
      note.title || '',
      note.content || '',
      note.color || 0,
      cat,
      note.isPinned ? 1 : 0,
      note.isArchived ? 1 : 0,
      note.isDeleted ? 1 : 0,
      note.deletedAt || null,
      note.isLocked ? 1 : 0,
      note.reminderTime || null,
      attStr,
      note.updatedAt || now,
      note.backgroundImage || null,
      note.id
    ]);

    this.save();
  }

  moveToTrash(id) {
    const now = Date.now();
    this.db.run('UPDATE notes SET isDeleted = 1, deletedAt = ?, isPinned = 0 WHERE id = ?', [now, id]);
    this.save();
  }

  moveNotesToTrash(ids) {
    if (!ids || ids.length === 0) return;
    const now = Date.now();
    const placeholders = ids.map(() => '?').join(',');
    this.db.run(`UPDATE notes SET isDeleted = 1, deletedAt = ?, isPinned = 0 WHERE id IN (${placeholders})`, [now, ...ids]);
    this.save();
  }

  restoreFromTrash(id) {
    this.db.run('UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id = ?', [id]);
    this.save();
  }

  deletePermanently(id) {
    this.db.run('DELETE FROM notes WHERE id = ?', [id]);
    this.save();
  }

  deleteNotesPermanently(ids) {
    if (!ids || ids.length === 0) return;
    const placeholders = ids.map(() => '?').join(',');
    this.db.run(`DELETE FROM notes WHERE id IN (${placeholders})`, ids);
    this.save();
  }

  emptyTrash() {
    this.db.run('DELETE FROM notes WHERE isDeleted = 1');
    this.save();
  }

  cleanUpOldTrashNotes(thresholdTimestamp) {
    this.db.run('DELETE FROM notes WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt < ?', [thresholdTimestamp]);
    this.save();
  }

  updateNotesCategory(ids, categoryId) {
    if (!ids || ids.length === 0) return;
    const placeholders = ids.map(() => '?').join(',');
    this.db.run(`UPDATE notes SET categoryId = ?, updatedAt = ? WHERE id IN (${placeholders})`, [
      categoryId !== undefined ? categoryId : null,
      Date.now(),
      ...ids
    ]);
    this.save();
  }

  // Categories
  getAllCategories() {
    const rows = this.queryRows('SELECT * FROM categories ORDER BY name ASC');
    return rows.map(r => ({
      id: Number(r.id),
      name: r.name,
      color: Number(r.color || 0)
    }));
  }

  insertCategory(category) {
    this.db.run('INSERT INTO categories (name, color) VALUES (?, ?)', [
      category.name,
      category.color || 0
    ]);
    const res = this.db.exec('SELECT last_insert_rowid() as id');
    const newId = res[0].values[0][0];
    this.save();
    return newId;
  }

  deleteCategory(id) {
    this.db.run('UPDATE notes SET categoryId = NULL WHERE categoryId = ?', [id]);
    this.db.run('DELETE FROM categories WHERE id = ?', [id]);
    this.save();
  }
}

module.exports = DatabaseManager;
