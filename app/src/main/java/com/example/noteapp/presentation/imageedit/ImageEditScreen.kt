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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.media.FileStorageHelper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CENTER
}

data class DrawingStroke(
    val pointsFraction: List<Offset>, // 0..1 normalize koordinatlar (görsele sabit)
    val color: Color,
    val strokeWidthDp: Float,
    val isHighlighter: Boolean = false
)

data class TextOverlayItem(
    val id: Long = System.currentTimeMillis(),
    var text: String,
    var positionFraction: Offset, // 0..1 normalize koordinatlar (görsele sabit)
    var color: Color = Color.White,
    var bgColor: Color = Color(0xCC1E1E1E),
    var fontSizeSp: Float = 28f
)

/**
 * Geri & İleri Alma için Tüm Düzenleme Durumunun Anlık Görüntüsü
 */
data class EditorSnapshot(
    val bitmap: Bitmap,
    val strokes: List<DrawingStroke>,
    val texts: List<TextOverlayItem>,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val filter: FilterPreset
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(
    imagePath: String,
    onSaveSuccess: (originalPath: String, newPath: String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    // Orijinal bitmap'i güvenli şekilde yükle
    var currentBitmap by remember {
        mutableStateOf<Bitmap?>(loadScaledBitmap(imagePath, 2560, 2560))
    }

    // --- DURUM DEĞİŞKENLERİ ---
    val drawingStrokes = remember { mutableStateListOf<DrawingStroke>() }
    val textOverlays = remember { mutableStateListOf<TextOverlayItem>() }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var selectedFilter by remember { mutableStateOf(FilterPreset.NONE) }

    // --- GERİ & İLERİ ALMA (UNDO / REDO) ---
    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }

    fun captureCurrentSnapshot(): EditorSnapshot? {
        val bmp = currentBitmap ?: return null
        return EditorSnapshot(
            bitmap = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true),
            strokes = drawingStrokes.map { it.copy(pointsFraction = ArrayList(it.pointsFraction)) },
            texts = textOverlays.map { it.copy() },
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            filter = selectedFilter
        )
    }

    fun recordSnapshot() {
        captureCurrentSnapshot()?.let { snap ->
            undoStack.add(snap)
            if (undoStack.size > 15) {
                undoStack.removeAt(0)
            }
            redoStack.clear()
        }
    }

    fun applySnapshot(snap: EditorSnapshot) {
        currentBitmap = snap.bitmap.copy(snap.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        drawingStrokes.clear()
        drawingStrokes.addAll(snap.strokes.map { it.copy(pointsFraction = ArrayList(it.pointsFraction)) })
        textOverlays.clear()
        textOverlays.addAll(snap.texts.map { it.copy() })
        brightness = snap.brightness
        contrast = snap.contrast
        saturation = snap.saturation
        selectedFilter = snap.filter
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val currentState = captureCurrentSnapshot()
            if (currentState != null) {
                redoStack.add(currentState)
            }
            val previousState = undoStack.removeAt(undoStack.lastIndex)
            applySnapshot(previousState)
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val currentState = captureCurrentSnapshot()
            if (currentState != null) {
                undoStack.add(currentState)
            }
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            applySnapshot(nextState)
        }
    }

    // Aktif Sekme
    var activeTab by remember { mutableStateOf(ImageEditorTab.CROP) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // --- 1. KIRPMA DURUMU ---
    var selectedAspectRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var cropRectFraction by remember { mutableStateOf(Rect(0.02f, 0.02f, 0.98f, 0.98f)) }
    var activeCropHandle by remember { mutableStateOf(CropHandle.NONE) }

    // En-boy oranı değiştiğinde orantıyı merkeze yerleştir
    fun updateCropAspectRatio(ratio: CropAspectRatio) {
        selectedAspectRatio = ratio
        val bmp = currentBitmap ?: return
        cropRectFraction = calculateCropFraction(ratio.ratio, bmp.width, bmp.height)
    }

    // --- 2. ÇİZİM VE SİLGİ DURUMU ---
    var drawingMode by remember { mutableStateOf(DrawingMode.PEN) }
    var brushColor by remember { mutableStateOf(Color(0xFFE53935)) }
    var brushSizeDp by remember { mutableFloatStateOf(10f) }
    var activePointsFraction by remember { mutableStateOf(listOf<Offset>()) }
    var eraserPositionScreen by remember { mutableStateOf<Offset?>(null) }

    val paletteColors = listOf(
        Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
        Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE91E63),
        Color.White, Color(0xFF212121), Color(0xFF757575), Color(0xFF00E676)
    )

    // --- 3. METİN DURUMU VE GEÇİCİ HAFIZA (DRAFT MEMORY) ---
    var showAddTextDialog by remember { mutableStateOf(false) }
    var editingTextItem by remember { mutableStateOf<TextOverlayItem?>(null) }
    var textInput by remember { mutableStateOf("") }
    var textColor by remember { mutableStateOf(Color.White) }
    var textBgType by remember { mutableIntStateOf(1) } // 0: Şeffaf, 1: Siyah, 2: Beyaz, 3: Vurgu
    var textSizeChoice by remember { mutableFloatStateOf(28f) }

    // Son kullanılan metin ayarlarını geçici hafızada tut
    var lastDraftText by remember { mutableStateOf("") }
    var lastDeletedTextItem by remember { mutableStateOf<TextOverlayItem?>(null) }

    // Kaydetme ve Çıkış
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val hasChanges = undoStack.isNotEmpty() || drawingStrokes.isNotEmpty() || textOverlays.isNotEmpty() ||
            brightness != 0f || contrast != 1f || saturation != 1f || selectedFilter != FilterPreset.NONE

    // Nihai Kaydetme (Tüm Çizim, Metin ve Filtre Katmanlarını Bitmap'e İşler)
    fun performSave() {
        val bmp = currentBitmap ?: return
        isSaving = true

        val resultBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(resultBitmap)

        // 1. Renk & Filtre
        val filterPaint = AndroidPaint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = buildCombinedColorFilter(brightness, contrast, saturation, selectedFilter)
        }
        canvas.drawBitmap(bmp, 0f, 0f, filterPaint)

        // 2. Çizim Darbeleri
        val strokePaint = AndroidPaint().apply {
            isAntiAlias = true
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            style = AndroidPaint.Style.STROKE
        }

        val baseScale = max(bmp.width, bmp.height) / 800f
        drawingStrokes.forEach { stroke ->
            if (stroke.pointsFraction.size > 1) {
                strokePaint.color = stroke.color.toArgb()
                strokePaint.strokeWidth = stroke.strokeWidthDp * density * baseScale
                strokePaint.alpha = if (stroke.isHighlighter) 115 else 255
                strokePaint.xfermode = null

                val path = android.graphics.Path()
                val p0 = stroke.pointsFraction[0]
                path.moveTo(p0.x * bmp.width, p0.y * bmp.height)
                for (i in 1 until stroke.pointsFraction.size) {
                    val pt = stroke.pointsFraction[i]
                    path.lineTo(pt.x * bmp.width, pt.y * bmp.height)
                }
                canvas.drawPath(path, strokePaint)
            }
        }

        // 3. Metin Katmanları (Ekrandaki orantıyla birebir aynı boyutta işlenir)
        val textPaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val bgPaint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.FILL
        }

        textOverlays.forEach { item ->
            val posX = item.positionFraction.x * bmp.width
            val posY = item.positionFraction.y * bmp.height
            val scaledFontSize = item.fontSizeSp * density * baseScale * 0.95f
            textPaint.textSize = scaledFontSize
            textPaint.color = item.color.toArgb()

            val textBounds = AndroidRect()
            textPaint.getTextBounds(item.text, 0, item.text.length, textBounds)

            val padX = 20f * baseScale
            val padY = 12f * baseScale

            if (item.bgColor != Color.Transparent) {
                bgPaint.color = item.bgColor.toArgb()
                val bgRect = AndroidRectF(
                    posX - padX,
                    posY - textBounds.height() - padY,
                    posX + textBounds.width() + padX,
                    posY + padY
                )
                canvas.drawRoundRect(bgRect, 14f * baseScale, 14f * baseScale, bgPaint)
            }

            canvas.drawText(item.text, posX, posY, textPaint)
        }

        val savedPath = FileStorageHelper.saveEditedImageBitmap(context, resultBitmap, imagePath)
        isSaving = false

        if (savedPath != null) {
            onSaveSuccess(imagePath, savedPath)
        } else {
            onBackClick()
        }
    }

    // --- METİN EKLEME VE DÜZENLEME DİALOGU ---
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddTextDialog = false
                editingTextItem = null
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (editingTextItem == null) "Metin Ekle" else "Metni Düzenle", fontWeight = FontWeight.Bold)
                    if (editingTextItem == null && lastDraftText.isNotBlank()) {
                        TextButton(onClick = { textInput = lastDraftText }) {
                            Text("Son Metni Kullan", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = {
                            textInput = it
                            lastDraftText = it
                        },
                        placeholder = { Text("Görsel üzerine yazılacak metin...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Yazı Rengi:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(paletteColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
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

                    Text("Kutu Arka Planı:", style = MaterialTheme.typography.labelMedium)
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

                    Text("Yazı Boyutu: ${textSizeChoice.roundToInt()}sp", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = textSizeChoice,
                        onValueChange = { textSizeChoice = it },
                        valueRange = 18f..64f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (textInput.isNotBlank()) {
                        recordSnapshot()
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
                                item.fontSizeSp = textSizeChoice
                            }
                        } else {
                            textOverlays.add(
                                TextOverlayItem(
                                    text = textInput,
                                    positionFraction = Offset(0.35f, 0.45f),
                                    color = textColor,
                                    bgColor = computedBg,
                                    fontSizeSp = textSizeChoice
                                )
                            )
                        }
                        lastDraftText = textInput
                        textInput = ""
                        editingTextItem = null
                        showAddTextDialog = false
                    }
                }) {
                    Text("Uygula")
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

    // Çıkış Onayı
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Değişiklikleri Kaydet") },
            text = { Text("Yaptığınız düzenlemeleri kaydetmeden çıkmak istediğinize emin misiniz?") },
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
                                recordSnapshot()
                                currentBitmap = original
                                drawingStrokes.clear()
                                textOverlays.clear()
                                brightness = 0f
                                contrast = 1f
                                saturation = 1f
                                selectedFilter = FilterPreset.NONE
                                cropRectFraction = Rect(0.02f, 0.02f, 0.98f, 0.98f)
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
                // Aktif Sekmenin Kontrol Paneli
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
                                    updateCropAspectRatio(ratio)
                                },
                                onRotateLeft = {
                                    currentBitmap?.let { bmp ->
                                        recordSnapshot()
                                        val m = Matrix().apply { postRotate(-90f) }
                                        currentBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                        cropRectFraction = Rect(0.02f, 0.02f, 0.98f, 0.98f)
                                    }
                                },
                                onRotateRight = {
                                    currentBitmap?.let { bmp ->
                                        recordSnapshot()
                                        val m = Matrix().apply { postRotate(90f) }
                                        currentBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                        cropRectFraction = Rect(0.02f, 0.02f, 0.98f, 0.98f)
                                    }
                                },
                                onFlipHorizontal = {
                                    currentBitmap?.let { bmp ->
                                        recordSnapshot()
                                        val m = Matrix().apply { postScale(-1f, 1f) }
                                        currentBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                    }
                                },
                                onFlipVertical = {
                                    currentBitmap?.let { bmp ->
                                        recordSnapshot()
                                        val m = Matrix().apply { postScale(1f, -1f) }
                                        currentBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                    }
                                },
                                onApplyCrop = {
                                    currentBitmap?.let { bmp ->
                                        recordSnapshot()
                                        val cropX = (bmp.width * cropRectFraction.left).roundToInt().coerceIn(0, bmp.width - 1)
                                        val cropY = (bmp.height * cropRectFraction.top).roundToInt().coerceIn(0, bmp.height - 1)
                                        val cropW = (bmp.width * cropRectFraction.width).roundToInt().coerceIn(1, bmp.width - cropX)
                                        val cropH = (bmp.height * cropRectFraction.height).roundToInt().coerceIn(1, bmp.height - cropY)

                                        currentBitmap = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)
                                        cropRectFraction = Rect(0.02f, 0.02f, 0.98f, 0.98f)
                                        selectedAspectRatio = CropAspectRatio.FREE
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
                                currentSize = brushSizeDp,
                                onSizeSelect = { brushSizeDp = it },
                                paletteColors = paletteColors,
                                onClearStrokes = {
                                    if (drawingStrokes.isNotEmpty()) {
                                        recordSnapshot()
                                        drawingStrokes.clear()
                                    }
                                }
                            )
                        }

                        ImageEditorTab.TEXT -> {
                            TextControlPanel(
                                onAddTextClick = {
                                    textInput = lastDraftText
                                    editingTextItem = null
                                    showAddTextDialog = true
                                },
                                textCount = textOverlays.size,
                                onClearAllText = {
                                    if (textOverlays.isNotEmpty()) {
                                        recordSnapshot()
                                        lastDeletedTextItem = textOverlays.lastOrNull()
                                        textOverlays.clear()
                                    }
                                },
                                hasDeletedText = lastDeletedTextItem != null,
                                onRestoreLastText = {
                                    lastDeletedTextItem?.let { restored ->
                                        recordSnapshot()
                                        textOverlays.add(restored.copy(id = System.currentTimeMillis()))
                                        lastDeletedTextItem = null
                                    }
                                }
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
                                        recordSnapshot()
                                        currentBitmap = applyColorAdjustmentsToBitmap(bmp, brightness, contrast, saturation, selectedFilter)
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

                // Ana Sekmeler
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
                .background(Color(0xFF141414))
                .onSizeChanged { viewportSize = it },
            contentAlignment = Alignment.Center
        ) {
            val bmp = currentBitmap
            if (bmp != null) {
                // Görselin ekrandaki sınırlarını hesapla
                val canvasW = viewportSize.width.toFloat()
                val canvasH = viewportSize.height.toFloat()

                val bmpW = bmp.width.toFloat()
                val bmpH = bmp.height.toFloat()
                val scale = if (canvasW > 0 && canvasH > 0) min(canvasW / bmpW, canvasH / bmpH) else 1f
                val dstW = bmpW * scale
                val dstH = bmpH * scale
                val dstLeft = (canvasW - dstW) / 2f
                val dstTop = (canvasH - dstH) / 2f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // ÇİZİM VE SİLGİ DOKUNMATİK ALGILAYICISI
                        .pointerInput(activeTab, drawingMode, brushColor, brushSizeDp, dstLeft, dstTop, dstW, dstH) {
                            if (activeTab == ImageEditorTab.DRAW && dstW > 0 && dstH > 0) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (drawingMode == DrawingMode.ERASER) {
                                            eraserPositionScreen = offset
                                            val norm = Offset((offset.x - dstLeft) / dstW, (offset.y - dstTop) / dstH)
                                            val eraseRadiusNorm = (brushSizeDp * 2.5f * density) / dstW
                                            val removed = eraseStrokesAt(drawingStrokes, norm, eraseRadiusNorm)
                                            if (removed) {
                                                recordSnapshot()
                                            }
                                        } else {
                                            val norm = Offset(
                                                ((offset.x - dstLeft) / dstW).coerceIn(0f, 1f),
                                                ((offset.y - dstTop) / dstH).coerceIn(0f, 1f)
                                            )
                                            activePointsFraction = listOf(norm)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (drawingMode == DrawingMode.ERASER) {
                                            eraserPositionScreen = change.position
                                            val norm = Offset((change.position.x - dstLeft) / dstW, (change.position.y - dstTop) / dstH)
                                            val eraseRadiusNorm = (brushSizeDp * 2.5f * density) / dstW
                                            eraseStrokesAt(drawingStrokes, norm, eraseRadiusNorm)
                                        } else {
                                            val norm = Offset(
                                                ((change.position.x - dstLeft) / dstW).coerceIn(0f, 1f),
                                                ((change.position.y - dstTop) / dstH).coerceIn(0f, 1f)
                                            )
                                            activePointsFraction = activePointsFraction + norm
                                        }
                                    },
                                    onDragEnd = {
                                        if (drawingMode == DrawingMode.ERASER) {
                                            eraserPositionScreen = null
                                        } else if (activePointsFraction.isNotEmpty()) {
                                            recordSnapshot()
                                            drawingStrokes.add(
                                                DrawingStroke(
                                                    pointsFraction = activePointsFraction,
                                                    color = brushColor,
                                                    strokeWidthDp = brushSizeDp,
                                                    isHighlighter = drawingMode == DrawingMode.HIGHLIGHTER
                                                )
                                            )
                                            activePointsFraction = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        activePointsFraction = emptyList()
                                        eraserPositionScreen = null
                                    }
                                )
                            }
                        }
                        // SERBEST VE HASSAS KIRPMA DOKUNMATİK ALGILAYICISI
                        .pointerInput(activeTab, cropRectFraction, dstLeft, dstTop, dstW, dstH) {
                            if (activeTab == ImageEditorTab.CROP && dstW > 0 && dstH > 0) {
                                val touchThreshold = 44.dp.toPx()
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val cropScreenLeft = dstLeft + cropRectFraction.left * dstW
                                        val cropScreenTop = dstTop + cropRectFraction.top * dstH
                                        val cropScreenRight = dstLeft + cropRectFraction.right * dstW
                                        val cropScreenBottom = dstTop + cropRectFraction.bottom * dstH

                                        activeCropHandle = detectCropHandle(
                                            touch = offset,
                                            left = cropScreenLeft,
                                            top = cropScreenTop,
                                            right = cropScreenRight,
                                            bottom = cropScreenBottom,
                                            threshold = touchThreshold
                                        )
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dNormX = dragAmount.x / dstW
                                        val dNormY = dragAmount.y / dstH

                                        cropRectFraction = applyCropHandleDrag(
                                            current = cropRectFraction,
                                            handle = activeCropHandle,
                                            dx = dNormX,
                                            dy = dNormY,
                                            aspectRatio = selectedAspectRatio.ratio
                                        )
                                    },
                                    onDragEnd = { activeCropHandle = CropHandle.NONE },
                                    onDragCancel = { activeCropHandle = CropHandle.NONE }
                                )
                            }
                        }
                ) {
                    // TUVAL RENDERI
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (dstW <= 0 || dstH <= 0) return@Canvas

                        // 1. Görsel Renderı
                        val filterPaint = AndroidPaint().apply {
                            isAntiAlias = true
                            isFilterBitmap = true
                            colorFilter = buildCombinedColorFilter(brightness, contrast, saturation, selectedFilter)
                        }

                        val srcRect = AndroidRect(0, 0, bmp.width, bmp.height)
                        val dstRect = AndroidRectF(dstLeft, dstTop, dstLeft + dstW, dstTop + dstH)
                        drawContext.canvas.nativeCanvas.drawBitmap(bmp, srcRect, dstRect, filterPaint)

                        // 2. Çizim Darbeleri
                        drawingStrokes.forEach { stroke ->
                            if (stroke.pointsFraction.size > 1) {
                                val path = Path()
                                val p0 = stroke.pointsFraction[0]
                                path.moveTo(dstLeft + p0.x * dstW, dstTop + p0.y * dstH)
                                for (i in 1 until stroke.pointsFraction.size) {
                                    val pt = stroke.pointsFraction[i]
                                    path.lineTo(dstLeft + pt.x * dstW, dstTop + pt.y * dstH)
                                }
                                drawPath(
                                    path = path,
                                    color = if (stroke.isHighlighter) stroke.color.copy(alpha = 0.45f) else stroke.color,
                                    style = Stroke(
                                        width = stroke.strokeWidthDp.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // 3. Canlı Çizilen Çizgi (Sadece Kalem/Fosforlu Modunda)
                        if (activePointsFraction.size > 1 && drawingMode != DrawingMode.ERASER) {
                            val path = Path()
                            val p0 = activePointsFraction[0]
                            path.moveTo(dstLeft + p0.x * dstW, dstTop + p0.y * dstH)
                            for (i in 1 until activePointsFraction.size) {
                                val pt = activePointsFraction[i]
                                path.lineTo(dstLeft + pt.x * dstW, dstTop + pt.y * dstH)
                            }
                            drawPath(
                                path = path,
                                color = if (drawingMode == DrawingMode.HIGHLIGHTER) brushColor.copy(alpha = 0.45f) else brushColor,
                                style = Stroke(
                                    width = brushSizeDp.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // 4. Silgi İmleci (Silgi aktifken dokunulan yerde gösterilir)
                        eraserPositionScreen?.let { eraserPos ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.7f),
                                radius = (brushSizeDp * 2.5f).dp.toPx(),
                                center = eraserPos,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.25f),
                                radius = (brushSizeDp * 2.5f).dp.toPx(),
                                center = eraserPos
                            )
                        }

                        // 5. Kırpma Alanı (Sadece Kırpma Sekmesinde)
                        if (activeTab == ImageEditorTab.CROP) {
                            val cLeft = dstLeft + cropRectFraction.left * dstW
                            val cTop = dstTop + cropRectFraction.top * dstH
                            val cRight = dstLeft + cropRectFraction.right * dstW
                            val cBottom = dstTop + cropRectFraction.bottom * dstH

                            // Karartma Maskesi
                            drawRect(color = Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, 0f), size = Size(size.width, cTop))
                            drawRect(color = Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, cBottom), size = Size(size.width, size.height - cBottom))
                            drawRect(color = Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, cTop), size = Size(cLeft, cBottom - cTop))
                            drawRect(color = Color.Black.copy(alpha = 0.65f), topLeft = Offset(cRight, cTop), size = Size(size.width - cRight, cBottom - cTop))

                            // Kırpma Çerçevesi
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(cLeft, cTop),
                                size = Size(cRight - cLeft, cBottom - cTop),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Kılavuz Çizgileri
                            val stepX = (cRight - cLeft) / 3f
                            val stepY = (cBottom - cTop) / 3f
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cLeft + stepX, cTop), Offset(cLeft + stepX, cBottom), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cLeft + stepX * 2, cTop), Offset(cLeft + stepX * 2, cBottom), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cLeft, cTop + stepY), Offset(cRight, cTop + stepY), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cLeft, cTop + stepY * 2), Offset(cRight, cTop + stepY * 2), strokeWidth = 1.dp.toPx())

                            // 4 Köşe Tutamaçları
                            val hLen = 28.dp.toPx()
                            val hStroke = 4.dp.toPx()
                            // Sol üst
                            drawLine(Color.White, Offset(cLeft, cTop), Offset(cLeft + hLen, cTop), strokeWidth = hStroke)
                            drawLine(Color.White, Offset(cLeft, cTop), Offset(cLeft, cTop + hLen), strokeWidth = hStroke)
                            // Sağ üst
                            drawLine(Color.White, Offset(cRight, cTop), Offset(cRight - hLen, cTop), strokeWidth = hStroke)
                            drawLine(Color.White, Offset(cRight, cTop), Offset(cRight, cTop + hLen), strokeWidth = hStroke)
                            // Sol alt
                            drawLine(Color.White, Offset(cLeft, cBottom), Offset(cLeft + hLen, cBottom), strokeWidth = hStroke)
                            drawLine(Color.White, Offset(cLeft, cBottom), Offset(cLeft, cBottom - hLen), strokeWidth = hStroke)
                            // Sağ alt
                            drawLine(Color.White, Offset(cRight, cBottom), Offset(cRight - hLen, cBottom), strokeWidth = hStroke)
                            drawLine(Color.White, Offset(cRight, cBottom), Offset(cRight, cBottom - hLen), strokeWidth = hStroke)
                        }
                    }

                    // METİN KATMANLARI (Görsele Birebir Sabitli, Sürüklenebilir)
                    textOverlays.forEach { item ->
                        val screenX = dstLeft + item.positionFraction.x * dstW
                        val screenY = dstTop + item.positionFraction.y * dstH

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                                .clip(RoundedCornerShape(8.dp))
                                .background(item.bgColor)
                                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .pointerInput(item, dstW, dstH) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val newNormX = (item.positionFraction.x + dragAmount.x / dstW).coerceIn(0f, 0.95f)
                                        val newNormY = (item.positionFraction.y + dragAmount.y / dstH).coerceIn(0f, 0.95f)
                                        item.positionFraction = Offset(newNormX, newNormY)
                                    }
                                }
                                .pointerInput(item) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            editingTextItem = item
                                            textInput = item.text
                                            textColor = item.color
                                            textSizeChoice = item.fontSizeSp
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
                                    fontSize = item.fontSizeSp.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (activeTab == ImageEditorTab.TEXT) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Metni Kaldır",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                recordSnapshot()
                                                lastDeletedTextItem = item
                                                textOverlays.remove(item)
                                            }
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

// --- ÇİZİM VE SİLGİ KONTROL PANELİ ---
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
                Text("Tümünü Temizle", color = MaterialTheme.colorScheme.error)
            }
        }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentMode == DrawingMode.ERASER) "Silgi Boyutu:" else "Fırça Kalınlığı:",
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = currentSize,
                onValueChange = onSizeSelect,
                valueRange = 4f..48f,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("${currentSize.roundToInt()}dp", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// --- METİN KONTROL PANELİ ---
@Composable
private fun TextControlPanel(
    onAddTextClick: () -> Unit,
    textCount: Int,
    onClearAllText: () -> Unit,
    hasDeletedText: Boolean,
    onRestoreLastText: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onAddTextClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Metin Ekle")
            }

            if (hasDeletedText) {
                OutlinedButton(onClick = onRestoreLastText) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Geri Getir")
                }
            }
        }

        if (textCount > 0) {
            TextButton(onClick = onClearAllText) {
                Text("Kaldır ($textCount)", color = MaterialTheme.colorScheme.error)
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

// --- YARDIMCI METOTLAR: SİLGİ, KIRPMA VE GRAFİK ---

/**
 * Silgi ile dokunulan alandaki çizgileri siler
 */
private fun eraseStrokesAt(
    strokes: MutableList<DrawingStroke>,
    centerNorm: Offset,
    radiusNorm: Float
): Boolean {
    val initialSize = strokes.size
    strokes.removeAll { stroke ->
        stroke.pointsFraction.any { pt ->
            val dx = pt.x - centerNorm.x
            val dy = pt.y - centerNorm.y
            sqrt(dx * dx + dy * dy) <= radiusNorm
        }
    }
    return strokes.size != initialSize
}

/**
 * En-boy oranına göre görsel üzerinde en büyük ortalanmış kırpma karesini hesaplar
 */
private fun calculateCropFraction(targetRatio: Float?, imgWidth: Int, imgHeight: Int): Rect {
    if (targetRatio == null || imgWidth <= 0 || imgHeight <= 0) {
        return Rect(0.02f, 0.02f, 0.98f, 0.98f)
    }

    val imgRatio = imgWidth.toFloat() / imgHeight.toFloat()
    val (wNorm, hNorm) = if (targetRatio > imgRatio) {
        // Hedef daha geniş: genişliği doldur, yüksekliği hesapla
        val w = 0.96f
        val h = (w / targetRatio) * imgRatio
        Pair(w, h.coerceAtMost(0.96f))
    } else {
        // Hedef daha dar / dik: yüksekliği doldur, genişliği hesapla
        val h = 0.96f
        val w = (h * targetRatio) / imgRatio
        Pair(w.coerceAtMost(0.96f), h)
    }

    val left = 0.5f - wNorm / 2f
    val top = 0.5f - hNorm / 2f
    return Rect(left, top, left + wNorm, top + hNorm)
}

/**
 * Dokunmanın kırpma kutusunun hangi tutamacına denk geldiğini bulur
 */
private fun detectCropHandle(
    touch: Offset,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    threshold: Float
): CropHandle {
    fun dist(x1: Float, y1: Float, x2: Float, y2: Float) = sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))

    // Köşeler
    if (dist(touch.x, touch.y, left, top) <= threshold) return CropHandle.TOP_LEFT
    if (dist(touch.x, touch.y, right, top) <= threshold) return CropHandle.TOP_RIGHT
    if (dist(touch.x, touch.y, left, bottom) <= threshold) return CropHandle.BOTTOM_LEFT
    if (dist(touch.x, touch.y, right, bottom) <= threshold) return CropHandle.BOTTOM_RIGHT

    // Kenarlar
    if (touch.x >= left && touch.x <= right) {
        if (kotlin.math.abs(touch.y - top) <= threshold) return CropHandle.TOP
        if (kotlin.math.abs(touch.y - bottom) <= threshold) return CropHandle.BOTTOM
    }
    if (touch.y >= top && touch.y <= bottom) {
        if (kotlin.math.abs(touch.x - left) <= threshold) return CropHandle.LEFT
        if (kotlin.math.abs(touch.x - right) <= threshold) return CropHandle.RIGHT
    }

    // Orta Alan (Kutuyu taşıma)
    if (touch.x in left..right && touch.y in top..bottom) return CropHandle.CENTER

    return CropHandle.NONE
}

/**
 * Sürüklemeyi tutamaca göre kırpma kutusuna uygular
 */
private fun applyCropHandleDrag(
    current: Rect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    aspectRatio: Float?
): Rect {
    val minSize = 0.08f
    var left = current.left
    var top = current.top
    var right = current.right
    var bottom = current.bottom

    when (handle) {
        CropHandle.TOP_LEFT -> {
            left = (left + dx).coerceIn(0f, right - minSize)
            top = (top + dy).coerceIn(0f, bottom - minSize)
        }
        CropHandle.TOP_RIGHT -> {
            right = (right + dx).coerceIn(left + minSize, 1f)
            top = (top + dy).coerceIn(0f, bottom - minSize)
        }
        CropHandle.BOTTOM_LEFT -> {
            left = (left + dx).coerceIn(0f, right - minSize)
            bottom = (bottom + dy).coerceIn(top + minSize, 1f)
        }
        CropHandle.BOTTOM_RIGHT -> {
            right = (right + dx).coerceIn(left + minSize, 1f)
            bottom = (bottom + dy).coerceIn(top + minSize, 1f)
        }
        CropHandle.TOP -> {
            top = (top + dy).coerceIn(0f, bottom - minSize)
        }
        CropHandle.BOTTOM -> {
            bottom = (bottom + dy).coerceIn(top + minSize, 1f)
        }
        CropHandle.LEFT -> {
            left = (left + dx).coerceIn(0f, right - minSize)
        }
        CropHandle.RIGHT -> {
            right = (right + dx).coerceIn(left + minSize, 1f)
        }
        CropHandle.CENTER -> {
            val width = right - left
            val height = bottom - top
            left = (left + dx).coerceIn(0f, 1f - width)
            right = left + width
            top = (top + dy).coerceIn(0f, 1f - height)
            bottom = top + height
        }
        CropHandle.NONE -> {}
    }

    return Rect(left, top, right, bottom)
}

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

private fun buildCombinedColorFilter(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    preset: FilterPreset
): ColorMatrixColorFilter {
    val matrix = ColorMatrix()
    matrix.setSaturation(saturation)

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
