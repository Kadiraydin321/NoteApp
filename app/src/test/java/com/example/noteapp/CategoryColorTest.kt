package com.example.noteapp

import com.example.noteapp.domain.model.Category
import org.junit.Assert.*
import org.junit.Test

class CategoryColorTest {

    @Test
    fun `default category color should be zero`() {
        val cat = Category(name = "Kişisel")
        assertEquals(0, cat.color)
    }

    @Test
    fun `category color palette contains default and distinct vibrant colors`() {
        val colors = Category.DEFAULT_COLORS
        assertTrue("Category palette should contain at least 8 colors", colors.size >= 8)
        assertEquals("First color should be 0 (neutral/transparent)", 0, colors.first())

        val distinctColors = colors.toSet()
        assertEquals("All palette colors should be unique", colors.size, distinctColors.size)
    }

    @Test
    fun `updating category color preserves id and name`() {
        val original = Category(id = 42, name = "İş Notları", color = 0)
        val crimson = Category.DEFAULT_COLORS[1]
        val updated = original.copy(color = crimson)

        assertEquals(42L, updated.id)
        assertEquals("İş Notları", updated.name)
        assertEquals(crimson, updated.color)
        assertNotEquals(original.color, updated.color)
    }

    @Test
    fun `renaming category preserves custom color`() {
        val emerald = Category.DEFAULT_COLORS[4]
        val original = Category(id = 10, name = "Eski İsim", color = emerald)
        val renamed = original.copy(name = "Yeni İsim")

        assertEquals(10L, renamed.id)
        assertEquals("Yeni İsim", renamed.name)
        assertEquals(emerald, renamed.color)
    }
}
