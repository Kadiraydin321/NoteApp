package com.example.noteapp.domain.util

data class AspectFitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Tuval veya ekran üzerinde görsellerin en-boy oranını (aspect ratio) koruyarak
 * ortalanmış yerleşimini hesaplayan yardımcı sınıf.
 */
object DrawingScaleHelper {

    fun calculateAspectFit(
        containerWidth: Float,
        containerHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ): AspectFitRect {
        if (containerWidth <= 0f || containerHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f) {
            return AspectFitRect(0f, 0f, containerWidth.coerceAtLeast(0f), containerHeight.coerceAtLeast(0f))
        }

        val containerRatio = containerWidth / containerHeight
        val contentRatio = contentWidth / contentHeight

        val (dstW, dstH) = if (contentRatio > containerRatio) {
            containerWidth to (containerWidth / contentRatio)
        } else {
            (containerHeight * contentRatio) to containerHeight
        }

        val left = (containerWidth - dstW) / 2f
        val top = (containerHeight - dstH) / 2f

        return AspectFitRect(left = left, top = top, width = dstW, height = dstH)
    }
}
