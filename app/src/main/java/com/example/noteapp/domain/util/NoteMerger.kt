package com.example.noteapp.domain.util

import com.example.noteapp.domain.model.Note

object NoteMerger {

    /**
     * Birden fazla notu kronolojik sırayla birleştirip tek bir zengin Markdown notu oluşturur.
     * Her notun başlığı H2 başlığına dönüştürülür, içerikler arasına Markdown ayraçları ('---')
     * eklenir ve tüm medya ekleri tekrarsız olarak birleştirilir.
     */
    fun mergeNotes(notes: List<Note>, customTitle: String? = null): Note {
        require(notes.isNotEmpty()) { "En az bir not gereklidir." }
        if (notes.size == 1) return notes.first()

        val sortedNotes = notes.sortedBy { it.timestamp }
        val title = customTitle?.trim()?.ifBlank { null }
            ?: (sortedNotes.firstOrNull { it.title.isNotBlank() }?.title?.let { "$it (Birleştirildi)" }
                ?: "Birleştirilmiş Not (${notes.size})")

        val contentBuilder = StringBuilder()
        sortedNotes.forEachIndexed { index, note ->
            if (index > 0) {
                contentBuilder.append("\n\n---\n\n")
            }
            val noteTitle = note.title.trim()
            if (noteTitle.isNotBlank()) {
                contentBuilder.append("## ").append(noteTitle).append("\n\n")
            }
            contentBuilder.append(note.content.trim())
        }

        val mergedAttachments = sortedNotes.flatMap { it.attachments }.distinct()
        val commonCategoryId = sortedNotes.map { it.categoryId }.distinct().singleOrNull()
        val firstColor = sortedNotes.firstOrNull { it.color != 0 }?.color ?: 0
        val anyPinned = sortedNotes.any { it.isPinned }
        val anyLocked = sortedNotes.any { it.isLocked }

        val now = System.currentTimeMillis()
        return Note(
            id = 0,
            title = title,
            content = contentBuilder.toString().trim(),
            timestamp = now,
            createdAt = now,
            updatedAt = now,
            color = firstColor,
            categoryId = commonCategoryId,
            isPinned = anyPinned,
            isLocked = anyLocked,
            attachments = mergedAttachments
        )
    }
}
