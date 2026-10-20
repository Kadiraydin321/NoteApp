package com.example.noteapp.domain.model

data class NoteTemplate(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val contentTemplate: String,
    val defaultTitle: String,
    val categoryName: String? = null,
    val isBuiltIn: Boolean = true
)
