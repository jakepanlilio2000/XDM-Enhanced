package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.SettingsWindow
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.plugin.api.PluginContext
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.media.MediaProvider
import xdm.plugin.api.media.PluginMediaStream
import xdm.plugin.api.models.DownloadInfo
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

class PluginFrameworkTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initPluginTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.init(safeMode = false)
    }

    @AfterEach
    fun cleanupPluginTest() {
        PluginManager.shutdown()
        testPluginsDir.deleteRecursively()
    }

    @Test
    fun testSamplePluginArchiveValidation() {
        val samplePackage = resolveSamplePlugin()
        assertTrue(samplePackage.exists(), "Sample plugin package should exist: ${samplePackage.absolutePath}")

        val result = PluginManifestValidator.validateArchive(samplePackage)
        assertTrue(result.isValid, "Sample plugin should pass archive validation: ${result.errors}")
        assertNotNull(result.manifest)
        assertEquals("checksum-verifier", result.manifest!!.id)
        assertEquals("Checksum Verifier", result.manifest!!.name)
        assertEquals("xdm.sample.plugin.ChecksumVerifierPlugin", result.manifest!!.entryPoint)
        assertTrue(result.manifest!!.capabilities.contains("POST_DOWNLOAD_HOOK"))
        assertTrue(result.manifest!!.capabilities.contains("CONTEXT_MENU"))
        assertTrue(result.manifest!!.capabilities.contains("SETTINGS_PANEL"))
        assertTrue(result.sha256.isNotBlank(), "Archive SHA-256 hash must be computed")
    }

    @Test
    fun testZipSlipAttackRejection() {
        val evilZip = File(dir, "zipslip.xdmplugin")
        createZip(evilZip, mapOf(
            "plugin.json" to validPluginJson("evil-plugin"),
            "../evil.txt" to "malicious payload".toByteArray(StandardCharsets.UTF_8)
        ))

        val result = PluginManifestValidator.validateArchive(evilZip)
        assertFalse(result.isValid, "Archive with path traversal must be rejected")
        assertTrue(result.errors.any { it.contains("Zip Slip") }, "Error message must mention Zip Slip: ${result.errors}")
    }

    @Test
    fun testNativeExecutableRejection() {
        val malwareZip = File(dir, "malware.xdmplugin")
        createZip(evilZip = malwareZip, entries = mapOf(
            "plugin.json" to validPluginJson("malware-plugin"),
            "payload.exe" to "MZ...fake-exe".toByteArray(StandardCharsets.UTF_8)
        ))

        val result = PluginManifestValidator.validateArchive(malwareZip)
        assertFalse(result.isValid, "Archive with native executable must be rejected")
        assertTrue(
            result.errors.any { it.contains("executable or script") },
            "Error message must mention security violation: ${result.errors}"
        )
    }

    @Test
    fun testMissingManifestRejection() {
        val noManifestZip = File(dir, "nomanifest.xdmplugin")
        createZip(noManifestZip, mapOf(
            "readme.txt" to "Just a file".toByteArray(StandardCharsets.UTF_8)
        ))

        val result = PluginManifestValidator.validateArchive(noManifestZip)
        assertFalse(result.isValid, "Archive without plugin.json must be rejected")
        assertTrue(
            result.errors.any { it.contains("missing required 'plugin.json'") },
            "Error message must mention missing plugin.json: ${result.errors}"
        )
    }

    @Test
    fun testChecksumMismatchRejection() {
        val mismatchZip = File(dir, "mismatch.xdmplugin")
        createZip(mismatchZip, mapOf(
            "plugin.json" to validPluginJson("mismatch-plugin", checksumSha256 = "0000000000000000000000000000000000000000000000000000000000000000"),
            "test.txt" to "some test data".toByteArray(StandardCharsets.UTF_8)
        ))

        val result = PluginManifestValidator.validateArchive(mismatchZip)
        assertFalse(result.isValid, "Archive with mismatching checksum in manifest must be rejected")
        assertTrue(
            result.errors.any { it.contains("Manifest checksum") && it.contains("does not match archive SHA-256") },
            "Error message must mention checksum mismatch: ${result.errors}"
        )
    }

    private fun resolveSamplePlugin(): File {
        val candidates = listOf(
            File("sample-plugins/checksum-verifier.xdmplugin"),
            File("../sample-plugins/checksum-verifier.xdmplugin"),
            File(System.getProperty("user.dir"), "sample-plugins/checksum-verifier.xdmplugin"),
            File(System.getProperty("user.dir"), "../sample-plugins/checksum-verifier.xdmplugin")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Cannot find sample-plugins/checksum-verifier.xdmplugin. Checked: ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun testPluginInstallationAndQuarantine() {
        val samplePackage = resolveSamplePlugin()
        val installResult = PluginManager.installPlugin(samplePackage)
        assertTrue(installResult.isValid, "Installation should succeed: ${installResult.errors}")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "checksum-verifier" }
        assertNotNull(plugin, "Plugin must be registered in installed plugins")
        // Untrusted by default until user explicitly confirms
        assertEquals(PluginStatus.UNTRUSTED, plugin!!.status, "Newly installed plugin must be quarantined as UNTRUSTED")
        assertNull(plugin.instance, "Untrusted plugin must not be instantiated")
    }

    @Test
    fun testPluginTrustAndActivation() {
        val samplePackage = resolveSamplePlugin()
        PluginManager.installPlugin(samplePackage)

        val success = PluginManager.trustAndEnablePlugin("checksum-verifier")
        assertTrue(success, "Trusting and enabling plugin should succeed")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "checksum-verifier" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.ACTIVE, plugin!!.status, "Plugin should be ACTIVE")
        assertNotNull(plugin.instance, "Plugin instance must exist")
        assertNotNull(plugin.context, "Plugin context must exist")

        // Check registered capabilities
        assertTrue(plugin.context!!.postDownloadHooks.isNotEmpty(), "PostDownloadHook must be registered")
        assertTrue(plugin.context!!.contextMenuContributors.isNotEmpty(), "ContextMenuContributor must be registered")
        assertTrue(plugin.context!!.settingsPanels.isNotEmpty(), "SettingsPanel must be registered")

        // Disable plugin
        PluginManager.disablePlugin("checksum-verifier")
        assertEquals(PluginStatus.DISABLED, plugin.status, "Plugin should be DISABLED")
        assertNull(plugin.instance, "Instance should be cleaned up on disable")
    }

    @Test
    fun testPostDownloadHookExecution() {
        val samplePackage = resolveSamplePlugin()
        PluginManager.installPlugin(samplePackage)
        PluginManager.trustAndEnablePlugin("checksum-verifier")

        // Create a test file
        val completedFile = File(dir, "completed_download.bin")
        completedFile.writeBytes("Sample downloaded payload for checksum verification".toByteArray())

        // Trigger post download notification
        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted(completedFile.absolutePath, 1001L)
        }
    }

    @Test
    fun testContextMenuActions() {
        val samplePackage = resolveSamplePlugin()
        PluginManager.installPlugin(samplePackage)
        PluginManager.trustAndEnablePlugin("checksum-verifier")

        val targetFile = File(dir, "test_file.txt").apply { writeText("Hello Plugin Test") }
        val testDownload = DownloadInfo(
            1002L,
            "http://example.com/test_file.txt",
            "test_file.txt",
            targetFile.absolutePath,
            targetFile.length(),
            targetFile.length(),
            100,
            0f,
            "FINISHED",
            "None",
            System.currentTimeMillis()
        )

        val actions = PluginManager.getContextMenuActions(listOf(testDownload))
        assertFalse(actions.isEmpty(), "Context menu action must be provided by plugin")
        val verifyAction = actions.firstOrNull { it.id == "verify-checksum" }
        assertNotNull(verifyAction, "Action 'verify-checksum' must be present")
        assertEquals("Calculate SHA-256 Checksum", verifyAction!!.label)

        // Invoke handler
        assertDoesNotThrow {
            verifyAction.handler.accept(listOf(testDownload))
        }
    }

    @Test
    fun testFaultIsolation() {
        // Create synthetic faulted plugin
        val faultPluginDir = File(testPluginsDir, "faulty-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "faulty-plugin",
            name = "Faulty Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "A plugin that crashes in init",
            entryPoint = "xdm.FaultyTestPlugin"
        )
        File(faultPluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "faulty-plugin" }
        assertNotNull(loaded, "Faulty plugin should be discovered")

        // Activate should isolate fault without throwing out of PluginManager
        val activated = PluginManager.activatePlugin(loaded!!)
        assertFalse(activated, "Activating faulty plugin should return false")
        assertEquals(PluginStatus.FAULTED, loaded.status, "Plugin should be marked as FAULTED")
        assertNotNull(loaded.faultReason, "Fault reason should be populated")
        assertTrue(loaded.faultReason!!.contains("Simulated plugin crash"), "Faulty reason must contain error message")
    }

    @Test
    fun testShutdownFaultIsolation() {
        val faultPluginDir = File(testPluginsDir, "faulty-shutdown-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "faulty-shutdown-plugin",
            name = "Faulty Shutdown Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "A plugin that crashes in shutdown",
            entryPoint = "xdm.FaultyShutdownPlugin"
        )
        File(faultPluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "faulty-shutdown-plugin" }
        assertNotNull(loaded, "Faulty shutdown plugin should be discovered")

        val activated = PluginManager.activatePlugin(loaded!!)
        assertTrue(activated, "Plugin should activate successfully")
        assertEquals(PluginStatus.ACTIVE, loaded.status)

        // Deactivating a plugin that throws in shutdown() must not crash and must complete deactivation
        assertDoesNotThrow {
            PluginManager.deactivatePlugin(loaded)
        }
        assertEquals(PluginStatus.DISABLED, loaded.status)
        assertNull(loaded.instance, "Instance must be cleared even if shutdown threw")
        assertNull(loaded.context, "Context must be cleared even if shutdown threw")
    }

    @Test
    fun testEventBusSubscriberFaultIsolation() {
        val receivedList = mutableListOf<String>()
        PluginEventBus.subscribe("faulty-plugin", "test.topic", xdm.plugin.api.events.PluginEventListener<String> { _, _ ->
            throw RuntimeException("Subscriber explosion")
        })
        PluginEventBus.subscribe("healthy-plugin", "test.topic", xdm.plugin.api.events.PluginEventListener<String> { _, data ->
            receivedList.add(data)
        })

        assertDoesNotThrow {
            PluginEventBus.publish("test.topic", "hello")
        }
        assertEquals(listOf("hello"), receivedList, "Healthy subscriber must receive event despite faulty subscriber crash")
        PluginEventBus.clearAll()
    }

    @Test
    fun testSafeModeSuppression() {
        val samplePackage = resolveSamplePlugin()
        PluginManager.installPlugin(samplePackage)

        // Add to trusted and enabled in config
        AppContext.config.trustedPlugins = listOf("checksum-verifier")
        AppContext.config.enabledPlugins = listOf("checksum-verifier")

        // Reinitialize PluginManager in Safe Mode
        PluginManager.init(safeMode = true)

        assertTrue(PluginManager.isSafeMode, "PluginManager must report safe mode")
        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "checksum-verifier" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.DISABLED, plugin!!.status, "All third-party plugins must be disabled in Safe Mode")
        assertNull(plugin.instance, "No instances must run in Safe Mode")
    }

    @Test
    fun testPluginUninstallation() {
        val samplePackage = resolveSamplePlugin()
        PluginManager.installPlugin(samplePackage)
        PluginManager.trustAndEnablePlugin("checksum-verifier")

        val pluginDir = File(testPluginsDir, "checksum-verifier")
        assertTrue(pluginDir.exists(), "Plugin directory must exist before uninstallation")

        val uninstalled = PluginManager.uninstallPlugin("checksum-verifier")
        assertTrue(uninstalled, "Uninstall must return true")
        assertFalse(pluginDir.exists(), "Plugin directory must be removed after uninstallation")
        assertFalse(PluginManager.installedPlugins.any { it.manifest.id == "checksum-verifier" })
        assertFalse(AppContext.config.enabledPlugins.contains("checksum-verifier"))
        assertFalse(AppContext.config.trustedPlugins.contains("checksum-verifier"))
    }

    @Test
    fun testCoreDownloadUnimpairedWithZeroPlugins() {
        // Verify that with 0 plugins, all core download notifications execute normally
        assertEquals(0, PluginManager.installedPlugins.size)

        val task = HttpDownloadTaskInfo(
            id = 0L,
            url = "http://localhost/test.zip",
            fileName = "test.zip",
            respectFileName = true,
            cookie = "",
            headers = emptyMap(),
            origin = "",
            categoryId = null,
            targetFolder = dir.absolutePath,
            maxPiece = 8,
            authInfo = null,
            knownFileSize = null,
            etag = "",
            convertToSrt = false
        )

        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted(File(dir, "test.zip").absolutePath, 999L)
            val actions = PluginManager.getContextMenuActions(emptyList())
            assertTrue(actions.isEmpty())
            val toolbarActions = PluginManager.getToolbarActions()
            assertTrue(toolbarActions.isEmpty())
        }
    }

    @Test
    fun testCapturePluginsScreenshots() {
        val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")
        if (!artifactDir.exists()) artifactDir.mkdirs()

        val samplePackage = resolveSamplePlugin()
        PluginManager.installPlugin(samplePackage)

        SwingUtilities.invokeAndWait {
            // Dark theme
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
            val settingsDark = SettingsWindow()
            settingsDark.loadConfig()
            settingsDark.selectPage("SETTINGS_SEC_PLUGINS")
            captureWindow(settingsDark, 820, 560, File(artifactDir, "screenshot-settings-plugins-dark.png"))
            settingsDark.dispose()

            // Light theme
            ThemeManager.applyTheme("light", null, 100, saveConfig = false)
            val settingsLight = SettingsWindow()
            settingsLight.loadConfig()
            settingsLight.selectPage("SETTINGS_SEC_PLUGINS")
            captureWindow(settingsLight, 820, 560, File(artifactDir, "screenshot-settings-plugins-light.png"))
            settingsLight.dispose()

            // Restore dark
            ThemeManager.applyTheme("dark", null, 100, saveConfig = false)
        }
    }

    private fun captureWindow(comp: Component, width: Int, height: Int, file: File) {
        comp.setSize(width, height)
        comp.addNotify()
        comp.validate()
        val target = (comp as? java.awt.Window)?.let { (it as? RootPaneContainer)?.contentPane } ?: comp
        target.setSize(width, height)
        target.doLayout()
        target.validate()

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        target.paint(g)
        g.dispose()

        ImageIO.write(img, "png", file)
        assertTrue(file.exists() && file.length() > 2000, "Screenshot created: ${file.name}")
    }

    private fun createZip(evilZip: File, entries: Map<String, Any>) {
        ZipOutputStream(FileOutputStream(evilZip)).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                val bytes = when (data) {
                    is ByteArray -> data
                    is String -> data.toByteArray(StandardCharsets.UTF_8)
                    else -> data.toString().toByteArray(StandardCharsets.UTF_8)
                }
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    private fun validPluginJson(id: String, checksumSha256: String? = null): String {
        val checksumField = if (checksumSha256 != null) """, "checksumSha256": "$checksumSha256"""" else ""
        return """
        {
          "id": "$id",
          "name": "Test Plugin $id",
          "version": "1.0.0",
          "author": "Tester",
          "description": "Test plugin description",
          "entryPoint": "xdm.sample.plugin.TestPlugin"
          $checksumField
        }
        """.trimIndent()
    }

    @Test
    fun testMediaProviderRegistrationAndResolution() {
        val samplePackage = resolveSamplePlugin()
        val installResult = PluginManager.installPlugin(samplePackage)
        assertTrue(installResult.isValid, "Plugin installation must succeed: ${installResult.errors}")

        PluginManager.trustAndEnablePlugin("checksum-verifier")
        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "checksum-verifier" }
        assertNotNull(plugin, "Plugin must be installed")
        assertNotNull(plugin!!.context, "Plugin context must be initialized")

        val hostContext = plugin.context as PluginHostContext

        val mockProvider = object : MediaProvider {
            override fun getProviderId(): String = "test-custom-media-provider"
            override fun getDisplayName(): String = "Test Custom Media Provider"
            override fun supportsUrl(url: String): Boolean = url.contains("example-media.org")
            override fun resolveStreams(url: String, headers: Map<String, String>?): List<PluginMediaStream> {
                return listOf(
                    PluginMediaStream(
                        "stream-custom-1080",
                        "https://example-media.org/stream-1080.mp4",
                        "Custom Video Title",
                        "MP4",
                        "1080p",
                        "50 MB",
                        true,
                        "60fps",
                        "HTTP",
                        emptyMap()
                    )
                )
            }
        }

        hostContext.registerMediaProvider(mockProvider)

        val allProviders = PluginManager.getAllMediaProviders()
        assertTrue(allProviders.any { it.second.getProviderId() == "test-custom-media-provider" },
            "Registered MediaProvider should be discoverable in PluginManager")

        val foundPair = PluginManager.findMediaProvider("https://example-media.org/video/99")
        assertNotNull(foundPair, "Should find provider for supported URL")
        assertEquals("test-custom-media-provider", foundPair!!.second.getProviderId())

        val notFound = PluginManager.findMediaProvider("https://other-domain.com/video")
        assertNull(notFound, "Should return null for unsupported URL")

        val streams = foundPair.second.resolveStreams("https://example-media.org/video/99", null)
        assertEquals(1, streams.size)
        val s = streams[0]
        assertEquals("stream-custom-1080", s.streamId)
        assertEquals("1080p", s.resolution)
        assertEquals("MP4", s.format)
        assertTrue(s.hasAudio())
    }

}

class FaultyTestPlugin : XdmPlugin {
    override fun getId(): String = "faulty-plugin"
    override fun init(context: PluginContext?) {
        throw RuntimeException("Simulated plugin crash during init()")
    }
    override fun shutdown() {}
}

class FaultyShutdownPlugin : XdmPlugin {
    override fun getId(): String = "faulty-shutdown-plugin"
    override fun init(context: PluginContext?) {}
    override fun shutdown() {
        throw RuntimeException("Simulated plugin crash during shutdown()")
    }
}
