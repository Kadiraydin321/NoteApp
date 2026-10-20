package com.example.noteapp

import com.example.noteapp.domain.util.ReadingColumnSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingColumnSplitterTest {

    @Test
    fun `splitIntoBalancedColumns returns empty for blank content`() {
        val result = ReadingColumnSplitter.splitIntoBalancedColumns("")
        assertEquals("", result.leftColumn)
        assertEquals("", result.rightColumn)
    }

    @Test
    fun `splitIntoBalancedColumns puts single paragraph in left column`() {
        val singleParagraph = "This is a single short paragraph without breaks."
        val result = ReadingColumnSplitter.splitIntoBalancedColumns(singleParagraph)

        assertEquals(singleParagraph, result.leftColumn)
        assertEquals("", result.rightColumn)
    }

    @Test
    fun `splitIntoBalancedColumns balances multiple paragraphs evenly`() {
        val p1 = "Paragraph 1: Introduction to clean architecture in Android development."
        val p2 = "Paragraph 2: Jetpack Compose simplifies UI implementation with declarative paradigms."
        val p3 = "Paragraph 3: Room Database provides abstraction over SQLite with compile-time verification."
        val p4 = "Paragraph 4: Hilt streamlines dependency injection with standard annotations."

        val content = "$p1\n\n$p2\n\n$p3\n\n$p4"
        val result = ReadingColumnSplitter.splitIntoBalancedColumns(content)

        assertTrue(result.leftColumn.isNotEmpty())
        assertTrue(result.rightColumn.isNotEmpty())
        assertTrue(result.leftColumn.contains(p1))
        assertTrue(result.rightColumn.contains(p4))
    }

    @Test
    fun `splitIntoSemanticBlocks preserves code blocks intact`() {
        val content = """
            # Section Title
            Here is some text before code.
            
            ```kotlin
            fun calculateSum(a: Int, b: Int): Int {
                // Important calculation
                return a + b
            }
            ```
            
            Here is text after code.
        """.trimIndent()

        val blocks = ReadingColumnSplitter.splitIntoSemanticBlocks(content)

        // Find the code block among semantic blocks
        val codeBlock = blocks.find { it.contains("fun calculateSum") }
        org.junit.Assert.assertNotNull(codeBlock)
        assertTrue(codeBlock!!.startsWith("```kotlin"))
        assertTrue(codeBlock.endsWith("```"))
    }

    @Test
    fun `splitIntoBalancedColumns with code block does not split code block`() {
        val content = """
            Paragraph 1: Setup guide.
            
            ```kotlin
            val list = listOf(1, 2, 3)
            val doubled = list.map { it * 2 }
            ```
            
            Paragraph 2: Conclusion and summary of instructions.
        """.trimIndent()

        val result = ReadingColumnSplitter.splitIntoBalancedColumns(content)

        // Neither column should have broken backticks
        val leftCount = result.leftColumn.split("```").size - 1
        val rightCount = result.rightColumn.split("```").size - 1

        // In markdown, code block backticks must be paired (0 or 2 in each column)
        assertEquals(0, leftCount % 2)
        assertEquals(0, rightCount % 2)
    }
}
