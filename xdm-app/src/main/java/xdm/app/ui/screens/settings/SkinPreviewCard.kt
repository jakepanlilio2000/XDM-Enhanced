package xdm.app.ui.screens.settings

import com.formdev.flatlaf.FlatLaf
import xdm.app.skins.Skin
import xdm.app.skins.SkinManager
import xdm.app.ui.components.ThemeManager
import xdm.app.utils.px
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * Live visual preview card demonstrating how a skin renders window framing,
 * toolbar buttons, category colors, sidebar background, and download progress.
 */
class SkinPreviewCard(private var skin: Skin = SkinManager.currentSkin) : JComponent() {

    init {
        preferredSize = Dimension(380.px, 150.px)
        maximumSize = Dimension(480.px, 160.px)
        minimumSize = Dimension(320.px, 140.px)
        isOpaque = false
    }

    fun setPreviewSkin(newSkin: Skin) {
        this.skin = newSkin
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val w = width
            val h = height
            val isDark = FlatLaf.isLafDark()
            val arc = (skin.componentArc * 1.5).toInt().coerceIn(4, 16).px
            val accent = ThemeManager.currentAccent ?: (if (isDark) skin.defaultDarkAccent else skin.defaultLightAccent)

            // Outer Card Background
            val cardBg = if (isDark) Color(0x18, 0x1A, 0x20) else Color(0xFA, 0xFA, 0xFB)
            val cardBorder = if (isDark) Color(0x2D, 0x31, 0x39) else Color(0xD8, 0xDC, 0xE2)
            g2.color = cardBg
            g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat() - 1, h.toFloat() - 1, arc.toFloat(), arc.toFloat()))
            skin.paintWallpaper(g2, w, h, isDark)
            g2.color = cardBorder
            g2.draw(RoundRectangle2D.Float(0f, 0f, w.toFloat() - 1, h.toFloat() - 1, arc.toFloat(), arc.toFloat()))

            // Window Header (Chrome)
            val headerH = 26.px
            val headerBg = if (isDark) Color(0x20, 0x23, 0x2B) else Color(0xEE, 0xF0, 0xF4)
            g2.color = headerBg
            g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat() - 1, headerH.toFloat(), arc.toFloat(), arc.toFloat()))
            g2.fillRect(0, headerH - 4.px, w - 1, 4.px)

            // Mac window dots / classic title
            val dotY = 13.px
            g2.color = Color(0xFF, 0x5F, 0x56)
            g2.fillOval(10.px, dotY - 4.px, 8.px, 8.px)
            g2.color = Color(0xFF, 0xBD, 0x2E)
            g2.fillOval(22.px, dotY - 4.px, 8.px, 8.px)
            g2.color = Color(0x27, 0xC9, 0x3F)
            g2.fillOval(34.px, dotY - 4.px, 8.px, 8.px)

            val baseFont = font ?: UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)

            // Header title
            g2.color = if (isDark) Color(0xEC, 0xED, 0xEF) else Color(0x18, 0x18, 0x1B)
            g2.font = baseFont.deriveFont(Font.BOLD, 10f.px)
            val title = "${skin.name} Preview"
            g2.drawString(title, 48.px, 17.px)

            // Active badge if active
            if (skin.id == SkinManager.currentSkin.id) {
                g2.color = accent
                val badgeText = "ACTIVE"
                g2.font = baseFont.deriveFont(Font.BOLD, 8.5f.px)
                val badgeW = g2.fontMetrics.stringWidth(badgeText) + 8.px
                g2.fillRoundRect(w - badgeW - 10.px, 6.px, badgeW, 14.px, 6.px, 6.px)
                g2.color = ThemeManager.contrastForeground(accent)
                g2.drawString(badgeText, w - badgeW - 6.px, 16.px)
            }

            // Sidebar on the left
            val sidebarW = 74.px
            val sidebarBg = if (skin.id == SkinManager.SKIN_ORIGINAL_XDM) {
                if (isDark) Color(0x1A, 0x1B, 0x1F) else Color(0xDF, 0xE1, 0xE5)
            } else {
                if (isDark) Color(0x16, 0x17, 0x1B) else Color(0xF5, 0xF6, 0xF8)
            }
            g2.color = sidebarBg
            g2.fillRect(1, headerH, sidebarW, h - headerH - 1)

            // Sidebar separator
            g2.color = if (isDark) Color(0x28, 0x2B, 0x33) else Color(0xE0, 0xE2, 0xE7)
            g2.drawLine(sidebarW, headerH, sidebarW, h - 1)

            // Mini category items in sidebar
            val catColors = listOf(
                skin.categoryColor("CAT_DOCUMENTS", isDark) ?: Color(88, 166, 255),
                skin.categoryColor("CAT_COMPRESSED", isDark) ?: Color(210, 153, 34),
                skin.categoryColor("CAT_MUSIC", isDark) ?: Color(240, 136, 62),
                skin.categoryColor("CAT_VIDEOS", isDark) ?: Color(163, 113, 247)
            )
            val catNames = listOf("Docs", "Zip", "Music", "Video")
            var catY = headerH + 12.px
            g2.font = baseFont.deriveFont(Font.PLAIN, 8f.px)
            for (i in catColors.indices) {
                g2.color = catColors[i]
                g2.fillOval(8.px, catY - 5.px, 6.px, 6.px)
                g2.color = if (isDark) Color(0xA8, 0xAC, 0xB6) else Color(0x4A, 0x4D, 0x54)
                g2.drawString(catNames[i], 18.px, catY)
                catY += 16.px
            }

            // Toolbar Area
            val toolX = sidebarW + 8.px
            val toolY = headerH + 6.px
            val btnW = 20.px
            val btnH = 16.px
            val btnArc = skin.buttonArc.px.coerceIn(2, 6)
            val toolBg = if (isDark) Color(0x28, 0x2A, 0x32) else Color(0xE2, 0xE5, 0xEC)

            for (b in 0 until 4) {
                val bx = toolX + b * (btnW + 4.px)
                g2.color = toolBg
                g2.fillRoundRect(bx, toolY, btnW, btnH, btnArc, btnArc)
                g2.color = if (b == 0) accent else (if (isDark) Color(0x9A, 0x9E, 0xA8) else Color(0x60, 0x64, 0x6E))
                g2.fillRect(bx + 5.px, toolY + 4.px, 10.px, 8.px)
            }

            // Download Rows Area
            val rowX = sidebarW + 8.px
            val rowW = w - rowX - 10.px

            // Row 1: Active download
            val r1Y = toolY + btnH + 10.px
            val rowBg = if (isDark) Color(0x1F, 0x21, 0x28) else Color(0xFA, 0xFB, 0xFD)
            val rArc = skin.componentArc.px.coerceIn(2, 8)
            g2.color = rowBg
            g2.fillRoundRect(rowX, r1Y, rowW, 28.px, rArc, rArc)

            // Category badge
            g2.color = catColors[0]
            g2.fillRoundRect(rowX + 6.px, r1Y + 5.px, 18.px, 18.px, 4.px, 4.px)

            // Filename
            g2.color = if (isDark) Color(0xEC, 0xED, 0xEF) else Color(0x18, 0x18, 0x1B)
            g2.font = baseFont.deriveFont(Font.BOLD, 9f.px)
            g2.drawString("Project_Specification.pdf", rowX + 28.px, r1Y + 12.px)

            // Mini progress bar in accent color
            val pbX = rowX + 28.px
            val pbY = r1Y + 16.px
            val pbW = rowW - 90.px
            g2.color = if (isDark) Color(0x33, 0x37, 0x42) else Color(0xD8, 0xDC, 0xE4)
            g2.fillRoundRect(pbX, pbY, pbW, 4.px, 2.px, 2.px)
            g2.color = accent
            g2.fillRoundRect(pbX, pbY, (pbW * 0.72f).toInt(), 4.px, 2.px, 2.px)

            // Speed
            g2.color = accent
            g2.font = baseFont.deriveFont(Font.PLAIN, 8f.px)
            g2.drawString("72% - 2.4 MB/s", rowX + rowW - 56.px, r1Y + 18.px)

            // Row 2: Completed download
            val r2Y = r1Y + 32.px
            g2.color = rowBg
            g2.fillRoundRect(rowX, r2Y, rowW, 26.px, rArc, rArc)

            g2.color = catColors[3]
            g2.fillRoundRect(rowX + 6.px, r2Y + 4.px, 18.px, 18.px, 4.px, 4.px)

            g2.color = if (isDark) Color(0xD0, 0xD2, 0xD6) else Color(0x30, 0x32, 0x36)
            g2.font = baseFont.deriveFont(Font.BOLD, 9f.px)
            g2.drawString("Release_Demo_1080p.mp4", rowX + 28.px, r2Y + 12.px)

            g2.color = if (isDark) Color(0x10, 0xB9, 0x81) else Color(0x05, 0x96, 0x69)
            g2.font = baseFont.deriveFont(Font.PLAIN, 8f.px)
            g2.drawString("Complete (42.8 MB)", rowX + 28.px, r2Y + 22.px)

        } finally {
            g2.dispose()
        }
    }
}

