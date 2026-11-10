package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.I8N
import xdm.app.ui.screens.settings.rounded
import xdm.app.ui.screens.settings.settingsAccentColor
import xdm.app.ui.screens.settings.settingsIconButton
import xdm.app.ui.screens.settings.settingsMutedColor
import xdm.app.ui.screens.settings.settingsStroke
import xdm.app.utils.BrandFontIcon
import xdm.app.utils.BrandIcon
import xdm.app.utils.Browser
import xdm.app.utils.BrowserLauncher
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.GITHUB_RELEASES_URL
import xdm.app.utils.GITHUB_REPO_URL
import xdm.app.utils.createIcon
import xdm.app.utils.px
import xdm.app.utils.scaledSize
import xdm.integration.BrowserIntegration
import xdm.integration.BrowserIntegrationRepair
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.concurrent.thread

/**
 * Modernized compact, clickable browser tiles for desktop settings and setup dialog.
 * Displays installed/supported browsers with live integration status, nonblocking checks,
 * full keyboard navigation, and theme-aware styling.
 */
class BrowserExtensionPanel : JPanel() {

    private val hint = JLabel().apply {
        alignmentX = LEFT_ALIGNMENT
        foreground = settingsMutedColor()
        border = ScaledEmptyBorder(0, 2, 4, 0)
    }
    private val link = LinkRow(fitText = false)

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        // 4 primary browser tiles in a compact responsive row
        val tilesPanel = JPanel(GridLayout(1, 4, 10.px, 0)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            add(BrowserTile(Browser.CHROME, BrandIcon.CHROME, Color(0x4285F4), extensionPageUrl("chrome")))
            add(BrowserTile(Browser.FIREFOX, BrandIcon.FIREFOX_BROWSER, Color(0xFF7139), extensionPageUrl("firefox")))
            add(BrowserTile(Browser.EDGE, BrandIcon.EDGE, Color(0x24B0C4), extensionPageUrl("edge")))
            add(BrowserTile(Browser.BRAVE, BrandIcon.BRAVE, Color(0xFB542B), extensionPageUrl("brave")))
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        add(tilesPanel)

        val picker = rounded(JComboBox(OTHER_BROWSERS.toTypedArray())).apply {
            maximumSize = preferredSize
            addActionListener { (selectedItem as? OtherBrowser)?.let { show(it) } }
        }
        add(Box.createRigidArea(scaledSize(0, 10)))
        add(Box.createHorizontalBox().apply {
            alignmentX = LEFT_ALIGNMENT
            add(JLabel(I8N.text("BROWSER_EXT_OTHER")).apply { border = ScaledEmptyBorder(0, 2, 0, 0) })
            add(Box.createRigidArea(scaledSize(10, 0)))
            add(Box.createHorizontalGlue())
            add(picker)
        })
        add(Box.createRigidArea(scaledSize(0, 8)))
        add(hint)
        add(link)

        val guideLink = JLabel(I8N.text("BTN_VIEW_TROUBLESHOOTING") ?: "Troubleshooting Guide →").apply {
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = settingsAccentColor()
            font = font.deriveFont(11.5f.px)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = BrowserIntegrationRepair.TROUBLESHOOTING_URL
            border = ScaledEmptyBorder(6, 2, 0, 0)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        BrowserLauncher.launch(BrowserIntegrationRepair.TROUBLESHOOTING_URL)
                    }
                }
            })
        }
        add(guideLink)
        show(OTHER_BROWSERS.first())
    }

    private fun show(browser: OtherBrowser) {
        hint.text = String.format(I8N.text("BROWSER_EXT_OPEN_IN"), browser.toString())
        link.url = extensionPageUrl(if (browser.firefoxBased) "firefox" else "chromium")
    }

    private class OtherBrowser(private val name: String?, val firefoxBased: Boolean) {
        override fun toString() = name
            ?: I8N.text(if (firefoxBased) "BROWSER_EXT_OTHER_FIREFOX_BASED" else "BROWSER_EXT_OTHER_CHROMIUM_BASED")
    }

    /**
     * Compact clickable browser tile with status indicator, action label, and keyboard support.
     */
    class BrowserTile(
        val browser: Browser,
        icon: BrandIcon,
        accent: Color,
        val url: String,
    ) : JPanel(BorderLayout(0, 6.px)) {

        private var hovered = false
        var isInstalled: Boolean = false
            private set
        var isConnected: Boolean = false
            private set

        private val statusLabel = JLabel("Checking…", SwingConstants.CENTER).apply {
            font = font.deriveFont(11f.px)
            foreground = settingsMutedColor()
        }

        private val actionLabel = JLabel("Install", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 11f.px)
            foreground = settingsAccentColor()
        }

        init {
            isOpaque = false
            isFocusable = true
            border = ScaledEmptyBorder(10, 8, 10, 8)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "${browser.displayName} - ${I8N.text("BROWSER_EXT_INSTALL")}"

            val header = JPanel(BorderLayout(0, 4.px)).apply {
                isOpaque = false
                add(JLabel(BrandFontIcon(icon, ICON_SIZE.px, accent)).apply {
                    horizontalAlignment = SwingConstants.CENTER
                }, BorderLayout.CENTER)
                add(JLabel(browser.displayName).apply {
                    horizontalAlignment = SwingConstants.CENTER
                    font = font.deriveFont(Font.BOLD, 12.5f.px)
                }, BorderLayout.SOUTH)
            }

            val body = JPanel(BorderLayout(0, 2.px)).apply {
                isOpaque = false
                add(statusLabel, BorderLayout.NORTH)
                add(actionLabel, BorderLayout.SOUTH)
            }

            add(header, BorderLayout.CENTER)
            add(body, BorderLayout.SOUTH)

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) = setHovered(true)
                override fun mouseExited(e: MouseEvent) = setHovered(false)
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) launch()
                }
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_SPACE || e.keyCode == KeyEvent.VK_ENTER) {
                        e.consume()
                        launch()
                    }
                }
            })
            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) = repaint()
                override fun focusLost(e: FocusEvent) = repaint()
            })

            // Nonblocking background detection
            checkStatusAsync()
        }

        fun checkStatusAsync() {
            thread(isDaemon = true, name = "browser-check-${browser.name}") {
                val installed = BrowserLauncher.isInstalled(browser)
                val connected = BrowserIntegration.isConnected(browser)
                SwingUtilities.invokeLater {
                    updateStatus(installed, connected)
                }
            }
        }

        fun updateStatus(installed: Boolean, connected: Boolean) {
            isInstalled = installed
            isConnected = connected

            when {
                connected -> {
                    statusLabel.text = "● Active"
                    statusLabel.foreground = Color(0x22, 0xc5, 0x5e)
                    actionLabel.text = "Extension Active"
                    actionLabel.foreground = Color(0x16, 0xa3, 0x4a)
                    toolTipText = "${browser.displayName}: Active & connected"
                }
                installed -> {
                    statusLabel.text = "● Installed"
                    statusLabel.foreground = settingsAccentColor()
                    actionLabel.text = "Install Extension →"
                    actionLabel.foreground = settingsAccentColor()
                    toolTipText = "${browser.displayName}: Detected on system. Click to install extension"
                }
                else -> {
                    statusLabel.text = "Not Detected"
                    statusLabel.foreground = settingsMutedColor()
                    actionLabel.text = "Get Extension"
                    actionLabel.foreground = settingsMutedColor()
                    toolTipText = "${browser.displayName}: Not found in system paths. Click for manual setup"
                }
            }
            repaint()
        }

        private fun setHovered(value: Boolean) {
            hovered = value
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = 12f.px

            // Card background: subtle filled container
            if (hovered) {
                g2.color = settingsStroke()
                g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc))
            } else {
                g2.color = Color(128, 128, 128, 20)
                g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc))
            }

            // Outer border
            g2.color = if (isConnected) Color(0x22, 0xc5, 0x5e, 160) else settingsStroke()
            g2.stroke = BasicStroke(1f.px)
            g2.draw(RoundRectangle2D.Float(0.5f.px, 0.5f.px, width - 1f.px, height - 1f.px, arc, arc))

            if (isFocusOwner) {
                g2.color = settingsAccentColor()
                g2.stroke = BasicStroke(1.5f.px)
                val inset = 1f.px
                g2.draw(
                    RoundRectangle2D.Float(
                        inset, inset, width - 2 * inset, height - 2 * inset, arc, arc
                    )
                )
            }
            g2.dispose()
            super.paintComponent(g)
        }

        fun launch() {
            val owner = SwingUtilities.getWindowAncestor(this)
            thread(isDaemon = true, name = "browser-launch") {
                if (!BrowserLauncher.launch(browser, url)) {
                    SwingUtilities.invokeLater { showNotInstalled(owner, browser, url) }
                }
            }
        }
    }

    companion object {
        private const val ICON_SIZE = 32

        private val OTHER_BROWSERS = listOf(
            OtherBrowser("Arc", false),
            OtherBrowser("Zen", true),
            OtherBrowser("Helium", false),
            OtherBrowser("Opera", false),
            OtherBrowser("Vivaldi", false),
            OtherBrowser("Floorp", true),
            OtherBrowser("LibreWolf", true),
            OtherBrowser("Waterfox", true),
            OtherBrowser(null, false),
            OtherBrowser(null, true),
        )

        fun extensionPageUrl(browser: String): String = when (browser.lowercase()) {
            "firefox" -> GITHUB_RELEASES_URL
            "edge" -> GITHUB_RELEASES_URL
            "chrome", "chromium", "brave" -> GITHUB_RELEASES_URL
            else -> "$GITHUB_REPO_URL#browser-integration"
        }
    }
}

/**
 * Read-only [url] field with a copy button beside it.
 */
private class LinkRow(fitText: Boolean = true) : JPanel(BorderLayout(6.px, 0)) {
    private val urlField = JTextField().apply {
        isEditable = false
        if (!fitText) columns = 1
        putClientProperty(
            FlatClientProperties.STYLE,
            "arc: 12; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; innerOutlineWidth: 0"
        )
    }

    var url: String
        get() = urlField.text
        set(value) {
            urlField.text = value
            urlField.caretPosition = 0
        }

    init {
        val copyIcon = createIcon(RemixIcon.FILE_COPY_LINE, 17, settingsMutedColor())
        val copiedIcon = createIcon(RemixIcon.CHECKBOX_CIRCLE_LINE, 17, settingsAccentColor())
        lateinit var revert: Timer
        val copy = settingsIconButton(RemixIcon.FILE_COPY_LINE, I8N.text("BROWSER_EXT_COPY")) {}
        copy.addActionListener {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
            urlField.selectAll()
            copy.icon = copiedIcon
            copy.toolTipText = I8N.text("BROWSER_EXT_COPIED")
            revert.restart()
        }
        revert = Timer(1500) {
            copy.icon = copyIcon
            copy.toolTipText = I8N.text("BROWSER_EXT_COPY")
        }.apply { isRepeats = false }

        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(urlField, BorderLayout.CENTER)
        add(copy, BorderLayout.EAST)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Shown when a tile's browser cannot be found: says so, and offers its install page link to copy. */
private fun showNotInstalled(owner: Window?, browser: Browser, url: String) {
    MessageBox.show(
        owner,
        String.format(I8N.text("BROWSER_NOT_INSTALLED_TITLE"), browser.displayName),
        arrayOf(
            JLabel(String.format(I8N.text("BROWSER_NOT_INSTALLED"), browser.displayName)),
            Box.createRigidArea(scaledSize(0, 4)),
            LinkRow().apply { this.url = url },
        ),
    )
}
