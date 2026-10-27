package com.example.noteapp.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.util.ChecklistStats
import com.example.noteapp.domain.util.WordGoalCalculator
import com.example.noteapp.presentation.detail.NoteColors
import java.io.File

/**
 * Editör alt araç kategorileri.
 */
enum class NoteToolCategory {
    NONE,
    MEDIA,
    FORMAT,
    COLOR,
    CATEGORY
}

/**
 * İmleç konumunda aktif olan metin biçimlendirme stilleri.
 */
data class ActiveEditorStyles(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isHighlight: Boolean = false,
    val isList: Boolean = false,
    val isNumberedList: Boolean = false,
    val isCode: Boolean = false,
    val isChecklist: Boolean = false,
    val isRecordingAudio: Boolean = false
)

/**
 * İmlecin bulunduğu satırdaki stil ve işaretçileri hızlıca tespit eder.
 */
fun detectActiveEditorStyles(
    text: String,
    cursor: Int,
    isRecording: Boolean = false
): ActiveEditorStyles {
    if (text.isEmpty() || cursor < 0) {
        return ActiveEditorStyles(isRecordingAudio = isRecording)
    }

    val safeCursor = cursor.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (safeCursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', safeCursor).let { if (it == -1) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    val relCursor = safeCursor - lineStart
    val trimmed = line.trimStart()

    // 1. Liste Tespiti
    val isList = trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ")
    val isNumberedList = Regex("^\\d+\\.\\s").containsMatchIn(trimmed)

    // 2. Onay Kutusu (Checklist)
    val isChecklist = trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]") ||
            trimmed.startsWith("[ ]") || trimmed.startsWith("[x]")

    // 3. Kalın (Bold)
    val isBold = isInsideDelimiters(line, relCursor, "**")

    // 4. İtalik (Italic)
    val isItalic = !isBold && isInsideItalic(line, relCursor)

    // 5. Üstü Çizili (Strikethrough)
    val isStrikethrough = isInsideDelimiters(line, relCursor, "~~")

    // 6. Vurgu (Highlight)
    val isHighlight = isInsideDelimiters(line, relCursor, "==")

    // 7. Kod (Inline `code` veya blok ```)
    val isInlineCode = isInsideDelimiters(line, relCursor, "`")
    val fenceCount = Regex("```").findAll(text.substring(0, safeCursor)).count()
    val isCode = isInlineCode || (fenceCount % 2 == 1)

    return ActiveEditorStyles(
        isBold = isBold,
        isItalic = isItalic,
        isStrikethrough = isStrikethrough,
        isHighlight = isHighlight,
        isList = isList,
        isNumberedList = isNumberedList,
        isCode = isCode,
        isChecklist = isChecklist,
        isRecordingAudio = isRecording
    )
}

private fun isInsideDelimiters(line: String, relPos: Int, delim: String): Boolean {
    val escaped = Regex.escape(delim)
    val regex = Regex("$escaped([^$escaped\n]+?)$escaped")
    for (match in regex.findAll(line)) {
        if (relPos in match.range.first..(match.range.last + 1)) {
            return true
        }
    }
    return false
}

private fun isInsideItalic(line: String, relPos: Int): Boolean {
    val starRegex = Regex("(?<!\\*)(\\*)(?!\\*)([^\n*]+?)(?<!\\*)(\\*)(?!\\*)")
    for (match in starRegex.findAll(line)) {
        if (relPos in match.range.first..(match.range.last + 1)) {
            return true
        }
    }
    return false
}

/**
 * Modern Material Design 3 Alt Kapsül Dock ve Yüzen Araç Paneli (BottomEditorBar).
 */
@Composable
fun BottomEditorBar(
    textFieldValue: TextFieldValue,
    activeToolCategory: NoteToolCategory,
    onToolCategoryChange: (NoteToolCategory) -> Unit,
    hasChecklistItems: Boolean,
    onToggleChecklist: () -> Unit,
    activeStyles: ActiveEditorStyles,
    // Biçimlendirme Aksiyonları
    onApplyWrap: (prefix: String, suffix: String) -> Unit,
    onApplyPrefix: (prefix: String) -> Unit,
    onApplyCodeBlock: () -> Unit,
    onAddImage: () -> Unit,
    onAddDrawing: () -> Unit,
    onToggleAudioRecording: () -> Unit,
    isRecordingAudio: Boolean,
    // Yazı Boyutu & Renk
    noteFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    selectedColor: Int,
    onColorChange: (Int) -> Unit,
    selectedBackgroundImage: String?,
    onPickBackgroundImage: () -> Unit,
    onRemoveBackgroundImage: () -> Unit,
    // Kategori
    selectedCategoryId: Long?,
    categories: List<Category>,
    onCategoryChange: (Long?) -> Unit,
    onAddNewCategory: () -> Unit,
    // Ekstra Fonksiyonlar
    onOpenSnippetPicker: () -> Unit,
    onOpenMathFormula: () -> Unit,
    onInsertTimestamp: () -> Unit,
    onInsertHorizontalRule: () -> Unit,
    // İstatistik & Çipler
    wordCount: Int,
    readingTime: Int,
    checklistStats: ChecklistStats?,
    wordGoal: Int?,
    onOpenStatsDialog: () -> Unit,
    onOpenChecklistHub: () -> Unit,
    onOpenWordGoalDialog: () -> Unit,
    onRemoveWordGoal: () -> Unit,
    topContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isAppDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dockSurfaceColor = if (isAppDark) Color(0xFF22252A) else Color(0xFFFFFFFF)
    val dockBorderColor = if (isAppDark) Color(0x35FFFFFF) else Color(0x18000000)
    val panelContentColor = if (isAppDark) Color(0xFFF0F1F5) else Color(0xFF191C20)
    val dockIconColor = if (isAppDark) Color(0xFFB0B7C3) else Color(0xFF49454F)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (topContent != null) {
            topContent()
        }

        // 1. Kategoriye Göre Açılan Yüzen Araç Kutusu (Secondary Floating Panel)
        AnimatedVisibility(
            visible = activeToolCategory != NoteToolCategory.NONE,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut()
        ) {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                color = dockSurfaceColor,
                contentColor = panelContentColor,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, dockBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Panel Başlığı ve Kapat Butonu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (activeToolCategory) {
                                NoteToolCategory.MEDIA -> "Medya ve Eklentiler"
                                NoteToolCategory.FORMAT -> "Metin Biçimlendirme"
                                NoteToolCategory.COLOR -> "Sayfa Arka Plan Rengi"
                                NoteToolCategory.CATEGORY -> "Not Kategorisi"
                                NoteToolCategory.NONE -> ""
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        IconButton(
                            onClick = { onToolCategoryChange(NoteToolCategory.NONE) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    when (activeToolCategory) {
                        NoteToolCategory.MEDIA -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Görsel Ekle
                                OutlinedButton(
                                    onClick = onAddImage,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resim", style = MaterialTheme.typography.labelMedium)
                                }

                                // 2. Çizim Yap
                                OutlinedButton(
                                    onClick = onAddDrawing,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Çizim", style = MaterialTheme.typography.labelMedium)
                                }

                                // 3. Ses Kaydet
                                Button(
                                    onClick = onToggleAudioRecording,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isRecordingAudio) "Durdur" else "Ses", style = MaterialTheme.typography.labelMedium)
                                }

                                // 4. Zaman Damgası
                                FilledTonalIconButton(
                                    onClick = onInsertTimestamp,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = "Zaman Damgası", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        NoteToolCategory.FORMAT -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Satır: Yazı Boyutu Kontrolü (A- / [16 sp] / A+)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FormatSize,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Yazı Boyutu",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = panelContentColor
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                val next = (noteFontSize - 1f).coerceIn(12f, 24f)
                                                onFontSizeChange(next)
                                            },
                                            enabled = noteFontSize > 12f,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Text("A-", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAppDark) Color(0x33FFFFFF) else Color(0x18000000)
                                        ) {
                                            Text(
                                                text = "${noteFontSize.toInt()} sp",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = panelContentColor,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }

                                        FilledTonalIconButton(
                                            onClick = {
                                                val next = (noteFontSize + 1f).coerceIn(12f, 24f)
                                                onFontSizeChange(next)
                                            },
                                            enabled = noteFontSize < 24f,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Text("A+", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // 2. Satır: Biçimlendirme Butonları (Aktif Stil Göstergeli)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Kalın (Bold)
                                    IconButton(
                                        onClick = { onApplyWrap("**", "**") },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (activeStyles.isBold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (activeStyles.isBold) MaterialTheme.colorScheme.primary else panelContentColor
                                        )
                                    ) {
                                        Icon(Icons.Default.FormatBold, contentDescription = "Kalın", modifier = Modifier.size(20.dp))
                                    }

                                    // İtalik (Italic)
                                    IconButton(
                                        onClick = { onApplyWrap("*", "*") },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (activeStyles.isItalic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (activeStyles.isItalic) MaterialTheme.colorScheme.primary else panelContentColor
                                        )
                                    ) {
                                        Icon(Icons.Default.FormatItalic, contentDescription = "İtalik", modifier = Modifier.size(20.dp))
                                    }

                                    // Üstü Çizili
                                    IconButton(
                                        onClick = { onApplyWrap("~~", "~~") },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (activeStyles.isStrikethrough) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (activeStyles.isStrikethrough) MaterialTheme.colorScheme.primary else panelContentColor
                                        )
                                    ) {
                                        Icon(Icons.Default.FormatStrikethrough, contentDescription = "Üstü Çizili", modifier = Modifier.size(20.dp))
                                    }

                                    // Fosforlu Vurgu
                                    IconButton(
                                        onClick = { onApplyWrap("==", "==") },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (activeStyles.isHighlight) Color(0x33FBC02D) else Color.Transparent,
                                            contentColor = Color(0xFFFBC02D)
                                        )
                                    ) {
                                        Icon(Icons.Default.BorderColor, contentDescription = "Vurgu", modifier = Modifier.size(20.dp))
                                    }

                                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                                    // Kod Bloku (```)
                                    IconButton(
                                        onClick = onApplyCodeBlock,
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (activeStyles.isCode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (activeStyles.isCode) MaterialTheme.colorScheme.primary else panelContentColor
                                        )
                                    ) {
                                        Icon(Icons.Default.Code, contentDescription = "Kod Bloku", modifier = Modifier.size(20.dp))
                                    }

                                    // Başlık H1, H2, H3
                                    FilledTonalButton(
                                        onClick = { onApplyPrefix("# ") },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("H1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }

                                    FilledTonalButton(
                                        onClick = { onApplyPrefix("## ") },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("H2", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }

                                    FilledTonalButton(
                                        onClick = { onApplyPrefix("### ") },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("H3", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }

                                    // Ayraç Çizgi (---)
                                    IconButton(
                                        onClick = onInsertHorizontalRule,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.HorizontalRule, contentDescription = "Ayraç Çizgi", modifier = Modifier.size(20.dp))
                                    }

                                    // Madde İşareti (Liste)
                                    IconButton(
                                        onClick = { onApplyPrefix("• ") },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (activeStyles.isList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (activeStyles.isList) MaterialTheme.colorScheme.primary else panelContentColor
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Madde", modifier = Modifier.size(20.dp))
                                    }

                                    // Numaralı Liste
                                    IconButton(
                                        onClick = { onApplyPrefix("1. ") },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (activeStyles.isNumberedList) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (activeStyles.isNumberedList) MaterialTheme.colorScheme.primary else panelContentColor
                                        )
                                    ) {
                                        Icon(Icons.Default.FormatListNumbered, contentDescription = "Numaralı", modifier = Modifier.size(20.dp))
                                    }

                                    // Alıntı
                                    IconButton(
                                        onClick = { onApplyPrefix("> ") },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.FormatQuote, contentDescription = "Alıntı", modifier = Modifier.size(20.dp))
                                    }

                                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                                    // Çift Yönlü Bağlantı ([[ ]])
                                    IconButton(
                                        onClick = { onApplyWrap("[[", "]]") },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = "Bağlantı [[ ]]", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }

                                    // Bilgi Kutusu (> [!NOTE])
                                    IconButton(
                                        onClick = { onApplyPrefix("> [!NOTE] ") },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = "Bilgi Kutusu", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    }

                                    // Hızlı Metin Parçacıkları (⚡)
                                    IconButton(
                                        onClick = onOpenSnippetPicker,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = "Metin Parçacıkları", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }

                                    // Matematiksel Formül (∑)
                                    IconButton(
                                        onClick = onOpenMathFormula,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Functions, contentDescription = "Matematik Formülü", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        NoteToolCategory.COLOR -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(NoteColors) { color ->
                                        val argb = color.toArgb()
                                        val isSelected = (selectedColor == argb) || (selectedColor == 0 && color == Color.Transparent)

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (color == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else color)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                                .clickable { onColorChange(argb) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (color == Color.Transparent) {
                                                Icon(
                                                    Icons.Default.FormatColorReset,
                                                    contentDescription = "Varsayılan",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (color.luminance() < 0.5f) Color.White else Color.Black
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onPickBackgroundImage,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (selectedBackgroundImage != null) "Arka Planı Değiştir" else "Arka Plan Resmi Seç",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }

                                        if (!selectedBackgroundImage.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = File(selectedBackgroundImage),
                                                contentDescription = "Arka Plan Önizleme",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }

                                    if (!selectedBackgroundImage.isNullOrEmpty()) {
                                        TextButton(
                                            onClick = onRemoveBackgroundImage,
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Kaldır", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        NoteToolCategory.CATEGORY -> {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    ElevatedAssistChip(
                                        onClick = onAddNewCategory,
                                        label = { Text("Yeni") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = selectedCategoryId == null,
                                        onClick = { onCategoryChange(null) },
                                        label = { Text("Kategorisiz") },
                                        leadingIcon = if (selectedCategoryId == null) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                items(categories) { category ->
                                    val isSelected = selectedCategoryId == category.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onCategoryChange(if (isSelected) null else category.id) },
                                        label = { Text(category.name) },
                                        shape = RoundedCornerShape(12.dp),
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            } else if (category.color != 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(category.color))
                                                )
                                            } else null
                                        }
                                    )
                                }
                            }
                        }

                        NoteToolCategory.NONE -> {}
                    }
                }
            }
        }

        // 2. Alt Yüzen Kapsül Dok Barı (Minimalist, Şık, Dokunma Konforlu)
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            color = dockSurfaceColor,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, dockBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol Düğmeler Grubu (Dokunma alanı konforu: 44dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // 0. Onay Kutusu (Checklist)
                    IconButton(
                        onClick = onToggleChecklist,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (hasChecklistItems || activeStyles.isChecklist) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = if (hasChecklistItems || activeStyles.isChecklist) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (hasChecklistItems) "Metne Dönüştür" else "Onay Kutusuna Dönüştür",
                            tint = if (hasChecklistItems || activeStyles.isChecklist) MaterialTheme.colorScheme.primary else dockIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 1. Ekle / Medya
                    IconButton(
                        onClick = {
                            onToolCategoryChange(if (activeToolCategory == NoteToolCategory.MEDIA) NoteToolCategory.NONE else NoteToolCategory.MEDIA)
                        },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (activeToolCategory == NoteToolCategory.MEDIA || isRecordingAudio) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = "Ekle / Medya",
                            tint = if (activeToolCategory == NoteToolCategory.MEDIA || isRecordingAudio) MaterialTheme.colorScheme.primary else dockIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 2. Metin Biçimlendirme
                    val isFormattingActive = activeToolCategory == NoteToolCategory.FORMAT ||
                            activeStyles.isBold || activeStyles.isItalic || activeStyles.isCode || activeStyles.isList
                    IconButton(
                        onClick = {
                            onToolCategoryChange(if (activeToolCategory == NoteToolCategory.FORMAT) NoteToolCategory.NONE else NoteToolCategory.FORMAT)
                        },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isFormattingActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(
                            Icons.Default.FormatSize,
                            contentDescription = "Biçimlendir",
                            tint = if (isFormattingActive) MaterialTheme.colorScheme.primary else dockIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Renk & Tema
                    IconButton(
                        onClick = {
                            onToolCategoryChange(if (activeToolCategory == NoteToolCategory.COLOR) NoteToolCategory.NONE else NoteToolCategory.COLOR)
                        },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (activeToolCategory == NoteToolCategory.COLOR) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Renk",
                            tint = if (activeToolCategory == NoteToolCategory.COLOR) MaterialTheme.colorScheme.primary else dockIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 4. Kategori / Klasör
                    IconButton(
                        onClick = {
                            onToolCategoryChange(if (activeToolCategory == NoteToolCategory.CATEGORY) NoteToolCategory.NONE else NoteToolCategory.CATEGORY)
                        },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (activeToolCategory == NoteToolCategory.CATEGORY) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Kategori",
                            tint = if (activeToolCategory == NoteToolCategory.CATEGORY) MaterialTheme.colorScheme.primary else dockIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Sağ Bölüm: Aktif ses kaydı bildirimi veya Tıklanabilir İstatistik Hapı
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                ) {
                    if (isRecordingAudio) {
                        AssistChip(
                            onClick = onToggleAudioRecording,
                            label = { Text("Durdur", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        )
                    } else {
                        if (checklistStats != null) {
                            ChecklistProgressRing(
                                stats = checklistStats,
                                contentColor = dockIconColor,
                                onMoveToBottom = {},
                                onToggleAll = {},
                                onRemoveCompleted = {},
                                onOpenHub = onOpenChecklistHub
                            )
                        }

                        if (wordGoal != null && wordGoal > 0) {
                            val goalStatus = remember(wordCount, wordGoal) {
                                WordGoalCalculator.calculateStatus(wordCount, wordGoal)
                            }
                            WordGoalProgressBadge(
                                status = goalStatus,
                                contentColor = dockIconColor,
                                onEditGoal = onOpenWordGoalDialog,
                                onRemoveGoal = onRemoveWordGoal
                            )
                        }

                        val hasExtraBadges = checklistStats != null || (wordGoal != null && wordGoal > 0)
                        Surface(
                            onClick = onOpenStatsDialog,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAppDark) Color(0x22FFFFFF) else Color(0x12000000),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = dockIconColor.copy(alpha = 0.75f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (hasExtraBadges) "$wordCount k" else "$wordCount k · ~${readingTime} dk",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = dockIconColor.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
