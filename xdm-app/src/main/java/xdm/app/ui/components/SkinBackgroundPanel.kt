package xdm.app.ui.components

import com.formdev.flatlaf.FlatLaf
import xdm.app.skins.SkinRegion
import xdm.app.skins.SkinStyleResolver
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * Reusable Swing container panel that paints the active skin's background color
 * and wallpaper for the specified [region] (defaulting to [SkinRegion.MAIN_FRAME]).
 */
open class SkinBackgroundPanel(
    layout: LayoutManager = BorderLayout(),
    val region: SkinRegion = SkinRegion.MAIN_FRAME
) : JPanel(layout) {
    init {
        isOpaque = true
    }

    override fun paintComponent(g: Graphics) {
        val isDark = FlatLaf.isLafDark()
        val painted = SkinStyleResolver.paintRegionBackground(g, width, height, region, isDark)
        if (!painted) {
            super.paintComponent(g)
        }
    }
}
