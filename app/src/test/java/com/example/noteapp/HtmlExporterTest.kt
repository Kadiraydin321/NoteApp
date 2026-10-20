package com.example.noteapp

import com.example.noteapp.util.NoteExporter
import org.junit.Assert.*
import org.junit.Test

class HtmlExporterTest {

    @Test
    fun `convertMarkdownToHtml converts headings correctly`() {
        val markdown = """
            # Başlık 1
            ## Başlık 2
            ### Başlık 3
        """.trimIndent()

        val html = NoteExporter.convertMarkdownToHtml(markdown)

        assertTrue(html.contains("<h1>Başlık 1</h1>"))
        assertTrue(html.contains("<h2>Başlık 2</h2>"))
        assertTrue(html.contains("<h3>Başlık 3</h3>"))
    }

    @Test
    fun `convertMarkdownToHtml converts checklist items with appropriate tags`() {
        val markdown = """
            - [ ] Yapılacak iş
            - [x] Tamamlanan iş
        """.trimIndent()

        val html = NoteExporter.convertMarkdownToHtml(markdown)

        assertTrue(html.contains("""class="checklist-item""""))
        assertTrue(html.contains("""class="checklist-item done""""))
        assertTrue(html.contains("Yapılacak iş"))
        assertTrue(html.contains("Tamamlanan iş"))
    }

    @Test
    fun `convertMarkdownToHtml handles inline bold italic code and strikethrough`() {
        val markdown = "Bu **kalın**, bu *italik*, bu ~~çizili~~ ve bu `kod`."
        val html = NoteExporter.convertMarkdownToHtml(markdown)

        assertTrue(html.contains("<strong>kalın</strong>"))
        assertTrue(html.contains("<em>italik</em>"))
        assertTrue(html.contains("<del>çizili</del>"))
        assertTrue(html.contains("<code>kod</code>"))
    }

    @Test
    fun `convertMarkdownToHtml converts blockquotes and code blocks`() {
        val markdown = """
            > Bu bir alıntıdır
            
            ```
            fun main() {
                println("Test")
            }
            ```
        """.trimIndent()

        val html = NoteExporter.convertMarkdownToHtml(markdown)

        assertTrue(html.contains("<blockquote>Bu bir alıntıdır</blockquote>"))
        assertTrue(html.contains("<pre><code>fun main() {"))
        assertTrue(html.contains("println(&quot;Test&quot;)"))
    }

    @Test
    fun `convertMarkdownToHtml escapes dangerous html tags`() {
        val markdown = "<script>alert('xss')</script>"
        val html = NoteExporter.convertMarkdownToHtml(markdown)

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `buildHtmlDocument contains title category tags and responsive styles`() {
        val title = "Toplantı Notları"
        val content = "Gündem maddeleri tartışıldı."
        val category = "Şirket"
        val tags = listOf("#iş", "#planlama")

        val doc = NoteExporter.buildHtmlDocument(
            title = title,
            content = content,
            categoryName = category,
            dateFormatted = "21.10.2026 10:00",
            tags = tags
        )

        assertTrue(doc.contains("<title>Toplantı Notları - Not Defterim</title>"))
        assertTrue(doc.contains("📁 Şirket"))
        assertTrue(doc.contains("#iş"))
        assertTrue(doc.contains("#planlama"))
        assertTrue(doc.contains("prefers-color-scheme: dark"))
    }
}
