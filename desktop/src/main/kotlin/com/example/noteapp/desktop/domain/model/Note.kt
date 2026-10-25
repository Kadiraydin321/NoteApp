package com.example.noteapp.desktop.domain.model

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val color: Int = 0,
    val categoryId: Long? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val isLocked: Boolean = false,
    val reminderTime: Long? = null,
    val attachments: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val backgroundImage: String? = null
)

data class Category(
    val id: Long = 0,
    val name: String,
    val color: Int = 0
)
