package xdm.app.ui.components

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.ui.FlatProgressBarUI
import xdm.app.skins.SkinManager
import xdm.app.skins.SkinRegion
import xdm.app.skins.SkinStyleResolver
import xdm.app.skins.SkinWidgetsConfig
import xdm.app.utils.px
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.AbstractBorder
import javax.swing.plaf.ComponentUI

/**
 * Custom progress bar UI registered globally via FlatLaf UIManager so segmented LED meters,
 * steampunk brass sight-tubes, anime candy stripes, and gradient glow bars
 * apply uniformly across download rows, cards, table cells, and progress windows.
 */
class SkinnedProgressBarUI : FlatProgressBarUI() {

    companion object {
        @Suppress("UNUSED_PARAMETER", "ACCIDENTAL_OVERRIDE")
        @JvmStatic
        fun createUI(c: JComponent): ComponentUI = SkinnedProgressBarUI()
    }

    override fun paint(g: Graphics, c: JComponent) {
        val widgets = SkinManager.currentSkin.widgetsConfig
        val pb = c as? JProgressBar
        val style = widgets?.progressBarStyle?.lowercase() ?: "flat"
        if (widgets == null || style == "flat" || pb == null || pb.isIndeterminate) {
            super.paint(g, c)
            return
        }

        val w = pb.width
        val h = pb.height
        if (w <= 0 || h <= 0) return

        val ins = pb.insets
        val barX = ins.left
        val barY = ins.top
        val barW = (w - ins.left - ins.right).coerceAtLeast(1)
        val barH = (h - ins.top - ins.bottom).coerceAtLeast(1)
        val fraction = pb.percentComplete.coerceIn(0.0, 1.0).toFloat()
        val isDark = FlatLaf.isLafDark()

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            when (style) {
                "segmented_led" -> paintSegmentedLed(g2, barX, barY, barW, barH, fraction, widgets, isDark)
                "brass_gauge" -> paintBrassGauge(g2, barX, barY, barW, barH, fraction, widgets, isDark)
                "striped_candy" -> paintStripedCandy(g2, barX, barY, barW, barH, fraction, widgets, isDark)
                "gradient_glow" -> paintGradientGlow(g2, barX, barY, barW, barH, fraction, widgets, isDark)
                else -> {
                    g2.dispose()
                    super.paint(g, c)
                    return
                }
            }
            if (pb.isStringPainted && pb.string != null) {
                paintString(g2, barX, barY, barW, barH, 0, ins)
            }
        } finally {
            g2.dispose()
        }
    }

    private fun resolveColors(cfg: SkinWidgetsConfig, isDark: Boolean, defTrack: Color, defStart: Color, defEnd: Color): Triple<Color, Color, Color> {
        val track = (if (isDark) cfg.progressTrackDark else cfg.progressTrackLight)
            ?.let { ThemeManager.parseHexColor(it) } ?: defTrack
        val start = (if (isDark) cfg.progressFillStartDark else cfg.progressFillStartLight)
            ?.let { ThemeManager.parseHexColor(it) } ?: defStart
        val end = (if (isDark) cfg.progressFillEndDark else cfg.progressFillEndLight)
            ?.let { ThemeManager.parseHexColor(it) } ?: defEnd
        return Triple(track, start, end)
    }

    private fun paintSegmentedLed(
        g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, fraction: Float, cfg: SkinWidgetsConfig, isDark: Boolean
    ) {
        val (track, startCol, endCol) = resolveColors(
            cfg, isDark, Color(0x0B, 0x10, 0x0C), Color(0x00, 0xFF, 0x66), Color(0xFF, 0xB8, 0x00)
        )
        g2.color = track
        g2.fillRect(x, y, w, h)
        g2.color = Color(0x3D, 0x44, 0x54)
        g2.drawRect(x, y, w - 1, h - 1)

        val count = cfg.progressSegments.coerceIn(6, 40)
        val gap = 2
        val innerW = (w - 4).coerceAtLeast(count)
        val segW = ((innerW - (count - 1) * gap).toFloat() / count).coerceAtLeast(2f)
        val litCount = (count * fraction).toInt()
        val sy = y + 2
        val sh = (h - 4).coerceAtLeast(2)

        for (i in 0 until count) {
            val sx = x + 2 + (i * (segW + gap)).toInt()
            val sw = segW.toInt().coerceAtLeast(2)
            if (sx >= x + w - 2) break
            val t = i.toFloat() / (count - 1).coerceAtLeast(1)
            val segCol = interpolate(startCol, endCol, t)
            g2.color = if (i < litCount) {
                segCol
            } else {
                Color(segCol.red / 5, segCol.green / 5, segCol.blue / 5)
            }
            g2.fillRect(sx, sy, sw, sh)
        }
    }

    private fun paintBrassGauge(
        g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, fraction: Float, cfg: SkinWidgetsConfig, isDark: Boolean
    ) {
        val (track, startCol, endCol) = resolveColors(
            cfg, isDark, Color(0x21, 0x15, 0x0B), Color(0xB4, 0x53, 0x09), Color(0xFB, 0xBF, 0x24)
        )
        val arc = minOf(h, 6.px)
        g2.color = track
        g2.fillRoundRect(x, y, w, h, arc, arc)

        val fillW = ((w - 2) * fraction).toInt().coerceAtLeast(if (fraction > 0f) 4 else 0)
        if (fillW > 0) {
            g2.paint = GradientPaint(x.toFloat(), y.toFloat(), startCol, (x + w).toFloat(), y.toFloat(), endCol)
            g2.fillRoundRect(x + 1, y + 1, fillW, (h - 2).coerceAtLeast(2), arc, arc)
            g2.color = Color(255, 255, 255, 70)
            g2.drawLine(x + 2, y + 2, x + fillW - 1, y + 2)
        }

        // Brass sight-tube tick marks & bezel
        g2.color = Color(0xB8, 0x86, 0x0B)
        g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc)
        val ticks = 5
        for (i in 1 until ticks) {
            val tx = x + (w * i) / ticks
            g2.drawLine(tx, y + 1, tx, y + h - 2)
        }
    }

    private fun paintStripedCandy(
        g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, fraction: Float, cfg: SkinWidgetsConfig, isDark: Boolean
    ) {
        val arc = SkinStyleResolver.resolveProgressBarArc(SkinRegion.PROGRESS_MODAL, h)
        val (track, startCol, endCol) = resolveColors(
            cfg, isDark, Color(0x2B, 0x1B, 0x3D), Color(0xFF, 0x5C, 0xA8), Color(0xA8, 0x55, 0xF7)
        )
        g2.color = track
        g2.fillRoundRect(x, y, w, h, arc, arc)

        val fillW = (w * fraction).toInt()
        if (fillW > 0) {
            val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), fillW.toFloat(), h.toFloat(), arc.toFloat(), arc.toFloat())
            val prevClip = g2.clip
            g2.clip = Area(shape)
            g2.paint = GradientPaint(x.toFloat(), y.toFloat(), startCol, (x + fillW).toFloat(), y.toFloat(), endCol)
            g2.fill(shape)
            g2.color = Color(255, 255, 255, 64)
            var sx = x - h
            while (sx < x + fillW) {
                val poly = Polygon(
                    intArrayOf(sx, sx + 5, sx + h + 5, sx + h),
                    intArrayOf(y + h, y + h, y, y),
                    4
                )
                g2.fillPolygon(poly)
                sx += 11
            }
            g2.clip = prevClip
        }
    }

    private fun paintGradientGlow(
        g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, fraction: Float, cfg: SkinWidgetsConfig, isDark: Boolean
    ) {
        val arc = SkinStyleResolver.resolveProgressBarArc(SkinRegion.PROGRESS_MODAL, h)
        val (track, startCol, endCol) = resolveColors(
            cfg, isDark, Color(0x14, 0x18, 0x26), Color(0x00, 0xE5, 0xFF), Color(0xFF, 0x2A, 0x85)
        )
        g2.color = track
        g2.fillRoundRect(x, y, w, h, arc, arc)

        val fillW = (w * fraction).toInt()
        if (fillW > 0) {
            g2.paint = GradientPaint(x.toFloat(), y.toFloat(), startCol, (x + fillW).toFloat(), y.toFloat(), endCol)
            g2.fillRoundRect(x, y, fillW, h, arc, arc)
            g2.color = Color(255, 255, 255, 95)
            val capX = (x + fillW - 3).coerceAtLeast(x)
            g2.fillRoundRect(capX, y, minOf(3, fillW), h, 2, 2)
        }
    }

    private fun interpolate(a: Color, b: Color, t: Float): Color = Color(
        (a.red + (b.red - a.red) * t).toInt().coerceIn(0, 255),
        (a.green + (b.green - a.green) * t).toInt().coerceIn(0, 255),
        (a.blue + (b.blue - a.blue) * t).toInt().coerceIn(0, 255)
    )
}

/**
 * Applies active skin's button bevel / metallic / neon / brass styling cleanly to a JButton.
 */
object SkinnedButtonStyler {
    fun apply(button: JButton, region: SkinRegion) {
        val widgets = SkinManager.currentSkin.widgetsConfig
        val style = widgets?.buttonBevelStyle?.lowercase() ?: "flat"
        val isDark = FlatLaf.isLafDark()
        val btnArc = SkinStyleResolver.resolveButtonArc(region)
        if (style == "flat") {
            button.border = null
            button.putClientProperty("FlatLaf.style", "arc: $btnArc")
            return
        }

        val surfCol = (if (isDark) widgets?.buttonSurfaceDark else widgets?.buttonSurfaceLight)
            ?.let { ThemeManager.parseHexColor(it) }
        val hlCol = (if (isDark) widgets?.buttonHighlightDark else widgets?.buttonHighlightLight)
            ?.let { ThemeManager.parseHexColor(it) }
        val shCol = (if (isDark) widgets?.buttonShadowDark else widgets?.buttonShadowLight)
            ?.let { ThemeManager.parseHexColor(it) }

        val fgOverride = SkinStyleResolver.resolveForeground(region, isDark)
        if (surfCol != null) {
            button.background = surfCol
            button.foreground = fgOverride ?: ThemeManager.contrastForeground(surfCol)
            button.isOpaque = true
        } else if (fgOverride != null) {
            button.foreground = fgOverride
        }
        button.border = BeveledSkinButtonBorder(style, hlCol, shCol, btnArc)
    }

    private class BeveledSkinButtonBorder(
        private val style: String,
        private val hl: Color?,
        private val sh: Color?,
        private val arc: Int
    ) : AbstractBorder() {
        override fun getBorderInsets(c: Component?): Insets = Insets(3.px, 6.px, 3.px, 6.px)
        override fun getBorderInsets(c: Component?, insets: Insets): Insets {
            insets.set(3.px, 6.px, 3.px, 6.px)
            return insets
        }

        private fun dimIfDisabled(c: Component?, color: Color): Color {
            if (c == null || c.isEnabled) return color
            return Color(color.red, color.green, color.blue, minOf(color.alpha, 72))
        }

        override fun paintBorder(c: Component?, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                when (style) {
                    "beveled_3d" -> {
                        g2.color = dimIfDisabled(c, hl ?: Color(0x8E, 0x96, 0xAB))
                        g2.drawLine(x, y, x + width - 2, y)
                        g2.drawLine(x, y, x, y + height - 2)
                        g2.color = dimIfDisabled(c, sh ?: Color(0x16, 0x18, 0x20))
                        g2.drawLine(x, y + height - 1, x + width - 1, y + height - 1)
                        g2.drawLine(x + width - 1, y, x + width - 1, y + height - 1)
                    }
                    "riveted_brass" -> {
                        val bCol = dimIfDisabled(c, hl ?: Color(0xD4, 0xAF, 0x37))
                        g2.color = bCol
                        g2.drawRoundRect(x, y, width - 1, height - 1, minOf(arc, 8), minOf(arc, 8))
                        if (c?.isEnabled != false) {
                            g2.fillRect(x + 2, y + 2, 2, 2)
                            g2.fillRect(x + width - 4, y + 2, 2, 2)
                        }
                    }
                    "neon_outline", "pill_glow" -> {
                        g2.color = dimIfDisabled(c, hl ?: AppTheme.accent)
                        g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
                    }
                }
            } finally {
                g2.dispose()
            }
        }
    }
}
