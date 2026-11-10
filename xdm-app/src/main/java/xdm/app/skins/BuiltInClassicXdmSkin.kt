package xdm.app.skins

import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager

/**
 * Upstream-style appearance preset recreating the classic XDM look and feel.
 * Uses original GPL-covered toolbar PNGs, category icons, compact near-square borders,
 * and classic dark/light palettes.
 */
class BuiltInClassicXdmSkin : Skin {
    override val id: String = SkinManager.SKIN_ORIGINAL_XDM
    override val name: String = "Original XDM"
    override val description: String =
        "Classic upstream XDM appearance preset recreating the traditional toolbar icons, category styling, and compact dark palette."
    override val author: String = "XDM Community (Upstream preset)"
    override val version: String = "1.0.0"
    override val isBuiltIn: Boolean = true
    override val attributionNotice: String =
        "Upstream-style appearance preset. Original XDM created by Subhra Das Gupta. Independent fork preset; not officially endorsed."

    override val componentArc: Int = 2
    override val buttonArc: Int = 2
    override val rowHeight: Int = 44
    override val sidebarWidth: Int = 200
    override val useClassicIcons: Boolean = true

    override val defaultDarkAccent: Color = Color(0x00, 0x78, 0xD7)
    override val defaultLightAccent: Color = Color(0x00, 0x78, 0xD7)

    private val darkCategoryColors = mapOf(
        "CAT_DOCUMENTS" to Color(0x8E, 0x92, 0x9C),
        "CAT_COMPRESSED" to Color(0xF5, 0xB4, 0x54),
        "CAT_MUSIC" to Color(0x5B, 0xC0, 0x8A),
        "CAT_VIDEOS" to Color(0xB7, 0x9C, 0xFF),
        "CAT_PROGRAMS" to Color(0x4F, 0xD1, 0xC5),
        "CAT_IMAGES" to Color(0xFF, 0x6B, 0x8B),
        "CAT_TORRENTS" to Color(0x4F, 0xC3, 0xF7),
        "other" to Color(0xF2, 0x8F, 0xB0)
    )

    private val lightCategoryColors = mapOf(
        "CAT_DOCUMENTS" to Color(0x5A, 0x5E, 0x68),
        "CAT_COMPRESSED" to Color(0xC6, 0x8A, 0x20),
        "CAT_MUSIC" to Color(0x1B, 0x80, 0x4B),
        "CAT_VIDEOS" to Color(0x63, 0x40, 0xB8),
        "CAT_PROGRAMS" to Color(0x0A, 0x6B, 0x63),
        "CAT_IMAGES" to Color(0xD6, 0x00, 0xFF),
        "CAT_TORRENTS" to Color(0x02, 0x88, 0xD1),
        "other" to Color(0xAD, 0x30, 0x68)
    )

    override fun categoryColor(categoryId: String, isDark: Boolean): Color? =
        if (isDark) darkCategoryColors[categoryId] else lightCategoryColors[categoryId]

    override fun getCategoryIcon(categoryId: String?, size: Int): Icon? {
        val iconName = when (categoryId) {
            "CAT_DOCUMENTS" -> "document"
            "CAT_COMPRESSED" -> "compressed"
            "CAT_MUSIC" -> "music"
            "CAT_VIDEOS" -> "video"
            "CAT_PROGRAMS" -> "program"
            "CAT_IMAGES" -> "covert_video"
            "CAT_TORRENTS", "torrents" -> "compressed"
            "ALL" -> "folder"
            "unfinished" -> "down"
            "completed" -> "checked"
            "queues" -> "bar"
            "other" -> "other"
            else -> "document"
        }
        return ClassicIconLoader.loadIcon(iconName, size)
    }

    override fun getToolbarIcon(actionKey: String, size: Int): Icon? {
        val iconName = when (actionKey) {
            "ADD_URL", "ADD_DOWNLOAD" -> "tool_add"
            "ADD_MAGNET" -> "down"
            "ADD_TORRENT" -> "compressed"
            "RESUME" -> "tool_resume"
            "STOP", "PAUSE" -> "tool_pause"
            "STOP_ALL" -> "tool_pause"
            "DELETE" -> "tool_del"
            "DELETE_COMPLETED" -> "checked"
            "SCHEDULER" -> "bar"
            "SETTINGS" -> "tool_settings"
            "SORT" -> "search"
            else -> null
        } ?: return null
        return ClassicIconLoader.loadIcon(iconName, size)
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
        UIManager.put("XDM.skin.useClassicIcons", true)

        if (isDark) {
            UIManager.put("XDM.list.background", Color(0x14, 0x15, 0x18))
            UIManager.put("XDM.sidebar.background", Color(0x1A, 0x1B, 0x1F))
            UIManager.put("XDM.sidebar.borderColor", Color(0x2E, 0x30, 0x36))
            UIManager.put("XDM.sidebar.selectionBackground", Color(0x2A, 0x2D, 0x35))
            UIManager.put("XDM.sidebar.hoverBackground", Color(0x22, 0x24, 0x2A))
            UIManager.put("XDM.sidebar.foreground", Color(0xC5, 0xC8, 0xD0))
            UIManager.put("XDM.sidebar.selectedForeground", Color(0xFF, 0xFF, 0xFF))
            UIManager.put("XDM.list.titleForeground", Color(0xDC, 0xDF, 0xE6))
            UIManager.put("Table.background", Color(0x14, 0x15, 0x18))
            UIManager.put("Panel.background", Color(0x1A, 0x1B, 0x1F))
            darkCategoryColors.forEach { (cat, col) ->
                UIManager.put("XDM.category.$cat", col)
            }
        } else {
            UIManager.put("XDM.list.background", Color(0xFF, 0xFF, 0xFF))
            UIManager.put("XDM.sidebar.background", Color(0xDF, 0xE1, 0xE5))
            UIManager.put("XDM.sidebar.borderColor", Color(0xCF, 0xD2, 0xD8))
            UIManager.put("XDM.sidebar.selectionBackground", Color(0xD0, 0xD5, 0xDE))
            UIManager.put("XDM.sidebar.hoverBackground", Color(0xD8, 0xDC, 0xE4))
            UIManager.put("XDM.sidebar.foreground", Color(0x3A, 0x3D, 0x44))
            UIManager.put("XDM.sidebar.selectedForeground", Color(0x11, 0x12, 0x14))
            UIManager.put("XDM.list.titleForeground", Color(0x20, 0x22, 0x26))
            UIManager.put("Table.background", Color(0xFF, 0xFF, 0xFF))
            UIManager.put("Panel.background", Color(0xF1, 0xF3, 0xF4))
            lightCategoryColors.forEach { (cat, col) ->
                UIManager.put("XDM.category.$cat", col)
            }
        }
    }
}

