package xdm

import com.formdev.flatlaf.FlatLaf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.ui.components.AppTheme
import xdm.app.ui.components.ThemeManager
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.UIManager

class ThemeManagerTest {

    @Test
    fun parseHexColor_handlesVariousFormats() {
        assertEquals(Color(0x3B, 0x82, 0xF6), ThemeManager.parseHexColor("#3B82F6"))
        assertEquals(Color(0x3B, 0x82, 0xF6), ThemeManager.parseHexColor("3B82F6"))
        assertEquals(Color(0x06, 0xB6, 0xD4), ThemeManager.parseHexColor("#06B6D4"))
        assertNull(ThemeManager.parseHexColor(null))
        assertNull(ThemeManager.parseHexColor(""))
        assertNull(ThemeManager.parseHexColor("   "))
        assertNull(ThemeManager.parseHexColor("invalid-color"))
    }

    @Test
    fun colorToHex_formatsUppercaseHex() {
        assertEquals("#3B82F6", ThemeManager.colorToHex(Color(0x3B, 0x82, 0xF6)))
        assertEquals("#10B981", ThemeManager.colorToHex(Color(0x10, 0xB9, 0x81)))
        assertEquals("#000000", ThemeManager.colorToHex(Color.BLACK))
        assertEquals("#FFFFFF", ThemeManager.colorToHex(Color.WHITE))
    }

    @Test
    fun presets_containExpectedCuratedColors() {
        assertEquals(6, ThemeManager.PRESETS.size)
        val names = ThemeManager.PRESETS.map { it.name }
        assertTrue(names.contains("Blue"))
        assertTrue(names.contains("Cyan"))
        assertTrue(names.contains("Green"))
        assertTrue(names.contains("Amber"))
        assertTrue(names.contains("Purple"))
        assertTrue(names.contains("Rose"))

        ThemeManager.PRESETS.forEach { preset ->
            assertEquals(preset.color, ThemeManager.parseHexColor(preset.hex))
        }
    }

    @Test
    fun applyTheme_switchesLightAndDark() {
        ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
        assertTrue(FlatLaf.isLafDark(), "Dark theme should set isLafDark to true")
        assertTrue(ThemeManager.isDark)

        ThemeManager.applyTheme("light", null, 100, saveConfig = false)
        assertFalse(FlatLaf.isLafDark(), "Light theme should set isLafDark to false")
        assertFalse(ThemeManager.isDark)

        // Restore dark for subsequent tests
        ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
    }

    @Test
    fun applyTheme_appliesCustomAccent() {
        val customAccent = Color(0x10, 0xB9, 0x81)
        ThemeManager.applyTheme("dark", customAccent, 100, saveConfig = false)

        assertEquals(customAccent, ThemeManager.currentAccent)
        assertEquals("#10B981", ThemeManager.currentAccentHex)
        assertEquals(customAccent, UIManager.getColor("Component.accentColor"))
        assertEquals(customAccent, AppTheme.accent)

        // Reset to default
        ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
        assertNull(ThemeManager.currentAccent)
        assertNull(ThemeManager.currentAccentHex)
    }

    @Test
    fun themeListeners_notifiedOnThemeAndAccentChange() {
        val notifyCount = AtomicInteger(0)
        val listener = ThemeManager.ThemeChangeListener {
            notifyCount.incrementAndGet()
        }

        ThemeManager.addThemeListener(listener)
        try {
            val initial = notifyCount.get()
            ThemeManager.setTheme("light")
            assertEquals(initial + 1, notifyCount.get())

            ThemeManager.setAccentColor(Color(0x8B, 0x5C, 0xF6))
            assertEquals(initial + 2, notifyCount.get())

            ThemeManager.setAccentColor(null)
            assertEquals(initial + 3, notifyCount.get())
        } finally {
            ThemeManager.removeThemeListener(listener)
            // Restore dark
            ThemeManager.setTheme("dark")
        }
    }
}

