package com.example.noteapp

import com.example.noteapp.domain.util.NoteSummarizerEngine
import org.junit.Assert.*
import org.junit.Test

class NoteSummarizerEngineTest {

    @Test
    fun summarize_shortText_returnsDirectText() {
        val short = "Kısa bir not."
        val result = NoteSummarizerEngine.summarize(short)

        assertEquals(short, result.executiveSummary)
        assertEquals(0, result.readTimeSavedMinutes)
    }

    @Test
    fun summarize_longStructuredDocument_extractsSummaryAndActionItems() {
        val markdown = """
            # Mobil Uygulama Mimarisi Toplantısı
            
            Uygulamanın yeni sürümünde Clean Architecture ve MVI desenleri temel alınacaktır.
            Veritabanı işlemleri Room üzerinden yönetilecek ve yerel şifreleme sağlanacaktır.
            Önemli: Kullanıcı gizliliği için tüm biyometrik veriler cihaz içinde kalmalıdır.
            
            Geliştirici ekibi Cuma gününe kadar API endpoint testlerini tamamlamalı.
            Yeni tasarım şeması gözden geçirilecek ve onaylanacak.
            
            Sonuç olarak performans optimizasyonları ve bellek tasarrufu öncelikli hedeftir.
        """.trimIndent()

        val result = NoteSummarizerEngine.summarize(markdown, targetSummaryRatio = 0.5f)

        assertTrue(result.executiveSummary.isNotBlank())
        assertTrue(result.originalWordCount > result.summaryWordCount)
        assertTrue(result.keyTakeaways.isNotEmpty())
        
        // Eylem maddesi kontrolü (tamamlamalı / incelenecek / gözden geçirilecek)
        assertTrue(result.actionItems.any { it.contains("tamamlamalı") || it.contains("gözden") })

        // Ana çıkarım kontrolü (Önemli / Sonuç olarak)
        assertTrue(result.keyTakeaways.any { it.contains("biyometrik") || it.contains("performans") || it.contains("önemli") })
    }

    @Test
    fun cleanMarkdownFormatting_stripsHeadersBoldCode() {
        val raw = "## Başlık\nBu bir **kalın** metin ve `kod parçası` ile [[WikiLink]]."
        val cleaned = NoteSummarizerEngine.cleanMarkdownFormatting(raw)

        assertFalse(cleaned.contains("##"))
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("`"))
        assertFalse(cleaned.contains("[["))
        assertTrue(cleaned.contains("Başlık"))
        assertTrue(cleaned.contains("kalın metin"))
        assertTrue(cleaned.contains("WikiLink"))
    }

    @Test
    fun formatAsCallout_generatesAdmonitionBlock() {
        val markdown = """
            Android Jetpack Compose ile modern arayüzler inşa edilir.
            Önemli: Durum yönetimi MutableState ve StateFlow ile yapılmalıdır.
            Test senaryoları hazırlanmalı ve koşturulmalı.
        """.trimIndent()

        val summary = NoteSummarizerEngine.summarize(markdown)
        val callout = NoteSummarizerEngine.formatAsCallout(summary)

        assertTrue(callout.startsWith("> [!NOTE] 💡 Akıllı Not Özeti"))
        assertTrue(callout.contains("> **Anahtar Noktalar:**"))
    }
}
