package com.example.noteapp

import com.example.noteapp.domain.util.ScreenSizeClass
import com.example.noteapp.domain.util.SplitPaneLayoutHelper
import com.example.noteapp.domain.util.SplitRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPaneLayoutHelperTest {

    @Test
    fun getScreenSizeClass_categorizesDimensionsCorrectly() {
        assertEquals(ScreenSizeClass.COMPACT, SplitPaneLayoutHelper.getScreenSizeClass(360))
        assertEquals(ScreenSizeClass.COMPACT, SplitPaneLayoutHelper.getScreenSizeClass(599))
        assertEquals(ScreenSizeClass.MEDIUM, SplitPaneLayoutHelper.getScreenSizeClass(600))
        assertEquals(ScreenSizeClass.MEDIUM, SplitPaneLayoutHelper.getScreenSizeClass(839))
        assertEquals(ScreenSizeClass.EXPANDED, SplitPaneLayoutHelper.getScreenSizeClass(840))
        assertEquals(ScreenSizeClass.EXPANDED, SplitPaneLayoutHelper.getScreenSizeClass(1200))
    }

    @Test
    fun isSplitPaneSupported_identifiesTabletAndLandscapeSupport() {
        // Dikey küçük telefon: desteklenmez
        assertFalse(SplitPaneLayoutHelper.isSplitPaneSupported(screenWidthDp = 390, isLandscape = false))

        // Yatay telefon (>=520 dp): desteklenir
        assertTrue(SplitPaneLayoutHelper.isSplitPaneSupported(screenWidthDp = 580, isLandscape = true))

        // Tablet dikey veya yatay (>=600 dp): desteklenir
        assertTrue(SplitPaneLayoutHelper.isSplitPaneSupported(screenWidthDp = 720, isLandscape = false))
        assertTrue(SplitPaneLayoutHelper.isSplitPaneSupported(screenWidthDp = 1024, isLandscape = true))
    }

    @Test
    fun getPaneWeights_returnsValidRatios() {
        val (eqEd, eqPrev) = SplitPaneLayoutHelper.getPaneWeights(SplitRatio.EQUAL)
        assertEquals(0.5f, eqEd, 0.01f)
        assertEquals(0.5f, eqPrev, 0.01f)

        val (wEd, wPrev) = SplitPaneLayoutHelper.getPaneWeights(SplitRatio.WIDE_EDITOR)
        assertEquals(0.6f, wEd, 0.01f)
        assertEquals(0.4f, wPrev, 0.01f)

        val (pEd, pPrev) = SplitPaneLayoutHelper.getPaneWeights(SplitRatio.WIDE_PREVIEW)
        assertEquals(0.4f, pEd, 0.01f)
        assertEquals(0.6f, pPrev, 0.01f)
    }

    @Test
    fun calculateScrollSyncFraction_normalizesAndClamps() {
        assertEquals(0f, SplitPaneLayoutHelper.calculateScrollSyncFraction(0, 1000), 0.01f)
        assertEquals(0.5f, SplitPaneLayoutHelper.calculateScrollSyncFraction(500, 1000), 0.01f)
        assertEquals(1.0f, SplitPaneLayoutHelper.calculateScrollSyncFraction(1000, 1000), 0.01f)
        // Negatif ve taşma değerleri
        assertEquals(0f, SplitPaneLayoutHelper.calculateScrollSyncFraction(-50, 1000), 0.01f)
        assertEquals(1.0f, SplitPaneLayoutHelper.calculateScrollSyncFraction(1500, 1000), 0.01f)
    }

    @Test
    fun calculateTargetScrollPosition_mapsFractionToPixels() {
        assertEquals(0, SplitPaneLayoutHelper.calculateTargetScrollPosition(0f, 800))
        assertEquals(400, SplitPaneLayoutHelper.calculateTargetScrollPosition(0.5f, 800))
        assertEquals(800, SplitPaneLayoutHelper.calculateTargetScrollPosition(1f, 800))
        assertEquals(0, SplitPaneLayoutHelper.calculateTargetScrollPosition(0.5f, 0))
    }
}
