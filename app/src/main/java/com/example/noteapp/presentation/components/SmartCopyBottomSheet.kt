package com.example.noteapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.util.ClipboardHelper

/**
 * Zengin Not Kopyalama Seçenekleri Alt Sayfası (Smart Copy Suite BottomSheet).
 * Kullanıcının notu düz metin, formatlı markdown, salt başlık, özet veya görev listesi olarak
 * tek dokunuşla kopyalamasını sağlar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCopyBottomSheet(
    title: String,
    content: String,
    tags: List<String> = emptyList(),
    timestamp: Long? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPreview by remember { mutableStateOf(false) }

    val plainText = remember(title, content) {
        ClipboardHelper.toPlainText(title, content)
    }
    val formattedMarkdown = remember(title, content, tags, timestamp) {
        ClipboardHelper.formatAsMarkdown(title, content, tags, timestamp)
    }
    val summaryText = remember(content) {
        ClipboardHelper.extractSummary(content, maxSentences = 3, maxWords = 50)
    }
    val remainingChecklist = remember(content) {
        ClipboardHelper.extractRemainingChecklist(content, formatAsBullets = false)
    }
    val completedChecklist = remember(content) {
        ClipboardHelper.extractCompletedChecklist(content, formatAsBullets = false)
    }

    val totalWords = remember(plainText) { ClipboardHelper.countWords(plainText) }
    val totalChars = plainText.length

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Başlık & İstatistikler
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Akıllı Panoya Kopyalama",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalWords kelime • $totalChars karakter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { showPreview = !showPreview }) {
                    Icon(
                        imageVector = if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Önizleme Aç/Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Metin Önizleme Kutusu (İsteğe Bağlı)
            AnimatedVisibility(
                visible = showPreview,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = plainText.ifBlank { "İçerik boş." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 1. Düz Metin Olarak Kopyala
            CopyOptionItem(
                title = "Düz Metin Olarak Kopyala",
                subtitle = "Biçimlendirme ve Markdown sembollerinden arındırılmış temiz metin",
                icon = Icons.Default.TextFields,
                iconColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    ClipboardHelper.copyToClipboard(
                        context = context,
                        label = "Düz Not Metni",
                        text = plainText,
                        option = ClipboardHelper.CopyOption.PLAIN_TEXT
                    )
                    onDismiss()
                }
            )

            // 2. Formatlı Markdown Olarak Kopyala
            CopyOptionItem(
                title = "Formatlı Markdown Olarak Kopyala",
                subtitle = "Tüm başlıklar, listeler, alıntılar ve etiketlerle orijinal format",
                icon = Icons.Default.Code,
                iconColor = MaterialTheme.colorScheme.secondary,
                onClick = {
                    ClipboardHelper.copyToClipboard(
                        context = context,
                        label = "Markdown Not",
                        text = formattedMarkdown,
                        option = ClipboardHelper.CopyOption.FORMATTED_MARKDOWN
                    )
                    onDismiss()
                }
            )

            // 3. Yalnızca Başlığı Kopyala (Başlık varsa)
            if (title.isNotBlank()) {
                CopyOptionItem(
                    title = "Yalnızca Başlığı Kopyala",
                    subtitle = title.trim(),
                    icon = Icons.Default.Title,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    onClick = {
                        ClipboardHelper.copyToClipboard(
                            context = context,
                            label = "Not Başlığı",
                            text = title.trim(),
                            option = ClipboardHelper.CopyOption.TITLE_ONLY
                        )
                        onDismiss()
                    }
                )
            }

            // 4. Hızlı Özet Kopyala (İçerik varsa)
            if (summaryText.isNotBlank()) {
                CopyOptionItem(
                    title = "Hızlı Özet Kopyala",
                    subtitle = "Giriş ve ilk 3 cümlenin özeti",
                    icon = Icons.AutoMirrored.Filled.ShortText,
                    iconColor = Color(0xFFE65100),
                    onClick = {
                        ClipboardHelper.copyToClipboard(
                            context = context,
                            label = "Not Özeti",
                            text = summaryText,
                            option = ClipboardHelper.CopyOption.SUMMARY
                        )
                        onDismiss()
                    }
                )
            }

            // 5. Tamamlanmamış Görevleri Kopyala (Varsa)
            if (remainingChecklist.isNotBlank()) {
                CopyOptionItem(
                    title = "Kalan Görevleri Kopyala",
                    subtitle = "${remainingChecklist.lines().size} adet tamamlanmamış [ ] kontrol listesi maddesi",
                    icon = Icons.Default.CheckBoxOutlineBlank,
                    iconColor = Color(0xFFD32F2F),
                    onClick = {
                        ClipboardHelper.copyToClipboard(
                            context = context,
                            label = "Kalan Görevler",
                            text = remainingChecklist,
                            option = ClipboardHelper.CopyOption.REMAINING_CHECKLIST
                        )
                        onDismiss()
                    }
                )
            }

            // 6. Tamamlanmış Görevleri Kopyala (Varsa)
            if (completedChecklist.isNotBlank()) {
                CopyOptionItem(
                    title = "Tamamlanan Görevleri Kopyala",
                    subtitle = "${completedChecklist.lines().size} adet tamamlanmış [x] görev maddesi",
                    icon = Icons.Default.CheckBox,
                    iconColor = Color(0xFF2E7D32),
                    onClick = {
                        ClipboardHelper.copyToClipboard(
                            context = context,
                            label = "Tamamlanan Görevler",
                            text = completedChecklist,
                            option = ClipboardHelper.CopyOption.COMPLETED_CHECKLIST
                        )
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CopyOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Kopyala",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
