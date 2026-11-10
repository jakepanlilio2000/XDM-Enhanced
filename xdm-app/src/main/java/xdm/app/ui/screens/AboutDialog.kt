package xdm.app.ui.screens

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.util.UIScale
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.components.AppTheme
import xdm.app.update.UpdateChecker
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.SingleWindows
import xdm.app.utils.WEBSITE_URL
import xdm.app.utils.logoIcon
import xdm.app.utils.openInDefaultBrowser
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.shownWindow
import xdm.app.utils.windowIconImages
import xdm.torrent.TorrentNativeLoader
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Redesigned About dialog for XDM Enhanced.
 *
 * Displays application identity, version, release channel, project description,
 * legal attribution, upstream acknowledgment, system runtime diagnostics,
 * third-party notices, and repository actions.
 */
class AboutDialog constructor(owner: Window?) : JDialog(owner) {

    companion object {
        /** Opens About, or brings the open one forward. Owned by [parent]'s window while that is on screen. EDT only. */
        fun open(parent: Component?) = SingleWindows.show(AboutDialog::class) { AboutDialog(shownWindow(parent)) }

        const val GITHUB_REPO_URL = "https://github.com/jakepanlilio2000/XDM-Enhanced"
        const val UPSTREAM_REPO_URL = "https://github.com/subhra74/xdm"
        const val APP_DISPLAY_NAME = "XDM Enhanced"
    }

    init {
        title = "About XDM Enhanced"
        runCatching { iconImages = windowIconImages }
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isModal = false
        isResizable = false

        val rawVersion = UpdateChecker.loadConfig()?.currentVersion ?: "1.0.0-beta.5"
        val version = rawVersion.ifBlank { "1.0.0-beta.5" }
        val isPreRelease = version.contains("beta", ignoreCase = true) || version.contains("alpha", ignoreCase = true) || version.contains("rc", ignoreCase = true)
        val releaseChannel = if (isPreRelease) "Beta Channel" else "Stable Channel"

        val root = JPanel(BorderLayout(0, 10.px)).apply {
            border = ScaledEmptyBorder(16, 20, 16, 20)
        }

        // ==========================================
        // 1. TOP HEADER: Logo, Title, Version Badge
        // ==========================================
        val headerPanel = JPanel(BorderLayout(16.px, 0)).apply {
            isOpaque = false
        }

        val logoImg = try {
            logoIcon(60.px)
        } catch (_: Throwable) {
            null
        }
        val lblLogo = JLabel(logoImg).apply {
            verticalAlignment = SwingConstants.TOP
        }
        headerPanel.add(lblLogo, BorderLayout.WEST)

        val headerTextPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val lblTitle = JLabel(APP_DISPLAY_NAME).apply {
            font = font.deriveFont(Font.BOLD, 18f.px)
            foreground = UIManager.getColor("Label.foreground")
        }

        val versionBox = Box.createHorizontalBox().apply {
            alignmentX = Component.LEFT_ALIGNMENT
            val lblVer = JLabel("v$version").apply {
                font = font.deriveFont(Font.BOLD, 12f.px)
                foreground = UIManager.getColor("Label.foreground")
            }
            val lblChannel = JLabel(releaseChannel).apply {
                font = font.deriveFont(Font.PLAIN, 10.5f.px)
                foreground = AppTheme.accent
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppTheme.accent, 1, true),
                    ScaledEmptyBorder(1, 6, 1, 6)
                )
            }
            add(lblVer)
            add(Box.createHorizontalStrut(8.px))
            add(lblChannel)
            add(Box.createHorizontalGlue())
        }

        val lblTagline = JLabel("Next-generation download accelerator with native BitTorrent engine").apply {
            font = font.deriveFont(Font.PLAIN, 11f.px)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }

        headerTextPanel.add(lblTitle)
        headerTextPanel.add(Box.createVerticalStrut(3.px))
        headerTextPanel.add(versionBox)
        headerTextPanel.add(Box.createVerticalStrut(4.px))
        headerTextPanel.add(lblTagline)

        headerPanel.add(headerTextPanel, BorderLayout.CENTER)

        // ==========================================
        // 2. ACTION BUTTONS: Star, Source, Website
        // ==========================================
        val actionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8.px, 0)).apply {
            isOpaque = false
            border = ScaledEmptyBorder(8, 0, 4, 0)
        }

        val btnStar = JButton("Star on GitHub", FontIcon(RemixIcon.STAR_FILL, 15.px, Color(234, 179, 8))).apply {
            putClientProperty("JButton.buttonType", "roundRect")
            putClientProperty(FlatClientProperties.STYLE, "font: bold; arc: 8")
            toolTipText = GITHUB_REPO_URL
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                openSafeUrl(GITHUB_REPO_URL)
            }
        }

        val btnSource = JButton("Source Code", FontIcon(RemixIcon.GITHUB_FILL, 15.px)).apply {
            putClientProperty("JButton.buttonType", "roundRect")
            putClientProperty(FlatClientProperties.STYLE, "arc: 8")
            toolTipText = GITHUB_REPO_URL
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                openSafeUrl(GITHUB_REPO_URL)
            }
        }

        val btnWebsite = JButton("Upstream Website", FontIcon(RemixIcon.GLOBAL_LINE, 15.px)).apply {
            putClientProperty("JButton.buttonType", "roundRect")
            putClientProperty(FlatClientProperties.STYLE, "arc: 8")
            toolTipText = WEBSITE_URL
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                openSafeUrl(WEBSITE_URL)
            }
        }

        actionPanel.add(btnStar)
        actionPanel.add(btnSource)
        actionPanel.add(btnWebsite)

        val topCombined = JPanel(BorderLayout(0, 4.px)).apply {
            isOpaque = false
            add(headerPanel, BorderLayout.NORTH)
            add(actionPanel, BorderLayout.SOUTH)
        }
        root.add(topCombined, BorderLayout.NORTH)

        // ==========================================
        // 3. TABBED BODY: Overview, System, Credits
        // ==========================================
        val tabbedPane = JTabbedPane().apply {
            putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE, FlatClientProperties.TABBED_PANE_TAB_TYPE_UNDERLINED)
            putClientProperty(FlatClientProperties.TABBED_PANE_SHOW_TAB_SEPARATORS, true)
            font = font.deriveFont(Font.PLAIN, 12f.px)
        }

        // --- Tab 1: Overview & Legal ---
        val pnlOverview = createOverviewPanel()
        tabbedPane.addTab("Overview", pnlOverview)

        // --- Tab 2: System Diagnostics ---
        val pnlSystem = createSystemPanel(version, releaseChannel)
        tabbedPane.addTab("System Info", pnlSystem)

        // --- Tab 3: Third-Party Credits ---
        val pnlCredits = createCreditsPanel()
        tabbedPane.addTab("Notices & Credits", pnlCredits)

        root.add(tabbedPane, BorderLayout.CENTER)

        // ==========================================
        // 4. FOOTER: Copyright & Close Action
        // ==========================================
        val footerPanel = JPanel(BorderLayout(10.px, 0)).apply {
            isOpaque = false
            border = ScaledEmptyBorder(6, 0, 0, 0)
        }

        val lblCopyright = JLabel("© 2013–2026 Subhra Das Gupta, Jake Panlilio & Contributors").apply {
            font = font.deriveFont(Font.PLAIN, 10.5f.px)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }

        val btnClose = JButton("Close").apply {
            putClientProperty("JButton.buttonType", "roundRect")
            putClientProperty(FlatClientProperties.STYLE, "arc: 8")
            addActionListener { dispose() }
        }

        footerPanel.add(lblCopyright, BorderLayout.WEST)
        footerPanel.add(btnClose, BorderLayout.EAST)
        root.add(footerPanel, BorderLayout.SOUTH)

        contentPane.add(root)
        setSize(500.px, 480.px)
        setLocationRelativeTo(owner)

        // Keyboard Shortcuts: ESC and Enter close dialog
        rootPane.defaultButton = btnClose
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeAbout"
        )
        rootPane.actionMap.put("closeAbout", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                dispose()
            }
        })
    }

    internal fun openSafeUrl(url: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                openInDefaultBrowser(url)
            } else {
                fallbackCopyToClipboard(url)
            }
        } catch (_: Throwable) {
            fallbackCopyToClipboard(url)
        }
    }

    internal fun fallbackCopyToClipboard(url: String, showNotice: Boolean = true) {
        try {
            val clip = Toolkit.getDefaultToolkit().systemClipboard
            clip.setContents(StringSelection(url), null)
            if (showNotice && !GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(
                    this,
                    "Could not open browser. Link copied to clipboard:\n$url",
                    "Open Link",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } catch (_: Throwable) {}
    }

    private fun createOverviewPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(12, 12, 12, 12)
            isOpaque = false
        }

        fun createSectionTitle(title: String): JLabel = JLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 12f.px)
            foreground = AppTheme.accent
            alignmentX = Component.LEFT_ALIGNMENT
        }

        fun createBodyText(html: String): JLabel = JLabel("<html><body style='width: 380px;'>$html</body></html>").apply {
            font = font.deriveFont(Font.PLAIN, 11f.px)
            foreground = UIManager.getColor("Label.foreground")
            alignmentX = Component.LEFT_ALIGNMENT
        }

        panel.add(createSectionTitle("Project Description"))
        panel.add(Box.createVerticalStrut(4.px))
        panel.add(createBodyText("XDM Enhanced is a high-performance download accelerator and media stream capture tool featuring native BitTorrent support, intelligent categorization, segment visualization, and multi-threaded transfers."))
        panel.add(Box.createVerticalStrut(10.px))

        panel.add(createSectionTitle("Legal Attribution & Upstream Acknowledgment"))
        panel.add(Box.createVerticalStrut(4.px))
        panel.add(createBodyText("Based on the original <b>Xtreme Download Manager (XDM)</b> created by Subhra Das Gupta &amp; contributors.<br><br><b>Independent Maintenance:</b> XDM Enhanced is an independent community fork developed and maintained separately. It is not endorsed by, sponsored by, or affiliated with the original XDM project."))
        panel.add(Box.createVerticalStrut(10.px))

        panel.add(createSectionTitle("License Information"))
        panel.add(Box.createVerticalStrut(4.px))
        panel.add(createBodyText("Free and Open Source Software licensed under the <b>GNU General Public License v2.0 (GPL-2.0)</b>. You are free to run, copy, distribute, study, change, and improve this software."))

        return JScrollPane(panel).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
        }
    }

    private fun createSystemPanel(appVersion: String, releaseChannel: String): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = ScaledEmptyBorder(12, 12, 12, 12)
            isOpaque = false
        }

        val (libtorrentVer, libtorrent4jVer) = try {
            if (!TorrentNativeLoader.isLoaded) {
                TorrentNativeLoader.load()
            }
            Pair(TorrentNativeLoader.version, TorrentNativeLoader.LIBTORRENT4J_VERSION)
        } catch (_: Throwable) {
            Pair("2.x", TorrentNativeLoader.LIBTORRENT4J_VERSION)
        }

        val javaVer = System.getProperty("java.version") ?: "Unknown"
        val javaVendor = System.getProperty("java.vendor") ?: "Unknown"
        val vmName = System.getProperty("java.vm.name") ?: "JVM"
        val osName = "${System.getProperty("os.name")} (${System.getProperty("os.arch")})"
        val lafName = UIManager.getLookAndFeel()?.name ?: "FlatLaf"
        val uiScale = try {
            "${(UIScale.getUserScaleFactor() * 100).toInt()}%"
        } catch (_: Throwable) {
            "100%"
        }

        val infoItems = listOf(
            "Application Version" to "XDM Enhanced v$appVersion",
            "Release Channel" to releaseChannel,
            "Java Runtime" to "$javaVer ($javaVendor)",
            "Virtual Machine" to vmName,
            "Operating System" to osName,
            "BitTorrent Engine" to "libtorrent4j $libtorrent4jVer (libtorrent $libtorrentVer)",
            "User Interface" to "$lafName (Scale: $uiScale)"
        )

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = scaledInsets(3, 4, 3, 4)
        }

        var row = 0
        for ((label, value) in infoItems) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35
            val lblKey = JLabel(label).apply {
                font = font.deriveFont(Font.BOLD, 11f.px)
                foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            }
            panel.add(lblKey, gbc)

            gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.65
            val lblVal = JLabel(value).apply {
                font = font.deriveFont(Font.PLAIN, 11f.px)
                foreground = UIManager.getColor("Label.foreground")
            }
            panel.add(lblVal, gbc)
            row++
        }

        // Copy button
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        gbc.insets = scaledInsets(12, 4, 4, 4)
        val btnCopy = JButton("Copy System Diagnostics", FontIcon(RemixIcon.CLIPBOARD_LINE, 14.px)).apply {
            putClientProperty("JButton.buttonType", "roundRect")
            putClientProperty(FlatClientProperties.STYLE, "arc: 8")
            addActionListener {
                val markdown = buildString {
                    appendLine("### XDM Enhanced Environment")
                    for ((k, v) in infoItems) {
                        appendLine("- **$k**: $v")
                    }
                }
                try {
                    val clip = Toolkit.getDefaultToolkit().systemClipboard
                    clip.setContents(StringSelection(markdown), null)
                    text = "Copied to Clipboard!"
                    Timer(2000) { text = "Copy System Diagnostics" }.apply {
                        isRepeats = false
                        start()
                    }
                } catch (_: Throwable) {}
            }
        }
        panel.add(btnCopy, gbc)

        // Spacer to push content to top
        gbc.gridx = 0; gbc.gridy = row + 1; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        panel.add(Box.createGlue(), gbc)

        return JScrollPane(panel).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
        }
    }

    private fun createCreditsPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(12, 12, 12, 12)
            isOpaque = false
        }

        val credits = listOf(
            Triple("Original XDM", "Subhra Das Gupta & Contributors", "GPL-2.0 — Original download management architecture"),
            Triple("FlatLaf", "FormDev Software GmbH", "Apache-2.0 — Modern desktop Look and Feel framework"),
            Triple("libtorrent & libtorrent4j", "Arvid Norberg, Alden Torres", "BSD-3-Clause — High-performance C++ BitTorrent engine"),
            Triple("Remix Icon", "Remix Design", "Apache-2.0 — Open-source system icon design"),
            Triple("OkHttp & Kotlinx", "Square Inc., JetBrains", "Apache-2.0 — Fast HTTP networking and JSON engine")
        )

        for ((name, author, notice) in credits) {
            val box = Box.createVerticalBox().apply {
                alignmentX = Component.LEFT_ALIGNMENT
                val lblName = JLabel(name).apply {
                    font = font.deriveFont(Font.BOLD, 11.5f.px)
                    foreground = AppTheme.accent
                }
                val lblAuthor = JLabel("By $author").apply {
                    font = font.deriveFont(Font.PLAIN, 10.5f.px)
                    foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                }
                val lblNotice = JLabel(notice).apply {
                    font = font.deriveFont(Font.PLAIN, 10.5f.px)
                    foreground = UIManager.getColor("Label.foreground")
                }
                add(lblName)
                add(lblAuthor)
                add(lblNotice)
            }
            panel.add(box)
            panel.add(Box.createVerticalStrut(8.px))
        }

        return JScrollPane(panel).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
        }
    }
}
