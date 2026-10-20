package com.example.noteapp

import com.example.noteapp.domain.util.MarkdownTable
import com.example.noteapp.domain.util.MarkdownTableParser
import com.example.noteapp.domain.util.TableAlignment
import org.junit.Assert.*
import org.junit.Test

class MarkdownTableParserTest {

    @Test
    fun isTableStart_detectsValidMarkdownTable() {
        val lines = listOf(
            "| Başlık 1 | Başlık 2 | Başlık 3 |",
            "| :--- | :---: | ---: |",
            "| Veri A | Veri B | Veri C |"
        )
        assertTrue(MarkdownTableParser.isTableStart(lines, 0))
        assertFalse(MarkdownTableParser.isTableStart(lines, 1))
        assertFalse(MarkdownTableParser.isTableStart(lines, 2))
    }

    @Test
    fun isTableStart_returnsFalseForNonTables() {
        val lines = listOf(
            "Bu normal bir paragraftır.",
            "Bu da ikinci satırdır.",
            "| Sadece boru | olan ama ayraç olmayan satır |",
            "Bu bir ayraç değildir"
        )
        assertFalse(MarkdownTableParser.isTableStart(lines, 0))
        assertFalse(MarkdownTableParser.isTableStart(lines, 2))
    }

    @Test
    fun parseAlignment_detectsAlignmentsCorrectly() {
        assertEquals(TableAlignment.LEFT, MarkdownTableParser.parseAlignment(":---"))
        assertEquals(TableAlignment.LEFT, MarkdownTableParser.parseAlignment("---"))
        assertEquals(TableAlignment.CENTER, MarkdownTableParser.parseAlignment(":---:"))
        assertEquals(TableAlignment.RIGHT, MarkdownTableParser.parseAlignment("---:"))
    }

    @Test
    fun parseTable_extractsHeadersAndRows() {
        val markdown = """
            | Görev | Durum | Öncelik |
            | :--- | :---: | ---: |
            | Kodlama | Tamamlandı | Yüksek |
            | Test | Devam Ediyor | Orta |
        """.trimIndent()

        val lines = markdown.lines()
        val result = MarkdownTableParser.parseTable(lines, 0)
        assertNotNull(result)

        val (table, nextIndex) = result!!
        assertEquals(4, nextIndex)
        assertEquals(listOf("Görev", "Durum", "Öncelik"), table.headers)
        assertEquals(
            listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT),
            table.alignments
        )
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Kodlama", "Tamamlandı", "Yüksek"), table.rows[0])
        assertEquals(listOf("Test", "Devam Ediyor", "Orta"), table.rows[1])
    }

    @Test
    fun toMarkdown_serializesCorrectly() {
        val table = MarkdownTable(
            headers = listOf("Ürün", "Adet"),
            alignments = listOf(TableAlignment.LEFT, TableAlignment.RIGHT),
            rows = listOf(
                listOf("Elma", "5"),
                listOf("Armut", "12")
            )
        )

        val markdown = MarkdownTableParser.toMarkdown(table)
        assertTrue(markdown.contains("| Ürün | Adet |"))
        assertTrue(markdown.contains("| :--- | ---: |"))
        assertTrue(markdown.contains("| Elma | 5 |"))
        assertTrue(markdown.contains("| Armut | 12 |"))
    }

    @Test
    fun addRow_and_deleteRow_workCorrectly() {
        val initial = MarkdownTable(
            headers = listOf("A", "B"),
            alignments = listOf(TableAlignment.LEFT, TableAlignment.LEFT),
            rows = listOf(listOf("1", "2"))
        )

        val withNewRow = MarkdownTableParser.addRow(initial)
        assertEquals(2, withNewRow.rows.size)
        assertEquals(listOf("", ""), withNewRow.rows[1])

        val afterDelete = MarkdownTableParser.deleteRow(withNewRow, 0)
        assertEquals(1, afterDelete.rows.size)
        assertEquals(listOf("", ""), afterDelete.rows[0])
    }

    @Test
    fun updateCell_and_updateHeader_workCorrectly() {
        val initial = MarkdownTable(
            headers = listOf("A", "B"),
            alignments = listOf(TableAlignment.LEFT, TableAlignment.LEFT),
            rows = listOf(listOf("1", "2"))
        )

        val updatedHeader = MarkdownTableParser.updateHeader(initial, 1, "Yeni B")
        assertEquals("Yeni B", updatedHeader.headers[1])

        val updatedCell = MarkdownTableParser.updateCell(initial, 0, 1, "99")
        assertEquals("99", updatedCell.rows[0][1])
    }

    @Test
    fun addColumn_and_deleteColumn_workCorrectly() {
        val initial = MarkdownTable(
            headers = listOf("A", "B"),
            alignments = listOf(TableAlignment.LEFT, TableAlignment.CENTER),
            rows = listOf(listOf("1", "2"))
        )

        val withColumn = MarkdownTableParser.addColumn(initial, "C", TableAlignment.RIGHT)
        assertEquals(3, withColumn.headers.size)
        assertEquals("C", withColumn.headers[2])
        assertEquals(TableAlignment.RIGHT, withColumn.alignments[2])
        assertEquals(listOf("1", "2", ""), withColumn.rows[0])

        val afterDeleteCol = MarkdownTableParser.deleteColumn(withColumn, 1)
        assertEquals(2, afterDeleteCol.headers.size)
        assertEquals(listOf("A", "C"), afterDeleteCol.headers)
        assertEquals(listOf("1", ""), afterDeleteCol.rows[0])
    }
}
