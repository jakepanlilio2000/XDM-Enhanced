package xdm.app.skins

import java.awt.Color
import javax.swing.Icon

/**
 * Definition of a visual skin preset for XDM Enhanced.
 * Defines geometry metrics, icon sets, category colors, default accents, and custom UIManager defaults.
 *
 * Theme and Style Resolution Precedence Hierarchy:
 * 1. Platform Accessibility Constraints (High Contrast, accessibility overrides, system antialiasing)
 * 2. Component State (Selected, Hovered, Pressed, Disabled, Focused)
 * 3. User Active Skin Tokens (Manifest colors, metrics, custom UIManager defaults, wallpaper)
 * 4. Selected Light/Dark Palette (FlatLaf theme mode, accent presets)
 * 5. Fallback Defaults (Hardcoded safe platform defaults)
 */
interface Skin {
    /** Unique persistent identifier (e.g. "enhanced", "classic_xdm"). */
    val id: String

    /** Human-readable display name. */
    val name: String

    /** Multi-line description of the skin's visual style. */
    val description: String

    /** Author or team responsible for the skin preset. */
    val author: String

    /** Semantic version string. */
    val version: String

    /** Whether this is a built-in, immutable skin. */
    val isBuiltIn: Boolean

    /** Optional legal, attribution, or upstream compatibility notice. */
    val attributionNotice: String?

    // Component geometry and layout metrics
    val componentArc: Int
    val buttonArc: Int
    val rowHeight: Int
    val sidebarWidth: Int

    // Icon set style
    val useClassicIcons: Boolean

    // Default accent colors for dark and light modes
    val defaultDarkAccent: Color
    val defaultLightAccent: Color

    /**
     * Category color override for the specified category ID and theme mode.
     * Returns null to use standard FlatLaf / CategoryStyle defaults.
     */
    fun categoryColor(categoryId: String, isDark: Boolean): Color?

    /**
     * Returns a skin-specific category icon if available, or null to fall back to RemixIcon glyphs.
     */
    fun getCategoryIcon(categoryId: String?, size: Int): Icon?

    /**
     * Returns a skin-specific toolbar button icon if available, or null to fall back to RemixIcon glyphs.
     */
    fun getToolbarIcon(actionKey: String, size: Int): Icon?

    /**
     * Applies skin-specific properties directly to UIManager when the theme is applied.
     */
    fun applyCustomDefaults(isDark: Boolean)

    /** Optional root directory where custom skin assets reside, or null for built-in skins. */
    val skinDir: java.io.File? get() = null

    /** Custom layout specification. */
    val layoutConfig: SkinLayoutConfig get() = SkinLayoutConfig()

    /**
     * Paints the skin's window wallpaper/background on [g] if defined.
     * Returns true if a wallpaper was painted, false otherwise.
     */
    fun paintWallpaper(g: java.awt.Graphics, width: Int, height: Int, isDark: Boolean): Boolean = false

    /**
     * Whether this skin defines an active wallpaper for the specified theme mode.
     */
    fun hasWallpaper(isDark: Boolean = com.formdev.flatlaf.FlatLaf.isLafDark()): Boolean = false

    /**
     * Returns region-specific declarative configuration if defined by this skin.
     */
    fun getRegionConfig(region: SkinRegion): SkinRegionConfig? = null

    /**
     * Paints a region-specific background wallpaper on [g] if defined.
     * Returns true if a region wallpaper was painted, false otherwise.
     */
    fun paintRegionWallpaper(g: java.awt.Graphics, width: Int, height: Int, region: SkinRegion, isDark: Boolean): Boolean = false

    /**
     * Whether this skin defines an active wallpaper for the specified region and theme mode.
     */
    fun hasRegionWallpaper(region: SkinRegion, isDark: Boolean = com.formdev.flatlaf.FlatLaf.isLafDark()): Boolean = false

    /**
     * Releases any cached images, fonts, or resources associated with this skin.
     */
    fun dispose() {}
}

