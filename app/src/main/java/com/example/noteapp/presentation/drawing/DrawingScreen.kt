package com.example.noteapp.presentation.drawing

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.noteapp.media.FileStorageHelper
import kotlin.math.*

/**
 * Çizim aracı tipleri
 */
enum class DrawingTool(val title: String) {
    PEN("Kalem"),
    HIGHLIGHTER("Fosforlu"),
    SHAPES("Şekiller"),
    ERASER("Silgi")
}

/**
 * Geometrik Şekil Tipleri
 */
enum class ShapeType(val title: String) {
    LINE("Çizgi"),
    ARROW("Ok"),
    RECTANGLE("Dikdörtgen"),
    CIRCLE("Çember")
}

/**
 * Tuval Kağıt Desenleri
 */
enum class CanvasPattern(val title: String) {
    BLANK("Düz"),
    LINED("Çizgili"),
    GRID("Kareli"),
    DOTS("Noktalı"),
    CHALKBOARD("Kara Tahta")
}

/**
 * Tuval üzerindeki çizim elemanları
 */
sealed class DrawElement {
    data class FreeHand(
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float,
        val alpha: Float = 1.0f,
        val isEraser: Boolean = false
    ) : DrawElement()

    data class Shape(
        val shapeType: ShapeType,
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val alpha: Float = 1.0f,
        val isFilled: Boolean = false
    ) : DrawElement()
}

val StandardPalette = listOf(
    Color(0xFF212121), // Siyah / Koyu Gri
    Color(0xFFFFFFFF), // Beyaz
    Color(0xFFE53935), // Kırmızı
    Color(0xFFEC407A), // Pembe
    Color(0xFF8E24AA), // Mor
    Color(0xFF1E88E5), // Mavi
    Color(0xFF03A9F4), // Açık Mavi
    Color(0xFF00ACC1), // Camgöbeği
    Color(0xFF43A047), // Yeşil
    Color(0xFF7CB342), // Açık Yeşil
    Color(0xFFFDD835), // Sarı
    Color(0xFFFB8C00), // Turuncu
    Color(0xFF6D4C41), // Kahverengi
    Color(0xFF757575)  // Gri
)

val HighlighterPalette = listOf(
    Color(0xFFFFEB3B), // Fosforlu Sarı
    Color(0xFF69F0AE), // Fosforlu Yeşil
    Color(0xFF40C4FF), // Fosforlu Mavi
    Color(0xFFFF4081), // Fosforlu Pembe
    Color(0xFFFF9100), // Fosforlu Turuncu
    Color(0xFFE040FB)  // Fosforlu Mor
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onDrawingSaved: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Tuval Elemanları ve Geçmiş (Undo/Redo)
    var elements by remember { mutableStateOf(listOf<DrawElement>()) }
    var redoStack by remember { mutableStateOf(listOf<DrawElement>()) }

    // Aktif Araç ve Ayarları
    var activeTool by remember { mutableStateOf(DrawingTool.PEN) }
    var selectedShapeType by remember { mutableStateOf(ShapeType.RECTANGLE) }
    var isShapeFilled by remember { mutableStateOf(false) }

    // Renk ve Kalınlıklar
    var penColor by remember { mutableStateOf(StandardPalette[0]) }
    var highlighterColor by remember { mutableStateOf(HighlighterPalette[0]) }
    var penStrokeWidth by remember { mutableFloatStateOf(10f) }
    var highlighterStrokeWidth by remember { mutableFloatStateOf(32f) }
    var eraserStrokeWidth by remember { mutableFloatStateOf(36f) }
    var shapeStrokeWidth by remember { mutableFloatStateOf(8f) }

    // Kağıt Deseni
    var canvasPattern by remember { mutableStateOf(CanvasPattern.BLANK) }
    var showPatternDialog by remember { mutableStateOf(false) }
    var includePatternInExport by remember { mutableStateOf(true) }

    // Temizleme Onay İletişim Kutusu
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Canlı Çizim Noktaları / Sürükleme Durumu
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeCurrent by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val canvasBgColor = if (canvasPattern == CanvasPattern.CHALKBOARD) Color(0xFF1E1E1E) else Color.White

    // Kağıt değiştiğinde varsayılan rengi akıllıca ayarla
    LaunchedEffect(canvasPattern) {
        if (canvasPattern == CanvasPattern.CHALKBOARD && penColor == StandardPalette[0]) {
            penColor = Color.White
        } else if (canvasPattern != CanvasPattern.CHALKBOARD && penColor == Color.White) {
            penColor = StandardPalette[0]
        }
    }

    // Bitmap Olarak Dışa Aktarma
    fun saveCanvasToBitmap(): String? {
        val width = if (canvasSize.width > 0) canvasSize.width else 1080
        val height = if (canvasSize.height > 0) canvasSize.height else 1920

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Arka Plan Rengi
        val bgArgb = if (canvasPattern == CanvasPattern.CHALKBOARD) {
            android.graphics.Color.rgb(30, 30, 30)
        } else {
            android.graphics.Color.WHITE
        }
        canvas.drawColor(bgArgb)

        // Şablon Deseni Çizimi (Eğer dahil edilmek isteniyorsa)
        if (includePatternInExport && canvasPattern != CanvasPattern.BLANK) {
            val patternPaint = Paint().apply {
                isAntiAlias = true
                strokeWidth = 2f
            }
            when (canvasPattern) {
                CanvasPattern.LINED -> {
                    patternPaint.color = if (canvasPattern == CanvasPattern.CHALKBOARD) {
                        android.graphics.Color.argb(50, 255, 255, 255)
                    } else {
                        android.graphics.Color.argb(50, 30, 136, 229)
                    }
                    val stepPx = 100f
                    var y = stepPx
                    while (y < height) {
                        canvas.drawLine(0f, y, width.toFloat(), y, patternPaint)
                        y += stepPx
                    }
                }
                CanvasPattern.GRID -> {
                    patternPaint.color = if (canvasPattern == CanvasPattern.CHALKBOARD) {
                        android.graphics.Color.argb(40, 255, 255, 255)
                    } else {
                        android.graphics.Color.argb(40, 0, 0, 0)
                    }
                    val stepPx = 80f
                    var x = stepPx
                    while (x < width) {
                        canvas.drawLine(x, 0f, x, height.toFloat(), patternPaint)
                        x += stepPx
                    }
                    var y = stepPx
                    while (y < height) {
                        canvas.drawLine(0f, y, width.toFloat(), y, patternPaint)
                        y += stepPx
                    }
                }
                CanvasPattern.DOTS -> {
                    patternPaint.color = if (canvasPattern == CanvasPattern.CHALKBOARD) {
                        android.graphics.Color.argb(70, 255, 255, 255)
                    } else {
                        android.graphics.Color.argb(60, 0, 0, 0)
                    }
                    val stepPx = 80f
                    var x = stepPx
                    while (x < width) {
                        var y = stepPx
                        while (y < height) {
                            canvas.drawCircle(x, y, 3f, patternPaint)
                            y += stepPx
                        }
                        x += stepPx
                    }
                }
                else -> {}
            }
        }

        // Çizim Elemanlarını Bitmap Üzerine Çiz
        val strokePaint = Paint().apply {
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        for (el in elements) {
            when (el) {
                is DrawElement.FreeHand -> {
                    if (el.points.size < 2) continue
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = el.strokeWidth

                    if (el.isEraser) {
                        strokePaint.color = bgArgb
                        strokePaint.alpha = 255
                    } else {
                        strokePaint.color = el.color.toArgb()
                        strokePaint.alpha = (el.alpha * 255).roundToInt().coerceIn(0, 255)
                    }

                    val androidPath = AndroidPath()
                    androidPath.moveTo(el.points.first().x, el.points.first().y)
                    for (i in 1 until el.points.size) {
                        val prev = el.points[i - 1]
                        val curr = el.points[i]
                        val midX = (prev.x + curr.x) / 2f
                        val midY = (prev.y + curr.y) / 2f
                        androidPath.quadTo(prev.x, prev.y, midX, midY)
                    }
                    androidPath.lineTo(el.points.last().x, el.points.last().y)
                    canvas.drawPath(androidPath, strokePaint)
                }
                is DrawElement.Shape -> {
                    strokePaint.color = el.color.toArgb()
                    strokePaint.alpha = (el.alpha * 255).roundToInt().coerceIn(0, 255)
                    strokePaint.strokeWidth = el.strokeWidth
                    strokePaint.style = if (el.isFilled) Paint.Style.FILL else Paint.Style.STROKE

                    when (el.shapeType) {
                        ShapeType.LINE -> {
                            canvas.drawLine(el.start.x, el.start.y, el.end.x, el.end.y, strokePaint)
                        }
                        ShapeType.ARROW -> {
                            canvas.drawLine(el.start.x, el.start.y, el.end.x, el.end.y, strokePaint)
                            val angle = atan2(el.end.y - el.start.y, el.end.x - el.start.x)
                            val arrowLen = (el.strokeWidth * 3.5f).coerceIn(24f, 60f)
                            val arrowAngle = Math.PI / 6.0
                            val x1 = (el.end.x - arrowLen * cos(angle - arrowAngle)).toFloat()
                            val y1 = (el.end.y - arrowLen * sin(angle - arrowAngle)).toFloat()
                            val x2 = (el.end.x - arrowLen * cos(angle + arrowAngle)).toFloat()
                            val y2 = (el.end.y - arrowLen * sin(angle + arrowAngle)).toFloat()
                            canvas.drawLine(el.end.x, el.end.y, x1, y1, strokePaint)
                            canvas.drawLine(el.end.x, el.end.y, x2, y2, strokePaint)
                        }
                        ShapeType.RECTANGLE -> {
                            val left = min(el.start.x, el.end.x)
                            val top = min(el.start.y, el.end.y)
                            val right = max(el.start.x, el.end.x)
                            val bottom = max(el.start.y, el.end.y)
                            canvas.drawRect(left, top, right, bottom, strokePaint)
                        }
                        ShapeType.CIRCLE -> {
                            val left = min(el.start.x, el.end.x)
                            val top = min(el.start.y, el.end.y)
                            val right = max(el.start.x, el.end.x)
                            val bottom = max(el.start.y, el.end.y)
                            canvas.drawOval(RectF(left, top, right, bottom), strokePaint)
                        }
                    }
                }
            }
        }

        return FileStorageHelper.saveDrawingBitmap(context, bitmap)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Çizim & Not Tuvali", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${activeTool.title} • ${canvasPattern.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Kağıt Deseni Seçici
                    IconButton(onClick = { showPatternDialog = true }) {
                        Icon(Icons.Default.GridOn, contentDescription = "Kağıt Deseni")
                    }

                    // Geri Al (Undo)
                    IconButton(
                        onClick = {
                            if (elements.isNotEmpty()) {
                                val last = elements.last()
                                elements = elements.dropLast(1)
                                redoStack = redoStack + last
                            }
                        },
                        enabled = elements.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Geri Al")
                    }

                    // İleri Al (Redo)
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val restored = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                elements = elements + restored
                            }
                        },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "İleri Al")
                    }

                    // Temizle Butonu
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        enabled = elements.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Temizle")
                    }

                    // Kaydet
                    IconButton(
                        onClick = {
                            val savedPath = saveCanvasToBitmap()
                            if (savedPath != null) {
                                onDrawingSaved(savedPath)
                            }
                        },
                        enabled = elements.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = "Kaydet",
                            tint = if (elements.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Satır: Ana Araç Seçimi (Kalem, Fosforlu, Şekil, Silgi)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Kalem
                        FilterChip(
                            selected = activeTool == DrawingTool.PEN,
                            onClick = { activeTool = DrawingTool.PEN },
                            label = { Text("Kalem") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        // Fosforlu Kalem
                        FilterChip(
                            selected = activeTool == DrawingTool.HIGHLIGHTER,
                            onClick = { activeTool = DrawingTool.HIGHLIGHTER },
                            label = { Text("Fosforlu") },
                            leadingIcon = {
                                Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        // Şekiller
                        FilterChip(
                            selected = activeTool == DrawingTool.SHAPES,
                            onClick = { activeTool = DrawingTool.SHAPES },
                            label = { Text("Şekil") },
                            leadingIcon = {
                                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        // Silgi
                        FilterChip(
                            selected = activeTool == DrawingTool.ERASER,
                            onClick = { activeTool = DrawingTool.ERASER },
                            label = { Text("Silgi") },
                            leadingIcon = {
                                Icon(Icons.Default.AutoFixNormal, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }

                    // 2. Satır: Seçilen Araca Özel Ayarlar
                    when (activeTool) {
                        DrawingTool.PEN -> {
                            // Kalem Kalınlık ve Renk Paleti
                            ToolSettingRow(
                                selectedSize = penStrokeWidth,
                                onSizeSelected = { penStrokeWidth = it },
                                sizeOptions = listOf(4f to "Çok İnce", 8f to "İnce", 14f to "Orta", 24f to "Kalın"),
                                currentColor = penColor,
                                onColorSelected = { penColor = it },
                                palette = StandardPalette
                            )
                        }
                        DrawingTool.HIGHLIGHTER -> {
                            // Fosforlu Kalem Kalınlık ve Canlı Pastel Palet
                            ToolSettingRow(
                                selectedSize = highlighterStrokeWidth,
                                onSizeSelected = { highlighterStrokeWidth = it },
                                sizeOptions = listOf(20f to "İnce", 32f to "Orta", 48f to "Geniş"),
                                currentColor = highlighterColor,
                                onColorSelected = { highlighterColor = it },
                                palette = HighlighterPalette
                            )
                        }
                        DrawingTool.SHAPES -> {
                            // Şekil Seçimi ve Dolgu Durumu
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Şekil Türleri (Çizgi, Ok, Dikdörtgen, Çember)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ShapeType.values().forEach { shape ->
                                            InputChip(
                                                selected = selectedShapeType == shape,
                                                onClick = { selectedShapeType = shape },
                                                label = { Text(shape.title, style = MaterialTheme.typography.bodySmall) }
                                            )
                                        }
                                    }

                                    // Dolgu / Boş Çizim Butonu (Sadece Dikdörtgen ve Çember için)
                                    if (selectedShapeType == ShapeType.RECTANGLE || selectedShapeType == ShapeType.CIRCLE) {
                                        FilterChip(
                                            selected = isShapeFilled,
                                            onClick = { isShapeFilled = !isShapeFilled },
                                            label = { Text(if (isShapeFilled) "Dolu" else "Çerçeve", style = MaterialTheme.typography.bodySmall) },
                                            leadingIcon = {
                                                Icon(Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        )
                                    }
                                }

                                // Şekil Renk Paleti
                                ToolSettingRow(
                                    selectedSize = shapeStrokeWidth,
                                    onSizeSelected = { shapeStrokeWidth = it },
                                    sizeOptions = listOf(4f to "İnce", 8f to "Orta", 14f to "Kalın"),
                                    currentColor = penColor,
                                    onColorSelected = { penColor = it },
                                    palette = StandardPalette
                                )
                            }
                        }
                        DrawingTool.ERASER -> {
                            // Silgi Kalınlık Seçenekleri ve Bilgi
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(16f to "İnce", 32f to "Orta", 54f to "Geniş", 80f to "Ekstra").forEach { (size, label) ->
                                        FilterChip(
                                            selected = eraserStrokeWidth == size,
                                            onClick = { eraserStrokeWidth = size },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { showClearConfirmDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Hepsini Sil")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (canvasPattern == CanvasPattern.CHALKBOARD) Color(0xFF121212) else Color(0xFFECEFF1))
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(canvasBgColor)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(activeTool, selectedShapeType, penColor, highlighterColor, isShapeFilled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                when (activeTool) {
                                    DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                                        currentPoints = listOf(offset)
                                    }
                                    DrawingTool.SHAPES -> {
                                        shapeStart = offset
                                        shapeCurrent = offset
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                when (activeTool) {
                                    DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                                        currentPoints = currentPoints + change.position
                                    }
                                    DrawingTool.SHAPES -> {
                                        shapeCurrent = change.position
                                    }
                                }
                            },
                            onDragEnd = {
                                when (activeTool) {
                                    DrawingTool.PEN -> {
                                        if (currentPoints.isNotEmpty()) {
                                            elements = elements + DrawElement.FreeHand(
                                                points = currentPoints,
                                                color = penColor,
                                                strokeWidth = penStrokeWidth,
                                                alpha = 1.0f
                                            )
                                            redoStack = emptyList()
                                            currentPoints = emptyList()
                                        }
                                    }
                                    DrawingTool.HIGHLIGHTER -> {
                                        if (currentPoints.isNotEmpty()) {
                                            elements = elements + DrawElement.FreeHand(
                                                points = currentPoints,
                                                color = highlighterColor,
                                                strokeWidth = highlighterStrokeWidth,
                                                alpha = 0.38f
                                            )
                                            redoStack = emptyList()
                                            currentPoints = emptyList()
                                        }
                                    }
                                    DrawingTool.ERASER -> {
                                        if (currentPoints.isNotEmpty()) {
                                            elements = elements + DrawElement.FreeHand(
                                                points = currentPoints,
                                                color = canvasBgColor,
                                                strokeWidth = eraserStrokeWidth,
                                                isEraser = true
                                            )
                                            redoStack = emptyList()
                                            currentPoints = emptyList()
                                        }
                                    }
                                    DrawingTool.SHAPES -> {
                                        val start = shapeStart
                                        val end = shapeCurrent
                                        if (start != null && end != null) {
                                            elements = elements + DrawElement.Shape(
                                                shapeType = selectedShapeType,
                                                start = start,
                                                end = end,
                                                color = penColor,
                                                strokeWidth = shapeStrokeWidth,
                                                isFilled = isShapeFilled
                                            )
                                            redoStack = emptyList()
                                            shapeStart = null
                                            shapeCurrent = null
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                currentPoints = emptyList()
                                shapeStart = null
                                shapeCurrent = null
                            }
                        )
                    }
            ) {
                // 1. Kağıt Desenini Çiz (Lined, Grid, Dots)
                drawCanvasPattern(canvasPattern, canvasBgColor)

                // 2. Kaydedilmiş Tüm Çizim Elemanlarını Çiz
                for (el in elements) {
                    drawElement(el, canvasBgColor)
                }

                // 3. Canlı Olarak Çizilmekte Olan Serbest Çizgiyi Çiz
                if (currentPoints.size > 1) {
                    val liveColor = when (activeTool) {
                        DrawingTool.PEN -> penColor
                        DrawingTool.HIGHLIGHTER -> highlighterColor.copy(alpha = 0.38f)
                        DrawingTool.ERASER -> canvasBgColor
                        else -> penColor
                    }
                    val liveWidth = when (activeTool) {
                        DrawingTool.PEN -> penStrokeWidth
                        DrawingTool.HIGHLIGHTER -> highlighterStrokeWidth
                        DrawingTool.ERASER -> eraserStrokeWidth
                        else -> penStrokeWidth
                    }

                    val path = Path().apply {
                        moveTo(currentPoints.first().x, currentPoints.first().y)
                        for (i in 1 until currentPoints.size) {
                            val prev = currentPoints[i - 1]
                            val curr = currentPoints[i]
                            val midX = (prev.x + curr.x) / 2f
                            val midY = (prev.y + curr.y) / 2f
                            quadraticTo(prev.x, prev.y, midX, midY)
                        }
                        lineTo(currentPoints.last().x, currentPoints.last().y)
                    }
                    drawPath(
                        path = path,
                        color = liveColor,
                        style = Stroke(width = liveWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 4. Canlı Olarak Çizilmekte Olan Şekli Göster (Live Preview)
                val s = shapeStart
                val e = shapeCurrent
                if (activeTool == DrawingTool.SHAPES && s != null && e != null) {
                    drawShapeElement(
                        shapeType = selectedShapeType,
                        start = s,
                        end = e,
                        color = penColor,
                        strokeWidth = shapeStrokeWidth,
                        isFilled = isShapeFilled
                    )
                }
            }
        }
    }

    // Kağıt Deseni Seçim İletişim Kutusu
    if (showPatternDialog) {
        AlertDialog(
            onDismissRequest = { showPatternDialog = false },
            title = { Text("Kağıt Şablonu Seç") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasPattern.values().forEach { pattern ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    canvasPattern = pattern
                                    showPatternDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = canvasPattern == pattern,
                                onClick = {
                                    canvasPattern = pattern
                                    showPatternDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(pattern.title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Şablonu Görsele Dahil Et", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = includePatternInExport,
                            onCheckedChange = { includePatternInExport = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPatternDialog = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    // Tuvali Temizleme Onay Kutusu
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Tuvali Temizle") },
            text = { Text("Tüm çizimler silinecek. Onaylıyor musunuz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        elements = emptyList()
                        redoStack = emptyList()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Evet, Temizle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

/**
 * Kalınlık ve Renk Paleti Satırı
 */
@Composable
private fun ToolSettingRow(
    selectedSize: Float,
    onSizeSelected: (Float) -> Unit,
    sizeOptions: List<Pair<Float, String>>,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    palette: List<Color>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Boyut Seçenekleri
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sizeOptions.forEach { (size, label) ->
                    FilterChip(
                        selected = selectedSize == size,
                        onClick = { onSizeSelected(size) },
                        label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            // Aktif Renk Önizleme Rozeti
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(1.5.dp, Color.LightGray, CircleShape)
            )
        }

        // Renk Paleti Listesi
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(palette) { color ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (currentColor == color) 3.dp else 1.dp,
                            color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}

/**
 * Kağıt Deseni Çizimi (Lined, Grid, Dots)
 */
private fun DrawScope.drawCanvasPattern(pattern: CanvasPattern, bgColor: Color) {
    if (pattern == CanvasPattern.BLANK) return

    val isDark = pattern == CanvasPattern.CHALKBOARD
    val lineColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF1E88E5).copy(alpha = 0.15f)
    val gridColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    val dotColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f)

    when (pattern) {
        CanvasPattern.LINED -> {
            val step = 38.dp.toPx()
            var y = step
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5f
                )
                y += step
            }
        }
        CanvasPattern.GRID -> {
            val step = 28.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }
        CanvasPattern.DOTS -> {
            val step = 28.dp.toPx()
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(
                        color = dotColor,
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                    y += step
                }
                x += step
            }
        }
        else -> {}
    }
}

/**
 * Tuval Elemanı Çizimi (Compose Canvas)
 */
private fun DrawScope.drawElement(el: DrawElement, canvasBgColor: Color) {
    when (el) {
        is DrawElement.FreeHand -> {
            if (el.points.size < 2) return
            val drawColor = if (el.isEraser) canvasBgColor else el.color.copy(alpha = el.alpha)

            val path = Path().apply {
                moveTo(el.points.first().x, el.points.first().y)
                for (i in 1 until el.points.size) {
                    val prev = el.points[i - 1]
                    val curr = el.points[i]
                    val midX = (prev.x + curr.x) / 2f
                    val midY = (prev.y + curr.y) / 2f
                    quadraticTo(prev.x, prev.y, midX, midY)
                }
                lineTo(el.points.last().x, el.points.last().y)
            }
            drawPath(
                path = path,
                color = drawColor,
                style = Stroke(width = el.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        is DrawElement.Shape -> {
            drawShapeElement(
                shapeType = el.shapeType,
                start = el.start,
                end = el.end,
                color = el.color.copy(alpha = el.alpha),
                strokeWidth = el.strokeWidth,
                isFilled = el.isFilled
            )
        }
    }
}

/**
 * Geometrik Şekil Çizimi (Compose Canvas)
 */
private fun DrawScope.drawShapeElement(
    shapeType: ShapeType,
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float,
    isFilled: Boolean
) {
    when (shapeType) {
        ShapeType.LINE -> {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        ShapeType.ARROW -> {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            val angle = atan2(end.y - start.y, end.x - start.x)
            val arrowLength = (strokeWidth * 3.5f).coerceIn(24f, 60f)
            val arrowAngle = Math.PI / 6.0
            val p1 = Offset(
                (end.x - arrowLength * cos(angle - arrowAngle)).toFloat(),
                (end.y - arrowLength * sin(angle - arrowAngle)).toFloat()
            )
            val p2 = Offset(
                (end.x - arrowLength * cos(angle + arrowAngle)).toFloat(),
                (end.y - arrowLength * sin(angle + arrowAngle)).toFloat()
            )
            drawLine(color = color, start = end, end = p1, strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color = color, start = end, end = p2, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        ShapeType.RECTANGLE -> {
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            val width = abs(end.x - start.x)
            val height = abs(end.y - start.y)
            val rectTopLeft = Offset(left, top)
            val rectSize = Size(width, height)

            if (isFilled) {
                drawRect(color = color, topLeft = rectTopLeft, size = rectSize)
            } else {
                drawRect(
                    color = color,
                    topLeft = rectTopLeft,
                    size = rectSize,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
        ShapeType.CIRCLE -> {
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            val width = abs(end.x - start.x)
            val height = abs(end.y - start.y)
            val ovalTopLeft = Offset(left, top)
            val ovalSize = Size(width, height)

            if (isFilled) {
                drawOval(color = color, topLeft = ovalTopLeft, size = ovalSize)
            } else {
                drawOval(
                    color = color,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}
