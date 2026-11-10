package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.skins.BuiltInClassicXdmSkin
import xdm.app.skins.BuiltInEnhancedSkin
import xdm.app.skins.ClassicIconLoader
import xdm.app.skins.Skin
import xdm.app.skins.SkinManager
import xdm.app.ui.components.CategoryStyle
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.settings.AppearancePanel
import xdm.app.ui.screens.settings.SkinPreviewCard
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.swing.SwingUtilities
import javax.swing.UIManager

class SkinEngineTest : DownloadManagerTestBase() {

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
    fun resetSkins() {
        xdm.app.I8N.loadTexts("en")
        runOnEdt {
            SkinManager.init(SkinManager.SKIN_ENHANCED)
        }
    }

    @Test
    fun testBuiltInSkinsMetadata() {
        val enhanced = SkinManager.BUILTIN_ENHANCED
        assertEquals(SkinManager.SKIN_ENHANCED, enhanced.id)
        assertEquals("XDM Enhanced", enhanced.name)
        assertTrue(enhanced.isBuiltIn)
        assertEquals(8, enhanced.componentArc)
        assertEquals(8, enhanced.buttonArc)
        assertEquals(54, enhanced.rowHeight)
        assertEquals(210, enhanced.sidebarWidth)
        assertFalse(enhanced.useClassicIcons)
        assertNull(enhanced.attributionNotice)

        val classic = SkinManager.BUILTIN_ORIGINAL_XDM
        assertEquals(SkinManager.SKIN_ORIGINAL_XDM, classic.id)
        assertEquals("Original XDM", classic.name)
        assertTrue(classic.isBuiltIn)
        assertEquals(0, classic.componentArc)
        assertEquals(0, classic.buttonArc)
        assertEquals(44, classic.rowHeight)
        assertEquals(200, classic.sidebarWidth)
        assertTrue(classic.useClassicIcons)
        assertNotNull(classic.attributionNotice)
        assertTrue(classic.attributionNotice!!.contains("Subhra Das Gupta"))
        assertTrue(classic.attributionNotice!!.contains("Independent fork preset"))
    }

    @Test
    fun testDynamicSkinSwitchingAndListeners() {
        runOnEdt {
            var notifiedSkin: Skin? = null
            val listener = SkinManager.SkinChangeListener { skin ->
                notifiedSkin = skin
            }
            SkinManager.addSkinListener(listener)
            try {
                // Switch to classic XDM skin
                val applied = SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
                assertTrue(applied, "Applying classic XDM skin should succeed")
                assertEquals(SkinManager.SKIN_ORIGINAL_XDM, SkinManager.currentSkin.id)
                assertEquals(SkinManager.SKIN_ORIGINAL_XDM, notifiedSkin?.id)
                assertEquals(0, UIManager.getInt("Component.arc"))
                assertEquals(0, UIManager.getInt("Button.arc"))
                assertEquals(44, UIManager.getInt("XDM.skin.rowHeight"))
                assertTrue(UIManager.getBoolean("XDM.skin.useClassicIcons"))

                // Switch back to enhanced skin
                val appliedEnhanced = SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
                assertTrue(appliedEnhanced, "Applying enhanced skin should succeed")
                assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)
                assertEquals(SkinManager.SKIN_ENHANCED, notifiedSkin?.id)
                assertEquals(8, UIManager.getInt("Component.arc"))
                assertEquals(54, UIManager.getInt("XDM.skin.rowHeight"))
                assertFalse(UIManager.getBoolean("XDM.skin.useClassicIcons"))
            } finally {
                SkinManager.removeSkinListener(listener)
            }
        }
    }

    @Test
    fun testFallbackOnInvalidSkinId() {
        runOnEdt {
            // Apply non-existent skin
            val success = SkinManager.applySkin("totally_invalid_skin_xyz_999", saveConfig = false)
            // Should fallback to default BUILTIN_ENHANCED cleanly
            assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)
            assertNotNull(SkinManager.currentSkin)

            // Test init with null or blank
            SkinManager.init(null)
            assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)

            SkinManager.init("")
            assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)
        }
    }

    @Test
    fun testClassicCategoryColorsAndIcons() {
        val classic = SkinManager.BUILTIN_ORIGINAL_XDM

        // Check dark category color overrides
        val docDark = classic.categoryColor("CAT_DOCUMENTS", isDark = true)
        assertNotNull(docDark)
        val compDark = classic.categoryColor("CAT_COMPRESSED", isDark = true)
        assertNotNull(compDark)

        // Check light category color overrides
        val docLight = classic.categoryColor("CAT_DOCUMENTS", isDark = false)
        assertNotNull(docLight)
        val compLight = classic.categoryColor("CAT_COMPRESSED", isDark = false)
        assertNotNull(compLight)
        assertNotEquals(docDark, docLight)

        // CategoryStyle integration
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
            val resolvedDocDark = CategoryStyle.colorForCategory("CAT_DOCUMENTS")
            assertEquals(docDark, resolvedDocDark)

            ThemeManager.applyTheme("light", null, 100, saveConfig = false)
            val resolvedCompLight = CategoryStyle.colorForCategory("CAT_COMPRESSED")
            assertEquals(compLight, resolvedCompLight)
        }

        // Classic icons loading
        val catIcon = classic.getCategoryIcon("CAT_DOCUMENTS", 18)
        assertNotNull(catIcon, "Classic category icon should resolve")
        assertEquals(18, catIcon?.iconWidth)

        val toolAdd = classic.getToolbarIcon("ADD_URL", 24)
        assertNotNull(toolAdd, "Classic toolbar icon should resolve")
        assertEquals(24, toolAdd?.iconWidth)

        val toolResume = classic.getToolbarIcon("RESUME", 24)
        assertNotNull(toolResume, "Classic resume icon should resolve")

        val toolPause = classic.getToolbarIcon("PAUSE", 24)
        assertNotNull(toolPause, "Classic pause icon should resolve")

        // Non-existent or unknown action returns null safely without exception
        assertNull(classic.getToolbarIcon("UNKNOWN_ACTION_XYZ", 24))
        assertNull(ClassicIconLoader.loadIcon("non_existent_glyph_12345", 24))

        // Torrents and Queues aliases
        assertNotNull(classic.getCategoryIcon("torrents", 18))
        assertNotNull(classic.getCategoryIcon("queues", 18))
    }

    @Test
    fun testRestoreDefaults() {
        runOnEdt {
            // Apply classic skin and custom theme
            SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = true)
            ThemeManager.applyTheme("light", Color.RED, 125, saveConfig = true)

            // Execute restoreDefaults
            SkinManager.restoreDefaults()

            assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)
            assertEquals(SkinManager.SKIN_ENHANCED, AppContext.config.skinId)
            assertEquals("system", AppContext.config.theme)
            assertNull(AppContext.config.accentColor)
            assertEquals(100, AppContext.config.uiScale)
        }
    }

    @Test
    fun testAppConfigSkinPersistence() {
        val config = AppConfig(dir.absolutePath)
        config.skinId = SkinManager.SKIN_ORIGINAL_XDM
        config.save()

        val loaded = AppConfig(dir.absolutePath).apply { load() }
        assertEquals(SkinManager.SKIN_ORIGINAL_XDM, loaded.skinId)

        // Default value test
        val emptyConfig = AppConfig(dir.absolutePath)
        assertEquals(SkinManager.SKIN_ENHANCED, emptyConfig.skinId)
    }

    @Test
    fun testSkinPreviewCardRendering() {
        runOnEdt {
            val card = SkinPreviewCard(SkinManager.BUILTIN_ENHANCED)
            card.setSize(380, 150)
            val img = java.awt.image.BufferedImage(380, 150, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            card.paint(g)
            g.dispose()

            card.setPreviewSkin(SkinManager.BUILTIN_ORIGINAL_XDM)
            val g2 = img.createGraphics()
            card.paint(g2)
            g2.dispose()
        }
    }

    @Test
    fun testAppearancePanelLoadAndSave() {
        runOnEdt {
            val panel = xdm.app.ui.screens.settings.AppearancePanel()
            AppContext.config.skinId = SkinManager.SKIN_ORIGINAL_XDM
            AppContext.config.theme = "light"
            AppContext.config.accentColor = "#10B981"
            panel.load()

            AppContext.config.skinId = SkinManager.SKIN_ENHANCED
            panel.save()
            assertEquals(SkinManager.SKIN_ORIGINAL_XDM, AppContext.config.skinId)
            panel.dispose()
        }
    }

    @Test
    fun testClassicXdmFidelityTokens() {
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM, saveConfig = false)
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
            assertEquals(0, UIManager.getInt("Component.arc"))
            assertEquals(0, UIManager.getInt("Button.arc"))
            assertEquals(0, UIManager.getInt("TextComponent.arc"))
            assertEquals(0, UIManager.getInt("ProgressBar.arc"))
            assertEquals(0, UIManager.getInt("CheckBox.arc"))
            assertEquals(0, UIManager.getInt("TabbedPane.tabArc"))
            assertEquals(0, UIManager.getInt("ScrollBar.thumbArc"))
            assertEquals(44, UIManager.getInt("XDM.skin.rowHeight"))
        }
    }

    @Test
    fun testSkinWallpaperAndOpacitySwitching() {
        val repoRoot = java.io.File("..").canonicalFile.let {
            if (java.io.File(it, "pom.xml").exists()) it else java.io.File(".").canonicalFile
        }
        val cyberNeonArchive = java.io.File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        if (cyberNeonArchive.exists()) {
            runOnEdt {
                SkinManager.installSkin(cyberNeonArchive)
                val skin = SkinManager.getSkin("cyber-neon")
                assertNotNull(skin, "Installed custom skin should exist")
                assertTrue(skin!!.hasWallpaper(isDark = true), "Cyber neon should report hasWallpaper = true")

                // Apply skin with wallpaper
                SkinManager.applySkin("cyber-neon", saveConfig = false)
                val tableView = xdm.app.ui.components.DownloadTableView()
                tableView.updateTheme()
                assertFalse(tableView.isOpaque, "TableView should be non-opaque when skin has wallpaper")

                // Check cell renderer opacity
                val renderer = xdm.app.ui.components.DownloadTableRenderers.FileNameCellRenderer()
                val table = javax.swing.JTable()
                val unselectedComp = renderer.getTableCellRendererComponent(table, "test.zip", false, false, 0, 0)
                assertFalse(unselectedComp.isOpaque, "Unselected cell should be non-opaque when skin has wallpaper")

                val selectedComp = renderer.getTableCellRendererComponent(table, "test.zip", true, true, 0, 0)
                assertTrue(selectedComp.isOpaque, "Selected cell should remain opaque with selection background")

                // Switch back to built-in skin without wallpaper
                SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
                assertFalse(SkinManager.currentSkin.hasWallpaper(isDark = true), "Enhanced skin has no wallpaper")
                tableView.updateTheme()
                assertTrue(tableView.isOpaque, "TableView should become opaque when skin has no wallpaper")

                val unselectedComp2 = renderer.getTableCellRendererComponent(table, "test.zip", false, false, 0, 0)
                assertTrue(unselectedComp2.isOpaque, "Unselected cell should be opaque when skin has no wallpaper")
            }
        }
    }

    @Test
    fun testSkinWallpaperSafePaintingZeroDimensionsAndFallback() {
        val repoRoot = java.io.File("..").canonicalFile.let {
            if (java.io.File(it, "pom.xml").exists()) it else java.io.File(".").canonicalFile
        }
        val cyberNeonArchive = java.io.File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        if (cyberNeonArchive.exists()) {
            val skin = SkinManager.getSkin("cyber-neon")
            if (skin != null) {
                val dummyImg = java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = dummyImg.createGraphics()
                try {
                    // Safe handling of 0 or negative dimensions
                    assertFalse(skin.paintWallpaper(g, 0, 0, true))
                    assertFalse(skin.paintWallpaper(g, -100, -100, false))
                    assertFalse(skin.paintWallpaper(g, 0, 500, true))
                    assertFalse(skin.paintWallpaper(g, 500, 0, false))

                    // Valid dimensions paint successfully
                    assertTrue(skin.paintWallpaper(g, 200, 200, true))
                } finally {
                    g.dispose()
                }
            }
        }
    }

    @Test
    fun testDeterministicPrecedenceHierarchy() {
        // High contrast / luminance foreground guarantee
        val brightAmber = Color(0xF5, 0x9E, 0x0B)
        val fgForBright = ThemeManager.contrastForeground(brightAmber)
        assertEquals(Color(0x18, 0x18, 0x1B), fgForBright, "Bright background requires dark text for accessibility")

        val darkBlue = Color(0x1E, 0x3A, 0x8A)
        val fgForDark = ThemeManager.contrastForeground(darkBlue)
        assertEquals(Color.WHITE, fgForDark, "Dark background requires white text for contrast")
    }

    @Test
    fun testCustomSkinPathTraversalProtection() {
        val repoRoot = java.io.File("..").canonicalFile.let {
            if (java.io.File(it, "pom.xml").exists()) it else java.io.File(".").canonicalFile
        }
        val cyberNeonArchive = java.io.File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        if (cyberNeonArchive.exists()) {
            val skin = SkinManager.getSkin("cyber-neon") as? xdm.app.skins.CustomSkin
            if (skin != null) {
                // Malicious path traversal attempts must be rejected safely
                assertNull(skin.getToolbarIcon("../../../../etc/passwd", 24))
                assertNull(skin.getToolbarIcon("..\\..\\windows\\system32\\calc.exe", 24))
                assertNull(skin.getToolbarIcon("C:/Windows/System32/notepad.exe", 24))
            }
        }
    }

    @Test
    fun testClassicListViewAndModernListViewInitOpacity() {
        val repoRoot = java.io.File("..").canonicalFile.let {
            if (java.io.File(it, "pom.xml").exists()) it else java.io.File(".").canonicalFile
        }
        val cyberNeonArchive = java.io.File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        if (cyberNeonArchive.exists()) {
            runOnEdt {
                SkinManager.installSkin(cyberNeonArchive)
                SkinManager.applySkin("cyber-neon", saveConfig = false)

                // Classic list view should immediately have non-opaque containers
                val classicView = xdm.app.ui.components.ClassicListView()
                val classicComp = classicView.component as javax.swing.JPanel
                assertFalse(classicComp.isOpaque, "ClassicListView outer panel should be non-opaque with wallpaper")

                // Modern list view should also immediately have non-opaque containers
                val modernView = xdm.app.ui.components.ModernListView()
                val modernComp = modernView.component as javax.swing.JPanel
                assertFalse(modernComp.isOpaque, "ModernListView outer panel should be non-opaque with wallpaper")

                // Switch back
                SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
                classicView.updateTheme()
                assertTrue(classicComp.isOpaque, "ClassicListView should be opaque when skin has no wallpaper")
            }
        }
    }

    @Test
    fun testModalDialogsLiveRepaintAndDisposal() {
        runOnEdt {
            SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
            val newDwn = xdm.app.ui.screens.NewDownloadWindow()
            assertTrue(newDwn.contentPane is xdm.app.ui.components.SkinBackgroundPanel)
            newDwn.dispose()

            val newVid = xdm.app.ui.screens.NewVideoDownloadWindow()
            assertTrue(newVid.contentPane is xdm.app.ui.components.SkinBackgroundPanel)
            newVid.dispose()

            val newTorr = xdm.app.ui.screens.NewTorrentDownloadWindow(
                xdm.torrent.TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=test")
            )
            assertTrue(newTorr.contentPane is xdm.app.ui.components.SkinBackgroundPanel)
            newTorr.dispose()
        }
    }
}

