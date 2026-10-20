package com.example.noteapp

import com.example.noteapp.domain.util.FindMatch
import com.example.noteapp.domain.util.FindReplaceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindReplaceEngineTest {

    @Test
    fun `findAllMatches returns correct indices in case-insensitive mode`() {
        val text = "Kotlin is concise. kotlin is safe. KOTLIN is interoperable."
        val matches = FindReplaceEngine.findAllMatches(text, "kotlin", matchCase = false)

        assertEquals(3, matches.size)
        assertEquals(0, matches[0].index)
        assertEquals(6, matches[0].length)
        assertEquals(19, matches[1].index)
        assertEquals(35, matches[2].index)
    }

    @Test
    fun `findAllMatches respects case when matchCase is true`() {
        val text = "Apple apple APPLE aPple"
        val matches = FindReplaceEngine.findAllMatches(text, "apple", matchCase = true)

        assertEquals(1, matches.size)
        assertEquals(6, matches[0].index)
        assertEquals(5, matches[0].length)
    }

    @Test
    fun `findAllMatches returns empty list when query is empty or not found`() {
        val text = "Sample note content"
        assertTrue(FindReplaceEngine.findAllMatches(text, "", false).isEmpty())
        assertTrue(FindReplaceEngine.findAllMatches("", "query", false).isEmpty())
        assertTrue(FindReplaceEngine.findAllMatches(text, "nonexistent", false).isEmpty())
    }

    @Test
    fun `replaceCurrent replaces single match correctly and updates selection`() {
        val text = "Hello World, welcome to the World!"
        val matches = FindReplaceEngine.findAllMatches(text, "World", matchCase = true)
        assertEquals(2, matches.size)

        // Replace the second "World"
        val result = FindReplaceEngine.replaceCurrent(
            content = text,
            match = matches[1],
            replacement = "Galaxy"
        )

        assertEquals("Hello World, welcome to the Galaxy!", result.newContent)
        assertEquals(1, result.count)
        assertEquals(text.indexOf("welcome to the ") + "welcome to the Galaxy".length, result.newSelectionIndex)
    }

    @Test
    fun `replaceCurrent handles out of bounds match gracefully`() {
        val text = "Short text"
        val invalidMatch = FindMatch(index = 50, length = 5)
        val result = FindReplaceEngine.replaceCurrent(text, invalidMatch, "new")

        assertEquals(text, result.newContent)
        assertEquals(0, result.count)
    }

    @Test
    fun `replaceAll replaces all occurrences in case-insensitive mode`() {
        val text = "Bug in line 1. BUG in line 5. Fixed bug in line 8."
        val result = FindReplaceEngine.replaceAll(
            content = text,
            query = "bug",
            replacement = "issue",
            matchCase = false
        )

        assertEquals("issue in line 1. issue in line 5. Fixed issue in line 8.", result.newContent)
        assertEquals(3, result.count)
    }

    @Test
    fun `replaceAll replaces only matching case when matchCase is true`() {
        val text = "Test test TEST"
        val result = FindReplaceEngine.replaceAll(
            content = text,
            query = "test",
            replacement = "exam",
            matchCase = true
        )

        assertEquals("Test exam TEST", result.newContent)
        assertEquals(1, result.count)
    }

    @Test
    fun `replaceAll returns original text when query not found`() {
        val text = "Clean architecture in Android"
        val result = FindReplaceEngine.replaceAll(text, "iOS", "Flutter", false)

        assertEquals(text, result.newContent)
        assertEquals(0, result.count)
    }
}
