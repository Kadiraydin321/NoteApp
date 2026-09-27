package com.example.noteapp.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
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
    val isLocked: Boolean = false,  // Biyometrik / Şifre kilidi
    val reminderTime: Long? = null, // Hatırlatıcı tarihi
    val attachments: List<String> = emptyList() // Görsel / Dosya yolları
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int = 0
)
