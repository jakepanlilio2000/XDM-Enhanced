package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.utils.AutoStart
import xdm.app.utils.linux.LinuxDesktopUtils
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path

class LinuxDesktopEntryTest {

    @TempDir
    lateinit var tempHome: Path

    @Test
    fun testDesktopEntryGenerationContent() {
        val cmd = listOf("/opt/xdm-app/bin/xdm-app", "--minimized")
        val content = LinuxDesktopUtils.generateDesktopContent(cmd)

        assertTrue(content.contains("[Desktop Entry]"), "Must have [Desktop Entry] section")
        assertTrue(content.contains("Type=Application"), "Must have Type=Application")
        assertTrue(content.contains("Name=XDM Enhanced"), "Name must be XDM Enhanced")
        assertTrue(content.contains("GenericName=Download Manager"), "GenericName must be Download Manager")
        assertTrue(content.contains("Comment=XDM Enhanced Download Manager"), "Comment must mention XDM Enhanced")
        assertTrue(content.contains("Icon=xdm-enhanced"), "Icon must reference xdm-enhanced")
        assertFalse(content.contains("Icon=xdm-app"), "Must not reference legacy xdm-app icon")
        assertTrue(content.contains("Exec=/opt/xdm-app/bin/xdm-app --minimized"), "Exec command must be present")
        assertTrue(content.contains("Terminal=false"), "Must be non-terminal")
        assertTrue(content.contains("Categories=Network;FileTransfer;"), "Must specify standard network categories")
        assertTrue(content.contains("StartupWMClass=xdm-app-AppMain"), "Must set WMClass for window grouping")
        assertTrue(content.contains("X-GNOME-Autostart-enabled=true"), "Must include GNOME autostart flag")
    }

    @Test
    fun testCustomIconInDesktopEntry() {
        val cmd = listOf("/usr/bin/xdm-enhanced", "--minimized")
        val content = LinuxDesktopUtils.generateDesktopContent(cmd, icon = "/opt/xdm-enhanced/icon.png")

        assertTrue(content.contains("Icon=/opt/xdm-enhanced/icon.png"))
        assertTrue(content.contains("Exec=/usr/bin/xdm-enhanced --minimized"))
    }

    @Test
    fun testUserIconExtractionAndFormat() {
        val homeDir = tempHome.toFile()
        val extracted = LinuxDesktopUtils.ensureUserIconInstalled(homeDir)

        assertNotNull(extracted, "Icon file must be extracted successfully")
        assertTrue(extracted!!.isFile, "Extracted icon must be a regular file")
        assertTrue(extracted.length() > 0, "Extracted icon file must not be empty")
        assertEquals("xdm-enhanced.png", extracted.name)
        assertTrue(
            extracted.absolutePath.replace("\\", "/").contains(".local/share/icons/hicolor/256x256/apps"),
            "Icon must be placed in standard XDG hicolor 256x256 directory"
        )

        // Verify valid PNG magic bytes: 0x89 0x50 0x4E 0x47
        val magic = ByteArray(4)
        FileInputStream(extracted).use { it.read(magic) }
        assertEquals(0x89.toByte(), magic[0])
        assertEquals('P'.code.toByte(), magic[1])
        assertEquals('N'.code.toByte(), magic[2])
        assertEquals('G'.code.toByte(), magic[3])

        // Verify fallback icon in root .local/share/icons
        val rootFallback = File(homeDir, ".local/share/icons/xdm-enhanced.png")
        assertTrue(rootFallback.isFile, "Root fallback icon in .local/share/icons must exist")
        assertTrue(rootFallback.length() > 0, "Root fallback icon must not be empty")

        // Idempotency: calling again should succeed without modifying/breaking
        val secondCall = LinuxDesktopUtils.ensureUserIconInstalled(homeDir)
        assertNotNull(secondCall)
        assertEquals(extracted.absolutePath, secondCall!!.absolutePath)
    }

    @Test
    fun testAutoStartLinuxPathsAndLegacyMigration() {
        val primaryFile = AutoStart.linuxDesktopFile()
        val legacyFile = AutoStart.legacyLinuxDesktopFile()

        assertTrue(primaryFile.name == "xdm-enhanced.desktop", "Primary file must be xdm-enhanced.desktop")
        assertTrue(legacyFile.name == "xdm-app.desktop", "Legacy file must be xdm-app.desktop")

        val cmd = listOf("/opt/xdm-app/bin/xdm-app", "--minimized")
        val content = AutoStart.linuxDesktopContent(cmd)
        assertTrue(content.contains("Name=XDM Enhanced"))
        assertTrue(content.contains("Icon=xdm-enhanced"))
    }
}

