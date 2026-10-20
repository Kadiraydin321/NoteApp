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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
     * Notu modern, duyarlı (responsive) ve karanlık/aydınlık tema uyumlu tekil bir HTML dosyası olarak dışa aktarır.
     */
    fun exportToHtml(
        context: Context,
        title: String,
        content: String,
        categoryName: String? = null,
        dateFormatted: String? = null,
        tags: List<String> = emptyList()
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        cleanOldExports(exportDir)
        val displayTitle = if (title.isNotBlank()) title else "İsimsiz Not"
        val sanitizedTitle = displayTitle.replace("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_\\-]".toRegex(), "_").take(30)
        val file = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.html")

        val htmlDoc = buildHtmlDocument(
            title = displayTitle,
            content = content,
            categoryName = categoryName,
            dateFormatted = dateFormatted,
            tags = tags
        )

        file.writeText(htmlDoc, Charsets.UTF_8)
        return file
    }

    fun buildHtmlDocument(
        title: String,
        content: String,
        categoryName: String? = null,
        dateFormatted: String? = null,
        tags: List<String> = emptyList()
    ): String {
        val bodyHtml = convertMarkdownToHtml(content)
        val escapedTitle = escapeHtml(title)
        val escapedCat = categoryName?.let { escapeHtml(it) }
        val escapedDate = dateFormatted?.let { escapeHtml(it) }

        return """
<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>$escapedTitle - Not Defterim</title>
  <style>
    :root {
      --bg: #f8fafc;
      --card-bg: #ffffff;
      --text: #0f172a;
      --text-muted: #64748b;
      --border: #e2e8f0;
      --primary: #2563eb;
      --primary-soft: #eff6ff;
      --code-bg: #f1f5f9;
      --hr: #cbd5e1;
      --quote-border: #3b82f6;
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --bg: #090d16;
        --card-bg: #131c2e;
        --text: #f1f5f9;
        --text-muted: #94a3b8;
        --border: #223249;
        --primary: #38bdf8;
        --primary-soft: #082f49;
        --code-bg: #0b1120;
        --hr: #334155;
        --quote-border: #38bdf8;
      }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      background-color: var(--bg);
      color: var(--text);
      line-height: 1.65;
      padding: 32px 16px;
    }
    .container {
      max-width: 760px;
      margin: 0 auto;
      background-color: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 36px 32px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
    }
    header {
      border-bottom: 1px solid var(--border);
      padding-bottom: 20px;
      margin-bottom: 24px;
    }
    h1.note-title {
      font-size: 28px;
      font-weight: 800;
      color: var(--text);
      letter-spacing: -0.5px;
      margin-bottom: 12px;
    }
    .meta-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 8px;
    }
    .badge.category { background: var(--primary-soft); color: var(--primary); }
    .badge.date { background: var(--border); color: var(--text-muted); }
    .badge.tag { background: var(--code-bg); color: var(--text); border: 1px solid var(--border); }
    .content h1, .content h2, .content h3 {
      font-weight: 700;
      color: var(--text);
      margin-top: 24px;
      margin-bottom: 10px;
      line-height: 1.3;
    }
    .content h1 { font-size: 22px; border-bottom: 1px solid var(--border); padding-bottom: 6px; }
    .content h2 { font-size: 18px; }
    .content h3 { font-size: 16px; }
    .content p { margin-bottom: 12px; }
    .content hr { border: none; height: 1px; background: var(--hr); margin: 24px 0; }
    .content blockquote {
      border-left: 4px solid var(--quote-border);
      padding: 8px 16px;
      margin: 16px 0;
      background: var(--code-bg);
      border-radius: 0 8px 8px 0;
      color: var(--text-muted);
      font-style: italic;
    }
    .content pre {
      background: var(--code-bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 14px;
      overflow-x: auto;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 13px;
      margin: 16px 0;
    }
    .content code {
      background: var(--code-bg);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 13px;
    }
    .content ul { list-style: disc inside; margin: 12px 0 12px 16px; }
    .content ol { list-style: decimal inside; margin: 12px 0 12px 16px; }
    .content li { margin-bottom: 6px; }
    .checklist-item {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 6px 0;
      font-size: 15px;
    }
    .checklist-item.done { text-decoration: line-through; color: var(--text-muted); }
    .checklist-box { font-size: 16px; user-select: none; }
    footer {
      margin-top: 36px;
      padding-top: 16px;
      border-top: 1px solid var(--border);
      text-align: center;
      font-size: 11px;
      color: var(--text-muted);
    }
    @media print {
      body { background: white; color: black; padding: 0; }
      .container { border: none; box-shadow: none; padding: 0; max-width: 100%; }
    }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <h1 class="note-title">$escapedTitle</h1>
      <div class="meta-bar">
        ${if (escapedCat != null) """<span class="badge category">📁 $escapedCat</span>""" else ""}
        ${if (escapedDate != null) """<span class="badge date">📅 $escapedDate</span>""" else ""}
        ${tags.joinToString("\n        ") { """<span class="badge tag">#${escapeHtml(it.removePrefix("#"))}</span>""" }}
      </div>
    </header>
    <div class="content">
$bodyHtml
    </div>
    <footer>
      Not Defterim ile oluşturuldu
    </footer>
  </div>
</body>
</html>
""".trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun formatInlineMarkdown(text: String): String {
        var result = escapeHtml(text)
        // **bold**
        result = result.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        // *italic*
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        // ~~strikethrough~~
        result = result.replace(Regex("~~(.*?)~~"), "<del>$1</del>")
        // `code`
        result = result.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        return result
    }

    fun convertMarkdownToHtml(content: String): String {
        val lines = content.lines()
        val out = StringBuilder()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    out.append("<pre><code>")
                    out.append(escapeHtml(codeBuffer.toString().trimEnd()))
                    out.append("</code></pre>\n")
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.appendLine(rawLine)
                continue
            }

            when {
                trimmed.startsWith("### ") -> {
                    out.append("<h3>").append(formatInlineMarkdown(trimmed.removePrefix("### "))).append("</h3>\n")
                }
                trimmed.startsWith("## ") -> {
                    out.append("<h2>").append(formatInlineMarkdown(trimmed.removePrefix("## "))).append("</h2>\n")
                }
                trimmed.startsWith("# ") -> {
                    out.append("<h1>").append(formatInlineMarkdown(trimmed.removePrefix("# "))).append("</h1>\n")
                }
                trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    val itemText = formatInlineMarkdown(trimmed.substring(6))
                    out.append("""<div class="checklist-item done"><span class="checklist-box">☑</span> <del>$itemText</del></div>""").append("\n")
                }
                trimmed.startsWith("- [ ] ") -> {
                    val itemText = formatInlineMarkdown(trimmed.substring(6))
                    out.append("""<div class="checklist-item"><span class="checklist-box">☐</span> $itemText</div>""").append("\n")
                }
                trimmed.startsWith("> ") -> {
                    val quoteText = formatInlineMarkdown(trimmed.removePrefix("> "))
                    out.append("<blockquote>").append(quoteText).append("</blockquote>\n")
                }
                trimmed == "---" || trimmed == "***" -> {
                    out.append("<hr>\n")
                }
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                    val liText = formatInlineMarkdown(trimmed.substring(2))
                    out.append("<ul><li>").append(liText).append("</li></ul>\n")
                }
                trimmed.isBlank() -> {
                    out.append("<br>\n")
                }
                else -> {
                    out.append("<p>").append(formatInlineMarkdown(rawLine)).append("</p>\n")
                }
            }
        }

        if (inCodeBlock) {
            out.append("<pre><code>")
            out.append(escapeHtml(codeBuffer.toString().trimEnd()))
            out.append("</code></pre>\n")
        }

        return out.toString()
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
