package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import java.io.File
import java.nio.file.Files

/** Verifies persistence and round-tripping of custom accent colors in AppConfig. */
class AppConfigAccentColorTest {
    private lateinit var dir: File

    @BeforeEach
    fun setup() {
        dir = Files.createTempDirectory("xdm-config").toFile()
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun load() = AppConfig(dir.absolutePath).apply { load() }

    @Test
    fun defaultAccentColor_isNull() {
        val config = AppConfig(dir.absolutePath)
        assertNull(config.accentColor, "Default accentColor should be null (theme default)")
    }

    @Test
    fun accentColor_roundTripsHexValues() {
        val hexes = listOf("#3B82F6", "#06B6D4", "#10B981", "#F59E0B", "#8B5CF6", "#F43F5E", "#123456")
        for (hex in hexes) {
            AppConfig(dir.absolutePath).apply { accentColor = hex }.save()
            assertEquals(hex, load().accentColor, "Accent color should persist: $hex")
        }
    }

    @Test
    fun accentColor_resetsToNull() {
        AppConfig(dir.absolutePath).apply { accentColor = "#3B82F6" }.save()
        assertEquals("#3B82F6", load().accentColor)

        AppConfig(dir.absolutePath).apply { accentColor = null }.save()
        assertNull(load().accentColor, "Accent color should be cleared to null")
    }
}

