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
 * Seçili metnin başına ve sonuna markdown formatı uygular, imleç ve seçimi korur.
 */
private fun applyMarkdownWrap(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusRequest: () -> Unit
) {
    val selection = value.selection
    val text = value.text

    if (selection.collapsed) {
        val safeStart = selection.start.coerceIn(0, text.length)
        val newText = text.substring(0, safeStart) + prefix + suffix + text.substring(safeStart)
        val newSelection = TextRange(safeStart + prefix.length)
        onValueChange(TextFieldValue(newText, newSelection))
    } else {
        val safeStart = selection.min.coerceIn(0, text.length)
        val safeEnd = selection.max.coerceIn(0, text.length)
        val selectedText = text.substring(safeStart, safeEnd)

        // Eğer seçilen metin zaten prefix ve suffix ile sarılıysa, kaldır (toggle off)
        if (selectedText.startsWith(prefix) && selectedText.endsWith(suffix) && selectedText.length >= prefix.length + suffix.length) {
            val unwrapped = selectedText.substring(prefix.length, selectedText.length - suffix.length)
            val newText = text.substring(0, safeStart) + unwrapped + text.substring(safeEnd)
            val newSelection = TextRange(safeStart, safeStart + unwrapped.length)
            onValueChange(TextFieldValue(newText, newSelection))
        } else {
            // Sar (toggle on)
            val newText = text.substring(0, safeStart) + prefix + selectedText + suffix + text.substring(safeEnd)
            val newSelection = TextRange(safeStart + prefix.length, safeEnd + prefix.length)
            onValueChange(TextFieldValue(newText, newSelection))
        }
    }
    onFocusRequest()
}

/**
 * İmlecin bulunduğu satırın başına prefix ekler veya varsa kaldırır.
 */
private fun applyPrefixToLine(
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
private fun insertTextAtCursor(
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
