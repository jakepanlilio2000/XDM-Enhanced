package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.ui.components.ThemeManager
import java.awt.Color
import java.io.File
import java.nio.file.Files
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.UIManager

/**
 * Regression tests for Issue #1395:
 * Accent Customization (presets, custom selection, contrast foreground calculation,
 * live UIManager property updates, reset to default, persistence, and safe translucency).
 */
class AccentCustomizationTest {

    private lateinit var tempDir: File
    private lateinit var config: AppConfig

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("xdm-accent-test").toFile()
        config = AppConfig(tempDir.absolutePath)
        AppContext.config = config
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
        ThemeManager.setAccentColor(null)
    }

    @Test
    fun presetsAreProperlyConfigured() {
        val presets = ThemeManager.PRESETS
        assertEquals(6, presets.size, "Should offer 6 curated presets")

        presets.forEach { preset ->
            assertNotNull(preset.name)
            assertTrue(preset.hex.startsWith("#"))
            assertEquals(7, preset.hex.length)
            val parsed = ThemeManager.parseHexColor(preset.hex)
            assertEquals(preset.color, parsed)
        }
    }

    @Test
    fun contrastCalculation_providesHighContrastForeground() {
        val darkText = Color(0x18, 0x18, 0x1B)
        val whiteText = Color.WHITE

        // Bright colors MUST receive dark foreground text for readability
        assertEquals(darkText, ThemeManager.contrastForeground(Color(0xF5, 0x9E, 0x0B)), "Amber must have dark text")
        assertEquals(darkText, ThemeManager.contrastForeground(Color.YELLOW), "Yellow must have dark text")
        assertEquals(darkText, ThemeManager.contrastForeground(Color.WHITE), "White must have dark text")
        assertEquals(darkText, ThemeManager.contrastForeground(Color(0xE0, 0xF7, 0xFA)), "Pale cyan must have dark text")

        // Dark or saturated colors MUST receive white foreground text for readability
        assertEquals(whiteText, ThemeManager.contrastForeground(Color(0x3B, 0x82, 0xF6)), "Blue must have white text")
        assertEquals(whiteText, ThemeManager.contrastForeground(Color(0x8B, 0x5C, 0xF6)), "Purple must have white text")
        assertEquals(whiteText, ThemeManager.contrastForeground(Color(0xF4, 0x3F, 0x5E)), "Rose must have white text")
        assertEquals(whiteText, ThemeManager.contrastForeground(Color.BLACK), "Black must have white text")
        assertEquals(whiteText, ThemeManager.contrastForeground(Color(0x0F, 0x17, 0x2A)), "Dark navy must have white text")
    }

    @Test
    fun hexColorParsingAndFormatting() {
        val opaque = Color(0x10, 0xB9, 0x81)
        val hexOpaque = ThemeManager.colorToHex(opaque)
        assertEquals("#10B981", hexOpaque)
        assertEquals(opaque, ThemeManager.parseHexColor(hexOpaque))

        // Supports optional alpha transparency
        val alphaColor = Color(0x10, 0xB9, 0x81, 0x80)
        val hexAlpha = ThemeManager.colorToHex(alphaColor)
        assertEquals("#8010B981", hexAlpha)
        assertEquals(alphaColor, ThemeManager.parseHexColor(hexAlpha))

        // Null and blank handling
        assertNull(ThemeManager.parseHexColor(null))
        assertNull(ThemeManager.parseHexColor(""))
        assertNull(ThemeManager.parseHexColor("invalid-hex"))
    }

    @Test
    fun liveAccentApplication_updatesUIManager_andResetRestoresNull() {
        val customAccent = Color(0x8B, 0x5C, 0xF6) // Purple
        val expectedFg = ThemeManager.contrastForeground(customAccent)

        // Apply accent
        ThemeManager.applyTheme("dark", customAccent, zoomPercent = 100, saveConfig = true)
        assertEquals(customAccent, ThemeManager.currentAccent)
        assertEquals("#8B5CF6", ThemeManager.currentAccentHex)

        assertEquals(customAccent, UIManager.getColor("Component.accentColor"))
        assertEquals(customAccent, UIManager.getColor("XDM.accent"))
        assertEquals(customAccent, UIManager.getColor("Button.default.background"))
        assertEquals(expectedFg, UIManager.getColor("Button.default.foreground"))
        assertEquals(customAccent, UIManager.getColor("ProgressBar.foreground"))
        assertEquals(expectedFg, UIManager.getColor("ProgressBar.selectionForeground"))

        // Reset to default (null)
        ThemeManager.setAccentColor(null)
        assertNull(ThemeManager.currentAccent)
        assertNull(ThemeManager.currentAccentHex)
        assertNotEquals(customAccent, UIManager.getColor("Button.default.background"), "Resetting accent must restore theme default button background")
        assertNotEquals(customAccent, UIManager.getColor("ProgressBar.foreground"), "Resetting accent must restore theme default progress foreground")
    }

    @Test
    fun accentPreference_persistsAcrossRestart() {
        val chosenHex = "#10B981"
        config.accentColor = chosenHex
        config.save()

        // Reload fresh config simulating app restart
        val reloaded = AppConfig(tempDir.absolutePath).apply { load() }
        assertEquals(chosenHex, reloaded.accentColor)
        val parsed = ThemeManager.parseHexColor(reloaded.accentColor)
        assertEquals(Color(0x10, 0xB9, 0x81), parsed)
    }

    @Test
    fun safeTranslucencyChecks_doNotThrow() {
        // canApplyTranslucency should return a boolean without throwing
        val supported = ThemeManager.canApplyTranslucency(null)
        // Testing with an undecorated frame/dialog
        val frame = JFrame().apply { isUndecorated = true }
        val dialog = JDialog()

        // Should execute safely without throwing exceptions on any platform
        val frameCanTranslucent = ThemeManager.canApplyTranslucency(frame)
        val dialogCanTranslucent = ThemeManager.canApplyTranslucency(dialog)

        assertFalse(ThemeManager.setWindowOpacitySafe(frame, -1.0f) && !frameCanTranslucent)
        frame.dispose()
        dialog.dispose()
    }
}

