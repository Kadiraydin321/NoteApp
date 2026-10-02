package com.example.noteapp.presentation.drawing

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
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
    Color(0xFF3F51B5), // İndigo
    Color(0xFF1E88E5), // Mavi
    Color(0xFF03A9F4), // Açık Mavi
    Color(0xFF00ACC1), // Camgöbeği
    Color(0xFF00897B), // Teal
    Color(0xFF43A047), // Yeşil
    Color(0xFF7CB342), // Açık Yeşil
    Color(0xFFFDD835), // Sarı
    Color(0xFFFB8C00), // Turuncu
    Color(0xFFD84315), // Koyu Turuncu
    Color(0xFF6D4C41), // Kahverengi
    Color(0xFF757575), // Gri
    // Pastel & Not Defteri Estetik Tonları
    Color(0xFFA29BFE), // Pastel Lavanta
    Color(0xFF74B9FF), // Pastel Bebek Mavisi
    Color(0xFF81ECEC), // Pastel Turkuaz
    Color(0xFF55E6C1), // Pastel Nane
    Color(0xFFFDCB6E), // Pastel Hardal
    Color(0xFFFAB1A0), // Pastel Şeftali
    Color(0xFFFF7675), // Pastel Mercan
    Color(0xFFFD79A8), // Pastel Pembe
    Color(0xFF636E72)  // Slate Gri
)

val HighlighterPalette = listOf(
    Color(0xFFFFEB3B), // Fosforlu Sarı
    Color(0xFF69F0AE), // Fosforlu Yeşil
    Color(0xFF40C4FF), // Fosforlu Mavi
    Color(0xFFFF4081), // Fosforlu Pembe
    Color(0xFFFF9100), // Fosforlu Turuncu
    Color(0xFFE040FB), // Fosforlu Mor
    Color(0xFFFF5252), // Fosforlu Mercan
    Color(0xFF64FFDA)  // Fosforlu Camgöbeği
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onDrawingSaved: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 400

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
                    Text("Çizim", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
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

                    // Geri / İleri Al Kapsülü (Animated Capsule)
                    AnimatedVisibility(
                        visible = elements.isNotEmpty() || redoStack.isNotEmpty(),
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (elements.isNotEmpty()) {
                                            val last = elements.last()
                                            elements = elements.dropLast(1)
                                            redoStack = redoStack + last
                                        }
                                    },
                                    enabled = elements.isNotEmpty(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Geri Al",
                                        tint = if (elements.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (redoStack.isNotEmpty()) {
                                            val restored = redoStack.last()
                                            redoStack = redoStack.dropLast(1)
                                            elements = elements + restored
                                        }
                                    },
                                    enabled = redoStack.isNotEmpty(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Redo,
                                        contentDescription = "İleri Al",
                                        tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Temizle Butonu (Yalnızca tuvalde çizim varsa görünür)
                    AnimatedVisibility(
                        visible = elements.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true }
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Temizle",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Kaydet
                    Button(
                        onClick = {
                            val savedPath = saveCanvasToBitmap()
                            if (savedPath != null) {
                                onDrawingSaved(savedPath)
                            }
                        },
                        enabled = elements.isNotEmpty(),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = if (isCompactScreen) PaddingValues(horizontal = 10.dp, vertical = 4.dp) else PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                        if (!isCompactScreen) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kaydet", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                }
            )
        },
        bottomBar = {
            val isAppDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val studioBgColor = if (isAppDark) Color(0xFF22252A) else Color(0xFFFFFFFF)
            val studioBorderColor = if (isAppDark) Color(0x30FFFFFF) else Color(0x18000000)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Üst Kısım: Seçili Araca Göre Hızlı Ayar Kartı (Renkler, Boyutlar, Şekiller)
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(22.dp),
                    color = studioBgColor,
                    border = BorderStroke(1.dp, studioBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = activeTool,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
                        },
                        label = "ActiveToolStudioRow"
                    ) { tool ->
                        when (tool) {
                            DrawingTool.PEN -> {
                                PenStudioRow(
                                    strokeWidth = penStrokeWidth,
                                    onStrokeWidthChange = { penStrokeWidth = it },
                                    currentColor = penColor,
                                    onColorChange = { penColor = it }
                                )
                            }
                            DrawingTool.HIGHLIGHTER -> {
                                HighlighterStudioRow(
                                    strokeWidth = highlighterStrokeWidth,
                                    onStrokeWidthChange = { highlighterStrokeWidth = it },
                                    currentColor = highlighterColor,
                                    onColorChange = { highlighterColor = it }
                                )
                            }
                            DrawingTool.SHAPES -> {
                                ShapesStudioRow(
                                    selectedShape = selectedShapeType,
                                    onShapeSelect = { selectedShapeType = it },
                                    isFilled = isShapeFilled,
                                    onToggleFill = { isShapeFilled = !isShapeFilled },
                                    currentColor = penColor,
                                    onColorChange = { penColor = it }
                                )
                            }
                            DrawingTool.ERASER -> {
                                EraserStudioRow(
                                    strokeWidth = eraserStrokeWidth,
                                    onStrokeWidthChange = { eraserStrokeWidth = it },
                                    onClearAll = { showClearConfirmDialog = true }
                                )
                            }
                        }
                    }
                }

                // 2. Alt Kısım: Ana Araç Seçim Kapsülü (Kalem, Fosforlu, Şekil, Silgi)
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(32.dp),
                    color = studioBgColor,
                    border = BorderStroke(1.dp, studioBorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DrawingTool.values().forEach { tool ->
                            val isSelected = activeTool == tool
                            val icon = when (tool) {
                                DrawingTool.PEN -> Icons.Default.Edit
                                DrawingTool.HIGHLIGHTER -> Icons.Default.BorderColor
                                DrawingTool.SHAPES -> Icons.Default.Category
                                DrawingTool.ERASER -> Icons.Default.AutoFixNormal
                            }

                            FilledTonalIconButton(
                                onClick = { activeTool = tool },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(icon, contentDescription = tool.title, modifier = Modifier.size(19.dp))
                                    if (tool != DrawingTool.ERASER) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val indicatorColor = when (tool) {
                                            DrawingTool.PEN, DrawingTool.SHAPES -> penColor
                                            DrawingTool.HIGHLIGHTER -> highlighterColor
                                            else -> Color.Transparent
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(indicatorColor)
                                        )
                                    }
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
                .background(canvasBgColor)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
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

    // Kağıt Deseni Seçim İletişim Kutusu (Görsel Kart Izgarası)
    if (showPatternDialog) {
        AlertDialog(
            onDismissRequest = { showPatternDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.GridOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Kağıt Şablonu Seç", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Notlarınız ve eskizleriniz için bir tuval deseni belirleyin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Sıra (Düz, Çizgili, Kareli)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(CanvasPattern.BLANK, CanvasPattern.LINED, CanvasPattern.GRID).forEach { pattern ->
                            PatternCardItem(
                                pattern = pattern,
                                isSelected = canvasPattern == pattern,
                                onClick = {
                                    canvasPattern = pattern
                                    showPatternDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 2. Sıra (Noktalı, Kara Tahta)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(CanvasPattern.DOTS, CanvasPattern.CHALKBOARD).forEach { pattern ->
                            PatternCardItem(
                                pattern = pattern,
                                isSelected = canvasPattern == pattern,
                                onClick = {
                                    canvasPattern = pattern
                                    showPatternDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Şablonu Görsele Dahil Et", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Kaydedilen çizime kılavuz çizgileri işlenir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = includePatternInExport,
                            onCheckedChange = { includePatternInExport = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPatternDialog = false }) {
                    Text("Kapat")
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
 * Kalem Ayar Satırı: Canlı Önizleme + Kalınlık Noktaları + Renk Paleti
 */
@Composable
private fun PenStudioRow(
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    currentColor: Color,
    onColorChange: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Canlı Çizgi Boyutu ve Rengi Önizleme
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size((strokeWidth * 0.8f).coerceIn(4f, 22f).dp)
                    .clip(CircleShape)
                    .background(currentColor)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Kalınlık Seçimi (Görsel Noktalar)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(4f to 4.dp, 8f to 8.dp, 14f to 12.dp, 24f to 16.dp).forEach { (size, dotDp) ->
                val isSelected = strokeWidth == size
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onStrokeWidthChange(size) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotDp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Renk Paleti
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            items(StandardPalette) { color ->
                val isSelected = currentColor == color
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 30.dp else 26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x30888888),
                            shape = CircleShape
                        )
                        .clickable { onColorChange(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fosforlu Kalem Ayar Satırı: Canlı Önizleme + Kalınlık + Fosforlu Renkler
 */
@Composable
private fun HighlighterStudioRow(
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    currentColor: Color,
    onColorChange: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Canlı Önizleme
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size((strokeWidth * 0.45f).coerceIn(6f, 22f).dp)
                    .clip(CircleShape)
                    .background(currentColor.copy(alpha = 0.7f))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(20f to 10.dp, 32f to 14.dp, 48f to 18.dp).forEach { (size, dotDp) ->
                val isSelected = strokeWidth == size
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onStrokeWidthChange(size) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotDp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            items(HighlighterPalette) { color ->
                val isSelected = currentColor == color
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 30.dp else 26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x30888888),
                            shape = CircleShape
                        )
                        .clickable { onColorChange(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Şekiller Ayar Satırı
 */
@Composable
private fun ShapesStudioRow(
    selectedShape: ShapeType,
    onShapeSelect: (ShapeType) -> Unit,
    isFilled: Boolean,
    onToggleFill: () -> Unit,
    currentColor: Color,
    onColorChange: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Şekil Seçenekleri Kapsülü
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShapeType.values().forEach { shape ->
                    val isSelected = selectedShape == shape
                    val icon = when (shape) {
                        ShapeType.LINE -> Icons.Default.HorizontalRule
                        ShapeType.ARROW -> Icons.AutoMirrored.Filled.ArrowForward
                        ShapeType.RECTANGLE -> Icons.Default.CropSquare
                        ShapeType.CIRCLE -> Icons.Default.RadioButtonUnchecked
                    }
                    Surface(
                        onClick = { onShapeSelect(shape) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = shape.title,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (selectedShape == ShapeType.RECTANGLE || selectedShape == ShapeType.CIRCLE) {
                    Surface(
                        onClick = onToggleFill,
                        shape = RoundedCornerShape(8.dp),
                        color = if (isFilled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FormatColorFill,
                                contentDescription = "Dolgu",
                                tint = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(6.dp))

        // Renk Paleti
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            items(StandardPalette) { color ->
                val isSelected = currentColor == color
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 30.dp else 26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x30888888),
                            shape = CircleShape
                        )
                        .clickable { onColorChange(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kağıt Deseni Kart Bileşeni
 */
@Composable
private fun PatternCardItem(
    pattern: CanvasPattern,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x22888888), RoundedCornerShape(8.dp))
            ) {
                PatternThumbnailPreview(pattern = pattern)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
            Text(
                text = pattern.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Kağıt Deseni Küçük Önizleme Tuvali (Mini Canvas Thumbnail)
 */
@Composable
private fun PatternThumbnailPreview(
    pattern: CanvasPattern,
    modifier: Modifier = Modifier
) {
    val isChalk = pattern == CanvasPattern.CHALKBOARD
    val bgColor = if (isChalk) Color(0xFF1E1E1E) else Color.White
    val lineColor = if (isChalk) Color.White.copy(alpha = 0.25f) else Color(0xFF1E88E5).copy(alpha = 0.35f)
    val gridColor = if (isChalk) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)
    val dotColor = if (isChalk) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        when (pattern) {
            CanvasPattern.BLANK -> {}
            CanvasPattern.LINED -> {
                val step = 10.dp.toPx()
                var y = step
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
            CanvasPattern.GRID -> {
                val step = 10.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.8f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f)
                    y += step
                }
            }
            CanvasPattern.DOTS -> {
                val step = 10.dp.toPx()
                var x = step
                while (x < size.width) {
                    var y = step
                    while (y < size.height) {
                        drawCircle(dotColor, radius = 1.2f, center = Offset(x, y))
                        y += step
                    }
                    x += step
                }
            }
            CanvasPattern.CHALKBOARD -> {
                val step = 10.dp.toPx()
                var y = step
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f)
                    y += step
                }
            }
        }
    }
}

/**
 * Silgi Ayar Satırı
 */
@Composable
private fun EraserStudioRow(
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(16f to 10.dp, 32f to 14.dp, 54f to 18.dp, 80f to 22.dp).forEach { (size, dotDp) ->
                val isSelected = strokeWidth == size
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onStrokeWidthChange(size) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotDp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        TextButton(
            onClick = onClearAll,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Tuvali Temizle", style = MaterialTheme.typography.labelMedium)
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
