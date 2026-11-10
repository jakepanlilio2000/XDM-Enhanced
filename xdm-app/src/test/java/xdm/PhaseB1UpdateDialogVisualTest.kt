package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.UpdateDialog
import xdm.app.ui.screens.settings.GeneralPanel
import xdm.app.update.GithubAsset
import xdm.app.update.ReleaseInfo
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*

class PhaseB1UpdateDialogVisualTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    private val sampleRelease = ReleaseInfo(
        version = "1.0.0-beta.5",
        releaseName = "XDM Enhanced v1.0.0-beta.5",
        releaseNotes = """
            ## XDM Enhanced v1.0.0-beta.5
            
            ### Improvements & Fixes
            * Extensible skin engine and custom skin packages (.xdmskin)
            * Decoupled Java plugin framework and public developer API
            * Floating browser video downloader with quality selection
        """.trimIndent(),
        htmlUrl = "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.0-beta.5",
        isPreRelease = true,
        publishedAt = "2026-11-10T04:00:00Z",
        installerAsset = GithubAsset(
            name = "XDM-Enhanced-Setup-v1.0.0-beta.5-win-x64.exe",
            size = 54525952L,
            browser_download_url = "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.5/XDM-Enhanced-Setup-v1.0.0-beta.5-win-x64.exe"
        ),
        checksumAsset = GithubAsset(
            name = "SHA256SUMS.txt",
            size = 512L,
            browser_download_url = "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.5/SHA256SUMS.txt"
        )
    )

    @BeforeEach
    fun initBase() {
        xdm.app.I8N.loadTexts("en")
        artifactDir.mkdirs()
    }

    @AfterEach
    fun cleanUp() {
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
    fun testUpdateDialogAvailableRenderingDarkAndLight() {
        runOnEdt {
            // Dark theme capture
            ThemeManager.applyTheme("dark", null, 100)
            val dialogDark = UpdateDialog(null, false, sampleRelease)
            try {
                val fileDark = File(artifactDir, "screenshot-b1-update-dialog-available-dark.png")
                captureComponent(dialogDark, 580, 460, fileDark)
                assertTrue(fileDark.exists() && fileDark.length() > 0)

                val all = findAllComponents(dialogDark.contentPane)
                val labels = all.filterIsInstance<JLabel>()
                assertTrue(labels.any { it.text?.contains("v1.0.0-beta.5") == true })
                assertTrue(labels.any { it.text?.contains("Beta / Pre-release") == true })
            } finally {
                dialogDark.dispose()
            }

            // Light theme capture
            ThemeManager.applyTheme("light", null, 100)
            val dialogLight = UpdateDialog(null, false, sampleRelease)
            try {
                val fileLight = File(artifactDir, "screenshot-b1-update-dialog-available-light.png")
                captureComponent(dialogLight, 580, 460, fileLight)
                assertTrue(fileLight.exists() && fileLight.length() > 0)
            } finally {
                dialogLight.dispose()
            }
        }
    }

    @Test
    fun testUpdateDialogUpToDateAndReadyStates() {
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val dialog = UpdateDialog(null, false, sampleRelease)
            try {
                // Up to date card
                dialog.showCard(UpdateDialog.CARD_UP_TO_DATE)
                val fileUpToDate = File(artifactDir, "screenshot-b1-update-dialog-uptodate-dark.png")
                captureComponent(dialog, 580, 460, fileUpToDate)
                assertTrue(fileUpToDate.exists() && fileUpToDate.length() > 0)

                // Ready to install card
                dialog.showCard(UpdateDialog.CARD_READY)
                val fileReady = File(artifactDir, "screenshot-b1-update-dialog-ready-dark.png")
                captureComponent(dialog, 580, 460, fileReady)
                assertTrue(fileReady.exists() && fileReady.length() > 0)
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun testGeneralPanelSoftwareUpdatesSection() {
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val panel = GeneralPanel()
            panel.load()
            panel.setSize(750, 700)
            panel.addNotify()
            panel.validate()

            val fileSettings = File(artifactDir, "screenshot-b1-settings-software-updates.png")
            captureComponent(panel, 750, 700, fileSettings)
            assertTrue(fileSettings.exists() && fileSettings.length() > 0)

            val all = findAllComponents(panel)
            val labels = all.filterIsInstance<JLabel>()
            assertTrue(labels.any { it.text?.contains("Software Updates", ignoreCase = true) == true }, "Must have Software Updates section header")
            assertTrue(labels.any { it.text?.contains("Automatically check for updates") == true }, "Must have auto check row")
            assertTrue(labels.any { it.text?.contains("Update channel") == true }, "Must have update channel row")

            val buttons = all.filterIsInstance<JButton>()
            assertTrue(buttons.any { it.text == "Check Now" }, "Must have Check Now button")
        }
    }
}

