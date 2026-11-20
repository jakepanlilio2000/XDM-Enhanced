package xdm.app.ui.screens.settings

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.I8N
import xdm.app.plugins.LoadedPlugin
import xdm.app.plugins.PluginCapabilityInfo
import xdm.app.plugins.PluginDiagnosticsExport
import xdm.app.plugins.PluginManager
import xdm.app.plugins.PluginManifestValidator
import xdm.app.plugins.PluginStatus
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.openFolderExternal
import xdm.app.utils.px
import xdm.app.utils.scaledSize
import xdm.core.util.Logger
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel

/**
 * Settings panel for managing installed plugins, trust verification, fault diagnostics,
 * capability exploration, and configuration.
 *
 * Guarantees non-blocking EDT interactions and accessibility across all themes.
 */
class PluginsPanel : SettingsPanel() {

    private val pluginsListContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val workerExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "PluginsPanel-Worker").apply { isDaemon = true }
    }

    private val stateListener = PluginManager.PluginStateChangeListener {
        SwingUtilities.invokeLater { refreshPluginsList() }
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = ScaledEmptyBorder(18, 24, 24, 24)

        buildHeader()
        add(Box.createRigidArea(Dimension(0, 14.px)))
        add(pluginsListContainer)

        PluginManager.addStateListener(stateListener)
        refreshPluginsList()
    }

    private fun buildHeader() {
        val headerCard = SettingsCard().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(16, 20, 16, 20)
            background = settingsSurface()

            val titleLabel = JLabel(I8N.text("PLUGINS_TITLE") ?: "Plugins").apply {
                font = font.deriveFont(Font.BOLD, 15f.px)
                icon = FontIcon(RemixIcon.PUZZLE_LINE, 20.px)
                iconTextGap = 8.px
                alignmentX = Component.LEFT_ALIGNMENT
            }

            val subtitleLabel = JLabel(
                I8N.text("PLUGINS_SUBTITLE") ?: "Extend XDM Enhanced with community extensions, protocols, recognizers, and custom actions."
            ).apply {
                font = font.deriveFont(Font.PLAIN, 12f.px)
                foreground = settingsMutedColor()
                alignmentX = Component.LEFT_ALIGNMENT
            }

            val securityModelNotice = JLabel(
                "Security Notice: In-process plugins run with your full user account privileges. Manifest capability declarations help coordinate features but cannot provide JVM sandboxing. Only install plugins from trusted sources."
            ).apply {
                font = font.deriveFont(Font.PLAIN, 11f.px)
                foreground = settingsMutedColor()
                alignmentX = Component.LEFT_ALIGNMENT
            }

            add(titleLabel)
            add(Box.createRigidArea(Dimension(0, 4.px)))
            add(subtitleLabel)
            add(Box.createRigidArea(Dimension(0, 4.px)))
            add(securityModelNotice)

            if (PluginManager.isSafeMode) {
                add(Box.createRigidArea(Dimension(0, 8.px)))
                val safeBanner = JLabel(
                    I8N.text("PLUGINS_SAFE_MODE_ACTIVE")
                        ?: "Safe Mode Active: All plugins are disabled for diagnostic recovery."
                ).apply {
                    font = font.deriveFont(Font.BOLD, 12f.px)
                    foreground = Color(0xE5, 0x39, 0x35)
                    icon = FontIcon(RemixIcon.ERROR_WARNING_FILL, 16.px, Color(0xE5, 0x39, 0x35))
                    iconTextGap = 6.px
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                add(safeBanner)
            }

            val needsRestart = PluginManager.installedPlugins.any { it.status == PluginStatus.NEEDS_RESTART } || PluginManager.requiresRestart
            if (needsRestart) {
                add(Box.createRigidArea(Dimension(0, 8.px)))
                val restartBanner = JPanel(BorderLayout(8.px, 0)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT

                    val lbl = JLabel(
                        I8N.text("PLUGINS_RESTART_NOTICE") ?: "Restart Required: One or more plugin changes require restarting XDM Enhanced to take full effect."
                    ).apply {
                        font = font.deriveFont(Font.BOLD, 12f.px)
                        foreground = Color(0x02, 0x88, 0xD1)
                        icon = FontIcon(RemixIcon.ERROR_WARNING_FILL, 16.px, Color(0x02, 0x88, 0xD1))
                        iconTextGap = 6.px
                    }
                    val btnRestart = settingsButton(I8N.text("PLUGINS_RESTART_APP") ?: "Restart App") {
                        promptRestartDialog(null)
                    }.apply {
                        accessibleContext.accessibleName = "Restart Application"
                    }
                    add(lbl, BorderLayout.CENTER)
                    add(btnRestart, BorderLayout.EAST)
                }
                add(restartBanner)
            }

            add(Box.createRigidArea(Dimension(0, 12.px)))

            val btnBar = JPanel(FlowLayout(FlowLayout.LEFT, 8.px, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                val btnInstall = settingsButton(I8N.text("PLUGINS_INSTALL_BUTTON") ?: "Install from File...") {
                    promptInstallPlugin()
                }.apply {
                    mnemonic = KeyEvent.VK_I
                    toolTipText = "Install a plugin from a local .xdmplugin or .jar package (Alt+I)"
                    accessibleContext.accessibleName = "Install Plugin from local file"
                    accessibleContext.accessibleDescription = "Opens file chooser to install a plugin package"
                }

                lateinit var btnExportDiagnostics: JButton
                btnExportDiagnostics = settingsButton(I8N.text("PLUGINS_EXPORT_DIAGNOSTICS") ?: "Export Diagnostics...") {
                    btnExportDiagnostics.isEnabled = false
                    workerExecutor.submit {
                        val report = PluginDiagnosticsExport.generateReport()
                        SwingUtilities.invokeLater {
                            btnExportDiagnostics.isEnabled = true
                            showExportDiagnosticsDialog(report)
                        }
                    }
                }.apply {
                    mnemonic = KeyEvent.VK_E
                    toolTipText = "Export detailed diagnostic report without secrets to troubleshoot plugins (Alt+E)"
                    accessibleContext.accessibleName = "Export Plugin Diagnostics"
                    accessibleContext.accessibleDescription = "Generates and previews sanitized diagnostic report without secrets"
                }

                val btnGuide = settingsButton(I8N.text("PLUGINS_CAPABILITIES_GUIDE") ?: "Capabilities Guide") {
                    showCapabilitiesGuideDialog()
                }.apply {
                    mnemonic = KeyEvent.VK_H
                    toolTipText = "Explain which capabilities require restart, OS support, external tools, or consent (Alt+H)"
                    accessibleContext.accessibleName = "Capabilities and Requirements Guide"
                    accessibleContext.accessibleDescription = "Displays detailed requirements for all plugin capabilities"
                }

                val btnOpenFolder = settingsButton(I8N.text("PLUGINS_OPEN_FOLDER") ?: "Open Plugins Folder") {
                    PluginManager.pluginsDir.mkdirs()
                    openFolderExternal(null, PluginManager.pluginsDir.absolutePath)
                }.apply {
                    mnemonic = KeyEvent.VK_F
                    toolTipText = "Open the local plugins folder in file manager (Alt+F)"
                    accessibleContext.accessibleName = "Open Plugins Folder"
                }

                add(btnInstall)
                add(btnExportDiagnostics)
                add(btnGuide)
                add(btnOpenFolder)
            }
            add(btnBar)
        }

        add(headerCard)
    }

    private fun refreshPluginsList() {
        pluginsListContainer.removeAll()

        val plugins = PluginManager.installedPlugins
        if (plugins.isEmpty()) {
            val emptyCard = SettingsCard().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = ScaledEmptyBorder(28, 24, 28, 24)
                background = settingsSurface()

                val iconLabel = JLabel(FontIcon(RemixIcon.PUZZLE_LINE, 32.px, settingsMutedColor())).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                val lblEmpty = JLabel(I8N.text("PLUGINS_NO_PLUGINS") ?: "No plugins installed").apply {
                    font = font.deriveFont(Font.BOLD, 14f.px)
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                val lblEmptySub = JLabel(
                    I8N.text("PLUGINS_NO_PLUGINS_SUB")
                        ?: "You can install custom .xdmplugin or .jar extension packages to add new capabilities."
                ).apply {
                    font = font.deriveFont(Font.PLAIN, 12f.px)
                    foreground = settingsMutedColor()
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                val btnEmptyInstall = settingsButton(I8N.text("PLUGINS_INSTALL_BUTTON") ?: "Install from File...") {
                    promptInstallPlugin()
                }.apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    accessibleContext.accessibleName = "Install Plugin from File"
                }

                add(iconLabel)
                add(Box.createRigidArea(Dimension(0, 8.px)))
                add(lblEmpty)
                add(Box.createRigidArea(Dimension(0, 4.px)))
                add(lblEmptySub)
                add(Box.createRigidArea(Dimension(0, 14.px)))
                add(btnEmptyInstall)
            }
            pluginsListContainer.add(emptyCard)
        } else {
            for (plugin in plugins) {
                pluginsListContainer.add(createPluginCard(plugin))
                pluginsListContainer.add(Box.createRigidArea(Dimension(0, 10.px)))
            }
        }

        pluginsListContainer.revalidate()
        pluginsListContainer.repaint()
    }

    private fun createPluginCard(plugin: LoadedPlugin): JPanel {
        return SettingsCard().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(14, 18, 14, 18)
            background = settingsSurface()

            // 1. Title row: Name, Version, Status Badge
            val titleRow = JPanel(BorderLayout(12.px, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                val nameLabel = JLabel("${plugin.manifest.name} v${plugin.manifest.version}").apply {
                    font = font.deriveFont(Font.BOLD, 14f.px)
                }

                val statusBadge = createStatusBadge(plugin)

                add(nameLabel, BorderLayout.WEST)
                add(statusBadge, BorderLayout.EAST)
            }
            add(titleRow)

            // 2. Metadata: Developer, License, Source, API Compatibility
            val metaPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                val authorText = if (plugin.manifest.author.isNotBlank()) "By ${plugin.manifest.author}" else "By Unknown Developer"
                val licenseText = if (plugin.manifest.license.isNotBlank()) "License: ${plugin.manifest.license}" else "License: Unspecified"
                val sourceText = when {
                    plugin.isBuiltIn -> "Source: Built-in (Host Application)"
                    plugin.archiveFile != null -> "Source: Local Archive (${plugin.archiveFile.name})"
                    else -> "Source: Directory (${plugin.pluginDir.name})"
                }
                val minApi = if (plugin.manifest.minApiVersion.isNotBlank()) plugin.manifest.minApiVersion else "1.0.0"
                val apiText = if (plugin.status == PluginStatus.INCOMPATIBLE) {
                    "API Compatibility: Incompatible (Requires API >=$minApi, Host: ${PluginManager.API_VERSION})"
                } else {
                    "API Compatibility: Requires >=$minApi (Host: ${PluginManager.API_VERSION} — Compatible)"
                }

                val row1 = JLabel("$authorText   •   $licenseText   •   $sourceText").apply {
                    font = font.deriveFont(Font.PLAIN, 11f.px)
                    foreground = settingsMutedColor()
                }
                val row2 = JLabel(apiText).apply {
                    font = font.deriveFont(Font.PLAIN, 11f.px)
                    foreground = if (plugin.status == PluginStatus.INCOMPATIBLE) Color(0xE5, 0x39, 0x35) else settingsMutedColor()
                }

                add(Box.createRigidArea(Dimension(0, 3.px)))
                add(row1)
                add(Box.createRigidArea(Dimension(0, 2.px)))
                add(row2)
            }
            add(metaPanel)

            // 3. Description
            if (plugin.manifest.description.isNotBlank()) {
                add(Box.createRigidArea(Dimension(0, 6.px)))
                val descArea = JTextArea(plugin.manifest.description).apply {
                    isEditable = false
                    isOpaque = false
                    isFocusable = false
                    lineWrap = true
                    wrapStyleWord = true
                    font = font.deriveFont(Font.PLAIN, 11.5f.px)
                    foreground = settingsMutedColor()
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                add(descArea)
            }

            // 4. Capabilities section with technical requirements tooltips
            if (plugin.manifest.capabilities.isNotEmpty()) {
                add(Box.createRigidArea(Dimension(0, 8.px)))
                val capsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4.px, 2.px)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT

                    val capsTitle = JLabel("Capabilities:").apply {
                        font = font.deriveFont(Font.BOLD, 10.5f.px)
                        foreground = settingsMutedColor()
                    }
                    add(capsTitle)

                    for (cap in plugin.manifest.capabilities) {
                        val capInfo = PluginCapabilityInfo.forCapability(cap)
                        val pill = JLabel(capInfo.displayName).apply {
                            font = font.deriveFont(Font.PLAIN, 10.5f.px)
                            foreground = settingsAccentColor()
                            toolTipText = capInfo.toHtmlTooltip()
                            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            border = BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(settingsAccentColor(), 1),
                                ScaledEmptyBorder(1, 6, 1, 6)
                            )
                        }
                        add(pill)
                    }
                }
                add(capsPanel)
            }

            // 5. Diagnostics / Alert Banners
            if (plugin.status == PluginStatus.FAULTED && !plugin.faultReason.isNullOrBlank()) {
                add(Box.createRigidArea(Dimension(0, 8.px)))
                val faultCard = JPanel(BorderLayout(8.px, 0)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color(0xE5, 0x39, 0x35), 1),
                        ScaledEmptyBorder(6, 10, 6, 10)
                    )
                    val lbl = JLabel("Fault Error: ${plugin.faultReason}").apply {
                        font = font.deriveFont(Font.BOLD, 11f.px)
                        foreground = Color(0xE5, 0x39, 0x35)
                        icon = FontIcon(RemixIcon.ERROR_WARNING_FILL, 14.px, Color(0xE5, 0x39, 0x35))
                        iconTextGap = 6.px
                    }
                    add(lbl, BorderLayout.CENTER)
                }
                add(faultCard)
            }

            if (plugin.status == PluginStatus.INCOMPATIBLE) {
                add(Box.createRigidArea(Dimension(0, 8.px)))
                val incompCard = JPanel(BorderLayout(8.px, 0)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color(0xD3, 0x2F, 0x2F), 1),
                        ScaledEmptyBorder(6, 10, 6, 10)
                    )
                    val reason = plugin.faultReason ?: "Plugin requires newer API version"
                    val lbl = JLabel("API Incompatibility: $reason").apply {
                        font = font.deriveFont(Font.BOLD, 11f.px)
                        foreground = Color(0xD3, 0x2F, 0x2F)
                        icon = FontIcon(RemixIcon.CLOSE_CIRCLE_LINE, 14.px, Color(0xD3, 0x2F, 0x2F))
                        iconTextGap = 6.px
                    }
                    add(lbl, BorderLayout.CENTER)
                }
                add(incompCard)
            }

            if (plugin.status == PluginStatus.NEEDS_RESTART) {
                add(Box.createRigidArea(Dimension(0, 8.px)))
                val restartCard = JPanel(BorderLayout(8.px, 0)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color(0x02, 0x88, 0xD1), 1),
                        ScaledEmptyBorder(6, 10, 6, 10)
                    )
                    val lbl = JLabel("Restart Required: Changes will take full effect after restarting XDM Enhanced.").apply {
                        font = font.deriveFont(Font.BOLD, 11f.px)
                        foreground = Color(0x02, 0x88, 0xD1)
                        icon = FontIcon(RemixIcon.ERROR_WARNING_FILL, 14.px, Color(0x02, 0x88, 0xD1))
                        iconTextGap = 6.px
                    }
                    add(lbl, BorderLayout.CENTER)
                }
                add(restartCard)
            }

            // Check for missing external tools (e.g. yt-dlp) without blocking EDT
            if (plugin.manifest.id == "ytdlp-downloader" || plugin.manifest.capabilities.contains("DOWNLOAD_PROVIDER")) {
                val ytdlpStatus = PluginDiagnosticsExport.checkYtDlpToolStatus(queryVersion = false)
                if (!ytdlpStatus.detected && plugin.status == PluginStatus.ACTIVE) {
                    add(Box.createRigidArea(Dimension(0, 8.px)))
                    val warnCard = JPanel(BorderLayout(8.px, 0)).apply {
                        isOpaque = false
                        alignmentX = Component.LEFT_ALIGNMENT
                        border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color(0xF5, 0x7C, 0x00), 1),
                            ScaledEmptyBorder(6, 10, 6, 10)
                        )
                        val lbl = JLabel("External Tool Missing: 'yt-dlp' executable was not found on system PATH.").apply {
                            font = font.deriveFont(Font.PLAIN, 11f.px)
                            foreground = Color(0xF5, 0x7C, 0x00)
                            icon = FontIcon(RemixIcon.ERROR_WARNING_FILL, 14.px, Color(0xF5, 0x7C, 0x00))
                            iconTextGap = 6.px
                        }
                        add(lbl, BorderLayout.CENTER)
                    }
                    add(warnCard)
                }
            }

            // 6. Action buttons row (NEVER SHIP DEAD BUTTONS)
            add(Box.createRigidArea(Dimension(0, 12.px)))
            val actionsRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8.px, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                // Configure button (ONLY when plugin contributes settings panels)
                val hasSettings = plugin.context?.settingsPanels?.isNotEmpty() == true
                if (hasSettings) {
                    val btnConfigure = settingsButton(I8N.text("PLUGINS_CONFIGURE") ?: "Configure") {
                        showPluginConfigDialog(plugin)
                    }.apply {
                        accessibleContext.accessibleName = "Configure ${plugin.manifest.name}"
                        accessibleContext.accessibleDescription = "Opens settings dialog for ${plugin.manifest.name}"
                    }
                    add(btnConfigure)
                }

                // View Logs button (ALWAYS present)
                val btnViewLogs = settingsButton(I8N.text("PLUGINS_VIEW_LOGS") ?: "View Logs") {
                    showPluginLogsDialog(plugin)
                }.apply {
                    accessibleContext.accessibleName = "View Logs for ${plugin.manifest.name}"
                    accessibleContext.accessibleDescription = "Displays diagnostic logs and telemetry records for ${plugin.manifest.name}"
                }
                add(btnViewLogs)

                // State Toggle button: Enable / Disable / Trust / Restart
                when (plugin.status) {
                    PluginStatus.ACTIVE -> {
                        val btnDisable = settingsButton(I8N.text("PLUGINS_DISABLE") ?: "Disable") {
                            workerExecutor.submit {
                                PluginManager.disablePlugin(plugin.manifest.id)
                                SwingUtilities.invokeLater { refreshPluginsList() }
                            }
                        }.apply {
                            accessibleContext.accessibleName = "Disable ${plugin.manifest.name}"
                        }
                        add(btnDisable)
                    }
                    PluginStatus.DISABLED -> {
                        val btnEnable = settingsButton(I8N.text("PLUGINS_ENABLE") ?: "Enable") {
                            workerExecutor.submit {
                                PluginManager.enablePlugin(plugin.manifest.id)
                                SwingUtilities.invokeLater { refreshPluginsList() }
                            }
                        }.apply {
                            accessibleContext.accessibleName = "Enable ${plugin.manifest.name}"
                        }
                        add(btnEnable)
                    }
                    PluginStatus.FAULTED -> {
                        val btnRetry = settingsButton("Retry / Enable") {
                            workerExecutor.submit {
                                PluginManager.enablePlugin(plugin.manifest.id)
                                SwingUtilities.invokeLater { refreshPluginsList() }
                            }
                        }.apply {
                            accessibleContext.accessibleName = "Retry enabling ${plugin.manifest.name}"
                        }
                        add(btnRetry)
                    }
                    PluginStatus.INCOMPATIBLE -> {
                        val btnIncomp = settingsButton(I8N.text("PLUGINS_STATUS_INCOMPATIBLE") ?: "Incompatible") {
                            JOptionPane.showMessageDialog(
                                this@PluginsPanel,
                                "This plugin cannot be enabled because it is incompatible with your XDM version or API:\n\n${plugin.faultReason ?: "Incompatible API version"}",
                                "Plugin Incompatible",
                                JOptionPane.WARNING_MESSAGE
                            )
                        }.apply {
                            accessibleContext.accessibleName = "Incompatible plugin info"
                        }
                        add(btnIncomp)
                    }
                    PluginStatus.DISCOVERED,
                    PluginStatus.UNTRUSTED -> {
                        val btnTrust = settingsButton(I8N.text("PLUGINS_TRUST_CONFIRM_TITLE") ?: "Trust & Enable") {
                            confirmAndTrustPlugin(plugin)
                        }.apply {
                            accessibleContext.accessibleName = "Trust and enable ${plugin.manifest.name}"
                        }
                        add(btnTrust)
                    }
                    PluginStatus.NEEDS_RESTART -> {
                        val btnRestart = settingsButton(I8N.text("PLUGINS_RESTART_APP") ?: "Restart App") {
                            promptRestartDialog(plugin)
                        }.apply {
                            accessibleContext.accessibleName = "Restart application for ${plugin.manifest.name}"
                        }
                        add(btnRestart)
                    }
                    else -> {}
                }

                // Uninstall / Remove button
                if (!plugin.isBuiltIn) {
                    val btnRemove = settingsButton(I8N.text("PLUGINS_UNINSTALL") ?: "Remove") {
                        confirmUninstallPlugin(plugin)
                    }.apply {
                        accessibleContext.accessibleName = "Remove ${plugin.manifest.name}"
                        accessibleContext.accessibleDescription = "Permanently uninstalls this plugin and removes its files"
                    }
                    add(btnRemove)
                }
            }
            add(actionsRow)
        }
    }

    private fun createStatusBadge(plugin: LoadedPlugin): JLabel {
        val (text, color) = when (plugin.status) {
            PluginStatus.DISCOVERED -> (I8N.text("PLUGINS_STATUS_UNTRUSTED") ?: "Discovered") to Color(0xF5, 0x7C, 0x00)
            PluginStatus.ACTIVE -> (I8N.text("PLUGINS_STATUS_ACTIVE") ?: "Active") to Color(0x2E, 0x7D, 0x32)
            PluginStatus.DISABLED -> (I8N.text("PLUGINS_STATUS_DISABLED") ?: "Disabled") to settingsMutedColor()
            PluginStatus.UNTRUSTED -> (I8N.text("PLUGINS_STATUS_UNTRUSTED") ?: "Untrusted") to Color(0xF5, 0x7C, 0x00)
            PluginStatus.FAULTED -> (I8N.text("PLUGINS_STATUS_FAULTED") ?: "Faulted") to Color(0xD3, 0x2F, 0x2F)
            PluginStatus.INCOMPATIBLE -> (I8N.text("PLUGINS_STATUS_INCOMPATIBLE") ?: "Incompatible") to Color(0xD3, 0x2F, 0x2F)
            PluginStatus.NEEDS_RESTART -> (I8N.text("PLUGINS_STATUS_NEEDS_RESTART") ?: "Restart Required") to Color(0x02, 0x88, 0xD1)
        }

        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 11f.px)
            foreground = color
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1),
                ScaledEmptyBorder(2, 8, 2, 8)
            )
        }
    }

    private fun promptInstallPlugin() {
        val chooser = JFileChooser().apply {
            dialogTitle = I8N.text("PLUGINS_INSTALL_BUTTON") ?: "Install Plugin"
            fileFilter = FileNameExtensionFilter("XDM Plugins (*.xdmplugin, *.jar)", "xdmplugin", "jar")
        }
        val res = chooser.showOpenDialog(this)
        if (res == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            workerExecutor.submit {
                val result = PluginManifestValidator.validateArchive(file)
                SwingUtilities.invokeLater {
                    if (!result.isValid) {
                        JOptionPane.showMessageDialog(
                            this@PluginsPanel,
                            "Plugin validation failed:\n\n${result.errors.joinToString("\n")}",
                            "Plugin Installation Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                        return@invokeLater
                    }

                    workerExecutor.submit {
                        val installResult = PluginManager.installPlugin(file)
                        SwingUtilities.invokeLater {
                            if (!installResult.isValid) {
                                JOptionPane.showMessageDialog(
                                    this@PluginsPanel,
                                    "Plugin installation failed:\n\n${installResult.errors.joinToString("\n")}",
                                    "Plugin Installation Error",
                                    JOptionPane.ERROR_MESSAGE
                                )
                            } else {
                                val installed = PluginManager.installedPlugins.firstOrNull { it.manifest.id == installResult.manifest?.id }
                                if (installed != null) {
                                    confirmAndTrustPlugin(installed)
                                } else {
                                    refreshPluginsList()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun confirmAndTrustPlugin(plugin: LoadedPlugin) {
        val caps = if (plugin.manifest.capabilities.isNotEmpty()) {
            plugin.manifest.capabilities.joinToString(", ")
        } else {
            "None declared"
        }
        val sha = if (plugin.sha256.isNotBlank()) plugin.sha256 else "N/A"
        val license = if (plugin.manifest.license.isNotBlank()) plugin.manifest.license else "Not specified"
        val apiReq = if (plugin.manifest.minApiVersion.isNotBlank()) ">=${plugin.manifest.minApiVersion}" else "Default"
        val source = plugin.archiveFile?.name ?: plugin.pluginDir.name

        val message = """
            Security & Trust Disclosure:

            Plugin: ${plugin.manifest.name} (${plugin.manifest.id})
            Version: ${plugin.manifest.version}
            Author: ${plugin.manifest.author}
            License: $license
            Source: $source
            API Compatibility: Requires $apiReq (Host API: ${PluginManager.API_VERSION})
            Capabilities: $caps
            SHA-256 Checksum: $sha

            ⚠️ IN-PROCESS EXECUTION WARNING:
            In-process Java plugins run inside the XDM Enhanced process with your full user account privileges. Manifest permissions alone cannot sandbox them.

            Do you explicitly approve and trust this third-party plugin?
        """.trimIndent()

        val choice = JOptionPane.showConfirmDialog(
            this,
            message,
            I8N.text("PLUGINS_TRUST_CONFIRM_TITLE") ?: "Security Disclosure: Trust & Enable Plugin",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (choice == JOptionPane.YES_OPTION) {
            workerExecutor.submit {
                val success = PluginManager.trustAndEnablePlugin(plugin.manifest.id)
                SwingUtilities.invokeLater {
                    if (!success && plugin.status == PluginStatus.FAULTED) {
                        JOptionPane.showMessageDialog(
                            this@PluginsPanel,
                            "Failed to activate plugin '${plugin.manifest.name}':\n${plugin.faultReason}",
                            "Plugin Activation Failed",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                    refreshPluginsList()
                }
            }
        }
    }

    private fun confirmUninstallPlugin(plugin: LoadedPlugin) {
        val msgTemplate = I8N.text("PLUGINS_UNINSTALL_CONFIRM")
            ?: "Are you sure you want to uninstall '%s'? All plugin files and settings will be permanently deleted."
        val message = String.format(msgTemplate, plugin.manifest.name)

        val choice = JOptionPane.showConfirmDialog(
            this,
            message,
            I8N.text("PLUGINS_UNINSTALL") ?: "Uninstall Plugin",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )

        if (choice == JOptionPane.YES_OPTION) {
            workerExecutor.submit {
                PluginManager.uninstallPlugin(plugin.manifest.id)
                SwingUtilities.invokeLater { refreshPluginsList() }
            }
        }
    }

    private fun showPluginLogsDialog(plugin: LoadedPlugin) {
        val window = SwingUtilities.getWindowAncestor(this)
        val dialog = JDialog(window, "Plugin Logs & Diagnostics — ${plugin.manifest.name}").apply {
            isModal = true
            layout = BorderLayout()
            size = scaledSize(680, 500)
            setLocationRelativeTo(window)

            val content = JTextArea().apply {
                isEditable = false
                font = Font("Monospaced", Font.PLAIN, 11.px)
                background = settingsSurface()
                foreground = UIManager.getColor("TextArea.foreground") ?: Color.LIGHT_GRAY
                border = ScaledEmptyBorder(10, 10, 10, 10)
            }

            val sb = StringBuilder()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
            sb.appendLine("================================================================================")
            sb.appendLine("                 PLUGIN DIAGNOSTICS & TELEMETRY SUMMARY                        ")
            sb.appendLine("================================================================================")
            sb.appendLine("Timestamp      : $dateStr")
            sb.appendLine("Plugin ID      : ${plugin.manifest.id}")
            sb.appendLine("Plugin Name    : ${plugin.manifest.name}")
            sb.appendLine("Version        : ${plugin.manifest.version}")
            sb.appendLine("Author         : ${plugin.manifest.author.ifBlank { "Unknown" }}")
            sb.appendLine("License        : ${plugin.manifest.license.ifBlank { "Unspecified" }}")
            sb.appendLine("Current Status : ${plugin.status.displayName}")
            sb.appendLine("ClassLoader    : ${if (plugin.classLoader != null) "Active (Isolated URLClassLoader)" else "Closed / Inactive"}")
            sb.appendLine("Built-in       : ${plugin.isBuiltIn}")
            sb.appendLine("Target Dir     : ${plugin.pluginDir.absolutePath}")
            sb.appendLine("Archive File   : ${plugin.archiveFile?.absolutePath ?: "N/A"}")
            sb.appendLine("SHA-256 Hash   : ${plugin.sha256.ifBlank { "N/A" }}")
            sb.appendLine("Capabilities   : ${if (plugin.manifest.capabilities.isNotEmpty()) plugin.manifest.capabilities.joinToString(", ") else "None"}")
            sb.appendLine("Dependencies   : ${if (plugin.manifest.dependencies.isNotEmpty()) plugin.manifest.dependencies.joinToString(", ") else "None"}")
            if (!plugin.faultReason.isNullOrBlank()) {
                sb.appendLine("Latest Fault   : ${PluginManager.sanitizeDiagnostics(plugin.faultReason!!)}")
            }
            sb.appendLine()

            sb.appendLine("--- [REGISTERED EXTENSION POINTS] ---")
            val ctx = plugin.context
            if (ctx != null) {
                sb.appendLine("Download Providers : ${ctx.downloadProviders.size} (${ctx.downloadProviders.joinToString { it.name }})")
                sb.appendLine("URL Recognizers    : ${ctx.urlRecognizers.size} (${ctx.urlRecognizers.joinToString { it.javaClass.simpleName }})")
                sb.appendLine("Settings Panels    : ${ctx.settingsPanels.size}")
                sb.appendLine("Context Menus      : ${ctx.contextMenuContributors.size}")
                sb.appendLine("Toolbar Actions    : ${ctx.toolbarContributors.size}")
                sb.appendLine("Proxy Providers    : ${ctx.proxyProviders.size}")
                sb.appendLine("Registrations Total: ${ctx.registrations.size}")
            } else {
                sb.appendLine("Plugin context is not active (Status: ${plugin.status.displayName}).")
            }
            sb.appendLine()

            sb.appendLine("--- [DIAGNOSTIC FAULT RECORDS] ---")
            val diagRecords = PluginManager.getDiagnostics().filter { it.pluginId == plugin.manifest.id }
            if (diagRecords.isEmpty()) {
                sb.appendLine("No diagnostic fault events recorded for this plugin.")
            } else {
                for (rec in diagRecords) {
                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(rec.timestamp))
                    sb.appendLine("[$ts] Action: ${rec.lastAction} | Reason: ${rec.faultReason}")
                }
            }
            sb.appendLine("================================================================================")

            content.text = sb.toString()
            content.caretPosition = 0

            add(JScrollPane(content).apply { border = ScaledEmptyBorder(0, 0, 0, 0) }, BorderLayout.CENTER)

            val btnBar = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 10.px)).apply {
                border = ScaledEmptyBorder(8, 12, 12, 12)

                val btnCopy = settingsButton("Copy to Clipboard") {
                    val sel = StringSelection(content.text)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    JOptionPane.showMessageDialog(this@apply, "Plugin diagnostics copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE)
                }.apply {
                    accessibleContext.accessibleName = "Copy logs to clipboard"
                }

                val btnClose = settingsButton("Close") { dispose() }.apply {
                    accessibleContext.accessibleName = "Close dialog"
                }

                add(btnCopy)
                add(btnClose)
            }
            add(btnBar, BorderLayout.SOUTH)
        }
        dialog.isVisible = true
    }

    private fun showExportDiagnosticsDialog(report: String) {
        val window = SwingUtilities.getWindowAncestor(this)
        val dialog = JDialog(window, "Export Diagnostic Report (Secret-Free)").apply {
            isModal = true
            layout = BorderLayout()
            size = scaledSize(740, 540)
            setLocationRelativeTo(window)

            val content = JTextArea(report).apply {
                isEditable = false
                font = Font("Monospaced", Font.PLAIN, 11.px)
                background = settingsSurface()
                foreground = UIManager.getColor("TextArea.foreground") ?: Color.LIGHT_GRAY
                border = ScaledEmptyBorder(10, 10, 10, 10)
                caretPosition = 0
            }

            add(JScrollPane(content).apply { border = ScaledEmptyBorder(0, 0, 0, 0) }, BorderLayout.CENTER)

            val btnBar = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 10.px)).apply {
                border = ScaledEmptyBorder(8, 12, 12, 12)

                val btnCopy = settingsButton("Copy to Clipboard") {
                    val sel = StringSelection(report)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    JOptionPane.showMessageDialog(this@apply, "Diagnostic report copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE)
                }.apply {
                    accessibleContext.accessibleName = "Copy diagnostic report to clipboard"
                }

                val btnSave = settingsButton("Save to File...") {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Save Diagnostic Report"
                        selectedFile = File("xdm-plugin-diagnostics-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.txt")
                    }
                    if (chooser.showSaveDialog(this@apply) == JFileChooser.APPROVE_OPTION) {
                        try {
                            PluginDiagnosticsExport.exportToFile(chooser.selectedFile, report)
                            JOptionPane.showMessageDialog(this@apply, "Diagnostics successfully saved to:\n${chooser.selectedFile.absolutePath}", "Export Successful", JOptionPane.INFORMATION_MESSAGE)
                        } catch (e: Exception) {
                            JOptionPane.showMessageDialog(this@apply, "Failed to save file: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                        }
                    }
                }.apply {
                    accessibleContext.accessibleName = "Save diagnostic report to file"
                }

                val btnClose = settingsButton("Close") { dispose() }.apply {
                    accessibleContext.accessibleName = "Close dialog"
                }

                add(btnCopy)
                add(btnSave)
                add(btnClose)
            }
            add(btnBar, BorderLayout.SOUTH)
        }
        dialog.isVisible = true
    }

    private fun showCapabilitiesGuideDialog() {
        val window = SwingUtilities.getWindowAncestor(this)
        val dialog = JDialog(window, "Plugin Capabilities & Requirements Guide").apply {
            isModal = true
            layout = BorderLayout()
            size = scaledSize(820, 500)
            setLocationRelativeTo(window)

            val columns = arrayOf("Capability", "Category", "External Tools", "OS Support", "User Consent", "Restart")
            val allCaps = PluginCapabilityInfo.allKnownCapabilities()
            val data = Array(allCaps.size) { i ->
                val c = allCaps[i]
                arrayOf(
                    c.displayName,
                    c.category,
                    c.externalTools,
                    c.osSupport,
                    c.consentRequired,
                    if (c.restartRequired) "Required" else "Dynamic"
                )
            }

            val table = JTable(DefaultTableModel(data, columns)).apply {
                font = font.deriveFont(Font.PLAIN, 11f.px)
                rowHeight = 24.px
                tableHeader.font = font.deriveFont(Font.BOLD, 11.5f.px)
                isOpaque = false
                setShowGrid(true)
            }

            val scrollPane = JScrollPane(table).apply {
                border = ScaledEmptyBorder(10, 10, 10, 10)
            }

            val headerText = JLabel(
                "<html><body style='padding: 10px; font-family: sans-serif; font-size: 11px;'>" +
                "<b>Understanding Plugin Capabilities:</b> Capabilities define technical touchpoints between XDM Enhanced and third-party extensions. " +
                "The table below details external tool requirements, operating system constraints, consent boundaries, and restart behavior." +
                "</body></html>"
            )

            add(headerText, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)

            val btnBar = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 10.px)).apply {
                border = ScaledEmptyBorder(8, 12, 12, 12)
                val btnClose = settingsButton("Close") { dispose() }.apply {
                    accessibleContext.accessibleName = "Close capabilities guide"
                }
                add(btnClose)
            }
            add(btnBar, BorderLayout.SOUTH)
        }
        dialog.isVisible = true
    }

    private fun promptRestartDialog(plugin: LoadedPlugin?) {
        val name = plugin?.manifest?.name ?: "Plugins"
        val msgTemplate = I8N.text("PLUGINS_RESTART_CONFIRM")
            ?: "An application restart is required for changes to '%s' to take full effect.\n\nRestart XDM Enhanced now?"
        val message = String.format(msgTemplate, name)
        val choice = JOptionPane.showConfirmDialog(
            this,
            message,
            I8N.text("PLUGINS_STATUS_NEEDS_RESTART") ?: "Restart Required",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
        if (choice == JOptionPane.YES_OPTION) {
            PluginManager.resetRestartFlag()
            JOptionPane.showMessageDialog(
                this,
                "Please reopen XDM Enhanced to complete applying plugin changes.",
                "Restart Pending",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun showPluginConfigDialog(plugin: LoadedPlugin) {
        val contributors = plugin.context?.settingsPanels ?: return
        if (contributors.isEmpty()) return
        val window = SwingUtilities.getWindowAncestor(this)

        try {
            val contentComponent: JComponent = if (contributors.size == 1) {
                val single = contributors.first()
                JScrollPane(single.createPanel(plugin.context)).apply {
                    border = ScaledEmptyBorder(0, 0, 0, 0)
                }
            } else {
                JTabbedPane().apply {
                    for (panel in contributors) {
                        try {
                            val comp = panel.createPanel(plugin.context)
                            addTab(panel.title, JScrollPane(comp).apply { border = ScaledEmptyBorder(0, 0, 0, 0) })
                        } catch (e: Exception) {
                            addTab(panel.title, JLabel("Error: ${e.message}"))
                        }
                    }
                }
            }

            val titleText = "${plugin.manifest.name} - " + (if (contributors.size == 1) contributors.first().title else "Settings")
            val dialog = JDialog(window, titleText).apply {
                isModal = true
                layout = BorderLayout()
                size = scaledSize(550, 400)
                setLocationRelativeTo(window)

                add(contentComponent, BorderLayout.CENTER)

                val btnBar = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 10.px)).apply {
                    border = ScaledEmptyBorder(8, 12, 12, 12)
                    val btnSave = settingsButton("Save") {
                        try {
                            for (panel in contributors) {
                                runCatching { panel.saveSettings() }
                            }
                            plugin.context?.config?.save()
                        } catch (e: Exception) {
                            JOptionPane.showMessageDialog(this@apply, "Error saving settings: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                        }
                        dispose()
                    }.apply {
                        accessibleContext.accessibleName = "Save settings"
                    }
                    val btnCancel = settingsButton("Close") { dispose() }.apply {
                        accessibleContext.accessibleName = "Close settings"
                    }
                    add(btnSave)
                    add(btnCancel)
                }
                add(btnBar, BorderLayout.SOUTH)
            }
            dialog.isVisible = true
        } catch (t: Throwable) {
            Logger.error("Failed to load plugin settings panel for ${plugin.manifest.id}", t)
            JOptionPane.showMessageDialog(window, "Failed to load plugin settings: ${t.message}", "Plugin Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun dispose() {
        PluginManager.removeStateListener(stateListener)
        workerExecutor.shutdown()
    }
}
