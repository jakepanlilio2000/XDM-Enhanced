package xdm.app.ui.screens.settings

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.I8N
import xdm.app.plugins.LoadedPlugin
import xdm.app.plugins.PluginManager
import xdm.app.plugins.PluginManifestValidator
import xdm.app.plugins.PluginStatus
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.openFolderExternal
import xdm.app.utils.px
import xdm.app.utils.scaledSize
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Settings panel for managing installed plugins, trust verification, fault diagnostics, and configuration.
 */
class PluginsPanel : SettingsPanel() {

    private val pluginsListContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
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
                I8N.text("PLUGINS_SUBTITLE") ?: "Extend XDM Enhanced with community extensions and tools."
            ).apply {
                font = font.deriveFont(Font.PLAIN, 12f.px)
                foreground = settingsMutedColor()
                alignmentX = Component.LEFT_ALIGNMENT
            }

            add(titleLabel)
            add(Box.createRigidArea(Dimension(0, 4.px)))
            add(subtitleLabel)

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

            add(Box.createRigidArea(Dimension(0, 12.px)))

            val btnBar = JPanel(FlowLayout(FlowLayout.LEFT, 8.px, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                val btnInstall = settingsButton(I8N.text("PLUGINS_INSTALL_BUTTON") ?: "Install Plugin...") {
                    promptInstallPlugin()
                }
                val btnOpenFolder = settingsButton(I8N.text("PLUGINS_OPEN_FOLDER") ?: "Open Plugins Folder") {
                    openFolderExternal(null, PluginManager.pluginsDir.absolutePath)
                }
                add(btnInstall)
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
                border = ScaledEmptyBorder(24, 20, 24, 20)
                background = settingsSurface()

                val lblEmpty = JLabel(I8N.text("PLUGINS_NO_PLUGINS") ?: "No plugins installed").apply {
                    font = font.deriveFont(Font.BOLD, 13f.px)
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                val lblEmptySub = JLabel(
                    I8N.text("PLUGINS_NO_PLUGINS_SUB")
                        ?: "Install custom .xdmplugin or .jar extension packages to add new capabilities."
                ).apply {
                    font = font.deriveFont(Font.PLAIN, 12f.px)
                    foreground = settingsMutedColor()
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                add(lblEmpty)
                add(Box.createRigidArea(Dimension(0, 4.px)))
                add(lblEmptySub)
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

            // Title row: Name, Version, Status Badge
            val titleRow = JPanel(BorderLayout()).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                val nameLabel = JLabel("${plugin.manifest.name} v${plugin.manifest.version}").apply {
                    font = font.deriveFont(Font.BOLD, 13f.px)
                }

                val statusBadge = createStatusBadge(plugin)

                add(nameLabel, BorderLayout.WEST)
                add(statusBadge, BorderLayout.EAST)
            }
            add(titleRow)

            // Author and ID
            val authorText = if (plugin.manifest.author.isNotBlank()) "By ${plugin.manifest.author}" else "ID: ${plugin.manifest.id}"
            val authorLabel = JLabel(authorText).apply {
                font = font.deriveFont(Font.PLAIN, 11f.px)
                foreground = settingsMutedColor()
                alignmentX = Component.LEFT_ALIGNMENT
            }
            add(Box.createRigidArea(Dimension(0, 2.px)))
            add(authorLabel)

            // Description
            if (plugin.manifest.description.isNotBlank()) {
                add(Box.createRigidArea(Dimension(0, 4.px)))
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

            // Capabilities pills
            if (plugin.manifest.capabilities.isNotEmpty()) {
                add(Box.createRigidArea(Dimension(0, 6.px)))
                val capsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4.px, 2.px)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    for (cap in plugin.manifest.capabilities) {
                        val pill = JLabel(cap).apply {
                            font = font.deriveFont(Font.PLAIN, 10.5f.px)
                            foreground = settingsAccentColor()
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

            // Diagnostics banner if faulted or incompatible
            if ((plugin.status == PluginStatus.FAULTED || plugin.status == PluginStatus.INCOMPATIBLE) && !plugin.faultReason.isNullOrBlank()) {
                add(Box.createRigidArea(Dimension(0, 6.px)))
                val prefix = if (plugin.status == PluginStatus.INCOMPATIBLE) "Incompatible" else "Fault"
                val faultLabel = JLabel("$prefix: ${plugin.faultReason}").apply {
                    font = font.deriveFont(Font.PLAIN, 11f.px)
                    foreground = Color(0xE5, 0x39, 0x35)
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                add(faultLabel)
            }

            // Action buttons row
            add(Box.createRigidArea(Dimension(0, 10.px)))
            val actionsRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8.px, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                // Configure button if plugin contributed a settings panel
                val hasSettings = plugin.context?.settingsPanels?.isNotEmpty() == true
                if (hasSettings) {
                    val btnConfigure = settingsButton(I8N.text("PLUGINS_CONFIGURE") ?: "Configure") {
                        showPluginConfigDialog(plugin)
                    }
                    add(btnConfigure)
                }

                // Enable / Disable / Trust button
                when (plugin.status) {
                    PluginStatus.ACTIVE -> {
                        val btnDisable = settingsButton(I8N.text("PLUGINS_DISABLE") ?: "Disable") {
                            PluginManager.disablePlugin(plugin.manifest.id)
                        }
                        add(btnDisable)
                    }
                    PluginStatus.DISABLED, PluginStatus.FAULTED -> {
                        val btnEnable = settingsButton(I8N.text("PLUGINS_ENABLE") ?: "Enable") {
                            PluginManager.enablePlugin(plugin.manifest.id)
                        }
                        add(btnEnable)
                    }
                    PluginStatus.INCOMPATIBLE -> {
                        val btnIncompatible = settingsButton(I8N.text("PLUGINS_STATUS_INCOMPATIBLE") ?: "Incompatible") {
                            JOptionPane.showMessageDialog(
                                this@PluginsPanel,
                                "This plugin cannot be enabled because it is incompatible with your XDM version or API:\n\n${plugin.faultReason}",
                                "Plugin Incompatible",
                                JOptionPane.WARNING_MESSAGE
                            )
                        }
                        add(btnIncompatible)
                    }
                    PluginStatus.UNTRUSTED -> {
                        val btnTrust = settingsButton(I8N.text("PLUGINS_TRUST_CONFIRM_TITLE") ?: "Trust & Enable") {
                            confirmAndTrustPlugin(plugin)
                        }
                        add(btnTrust)
                    }
                    else -> {}
                }

                // Uninstall button
                if (!plugin.isBuiltIn) {
                    val btnUninstall = settingsButton(I8N.text("PLUGINS_UNINSTALL") ?: "Uninstall") {
                        confirmUninstallPlugin(plugin)
                    }
                    add(btnUninstall)
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
            val result = PluginManifestValidator.validateArchive(file)
            if (!result.isValid) {
                JOptionPane.showMessageDialog(
                    this,
                    "Plugin validation failed:\n\n${result.errors.joinToString("\n")}",
                    "Plugin Installation Error",
                    JOptionPane.ERROR_MESSAGE
                )
                return
            }

            val installResult = PluginManager.installPlugin(file)
            if (!installResult.isValid) {
                JOptionPane.showMessageDialog(
                    this,
                    "Plugin installation failed:\n\n${installResult.errors.joinToString("\n")}",
                    "Plugin Installation Error",
                    JOptionPane.ERROR_MESSAGE
                )
            } else {
                val installed = PluginManager.installedPlugins.firstOrNull { it.manifest.id == installResult.manifest?.id }
                if (installed != null) {
                    confirmAndTrustPlugin(installed)
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

        val msgTemplate = I8N.text("PLUGINS_TRUST_CONFIRM_MSG")
            ?: "Security Notice:\n\nPlugin: %s (v%s)\nAuthor: %s\nCapabilities: %s\nSHA-256: %s\n\nPlugins execute in the Java Virtual Machine with your full user account privileges. Only enable plugins from sources you know and trust.\n\nDo you want to trust and enable this plugin?"

        val message = String.format(
            msgTemplate,
            plugin.manifest.name,
            plugin.manifest.version,
            plugin.manifest.author,
            caps,
            sha
        )

        val choice = JOptionPane.showConfirmDialog(
            this,
            message,
            I8N.text("PLUGINS_TRUST_CONFIRM_TITLE") ?: "Trust & Enable Plugin",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (choice == JOptionPane.YES_OPTION) {
            val success = PluginManager.trustAndEnablePlugin(plugin.manifest.id)
            if (!success && plugin.status == PluginStatus.FAULTED) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to activate plugin '${plugin.manifest.name}':\n${plugin.faultReason}",
                    "Plugin Activation Failed",
                    JOptionPane.ERROR_MESSAGE
                )
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
            PluginManager.uninstallPlugin(plugin.manifest.id)
        }
    }

    private fun showPluginConfigDialog(plugin: LoadedPlugin) {
        val panelContributor = plugin.context?.settingsPanels?.firstOrNull() ?: return
        val window = SwingUtilities.getWindowAncestor(this)

        val dialog = JDialog(window, "${plugin.manifest.name} - ${panelContributor.title}").apply {
            isModal = true
            layout = BorderLayout()
            size = scaledSize(550, 400)
            setLocationRelativeTo(window)

            val customPanel = panelContributor.createPanel(plugin.context)
            add(JScrollPane(customPanel).apply {
                border = ScaledEmptyBorder(0, 0, 0, 0)
            }, BorderLayout.CENTER)

            val btnBar = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 10.px)).apply {
                border = ScaledEmptyBorder(8, 12, 12, 12)
                val btnSave = settingsButton("Save") {
                    try {
                        panelContributor.saveSettings()
                        plugin.context?.config?.save()
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(this@apply, "Error saving settings: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                    }
                    dispose()
                }
                val btnCancel = settingsButton("Close") { dispose() }
                add(btnSave)
                add(btnCancel)
            }
            add(btnBar, BorderLayout.SOUTH)
        }

        dialog.isVisible = true
    }

    fun dispose() {
        PluginManager.removeStateListener(stateListener)
    }
}
