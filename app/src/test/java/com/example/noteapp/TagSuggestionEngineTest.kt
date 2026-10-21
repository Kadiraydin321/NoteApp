package com.example.noteapp

import com.example.noteapp.domain.util.TagSuggestionEngine
import org.junit.Assert.*
import org.junit.Test

class TagSuggestionEngineTest {

    @Test
    fun findActiveTagQuery_cursorAtEndOfTag_returnsCorrectQuery() {
        val text = "Bu bir #proje notudur"
        val query = TagSuggestionEngine.findActiveTagQuery(text, cursorPosition = 13) // right after '#proje'
        assertNotNull(query)
        assertEquals("proje", query?.query)
        assertEquals(7, query?.startIndex)
        assertEquals(13, query?.endIndex)
    }

    @Test
    fun findActiveTagQuery_justTypedHash_returnsEmptyQuery() {
        val text = "Yeni not #"
        val query = TagSuggestionEngine.findActiveTagQuery(text, cursorPosition = text.length)
        assertNotNull(query)
        assertEquals("", query?.query)
        assertEquals(9, query?.startIndex)
        assertEquals(10, query?.endIndex)
    }

    @Test
    fun findActiveTagQuery_spaceAfterHash_returnsNull() {
        val text = "Yeni not # "
        val query = TagSuggestionEngine.findActiveTagQuery(text, cursorPosition = text.length)
        assertNull(query)
    }

    @Test
    fun findActiveTagQuery_hashInsideWord_returnsNull() {
        val text = "email@test#domain"
        val query = TagSuggestionEngine.findActiveTagQuery(text, cursorPosition = text.length)
        assertNull(query)
    }

    @Test
    fun applyTagSuggestion_replacesQueryWithFullTagAndSpace() {
        val text = "Bugün #pr işleri yapılacak"
        val query = TagSuggestionEngine.findActiveTagQuery(text, cursorPosition = 9) // after #pr
        assertNotNull(query)
        val (newText, newCursor) = TagSuggestionEngine.applyTagSuggestion(text, query!!, "proje")
        assertEquals("Bugün #proje işleri yapılacak", newText)
        assertEquals(13, newCursor)
    }

    @Test
    fun applyTagSuggestion_atEndOfString_appendsSpaceAndPlacesCursor() {
        val text = "Yeni not #pr"
        val query = TagSuggestionEngine.findActiveTagQuery(text, cursorPosition = text.length)
        assertNotNull(query)
        val (newText, newCursor) = TagSuggestionEngine.applyTagSuggestion(text, query!!, "proje")
        assertEquals("Yeni not #proje ", newText)
        assertEquals(16, newCursor)
        assertEquals(newText.length, newCursor)
    }

    @Test
    fun replaceTagInText_replacesExactTagWithoutAffectingSubstrings() {
        val text = "Notlarımızda #plan ve #planlama mevcut."
        val result = TagSuggestionEngine.replaceTagInText(text, oldTag = "plan", newTag = "strateji")
        assertEquals("Notlarımızda #strateji ve #planlama mevcut.", result)
    }

    @Test
    fun replaceTagInText_caseInsensitiveMatch_preservesOtherText() {
        val text = "Görevler: #PROJE tamamlandı."
        val result = TagSuggestionEngine.replaceTagInText(text, oldTag = "proje", newTag = "gorev")
        assertEquals("Görevler: #gorev tamamlandı.", result)
    }

    @Test
    fun removeTagFromText_removesOnlyTargetTag() {
        val text = "Bu not #eski ve #kalici etiketlere sahip."
        val result = TagSuggestionEngine.removeTagFromText(text, tagToRemove = "eski")
        assertEquals("Bu not ve #kalici etiketlere sahip.", result)
    }
}
