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
import xdm.app.ui.screens.NewDownloadWindow
import xdm.app.ui.screens.NewTorrentDownloadWindow
import xdm.app.ui.screens.NewVideoDownloadWindow
import xdm.app.ui.screens.ProgressWindow
import xdm.app.ui.screens.SettingsWindow
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.TorrentSource
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.SwingUtilities
import javax.swing.UIManager

class FullUiSkinContractTest : DownloadManagerTestBase() {

    private lateinit var testSkinsDir: File

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
    fun setUpSkinTest() {
        I8N.loadTexts("en")
        testSkinsDir = File(dir, "skins").apply { mkdirs() }
        SkinManager.customSkinsDir = testSkinsDir

        runOnEdt {
            SkinManager.init(SkinManager.SKIN_ENHANCED)
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
        }
    }

    @AfterEach
    fun tearDownSkinTest() {
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
        }
    }

    @Test
    fun testSchemaVersion2ParsingAndAllSemanticRegions() {
        val repoRoot = File("..").canonicalFile.let {
            if (File(it, "pom.xml").exists()) it else File(".").canonicalFile
        }
        val sampleSkinFile = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        assertTrue(sampleSkinFile.exists(), "Sample skin archive should exist at ${sampleSkinFile.absolutePath}")

        val result = SkinPackageValidator.validateArchive(sampleSkinFile)
        assertTrue(result.isValid, "Schema v2 manifest should validate cleanly: ${result.errors.joinToString()}")
        val manifest = result.manifest
        assertNotNull(manifest)
        assertEquals(2, manifest!!.schemaVersion, "Should be schemaVersion 2")
        assertEquals("cyber-neon", manifest.id)

        // Verify all 13 regions are present in the cyber-neon manifest
        val expectedRegions = listOf(
            "main_frame", "toolbar", "sidebar", "download_list",
            "download_modal", "video_download_modal", "progress_modal", "torrent_modal",
            "settings", "plugin_manager", "about", "dialogs", "tooltips_popups"
        )
        for (regionKey in expectedRegions) {
            assertTrue(manifest.regions.containsKey(regionKey), "Missing region '$regionKey' in manifest")
            val regCfg = manifest.regions[regionKey]!!
            assertNotNull(regCfg)
        }
    }

    @Test
    fun testBuiltInClassicXdmSkinRegionalContract() {
        val classic = BuiltInClassicXdmSkin()
        assertEquals(SkinManager.SKIN_ORIGINAL_XDM, classic.id)
        assertTrue(classic.isBuiltIn)
        assertEquals(0, classic.componentArc)
        assertEquals(0, classic.buttonArc)
        assertEquals(44, classic.rowHeight)
        assertEquals(200, classic.sidebarWidth)
        assertTrue(classic.useClassicIcons)

        for (region in SkinRegion.values()) {
            val regCfg = classic.getRegionConfig(region)
            assertNotNull(regCfg, "BuiltInClassicXdmSkin must define config for region ${region.slotId}")
            assertEquals(0, regCfg!!.cornerArc, "Region ${region.slotId} must have cornerArc 0")
            if (regCfg.buttonArc != null) {
                assertEquals(0, regCfg.buttonArc, "Region ${region.slotId} must have buttonArc 0")
            }
        }

        // Apply and verify FlatLaf UIManager tokens
        classic.applyCustomDefaults(isDark = true)
        assertEquals(0, UIManager.getInt("Component.arc"))
        assertEquals(0, UIManager.getInt("Button.arc"))
        assertEquals(0, UIManager.getInt("PopupMenu.arc"))
        assertEquals(0, UIManager.getInt("ToolTip.arc"))
        assertEquals(Color(0x14, 0x15, 0x18), UIManager.getColor("XDM.list.background"))
        assertEquals(Color(0x1A, 0x1B, 0x1F), UIManager.getColor("OptionPane.background"))
    }

    @Test
    fun testBuiltInEnhancedSkinRegionalContract() {
        val enhanced = BuiltInEnhancedSkin()
        assertEquals(SkinManager.SKIN_ENHANCED, enhanced.id)
        assertTrue(enhanced.isBuiltIn)
        assertEquals(8, enhanced.componentArc)
        assertEquals(8, enhanced.buttonArc)
        assertEquals(54, enhanced.rowHeight)
        assertEquals(210, enhanced.sidebarWidth)
        assertFalse(enhanced.useClassicIcons)

        for (region in SkinRegion.values()) {
            val regCfg = enhanced.getRegionConfig(region)
            assertNotNull(regCfg, "BuiltInEnhancedSkin must define config for region ${region.slotId}")
            assertTrue((regCfg!!.cornerArc ?: 0) >= 8, "Enhanced skin regions must have smooth rounding >= 8px")
        }

        enhanced.applyCustomDefaults(isDark = true)
        assertEquals(8, UIManager.getInt("Component.arc"))
        assertEquals(8, UIManager.getInt("Button.arc"))
    }

    @Test
    fun testAuthoritativeStyleResolverPrecedence() {
        val repoRoot = File("..").canonicalFile.let {
            if (File(it, "pom.xml").exists()) it else File(".").canonicalFile
        }
        val skinFile = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        val installedSkin = SkinManager.installSkin(skinFile)
        runOnEdt {
            SkinManager.applySkin(installedSkin.id, saveConfig = false)
        }

        // 1. Verify region-specific tokens take precedence over global skin defaults
        val modalArc = SkinStyleResolver.resolveCornerArc(SkinRegion.DOWNLOAD_MODAL)
        assertEquals(14, modalArc, "DOWNLOAD_MODAL cornerArc should resolve to 14 from region config")

        val listHeight = SkinStyleResolver.resolveDimension(SkinRegion.DOWNLOAD_LIST, "height", 50)
        assertEquals(32, listHeight, "DOWNLOAD_LIST height should resolve to 32 from region config")

        val toolbarHeight = SkinStyleResolver.resolveDimension(SkinRegion.TOOLBAR, "height", 36)
        assertEquals(42, toolbarHeight, "TOOLBAR height should resolve to 42 from region config")

        val progressArc = SkinStyleResolver.resolveProgressBarArc(SkinRegion.PROGRESS_MODAL)
        assertEquals(10, progressArc, "PROGRESS_MODAL progressBarArc should resolve to 10 from region config")

        val dialogBg = SkinStyleResolver.resolveBackground(SkinRegion.DIALOGS, isDark = true)
        assertEquals(Color(0x13, 0x17, 0x20), dialogBg, "DIALOGS background should resolve to #131720")

        val tooltipFg = SkinStyleResolver.resolveForeground(SkinRegion.TOOLTIPS_POPUPS, isDark = true)
        assertEquals(Color(0x00, 0xF0, 0xFF), tooltipFg, "TOOLTIPS_POPUPS foreground should resolve to cyan #00F0FF")

        // 2. High Contrast accessibility override test
        UIManager.put("FlatLaf.accessibility.highContrast", true)
        try {
            assertTrue(SkinStyleResolver.isHighContrastActive())
            assertEquals(0, SkinStyleResolver.resolveCornerArc(SkinRegion.DOWNLOAD_MODAL), "High contrast should force arc 0")
            assertEquals(Color.BLACK, SkinStyleResolver.resolveBackground(SkinRegion.MAIN_FRAME, isDark = true))
            assertEquals(Color.WHITE, SkinStyleResolver.resolveForeground(SkinRegion.MAIN_FRAME, isDark = true))
        } finally {
            UIManager.put("FlatLaf.accessibility.highContrast", false)
        }
    }

    @Test
    fun testCustomModalCustomizationWithoutCoreChanges() {
        // Build an external custom skin with distinct modal overrides
        val skinArchive = createMockSkinPackage(
            id = "custom-modals-theme",
            name = "Custom Modals Theme",
            version = "2.0.0",
            schemaVersion = 2,
            componentArc = 18,
            regions = """
                "download_modal": {
                    "background": "#202530",
                    "cornerArc": 20,
                    "buttonArc": 16
                },
                "progress_modal": {
                    "background": "#151922",
                    "cornerArc": 22,
                    "buttonArc": 14,
                    "progressBarArc": 12
                },
                "about": {
                    "background": "#1A202C",
                    "cornerArc": 24,
                    "buttonArc": 12
                }
            """.trimIndent()
        )

        val skin = SkinManager.installSkin(skinArchive)
        assertNotNull(skin)
        runOnEdt {
            SkinManager.applySkin(skin.id, saveConfig = false)
        }

        runOnEdt {
            // Test 1: Download modal adopts custom region skinning
            val dlWindow = NewDownloadWindow()
            try {
                val bgPanel = dlWindow.contentPane as? SkinBackgroundPanel
                assertNotNull(bgPanel, "Download window contentPane must be a SkinBackgroundPanel")
                assertEquals(SkinRegion.DOWNLOAD_MODAL, bgPanel!!.region)
                assertEquals(20, SkinStyleResolver.resolveCornerArc(SkinRegion.DOWNLOAD_MODAL))
                assertEquals(16, SkinStyleResolver.resolveButtonArc(SkinRegion.DOWNLOAD_MODAL))
                assertEquals(Color(0x20, 0x25, 0x30), SkinStyleResolver.resolveBackground(SkinRegion.DOWNLOAD_MODAL))
            } finally {
                dlWindow.dispose()
            }

            // Test 2: Progress modal adopts custom region skinning
            val rec = DbRecord(
                id = 9999L,
                size = 1000L,
                downloaded = 500L,
                progress = 50,
                date = System.currentTimeMillis(),
                fileName = "test-video.mp4",
                eta = 10L,
                speed = 100f,
                selected = false,
                status = RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Http
            )
            AppContext.db.add(rec)
            val progWindow = ProgressWindow(rec.id)
            try {
                val progBgPanel = progWindow.contentPane as? SkinBackgroundPanel
                assertNotNull(progBgPanel, "Progress window contentPane must be a SkinBackgroundPanel")
                assertEquals(SkinRegion.PROGRESS_MODAL, progBgPanel!!.region)
                assertEquals(22, SkinStyleResolver.resolveCornerArc(SkinRegion.PROGRESS_MODAL))
                assertEquals(12, SkinStyleResolver.resolveProgressBarArc(SkinRegion.PROGRESS_MODAL))
                assertEquals(Color(0x15, 0x19, 0x22), SkinStyleResolver.resolveBackground(SkinRegion.PROGRESS_MODAL))
            } finally {
                progWindow.dispose()
            }

            // Test 3: About dialog adopts custom region skinning
            val aboutDialog = AboutDialog(null)
            try {
                val aboutBgPanel = aboutDialog.contentPane as? SkinBackgroundPanel
                assertNotNull(aboutBgPanel, "About dialog contentPane must be a SkinBackgroundPanel")
                assertEquals(SkinRegion.ABOUT, aboutBgPanel!!.region)
                assertEquals(24, SkinStyleResolver.resolveCornerArc(SkinRegion.ABOUT))
                assertEquals(Color(0x1A, 0x20, 0x2C), SkinStyleResolver.resolveBackground(SkinRegion.ABOUT))
            } finally {
                aboutDialog.dispose()
            }
        }
    }

    @Test
    fun testActiveDownloadsSurviveSkinSwitching() {
        val rec = DbRecord(
            id = 8888L,
            size = 4000000000L,
            downloaded = 1500000000L,
            progress = 37,
            date = System.currentTimeMillis(),
            fileName = "ubuntu-24.04-desktop-amd64.iso",
            eta = 120L,
            speed = 5000000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http
        )
        AppContext.db.add(rec)

        runOnEdt {
            val progWindow = ProgressWindow(rec.id)
            try {
                assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(rec.id)?.status)

                // Switch through Classic -> Cyber Neon -> Enhanced -> Classic
                SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
                assertEquals(SkinManager.SKIN_ORIGINAL_XDM, SkinManager.currentSkin.id)
                assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(rec.id)?.status)

                val repoRoot = File("..").canonicalFile.let {
                    if (File(it, "pom.xml").exists()) it else File(".").canonicalFile
                }
                val sampleSkinFile = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
                val cyberSkin = SkinManager.installSkin(sampleSkinFile)
                SkinManager.applySkin(cyberSkin.id, saveConfig = false)
                assertEquals(cyberSkin.id, SkinManager.currentSkin.id)
                assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(rec.id)?.status)

                SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
                assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)
                assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(rec.id)?.status)
            } finally {
                progWindow.dispose()
            }
        }
    }

    @Test
    fun testValidationCatchesInvalidSchemaAndHostileEntries() {
        // 1. Unsupported schemaVersion
        val invalidSchemaZip = createRawZip("invalid-schema.xdmskin", mapOf(
            "skin.json" to """
                {
                    "schemaVersion": 99,
                    "id": "invalid-schema",
                    "name": "Invalid Schema",
                    "version": "1.0.0",
                    "author": "Tester"
                }
            """.trimIndent().toByteArray()
        ))
        val r1 = SkinPackageValidator.validateArchive(invalidSchemaZip)
        assertFalse(r1.isValid, "Should reject schemaVersion 99")
        assertTrue(r1.errors.any { it.contains("Unsupported schemaVersion '99'") })

        // 2. Out of bounds bounds in region
        val outOfBoundsZip = createRawZip("oob-region.xdmskin", mapOf(
            "skin.json" to """
                {
                    "schemaVersion": 2,
                    "id": "oob-region",
                    "name": "Out Of Bounds Region",
                    "version": "1.0.0",
                    "author": "Tester",
                    "regions": {
                        "toolbar": {
                            "borderWidth": 999,
                            "cornerArc": 500,
                            "background": "not-a-color"
                        }
                    }
                }
            """.trimIndent().toByteArray()
        ))
        val r2 = SkinPackageValidator.validateArchive(outOfBoundsZip)
        assertFalse(r2.isValid, "Should reject out-of-bounds region properties")
        assertTrue(r2.errors.any { it.contains("borderWidth must be between 0 and 16") })
        assertTrue(r2.errors.any { it.contains("cornerArc must be between 0 and 48") })
        assertTrue(r2.errors.any { it.contains("must be a valid #RRGGBB") })

        // 3. Zip slip attack entry
        val zipSlipZip = createRawZip("zip-slip.xdmskin", mapOf(
            "skin.json" to """{"id":"zip-slip","name":"Zip Slip","version":"1.0","author":"Tester"}""".toByteArray(),
            "../secret.txt" to "compromised".toByteArray()
        ))
        val r3 = SkinPackageValidator.validateArchive(zipSlipZip)
        assertFalse(r3.isValid, "Should detect and reject Zip Slip entry")
        assertTrue(r3.errors.any { it.contains("Zip Slip") })

        // 4. Executable / script payload
        val exePayloadZip = createRawZip("malicious.xdmskin", mapOf(
            "skin.json" to """{"id":"malicious-skin","name":"Malicious Skin","version":"1.0","author":"Tester"}""".toByteArray(),
            "payload.exe" to byteArrayOf(0x4D, 0x5A, 0x90.toByte())
        ))
        val r4 = SkinPackageValidator.validateArchive(exePayloadZip)
        assertFalse(r4.isValid, "Should reject executable payload in skin")
        assertTrue(r4.errors.any { it.contains("Security violation") })
    }

    @Test
    fun testSkinBackgroundPanelSinglePaintNoAlphaCompounding() {
        val panel = SkinBackgroundPanel(region = SkinRegion.MAIN_FRAME)
        panel.setSize(100, 100)
        val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            // Paint the panel
            panel.paint(g)
            // Pixel (50, 50) should have valid alpha
            val rgb = img.getRGB(50, 50)
            val alpha = (rgb ushr 24) and 0xFF
            assertEquals(255, alpha, "Panel background must be fully opaque")
        } finally {
            g.dispose()
        }
    }

    @Test
    fun testAboutDialogLiveButtonArcUpdate() {
        runOnEdt {
            val dialog = AboutDialog(null)
            try {
                // Under Enhanced skin: button arc should be 8 or match region
                SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
                assertEquals("arc: 8", dialog.btnClose.getClientProperty(FlatClientProperties.STYLE))

                // Under Classic XDM skin: button arc should be 0
                SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
                assertEquals("arc: 0", dialog.btnClose.getClientProperty(FlatClientProperties.STYLE))
            } finally {
                dialog.dispose()
            }
        }
    }

    @Test
    fun testProgressWindowLiveUpdatesTabbedPaneArc() {
        val rec = DbRecord(
            id = 7777L,
            size = 100L,
            downloaded = 10L,
            progress = 10,
            date = System.currentTimeMillis(),
            fileName = "sample.bin",
            eta = 5L,
            speed = 20f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http
        )
        AppContext.db.add(rec)
        runOnEdt {
            val prog = ProgressWindow(rec.id)
            try {
                // Classic XDM skin forces tabArc: 0
                SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
                assertEquals("tabArc: 0", prog.tabbedPane.getClientProperty(FlatClientProperties.STYLE))

                // Enhanced skin forces tabArc: 12 (or >= 8)
                SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
                val enhancedArc = SkinStyleResolver.resolveCornerArc(SkinRegion.PROGRESS_MODAL)
                assertEquals("tabArc: $enhancedArc", prog.tabbedPane.getClientProperty(FlatClientProperties.STYLE))
            } finally {
                prog.dispose()
            }
        }
    }

    @Test
    fun testSettingsWindowDisposesGeneralPanelWithoutThemeListenerLeak() {
        runOnEdt {
            val initialListeners = ThemeManager.listenerCount
            val settings = SettingsWindow()
            assertTrue(ThemeManager.listenerCount > initialListeners, "SettingsWindow should register listeners while open")
            settings.dispose()
            assertEquals(initialListeners, ThemeManager.listenerCount, "All theme listeners must be unhooked on dispose")
        }
    }

    @Test
    fun testModalButtonArcsUpdateOnAllDialogs() {
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)

            val dwn = NewDownloadWindow()
            try {
                assertEquals(0, SkinStyleResolver.resolveButtonArc(SkinRegion.DOWNLOAD_MODAL))
                assertEquals(0, dwn.btnDownload.getClientProperty("Button.arc"))
            } finally {
                dwn.dispose()
            }

            val vid = NewVideoDownloadWindow()
            try {
                assertEquals(0, SkinStyleResolver.resolveButtonArc(SkinRegion.VIDEO_DOWNLOAD_MODAL))
                assertEquals(0, vid.btnDownload.getClientProperty("Button.arc"))
            } finally {
                vid.dispose()
            }

            val torr = NewTorrentDownloadWindow(
                TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=test")
            )
            try {
                assertEquals(0, SkinStyleResolver.resolveButtonArc(SkinRegion.TORRENT_MODAL))
                assertEquals(0, torr.btnStart.getClientProperty("Button.arc"))
            } finally {
                torr.dispose()
            }
        }
    }

    private fun createMockSkinPackage(
        id: String,
        name: String,
        version: String,
        schemaVersion: Int = 2,
        componentArc: Int = 8,
        regions: String = ""
    ): File {
        val archiveFile = File(dir, "$id.xdmskin")
        val manifestJson = """
            {
                "schemaVersion": $schemaVersion,
                "id": "$id",
                "name": "$name",
                "version": "$version",
                "minAppVersion": "1.0.0-beta.4",
                "author": "Test Author",
                "description": "Mock skin for automated testing",
                "geometry": {
                    "componentArc": $componentArc,
                    "buttonArc": $componentArc
                },
                "accents": {
                    "dark": "#3B82F6",
                    "light": "#2563EB"
                },
                "regions": {
                    $regions
                }
            }
        """.trimIndent()

        return createRawZip(archiveFile.name, mapOf("skin.json" to manifestJson.toByteArray()))
    }

    private fun createRawZip(fileName: String, entries: Map<String, ByteArray>): File {
        val file = File(dir, fileName)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }
}
