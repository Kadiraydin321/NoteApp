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
 * Orijinal metin uzunluğunu değiştirmediği için OffsetMapping.Identity
 * kullanılır; imleç ve seçim konumu asla kaymaz.
 */
class MarkdownVisualTransformation(
    private val syntaxColor: Color = Color.Gray.copy(alpha = 0.55f),
    private val highlightColor: Color = Color(0xFFFFF59D),
    private val codeBgColor: Color = Color(0x1F000000)
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

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
                // # işaretlerini hafif soluk göster
                addStyle(
                    SpanStyle(color = syntaxColor, fontWeight = FontWeight.Normal),
                    range.first,
                    range.first + level
                )
            }

            // 2. Kalın (Bold): **metin**
            val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
            for (match in boldRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        range.first,
                        range.last + 1
                    )
                    // ** işaretlerini daha soluk yap
                    addStyle(SpanStyle(color = syntaxColor), range.first, range.first + 2)
                    addStyle(SpanStyle(color = syntaxColor), range.last - 1, range.last + 1)
                }
            }

            // 3. İtalik (Italic): *metin* (ancak ** olmayanlar)
            val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)")
            for (match in italicRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 2) {
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        range.first,
                        range.last + 1
                    )
                    addStyle(SpanStyle(color = syntaxColor), range.first, range.first + 1)
                    addStyle(SpanStyle(color = syntaxColor), range.last, range.last + 1)
                }
            }

            // 4. Üstü Çizili (Strikethrough): ~~metin~~
            val strikeRegex = Regex("~~(.*?)~~")
            for (match in strikeRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough),
                        range.first,
                        range.last + 1
                    )
                    addStyle(SpanStyle(color = syntaxColor), range.first, range.first + 2)
                    addStyle(SpanStyle(color = syntaxColor), range.last - 1, range.last + 1)
                }
            }

            // 5. Fosforlu Vurgu (Highlight): ==metin==
            val highlightRegex = Regex("==(.*?)==")
            for (match in highlightRegex.findAll(raw)) {
                val range = match.range
                if (range.last >= range.first + 3) {
                    addStyle(
                        SpanStyle(background = highlightColor, color = Color(0xFF1E1E1E)),
                        range.first,
                        range.last + 1
                    )
                    addStyle(SpanStyle(color = syntaxColor), range.first, range.first + 2)
                    addStyle(SpanStyle(color = syntaxColor), range.last - 1, range.last + 1)
                }
            }

            // 6. Kod Bloğu / Satır içi kod: `kod`
            val codeRegex = Regex("`([^`]+)`")
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
            }
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
