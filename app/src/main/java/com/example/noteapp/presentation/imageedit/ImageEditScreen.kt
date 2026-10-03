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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.data.security.NoteCryptoManager
import com.example.noteapp.media.FileStorageHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
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

enum class CropAspectRatio(val title: String, val subtitle: String, val ratio: Float?) {
    FREE("Serbest", "Özel", null),
    SQUARE("1:1", "Kare", 1f),
    RATIO_4_3("4:3", "Standart", 4f / 3f),
    RATIO_16_9("16:9", "Yatay", 16f / 9f),
    RATIO_9_16("9:16", "Hikaye", 9f / 16f),
    RATIO_3_4("3:4", "Portre", 3f / 4f)
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

class TextOverlayItem(
    val id: Long = System.nanoTime(),
    text: String,
    positionFraction: Offset, // 0..1 normalize koordinatlar (görsele sabit)
    color: Color = Color.White,
    bgColor: Color = Color(0xCC1E1E1E),
    fontSizeSp: Float = 28f
) {
    var text by mutableStateOf(text)
    var positionFraction by mutableStateOf(positionFraction)
    var color by mutableStateOf(color)
    var bgColor by mutableStateOf(bgColor)
    var fontSizeSp by mutableFloatStateOf(fontSizeSp)

    fun copy(
        id: Long = this.id,
        text: String = this.text,
        positionFraction: Offset = this.positionFraction,
        color: Color = this.color,
        bgColor: Color = this.bgColor,
        fontSizeSp: Float = this.fontSizeSp
    ): TextOverlayItem = TextOverlayItem(
        id = id,
        text = text,
        positionFraction = positionFraction,
        color = color,
        bgColor = bgColor,
        fontSizeSp = fontSizeSp
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextOverlayItem) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

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
    cryptoManager: NoteCryptoManager,
    onSaveSuccess: (originalPath: String, newPath: String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 400

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
    var historyRestored by remember(imagePath) { mutableStateOf(false) }

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
    var didSave by remember(imagePath) { mutableStateOf(false) }

    val hasChanges = undoStack.isNotEmpty() || drawingStrokes.isNotEmpty() || textOverlays.isNotEmpty() ||
            brightness != 0f || contrast != 1f || saturation != 1f || selectedFilter != FilterPreset.NONE

    fun historyKey(path: String): String = MessageDigest.getInstance("SHA-256")
        .digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
    fun historyFile(path: String) = File(context.noBackupFilesDir, "image_edit_${historyKey(path)}.json")
    fun bitmapFile(path: String) = File(context.cacheDir, "image_edit_${historyKey(path)}.png")

    LaunchedEffect(imagePath) {
        val file = historyFile(imagePath)
        runCatching {
            val encrypted = file.readText()
            val raw = cryptoManager.decrypt(encrypted)
            if (raw == encrypted || raw.startsWith("[Korumalı")) return@runCatching
            val data = JSONObject(raw)
            if (System.currentTimeMillis() - data.getLong("updated") > 60 * 60 * 1000L) {
                file.delete(); bitmapFile(imagePath).delete(); return@runCatching
            }
            val savedBitmap = bitmapFile(imagePath)
            if (savedBitmap.exists()) BitmapFactory.decodeFile(savedBitmap.absolutePath)?.let { currentBitmap = it }
            brightness = data.optDouble("brightness", 0.0).toFloat()
            contrast = data.optDouble("contrast", 1.0).toFloat()
            saturation = data.optDouble("saturation", 1.0).toFloat()
            selectedFilter = runCatching { FilterPreset.valueOf(data.optString("filter", FilterPreset.NONE.name)) }.getOrDefault(FilterPreset.NONE)
            val strokes = data.optJSONArray("strokes") ?: JSONArray()
            drawingStrokes.clear()
            for (i in 0 until strokes.length()) {
                val s = strokes.getJSONObject(i); val pts = s.getJSONArray("points")
                drawingStrokes += DrawingStroke((0 until pts.length()).map { j -> val p = pts.getJSONObject(j); Offset(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()) }, Color(s.getLong("color").toULong()), s.getDouble("width").toFloat(), s.optBoolean("highlighter"))
            }
            val texts = data.optJSONArray("texts") ?: JSONArray()
            textOverlays.clear()
            for (i in 0 until texts.length()) {
                val t = texts.getJSONObject(i)
                textOverlays += TextOverlayItem(t.optLong("id", System.nanoTime()), t.getString("text"), Offset(t.getDouble("x").toFloat(), t.getDouble("y").toFloat()), Color(t.getLong("color").toULong()), Color(t.getLong("bg").toULong()), t.getDouble("size").toFloat())
            }
        }.onFailure { /* A missing/invalid one-hour session simply starts from the image. */ }
        historyRestored = true
    }

    LaunchedEffect(historyRestored, currentBitmap, brightness, contrast, saturation, selectedFilter, drawingStrokes.map { it.hashCode() }, textOverlays.map { listOf(it.id, it.text, it.positionFraction, it.color, it.bgColor, it.fontSizeSp) }) {
        if (!historyRestored) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        runCatching {
            currentBitmap?.let { bmp -> bitmapFile(imagePath).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            val strokes = JSONArray()
            drawingStrokes.forEach { s ->
                val points = JSONArray(); s.pointsFraction.forEach { points.put(JSONObject().put("x", it.x).put("y", it.y)) }
                strokes.put(JSONObject().put("points", points).put("color", s.color.value.toLong()).put("width", s.strokeWidthDp).put("highlighter", s.isHighlighter))
            }
            val texts = JSONArray()
            textOverlays.forEach { t -> texts.put(JSONObject().put("id", t.id).put("text", t.text).put("x", t.positionFraction.x).put("y", t.positionFraction.y).put("color", t.color.value.toLong()).put("bg", t.bgColor.value.toLong()).put("size", t.fontSizeSp)) }
            val raw = JSONObject().put("updated", System.currentTimeMillis()).put("brightness", brightness).put("contrast", contrast).put("saturation", saturation).put("filter", selectedFilter.name).put("strokes", strokes).put("texts", texts).toString()
            historyFile(imagePath).writeText(cryptoManager.encrypt(raw))
        }
    }

    // Nihai Kaydetme (Tüm Çizim, Metin ve Filtre Katmanlarını Bitmap'e İşler)
    fun performSave() {
        if (isSaving || didSave) return
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

        // 3. Metin Katmanları (Ekrandaki orantıyla birebir aynı boyutta ve konumda işlenir)
        val textPaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val bgPaint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.FILL
        }

        val canvasW = viewportSize.width.toFloat()
        val canvasH = viewportSize.height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        val screenScale = if (canvasW > 0 && canvasH > 0) min(canvasW / bmpW, canvasH / bmpH) else 1f
        val textScaleFactor = if (screenScale > 0f) 1f / screenScale else baseScale

        textOverlays.forEach { item ->
            val scaledFontSize = item.fontSizeSp * density * textScaleFactor * 0.95f
            textPaint.textSize = scaledFontSize
            textPaint.color = item.color.toArgb()

            val fontMetrics = textPaint.fontMetrics
            val textWidth = textPaint.measureText(item.text)
            val textHeight = fontMetrics.descent - fontMetrics.ascent

            val padX = 10f * density * textScaleFactor
            val padY = 6f * density * textScaleFactor
            val cornerRadius = 8f * density * textScaleFactor

            val boxLeft = item.positionFraction.x * bmp.width
            val boxTop = item.positionFraction.y * bmp.height

            if (item.bgColor != Color.Transparent) {
                bgPaint.color = item.bgColor.toArgb()
                val bgRect = AndroidRectF(
                    boxLeft,
                    boxTop,
                    boxLeft + textWidth + 2 * padX,
                    boxTop + textHeight + 2 * padY
                )
                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
            }

            val textDrawX = boxLeft + padX
            val textDrawY = boxTop + padY - fontMetrics.ascent
            canvas.drawText(item.text, textDrawX, textDrawY, textPaint)
        }

        val savedPath = FileStorageHelper.saveEditedImageBitmap(context, resultBitmap, imagePath)
        isSaving = false

        if (savedPath != null) {
            runCatching {
                currentBitmap?.let { base -> bitmapFile(savedPath).outputStream().use { base.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                val strokes = JSONArray()
                drawingStrokes.forEach { s ->
                    val points = JSONArray(); s.pointsFraction.forEach { points.put(JSONObject().put("x", it.x).put("y", it.y)) }
                    strokes.put(JSONObject().put("points", points).put("color", s.color.value.toLong()).put("width", s.strokeWidthDp).put("highlighter", s.isHighlighter))
                }
                val texts = JSONArray()
                textOverlays.forEach { t -> texts.put(JSONObject().put("id", t.id).put("text", t.text).put("x", t.positionFraction.x).put("y", t.positionFraction.y).put("color", t.color.value.toLong()).put("bg", t.bgColor.value.toLong()).put("size", t.fontSizeSp)) }
                val raw = JSONObject().put("updated", System.currentTimeMillis()).put("brightness", brightness).put("contrast", contrast).put("saturation", saturation).put("filter", selectedFilter.name).put("strokes", strokes).put("texts", texts).toString()
                historyFile(savedPath).writeText(cryptoManager.encrypt(raw))
            }
            didSave = true
            onSaveSuccess(imagePath, savedPath)
        } else {
            didSave = true
            onBackClick()
        }
    }

    val exitEditor: () -> Unit = {
        if (!isSaving && !didSave && hasChanges) performSave() else if (!isSaving) onBackClick()
    }
    BackHandler { exitEditor() }

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

    val hasPreEditBackup = remember { com.example.noteapp.media.ImageBackupManager.hasPreviousVersion(context, imagePath) }
    var showRevertBackupConfirm by remember { mutableStateOf(false) }

    if (showRevertBackupConfirm) {
        AlertDialog(
            onDismissRequest = { showRevertBackupConfirm = false },
            icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Orijinal Görsele Dön") },
            text = { Text("Bu görselin önceki tüm düzenlemelerini geri alıp en baştaki orijinal haline dönmek istiyor musunuz?") },
            confirmButton = {
                Button(onClick = {
                    showRevertBackupConfirm = false
                    val originalBackupPath = com.example.noteapp.media.ImageBackupManager.getPreviousVersionPath(context, imagePath)
                    if (originalBackupPath != null) {
                        loadScaledBitmap(originalBackupPath, 2560, 2560)?.let { originalBmp ->
                            recordSnapshot()
                            currentBitmap = originalBmp
                            drawingStrokes.clear()
                            textOverlays.clear()
                            brightness = 0f
                            contrast = 1f
                            saturation = 1f
                            selectedFilter = FilterPreset.NONE
                            cropRectFraction = Rect(0.02f, 0.02f, 0.98f, 0.98f)
                        }
                    }
                }) {
                    Text("Orijinale Dön")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevertBackupConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF0C0C0E),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Düzenleyici", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Text(activeTab.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = exitEditor,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF22222A),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Orijinal Görsele Geri Dön (Eğer önceden düzenlendiyse)
                    if (hasPreEditBackup) {
                        IconButton(onClick = { showRevertBackupConfirm = true }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Önceki Orijinale Dön",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Geri & İleri Al (Undo / Redo Kapsülü)
                    AnimatedVisibility(
                        visible = undoStack.isNotEmpty() || redoStack.isNotEmpty(),
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Surface(
                            color = Color(0xFF22222A),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { handleUndo() },
                                    enabled = undoStack.isNotEmpty(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Geri Al",
                                        tint = if (undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { handleRedo() },
                                    enabled = redoStack.isNotEmpty(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Redo,
                                        contentDescription = "İleri Al",
                                        tint = if (redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Sıfırla (Reset)
                    AnimatedVisibility(
                        visible = hasChanges,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
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
                            }
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Sıfırla", tint = Color.White.copy(alpha = 0.85f))
                        }
                    }

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF16161A)
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Üst Kontrol Alanı (Yüzen Araç Paneli)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = Color(0xFF1E2026),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                    onResetAdjustments = {
                                        brightness = 0f
                                        contrast = 1f
                                        saturation = 1f
                                        selectedFilter = FilterPreset.NONE
                                    },
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
                }

                // 2. Yüzen Kapsül Dock (Floating Capsule Dock)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = Color(0xFF22252A),
                    border = BorderStroke(1.dp, Color(0x30FFFFFF)),
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (tab in ImageEditorTab.values()) {
                            val isSelected = activeTab == tab
                            val activeTint = MaterialTheme.colorScheme.primary
                            val inactiveTint = Color(0xFF9EABB8)
                            val tabBg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent

                            Surface(
                                onClick = { activeTab = tab },
                                shape = RoundedCornerShape(20.dp),
                                color = tabBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (tab) {
                                            ImageEditorTab.CROP -> Icons.Default.Crop
                                            ImageEditorTab.DRAW -> Icons.Default.Edit
                                            ImageEditorTab.TEXT -> Icons.Default.TextFields
                                            ImageEditorTab.ADJUST -> Icons.Default.Tune
                                        },
                                        contentDescription = tab.title,
                                        tint = if (isSelected) activeTint else inactiveTint,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    if (!isCompactScreen || isSelected) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) activeTint else inactiveTint,
                                            fontSize = if (isCompactScreen) 10.sp else 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
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

                val currentCropRect by rememberUpdatedState(cropRectFraction)
                val currentAspectRatio by rememberUpdatedState(selectedAspectRatio.ratio)
                val currentDrawingMode by rememberUpdatedState(drawingMode)
                val currentBrushColor by rememberUpdatedState(brushColor)
                val currentBrushSizeDp by rememberUpdatedState(brushSizeDp)
                val imgRatio = if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 1f
                val currentImgRatio by rememberUpdatedState(imgRatio)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeTab, dstLeft, dstTop, dstW, dstH) {
                            if (dstW <= 0 || dstH <= 0) return@pointerInput
                            when (activeTab) {
                                ImageEditorTab.DRAW -> {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            if (currentDrawingMode == DrawingMode.ERASER) {
                                                eraserPositionScreen = offset
                                                val norm = Offset((offset.x - dstLeft) / dstW, (offset.y - dstTop) / dstH)
                                                val eraseRadiusNorm = (currentBrushSizeDp * 2.5f * density) / dstW
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
                                            if (currentDrawingMode == DrawingMode.ERASER) {
                                                eraserPositionScreen = change.position
                                                val norm = Offset((change.position.x - dstLeft) / dstW, (change.position.y - dstTop) / dstH)
                                                val eraseRadiusNorm = (currentBrushSizeDp * 2.5f * density) / dstW
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
                                            if (currentDrawingMode == DrawingMode.ERASER) {
                                                eraserPositionScreen = null
                                            } else if (activePointsFraction.isNotEmpty()) {
                                                recordSnapshot()
                                                drawingStrokes.add(
                                                    DrawingStroke(
                                                        pointsFraction = activePointsFraction,
                                                        color = currentBrushColor,
                                                        strokeWidthDp = currentBrushSizeDp,
                                                        isHighlighter = currentDrawingMode == DrawingMode.HIGHLIGHTER
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
                                ImageEditorTab.CROP -> {
                                    val touchThreshold = 44.dp.toPx()
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val r = currentCropRect
                                            val cropScreenLeft = dstLeft + r.left * dstW
                                            val cropScreenTop = dstTop + r.top * dstH
                                            val cropScreenRight = dstLeft + r.right * dstW
                                            val cropScreenBottom = dstTop + r.bottom * dstH

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
                                            if (activeCropHandle != CropHandle.NONE) {
                                                val dNormX = dragAmount.x / dstW
                                                val dNormY = dragAmount.y / dstH

                                                cropRectFraction = applyCropHandleDrag(
                                                    current = cropRectFraction,
                                                    handle = activeCropHandle,
                                                    dx = dNormX,
                                                    dy = dNormY,
                                                    aspectRatio = currentAspectRatio,
                                                    imgRatio = currentImgRatio
                                                )
                                            }
                                        },
                                        onDragEnd = { activeCropHandle = CropHandle.NONE },
                                        onDragCancel = { activeCropHandle = CropHandle.NONE }
                                    )
                                }
                                else -> {}
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
                            val hLen = 30.dp.toPx()
                            val hStroke = 5.dp.toPx()
                            // Sol üst
                            drawLine(Color.White, Offset(cLeft, cTop), Offset(cLeft + hLen, cTop), strokeWidth = hStroke, cap = StrokeCap.Round)
                            drawLine(Color.White, Offset(cLeft, cTop), Offset(cLeft, cTop + hLen), strokeWidth = hStroke, cap = StrokeCap.Round)
                            // Sağ üst
                            drawLine(Color.White, Offset(cRight, cTop), Offset(cRight - hLen, cTop), strokeWidth = hStroke, cap = StrokeCap.Round)
                            drawLine(Color.White, Offset(cRight, cTop), Offset(cRight, cTop + hLen), strokeWidth = hStroke, cap = StrokeCap.Round)
                            // Sol alt
                            drawLine(Color.White, Offset(cLeft, cBottom), Offset(cLeft + hLen, cBottom), strokeWidth = hStroke, cap = StrokeCap.Round)
                            drawLine(Color.White, Offset(cLeft, cBottom), Offset(cLeft, cBottom - hLen), strokeWidth = hStroke, cap = StrokeCap.Round)
                            // Sağ alt
                            drawLine(Color.White, Offset(cRight, cBottom), Offset(cRight - hLen, cBottom), strokeWidth = hStroke, cap = StrokeCap.Round)
                            drawLine(Color.White, Offset(cRight, cBottom), Offset(cRight, cBottom - hLen), strokeWidth = hStroke, cap = StrokeCap.Round)

                            // 4 Kenar Tutamaç Çizgileri (Geniş, tutması kolay ve belirgin göstergeler)
                            val edgeBarLen = 36.dp.toPx()
                            val edgeStroke = 5.dp.toPx()
                            val midX = (cLeft + cRight) / 2f
                            val midY = (cTop + cBottom) / 2f

                            // Üst Kenar
                            drawLine(Color.White, Offset(midX - edgeBarLen / 2f, cTop), Offset(midX + edgeBarLen / 2f, cTop), strokeWidth = edgeStroke, cap = StrokeCap.Round)
                            // Alt Kenar
                            drawLine(Color.White, Offset(midX - edgeBarLen / 2f, cBottom), Offset(midX + edgeBarLen / 2f, cBottom), strokeWidth = edgeStroke, cap = StrokeCap.Round)
                            // Sol Kenar
                            drawLine(Color.White, Offset(cLeft, midY - edgeBarLen / 2f), Offset(cLeft, midY + edgeBarLen / 2f), strokeWidth = edgeStroke, cap = StrokeCap.Round)
                            // Sağ Kenar
                            drawLine(Color.White, Offset(cRight, midY - edgeBarLen / 2f), Offset(cRight, midY + edgeBarLen / 2f), strokeWidth = edgeStroke, cap = StrokeCap.Round)
                        }
                    }

                    // METİN KATMANLARI (Görsele Birebir Sabitli, Akıcı Sürüklenebilir)
                    textOverlays.forEach { item ->
                        key(item.id) {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        if (dstW <= 0f || dstH <= 0f) {
                                            IntOffset.Zero
                                        } else {
                                            val screenX = dstLeft + item.positionFraction.x * dstW
                                            val screenY = dstTop + item.positionFraction.y * dstH
                                            IntOffset(screenX.roundToInt(), screenY.roundToInt())
                                        }
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(item.bgColor)
                                    .border(
                                        width = if (activeTab == ImageEditorTab.TEXT) 1.5.dp else 1.dp,
                                        color = if (activeTab == ImageEditorTab.TEXT) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .pointerInput(item.id, activeTab, dstW, dstH) {
                                        if (activeTab == ImageEditorTab.DRAW || activeTab == ImageEditorTab.CROP) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = {
                                                recordSnapshot()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (dstW > 0f && dstH > 0f) {
                                                    val currentPos = item.positionFraction
                                                    val newNormX = (currentPos.x + dragAmount.x / dstW).coerceIn(0f, 0.95f)
                                                    val newNormY = (currentPos.y + dragAmount.y / dstH).coerceIn(0f, 0.95f)
                                                    item.positionFraction = Offset(newNormX, newNormY)
                                                }
                                            }
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.pointerInput(item.id) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                editingTextItem = item
                                                textInput = item.text
                                                textColor = item.color
                                                textBgType = when (item.bgColor) {
                                                    Color.Transparent -> 0
                                                    Color(0xCCFFFFFF) -> 2
                                                    else -> 1
                                                }
                                                textSizeChoice = item.fontSizeSp
                                                showAddTextDialog = true
                                            }
                                        )
                                    }
                                ) {
                                    Text(
                                        text = item.text,
                                        color = item.color,
                                        fontSize = item.fontSizeSp.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (activeTab == ImageEditorTab.TEXT) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Metni Düzenle",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    editingTextItem = item
                                                    textInput = item.text
                                                    textColor = item.color
                                                    textBgType = when (item.bgColor) {
                                                        Color.Transparent -> 0
                                                        Color(0xCCFFFFFF) -> 2
                                                        else -> 1
                                                    }
                                                    textSizeChoice = item.fontSizeSp
                                                    showAddTextDialog = true
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Metni Kaldır",
                                            tint = Color(0xFFFF5252),
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // En-Boy Oranı Hap Çipleri
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CropAspectRatio.values()) { ratio ->
                val isSelected = selectedRatio == ratio
                Surface(
                    onClick = { onRatioSelect(ratio) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF24242C),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ratio.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFFD4D4DC)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ratio.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else Color(0xFF888894),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Çevirme & Kırpma Eylemleri
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onRotateLeft,
                    shape = CircleShape,
                    color = Color(0xFF24242C),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Sola Döndür", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
                Surface(
                    onClick = onRotateRight,
                    shape = CircleShape,
                    color = Color(0xFF24242C),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Sağa Döndür", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
                Surface(
                    onClick = onFlipHorizontal,
                    shape = CircleShape,
                    color = Color(0xFF24242C),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Flip, contentDescription = "Yatay Aynala", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
                Surface(
                    onClick = onFlipVertical,
                    shape = CircleShape,
                    color = Color(0xFF24242C),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Dikey Aynala", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
            }

            Button(
                onClick = onApplyCrop,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kırp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Araç Seçici Segmentli Kapsül + Temizle Butonu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF24242C),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    DrawingMode.values().forEach { mode ->
                        val isSelected = currentMode == mode
                        Surface(
                            onClick = { onModeSelect(mode) },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        DrawingMode.PEN -> Icons.Default.Edit
                                        DrawingMode.HIGHLIGHTER -> Icons.Default.Brush
                                        DrawingMode.ERASER -> Icons.Default.AutoFixNormal
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFB0B0BC),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFB0B0BC)
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = onClearStrokes) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Tüm Çizimleri Temizle",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Renk Paleti (Silgi Açıkken Gizlenir)
        if (currentMode != DrawingMode.ERASER) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(paletteColors) { color ->
                    val isSelected = currentColor == color
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 34.dp else 28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelect(color) }
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0x44FFFFFF),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = if (color == Color.White) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Boyut Çubuğu + Anlık Canlı Çap Önizleme Noktası
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF24242C)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((currentSize.coerceIn(4f, 26f)).dp)
                        .clip(CircleShape)
                        .background(if (currentMode == DrawingMode.ERASER) Color.White else currentColor)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = currentSize,
                onValueChange = onSizeSelect,
                valueRange = 4f..48f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color(0xFF2E2E38)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${currentSize.roundToInt()}dp",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB0B0BC),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddTextClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Yeni Metin Ekle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasDeletedText) {
                    OutlinedButton(
                        onClick = onRestoreLastText,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Geri Al", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (textCount > 0) {
                    OutlinedButton(
                        onClick = onClearAllText,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x33EF5350)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kaldır ($textCount)", color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (textCount > 0) {
            Text(
                text = "💡 Metni parmağınızla tutarak görsel üzerinde istediğiniz yere sürükleyebilirsiniz.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB0B0BC),
                fontSize = 11.sp
            )
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
    onResetAdjustments: () -> Unit,
    onApplyAdjustments: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filtre Başlığı ve Sıfırla Butonu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Filtreler & Efektler",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB0B0BC),
                fontWeight = FontWeight.SemiBold
            )
            val hasAnyAdjustments = brightness != 0f || contrast != 1f || saturation != 1f || selectedFilter != FilterPreset.NONE
            if (hasAnyAdjustments) {
                TextButton(
                    onClick = onResetAdjustments,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Tümünü Sıfırla", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Hazır Filtreler
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FilterPreset.values()) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    onClick = { onFilterSelect(filter) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF24242C),
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF))
                ) {
                    Text(
                        text = filter.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFFD4D4DC),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Kaydırıcılar
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Parlaklık
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Brightness6, contentDescription = null, tint = Color(0xFFB0B0BC), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Parlaklık", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0B0BC), modifier = Modifier.width(60.dp))
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = -100f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF2E2E38)
                    )
                )
                Text("${brightness.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }

            // Kontrast
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Contrast, contentDescription = null, tint = Color(0xFFB0B0BC), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kontrast", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0B0BC), modifier = Modifier.width(60.dp))
                Slider(
                    value = contrast,
                    onValueChange = onContrastChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF2E2E38)
                    )
                )
                Text(String.format(java.util.Locale.US, "%.1fx", contrast), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }

            // Doygunluk
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFB0B0BC), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Doygunluk", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0B0BC), modifier = Modifier.width(60.dp))
                Slider(
                    value = saturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0.0f..2.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF2E2E38)
                    )
                )
                Text(String.format(java.util.Locale.US, "%.1fx", saturation), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }
        }

        Button(
            onClick = onApplyAdjustments,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Ayarları Görsele İşle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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

    // 1. Köşeler (Önce köşeler kontrol edilir)
    if (dist(touch.x, touch.y, left, top) <= threshold) return CropHandle.TOP_LEFT
    if (dist(touch.x, touch.y, right, top) <= threshold) return CropHandle.TOP_RIGHT
    if (dist(touch.x, touch.y, left, bottom) <= threshold) return CropHandle.BOTTOM_LEFT
    if (dist(touch.x, touch.y, right, bottom) <= threshold) return CropHandle.BOTTOM_RIGHT

    // 2. Kenarlar (Tüm kenar uzunluğu boyunca geniş yakalama alanı)
    val inXRange = touch.x in (left - threshold)..(right + threshold)
    val inYRange = touch.y in (top - threshold)..(bottom + threshold)

    if (inXRange) {
        if (kotlin.math.abs(touch.y - top) <= threshold) return CropHandle.TOP
        if (kotlin.math.abs(touch.y - bottom) <= threshold) return CropHandle.BOTTOM
    }
    if (inYRange) {
        if (kotlin.math.abs(touch.x - left) <= threshold) return CropHandle.LEFT
        if (kotlin.math.abs(touch.x - right) <= threshold) return CropHandle.RIGHT
    }

    // 3. Orta Alan (Kutuyu taşıma)
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
    aspectRatio: Float?,
    imgRatio: Float = 1f
): Rect {
    val minSize = 0.05f
    var left = current.left
    var top = current.top
    var right = current.right
    var bottom = current.bottom

    if (aspectRatio != null && aspectRatio > 0f && imgRatio > 0f) {
        val normRatio = aspectRatio / imgRatio

        when (handle) {
            CropHandle.BOTTOM_RIGHT -> {
                val newW = (right - left + dx).coerceIn(minSize, 1f - left)
                val newH = (newW / normRatio).coerceIn(minSize, 1f - top)
                val finalW = (newH * normRatio).coerceIn(minSize, 1f - left)
                right = left + finalW
                bottom = top + newH
            }
            CropHandle.BOTTOM_LEFT -> {
                val newW = (right - left - dx).coerceIn(minSize, right)
                val newH = (newW / normRatio).coerceIn(minSize, 1f - top)
                val finalW = (newH * normRatio).coerceIn(minSize, right)
                left = right - finalW
                bottom = top + newH
            }
            CropHandle.TOP_RIGHT -> {
                val newW = (right - left + dx).coerceIn(minSize, 1f - left)
                val newH = (newW / normRatio).coerceIn(minSize, bottom)
                val finalW = (newH * normRatio).coerceIn(minSize, 1f - left)
                right = left + finalW
                top = bottom - newH
            }
            CropHandle.TOP_LEFT -> {
                val newW = (right - left - dx).coerceIn(minSize, right)
                val newH = (newW / normRatio).coerceIn(minSize, bottom)
                val finalW = (newH * normRatio).coerceIn(minSize, right)
                left = right - finalW
                top = bottom - newH
            }
            CropHandle.TOP -> {
                val newH = (bottom - top - dy).coerceIn(minSize, bottom)
                val newW = (newH * normRatio).coerceIn(minSize, 1f)
                val finalH = (newW / normRatio).coerceIn(minSize, bottom)
                val centerX = (left + right) / 2f
                val halfW = newW / 2f
                left = if (centerX - halfW < 0f) 0f else if (centerX + halfW > 1f) 1f - newW else centerX - halfW
                right = left + newW
                top = bottom - finalH
            }
            CropHandle.BOTTOM -> {
                val newH = (bottom - top + dy).coerceIn(minSize, 1f - top)
                val newW = (newH * normRatio).coerceIn(minSize, 1f)
                val finalH = (newW / normRatio).coerceIn(minSize, 1f - top)
                val centerX = (left + right) / 2f
                val halfW = newW / 2f
                left = if (centerX - halfW < 0f) 0f else if (centerX + halfW > 1f) 1f - newW else centerX - halfW
                right = left + newW
                bottom = top + finalH
            }
            CropHandle.LEFT -> {
                val newW = (right - left - dx).coerceIn(minSize, right)
                val newH = (newW / normRatio).coerceIn(minSize, 1f)
                val finalW = (newH * normRatio).coerceIn(minSize, right)
                val centerY = (top + bottom) / 2f
                val halfH = newH / 2f
                top = if (centerY - halfH < 0f) 0f else if (centerY + halfH > 1f) 1f - newH else centerY - halfH
                bottom = top + newH
                left = right - finalW
            }
            CropHandle.RIGHT -> {
                val newW = (right - left + dx).coerceIn(minSize, 1f - left)
                val newH = (newW / normRatio).coerceIn(minSize, 1f)
                val finalW = (newH * normRatio).coerceIn(minSize, 1f - left)
                val centerY = (top + bottom) / 2f
                val halfH = newH / 2f
                top = if (centerY - halfH < 0f) 0f else if (centerY + halfH > 1f) 1f - newH else centerY - halfH
                bottom = top + newH
                right = left + finalW
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
    } else {
        // SERBEST (FREE) ŞEKİLLENDİRME - Kısıtlamasız, kullanıcı istediği kenar veya köşeyi serbestçe taşır/boyutlandırır
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
