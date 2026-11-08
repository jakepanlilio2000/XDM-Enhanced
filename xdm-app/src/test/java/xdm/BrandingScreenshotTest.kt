package xdm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.AppMain
import xdm.app.AppContext
import xdm.app.ui.screens.AboutDialog
import xdm.app.ui.screens.AppWindow
import xdm.app.utils.logoImage
import xdm.app.utils.windowsLogoImage
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class BrandingScreenshotTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @Test
    fun captureBrandingArtifacts() {
        artifactDir.mkdirs()

        // 1. Setup Theme
        runOnEdt {
            AppMain.setupTheme("dark", 100)
        }

        // 2. Capture and Verify About Dialog
        runOnEdt {
            val about = SingleWindowsReflect.createAboutDialog()
            assertEquals("About XDM Enhanced", about.title)

            about.setSize(420, 400)
            about.addNotify()
            about.validate()
            val cp = about.contentPane
            cp.setSize(420, 400)
            cp.doLayout()
            cp.validate()

            val img = BufferedImage(420, 400, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            cp.paint(g)
            g.dispose()

            val outFile = File(artifactDir, "screenshot-about-dialog.png")
            ImageIO.write(img, "png", outFile)
            assertTrue(outFile.exists() && outFile.length() > 2000, "About dialog screenshot created: ${outFile.length()} bytes")
            about.dispose()
        }

        // 3. Capture and Verify Main Window
        runOnEdt {
            val appWindow = AppWindow(logoImage(256), startHidden = true)
            assertEquals("XDM Enhanced", appWindow.title)

            appWindow.setSize(1000, 620)
            appWindow.addNotify()
            appWindow.validate()
            val cp = appWindow.contentPane
            cp.setSize(1000, 620)
            cp.doLayout()
            cp.validate()

            val img = BufferedImage(1000, 620, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            cp.paint(g)
            g.dispose()

            val outFile = File(artifactDir, "screenshot-main-window.png")
            ImageIO.write(img, "png", outFile)
            assertTrue(outFile.exists() && outFile.length() > 5000, "Main window screenshot created: ${outFile.length()} bytes")
            appWindow.dispose()
        }

        // 4. Generate Icon Grid Comparison (on dark and light backgrounds)
        val gridWidth = 840
        val gridHeight = 440
        val gridImg = BufferedImage(gridWidth, gridHeight, BufferedImage.TYPE_INT_ARGB)
        val g2 = gridImg.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Dark background section (top half)
        g2.color = Color(20, 24, 34)
        g2.fillRect(0, 0, gridWidth, gridHeight / 2)
        // Light background section (bottom half)
        g2.color = Color(245, 247, 250)
        g2.fillRect(0, gridHeight / 2, gridWidth, gridHeight / 2)

        g2.font = Font("SansSerif", Font.BOLD, 14)
        g2.color = Color(148, 163, 184)
        g2.drawString("XDM Enhanced Brand Icon — Dark Background Preview", 24, 28)
        g2.color = Color(71, 85, 105)
        g2.drawString("XDM Enhanced Brand Icon — Light Background Preview", 24, gridHeight / 2 + 28)

        val sizes = intArrayOf(16, 24, 32, 48, 64, 128)
        var xOffset = 30
        for (s in sizes) {
            val icon = logoImage(s)
            // Dark row
            g2.drawImage(icon, xOffset, 50 + (128 - s) / 2, null)
            // Light row
            g2.drawImage(icon, xOffset, gridHeight / 2 + 50 + (128 - s) / 2, null)

            g2.font = Font("SansSerif", Font.PLAIN, 11)
            g2.color = Color(148, 163, 184)
            g2.drawString("${s}px", xOffset + (s / 2) - 10, 195)
            g2.color = Color(100, 116, 139)
            g2.drawString("${s}px", xOffset + (s / 2) - 10, gridHeight / 2 + 195)

            xOffset += s + 36
        }

        // Draw 256px hero icon on the right
        val hero256 = logoImage(256)
        val scaledHero = BufferedImage(140, 140, BufferedImage.TYPE_INT_ARGB).apply {
            val hg = createGraphics()
            hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            hg.drawImage(hero256, 0, 0, 140, 140, null)
            hg.dispose()
        }
        g2.drawImage(scaledHero, gridWidth - 170, 44, null)
        g2.drawImage(scaledHero, gridWidth - 170, gridHeight / 2 + 44, null)
        g2.drawString("256px (Scaled)", gridWidth - 146, 195)
        g2.drawString("256px (Scaled)", gridWidth - 146, gridHeight / 2 + 195)

        g2.dispose()

        val gridFile = File(artifactDir, "icon-preview-grid.png")
        ImageIO.write(gridImg, "png", gridFile)
        assertTrue(gridFile.exists() && gridFile.length() > 0, "Icon grid preview created")
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
}

object SingleWindowsReflect {
    fun createAboutDialog(): AboutDialog {
        val constructor = AboutDialog::class.java.getDeclaredConstructor(java.awt.Window::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(null)
    }
}
