package com.example.noteapp.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object NoteExporter {

    private const val PAGE_WIDTH = 595 // Standard A4 width in points
    private const val PAGE_HEIGHT = 842 // Standard A4 height in points
    private const val MARGIN_X = 45f
    private const val MARGIN_TOP = 50f
    private const val MARGIN_BOTTOM = 50f

    /**
     * Notu A4 formatında çok sayfalı PDF belgesi olarak oluşturup önbelleğe kaydeder.
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
            color = Color.rgb(20, 24, 33)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
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
        currentY += 8f

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
            currentY += 10f
        }

        // Ayırıcı çizgi
        canvas.drawLine(MARGIN_X, currentY, PAGE_WIDTH - MARGIN_X, currentY, dividerPaint)
        currentY += 18f

        // 3. Not İçeriği Satırları
        val lineHeight = bodyPaint.textSize * 1.55f
        val rawLines = content.lines()

        for (rawLine in rawLines) {
            val wrapped = if (rawLine.isBlank()) listOf("") else wrapText(rawLine, bodyPaint, usableWidth)
            for (subLine in wrapped) {
                if (currentY + lineHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    // Sayfa sonu: Geçerli sayfayı bitir ve yeni sayfa aç
                    drawFooter(canvas, pageNumber)
                    pdfDocument.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN_TOP
                }

                currentY += bodyPaint.textSize
                canvas.drawText(subLine, MARGIN_X, currentY, bodyPaint)
                currentY += (lineHeight - bodyPaint.textSize)
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
                    // Kelime tek başına satırdan daha uzunsa karakter karakter böl
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
