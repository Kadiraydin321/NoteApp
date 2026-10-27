package com.example.noteapp.domain.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Android NoteApp için Çekirdek Markdown Render Motoru (Core Markdown Renderer).
 * Satır içi biçimlendirmeleri (**kalın**, *italik*, ~~üstü çizili~~, ==vurgu==, `kod`, $matematik$)
 * ve Obsidian / Logseq stili çift köşeli parantezli [[Wikilink]] bağlantılarını ayrıştırıp
 * Jetpack Compose AnnotatedString nesnesine dönüştürür.
 */
object MarkdownRenderer {

    private val INLINE_BOLD_ASTERISK_REGEX = Regex("(?<!\\\\)\\*\\*(?!\\s)([^\\n]+?)(?<!\\s)\\*\\*")
    private val INLINE_BOLD_UNDERSCORE_REGEX = Regex("(?<!\\\\)__(?!\\s)([^\\n]+?)(?<!\\s)__")
    private val INLINE_STRIKE_REGEX = Regex("~~([^~\\n]+?)~~")
    private val INLINE_HIGHLIGHT_REGEX = Regex("==([^=\\n]+?)==")
    private val INLINE_CODE_REGEX = Regex("`([^`\\n]+?)`")
    private val INLINE_MATH_REGEX = Regex("(?<!\\\\)\\$(?!\\s)([^$\\n]+?)(?<![\\s\\\\])\\$")
    val CHECKBOX_REGEX = Regex("^(\\s*)([-*])\\s+\\[([ xX])\\]\\s*(.*)$")

    data class Span(val start: Int, val end: Int, val style: SpanStyle)

    /**
     * Satır içi Markdown formatlarını ve [[Wikilink]] referanslarını temizleyip
     * tıklanabilir ("backlink" tag'li) AnnotatedString üretir.
     */
    fun parseInlineMarkdown(text: String): AnnotatedString {
        if (text.isEmpty()) return buildAnnotatedString { }

        val tokenIndices = mutableSetOf<Int>()
        val spans = mutableListOf<Span>()
        val boldTokenRanges = mutableListOf<IntRange>()

        // 1. Çift Yıldız Kalın: **metin**
        for (match in INLINE_BOLD_ASTERISK_REGEX.findAll(text)) {
            val range = match.range
            if (range.last >= range.first + 3) {
                val openToken = range.first until (range.first + 2)
                val closeToken = (range.last - 1)..range.last
                boldTokenRanges.add(openToken)
                boldTokenRanges.add(closeToken)
                openToken.forEach { tokenIndices.add(it) }
                closeToken.forEach { tokenIndices.add(it) }
                spans.add(Span(range.first + 2, range.last - 1, SpanStyle(fontWeight = FontWeight.Bold)))
            }
        }

        // 2. Çift Alt Çizgi Kalın: __metin__
        for (match in INLINE_BOLD_UNDERSCORE_REGEX.findAll(text)) {
            val range = match.range
            if (range.last >= range.first + 3) {
                val openToken = range.first until (range.first + 2)
                val closeToken = (range.last - 1)..range.last
                boldTokenRanges.add(openToken)
                boldTokenRanges.add(closeToken)
                openToken.forEach { tokenIndices.add(it) }
                closeToken.forEach { tokenIndices.add(it) }
                spans.add(Span(range.first + 2, range.last - 1, SpanStyle(fontWeight = FontWeight.Bold)))
            }
        }

        fun isIndexInBoldToken(index: Int): Boolean = boldTokenRanges.any { index in it }

        // 3. Tek Yıldız İtalik: *metin*
        var i = 0
        while (i < text.length) {
            if (text[i] == '*' && !isIndexInBoldToken(i)) {
                if (i + 1 < text.length && text[i + 1] != ' ' && text[i + 1] != '\t' && text[i + 1] != '\n' && text[i + 1] != '*') {
                    var closeIdx = -1
                    var j = i + 1
                    while (j < text.length && text[j] != '\n') {
                        if (text[j] == '*' && !isIndexInBoldToken(j)) {
                            if (text[j - 1] != ' ' && text[j - 1] != '\t' && text[j - 1] != '*') {
                                closeIdx = j
                                break
                            }
                        }
                        j++
                    }

                    if (closeIdx != -1 && closeIdx > i + 1) {
                        tokenIndices.add(i)
                        tokenIndices.add(closeIdx)
                        spans.add(Span(i + 1, closeIdx, SpanStyle(fontStyle = FontStyle.Italic)))
                        i = closeIdx + 1
                        continue
                    }
                }
            }
            i++
        }

        // 4. Tek Alt Çizgi İtalik: _metin_
        var u = 0
        while (u < text.length) {
            if (text[u] == '_' && !isIndexInBoldToken(u)) {
                if (u + 1 < text.length && text[u + 1] != ' ' && text[u + 1] != '\t' && text[u + 1] != '\n' && text[u + 1] != '_') {
                    var closeIdx = -1
                    var j = u + 1
                    while (j < text.length && text[j] != '\n') {
                        if (text[j] == '_' && !isIndexInBoldToken(j)) {
                            if (text[j - 1] != ' ' && text[j - 1] != '\t' && text[j - 1] != '_') {
                                closeIdx = j
                                break
                            }
                        }
                        j++
                    }

                    if (closeIdx != -1 && closeIdx > u + 1) {
                        tokenIndices.add(u)
                        tokenIndices.add(closeIdx)
                        spans.add(Span(u + 1, closeIdx, SpanStyle(fontStyle = FontStyle.Italic)))
                        u = closeIdx + 1
                        continue
                    }
                }
            }
            u++
        }

        // 5. Üstü Çizili: ~~metin~~
        for (match in INLINE_STRIKE_REGEX.findAll(text)) {
            val range = match.range
            if (range.last >= range.first + 3) {
                tokenIndices.add(range.first)
                tokenIndices.add(range.first + 1)
                tokenIndices.add(range.last - 1)
                tokenIndices.add(range.last)
                spans.add(Span(range.first + 2, range.last - 1, SpanStyle(textDecoration = TextDecoration.LineThrough)))
            }
        }

        // 6. Fosforlu Vurgu: ==metin==
        for (match in INLINE_HIGHLIGHT_REGEX.findAll(text)) {
            val range = match.range
            if (range.last >= range.first + 3) {
                tokenIndices.add(range.first)
                tokenIndices.add(range.first + 1)
                tokenIndices.add(range.last - 1)
                tokenIndices.add(range.last)
                spans.add(Span(range.first + 2, range.last - 1, SpanStyle(background = Color(0xFFFFF59D), color = Color(0xFF212121))))
            }
        }

        // 7. Kod: `metin`
        for (match in INLINE_CODE_REGEX.findAll(text)) {
            val range = match.range
            tokenIndices.add(range.first)
            tokenIndices.add(range.last)
            spans.add(Span(range.first + 1, range.last, SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1F000000))))
        }

        // 8. Çift Yönlü Bağlantı (Backlink): [[Note Name]], [[Note Name|Display]] veya [[Note#Heading|Display]]
        val stringAnnotations = mutableListOf<Triple<String, String, IntRange>>()
        for (match in WikiLinkParser.WIKILINK_REGEX.findAll(text)) {
            val range = match.range
            val targetTitle = match.groupValues[1].trim()
            if (targetTitle.isBlank()) continue

            val rawValue = match.value
            val pipeOffset = rawValue.indexOf('|')

            if (pipeOffset != -1) {
                val pipeAbs = range.first + pipeOffset
                for (idx in range.first..pipeAbs) {
                    tokenIndices.add(idx)
                }
                tokenIndices.add(range.last - 1)
                tokenIndices.add(range.last)

                val displayStart = pipeAbs + 1
                val displayEnd = range.last - 1
                if (displayEnd > displayStart) {
                    spans.add(Span(displayStart, displayEnd, SpanStyle(color = Color(0xFF1E88E5), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)))
                    stringAnnotations.add(Triple("backlink", targetTitle, displayStart until displayEnd))
                }
            } else {
                tokenIndices.add(range.first)
                tokenIndices.add(range.first + 1)
                tokenIndices.add(range.last - 1)
                tokenIndices.add(range.last)

                val displayStart = range.first + 2
                val displayEnd = range.last - 1
                if (displayEnd > displayStart) {
                    spans.add(Span(displayStart, displayEnd, SpanStyle(color = Color(0xFF1E88E5), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)))
                    stringAnnotations.add(Triple("backlink", targetTitle, displayStart until displayEnd))
                }
            }
        }

        // 9. Satır İçi Matematik Formülü: $formül$
        for (match in INLINE_MATH_REGEX.findAll(text)) {
            val range = match.range
            val mathContent = match.groupValues[1]
            if (mathContent.isNotBlank() && !mathContent.matches(Regex("^\\d+([.,]\\d+)?$"))) {
                tokenIndices.add(range.first)
                tokenIndices.add(range.last)
                spans.add(Span(range.first + 1, range.last, SpanStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF673AB7)
                )))
            }
        }

        val oldToNew = IntArray(text.length) { -1 }
        val cleanBuilder = StringBuilder()

        for (k in text.indices) {
            if (k !in tokenIndices) {
                oldToNew[k] = cleanBuilder.length
                cleanBuilder.append(text[k])
            }
        }

        return buildAnnotatedString {
            append(cleanBuilder.toString())

            for (span in spans) {
                var newStart = -1
                for (pos in span.start until span.end) {
                    if (oldToNew[pos] != -1) {
                        newStart = oldToNew[pos]
                        break
                    }
                }

                var newEnd = -1
                for (pos in (span.end - 1) downTo span.start) {
                    if (oldToNew[pos] != -1) {
                        newEnd = oldToNew[pos] + 1
                        break
                    }
                }

                if (newStart != -1 && newEnd != -1 && newEnd > newStart) {
                    addStyle(span.style, newStart, newEnd)
                }
            }

            for (annotation in stringAnnotations) {
                var newStart = -1
                for (pos in annotation.third.start until annotation.third.endInclusive + 1) {
                    if (pos < oldToNew.size && oldToNew[pos] != -1) {
                        newStart = oldToNew[pos]
                        break
                    }
                }

                var newEnd = -1
                for (pos in annotation.third.endInclusive downTo annotation.third.start) {
                    if (pos < oldToNew.size && oldToNew[pos] != -1) {
                        newEnd = oldToNew[pos] + 1
                        break
                    }
                }

                if (newStart != -1 && newEnd != -1 && newEnd > newStart) {
                    addStringAnnotation(tag = annotation.first, annotation = annotation.second, start = newStart, end = newEnd)
                }
            }
        }
    }
}
