package xdm.app.skins

import kotlinx.serialization.Serializable

/**
 * Root specification for a portable .xdmskin package manifest (skin.json).
 */
@Serializable
data class SkinManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "",
    val description: String = "",
    val minAppVersion: String = "1.0.0",
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

