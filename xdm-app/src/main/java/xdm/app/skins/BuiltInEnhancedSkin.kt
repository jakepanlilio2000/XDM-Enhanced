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

    override fun categoryColor(categoryId: String, isDark: Boolean): Color? = null

    override fun getCategoryIcon(categoryId: String?, size: Int): Icon? = null

    override fun getToolbarIcon(actionKey: String, size: Int): Icon? = null

    override fun applyCustomDefaults(isDark: Boolean) {
        UIManager.put("Component.arc", componentArc)
        UIManager.put("Button.arc", buttonArc)
        UIManager.put("TextComponent.arc", componentArc)
        UIManager.put("ProgressBar.arc", componentArc)
        UIManager.put("CheckBox.arc", componentArc)

        UIManager.put("XDM.skin.id", id)
        UIManager.put("XDM.skin.rowHeight", rowHeight)
        UIManager.put("XDM.skin.sidebarWidth", sidebarWidth)
        UIManager.put("XDM.skin.useClassicIcons", false)
    }
}

