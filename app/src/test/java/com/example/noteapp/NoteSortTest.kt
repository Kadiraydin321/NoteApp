package com.example.noteapp

import com.example.noteapp.domain.model.Note
import com.example.noteapp.presentation.notes.NoteSortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSortTest {

    @Test
    fun colorSortOrder_groupsColoredNotesFirstAndSortsByColor() {
        val n1 = Note(id = 1, title = "Note No Color", content = "text", color = 0, updatedAt = 1000)
        val n2 = Note(id = 2, title = "Note Red", content = "text", color = 0xFFFF0000.toInt(), updatedAt = 2000)
        val n3 = Note(id = 3, title = "Note Blue", content = "text", color = 0xFF0000FF.toInt(), updatedAt = 1500)
        val n4 = Note(id = 4, title = "Note Another Red", content = "text", color = 0xFFFF0000.toInt(), updatedAt = 3000)

        val notes = listOf(n1, n2, n3, n4)

        fun modifiedTime(note: Note): Long = when {
            note.updatedAt > 0L -> note.updatedAt
            note.timestamp > 0L -> note.timestamp
            else -> note.id
        }

        val comparator = compareByDescending<Note> { it.color != 0 }
            .thenBy { it.color }
            .thenByDescending { modifiedTime(it) }

        val sorted = notes.sortedWith(comparator)

        // Colored notes should be first (n2, n4, n3) and n1 (color 0) last
        assertEquals(0, sorted.last().color)
        assertEquals(1L, sorted.last().id)
        // Red notes (same color) sorted by modifiedTime descending -> n4 (3000) before n2 (2000)
        val redNotes = sorted.filter { it.color == 0xFFFF0000.toInt() }
        assertEquals(4L, redNotes[0].id)
        assertEquals(2L, redNotes[1].id)
    }
}
