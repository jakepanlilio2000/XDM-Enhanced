package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.ui.components.AppMenuHandler
import xdm.app.ui.screens.SettingsWindow
import xdm.app.ui.screens.settings.GeneralPanel
import xdm.app.ui.screens.settings.LanguagePanel
import java.io.File
import javax.swing.SwingUtilities

class LanguageSettingsRegressionTest {

    @TempDir
    lateinit var tempDir: File

    private var originalLang = "en"
    private lateinit var config: AppConfig

    @BeforeEach
    fun setUp() {
        I8N.loadTexts("en")
        config = AppConfig(tempDir.absolutePath)
        AppContext.config = config
        originalLang = config.lang
    }

    @AfterEach
    fun tearDown() {
        I8N.loadTexts("en")
    }

    @Test
    fun languagePageRegisteredInSettingsWindow() {
        var window: SettingsWindow? = null
        SwingUtilities.invokeAndWait {
            window = SettingsWindow()
        }
        val win = window!!
        try {
            SwingUtilities.invokeAndWait {
                win.loadConfig()
                win.selectPage("MSG_LANG1")
            }
            // Nav list has the selected item
            val navListField = SettingsWindow::class.java.getDeclaredField("navList").apply { isAccessible = true }
            val navList = navListField.get(win) as javax.swing.JList<*>
            val pagesField = SettingsWindow::class.java.getDeclaredField("pages").apply { isAccessible = true }
            val pages = pagesField.get(win) as List<*>

            val pageClass = Class.forName("xdm.app.ui.screens.SettingsWindow\$Page")
            val keyField = pageClass.getDeclaredField("key").apply { isAccessible = true }
            val panelField = pageClass.getDeclaredField("panel").apply { isAccessible = true }

            val langPageIdx = pages.indexOfFirst { keyField.get(it) == "MSG_LANG1" }
            assertTrue(langPageIdx >= 0, "Language page MSG_LANG1 must exist in pages list")
            assertEquals(langPageIdx, navList.selectedIndex, "selectPage('MSG_LANG1') must select the language page index")
            assertTrue(panelField.get(pages[langPageIdx]) is LanguagePanel, "Language page must contain a LanguagePanel instance")
        } finally {
            SwingUtilities.invokeAndWait { win.dispose() }
        }
    }

    @Test
    fun languagePanelLoadsAndSavesLanguage() {
        val languages = I8N.languages()
        assertTrue(languages.isNotEmpty(), "Must have at least one language available")

        var panel: LanguagePanel? = null
        SwingUtilities.invokeAndWait {
            panel = LanguagePanel()
        }
        val lp = panel!!

        // Find a non-default language if available
        val targetLang = languages.firstOrNull { it.code != "en" } ?: languages.first()
        val targetIdx = languages.indexOfFirst { it.code == targetLang.code }

        SwingUtilities.invokeAndWait {
            lp.load()
            lp.setLangSelectedIndex(targetIdx)
            assertEquals(targetIdx, lp.getLangSelectedIndex())
            lp.save()
        }

        assertEquals(targetLang.code, AppContext.config.lang, "Saving LanguagePanel should persist selected lang code to config")
    }

    @Test
    fun bidirectionalSyncBetweenGeneralAndLanguagePanels() {
        var generalPanel: GeneralPanel? = null
        var languagePanel: LanguagePanel? = null

        SwingUtilities.invokeAndWait {
            generalPanel = GeneralPanel()
            languagePanel = LanguagePanel()

            generalPanel!!.onLangChanged = { idx -> languagePanel!!.setLangSelectedIndex(idx) }
            languagePanel!!.onLangChanged = { idx -> generalPanel!!.setLangSelectedIndex(idx) }
        }

        val gp = generalPanel!!
        val lp = languagePanel!!

        val languages = I8N.languages()
        if (languages.size > 1) {
            SwingUtilities.invokeAndWait {
                gp.setLangSelectedIndex(1)
                gp.cmbLang.actionListeners.forEach { it.actionPerformed(null) }
            }
            assertEquals(1, lp.getLangSelectedIndex(), "LanguagePanel must reflect change from GeneralPanel")

            SwingUtilities.invokeAndWait {
                lp.setLangSelectedIndex(0)
                lp.cmbLang.actionListeners.forEach { it.actionPerformed(null) }
            }
            assertEquals(0, gp.getLangSelectedIndex(), "GeneralPanel must reflect change from LanguagePanel")
        }
    }

    @Test
    fun showLanguageDlgOpensSettingsOnLanguagePage() {
        var settingsWindow: SettingsWindow? = null
        SwingUtilities.invokeAndWait {
            AppMenuHandler.showLanguageDlg(null)
            val openField = xdm.app.utils.SingleWindows::class.java.getDeclaredField("open").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val openMap = openField.get(xdm.app.utils.SingleWindows) as Map<Any, java.awt.Window>
            settingsWindow = openMap[SettingsWindow::class] as? SettingsWindow
        }
        val win = settingsWindow
        assertNotNull(win, "showLanguageDlg should have opened SettingsWindow in SingleWindows")
        try {
            val navListField = SettingsWindow::class.java.getDeclaredField("navList").apply { isAccessible = true }
            val navList = navListField.get(win) as javax.swing.JList<*>
            val pagesField = SettingsWindow::class.java.getDeclaredField("pages").apply { isAccessible = true }
            val pages = pagesField.get(win) as List<*>

            val pageClass = Class.forName("xdm.app.ui.screens.SettingsWindow\$Page")
            val keyField = pageClass.getDeclaredField("key").apply { isAccessible = true }
            val langPageIdx = pages.indexOfFirst { keyField.get(it) == "MSG_LANG1" }

            assertEquals(langPageIdx, navList.selectedIndex, "showLanguageDlg must switch nav selection to MSG_LANG1")
        } finally {
            SwingUtilities.invokeAndWait { win?.dispose() }
        }
    }

    @Test
    fun languageSwitchPersistsAndReloadsI8NTexts() {
        var window: SettingsWindow? = null
        SwingUtilities.invokeAndWait {
            window = SettingsWindow().apply { loadConfig() }
        }
        val win = window!!
        try {
            val languages = I8N.languages()
            val deLang = languages.firstOrNull { it.code == "de" }
            if (deLang != null) {
                val deIdx = languages.indexOfFirst { it.code == "de" }
                val lpField = SettingsWindow::class.java.getDeclaredField("languagePanel").apply { isAccessible = true }
                val lp = lpField.get(win) as LanguagePanel

                SwingUtilities.invokeAndWait {
                    lp.setLangSelectedIndex(deIdx)
                    win.save()
                }

                assertEquals("de", AppContext.config.lang, "Saving settings with German selected should persist 'de' to config")
                assertEquals("Sprache", I8N.text("MSG_LANG1"), "I8N should have reloaded texts so MSG_LANG1 returns German 'Sprache'")

                // Switch back to English
                val enIdx = languages.indexOfFirst { it.code == "en" }
                SwingUtilities.invokeAndWait {
                    lp.setLangSelectedIndex(enIdx)
                    win.save()
                }
                assertEquals("en", AppContext.config.lang)
                assertEquals("Language", I8N.text("MSG_LANG1"))
            }
        } finally {
            SwingUtilities.invokeAndWait { win.dispose() }
        }
    }
}
