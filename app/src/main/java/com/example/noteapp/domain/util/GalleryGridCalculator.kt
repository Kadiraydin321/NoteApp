package com.example.noteapp.domain.util

data class GalleryGridItem(
    val path: String,
    val originalIndex: Int,
    val isFullWidth: Boolean = false,
    val overflowCount: Int = 0 // > 0 if this item represents "+X" more images
)

/**
 * Not eklerindeki çoklu görseller için estetik galeri ızgarası (collage/grid)
 * ve taşma (overflow) düzenini hesaplayan yardımcı sınıf.
 */
object GalleryGridCalculator {

    fun isAudioAttachment(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")
    }

    fun isImageAttachment(path: String): Boolean {
        return !isAudioAttachment(path)
    }

    /**
     * Resim listesini estetik bir ızgara düzenine dönüştürür.
     * En fazla 4 öğe gösterilir:
     * - 1 görsel: Tam genişlik (isFullWidth = true)
     * - 2 görsel: 1x2 yan yana iki kart
     * - 3 görsel: 1 tam genişlik üst kart + 2 yan yana alt kart
     * - 4 görsel: 2x2 simetrik ızgara
     * - 5+ görsel: 2x2 ızgara ve 4. kart üzerinde "+X" kalan görsel rozeti
     */
    fun calculateGalleryGrid(imagePaths: List<String>): List<GalleryGridItem> {
        if (imagePaths.isEmpty()) return emptyList()

        return when (imagePaths.size) {
            1 -> listOf(GalleryGridItem(imagePaths[0], originalIndex = 0, isFullWidth = true))
            2 -> listOf(
                GalleryGridItem(imagePaths[0], originalIndex = 0, isFullWidth = false),
                GalleryGridItem(imagePaths[1], originalIndex = 1, isFullWidth = false)
            )
            3 -> listOf(
                GalleryGridItem(imagePaths[0], originalIndex = 0, isFullWidth = true),
                GalleryGridItem(imagePaths[1], originalIndex = 1, isFullWidth = false),
                GalleryGridItem(imagePaths[2], originalIndex = 2, isFullWidth = false)
            )
            4 -> listOf(
                GalleryGridItem(imagePaths[0], originalIndex = 0, isFullWidth = false),
                GalleryGridItem(imagePaths[1], originalIndex = 1, isFullWidth = false),
                GalleryGridItem(imagePaths[2], originalIndex = 2, isFullWidth = false),
                GalleryGridItem(imagePaths[3], originalIndex = 3, isFullWidth = false)
            )
            else -> {
                val remaining = imagePaths.size - 3
                listOf(
                    GalleryGridItem(imagePaths[0], originalIndex = 0, isFullWidth = false),
                    GalleryGridItem(imagePaths[1], originalIndex = 1, isFullWidth = false),
                    GalleryGridItem(imagePaths[2], originalIndex = 2, isFullWidth = false),
                    GalleryGridItem(imagePaths[3], originalIndex = 3, isFullWidth = false, overflowCount = remaining)
                )
            }
        }
    }
}
