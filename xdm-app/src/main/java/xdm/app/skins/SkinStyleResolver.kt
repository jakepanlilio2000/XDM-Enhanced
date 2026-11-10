package xdm.app.skins

import com.formdev.flatlaf.FlatLaf
import xdm.app.ui.components.ThemeManager
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import javax.swing.UIManager

/**
 * Enumeration of all application-controlled customizable UI regions.
 *
 * Title bars, native file dialogs, and OS-level security dialogs are owned by the host
 * operating system and window manager (Windows DWM / macOS WindowServer / Linux Wayland/X11)
 * and cannot be arbitrarily skinned.
 */
enum class SkinRegion(val slotId: String, val displayName: String) {
    MAIN_FRAME("main_frame", "Main Window Interior"),
    TOOLBAR("toolbar", "Toolbar & Actions"),
    SIDEBAR("sidebar", "Category Sidebar"),
    DOWNLOAD_LIST("download_list", "Download List & Cards"),
    DOWNLOAD_MODAL("download_modal", "Standard Download Dialog"),
    VIDEO_DOWNLOAD_MODAL("video_download_modal", "Streaming Video Download Dialog"),
    PROGRESS_MODAL("progress_modal", "Download Progress & Completion Window"),
    TORRENT_MODAL("torrent_modal", "BitTorrent Task & Metadata Dialog"),
    SETTINGS("settings", "Settings & Preferences Window"),
    PLUGIN_MANAGER("plugin_manager", "Plugin Management & Settings View"),
    ABOUT("about", "About & Diagnostics Dialog"),
    DIALOGS("dialogs", "Confirmation & Notification Dialogs"),
    TOOLTIPS_POPUPS("tooltips_popups", "Tooltips & Context Menus");

    companion object {
        fun fromSlotId(id: String): SkinRegion? =
            values().firstOrNull { it.slotId.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
    }
}

/**
 * Single authoritative style resolver implementing deterministic theme precedence:
 *
 * 1. Platform Accessibility Constraints (High Contrast, accessibility overrides)
 * 2. Component State (Selected, Hovered, Focused, Disabled)
 * 3. User Active Skin Region Tokens (Region-specific manifest overrides in Schema v2)
 * 4. User Active Skin Global Tokens (Global manifest colors, geometry, wallpapers)
 * 5. Active Theme Mode Palette (FlatLaf Dark / Light, accent colors)
 * 6. Hardcoded Safe Platform Defaults
 */
object SkinStyleResolver {

    /**
     * Checks whether high-contrast accessibility mode is active in the environment.
     */
    fun isHighContrastActive(): Boolean {
        val hc = UIManager.getBoolean("FlatLaf.accessibility.highContrast")
        if (hc) return true
        val sysProp = System.getProperty("sun.awt.enableHighContrast")
        return sysProp.equals("true", ignoreCase = true)
    }

    /**
     * Resolves the effective [SkinRegionConfig] for the requested [region] on the active skin.
     */
    fun resolveRegionConfig(region: SkinRegion, skin: Skin = SkinManager.currentSkin): SkinRegionConfig? =
        skin.getRegionConfig(region)

    /**
     * Resolves background color for [region] following the precedence hierarchy.
     */
    fun resolveBackground(region: SkinRegion, isDark: Boolean = FlatLaf.isLafDark(), skin: Skin = SkinManager.currentSkin): Color? {
        if (isHighContrastActive()) return if (isDark) Color.BLACK else Color.WHITE
        val cfg = skin.getRegionConfig(region)
        cfg?.background?.let { ThemeManager.parseHexColor(it) }?.let { return it }

        // Region-to-global fallback mappings
        return when (region) {
            SkinRegion.MAIN_FRAME -> UIManager.getColor("XDM.window.background")
            SkinRegion.SIDEBAR -> UIManager.getColor("XDM.sidebar.background")
            SkinRegion.DOWNLOAD_LIST -> UIManager.getColor("XDM.list.background") ?: UIManager.getColor("Table.background")
            SkinRegion.TOOLBAR -> UIManager.getColor("Panel.background")
            SkinRegion.DIALOGS -> UIManager.getColor("OptionPane.background") ?: UIManager.getColor("Panel.background")
            SkinRegion.TOOLTIPS_POPUPS -> UIManager.getColor("ToolTip.background") ?: UIManager.getColor("PopupMenu.background")
            else -> UIManager.getColor("Panel.background")
        }
    }

    /**
     * Resolves primary text foreground color for [region].
     */
    fun resolveForeground(region: SkinRegion, isDark: Boolean = FlatLaf.isLafDark(), skin: Skin = SkinManager.currentSkin): Color? {
        if (isHighContrastActive()) return if (isDark) Color.WHITE else Color.BLACK
        val cfg = skin.getRegionConfig(region)
        cfg?.foreground?.let { ThemeManager.parseHexColor(it) }?.let { return it }

        return when (region) {
            SkinRegion.SIDEBAR -> UIManager.getColor("XDM.sidebar.foreground") ?: UIManager.getColor("Label.foreground")
            SkinRegion.DOWNLOAD_LIST -> UIManager.getColor("XDM.list.titleForeground") ?: UIManager.getColor("Label.foreground")
            SkinRegion.TOOLTIPS_POPUPS -> UIManager.getColor("ToolTip.foreground")
            else -> UIManager.getColor("Label.foreground")
        }
    }

    /**
     * Resolves muted/secondary text foreground color for [region].
     */
    fun resolveMutedForeground(region: SkinRegion, isDark: Boolean = FlatLaf.isLafDark(), skin: Skin = SkinManager.currentSkin): Color {
        if (isHighContrastActive()) return if (isDark) Color(0xCC, 0xCC, 0xCC) else Color(0x33, 0x33, 0x33)
        val cfg = skin.getRegionConfig(region)
        cfg?.mutedForeground?.let { ThemeManager.parseHexColor(it) }?.let { return it }
        return UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
    }

    /**
     * Resolves border color for [region].
     */
    fun resolveBorderColor(region: SkinRegion, isDark: Boolean = FlatLaf.isLafDark(), skin: Skin = SkinManager.currentSkin): Color? {
        if (isHighContrastActive()) return if (isDark) Color.WHITE else Color.BLACK
        val cfg = skin.getRegionConfig(region)
        cfg?.border?.let { ThemeManager.parseHexColor(it) }?.let { return it }

        return when (region) {
            SkinRegion.SIDEBAR -> UIManager.getColor("XDM.sidebar.borderColor")
            SkinRegion.DIALOGS -> UIManager.getColor("PopupMenu.borderColor")
            else -> UIManager.getColor("Component.borderColor")
        }
    }

    /**
     * Resolves border line width for [region].
     */
    fun resolveBorderWidth(region: SkinRegion, defaultWidth: Int = 1, skin: Skin = SkinManager.currentSkin): Int {
        val cfg = skin.getRegionConfig(region)
        return (cfg?.borderWidth ?: defaultWidth).coerceIn(0, 16)
    }

    /**
     * Resolves corner rounding arc for containers in [region].
     */
    fun resolveCornerArc(region: SkinRegion, defaultArc: Int = SkinManager.currentSkin.componentArc, skin: Skin = SkinManager.currentSkin): Int {
        if (isHighContrastActive()) return 0
        val cfg = skin.getRegionConfig(region)
        return (cfg?.cornerArc ?: defaultArc).coerceIn(0, 48)
    }

    /**
     * Resolves button corner rounding arc in [region].
     */
    fun resolveButtonArc(region: SkinRegion, defaultArc: Int = SkinManager.currentSkin.buttonArc, skin: Skin = SkinManager.currentSkin): Int {
        if (isHighContrastActive()) return 0
        val cfg = skin.getRegionConfig(region)
        return (cfg?.buttonArc ?: defaultArc).coerceIn(0, 48)
    }

    /**
     * Resolves download card corner rounding arc in [region].
     */
    fun resolveCardArc(region: SkinRegion, defaultArc: Int = SkinManager.currentSkin.componentArc, skin: Skin = SkinManager.currentSkin): Int {
        if (isHighContrastActive()) return 0
        val cfg = skin.getRegionConfig(region)
        return (cfg?.cardArc ?: defaultArc).coerceIn(0, 48)
    }

    /**
     * Resolves progress bar corner rounding arc in [region].
     */
    fun resolveProgressBarArc(region: SkinRegion, defaultArc: Int = SkinManager.currentSkin.componentArc, skin: Skin = SkinManager.currentSkin): Int {
        if (isHighContrastActive()) return 0
        val cfg = skin.getRegionConfig(region)
        return (cfg?.progressBarArc ?: defaultArc).coerceIn(0, 48)
    }

    /**
     * Resolves padding insets for [region].
     */
    fun resolvePadding(region: SkinRegion, defaultInsets: Insets, skin: Skin = SkinManager.currentSkin): Insets {
        val cfg = skin.getRegionConfig(region)
        val p = cfg?.padding ?: return defaultInsets
        return Insets(p.top.coerceIn(0, 64), p.left.coerceIn(0, 64), p.bottom.coerceIn(0, 64), p.right.coerceIn(0, 64))
    }

    /**
     * Resolves custom dimension constraints (e.g. "height", "width") for [region].
     */
    fun resolveDimension(region: SkinRegion, dimensionKey: String, defaultValue: Int, skin: Skin = SkinManager.currentSkin): Int {
        val cfg = skin.getRegionConfig(region)
        return when (dimensionKey.lowercase()) {
            "height" -> cfg?.height ?: defaultValue
            "width" -> cfg?.width ?: defaultValue
            else -> defaultValue
        }
    }

    /**
     * Resolves region wallpaper specification, or falls back to global skin wallpaper.
     */
    fun resolveWallpaper(region: SkinRegion, isDark: Boolean = FlatLaf.isLafDark(), skin: Skin = SkinManager.currentSkin): SkinWallpaperConfig? {
        val cfg = skin.getRegionConfig(region)
        if (cfg?.wallpaper != null) {
            val wp = cfg.wallpaper
            val path = if (isDark) wp.dark else wp.light
            if (!path.isNullOrBlank()) return wp
        }
        return (skin as? CustomSkin)?.manifest?.wallpaper
    }

    /**
     * Paints background and wallpaper for [region] on graphics context [g].
     * Returns true if any region background or wallpaper was painted.
     */
    fun paintRegionBackground(g: Graphics, width: Int, height: Int, region: SkinRegion, isDark: Boolean = FlatLaf.isLafDark(), skin: Skin = SkinManager.currentSkin): Boolean {
        if (width <= 0 || height <= 0) return false

        var painted = false
        // 1. Paint solid region background if defined
        val bg = resolveBackground(region, isDark, skin)
        if (bg != null && bg.alpha > 0) {
            val oldColor = g.color
            g.color = bg
            g.fillRect(0, 0, width, height)
            g.color = oldColor
            painted = true
        }

        // 2. Paint region-specific wallpaper or fall back to global wallpaper
        val wpPainted = if (skin.hasRegionWallpaper(region, isDark)) {
            skin.paintRegionWallpaper(g, width, height, region, isDark)
        } else {
            skin.paintWallpaper(g, width, height, isDark)
        }

        return painted || wpPainted
    }
}

