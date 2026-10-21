package com.example.noteapp

import com.example.noteapp.domain.util.ClipboardHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHelperTest {

    @Test
    fun stripMarkdown_removesHeadersBoldItalicsAndLinks() {
        val markdown = """
            # Ana Başlık
            ## Alt Başlık
            Bu bir **kalın metin** ve bu bir *italik metin*.
            Burada ~~üstü çizili~~ bir yazı var.
            Daha fazla bilgi için [Tıklayınız](https://example.com).
            İç bağlantı: [[Seyahat Planı]].
        """.trimIndent()

        val stripped = ClipboardHelper.stripMarkdown(markdown)

        assertFalse(stripped.contains("#"))
        assertFalse(stripped.contains("**"))
        assertFalse(stripped.contains("~~"))
        assertFalse(stripped.contains("[["))
        assertFalse(stripped.contains("]]"))
        assertTrue(stripped.contains("Ana Başlık"))
        assertTrue(stripped.contains("Alt Başlık"))
        assertTrue(stripped.contains("kalın metin"))
        assertTrue(stripped.contains("italik metin"))
        assertTrue(stripped.contains("üstü çizili"))
        assertTrue(stripped.contains("Tıklayınız (https://example.com)"))
        assertTrue(stripped.contains("Seyahat Planı"))
    }

    @Test
    fun stripMarkdown_removesCodeBlocksAndInlineCode() {
        val markdown = """
            Fonksiyon çağrısı: `calculateTotal()`
            ```kotlin
            val x = 10
            val y = 20
            ```
            Sonuç hazır.
        """.trimIndent()

        val stripped = ClipboardHelper.stripMarkdown(markdown)

        assertFalse(stripped.contains("```"))
        assertFalse(stripped.contains("`"))
        assertTrue(stripped.contains("calculateTotal()"))
        assertTrue(stripped.contains("val x = 10"))
        assertTrue(stripped.contains("Sonuç hazır."))
    }

    @Test
    fun stripMarkdown_cleansMarkdownTables() {
        val markdown = """
            | Ürün | Adet | Fiyat |
            | :--- | :---: | ---: |
            | Elma | 5 | 25 TL |
            | Armut | 2 | 30 TL |
        """.trimIndent()

        val stripped = ClipboardHelper.stripMarkdown(markdown)

        assertFalse(stripped.contains("---"))
        assertTrue(stripped.contains("Elma"))
        assertTrue(stripped.contains("25 TL"))
        assertTrue(stripped.contains("Armut"))
    }

    @Test
    fun toPlainText_combinesTitleAndContentCleanly() {
        val title = "# Alışveriş Listesi"
        val content = """
            - [ ] Süt
            - [x] Ekmek
            > Marketten al
        """.trimIndent()

        val plain = ClipboardHelper.toPlainText(title, content)

        assertFalse(plain.startsWith("#"))
        assertTrue(plain.startsWith("Alışveriş Listesi"))
        assertTrue(plain.contains("• Süt"))
        assertTrue(plain.contains("• Ekmek"))
        assertTrue(plain.contains("Marketten al"))
    }

    @Test
    fun toPlainText_emptyTitleOnlyReturnsContent() {
        val plain = ClipboardHelper.toPlainText("", "Yalnızca içerik var.")
        assertEquals("Yalnızca içerik var.", plain)
    }

    @Test
    fun formatAsMarkdown_addsHeadingAndTagsCorrectly() {
        val title = "Toplantı Gündemi"
        val content = "1. Çeyrek hedefleri incelenecek."
        val tags = listOf("iş", "#strateji")

        val markdown = ClipboardHelper.formatAsMarkdown(title, content, tags)

        assertTrue(markdown.startsWith("# Toplantı Gündemi"))
        assertTrue(markdown.contains("1. Çeyrek hedefleri incelenecek."))
        assertTrue(markdown.contains("Etiketler: #iş #strateji"))
    }

    @Test
    fun formatAsMarkdown_doesNotDuplicateExistingTags() {
        val title = "Seyahat Notu"
        val content = "Roma seyahatinde gidilecek yerler. #gezi"
        val tags = listOf("gezi", "tatil")

        val markdown = ClipboardHelper.formatAsMarkdown(title, content, tags)

        // #gezi already exists in content, so only #tatil should be added in footer
        assertTrue(markdown.contains("Etiketler: #tatil"))
        assertFalse(markdown.contains("Etiketler: #gezi #tatil"))
    }

    @Test
    fun extractSummary_returnsFirstSentencesOrLimitsWords() {
        val content = """
            Birinci cümle burada bitiyor. İkinci cümle oldukça önemli detaylar içeriyor!
            Üçüncü cümle ise gelecek adımları açıklıyor? Dördüncü cümle özete dahil edilmemeli.
        """.trimIndent()

        val summary = ClipboardHelper.extractSummary(content, maxSentences = 2)

        assertTrue(summary.contains("Birinci cümle burada bitiyor."))
        assertTrue(summary.contains("İkinci cümle oldukça önemli detaylar içeriyor!"))
        assertFalse(summary.contains("Üçüncü cümle"))
        assertFalse(summary.contains("Dördüncü cümle"))
    }

    @Test
    fun extractSummary_limitsWordCountWithEllipsis() {
        val longContent = "Bu çok uzun bir cümle olup " + "kelime ".repeat(30) + "içermektedir."
        val summary = ClipboardHelper.extractSummary(longContent, maxWords = 10)

        assertTrue(summary.endsWith("..."))
        val wordCount = summary.removeSuffix("...").trim().split(Regex("\\s+")).size
        assertEquals(10, wordCount)
    }

    @Test
    fun extractRemainingChecklist_extractsOnlyUncheckedItems() {
        val content = """
            Günlük yapılacaklar:
            - [ ] Raporu hazırla
            - [x] E-postaları cevapla
            * [ ] Sunum slaydını tamamla
            Son not.
        """.trimIndent()

        val remaining = ClipboardHelper.extractRemainingChecklist(content, formatAsBullets = false)

        assertTrue(remaining.contains("[ ] Raporu hazırla"))
        assertTrue(remaining.contains("[ ] Sunum slaydını tamamla"))
        assertFalse(remaining.contains("E-postaları cevapla"))
        assertFalse(remaining.contains("Günlük yapılacaklar"))
        assertFalse(remaining.contains("Son not."))
    }

    @Test
    fun extractCompletedChecklist_extractsOnlyCheckedItems() {
        val content = """
            - [ ] Görev 1
            - [x] Görev 2
            - [X] Görev 3
        """.trimIndent()

        val completed = ClipboardHelper.extractCompletedChecklist(content, formatAsBullets = false)

        assertTrue(completed.contains("[x] Görev 2"))
        assertTrue(completed.contains("[x] Görev 3"))
        assertFalse(completed.contains("Görev 1"))
    }

    @Test
    fun hasChecklistItems_detectsChecklistPresence() {
        assertTrue(ClipboardHelper.hasChecklistItems("- [ ] Yapılacak iş"))
        assertTrue(ClipboardHelper.hasChecklistItems("- [x] Biten iş"))
        assertFalse(ClipboardHelper.hasChecklistItems("Normal bir not metni"))
    }

    @Test
    fun countWords_calculatesAccurately() {
        assertEquals(0, ClipboardHelper.countWords(""))
        assertEquals(0, ClipboardHelper.countWords("    \n\t  "))
        assertEquals(4, ClipboardHelper.countWords("Bu bir test metnidir"))
        assertEquals(3, ClipboardHelper.countWords("Birinci   \n\n İkinci \t Üçüncü"))
    }
}
