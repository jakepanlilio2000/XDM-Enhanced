package xdm.app.ui.components

import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager
import xdm.app.skins.SkinManager
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.px
import xdm.core.downloaders.DownloadType

/**
 * Shared palette and file-type glyphs. Vibrant, mid-luminance hues
 * (Tailwind 500-ish) that keep good contrast on both light and dark themes.
 * Used by the sidebar filter list, dialogs, and the main download-list rows
 * so a given category looks consistent everywhere.
 */
object CategoryStyle {
    val INDIGO = Color(0x63, 0x66, 0xF1)
    val EMERALD = Color(0x10, 0xB9, 0x81)
    val AMBER = Color(0xF5, 0x9E, 0x0B)
    val VIOLET = Color(0x8B, 0x5C, 0xF6)
    val SKY = Color(0x0E, 0xA5, 0xE9)
    val ORANGE = Color(0xF9, 0x73, 0x16)
    val PINK = Color(0xEC, 0x48, 0x99)
    val RED = Color(0xEF, 0x44, 0x44)
    val TEAL = Color(0x14, 0xB8, 0xA6)

    // Semantic colors for toolbar / row action icons.
    val ACCENT = INDIGO            // primary action (new download)
    val SUCCESS = EMERALD          // resume / play
    val WARN = AMBER               // pause
    val DANGER = RED               // delete

    /**
     * Theme-aware neutral icon color. Uses the look-and-feel's full-strength
     * label foreground so icons stay crisp and high-contrast on both light and
     * dark themes, instead of a fixed mid-gray that washes out.
     */
    fun neutralIcon(): Color = UIManager.getColor("Label.foreground") ?: Color.GRAY

    /** Glyph used for the "All types" sidebar row, which has no category behind it. */
    val ALL_ICON = RemixIcon.ARCHIVE_2_FILL

    /** Fill glyphs mapped to their outlined twin, for places that draw a file glyph outlined. */
    private val LINE_VARIANTS: Map<RemixIcon, RemixIcon> = linkedMapOf(
        RemixIcon.FILE_FILL to RemixIcon.FILE_LINE,
        RemixIcon.FILE_LIST_2_FILL to RemixIcon.FILE_LIST_2_LINE,
        RemixIcon.FILE_TEXT_FILL to RemixIcon.FILE_TEXT_LINE,
        RemixIcon.FILE_ZIP_FILL to RemixIcon.FILE_ZIP_LINE,
        RemixIcon.IMAGE_FILL to RemixIcon.IMAGE_LINE,
        RemixIcon.MAGNET_FILL to RemixIcon.MAGNET_LINE,
        RemixIcon.MV_FILL to RemixIcon.MUSIC_2_LINE,
        RemixIcon.MOVIE_FILL to RemixIcon.MOVIE_LINE,
        RemixIcon.MICROSOFT_FILL to RemixIcon.TERMINAL_BOX_LINE,
        RemixIcon.ARCHIVE_2_FILL to RemixIcon.ARCHIVE_2_LINE,
    )

    /** The outlined twin of [icon], or [icon] itself when it has no filled/outlined pair. */
    fun lineVariant(icon: RemixIcon): RemixIcon = LINE_VARIANTS[icon] ?: icon

    /**
     * File-type glyphs by extension. A download's glyph comes from its file name alone, never from its
     * category (categories only have a color).
     */
    private val GLYPHS: Map<String, RemixIcon> = buildMap {
        fun put(glyph: RemixIcon, vararg exts: String) = exts.forEach { put(it, glyph) }
        put(RemixIcon.MOVIE_FILL, "mp4", "mkv", "webm", "avi", "ts", "mov", "m4v", "wmv", "flv", "mpg", "mpeg", "3gp", "ogv")
        put(RemixIcon.MV_FILL, "mp3", "aac", "wav", "ac3", "m4a", "flac", "ogg", "oga", "opus", "wma")
        put(RemixIcon.FILE_ZIP_FILL, "zip", "rar", "7z", "gz", "tar", "tgz", "tz", "bz2", "xz", "zst", "cab")
        put(RemixIcon.FILE_LIST_2_FILL, "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "odt", "odf", "ods", "odp", "rtf", "txt", "epub")
        put(RemixIcon.MICROSOFT_FILL, "exe", "msi", "msix", "deb", "dmg", "rpm", "iso", "pkg", "sh", "py", "apk", "xapk", "appimage", "jar", "img")
        put(RemixIcon.IMAGE_FILL, "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "tiff", "tif", "avif", "heic")
        put(RemixIcon.MAGNET_FILL, "torrent")
    }

    /** The glyph for a file of this name, from its extension; a plain file glyph when it is unknown. */
    fun glyphForFile(fileName: String): RemixIcon =
        GLYPHS[fileName.substringAfterLast('.', "").lowercase()] ?: RemixIcon.FILE_FILL

    /**
     * Returns the appropriate line glyph representing the category or file type.
     */
    fun glyphForCategory(categoryId: String?, fileName: String? = null, downloadType: DownloadType? = null): RemixIcon {
        if (downloadType == DownloadType.Torrent) return RemixIcon.MAGNET_LINE
        return when (categoryId) {
            "CAT_DOCUMENTS" -> RemixIcon.FILE_TEXT_LINE
            "CAT_COMPRESSED" -> RemixIcon.FILE_ZIP_LINE
            "CAT_MUSIC" -> RemixIcon.MUSIC_2_LINE
            "CAT_VIDEOS" -> RemixIcon.MOVIE_LINE
            "CAT_PROGRAMS" -> RemixIcon.TERMINAL_BOX_LINE
            "CAT_IMAGES" -> RemixIcon.IMAGE_LINE
            else -> {
                if (!fileName.isNullOrBlank()) {
                    val glyph = glyphForFile(fileName)
                    lineVariant(glyph)
                } else {
                    RemixIcon.FILE_LINE
                }
            }
        }
    }

    /**
     * Returns the theme/category color for the category.
     */
    fun colorForCategory(categoryId: String?, downloadType: DownloadType? = null): Color {
        if (downloadType == DownloadType.Torrent) return EMERALD
        val skinColor = categoryId?.let { SkinManager.currentSkin.categoryColor(it, com.formdev.flatlaf.FlatLaf.isLafDark()) }
        if (skinColor != null) return skinColor
        val themeColor = categoryId?.let { UIManager.getColor("XDM.category.$it") }
        if (themeColor != null) return themeColor
        return when (categoryId) {
            "CAT_DOCUMENTS" -> Color(88, 166, 255)
            "CAT_COMPRESSED" -> Color(210, 153, 34)
            "CAT_MUSIC" -> Color(240, 136, 62)
            "CAT_VIDEOS" -> Color(163, 113, 247)
            "CAT_PROGRAMS" -> Color(230, 80, 80)
            "CAT_IMAGES" -> Color(56, 189, 248)
            else -> neutralIcon()
        }
    }

    /**
     * Resolves a human-readable category name for display.
     */
    fun displayNameForCategory(categoryId: String?, downloadType: DownloadType? = null): String {
        if (downloadType == DownloadType.Torrent) return "Torrents"
        return when (categoryId) {
            "CAT_DOCUMENTS" -> "Documents"
            "CAT_COMPRESSED" -> "Compressed"
            "CAT_MUSIC" -> "Music"
            "CAT_VIDEOS" -> "Videos"
            "CAT_PROGRAMS" -> "Programs"
            "CAT_IMAGES" -> "Images"
            else -> "Other"
        }
    }

    /**
     * Creates a DPI-scaled category Icon (classic bitmap or modern vector glyph).
     */
    fun createCategoryIcon(
        categoryId: String?,
        fileName: String? = null,
        downloadType: DownloadType? = null,
        size: Int = 18
    ): Icon {
        val classicIcon = SkinManager.currentSkin.getCategoryIcon(categoryId, size.px)
        if (classicIcon != null) {
            return classicIcon
        }
        val glyph = glyphForCategory(categoryId, fileName, downloadType)
        val color = colorForCategory(categoryId, downloadType)
        return FontIcon(glyph, size.px, color)
    }
}
