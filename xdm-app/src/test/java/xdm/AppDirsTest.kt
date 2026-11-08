package xdm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.OS
import xdm.app.utils.AppDirs
import java.io.File

/** The per-OS state folder, and the overrides the recording scripts and XDG rely on. */
class AppDirsTest {

    private val home = File("/home/u").path

    private fun resolve(os: OS, override: String? = null, vararg env: Pair<String, String>) =
        AppDirs.resolve(os, override, home, mapOf(*env)::get)

    @Test
    fun windowsUsesLocalAppData() {
        assertEquals(
            File("""C:\Users\u\AppData\Local""", "xdm-enhanced"),
            resolve(OS.Windows, null, "LOCALAPPDATA" to """C:\Users\u\AppData\Local""", "APPDATA" to "roaming"),
        )
    }

    @Test
    fun windowsFallsBackToAppDataThenHome() {
        assertEquals(File("roaming", "xdm-enhanced"), resolve(OS.Windows, null, "APPDATA" to "roaming"))
        assertEquals(File(home, "xdm-enhanced"), resolve(OS.Windows))
    }

    @Test
    fun macUsesApplicationSupport() {
        assertEquals(File(home, "Library/Application Support/xdm-enhanced"), resolve(OS.MacOS))
    }

    @Test
    fun linuxUsesXdgDataHome() {
        assertEquals(File(home, ".local/share/xdm-enhanced"), resolve(OS.Linux))
        assertEquals(File("/data/u", "xdm-enhanced"), resolve(OS.Linux, null, "XDG_DATA_HOME" to "/data/u"))
        assertEquals(
            File(home, ".local/share/xdm-enhanced"), resolve(OS.Linux, null, "XDG_DATA_HOME" to "rel/dir"),
            "a relative XDG_DATA_HOME is invalid and ignored"
        )
    }

    @Test
    fun propertyOverridesEveryOs() {
        OS.entries.forEach {
            assertEquals(File("/tmp/rec/xdm-enhanced").absoluteFile, resolve(it, "/tmp/rec/xdm-enhanced", "LOCALAPPDATA" to "x"), "$it")
        }
        assertEquals(File(home, ".local/share/xdm-enhanced"), resolve(OS.Linux, " "), "a blank override is ignored")
    }

    @Test
    fun migrationCopiesConfigAndDatabase(@TempDir tempDir: File) {
        val legacyDir = File(tempDir, AppDirs.LEGACY_NAME)
        legacyDir.mkdirs()
        File(legacyDir, "xdm-app.config").writeText("legacy-config-data")
        File(legacyDir, "xdm.db").writeText("legacy-db-data")
        File(legacyDir, "tmp").mkdirs()
        File(File(legacyDir, "tmp"), "part.bin").writeText("temporary-chunk")

        val targetDir = File(tempDir, AppDirs.NAME)
        AppDirs.migrateLegacyData(targetDir, legacyDir)

        assertTrue(File(targetDir, "xdm-app.config").exists(), "config was migrated")
        assertEquals("legacy-config-data", File(targetDir, "xdm-app.config").readText())
        assertTrue(File(targetDir, "xdm.db").exists(), "db was migrated")
        assertEquals("legacy-db-data", File(targetDir, "xdm.db").readText())
        assertTrue(!File(targetDir, "tmp").exists(), "tmp directory is not copied")
        assertTrue(File(legacyDir, "xdm-app.config").exists(), "legacy source file preserved")
    }
}
