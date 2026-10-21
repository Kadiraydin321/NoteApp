package com.example.noteapp

import com.example.noteapp.domain.util.NoteOutlineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteOutlineEngineTest {

    @Test
    fun extractOutline_empty_content_returns_empty_outline() {
        val outline = NoteOutlineEngine.extractOutline("")
        assertEquals(0, outline.totalHeadings)
        assertEquals(0, outline.maxDepth)
        assertTrue(outline.headings.isEmpty())
    }

    @Test
    fun extractOutline_extracts_headings_with_correct_levels_and_offsets() {
        val markdown = """
            # Ana Başlık
            Burası giriş paragrafı ve beş kelime.
            
            ## Bölüm 1: Detaylar
            Bu bölümde bazı önemli açıklamalar yer alıyor.
            
            ### Alt Başlık 1.1
            Kısa açıklama.
        """.trimIndent()

        val outline = NoteOutlineEngine.extractOutline(markdown)

        assertEquals(3, outline.totalHeadings)
        assertEquals(3, outline.maxDepth)

        val h1 = outline.headings[0]
        assertEquals(1, h1.level)
        assertEquals("Ana Başlık", h1.text)
        assertEquals(0, h1.rawLineIndex)
        assertTrue(h1.wordCount > 0)

        val h2 = outline.headings[1]
        assertEquals(2, h2.level)
        assertEquals("Bölüm 1: Detaylar", h2.text)
        assertTrue(h2.wordCount > 0)

        val h3 = outline.headings[2]
        assertEquals(3, h3.level)
        assertEquals("Alt Başlık 1.1", h3.text)
    }

    @Test
    fun extractOutline_ignores_headings_inside_code_blocks() {
        val markdown = """
            # Gerçek Başlık
            Açıklama.
            
            ```python
            # Bu bir Python yorumudur, başlık değildir
            def selam():
                pass
            ```
            
            ## İkinci Gerçek Başlık
            Bitiş.
        """.trimIndent()

        val outline = NoteOutlineEngine.extractOutline(markdown)

        assertEquals(2, outline.totalHeadings)
        assertEquals("Gerçek Başlık", outline.headings[0].text)
        assertEquals("İkinci Gerçek Başlık", outline.headings[1].text)
    }

    @Test
    fun generateMarkdownToc_produces_indented_list_with_anchors() {
        val markdown = """
            # Giriş
            Giriş metni.
            ## Kurulum
            Kurulum adımları.
            ### Android
            Android detayları.
        """.trimIndent()

        val outline = NoteOutlineEngine.extractOutline(markdown)
        val toc = NoteOutlineEngine.generateMarkdownToc(outline)

        assertTrue(toc.contains("### 📑 İçindekiler"))
        assertTrue(toc.contains("- [Giriş](#giriş)"))
        assertTrue(toc.contains("  - [Kurulum](#kurulum)"))
        assertTrue(toc.contains("    - [Android](#android)"))
    }
}
