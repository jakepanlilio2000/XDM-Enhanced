package xdm.app.skins

import com.formdev.flatlaf.FlatLaf
import kotlinx.serialization.json.Json
import xdm.app.ui.components.ThemeManager
import xdm.core.util.Logger
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource

/**
 * Runtime skin implementation backed by a user-installed .xdmskin package.
 */
class CustomSkin(
    override val skinDir: File,
    val manifest: SkinManifest
) : Skin {

    override val id: String = manifest.id
    override val name: String = manifest.name
    override val description: String = manifest.description
    override val author: String = manifest.author
    override val version: String = manifest.version
    override val isBuiltIn: Boolean = false
    override val attributionNotice: String? = manifest.attributionNotice

    override val componentArc: Int = manifest.geometry.componentArc
    override val buttonArc: Int = manifest.geometry.buttonArc
    override val rowHeight: Int = manifest.geometry.rowHeight
    override val sidebarWidth: Int = manifest.geometry.sidebarWidth
    override val useClassicIcons: Boolean = manifest.icons.toolbar.isNotEmpty()

    override val layoutConfig: SkinLayoutConfig = manifest.layout

    override val defaultDarkAccent: Color =
        ThemeManager.parseHexColor(manifest.accents.dark) ?: Color(0x3B, 0x82, 0xF6)
    override val defaultLightAccent: Color =
        ThemeManager.parseHexColor(manifest.accents.light) ?: Color(0x25, 0x63, 0xEB)

    private val iconCache = ConcurrentHashMap<String, Icon>()
    private var cachedCustomFont: Font? = null

    // Wallpaper cache
    private var lastDarkWallpaperRaw: BufferedImage? = null
    private var lastLightWallpaperRaw: BufferedImage? = null
    private var cachedScaledWallpaper: BufferedImage? = null
    private var cachedWallpaperWidth: Int = -1
    private var cachedWallpaperHeight: Int = -1
    private var cachedWallpaperDark: Boolean = false

    init {
        loadCustomFont()
    }

    private fun loadCustomFont() {
        val fontPath = manifest.font?.resolvedFile ?: return
        val fontFile = File(skinDir, fontPath)
        if (fontFile.exists() && fontFile.isFile) {
            runCatching {
                val f = Font.createFont(Font.TRUETYPE_FONT, fontFile)
                cachedCustomFont = f
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f)
                Logger.info("Loaded custom skin font: '${f.name}' from $fontPath")
            }.onFailure {
                Logger.error("Failed to load custom skin font: ${it.message}", it)
            }
        }
    }

    override fun categoryColor(categoryId: String, isDark: Boolean): Color? {
        val colors = if (isDark) manifest.colors.dark.categories else manifest.colors.light.categories
        val hex = colors[categoryId] ?: return null
        return ThemeManager.parseHexColor(hex)
    }

    override fun getCategoryIcon(categoryId: String?, size: Int): Icon? {
        if (categoryId.isNullOrBlank()) return null
        val relPath = manifest.icons.categories[categoryId] ?: return null
        return loadCachedIcon(relPath, size)
    }

    override fun getToolbarIcon(actionKey: String, size: Int): Icon? {
        val relPath = manifest.icons.toolbar[actionKey] ?: return null
        return loadCachedIcon(relPath, size)
    }

    private fun loadCachedIcon(relPath: String, targetSize: Int): Icon? {
        val key = "$relPath@$targetSize"
        val cached = iconCache[key]
        if (cached != null) return cached

        val file = File(skinDir, relPath)
        if (!file.exists() || !file.isFile) return null

        val img = runCatching { ImageIO.read(file) }.getOrNull() ?: return null
        val icon = ScaledImageIcon(img, targetSize, targetSize)
        iconCache[key] = icon
        return icon
    }

    override fun paintWallpaper(g: Graphics, width: Int, height: Int, isDark: Boolean): Boolean {
        val wpConfig = manifest.wallpaper ?: return false
        val relPath = if (isDark) wpConfig.dark else wpConfig.light
        if (relPath.isNullOrBlank()) return false

        val rawImage = if (isDark) {
            if (lastDarkWallpaperRaw == null) {
                val file = File(skinDir, relPath)
                if (file.exists()) lastDarkWallpaperRaw = runCatching { ImageIO.read(file) }.getOrNull()
            }
            lastDarkWallpaperRaw
        } else {
            if (lastLightWallpaperRaw == null) {
                val file = File(skinDir, relPath)
                if (file.exists()) lastLightWallpaperRaw = runCatching { ImageIO.read(file) }.getOrNull()
            }
            lastLightWallpaperRaw
        } ?: return false

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val opacity = wpConfig.opacity.coerceIn(0.01f, 1.0f)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)

            when (wpConfig.mode.lowercase()) {
                "fit" -> {
                    val scale = minOf(width.toFloat() / rawImage.width, height.toFloat() / rawImage.height)
                    val sw = (rawImage.width * scale).toInt()
                    val sh = (rawImage.height * scale).toInt()
                    val x = (width - sw) / 2
                    val y = (height - sh) / 2
                    g2.drawImage(rawImage, x, y, sw, sh, null)
                }
                "center" -> {
                    val x = (width - rawImage.width) / 2
                    val y = (height - rawImage.height) / 2
                    g2.drawImage(rawImage, x, y, null)
                }
                "tile" -> {
                    val iw = rawImage.width
                    val ih = rawImage.height
                    var y = 0
                    while (y < height) {
                        var x = 0
                        while (x < width) {
                            g2.drawImage(rawImage, x, y, null)
                            x += iw
                        }
                        y += ih
                    }
                }
                else -> { // "fill"
                    if (cachedScaledWallpaper == null || cachedWallpaperWidth != width || cachedWallpaperHeight != height || cachedWallpaperDark != isDark) {
                        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                        val sg = scaled.createGraphics()
                        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        sg.drawImage(rawImage, 0, 0, width, height, null)
                        sg.dispose()
                        cachedScaledWallpaper = scaled
                        cachedWallpaperWidth = width
                        cachedWallpaperHeight = height
                        cachedWallpaperDark = isDark
                    }
                    g2.drawImage(cachedScaledWallpaper, 0, 0, null)
                }
            }
            return true
        } finally {
            g2.dispose()
        }
    }

    override fun applyCustomDefaults(isDark: Boolean) {
        UIManager.put("Component.arc", componentArc)
        UIManager.put("Button.arc", buttonArc)
        UIManager.put("TextComponent.arc", componentArc)
        UIManager.put("ProgressBar.arc", componentArc)
        UIManager.put("CheckBox.arc", componentArc)

        UIManager.put("XDM.skin.id", id)
        UIManager.put("XDM.skin.rowHeight", rowHeight)
        UIManager.put("XDM.skin.sidebarWidth", sidebarWidth)
        UIManager.put("XDM.skin.useClassicIcons", useClassicIcons)

        val colors = if (isDark) manifest.colors.dark else manifest.colors.light

        colors.windowBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.window.background", it)
        }
        colors.panelBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("Panel.background", it)
        }
        colors.sidebarBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.sidebar.background", it)
        }
        colors.sidebarBorder?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.sidebar.borderColor", it)
        }
        colors.sidebarForeground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.sidebar.foreground", it)
        }
        colors.sidebarSelectedForeground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.sidebar.selectedForeground", it)
        }
        colors.sidebarSelectionBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.sidebar.selectionBackground", it)
        }
        colors.sidebarHoverBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.sidebar.hoverBackground", it)
        }
        colors.listBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.list.background", it)
            UIManager.put("Table.background", it)
        }
        colors.listTitleForeground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.list.titleForeground", it)
        }
        colors.listSelectionBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.list.selectionBackground", it)
        }
        colors.listHoverBackground?.let { ThemeManager.parseHexColor(it) }?.let {
            UIManager.put("XDM.list.hoverBackground", it)
        }

        colors.categories.forEach { (cat, hex) ->
            ThemeManager.parseHexColor(hex)?.let { UIManager.put("XDM.category.$cat", it) }
        }

        // Apply raw uiDefaults from manifest
        manifest.uiDefaults.forEach { (k, v) ->
            when (v.lowercase()) {
                "true" -> UIManager.put(k, true)
                "false" -> UIManager.put(k, false)
                else -> {
                    val intVal = v.toIntOrNull()
                    if (intVal != null) {
                        UIManager.put(k, intVal)
                    } else {
                        val col = ThemeManager.parseHexColor(v)
                        UIManager.put(k, col ?: v)
                    }
                }
            }
        }

        // Custom font override
        cachedCustomFont?.let { font ->
            UIManager.put("defaultFont", FontUIResource(font.deriveFont(12f)))
        }
    }

    override fun dispose() {
        iconCache.clear()
        lastDarkWallpaperRaw = null
        lastLightWallpaperRaw = null
        cachedScaledWallpaper = null
    }

    private class ScaledImageIcon(
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

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun loadFromDir(skinDir: File): CustomSkin? {
            val manifestFile = File(skinDir, "skin.json")
            if (!manifestFile.exists()) return null
            return try {
                val manifest = json.decodeFromString<SkinManifest>(manifestFile.readText())
                val validation = SkinPackageValidator.validateManifest(manifest, skinRoot = skinDir)
                if (!validation.isValid) {
                    Logger.error("Skin manifest in ${skinDir.name} failed validation: ${validation.errors.joinToString("; ")}")
                    return null
                }
                CustomSkin(skinDir, manifest)
            } catch (e: Throwable) {
                Logger.error("Failed to load skin from ${skinDir.absolutePath}: ${e.message}", e)
                null
            }
        }
    }
}

