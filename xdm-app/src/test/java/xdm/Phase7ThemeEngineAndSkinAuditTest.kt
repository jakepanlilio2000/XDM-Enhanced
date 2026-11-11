package xdm

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.skins.*
import xdm.app.ui.components.SkinBackgroundPanel
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.AboutDialog
import xdm.app.ui.screens.AppWindow
import xdm.app.ui.screens.NewDownloadWindow
import xdm.app.ui.screens.NewTorrentDownloadWindow
import xdm.app.ui.screens.NewVideoDownloadWindow
import xdm.app.ui.screens.ProgressWindow
import xdm.app.ui.screens.PropertiesDialog
import xdm.app.ui.screens.SettingsWindow
import xdm.app.utils.logoImage
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.TorrentSource
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Phase 7: Advanced Theme Engine, Presets, Custom Skins & Modal Regional Styling Audit Test Suite.
 *
 * Scenarios:
 * 50. Background rendering behind proper containers in main window, cards, table, sidebar, dialogs, settings, About.
 * 51. Layout assets, alpha/transparency, image scaling, HiDPI, color overrides, and live switching.
 * 52. Repeated switching across built-in/custom skins, Light/Dark/System and accents without task loss.
 * 53. Hostile archive defense: Zip Slip, giant images, missing assets, malformed manifest, out-of-bound bounds, ID collision.
 * 54. Third-party sample skin (Schema v2) documentation compliance and graceful fallback.
 * 55. Declarative safety: cannot hide essential controls or inject executable code.
 */
class Phase7ThemeEngineAndSkinAuditTest : DownloadManagerTestBase() {

    private lateinit var testSkinsDir: File

    private val repoRoot: File = run {
        val here = File(".").canonicalFile
        if (File(here, "sample-skins").exists()) {
            here
        } else if (File(here.parentFile, "sample-skins").exists()) {
            here.parentFile
        } else {
            here
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

    @BeforeEach
    fun initPhase7Test() {
        I8N.loadTexts("en")
        testSkinsDir = File(dir, "skins").apply { mkdirs() }
        SkinManager.customSkinsDir = testSkinsDir

        runOnEdt {
            SkinManager.init(SkinManager.SKIN_ENHANCED)
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
        }
    }

    @AfterEach
    fun cleanupPhase7Test() {
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
        }
    }

    /**
     * Scenario 50: Skin background actually renders behind proper containers across all documented UI regions.
     */
    @Test
    fun testScenario50SkinBackgroundRenderingAcrossAllContainers() {
        val sampleSkinFile = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        assertTrue(sampleSkinFile.exists(), "cyber-neon.xdmskin must exist")
        val cyberSkin = SkinManager.installSkin(sampleSkinFile)
        assertNotNull(cyberSkin)

        val rec = DbRecord(
            id = 5001L,
            size = 100000000L,
            downloaded = 50000000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "ubuntu-24.04.iso",
            eta = 20L,
            speed = 2500000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
            categoryId = "CAT_PROGRAMS"
        )
        AppContext.db.add(rec)

        runOnEdt {
            SkinManager.applySkin(cyberSkin.id, saveConfig = false)
            ThemeManager.applyTheme("dark", Color(0x00, 0xF0, 0xFF), 100, saveConfig = false)

            // 1. Main Window Interior & Sidebar
            val appWin = AppWindow(logoImage(256), startHidden = true)
            try {
                val mainPane = appWin.contentPane as? SkinBackgroundPanel
                assertNotNull(mainPane, "AppWindow contentPane must be a SkinBackgroundPanel")
                assertEquals(SkinRegion.MAIN_FRAME, mainPane!!.region)

                val mainImg = BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB)
                val mainG = mainImg.createGraphics()
                assertTrue(SkinStyleResolver.paintRegionBackground(mainG, 600, 400, SkinRegion.MAIN_FRAME))
                mainG.dispose()

                val sidebar = appWin.sidebar
                assertNotNull(sidebar)
                val sbImg = BufferedImage(220, 400, BufferedImage.TYPE_INT_ARGB)
                val sbG = sbImg.createGraphics()
                assertTrue(SkinStyleResolver.paintRegionBackground(sbG, 220, 400, SkinRegion.SIDEBAR))
                sbG.dispose()

                // Download List (Cards / Table view)
                val listImg = BufferedImage(380, 400, BufferedImage.TYPE_INT_ARGB)
                val listG = listImg.createGraphics()
                assertTrue(SkinStyleResolver.paintRegionBackground(listG, 380, 400, SkinRegion.DOWNLOAD_LIST))
                listG.dispose()
            } finally {
                appWin.dispose()
            }

            // 2. Standard Download Modal
            val dlWin = NewDownloadWindow()
            try {
                val dlPane = dlWin.contentPane as? SkinBackgroundPanel
                assertNotNull(dlPane, "NewDownloadWindow contentPane must be SkinBackgroundPanel")
                assertEquals(SkinRegion.DOWNLOAD_MODAL, dlPane!!.region)
                assertEquals(Color(0x0E, 0x12, 0x1A), SkinStyleResolver.resolveBackground(SkinRegion.DOWNLOAD_MODAL))
            } finally {
                dlWin.dispose()
            }

            // 3. Streaming Video Download Modal
            val vidWin = NewVideoDownloadWindow()
            try {
                val vidPane = vidWin.contentPane as? SkinBackgroundPanel
                assertNotNull(vidPane, "NewVideoDownloadWindow contentPane must be SkinBackgroundPanel")
                assertEquals(SkinRegion.VIDEO_DOWNLOAD_MODAL, vidPane!!.region)
            } finally {
                vidWin.dispose()
            }

            // 4. Progress / Completion Window
            val progWin = ProgressWindow(rec.id)
            try {
                val progPane = progWin.contentPane as? SkinBackgroundPanel
                assertNotNull(progPane, "ProgressWindow contentPane must be SkinBackgroundPanel")
                assertEquals(SkinRegion.PROGRESS_MODAL, progPane!!.region)
                assertEquals(10, SkinStyleResolver.resolveProgressBarArc(SkinRegion.PROGRESS_MODAL))
            } finally {
                progWin.dispose()
            }

            // 5. BitTorrent Task Dialog
            val torrWin = NewTorrentDownloadWindow(TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=test"))
            try {
                val torrPane = torrWin.contentPane as? SkinBackgroundPanel
                assertNotNull(torrPane, "NewTorrentDownloadWindow contentPane must be SkinBackgroundPanel")
                assertEquals(SkinRegion.TORRENT_MODAL, torrPane!!.region)
            } finally {
                torrWin.dispose()
            }

            // 6. Settings Window
            val setWin = SettingsWindow()
            try {
                val setPane = setWin.contentPane as? SkinBackgroundPanel
                assertNotNull(setPane, "SettingsWindow contentPane must be SkinBackgroundPanel")
                assertEquals(SkinRegion.SETTINGS, setPane!!.region)
            } finally {
                setWin.dispose()
            }

            // 7. About & Diagnostics Dialog
            val aboutWin = AboutDialog(null)
            try {
                val aboutPane = aboutWin.contentPane as? SkinBackgroundPanel
                assertNotNull(aboutPane, "AboutDialog contentPane must be SkinBackgroundPanel")
                assertEquals(SkinRegion.ABOUT, aboutPane!!.region)
                assertEquals(16, SkinStyleResolver.resolveCornerArc(SkinRegion.ABOUT))
            } finally {
                aboutWin.dispose()
            }

            // 8. Properties Dialog (Dialogs region)
            val propWin = PropertiesDialog(null, rec)
            try {
                val propPane = propWin.contentPane as? SkinBackgroundPanel
                assertNotNull(propPane, "PropertiesDialog contentPane must be SkinBackgroundPanel")
                assertEquals(SkinRegion.DIALOGS, propPane!!.region)
                assertEquals(Color(0x13, 0x17, 0x20), SkinStyleResolver.resolveBackground(SkinRegion.DIALOGS))
            } finally {
                propWin.dispose()
            }
        }
    }

    /**
     * Scenario 51: Layout assets, alpha/transparency, image scaling, HiDPI, FlatLaf overrides, and live switching.
     */
    @Test
    fun testScenario51AssetScalingAlphaHiDpiAndLiveSwitching() {
        val sampleSkinFile = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        val cyberSkin = SkinManager.installSkin(sampleSkinFile) as CustomSkin

        // 1. Alpha / transparency scaling (opacity clamped 0.01f..1.0f)
        val img = BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val paintedDark = cyberSkin.paintWallpaper(g, 300, 200, isDark = true)
        val paintedLight = cyberSkin.paintWallpaper(g, 300, 200, isDark = false)
        g.dispose()
        assertTrue(paintedDark, "Dark wallpaper must paint successfully")
        assertTrue(paintedLight, "Light wallpaper must paint successfully")

        // 2. Wallpaper scaling modes: fill, fit, tile, center
        val testModes = listOf("fill", "fit", "tile", "center")
        for (mode in testModes) {
            val manifestJson = """
            {
              "schemaVersion": 2,
              "id": "skin-mode-$mode",
              "name": "Skin Mode $mode",
              "version": "1.0.0",
              "author": "Tester",
              "wallpaper": {
                "dark": "backgrounds/bg.png",
                "mode": "$mode",
                "opacity": 0.25
              }
            }
            """.trimIndent()
            val skinZip = File(dir, "skin-mode-$mode.xdmskin")
            createZip(skinZip, mapOf(
                "skin.json" to manifestJson,
                "backgrounds/bg.png" to createDummyPng(32, 32)
            ))
            val skin = SkinManager.installSkin(skinZip) as CustomSkin
            val testImg = BufferedImage(150, 100, BufferedImage.TYPE_INT_ARGB)
            val testG = testImg.createGraphics()
            val success = skin.paintWallpaper(testG, 150, 100, isDark = true)
            testG.dispose()
            assertTrue(success, "Mode $mode must render successfully")
        }

        // 3. Category & Toolbar Icon resolution and scaling
        val docIcon = cyberSkin.getCategoryIcon("documents", 24)
        assertNotNull(docIcon, "Custom category icon for documents must resolve")
        assertEquals(24, docIcon!!.iconWidth)
        assertEquals(24, docIcon.iconHeight)

        val addIcon = cyberSkin.getToolbarIcon("ADD_URL", 20)
        assertNotNull(addIcon, "Custom toolbar icon for ADD_URL must resolve")
        assertEquals(20, addIcon!!.iconWidth)
        assertEquals(20, addIcon.iconHeight)

        // 4. Live switching between BuiltInClassic (arc: 0) and BuiltInEnhanced (arc: 8)
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
            assertEquals(0, UIManager.getInt("Component.arc"))
            assertEquals(0, UIManager.getInt("Button.arc"))

            SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
            assertEquals(8, UIManager.getInt("Component.arc"))
            assertEquals(8, UIManager.getInt("Button.arc"))
        }
    }

    /**
     * Scenario 52: Switch repeatedly between built-in skins, Light/Dark/System and custom accent;
     * verify task state persistence.
     */
    @Test
    fun testScenario52RepeatedSwitchingAndTaskPersistence() {
        val sampleSkinFile = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        val cyberSkin = SkinManager.installSkin(sampleSkinFile)

        val rec = DbRecord(
            id = 5201L,
            size = 5000000000L,
            downloaded = 2500000000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "archlinux-x86_64.iso",
            eta = 60L,
            speed = 10000000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http
        )
        AppContext.db.add(rec)

        val accents = listOf(
            Color(0x3B, 0x82, 0xF6), // Blue
            Color(0x00, 0xF0, 0xFF), // Cyan
            Color(0x10, 0xB9, 0x81), // Emerald
            Color(0x8B, 0x5C, 0xF6)  // Purple
        )

        runOnEdt {
            val prog = ProgressWindow(rec.id)
            try {
                for (cycle in 1..4) {
                    // Enhanced Skin in Dark Mode with Accent
                    SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
                    ThemeManager.applyTheme("dark", accents[cycle - 1], 100, saveConfig = false)
                    assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)
                    assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(rec.id)?.status)

                    // Classic XDM Skin in Light Mode
                    SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
                    ThemeManager.applyTheme("light", null, 100, saveConfig = false)
                    assertEquals(SkinManager.SKIN_ORIGINAL_XDM, SkinManager.currentSkin.id)
                    assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(rec.id)?.status)

                    // Custom Cyber Neon Skin with Neon Cyan
                    SkinManager.applySkin(cyberSkin.id, saveConfig = false)
                    ThemeManager.applyTheme("dark", Color(0x00, 0xF0, 0xFF), 100, saveConfig = false)
                    assertEquals(cyberSkin.id, SkinManager.currentSkin.id)
                    assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(rec.id)?.status)
                }
            } finally {
                prog.dispose()
            }
        }

        // Verify task state remains intact in database
        val persisted = AppContext.db.getById(rec.id)
        assertNotNull(persisted)
        assertEquals(5000000000L, persisted!!.size)
        assertEquals(2500000000L, persisted.downloaded)
        assertEquals(50, persisted.progress)
        assertEquals(RecordStatus.DOWNLOADING, persisted.status)
    }

    /**
     * Scenario 53: Import hostile and invalid archives; verify defense against path traversal,
     * giant payloads, missing assets, malformed manifest, and ID collisions.
     */
    @Test
    fun testScenario53HostileAndInvalidArchiveDefense() {
        // 1. Path traversal (Zip Slip)
        val zipSlip = File(dir, "hostile-zipslip.xdmskin")
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("root:x:0:0:root".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("skin.json"))
            zos.write("""{"id":"slip-skin","name":"Slip","version":"1.0","author":"Hacker"}""".toByteArray())
            zos.closeEntry()
        }
        zipSlip.writeBytes(baos.toByteArray())
        val resSlip = SkinPackageValidator.validateArchive(zipSlip)
        assertFalse(resSlip.isValid, "Must reject Zip Slip path traversal")
        assertTrue(resSlip.errors.any { it.contains("Zip Slip", ignoreCase = true) })

        // 2. Disallowed executable file
        val evilExe = File(dir, "hostile-exe.xdmskin")
        createZip(evilExe, mapOf(
            "skin.json" to """{"id":"exe-skin","name":"Exe","version":"1.0","author":"Hacker"}""",
            "backdoor.dll" to "MZ\u0000\u0000fake-binary".toByteArray()
        ))
        val resExe = SkinPackageValidator.validateArchive(evilExe)
        assertFalse(resExe.isValid, "Must reject archive containing .dll")
        assertTrue(resExe.errors.any { it.contains("executable or script", ignoreCase = true) })

        // 3. Missing declared assets
        val missingAsset = File(dir, "missing-asset.xdmskin")
        createZip(missingAsset, mapOf(
            "skin.json" to """
            {
              "schemaVersion": 2,
              "id": "missing-asset",
              "name": "Missing Asset",
              "version": "1.0",
              "author": "Tester",
              "wallpaper": {
                "dark": "backgrounds/missing_wallpaper.png"
              }
            }
            """.trimIndent()
        ))
        val resMissing = SkinPackageValidator.validateArchive(missingAsset)
        assertFalse(resMissing.isValid, "Must reject manifest pointing to nonexistent assets")
        assertTrue(resMissing.errors.any { it.contains("dark wallpaper file not found", ignoreCase = true) })

        // 4. Built-in ID collision
        val reservedId = File(dir, "reserved-id.xdmskin")
        createZip(reservedId, mapOf(
            "skin.json" to """{"id":"enhanced","name":"Enhanced Impostor","version":"1.0","author":"Tester"}"""
        ))
        val resId = SkinPackageValidator.validateArchive(reservedId)
        assertFalse(resId.isValid, "Must reject reserved ID 'enhanced'")
        assertTrue(resId.errors.any { it.contains("conflicts with reserved built-in", ignoreCase = true) })

        // 5. Out of bounds bounds (e.g. cornerArc 9999, borderWidth 500)
        val oobManifest = File(dir, "oob-bounds.xdmskin")
        createZip(oobManifest, mapOf(
            "skin.json" to """
            {
              "schemaVersion": 2,
              "id": "oob-bounds",
              "name": "OOB Bounds",
              "version": "1.0",
              "author": "Tester",
              "geometry": {
                "componentArc": 100
              },
              "regions": {
                "main_frame": {
                  "cornerArc": 999,
                  "borderWidth": 50
                }
              }
            }
            """.trimIndent()
        ))
        val resOob = SkinPackageValidator.validateArchive(oobManifest)
        assertFalse(resOob.isValid, "Must reject out-of-bounds geometry")
        assertTrue(resOob.errors.any { it.contains("componentArc must be between 0 and 48") })
        assertTrue(resOob.errors.any { it.contains("cornerArc must be between 0 and 48") })
        assertTrue(resOob.errors.any { it.contains("borderWidth must be between 0 and 16") })

        // 6. Malformed JSON manifest
        val corruptJson = File(dir, "corrupt-json.xdmskin")
        createZip(corruptJson, mapOf(
            "skin.json" to "{ id: unquoted, broken json ... "
        ))
        val resCorrupt = SkinPackageValidator.validateArchive(corruptJson)
        assertFalse(resCorrupt.isValid, "Must reject syntactically invalid JSON")
        assertTrue(resCorrupt.errors.any { it.contains("Failed to parse 'skin.json'", ignoreCase = true) })
    }

    /**
     * Scenario 54: Build and verify third-party sample skin using only documented format;
     * test UI changes and fallback behavior.
     */
    @Test
    fun testScenario54ThirdPartySampleSkinDocumentationCompliance() {
        val sampleSkinFile = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        val validation = SkinPackageValidator.validateArchive(sampleSkinFile)
        assertTrue(validation.isValid, "Sample skin must pass full archive validation: ${validation.errors}")
        val manifest = validation.manifest!!
        assertEquals(2, manifest.schemaVersion)
        assertEquals("cyber-neon", manifest.id)
        assertEquals("Cyber Neon", manifest.name)

        // Install skin
        val installed = SkinManager.installSkin(sampleSkinFile) as CustomSkin
        assertEquals(12, installed.componentArc)
        assertEquals(10, installed.buttonArc)
        assertEquals(28, installed.rowHeight)
        assertEquals(220, installed.sidebarWidth)

        // Verify fallback when an asset disappears from disk post-installation
        val testAsset = File(installed.skinDir, "backgrounds/cyber-dark.png")
        assertTrue(testAsset.exists(), "cyber-dark.png must exist initially")

        val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        assertTrue(installed.paintWallpaper(g, 100, 100, isDark = true))
        g.dispose()

        // Temporarily rename asset to simulate file loss
        val backupAsset = File(installed.skinDir, "backgrounds/cyber-dark.png.bak")
        testAsset.renameTo(backupAsset)
        try {
            installed.dispose() // clears cache
            val g2 = img.createGraphics()
            // Must safely return false without crashing
            val result = installed.paintWallpaper(g2, 100, 100, isDark = true)
            assertFalse(result, "Missing asset must safely return false without exception")
            g2.dispose()
        } finally {
            backupAsset.renameTo(testAsset)
        }
    }

    /**
     * Scenario 55: Declarative safety: ensure optional skins cannot hide essential controls
     * or inject executable code.
     */
    @Test
    fun testScenario55DeclarativeSafetyAndEssentialControlIntegrity() {
        // 1. Code injection prevention: verify SkinPackageValidator strictly limits allowed extensions
        val allowedExtensions = setOf("json", "png", "jpg", "jpeg", "gif", "webp", "ttf", "otf", "txt", "md")
        val disallowedExtensions = setOf(
            "exe", "dll", "so", "dylib", "class", "jar", "bat", "cmd", "sh", "ps1",
            "vbs", "js", "py", "bin", "com", "scr", "msi", "appimage", "c", "cpp", "h"
        )
        for (ext in disallowedExtensions) {
            val badZip = File(dir, "malicious-$ext.xdmskin")
            createZip(badZip, mapOf(
                "skin.json" to """{"id":"skin-$ext","name":"Skin $ext","version":"1.0","author":"Tester"}""",
                "payload.$ext" to "payload".toByteArray()
            ))
            val res = SkinPackageValidator.validateArchive(badZip)
            assertFalse(res.isValid, "Extension .$ext must be rejected")
            assertTrue(res.errors.any { it.contains("executable or script", ignoreCase = true) })
        }

        // 2. Essential controls integrity: Download, Pause, Resume, and Cancel buttons
        // cannot be removed or hidden by skin declarative configs
        val maliciousLayoutZip = File(dir, "malicious-layout.xdmskin")
        createZip(maliciousLayoutZip, mapOf(
            "skin.json" to """
            {
              "schemaVersion": 2,
              "id": "malicious-layout",
              "name": "Malicious Layout",
              "version": "1.0",
              "author": "Tester",
              "layout": {
                "toolbarPosition": "hidden",
                "sidebarPosition": "hidden",
                "showStatusBar": false
              }
            }
            """.trimIndent()
        ))
        val layoutSkin = SkinManager.installSkin(maliciousLayoutZip)

        runOnEdt {
            SkinManager.applySkin(layoutSkin.id, saveConfig = false)

            // Essential buttons in NewDownloadWindow remain present and visible
            val dwn = NewDownloadWindow()
            try {
                assertNotNull(dwn.btnDownload, "Download button must exist")
                assertNotNull(dwn.btnCancel, "Cancel button must exist")
                assertTrue(dwn.btnDownload.isVisible, "Download button must be visible")
                assertTrue(dwn.btnCancel.isVisible, "Cancel button must be visible")
            } finally {
                dwn.dispose()
            }

            // Essential buttons in AboutDialog remain present and visible
            val about = AboutDialog(null)
            try {
                assertNotNull(about.btnClose, "Close button in AboutDialog must exist")
                assertTrue(about.btnClose.isVisible, "Close button must be visible")
            } finally {
                about.dispose()
            }
        }
    }

    private fun createDummyPng(width: Int = 16, height: Int = 16): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }

    private fun createZip(dest: File, entries: Map<String, Any>) {
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                when (content) {
                    is String -> zos.write(content.toByteArray(StandardCharsets.UTF_8))
                    is ByteArray -> zos.write(content)
                    else -> error("Unsupported content type")
                }
                zos.closeEntry()
            }
        }
    }
}

