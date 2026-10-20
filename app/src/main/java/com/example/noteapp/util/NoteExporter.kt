package com.example.noteapp.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class BatchNoteExportItem(
    val title: String,
    val content: String,
    val categoryName: String? = null,
    val dateFormatted: String? = null,
    val tags: List<String> = emptyList()
)

object NoteExporter {

    private const val PAGE_WIDTH = 595 // Standard A4 width in points
    private const val PAGE_HEIGHT = 842 // Standard A4 height in points
    private const val MARGIN_X = 45f
    private const val MARGIN_TOP = 50f
    private const val MARGIN_BOTTOM = 50f

    /**
     * Notu A4 formatında çok sayfalı, başlıklar, kontrol listeleri ve blokları destekleyen
     * zengin stilli bir PDF belgesi olarak oluşturup önbelleğe kaydeder.
     */
    fun exportToPdf(
        context: Context,
        title: String,
        content: String,
        categoryName: String? = null,
        dateFormatted: String? = null,
        tags: List<String> = emptyList()
    ): File {
        val pdfDocument = PdfDocument()
        val usableWidth = PAGE_WIDTH - (MARGIN_X * 2)

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val h1Paint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val h2Paint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val h3Paint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val bodyMutedPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val codePaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val quotePaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
            isAntiAlias = true
        }

        val checkboxStrokePaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }

        val checkboxCheckedPaint = Paint().apply {
            color = Color.rgb(34, 197, 94) // Green-500
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val bulletPaint = Paint().apply {
            color = Color.rgb(79, 70, 229) // Indigo-600
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val quoteBorderPaint = Paint().apply {
            color = Color.rgb(99, 102, 241) // Indigo-500 accent line
            strokeWidth = 2.5f
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = MARGIN_TOP

        // 1. Sayfa Başlığı
        val displayTitle = if (title.isNotBlank()) title else "İsimsiz Not"
        val titleLines = wrapText(displayTitle, titlePaint, usableWidth)
        for (line in titleLines) {
            currentY += titlePaint.textSize
            canvas.drawText(line, MARGIN_X, currentY, titlePaint)
            currentY += 4f
        }
        currentY += 6f

        // 2. Meta Bilgileri (Tarih, Kategori, Etiketler)
        val metaParts = mutableListOf<String>()
        if (!dateFormatted.isNullOrBlank()) metaParts.add(dateFormatted)
        if (!categoryName.isNullOrBlank()) metaParts.add("Kategori: $categoryName")
        if (tags.isNotEmpty()) metaParts.add(tags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })

        if (metaParts.isNotEmpty()) {
            val metaText = metaParts.joinToString(" • ")
            val metaLines = wrapText(metaText, metaPaint, usableWidth)
            for (line in metaLines) {
                currentY += metaPaint.textSize
                canvas.drawText(line, MARGIN_X, currentY, metaPaint)
                currentY += 2f
            }
            currentY += 8f
        }

        // Ayırıcı çizgi
        canvas.drawLine(MARGIN_X, currentY, PAGE_WIDTH - MARGIN_X, currentY, dividerPaint)
        currentY += 16f

        fun ensureSpace(neededHeight: Float) {
            if (currentY + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN_TOP
            }
        }

        // 3. İçerik Satırlarının Ayrıştırılması ve Çizimi
        val rawLines = content.lines()
        var inCodeBlock = false

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()

            // Kod Bloğu Başlangıcı / Bitişi
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                currentY += 4f
                continue
            }

            if (inCodeBlock) {
                ensureSpace(codePaint.textSize + 6f)
                val wrapped = wrapText(rawLine, codePaint, usableWidth - 16f)
                for (subLine in wrapped) {
                    ensureSpace(codePaint.textSize + 4f)
                    currentY += codePaint.textSize
                    canvas.drawText(subLine, MARGIN_X + 10f, currentY, codePaint)
                    currentY += 3f
                }
                continue
            }

            // Boş Satır
            if (trimmed.isEmpty()) {
                currentY += bodyPaint.textSize * 0.9f
                continue
            }

            // Yatay Çizgi
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                ensureSpace(16f)
                currentY += 6f
                canvas.drawLine(MARGIN_X + 20f, currentY, PAGE_WIDTH - MARGIN_X - 20f, currentY, dividerPaint)
                currentY += 10f
                continue
            }

            // Başlıklar
            if (trimmed.startsWith("### ")) {
                val text = trimmed.removePrefix("### ").trim()
                ensureSpace(24f)
                currentY += 10f
                val wrapped = wrapText(text, h3Paint, usableWidth)
                for (sub in wrapped) {
                    currentY += h3Paint.textSize
                    canvas.drawText(sub, MARGIN_X, currentY, h3Paint)
                    currentY += 3f
                }
                currentY += 4f
                continue
            }

            if (trimmed.startsWith("## ")) {
                val text = trimmed.removePrefix("## ").trim()
                ensureSpace(28f)
                currentY += 12f
                val wrapped = wrapText(text, h2Paint, usableWidth)
                for (sub in wrapped) {
                    currentY += h2Paint.textSize
                    canvas.drawText(sub, MARGIN_X, currentY, h2Paint)
                    currentY += 4f
                }
                currentY += 4f
                continue
            }

            if (trimmed.startsWith("# ")) {
                val text = trimmed.removePrefix("# ").trim()
                ensureSpace(32f)
                currentY += 14f
                val wrapped = wrapText(text, h1Paint, usableWidth)
                for (sub in wrapped) {
                    currentY += h1Paint.textSize
                    canvas.drawText(sub, MARGIN_X, currentY, h1Paint)
                    currentY += 4f
                }
                currentY += 6f
                continue
            }

            // Kontrol Listesi (Checklist)
            val isChecked = trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") ||
                    trimmed.startsWith("* [x]") || trimmed.startsWith("* [X]") ||
                    trimmed.startsWith("[x]") || trimmed.startsWith("[X]")
            val isUnchecked = trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]") ||
                    trimmed.startsWith("[ ]")

            if (isChecked || isUnchecked) {
                val taskText = trimmed
                    .replaceFirst(Regex("^(-\\s*|\t|\\*\\s*)?\\[[ xX]\\]\\s*"), "")
                    .trim()
                val targetPaint = if (isChecked) bodyMutedPaint else bodyPaint
                val wrapped = wrapText(taskText, targetPaint, usableWidth - 22f)

                ensureSpace(18f)
                currentY += targetPaint.textSize + 2f

                // Kutu çizimi
                val boxSize = 9.5f
                val boxLeft = MARGIN_X + 2f
                val boxTop = currentY - boxSize + 1f
                val boxRect = RectF(boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize)

                if (isChecked) {
                    canvas.drawRoundRect(boxRect, 2f, 2f, checkboxCheckedPaint)
                    // Beyaz tik işareti (V)
                    val tickPaint = Paint().apply {
                        color = Color.WHITE
                        strokeWidth = 1.3f
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                    }
                    canvas.drawLine(boxLeft + 2.2f, boxTop + 4.8f, boxLeft + 4.2f, boxTop + 7.2f, tickPaint)
                    canvas.drawLine(boxLeft + 4.2f, boxTop + 7.2f, boxLeft + 7.6f, boxTop + 2.6f, tickPaint)
                } else {
                    canvas.drawRoundRect(boxRect, 2f, 2f, checkboxStrokePaint)
                }

                // Metin çizimi
                var isFirst = true
                for (sub in wrapped) {
                    if (!isFirst) {
                        currentY += targetPaint.textSize + 3f
                    }
                    val x = MARGIN_X + 18f
                    canvas.drawText(sub, x, currentY, targetPaint)
                    if (isChecked) {
                        // Tamamlanan metnin üzerine ince çizgi
                        val textWidth = targetPaint.measureText(sub)
                        val lineY = currentY - (targetPaint.textSize * 0.35f)
                        canvas.drawLine(x, lineY, x + textWidth, lineY, checkboxStrokePaint)
                    }
                    isFirst = false
                }
                currentY += 4f
                continue
            }

            // Madde İşareti (Bullet Point)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                val bulletText = trimmed.substring(2).trim()
                val wrapped = wrapText(bulletText, bodyPaint, usableWidth - 16f)

                ensureSpace(16f)
                currentY += bodyPaint.textSize + 2f

                // Yuvarlak bullet noktası
                canvas.drawCircle(MARGIN_X + 5f, currentY - (bodyPaint.textSize * 0.35f), 2.2f, bulletPaint)

                var isFirst = true
                for (sub in wrapped) {
                    if (!isFirst) {
                        currentY += bodyPaint.textSize + 3f
                    }
                    canvas.drawText(sub, MARGIN_X + 14f, currentY, bodyPaint)
                    isFirst = false
                }
                currentY += 4f
                continue
            }

            // Alıntı (Quote)
            if (trimmed.startsWith(">")) {
                val quoteText = trimmed.removePrefix(">").trim()
                val wrapped = wrapText(quoteText, quotePaint, usableWidth - 16f)

                ensureSpace(18f)
                val startY = currentY + 2f
                var isFirst = true
                for (sub in wrapped) {
                    currentY += quotePaint.textSize + 3f
                    canvas.drawText(sub, MARGIN_X + 12f, currentY, quotePaint)
                    isFirst = false
                }
                val endY = currentY + 2f
                canvas.drawLine(MARGIN_X + 3f, startY, MARGIN_X + 3f, endY, quoteBorderPaint)
                currentY += 4f
                continue
            }

            // Standart Düz Metin
            val wrapped = wrapText(rawLine, bodyPaint, usableWidth)
            for (subLine in wrapped) {
                ensureSpace(bodyPaint.textSize + 4f)
                currentY += bodyPaint.textSize
                canvas.drawText(subLine, MARGIN_X, currentY, bodyPaint)
                currentY += 3.5f
            }
        }

        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        // Dosyaya yaz
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        cleanOldExports(exportDir)
        val sanitizedTitle = displayTitle.replace("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_\\-]".toRegex(), "_").take(30)
        val file = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    /**
     * Birden çok notu tek bir şık ve derli toplu PDF belgesinde birleştirir.
     */
    fun exportMultipleToPdf(
        context: Context,
        notes: List<BatchNoteExportItem>,
        documentTitle: String = "Toplu_Notlar"
    ): File {
        val pdfDocument = PdfDocument()
        val usableWidth = PAGE_WIDTH - (MARGIN_X * 2)

        val docHeaderPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val noteTitlePaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = MARGIN_TOP

        fun ensureSpace(neededHeight: Float) {
            if (currentY + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN_TOP
            }
        }

        // Ana Başlık
        ensureSpace(40f)
        canvas.drawText("Not Defterim • Toplu Dışa Aktarım", MARGIN_X, currentY + 18f, docHeaderPaint)
        currentY += 26f
        canvas.drawText("${notes.size} Not • ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}", MARGIN_X, currentY + 10f, metaPaint)
        currentY += 18f
        canvas.drawLine(MARGIN_X, currentY, PAGE_WIDTH - MARGIN_X, currentY, dividerPaint)
        currentY += 20f

        notes.forEachIndexed { index, noteItem ->
            ensureSpace(50f)
            val displayTitle = "${index + 1}. ${noteItem.title.ifBlank { "İsimsiz Not" }}"
            canvas.drawText(displayTitle, MARGIN_X, currentY + 14f, noteTitlePaint)
            currentY += 20f

            val metaParts = mutableListOf<String>()
            if (!noteItem.dateFormatted.isNullOrBlank()) metaParts.add(noteItem.dateFormatted)
            if (!noteItem.categoryName.isNullOrBlank()) metaParts.add("Kategori: ${noteItem.categoryName}")
            if (noteItem.tags.isNotEmpty()) metaParts.add(noteItem.tags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })
            if (metaParts.isNotEmpty()) {
                canvas.drawText(metaParts.joinToString(" • "), MARGIN_X, currentY + 10f, metaPaint)
                currentY += 16f
            }

            // Not İçeriği
            for (line in noteItem.content.lines()) {
                val wrapped = if (line.isBlank()) listOf("") else wrapText(line, bodyPaint, usableWidth)
                for (subLine in wrapped) {
                    ensureSpace(bodyPaint.textSize + 4f)
                    currentY += bodyPaint.textSize
                    canvas.drawText(subLine, MARGIN_X, currentY, bodyPaint)
                    currentY += 3f
                }
            }

            currentY += 14f
            ensureSpace(14f)
            canvas.drawLine(MARGIN_X + 40f, currentY, PAGE_WIDTH - MARGIN_X - 40f, currentY, dividerPaint)
            currentY += 16f
        }

        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        cleanOldExports(exportDir)
        val sanitizedTitle = documentTitle.replace("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_\\-]".toRegex(), "_").take(30)
        val file = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    /**
     * Notu .txt dosyası olarak dışa aktarır.
     */
    fun exportToTxt(
        context: Context,
        title: String,
        content: String,
        categoryName: String? = null,
        dateFormatted: String? = null
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val displayTitle = if (title.isNotBlank()) title else "İsimsiz Not"
        val sanitizedTitle = displayTitle.replace("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_\\-]".toRegex(), "_").take(30)
        val file = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.txt")

        val fullText = buildString {
            appendLine(displayTitle)
            appendLine("=".repeat(displayTitle.length.coerceAtLeast(10)))
            if (!dateFormatted.isNullOrBlank()) appendLine("Tarih: $dateFormatted")
            if (!categoryName.isNullOrBlank()) appendLine("Kategori: $categoryName")
            appendLine()
            append(content)
        }

        file.writeText(fullText, Charsets.UTF_8)
        return file
    }

    /**
     * Notu standart Markdown (.md) dosyası olarak dışa aktarır.
     */
    fun exportToMarkdown(
        context: Context,
        title: String,
        content: String,
        categoryName: String? = null,
        dateFormatted: String? = null,
        tags: List<String> = emptyList()
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val displayTitle = if (title.isNotBlank()) title else "İsimsiz Not"
        val sanitizedTitle = displayTitle.replace("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_\\-]".toRegex(), "_").take(30)
        val file = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.md")

        val fullText = buildString {
            appendLine("# $displayTitle")
            appendLine()
            val metaParts = mutableListOf<String>()
            if (!dateFormatted.isNullOrBlank()) metaParts.add("📅 $dateFormatted")
            if (!categoryName.isNullOrBlank()) metaParts.add("📁 $categoryName")
            if (tags.isNotEmpty()) metaParts.add(tags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })
            if (metaParts.isNotEmpty()) {
                appendLine(metaParts.joinToString("  |  "))
                appendLine()
                appendLine("---")
                appendLine()
            }
            append(content)
        }

        file.writeText(fullText, Charsets.UTF_8)
        return file
    }

    /**
     * Oluşturulan bir dosyayı (PDF, MD, TXT) doğrudan kullanıcının Cihaz İndirilenler (Downloads) klasörüne kaydeder.
     */
    fun saveToDownloads(context: Context, file: File, mimeType: String, subfolder: String = "NotDefterim"): Uri? {
        val fileName = file.name
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, subfolder).apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                file.copyTo(targetFile, overwrite = true)
                Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val text = "Not Defterim • Sayfa $pageNumber"
        val width = footerPaint.measureText(text)
        canvas.drawText(text, (PAGE_WIDTH - width) / 2f, PAGE_HEIGHT - 25f, footerPaint)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    var sub = ""
                    for (char in word) {
                        if (paint.measureText(sub + char) <= maxWidth) {
                            sub += char
                        } else {
                            lines.add(sub)
                            sub = char.toString()
                        }
                    }
                    currentLine = StringBuilder(sub)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }

    private fun cleanOldExports(exportDir: File) {
        try {
            val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            exportDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < oneDayAgo) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }
}
