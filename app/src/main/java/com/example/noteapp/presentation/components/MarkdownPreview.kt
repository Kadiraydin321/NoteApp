package com.example.noteapp.presentation.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.ClipboardHelper
import com.example.noteapp.domain.util.CodeSyntaxHighlighter
import com.example.noteapp.domain.util.MarkdownRenderer
import com.example.noteapp.domain.util.MarkdownTableParser
import com.example.noteapp.domain.util.MathFormulaEngine
import com.example.noteapp.domain.util.WikiLinkParser

@Composable
fun MarkdownPreview(
    content: String,
    onContentChange: (String) -> Unit,
    contentColor: Color,
    onLinkClick: (String) -> Unit = {},
    fontSize: Float = 16f,
    lineHeightMultiplier: Float = 1.35f,
    letterSpacingSp: Float = 0f,
    modifier: Modifier = Modifier
) {
    val lines = content.lines()
    val computedLineHeight = (fontSize * lineHeightMultiplier).sp
    val computedLetterSpacing = letterSpacingSp.sp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var index = 0
        while (index < lines.size) {
            val lineIndex = index
            val line = lines[lineIndex]
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim()
                val codeStart = lineIndex + 1
                var codeEnd = codeStart
                while (codeEnd < lines.size && !lines[codeEnd].trimStart().startsWith("```")) {
                    codeEnd++
                }
                val code = lines.subList(codeStart, codeEnd).joinToString("\n")
                val isDark = contentColor.luminance() > 0.5f
                val normLang = remember(language) { CodeSyntaxHighlighter.normalizeLanguageName(language) }
                val highlightedCode = remember(code, language, isDark, contentColor) {
                    CodeSyntaxHighlighter.highlightToAnnotatedString(code, language, isDarkTheme = isDark, defaultContentColor = contentColor)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = normLang,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            val ctx = LocalContext.current
                            IconButton(
                                onClick = {
                                    ClipboardHelper.copyToClipboard(
                                        context = ctx,
                                        label = "Kod",
                                        text = code,
                                        option = ClipboardHelper.CopyOption.PLAIN_TEXT,
                                        showToast = true
                                    )
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Kodu Kopyala",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = highlightedCode,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    lineHeight = computedLineHeight,
                                    letterSpacing = computedLetterSpacing
                                )
                            )
                        }
                    }
                }
                index = if (codeEnd < lines.size) codeEnd + 1 else lines.size
                continue
            }

            // 0. Matematiksel Blok Formül: $$ ... $$
            if (trimmed.startsWith("$$")) {
                val isSingleLine = trimmed.length > 2 && trimmed.endsWith("$$") && trimmed != "$$"
                val mathStart = lineIndex
                var mathEnd = lineIndex
                val latexContent = if (isSingleLine) {
                    trimmed.removePrefix("$$").removeSuffix("$$").trim()
                } else {
                    mathEnd = lineIndex + 1
                    while (mathEnd < lines.size && !lines[mathEnd].trim().endsWith("$$")) {
                        mathEnd++
                    }
                    val rawBlockLines = lines.subList(mathStart, minOf(mathEnd + 1, lines.size))
                    rawBlockLines.joinToString("\n")
                        .replace(Regex("^\\s*\\$\\$"), "")
                        .replace(Regex("\\$\\$\\s*$"), "")
                        .trim()
                }

                val renderedUnicode = remember(latexContent) {
                    MathFormulaEngine.convertToUnicodeMath(latexContent)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Functions,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Matematik / LaTeX",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            val ctx = LocalContext.current
                            IconButton(
                                onClick = {
                                    ClipboardHelper.copyToClipboard(
                                        context = ctx,
                                        label = "Formül",
                                        text = latexContent,
                                        option = ClipboardHelper.CopyOption.PLAIN_TEXT,
                                        showToast = true
                                    )
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Formülü Kopyala",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = renderedUnicode.ifBlank { latexContent },
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = (fontSize * 1.25f).sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                color = contentColor
                            )
                        }
                    }
                }

                index = if (isSingleLine) lineIndex + 1 else if (mathEnd < lines.size) mathEnd + 1 else lines.size
                continue
            }

            if (MarkdownTableParser.isTableStart(lines, lineIndex)) {
                val tableResult = MarkdownTableParser.parseTable(lines, lineIndex)
                if (tableResult != null) {
                    val (table, nextIndex) = tableResult
                    MarkdownTableView(
                        table = table,
                        contentColor = contentColor,
                        fontSize = fontSize,
                        isEditable = true,
                        onTableChange = { updatedTable ->
                            val newMarkdownTable = MarkdownTableParser.toMarkdown(updatedTable)
                            val tableLines = newMarkdownTable.lines()
                            val updatedLines = lines.toMutableList()
                            val before = updatedLines.subList(0, lineIndex)
                            val after = if (nextIndex < updatedLines.size) updatedLines.subList(nextIndex, updatedLines.size) else emptyList()
                            val allUpdated = before + tableLines + after
                            onContentChange(allUpdated.joinToString("\n"))
                        }
                    )
                    index = nextIndex
                    continue
                }
            }

            when {
                // 1. Checkbox: - [ ] veya - [x] veya * [ ] veya * [x]
                CHECKBOX_LINE_REGEX.matches(line) -> {
                    val match = CHECKBOX_LINE_REGEX.matchEntire(line)!!
                    val indent = match.groupValues[1]
                    val bullet = match.groupValues[2]
                    val mark = match.groupValues[3]
                    val itemText = match.groupValues[4]
                    val isChecked = mark.equals("x", ignoreCase = true)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KeepCheckboxIcon(
                            isChecked = isChecked,
                            fontSize = fontSize.sp,
                            contentColor = contentColor,
                            onToggle = {
                                val nextMark = if (!isChecked) "x" else " "
                                val updatedLines = lines.toMutableList()
                                updatedLines[lineIndex] = "$indent$bullet [$nextMark] $itemText"
                                onContentChange(updatedLines.joinToString("\n"))
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MarkdownText(
                            text = parseInlineMarkdown(itemText),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                lineHeight = computedLineHeight,
                                letterSpacing = computedLetterSpacing,
                                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (isChecked) contentColor.copy(alpha = 0.5f) else contentColor
                            ),
                            onLinkClick = onLinkClick
                        )
                    }
                }

                // 2. Başlık 1: # Başlık
                PREVIEW_HEADING_REGEX.matches(trimmed) -> {
                    val heading = PREVIEW_HEADING_REGEX.matchEntire(trimmed)!!
                    val level = heading.groupValues[1].length
                    val headingSize = when (level) {
                        1 -> fontSize * 1.5f
                        2 -> fontSize * 1.3f
                        3 -> fontSize * 1.15f
                        else -> fontSize
                    }
                    MarkdownText(
                        text = parseInlineMarkdown(heading.groupValues[2]),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = headingSize.sp,
                            lineHeight = (headingSize * lineHeightMultiplier).sp,
                            letterSpacing = computedLetterSpacing,
                            color = contentColor
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick
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
                                        MarkdownText(
                                            text = parseInlineMarkdown(calloutText),
                                            style = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                                            onLinkClick = onLinkClick
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
                            MarkdownText(
                                text = parseInlineMarkdown(rawQuote),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = contentColor.copy(alpha = 0.85f)
                                ),
                                onLinkClick = onLinkClick
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
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                lineHeight = computedLineHeight,
                                letterSpacing = computedLetterSpacing,
                                color = contentColor
                            ),
                            onLinkClick = onLinkClick
                        )
                    }
                }

                PREVIEW_NUMBERED_ITEM_REGEX.matches(trimmed) -> {
                    val item = PREVIEW_NUMBERED_ITEM_REGEX.matchEntire(trimmed)!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${item.groupValues[1]}. ",
                            style = MaterialTheme.typography.bodyLarge.copy(color = contentColor)
                        )
                        MarkdownText(
                            text = parseInlineMarkdown(item.groupValues[2]),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                lineHeight = computedLineHeight,
                                letterSpacing = computedLetterSpacing,
                                color = contentColor
                            ),
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
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                lineHeight = computedLineHeight,
                                letterSpacing = computedLetterSpacing,
                                color = contentColor
                            ),
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
            index++
        }
    }
}

/** Creates a compact, styled excerpt for note cards while removing block Markdown markers. */
fun parseMarkdownCardPreview(content: String, lineLimit: Int = 6): androidx.compose.ui.text.AnnotatedString {
    if (content.isEmpty() || lineLimit <= 0) return buildAnnotatedString { }

    return buildAnnotatedString {
        var insideCodeBlock = false
        var emittedLine = false
        var lineStart = 0
        var lineCount = 0
        // A note card only shows a few lines. Scan those lines directly instead of
        // splitting the full note body (which can be very large) on every card bind.
        while (lineStart <= content.length && lineCount < lineLimit) {
            val lineEnd = content.indexOf('\n', lineStart).let { if (it == -1) content.length else it }
            val rawLine = content.substring(lineStart, lineEnd).take(MAX_CARD_PREVIEW_LINE_LENGTH)
            lineCount++
            val trimmed = rawLine.trimStart()
            if (trimmed.startsWith("```")) {
                insideCodeBlock = !insideCodeBlock
                lineStart = lineEnd + 1
                continue
            }

            val displayLine = when {
                PREVIEW_HEADING_LINE_REGEX.matches(trimmed) -> trimmed.replaceFirst(PREVIEW_HEADING_PREFIX_REGEX, "")
                trimmed.startsWith("- [x] ", ignoreCase = true) || trimmed.startsWith("* [x] ", ignoreCase = true) -> "☑ " + trimmed.drop(6)
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> "☐ " + trimmed.drop(6)
                trimmed.startsWith("> [!", ignoreCase = true) ->
                    trimmed.replaceFirst(PREVIEW_CALLOUT_PREFIX_REGEX, "").trim()
                trimmed.startsWith("> ") -> trimmed.removePrefix("> ")
                trimmed.startsWith("- ") -> "• " + trimmed.removePrefix("- ")
                else -> rawLine
            }

            if (displayLine.isNotBlank()) {
                if (emittedLine) append("\n")
                emittedLine = true
                if (insideCodeBlock) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1F000000))) {
                        append(displayLine)
                    }
                } else {
                    append(parseInlineMarkdown(displayLine))
                }
            }

            if (lineEnd == content.length) break
            lineStart = lineEnd + 1
        }
    }
}

private const val MAX_CARD_PREVIEW_LINE_LENGTH = 240
private val CHECKBOX_LINE_REGEX = Regex("^(\\s*)([-*])\\s+\\[([ xX])\\]\\s*(.*)$")
private val PREVIEW_HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
private val PREVIEW_NUMBERED_ITEM_REGEX = Regex("^(\\d+)[.)]\\s+(.+)$")
private val PREVIEW_HEADING_LINE_REGEX = Regex("#{1,6}\\s+.*")
private val PREVIEW_HEADING_PREFIX_REGEX = Regex("^#{1,6}\\s+")
private val PREVIEW_CALLOUT_PREFIX_REGEX = Regex("^>\\s+\\[![A-Za-z]+]\\s*", RegexOption.IGNORE_CASE)
private val INLINE_BOLD_ASTERISK_REGEX = Regex("(?<!\\\\)\\*\\*(?!\\s)([^\\n]+?)(?<!\\s)\\*\\*")
private val INLINE_BOLD_UNDERSCORE_REGEX = Regex("(?<!\\\\)__(?!\\s)([^\\n]+?)(?<!\\s)__")
private val INLINE_STRIKE_REGEX = Regex("~~([^~\\n]+?)~~")
private val INLINE_HIGHLIGHT_REGEX = Regex("==([^=\\n]+?)==")
private val INLINE_CODE_REGEX = Regex("`([^`\\n]+?)`")
private val INLINE_WIKI_LINK_REGEX = WikiLinkParser.WIKILINK_REGEX
private val INLINE_MATH_REGEX = Regex("(?<!\\\\)\\$(?!\\s)([^$\\n]+?)(?<![\\s\\\\])\\$")

@Composable
fun MarkdownText(
    text: androidx.compose.ui.text.AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit
) {
    androidx.compose.foundation.text.ClickableText(
        text = text,
        style = style,
        modifier = modifier,
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
 * ve [[Wikilink]] bağlantılarını MarkdownRenderer aracılığıyla temiz ve stilize bir şekilde işler.
 */
fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString =
    MarkdownRenderer.parseInlineMarkdown(text)
