package com.example.noteapp

import com.example.noteapp.domain.util.WikiLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinkParserTest {

    @Test
    fun `extractWikiLinks parses standard double bracket links`() {
        val text = "Bu bir [[Android]] geliştirme notudur."
        val links = WikiLinkParser.extractWikiLinks(text)

        assertEquals(1, links.size)
        assertEquals("Android", links[0].targetTitle)
        assertEquals("Android", links[0].displayText)
        assertEquals(null, links[0].subSection)
        assertEquals("[[Android]]", links[0].raw)
    }

    @Test
    fun `extractWikiLinks parses links with alias correctly`() {
        val text = "Daha fazla bilgi için [[Kotlin Multiplatform|KMP Rehberi]] sayfasına bakınız."
        val links = WikiLinkParser.extractWikiLinks(text)

        assertEquals(1, links.size)
        assertEquals("Kotlin Multiplatform", links[0].targetTitle)
        assertEquals("KMP Rehberi", links[0].displayText)
        assertEquals(null, links[0].subSection)
        assertEquals("[[Kotlin Multiplatform|KMP Rehberi]]", links[0].raw)
    }

    @Test
    fun `extractWikiLinks parses section headings correctly`() {
        val text = "Detaylar için [[Mimari#Dependency Injection]] konusunu inceleyin."
        val links = WikiLinkParser.extractWikiLinks(text)

        assertEquals(1, links.size)
        assertEquals("Mimari", links[0].targetTitle)
        assertEquals("Dependency Injection", links[0].subSection)
        assertEquals("Mimari > Dependency Injection", links[0].displayText)
    }

    @Test
    fun `extractWikiLinks parses section heading with custom alias`() {
        val text = "İncele: [[Mimariler#Clean Architecture|Temiz Kod Yaklaşımı]] burada."
        val links = WikiLinkParser.extractWikiLinks(text)

        assertEquals(1, links.size)
        assertEquals("Mimariler", links[0].targetTitle)
        assertEquals("Clean Architecture", links[0].subSection)
        assertEquals("Temiz Kod Yaklaşımı", links[0].displayText)
    }

    @Test
    fun `extractTargetTitles extracts distinct target note titles`() {
        val text = """
            Referanslar:
            - [[Jetpack Compose]]
            - [[Kotlin]]
            - [[Jetpack Compose|Compose UI]]
            - [[Jetpack Compose#State]]
        """.trimIndent()

        val titles = WikiLinkParser.extractTargetTitles(text)
        assertEquals(2, titles.size)
        assertTrue(titles.contains("Jetpack Compose"))
        assertTrue(titles.contains("Kotlin"))
    }

    @Test
    fun `extractWikilinksWithSnippets produces contextual snippet`() {
        val text = "Bugün yeni bir proje başlattık. Proje için [[Retrofit]] kütüphanesini kullanmaya karar verdik."
        val snippets = WikiLinkParser.extractWikilinksWithSnippets(text)

        assertEquals(1, snippets.size)
        assertEquals("Retrofit", snippets[0].targetTitle)
        assertTrue(snippets[0].contextSnippet.contains("Retrofit"))
    }

    @Test
    fun `extractWikiLinks handles empty, blank or text without wikilinks`() {
        assertTrue(WikiLinkParser.extractWikiLinks("").isEmpty())
        assertTrue(WikiLinkParser.extractWikiLinks("   \n\t  ").isEmpty())
        assertTrue(WikiLinkParser.extractWikiLinks("Bu metinde hiç çift köşeli bağlantı bulunmuyor.").isEmpty())
        assertTrue(WikiLinkParser.extractWikiLinks("[Tek köşeli]").isEmpty())
    }

    @Test
    fun `extractWikiLinks handles multiple links on a single line`() {
        val line = "Bağlantılar: [[Not A]], [[Not B|İkinci Not]] ve [[Not C#Detay]]."
        val links = WikiLinkParser.extractWikiLinks(line)

        assertEquals(3, links.size)
        assertEquals("Not A", links[0].targetTitle)
        assertEquals("Not B", links[1].targetTitle)
        assertEquals("İkinci Not", links[1].displayText)
        assertEquals("Not C", links[2].targetTitle)
        assertEquals("Detay", links[2].subSection)
        assertEquals("Not C > Detay", links[2].displayText)
    }

    @Test
    fun `extractWikiLinks ignores malformed empty brackets`() {
        val text = "Hatalı formatlar: [[]], [[   ]], [[]"
        val links = WikiLinkParser.extractWikiLinks(text)
        assertTrue(links.isEmpty())
    }

    @Test
    fun `extractWikiLinks supports Turkish unicode characters and special names`() {
        val text = "Ders listesi: [[Türkçe Çalışma Notları#Giriş|Türkçe]] ve [[İleri Düzey Ödevler]]."
        val links = WikiLinkParser.extractWikiLinks(text)

        assertEquals(2, links.size)
        assertEquals("Türkçe Çalışma Notları", links[0].targetTitle)
        assertEquals("Giriş", links[0].subSection)
        assertEquals("Türkçe", links[0].displayText)
        assertEquals("İleri Düzey Ödevler", links[1].targetTitle)
    }

    @Test
    fun `extractWikilinksWithSnippets respects custom snippetRadius`() {
        val prefix = "A".repeat(100)
        val suffix = "B".repeat(100)
        val text = "$prefix[[Hedef Not]]$suffix"

        val snippets = WikiLinkParser.extractWikilinksWithSnippets(text, snippetRadius = 15)
        assertEquals(1, snippets.size)
        val snippet = snippets[0].contextSnippet
        assertTrue(snippet.contains("Hedef Not"))
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        // Uzunluk çok kısıtlı olmalı (15 öncesi + 15 sonrası + link boyutu)
        assertTrue(snippet.length < 50)
    }
}

