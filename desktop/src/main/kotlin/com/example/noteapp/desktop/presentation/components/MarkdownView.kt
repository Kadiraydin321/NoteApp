package com.example.noteapp.desktop.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownToolbar(
    onInsertText: (prefix: String, suffix: String) -> Unit,
    viewMode: MarkdownViewMode,
    onViewModeChange: (MarkdownViewMode) -> Unit,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (onUndo != null) {
                MarkdownToolButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    tooltip = "Geri Al (Ctrl+Z)",
                    enabled = canUndo,
                    onClick = onUndo
                )
            }
            if (onRedo != null) {
                MarkdownToolButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    tooltip = "İleri Al (Ctrl+Y)",
                    enabled = canRedo,
                    onClick = onRedo
                )
            }
            if (onUndo != null || onRedo != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                )
            }

            MarkdownToolButton(
                icon = Icons.Default.FormatBold,
                tooltip = "Kalın (Ctrl+B)",
                onClick = { onInsertText("**", "**") }
            )
            MarkdownToolButton(
                icon = Icons.Default.FormatItalic,
                tooltip = "İtalik (Ctrl+I)",
                onClick = { onInsertText("*", "*") }
            )
            MarkdownToolButton(
                icon = Icons.Default.FormatStrikethrough,
                tooltip = "Üstü Çizili",
                onClick = { onInsertText("~~", "~~") }
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )

            MarkdownToolButton(
                icon = Icons.Default.Title,
                tooltip = "Büyük Başlık (H1)",
                onClick = { onInsertText("# ", "") }
            )
            MarkdownToolButton(
                label = "H2",
                tooltip = "Alt Başlık (H2)",
                onClick = { onInsertText("## ", "") }
            )
            MarkdownToolButton(
                icon = Icons.Default.CheckBox,
                tooltip = "Yapılacak Maddesi",
                onClick = { onInsertText("- [ ] ", "") }
            )
            MarkdownToolButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                tooltip = "Madde İmi",
                onClick = { onInsertText("- ", "") }
            )
            MarkdownToolButton(
                icon = Icons.Default.Code,
                tooltip = "Kod Bloğu",
                onClick = { onInsertText("`", "`") }
            )
            MarkdownToolButton(
                icon = Icons.Default.FormatQuote,
                tooltip = "Alıntı",
                onClick = { onInsertText("> ", "") }
            )
            MarkdownToolButton(
                label = "[[ ]]",
                tooltip = "Not Bağlantısı (WikiLink)",
                onClick = { onInsertText("[[", "]]") }
            )
            MarkdownToolButton(
                label = "#",
                tooltip = "Etiket (Hashtag)",
                onClick = { onInsertText("#", "") }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Şık Kompakt Görünüm Değiştirici Hap (Pill Switcher)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.height(30.dp)
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ViewModePillButton(
                        icon = Icons.Default.Edit,
                        tooltip = "Sadece Düzenleyici",
                        isSelected = viewMode == MarkdownViewMode.EDIT_ONLY,
                        onClick = { onViewModeChange(MarkdownViewMode.EDIT_ONLY) }
                    )
                    ViewModePillButton(
                        icon = Icons.Default.VerticalSplit,
                        tooltip = "Bölünmüş Görünüm",
                        isSelected = viewMode == MarkdownViewMode.SPLIT,
                        onClick = { onViewModeChange(MarkdownViewMode.SPLIT) }
                    )
                    ViewModePillButton(
                        icon = Icons.Default.Visibility,
                        tooltip = "Canlı Önizleme",
                        isSelected = viewMode == MarkdownViewMode.PREVIEW_ONLY,
                        onClick = { onViewModeChange(MarkdownViewMode.PREVIEW_ONLY) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownToolButton(
    icon: ImageVector? = null,
    label: String? = null,
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    Surface(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = tooltip,
                    modifier = Modifier.size(15.dp),
                    tint = Color(0xFFCBD5E1).copy(alpha = alpha)
                )
            } else if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = Color(0xFFCBD5E1).copy(alpha = alpha)
                )
            }
        }
    }
}

@Composable
private fun ViewModePillButton(
    icon: ImageVector,
    tooltip: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF94A3B8)

    Surface(
        modifier = Modifier
            .size(width = 30.dp, height = 26.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                modifier = Modifier.size(14.dp),
                tint = iconColor
            )
        }
    }
}

enum class MarkdownViewMode {
    EDIT_ONLY,
    SPLIT,
    PREVIEW_ONLY
}

@Composable
fun MarkdownViewer(
    markdown: String,
    modifier: Modifier = Modifier,
    onCheckboxToggle: ((lineIndex: Int, newChecked: Boolean) -> Unit)? = null,
    onWikilinkClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null
) {
    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val lines = markdown.lines()
            lines.forEachIndexed { index, line ->
                when {
                    line.startsWith("# ") -> {
                        Text(
                            text = line.removePrefix("# "),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    line.startsWith("## ") -> {
                        Text(
                            text = line.removePrefix("## "),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    line.startsWith("### ") -> {
                        Text(
                            text = line.removePrefix("### "),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    line.startsWith("- [ ] ") || line.startsWith("- [x] ") || line.startsWith("- [X] ") -> {
                        val isChecked = line.startsWith("- [x] ") || line.startsWith("- [X] ")
                        val taskText = line.substring(6)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onCheckboxToggle?.invoke(index, !isChecked)
                                }
                                .padding(vertical = 3.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    onCheckboxToggle?.invoke(index, checked)
                                },
                                modifier = Modifier.size(20.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = taskText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                    line.startsWith("- ") || line.startsWith("* ") -> {
                        Row(modifier = Modifier.padding(start = 6.dp), verticalAlignment = Alignment.Top) {
                            Text("• ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            val inlineAnnotated = renderInlineMarkdown(line.substring(2))
                            androidx.compose.foundation.text.ClickableText(
                                text = inlineAnnotated,
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                onClick = { offset ->
                                    inlineAnnotated.getStringAnnotations("WIKILINK", offset, offset).firstOrNull()?.let {
                                        onWikilinkClick?.invoke(it.item)
                                    }
                                    inlineAnnotated.getStringAnnotations("TAG", offset, offset).firstOrNull()?.let {
                                        onTagClick?.invoke(it.item)
                                    }
                                }
                            )
                        }
                    }
                    line.startsWith("> ") -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = renderInlineMarkdown(line.removePrefix("> ")),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    line.startsWith("```") -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = line.removePrefix("```").trim(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    line.isBlank() -> {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    else -> {
                        val inlineAnnotated = renderInlineMarkdown(line)
                        androidx.compose.foundation.text.ClickableText(
                            text = inlineAnnotated,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface),
                            onClick = { offset ->
                                inlineAnnotated.getStringAnnotations("WIKILINK", offset, offset).firstOrNull()?.let {
                                    onWikilinkClick?.invoke(it.item)
                                }
                                inlineAnnotated.getStringAnnotations("TAG", offset, offset).firstOrNull()?.let {
                                    onTagClick?.invoke(it.item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Satır içi formatları işler.
 */
fun renderInlineMarkdown(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x33888888),
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(" " + text.substring(i + 1, end) + " ")
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("[[", i) -> {
                val end = text.indexOf("]]", i + 2)
                if (end != -1) {
                    val linkTitle = text.substring(i + 2, end).trim()
                    pushStringAnnotation(tag = "WIKILINK", annotation = linkTitle)
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("🔗 $linkTitle")
                    }
                    pop()
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '#' && (i == 0 || text[i - 1].isWhitespace()) -> {
                val end = text.indexOfAny(charArrayOf(' ', '\t', '\n', ',', '.', ';', '!', '?', ')', ']'), i + 1)
                    .let { if (it == -1) text.length else it }
                if (end > i + 1) {
                    val tag = text.substring(i + 1, end)
                    pushStringAnnotation(tag = "TAG", annotation = tag)
                    withStyle(
                        SpanStyle(
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append("#$tag")
                    }
                    pop()
                    i = end
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
