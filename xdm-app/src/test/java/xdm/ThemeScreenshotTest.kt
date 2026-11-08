package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.AboutDialog
import xdm.app.ui.screens.AppWindow
import xdm.app.ui.screens.SettingsWindow
import xdm.app.utils.logoImage
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.SwingUtilities

class ThemeScreenshotTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

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
        target.paint(g)
        g.dispose()

        ImageIO.write(img, "png", file)
        assertTrue(file.exists() && file.length() > 2000, "Screenshot created: ${file.name} (${file.length()} bytes)")
    }

    @Test
    fun captureAllThemeScreenshots() {
        artifactDir.mkdirs()

        // Insert sample realistic records
        val rec1 = DbRecord(
            id = 101L,
            size = 104857600L,
            downloaded = 68157440L,
            progress = 65,
            date = System.currentTimeMillis() - 3600000,
            fileName = "XDM-Enhanced-v1.0.0-beta.1-windows-x64.zip",
            eta = 42L,
            speed = 3450000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
            categoryId = "CAT_COMPRESSED"
        )
        val rec2 = DbRecord(
            id = 102L,
            size = 524288000L,
            downloaded = 524288000L,
            progress = 100,
            date = System.currentTimeMillis() - 7200000,
            fileName = "developer_guide_reference.pdf",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.FINISHED,
            downloadType = DownloadType.Http,
            categoryId = "CAT_DOCUMENTS"
        )
        val rec3 = DbRecord(
            id = 103L,
            size = 1420000000L,
            downloaded = 355000000L,
            progress = 25,
            date = System.currentTimeMillis() - 1800000,
            fileName = "archlinux-2026.iso",
            eta = 310L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Torrent,
            categoryId = null
        )
        AppContext.db.add(rec1)
        AppContext.db.add(rec2)
        AppContext.db.add(rec3)

        // 1. Dark Theme Main Window
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1020, 620, File(artifactDir, "screenshot-theme-dark.png"))
            window.dispose()
        }

        // 2. Light Theme Main Window
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1020, 620, File(artifactDir, "screenshot-theme-light.png"))
            window.dispose()
        }

        // 3. Custom Accent: Emerald (#10B981) in Dark Theme
        runOnEdt {
            ThemeManager.applyTheme("dark", Color(0x10, 0xB9, 0x81), 100)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1020, 620, File(artifactDir, "screenshot-theme-emerald-accent.png"))
            window.dispose()
        }

        // 4. Custom Accent: Purple (#8B5CF6) in Light Theme
        runOnEdt {
            ThemeManager.applyTheme("light", Color(0x8B, 0x5C, 0xF6), 100)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1020, 620, File(artifactDir, "screenshot-theme-purple-accent.png"))
            window.dispose()
        }

        // 5. Settings Window with Appearance & Accent Picker
        runOnEdt {
            ThemeManager.applyTheme("dark", Color(0x3B, 0x82, 0xF6), 100)
            val settings = SettingsWindow()
            settings.loadConfig()
            captureComponent(settings, 780, 500, File(artifactDir, "screenshot-settings-appearance.png"))
            settings.dispose()
        }

        // 6. About Dialog in Light Mode
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val about = SingleWindowsReflect.createAboutDialog()
            captureComponent(about, 420, 400, File(artifactDir, "screenshot-about-light.png"))
            about.dispose()
        }

        // Restore default
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
        }
    }
}

