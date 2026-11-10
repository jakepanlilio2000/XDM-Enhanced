package xdm.app.skins

import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager

/**
 * Modern default skin for XDM Enhanced.
 * Smooth 8px rounded corners, dynamic accents, FlatMac styling, and RemixIcon vector glyphs.
 */
class BuiltInEnhancedSkin : Skin {
    override val id: String = SkinManager.SKIN_ENHANCED
    override val name: String = "XDM Enhanced"
    override val description: String =
        "Modern refined appearance featuring FlatMac styling, smooth rounded corners, dynamic accents, and RemixIcon vector glyphs."
    override val author: String = "XDM Enhanced Contributors"
    override val version: String = "1.0.0"
    override val isBuiltIn: Boolean = true
    override val attributionNotice: String? = null

    override val componentArc: Int = 8
    override val buttonArc: Int = 8
    override val rowHeight: Int = 54
    override val sidebarWidth: Int = 210
    override val useClassicIcons: Boolean = false

    override val defaultDarkAccent: Color = Color(0x6E, 0x9B, 0xFF)
    override val defaultLightAccent: Color = Color(0x3B, 0x6F, 0xE0)

    private val enhancedRegions: Map<SkinRegion, SkinRegionConfig> by lazy {
        mapOf(
            SkinRegion.MAIN_FRAME to SkinRegionConfig(cornerArc = 8, buttonArc = 8),
            SkinRegion.TOOLBAR to SkinRegionConfig(height = 42, buttonArc = 8, cornerArc = 8),
            SkinRegion.SIDEBAR to SkinRegionConfig(width = 210, cornerArc = 8),
            SkinRegion.DOWNLOAD_LIST to SkinRegionConfig(height = 54, cardArc = 8, cornerArc = 8),
            SkinRegion.DOWNLOAD_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.VIDEO_DOWNLOAD_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.PROGRESS_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8, progressBarArc = 8),
            SkinRegion.TORRENT_MODAL to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.SETTINGS to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.PLUGIN_MANAGER to SkinRegionConfig(cornerArc = 10, buttonArc = 8, cardArc = 8),
            SkinRegion.ABOUT to SkinRegionConfig(cornerArc = 12, buttonArc = 8),
            SkinRegion.DIALOGS to SkinRegionConfig(cornerArc = 10, buttonArc = 8),
            SkinRegion.TOOLTIPS_POPUPS to SkinRegionConfig(cornerArc = 8)
        )
    }

    override fun getRegionConfig(region: SkinRegion): SkinRegionConfig? = enhancedRegions[region]

    override fun categoryColor(categoryId: String, isDark: Boolean): Color? = null

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

        UIManager.put("XDM.skin.id", id)
        UIManager.put("XDM.skin.rowHeight", rowHeight)
        UIManager.put("XDM.skin.sidebarWidth", sidebarWidth)
        UIManager.put("XDM.skin.useClassicIcons", false)

        enhancedRegions.forEach { (region, cfg) ->
            UIManager.put("XDM.region.${region.slotId}.cornerArc", cfg.cornerArc ?: 8)
            UIManager.put("XDM.region.${region.slotId}.buttonArc", cfg.buttonArc ?: 8)
            cfg.cardArc?.let { UIManager.put("XDM.region.${region.slotId}.cardArc", it) }
            cfg.progressBarArc?.let { UIManager.put("XDM.region.${region.slotId}.progressBarArc", it) }
        }
    }
}
