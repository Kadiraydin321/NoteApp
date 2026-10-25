package com.example.noteapp.desktop.presentation.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.noteapp.desktop.data.local.DatabaseHelper
import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class DesktopDrawTool(val title: String) {
    PEN("Kalem"),
    HIGHLIGHTER("Fosforlu"),
    SHAPES("Şekiller"),
    ERASER("Silgi")
}

enum class DesktopShapeType(val title: String) {
    LINE("Çizgi"),
    ARROW("Ok"),
    RECTANGLE("Dikdörtgen"),
    CIRCLE("Çember")
}

enum class DesktopCanvasPattern(val title: String) {
    BLANK("Düz"),
    LINED("Çizgili"),
    GRID("Kareli"),
    CHALKBOARD("Kara Tahta")
}

sealed class DesktopDrawElement {
    data class FreeHand(
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float,
        val alpha: Float = 1.0f,
        val isEraser: Boolean = false
    ) : DesktopDrawElement()

    data class Shape(
        val type: DesktopShapeType,
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val alpha: Float = 1.0f
    ) : DesktopDrawElement()
}

@Composable
fun DesktopDrawingDialog(
    onDismiss: () -> Unit,
    onSaveDrawing: (String) -> Unit
) {
    var selectedTool by remember { mutableStateOf(DesktopDrawTool.PEN) }
    var selectedShape by remember { mutableStateOf(DesktopShapeType.LINE) }
    var selectedPattern by remember { mutableStateOf(DesktopCanvasPattern.BLANK) }
    var strokeColor by remember { mutableStateOf(Color.White) }
    var strokeWidth by remember { mutableStateOf(4f) }

    val elements = remember { mutableStateListOf<DesktopDrawElement>() }
    val undoStack = remember { mutableStateListOf<List<DesktopDrawElement>>() }
    val redoStack = remember { mutableStateListOf<List<DesktopDrawElement>>() }

    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

    fun commitAction() {
        undoStack.add(elements.toList())
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(elements.toList())
            val previous = undoStack.removeAt(undoStack.size - 1)
            elements.clear()
            elements.addAll(previous)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(elements.toList())
            val next = redoStack.removeAt(redoStack.size - 1)
            elements.clear()
            elements.addAll(next)
        }
    }

    val palette = listOf(
        Color(0xFFFFFFFF),
        Color(0xFF38BDF8),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFFEF4444),
        Color(0xFFA855F7),
        Color(0xFFEC4899),
        Color(0xFF000000)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF090E17),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Brush, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                        Text("Çizim & Eskiz Stüdyosu", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Geri Al", tint = if (undoStack.isNotEmpty()) Color(0xFFCBD5E1) else Color(0xFF475569))
                        }
                        IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "İleri Al", tint = if (redoStack.isNotEmpty()) Color(0xFFCBD5E1) else Color(0xFF475569))
                        }
                        IconButton(onClick = {
                            if (elements.isNotEmpty()) {
                                commitAction()
                                elements.clear()
                            }
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Temizle", tint = Color(0xFFEF4444))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val width = 900
                                    val height = 650
                                    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                                    val g2d = image.createGraphics()
                                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                                    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

                                    val bgColor = if (selectedPattern == DesktopCanvasPattern.CHALKBOARD) java.awt.Color(27, 40, 35) else java.awt.Color(20, 26, 38)
                                    g2d.color = bgColor
                                    g2d.fillRect(0, 0, width, height)

                                    // Render elements
                                    for (elem in elements) {
                                        when (elem) {
                                            is DesktopDrawElement.FreeHand -> {
                                                if (elem.points.size > 1) {
                                                    val c = if (elem.isEraser) bgColor else java.awt.Color(elem.color.red, elem.color.green, elem.color.blue, elem.alpha)
                                                    g2d.color = c
                                                    g2d.stroke = BasicStroke(elem.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                                                    for (k in 0 until elem.points.size - 1) {
                                                        val p1 = elem.points[k]
                                                        val p2 = elem.points[k + 1]
                                                        g2d.drawLine(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                                                    }
                                                }
                                            }
                                            is DesktopDrawElement.Shape -> {
                                                val c = java.awt.Color(elem.color.red, elem.color.green, elem.color.blue, elem.alpha)
                                                g2d.color = c
                                                g2d.stroke = BasicStroke(elem.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                                                val x1 = elem.start.x.toInt()
                                                val y1 = elem.start.y.toInt()
                                                val x2 = elem.end.x.toInt()
                                                val y2 = elem.end.y.toInt()
                                                when (elem.type) {
                                                    DesktopShapeType.LINE -> g2d.drawLine(x1, y1, x2, y2)
                                                    DesktopShapeType.RECTANGLE -> {
                                                        val minX = minOf(x1, x2)
                                                        val minY = minOf(y1, y2)
                                                        val w = kotlin.math.abs(x2 - x1)
                                                        val h = kotlin.math.abs(y2 - y1)
                                                        g2d.drawRect(minX, minY, w, h)
                                                    }
                                                    DesktopShapeType.CIRCLE -> {
                                                        val minX = minOf(x1, x2)
                                                        val minY = minOf(y1, y2)
                                                        val w = kotlin.math.abs(x2 - x1)
                                                        val h = kotlin.math.abs(y2 - y1)
                                                        g2d.drawOval(minX, minY, w, h)
                                                    }
                                                    DesktopShapeType.ARROW -> {
                                                        g2d.drawLine(x1, y1, x2, y2)
                                                        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                                                        val headLength = 15.0
                                                        val xArrow1 = (x2 - headLength * cos(angle - Math.PI / 6)).toInt()
                                                        val yArrow1 = (y2 - headLength * sin(angle - Math.PI / 6)).toInt()
                                                        val xArrow2 = (x2 - headLength * cos(angle + Math.PI / 6)).toInt()
                                                        val yArrow2 = (y2 - headLength * sin(angle + Math.PI / 6)).toInt()
                                                        g2d.drawLine(x2, y2, xArrow1, yArrow1)
                                                        g2d.drawLine(x2, y2, xArrow2, yArrow2)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    g2d.dispose()

                                    val outputFile = File(DatabaseHelper.attachmentsDir, "drawing_${System.currentTimeMillis()}.png")
                                    ImageIO.write(image, "PNG", outputFile)
                                    onSaveDrawing(outputFile.absolutePath)
                                    onDismiss()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nota Ekle")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Toolbar Controls
                Surface(
                    color = Color(0xFF131D2E),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Araçlar
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DesktopDrawTool.values().forEach { tool ->
                                val isSelected = selectedTool == tool
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selectedTool = tool }
                                ) {
                                    Text(
                                        tool.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (selectedTool == DesktopDrawTool.SHAPES) {
                                DesktopShapeType.values().forEach { shape ->
                                    val isSelected = selectedShape == shape
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.35f) else Color.Transparent,
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFF334155)),
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selectedShape = shape }
                                    ) {
                                        Text(
                                            shape.title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Renk Paleti & Kalınlık & Desen
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            palette.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (strokeColor == c) 2.dp else 0.dp, Color(0xFF38BDF8), CircleShape)
                                        .clickable { strokeColor = c }
                                )
                            }

                            Spacer(Modifier.width(6.dp))

                            // Desen Seçici
                            DesktopCanvasPattern.values().forEach { pattern ->
                                val isPatternSel = selectedPattern == pattern
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isPatternSel) Color(0xFF334155) else Color.Transparent,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { selectedPattern = pattern }
                                ) {
                                    Text(
                                        pattern.title,
                                        fontSize = 11.sp,
                                        color = if (isPatternSel) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Drawing Canvas
                val canvasBg = if (selectedPattern == DesktopCanvasPattern.CHALKBOARD) Color(0xFF1B2823) else Color(0xFF141A26)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(canvasBg)
                        .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedTool, selectedShape, strokeColor, strokeWidth) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        commitAction()
                                        when (selectedTool) {
                                            DesktopDrawTool.PEN, DesktopDrawTool.HIGHLIGHTER, DesktopDrawTool.ERASER -> {
                                                currentPoints = listOf(offset)
                                            }
                                            DesktopDrawTool.SHAPES -> {
                                                shapeStart = offset
                                                shapeEnd = offset
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        when (selectedTool) {
                                            DesktopDrawTool.PEN, DesktopDrawTool.HIGHLIGHTER, DesktopDrawTool.ERASER -> {
                                                currentPoints = currentPoints + pos
                                            }
                                            DesktopDrawTool.SHAPES -> {
                                                shapeEnd = pos
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        when (selectedTool) {
                                            DesktopDrawTool.PEN -> {
                                                if (currentPoints.isNotEmpty()) {
                                                    elements.add(DesktopDrawElement.FreeHand(currentPoints, strokeColor, strokeWidth, 1.0f, false))
                                                }
                                                currentPoints = emptyList()
                                            }
                                            DesktopDrawTool.HIGHLIGHTER -> {
                                                if (currentPoints.isNotEmpty()) {
                                                    elements.add(DesktopDrawElement.FreeHand(currentPoints, strokeColor, strokeWidth * 2.8f, 0.45f, false))
                                                }
                                                currentPoints = emptyList()
                                            }
                                            DesktopDrawTool.ERASER -> {
                                                if (currentPoints.isNotEmpty()) {
                                                    elements.add(DesktopDrawElement.FreeHand(currentPoints, canvasBg, strokeWidth * 3.5f, 1.0f, true))
                                                }
                                                currentPoints = emptyList()
                                            }
                                            DesktopDrawTool.SHAPES -> {
                                                val start = shapeStart
                                                val end = shapeEnd
                                                if (start != null && end != null) {
                                                    elements.add(DesktopDrawElement.Shape(selectedShape, start, end, strokeColor, strokeWidth, 1.0f))
                                                }
                                                shapeStart = null
                                                shapeEnd = null
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        // Kağıt Deseni Çizimi
                        when (selectedPattern) {
                            DesktopCanvasPattern.LINED -> {
                                val step = 28.dp.toPx()
                                var y = step
                                while (y < size.height) {
                                    drawLine(Color(0xFF334155).copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                    y += step
                                }
                            }
                            DesktopCanvasPattern.GRID -> {
                                val step = 24.dp.toPx()
                                var x = step
                                while (x < size.width) {
                                    drawLine(Color(0xFF334155).copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                    x += step
                                }
                                var y = step
                                while (y < size.height) {
                                    drawLine(Color(0xFF334155).copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                    y += step
                                }
                            }
                            else -> {}
                        }

                        // Önceden Çizilen Elemanlar
                        for (elem in elements) {
                            when (elem) {
                                is DesktopDrawElement.FreeHand -> {
                                    if (elem.points.size > 1) {
                                        val path = Path()
                                        path.moveTo(elem.points[0].x, elem.points[0].y)
                                        for (i in 1 until elem.points.size) {
                                            path.lineTo(elem.points[i].x, elem.points[i].y)
                                        }
                                        drawPath(
                                            path = path,
                                            color = if (elem.isEraser) canvasBg else elem.color.copy(alpha = elem.alpha),
                                            style = Stroke(width = elem.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                    }
                                }
                                is DesktopDrawElement.Shape -> {
                                    when (elem.type) {
                                        DesktopShapeType.LINE -> {
                                            drawLine(elem.color.copy(alpha = elem.alpha), elem.start, elem.end, strokeWidth = elem.strokeWidth, cap = StrokeCap.Round)
                                        }
                                        DesktopShapeType.RECTANGLE -> {
                                            val topLeft = Offset(minOf(elem.start.x, elem.end.x), minOf(elem.start.y, elem.end.y))
                                            val rectSize = Size(kotlin.math.abs(elem.end.x - elem.start.x), kotlin.math.abs(elem.end.y - elem.start.y))
                                            drawRect(elem.color.copy(alpha = elem.alpha), topLeft, rectSize, style = Stroke(width = elem.strokeWidth))
                                        }
                                        DesktopShapeType.CIRCLE -> {
                                            val center = Offset((elem.start.x + elem.end.x) / 2, (elem.start.y + elem.end.y) / 2)
                                            val radius = kotlin.math.sqrt((elem.end.x - elem.start.x) * (elem.end.x - elem.start.x) + (elem.end.y - elem.start.y) * (elem.end.y - elem.start.y)) / 2
                                            drawCircle(elem.color.copy(alpha = elem.alpha), radius, center, style = Stroke(width = elem.strokeWidth))
                                        }
                                        DesktopShapeType.ARROW -> {
                                            drawLine(elem.color.copy(alpha = elem.alpha), elem.start, elem.end, strokeWidth = elem.strokeWidth, cap = StrokeCap.Round)
                                            val angle = atan2((elem.end.y - elem.start.y).toDouble(), (elem.end.x - elem.start.x).toDouble())
                                            val headLen = 16f
                                            val arr1 = Offset((elem.end.x - headLen * cos(angle - Math.PI / 6)).toFloat(), (elem.end.y - headLen * sin(angle - Math.PI / 6)).toFloat())
                                            val arr2 = Offset((elem.end.x - headLen * cos(angle + Math.PI / 6)).toFloat(), (elem.end.y - headLen * sin(angle + Math.PI / 6)).toFloat())
                                            drawLine(elem.color.copy(alpha = elem.alpha), elem.end, arr1, strokeWidth = elem.strokeWidth, cap = StrokeCap.Round)
                                            drawLine(elem.color.copy(alpha = elem.alpha), elem.end, arr2, strokeWidth = elem.strokeWidth, cap = StrokeCap.Round)
                                        }
                                    }
                                }
                            }
                        }

                        // Şu anda sürüklenen çizim
                        if (currentPoints.size > 1) {
                            val activeColor = when (selectedTool) {
                                DesktopDrawTool.HIGHLIGHTER -> strokeColor.copy(alpha = 0.45f)
                                DesktopDrawTool.ERASER -> canvasBg
                                else -> strokeColor
                            }
                            val activeWidth = when (selectedTool) {
                                DesktopDrawTool.HIGHLIGHTER -> strokeWidth * 2.8f
                                DesktopDrawTool.ERASER -> strokeWidth * 3.5f
                                else -> strokeWidth
                            }
                            val path = Path()
                            path.moveTo(currentPoints[0].x, currentPoints[0].y)
                            for (i in 1 until currentPoints.size) {
                                path.lineTo(currentPoints[i].x, currentPoints[i].y)
                            }
                            drawPath(path = path, color = activeColor, style = Stroke(width = activeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }

                        // Şu anda sürüklenen şekil
                        val sStart = shapeStart
                        val sEnd = shapeEnd
                        if (sStart != null && sEnd != null) {
                            when (selectedShape) {
                                DesktopShapeType.LINE -> drawLine(strokeColor, sStart, sEnd, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                                DesktopShapeType.RECTANGLE -> {
                                    val topLeft = Offset(minOf(sStart.x, sEnd.x), minOf(sStart.y, sEnd.y))
                                    val rectSize = Size(kotlin.math.abs(sEnd.x - sStart.x), kotlin.math.abs(sEnd.y - sStart.y))
                                    drawRect(strokeColor, topLeft, rectSize, style = Stroke(width = strokeWidth))
                                }
                                DesktopShapeType.CIRCLE -> {
                                    val center = Offset((sStart.x + sEnd.x) / 2, (sStart.y + sEnd.y) / 2)
                                    val radius = kotlin.math.sqrt((sEnd.x - sStart.x) * (sEnd.x - sStart.x) + (sEnd.y - sStart.y) * (sEnd.y - sStart.y)) / 2
                                    drawCircle(strokeColor, radius, center, style = Stroke(width = strokeWidth))
                                }
                                DesktopShapeType.ARROW -> {
                                    drawLine(strokeColor, sStart, sEnd, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                                    val angle = atan2((sEnd.y - sStart.y).toDouble(), (sEnd.x - sStart.x).toDouble())
                                    val headLen = 16f
                                    val arr1 = Offset((sEnd.x - headLen * cos(angle - Math.PI / 6)).toFloat(), (sEnd.y - headLen * sin(angle - Math.PI / 6)).toFloat())
                                    val arr2 = Offset((sEnd.x - headLen * cos(angle + Math.PI / 6)).toFloat(), (sEnd.y - headLen * sin(angle + Math.PI / 6)).toFloat())
                                    drawLine(strokeColor, sEnd, arr1, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                                    drawLine(strokeColor, sEnd, arr2, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
