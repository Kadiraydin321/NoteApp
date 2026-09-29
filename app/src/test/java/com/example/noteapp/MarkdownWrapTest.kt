package com.example.noteapp

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.noteapp.presentation.components.applyMarkdownWrap
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownWrapTest {

    @Test
    fun testWordBoldToggle() {
        // İmleç kelime üstünde (collapsed): ilk tık kalın yapmalı, ikinci tık kaldırmalı
        var state = TextFieldValue("Bugün toplantı var", TextRange(8)) // "toplantı" içinde
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Bugün **toplantı** var", state.text)

        // İkinci kez basıldığında ** işaretleri kalkmalı, kelime temiz kalmalı
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Bugün toplantı var", state.text)

        // Üçüncü kez basıldığında tekrar kalın olmalı (yıldızlar üst üste yığılmamalı)
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Bugün **toplantı** var", state.text)
    }

    @Test
    fun testSelectedTextBoldToggle() {
        // Kullanıcı "toplantı" kelimesini seçti
        var state = TextFieldValue("Bugün toplantı var", TextRange(6, 14))
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Bugün **toplantı** var", state.text)

        // Tekrar basıldığında dıştaki ** işaretleri kalkmalı
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Bugün toplantı var", state.text)
    }

    @Test
    fun testBoldAndItalicInteraction() {
        // Kalın olan kelimeye italik ekleme ve geri alma
        var state = TextFieldValue("Bugün toplantı var", TextRange(8))
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Bugün **toplantı** var", state.text)

        // İtalik ekle -> ***toplantı*** olmalı
        applyMarkdownWrap(state, "*", "*", { state = it }, {})
        assertEquals("Bugün ***toplantı*** var", state.text)

        // İtalik kaldır -> **toplantı** kalmalı
        applyMarkdownWrap(state, "*", "*", { state = it }, {})
        assertEquals("Bugün **toplantı** var", state.text)

        // Kalın kaldır -> toplantı kalmalı
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Bugün toplantı var", state.text)
    }

    @Test
    fun testEmptyCursorToggle() {
        // Boşlukta kalın basıldığında **** eklenmeli
        var state = TextFieldValue("Not ", TextRange(4))
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Not ****", state.text)
        assertEquals(6, state.selection.start)

        // Tekrar basıldığında **** kalkmalı
        applyMarkdownWrap(state, "**", "**", { state = it }, {})
        assertEquals("Not ", state.text)
        assertEquals(4, state.selection.start)
    }

    @Test
    fun testStrikethroughAndHighlightToggle() {
        var state = TextFieldValue("kelime", TextRange(2))
        applyMarkdownWrap(state, "~~", "~~", { state = it }, {})
        assertEquals("~~kelime~~", state.text)

        applyMarkdownWrap(state, "~~", "~~", { state = it }, {})
        assertEquals("kelime", state.text)

        applyMarkdownWrap(state, "==", "==", { state = it }, {})
        assertEquals("==kelime==", state.text)

        applyMarkdownWrap(state, "==", "==", { state = it }, {})
        assertEquals("kelime", state.text)
    }
}
