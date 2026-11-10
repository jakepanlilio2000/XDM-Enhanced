package xdm.app.skins

import xdm.app.AppContext
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon

/**
 * Loads and caches upstream multi-resolution PNG icons (hdpi, xhdpi, xxhdpi)
 * with DPI-scaled antialiased rendering.
 */
object ClassicIconLoader {
    private val iconCache = ConcurrentHashMap<String, Icon>()

    fun loadIcon(name: String, targetSize: Int): Icon? {
        val key = "$name@$targetSize"
        val cached = iconCache[key]
        if (cached != null) return cached

        val hdpi = loadImage("/icons/hdpi/$name.png") ?: return null
        val xhdpi = loadImage("/icons/xhdpi/$name.png")
        val xxhdpi = loadImage("/icons/xxhdpi/$name.png")

        val multiResImage: Image = if (xhdpi != null && xxhdpi != null) {
            try {
                BaseMultiResolutionImage(hdpi, xhdpi, xxhdpi)
            } catch (_: Throwable) {
                hdpi
            }
        } else {
            hdpi
        }

        val icon = ScaledMultiResIcon(multiResImage, targetSize, targetSize)
        iconCache[key] = icon
        return icon
    }

    private fun loadImage(path: String): BufferedImage? = runCatching {
        AppContext::class.java.getResourceAsStream(path)?.use { ImageIO.read(it) }
    }.getOrNull()

    private class ScaledMultiResIcon(
        private val image: Image,
        private val width: Int,
        private val height: Int
    ) : Icon {
        override fun getIconWidth(): Int = width
        override fun getIconHeight(): Int = height

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2.drawImage(image, x, y, width, height, null)
            } finally {
                g2.dispose()
            }
        }
    }
}

