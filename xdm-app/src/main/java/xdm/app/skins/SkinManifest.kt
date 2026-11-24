package xdm.app.skins

import kotlinx.serialization.Serializable

/**
 * Root specification for a portable .xdmskin package manifest (skin.json).
 * Supports Schema Version 1 (basic styling), Version 2 (full-UI regional contract),
 * and Version 3 (custom window chrome, compact shade mode, frame bevels, and styled widgets).
 */
@Serializable
data class SkinManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String,
    val author: String = "",
    val description: String = "",
    val minAppVersion: String = "1.0.0-beta.1",
    val license: String = "MIT",
    val homepage: String? = null,
    val attributionNotice: String? = null,
    val geometry: SkinGeometry = SkinGeometry(),
    val accents: SkinAccents = SkinAccents(),
    val colors: SkinColorsConfig = SkinColorsConfig(),
    val wallpaper: SkinWallpaperConfig? = null,
    val font: SkinFontConfig? = null,
    val layout: SkinLayoutConfig = SkinLayoutConfig(),
    val icons: SkinIconsConfig = SkinIconsConfig(),
    val regions: Map<String, SkinRegionConfig> = emptyMap(),
    val window: SkinWindowConfig? = null,
    val widgets: SkinWidgetsConfig? = null,
    val uiDefaults: Map<String, String> = emptyMap()
)

@Serializable
data class SkinGeometry(
    val componentArc: Int = 8,
    val buttonArc: Int = 8,
    val rowHeight: Int = 54,
    val sidebarWidth: Int = 210
)

@Serializable
data class SkinAccents(
    val dark: String = "#3B82F6",
    val light: String = "#2563EB"
)

@Serializable
data class SkinColorsConfig(
    val dark: SkinThemeColors = SkinThemeColors(),
    val light: SkinThemeColors = SkinThemeColors()
)

@Serializable
data class SkinThemeColors(
    val windowBackground: String? = null,
    val panelBackground: String? = null,
    val sidebarBackground: String? = null,
    val sidebarBorder: String? = null,
    val sidebarForeground: String? = null,
    val sidebarSelectedForeground: String? = null,
    val sidebarSelectionBackground: String? = null,
    val sidebarHoverBackground: String? = null,
    val listBackground: String? = null,
    val listTitleForeground: String? = null,
    val listSelectionBackground: String? = null,
    val listHoverBackground: String? = null,
    val categories: Map<String, String> = emptyMap()
)

@Serializable
data class SkinWallpaperConfig(
    val dark: String? = null,
    val light: String? = null,
    val mode: String = "fill", // "fill", "fit", "tile", "center"
    val opacity: Float = 0.2f
)

@Serializable
data class SkinFontConfig(
    val file: String? = null,
    val path: String? = null,
    val name: String? = null,
    val family: String? = null,
    val size: Int? = null
) {
    val resolvedFile: String? get() = file ?: path
    val resolvedName: String? get() = name ?: family
}

@Serializable
data class SkinLayoutConfig(
    val toolbarPosition: String = "top", // "top", "bottom", "hidden"
    val sidebarPosition: String = "left", // "left", "right", "hidden"
    val showStatusBar: Boolean = true,
    val showSearchBar: Boolean = true,
    val showMenuBar: Boolean = true,
    val chromeMode: String = "enhanced" // "enhanced", "upstream", "custom"
)

@Serializable
data class SkinWindowConfig(
    val customTitleBar: Boolean = false,
    val hideNativeTitleBar: Boolean = false,
    val titleBarHeight: Int = 34,
    val titleText: String? = null,
    val titleAlignment: String = "left", // "left", "center"
    val showAppIcon: Boolean = true,
    val showWindowShadeButton: Boolean = true,
    val windowShadeHeight: Int = 64,
    val showStatsTicker: Boolean = false,
    val controlButtonsStyle: String = "mac", // "mac", "beveled", "anime", "steampunk", "minimal"
    val titleBarBackgroundDark: String? = null,
    val titleBarBackgroundLight: String? = null,
    val titleBarGradientStartDark: String? = null,
    val titleBarGradientEndDark: String? = null,
    val titleBarGradientStartLight: String? = null,
    val titleBarGradientEndLight: String? = null,
    val titleBarForegroundDark: String? = null,
    val titleBarForegroundLight: String? = null,
    val titleBarTexture: SkinWallpaperConfig? = null,
    val frameBorderWidth: Int = 0,
    val frameCornerArc: Int = 0,
    val frameBorderDark: String? = null,
    val frameBorderLight: String? = null,
    val frameHighlightDark: String? = null,
    val frameHighlightLight: String? = null,
    val frameShadowDark: String? = null,
    val frameShadowLight: String? = null,
    val controlIcons: SkinWindowControlIcons = SkinWindowControlIcons()
)

@Serializable
data class SkinWindowControlIcons(
    val minimize: String? = null,
    val maximize: String? = null,
    val restore: String? = null,
    val shade: String? = null,
    val close: String? = null
)

@Serializable
data class SkinWidgetsConfig(
    val progressBarStyle: String = "flat", // "flat", "segmented_led", "gradient_glow", "brass_gauge", "striped_candy"
    val progressTrackDark: String? = null,
    val progressTrackLight: String? = null,
    val progressFillStartDark: String? = null,
    val progressFillEndDark: String? = null,
    val progressFillStartLight: String? = null,
    val progressFillEndLight: String? = null,
    val progressSegments: Int = 18,
    val buttonBevelStyle: String = "flat", // "flat", "beveled_3d", "pill_glow", "riveted_brass", "neon_outline"
    val buttonSurfaceDark: String? = null,
    val buttonSurfaceLight: String? = null,
    val buttonHighlightDark: String? = null,
    val buttonShadowDark: String? = null,
    val buttonHighlightLight: String? = null,
    val buttonShadowLight: String? = null,
    val showScanlines: Boolean = false,
    val scanlineOpacity: Float = 0.08f
)

@Serializable
data class SkinIconsConfig(
    val toolbar: Map<String, String> = emptyMap(),
    val categories: Map<String, String> = emptyMap()
)

/**
 * Region-specific declarative styling contract for Schema Version 2.
 * Allows targeting specific application regions (main frame, toolbar, sidebar,
 * tables/cards, modals, settings, about dialog, and popups).
 */
@Serializable
data class SkinRegionConfig(
    val background: String? = null,
    val foreground: String? = null,
    val mutedForeground: String? = null,
    val border: String? = null,
    val borderWidth: Int? = null,
    val cornerArc: Int? = null,
    val buttonArc: Int? = null,
    val cardArc: Int? = null,
    val progressBarArc: Int? = null,
    val padding: SkinPaddingConfig? = null,
    val wallpaper: SkinWallpaperConfig? = null,
    val customIcon: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    val selectionBackground: String? = null,
    val selectionForeground: String? = null,
    val hoverBackground: String? = null,
    val layoutPosition: String? = null,
    val extraProperties: Map<String, String> = emptyMap()
)

@Serializable
data class SkinPaddingConfig(
    val top: Int = 0,
    val left: Int = 0,
    val bottom: Int = 0,
    val right: Int = 0
)
