package com.example.noteapp.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun RichTextToolbar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onAddImage: () -> Unit,
    onAddDrawing: () -> Unit,
    onRecordAudio: () -> Unit,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Kalın (Bold - **text**)
            IconButton(onClick = {
                applyMarkdownWrap(textFieldValue, "**", "**", onValueChange)
            }) {
                Icon(Icons.Default.FormatBold, contentDescription = "Kalın")
            }

            // İtalik (Italic - *text*)
            IconButton(onClick = {
                applyMarkdownWrap(textFieldValue, "*", "*", onValueChange)
            }) {
                Icon(Icons.Default.FormatItalic, contentDescription = "İtalik")
            }

            // Başlık (Heading - # )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "### ", onValueChange)
            }) {
                Icon(Icons.Default.Title, contentDescription = "Başlık")
            }

            // Madde İşareti (Bullet List - - )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "- ", onValueChange)
            }) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = "Liste")
            }

            // Görev / Checkbox (Todo - [ ] )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "- [ ] ", onValueChange)
            }) {
                Icon(Icons.Default.Checklist, contentDescription = "Görev Listesi")
            }

            // Alıntı (Quote - > )
            IconButton(onClick = {
                applyPrefixToLine(textFieldValue, "> ", onValueChange)
            }) {
                Icon(Icons.Default.FormatQuote, contentDescription = "Alıntı")
            }

            VerticalDivider(modifier = Modifier.height(28.dp).padding(vertical = 4.dp))

            // Görsel Ekle
            IconButton(onClick = onAddImage) {
                Icon(Icons.Default.Image, contentDescription = "Resim Ekle")
            }

            // Çizim Ekle
            IconButton(onClick = onAddDrawing) {
                Icon(Icons.Default.Brush, contentDescription = "Çizim Yap")
            }

            // Ses Kaydı
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

private fun applyMarkdownWrap(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    onValueChange: (TextFieldValue) -> Unit
) {
    val selection = value.selection
    val text = value.text

    if (selection.collapsed) {
        val newText = text.substring(0, selection.start) + prefix + suffix + text.substring(selection.end)
        val newSelection = TextRange(selection.start + prefix.length)
        onValueChange(TextFieldValue(newText, newSelection))
    } else {
        val selectedText = text.substring(selection.start, selection.end)
        val newText = text.substring(0, selection.start) + prefix + selectedText + suffix + text.substring(selection.end)
        val newSelection = TextRange(selection.start, selection.end + prefix.length + suffix.length)
        onValueChange(TextFieldValue(newText, newSelection))
    }
}

private fun applyPrefixToLine(
    value: TextFieldValue,
    prefix: String,
    onValueChange: (TextFieldValue) -> Unit
) {
    val selection = value.selection
    val text = value.text
    val lineStart = text.lastIndexOf('\n', selection.start - 1).let { if (it == -1) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    val newSelection = TextRange(selection.start + prefix.length)
    onValueChange(TextFieldValue(newText, newSelection))
}
