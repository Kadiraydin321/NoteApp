package com.example.noteapp

import com.example.noteapp.domain.util.NoteMetricsEngine
import com.example.noteapp.domain.util.ReadabilityGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMetricsEngineTest {

    @Test
    fun calculateMetrics_emptyContent_returnsZeroMetrics() {
        val metrics = NoteMetricsEngine.calculateMetrics(title = "", content = "")

        assertEquals(0, metrics.wordCount)
        assertEquals(0, metrics.charCountWithSpaces)
        assertEquals(0, metrics.charCountWithoutSpaces)
        assertEquals(0, metrics.paragraphCount)
        assertEquals(0, metrics.sentenceCount)
        assertEquals("< 1 dk", metrics.readingTimeFormatted)
        assertEquals(ReadabilityGrade.VERY_EASY, metrics.readabilityGrade)
        assertTrue(metrics.topKeywords.isEmpty())
    }

    @Test
    fun calculateMetrics_simpleText_computesCorrectCounts() {
        val title = "Toplantı Notları"
        val content = """
            Bugün yeni projenin mimarisini tartıştık.
            Tüm ekip üyeleri fikirlerini paylaştı ve karar verildi.
        """.trimIndent()

        val metrics = NoteMetricsEngine.calculateMetrics(title, content)

        // 2 başlık kelimesi + 13 içerik kelimesi = 15 kelime
        assertTrue(metrics.wordCount in 14..16)
        assertTrue(metrics.sentenceCount in 2..3)
        assertTrue(metrics.paragraphCount >= 1)
        assertTrue(metrics.charCountWithoutSpaces > 50)
        assertTrue(metrics.lexicalRichnessPercent > 50)
    }

    @Test
    fun countSyllables_countsVowelsCorrectly() {
        assertEquals(3, NoteMetricsEngine.countSyllables("merhaba"))
        assertEquals(4, NoteMetricsEngine.countSyllables("bilgisayar"))
        assertEquals(1, NoteMetricsEngine.countSyllables("test"))
        assertEquals(2, NoteMetricsEngine.countSyllables("mobil"))
        assertEquals(3, NoteMetricsEngine.countSyllables("Android"))
    }

    @Test
    fun stripMarkdownAndSpecialChars_removesFormattingArtifacts() {
        val markdown = """
            # Başlık
            **Kalın yazı** ve *italik metin*.
            - [ ] Yapılacak iş 1
            - [x] Tamamlanan iş 2
            [Google](https://google.com)
            > Bu bir alıntıdır.
        """.trimIndent()

        val clean = NoteMetricsEngine.stripMarkdownAndSpecialChars(markdown)

        assertFalse(clean.contains("#"))
        assertFalse(clean.contains("**"))
        assertFalse(clean.contains("[x]"))
        assertFalse(clean.contains("https://google.com"))
        assertTrue(clean.contains("Google"))
        assertTrue(clean.contains("Başlık"))
        assertTrue(clean.contains("Kalın yazı"))
    }

    @Test
    fun formatMinutes_formatsShortAndLongDurations() {
        assertEquals("< 1 dk", NoteMetricsEngine.formatMinutes(0.3f))
        assertEquals("1 dk", NoteMetricsEngine.formatMinutes(1.0f))
        assertEquals("2 dk 30 sn", NoteMetricsEngine.formatMinutes(2.5f))
    }

    @Test
    fun extractTopKeywords_filtersStopWordsAndRanksByFrequency() {
        val words = listOf(
            "kotlin", "ve", "android", "ile", "harika", "bir", "android",
            "uygulaması", "yaptık", "android", "kotlin"
        )

        val keywords = NoteMetricsEngine.extractTopKeywords(words, limit = 3)

        assertNotNull(keywords)
        assertEquals(3, keywords.size)
        // En çok geçen 'android' (3 kez)
        assertEquals("android", keywords[0].word)
        assertEquals(3, keywords[0].count)
        // İkinci 'kotlin' (2 kez)
        assertEquals("kotlin", keywords[1].word)
        assertEquals(2, keywords[1].count)
        // Stop word 've', 'ile', 'bir' elenmiş olmalı
        assertTrue(keywords.none { it.word in listOf("ve", "ile", "bir") })
    }

    @Test
    fun calculateMetrics_readabilityGrade_producesValidResult() {
        val technicalContent = """
            Asenkron reaktif akış mimarisi, dağıtık sistemler arasındaki mikroservis iletişimini optimize eder.
            İşlem parçacıklarının senkronizasyon maliyetini minimize etmek amacıyla aktör modeli kullanılır.
        """.trimIndent()

        val metrics = NoteMetricsEngine.calculateMetrics("Teknik Mimari", technicalContent)

        assertTrue(metrics.readabilityScore in 0f..100f)
        assertNotNull(metrics.readabilityGrade)
    }

    private fun assertFalse(condition: Boolean) {
        org.junit.Assert.assertFalse(condition)
    }
}
