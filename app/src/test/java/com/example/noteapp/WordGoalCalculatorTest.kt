package com.example.noteapp

import com.example.noteapp.domain.util.WordGoalCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGoalCalculatorTest {

    @Test
    fun countWords_emptyAndBlankString_returnsZero() {
        assertEquals(0, WordGoalCalculator.countWords(""))
        assertEquals(0, WordGoalCalculator.countWords("   "))
        assertEquals(0, WordGoalCalculator.countWords("\n\t  \n"))
    }

    @Test
    fun countWords_normalText_countsCorrectly() {
        val text = "Merhaba dünya! Bu bir test notudur."
        assertEquals(6, WordGoalCalculator.countWords(text))
    }

    @Test
    fun countWords_multipleSpacesAndNewlines_countsCorrectly() {
        val text = "  Bir   iki\nüç\t\tdört   beş  "
        assertEquals(5, WordGoalCalculator.countWords(text))
    }

    @Test
    fun calculateStatus_zeroCurrentWords_calculatesCorrectly() {
        val status = WordGoalCalculator.calculateStatus(currentWords = 0, targetWords = 100)
        assertEquals(0, status.currentWords)
        assertEquals(100, status.targetWords)
        assertEquals(0f, status.progress, 0.001f)
        assertEquals(0, status.percent)
        assertEquals(100, status.remainingWords)
        assertFalse(status.isCompleted)
    }

    @Test
    fun calculateStatus_halfwayProgress_calculatesCorrectly() {
        val status = WordGoalCalculator.calculateStatus(currentWords = 250, targetWords = 500)
        assertEquals(250, status.currentWords)
        assertEquals(500, status.targetWords)
        assertEquals(0.5f, status.progress, 0.001f)
        assertEquals(50, status.percent)
        assertEquals(250, status.remainingWords)
        assertFalse(status.isCompleted)
    }

    @Test
    fun calculateStatus_exactGoalReached_isCompleted() {
        val status = WordGoalCalculator.calculateStatus(currentWords = 300, targetWords = 300)
        assertEquals(300, status.currentWords)
        assertEquals(300, status.targetWords)
        assertEquals(1f, status.progress, 0.001f)
        assertEquals(100, status.percent)
        assertEquals(0, status.remainingWords)
        assertTrue(status.isCompleted)
    }

    @Test
    fun calculateStatus_exceededGoal_capsProgressAtOneAndPercentExceeds100() {
        val status = WordGoalCalculator.calculateStatus(currentWords = 150, targetWords = 100)
        assertEquals(150, status.currentWords)
        assertEquals(100, status.targetWords)
        assertEquals(1.0f, status.progress, 0.001f)
        assertEquals(150, status.percent)
        assertEquals(0, status.remainingWords)
        assertTrue(status.isCompleted)
    }

    @Test
    fun calculateStatus_zeroOrNegativeTarget_coercesToOne() {
        val status = WordGoalCalculator.calculateStatus(currentWords = 5, targetWords = 0)
        assertEquals(1, status.targetWords)
        assertTrue(status.isCompleted)
        assertEquals(0, status.remainingWords)
    }
}
