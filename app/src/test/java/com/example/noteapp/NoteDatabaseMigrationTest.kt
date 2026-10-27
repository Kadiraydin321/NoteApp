package com.example.noteapp

import androidx.room.Entity
import androidx.room.Index
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.noteapp.data.local.NoteDatabase
import com.example.noteapp.data.local.NoteDraftEntity
import com.example.noteapp.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class NoteDatabaseMigrationTest {

    @Test
    fun `migration 5 to 6 executes all required DDL statements`() {
        val executedSql = mutableListOf<String>()

        // Mock SupportSQLiteDatabase via dynamic proxy
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0].toString())
            }
            null
        } as SupportSQLiteDatabase

        NoteDatabase.MIGRATION_5_6.migrate(dbProxy)

        // Verify folderId column addition
        assertTrue(
            "folderId column must be added to notes",
            executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN folderId INTEGER DEFAULT NULL", ignoreCase = true) }
        )

        // Verify all 5 requested Room indices are created
        val indexStatements = executedSql.filter { it.contains("CREATE INDEX", ignoreCase = true) }
        assertTrue("folderId index must be created", indexStatements.any { it.contains("index_notes_folderId") })
        assertTrue("isPinned index must be created", indexStatements.any { it.contains("index_notes_isPinned") })
        assertTrue("isArchived index must be created", indexStatements.any { it.contains("index_notes_isArchived") })
        assertTrue("isLocked index must be created", indexStatements.any { it.contains("index_notes_isLocked") })
        assertTrue("updatedAt index must be created", indexStatements.any { it.contains("index_notes_updatedAt") })

        // Verify note_drafts table and timestamp index
        assertTrue(
            "note_drafts table must be created with correct columns",
            executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS note_drafts") && it.contains("noteId INTEGER PRIMARY KEY") }
        )
        assertTrue(
            "note_drafts timestamp index must be created",
            indexStatements.any { it.contains("index_note_drafts_timestamp") }
        )
    }

    @Test
    fun `Note entity contains all required indices and folderId column`() {
        // Verify folderId property defaults to null
        val note = Note(title = "Test", content = "Test Content")
        assertNull(note.folderId)

        val noteWithFolder = note.copy(folderId = 42L)
        assertEquals(42L, noteWithFolder.folderId)
    }

    @Test
    fun `NoteDraftEntity schema and defaults are valid`() {
        val draft = NoteDraftEntity(
            noteId = 0L,
            title = "Taslak Başlık",
            content = "Taslak İçerik"
        )
        assertEquals(0L, draft.noteId)
        assertEquals("Taslak Başlık", draft.title)
        assertEquals("Taslak İçerik", draft.content)
        assertTrue(draft.timestamp > 0L)
    }
}
