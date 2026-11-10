package xdm.app.ui.screens.settings

import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.ui.components.BrowserExtensionPanel
import xdm.app.ui.components.MessageBox
import xdm.app.utils.BrowserLauncher
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.scaledSize
import xdm.integration.BrowserIntegrationRepair
import java.awt.Dimension
import java.awt.Insets
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** What the browser extension hands over: which file types are captured, and from which sites. */
class BrowserMonitorPanel : SettingsPanel() {
    private val txtFileExt = extensionArea()
    private val txtVidExt = extensionArea()
    private val txtBlockedHosts = extensionArea()
    private val cmbMinVidSize = rounded(JComboBox<Long>()).apply {
        listOf(1L, 5L, 10L, 50L, AppConfig.MAX_MIN_VIDEO_SIZE_MB).forEach { addItem(it) }
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }
    private val tglGetServerTime = SettingsToggle()
    private val tglDetectSubtitles = SettingsToggle()
    private val tglMonitorClipboard = SettingsToggle()
    private val lblDiagnosticsStatus = JLabel("Port 8597: Checking… | Protocol: Checking…").apply {
        foreground = settingsMutedColor()
        alignmentX = LEFT_ALIGNMENT
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        add(settingsTitle(I8N.text("SETTINGS_MONITORING")))

        add(
            settingsSection(
                I8N.text("SETTINGS_SEC_CLIPBOARD") ?: "Clipboard Monitoring",
                settingsRow(
                    I8N.text("LBL_MONITOR_CLIPBOARD") ?: "Monitor copied download links",
                    I8N.text("LBL_MONITOR_CLIPBOARD_SUB")
                        ?: "Automatically detect copied download links and prepare the download modal. Unrelated clipboard text is never stored or transmitted.",
                    tglMonitorClipboard
                )
            )
        )
        add(settingsGap())

        add(
            settingsSection(
                I8N.text("SETTINGS_SEC_BROWSERS"),
                settingsFullRow(null, I8N.text("SETTINGS_SEC_BROWSERS_SUB"), BrowserExtensionPanel()),
            )
        )
        add(settingsGap())

        add(
            settingsSection(
                I8N.text("SETTINGS_SEC_DIAGNOSTICS") ?: "Integration Diagnostics & Repair",
                settingsFullRow(
                    I8N.text("LBL_REPAIR_INTEGRATION") ?: "Self-Diagnostics & Repair",
                    I8N.text("DESC_REPAIR_INTEGRATION")
                        ?: "Inspects loopback listener (port 8597) and registers the xdm-app:// protocol handler in your user account.",
                    createDiagnosticsRow()
                )
            )
        )
        add(settingsGap())

        add(
            settingsSection(
                I8N.text("SETTINGS_SEC_FILETYPES"),
                settingsFullRow(
                    I8N.text("DESC_FILETYPES"),
                    I8N.text("DESC_FILETYPES_SUB"),
                    areaWithReset(txtFileExt) { AppContext.config.defFileExtensions }
                ),
                settingsFullRow(
                    I8N.text("DESC_VIDEOTYPES"),
                    I8N.text("DESC_VIDEOTYPES_SUB"),
                    areaWithReset(txtVidExt) { AppContext.config.defVideoExtensions }
                ),
                settingsRow(
                    I8N.text("LBL_MIN_VIDEO_SIZE"),
                    I8N.text("LBL_MIN_VIDEO_SIZE_SUB"),
                    Box.createHorizontalBox().apply {
                        add(cmbMinVidSize)
                        add(Box.createRigidArea(scaledSize(8, 0)))
                        add(JLabel("MB").apply { foreground = settingsMutedColor() })
                    }
                ),
                settingsRow(I8N.text("LBL_DETECT_SUBTITLES"), I8N.text("LBL_DETECT_SUBTITLES_SUB"), tglDetectSubtitles),
            )
        )
        add(settingsGap())

        add(
            settingsSection(
                I8N.text("SETTINGS_SEC_EXCEPTIONS"),
                settingsFullRow(
                    I8N.text("DESC_SITEEXCEPTIONS"),
                    I8N.text("DESC_SITEEXCEPTIONS_SUB"),
                    areaWithReset(txtBlockedHosts) { AppContext.config.defBlockedHosts }
                ),
                settingsRow(I8N.text("LBL_GET_TIMESTAMP"), I8N.text("LBL_GET_TIMESTAMP_SUB"), tglGetServerTime),
            )
        )

        add(Box.createVerticalGlue())
    }

    private fun extensionArea(): JTextArea = JTextArea().apply {
        rows = 3
        wrapStyleWord = true
        lineWrap = true
        border = ScaledEmptyBorder(7, 9, 7, 9)
    }

    /**
     * A rounded, wrapping list of comma-separated values with its reset button underneath.
     * `lineWrap` keeps a long list inside the pane's width, so it only ever scrolls vertically.
     */
    private fun areaWithReset(area: JTextArea, defaults: () -> List<String>): JComponent {
        val scroll = rounded(JScrollPane(area)).apply {
            alignmentX = LEFT_ALIGNMENT
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(preferredSize.width, 78.px)
            maximumSize = Dimension(Int.MAX_VALUE, 78.px)
        }
        return Box.createVerticalBox().apply {
            alignmentX = LEFT_ALIGNMENT
            add(scroll)
            add(Box.createRigidArea(scaledSize(0, 9)))
            add(settingsLeftAligned(settingsButton(I8N.text("DESC_DEF")) {
                area.text = defaults().joinToString(", ")
            }))
        }
    }

    private fun createDiagnosticsRow(): JComponent {
        lateinit var btnRepair: JButton
        btnRepair = settingsButton(I8N.text("BTN_REPAIR_INTEGRATION") ?: "Repair Integration") {
            btnRepair.isEnabled = false
            btnRepair.text = "Repairing…"
            thread(isDaemon = true, name = "browser-repair") {
                val res = BrowserIntegrationRepair.repair()
                SwingUtilities.invokeLater {
                    btnRepair.isEnabled = true
                    btnRepair.text = I8N.text("BTN_REPAIR_INTEGRATION") ?: "Repair Integration"
                    refreshDiagnostics()
                    val owner = SwingUtilities.getWindowAncestor(this)
                    val lines = mutableListOf(res.message)
                    if (res.actionsTaken.isNotEmpty()) lines.add("Actions: " + res.actionsTaken.joinToString("; "))
                    if (res.manualSteps.isNotEmpty()) lines.add("Notice: " + res.manualSteps.joinToString("; "))
                    MessageBox.show(
                        owner,
                        if (res.success) "Browser Integration" else "Repair Incomplete",
                        lines.joinToString("\n")
                    )
                }
            }
        }

        val btnGuide = settingsButton(I8N.text("BTN_VIEW_TROUBLESHOOTING") ?: "Troubleshooting Guide") {
            BrowserLauncher.launch(BrowserIntegrationRepair.TROUBLESHOOTING_URL)
        }

        refreshDiagnostics()

        return Box.createVerticalBox().apply {
            alignmentX = LEFT_ALIGNMENT
            add(lblDiagnosticsStatus)
            add(Box.createRigidArea(scaledSize(0, 8)))
            add(Box.createHorizontalBox().apply {
                alignmentX = LEFT_ALIGNMENT
                add(btnRepair)
                add(Box.createRigidArea(scaledSize(10, 0)))
                add(btnGuide)
                add(Box.createHorizontalGlue())
            })
        }
    }

    private fun refreshDiagnostics() {
        thread(isDaemon = true, name = "browser-diag-check") {
            val diag = BrowserIntegrationRepair.diagnose()
            SwingUtilities.invokeLater {
                val portText = if (diag.portListening) "Port 8597: Active" else "Port 8597: Closed"
                val protoText = if (diag.protocolRegistered) "Protocol: Registered" else "Protocol: Missing"
                val browserText = if (diag.activeBrowsers.isNotEmpty()) "${diag.activeBrowsers.size} active browser(s)" else "No active browser"
                lblDiagnosticsStatus.text = "$portText | $protoText | $browserText"
                lblDiagnosticsStatus.toolTipText = diag.statusSummary
            }
        }
    }

    fun load() {
        val config = AppContext.config
        txtFileExt.text = config.fileExtensions.joinToString(", ")
        txtVidExt.text = config.videoExtensions.joinToString(", ")
        txtBlockedHosts.text = config.blockedHosts.joinToString(", ")
        // The config holds KB, the choices are MB.
        cmbMinVidSize.selectedItem = config.minVideoSize / 1024
        tglGetServerTime.isSelected = config.getServerTime
        tglDetectSubtitles.isSelected = config.detectSubtitles
        tglMonitorClipboard.isSelected = config.monitorClipboard
    }

    fun save() {
        val config = AppContext.config
        config.fileExtensions = txtFileExt.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        config.videoExtensions = txtVidExt.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        config.blockedHosts = txtBlockedHosts.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        config.minVideoSize = (cmbMinVidSize.selectedItem as Long) * 1024
        config.getServerTime = tglGetServerTime.isSelected
        config.detectSubtitles = tglDetectSubtitles.isSelected
        config.monitorClipboard = tglMonitorClipboard.isSelected
        if (config.monitorClipboard) {
            xdm.app.preflight.ClipboardMonitor.start()
        } else {
            xdm.app.preflight.ClipboardMonitor.stop()
        }
    }

    override fun getInsets(): Insets = scaledInsets(18, 24, 24, 24)
}
