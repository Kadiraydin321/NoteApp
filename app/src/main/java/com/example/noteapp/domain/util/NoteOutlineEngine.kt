package com.example.noteapp.domain.util

import java.util.Locale

/**
 * Başlık öğesi modeli.
 */
data class HeadingItem(
    val level: Int,
    val text: String,
    val rawLineIndex: Int,
    val charOffset: Int,
    val wordCount: Int = 0
)

/**
 * Not içindekiler özeti.
 */
data class NoteOutline(
    val headings: List<HeadingItem>,
    val totalHeadings: Int,
    val maxDepth: Int
)

/**
 * Not Detayı İçindekiler Tablosu & Başlık Gezgini Motoru.
 * Not içeriğindeki H1, H2, H3 (`#`, `##`, `###`) başlıklarını kod bloklarından
 * izole ederek ağaç hiyerarşisi şeklinde ayrıştırır, her bölümün kelime sayısını
 * ve karakter ofsetini hesaplar; ayrıca tek tıkla nota eklenebilir Markdown TOC üretir.
 */
object NoteOutlineEngine {

    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")

    /**
     * Not metninden başlıkları ve bölümleri ayrıştırır.
     */
    fun extractOutline(content: String): NoteOutline {
        if (content.isBlank()) {
            return NoteOutline(emptyList(), 0, 0)
        }

        val lines = content.lines()
        val rawHeadings = mutableListOf<HeadingItem>()
        var insideCodeBlock = false
        var currentOffset = 0

        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()

            // Kod bloklarını atla (kod içindeki # yorumları başlık sayılmamalı)
            if (trimmed.startsWith("```")) {
                insideCodeBlock = !insideCodeBlock
                currentOffset += line.length + 1
                return@forEachIndexed
            }

            if (!insideCodeBlock) {
                val match = HEADING_REGEX.matchEntire(trimmed)
                if (match != null) {
                    val level = match.groupValues[1].length
                    val text = match.groupValues[2].trim()
                    rawHeadings.add(
                        HeadingItem(
                            level = level,
                            text = text,
                            rawLineIndex = index,
                            charOffset = currentOffset
                        )
                    )
                }
            }

            currentOffset += line.length + 1 // +1 for newline character
        }

        if (rawHeadings.isEmpty()) {
            return NoteOutline(emptyList(), 0, 0)
        }

        // Her bölümün kelime sayısını hesapla (başlıktan sonraki başlığa kadar olan aralık)
        val headingsWithWordCount = rawHeadings.mapIndexed { i, heading ->
            val startLine = heading.rawLineIndex + 1
            val endLine = if (i + 1 < rawHeadings.size) rawHeadings[i + 1].rawLineIndex else lines.size
            val sectionLines = lines.subList(startLine.coerceAtMost(lines.size), endLine.coerceAtMost(lines.size))
            val words = sectionLines.joinToString(" ")
                .trim()
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }
            heading.copy(wordCount = words)
        }

        val maxDepth = headingsWithWordCount.maxOfOrNull { it.level } ?: 0

        return NoteOutline(
            headings = headingsWithWordCount,
            totalHeadings = headingsWithWordCount.size,
            maxDepth = maxDepth
        )
    }

    /**
     * Başlıklardan Markdown uyumlu İçindekiler Tablosu (TOC) listesi üretir.
     */
    fun generateMarkdownToc(outline: NoteOutline, maxLevel: Int = 3): String {
        val eligibleHeadings = outline.headings.filter { it.level <= maxLevel }
        if (eligibleHeadings.isEmpty()) return ""

        return buildString {
            appendLine("### 📑 İçindekiler")
            eligibleHeadings.forEach { heading ->
                val indent = "  ".repeat((heading.level - 1).coerceAtLeast(0))
                val anchor = heading.text.lowercase(Locale.ROOT)
                    .replace(Regex("[^a-z0-9çğıöşü -]"), "")
                    .trim()
                    .replace(" ", "-")
                appendLine("$indent- [${heading.text}](#$anchor)")
            }
        }.trimEnd()
    }
}
