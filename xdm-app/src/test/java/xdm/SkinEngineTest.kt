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
        assertEquals(2, classic.componentArc)
        assertEquals(2, classic.buttonArc)
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
                assertEquals(2, UIManager.getInt("Component.arc"))
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
}

