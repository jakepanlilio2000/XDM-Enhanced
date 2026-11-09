package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.AboutDialog
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*

class Phase8AboutDialogTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @BeforeEach
    fun initBase() {
        xdm.app.I8N.loadTexts("en")
        artifactDir.mkdirs()
    }

    @AfterEach
    fun cleanUp() {
        // Restore default theme
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
        }
    }

    private fun <T> runOnEdt(action: () -> T): T {
        var result: T? = null
        var err: Throwable? = null
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        SwingUtilities.invokeAndWait {
            try {
                result = action()
            } catch (t: Throwable) {
                err = t
            }
        }
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun captureComponent(comp: Component, width: Int, height: Int, file: File) {
        comp.setSize(width, height)
        comp.addNotify()
        comp.validate()
        val target = (comp as? java.awt.Window)?.let { (it as? javax.swing.RootPaneContainer)?.contentPane } ?: comp
        target.setSize(width, height)
        target.doLayout()
        target.validate()

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            target.paint(g)
        } finally {
            g.dispose()
        }
        ImageIO.write(img, "PNG", file)
    }

    private fun findAllComponents(container: Container): List<Component> {
        val list = mutableListOf<Component>()
        for (c in container.components) {
            list.add(c)
            if (c is Container) {
                list.addAll(findAllComponents(c))
            }
        }
        return list
    }

    @Test
    fun testAboutDialogBrandingAndVersionMetadata() {
        runOnEdt {
            val about = AboutDialog(null)
            try {
                assertEquals("About XDM Enhanced", about.title)
                assertFalse(about.isModal, "About dialog should be non-modal")

                val all = findAllComponents(about.contentPane)
                val labels = all.filterIsInstance<JLabel>()

                // 1. App name
                val nameLabel = labels.find { it.text == "XDM Enhanced" }
                assertNotNull(nameLabel, "Must display 'XDM Enhanced'")

                // 2. Version 1.0.0-beta.4 from build metadata
                val verLabel = labels.find { it.text?.contains("v1.0.0-beta.4") == true }
                assertNotNull(verLabel, "Must display version from build metadata (v1.0.0-beta.4)")

                // 3. Release channel
                val channelLabel = labels.find { it.text?.contains("Beta Channel") == true }
                assertNotNull(channelLabel, "Must display 'Beta Channel' badge")

                // 4. Icon
                val iconLabel = labels.find { it.icon != null }
                assertNotNull(iconLabel, "Must include application logo icon")
            } finally {
                about.dispose()
            }
        }
    }

    @Test
    fun testGitHubIntegrationAndActions() {
        runOnEdt {
            val about = AboutDialog(null)
            try {
                val all = findAllComponents(about.contentPane)
                val buttons = all.filterIsInstance<JButton>()

                // 1. "Star on GitHub" action
                val starBtn = buttons.find { it.text == "Star on GitHub" }
                assertNotNull(starBtn, "Must have a 'Star on GitHub' button")
                assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced", starBtn!!.toolTipText)
                assertNotNull(starBtn.icon, "Star button must have an icon")

                // 2. "Source Code" action
                val sourceBtn = buttons.find { it.text == "Source Code" }
                assertNotNull(sourceBtn, "Must have a 'Source Code' button")
                assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced", sourceBtn!!.toolTipText)

                // 3. "Upstream Website" action
                val websiteBtn = buttons.find { it.text == "Upstream Website" }
                assertNotNull(websiteBtn, "Must have an 'Upstream Website' button")
                assertEquals("https://xtremedownloadmanager.com", websiteBtn!!.toolTipText)
            } finally {
                about.dispose()
            }
        }
    }

    @Test
    fun testLegalAttributionAndUpstreamAcknowledgment() {
        runOnEdt {
            val about = AboutDialog(null)
            try {
                val all = findAllComponents(about.contentPane)
                val labels = all.filterIsInstance<JLabel>()
                val textContent = labels.mapNotNull { it.text }.joinToString(" ")

                // Upstream acknowledgment
                assertTrue(textContent.contains("Subhra Das Gupta"), "Must acknowledge original upstream developer Subhra Das Gupta")
                assertTrue(textContent.contains("Xtreme Download Manager"), "Must mention Xtreme Download Manager")

                // Independent maintenance notice
                assertTrue(textContent.contains("independent community fork"), "Must clearly state independent community fork")
                assertTrue(textContent.contains("not endorsed by"), "Must state it is not endorsed by original project")

                // License notice
                assertTrue(textContent.contains("GPL-2.0") || textContent.contains("GNU General Public License"), "Must display GPL-2.0 license")
            } finally {
                about.dispose()
            }
        }
    }

    @Test
    fun testSystemDiagnosticsAndCopyAction() {
        runOnEdt {
            val about = AboutDialog(null)
            try {
                val all = findAllComponents(about.contentPane)
                val tabbedPane = all.filterIsInstance<JTabbedPane>().firstOrNull()
                assertNotNull(tabbedPane, "Should have JTabbedPane")
                assertEquals(3, tabbedPane!!.tabCount)
                assertEquals("Overview", tabbedPane.getTitleAt(0))
                assertEquals("System Info", tabbedPane.getTitleAt(1))
                assertEquals("Notices & Credits", tabbedPane.getTitleAt(2))

                // Switch to System Info tab
                tabbedPane.selectedIndex = 1

                val buttons = all.filterIsInstance<JButton>()
                val copyBtn = buttons.find { it.text.contains("Copy System Diagnostics") }
                assertNotNull(copyBtn, "Must have 'Copy System Diagnostics' button")

                // Test copying diagnostics to clipboard
                copyBtn!!.doClick()
                val clipboardText = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    .getData(DataFlavor.stringFlavor) as? String
                assertNotNull(clipboardText)
                assertTrue(clipboardText!!.contains("Application Version") && clipboardText.contains("XDM Enhanced v1.0.0-beta.4"))
                assertTrue(clipboardText.contains("Java Runtime"))
                assertTrue(clipboardText.contains("Operating System"))
                assertTrue(clipboardText.contains("BitTorrent Engine"))
            } finally {
                about.dispose()
            }
        }
    }

    @Test
    fun testRepeatedOpenAndCloseCycle() {
        runOnEdt {
            // Verify dialog can be opened and closed 5 times without leaking or exceptions
            for (i in 1..5) {
                val dialog = AboutDialog(null)
                assertEquals("About XDM Enhanced", dialog.title)
                dialog.pack()
                dialog.dispose()
            }
        }
    }

    @Test
    fun testSingleWindowsManagement() {
        runOnEdt {
            val d1 = AboutDialog.open(null)
            try {
                assertTrue(d1.isVisible)
                val d2 = AboutDialog.open(null)
                assertSame(d1, d2, "Calling open again must return the identical existing window instance")
            } finally {
                d1.dispose()
            }

            // After disposing, opening again must create a fresh instance
            val d3 = AboutDialog.open(null)
            try {
                assertNotSame(d1, d3, "Opening after disposal must instantiate a new window")
            } finally {
                d3.dispose()
            }
        }
    }

    @Test
    fun testKeyboardDismissalAndDefaultButton() {
        runOnEdt {
            val dialog = AboutDialog(null)
            try {
                dialog.isVisible = true
                assertNotNull(dialog.rootPane.defaultButton, "Must have default button")
                assertEquals("Close", dialog.rootPane.defaultButton?.text)

                // Verify and trigger Escape action registered on rootPane
                val actionKey = dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
                )
                assertEquals("closeAbout", actionKey)
                val action = dialog.rootPane.actionMap.get(actionKey)
                assertNotNull(action, "Must have an action registered for ESC key")
                action.actionPerformed(java.awt.event.ActionEvent(dialog.rootPane, java.awt.event.ActionEvent.ACTION_PERFORMED, ""))
                assertFalse(dialog.isDisplayable, "ESC action must dispose the dialog")
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun testFallbackCopyToClipboard() {
        runOnEdt {
            val dialog = AboutDialog(null)
            try {
                val testUrl = "https://github.com/jakepanlilio2000/XDM-Enhanced"
                dialog.fallbackCopyToClipboard(testUrl, showNotice = false)
                val clipText = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    .getData(DataFlavor.stringFlavor) as? String
                assertEquals(testUrl, clipText, "Fallback must place exact URL on system clipboard")
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun testVisualScreenshotsAcrossThemes() {
        runOnEdt {
            // 1. Light Theme Overview
            ThemeManager.applyTheme("light", null, 100)
            val aboutLight = AboutDialog(null)
            captureComponent(aboutLight, 500, 480, File(artifactDir, "screenshot-phase8-about-dialog-light.png"))
            aboutLight.dispose()

            // 2. Dark Theme Overview
            ThemeManager.applyTheme("dark", null, 100)
            val aboutDark = AboutDialog(null)
            captureComponent(aboutDark, 500, 480, File(artifactDir, "screenshot-phase8-about-dialog-dark.png"))

            // 3. System Info Tab in Dark Theme
            val tabbedPane = findAllComponents(aboutDark.contentPane).filterIsInstance<JTabbedPane>().first()
            tabbedPane.selectedIndex = 1
            captureComponent(aboutDark, 500, 480, File(artifactDir, "screenshot-phase8-about-dialog-system-tab.png"))

            // 4. Notices & Credits Tab in Dark Theme
            tabbedPane.selectedIndex = 2
            captureComponent(aboutDark, 500, 480, File(artifactDir, "screenshot-phase8-about-dialog-notices-tab.png"))

            aboutDark.dispose()
        }
    }
}
