package xdm.app.skins

import kotlinx.serialization.Serializable

/**
 * Root specification for a portable .xdmskin package manifest (skin.json).
 * Supports Schema Version 1 (legacy single-view styling) and Version 2 (Full-UI regional contract).
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
    val showSearchBar: Boolean = true
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
