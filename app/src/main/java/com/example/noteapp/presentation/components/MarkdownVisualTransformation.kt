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
    private val activeMatchStartIndex: Int? = null
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val hiddenTokenStyle = SpanStyle(color = Color.Transparent, fontSize = 0.01.sp)
        val visibleTokenStyle = SpanStyle(color = syntaxColor)
        val tokenStyle = if (hideSyntaxTokens) hiddenTokenStyle else visibleTokenStyle

        val annotated = buildAnnotatedString {
            append(raw)

            // 1. Başlıklar: # Başlık 1, ## Başlık 2, ### Başlık 3
            val headingRegex = Regex("(?m)^(#{1,3})\\s+(.*)$")
            for (match in headingRegex.findAll(raw)) {
                val range = match.range
                val level = match.groupValues[1].length
                val fontSize = when (level) {
                    1 -> 22.sp
                    2 -> 19.sp
                    else -> 17.sp
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
            val boldAsteriskRegex = Regex("(?<!\\\\)\\*\\*(?!\\s)([^\n]+?)(?<!\\s)\\*\\*")
            for (match in boldAsteriskRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first + 2, range.last - 1)
                    addStyle(tokenStyle, range.first, range.first + 2)
                    addStyle(tokenStyle, range.last - 1, range.last + 1)
                }
            }

            // B. Çift Altçizgi Kalın: __metin__
            val boldUnderscoreRegex = Regex("(?<!\\\\)__(?!\\s)([^\n]+?)(?<!\\s)__")
            for (match in boldUnderscoreRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first + 2, range.last - 1)
                    addStyle(tokenStyle, range.first, range.first + 2)
                    addStyle(tokenStyle, range.last - 1, range.last + 1)
                }
            }

            // C. Tek Yıldız İtalik: *metin*
            val italicAsteriskRegex = Regex("(?<![\\*\\\\])\\*(?!\\s)([^\n\\*]+?)(?<!\\s)\\*(?!\\*)")
            for (match in italicAsteriskRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 2) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                    addStyle(tokenStyle, range.first, range.first + 1)
                    addStyle(tokenStyle, range.last, range.last + 1)
                }
            }

            // D. Tek Altçizgi İtalik: _metin_
            val italicUnderscoreRegex = Regex("(?<![_\\\\])_(?!\\s)([^\n_]+?)(?<!\\s)_(?!_)")
            for (match in italicUnderscoreRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 2) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                    addStyle(tokenStyle, range.first, range.first + 1)
                    addStyle(tokenStyle, range.last, range.last + 1)
                }
            }

            // 3. Üstü Çizili (Strikethrough): ~~metin~~
            val strikeRegex = Regex("~~([^~\\n]+?)~~")
            for (match in strikeRegex.findAll(raw)) {
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
            val highlightRegex = Regex("==([^=\\n]+?)==")
            for (match in highlightRegex.findAll(raw)) {
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
            val codeRegex = Regex("`([^`\\n]+?)`")
            for (match in codeRegex.findAll(raw)) {
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

            // 6. Not İçi Arama Vurgusu (Search Query Highlighting)
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
