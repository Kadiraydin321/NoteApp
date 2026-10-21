package com.example.noteapp

import com.example.noteapp.domain.util.CodeSyntaxHighlighter
import com.example.noteapp.domain.util.CodeTokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSyntaxHighlighterTest {

    @Test
    fun normalizeLanguageName_maps_aliases_correctly() {
        assertEquals("Kotlin", CodeSyntaxHighlighter.normalizeLanguageName("kt"))
        assertEquals("Kotlin", CodeSyntaxHighlighter.normalizeLanguageName("kotlin"))
        assertEquals("Python", CodeSyntaxHighlighter.normalizeLanguageName("py"))
        assertEquals("Python", CodeSyntaxHighlighter.normalizeLanguageName("python"))
        assertEquals("JavaScript", CodeSyntaxHighlighter.normalizeLanguageName("js"))
        assertEquals("TypeScript", CodeSyntaxHighlighter.normalizeLanguageName("ts"))
        assertEquals("SQL", CodeSyntaxHighlighter.normalizeLanguageName("sql"))
        assertEquals("JSON", CodeSyntaxHighlighter.normalizeLanguageName("json"))
        assertEquals("Bash", CodeSyntaxHighlighter.normalizeLanguageName("sh"))
        assertEquals("Bash", CodeSyntaxHighlighter.normalizeLanguageName("bash"))
        assertEquals("Kod", CodeSyntaxHighlighter.normalizeLanguageName(""))
        assertEquals("Rust", CodeSyntaxHighlighter.normalizeLanguageName("rust"))
    }

    @Test
    fun tokenize_kotlin_code_identifies_keywords_strings_comments_and_numbers() {
        val code = """
            // Bir fonksiyon
            fun hesapla(x: Int): String {
                val sonuc = 42
                return "Değer: ${'$'}sonuc"
            }
        """.trimIndent()

        val tokens = CodeSyntaxHighlighter.tokenize(code, "kotlin")
        assertTrue(tokens.isNotEmpty())

        val commentTokens = tokens.filter { it.type == CodeTokenType.COMMENT }
        assertTrue(commentTokens.any { it.text.contains("Bir fonksiyon") })

        val keywordTokens = tokens.filter { it.type == CodeTokenType.KEYWORD }
        val keywordTexts = keywordTokens.map { it.text }
        assertTrue(keywordTexts.contains("fun"))
        assertTrue(keywordTexts.contains("val"))
        assertTrue(keywordTexts.contains("return"))

        val numberTokens = tokens.filter { it.type == CodeTokenType.NUMBER }
        assertTrue(numberTokens.any { it.text == "42" })

        val stringTokens = tokens.filter { it.type == CodeTokenType.STRING }
        assertTrue(stringTokens.isNotEmpty())
    }

    @Test
    fun tokenize_python_code_recognizes_hash_comments_and_def() {
        val code = """
            # Python kodu
            def topla(a, b):
                return a + b
        """.trimIndent()

        val tokens = CodeSyntaxHighlighter.tokenize(code, "python")
        val commentTokens = tokens.filter { it.type == CodeTokenType.COMMENT }
        assertTrue(commentTokens.any { it.text.startsWith("# Python") })

        val keywordTokens = tokens.filter { it.type == CodeTokenType.KEYWORD }
        val keywordTexts = keywordTokens.map { it.text }
        assertTrue(keywordTexts.contains("def"))
        assertTrue(keywordTexts.contains("return"))
    }

    @Test
    fun tokenize_sql_code_handles_case_insensitive_keywords_and_dash_comments() {
        val code = """
            -- SQL sorgusu
            select id, name from users where age >= 18;
        """.trimIndent()

        val tokens = CodeSyntaxHighlighter.tokenize(code, "sql")
        val commentTokens = tokens.filter { it.type == CodeTokenType.COMMENT }
        assertTrue(commentTokens.any { it.text.startsWith("-- SQL") })

        val keywordTokens = tokens.filter { it.type == CodeTokenType.KEYWORD }
        val keywordTexts = keywordTokens.map { it.text.lowercase() }
        assertTrue(keywordTexts.contains("select"))
        assertTrue(keywordTexts.contains("from"))
        assertTrue(keywordTexts.contains("where"))
    }

    @Test
    fun tokenize_empty_code_returns_empty_list() {
        val tokens = CodeSyntaxHighlighter.tokenize("", "kotlin")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun highlightToAnnotatedString_produces_valid_annotated_string_matching_length() {
        val code = "val x = 100"
        val annotated = CodeSyntaxHighlighter.highlightToAnnotatedString(code, "kotlin")
        assertNotNull(annotated)
        assertEquals(code.length, annotated.text.length)
        assertEquals(code, annotated.text)
    }
}
