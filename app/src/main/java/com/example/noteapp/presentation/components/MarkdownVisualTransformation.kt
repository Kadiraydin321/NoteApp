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
 * [hideSyntaxTokens] true olduğunda, biçimlendirme işaretleri (*, **, ~~, ==)
 * gizlenir; metin temiz ve stilize olarak görünür. Ancak tek başına yazılan
 * veya madde imi olarak kullanılan yıldız işaretleri (* madde, 5 * 3) ASLA gizlenmez.
 *
 * Orijinal metin uzunluğunu değiştirmediği için OffsetMapping.Identity
 * kullanılır; imleç ve seçim konumu asla kaymaz.
 */
class MarkdownVisualTransformation(
    val hideSyntaxTokens: Boolean = true,
    private val syntaxColor: Color = Color.Gray.copy(alpha = 0.55f),
    private val highlightColor: Color = Color(0xFFFFF59D),
    private val codeBgColor: Color = Color(0x1F000000)
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
                // # işaretlerini ayara göre gizle veya soluk göster
                addStyle(
                    tokenStyle,
                    range.first,
                    range.first + level
                )
            }

            // 2. KALIN (BOLD) VE İTALİK (ITALIC) - İç içe, birleşik ve karmaşık durumlar dahil (***, **, *, __, _)
            val boldTokenRanges = mutableListOf<IntRange>()

            // A. Çift Yıldız Kalın: **metin**
            // Satır içi, başı ve sonu boşluk olmayan kapalı ** çiftleri
            val boldAsteriskRegex = Regex("(?<!\\\\)\\*\\*(?!\\s)([^\n]+?)(?<!\\s)\\*\\*")
            for (match in boldAsteriskRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first + 2, range.last - 1)
                    val openToken = range.first until (range.first + 2)
                    val closeToken = (range.last - 1)..range.last
                    boldTokenRanges.add(openToken)
                    boldTokenRanges.add(closeToken)
                    addStyle(tokenStyle, openToken.first, openToken.last + 1)
                    addStyle(tokenStyle, closeToken.first, closeToken.last + 1)
                }
            }

            // B. Çift Alt Çizgi Kalın: __metin__
            val boldUnderscoreRegex = Regex("(?<!\\\\)__(?!\\s)([^\n]+?)(?<!\\s)__")
            for (match in boldUnderscoreRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first + 2, range.last - 1)
                    val openToken = range.first until (range.first + 2)
                    val closeToken = (range.last - 1)..range.last
                    boldTokenRanges.add(openToken)
                    boldTokenRanges.add(closeToken)
                    addStyle(tokenStyle, openToken.first, openToken.last + 1)
                    addStyle(tokenStyle, closeToken.first, closeToken.last + 1)
                }
            }

            // C. Tek Yıldız İtalik: *metin*
            // Kalın işaretlerine (boldTokenRanges) ait olmayan ve başı/sonu boşluk veya tek yıldız olmayan yıldız çiftleri
            fun isIndexInBoldToken(index: Int): Boolean {
                return boldTokenRanges.any { index in it }
            }

            var i = 0
            while (i < raw.length) {
                if (raw[i] == '*' && !isIndexInBoldToken(i)) {
                    // Açılış yıldızı kontrolü: sonraki karakter boşluk, yıldız veya satır sonu olmamalı
                    if (i + 1 < raw.length && raw[i + 1] != ' ' && raw[i + 1] != '\t' && raw[i + 1] != '\n' && raw[i + 1] != '*') {
                        // Kapanış yıldızı ara
                        var closeIdx = -1
                        var j = i + 1
                        while (j < raw.length && raw[j] != '\n') {
                            if (raw[j] == '*' && !isIndexInBoldToken(j)) {
                                // Öncesi boşluk veya yıldız olmamalı
                                if (raw[j - 1] != ' ' && raw[j - 1] != '\t' && raw[j - 1] != '*') {
                                    closeIdx = j
                                    break
                                }
                            }
                            j++
                        }

                        if (closeIdx != -1 && closeIdx > i + 1) {
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), i + 1, closeIdx)
                            addStyle(tokenStyle, i, i + 1)
                            addStyle(tokenStyle, closeIdx, closeIdx + 1)
                            i = closeIdx + 1
                            continue
                        }
                    }
                }
                i++
            }

            // D. Tek Alt Çizgi İtalik: _metin_
            var u = 0
            while (u < raw.length) {
                if (raw[u] == '_' && !isIndexInBoldToken(u)) {
                    if (u + 1 < raw.length && raw[u + 1] != ' ' && raw[u + 1] != '\t' && raw[u + 1] != '\n' && raw[u + 1] != '_') {
                        var closeIdx = -1
                        var j = u + 1
                        while (j < raw.length && raw[j] != '\n') {
                            if (raw[j] == '_' && !isIndexInBoldToken(j)) {
                                if (raw[j - 1] != ' ' && raw[j - 1] != '\t' && raw[j - 1] != '_') {
                                    closeIdx = j
                                    break
                                }
                            }
                            j++
                        }

                        if (closeIdx != -1 && closeIdx > u + 1) {
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), u + 1, closeIdx)
                            addStyle(tokenStyle, u, u + 1)
                            addStyle(tokenStyle, closeIdx, closeIdx + 1)
                            u = closeIdx + 1
                            continue
                        }
                    }
                }
                u++
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
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
