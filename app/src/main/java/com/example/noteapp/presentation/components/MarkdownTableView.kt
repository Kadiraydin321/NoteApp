package com.example.noteapp.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.noteapp.domain.util.MarkdownTable
import com.example.noteapp.domain.util.MarkdownTableParser
import com.example.noteapp.domain.util.TableAlignment

/**
 * Markdown Tablosunu Material 3 standartlarında yatay kaydırmalı,
 * şık ızgara ve düzenleme desteğiyle render eden Composable.
 */
@Composable
fun MarkdownTableView(
    table: MarkdownTable,
    contentColor: Color,
    fontSize: Float = 14f,
    isEditable: Boolean = false,
    onTableChange: (MarkdownTable) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var cellToEdit by remember { mutableStateOf<Triple<Int, Int, String>?>(null) } // rowIndex (-1 for header), colIndex, currentText

    // Hücre Düzenleme Diyaloğu
    cellToEdit?.let { (r, c, currentText) ->
        var editedText by remember { mutableStateOf(currentText) }
        AlertDialog(
            onDismissRequest = { cellToEdit = null },
            title = {
                Text(
                    text = if (r == -1) "Sütun Başlığını Düzenle" else "Hücreyi Düzenle (Satır ${r + 1}, Sütun ${c + 1})",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedTable = if (r == -1) {
                            MarkdownTableParser.updateHeader(table, c, editedText)
                        } else {
                            MarkdownTableParser.updateCell(table, r, c, editedText)
                        }
                        onTableChange(updatedTable)
                        cellToEdit = null
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { cellToEdit = null }) {
                    Text("İptal")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Yatay Kaydırılabilir Tablo Gövdesi
            val horizontalScrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(8.dp)
            ) {
                Column {
                    // 1. BAŞLIK SATIRI
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        table.headers.forEachIndexed { colIdx, header ->
                            val align = if (colIdx < table.alignments.size) table.alignments[colIdx] else TableAlignment.LEFT
                            val textAlign = when (align) {
                                TableAlignment.LEFT -> TextAlign.Start
                                TableAlignment.CENTER -> TextAlign.Center
                                TableAlignment.RIGHT -> TextAlign.End
                            }

                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 220.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .then(
                                        if (isEditable) Modifier.clickable {
                                            cellToEdit = Triple(-1, colIdx, header)
                                        } else Modifier
                                    ),
                                contentAlignment = when (align) {
                                    TableAlignment.LEFT -> Alignment.CenterStart
                                    TableAlignment.CENTER -> Alignment.Center
                                    TableAlignment.RIGHT -> Alignment.CenterEnd
                                }
                            ) {
                                Text(
                                    text = header.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (fontSize * 0.95f).sp,
                                        color = contentColor,
                                        textAlign = textAlign
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. VERİ SATIRLARI
                    table.rows.forEachIndexed { rowIdx, rowCells ->
                        val isEvenRow = rowIdx % 2 == 0
                        val rowBg = if (isEvenRow) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(rowBg)
                        ) {
                            rowCells.forEachIndexed { colIdx, cellValue ->
                                val align = if (colIdx < table.alignments.size) table.alignments[colIdx] else TableAlignment.LEFT
                                val textAlign = when (align) {
                                    TableAlignment.LEFT -> TextAlign.Start
                                    TableAlignment.CENTER -> TextAlign.Center
                                    TableAlignment.RIGHT -> TextAlign.End
                                }

                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 100.dp, max = 220.dp)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .then(
                                            if (isEditable) Modifier.clickable {
                                                cellToEdit = Triple(rowIdx, colIdx, cellValue)
                                            } else Modifier
                                        ),
                                    contentAlignment = when (align) {
                                        TableAlignment.LEFT -> Alignment.CenterStart
                                        TableAlignment.CENTER -> Alignment.Center
                                        TableAlignment.RIGHT -> Alignment.CenterEnd
                                    }
                                ) {
                                    Text(
                                        text = cellValue.ifBlank { " " },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = (fontSize * 0.9f).sp,
                                            color = contentColor,
                                            textAlign = textAlign
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Düzenleme Butonları (isEditable aktifse)
            if (isEditable) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val updated = MarkdownTableParser.addRow(table)
                            onTableChange(updated)
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Satır Ekle", style = MaterialTheme.typography.labelSmall)
                    }

                    TextButton(
                        onClick = {
                            val updated = MarkdownTableParser.addColumn(table, "Sütun ${table.headers.size + 1}")
                            onTableChange(updated)
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sütun Ekle", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (table.rows.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val updated = MarkdownTableParser.deleteRow(table, table.rows.size - 1)
                                onTableChange(updated)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Satır Sil", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
