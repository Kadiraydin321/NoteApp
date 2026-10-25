package com.example.noteapp.desktop.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object AppIconRenderer {

    /**
     * Android Not Defterim resmi adaptive launcher ikonunu birebir çizer.
     * Viewport: 108x108 baz alınarak istenen çözünürlükte (varsayılan 256x256) render edilir.
     */
    fun createIconImage(size: Int = 256): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        val scale = size.toDouble() / 108.0
        g2.scale(scale, scale)

        // 1. ARKA PLAN - YUVARLATILMIŞ KARE & İNDİGO-MOR GRADIENT
        val bgShape = RoundRectangle2D.Double(0.0, 0.0, 108.0, 108.0, 32.0, 32.0)
        g2.clip(bgShape)

        val gradient = LinearGradientPaint(
            Point2D.Double(12.0, 8.0),
            Point2D.Double(96.0, 100.0),
            floatArrayOf(0.0f, 0.45f, 1.0f),
            arrayOf(
                Color(0x43, 0x38, 0xCA), // #4338CA
                Color(0x4F, 0x46, 0xE5), // #4F46E5
                Color(0x7C, 0x3A, 0xED)  // #7C3AED
            )
        )
        g2.paint = gradient
        g2.fill(bgShape)

        // Üst köşe yumuşak ışık yansıması
        val cornerLight = Path2D.Double().apply {
            moveTo(0.0, 0.0)
            lineTo(76.0, 0.0)
            lineTo(0.0, 76.0)
            closePath()
        }
        val lightGradient = LinearGradientPaint(
            Point2D.Double(0.0, 0.0),
            Point2D.Double(45.0, 45.0),
            floatArrayOf(0.0f, 1.0f),
            arrayOf(Color(255, 255, 255, 51), Color(255, 255, 255, 0))
        )
        g2.paint = lightGradient
        g2.fill(cornerLight)

        // Diyagonal cam ışık desenleri
        g2.color = Color(255, 255, 255, 18)
        val wave1 = Path2D.Double().apply {
            moveTo(-5.0, 45.0)
            lineTo(65.0, -5.0)
            lineTo(85.0, -5.0)
            lineTo(-5.0, 85.0)
            closePath()
        }
        g2.fill(wave1)

        g2.color = Color(255, 255, 255, 13)
        val wave2 = Path2D.Double().apply {
            moveTo(25.0, 113.0)
            lineTo(113.0, 25.0)
            lineTo(113.0, 50.0)
            lineTo(50.0, 113.0)
            closePath()
        }
        g2.fill(wave2)

        // 2. ÖN PLAN - NOT KAĞIDI, GÖREVLER VE MERCAN KALEM

        // Kağıt gölgesi (#261E72)
        g2.color = Color(0x26, 0x1E, 0x72)
        val paperShadow = Path2D.Double().apply {
            moveTo(31.0, 20.0)
            lineTo(65.0, 20.0)
            lineTo(81.0, 36.0)
            lineTo(81.0, 78.0)
            curveTo(81.0, 82.4, 77.4, 86.0, 73.0, 86.0)
            lineTo(31.0, 86.0)
            curveTo(26.6, 86.0, 23.0, 82.4, 23.0, 78.0)
            lineTo(23.0, 28.0)
            curveTo(26.6, 23.6, 26.6, 20.0, 31.0, 20.0)
            closePath()
        }
        g2.fill(paperShadow)

        // Beyaz not kağıdı (#FFFFFF)
        g2.color = Color.WHITE
        val paper = Path2D.Double().apply {
            moveTo(31.0, 16.0)
            lineTo(64.0, 16.0)
            lineTo(80.0, 32.0)
            lineTo(80.0, 76.0)
            curveTo(80.0, 80.4, 76.4, 84.0, 72.0, 84.0)
            lineTo(31.0, 84.0)
            curveTo(26.6, 84.0, 23.0, 80.4, 23.0, 76.0)
            lineTo(23.0, 24.0)
            curveTo(23.0, 19.6, 26.6, 16.0, 31.0, 16.0)
            closePath()
        }
        g2.fill(paper)

        // Katlanmış üst-sağ köşe (#DCE2FF)
        g2.color = Color(0xDC, 0xE2, 0xFF)
        val foldedCorner = Path2D.Double().apply {
            moveTo(64.0, 16.0)
            lineTo(64.0, 28.0)
            curveTo(64.0, 30.2, 65.8, 32.0, 68.0, 32.0)
            lineTo(80.0, 32.0)
            closePath()
        }
        g2.fill(foldedCorner)

        // Not başlık çizgisi (#4F46E5)
        g2.color = Color(0x4F, 0x46, 0xE5)
        val titleLine = Path2D.Double().apply {
            moveTo(33.0, 31.0)
            lineTo(60.0, 31.0)
            curveTo(61.7, 31.0, 63.0, 32.3, 63.0, 34.0)
            curveTo(63.0, 35.7, 61.7, 37.0, 60.0, 37.0)
            lineTo(33.0, 37.0)
            curveTo(31.3, 37.0, 30.0, 35.7, 30.0, 34.0)
            curveTo(30.0, 32.3, 31.3, 31.0, 33.0, 31.0)
            closePath()
        }
        g2.fill(titleLine)

        // 1. Görev Çemberi (#14B8A6 - Teal)
        g2.color = Color(0x14, 0xB8, 0xA6)
        val checkCircle1 = Path2D.Double().apply {
            moveTo(36.0, 45.0)
            curveTo(39.9, 45.0, 43.0, 48.1, 43.0, 52.0)
            curveTo(43.0, 55.9, 39.9, 59.0, 36.0, 59.0)
            curveTo(32.1, 59.0, 29.0, 55.9, 29.0, 52.0)
            curveTo(29.0, 48.1, 32.1, 45.0, 36.0, 45.0)
            closePath()
        }
        g2.fill(checkCircle1)

        // 1. Görev Beyaz Tik İmi
        g2.color = Color.WHITE
        val checkMark1 = Path2D.Double().apply {
            moveTo(32.4, 51.7)
            lineTo(34.6, 49.7)
            lineTo(36.1, 51.3)
            lineTo(39.5, 47.6)
            lineTo(41.6, 49.5)
            lineTo(36.0, 55.6)
            closePath()
        }
        g2.fill(checkMark1)

        // 1. Görev Metin Çizgisi (#A5B4FC)
        g2.color = Color(0xA5, 0xB4, 0xFC)
        val taskLine1 = Path2D.Double().apply {
            moveTo(49.0, 48.0)
            lineTo(68.0, 48.0)
            curveTo(69.7, 48.0, 71.0, 49.3, 71.0, 51.0)
            curveTo(71.0, 52.7, 69.7, 54.0, 68.0, 54.0)
            lineTo(49.0, 54.0)
            curveTo(47.3, 54.0, 46.0, 52.7, 46.0, 51.0)
            curveTo(46.0, 49.3, 47.3, 48.0, 49.0, 48.0)
            closePath()
        }
        g2.fill(taskLine1)

        // 2. Görev Çemberi (#14B8A6 - Teal)
        g2.color = Color(0x14, 0xB8, 0xA6)
        val checkCircle2 = Path2D.Double().apply {
            moveTo(36.0, 62.0)
            curveTo(39.9, 62.0, 43.0, 65.1, 43.0, 69.0)
            curveTo(43.0, 72.9, 39.9, 76.0, 36.0, 76.0)
            curveTo(32.1, 76.0, 29.0, 72.9, 29.0, 69.0)
            curveTo(29.0, 65.1, 32.1, 62.0, 36.0, 62.0)
            closePath()
        }
        g2.fill(checkCircle2)

        // 2. Görev Beyaz Tik İmi
        g2.color = Color.WHITE
        val checkMark2 = Path2D.Double().apply {
            moveTo(32.4, 68.7)
            lineTo(34.6, 66.7)
            lineTo(36.1, 68.3)
            lineTo(39.5, 64.6)
            lineTo(41.6, 66.5)
            lineTo(36.0, 72.6)
            closePath()
        }
        g2.fill(checkMark2)

        // 2. Görev Metin Çizgisi (#A5B4FC)
        g2.color = Color(0xA5, 0xB4, 0xFC)
        val taskLine2 = Path2D.Double().apply {
            moveTo(49.0, 65.0)
            lineTo(66.0, 65.0)
            curveTo(67.7, 65.0, 69.0, 66.3, 69.0, 68.0)
            curveTo(69.0, 69.7, 67.7, 71.0, 66.0, 71.0)
            lineTo(49.0, 71.0)
            curveTo(47.3, 71.0, 46.0, 69.7, 46.0, 68.0)
            curveTo(46.0, 66.3, 47.3, 65.0, 49.0, 65.0)
            closePath()
        }
        g2.fill(taskLine2)

        // Mercan Kalem Gövdesi (#F97360)
        g2.color = Color(0xF9, 0x73, 0x60)
        val pencilBody = Path2D.Double().apply {
            moveTo(68.0, 77.0)
            lineTo(81.0, 62.0)
            curveTo(82.7, 60.0, 85.6, 59.8, 87.5, 61.6)
            curveTo(89.4, 63.4, 89.5, 66.3, 87.7, 68.2)
            lineTo(74.8, 82.2)
            lineTo(67.0, 84.0)
            closePath()
        }
        g2.fill(pencilBody)

        // Kalem Ucu (#312E81)
        g2.color = Color(0x31, 0x2E, 0x81)
        val pencilTip = Path2D.Double().apply {
            moveTo(67.0, 84.0)
            lineTo(69.0, 76.0)
            lineTo(74.8, 82.2)
            closePath()
        }
        g2.fill(pencilTip)

        g2.dispose()
        return img
    }

    fun createIconBitmap(size: Int = 256): ImageBitmap {
        return createIconImage(size).toComposeImageBitmap()
    }

    /**
     * Masaüstü kaynakları dizinine PNG ikon dosyası yazar.
     */
    fun saveIconToFile(destFile: File, size: Int = 256) {
        destFile.parentFile?.mkdirs()
        val img = createIconImage(size)
        ImageIO.write(img, "PNG", destFile)
    }
}
