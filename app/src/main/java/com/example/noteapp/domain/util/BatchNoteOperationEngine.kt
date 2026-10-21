package com.example.noteapp.domain.util

import com.example.noteapp.domain.model.Note

/**
 * Toplu not istatistikleri modeli.
 */
data class BatchStatistics(
    val noteCount: Int,
    val totalWordCount: Int,
    val totalCharCount: Int,
    val totalAttachments: Int,
    val uniqueCategoryCount: Int,
    val hasPinnedNotes: Boolean,
    val hasLockedNotes: Boolean,
    val hasChecklists: Boolean
)

/**
 * Çoklu Not Seçimi ve Toplu İşlemler Motoru.
 * Seçili notlar üzerinde toplu sabitleme, etiket ekleme/çıkarma, not birleştirme,
 * istatistik hesaplama ve özet çıkarma mantığını yönetir.
 */
object BatchNoteOperationEngine {

    /**
     * Seçili notların toplu sabitleme durumunu hesaplar.
     * En az bir not sabitlenmemişse true (hepsini sabitle), tümü zaten sabitlenmişse false (hepsinin sabitlemesini kaldır).
     */
    fun calculateTargetPinState(notes: List<Note>): Boolean {
        if (notes.isEmpty()) return false
        return notes.any { !it.isPinned }
    }

    /**
     * Not içeriğine toplu etiket ekler. Etiket '#' ile başlamıyorsa otomatik eklenir.
     * Eğer etiket zaten içerikte tam kelime olarak varsa içeriği değiştirmez.
     */
    fun applyBulkTag(content: String, rawTag: String): String {
        val cleanTag = rawTag.trim().removePrefix("#").trim()
        if (cleanTag.isBlank()) return content

        val tagFormatted = "#$cleanTag"
        val tagRegex = Regex("(?<=^|\\s)#${Regex.escape(cleanTag)}(?=\\s|$|[.,!?;:])", RegexOption.IGNORE_CASE)
        if (tagRegex.containsMatchIn(content)) {
            return content // Zaten mevcut
        }

        return if (content.isBlank()) {
            tagFormatted
        } else if (content.endsWith("\n")) {
            "$content$tagFormatted"
        } else {
            "$content $tagFormatted"
        }
    }

    /**
     * Not içeriğinden belirtilen etiketi temizler.
     */
    fun removeBulkTag(content: String, rawTag: String): String {
        val cleanTag = rawTag.trim().removePrefix("#").trim()
        if (cleanTag.isBlank() || content.isBlank()) return content

        val tagRegex = Regex("(?<=^|\\s)#${Regex.escape(cleanTag)}(?=\\s|$|[.,!?;:])", RegexOption.IGNORE_CASE)
        val updated = tagRegex.replace(content, "").trim()
        // Birden fazla ardışık boşluğu temizle
        return updated.replace(Regex("[ \\t]+"), " ")
    }

    /**
     * Seçili notları tek bir not altında birleştirir.
     * Başlıklar birleştirilir, içerikler Markdown '---' ayırıcı ile bağlanır, ekler birleştirilir.
     */
    fun mergeNotes(
        notes: List<Note>,
        titlePrefix: String = "Birleştirilmiş Not",
        includeHeadings: Boolean = true
    ): Note {
        require(notes.isNotEmpty()) { "Birleştirilecek not listesi boş olamaz." }

        if (notes.size == 1) return notes.first()

        val firstNote = notes.first()
        val mergedTitle = if (firstNote.title.isNotBlank()) {
            "${firstNote.title} (+${notes.size - 1} Not)"
        } else {
            "$titlePrefix (${notes.size} Adet)"
        }

        val contentBuilder = StringBuilder()
        notes.forEachIndexed { index, note ->
            if (index > 0) {
                contentBuilder.append("\n\n---\n\n")
            }
            if (includeHeadings && note.title.isNotBlank()) {
                contentBuilder.append("### ${note.title}\n\n")
            }
            contentBuilder.append(note.content.trim())
        }

        val mergedAttachments = notes.flatMap { it.attachments }.distinct()
        val anyPinned = notes.any { it.isPinned }
        val anyLocked = notes.any { it.isLocked }

        return Note(
            id = 0, // Yeni oluşturulacak birleştirilmiş not
            title = mergedTitle,
            content = contentBuilder.toString().trim(),
            color = firstNote.color,
            categoryId = firstNote.categoryId,
            isPinned = anyPinned,
            isArchived = false,
            isDeleted = false,
            isLocked = anyLocked,
            attachments = mergedAttachments,
            timestamp = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Seçili notların genel istatistiklerini hesaplar.
     */
    fun calculateStatistics(notes: List<Note>): BatchStatistics {
        if (notes.isEmpty()) {
            return BatchStatistics(0, 0, 0, 0, 0, false, false, false)
        }

        var totalWords = 0
        var totalChars = 0
        var totalAttachments = 0
        var hasPinned = false
        var hasLocked = false
        var hasChecklists = false
        val categories = mutableSetOf<Long>()

        for (note in notes) {
            val text = "${note.title} ${note.content}".trim()
            totalChars += text.length
            if (text.isNotBlank()) {
                totalWords += text.split(Regex("\\s+")).count { it.isNotBlank() }
            }
            totalAttachments += note.attachments.size
            if (note.isPinned) hasPinned = true
            if (note.isLocked) hasLocked = true
            if (note.categoryId != null) categories.add(note.categoryId)
            if (note.content.contains("- [ ]") || note.content.contains("- [x]")) {
                hasChecklists = true
            }
        }

        return BatchStatistics(
            noteCount = notes.size,
            totalWordCount = totalWords,
            totalCharCount = totalChars,
            totalAttachments = totalAttachments,
            uniqueCategoryCount = categories.size,
            hasPinnedNotes = hasPinned,
            hasLockedNotes = hasLocked,
            hasChecklists = hasChecklists
        )
    }

    /**
     * Seçili notlar için kompakt özet metni üretir. (Örn: "3 not • 420 kelime • 2 ek")
     */
    fun formatBatchSummary(notes: List<Note>): String {
        if (notes.isEmpty()) return "Seçili not yok"
        val stats = calculateStatistics(notes)
        val parts = mutableListOf("${stats.noteCount} not")
        if (stats.totalWordCount > 0) {
            parts.add("${stats.totalWordCount} kelime")
        }
        if (stats.totalAttachments > 0) {
            parts.add("${stats.totalAttachments} ek")
        }
        return parts.joinToString(" • ")
    }
}
