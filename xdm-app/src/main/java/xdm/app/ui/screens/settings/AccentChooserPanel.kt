package xdm.app.ui.screens.settings

import xdm.app.I8N
import xdm.app.ui.components.AppTheme
import xdm.app.ui.components.ThemeManager
import xdm.app.utils.px
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * Visual accent color selector offering default, curated presets, custom color chooser, and reset.
 */
class AccentChooserPanel : JPanel() {
    private val group = ButtonGroup()
    private val buttons = mutableListOf<SwatchButton>()
    private val btnCustom = JButton(I8N.text("ACCENT_CUSTOM")).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        putClientProperty("JButton.buttonType", "roundRect")
        font = font.deriveFont(11f.px)
        toolTipText = I8N.text("ACCENT_CUSTOM")
        addActionListener { pickCustomColor() }
    }
    private val btnReset = JButton(I8N.text("ACCENT_RESET")).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        putClientProperty("JButton.buttonType", "roundRect")
        font = font.deriveFont(11f.px)
        toolTipText = I8N.text("ACCENT_RESET")
        addActionListener { selectAccent(null) }
    }

    var selectedColor: Color? = null
        private set

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT

        // Default swatch
        val defaultBtn = SwatchButton(
            title = I8N.text("ACCENT_DEFAULT"),
            colorProvider = { ThemeManager.defaultAccentColor },
            isDefault = true
        ) { selectAccent(null) }
        group.add(defaultBtn)
        buttons.add(defaultBtn)
        add(defaultBtn)
        add(Box.createRigidArea(Dimension(6.px, 0)))

        // Preset swatches
        ThemeManager.PRESETS.forEach { preset ->
            val btn = SwatchButton(
                title = "${preset.name} (${preset.hex})",
                colorProvider = { preset.color },
                presetHex = preset.hex
            ) { selectAccent(preset.color) }
            group.add(btn)
            buttons.add(btn)
            add(btn)
            add(Box.createRigidArea(Dimension(6.px, 0)))
        }

        add(Box.createRigidArea(Dimension(4.px, 0)))
        add(btnCustom)
        add(Box.createRigidArea(Dimension(4.px, 0)))
        add(btnReset)

        updateSelection(ThemeManager.currentAccent)
    }

    private fun pickCustomColor() {
        val initial = selectedColor ?: AppTheme.accent
        val chosen = JColorChooser.showDialog(this, I8N.text("MSG_ACCENT"), initial)
        if (chosen != null) {
            selectAccent(chosen)
        }
    }

    fun selectAccent(color: Color?) {
        selectedColor = color
        ThemeManager.setAccentColor(color)
        updateSelection(color)
    }

    fun updateSelection(color: Color?) {
        selectedColor = color
        val hex = color?.let { ThemeManager.colorToHex(it) }
        var matched = false
        for (btn in buttons) {
            if (color == null && btn.isDefault) {
                btn.isSelected = true
                matched = true
            } else if (hex != null && btn.presetHex.equals(hex, ignoreCase = true)) {
                btn.isSelected = true
                matched = true
            } else {
                btn.isSelected = false
            }
            btn.repaint()
        }
        if (!matched && color != null) {
            group.clearSelection()
            btnCustom.text = hex ?: I8N.text("ACCENT_CUSTOM")
        } else {
            btnCustom.text = I8N.text("ACCENT_CUSTOM")
        }
        repaint()
    }

    private class SwatchButton(
        val title: String,
        val colorProvider: () -> Color,
        val presetHex: String = "",
        val isDefault: Boolean = false,
        onClick: () -> Unit
    ) : JToggleButton() {
        init {
            toolTipText = title
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val s = 24.px
            preferredSize = Dimension(s, s)
            minimumSize = preferredSize
            maximumSize = preferredSize
            addActionListener { onClick() }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val d = width.coerceAtMost(height) - 4.px
            val x = (width - d) / 2
            val y = (height - d) / 2
            val fill = colorProvider()

            // Fill circle
            g2.color = fill
            g2.fillOval(x, y, d, d)

            // Outline / ring
            if (isSelected) {
                g2.color = if (ThemeManager.isDark) Color.WHITE else Color.BLACK
                g2.stroke = BasicStroke(2f.px)
                g2.drawOval(x, y, d, d)
                // Center pip
                val pip = 6.px
                g2.color = if (ThemeManager.isDark) Color.WHITE else Color.BLACK
                g2.fillOval((width - pip) / 2, (height - pip) / 2, pip, pip)
            } else if (model.isRollover) {
                g2.color = if (ThemeManager.isDark) Color(255, 255, 255, 140) else Color(0, 0, 0, 120)
                g2.stroke = BasicStroke(1.5f.px)
                g2.drawOval(x, y, d, d)
            } else {
                g2.color = Color(0, 0, 0, 40)
                g2.stroke = BasicStroke(1f.px)
                g2.drawOval(x, y, d, d)
            }
            g2.dispose()
        }
    }
}

