package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.plugin.api.PluginContext
import xdm.plugin.api.PluginException
import xdm.plugin.api.Registration
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.events.EventTopics
import xdm.plugin.api.models.DownloadInfo
import xdm.plugin.api.network.ProxyProvider
import xdm.plugin.api.network.ProxyResolution
import xdm.plugin.api.network.VpnStatus
import xdm.plugin.api.network.VpnStatusProvider
import xdm.plugin.api.processors.PostDownloadProcessor
import xdm.plugin.api.processors.ProcessingResult
import xdm.plugin.api.ui.DashboardWidgetContributor
import java.awt.Component
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JLabel

class PluginSdkV2FoundationTest : DownloadManagerTestBase() {

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

    private fun resolvePluginPackage(name: String): File {
        val candidates = listOf(
            File("sample-plugins/$name"),
            File("../sample-plugins/$name"),
            File(System.getProperty("user.dir"), "sample-plugins/$name"),
            File(System.getProperty("user.dir"), "../sample-plugins/$name")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Cannot find sample-plugins/$name. Checked: ${candidates.map { it.absolutePath }}")
    }

    private fun createZip(zipFile: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Existing v1 sample plugin backward-compatibility & coexistence
    // -------------------------------------------------------------------------
    @Test
    fun testV1SamplePluginCoexistence() {
        val v1Checksum = resolvePluginPackage("checksum-verifier.xdmplugin")
        val v1Ytdlp = resolvePluginPackage("ytdlp-downloader.xdmplugin")

        val res1 = PluginManager.installPlugin(v1Checksum)
        assertTrue(res1.isValid, "v1 ChecksumVerifier should pass validation: ${res1.errors}")

        val res2 = PluginManager.installPlugin(v1Ytdlp)
        assertTrue(res2.isValid, "v1 YtDlp should pass validation: ${res2.errors}")

        assertTrue(PluginManager.trustAndEnablePlugin("checksum-verifier"))
        assertTrue(PluginManager.trustAndEnablePlugin("ytdlp-downloader"))

        val p1 = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "checksum-verifier" }
        assertNotNull(p1)
        assertEquals(PluginStatus.ACTIVE, p1!!.status)
        assertEquals(1, p1.manifest.manifestVersion, "v1 plugin manifests default to version 1")

        val p2 = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ytdlp-downloader" }
        assertNotNull(p2)
        assertEquals(PluginStatus.ACTIVE, p2!!.status)

        // Verify v1 providers are discoverable in PluginManager
        val downloadProviders = PluginManager.getAllDownloadProviders()
        assertTrue(downloadProviders.any { it.first.manifest.id == "ytdlp-downloader" })

        // Verify notification does not throw and executes v1 hooks
        val testFile = File(dir, "v1_download.bin").apply { writeBytes("v1 payload".toByteArray()) }
        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted(testFile.absolutePath, 5001L)
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: v2 Sample Plugin Lifecycle & Registration Handles
    // -------------------------------------------------------------------------
    @Test
    fun testV2SamplePluginLifecycle() {
        val v2Package = resolvePluginPackage("v2-sample-plugin.xdmplugin")
        val res = PluginManager.installPlugin(v2Package)
        assertTrue(res.isValid, "v2 sample plugin package should be valid: ${res.errors}")
        assertEquals(2, res.manifest?.manifestVersion, "Manifest version must be 2")

        assertTrue(PluginManager.trustAndEnablePlugin("v2-sample-processor"))
        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "v2-sample-processor" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.ACTIVE, plugin!!.status)

        val context = plugin.context as? PluginHostContext
        assertNotNull(context)
        assertEquals(1, context!!.postDownloadProcessors.size, "Processor SPI must be registered")
        assertEquals(1, context.dashboardWidgets.size, "Dashboard widget SPI must be registered")

        // Verify dashboard widget contributor
        val widgets = PluginManager.getDashboardWidgets()
        assertEquals(1, widgets.size)
        assertEquals("v2-sample-widget", widgets[0].second.widgetId)
        val comp = widgets[0].second.createWidget()
        assertNotNull(comp)

        // Execute download notification and verify post-download processor
        val testFile = File(dir, "v2_test.txt").apply { writeText("v2 processor test content") }
        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted(testFile.absolutePath, 6001L)
        }

        // Deactivate plugin and verify clean handle disposal
        PluginManager.disablePlugin("v2-sample-processor")
        assertEquals(PluginStatus.DISABLED, plugin.status)
        assertNull(plugin.context, "Plugin context should be cleared upon deactivation")
    }

    // -------------------------------------------------------------------------
    // Scenario 3: ClassLoader Isolation Boundary (GAP-01)
    // -------------------------------------------------------------------------
    @Test
    fun testClassLoaderIsolationBoundary() {
        val cl = PluginFilteringClassLoader(emptyArray(), javaClass.classLoader, "test-isolation-plugin")

        // Public API class should be allowed
        assertDoesNotThrow {
            val apiClass = cl.loadClass("xdm.plugin.api.XdmPlugin")
            assertNotNull(apiClass)
        }
        assertDoesNotThrow {
            val capClass = cl.loadClass("xdm.plugin.api.models.PluginCapability")
            assertNotNull(capClass)
        }

        // Host internal classes must throw ClassNotFoundException
        val forbidden = listOf(
            "xdm.app.AppContext",
            "xdm.app.plugins.PluginManager",
            "xdm.core.downloaders.HttpDownloadTaskInfo",
            "xdm.manager.DownloadManager"
        )

        for (className in forbidden) {
            val ex = assertThrows(ClassNotFoundException::class.java, {
                cl.loadClass(className)
            }, "Expected ClassNotFoundException when loading internal host class: $className")
            assertTrue(ex.message!!.contains("prohibited from accessing internal host class"),
                "Exception message must specify security/module boundary: ${ex.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Duplicate Plugin ID Rejection
    // -------------------------------------------------------------------------
    @Test
    fun testDuplicatePluginIdRejection() {
        // Create two separate directories with identical plugin ID
        val dir1 = File(testPluginsDir, "plugin-alpha").apply { mkdirs() }
        val dir2 = File(testPluginsDir, "plugin-beta").apply { mkdirs() }

        val json = """
            {
                "manifestVersion": 2,
                "id": "duplicate-identity-plugin",
                "name": "Duplicate Plugin",
                "version": "1.0.0",
                "author": "Tester",
                "entryPoint": "xdm.sample.Dummy"
            }
        """.trimIndent()

        File(dir1, "plugin.json").writeText(json)
        File(dir2, "plugin.json").writeText(json)

        PluginManager.discoverPlugins()

        val matching = PluginManager.installedPlugins.filter { it.manifest.id == "duplicate-identity-plugin" }
        assertEquals(2, matching.size, "Both plugins should be tracked")

        val faulted = matching.firstOrNull { it.status == PluginStatus.FAULTED }
        assertNotNull(faulted, "Second plugin with duplicate ID must be marked FAULTED")
        assertTrue(faulted!!.faultReason!!.contains("Duplicate plugin ID"), "Reason must mention duplicate ID: ${faulted.faultReason}")
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Mismatched API Version Diagnostics
    // -------------------------------------------------------------------------
    @Test
    fun testMismatchedApiVersionDiagnostics() {
        val manifestFuture = PluginManifest(
            manifestVersion = 2,
            id = "future-api-plugin",
            name = "Future Plugin",
            version = "1.0.0",
            author = "Tester",
            minApiVersion = "99.0.0",
            entryPoint = "xdm.sample.Dummy"
        )

        val validation = PluginManifestValidator.validateManifest(manifestFuture)
        assertFalse(validation.isValid, "Future API version requirement must fail validation")
        assertTrue(validation.errors.any { it.contains("Plugin requires API version '>=99.0.0'") },
            "Error must specify required API vs host API: ${validation.errors}")

        // Range incompatibility check
        val manifestRange = PluginManifest(
            manifestVersion = 2,
            id = "range-incompatible-plugin",
            name = "Range Incompatible",
            version = "1.0.0",
            author = "Tester",
            apiCompatibility = "[2.0.0, 3.0.0)",
            entryPoint = "xdm.sample.Dummy"
        )
        val rangeVal = PluginManifestValidator.validateManifest(manifestRange)
        assertFalse(rangeVal.isValid, "Range constraint outside host API must fail validation")
        assertTrue(rangeVal.errors.any { it.contains("is below required range") || it.contains("Plugin API compatibility") },
            "Error must detail range evaluation: ${rangeVal.errors}")
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Missing Optional Capabilities Handling
    // -------------------------------------------------------------------------
    @Test
    fun testMissingOptionalCapabilities() {
        val manifestWithUnknownCap = PluginManifest(
            manifestVersion = 2,
            id = "optional-caps-plugin",
            name = "Optional Caps Plugin",
            version = "1.0.0",
            author = "Tester",
            capabilities = listOf("DOWNLOAD_PROVIDER", "FUTURE_EXPERIMENTAL_AI_CLASSIFIER"),
            entryPoint = "xdm.sample.Dummy"
        )

        val validation = PluginManifestValidator.validateManifest(manifestWithUnknownCap)
        assertTrue(validation.isValid, "Unknown capabilities must not invalidate the plugin (treated as optional/warning)")
        assertTrue(validation.warnings.any { it.contains("Unknown capability declared: 'FUTURE_EXPERIMENTAL_AI_CLASSIFIER'") },
            "Unknown capability must produce diagnostic warning: ${validation.warnings}")
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Malformed Manifest Rejection
    // -------------------------------------------------------------------------
    @Test
    fun testMalformedManifestRejection() {
        // Test invalid ID with spaces and illegal characters
        val invalidIdManifest = PluginManifest(
            manifestVersion = 2,
            id = "bad id with spaces!!",
            name = "Invalid ID Plugin",
            version = "1.0.0",
            author = "Tester",
            entryPoint = "xdm.sample.Dummy"
        )
        val res1 = PluginManifestValidator.validateManifest(invalidIdManifest)
        assertFalse(res1.isValid)
        assertTrue(res1.errors.any { it.contains("Must be 3-64 characters") })

        // Test invalid entryPoint class
        val invalidEntryManifest = PluginManifest(
            manifestVersion = 2,
            id = "invalid-entry-plugin",
            name = "Invalid Entry Plugin",
            version = "1.0.0",
            author = "Tester",
            entryPoint = "123NotAValidClassName!"
        )
        val res2 = PluginManifestValidator.validateManifest(invalidEntryManifest)
        assertFalse(res2.isValid)
        assertTrue(res2.errors.any { it.contains("not a valid fully-qualified Java class name") })
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Repeated Enable/Disable Cycles
    // -------------------------------------------------------------------------
    @Test
    fun testRepeatedEnableDisable() {
        val v2Package = resolvePluginPackage("v2-sample-plugin.xdmplugin")
        PluginManager.installPlugin(v2Package)
        PluginManager.trustAndEnablePlugin("v2-sample-processor")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "v2-sample-processor" }

        for (cycle in 1..5) {
            PluginManager.disablePlugin("v2-sample-processor")
            assertEquals(PluginStatus.DISABLED, plugin.status, "Cycle $cycle: must be DISABLED")
            assertNull(plugin.instance, "Cycle $cycle: instance must be null")
            assertNull(plugin.context, "Cycle $cycle: context must be null")
            assertNull(plugin.classLoader, "Cycle $cycle: classLoader must be null")

            val reenabled = PluginManager.enablePlugin("v2-sample-processor")
            assertTrue(reenabled, "Cycle $cycle: re-enabling must succeed")
            assertEquals(PluginStatus.ACTIVE, plugin.status, "Cycle $cycle: must be ACTIVE")
            assertNotNull(plugin.instance, "Cycle $cycle: instance must be re-created")
            assertNotNull(plugin.context, "Cycle $cycle: context must be re-created")
        }

        // Final shutdown
        PluginManager.disablePlugin("v2-sample-processor")
        assertEquals(PluginStatus.DISABLED, plugin.status)
    }

    // -------------------------------------------------------------------------
    // Scenario 9: Clean Startup Without Any Plugins
    // -------------------------------------------------------------------------
    @Test
    fun testStartupWithoutPlugins() {
        // testPluginsDir is empty
        PluginManager.init(safeMode = false)
        assertTrue(PluginManager.installedPlugins.isEmpty(), "Installed plugins list should be empty")

        // Method calls return safe empty results without NPE
        assertNull(PluginManager.findDownloadProvider("https://example.com/test.zip"))
        assertNull(PluginManager.findMediaProvider("https://youtube.com/watch?v=123"))
        assertNull(PluginManager.findUrlRecognizer("https://example.com/file.mp4"))
        assertNull(PluginManager.resolveProxy(URI.create("https://example.com/")))
        assertTrue(PluginManager.getVpnStatusProviders().isEmpty())
        assertTrue(PluginManager.getDashboardWidgets().isEmpty())
        assertTrue(PluginManager.getContextMenuActions(emptyList()).isEmpty())
        assertTrue(PluginManager.getToolbarActions().isEmpty())
        assertTrue(PluginManager.getSettingsPanels().isEmpty())

        // Notification on empty plugin set does nothing and doesn't crash
        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted("C:\\nonexistent\\file.txt", 9999L)
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 10: Dynamic ProxyProvider and VpnStatusProvider SPI
    // -------------------------------------------------------------------------
    @Test
    fun testProxyAndVpnProviderIntegration() {
        val dummyPlugin = LoadedPlugin(
            manifest = PluginManifest(id = "network-test-plugin", name = "Network Plugin", version = "1.0.0", entryPoint = "Dummy"),
            pluginDir = File(testPluginsDir, "network-test-plugin").apply { mkdirs() },
            status = PluginStatus.ACTIVE
        )
        val context = PluginHostContext(dummyPlugin, dummyPlugin.manifest.let {
            xdm.plugin.api.models.PluginMetadata(it.id, it.name, it.version, it.author, it.description, it.minAppVersion, it.minApiVersion, it.entryPoint, it.capabilities.toSet(), it.dependencies, it.license, it.homepage, "", false)
        })
        dummyPlugin.context = context
        PluginManager.installedPlugins.add(dummyPlugin)

        // Register custom ProxyProvider
        val proxyReg = context.registerProxyProvider(object : ProxyProvider {
            override fun getProviderId(): String = "test-proxy"
            override fun getPriority(): Int = 10
            override fun resolveProxy(targetUri: URI?): ProxyResolution? {
                if (targetUri?.host == "proxied.example.com") {
                    return ProxyResolution.http("127.0.0.1", 8080)
                }
                return null
            }
        })

        // Register custom VpnStatusProvider
        val vpnStatus = VpnStatus.connected("wg0", "US-East", "10.0.0.2")
        val vpnReg = context.registerVpnStatusProvider(object : VpnStatusProvider {
            override fun getProviderId(): String = "test-vpn"
            override fun getDisplayName(): String = "WireGuard-Test"
            override fun getCurrentStatus(): VpnStatus = vpnStatus
            override fun addStateListener(listener: xdm.plugin.api.network.VpnStateListener?) {}
            override fun removeStateListener(listener: xdm.plugin.api.network.VpnStateListener?) {}
        })

        // Verify Proxy resolution
        val resolved = PluginManager.resolveProxy(URI.create("http://proxied.example.com/data"))
        assertNotNull(resolved)
        assertEquals(ProxyResolution.Type.HTTP, resolved!!.type)
        assertEquals("127.0.0.1", resolved.host)
        assertEquals(8080, resolved.port)

        val direct = PluginManager.resolveProxy(URI.create("http://direct.example.com/data"))
        assertNull(direct, "Non-matching URI should fall through to null")

        // Verify VPN Provider
        val vpnProviders = PluginManager.getVpnStatusProviders()
        assertEquals(1, vpnProviders.size)
        assertEquals("WireGuard-Test", vpnProviders[0].second.displayName)
        assertEquals("wg0", vpnProviders[0].second.currentStatus.interfaceName)
        assertTrue(vpnProviders[0].second.currentStatus.isConnected)

        // Test disposable handles unregistering
        proxyReg.unregister()
        vpnReg.unregister()

        assertNull(PluginManager.resolveProxy(URI.create("http://proxied.example.com/data")), "Proxy should no longer be resolved after unregister")
        assertTrue(PluginManager.getVpnStatusProviders().isEmpty(), "VPN providers should be empty after unregister")
    }

    // -------------------------------------------------------------------------
    // Scenario 11: PostDownloadProcessor Pipeline Execution & File Modification
    // -------------------------------------------------------------------------
    @Test
    fun testPostDownloadProcessorPipeline() {
        val dummyPlugin = LoadedPlugin(
            manifest = PluginManifest(id = "pipeline-test-plugin", name = "Pipeline Plugin", version = "1.0.0", entryPoint = "Dummy"),
            pluginDir = File(testPluginsDir, "pipeline-test-plugin").apply { mkdirs() },
            status = PluginStatus.ACTIVE
        )
        val context = PluginHostContext(dummyPlugin, dummyPlugin.manifest.let {
            xdm.plugin.api.models.PluginMetadata(it.id, it.name, it.version, it.author, it.description, it.minAppVersion, it.minApiVersion, it.entryPoint, it.capabilities.toSet(), it.dependencies, it.license, it.homepage, "", false)
        })
        dummyPlugin.context = context
        PluginManager.installedPlugins.add(dummyPlugin)

        val processedFlag = AtomicBoolean(false)
        val originalFile = File(dir, "original_file.txt").apply { writeText("original text") }
        val modifiedFile = File(dir, "modified_file.txt").apply { writeText("modified text") }

        context.registerPostDownloadProcessor(object : PostDownloadProcessor {
            override fun getName(): String = "test-modifier"
            override fun getOrder(): Int = 10
            override fun canProcess(download: DownloadInfo?, downloadedFile: File?): Boolean = true
            override fun process(download: DownloadInfo?, downloadedFile: File?): ProcessingResult {
                processedFlag.set(true)
                return ProcessingResult.modified(modifiedFile, "Renamed and modified")
            }
        })

        PluginManager.notifyDownloadCompleted(originalFile.absolutePath, 7777L)
        assertTrue(processedFlag.get(), "Processor should have been executed in pipeline")
    }
}
