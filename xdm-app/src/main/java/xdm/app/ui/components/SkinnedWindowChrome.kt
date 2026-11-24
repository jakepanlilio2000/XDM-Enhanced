package xdm.app.ui.components

import com.formdev.flatlaf.FlatLaf
import xdm.app.AppContext
import xdm.app.skins.Skin
import xdm.app.skins.SkinManager
import xdm.app.skins.SkinWindowConfig
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.createIcon
import xdm.app.utils.logoIcon
import xdm.app.utils.px
import xdm.core.util.FormatHelper
import xdm.manager.RecordStatus
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.AbstractBorder

/**
 * Custom skinnable window header bar supporting gradients, textures, LCD/LED stats ticker,
 * draggable repositioning, custom control buttons, and compact WindowShade collapse mode.
 */
class SkinnedTitleBar(
    private val frame: JFrame,
    private val onToggleShade: (Boolean) -> Unit
) : JPanel(BorderLayout()) {

    var isShaded: Boolean = false
        private set

    private val lblIcon = JLabel().apply {
        border = ScaledEmptyBorder(0, 6, 0, 6)
    }
    private val lblTitle = JLabel(frame.title).apply {
        font = font.deriveFont(Font.BOLD, 12f.px)
    }
    private val tickerPanel = LcdStatsTicker()
    private val controlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5.px, 0)).apply {
        isOpaque = false
    }

    private val btnShade = ChromeControlButton("shade") { toggleShade() }
    private val btnMin = ChromeControlButton("minimize") {
        frame.extendedState = frame.extendedState or Frame.ICONIFIED
    }
    private val btnMax = ChromeControlButton("maximize") {
        val maximized = (frame.extendedState and Frame.MAXIMIZED_BOTH) != 0
        frame.extendedState = if (maximized) Frame.NORMAL else (frame.extendedState or Frame.MAXIMIZED_BOTH)
    }
    private val btnClose = ChromeControlButton("close") {
        frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
    }

    private var dragOffset: Point? = null
    private val statsTimer = Timer(500) { updateStats() }

    init {
        isOpaque = true
        border = ScaledEmptyBorder(2, 6, 2, 6)

        val leftWrap = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(lblIcon, BorderLayout.WEST)
            add(lblTitle, BorderLayout.CENTER)
        }

        val rightWrap = JPanel(FlowLayout(FlowLayout.RIGHT, 8.px, 0)).apply {
            isOpaque = false
            add(tickerPanel)
            add(controlsPanel)
        }

        controlsPanel.add(btnMin)
        controlsPanel.add(btnShade)
        controlsPanel.add(btnMax)
        controlsPanel.add(btnClose)

        add(leftWrap, BorderLayout.CENTER)
        add(rightWrap, BorderLayout.EAST)

        val dragAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragOffset = e.locationOnScreen.let { pt ->
                        Point(pt.x - frame.x, pt.y - frame.y)
                    }
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val off = dragOffset ?: return
                if ((frame.extendedState and Frame.MAXIMIZED_BOTH) == 0) {
                    val p = e.locationOnScreen
                    frame.setLocation(p.x - off.x, p.y - off.y)
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    val cfg = SkinManager.currentSkin.windowConfig
                    if (cfg?.showWindowShadeButton != false) {
                        toggleShade()
                    } else {
                        btnMax.doClick()
                    }
                }
            }
        }
        addMouseListener(dragAdapter)
        addMouseMotionListener(dragAdapter)
        lblTitle.addMouseListener(dragAdapter)
        lblTitle.addMouseMotionListener(dragAdapter)

        updateFromSkin()
    }

    fun toggleShade() {
        isShaded = !isShaded
        btnShade.repaint()
        onToggleShade(isShaded)
    }

    fun setShadedState(shaded: Boolean) {
        if (isShaded != shaded) {
            isShaded = shaded
            btnShade.repaint()
        }
    }

    fun startStatsTicker() {
        if (!statsTimer.isRunning) statsTimer.start()
        updateStats()
    }

    fun stopStatsTicker() {
        statsTimer.stop()
    }

    fun updateStats() {
        val cfg = SkinManager.currentSkin.windowConfig ?: return
        if (!cfg.showStatsTicker) return
        val records = AppContext.db.filter { true }
        var active = 0
        var speed = 0f
        for (r in records) {
            if (r.status == RecordStatus.DOWNLOADING || r.status == RecordStatus.READY || r.status == RecordStatus.ASSEMBLING) {
                active++
                if (r.status == RecordStatus.DOWNLOADING) speed += r.speed
            }
        }
        val speedStr = if (speed > 0f) "${FormatHelper.formatSize(speed.toDouble())}/s" else "0 B/s"
        tickerPanel.text = if (active > 0) {
            "▶ $speedStr  •  $active ACT  •  ${records.size} TOT"
        } else {
            "■ IDLE  •  ${records.size} FILES"
        }
        tickerPanel.repaint()
    }

    fun updateFromSkin() {
        val skin = SkinManager.currentSkin
        val cfg = skin.windowConfig
        if (cfg == null || !cfg.customTitleBar) {
            isVisible = false
            stopStatsTicker()
            return
        }
        isVisible = true
        val isDark = FlatLaf.isLafDark()
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), cfg.titleBarHeight.px)
        minimumSize = Dimension(240.px, cfg.titleBarHeight.px)

        val titleStr = cfg.titleText?.takeIf { it.isNotBlank() } ?: frame.title
        lblTitle.text = titleStr
        lblTitle.horizontalAlignment = if (cfg.titleAlignment.equals("center", ignoreCase = true)) {
            SwingConstants.CENTER
        } else {
            SwingConstants.LEFT
        }
        val fgHex = if (isDark) cfg.titleBarForegroundDark else cfg.titleBarForegroundLight
        lblTitle.foreground = fgHex?.let { ThemeManager.parseHexColor(it) }
            ?: if (isDark) Color(0xF1, 0xF5, 0xF9) else Color(0x11, 0x18, 0x27)
        val monoFont = cfg.controlButtonsStyle.equals("beveled", ignoreCase = true)
        lblTitle.font = Font(
            if (monoFont) Font.MONOSPACED else UIManager.getFont("Label.font")?.family ?: Font.SANS_SERIF,
            Font.BOLD,
            12
        ).deriveFont((if (cfg.titleBarHeight < 28) 11f else 12.5f).px)

        if (cfg.showAppIcon) {
            val ico = runCatching { logoIcon(16.px) }.getOrNull()
                ?: createIcon(RemixIcon.DOWNLOAD_2_LINE, 16, AppTheme.accent)
            lblIcon.icon = ico
            lblIcon.isVisible = true
        } else {
            lblIcon.isVisible = false
        }

        btnShade.isVisible = cfg.showWindowShadeButton
        tickerPanel.isVisible = cfg.showStatsTicker
        if (cfg.showStatsTicker) {
            startStatsTicker()
        } else {
            stopStatsTicker()
        }

        val btnSize = Dimension(18.px, 16.px)
        listOf(btnMin, btnShade, btnMax, btnClose).forEach { btn ->
            btn.preferredSize = btnSize
            btn.minimumSize = btnSize
            btn.maximumSize = btnSize
            btn.customIcon = skin.getWindowControlIcon(btn.kind, 12.px)
            btn.styleName = cfg.controlButtonsStyle
            btn.repaint()
        }
        revalidate()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val skin = SkinManager.currentSkin
        val cfg = skin.windowConfig ?: return super.paintComponent(g)
        val isDark = FlatLaf.isLafDark()
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val startHex = if (isDark) cfg.titleBarGradientStartDark else cfg.titleBarGradientStartLight
            val endHex = if (isDark) cfg.titleBarGradientEndDark else cfg.titleBarGradientEndLight
            val solidHex = if (isDark) cfg.titleBarBackgroundDark else cfg.titleBarBackgroundLight
            val startCol = startHex?.let { ThemeManager.parseHexColor(it) }
            val endCol = endHex?.let { ThemeManager.parseHexColor(it) }
            val solidCol = solidHex?.let { ThemeManager.parseHexColor(it) }
                ?: AppTheme.sidebarBackground

            if (startCol != null && endCol != null) {
                g2.paint = GradientPaint(0f, 0f, startCol, width.toFloat(), 0f, endCol)
                g2.fillRect(0, 0, width, height)
            } else {
                g2.color = solidCol
                g2.fillRect(0, 0, width, height)
            }

            // Optional title bar texture stripe overlay
            cfg.titleBarTexture?.let { tex ->
                skin.paintWallpaperConfigSurface(g2, width, height, tex, isDark, "titlebar")
            }

            // Decorative inner bevel highlight & bottom divider
            val hlHex = if (isDark) cfg.frameHighlightDark else cfg.frameHighlightLight
            val shHex = if (isDark) cfg.frameShadowDark else cfg.frameShadowLight
            val hlCol = hlHex?.let { ThemeManager.parseHexColor(it) } ?: Color(255, 255, 255, 28)
            val shCol = shHex?.let { ThemeManager.parseHexColor(it) } ?: Color(0, 0, 0, 90)
            g2.color = hlCol
            g2.drawLine(0, 0, width - 1, 0)
            g2.color = shCol
            g2.drawLine(0, height - 1, width - 1, height - 1)
        } finally {
            g2.dispose()
        }
    }

    private class LcdStatsTicker : JComponent() {
        var text: String = "■ IDLE  •  0 FILES"

        init {
            isOpaque = false
            preferredSize = Dimension(190.px, 20.px)
            minimumSize = Dimension(150.px, 20.px)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                val style = SkinManager.currentSkin.windowConfig?.controlButtonsStyle ?: "beveled"
                val isDark = FlatLaf.isLafDark()
                val (bg, borderCol, ledCol) = when {
                    style.equals("steampunk", true) -> Triple(
                        Color(0x1E, 0x13, 0x09), Color(0x8C, 0x62, 0x29), Color(0xFF, 0xC8, 0x57)
                    )
                    style.equals("anime", true) -> Triple(
                        Color(0x2D, 0x16, 0x36), Color(0xFF, 0x8C, 0xC6), Color(0xFF, 0xB7, 0xE2)
                    )
                    !isDark && style.equals("minimal", true) -> Triple(
                        Color(0xE8, 0xED, 0xF5), Color(0xC0, 0xCC, 0xDC), Color(0x0F, 0x4C, 0x81)
                    )
                    style.equals("beveled", true) -> Triple(
                        Color(0x0A, 0x14, 0x0B), Color(0x3D, 0x44, 0x54), Color(0x28, 0xFF, 0x64)
                    )
                    else -> Triple(
                        Color(0x0D, 0x14, 0x22), AppTheme.sidebarBorder, AppTheme.accent
                    )
                }

                g2.color = bg
                g2.fillRoundRect(0, 1, width - 1, height - 3, 4.px, 4.px)
                g2.color = borderCol
                g2.drawRoundRect(0, 1, width - 2, height - 3, 4.px, 4.px)

                g2.color = ledCol
                g2.font = Font(Font.MONOSPACED, Font.BOLD, 10).deriveFont(10f.px)
                val fm = g2.fontMetrics
                val ty = (height + fm.ascent - fm.descent) / 2
                g2.drawString(text, 7.px, ty)
            } finally {
                g2.dispose()
            }
        }
    }

    private inner class ChromeControlButton(
        val kind: String,
        val onClick: () -> Unit
    ) : JButton() {
        var styleName: String = "mac"
        var customIcon: Icon? = null
        private var hovered = false

        init {
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = when (kind) {
                "minimize" -> "Minimize"
                "maximize" -> "Maximize / Restore"
                "shade" -> "Toggle WindowShade compact mode"
                "close" -> "Close"
                else -> kind
            }
            addActionListener { onClick() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    repaint()
                }
                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    repaint()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            customIcon?.let { ico ->
                val ix = (width - ico.iconWidth) / 2
                val iy = (height - ico.iconHeight) / 2
                ico.paintIcon(this, g, ix, iy)
                return
            }

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val cx = width / 2f
                val cy = height / 2f

                when (styleName.lowercase()) {
                    "beveled" -> {
                        val face = if (hovered) Color(0x68, 0x6F, 0x86) else Color(0x47, 0x4D, 0x60)
                        g2.color = face
                        g2.fillRect(1, 1, width - 2, height - 2)
                        g2.color = Color(0xB8, 0xBF, 0xD0)
                        g2.drawLine(1, 1, width - 2, 1)
                        g2.drawLine(1, 1, 1, height - 2)
                        g2.color = Color(0x1C, 0x1E, 0x26)
                        g2.drawLine(width - 2, 1, width - 2, height - 2)
                        g2.drawLine(1, height - 2, width - 2, height - 2)
                        g2.color = Color(0xF0, 0xF4, 0xFF)
                        drawGlyph(g2, width, height)
                    }
                    "steampunk" -> {
                        val base = if (hovered) Color(0xD4, 0xA2, 0x45) else Color(0xA6, 0x79, 0x2A)
                        g2.color = base
                        g2.fill(Ellipse2D.Float(2f, 1f, width - 4f, height - 2f))
                        g2.color = Color(0x4A, 0x30, 0x12)
                        g2.draw(Ellipse2D.Float(2f, 1f, width - 5f, height - 3f))
                        g2.color = Color(0x2B, 0x18, 0x06)
                        drawGlyph(g2, width, height)
                    }
                    "anime" -> {
                        val fill = when (kind) {
                            "close" -> Color(0xFF, 0x5C, 0x97)
                            "shade" -> Color(0xC0, 0x84, 0xFC)
                            "maximize" -> Color(0x60, 0xA5, 0xFA)
                            else -> Color(0xFB, 0xBF, 0x24)
                        }
                        g2.color = if (hovered) fill.brighter() else fill
                        g2.fill(RoundRectangle2D.Float(1f, 1f, width - 2f, height - 2f, 8f, 8f))
                        g2.color = Color.WHITE
                        drawGlyph(g2, width, height)
                    }
                    "mac" -> {
                        val dotCol = when (kind) {
                            "close" -> Color(0xFF, 0x5F, 0x56)
                            "minimize" -> Color(0xFF, 0xBD, 0x2E)
                            "maximize" -> Color(0x27, 0xC9, 0x3F)
                            else -> Color(0x38, 0xBD, 0xF8)
                        }
                        val d = minOf(width, height) - 4f
                        g2.color = if (hovered) dotCol.brighter() else dotCol
                        g2.fill(Ellipse2D.Float(cx - d / 2f, cy - d / 2f, d, d))
                        if (hovered) {
                            g2.color = Color(0x22, 0x22, 0x22)
                            drawGlyph(g2, width, height)
                        }
                    }
                    else -> { // minimal
                        if (hovered) {
                            g2.color = Color(255, 255, 255, 36)
                            g2.fillRoundRect(1, 1, width - 2, height - 2, 6, 6)
                        }
                        g2.color = if (FlatLaf.isLafDark()) Color(0xE2, 0xE8, 0xF0) else Color(0x1E, 0x29, 0x3B)
                        drawGlyph(g2, width, height)
                    }
                }
            } finally {
                g2.dispose()
            }
        }

        private fun drawGlyph(g2: Graphics2D, w: Int, h: Int) {
            val mx = w / 2
            val my = h / 2
            g2.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            when (kind) {
                "close" -> {
                    g2.drawLine(mx - 3, my - 3, mx + 3, my + 3)
                    g2.drawLine(mx + 3, my - 3, mx - 3, my + 3)
                }
                "minimize" -> {
                    g2.drawLine(mx - 3, my + 2, mx + 3, my + 2)
                }
                "maximize" -> {
                    g2.drawRect(mx - 3, my - 3, 6, 6)
                }
                "shade" -> {
                    g2.drawLine(mx - 4, my - 2, mx + 4, my - 2)
                    g2.drawLine(mx - 4, my + 2, mx + 4, my + 2)
                    if (!isShaded) g2.drawLine(mx - 2, my, mx + 2, my)
                }
            }
        }
    }
}

/**
 * Multi-bevel metallic / brass outer frame border around the main contentPane.
 */
class SkinnedFrameBorder : AbstractBorder() {
    override fun getBorderInsets(c: Component?): Insets {
        val bw = resolveWidth()
        return Insets(bw, bw, bw, bw)
    }

    override fun getBorderInsets(c: Component?, insets: Insets): Insets {
        val bw = resolveWidth()
        insets.set(bw, bw, bw, bw)
        return insets
    }

    private fun resolveWidth(): Int {
        val cfg = SkinManager.currentSkin.windowConfig ?: return 0
        return cfg.frameBorderWidth.coerceIn(0, 12).px
    }

    override fun paintBorder(c: Component?, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val cfg = SkinManager.currentSkin.windowConfig ?: return
        val bw = resolveWidth()
        if (bw <= 0 || width <= 0 || height <= 0) return

        val isDark = FlatLaf.isLafDark()
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val baseCol = (if (isDark) cfg.frameBorderDark else cfg.frameBorderLight)
                ?.let { ThemeManager.parseHexColor(it) }
                ?: AppTheme.sidebarBorder
            val hlCol = (if (isDark) cfg.frameHighlightDark else cfg.frameHighlightLight)
                ?.let { ThemeManager.parseHexColor(it) }
                ?: baseCol.brighter()
            val shCol = (if (isDark) cfg.frameShadowDark else cfg.frameShadowLight)
                ?.let { ThemeManager.parseHexColor(it) }
                ?: baseCol.darker()

            // Chassis band fill
            g2.color = baseCol
            g2.fillRect(x, y, width, bw)
            g2.fillRect(x, y + height - bw, width, bw)
            g2.fillRect(x, y, bw, height)
            g2.fillRect(x + width - bw, y, bw, height)

            // Raised outer highlight & bottom-right bevel shadow
            g2.color = hlCol
            g2.drawLine(x, y, x + width - 1, y)
            g2.drawLine(x, y, x, y + height - 1)
            g2.color = shCol
            g2.drawLine(x, y + height - 1, x + width - 1, y + height - 1)
            g2.drawLine(x + width - 1, y, x + width - 1, y + height - 1)

            // Decorative rivets in corners for Beveled / Steampunk metallic chassis
            if (bw >= 4 && (cfg.controlButtonsStyle.equals("beveled", true) || cfg.controlButtonsStyle.equals("steampunk", true))) {
                val r = maxOf(2, bw / 2)
                g2.color = hlCol
                val pts = listOf(
                    Point(x + bw / 2, y + bw / 2),
                    Point(x + width - bw / 2 - 1, y + bw / 2),
                    Point(x + bw / 2, y + height - bw / 2 - 1),
                    Point(x + width - bw / 2 - 1, y + height - bw / 2 - 1)
                )
                for (p in pts) {
                    g2.fillOval(p.x - r / 2, p.y - r / 2, r, r)
                }
            }
        } finally {
            g2.dispose()
        }
    }
}

/**
 * Configures undecorated frame state prior to display when a skin requests [SkinWindowConfig.hideNativeTitleBar].
 */
object SkinnedFrameChromeHelper {
    fun configurePreDisplay(frame: JFrame, skin: Skin = SkinManager.currentSkin) {
        val cfg = skin.windowConfig ?: return
        if (cfg.customTitleBar && cfg.hideNativeTitleBar && !frame.isDisplayable) {
            runCatching { frame.isUndecorated = true }
        }
    }
}
