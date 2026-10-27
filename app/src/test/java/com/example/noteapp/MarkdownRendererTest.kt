package com.example.noteapp

import com.example.noteapp.domain.util.MarkdownRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun `parseInlineMarkdown parses bold and italic correctly`() {
        val input = "Bu **kalın** ve *italik* bir metindir."
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Bu kalın ve italik bir metindir.", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseInlineMarkdown parses standard wikilink and adds annotation`() {
        val input = "Bkz. [[Android Geliştirme]] konusu."
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Bkz. Android Geliştirme konusu.", result.text)
        val annotations = result.getStringAnnotations(tag = "backlink", start = 0, end = result.text.length)
        assertEquals(1, annotations.size)
        assertEquals("Android Geliştirme", annotations[0].item)
        assertEquals("Android Geliştirme", result.text.substring(annotations[0].start, annotations[0].end))
    }

    @Test
    fun `parseInlineMarkdown parses wikilink with alias and displays only alias`() {
        val input = "Daha fazla bilgi için [[Kotlin Multiplatform|KMP]] rehberine bakın."
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Daha fazla bilgi için KMP rehberine bakın.", result.text)
        val annotations = result.getStringAnnotations(tag = "backlink", start = 0, end = result.text.length)
        assertEquals(1, annotations.size)
        assertEquals("Kotlin Multiplatform", annotations[0].item)
        assertEquals("KMP", result.text.substring(annotations[0].start, annotations[0].end))
    }

    @Test
    fun `checkbox regex matches hyphen and asterisk variants`() {
        val unchecked1 = "- [ ] Görev 1"
        val checked1 = "- [x] Tamamlanan görev"
        val checkedCapital = "- [X] Büyük harfli tamamlanan"
        val asteriskUnchecked = "* [ ] Yıldızlı görev"
        val asteriskChecked = "* [x] Yıldızlı tamamlanan"
        val indented = "    - [ ] Girintili alt görev"

        assertTrue(MarkdownRenderer.CHECKBOX_REGEX.matches(unchecked1))
        assertTrue(MarkdownRenderer.CHECKBOX_REGEX.matches(checked1))
        assertTrue(MarkdownRenderer.CHECKBOX_REGEX.matches(checkedCapital))
        assertTrue(MarkdownRenderer.CHECKBOX_REGEX.matches(asteriskUnchecked))
        assertTrue(MarkdownRenderer.CHECKBOX_REGEX.matches(asteriskChecked))
        assertTrue(MarkdownRenderer.CHECKBOX_REGEX.matches(indented))

        val match = MarkdownRenderer.CHECKBOX_REGEX.matchEntire(indented)!!
        assertEquals("    ", match.groupValues[1])
        assertEquals("-", match.groupValues[2])
        assertEquals(" ", match.groupValues[3])
        assertEquals("Girintili alt görev", match.groupValues[4])
    }

    @Test
    fun `parseInlineMarkdown parses strikethrough correctly`() {
        val input = "Bu ~~silinmiş~~ bir ifade."
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Bu silinmiş bir ifade.", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseInlineMarkdown parses highlight correctly`() {
        val input = "Önemli: ==sarı vurgulu metin=="
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Önemli: sarı vurgulu metin", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseInlineMarkdown parses inline code block correctly`() {
        val input = "Komut: `git status` terminalde çalıştırılır."
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Komut: git status terminalde çalıştırılır.", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseInlineMarkdown parses math formula correctly`() {
        val input = "Formül: \$E = mc^2\$ fizik kuralıdır."
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Formül: E = mc^2 fizik kuralıdır.", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseInlineMarkdown parses underscore bold and italic`() {
        val input = "__Kalın__ ve _italik_ metin."
        val result = MarkdownRenderer.parseInlineMarkdown(input)

        assertEquals("Kalın ve italik metin.", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parseInlineMarkdown handles empty string safely`() {
        val result = MarkdownRenderer.parseInlineMarkdown("")
        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }
}
