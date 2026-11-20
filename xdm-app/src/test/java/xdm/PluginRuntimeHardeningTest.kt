package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.plugin.api.PluginContext
import xdm.plugin.api.Registration
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.download.DownloadProvider
import xdm.plugin.api.models.PluginDownloadRequest
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginRuntimeHardeningTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initHardeningTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.resetRestartFlag()
        PluginManager.init(safeMode = false)
    }

    @AfterEach
    fun cleanupHardeningTest() {
        PluginManager.shutdown()
        testPluginsDir.deleteRecursively()
        PluginManager.resetRestartFlag()
    }

    private fun createPluginDir(
        id: String,
        entryPoint: String,
        version: String = "1.0.0",
        dependencies: List<String> = emptyList(),
        targetPlatforms: List<String> = emptyList()
    ): File {
        val pluginDir = File(testPluginsDir, id).apply { mkdirs() }
        val manifest = PluginManifest(
            id = id,
            name = "Test $id",
            version = version,
            author = "Unit Test",
            entryPoint = entryPoint,
            dependencies = dependencies,
            targetPlatforms = targetPlatforms
        )
        val jsonStr = kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(manifest)
        File(pluginDir, "plugin.json").writeText(jsonStr)
        return pluginDir
    }

    private fun createPluginArchive(
        id: String,
        entryPoint: String,
        version: String = "1.0.0",
        dependencies: List<String> = emptyList(),
        targetPlatforms: List<String> = emptyList(),
        extraFiles: Map<String, ByteArray> = emptyMap()
    ): File {
        val zipFile = File(dir, "$id.xdmplugin")
        val manifest = PluginManifest(
            id = id,
            name = "Test $id",
            version = version,
            author = "Unit Test",
            entryPoint = entryPoint,
            dependencies = dependencies,
            targetPlatforms = targetPlatforms
        )
        val jsonStr = kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(manifest)
        val entries = mutableMapOf<String, ByteArray>()
        entries["plugin.json"] = jsonStr.toByteArray(StandardCharsets.UTF_8)
        entries.putAll(extraFiles)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return zipFile
    }

    @Test
    fun testBadManifestValidation() {
        // 1. Invalid ID with special characters/spaces
        val badIdManifest = PluginManifest(
            id = "invalid id with spaces!",
            name = "Bad ID",
            version = "1.0.0",
            entryPoint = "xdm.WellBehavedLifecyclePlugin"
        )
        val val1 = PluginManifestValidator.validateManifest(badIdManifest)
        assertFalse(val1.isValid, "Manifest with invalid ID characters must be invalid")
        assertTrue(val1.errors.any { it.contains("invalid") })

        // 2. Incompatible target platform
        val incompatiblePlatform = if (PluginManifestValidator.getCurrentPlatform().startsWith("windows")) "macos-arm64" else "windows-x64"
        val badPlatManifest = PluginManifest(
            id = "bad-plat-plugin",
            name = "Bad Platform",
            version = "1.0.0",
            entryPoint = "xdm.WellBehavedLifecyclePlugin",
            targetPlatforms = listOf(incompatiblePlatform)
        )
        val val2 = PluginManifestValidator.validateManifest(badPlatManifest)
        assertFalse(val2.isValid, "Manifest targeting non-current platform must fail validation")
        assertTrue(val2.errors.any { it.contains("does not support current platform") })

        // 3. Blank entry point
        val badEntryManifest = PluginManifest(
            id = "bad-entry-plugin",
            name = "Bad Entry",
            version = "1.0.0",
            entryPoint = ""
        )
        val val3 = PluginManifestValidator.validateManifest(badEntryManifest)
        assertFalse(val3.isValid, "Manifest with empty entryPoint must fail validation")
    }

    @Test
    fun testUnavailableDependencyRejection() {
        val pluginDir = createPluginDir(
            id = "dependent-plugin",
            entryPoint = "xdm.WellBehavedLifecyclePlugin",
            dependencies = listOf("missing-dep@>=1.0.0")
        )
        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "dependent-plugin" }
        assertNotNull(loaded, "Plugin should be discovered")

        // Mark as trusted & enabled
        AppContext.config.trustedPlugins = listOf("dependent-plugin")
        AppContext.config.enabledPlugins = listOf("dependent-plugin")

        val activated = PluginManager.activatePlugin(loaded!!)
        assertFalse(activated, "Activation must fail when dependency is missing")
        assertEquals(PluginStatus.FAULTED, loaded.status)
        assertTrue(loaded.faultReason?.contains("Missing required dependency plugin 'missing-dep'") == true)

        val diagnostics = PluginManager.getDiagnostics()
        assertTrue(diagnostics.any { it.pluginId == "dependent-plugin" && it.faultReason.contains("missing-dep") })
    }

    @Test
    fun testLifecycleTimeoutHandling() {
        val pluginDir = createPluginDir(
            id = "hanging-plugin",
            entryPoint = "xdm.HangingInitPlugin"
        )
        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "hanging-plugin" }
        assertNotNull(loaded)

        val activated = PluginManager.activatePlugin(loaded!!)
        assertFalse(activated, "Activation must abort on bounded lifecycle timeout")
        assertEquals(PluginStatus.FAULTED, loaded.status)
        assertTrue(loaded.faultReason?.contains("timed out") == true, "Fault reason must state timeout: ${loaded.faultReason}")

        val diagnostics = PluginManager.getDiagnostics()
        assertTrue(diagnostics.any { it.pluginId == "hanging-plugin" && it.faultReason.contains("timed out") })
    }

    @Test
    fun testInitializationCrashIsolationAndSecretSanitization() {
        val pluginDir = createPluginDir(
            id = "crashing-secrets-plugin",
            entryPoint = "xdm.CrashingInitWithSecretsPlugin"
        )
        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "crashing-secrets-plugin" }
        assertNotNull(loaded)

        val activated = PluginManager.activatePlugin(loaded!!)
        assertFalse(activated, "Crashing plugin must not activate")
        assertEquals(PluginStatus.FAULTED, loaded.status)

        // Verify diagnostic was saved and secrets were sanitized
        val diagnostics = PluginManager.getDiagnostics()
        val diag = diagnostics.firstOrNull { it.pluginId == "crashing-secrets-plugin" }
        assertNotNull(diag, "Diagnostic record must be saved for faulted plugin")

        assertFalse(diag!!.faultReason.contains("SuperSecretPassword!"), "Plaintext password must NOT appear in diagnostics")
        assertFalse(diag.faultReason.contains("secretToken12345"), "Plaintext bearer token must NOT appear in diagnostics")
        assertFalse(diag.faultReason.contains("pass123"), "URL credential must NOT appear in diagnostics")
        assertTrue(diag.faultReason.contains("password=***") || diag.faultReason.contains(":***@"))
    }

    @Test
    fun testRepeatedDisableReEnableCycles() {
        val pluginDir = createPluginDir(
            id = "well-behaved-plugin",
            entryPoint = "xdm.WellBehavedLifecyclePlugin"
        )
        PluginManager.discoverPlugins()
        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "well-behaved-plugin" }
        assertNotNull(plugin)

        AppContext.config.trustedPlugins = listOf("well-behaved-plugin")

        for (cycle in 1..3) {
            // Enable
            val enabled = PluginManager.enablePlugin("well-behaved-plugin")
            assertTrue(enabled, "Plugin should enable successfully on cycle $cycle")
            assertEquals(PluginStatus.ACTIVE, plugin!!.status)
            assertNotNull(PluginManager.findDownloadProvider("https://wellbehaved.org/test"))

            // Disable
            PluginManager.disablePlugin("well-behaved-plugin")
            assertEquals(PluginStatus.DISABLED, plugin.status)
            assertNull(PluginManager.findDownloadProvider("https://wellbehaved.org/test"))
        }
    }

    @Test
    fun testCircuitBreakerSafeModeRecovery() {
        val recoveryState = PluginManager.loadCrashRecoveryState()
        recoveryState.consecutiveStartupFailures = 3
        PluginManager.saveCrashRecoveryState(recoveryState)

        createPluginDir(id = "quarantined-test", entryPoint = "xdm.WellBehavedLifecyclePlugin")
        AppContext.config.trustedPlugins = listOf("quarantined-test")
        AppContext.config.enabledPlugins = listOf("quarantined-test")

        PluginManager.init(safeMode = false)

        assertTrue(PluginManager.isSafeMode, "PluginManager must enter Safe Mode after 3 consecutive startup failures")
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "quarantined-test" }
        assertNotNull(loaded)
        assertEquals(PluginStatus.DISABLED, loaded!!.status, "Third party plugins must remain DISABLED in Safe Mode")
    }

    @Test
    fun testRepeatedPluginFailureQuarantine() {
        val pluginDir = createPluginDir(
            id = "crashing-quarantine-test",
            entryPoint = "xdm.CrashingInitWithSecretsPlugin"
        )
        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "crashing-quarantine-test" }
        assertNotNull(loaded)

        AppContext.config.trustedPlugins = listOf("crashing-quarantine-test")
        AppContext.config.enabledPlugins = listOf("crashing-quarantine-test")

        // Fail 3 times
        PluginManager.activatePlugin(loaded!!)
        PluginManager.activatePlugin(loaded)
        PluginManager.activatePlugin(loaded)

        val state = PluginManager.loadCrashRecoveryState()
        assertTrue(state.quarantinedPlugins.contains("crashing-quarantine-test"), "Plugin must be quarantined after 3 failures")
        assertFalse(AppContext.config.enabledPlugins.contains("crashing-quarantine-test"), "Plugin must be removed from enabled list")

        // 4th activation attempt is blocked immediately
        val blocked = PluginManager.activatePlugin(loaded)
        assertFalse(blocked)
        assertTrue(loaded.faultReason?.contains("quarantined") == true)
    }

    @Test
    fun testNativeLibraryRequiresRestart() {
        val pluginDir = createPluginDir(
            id = "native-lib-plugin",
            entryPoint = "xdm.WellBehavedLifecyclePlugin"
        )
        // Add dummy native library
        File(pluginDir, "native-hook.dll").writeText("DUMMY_DLL_BYTES")

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "native-lib-plugin" }
        assertNotNull(loaded)

        val activated = PluginManager.activatePlugin(loaded!!)
        assertTrue(activated)
        assertEquals(PluginStatus.ACTIVE, loaded.status)

        // Deactivate
        PluginManager.deactivatePlugin(loaded, setStatus = true)
        assertTrue(PluginManager.requiresRestart, "Native library presence must trigger requiresRestart = true")
        assertEquals(PluginStatus.NEEDS_RESTART, loaded.status, "Plugin status must be NEEDS_RESTART")
    }

    @Test
    fun testUninstallWithDataPreservationAndRestore() {
        val archive = createPluginArchive(
            id = "preservation-plugin",
            entryPoint = "xdm.WellBehavedLifecyclePlugin"
        )

        // Install archive
        val res = PluginManager.installPlugin(archive)
        assertTrue(res.isValid)
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "preservation-plugin" }
        assertNotNull(loaded)

        // Create data files
        val dataDir = File(loaded!!.pluginDir, "data").apply { mkdirs() }
        File(dataDir, "user-preferences.json").writeText("{\"theme\":\"dark\",\"customPort\":8088}")
        File(dataDir, "history.log").writeText("item1,item2,item3")

        // Uninstall with data preservation
        val uninstalled = PluginManager.uninstallPlugin("preservation-plugin", preserveData = true)
        assertTrue(uninstalled)
        assertFalse(loaded.pluginDir.exists(), "Plugin directory must be removed on uninstall")

        val preservedDir = File(testPluginsDir, ".preserved-data/preservation-plugin")
        assertTrue(preservedDir.exists(), "Preserved data directory must exist")
        assertTrue(File(preservedDir, "user-preferences.json").exists())

        // Re-install plugin
        val res2 = PluginManager.installPlugin(archive)
        assertTrue(res2.isValid)
        val reloaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "preservation-plugin" }
        assertNotNull(reloaded)

        val restoredPrefs = File(reloaded!!.pluginDir, "data/user-preferences.json")
        assertTrue(restoredPrefs.exists(), "User preferences must be restored on re-installation")
        assertEquals("{\"theme\":\"dark\",\"customPort\":8088}", restoredPrefs.readText())

        // Uninstall without preservation
        val uninstalledClean = PluginManager.uninstallPlugin("preservation-plugin", preserveData = false)
        assertTrue(uninstalledClean)
        assertFalse(reloaded.pluginDir.exists())
    }

    @Test
    fun testDiagnosticSecretSanitizationPatterns() {
        val urlInput = "Connecting to https://admin:SuperSecretPass123!@proxy.corp.internal:8080 failed"
        val bearerInput = "Authorization failed with Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0"
        val paramInput = "Invalid request password=UnsafeSecretPassword and apiKey=SECRET_KEY_9999"

        val sanitizedUrl = PluginManager.sanitizeDiagnostics(urlInput)
        val sanitizedBearer = PluginManager.sanitizeDiagnostics(bearerInput)
        val sanitizedParam = PluginManager.sanitizeDiagnostics(paramInput)

        assertFalse(sanitizedUrl.contains("SuperSecretPass123!"))
        assertTrue(sanitizedUrl.contains(":***@"))

        assertFalse(sanitizedBearer.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(sanitizedBearer.contains("Bearer ***"))

        assertFalse(sanitizedParam.contains("UnsafeSecretPassword"))
        assertFalse(sanitizedParam.contains("SECRET_KEY_9999"))
        assertTrue(sanitizedParam.contains("password=***"))
        assertTrue(sanitizedParam.contains("apiKey=***"))
    }

    @Test
    fun testTopologicalDependencyActivationAndCycleDetection() {
        val baseDir = createPluginDir(id = "base-lib", entryPoint = "xdm.WellBehavedLifecyclePlugin")
        val extDir = createPluginDir(
            id = "ext-module",
            entryPoint = "xdm.WellBehavedLifecyclePlugin",
            dependencies = listOf("base-lib")
        )

        PluginManager.discoverPlugins()
        val base = PluginManager.installedPlugins.first { it.manifest.id == "base-lib" }
        val ext = PluginManager.installedPlugins.first { it.manifest.id == "ext-module" }

        // Reverse order passed into sort
        val sorted = PluginManager.sortPluginsByDependencies(listOf(ext, base))
        assertEquals("base-lib", sorted[0].manifest.id, "base-lib must be sorted before ext-module")
        assertEquals("ext-module", sorted[1].manifest.id)

        // Cycle test
        val cyclicDirA = createPluginDir(id = "cycle-a", entryPoint = "xdm.WellBehavedLifecyclePlugin", dependencies = listOf("cycle-b"))
        val cyclicDirB = createPluginDir(id = "cycle-b", entryPoint = "xdm.WellBehavedLifecyclePlugin", dependencies = listOf("cycle-a"))
        PluginManager.discoverPlugins()
        val pA = PluginManager.installedPlugins.first { it.manifest.id == "cycle-a" }
        val pB = PluginManager.installedPlugins.first { it.manifest.id == "cycle-b" }

        val cyclicSorted = PluginManager.sortPluginsByDependencies(listOf(pA, pB))
        assertTrue(cyclicSorted.any { it.status == PluginStatus.FAULTED && it.faultReason?.contains("Cyclic") == true })
    }

    @Test
    fun testGracefulShutdown() {
        val pluginDir = createPluginDir(id = "shutdown-test", entryPoint = "xdm.WellBehavedLifecyclePlugin")
        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.first { it.manifest.id == "shutdown-test" }
        PluginManager.activatePlugin(loaded)
        assertEquals(PluginStatus.ACTIVE, loaded.status)

        // Simulate startup failure count
        val state = PluginManager.loadCrashRecoveryState()
        state.consecutiveStartupFailures = 1
        PluginManager.saveCrashRecoveryState(state)

        PluginManager.shutdown()
        assertEquals(PluginStatus.DISABLED, loaded.status)

        val finalState = PluginManager.loadCrashRecoveryState()
        assertEquals(0, finalState.consecutiveStartupFailures, "Startup failure counter must be reset to 0 upon graceful shutdown")
    }
}

// Test Plugin Implementations

class HangingInitPlugin : XdmPlugin {
    override fun getId(): String = "hanging-plugin"
    override fun init(context: PluginContext?) {
        try {
            Thread.sleep(15000)
        } catch (_: InterruptedException) {
            // Cancelled
        }
    }
    override fun shutdown() {}
}

class CrashingInitWithSecretsPlugin : XdmPlugin {
    override fun getId(): String = "crashing-secrets-plugin"
    override fun init(context: PluginContext?) {
        throw RuntimeException("Fatal error password=SuperSecretPassword! with Bearer secretToken12345 at https://user:pass123@api.internal")
    }
    override fun shutdown() {}
}

class WellBehavedLifecyclePlugin : XdmPlugin {
    private var sub: Registration? = null
    override fun getId(): String = "well-behaved-plugin"
    override fun init(context: PluginContext?) {
        context?.registerDownloadProvider(object : DownloadProvider {
            override fun getName(): String = "wb-provider"
            override fun canHandle(url: String?): Boolean = url?.startsWith("https://wellbehaved.org") == true
            override fun createDownloadTask(req: PluginDownloadRequest?) = null
        })
        sub = context?.registerProxyProvider(object : xdm.plugin.api.network.ProxyProvider {
            override fun getProviderId(): String = "wb-proxy-provider"
            override fun getPriority(): Int = 10
            override fun resolveProxy(uri: java.net.URI?) = null
        })
    }
    override fun shutdown() {
        sub?.unregister()
        sub = null
    }
}
