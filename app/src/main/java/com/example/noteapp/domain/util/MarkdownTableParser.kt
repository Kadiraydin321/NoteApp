package com.example.noteapp.domain.util

enum class TableAlignment {
    LEFT, CENTER, RIGHT
}

data class MarkdownTable(
    val headers: List<String>,
    val alignments: List<TableAlignment>,
    val rows: List<List<String>>
)

/**
 * Markdown tablolarını (| Başlık 1 | Başlık 2 |) ayrıştıran,
 * düzenleyen ve tekrar Markdown formatına çeviren yardımcı motor.
 */
object MarkdownTableParser {

    private val SEPARATOR_REGEX = Regex("^\\s*\\|?\\s*(:?-+:?\\s*\\|\\s*)+:?-+:?\\s*\\|?\\s*$")

    /**
     * Belirtilen satırın geçerli bir Markdown tablosu başlangıcı (başlık satırı)
     * olup olmadığını ve bir sonraki satırın ayraç (separator) olup olmadığını doğrular.
     */
    fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val headerLine = lines[index].trim()
        val separatorLine = lines[index + 1].trim()

        if (!headerLine.contains("|")) return false
        if (!separatorLine.contains("|") || !separatorLine.contains("-")) return false

        return isSeparatorLine(separatorLine)
    }

    /**
     * Ayraç satırını doğrular (örn: | --- | :---: | ---: |).
     */
    fun isSeparatorLine(line: String): Boolean {
        val trimmed = line.trim()
        val cells = splitRow(trimmed)
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            val stripped = cell.trim()
            stripped.all { it == '-' || it == ':' } && stripped.count { it == '-' } >= 1
        }
    }

    /**
     * Satırı boru karakterlerinden (|) hücrelere böler.
     */
    fun splitRow(row: String): List<String> {
        var trimmed = row.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length - 1)

        return trimmed.split("|").map { it.trim() }
    }

    /**
     * Ayraç hücresinin hizalamasını belirler (:--- -> LEFT, :---: -> CENTER, ---: -> RIGHT).
     */
    fun parseAlignment(cell: String): TableAlignment {
        val trimmed = cell.trim()
        val startsWithColon = trimmed.startsWith(":")
        val endsWithColon = trimmed.endsWith(":")

        return when {
            startsWithColon && endsWithColon -> TableAlignment.CENTER
            endsWithColon -> TableAlignment.RIGHT
            else -> TableAlignment.LEFT
        }
    }

    /**
     * Belirtilen satırdan başlayarak tam bir Markdown tablosunu ayrıştırır.
     * @return Pair(Ayrıştırılmış Tablo, Tablonun bittiği satır indisi)
     */
    fun parseTable(lines: List<String>, startIndex: Int): Pair<MarkdownTable, Int>? {
        if (!isTableStart(lines, startIndex)) return null

        val headers = splitRow(lines[startIndex])
        val separatorCells = splitRow(lines[startIndex + 1])
        val columnCount = headers.size

        val alignments = (0 until columnCount).map { i ->
            if (i < separatorCells.size) parseAlignment(separatorCells[i])
            else TableAlignment.LEFT
        }

        val rows = mutableListOf<List<String>>()
        var currentIndex = startIndex + 2

        while (currentIndex < lines.size) {
            val line = lines[currentIndex].trim()
            if (!line.contains("|") || line.isEmpty()) {
                break
            }

            val cells = splitRow(line)
            // Sütun sayısına eşitle (eksikse boş ekle, fazlaysa sınırla)
            val paddedCells = (0 until columnCount).map { i ->
                if (i < cells.size) cells[i] else ""
            }
            rows.add(paddedCells)
            currentIndex++
        }

        val table = MarkdownTable(
            headers = headers,
            alignments = alignments,
            rows = rows
        )

        return Pair(table, currentIndex)
    }

    /**
     * Tablo nesnesini temiz, dikeyde boruları (|) hizalı,
     * profesyonel Markdown tablosu metnine çevirir.
     */
    fun toMarkdown(table: MarkdownTable): String {
        return prettifyTable(table)
    }

    /**
     * Sütun genişliklerini hesaplar ve hücreleri hizalayarak
     * estetik, boru (|) karakterleri dikeyde tam hizalı Markdown tablosu metni üretir.
     */
    fun prettifyTable(table: MarkdownTable): String {
        val columnCount = table.headers.size
        if (columnCount == 0) return ""

        // Her sütun için en uzun içeriği hesapla (minimum 3 karakter)
        val colWidths = (0 until columnCount).map { col ->
            val headerLen = if (col < table.headers.size) table.headers[col].length else 0
            val maxRowLen = table.rows.maxOfOrNull { row ->
                if (col < row.size) row[col].length else 0
            } ?: 0
            maxOf(3, maxOf(headerLen, maxRowLen))
        }

        val sb = StringBuilder()

        // 1. Başlık Satırı
        sb.append("|")
        for (i in 0 until columnCount) {
            val header = if (i < table.headers.size) table.headers[i] else ""
            val align = if (i < table.alignments.size) table.alignments[i] else TableAlignment.LEFT
            val padded = padCell(header, colWidths[i], align)
            sb.append(" ").append(padded).append(" |")
        }
        sb.append("\n")

        // 2. Ayraç Satırı
        sb.append("|")
        for (i in 0 until columnCount) {
            val width = colWidths[i]
            val align = if (i < table.alignments.size) table.alignments[i] else TableAlignment.LEFT
            val sep = when (align) {
                TableAlignment.LEFT -> ":" + "-".repeat((width - 1).coerceAtLeast(2))
                TableAlignment.RIGHT -> "-".repeat((width - 1).coerceAtLeast(2)) + ":"
                TableAlignment.CENTER -> ":" + "-".repeat((width - 2).coerceAtLeast(1)) + ":"
            }
            sb.append(" ").append(sep).append(" |")
        }
        sb.append("\n")

        // 3. Veri Satırları
        for (row in table.rows) {
            sb.append("|")
            for (i in 0 until columnCount) {
                val cell = if (i < row.size) row[i] else ""
                val align = if (i < table.alignments.size) table.alignments[i] else TableAlignment.LEFT
                val padded = padCell(cell, colWidths[i], align)
                sb.append(" ").append(padded).append(" |")
            }
            sb.append("\n")
        }

        return sb.toString().trimEnd()
    }

    private fun padCell(text: String, width: Int, align: TableAlignment): String {
        val diff = width - text.length
        if (diff <= 0) return text
        return when (align) {
            TableAlignment.LEFT -> text + " ".repeat(diff)
            TableAlignment.RIGHT -> " ".repeat(diff) + text
            TableAlignment.CENTER -> {
                val leftSpaces = diff / 2
                val rightSpaces = diff - leftSpaces
                " ".repeat(leftSpaces) + text + " ".repeat(rightSpaces)
            }
        }
    }

    /**
     * Verilen metindeki tüm Markdown tablolarını tespit edip hizalanmış ve estetik hale getirir.
     */
    fun formatAllTablesInText(text: String): String {
        val lines = text.lines()
        val result = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            if (isTableStart(lines, index)) {
                val tableResult = parseTable(lines, index)
                if (tableResult != null) {
                    val (table, nextIndex) = tableResult
                    val prettyMarkdown = prettifyTable(table)
                    result.addAll(prettyMarkdown.lines())
                    index = nextIndex
                    continue
                }
            }
            result.add(lines[index])
            index++
        }

        return result.joinToString("\n")
    }

    /**
     * Belirtilen sütunun hizalamasını döngüsel olarak değiştirir (LEFT -> CENTER -> RIGHT -> LEFT).
     */
    fun cycleAlignment(table: MarkdownTable, colIndex: Int): MarkdownTable {
        if (colIndex !in table.headers.indices) return table
        val current = if (colIndex < table.alignments.size) table.alignments[colIndex] else TableAlignment.LEFT
        val next = when (current) {
            TableAlignment.LEFT -> TableAlignment.CENTER
            TableAlignment.CENTER -> TableAlignment.RIGHT
            TableAlignment.RIGHT -> TableAlignment.LEFT
        }
        val updatedAlignments = table.alignments.toMutableList()
        while (updatedAlignments.size <= colIndex) {
            updatedAlignments.add(TableAlignment.LEFT)
        }
        updatedAlignments[colIndex] = next
        return table.copy(alignments = updatedAlignments)
    }

    /**
     * Belirtilen sütunun hizalamasını doğrudan atar.
     */
    fun setAlignment(table: MarkdownTable, colIndex: Int, alignment: TableAlignment): MarkdownTable {
        if (colIndex !in table.headers.indices) return table
        val updatedAlignments = table.alignments.toMutableList()
        while (updatedAlignments.size <= colIndex) {
            updatedAlignments.add(TableAlignment.LEFT)
        }
        updatedAlignments[colIndex] = alignment
        return table.copy(alignments = updatedAlignments)
    }

    /**
     * Tabloya yeni boş satır ekler.
     */
    fun addRow(table: MarkdownTable): MarkdownTable {
        val newRow = List(table.headers.size) { "" }
        return table.copy(rows = table.rows + listOf(newRow))
    }

    /**
     * Belirtilen indisi siler.
     */
    fun deleteRow(table: MarkdownTable, rowIndex: Int): MarkdownTable {
        if (rowIndex !in table.rows.indices) return table
        val updated = table.rows.toMutableList().apply { removeAt(rowIndex) }
        return table.copy(rows = updated)
    }

    /**
     * Belirtilen hücrenin içeriğini günceller.
     */
    fun updateCell(table: MarkdownTable, rowIndex: Int, colIndex: Int, newValue: String): MarkdownTable {
        if (rowIndex !in table.rows.indices || colIndex !in table.headers.indices) return table
        val updatedRows = table.rows.mapIndexed { r, row ->
            if (r == rowIndex) {
                row.mapIndexed { c, cell -> if (c == colIndex) newValue else cell }
            } else row
        }
        return table.copy(rows = updatedRows)
    }

    /**
     * Tablo başlığını günceller.
     */
    fun updateHeader(table: MarkdownTable, colIndex: Int, newHeader: String): MarkdownTable {
        if (colIndex !in table.headers.indices) return table
        val updatedHeaders = table.headers.mapIndexed { c, h -> if (c == colIndex) newHeader else h }
        return table.copy(headers = updatedHeaders)
    }

    /**
     * Tabloya yeni bir sütun ekler.
     */
    fun addColumn(table: MarkdownTable, defaultHeader: String = "Yeni Sütun", alignment: TableAlignment = TableAlignment.LEFT): MarkdownTable {
        val newHeaders = table.headers + defaultHeader
        val newAlignments = table.alignments + alignment
        val newRows = table.rows.map { it + "" }
        return table.copy(headers = newHeaders, alignments = newAlignments, rows = newRows)
    }

    /**
     * Belirtilen sütunu tablodan kaldırır. (En az 1 sütun kalmalıdır).
     */
    fun deleteColumn(table: MarkdownTable, colIndex: Int): MarkdownTable {
        if (colIndex !in table.headers.indices || table.headers.size <= 1) return table
        val newHeaders = table.headers.filterIndexed { i, _ -> i != colIndex }
        val newAlignments = table.alignments.filterIndexed { i, _ -> i != colIndex }
        val newRows = table.rows.map { row -> row.filterIndexed { i, _ -> i != colIndex } }
        return table.copy(headers = newHeaders, alignments = newAlignments, rows = newRows)
    }
}
