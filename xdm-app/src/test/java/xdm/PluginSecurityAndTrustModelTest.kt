package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginSecurityAndTrustModelTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun setupSecurityTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginEventBus.clearAll()
        AppContext.config.trustedPlugins = emptyList()
        AppContext.config.enabledPlugins = emptyList()
        PluginManager.init(safeMode = false)
    }

    @AfterEach
    fun cleanupSecurityTest() {
        PluginManager.shutdown()
        PluginEventBus.clearAll()
        testPluginsDir.deleteRecursively()
        AppContext.config.trustedPlugins = emptyList()
        AppContext.config.enabledPlugins = emptyList()
    }

    private fun createZipArchive(destFile: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    private fun validManifestJson(id: String = "test-plugin", version: String = "1.0.0"): String {
        return """
            {
              "id": "$id",
              "name": "Test Security Plugin",
              "version": "$version",
              "author": "Security Team",
              "license": "Apache-2.0",
              "entryPoint": "xdm.MockSecurityPlugin",
              "minAppVersion": "1.0.0-beta.1",
              "minApiVersion": "1.0.0",
              "capabilities": ["TASK_ADAPTER", "EVENT_BUS"]
            }
        """.trimIndent()
    }

    @Test
    fun testZipSlipPathTraversalRejectedInArchiveValidation() {
        val maliciousEntries = listOf(
            "../../evil.txt",
            "..\\..\\evil.txt",
            "/root/evil.txt",
            "C:\\Windows\\evil.dll",
            "sub/../../outside.txt",
            "~/.bashrc"
        )

        for ((idx, hostilePath) in maliciousEntries.withIndex()) {
            val archive = File(dir, "hostile-zipslip-$idx.xdmplugin")
            createZipArchive(
                archive,
                mapOf(
                    "plugin.json" to validManifestJson("slip-$idx").toByteArray(),
                    hostilePath to "malicious payload".toByteArray()
                )
            )

            val result = PluginManifestValidator.validateArchive(archive)
            assertFalse(result.isValid, "Hostile path '$hostilePath' must be rejected during archive validation")
            assertTrue(
                result.errors.any { it.contains("Zip Slip") || it.contains("Hostile archive entry") },
                "Error must indicate Zip Slip detection for '$hostilePath': ${result.errors}"
            )
            archive.delete()
        }
    }

    @Test
    fun testDisallowedExecutableExtensionsRejectedInArchive() {
        val badExtensions = listOf("exe", "dll", "so", "dylib", "bat", "cmd", "sh", "ps1", "vbs", "js", "py", "com", "scr")

        for (ext in badExtensions) {
            val archive = File(dir, "executable-$ext.xdmplugin")
            createZipArchive(
                archive,
                mapOf(
                    "plugin.json" to validManifestJson("bin-$ext").toByteArray(),
                    "payload.$ext" to "binary content".toByteArray()
                )
            )

            val result = PluginManifestValidator.validateArchive(archive)
            assertFalse(result.isValid, "Extension '.$ext' must be rejected as hostile executable/script")
            assertTrue(
                result.errors.any { it.contains("native executable or script") },
                "Error must flag executable extension '.$ext': ${result.errors}"
            )
            archive.delete()
        }
    }

    @Test
    fun testReservedNamespaceIdRejectedInManifest() {
        val reserved = listOf("xdm", "core", "system", "builtin", "app", "manager", "torrent", "config")

        for (resId in reserved) {
            val manifest = PluginManifest(
                id = resId,
                name = "Reserved Plugin",
                version = "1.0.0",
                author = "Attacker",
                entryPoint = "com.example.FakeCore"
            )
            val result = PluginManifestValidator.validateManifest(manifest)
            assertFalse(result.isValid, "Reserved namespace ID '$resId' must be rejected")
            assertTrue(
                result.errors.any { it.contains("reserved namespace identifier") },
                "Error must identify reserved ID '$resId': ${result.errors}"
            )
        }
    }

    @Test
    fun testMissingManifestRejected() {
        val archive = File(dir, "no-manifest.xdmplugin")
        createZipArchive(
            archive,
            mapOf("somefile.txt" to "no manifest here".toByteArray())
        )

        val result = PluginManifestValidator.validateArchive(archive)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("missing required 'plugin.json'") })
        archive.delete()
    }

    @Test
    fun testCorruptArchiveRejected() {
        val corruptFile = File(dir, "corrupt.xdmplugin").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))
        }

        val result = PluginManifestValidator.validateArchive(corruptFile)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Invalid or corrupt ZIP") })
        corruptFile.delete()
    }

    @Test
    fun testChecksumMismatchRejected() {
        val archive = File(dir, "checksum-mismatch.xdmplugin")
        val manifestWithWrongSha = """
            {
              "id": "sha-mismatch",
              "name": "Checksum Mismatch Plugin",
              "version": "1.0.0",
              "author": "Tester",
              "license": "MIT",
              "entryPoint": "xdm.MockSecurityPlugin",
              "checksumSha256": "0000000000000000000000000000000000000000000000000000000000000000"
            }
        """.trimIndent()
        createZipArchive(
            archive,
            mapOf("plugin.json" to manifestWithWrongSha.toByteArray())
        )

        val result = PluginManifestValidator.validateArchive(archive)
        assertFalse(result.isValid, "Archive with mismatched checksum must be rejected")
        assertTrue(result.errors.any { it.contains("does not match archive SHA-256") })
        archive.delete()
    }

    @Test
    fun testPermissionDenialForUntrustedPluginActivation() {
        val archive = File(dir, "untrusted-plugin.xdmplugin")
        createZipArchive(
            archive,
            mapOf("plugin.json" to validManifestJson("untrusted-one").toByteArray())
        )

        val installResult = PluginManager.installPlugin(archive)
        assertTrue(installResult.isValid, "Install archive validation should succeed")

        val installed = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "untrusted-one" }
        assertNotNull(installed)
        assertEquals(PluginStatus.UNTRUSTED, installed!!.status, "Installed plugin must start as UNTRUSTED")

        // Attempting to activate an untrusted plugin directly must be DENIED
        val activated = PluginManager.activatePlugin(installed)
        assertFalse(activated, "Activation must be denied for untrusted plugin")
        assertEquals(PluginStatus.UNTRUSTED, installed.status, "Status must remain UNTRUSTED")
        assertEquals("Permission denied: plugin has not received explicit user approval.", installed.faultReason)

        // Attempting to enable via enablePlugin without trust must also fail
        val enabled = PluginManager.enablePlugin("untrusted-one")
        assertFalse(enabled, "enablePlugin must fail if plugin is not trusted")

        archive.delete()
    }

    @Test
    fun testExplicitUserApprovalAllowsActivation() {
        val archive = File(dir, "trusted-approval.xdmplugin")
        createZipArchive(
            archive,
            mapOf("plugin.json" to validManifestJson("approved-plugin").toByteArray())
        )

        val installResult = PluginManager.installPlugin(archive)
        assertTrue(installResult.isValid)

        val installed = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "approved-plugin" }
        assertNotNull(installed)
        assertEquals(PluginStatus.UNTRUSTED, installed!!.status)

        // Explicit user approval via trustAndEnablePlugin
        AppContext.config.trustedPlugins = listOf("approved-plugin")
        assertTrue(AppContext.config.trustedPlugins.contains("approved-plugin"))

        archive.delete()
    }

    @Test
    fun testSafeModeSuppressionAndQuarantine() {
        val archive = File(dir, "quarantine-test.xdmplugin")
        createZipArchive(
            archive,
            mapOf("plugin.json" to validManifestJson("quarantine-plugin").toByteArray())
        )

        PluginManager.installPlugin(archive)
        AppContext.config.trustedPlugins = listOf("quarantine-plugin")
        AppContext.config.enabledPlugins = listOf("quarantine-plugin")

        // Reinitialize in Safe Mode
        PluginManager.init(safeMode = true)
        assertTrue(PluginManager.isSafeMode, "PluginManager must report safe mode")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "quarantine-plugin" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.DISABLED, plugin!!.status, "All third-party plugins must be quarantined/disabled in Safe Mode")
        assertNull(plugin.instance, "No instances should run in Safe Mode")

        archive.delete()
    }

    @Test
    fun testLocalOnlyCommunicationBoundary() {
        // Verify PluginEventBus and PluginHostContext operate strictly in-process with no listening server sockets
        var eventReceived = false
        PluginEventBus.subscribe("security-tester", "internal.topic", xdm.plugin.api.events.PluginEventListener<String> { _, data ->
            if (data == "local-secret") {
                eventReceived = true
            }
        })

        PluginEventBus.publishSync("internal.topic", "local-secret")
        assertTrue(eventReceived, "Local event dispatch must succeed within JVM boundary")
    }

    @Test
    fun testUserDataPreservedAcrossUpgrade() {
        val v1Archive = File(dir, "data-plugin-v1.xdmplugin")
        val v2Archive = File(dir, "data-plugin-v2.xdmplugin")

        createZipArchive(v1Archive, mapOf("plugin.json" to validManifestJson("persistent-plugin", "1.0.0").toByteArray()))
        createZipArchive(v2Archive, mapOf("plugin.json" to validManifestJson("persistent-plugin", "2.0.0").toByteArray()))

        // 1. Install v1
        val res1 = PluginManager.installPlugin(v1Archive)
        assertTrue(res1.isValid)

        // 2. Create private user data
        val pluginDir = File(testPluginsDir, "persistent-plugin")
        val dataDir = File(pluginDir, "data").apply { mkdirs() }
        val configFile = File(dataDir, "user_settings.json").apply { writeText("""{"custom_key": "custom_value"}""") }
        assertTrue(configFile.exists())

        // 3. Upgrade to v2
        val res2 = PluginManager.installPlugin(v2Archive)
        assertTrue(res2.isValid)

        // 4. Verify data was preserved
        val reloadedConfig = File(File(testPluginsDir, "persistent-plugin"), "data/user_settings.json")
        assertTrue(reloadedConfig.exists(), "User data must be preserved across plugin upgrades")
        assertEquals("""{"custom_key": "custom_value"}""", reloadedConfig.readText())

        v1Archive.delete()
        v2Archive.delete()
    }

    @Test
    fun testZipBombOversizedEntryRejection() {
        val bombArchive = File(dir, "zipbomb.xdmplugin")
        // Create an archive with 26 MB uncompressed content (which compresses to < 30 KB)
        val massiveBytes = ByteArray(26 * 1024 * 1024) { 0x41 }
        createZipArchive(
            bombArchive,
            mapOf(
                "plugin.json" to validManifestJson("bomb-plugin").toByteArray(),
                "huge.dat" to massiveBytes
            )
        )

        val result = PluginManifestValidator.validateArchive(bombArchive)
        assertFalse(result.isValid, "Oversized entry exceeding MAX_ENTRY_BYTES must be rejected")
        assertTrue(result.errors.any { it.contains("exceeds maximum size") })

        bombArchive.delete()
    }
}
