package com.example.noteapp.presentation.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.noteapp.R
import com.example.noteapp.data.settings.CardCornerStyle
import com.example.noteapp.data.settings.CardElevationStyle
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.presentation.components.CategoryBadge
import com.example.noteapp.presentation.components.parseMarkdownCardPreview
import com.example.noteapp.presentation.theme.getNoteColorSpec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Not Kartı (Modern Material 3 Card Görünümü).
 * 16-20dp köşe yuvarlaklığı, ince şık kenarlık, kilit/iğne rozetleri,
 * hashtag etiket çipleri ve hiyerarşik metin yapısı (başlık, önizleme, tarih).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    category: Category? = null,
    isCategoryLocked: Boolean = false,
    isConcealed: Boolean = false,
    viewMode: NotesViewMode,
    searchQuery: String = "",
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    cornerStyle: CardCornerStyle = CardCornerStyle.ROUNDED,
    elevationStyle: CardElevationStyle = CardElevationStyle.BORDERED,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeletePermanentlyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultSurface = MaterialTheme.colorScheme.surfaceVariant
    val colorSpec = remember(note.color, defaultSurface) {
        getNoteColorSpec(note.color, defaultSurface)
    }

    val firstImage = remember(note.attachments) {
        note.attachments.firstOrNull { !it.endsWith(".mp4") && !it.endsWith(".m4a") }
    }

    val displayContent = remember(note.content) {
        parseMarkdownCardPreview(note.content)
    }

    val checklistStats = remember(note.content) {
        com.example.noteapp.domain.util.ChecklistParser.parse(note.content)
    }

    // Hashtag etiketlerini içerikten çıkart (en fazla 3 adet)
    val tags = remember(note.content) {
        if (isConcealed || note.isLocked) emptyList()
        else {
            Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")
                .findAll(note.content)
                .map { it.groupValues[1] }
                .distinct()
                .take(3)
                .toList()
        }
    }

    // Tarih formatlama
    val formattedDate = remember(note.timestamp, note.updatedAt) {
        formatCardDate(if (note.updatedAt > 0) note.updatedAt else note.timestamp)
    }

    val cardShape = remember(cornerStyle) { RoundedCornerShape(cornerStyle.radiusDp.dp) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    // Modern M3 ince şık kenarlık
    val cardBorder = remember(isSelected, elevationStyle, primaryColor, outlineVariantColor) {
        when {
            isSelected -> BorderStroke(2.dp, primaryColor)
            elevationStyle == CardElevationStyle.BORDERED -> BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.5f))
            elevationStyle == CardElevationStyle.GLASS -> BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
            else -> null
        }
    }

    val cardElevation = when (elevationStyle) {
        CardElevationStyle.ELEVATED -> CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
        else -> CardDefaults.cardElevation(defaultElevation = 0.dp)
    }

    val cardBg = remember(elevationStyle, colorSpec.backgroundColor) {
        if (elevationStyle == CardElevationStyle.GLASS) colorSpec.backgroundColor.copy(alpha = 0.7f)
        else colorSpec.backgroundColor
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (cardBorder != null) Modifier.border(cardBorder, cardShape)
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape,
        elevation = cardElevation,
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!note.backgroundImage.isNullOrEmpty() && !isConcealed) {
                val context = LocalContext.current
                val bgRequest = remember(note.backgroundImage) {
                    coil.request.ImageRequest.Builder(context)
                        .data(File(note.backgroundImage))
                        .size(coil.size.Size(360, 360))
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = bgRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.35f)
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                // Görsel veya Çizim eki (Varsa)
                if (!isConcealed) {
                    firstImage?.let { imgPath ->
                        val isDrawing = remember(imgPath) { imgPath.contains("drawing", ignoreCase = true) }
                        val context = LocalContext.current
                        val imgRequest = remember(imgPath) {
                            coil.request.ImageRequest.Builder(context)
                                .data(File(imgPath))
                                .size(coil.size.Size(360, 240))
                                .crossfade(false)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = imgRequest,
                                contentDescription = if (isDrawing) "El Çizimi" else "Not Görseli",
                                contentScale = if (isDrawing) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
                                    .then(
                                        if (isDrawing) Modifier.background(Color.White.copy(alpha = 0.96f))
                                        else Modifier
                                    )
                            )
                            if (isDrawing) {
                                Surface(
                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Brush,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "Çizim",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // 1. SATIR: Başlık ve Sabitleme / Seçim Rozeti (Metin Hiyerarşisi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.title.isNotEmpty()) {
                        Text(
                            text = if (isConcealed) androidx.compose.ui.text.AnnotatedString(note.title)
                            else if (searchQuery.isNotBlank()) com.example.noteapp.domain.util.TextHighlightHelper.highlightQuery(
                                text = note.title,
                                query = searchQuery,
                                highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) else androidx.compose.ui.text.AnnotatedString(note.title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 21.sp
                            ),
                            color = colorSpec.contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Sabitleme rozeti veya Seçim kutusu (min 48dp dokunma alanı)
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onClick() },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else if (viewMode == NotesViewMode.ALL || viewMode == NotesViewMode.REMINDERS) {
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (note.isPinned) "Sabitlemeyi kaldır" else "Sabitle",
                                    onClick = onPinClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (note.isPinned) {
                                    colorSpec.contentColor.copy(alpha = 0.16f)
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (note.isPinned) {
                                        Icon(
                                            imageVector = Icons.Filled.PushPin,
                                            contentDescription = "Sabitlendi",
                                            tint = colorSpec.contentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_push_pin_off),
                                            contentDescription = "Sabitle",
                                            tint = colorSpec.contentColor.copy(alpha = 0.40f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Kilit Rozeti (Şık Pill Badge)
                if (note.isLocked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorSpec.contentColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, colorSpec.contentColor.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Kilitli Not",
                                tint = colorSpec.contentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Kilitli Not",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colorSpec.contentColor
                            )
                        }
                    }
                } else if (isConcealed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Kilitli Kategori",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "Kilitli Kategori — Açmak için dokun",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else if (displayContent.text.isNotEmpty()) {
                    // 2. BÖLÜM: Not Önizleme Metni (Metin Hiyerarşisi)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) com.example.noteapp.domain.util.TextHighlightHelper.highlightQuery(
                            text = displayContent.text,
                            query = searchQuery,
                            highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else displayContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        ),
                        color = colorSpec.contentColor.copy(alpha = 0.82f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Yapılacaklar (Checklist) İlerleme Çubuğu
                if (checklistStats != null && !note.isLocked && !isConcealed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorSpec.contentColor.copy(alpha = 0.07f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (checklistStats.isAllCompleted) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else colorSpec.iconTint,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${checklistStats.completedCount}/${checklistStats.totalCount} yapıldı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorSpec.contentColor.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "%${checklistStats.percent}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else colorSpec.contentColor.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { checklistStats.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            trackColor = colorSpec.contentColor.copy(alpha = 0.12f),
                        )
                    }
                }

                // Etiket Çipleri (Hashtags)
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorSpec.contentColor.copy(alpha = 0.07f),
                                border = BorderStroke(1.dp, colorSpec.contentColor.copy(alpha = 0.14f))
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = colorSpec.contentColor.copy(alpha = 0.88f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. SATIR: Alt Çubuk (Tarih, Kategori, Göstergeler ve Aksiyonlar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Tarih etiketi
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = colorSpec.secondaryColor
                        )

                        if (note.reminderTime != null && note.reminderTime > 0) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = "Hatırlatıcı",
                                modifier = Modifier.size(13.dp),
                                tint = colorSpec.secondaryColor
                            )
                        }
                        if (note.attachments.any { it.endsWith(".mp4") || it.endsWith(".m4a") }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Ses Kaydı",
                                modifier = Modifier.size(13.dp),
                                tint = colorSpec.secondaryColor
                            )
                        }
                        if (category != null) {
                            CategoryBadge(
                                category = category,
                                contentColorHint = colorSpec.contentColor,
                                isLocked = isCategoryLocked
                            )
                        }
                    }

                    // Hızlı Aksiyon Butonları (Arşiv ve Çöp Kutusu modları için - min 48dp dokunma alanı)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (viewMode) {
                            NotesViewMode.ALL, NotesViewMode.REMINDERS -> Unit
                            NotesViewMode.ARCHIVE -> {
                                NoteActionIcon(Icons.Default.Unarchive, "Arşivden çıkar", colorSpec.iconTint, onArchiveClick)
                                NoteActionIcon(Icons.Default.Delete, "Çöp kutusuna taşı", noteActionErrorColor(colorSpec.backgroundColor), onDeleteClick)
                            }
                            NotesViewMode.TRASH -> {
                                NoteActionIcon(Icons.Default.RestoreFromTrash, "Notu geri yükle", MaterialTheme.colorScheme.primary, onRestoreClick)
                                NoteActionIcon(Icons.Default.DeleteForever, "Kalıcı olarak sil", noteActionErrorColor(colorSpec.backgroundColor), onDeletePermanentlyClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kompakt Liste Kartı (Hızlı ve yoğun listeleme modu için).
 * Tek satırda başlık, önizleme ve tarih hiyerarşisi, kilit ve kategori rozetleri.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactNoteCard(
    note: Note,
    category: Category? = null,
    categoryName: String? = null,
    isCategoryLocked: Boolean = false,
    isConcealed: Boolean = false,
    viewMode: NotesViewMode,
    searchQuery: String = "",
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    cornerStyle: CardCornerStyle = CardCornerStyle.ROUNDED,
    elevationStyle: CardElevationStyle = CardElevationStyle.BORDERED,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeletePermanentlyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultSurface = MaterialTheme.colorScheme.surfaceVariant
    val colorSpec = remember(note.color, defaultSurface) {
        getNoteColorSpec(note.color, defaultSurface)
    }
    val dateStr = remember(note.timestamp, note.updatedAt) {
        formatCardDate(if (note.updatedAt > 0) note.updatedAt else note.timestamp)
    }
    val checklistStats = remember(note.content) {
        com.example.noteapp.domain.util.ChecklistParser.parse(note.content)
    }

    val compactShape = remember(cornerStyle) {
        val compactRadius = (cornerStyle.radiusDp * 0.75f).coerceAtLeast(6f).dp
        RoundedCornerShape(compactRadius)
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val cardBorder = remember(isSelected, elevationStyle, primaryColor, outlineVariantColor) {
        when {
            isSelected -> BorderStroke(2.dp, primaryColor)
            elevationStyle == CardElevationStyle.BORDERED -> BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.5f))
            elevationStyle == CardElevationStyle.GLASS -> BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
            else -> null
        }
    }

    Surface(
        shape = compactShape,
        color = if (elevationStyle == CardElevationStyle.GLASS) colorSpec.backgroundColor.copy(alpha = 0.7f) else colorSpec.backgroundColor,
        shadowElevation = if (elevationStyle == CardElevationStyle.ELEVATED) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (cardBorder != null) Modifier.border(cardBorder, compactShape)
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            // 1. SATIR: Başlık ve Kısa Özeti (+ Renk/Kilit/Seçim ve Tarih)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (note.color != 0) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(note.color))
                    )
                    Spacer(Modifier.width(8.dp))
                }

                if (note.isLocked || isConcealed) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = if (note.isLocked) "Kilitli Not" else "Kilitli Kategori",
                        modifier = Modifier.size(14.dp),
                        tint = if (isConcealed) MaterialTheme.colorScheme.primary else colorSpec.contentColor
                    )
                    Spacer(Modifier.width(6.dp))
                }

                // Başlık ve Kısa Özeti
                val titleSnippet = remember(note.title, note.content, note.isLocked, isConcealed, searchQuery) {
                    val rawTitle = if (isConcealed) {
                        if (note.title.isNotBlank()) note.title else "Kilitli Kategori Notu"
                    } else {
                        note.title.ifBlank { "Başlıksız Not" }
                    }
                    val highlightedTitle = if (searchQuery.isNotBlank() && !isConcealed) {
                        com.example.noteapp.domain.util.TextHighlightHelper.highlightQuery(
                            text = rawTitle,
                            query = searchQuery,
                            highlightColor = colorSpec.contentColor.copy(alpha = 0.22f)
                        )
                    } else androidx.compose.ui.text.AnnotatedString(rawTitle)

                    val preview = if (!note.isLocked && !isConcealed && note.content.isNotBlank()) {
                        val parsed = parseMarkdownCardPreview(note.content, lineLimit = 1)
                        if (searchQuery.isNotBlank()) {
                            com.example.noteapp.domain.util.TextHighlightHelper.highlightQuery(
                                text = parsed.text,
                                query = searchQuery,
                                highlightColor = colorSpec.contentColor.copy(alpha = 0.22f)
                            )
                        } else parsed
                    } else if (isConcealed) {
                        androidx.compose.ui.text.AnnotatedString("Gizli İçerik")
                    } else androidx.compose.ui.text.AnnotatedString("")

                    Pair(highlightedTitle, preview)
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colorSpec.contentColor)) {
                            append(titleSnippet.first)
                        }
                        if (titleSnippet.second.text.isNotEmpty()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = colorSpec.secondaryColor)) {
                                append("  —  ")
                                append(titleSnippet.second)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colorSpec.secondaryColor
                )
            }

            Spacer(Modifier.height(4.dp))

            // 2. SATIR: Göstergeler ve Hızlı Aksiyonlar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (category != null) {
                        CategoryBadge(
                            category = category,
                            contentColorHint = colorSpec.contentColor,
                            isLocked = isCategoryLocked
                        )
                    } else if (!categoryName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorSpec.contentColor.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "#$categoryName",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorSpec.contentColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (checklistStats != null && !note.isLocked && !isConcealed) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else colorSpec.contentColor.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (checklistStats.isAllCompleted) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else colorSpec.iconTint,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${checklistStats.completedCount}/${checklistStats.totalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorSpec.contentColor.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (note.reminderTime != null && note.reminderTime > 0) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = "Hatırlatıcı",
                            modifier = Modifier.size(13.dp),
                            tint = colorSpec.secondaryColor
                        )
                    }
                    if (note.attachments.any { it.contains("drawing", ignoreCase = true) }) {
                        Icon(
                            Icons.Default.Brush,
                            contentDescription = "Çizim",
                            modifier = Modifier.size(13.dp),
                            tint = colorSpec.secondaryColor
                        )
                    } else if (note.attachments.any { !it.endsWith(".mp4") && !it.endsWith(".m4a") }) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Görsel",
                            modifier = Modifier.size(13.dp),
                            tint = colorSpec.secondaryColor
                        )
                    }
                    if (note.attachments.any { it.endsWith(".mp4") || it.endsWith(".m4a") }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Ses",
                            modifier = Modifier.size(13.dp),
                            tint = colorSpec.secondaryColor
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Sağ taraf aksiyon butonları (min 48dp dokunma alanı)
                if (!isSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (viewMode) {
                            NotesViewMode.ALL, NotesViewMode.REMINDERS -> {
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = if (note.isPinned) "Sabitlemeyi kaldır" else "Sabitle",
                                            onClick = onPinClick
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (note.isPinned) colorSpec.contentColor.copy(alpha = 0.14f) else Color.Transparent,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (note.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Filled.PushPin,
                                                    contentDescription = "Sabitlemeyi kaldır",
                                                    tint = colorSpec.contentColor.copy(alpha = 0.95f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_push_pin_off),
                                                    contentDescription = "Sabitle",
                                                    tint = colorSpec.contentColor.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            NotesViewMode.ARCHIVE -> {
                                NoteActionIcon(Icons.Default.Unarchive, "Arşivden çıkar", colorSpec.iconTint, onArchiveClick)
                                NoteActionIcon(Icons.Default.Delete, "Çöp kutusuna taşı", MaterialTheme.colorScheme.error, onDeleteClick)
                            }
                            NotesViewMode.TRASH -> {
                                NoteActionIcon(Icons.Default.RestoreFromTrash, "Notu geri yükle", MaterialTheme.colorScheme.primary, onRestoreClick)
                                NoteActionIcon(Icons.Default.DeleteForever, "Kalıcı olarak sil", MaterialTheme.colorScheme.error, onDeletePermanentlyClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 48dp erişilebilirlik standartlarına uygun aksiyon butonu.
 */
@Composable
fun NoteActionIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    modifier = Modifier.size(17.dp),
                    tint = tint
                )
            }
        }
    }
}

fun noteActionErrorColor(cardColor: Color): Color =
    if (cardColor.luminance() < 0.48f) Color(0xFFFF8A80) else Color(0xFFB3261E)

private fun formatCardDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val noteCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return if (now.get(Calendar.YEAR) == noteCal.get(Calendar.YEAR)) {
        if (now.get(Calendar.DAY_OF_YEAR) == noteCal.get(Calendar.DAY_OF_YEAR)) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        } else {
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
        }
    } else {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
