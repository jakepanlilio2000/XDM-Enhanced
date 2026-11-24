package xdm.app.skins

import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager

/**
 * 1:1 upstream appearance preset faithfully recreating subhra74/xdm (Xtreme Download Manager):
 * FilterListPanel (Library + File types + Stay in touch), AppToolBar (+ New / Clear / Sort / Settings / Search / Menu),
 * FlatMac Dark (#282828/#1E1E1E/#1E3454) & Light (#F5F6F8/#FFFFFF/#D4DFF8) palettes, and compact DownloadRowPanel rows.
 */
class BuiltInClassicXdmSkin : Skin {
    override val id: String = SkinManager.SKIN_ORIGINAL_XDM
    override val name: String = "Original XDM"
    override val description: String =
        "Authentic 1:1 upstream Xtreme Download Manager (subhra74/xdm) UI preset: FilterListPanel sidebar, AppToolBar, compact download rows, and upstream FlatMac palette."
    override val author: String = "XDM Community (Upstream preset)"
    override val version: String = "1.1.0"
    override val isBuiltIn: Boolean = true
    override val attributionNotice: String =
        "Upstream-style appearance preset. Original XDM created by Subhra Das Gupta. Independent fork preset; not officially endorsed."

    override val componentArc: Int = 8
    override val buttonArc: Int = 8
    override val rowHeight: Int = 52
    override val sidebarWidth: Int = 190
    override val useClassicIcons: Boolean = false

    override val layoutConfig: SkinLayoutConfig = SkinLayoutConfig(
        toolbarPosition = "top",
        sidebarPosition = "left",
        showStatusBar = false,
        showSearchBar = true,
        showMenuBar = false,
        chromeMode = "upstream"
    )

    override val defaultDarkAccent: Color = Color(0x6E, 0x9B, 0xFF)
    override val defaultLightAccent: Color = Color(0x3B, 0x6F, 0xE0)

    private val classicRegions: Map<SkinRegion, SkinRegionConfig> by lazy {
        mapOf(
            SkinRegion.MAIN_FRAME to SkinRegionConfig(cornerArc = 8, buttonArc = 8),
            SkinRegion.TOOLBAR to SkinRegionConfig(height = 40, buttonArc = 8, cornerArc = 8),
            SkinRegion.SIDEBAR to SkinRegionConfig(width = 190, cornerArc = 8),
            SkinRegion.DOWNLOAD_LIST to SkinRegionConfig(height = 52, cardArc = 12, cornerArc = 8),
            SkinRegion.DOWNLOAD_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.VIDEO_DOWNLOAD_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.PROGRESS_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8, progressBarArc = 8),
            SkinRegion.TORRENT_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.SETTINGS to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.PLUGIN_MANAGER to SkinRegionConfig(cornerArc = 10, buttonArc = 8, cardArc = 8),
            SkinRegion.ABOUT to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.DIALOGS to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.TOOLTIPS_POPUPS to SkinRegionConfig(cornerArc = 8)
        )
    }

    override fun getRegionConfig(region: SkinRegion): SkinRegionConfig? = classicRegions[region]

    private val darkCategoryColors = mapOf(
        "CAT_DOCUMENTS" to Color(0x5B, 0xC0, 0x8A),
        "CAT_COMPRESSED" to Color(0x6E, 0x9B, 0xFF),
        "CAT_MUSIC" to Color(0xF5, 0xB4, 0x54),
        "CAT_VIDEOS" to Color(0xB7, 0x9C, 0xFF),
        "CAT_PROGRAMS" to Color(0x4F, 0xD1, 0xC5),
        "CAT_IMAGES" to Color(0xF2, 0x8F, 0xB0),
        "CAT_TORRENTS" to Color(0x6E, 0x9B, 0xFF),
        "other" to Color(0xF2, 0x8F, 0xB0)
    )

    private val lightCategoryColors = mapOf(
        "CAT_DOCUMENTS" to Color(0x2E, 0x9E, 0x62),
        "CAT_COMPRESSED" to Color(0x3B, 0x6F, 0xE0),
        "CAT_MUSIC" to Color(0xD6, 0x96, 0x2A),
        "CAT_VIDEOS" to Color(0x7B, 0x5B, 0xD6),
        "CAT_PROGRAMS" to Color(0x0E, 0x8F, 0x84),
        "CAT_IMAGES" to Color(0xC2, 0x45, 0x7A),
        "CAT_TORRENTS" to Color(0x3B, 0x6F, 0xE0),
        "other" to Color(0xC2, 0x45, 0x7A)
    )

    override fun categoryColor(categoryId: String, isDark: Boolean): Color? =
        if (isDark) darkCategoryColors[categoryId] else lightCategoryColors[categoryId]

    override fun getCategoryIcon(categoryId: String?, size: Int): Icon? = null

    override fun getToolbarIcon(actionKey: String, size: Int): Icon? = null

    override fun applyCustomDefaults(isDark: Boolean) {
        UIManager.put("Component.arc", componentArc)
        UIManager.put("Button.arc", buttonArc)
        UIManager.put("TextComponent.arc", componentArc)
        UIManager.put("ProgressBar.arc", componentArc)
        UIManager.put("CheckBox.arc", componentArc)
        UIManager.put("TabbedPane.tabArc", componentArc)
        UIManager.put("ScrollBar.thumbArc", componentArc)
        UIManager.put("PopupMenu.arc", componentArc)
        UIManager.put("ToolTip.arc", componentArc)
        UIManager.put("PopupMenu.borderColor", if (isDark) Color(0x3A, 0x3D, 0x45) else Color(0xDC, 0xDE, 0xE3))

        UIManager.put("XDM.skin.id", id)
        UIManager.put("XDM.skin.rowHeight", rowHeight)
        UIManager.put("XDM.skin.sidebarWidth", sidebarWidth)
        UIManager.put("XDM.skin.useClassicIcons", false)

        if (isDark) {
            UIManager.put("XDM.accent", Color(0x6E, 0x9B, 0xFF))
            UIManager.put("XDM.window.background", Color(0x28, 0x28, 0x28))
            UIManager.put("XDM.list.background", Color(0x1E, 0x1E, 0x1E))
            UIManager.put("XDM.list.titleForeground", Color(0xD4, 0xD4, 0xD4))
            UIManager.put("XDM.list.emptyIconColor", Color(0x2C, 0x2C, 0x2C))
            UIManager.put("XDM.list.selectionBackground", Color(0x1E, 0x34, 0x54))
            UIManager.put("XDM.list.hoverBackground", Color(0x25, 0x25, 0x25))
            UIManager.put("XDM.list.infoForeground", Color(0x9E, 0x9E, 0x9E))
            UIManager.put("XDM.list.selectedInfoForeground", Color(0xC8, 0xC8, 0xC8))
            UIManager.put("XDM.list.selectedButtonColor", Color(0xD8, 0xD8, 0xD8))
            UIManager.put("XDM.sidebar.background", Color(0x28, 0x28, 0x28))
            UIManager.put("XDM.sidebar.borderColor", Color(0x00, 0x00, 0x00))
            UIManager.put("XDM.sidebar.foreground", Color(0xB8, 0xB8, 0xB8))
            UIManager.put("XDM.sidebar.selectedForeground", Color(0xFE, 0xFE, 0xFE))
            UIManager.put("XDM.sidebar.selectionBackground", Color(0x38, 0x38, 0x38))
            UIManager.put("XDM.sidebar.hoverBackground", Color(0x30, 0x30, 0x30))
            UIManager.put("XDM.sidebar.iconColor", Color(0x92, 0x92, 0x92))
            UIManager.put("XDM.sidebar.sectionForeground", Color(0x80, 0x80, 0x80))
            UIManager.put("XDM.sidebar.countForeground", Color(0x80, 0x80, 0x80))
            UIManager.put("XDM.sidebar.selectedCountForeground", Color(0xB8, 0xB8, 0xB8))
            UIManager.put("Table.background", Color(0x1E, 0x1E, 0x1E))
            UIManager.put("Panel.background", Color(0x28, 0x28, 0x28))
            UIManager.put("OptionPane.background", Color(0x28, 0x28, 0x28))
            UIManager.put("Button.foreground", Color.WHITE)
            UIManager.put("Button.disabledBackground", Color(0x33, 0x33, 0x33))
            UIManager.put("Button.disabledText", Color(0x6E, 0x6E, 0x6E))
            UIManager.put("ToolTip.background", Color(0x32, 0x32, 0x32))
            UIManager.put("ToolTip.foreground", Color(0xEC, 0xED, 0xEF))
            darkCategoryColors.forEach { (cat, col) ->
                UIManager.put("XDM.category.$cat", col)
            }
        } else {
            UIManager.put("XDM.accent", Color(0x3B, 0x6F, 0xE0))
            UIManager.put("XDM.window.background", Color(0xF5, 0xF6, 0xF8))
            UIManager.put("XDM.list.background", Color(0xFF, 0xFF, 0xFF))
            UIManager.put("XDM.list.titleForeground", Color(0x2E, 0x31, 0x38))
            UIManager.put("XDM.list.emptyIconColor", Color(0xF0, 0xF1, 0xF4))
            UIManager.put("XDM.list.selectionBackground", Color(0xD4, 0xDF, 0xF8))
            UIManager.put("XDM.list.hoverBackground", Color(0xF6, 0xF7, 0xF9))
            UIManager.put("XDM.list.infoForeground", Color(0x6B, 0x6F, 0x78))
            UIManager.put("XDM.list.selectedInfoForeground", Color(0x3E, 0x4A, 0x63))
            UIManager.put("XDM.list.selectedButtonColor", Color(0x2F, 0x3B, 0x54))
            UIManager.put("XDM.sidebar.background", Color(0xF5, 0xF6, 0xF8))
            UIManager.put("XDM.sidebar.borderColor", Color(0xE4, 0xE6, 0xEA))
            UIManager.put("XDM.sidebar.foreground", Color(0x4A, 0x4E, 0x57))
            UIManager.put("XDM.sidebar.selectedForeground", Color(0x17, 0x18, 0x1C))
            UIManager.put("XDM.sidebar.selectionBackground", Color(0xD4, 0xDF, 0xF8))
            UIManager.put("XDM.sidebar.hoverBackground", Color(0xEC, 0xEE, 0xF2))
            UIManager.put("XDM.sidebar.iconColor", Color(0x6B, 0x6F, 0x78))
            UIManager.put("XDM.sidebar.sectionForeground", Color(0x6E, 0x72, 0x7B))
            UIManager.put("XDM.sidebar.countForeground", Color(0x68, 0x6C, 0x75))
            UIManager.put("XDM.sidebar.selectedCountForeground", Color(0x34, 0x40, 0x5A))
            UIManager.put("Table.background", Color(0xFF, 0xFF, 0xFF))
            UIManager.put("Panel.background", Color(0xF5, 0xF6, 0xF8))
            UIManager.put("OptionPane.background", Color(0xF5, 0xF6, 0xF8))
            UIManager.put("ToolTip.background", Color(0xFF, 0xFF, 0xFF))
            UIManager.put("ToolTip.foreground", Color(0x17, 0x18, 0x1C))
            lightCategoryColors.forEach { (cat, col) ->
                UIManager.put("XDM.category.$cat", col)
            }
        }

        classicRegions.forEach { (region, cfg) ->
            UIManager.put("XDM.region.${region.slotId}.cornerArc", cfg.cornerArc ?: 8)
            UIManager.put("XDM.region.${region.slotId}.buttonArc", cfg.buttonArc ?: 8)
            cfg.cardArc?.let { UIManager.put("XDM.region.${region.slotId}.cardArc", it) }
            cfg.progressBarArc?.let { UIManager.put("XDM.region.${region.slotId}.progressBarArc", it) }
        }
    }
}
