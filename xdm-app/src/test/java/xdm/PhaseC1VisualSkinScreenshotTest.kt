package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.skins.SkinManager
import xdm.app.ui.components.ThemeManager
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
import javax.swing.SwingUtilities

class PhaseC1VisualSkinScreenshotTest : DownloadManagerTestBase() {

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
    fun captureAllSkinVisualScreenshots() {
        xdm.app.I8N.loadTexts("en")
        artifactDir.mkdirs()

        // Populate sample realistic downloads to show icons, categories, rows, and progress bars
        val rec1 = DbRecord(
            id = 201L,
            size = 104857600L,
            downloaded = 72157440L,
            progress = 68,
            date = System.currentTimeMillis() - 3600000,
            fileName = "XDM-Enhanced-v1.0.0-beta.5-windows-x64.zip",
            eta = 28L,
            speed = 4250000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
            categoryId = "CAT_COMPRESSED"
        )
        val rec2 = DbRecord(
            id = 202L,
            size = 524288000L,
            downloaded = 524288000L,
            progress = 100,
            date = System.currentTimeMillis() - 7200000,
            fileName = "xdm_skin_engine_architecture_guide.pdf",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.FINISHED,
            downloadType = DownloadType.Http,
            categoryId = "CAT_DOCUMENTS"
        )
        val rec3 = DbRecord(
            id = 203L,
            size = 1420000000L,
            downloaded = 355000000L,
            progress = 25,
            date = System.currentTimeMillis() - 1800000,
            fileName = "debian-live-12.0.0-amd64.iso",
            eta = 310L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Torrent,
            categoryId = "CAT_PROGRAMS"
        )
        AppContext.db.add(rec1)
        AppContext.db.add(rec2)
        AppContext.db.add(rec3)

        // 1. Built-in Skin: XDM Enhanced in Dark Mode
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1040, 640, File(artifactDir, "screenshot-skin-enhanced-dark.png"))
            window.dispose()
        }

        // 2. Built-in Skin: XDM Enhanced in Light Mode
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
            ThemeManager.applyTheme("light", null, 100, saveConfig = false)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1040, 640, File(artifactDir, "screenshot-skin-enhanced-light.png"))
            window.dispose()
        }

        // 3. Built-in Skin: Original XDM (Classic Upstream preset) in Dark Mode
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1040, 640, File(artifactDir, "screenshot-skin-classic-dark.png"))
            window.dispose()
        }

        // 4. Built-in Skin: Original XDM in Light Mode
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
            ThemeManager.applyTheme("light", null, 100, saveConfig = false)
            val window = AppWindow(logoImage(256), startHidden = true)
            window.updateDownloadInView(0)
            captureComponent(window, 1040, 640, File(artifactDir, "screenshot-skin-classic-light.png"))
            window.dispose()
        }

        // 5. Settings Window: Appearance & Skin Manager with live SkinPreviewCard
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
            ThemeManager.applyTheme("dark", Color(0x3B, 0x82, 0xF6), 100, saveConfig = false)
            val settings = SettingsWindow()
            settings.loadConfig()
            settings.selectPage("SETTINGS_SEC_APPEARANCE")
            captureComponent(settings, 780, 560, File(artifactDir, "screenshot-settings-skin-manager.png"))
            settings.dispose()
        }

        // 6. Custom Skin: Cyber Neon in Dark Mode
        val repoRoot = File("..").canonicalFile.let {
            if (File(it, "pom.xml").exists()) it else File(".").canonicalFile
        }
        val cyberNeonArchive = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        if (cyberNeonArchive.exists()) {
            runOnEdt {
                SkinManager.installSkin(cyberNeonArchive)
                SkinManager.applySkin("cyber-neon", saveConfig = false)
                ThemeManager.applyTheme("dark", Color(0x00, 0xF0, 0xFF), 100, saveConfig = false)
                val window = AppWindow(logoImage(256), startHidden = true)
                window.updateDownloadInView(0)
                captureComponent(window, 1040, 640, File(artifactDir, "screenshot-skin-custom-cyber-neon-dark.png"))
                window.dispose()
            }

            // 7. Custom Skin: Cyber Neon in Light Mode
            runOnEdt {
                SkinManager.applySkin("cyber-neon", saveConfig = false)
                ThemeManager.applyTheme("light", Color(0x02, 0x84, 0xC7), 100, saveConfig = false)
                val window = AppWindow(logoImage(256), startHidden = true)
                window.updateDownloadInView(0)
                captureComponent(window, 1040, 640, File(artifactDir, "screenshot-skin-custom-cyber-neon-light.png"))
                window.dispose()
            }

            // 8. Settings Window with Custom Skin selected
            runOnEdt {
                SkinManager.applySkin("cyber-neon", saveConfig = false)
                ThemeManager.applyTheme("dark", Color(0x00, 0xF0, 0xFF), 100, saveConfig = false)
                val settings = SettingsWindow()
                settings.loadConfig()
                settings.selectPage("SETTINGS_SEC_APPEARANCE")
                captureComponent(settings, 780, 560, File(artifactDir, "screenshot-settings-custom-skins.png"))
                settings.dispose()
            }
        }

        // Restore clean default state
        runOnEdt {
            SkinManager.restoreDefaults()
        }
    }
}

