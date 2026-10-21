package com.example.noteapp

import com.example.noteapp.domain.util.DrawingScaleHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingScaleHelperTest {

    @Test
    fun calculateAspectFit_squareContentInSquareContainer_fillsExactly() {
        val rect = DrawingScaleHelper.calculateAspectFit(
            containerWidth = 1000f,
            containerHeight = 1000f,
            contentWidth = 500f,
            contentHeight = 500f
        )
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(1000f, rect.width, 0.01f)
        assertEquals(1000f, rect.height, 0.01f)
    }

    @Test
    fun calculateAspectFit_wideContentInSquareContainer_centersVertically() {
        // Content: 16:9 (1600 x 900), Container: 1000 x 1000
        val rect = DrawingScaleHelper.calculateAspectFit(
            containerWidth = 1000f,
            containerHeight = 1000f,
            contentWidth = 1600f,
            contentHeight = 900f
        )
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(1000f, rect.width, 0.01f)
        val expectedHeight = 1000f / (1600f / 900f) // 562.5f
        assertEquals(expectedHeight, rect.height, 0.01f)
        val expectedTop = (1000f - expectedHeight) / 2f // 218.75f
        assertEquals(expectedTop, rect.top, 0.01f)
    }

    @Test
    fun calculateAspectFit_tallContentInSquareContainer_centersHorizontally() {
        // Content: 9:16 (900 x 1600), Container: 1000 x 1000
        val rect = DrawingScaleHelper.calculateAspectFit(
            containerWidth = 1000f,
            containerHeight = 1000f,
            contentWidth = 900f,
            contentHeight = 1600f
        )
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(1000f, rect.height, 0.01f)
        val expectedWidth = 1000f * (900f / 1600f) // 562.5f
        assertEquals(expectedWidth, rect.width, 0.01f)
        val expectedLeft = (1000f - expectedWidth) / 2f
        assertEquals(expectedLeft, rect.left, 0.01f)
    }

    @Test
    fun calculateAspectFit_zeroOrNegativeDimensions_handlesGracefully() {
        val rect = DrawingScaleHelper.calculateAspectFit(0f, 1000f, 500f, 500f)
        assertEquals(0f, rect.width, 0.01f)
        assertEquals(1000f, rect.height, 0.01f)
    }
}
