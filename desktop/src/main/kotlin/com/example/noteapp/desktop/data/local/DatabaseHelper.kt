package com.example.noteapp.desktop.data.local

import com.example.noteapp.desktop.domain.model.Category
import com.example.noteapp.desktop.domain.model.Note
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

object DatabaseHelper {
    val appDir: File by lazy {
        val appData = System.getenv("APPDATA")
        val baseDir = if (!appData.isNullOrBlank()) {
            File(appData, "NotDefterim")
        } else {
            File(System.getProperty("user.home"), ".notdefterim")
        }
        baseDir.apply { mkdirs() }
    }
    val dbFile: File by lazy { File(appDir, "notes_desktop.db") }
    val attachmentsDir: File by lazy { File(appDir, "attachments").apply { mkdirs() } }

    private val url get() = "jdbc:sqlite:${dbFile.absolutePath}"

    init {
        initDatabase()
    }

    fun getConnection(): Connection {
        return DriverManager.getConnection(url)
    }

    private fun initDatabase() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                // Categories tablosu
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        color INTEGER NOT NULL DEFAULT 0
                    );
                    """.trimIndent()
                )

                // Notes tablosu (Android Room şeması ile %100 uyumlu)
                stmt.execute(
                    """
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
                        attachments TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        backgroundImage TEXT,
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE SET NULL
                    );
                    """.trimIndent()
                )
            }
        }
    }
}
