package com.example.noteapp.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Not Listesi Çoklu Seçim Modu Modern Material 3 Alt Eylem Çubuğu (Batch Actions Floating Bar).
 * Seçili not sayısı, pin, kilit, renk değiştir ve sil eylemlerini modern floating pill tasarımında sunar.
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
    modifier: Modifier = Modifier,
    isAllLocked: Boolean = false,
    onToggleLock: () -> Unit = {}
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Seçili Not Sayısı Rozeti (Counter Pill)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$selectedCount",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                if (!isTrashMode) {
                    // 2. Sabitle / Kaldır (Pin)
                    BatchActionButton(
                        icon = if (isAllPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                        label = if (isAllPinned) "Kaldır" else "Sabitle",
                        onClick = onTogglePin,
                        tint = if (isAllPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // 3. Kilit / Kilit Aç (Lock)
                    BatchActionButton(
                        icon = if (isAllLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        label = if (isAllLocked) "Aç" else "Kilitle",
                        onClick = onToggleLock,
                        tint = if (isAllLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // 4. Renk Değiştir (Palette)
                    BatchActionButton(
                        icon = Icons.Default.Palette,
                        label = "Renk",
                        onClick = onChangeColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 5. Sil / Çöp Kutusuna Taşı (Delete)
                BatchActionButton(
                    icon = Icons.Default.Delete,
                    label = if (isTrashMode) "Kalıcı Sil" else "Sil",
                    onClick = onDeleteNotes,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )

                // 6. Diğer İşlemler (More Menu)
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
                            if (canMerge) {
                                DropdownMenuItem(
                                    text = { Text("Seçili Notları Birleştir") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.CallMerge,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onMergeNotes()
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Kategori / Etiket Ata") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onAssignCategory()
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

                            DropdownMenuItem(
                                text = { Text("Tek PDF Olarak Paylaş") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onExportPdf()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Şifreli Kasa (.notevault) Olarak Aktar") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onExportVault()
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
 * 48dp erişilebilirlik standartlarına uygun alt eylem butonu.
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
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
