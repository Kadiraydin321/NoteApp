package com.example.noteapp.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

// Compiled once because filter() runs on every editor text update.
private val fencedCodeBlockRegex = Regex("(?m)^```[^\\n]*\\n([\\s\\S]*?)^```\\s*$")
private val headingRegex = Regex("(?m)^(#{1,3})\\s+(.*)$")
private val boldAsteriskRegex = Regex("(?<!\\\\)\\*\\*(?!\\s)([^\\n]+?)(?<!\\s)\\*\\*")
private val boldUnderscoreRegex = Regex("(?<!\\\\)__(?!\\s)([^\\n]+?)(?<!\\s)__")
private val italicAsteriskRegex = Regex("(?<![\\*\\\\])\\*(?!\\s)([^\\n\\*]+?)(?<!\\s)\\*(?!\\*)")
private val italicUnderscoreRegex = Regex("(?<![_\\\\])_(?!\\s)([^\\n_]+?)(?<!\\s)_(?!_)")
private val strikeRegex = Regex("~~([^~\\n]+?)~~")
private val highlightRegex = Regex("==([^=\\n]+?)==")
private val inlineCodeRegex = Regex("`([^`\\n]+?)`")
private val wikiLinkRegex = Regex("\\[\\[([^\\]\\n]+?)\\]\\]")
private val quoteRegex = Regex("(?m)^>\\s+(.*)$")

/**
 * Kullanıcı metin yazarken veya butonlarla formatlarken
 * **kalın**, *italik*, ~~üstü çizili~~, ==vurgu== ve # Başlıkları
 * anında canlı olarak şekillendirip gösteren VisualTransformation.
 *
 * Ayrıca [searchQuery] verildiğinde, aranan tüm kelimeleri sarı renkle,
 * o an seçili olan [activeMatchStartIndex] eşleşmesini ise belirgin turuncuyla vurgular.
 */
class MarkdownVisualTransformation(
    val hideSyntaxTokens: Boolean = true,
    private val syntaxColor: Color = Color.Gray.copy(alpha = 0.55f),
    private val highlightColor: Color = Color(0xFFFFF59D),
    private val codeBgColor: Color = Color(0x1F000000),
    private val searchQuery: String = "",
    private val activeMatchStartIndex: Int? = null,
    private val baseFontSize: Float = 16f
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val hiddenTokenStyle = SpanStyle(color = Color.Transparent, fontSize = 0.01.sp)
        val visibleTokenStyle = SpanStyle(color = syntaxColor)
        val tokenStyle = if (hideSyntaxTokens) hiddenTokenStyle else visibleTokenStyle
        val fencedCodeMatches = fencedCodeBlockRegex.findAll(raw).toList()
        fun isInFencedCode(range: IntRange): Boolean = fencedCodeMatches.any { range.first in it.range }

        val annotated = buildAnnotatedString {
            append(raw)

            // Fenced code blocks use monospace styling; supported inline actions remain active inside them.
            for (match in fencedCodeMatches) {
                val codeRange = match.groups[1]?.range ?: continue
                if (!codeRange.isEmpty()) {
                    addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor),
                        codeRange.first,
                        codeRange.last + 1
                    )
                }
                addStyle(tokenStyle, match.range.first, codeRange.first)
                addStyle(tokenStyle, codeRange.last + 1, match.range.last + 1)
            }

            // 1. Başlıklar: # Başlık 1, ## Başlık 2, ### Başlık 3
            for (match in headingRegex.findAll(raw).filterNot { isInFencedCode(it.range) }) {
                val range = match.range
                val level = match.groupValues[1].length
                val fontSize = when (level) {
                    1 -> (baseFontSize * 1.38f).sp
                    2 -> (baseFontSize * 1.22f).sp
                    else -> (baseFontSize * 1.12f).sp
                }
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize),
                    range.first,
                    range.last + 1
                )
                addStyle(
                    tokenStyle,
                    range.first,
                    range.first + level
                )
            }

            // 2. KALIN (BOLD) VE İTALİK (ITALIC)
            // A. Çift Yıldız Kalın: **metin**
            for (match in boldAsteriskRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first + 2, range.last - 1)
                    addStyle(tokenStyle, range.first, range.first + 2)
                    addStyle(tokenStyle, range.last - 1, range.last + 1)
                }
            }

            // B. Çift Altçizgi Kalın: __metin__
            for (match in boldUnderscoreRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first + 2, range.last - 1)
                    addStyle(tokenStyle, range.first, range.first + 2)
                    addStyle(tokenStyle, range.last - 1, range.last + 1)
                }
            }

            // C. Tek Yıldız İtalik: *metin*
            for (match in italicAsteriskRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 2) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                    addStyle(tokenStyle, range.first, range.first + 1)
                    addStyle(tokenStyle, range.last, range.last + 1)
                }
            }

            // D. Tek Altçizgi İtalik: _metin_
            for (match in italicUnderscoreRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 2) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                    addStyle(tokenStyle, range.first, range.first + 1)
                    addStyle(tokenStyle, range.last, range.last + 1)
                }
            }

            // 3. Üstü Çizili (Strikethrough): ~~metin~~
            for (match in strikeRegex.findAll(raw).filterNot { isInFencedCode(it.range) }) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough),
                        range.first + 2,
                        range.last - 1
                    )
                    addStyle(tokenStyle, range.first, range.first + 2)
                    addStyle(tokenStyle, range.last - 1, range.last + 1)
                }
            }

            // 4. Fosforlu Vurgu (Highlight): ==metin==
            for (match in highlightRegex.findAll(raw).filterNot { isInFencedCode(it.range) }) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(
                        SpanStyle(background = highlightColor, color = Color(0xFF1E1E1E)),
                        range.first + 2,
                        range.last - 1
                    )
                    addStyle(tokenStyle, range.first, range.first + 2)
                    addStyle(tokenStyle, range.last - 1, range.last + 1)
                }
            }

            // 5. Kod Bloğu / Satır içi kod: `kod`
            for (match in inlineCodeRegex.findAll(raw).filterNot { isInFencedCode(it.range) }) {
                val range = match.range
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor
                    ),
                    range.first,
                    range.last + 1
                )
                addStyle(tokenStyle, range.first, range.first + 1)
                addStyle(tokenStyle, range.last, range.last + 1)
            }

            // 6. Çift Yönlü Bağlantı (Backlink): [[Note Name]]
            for (match in wikiLinkRegex.findAll(raw)) {
                val range = match.range
                addStyle(
                    SpanStyle(
                        color = Color(0xFF1E88E5),
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    ),
                    range.first + 2,
                    range.last - 1
                )
                addStyle(tokenStyle, range.first, range.first + 2)
                addStyle(tokenStyle, range.last - 1, range.last + 1)
            }

            // 7. Alıntı (Blockquote): > metin
            for (match in quoteRegex.findAll(raw).filterNot { isInFencedCode(it.range) }) {
                val range = match.range
                addStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = syntaxColor
                    ),
                    range.first + 2,
                    range.last + 1
                )
                addStyle(tokenStyle, range.first, range.first + 2)
            }

            // 8. Not İçi Arama Vurgusu (Search Query Highlighting)
            if (searchQuery.isNotBlank()) {
                var searchIdx = raw.indexOf(searchQuery, 0, ignoreCase = true)
                while (searchIdx >= 0) {
                    val endIdx = (searchIdx + searchQuery.length).coerceAtMost(raw.length)
                    val isActive = activeMatchStartIndex != null && searchIdx == activeMatchStartIndex

                    if (isActive) {
                        // Aktif odaklanılan eşleşme: Belirgin Turuncu arka plan + Kalın yazı
                        addStyle(
                            SpanStyle(
                                background = Color(0xFFFF9800),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            ),
                            searchIdx,
                            endIdx
                        )
                    } else {
                        // Diğer tüm eşleşmeler: Parlak Sarı arka plan
                        addStyle(
                            SpanStyle(
                                background = Color(0xFFFFF176),
                                color = Color.Black
                            ),
                            searchIdx,
                            endIdx
                        )
                    }
                    searchIdx = raw.indexOf(searchQuery, searchIdx + 1, ignoreCase = true)
                }
            }
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
