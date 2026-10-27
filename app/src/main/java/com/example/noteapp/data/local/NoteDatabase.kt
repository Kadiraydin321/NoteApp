package com.example.noteapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.model.NoteHistory

@Database(
    entities = [Note::class, Category::class, NoteHistory::class, NoteDraftEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract val noteDao: NoteDao

    companion object {
        const val DATABASE_NAME = "notes_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN backgroundImage TEXT DEFAULT NULL")
                db.execSQL("UPDATE notes SET createdAt = timestamp WHERE createdAt = 0")
                db.execSQL("UPDATE notes SET updatedAt = timestamp WHERE updatedAt = 0")
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isDeleted_isArchived_isPinned_timestamp ON notes (isDeleted, isArchived, isPinned, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_categoryId ON notes (categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_reminderTime ON notes (reminderTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_title ON notes (title)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        wordCount INTEGER NOT NULL DEFAULT 0,
                        changeSummary TEXT DEFAULT NULL,
                        FOREIGN KEY (noteId) REFERENCES notes (id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_history_noteId_timestamp ON note_history (noteId, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_history_timestamp ON note_history (timestamp)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. notes tablosuna folderId sütunu ekle
                db.execSQL("ALTER TABLE notes ADD COLUMN folderId INTEGER DEFAULT NULL")

                // 2. Sık sorgulanan sütunlar için indeksleri tanımla
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId ON notes (folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isPinned ON notes (isPinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isArchived ON notes (isArchived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isLocked ON notes (isLocked)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_updatedAt ON notes (updatedAt)")

                // 3. Taslak notlar ve çökme/kapanma kurtarma tablosunu oluştur
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_drafts (
                        noteId INTEGER PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_drafts_timestamp ON note_drafts (timestamp)")
            }
        }

        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getInstance(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .build().also { INSTANCE = it }
            }
        }
    }
}
