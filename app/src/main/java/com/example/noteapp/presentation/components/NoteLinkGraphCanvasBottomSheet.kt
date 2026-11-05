package com.example.noteapp.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.NoteLinkGraphEngine
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Görselleştirilmiş Graf Düğümü
 */
data class GraphVisualNode(
    val noteId: Long,
    val title: String,
    val inDegree: Int,
    val outDegree: Int,
    var x: Float = 0f,
    var y: Float = 0f,
    val radius: Float = 24f
) {
    val degree: Int get() = inDegree + outDegree
}

/**
 * İki not arasındaki yönlü wikilink bağlantısı
 */
data class GraphVisualEdge(
    val sourceId: Long,
    val targetId: Long
)

/**
 * Tüm notların wikilink bağlantılarını Compose Canvas üzerinde interaktif olarak
 * çizen iki yönlü graf görselleştirici (Graph View).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteLinkGraphCanvasBottomSheet(
    notes: List<Note>,
    onNavigateToNote: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Graf istatistikleri
    val stats = remember(notes) {
        NoteLinkGraphEngine.computeGraphStats(notes)
    }

    var filterOnlyConnected by remember { mutableStateOf(false) }
    var selectedNodeId by remember { mutableStateOf<Long?>(null) }

    // Yakınlaştırma ve Kaydırma durumları
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val textMeasurer = rememberTextMeasurer()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantAlpha = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = surfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // 1. Üst Başlık Çubuğu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(primaryContainerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = onPrimaryContainerColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "🕸️ Not İlişki Grafiği (Graph View)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stats.totalNotes} Not • ${stats.totalLinks} Wikilink • ${stats.orphanNotes.size} Bağımsız",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 2. Filtre ve Kontrol Araçları Çubuğu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !filterOnlyConnected,
                        onClick = { filterOnlyConnected = false },
                        label = { Text("Tüm Notlar (${notes.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filterOnlyConnected,
                        onClick = { filterOnlyConnected = true },
                        label = { Text("Bağlantılılar", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                // Yakınlaştırma ve Sıfırlama Butonları
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(3.5f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Yakınlaştır", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.4f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Uzaklaştır", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            selectedNodeId = null
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sıfırla", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3. İnteraktif Canvas Çizim Alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfaceVariantAlpha)
                    .border(1.dp, outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, panDelta, zoomDelta, _ ->
                                zoomScale = (zoomScale * zoomDelta).coerceIn(0.4f, 3.5f)
                                panOffset += panDelta
                            }
                        }
                        .pointerInput(notes, filterOnlyConnected, zoomScale, panOffset) {
                            detectTapGestures { tapOffset ->
                                val canvasWidth = size.width.toFloat()
                                val canvasHeight = size.height.toFloat()
                                val centerX = canvasWidth / 2f
                                val centerY = canvasHeight / 2f

                                val (visualNodes, _) = computeGraphLayout(
                                    notes = notes,
                                    filterOnlyConnected = filterOnlyConnected,
                                    canvasWidth = canvasWidth,
                                    canvasHeight = canvasHeight
                                )

                                // Ekrana dokunma koordinatını Canvas koordinat sistemine dönüştür
                                val transformedTapX = (tapOffset.x - panOffset.x - centerX) / zoomScale + centerX
                                val transformedTapY = (tapOffset.y - panOffset.y - centerY) / zoomScale + centerY

                                val hit = visualNodes.find { node ->
                                    val dx = node.x - transformedTapX
                                    val dy = node.y - transformedTapY
                                    sqrt(dx * dx + dy * dy) <= (node.radius + 14f)
                                }

                                selectedNodeId = hit?.noteId
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val centerX = canvasWidth / 2f
                    val centerY = canvasHeight / 2f

                    val (visualNodes, visualEdges) = computeGraphLayout(
                        notes = notes,
                        filterOnlyConnected = filterOnlyConnected,
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight
                    )

                    val nodeMap = visualNodes.associateBy { it.noteId }

                    // Arka plan ızgara noktaları (subtle background grid)
                    val gridSpacing = 40f * zoomScale
                    val startX = (panOffset.x % gridSpacing)
                    val startY = (panOffset.y % gridSpacing)
                    var gx = startX
                    while (gx < canvasWidth) {
                        var gy = startY
                        while (gy < canvasHeight) {
                            drawCircle(
                                color = outlineVariant.copy(alpha = 0.25f),
                                radius = 1.2f,
                                center = Offset(gx, gy)
                            )
                            gy += gridSpacing
                        }
                        gx += gridSpacing
                    }

                    // Koordinat Dönüşümü: Pan ve Zoom uygula
                    withTransform({
                        translate(panOffset.x, panOffset.y)
                        scale(zoomScale, zoomScale, Offset(centerX, centerY))
                    }) {
                        // 1. Kenarları (Edges) Çiz
                        for (edge in visualEdges) {
                            val srcNode = nodeMap[edge.sourceId] ?: continue
                            val dstNode = nodeMap[edge.targetId] ?: continue

                            val isConnectedToSelected = selectedNodeId != null &&
                                    (edge.sourceId == selectedNodeId || edge.targetId == selectedNodeId)

                            val edgeColor = if (isConnectedToSelected) {
                                primaryColor.copy(alpha = 0.95f)
                            } else if (selectedNodeId != null) {
                                outlineVariant.copy(alpha = 0.15f)
                            } else {
                                outlineVariant.copy(alpha = 0.55f)
                            }

                            val edgeWidth = if (isConnectedToSelected) 3.5.dp.toPx() else 1.5.dp.toPx()

                            drawLine(
                                color = edgeColor,
                                start = Offset(srcNode.x, srcNode.y),
                                end = Offset(dstNode.x, dstNode.y),
                                strokeWidth = edgeWidth,
                                cap = StrokeCap.Round
                            )

                            // Yön Göstergesi (Orta Noktada Küçük Yön Noktası)
                            val midX = (srcNode.x + dstNode.x) / 2f
                            val midY = (srcNode.y + dstNode.y) / 2f
                            drawCircle(
                                color = if (isConnectedToSelected) primaryColor else outlineVariant,
                                radius = if (isConnectedToSelected) 4.5f else 2.5f,
                                center = Offset(midX, midY)
                            )
                        }

                        // 2. Düğümleri (Nodes) Çiz
                        for (node in visualNodes) {
                            val isSelected = node.noteId == selectedNodeId
                            val isConnectedNeighbor = selectedNodeId != null && visualEdges.any {
                                (it.sourceId == selectedNodeId && it.targetId == node.noteId) ||
                                        (it.targetId == selectedNodeId && it.sourceId == node.noteId)
                            }

                            val nodeFillColor = when {
                                isSelected -> primaryColor
                                isConnectedNeighbor -> secondaryColor
                                node.degree > 3 -> tertiaryColor
                                node.degree > 0 -> primaryContainerColor
                                else -> outlineVariant.copy(alpha = 0.6f)
                            }

                            val nodeAlpha = if (selectedNodeId == null || isSelected || isConnectedNeighbor) 1f else 0.35f

                            // Seçili Düğüm Vurgu / Halka Efekti
                            if (isSelected) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.35f),
                                    radius = node.radius + 10f,
                                    center = Offset(node.x, node.y)
                                )
                            }

                            // Düğüm Dairesi
                            drawCircle(
                                color = nodeFillColor.copy(alpha = nodeAlpha),
                                radius = node.radius,
                                center = Offset(node.x, node.y)
                            )

                            // Düğüm Çerçevesi
                            drawCircle(
                                color = if (isSelected) primaryColor else outlineVariant.copy(alpha = nodeAlpha),
                                radius = node.radius,
                                center = Offset(node.x, node.y),
                                style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.2.dp.toPx())
                            )

                            // Düğüm İçi Derece Sayısı
                            if (node.degree > 0) {
                                val degreeText = "${node.degree}"
                                val degreeMeasure = textMeasurer.measure(
                                    text = degreeText,
                                    style = TextStyle(
                                        color = if (isSelected || isConnectedNeighbor) Color.White else onSurface,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = degreeText,
                                    topLeft = Offset(
                                        node.x - degreeMeasure.size.width / 2f,
                                        node.y - degreeMeasure.size.height / 2f
                                    ),
                                    style = TextStyle(
                                        color = if (isSelected || isConnectedNeighbor) Color.White else onSurface,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            // Düğüm Altı Not Başlığı
                            val displayTitle = if (node.title.length > 14) "${node.title.take(12)}.." else node.title
                            val textMeasure = textMeasurer.measure(
                                text = displayTitle,
                                style = TextStyle(
                                    color = onSurface.copy(alpha = nodeAlpha),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )

                            drawText(
                                textMeasurer = textMeasurer,
                                text = displayTitle,
                                topLeft = Offset(
                                    node.x - textMeasure.size.width / 2f,
                                    node.y + node.radius + 4f
                                ),
                                style = TextStyle(
                                    color = onSurface.copy(alpha = nodeAlpha),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // 4. Seçili Düğüm Alt Bilgi Kartı (Overlay Card)
                val selectedNode = remember(selectedNodeId, notes) {
                    notes.find { it.id == selectedNodeId }
                }

                if (selectedNode != null) {
                    val fullGraph = remember(notes) { NoteLinkGraphEngine.buildGraph(notes) }
                    val nodeInfo = fullGraph[selectedNode.id]
                    val inCount = nodeInfo?.inboundNoteIds?.size ?: 0
                    val outCount = nodeInfo?.outboundTitles?.size ?: 0

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceColor,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedNode.title.ifBlank { "Başlıksız Not" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "← $inCount Gelen Link • → $outCount Giden Link",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    onDismiss()
                                    onNavigateToNote(selectedNode.id)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Notu Aç", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phyllotaxis (Altın Oran Çiçek Spirali) algoritması ile düğümleri çakışmasız,
 * yüksek dereceliler merkezde olacak şekilde 2 boyutlu düzleme yerleştirir.
 */
private fun computeGraphLayout(
    notes: List<Note>,
    filterOnlyConnected: Boolean,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<List<GraphVisualNode>, List<GraphVisualEdge>> {
    val graph = NoteLinkGraphEngine.buildGraph(notes)
    val titleToIdMap = notes.associate { it.title.trim().lowercase(Locale.getDefault()) to it.id }

    val edges = mutableListOf<GraphVisualEdge>()
    for (note in notes) {
        val targets = NoteLinkGraphEngine.extractOutboundTitles(note.content)
        for (targetTitle in targets) {
            val targetId = titleToIdMap[targetTitle.lowercase(Locale.getDefault())]
            if (targetId != null && targetId != note.id) {
                edges.add(GraphVisualEdge(note.id, targetId))
            }
        }
    }

    val connectedIds = edges.flatMap { listOf(it.sourceId, it.targetId) }.toSet()
    val displayNotes = if (filterOnlyConnected && connectedIds.isNotEmpty()) {
        notes.filter { connectedIds.contains(it.id) }
    } else {
        notes
    }

    if (displayNotes.isEmpty()) return Pair(emptyList(), emptyList())

    val centerX = canvasWidth / 2f
    val centerY = canvasHeight / 2f

    // Dereceye göre azalan sırada sırala (Hub notlar merkezde başlar)
    val visualNodes = displayNotes.map { note ->
        val nodeInfo = graph[note.id]
        val inDeg = nodeInfo?.inboundNoteIds?.size ?: 0
        val outDeg = nodeInfo?.outboundTitles?.size ?: 0
        val degree = inDeg + outDeg
        val radius = 18f + (degree.coerceAtMost(8) * 2.2f)
        GraphVisualNode(
            noteId = note.id,
            title = note.title.ifBlank { "Başlıksız" },
            inDegree = inDeg,
            outDegree = outDeg,
            radius = radius
        )
    }.sortedByDescending { it.degree }

    val count = visualNodes.size
    val minDim = minOf(canvasWidth, canvasHeight)
    val spacingFactor = (minDim * 0.44f) / sqrt(maxOf(1f, count.toFloat()))
    val goldenAngle = PI * (3.0 - sqrt(5.0)) // ~137.5 derece altın oran

    for (i in visualNodes.indices) {
        val node = visualNodes[i]
        if (i == 0 && count > 1) {
            node.x = centerX
            node.y = centerY
        } else {
            val theta = i * goldenAngle
            val r = (spacingFactor * sqrt(i.toDouble()) * 1.35).toFloat()
            node.x = centerX + (r * cos(theta)).toFloat()
            node.y = centerY + (r * sin(theta)).toFloat()
        }
    }

    val activeEdges = edges.filter { edge ->
        displayNotes.any { it.id == edge.sourceId } && displayNotes.any { it.id == edge.targetId }
    }

    return Pair(visualNodes, activeEdges)
}
