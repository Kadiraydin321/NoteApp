package com.example.noteapp.domain.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

@Immutable
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["isDeleted", "isArchived", "isPinned", "timestamp"]),
        Index(value = ["categoryId"]),
        Index(value = ["reminderTime"]),
        Index(value = ["title"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val color: Int = 0,
    val categoryId: Long? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false, // Çöp kutusu
    val deletedAt: Long? = null, // Çöp kutusuna atılma tarihi
    val isLocked: Boolean = false,  // Biyometrik / Şifre kilidi
    val reminderTime: Long? = null, // Hatırlatıcı tarihi
    val attachments: List<String> = emptyList(), // Görsel / Dosya yolları
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val backgroundImage: String? = null // Arka plan görseli
)

@Immutable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int = 0
) {
    companion object {
        val DEFAULT_COLORS: List<Int> = listOf(
            0,                     // 0: Nötr / Varsayılan
            0xFFE53935.toInt(),   // Mercan Kırmızısı
            0xFFFB8C00.toInt(),   // Sıcak Turuncu
            0xFFFDD835.toInt(),   // Kehribar Sarısı
            0xFF43A047.toInt(),   // Zümrüt Yeşili
            0xFF00ACC1.toInt(),   // Camgöbeği Turkuaz
            0xFF1E88E5.toInt(),   // Okyanus Mavisi
            0xFF8E24AA.toInt(),   // Ametist Moru
            0xFFD81B60.toInt(),   // Gül Pembesi
            0xFF6D4C41.toInt()    // Arduvaz Kahve
        )
    }
}
