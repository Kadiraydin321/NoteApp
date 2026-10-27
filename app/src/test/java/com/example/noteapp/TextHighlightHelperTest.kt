package com.example.noteapp

import com.example.noteapp.domain.util.TextHighlightHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextHighlightHelperTest {

    @Test
    fun testEmptyQueryReturnsOriginalText() {
        val original = "Merhaba Dünya"
        val result = TextHighlightHelper.highlightQuery(original, "")
        assertEquals(original, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun testSingleMatchHighlighting() {
        val text = "Toplantı notları ve kararlar"
        val query = "toplantı"
        val result = TextHighlightHelper.highlightQuery(text, query)

        assertEquals(text, result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(8, result.spanStyles[0].end)
    }

    @Test
    fun testMultipleMatchesHighlighting() {
        val text = "Not almak güzeldir. Her not bir fikirdir."
        val query = "not"
        val result = TextHighlightHelper.highlightQuery(text, query)

        assertEquals(text, result.text)
        assertEquals(2, result.spanStyles.size)
        // First match: "Not" at index 0..3
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(3, result.spanStyles[0].end)
        // Second match: "not" at index 24..27
        assertEquals(24, result.spanStyles[1].start)
        assertEquals(27, result.spanStyles[1].end)
    }

    @Test
    fun testNoMatchHighlighting() {
        val text = "Bugün hava çok güzel"
        val query = "akşam"
        val result = TextHighlightHelper.highlightQuery(text, query)

        assertEquals(text, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun testMultiTokenHighlighting() {
        val text = "Android geliştiricisi Jetpack Compose ile harika arayüzler tasarlar."
        val query = "android compose"
        val result = TextHighlightHelper.highlightQuery(text, query)

        assertEquals(text, result.text)
        // İki farklı token ("android" ve "compose") eşleşti ve iki ayrı span oluşturdu
        assertEquals(2, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start) // "Android"
        assertEquals(7, result.spanStyles[0].end)
        assertEquals(30, result.spanStyles[1].start) // "Compose"
        assertEquals(37, result.spanStyles[1].end)
    }

    @Test
    fun testHashtagTokenHighlighting() {
        val text = "Sprint planlaması yapıldı, #mobil ekibi görevleri aldı."
        val query = "#mobil"
        val result = TextHighlightHelper.highlightQuery(text, query)

        assertEquals(text, result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles[0]
        assertEquals("#mobil", text.substring(span.start, span.end))
    }
}
