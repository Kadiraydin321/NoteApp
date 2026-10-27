package com.example.noteapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Veritabanı katmanında kalıcı taslak saklama tablosu (Crash/çökme ve ani kapanma kurtarması için).
 * noteId = 0L henüz oluşturulmamış / yeni notun taslağını temsil eder.
 */
@Entity(
    tableName = "note_drafts",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class NoteDraftEntity(
    @PrimaryKey
    val noteId: Long = 0L,
    val title: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
