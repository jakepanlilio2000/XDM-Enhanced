package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.ui.components.BrowserExtensionPanel
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.BrowserSetupDialog
import xdm.app.ui.screens.settings.BrowserMonitorPanel
import xdm.app.ui.screens.settings.settingsCard
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities

class BrowserSettingsScreenshotTest {

    private val artifactDir = File(System.getProperty("user.home"), ".gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

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
        target.paint(g)
        g.dispose()

        ImageIO.write(img, "png", file)
        assertTrue(file.exists() && file.length() > 2000, "Screenshot created: ${file.name}")
    }

    @Test
    fun captureBrowserSettingsUi() {
        artifactDir.mkdirs()

        // 1. Browser Setup Dialog in Dark Mode
        SwingUtilities.invokeAndWait {
            ThemeManager.applyTheme("dark", Color(0x3B, 0x82, 0xF6), 100)
            val dialog = BrowserSetupDialog(null)
            captureComponent(dialog, 580, 420, File(artifactDir, "screenshot-browser-tiles-dark.png"))
            dialog.dispose()
        }

        // 2. Browser Setup Dialog in Light Mode
        SwingUtilities.invokeAndWait {
            ThemeManager.applyTheme("light", Color(0x3B, 0x82, 0xF6), 100)
            val dialog = BrowserSetupDialog(null)
            captureComponent(dialog, 580, 420, File(artifactDir, "screenshot-browser-tiles-light.png"))
            dialog.dispose()
        }
    }
}

