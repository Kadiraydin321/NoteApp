package com.example.noteapp.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RichTextToolbar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusRequest: () -> Unit = {},
    hideMarkdownTokens: Boolean = true,
    onToggleHideMarkdownTokens: () -> Unit = {},
    onAddImage: () -> Unit,
    onAddDrawing: () -> Unit,
    onRecordAudio: () -> Unit,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 0. Hızlı İşaret Gizleme / Gösterme Anahtarı (Acil durum erişimi)
            IconButton(
                onClick = onToggleHideMarkdownTokens,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (!hideMarkdownTokens) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent
                )
            ) {
                Icon(
                    imageVector = if (hideMarkdownTokens) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (hideMarkdownTokens) "İşaretler Gizli (Temiz Görünüm)" else "İşaretler Görünür",
                    tint = if (!hideMarkdownTokens) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

            // 1. Kalın (Bold - **text**)
            IconButton(onClick = {
                applyMarkdownWrap(textFieldValue, "**", "**", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.FormatBold, contentDescription = "Kalın (Bold)")
            }

            // 2. İtalik (Italic - *text*)
            IconButton(onClick = {
                applyMarkdownWrap(textFieldValue, "*", "*", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.FormatItalic, contentDescription = "İtalik (Italic)")
            }

            // 3. Üstü Çizili (Strikethrough - ~~text~~)
            IconButton(onClick = {
                applyMarkdownWrap(textFieldValue, "~~", "~~", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.FormatStrikethrough, contentDescription = "Üstü Çizili")
            }

            // 4. Fosforlu Vurgu (Highlight - ==text==)
            IconButton(onClick = {
                applyMarkdownWrap(textFieldValue, "==", "==", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.BorderColor, contentDescription = "Vurgu / Fosforlu", tint = Color(0xFFFBC02D))
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // 5. Başlık (Heading - # )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "### ", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.Title, contentDescription = "Başlık")
            }

            // 6. Kontrol Kutusu / Yapılacak (Checkbox - - [ ] )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "- [ ] ", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.Checklist, contentDescription = "Yapılacak Maddesi")
            }

            // 7. Madde İşareti (Bullet List - • )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "• ", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Madde İşaretli Liste")
            }

            // 8. Numaralı Liste (Numbered List - 1. )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "1. ", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = "Numaralı Liste")
            }

            // 9. Alıntı (Quote - > )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "> ", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.FormatQuote, contentDescription = "Alıntı")
            }

            // 10. Zaman Damgası Ekle (Tarih ve Saat)
            IconButton(onClick = {
                val now = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                insertTextAtCursor(textFieldValue, "[$now] ", onValueChange, onFocusRequest)
            }) {
                Icon(Icons.Default.AccessTime, contentDescription = "Tarih & Saat Damgası Ekle")
            }

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

            // 11. Görsel Ekle
            IconButton(onClick = onAddImage) {
                Icon(Icons.Default.Image, contentDescription = "Resim Ekle")
            }

            // 12. Çizim Ekle
            IconButton(onClick = onAddDrawing) {
                Icon(Icons.Default.Brush, contentDescription = "Çizim Yap")
            }

            // 13. Ses Kaydı
            IconButton(
                onClick = onRecordAudio,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Kaydı Durdur" else "Ses Kaydet"
                )
            }
        }
    }
}

/**
 * Seçili veya imlecin üzerinde bulunduğu metne akıllı markdown biçimlendirmesi uygular veya geri alır (toggle).
 * Üst üste tıklandığında işaretleri yığmak yerine (*****) biçimlendirmeyi açıp kapatır.
 */
internal fun applyMarkdownWrap(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusRequest: () -> Unit
) {
    val selection = value.selection
    val text = value.text
    val safeStart = selection.min.coerceIn(0, text.length)
    val safeEnd = selection.max.coerceIn(0, text.length)

    // A. SEÇİM VARSA (Kullanıcı bir metin aralığını seçtiyse)
    if (!selection.collapsed) {
        val selectedText = text.substring(safeStart, safeEnd)

        // 1. Seçim kendi içinde bu biçimlendirmeye sahip mi? (Örn: Seçim "**metin**" ise)
        if (hasFormattingInside(selectedText, prefix, suffix)) {
            val unwrapped = unwrapFormattingInside(selectedText, prefix, suffix)
            val newText = text.substring(0, safeStart) + unwrapped + text.substring(safeEnd)
            val newSelection = TextRange(safeStart, safeStart + unwrapped.length)
            onValueChange(TextFieldValue(newText, newSelection))
            onFocusRequest()
            return
        }

        // 2. Seçimin hemen dışında bu biçimlendirme var mı? (Örn: Seçim "metin", etrafında "**" var)
        if (hasFormattingOutside(text, safeStart, safeEnd, prefix, suffix)) {
            val (newText, newSelection) = unwrapFormattingOutside(text, safeStart, safeEnd, prefix, suffix)
            onValueChange(TextFieldValue(newText, newSelection))
            onFocusRequest()
            return
        }

        // 3. Seçim biçimlendirilmemiş -> Biçimlendirmeyi sar
        val (newText, newSelection) = wrapSelection(text, safeStart, safeEnd, prefix, suffix)
        onValueChange(TextFieldValue(newText, newSelection))
        onFocusRequest()
        return
    }

    // B. SEÇİM YOKSA / İMLEÇ SABİTSE (Collapsed)
    val pos = safeStart

    // 1. İmleç tam olarak boş etiketler arasında mı? (Örn: "****" veya "~~~~" arasında)
    if (isBetweenEmptyDelimiters(text, pos, prefix, suffix)) {
        val (newText, newSelection) = removeEmptyDelimiters(text, pos, prefix, suffix)
        onValueChange(TextFieldValue(newText, newSelection))
        onFocusRequest()
        return
    }

    // 2. İmleç satır içinde bu biçimlendirmeye sahip bir span üzerinde mi?
    val span = findFormattingSpanAtCursor(text, pos, prefix, suffix)
    if (span != null) {
        val (newText, newSelection) = toggleFormattingSpan(text, span, pos, prefix, suffix)
        onValueChange(TextFieldValue(newText, newSelection))
        onFocusRequest()
        return
    }

    // 3. İmleç bir kelimenin üzerinde mi?
    val wordRange = findWordAt(text, pos)
    if (wordRange != null) {
        val wordStart = wordRange.first
        val wordEnd = wordRange.last + 1

        // Kelimenin hemen dışında zaten bu biçimlendirme var mı?
        if (hasFormattingOutside(text, wordStart, wordEnd, prefix, suffix)) {
            val (newText, newSelection) = unwrapFormattingOutside(text, wordStart, wordEnd, prefix, suffix)
            onValueChange(TextFieldValue(newText, newSelection))
            onFocusRequest()
            return
        }

        // Kelimeyi sar
        val (newText, newSelection) = wrapSelection(text, wordStart, wordEnd, prefix, suffix)
        onValueChange(TextFieldValue(newText, newSelection))
        onFocusRequest()
        return
    }

    // 4. Boşlukta veya satır başında -> Boş etiket çifti yerleştir ve imleci ortaya koy
    val newText = text.substring(0, pos) + prefix + suffix + text.substring(pos)
    val newSelection = TextRange(pos + prefix.length)
    onValueChange(TextFieldValue(newText, newSelection))
    onFocusRequest()
}

private fun hasFormattingInside(text: String, prefix: String, suffix: String): Boolean {
    if (prefix == "**") {
        return (text.startsWith("***") && text.endsWith("***") && text.length >= 6) ||
                (text.startsWith("**") && text.endsWith("**") && text.length >= 4)
    }
    if (prefix == "*") {
        if (text.startsWith("***") && text.endsWith("***") && text.length >= 6) return true
        if (text.startsWith("*") && text.endsWith("*") && text.length >= 2) {
            val isStartDouble = text.startsWith("**")
            val isEndDouble = text.endsWith("**")
            return !isStartDouble && !isEndDouble
        }
        return false
    }
    return text.startsWith(prefix) && text.endsWith(suffix) && text.length >= prefix.length + suffix.length
}

private fun unwrapFormattingInside(text: String, prefix: String, suffix: String): String {
    if (prefix == "**") {
        if (text.startsWith("***") && text.endsWith("***") && text.length >= 6) {
            return "*" + text.substring(3, text.length - 3) + "*"
        }
        if (text.startsWith("**") && text.endsWith("**") && text.length >= 4) {
            return text.substring(2, text.length - 2)
        }
    }
    if (prefix == "*") {
        if (text.startsWith("***") && text.endsWith("***") && text.length >= 6) {
            return "**" + text.substring(3, text.length - 3) + "**"
        }
        if (text.startsWith("*") && text.endsWith("*") && text.length >= 2) {
            return text.substring(1, text.length - 1)
        }
    }
    if (text.length >= prefix.length + suffix.length) {
        return text.substring(prefix.length, text.length - suffix.length)
    }
    return text
}

private fun hasFormattingOutside(text: String, start: Int, end: Int, prefix: String, suffix: String): Boolean {
    if (prefix == "**") {
        if (start >= 3 && end + 3 <= text.length &&
            text.substring(start - 3, start) == "***" &&
            text.substring(end, end + 3) == "***"
        ) return true

        if (start >= 2 && end + 2 <= text.length &&
            text.substring(start - 2, start) == "**" &&
            text.substring(end, end + 2) == "**"
        ) {
            val hasExtraBefore = start >= 3 && text[start - 3] == '*'
            val hasExtraAfter = end + 3 <= text.length && text[end + 2] == '*'
            return !hasExtraBefore && !hasExtraAfter
        }
        return false
    }

    if (prefix == "*") {
        if (start >= 3 && end + 3 <= text.length &&
            text.substring(start - 3, start) == "***" &&
            text.substring(end, end + 3) == "***"
        ) return true

        if (start >= 1 && end + 1 <= text.length &&
            text[start - 1] == '*' && text[end] == '*'
        ) {
            val hasExtraBefore = start >= 2 && text[start - 2] == '*'
            val hasExtraAfter = end + 2 <= text.length && text[end + 1] == '*'
            return !hasExtraBefore && !hasExtraAfter
        }
        return false
    }

    return start >= prefix.length && end + suffix.length <= text.length &&
            text.substring(start - prefix.length, start) == prefix &&
            text.substring(end, end + suffix.length) == suffix
}

private fun unwrapFormattingOutside(
    text: String,
    start: Int,
    end: Int,
    prefix: String,
    suffix: String
): Pair<String, TextRange> {
    if (prefix == "**") {
        if (start >= 3 && end + 3 <= text.length &&
            text.substring(start - 3, start) == "***" &&
            text.substring(end, end + 3) == "***"
        ) {
            val newText = text.substring(0, start - 2) + text.substring(start, end) + text.substring(end + 2)
            val newSelection = TextRange(start - 2, end - 2)
            return newText to newSelection
        }
        val newText = text.substring(0, start - 2) + text.substring(start, end) + text.substring(end + 2)
        val newSelection = TextRange(start - 2, end - 2)
        return newText to newSelection
    }

    if (prefix == "*") {
        if (start >= 3 && end + 3 <= text.length &&
            text.substring(start - 3, start) == "***" &&
            text.substring(end, end + 3) == "***"
        ) {
            val newText = text.substring(0, start - 1) + text.substring(start, end) + text.substring(end + 1)
            val newSelection = TextRange(start - 1, end - 1)
            return newText to newSelection
        }
        val newText = text.substring(0, start - 1) + text.substring(start, end) + text.substring(end + 1)
        val newSelection = TextRange(start - 1, end - 1)
        return newText to newSelection
    }

    val newText = text.substring(0, start - prefix.length) + text.substring(start, end) + text.substring(end + suffix.length)
    val newSelection = TextRange(start - prefix.length, end - prefix.length)
    return newText to newSelection
}

private fun wrapSelection(
    text: String,
    start: Int,
    end: Int,
    prefix: String,
    suffix: String
): Pair<String, TextRange> {
    val selectedText = text.substring(start, end)

    if (prefix == "**") {
        if (start >= 1 && end + 1 <= text.length &&
            text[start - 1] == '*' && text[end] == '*' &&
            (start < 2 || text[start - 2] != '*') && (end + 1 >= text.length || text[end + 1] != '*')
        ) {
            val newText = text.substring(0, start - 1) + "***" + selectedText + "***" + text.substring(end + 1)
            val newSelection = TextRange(start + 2, end + 2)
            return newText to newSelection
        }
    }

    if (prefix == "*") {
        if (start >= 2 && end + 2 <= text.length &&
            text.substring(start - 2, start) == "**" && text.substring(end, end + 2) == "**" &&
            (start < 3 || text[start - 3] != '*') && (end + 2 >= text.length || text[end + 2] != '*')
        ) {
            val newText = text.substring(0, start - 2) + "***" + selectedText + "***" + text.substring(end + 2)
            val newSelection = TextRange(start + 1, end + 1)
            return newText to newSelection
        }
    }

    val newText = text.substring(0, start) + prefix + selectedText + suffix + text.substring(end)
    val newSelection = TextRange(start + prefix.length, end + prefix.length)
    return newText to newSelection
}

private fun isBetweenEmptyDelimiters(text: String, pos: Int, prefix: String, suffix: String): Boolean {
    if (prefix == "**") {
        return pos >= 2 && pos + 2 <= text.length && text.substring(pos - 2, pos + 2) == "****"
    }
    if (prefix == "*") {
        if (pos >= 1 && pos + 1 <= text.length && text.substring(pos - 1, pos + 1) == "**") {
            val isPartOfEmptyBold = pos >= 2 && pos + 2 <= text.length && text.substring(pos - 2, pos + 2) == "****"
            return !isPartOfEmptyBold
        }
        return false
    }
    return pos >= prefix.length && pos + suffix.length <= text.length &&
            text.substring(pos - prefix.length, pos + suffix.length) == (prefix + suffix)
}

private fun removeEmptyDelimiters(
    text: String,
    pos: Int,
    prefix: String,
    suffix: String
): Pair<String, TextRange> {
    val newText = text.substring(0, pos - prefix.length) + text.substring(pos + suffix.length)
    val newSelection = TextRange(pos - prefix.length)
    return newText to newSelection
}

private data class MatchedSpan(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
    val leadingCount: Int,
    val trailingCount: Int
)

private fun findFormattingSpanAtCursor(
    text: String,
    pos: Int,
    prefix: String,
    suffix: String
): MatchedSpan? {
    val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', pos).let { if (it == -1) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    val relPos = pos - lineStart

    if (prefix == "**" || prefix == "*") {
        val starRegex = Regex("(?<!\\\\)(\\*{1,3})(?!\\s)([^\n]+?)(?<!\\s)(\\*{1,3})")
        for (match in starRegex.findAll(line)) {
            val range = match.range
            if (relPos in range.first..(range.last + 1)) {
                val leading = match.groups[1]!!.value.length
                val trailing = match.groups[3]!!.value.length
                val openStart = lineStart + range.first
                val openEnd = openStart + leading
                val closeEnd = lineStart + range.last + 1
                val closeStart = closeEnd - trailing

                if (prefix == "**") {
                    if (leading >= 2 && trailing >= 2) {
                        return MatchedSpan(openStart, openEnd, closeStart, closeEnd, leading, trailing)
                    }
                } else {
                    if ((leading == 3 && trailing == 3) || (leading == 1 && trailing == 1) || (leading == 2 && trailing == 2)) {
                        return MatchedSpan(openStart, openEnd, closeStart, closeEnd, leading, trailing)
                    }
                }
            }
        }
        return null
    }

    if (prefix == "~~" || prefix == "==") {
        val delim = Regex.escape(prefix)
        val regex = Regex("$delim([^$delim\\n]+?)$delim")
        for (match in regex.findAll(line)) {
            val range = match.range
            if (relPos in range.first..(range.last + 1)) {
                val openStart = lineStart + range.first
                val openEnd = openStart + prefix.length
                val closeEnd = lineStart + range.last + 1
                val closeStart = closeEnd - suffix.length
                return MatchedSpan(openStart, openEnd, closeStart, closeEnd, prefix.length, suffix.length)
            }
        }
    }

    return null
}

private fun toggleFormattingSpan(
    text: String,
    span: MatchedSpan,
    pos: Int,
    prefix: String,
    suffix: String
): Pair<String, TextRange> {
    val content = text.substring(span.openEnd, span.closeStart)

    if (prefix == "**") {
        val newLeading = "*".repeat((span.leadingCount - 2).coerceAtLeast(0))
        val newTrailing = "*".repeat((span.trailingCount - 2).coerceAtLeast(0))
        val newSpan = newLeading + content + newTrailing
        val newText = text.substring(0, span.openStart) + newSpan + text.substring(span.closeEnd)

        val removedBeforePos = when {
            pos <= span.openStart -> 0
            pos <= span.openEnd -> (pos - span.openStart).coerceAtMost(2)
            else -> 2
        }
        val newPos = (pos - removedBeforePos).coerceIn(0, newText.length)
        return newText to TextRange(newPos)
    }

    if (prefix == "*") {
        if (span.leadingCount == 3 && span.trailingCount == 3) {
            val newSpan = "**" + content + "**"
            val newText = text.substring(0, span.openStart) + newSpan + text.substring(span.closeEnd)
            val removedBeforePos = if (pos > span.openStart) 1 else 0
            val newPos = (pos - removedBeforePos).coerceIn(0, newText.length)
            return newText to TextRange(newPos)
        } else if (span.leadingCount == 1 && span.trailingCount == 1) {
            val newSpan = content
            val newText = text.substring(0, span.openStart) + newSpan + text.substring(span.closeEnd)
            val removedBeforePos = if (pos > span.openStart) 1 else 0
            val newPos = (pos - removedBeforePos).coerceIn(0, newText.length)
            return newText to TextRange(newPos)
        } else if (span.leadingCount == 2 && span.trailingCount == 2) {
            val newSpan = "***" + content + "***"
            val newText = text.substring(0, span.openStart) + newSpan + text.substring(span.closeEnd)
            val addedBeforePos = if (pos > span.openStart) 1 else 0
            val newPos = (pos + addedBeforePos).coerceIn(0, newText.length)
            return newText to TextRange(newPos)
        }
    }

    val newSpan = content
    val newText = text.substring(0, span.openStart) + newSpan + text.substring(span.closeEnd)
    val removedBeforePos = when {
        pos <= span.openStart -> 0
        pos <= span.openEnd -> (pos - span.openStart).coerceAtMost(prefix.length)
        else -> prefix.length
    }
    val newPos = (pos - removedBeforePos).coerceIn(0, newText.length)
    return newText to TextRange(newPos)
}

private fun findWordAt(text: String, pos: Int): IntRange? {
    if (text.isEmpty()) return null
    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    val targetPos = when {
        pos < text.length && isWordChar(text[pos]) -> pos
        pos > 0 && isWordChar(text[pos - 1]) -> pos - 1
        else -> return null
    }

    var start = targetPos
    while (start > 0 && isWordChar(text[start - 1])) {
        start--
    }

    var end = targetPos
    while (end < text.length && isWordChar(text[end])) {
        end++
    }

    return if (start < end) start until end else null
}

/**
 * İmlecin bulunduğu satırın başına prefix ekler veya varsa kaldırır.
 */
internal fun applyPrefixToLine(
    value: TextFieldValue,
    prefix: String,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusRequest: () -> Unit
) {
    val selection = value.selection
    val text = value.text
    val safePos = selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (safePos - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
    val currentLine = text.substring(lineStart, lineEnd)

    val (newText, newSelection) = if (currentLine.startsWith(prefix)) {
        // Kaldır
        val updated = text.substring(0, lineStart) + currentLine.removePrefix(prefix) + text.substring(lineEnd)
        val sel = TextRange((selection.start - prefix.length).coerceAtLeast(lineStart))
        updated to sel
    } else {
        // Ekle
        val updated = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        val sel = TextRange(selection.start + prefix.length)
        updated to sel
    }

    onValueChange(TextFieldValue(newText, newSelection))
    onFocusRequest()
}

/**
 * İmlecin bulunduğu konuma metin ekler.
 */
internal fun insertTextAtCursor(
    value: TextFieldValue,
    inserted: String,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusRequest: () -> Unit
) {
    val selection = value.selection
    val text = value.text
    val safeStart = selection.min.coerceIn(0, text.length)
    val safeEnd = selection.max.coerceIn(0, text.length)
    val newText = text.substring(0, safeStart) + inserted + text.substring(safeEnd)
    val newSelection = TextRange(safeStart + inserted.length)
    onValueChange(TextFieldValue(newText, newSelection))
    onFocusRequest()
}
