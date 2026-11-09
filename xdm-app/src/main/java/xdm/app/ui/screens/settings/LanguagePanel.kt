package xdm.app.ui.screens.settings

import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.utils.openFolderExternal
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.core.util.Logger
import java.awt.Dimension
import java.awt.Insets
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/**
 * Dedicated Language settings tab in SettingsWindow: lists all available languages,
 * provides language selection, and allows opening the custom translations folder.
 */
class LanguagePanel : SettingsPanel() {
    private val langModel = DefaultComboBoxModel<String>()
    val cmbLang = rounded(JComboBox(langModel)).apply {
        preferredSize = Dimension(220.px, preferredSize.height)
        maximumSize = preferredSize
    }
    private val languages = I8N.languages()

    var onLangChanged: ((Int) -> Unit)? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        add(settingsTitle(I8N.text("MSG_LANG1")))

        langModel.addAll(languages.map { it.name })

        cmbLang.addActionListener {
            onLangChanged?.invoke(cmbLang.selectedIndex)
        }

        add(
            settingsSection(
                I8N.text("MSG_LANG1"),
                settingsRow(
                    I8N.text("MSG_LANG1"),
                    I8N.text("MSG_LANG_SUB") + "\n" + I8N.text("MSG_LANG2"),
                    cmbLang
                ),
                settingsRow(
                    I8N.text("LANG_FOLDER"),
                    I8N.text("LANG_FOLDER_SUB"),
                    settingsButton(I8N.text("LANG_FOLDER_OPEN"), ::openTranslationFolder)
                )
            )
        )

        add(Box.createVerticalGlue())
    }

    fun getLangSelectedIndex(): Int = cmbLang.selectedIndex

    fun setLangSelectedIndex(index: Int) {
        if (index in 0 until langModel.size && cmbLang.selectedIndex != index) {
            cmbLang.selectedIndex = index
        }
    }

    fun load() {
        val config = AppContext.config
        languages.indexOfFirst { it.code == config.lang }.takeIf { it >= 0 }?.let {
            setLangSelectedIndex(it)
        }
    }

    fun save() {
        val config = AppContext.config
        languages.getOrNull(cmbLang.selectedIndex)?.let {
            val changed = config.lang != it.code
            config.lang = it.code
            if (changed) {
                I8N.loadTexts(it.code)
            }
        }
    }

    private fun openTranslationFolder() {
        val dir = I8N.overrideDir
        dir.mkdirs()
        runCatching { openFolderExternal(null, dir.path) }.onFailure { Logger.error(it) }
    }

    override fun getInsets(): Insets = scaledInsets(18, 24, 24, 24)
}

