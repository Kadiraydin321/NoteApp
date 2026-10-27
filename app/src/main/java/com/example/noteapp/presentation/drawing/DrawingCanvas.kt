package com.example.noteapp.presentation.drawing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Modern Material Design 3 Yüzen Araç Çubuğu (Drawing Floating Toolbar).
 * Fırça kalınlığı kaydırıcısı (slider), renk paleti çipleri,
 * geri al (undo), yinele (redo), temizle ve araç seçim butonlarını
 * tuval üzerinde şık ve ergonomik bir yüzen kapsülde sunar.
 */
@Composable
fun DrawingFloatingToolbar(
    activeTool: DrawingTool,
    onToolSelect: (DrawingTool) -> Unit,
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    strokeWidthRange: ClosedFloatingPointRange<Float>,
    currentColor: Color,
    onColorChange: (Color) -> Unit,
    colorPalette: List<Color>,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    canClear: Boolean,
    selectedShapeType: ShapeType = ShapeType.RECTANGLE,
    onShapeTypeSelect: ((ShapeType) -> Unit)? = null,
    isShapeFilled: Boolean = false,
    onToggleShapeFill: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val toolbarBgColor = if (isDark) Color(0xFF20232A) else MaterialTheme.colorScheme.surfaceContainerHigh
    val toolbarBorderColor = if (isDark) Color(0x35FFFFFF) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    var isDetailsExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. ÜST PANEL: Fırça Kalınlığı Slider'ı, Renk Çipleri ve Şekil Seçenekleri (Genişletilebilir)
        AnimatedVisibility(
            visible = isDetailsExpanded,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(150))
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = toolbarBgColor,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, toolbarBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Kalınlık Ayar Satırı (Slider + Canlı Önizleme Çemberi)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Canlı Fırça Önizleme Balonu (Genişlik ve Renk)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val dotSize = when (activeTool) {
                                DrawingTool.HIGHLIGHTER -> (strokeWidth * 0.45f).coerceIn(4f, 26f).dp
                                DrawingTool.ERASER -> (strokeWidth * 0.40f).coerceIn(6f, 26f).dp
                                else -> (strokeWidth * 0.75f).coerceIn(3f, 26f).dp
                            }
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(
                                        if (activeTool == DrawingTool.ERASER) MaterialTheme.colorScheme.onSurfaceVariant
                                        else if (activeTool == DrawingTool.HIGHLIGHTER) currentColor.copy(alpha = 0.75f)
                                        else currentColor
                                    )
                            )
                        }

                        // Slider (Kaydırıcı)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (activeTool) {
                                        DrawingTool.PEN -> "Kalem Kalınlığı"
                                        DrawingTool.HIGHLIGHTER -> "Fosforlu Kalınlığı"
                                        DrawingTool.SHAPES -> "Şekil Çizgisi"
                                        DrawingTool.ERASER -> "Silgi Boyutu"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "${strokeWidth.roundToInt()} px",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = strokeWidth,
                                onValueChange = onStrokeWidthChange,
                                valueRange = strokeWidthRange,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }
                    }

                    // Şekil Seçenekleri (Yalnızca Şekiller Aracı Aktifse)
                    if (activeTool == DrawingTool.SHAPES && onShapeTypeSelect != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShapeType.entries.forEach { shape ->
                                val isSelected = selectedShapeType == shape
                                val icon = when (shape) {
                                    ShapeType.LINE -> Icons.Default.HorizontalRule
                                    ShapeType.ARROW -> Icons.AutoMirrored.Filled.ArrowForward
                                    ShapeType.RECTANGLE -> Icons.Default.CropSquare
                                    ShapeType.CIRCLE -> Icons.Default.RadioButtonUnchecked
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onShapeTypeSelect(shape) },
                                    label = { Text(shape.title, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.height(32.dp)
                                )
                            }

                            if ((selectedShapeType == ShapeType.RECTANGLE || selectedShapeType == ShapeType.CIRCLE) && onToggleShapeFill != null) {
                                FilterChip(
                                    selected = isShapeFilled,
                                    onClick = onToggleShapeFill,
                                    label = { Text("Dolgu", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }

                    // Renk Paleti Çipleri (Silgi hariç)
                    if (activeTool != DrawingTool.ERASER) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(colorPalette) { color ->
                                val isSelected = currentColor == color
                                val animatedSize by animateDpAsState(
                                    targetValue = if (isSelected) 34.dp else 28.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "chipSize"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(animatedSize)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x35888888),
                                            shape = CircleShape
                                        )
                                        .clickable { onColorChange(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili Renk",
                                            tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. ALT PANEL: Ana Araç Seçimi + Geri Al / Yinele + Temizle + Daralt Kapsülü
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = toolbarBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, toolbarBorderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A) Geri Al & Yinele Kapsülü (Segmented Capsule)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Geri Al",
                                tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(18.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        IconButton(
                            onClick = onRedo,
                            enabled = canRedo,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Yinele",
                                tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Dikey Ayırıcı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                // B) Çizim Araçları Seçim Butonları (Kalem, Fosforlu, Şekiller, Silgi)
                DrawingTool.entries.forEach { tool ->
                    val isSelected = activeTool == tool
                    val icon = when (tool) {
                        DrawingTool.PEN -> Icons.Default.Edit
                        DrawingTool.HIGHLIGHTER -> Icons.Default.BorderColor
                        DrawingTool.SHAPES -> Icons.Default.Category
                        DrawingTool.ERASER -> Icons.Default.AutoFixNormal
                    }

                    FilledTonalIconButton(
                        onClick = { onToolSelect(tool) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = tool.title, modifier = Modifier.size(20.dp))
                            if (tool != DrawingTool.ERASER) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) currentColor else Color.Transparent)
                                )
                            }
                        }
                    }
                }

                // Dikey Ayırıcı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                // C) Temizle Butonu (Clear All)
                IconButton(
                    onClick = onClear,
                    enabled = canClear,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Tuvali Temizle",
                        tint = if (canClear) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // D) Detay Panelini Genişlet / Daralt Butonu
                IconButton(
                    onClick = { isDetailsExpanded = !isDetailsExpanded },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isDetailsExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.Tune,
                        contentDescription = if (isDetailsExpanded) "Ayar Panelini Daralt" else "Ayar Panelini Aç",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
