package xdm.app.ui.screens.settings

import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.ListSelectionMode
import xdm.app.skins.CustomSkin
import xdm.app.skins.Skin
import xdm.app.skins.SkinManager
import xdm.app.ui.components.MessageBox
import xdm.app.ui.components.ThemeManager
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.openFolderExternal
import xdm.app.utils.px
import xdm.app.utils.scaledSize
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Settings panel for comprehensive visual customization:
 * - Built-in and custom skin package management (.xdmskin)
 * - Live interactive preview card
 * - Apply skin, install, export, delete, and restore defaults actions
 * - System, dark, and light theme mode toggles
 * - Curated and custom accent color chooser
 * - UI scaling and download list display mode
 */
class AppearancePanel : SettingsPanel() {
    data class SkinItem(val id: String, val name: String, val isBuiltIn: Boolean) {
        override fun toString(): String = if (isBuiltIn) "$name [Built-In]" else "$name [Custom]"
    }

    private val previewCard = SkinPreviewCard(SkinManager.currentSkin)

    private val cmbSkin = rounded(JComboBox<SkinItem>()).apply {
        preferredSize = Dimension(220.px, preferredSize.height)
        maximumSize = preferredSize
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                if (value is SkinItem) {
                    val glyph = when (value.id) {
                        SkinManager.SKIN_ENHANCED -> RemixIcon.SPARKLING_2_LINE
                        SkinManager.SKIN_ORIGINAL_XDM -> RemixIcon.WINDOW_2_LINE
                        else -> RemixIcon.BRUSH_LINE
                    }
                    c.icon = FontIcon(glyph, 16.px)
                    c.text = value.toString()
                }
                return c
            }
        }
    }

    private val lblActive = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 11f.px)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val lblMeta = JLabel().apply {
        font = font.deriveFont(Font.PLAIN, 11f.px)
        foreground = settingsMutedColor()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val lblNotice = JTextArea().apply {
        isEditable = false
        isOpaque = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        font = font.deriveFont(Font.PLAIN, 11f.px)
        foreground = settingsMutedColor()
        maximumSize = Dimension(Int.MAX_VALUE, 40.px)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val btnApplySkin = settingsButton(I8N.text("SKIN_BTN_APPLY")) {
        val selectedItem = cmbSkin.selectedItem as? SkinItem ?: return@settingsButton
        SkinManager.applySkin(selectedItem.id, saveConfig = true)
        updateSkinDetails(SkinManager.currentSkin)
        previewCard.setPreviewSkin(SkinManager.currentSkin)
    }

    private val btnInstallSkin = settingsButton(I8N.text("SKIN_BTN_INSTALL")) {
        val chooser = JFileChooser().apply {
            dialogTitle = I8N.text("SKIN_INSTALL_TITLE")
            fileFilter = FileNameExtensionFilter(I8N.text("SKIN_FILTER_DESC"), "xdmskin", "zip")
        }
        val res = chooser.showOpenDialog(this)
        if (res == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            try {
                val installedSkin = SkinManager.installSkin(file)
                loadSkinList(installedSkin.id)
                MessageBox.show(
                    this,
                    I8N.text("SKIN_INSTALL_TITLE"),
                    String.format(I8N.text("SKIN_INSTALL_SUCCESS"), installedSkin.name, (installedSkin as? CustomSkin)?.manifest?.version ?: "1.0.0"),
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                MessageBox.show(
                    this,
                    I8N.text("SKIN_INSTALL_TITLE"),
                    String.format(I8N.text("SKIN_INSTALL_FAILED"), e.message ?: e.toString()),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private val btnExportSkin: JButton = settingsButton(I8N.text("SKIN_BTN_EXPORT")) {
        val item = cmbSkin.selectedItem as? SkinItem ?: return@settingsButton
        val skin = SkinManager.getSkin(item.id) ?: return@settingsButton
        if (skin.skinDir == null) {
            MessageBox.show(
                this,
                I8N.text("SKIN_EXPORT_TITLE"),
                "Only custom skins can be exported as .xdmskin packages.",
                JOptionPane.WARNING_MESSAGE
            )
            return@settingsButton
        }
        val chooser = JFileChooser().apply {
            dialogTitle = I8N.text("SKIN_EXPORT_TITLE")
            selectedFile = File("${skin.id}.xdmskin")
            fileFilter = FileNameExtensionFilter(I8N.text("SKIN_FILTER_DESC"), "xdmskin")
        }
        val res = chooser.showSaveDialog(this)
        if (res == JFileChooser.APPROVE_OPTION) {
            var dest = chooser.selectedFile
            if (!dest.name.endsWith(".xdmskin", ignoreCase = true)) {
                dest = File(dest.parentFile, "${dest.name}.xdmskin")
            }
            try {
                SkinManager.exportSkin(skin.id, dest)
                MessageBox.show(
                    this,
                    I8N.text("SKIN_EXPORT_TITLE"),
                    String.format(I8N.text("SKIN_EXPORT_SUCCESS"), dest.absolutePath),
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                MessageBox.show(
                    this,
                    I8N.text("SKIN_EXPORT_TITLE"),
                    String.format(I8N.text("SKIN_EXPORT_FAILED"), e.message ?: e.toString()),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private val btnDeleteSkin: JButton = settingsButton(I8N.text("SKIN_BTN_DELETE")) {
        val item = cmbSkin.selectedItem as? SkinItem ?: return@settingsButton
        val skin = SkinManager.getSkin(item.id) ?: return@settingsButton
        if (skin.skinDir == null) {
            MessageBox.show(
                this,
                I8N.text("SKIN_DELETE_TITLE"),
                I8N.text("SKIN_DELETE_BUILTIN_WARN"),
                JOptionPane.WARNING_MESSAGE
            )
            return@settingsButton
        }
        val confirm = JOptionPane.showConfirmDialog(
            this,
            String.format(I8N.text("SKIN_DELETE_CONFIRM"), skin.name),
            I8N.text("SKIN_DELETE_TITLE"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (confirm == JOptionPane.YES_OPTION) {
            SkinManager.uninstallSkin(skin.id)
            loadSkinList(SkinManager.currentSkin.id)
        }
    }

    private val btnOpenFolder = settingsButton(I8N.text("SKIN_BTN_FOLDER")) {
        val folder = SkinManager.getSkinsFolder()
        try {
            openFolderExternal(null, folder.absolutePath)
        } catch (e: Exception) {
            MessageBox.show(this, "Open Folder Error", e.message, JOptionPane.ERROR_MESSAGE)
        }
    }

    private val btnRestoreDefaults = settingsButton(I8N.text("SKIN_BTN_RESTORE_DEFAULTS")) {
        val res = JOptionPane.showConfirmDialog(
            this,
            I8N.text("SKIN_RESTORE_CONFIRM"),
            I8N.text("SKIN_BTN_RESTORE_DEFAULTS"),
            JOptionPane.YES_NO_OPTION
        )
        if (res == JOptionPane.YES_OPTION) {
            SkinManager.restoreDefaults()
            load()
        }
    }

    private val themeCodes = listOf("system", "dark", "light")
    private val optTheme = SettingsOptionGroup(
        listOf(
            SettingsOption("system", I8N.text("THEME_SYSTEM"), RemixIcon.COMPUTER_FILL),
            SettingsOption("dark", I8N.text("THEME_DARK"), RemixIcon.MOON_FILL),
            SettingsOption("light", I8N.text("THEME_LIGHT"), RemixIcon.SUN_FILL),
        )
    )

    private val accentChooser = AccentChooserPanel()

    private val cmbUiScale = rounded(JComboBox(AppConfig.UI_SCALES.map { "$it%" }.toTypedArray())).apply {
        preferredSize = Dimension(190.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val optListMode = SettingsOptionGroup(
        listOf(
            SettingsOption(ListSelectionMode.TABLE, I8N.text("LIST_MODE_TABLE"), RemixIcon.FILE_LIST_2_LINE),
            SettingsOption(ListSelectionMode.CLASSIC, I8N.text("LIST_MODE_CLASSIC"), RemixIcon.FILE_TEXT_LINE),
            SettingsOption(ListSelectionMode.MODERN, I8N.text("LIST_MODE_MODERN"), RemixIcon.CHECKBOX_LINE),
        )
    )

    private val skinListener = SkinManager.SkinChangeListener { skin ->
        loadSkinList(skin.id)
        updateSkinDetails(skin)
        previewCard.setPreviewSkin(skin)
        repaint()
    }

    private val themeListener = ThemeManager.ThemeChangeListener {
        optTheme.updateTheme()
        optListMode.updateTheme()
        previewCard.repaint()
        lblNotice.foreground = settingsMutedColor()
        lblMeta.foreground = settingsMutedColor()
        repaint()
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        cmbSkin.addActionListener {
            val item = cmbSkin.selectedItem as? SkinItem ?: return@addActionListener
            val skin = SkinManager.getSkin(item.id) ?: SkinManager.BUILTIN_ENHANCED
            previewCard.setPreviewSkin(skin)
            updateSkinDetails(skin)
        }

        optTheme.onChange = {
            ThemeManager.setTheme(optTheme.selected)
            optTheme.updateTheme()
            optListMode.updateTheme()
            accentChooser.updateSelection(ThemeManager.currentAccent)
            previewCard.repaint()
        }

        val buttonBar1 = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(btnApplySkin)
            add(Box.createRigidArea(scaledSize(8, 0)))
            add(btnInstallSkin)
            add(Box.createRigidArea(scaledSize(8, 0)))
            add(btnExportSkin)
            add(Box.createRigidArea(scaledSize(8, 0)))
            add(btnDeleteSkin)
            add(Box.createHorizontalGlue())
        }

        val buttonBar2 = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(btnOpenFolder)
            add(Box.createRigidArea(scaledSize(8, 0)))
            add(btnRestoreDefaults)
            add(Box.createHorizontalGlue())
        }

        val skinHeader = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(lblActive)
            add(Box.createRigidArea(scaledSize(0, 4)))
            add(lblMeta)
            add(Box.createRigidArea(scaledSize(0, 6)))
            add(lblNotice)
            add(Box.createRigidArea(scaledSize(0, 10)))
            add(buttonBar1)
            add(Box.createRigidArea(scaledSize(0, 6)))
            add(buttonBar2)
        }

        add(settingsTitle(I8N.text("SETTINGS_SEC_APPEARANCE")))

        // 1. Skins & Themes Section
        add(
            settingsSection(
                I8N.text("SKIN_MANAGER_TITLE"),
                settingsFullRow(I8N.text("SKIN_PREVIEW_TITLE"), I8N.text("SKIN_PREVIEW_SUB"), previewCard),
                settingsRow(I8N.text("SKIN_SELECT_TITLE"), I8N.text("SKIN_SELECT_SUB"), cmbSkin),
                settingsFullRow("", null, skinHeader)
            )
        )
        add(settingsGap())

        // 2. Theme & Accent Section
        add(
            settingsSection(
                I8N.text("MSG_THEME"),
                settingsFullRow(I8N.text("MSG_THEME"), I8N.text("MSG_THEME_SUB"), optTheme),
                settingsFullRow(I8N.text("MSG_ACCENT"), I8N.text("MSG_ACCENT_SUB"), accentChooser)
            )
        )
        add(settingsGap())

        // 3. Layout & Accessibility Section
        add(
            settingsSection(
                I8N.text("UI_SIZE"),
                settingsRow(I8N.text("UI_SIZE"), I8N.text("UI_SIZE_SUB") + "\n" + I8N.text("MSG_RESTART_NEEDED"), cmbUiScale),
                settingsFullRow(I8N.text("LIST_MODE"), I8N.text("LIST_MODE_SUB"), optListMode)
            )
        )

        add(Box.createVerticalGlue())

        SkinManager.addSkinListener(skinListener)
        ThemeManager.addThemeListener(themeListener)
    }

    private fun loadSkinList(targetSkinId: String?) {
        SkinManager.refreshSkins()
        val allSkins = SkinManager.ALL_SKINS
        cmbSkin.removeAllItems()
        var selectedIndex = 0
        allSkins.forEachIndexed { index, skin ->
            val isBuiltIn = (skin.skinDir == null)
            val item = SkinItem(skin.id, skin.name, isBuiltIn)
            cmbSkin.addItem(item)
            if (skin.id.equals(targetSkinId, ignoreCase = true)) {
                selectedIndex = index
            }
        }
        if (cmbSkin.itemCount > 0) {
            cmbSkin.selectedIndex = selectedIndex
            val skin = allSkins[selectedIndex]
            updateSkinDetails(skin)
            previewCard.setPreviewSkin(skin)
        }
    }

    private fun updateSkinDetails(skin: Skin) {
        val isCurrent = skin.id.equals(SkinManager.currentSkin.id, ignoreCase = true)
        val activePrefix = if (isCurrent) {
            String.format(I8N.text("SKIN_CURRENT_ACTIVE"), skin.name) + " [${I8N.text("SKIN_ACTIVE_BADGE")}]"
        } else {
            String.format(I8N.text("SKIN_CURRENT_ACTIVE"), SkinManager.currentSkin.name)
        }
        lblActive.text = activePrefix
        lblActive.foreground = settingsAccentColor()

        val isCustom = skin is CustomSkin
        btnDeleteSkin.isEnabled = isCustom
        btnExportSkin.isEnabled = isCustom

        if (isCustom) {
            val manifest = skin.manifest
            val metaParts = mutableListOf<String>()
            metaParts.add("Version: ${manifest.version}")
            if (manifest.author.isNotBlank()) metaParts.add("Author: ${manifest.author}")
            if (manifest.license.isNotBlank()) metaParts.add("License: ${manifest.license}")
            lblMeta.text = metaParts.joinToString(" • ")
            lblMeta.isVisible = true
        } else {
            lblMeta.text = ""
            lblMeta.isVisible = false
        }

        val notice = skin.attributionNotice ?: skin.description
        lblNotice.text = notice
        lblNotice.foreground = settingsMutedColor()
    }

    fun load() {
        val config = AppContext.config
        val skinId = config.skinId.takeIf { it.isNotBlank() } ?: SkinManager.SKIN_ENHANCED
        loadSkinList(skinId)

        optTheme.selected = config.theme.lowercase().takeIf { themeCodes.contains(it) } ?: "dark"
        accentChooser.updateSelection(ThemeManager.parseHexColor(config.accentColor))
        cmbUiScale.selectedIndex = AppConfig.UI_SCALES.indexOf(config.uiScale).takeIf { it >= 0 } ?: AppConfig.UI_SCALES.indexOf(100)
        optListMode.selected = config.listSelectionMode
    }

    fun save() {
        val config = AppContext.config
        val selectedItem = cmbSkin.selectedItem as? SkinItem
        if (selectedItem != null) {
            config.skinId = selectedItem.id
        }
        config.theme = optTheme.selected
        config.accentColor = accentChooser.selectedColor?.let { ThemeManager.colorToHex(it) }
        AppConfig.UI_SCALES.getOrNull(cmbUiScale.selectedIndex)?.let { config.uiScale = it }
        config.listSelectionMode = optListMode.selected
    }

    fun dispose() {
        SkinManager.removeSkinListener(skinListener)
        ThemeManager.removeThemeListener(themeListener)
    }
}
