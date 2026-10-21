package com.example.noteapp.domain.util

/**
 * Ekran boyutu sınıfı.
 */
enum class ScreenSizeClass {
    COMPACT,   // < 600 dp (Standart dikey telefon)
    MEDIUM,    // 600 dp - 839 dp (Küçük tablet, katlanabilir açık ekran veya yatay telefon)
    EXPANDED   // >= 840 dp (Büyük tablet, masaüstü, Chromebook)
}

/**
 * Bölünmüş ekran oran seçenekleri.
 */
enum class SplitRatio(val editorWeight: Float, val previewWeight: Float, val title: String) {
    EQUAL(0.5f, 0.5f, "%50 / %50 Eşit"),
    WIDE_EDITOR(0.6f, 0.4f, "%60 Düzenleyici / %40 Önizleme"),
    WIDE_PREVIEW(0.4f, 0.6f, "%40 Düzenleyici / %60 Önizleme")
}

/**
 * Çift Bölmeli Canlı Düzenleyici ve Geniş Ekran / Tablet Düzeni Yardımcısı.
 * Geniş ekranlarda (Tablet, Katlanabilir, Yatay Mod) aynı anda sol tarafta kaynak metin editörü,
 * sağ tarafta canlı render edilen Markdown önizlemesi sunar.
 */
object SplitPaneLayoutHelper {

    const val SPLIT_PANE_MIN_WIDTH_DP = 600

    /**
     * Verilen ekran genişliğine göre boyut sınıfını belirler.
     */
    fun getScreenSizeClass(screenWidthDp: Int): ScreenSizeClass {
        return when {
            screenWidthDp < 600 -> ScreenSizeClass.COMPACT
            screenWidthDp < 840 -> ScreenSizeClass.MEDIUM
            else -> ScreenSizeClass.EXPANDED
        }
    }

    /**
     * Ekranın bölünmüş çift bölmeli düzeni (Split-Pane) destekleyip desteklemediğini kontrol eder.
     */
    fun isSplitPaneSupported(screenWidthDp: Int, isLandscape: Boolean = false): Boolean {
        return screenWidthDp >= SPLIT_PANE_MIN_WIDTH_DP || (isLandscape && screenWidthDp >= 520)
    }

    /**
     * Belirtilen orana göre editör ve önizleme ağırlıklarını döndürür.
     */
    fun getPaneWeights(ratio: SplitRatio): Pair<Float, Float> {
        return Pair(ratio.editorWeight, ratio.previewWeight)
    }

    /**
     * İki liste veya kaydırma bileşeni arasındaki pürüzsüz kaydırma yüzdesini normalize eder (0.0f..1.0f).
     */
    fun calculateScrollSyncFraction(currentScrollPx: Int, maxScrollPx: Int): Float {
        if (maxScrollPx <= 0) return 0f
        return (currentScrollPx.toFloat() / maxScrollPx.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Normalize edilmiş kaydırma yüzdesini hedef bileşenin piksel konumuna çevirir.
     */
    fun calculateTargetScrollPosition(fraction: Float, targetMaxScrollPx: Int): Int {
        if (targetMaxScrollPx <= 0) return 0
        return (fraction.coerceIn(0f, 1f) * targetMaxScrollPx).toInt()
    }
}
