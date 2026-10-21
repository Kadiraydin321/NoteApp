package com.example.noteapp

import com.example.noteapp.domain.util.NoteDiffEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDiffEngineTest {

    @Test
    fun computeDiff_identicalTextsHaveZeroAdditionsAndDeletions() {
        val text = "Satır 1\nSatır 2\nSatır 3"
        val result = NoteDiffEngine.computeDiff("Başlık", text, "Başlık", text)

        assertEquals(0, result.additionsCount)
        assertEquals(0, result.deletionsCount)
        assertEquals(3, result.unchangedCount)
        assertFalse(result.titleChanged)
        assertFalse(result.hasChanges)
    }

    @Test
    fun computeDiff_pureAdditionsFromEmpty() {
        val result = NoteDiffEngine.computeDiff("Eski", "", "Yeni", "Satır 1\nSatır 2")

        assertEquals(2, result.additionsCount)
        assertEquals(0, result.deletionsCount)
        assertEquals(0, result.unchangedCount)
        assertTrue(result.titleChanged)
        assertTrue(result.hasChanges)
        assertEquals(1, result.lines[0].newLineNumber)
        assertNull(result.lines[0].oldLineNumber)
    }

    @Test
    fun computeDiff_pureDeletionsToEmpty() {
        val result = NoteDiffEngine.computeDiff("Başlık", "Satır 1\nSatır 2", "Başlık", "")

        assertEquals(0, result.additionsCount)
        assertEquals(2, result.deletionsCount)
        assertEquals(0, result.unchangedCount)
        assertFalse(result.titleChanged)
        assertTrue(result.hasChanges)
        assertEquals(1, result.lines[0].oldLineNumber)
        assertNull(result.lines[0].newLineNumber)
    }

    @Test
    fun computeDiff_modificationInMiddlePreservesPrefixAndSuffix() {
        val oldText = """
            Giriş
            Eski Satır
            Sonuç
        """.trimIndent()

        val newText = """
            Giriş
            Yeni Satır
            Sonuç
        """.trimIndent()

        val result = NoteDiffEngine.computeDiff("T", oldText, "T", newText)

        assertEquals(1, result.additionsCount)
        assertEquals(1, result.deletionsCount)
        assertEquals(2, result.unchangedCount) // Giriş + Sonuç

        assertEquals(NoteDiffEngine.DiffType.UNCHANGED, result.lines[0].type)
        assertEquals("Giriş", result.lines[0].text)

        val deletedLine = result.lines.find { it.type == NoteDiffEngine.DiffType.DELETED }!!
        assertEquals("Eski Satır", deletedLine.text)
        assertEquals(2, deletedLine.oldLineNumber)

        val addedLine = result.lines.find { it.type == NoteDiffEngine.DiffType.ADDED }!!
        assertEquals("Yeni Satır", addedLine.text)
        assertEquals(2, addedLine.newLineNumber)

        val suffixLine = result.lines.last()
        assertEquals(NoteDiffEngine.DiffType.UNCHANGED, suffixLine.type)
        assertEquals("Sonuç", suffixLine.text)
    }

    @Test
    fun computeDiff_handlesInterspersedEdits() {
        val oldText = "A\nB\nC\nD"
        val newText = "A\nB_MOD\nC\nD\nE"

        val result = NoteDiffEngine.computeDiff("B1", oldText, "B1", newText)

        // B deleted, B_MOD added, E added -> 2 additions, 1 deletion, 3 unchanged (A, C, D)
        assertEquals(2, result.additionsCount)
        assertEquals(1, result.deletionsCount)
        assertEquals(3, result.unchangedCount)
    }

    @Test
    fun computeDiff_detectsTitleOnlyChange() {
        val result = NoteDiffEngine.computeDiff("Eski Başlık", "Sabit İçerik", "Yeni Başlık", "Sabit İçerik")

        assertTrue(result.titleChanged)
        assertEquals(0, result.additionsCount)
        assertEquals(0, result.deletionsCount)
        assertEquals(1, result.unchangedCount)
        assertTrue(result.hasChanges)
    }
}
