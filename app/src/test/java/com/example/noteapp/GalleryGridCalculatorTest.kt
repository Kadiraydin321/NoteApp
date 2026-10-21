package com.example.noteapp

import com.example.noteapp.domain.util.GalleryGridCalculator
import org.junit.Assert.*
import org.junit.Test

class GalleryGridCalculatorTest {

    @Test
    fun isAudioAttachment_detectsAudioExtensions() {
        assertTrue(GalleryGridCalculator.isAudioAttachment("voice_123.mp4"))
        assertTrue(GalleryGridCalculator.isAudioAttachment("audio.m4a"))
        assertTrue(GalleryGridCalculator.isAudioAttachment("recording.mp3"))
        assertFalse(GalleryGridCalculator.isAudioAttachment("photo.jpg"))
        assertFalse(GalleryGridCalculator.isAudioAttachment("drawing.png"))
    }

    @Test
    fun calculateGalleryGrid_singleImage_isFullWidth() {
        val items = GalleryGridCalculator.calculateGalleryGrid(listOf("/path/img1.jpg"))
        assertEquals(1, items.size)
        assertTrue(items[0].isFullWidth)
        assertEquals(0, items[0].originalIndex)
        assertEquals(0, items[0].overflowCount)
    }

    @Test
    fun calculateGalleryGrid_twoImages_twoHalfWidthItems() {
        val items = GalleryGridCalculator.calculateGalleryGrid(listOf("/path/img1.jpg", "/path/img2.jpg"))
        assertEquals(2, items.size)
        assertFalse(items[0].isFullWidth)
        assertFalse(items[1].isFullWidth)
        assertEquals(0, items[0].originalIndex)
        assertEquals(1, items[1].originalIndex)
    }

    @Test
    fun calculateGalleryGrid_threeImages_oneFullWidthTwoHalfWidth() {
        val items = GalleryGridCalculator.calculateGalleryGrid(listOf("a.jpg", "b.jpg", "c.jpg"))
        assertEquals(3, items.size)
        assertTrue(items[0].isFullWidth)
        assertFalse(items[1].isFullWidth)
        assertFalse(items[2].isFullWidth)
    }

    @Test
    fun calculateGalleryGrid_fourImages_fourHalfWidthItems() {
        val items = GalleryGridCalculator.calculateGalleryGrid(listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg"))
        assertEquals(4, items.size)
        assertEquals(0, items[3].overflowCount)
    }

    @Test
    fun calculateGalleryGrid_fiveImages_fourthItemHasOverflowCountTwo() {
        val items = GalleryGridCalculator.calculateGalleryGrid(listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg"))
        assertEquals(4, items.size)
        assertEquals(2, items[3].overflowCount) // 5 - 3 = 2 more images (+2)
    }

    @Test
    fun calculateGalleryGrid_sevenImages_fourthItemHasOverflowCountFour() {
        val items = GalleryGridCalculator.calculateGalleryGrid(listOf("1", "2", "3", "4", "5", "6", "7"))
        assertEquals(4, items.size)
        assertEquals(4, items[3].overflowCount) // 7 - 3 = 4 more images (+4)
    }
}
