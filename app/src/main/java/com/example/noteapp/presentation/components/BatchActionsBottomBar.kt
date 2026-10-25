package com.example.noteapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Not Listesi Çoklu Seçim Modu Alt Eylem Çubuğu (Batch Actions Floating Bottom Bar).
 */
@Composable
fun BatchActionsBottomBar(
    selectedCount: Int,
    isAllPinned: Boolean,
    canMerge: Boolean,
    isTrashMode: Boolean,
    onTogglePin: () -> Unit,
    onChangeColor: () -> Unit,
    onAssignCategory: () -> Unit,
    onMergeNotes: () -> Unit,
    onDeleteNotes: () -> Unit,
    onExportVault: () -> Unit,
    onExportPdf: () -> Unit,
    onArchiveNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            tonalElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isTrashMode) {
                    // Sabitle / Kaldır
                    BatchActionButton(
                        icon = if (isAllPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                        label = if (isAllPinned) "Kaldır" else "Sabitle",
                        onClick = onTogglePin,
                        tint = if (isAllPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Renk Değiştir
                    BatchActionButton(
                        icon = Icons.Default.Palette,
                        label = "Renk",
                        onClick = onChangeColor,
                        modifier = Modifier.weight(1f)
                    )

                    // Kategori / Etiket Ata
                    BatchActionButton(
                        icon = Icons.AutoMirrored.Filled.Label,
                        label = "Kategori",
                        onClick = onAssignCategory,
                        modifier = Modifier.weight(1f)
                    )

                    // Birleştir (Eğer 2 veya daha fazla not seçilmişse)
                    if (canMerge) {
                        BatchActionButton(
                            icon = Icons.AutoMirrored.Filled.CallMerge,
                            label = "Birleştir",
                            onClick = onMergeNotes,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Sil / Çöp Kutusuna Taşı
                BatchActionButton(
                    icon = Icons.Default.Delete,
                    label = if (isTrashMode) "Kalıcı Sil" else "Sil",
                    onClick = onDeleteNotes,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )

                // Diğer İşlemler (Menü)
                if (!isTrashMode) {
                    Box(modifier = Modifier.weight(1f)) {
                        BatchActionButton(
                            icon = Icons.Default.MoreVert,
                            label = "Diğer",
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        )

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Seçili Notları Tek PDF Olarak Paylaş") },
                                leadingIcon = {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onExportPdf()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Şifreli Kasa (.notevault) Olarak Aktar") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onExportVault()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Arşivle") },
                                leadingIcon = {
                                    Icon(Icons.Default.Archive, contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onArchiveNotes()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kompakt alt eylem butonu.
 */
@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
