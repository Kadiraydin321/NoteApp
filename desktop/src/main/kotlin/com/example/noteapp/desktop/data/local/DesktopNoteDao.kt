package com.example.noteapp.desktop.data.local

import com.example.noteapp.desktop.domain.model.Category
import com.example.noteapp.desktop.domain.model.Note
import java.sql.ResultSet

class DesktopNoteDao {

    private fun mapNote(rs: ResultSet): Note {
        val rawAttachments = rs.getString("attachments") ?: ""
        val attachments = if (rawAttachments.isBlank()) {
            emptyList()
        } else {
            rawAttachments.split(";;;").filter { it.isNotBlank() }
        }

        val catIdObj = rs.getObject("categoryId")
        val catId = if (catIdObj != null) rs.getLong("categoryId") else null

        val delAtObj = rs.getObject("deletedAt")
        val delAt = if (delAtObj != null) rs.getLong("deletedAt") else null

        val remTimeObj = rs.getObject("reminderTime")
        val remTime = if (remTimeObj != null) rs.getLong("reminderTime") else null

        return Note(
            id = rs.getLong("id"),
            title = rs.getString("title"),
            content = rs.getString("content"),
            timestamp = rs.getLong("timestamp"),
            color = rs.getInt("color"),
            categoryId = catId,
            isPinned = rs.getInt("isPinned") == 1,
            isArchived = rs.getInt("isArchived") == 1,
            isDeleted = rs.getInt("isDeleted") == 1,
            deletedAt = delAt,
            isLocked = rs.getInt("isLocked") == 1,
            reminderTime = remTime,
            attachments = attachments,
            createdAt = rs.getLong("createdAt"),
            updatedAt = rs.getLong("updatedAt"),
            backgroundImage = rs.getString("backgroundImage")
        )
    }

    private fun mapCategory(rs: ResultSet): Category {
        return Category(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            color = rs.getInt("color")
        )
    }

    fun getActiveNotes(): List<Note> {
        val list = mutableListOf<Note>()
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC, timestamp DESC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(mapNote(rs))
                }
            }
        }
        return list
    }

    fun getArchivedNotes(): List<Note> {
        val list = mutableListOf<Note>()
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0 ORDER BY updatedAt DESC, timestamp DESC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(mapNote(rs))
                }
            }
        }
        return list
    }

    fun getTrashNotes(): List<Note> {
        val list = mutableListOf<Note>()
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC, timestamp DESC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(mapNote(rs))
                }
            }
        }
        return list
    }

    fun getAllNotes(): List<Note> {
        val list = mutableListOf<Note>()
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes ORDER BY id ASC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(mapNote(rs))
                }
            }
        }
        return list
    }

    fun getNoteById(id: Long): Note? {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes WHERE id = ? LIMIT 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return mapNote(rs)
                }
            }
        }
        return null
    }

    fun insertNote(note: Note): Long {
        DatabaseHelper.getConnection().use { conn ->
            val sql = """
                INSERT INTO notes (
                    title, content, timestamp, color, categoryId, isPinned, isArchived,
                    isDeleted, deletedAt, isLocked, reminderTime, attachments,
                    createdAt, updatedAt, backgroundImage
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setString(1, note.title)
                stmt.setString(2, note.content)
                stmt.setLong(3, note.timestamp)
                stmt.setInt(4, note.color)
                if (note.categoryId != null) stmt.setLong(5, note.categoryId) else stmt.setNull(5, java.sql.Types.INTEGER)
                stmt.setInt(6, if (note.isPinned) 1 else 0)
                stmt.setInt(7, if (note.isArchived) 1 else 0)
                stmt.setInt(8, if (note.isDeleted) 1 else 0)
                if (note.deletedAt != null) stmt.setLong(9, note.deletedAt) else stmt.setNull(9, java.sql.Types.INTEGER)
                stmt.setInt(10, if (note.isLocked) 1 else 0)
                if (note.reminderTime != null) stmt.setLong(11, note.reminderTime) else stmt.setNull(11, java.sql.Types.INTEGER)
                stmt.setString(12, note.attachments.joinToString(";;;"))
                stmt.setLong(13, if (note.createdAt != 0L) note.createdAt else note.timestamp)
                stmt.setLong(14, if (note.updatedAt != 0L) note.updatedAt else note.timestamp)
                stmt.setString(15, note.backgroundImage)

                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                if (keys.next()) {
                    return keys.getLong(1)
                }
            }
        }
        return 0L
    }

    fun updateNote(note: Note) {
        DatabaseHelper.getConnection().use { conn ->
            val sql = """
                UPDATE notes SET
                    title = ?, content = ?, timestamp = ?, color = ?, categoryId = ?,
                    isPinned = ?, isArchived = ?, isDeleted = ?, deletedAt = ?,
                    isLocked = ?, reminderTime = ?, attachments = ?,
                    createdAt = ?, updatedAt = ?, backgroundImage = ?
                WHERE id = ?
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, note.title)
                stmt.setString(2, note.content)
                stmt.setLong(3, note.timestamp)
                stmt.setInt(4, note.color)
                if (note.categoryId != null) stmt.setLong(5, note.categoryId) else stmt.setNull(5, java.sql.Types.INTEGER)
                stmt.setInt(6, if (note.isPinned) 1 else 0)
                stmt.setInt(7, if (note.isArchived) 1 else 0)
                stmt.setInt(8, if (note.isDeleted) 1 else 0)
                if (note.deletedAt != null) stmt.setLong(9, note.deletedAt) else stmt.setNull(9, java.sql.Types.INTEGER)
                stmt.setInt(10, if (note.isLocked) 1 else 0)
                if (note.reminderTime != null) stmt.setLong(11, note.reminderTime) else stmt.setNull(11, java.sql.Types.INTEGER)
                stmt.setString(12, note.attachments.joinToString(";;;"))
                stmt.setLong(13, note.createdAt)
                stmt.setLong(14, note.updatedAt)
                stmt.setString(15, note.backgroundImage)
                stmt.setLong(16, note.id)

                stmt.executeUpdate()
            }
        }
    }

    fun moveToTrash(id: Long) {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "UPDATE notes SET isDeleted = 1, deletedAt = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, System.currentTimeMillis())
                stmt.setLong(2, id)
                stmt.executeUpdate()
            }
        }
    }

    fun restoreFromTrash(id: Long) {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun deletePermanently(id: Long) {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "DELETE FROM notes WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun emptyTrash() {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "DELETE FROM notes WHERE isDeleted = 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeUpdate()
            }
        }
    }

    fun deleteAllNotes() {
        DatabaseHelper.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM notes")
            }
        }
    }

    // Categories
    fun getAllCategories(): List<Category> {
        val list = mutableListOf<Category>()
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM categories ORDER BY name ASC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(mapCategory(rs))
                }
            }
        }
        return list
    }

    fun insertCategory(category: Category): Long {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "INSERT INTO categories (name, color) VALUES (?, ?)"
            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setString(1, category.name)
                stmt.setInt(2, category.color)
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                if (keys.next()) {
                    return keys.getLong(1)
                }
            }
        }
        return 0L
    }

    fun updateCategory(category: Category) {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "UPDATE categories SET name = ?, color = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, category.name)
                stmt.setInt(2, category.color)
                stmt.setLong(3, category.id)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        DatabaseHelper.getConnection().use { conn ->
            // Kategori silindiğinde o kategoriye bağlı notların categoryId alanını NULL yap
            conn.prepareStatement("UPDATE notes SET categoryId = NULL WHERE categoryId = ?").use { stmt ->
                stmt.setLong(1, categoryId)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM categories WHERE id = ?").use { stmt ->
                stmt.setLong(1, categoryId)
                stmt.executeUpdate()
            }
        }
    }

    fun cleanUpOldTrashNotes(threshold: Long) {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "DELETE FROM notes WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt < ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, threshold)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteNotesPermanently(ids: List<Long>) {
        if (ids.isEmpty()) return
        DatabaseHelper.getConnection().use { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            val sql = "DELETE FROM notes WHERE id IN ($placeholders)"
            conn.prepareStatement(sql).use { stmt ->
                ids.forEachIndexed { index, id ->
                    stmt.setLong(index + 1, id)
                }
                stmt.executeUpdate()
            }
        }
    }

    fun moveNotesToTrash(ids: List<Long>, deletedAt: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        DatabaseHelper.getConnection().use { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            val sql = "UPDATE notes SET isDeleted = 1, deletedAt = ? WHERE id IN ($placeholders)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, deletedAt)
                ids.forEachIndexed { index, id ->
                    stmt.setLong(index + 2, id)
                }
                stmt.executeUpdate()
            }
        }
    }

    fun updateNotesCategory(ids: List<Long>, categoryId: Long?) {
        if (ids.isEmpty()) return
        DatabaseHelper.getConnection().use { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            val sql = "UPDATE notes SET categoryId = ? WHERE id IN ($placeholders)"
            conn.prepareStatement(sql).use { stmt ->
                if (categoryId != null) stmt.setLong(1, categoryId) else stmt.setNull(1, java.sql.Types.INTEGER)
                ids.forEachIndexed { index, id ->
                    stmt.setLong(index + 2, id)
                }
                stmt.executeUpdate()
            }
        }
    }

    fun getNotesLinkingTo(noteTitle: String, excludeId: Long): List<Note> {
        val list = mutableListOf<Note>()
        if (noteTitle.isBlank()) return list
        val linkToken = "[[$noteTitle]]"
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 AND (content LIKE ? OR content LIKE ?) AND id != ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, "%$linkToken%")
                stmt.setString(2, "%$noteTitle%")
                stmt.setLong(3, excludeId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(mapNote(rs))
                }
            }
        }
        return list
    }

    fun getNoteByTitle(title: String): Note? {
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes WHERE isDeleted = 0 AND title = ? LIMIT 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, title)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return mapNote(rs)
                }
            }
        }
        return null
    }

    fun getReminderNotes(): List<Note> {
        val list = mutableListOf<Note>()
        DatabaseHelper.getConnection().use { conn ->
            val sql = "SELECT * FROM notes WHERE isDeleted = 0 AND reminderTime IS NOT NULL AND reminderTime > 0 ORDER BY reminderTime ASC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(mapNote(rs))
                }
            }
        }
        return list
    }

    fun deleteAllCategories() {
        DatabaseHelper.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM categories")
            }
        }
    }
}
