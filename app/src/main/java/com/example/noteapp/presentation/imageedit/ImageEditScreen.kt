package com.example.noteapp.presentation.imageedit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.graphics.RectF as AndroidRectF
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.media.FileStorageHelper
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ImageEditorTab(val title: String) {
    CROP("Kırp & Çevir"),
    DRAW("Çizim"),
    TEXT("Metin"),
    ADJUST("Renk & Filtre")
}

enum class CropAspectRatio(val title: String, val ratio: Float?) {
    FREE("Serbest", null),
    SQUARE("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_3_4("3:4", 3f / 4f)
}

enum class DrawingMode(val title: String) {
    PEN("Kalem"),
    HIGHLIGHTER("Fosforlu"),
    ERASER("Silgi")
}

enum class FilterPreset(val title: String) {
    NONE("Orijinal"),
    GRAYSCALE("Siyah & Beyaz"),
    SEPIA("Sepya"),
    WARM("Sıcak"),
    COOL("Soğuk"),
    INVERT("Negatif")
}

data class DrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val isEraser: Boolean = false
)

data class TextOverlayItem(
    val id: Long = System.currentTimeMillis(),
    var text: String,
    var position: Offset,
    var color: Color = Color.White,
    var bgColor: Color = Color(0x99000000),
    var fontSize: Float = 32f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(
    imagePath: String,
    onSaveSuccess: (originalPath: String, newPath: String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // Orijinal bitmap'i güvenli şekilde yükle (bellek taşmalarına karşı en fazla 2560px)
    var currentBitmap by remember {
        mutableStateOf<Bitmap?>(loadScaledBitmap(imagePath, 2560, 2560))
    }

    // Geri & İleri Alma Geçmişi (Undo / Redo stacks)
    val undoStack = remember { mutableStateListOf<Bitmap>() }
    val redoStack = remember { mutableStateListOf<Bitmap>() }

    fun pushUndo(newBitmap: Bitmap) {
        currentBitmap?.let { bmp ->
            undoStack.add(bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true))
            if (undoStack.size > 12) {
                undoStack.removeAt(0)
            }
            redoStack.clear()
            currentBitmap = newBitmap
        }
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty() && currentBitmap != null) {
            val lastState = undoStack.removeAt(undoStack.lastIndex)
            val bmp = currentBitmap!!
            redoStack.add(bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true))
            currentBitmap = lastState
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty() && currentBitmap != null) {
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            val bmp = currentBitmap!!
            undoStack.add(bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true))
            currentBitmap = nextState
        }
    }

    // Aktif Sekme
    var activeTab by remember { mutableStateOf(ImageEditorTab.CROP) }

    // Görüntüleme Boyutları
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // --- 1. KIRPMA DURUMU ---
    var selectedAspectRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var cropRectFraction by remember { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }

    // --- 2. ÇİZİM DURUMU ---
    var drawingMode by remember { mutableStateOf(DrawingMode.PEN) }
    var brushColor by remember { mutableStateOf(Color.Red) }
    var brushSize by remember { mutableFloatStateOf(12f) }
    val drawingStrokes = remember { mutableStateListOf<DrawingStroke>() }
    var activePoints by remember { mutableStateOf(listOf<Offset>()) }

    // Renk Paleti
    val paletteColors = listOf(
        Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
        Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE91E63),
        Color.White, Color(0xFF212121), Color(0xFF757575), Color(0xFF00E676)
    )

    // --- 3. METİN DURUMU ---
    val textOverlays = remember { mutableStateListOf<TextOverlayItem>() }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var editingTextItem by remember { mutableStateOf<TextOverlayItem?>(null) }
    var textInput by remember { mutableStateOf("") }
    var textColor by remember { mutableStateOf(Color.White) }
    var textBgType by remember { mutableIntStateOf(1) } // 0: Şeffaf, 1: Siyah kutu, 2: Beyaz kutu, 3: Vurgu
    var textSizeChoice by remember { mutableFloatStateOf(32f) }

    // --- 4. RENK VE FİLTRE DURUMU ---
    var brightness by remember { mutableFloatStateOf(0f) }   // -100..100
    var contrast by remember { mutableFloatStateOf(1f) }      // 0.5..2.0
    var saturation by remember { mutableFloatStateOf(1f) }    // 0.0..2.0
    var selectedFilter by remember { mutableStateOf(FilterPreset.NONE) }

    // Kaydetme ve Çıkış
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    // Değişiklik oldu mu kontrolü
    val hasChanges = undoStack.isNotEmpty() || drawingStrokes.isNotEmpty() || textOverlays.isNotEmpty() ||
            brightness != 0f || contrast != 1f || saturation != 1f || selectedFilter != FilterPreset.NONE

    // Çizim ve Metin Katmanlarını Bitmap'e İşleme Fonksiyonu
    fun applyStrokesAndTextToBitmap(sourceBmp: Bitmap): Bitmap {
        if (drawingStrokes.isEmpty() && textOverlays.isEmpty() &&
            brightness == 0f && contrast == 1f && saturation == 1f && selectedFilter == FilterPreset.NONE
        ) {
            return sourceBmp
        }

        val resultBitmap = Bitmap.createBitmap(sourceBmp.width, sourceBmp.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(resultBitmap)

        // Renk filtresi varsa uygula
        val filterPaint = AndroidPaint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = buildCombinedColorFilter(brightness, contrast, saturation, selectedFilter)
        }
        canvas.drawBitmap(sourceBmp, 0f, 0f, filterPaint)

        // Çizimleri uygula (Display koordinatlarını Bitmap koordinatlarına ölçekle)
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val scaleX = sourceBmp.width.toFloat() / viewportSize.width
            val scaleY = sourceBmp.height.toFloat() / viewportSize.height

            val strokePaint = AndroidPaint().apply {
                isAntiAlias = true
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                style = AndroidPaint.Style.STROKE
            }

            drawingStrokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    strokePaint.color = stroke.color.toArgb()
                    strokePaint.strokeWidth = stroke.strokeWidth * ((scaleX + scaleY) / 2f)
                    if (stroke.isHighlighter) {
                        strokePaint.alpha = 110
                    } else if (stroke.isEraser) {
                        strokePaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                    } else {
                        strokePaint.alpha = 255
                        strokePaint.xfermode = null
                    }

                    val path = android.graphics.Path()
                    val p0 = stroke.points[0]
                    path.moveTo(p0.x * scaleX, p0.y * scaleY)
                    for (i in 1 until stroke.points.size) {
                        val pt = stroke.points[i]
                        path.lineTo(pt.x * scaleX, pt.y * scaleY)
                    }
                    canvas.drawPath(path, strokePaint)
                }
            }

            // Metinleri uygula
            val textPaint = AndroidPaint().apply {
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
            val bgPaint = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.FILL
            }

            textOverlays.forEach { item ->
                val posX = item.position.x * scaleX
                val posY = item.position.y * scaleY
                val scaledFontSize = item.fontSize * ((scaleX + scaleY) / 2f)
                textPaint.textSize = scaledFontSize
                textPaint.color = item.color.toArgb()

                val textBounds = AndroidRect()
                textPaint.getTextBounds(item.text, 0, item.text.length, textBounds)

                val paddingX = 24f * scaleX
                val paddingY = 16f * scaleY

                if (item.bgColor != Color.Transparent) {
                    bgPaint.color = item.bgColor.toArgb()
                    val bgRect = AndroidRectF(
                        posX - paddingX,
                        posY - textBounds.height() - paddingY,
                        posX + textBounds.width() + paddingX,
                        posY + paddingY
                    )
                    canvas.drawRoundRect(bgRect, 16f * scaleX, 16f * scaleY, bgPaint)
                }

                canvas.drawText(item.text, posX, posY, textPaint)
            }
        }

        return resultBitmap
    }

    // Nihai Kaydetme
    fun performSave() {
        val bmp = currentBitmap ?: return
        isSaving = true

        val finalBitmap = applyStrokesAndTextToBitmap(bmp)
        val savedPath = FileStorageHelper.saveEditedImageBitmap(context, finalBitmap, imagePath)

        isSaving = false
        if (savedPath != null) {
            onSaveSuccess(imagePath, savedPath)
        } else {
            onBackClick()
        }
    }

    // Metin Düzenleme Dialogu
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddTextDialog = false
                editingTextItem = null
            },
            title = { Text(if (editingTextItem == null) "Metin Ekle" else "Metni Düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Metninizi yazın...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Metin Rengi:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(paletteColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { textColor = color }
                                    .border(
                                        width = if (textColor == color) 3.dp else 1.dp,
                                        color = if (textColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Text("Arka Plan Stili:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilterChip(
                            selected = textBgType == 0,
                            onClick = { textBgType = 0 },
                            label = { Text("Şeffaf") }
                        )
                        FilterChip(
                            selected = textBgType == 1,
                            onClick = { textBgType = 1 },
                            label = { Text("Siyah") }
                        )
                        FilterChip(
                            selected = textBgType == 2,
                            onClick = { textBgType = 2 },
                            label = { Text("Beyaz") }
                        )
                    }

                    Text("Boyut: ${textSizeChoice.roundToInt()}sp", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = textSizeChoice,
                        onValueChange = { textSizeChoice = it },
                        valueRange = 20f..72f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (textInput.isNotBlank()) {
                        val computedBg = when (textBgType) {
                            0 -> Color.Transparent
                            1 -> Color(0xCC1E1E1E)
                            2 -> Color(0xCCFFFFFF)
                            else -> Color(0xCC1E1E1E)
                        }
                        if (editingTextItem != null) {
                            editingTextItem?.let { item ->
                                item.text = textInput
                                item.color = textColor
                                item.bgColor = computedBg
                                item.fontSize = textSizeChoice
                            }
                        } else {
                            val defaultPos = Offset(
                                (viewportSize.width / 4f).coerceAtLeast(40f),
                                (viewportSize.height / 2f).coerceAtLeast(100f)
                            )
                            textOverlays.add(
                                TextOverlayItem(
                                    text = textInput,
                                    position = defaultPos,
                                    color = textColor,
                                    bgColor = computedBg,
                                    fontSize = textSizeChoice
                                )
                            )
                        }
                        textInput = ""
                        editingTextItem = null
                        showAddTextDialog = false
                    }
                }) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddTextDialog = false
                    editingTextItem = null
                }) {
                    Text("İptal")
                }
            }
        )
    }

    // Çıkış Onayı Dialogu
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Değişiklikler Kaydedilmedi") },
            text = { Text("Yaptığınız görsel düzenlemeleri kaydetmeden çıkmak istiyor musunuz?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Değişiklikleri Sil ve Çık")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("Düzenlemeye Devam Et")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Görsel Düzenleyici", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) {
                            showDiscardConfirmDialog = true
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Geri Al (Undo)
                    IconButton(
                        onClick = { handleUndo() },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Geri Al",
                            tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // İleri Al (Redo)
                    IconButton(
                        onClick = { handleRedo() },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "İleri Al",
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Sıfırla (Reset)
                    IconButton(
                        onClick = {
                            loadScaledBitmap(imagePath, 2560, 2560)?.let { original ->
                                pushUndo(original)
                                drawingStrokes.clear()
                                textOverlays.clear()
                                brightness = 0f
                                contrast = 1f
                                saturation = 1f
                                selectedFilter = FilterPreset.NONE
                            }
                        },
                        enabled = hasChanges
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Sıfırla")
                    }

                    // Kaydet
                    Button(
                        onClick = { performSave() },
                        modifier = Modifier.padding(end = 8.dp),
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kaydet")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Aktif Sekmenin Alt Kontrol Paneli
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    when (activeTab) {
                        ImageEditorTab.CROP -> {
                            CropControlPanel(
                                selectedRatio = selectedAspectRatio,
                                onRatioSelect = { ratio ->
                                    selectedAspectRatio = ratio
                                    ratio.ratio?.let { r ->
                                        cropRectFraction = adjustCropRectToRatio(cropRectFraction, r)
                                    }
                                },
                                onRotateLeft = {
                                    currentBitmap?.let { bmp ->
                                        val m = Matrix().apply { postRotate(-90f) }
                                        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                        pushUndo(rotated)
                                    }
                                },
                                onRotateRight = {
                                    currentBitmap?.let { bmp ->
                                        val m = Matrix().apply { postRotate(90f) }
                                        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                        pushUndo(rotated)
                                    }
                                },
                                onFlipHorizontal = {
                                    currentBitmap?.let { bmp ->
                                        val m = Matrix().apply { postScale(-1f, 1f) }
                                        val flipped = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                        pushUndo(flipped)
                                    }
                                },
                                onFlipVertical = {
                                    currentBitmap?.let { bmp ->
                                        val m = Matrix().apply { postScale(1f, -1f) }
                                        val flipped = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                        pushUndo(flipped)
                                    }
                                },
                                onApplyCrop = {
                                    currentBitmap?.let { bmp ->
                                        val cropX = (bmp.width * cropRectFraction.left).roundToInt().coerceIn(0, bmp.width - 1)
                                        val cropY = (bmp.height * cropRectFraction.top).roundToInt().coerceIn(0, bmp.height - 1)
                                        val cropW = (bmp.width * cropRectFraction.width).roundToInt().coerceIn(1, bmp.width - cropX)
                                        val cropH = (bmp.height * cropRectFraction.height).roundToInt().coerceIn(1, bmp.height - cropY)

                                        val cropped = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)
                                        pushUndo(cropped)
                                        cropRectFraction = Rect(0.05f, 0.05f, 0.95f, 0.95f)
                                    }
                                }
                            )
                        }

                        ImageEditorTab.DRAW -> {
                            DrawControlPanel(
                                currentMode = drawingMode,
                                onModeSelect = { drawingMode = it },
                                currentColor = brushColor,
                                onColorSelect = { brushColor = it },
                                currentSize = brushSize,
                                onSizeSelect = { brushSize = it },
                                paletteColors = paletteColors,
                                onClearStrokes = { drawingStrokes.clear() }
                            )
                        }

                        ImageEditorTab.TEXT -> {
                            TextControlPanel(
                                onAddTextClick = {
                                    textInput = ""
                                    editingTextItem = null
                                    showAddTextDialog = true
                                },
                                textCount = textOverlays.size,
                                onClearAllText = { textOverlays.clear() }
                            )
                        }

                        ImageEditorTab.ADJUST -> {
                            AdjustControlPanel(
                                brightness = brightness,
                                onBrightnessChange = { brightness = it },
                                contrast = contrast,
                                onContrastChange = { contrast = it },
                                saturation = saturation,
                                onSaturationChange = { saturation = it },
                                selectedFilter = selectedFilter,
                                onFilterSelect = { selectedFilter = it },
                                onApplyAdjustments = {
                                    currentBitmap?.let { bmp ->
                                        val adjusted = applyColorAdjustmentsToBitmap(bmp, brightness, contrast, saturation, selectedFilter)
                                        pushUndo(adjusted)
                                        brightness = 0f
                                        contrast = 1f
                                        saturation = 1f
                                        selectedFilter = FilterPreset.NONE
                                    }
                                }
                            )
                        }
                    }
                }

                // Ana Sekmeler (Material 3 TabRow)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(64.dp)
                ) {
                    ImageEditorTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        ImageEditorTab.CROP -> Icons.Default.Crop
                                        ImageEditorTab.DRAW -> Icons.Default.Draw
                                        ImageEditorTab.TEXT -> Icons.Default.TextFields
                                        ImageEditorTab.ADJUST -> Icons.Default.Tune
                                    },
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
                .onSizeChanged { viewportSize = it },
            contentAlignment = Alignment.Center
        ) {
            val bmp = currentBitmap
            if (bmp != null) {
                // Tuval: Görseli, Çizimleri, Kırpma Alanını ve Metinleri Göster
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeTab, drawingMode, brushColor, brushSize) {
                            if (activeTab == ImageEditorTab.DRAW) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activePoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activePoints = activePoints + change.position
                                    },
                                    onDragEnd = {
                                        if (activePoints.isNotEmpty()) {
                                            drawingStrokes.add(
                                                DrawingStroke(
                                                    points = activePoints,
                                                    color = brushColor,
                                                    strokeWidth = brushSize,
                                                    isHighlighter = drawingMode == DrawingMode.HIGHLIGHTER,
                                                    isEraser = drawingMode == DrawingMode.ERASER
                                                )
                                            )
                                            activePoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        activePoints = emptyList()
                                    }
                                )
                            }
                        }
                ) {
                    // 1. Görsel Renderı (Renk / Filtre Matrisi ile Canlı)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // Görseli Aspect Fit olarak ortala
                        val bmpW = bmp.width.toFloat()
                        val bmpH = bmp.height.toFloat()
                        val scale = min(canvasW / bmpW, canvasH / bmpH)
                        val dstW = bmpW * scale
                        val dstH = bmpH * scale
                        val dstLeft = (canvasW - dstW) / 2f
                        val dstTop = (canvasH - dstH) / 2f

                        val filterPaint = AndroidPaint().apply {
                            isAntiAlias = true
                            isFilterBitmap = true
                            colorFilter = buildCombinedColorFilter(brightness, contrast, saturation, selectedFilter)
                        }

                        val srcRect = AndroidRect(0, 0, bmp.width, bmp.height)
                        val dstRect = AndroidRectF(dstLeft, dstTop, dstLeft + dstW, dstTop + dstH)

                        drawContext.canvas.nativeCanvas.drawBitmap(bmp, srcRect, dstRect, filterPaint)

                        // Çizim Darbeleri (Strokes)
                        drawingStrokes.forEach { stroke ->
                            if (stroke.points.size > 1) {
                                val path = Path()
                                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                for (i in 1 until stroke.points.size) {
                                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                                drawPath(
                                    path = path,
                                    color = if (stroke.isHighlighter) stroke.color.copy(alpha = 0.45f) else stroke.color,
                                    style = Stroke(
                                        width = stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Canlı çizilen aktif çizgi
                        if (activePoints.size > 1) {
                            val path = Path()
                            path.moveTo(activePoints[0].x, activePoints[0].y)
                            for (i in 1 until activePoints.size) {
                                path.lineTo(activePoints[i].x, activePoints[i].y)
                            }
                            drawPath(
                                path = path,
                                color = if (drawingMode == DrawingMode.HIGHLIGHTER) brushColor.copy(alpha = 0.45f) else brushColor,
                                style = Stroke(
                                    width = brushSize,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Kırpma Alanı (Sadece Kırpma Sekmesinde)
                        if (activeTab == ImageEditorTab.CROP) {
                            val cropLeft = canvasW * cropRectFraction.left
                            val cropTop = canvasH * cropRectFraction.top
                            val cropRight = canvasW * cropRectFraction.right
                            val cropBottom = canvasH * cropRectFraction.bottom

                            // Karartma Maskesi
                            drawRect(color = Color.Black.copy(alpha = 0.6f), size = Size(canvasW, cropTop))
                            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, cropBottom), size = Size(canvasW, canvasH - cropBottom))
                            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropBottom - cropTop))
                            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(cropRight, cropTop), size = Size(canvasW - cropRight, cropBottom - cropTop))

                            // Kırpma Çerçevesi
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Üçte Bir Kuralı Kılavuz Çizgileri
                            val stepX = (cropRight - cropLeft) / 3f
                            val stepY = (cropBottom - cropTop) / 3f
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(cropLeft + stepX, cropTop), Offset(cropLeft + stepX, cropBottom), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(cropLeft + stepX * 2, cropTop), Offset(cropLeft + stepX * 2, cropBottom), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(cropLeft, cropTop + stepY), Offset(cropRight, cropTop + stepY), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(cropLeft, cropTop + stepY * 2), Offset(cropRight, cropTop + stepY * 2), strokeWidth = 1.dp.toPx())

                            // 4 Köşe Tutamacı
                            val handleLen = 24.dp.toPx()
                            val handleStroke = 4.dp.toPx()
                            // Sol üst
                            drawLine(Color.White, Offset(cropLeft, cropTop), Offset(cropLeft + handleLen, cropTop), strokeWidth = handleStroke)
                            drawLine(Color.White, Offset(cropLeft, cropTop), Offset(cropLeft, cropTop + handleLen), strokeWidth = handleStroke)
                            // Sağ üst
                            drawLine(Color.White, Offset(cropRight, cropTop), Offset(cropRight - handleLen, cropTop), strokeWidth = handleStroke)
                            drawLine(Color.White, Offset(cropRight, cropTop), Offset(cropRight, cropTop + handleLen), strokeWidth = handleStroke)
                            // Sol alt
                            drawLine(Color.White, Offset(cropLeft, cropBottom), Offset(cropLeft + handleLen, cropBottom), strokeWidth = handleStroke)
                            drawLine(Color.White, Offset(cropLeft, cropBottom), Offset(cropLeft, cropBottom - handleLen), strokeWidth = handleStroke)
                            // Sağ alt
                            drawLine(Color.White, Offset(cropRight, cropBottom), Offset(cropRight - handleLen, cropBottom), strokeWidth = handleStroke)
                            drawLine(Color.White, Offset(cropRight, cropBottom), Offset(cropRight, cropBottom - handleLen), strokeWidth = handleStroke)
                        }
                    }

                    // Kırpma Alanı Dokunmatik Sürükleme Mantığı
                    if (activeTab == ImageEditorTab.CROP) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaX = dragAmount.x / size.width
                                        val deltaY = dragAmount.y / size.height

                                        val newLeft = (cropRectFraction.left + deltaX).coerceIn(0f, cropRectFraction.right - 0.1f)
                                        val newRight = (cropRectFraction.right + deltaX).coerceIn(cropRectFraction.left + 0.1f, 1f)
                                        val newTop = (cropRectFraction.top + deltaY).coerceIn(0f, cropRectFraction.bottom - 0.1f)
                                        val newBottom = (cropRectFraction.bottom + deltaY).coerceIn(cropRectFraction.top + 0.1f, 1f)

                                        cropRectFraction = Rect(newLeft, newTop, newRight, newBottom)
                                    }
                                }
                        )
                    }

                    // 2. Metin Katmanları (Sürüklenebilir & Düzenlenebilir)
                    textOverlays.forEach { item ->
                        var itemOffset by remember { mutableStateOf(item.position) }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(itemOffset.x.roundToInt(), itemOffset.y.roundToInt()) }
                                .clip(RoundedCornerShape(8.dp))
                                .background(item.bgColor)
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .pointerInput(item) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            itemOffset = Offset(itemOffset.x + dragAmount.x, itemOffset.y + dragAmount.y)
                                            item.position = itemOffset
                                        }
                                    )
                                }
                                .pointerInput(item) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            editingTextItem = item
                                            textInput = item.text
                                            textColor = item.color
                                            textSizeChoice = item.fontSize
                                            showAddTextDialog = true
                                        }
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.text,
                                    color = item.color,
                                    fontSize = item.fontSize.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (activeTab == ImageEditorTab.TEXT) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Metni Kaldır",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { textOverlays.remove(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// --- KIRPMA VE ÇEVİRME KONTROL PANELİ ---
@Composable
private fun CropControlPanel(
    selectedRatio: CropAspectRatio,
    onRatioSelect: (CropAspectRatio) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onApplyCrop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // En-Boy Oranları
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(CropAspectRatio.values()) { ratio ->
                FilterChip(
                    selected = selectedRatio == ratio,
                    onClick = { onRatioSelect(ratio) },
                    label = { Text(ratio.title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Döndürme ve Aynalama Butonları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRotateLeft) {
                    Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Sola Döndür")
                }
                IconButton(onClick = onRotateRight) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Sağa Döndür")
                }
                IconButton(onClick = onFlipHorizontal) {
                    Icon(Icons.Default.Flip, contentDescription = "Yatay Aynala")
                }
                IconButton(onClick = onFlipVertical) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Dikey Aynala")
                }
            }

            Button(
                onClick = onApplyCrop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Kırpmayı Uygula")
            }
        }
    }
}

// --- ÇİZİM KONTROL PANELİ ---
@Composable
private fun DrawControlPanel(
    currentMode: DrawingMode,
    onModeSelect: (DrawingMode) -> Unit,
    currentColor: Color,
    onColorSelect: (Color) -> Unit,
    currentSize: Float,
    onSizeSelect: (Float) -> Unit,
    paletteColors: List<Color>,
    onClearStrokes: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Çizim Araçları ve Temizle Butonu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DrawingMode.values().forEach { mode ->
                    FilterChip(
                        selected = currentMode == mode,
                        onClick = { onModeSelect(mode) },
                        label = { Text(mode.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    DrawingMode.PEN -> Icons.Default.Edit
                                    DrawingMode.HIGHLIGHTER -> Icons.Default.Brush
                                    DrawingMode.ERASER -> Icons.Default.AutoFixNormal
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            TextButton(onClick = onClearStrokes) {
                Text("Çizimleri Temizle", color = MaterialTheme.colorScheme.error)
            }
        }

        // Renk Paleti (Silgi haricinde)
        if (currentMode != DrawingMode.ERASER) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(paletteColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelect(color) }
                            .border(
                                width = if (currentColor == color) 3.dp else 1.dp,
                                color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // Fırça Boyutu Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kalınlık:", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = currentSize,
                onValueChange = onSizeSelect,
                valueRange = 4f..48f,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("${currentSize.roundToInt()}px", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// --- METİN KONTROL PANELİ ---
@Composable
private fun TextControlPanel(
    onAddTextClick: () -> Unit,
    textCount: Int,
    onClearAllText: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onAddTextClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Yeni Metin Ekle")
        }

        if (textCount > 0) {
            TextButton(onClick = onClearAllText) {
                Text("Tüm Metinleri Kaldır ($textCount)", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// --- RENK VE FİLTRE KONTROL PANELİ ---
@Composable
private fun AdjustControlPanel(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    selectedFilter: FilterPreset,
    onFilterSelect: (FilterPreset) -> Unit,
    onApplyAdjustments: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Hazır Filtreler
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(FilterPreset.values()) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter.title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Parlaklık (-100..100)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Parlaklık", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = -100f..100f,
                modifier = Modifier.weight(1f)
            )
            Text("${brightness.roundToInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        }

        // Kontrast (0.5..2.0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kontrast", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f)
            )
            Text(String.format("%.1fx", contrast), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        }

        // Doygunluk (0.0..2.0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Doygunluk", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
            Slider(
                value = saturation,
                onValueChange = onSaturationChange,
                valueRange = 0.0f..2.0f,
                modifier = Modifier.weight(1f)
            )
            Text(String.format("%.1fx", saturation), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        }

        // Uygula Butonu
        Button(
            onClick = onApplyAdjustments,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Renk ve Filtre Ayarlarını Görsele Uygula")
        }
    }
}

// --- YARDIMCI GRAFİK METOTLARI ---

private fun loadScaledBitmap(filePath: String, maxW: Int, maxH: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)

        var inSampleSize = 1
        while (options.outWidth / inSampleSize > maxW || options.outHeight / inSampleSize > maxH) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inMutable = true
        }
        BitmapFactory.decodeFile(filePath, decodeOptions)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun adjustCropRectToRatio(current: Rect, targetRatio: Float): Rect {
    val currentW = current.width
    val currentH = current.height
    val currentRatio = currentW / currentH

    return if (currentRatio > targetRatio) {
        val newW = currentH * targetRatio
        val centerX = current.left + currentW / 2f
        Rect(centerX - newW / 2f, current.top, centerX + newW / 2f, current.bottom)
    } else {
        val newH = currentW / targetRatio
        val centerY = current.top + currentH / 2f
        Rect(current.left, centerY - newH / 2f, current.right, centerY + newH / 2f)
    }
}

private fun buildCombinedColorFilter(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    preset: FilterPreset
): ColorMatrixColorFilter {
    val matrix = ColorMatrix()

    // 1. Doygunluk (Saturation)
    matrix.setSaturation(saturation)

    // 2. Parlaklık & Kontrast
    val scale = contrast
    val translate = (-0.5f * scale + 0.5f) * 255f + brightness

    val bcMatrix = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    matrix.postConcat(bcMatrix)

    // 3. Preset Filtreler
    when (preset) {
        FilterPreset.GRAYSCALE -> {
            val gray = ColorMatrix().apply { setSaturation(0f) }
            matrix.postConcat(gray)
        }
        FilterPreset.SEPIA -> {
            val sepia = ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(sepia)
        }
        FilterPreset.WARM -> {
            val warm = ColorMatrix(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1.0f, 0f, 0f, 10f,
                    0f, 0f, 0.8f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(warm)
        }
        FilterPreset.COOL -> {
            val cool = ColorMatrix(
                floatArrayOf(
                    0.8f, 0f, 0f, 0f, -20f,
                    0f, 1.0f, 0f, 0f, 10f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(cool)
        }
        FilterPreset.INVERT -> {
            val invert = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(invert)
        }
        FilterPreset.NONE -> {}
    }

    return ColorMatrixColorFilter(matrix)
}

private fun applyColorAdjustmentsToBitmap(
    sourceBmp: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    preset: FilterPreset
): Bitmap {
    val result = Bitmap.createBitmap(sourceBmp.width, sourceBmp.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        colorFilter = buildCombinedColorFilter(brightness, contrast, saturation, preset)
    }
    canvas.drawBitmap(sourceBmp, 0f, 0f, paint)
    return result
}
