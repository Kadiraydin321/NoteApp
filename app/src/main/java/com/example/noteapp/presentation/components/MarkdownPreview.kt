package com.example.noteapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownPreview(
    content: String,
    onContentChange: (String) -> Unit,
    contentColor: Color,
    onLinkClick: (String) -> Unit = {},
    fontSize: Float = 16f,
    modifier: Modifier = Modifier
) {
    val lines = content.lines()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()

            when {
                // 1. Checkbox: - [ ] veya - [x]
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") -> {
                    val isChecked = trimmed.startsWith("- [x] ")
                    val itemText = if (isChecked) trimmed.removePrefix("- [x] ") else trimmed.removePrefix("- [ ] ")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KeepCheckboxIcon(
                            isChecked = isChecked,
                            fontSize = fontSize.sp,
                            contentColor = contentColor,
                            onToggle = {
                                val prefix = if (!isChecked) "- [x] " else "- [ ] "
                                val updatedLines = lines.toMutableList()
                                updatedLines[index] = prefix + itemText
                                onContentChange(updatedLines.joinToString("\n"))
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(itemText),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (isChecked) contentColor.copy(alpha = 0.5f) else contentColor
                            )
                        )
                    }
                }

                // 2. Başlık 1: # Başlık
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }

                // 3. Başlık 2: ## Başlık
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                // 4. Başlık 3: ### Başlık
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### ")),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                // 5. Alıntı ve Vurgu Kutusu (Notion & Obsidian Callout Block: > [!NOTE], > [!TIP], > [!WARNING])
                trimmed.startsWith("> ") -> {
                    val rawQuote = trimmed.removePrefix("> ").trim()
                    val isCallout = rawQuote.startsWith("[!NOTE]") ||
                            rawQuote.startsWith("[!TIP]") ||
                            rawQuote.startsWith("[!WARNING]") ||
                            rawQuote.startsWith("[!IMPORTANT]") ||
                            rawQuote.startsWith("[!CAUTION]")

                    if (isCallout) {
                        val calloutType = when {
                            rawQuote.startsWith("[!NOTE]") -> "NOTE"
                            rawQuote.startsWith("[!TIP]") -> "TIP"
                            rawQuote.startsWith("[!WARNING]") -> "WARNING"
                            rawQuote.startsWith("[!IMPORTANT]") -> "IMPORTANT"
                            else -> "CAUTION"
                        }
                        val calloutText = rawQuote.removePrefix("[!$calloutType]").trim()
                        val calloutColor = when (calloutType) {
                            "NOTE" -> MaterialTheme.colorScheme.primary
                            "TIP" -> Color(0xFF4CAF50)
                            "WARNING" -> Color(0xFFFFA000)
                            "IMPORTANT" -> Color(0xFF9C27B0)
                            else -> MaterialTheme.colorScheme.error
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = calloutColor.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, calloutColor.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = when (calloutType) {
                                        "NOTE" -> "ℹ️ "
                                        "TIP" -> "💡 "
                                        "WARNING" -> "⚠️ "
                                        "IMPORTANT" -> "⭐ "
                                        else -> "🚨 "
                                    },
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = calloutType,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = calloutColor
                                    )
                                    if (calloutText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = parseInlineMarkdown(calloutText),
                                            style = MaterialTheme.typography.bodyMedium.copy(color = contentColor)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parseInlineMarkdown(rawQuote),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = contentColor.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }
                }

                // 6. Madde İşareti: • veya -
                trimmed.startsWith("• ") || trimmed.startsWith("- ") -> {
                    val bulletText = if (trimmed.startsWith("• ")) trimmed.removePrefix("• ") else trimmed.removePrefix("- ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", style = MaterialTheme.typography.bodyLarge.copy(color = contentColor, fontWeight = FontWeight.Bold))
                        MarkdownText(
                            text = parseInlineMarkdown(bulletText),
                            style = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                            onLinkClick = onLinkClick
                        )
                    }
                }

                // 7. Normal Paragraf
                else -> {
                    if (trimmed.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        MarkdownText(
                            text = parseInlineMarkdown(trimmed),
                            style = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: androidx.compose.ui.text.AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    onLinkClick: (String) -> Unit
) {
    androidx.compose.foundation.text.ClickableText(
        text = text,
        style = style,
        onClick = { offset ->
            text.getStringAnnotations(tag = "backlink", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onLinkClick(annotation.item)
                }
        }
    )
}

/**
 * Satır içi Markdown formatlarını (**kalın**, *italik*, ~~üstü çizili~~, ==vurgu==, `kod`)
 * temiz bir şekilde işler; biçimlendirme işaretlerini kaldırarak sadece stilize edilmiş metni döndürür.
 */
fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    if (text.isEmpty()) return buildAnnotatedString { }

    data class Span(val start: Int, val end: Int, val style: SpanStyle)
    val tokenIndices = mutableSetOf<Int>()
    val spans = mutableListOf<Span>()
    val boldTokenRanges = mutableListOf<IntRange>()

    // 1. Çift Yıldız Kalın: **metin**
    val boldAsteriskRegex = Regex("(?<!\\\\)\\*\\*(?!\\s)([^\n]+?)(?<!\\s)\\*\\*")
    for (match in boldAsteriskRegex.findAll(text)) {
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
    val boldUnderscoreRegex = Regex("(?<!\\\\)__(?!\\s)([^\n]+?)(?<!\\s)__")
    for (match in boldUnderscoreRegex.findAll(text)) {
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

    // 3. Tek Yıldız İtalik: *metin* (Kalın işaretlerine dahil olmayanlar)
    fun isIndexInBoldToken(index: Int): Boolean = boldTokenRanges.any { index in it }

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
    val strikeRegex = Regex("~~([^~\\n]+?)~~")
    for (match in strikeRegex.findAll(text)) {
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
    val highlightRegex = Regex("==([^=\\n]+?)==")
    for (match in highlightRegex.findAll(text)) {
        val range = match.range
        if (range.last >= range.first + 3) {
            tokenIndices.add(range.first)
            tokenIndices.add(range.first + 1)
            tokenIndices.add(range.last - 1)
            tokenIndices.add(range.last)
            spans.add(Span(range.first + 2, range.last - 1, SpanStyle(background = Color(0xFFFFF59D), color = Color(0xFF212121))))
        }
    }

    // 8. Kod: `metin`
    val codeRegex = Regex("`([^`\\n]+?)`")
    for (match in codeRegex.findAll(text)) {
        val range = match.range
        tokenIndices.add(range.first)
        tokenIndices.add(range.last)
        spans.add(Span(range.first + 1, range.last, SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1F000000))))
    }

    // 9. Çift Yönlü Bağlantı (Backlink): [[Note Name]]
    val linkRegex = Regex("\\[\\[([^\\]\\n]+?)\\]\\]")
    val stringAnnotations = mutableListOf<Triple<String, String, IntRange>>()
    for (match in linkRegex.findAll(text)) {
        val range = match.range
        val noteName = match.groupValues[1]
        tokenIndices.add(range.first)
        tokenIndices.add(range.first + 1)
        tokenIndices.add(range.last - 1)
        tokenIndices.add(range.last)
        spans.add(Span(range.first + 2, range.last - 1, SpanStyle(color = Color(0xFF1E88E5), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)))
        stringAnnotations.add(Triple("backlink", noteName, (range.first + 2) until (range.last)))
    }

    // Metni oluştururken işaretleri atla ve yeni indeksleri haritalandır
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
