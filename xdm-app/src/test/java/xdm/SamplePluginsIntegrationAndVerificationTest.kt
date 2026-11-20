package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.LoadedPlugin
import xdm.app.plugins.PluginEventBus
import xdm.app.plugins.PluginManager
import xdm.app.plugins.PluginManifestValidator
import xdm.app.plugins.PluginStatus
import xdm.plugin.api.events.EventTopics
import xdm.plugin.api.events.TaskLifecycleEvent
import xdm.plugin.api.network.ProxyResolution
import java.io.File
import java.net.URI

/**
 * End-to-end integration and verification test suite for standalone sample plugins:
 * 1. ytdlp-downloader.xdmplugin (Download Provider & Tasks)
 * 2. proxy-provider.xdmplugin (Scoped Proxy Profile Provider)
 * 3. vpn-status.xdmplugin (Non-privileged VPN Telemetry Provider)
 * 4. ui-notification.xdmplugin (UI Enhancements, Theme & Notification Provider)
 *
 * Verifies clean installation, manifest integrity, SPI execution, and clean disposal.
 */
class SamplePluginsIntegrationAndVerificationTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File
    private val samplesDir: File = run {
        val candidates = listOf(
            File("sample-plugins"),
            File("../sample-plugins"),
            File("../../sample-plugins")
        )
        candidates.firstOrNull { it.exists() && it.isDirectory }?.canonicalFile
            ?: File("sample-plugins")
    }

    @BeforeEach
    fun setupSampleTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginEventBus.clearAll()
        AppContext.config.trustedPlugins = emptyList()
        AppContext.config.enabledPlugins = emptyList()
        AppContext.config.vpnRequiredProviderId = ""
        AppContext.config.vpnPrerequisiteEnabled = false
        PluginManager.init(safeMode = false)
    }

    @AfterEach
    fun cleanupSampleTest() {
        PluginManager.shutdown()
        PluginEventBus.clearAll()
        testPluginsDir.deleteRecursively()
        AppContext.config.trustedPlugins = emptyList()
        AppContext.config.enabledPlugins = emptyList()
        AppContext.config.vpnRequiredProviderId = ""
        AppContext.config.vpnPrerequisiteEnabled = false
    }

    @Test
    fun testProxyProviderSampleLifecycleAndRouting() {
        val archive = File(samplesDir, "proxy-provider.xdmplugin")
        assertTrue(archive.exists(), "proxy-provider.xdmplugin must be packaged")

        val valResult = PluginManifestValidator.validateArchive(archive)
        assertTrue(valResult.isValid, "Archive validation must succeed: ${valResult.errors}")

        val installResult = PluginManager.installPlugin(archive)
        assertTrue(installResult.isValid, "Installation must succeed: ${installResult.errors}")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "sample-proxy-provider" }
        assertNotNull(plugin, "Plugin must appear in installed roster")
        assertEquals(PluginStatus.UNTRUSTED, plugin!!.status)
        assertEquals("Sample Proxy Profile Provider", plugin.manifest.name)
        assertTrue(plugin.manifest.capabilities.contains("NETWORK_PROXY"))
        assertTrue(plugin.manifest.capabilities.contains("SETTINGS_PANEL"))

        val trusted = PluginManager.trustAndEnablePlugin("sample-proxy-provider")
        assertTrue(trusted, "Trust and activation must succeed")
        assertEquals(PluginStatus.ACTIVE, plugin.status)

        // Verify registered proxy provider
        val activeProviders = PluginManager.getActiveProxyProviders()
        assertTrue(activeProviders.any { it.second.providerId == "sample-fixed-proxy" })

        // Test scoped proxy resolution
        val targetUri = URI("https://test.proxy-fixture.local/stream.mp4")
        val resolution = PluginManager.resolveProxy(targetUri, ProxyResolution.NetworkEngine.HTTP)
        assertNotNull(resolution, "Proxy fixture host must resolve via sample proxy provider")
        assertEquals(ProxyResolution.Type.SOCKS5, resolution!!.type)
        assertEquals("127.0.0.1", resolution.host)
        assertEquals(1080, resolution.port)
        assertEquals(ProxyResolution.FallbackPolicy.FAIL_CLOSED, resolution.fallbackPolicy)
        assertTrue(resolution.username != null && resolution.password != null)

        // Test non-matching domain returns default/null
        val unrelatedUri = URI("https://example.com/file.zip")
        val unrelatedResolution = PluginManager.resolveProxy(unrelatedUri, ProxyResolution.NetworkEngine.HTTP)
        assertNull(unrelatedResolution, "Unrelated domains must not be intercepted")

        // Clean uninstallation
        val uninstalled = PluginManager.uninstallPlugin("sample-proxy-provider")
        assertTrue(uninstalled)
        assertNull(PluginManager.installedPlugins.firstOrNull { it.manifest.id == "sample-proxy-provider" })
    }

    @Test
    fun testVpnStatusSampleLifecycleAndTelemetry() {
        val archive = File(samplesDir, "vpn-status.xdmplugin")
        assertTrue(archive.exists(), "vpn-status.xdmplugin must be packaged")

        val valResult = PluginManifestValidator.validateArchive(archive)
        assertTrue(valResult.isValid, "Archive validation must succeed: ${valResult.errors}")

        val installResult = PluginManager.installPlugin(archive)
        assertTrue(installResult.isValid, "Installation must succeed: ${installResult.errors}")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "sample-vpn-status" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.UNTRUSTED, plugin!!.status)
        assertEquals("Sample VPN Status & Telemetry Provider", plugin.manifest.name)
        assertTrue(plugin.manifest.capabilities.contains("VPN_STATUS"))

        val trusted = PluginManager.trustAndEnablePlugin("sample-vpn-status")
        assertTrue(trusted)
        assertEquals(PluginStatus.ACTIVE, plugin.status)

        val vpnProviders = PluginManager.getVpnStatusProviders()
        assertTrue(vpnProviders.any { it.second.providerId == "sample-mock-vpn" })

        val (_, vpnProv) = vpnProviders.first { it.second.providerId == "sample-mock-vpn" }
        val caps = vpnProv.capabilities
        assertTrue(caps.isClientInstalled)
        assertTrue(caps.isSupportsProfiles)
        assertEquals("1.0.2026", caps.clientVersion)

        val profiles = vpnProv.availableProfiles
        assertEquals(3, profiles.size)
        assertTrue(profiles.any { it.displayName.contains("US East") })

        val currentStatus = vpnProv.currentStatus
        assertTrue(currentStatus.isConnected)
        assertEquals("wg0", currentStatus.interfaceName)

        // Exercise disconnect
        vpnProv.requestDisconnect()
        assertFalse(vpnProv.currentStatus.isConnected)

        // Exercise reconnect
        vpnProv.requestConnect("us-east-wireguard")
        assertTrue(vpnProv.currentStatus.isConnected)

        val uninstalled = PluginManager.uninstallPlugin("sample-vpn-status")
        assertTrue(uninstalled)
    }

    @Test
    fun testUiNotificationSampleLifecycleAndContributions() {
        val archive = File(samplesDir, "ui-notification.xdmplugin")
        assertTrue(archive.exists(), "ui-notification.xdmplugin must be packaged")

        val valResult = PluginManifestValidator.validateArchive(archive)
        assertTrue(valResult.isValid, "Archive validation must succeed: ${valResult.errors}")

        val installResult = PluginManager.installPlugin(archive)
        assertTrue(installResult.isValid, "Installation must succeed: ${installResult.errors}")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "sample-ui-notification" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.UNTRUSTED, plugin!!.status)
        assertEquals("Sample UI & Notification Enhancement", plugin.manifest.name)

        val trusted = PluginManager.trustAndEnablePlugin("sample-ui-notification")
        assertTrue(trusted)
        assertEquals(PluginStatus.ACTIVE, plugin.status)

        // Verify registered UI contributors
        val context = plugin.context
        assertNotNull(context)
        assertEquals(1, context!!.contextMenuContributors.size)
        assertEquals(1, context.toolbarContributors.size)
        assertEquals(1, context.settingsPanels.size)
        assertEquals(1, context.detailsSectionContributors.size)
        assertEquals(1, context.statusIndicatorContributors.size)

        val dl = xdm.plugin.api.models.DownloadInfo(
            12345L, "https://example.com/test.zip", "test.zip", "C:\\Downloads\\test.zip",
            1024L, 1024L, 100, 500f, "Complete", "General", System.currentTimeMillis()
        )
        // Verify event publishing does not crash listener
        assertDoesNotThrow {
            PluginEventBus.publish(
                EventTopics.DOWNLOAD_COMPLETED,
                TaskLifecycleEvent(TaskLifecycleEvent.Type.COMPLETED, dl)
            )
        }

        // Clean uninstallation
        val uninstalled = PluginManager.uninstallPlugin("sample-ui-notification")
        assertTrue(uninstalled)
        assertNull(PluginManager.installedPlugins.firstOrNull { it.manifest.id == "sample-ui-notification" })
    }

    @Test
    fun testYtDlpDownloaderSamplePackageAndValidation() {
        val archive = File(samplesDir, "ytdlp-downloader.xdmplugin")
        assertTrue(archive.exists(), "ytdlp-downloader.xdmplugin must be packaged")

        val valResult = PluginManifestValidator.validateArchive(archive)
        assertTrue(valResult.isValid, "Archive validation must succeed: ${valResult.errors}")

        val installResult = PluginManager.installPlugin(archive)
        assertTrue(installResult.isValid, "Installation must succeed: ${installResult.errors}")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ytdlp-downloader" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.UNTRUSTED, plugin!!.status)
        assertEquals("yt-dlp Media Downloader", plugin.manifest.name)
        assertTrue(plugin.manifest.capabilities.contains("DOWNLOAD_PROVIDER"))
        assertTrue(plugin.manifest.capabilities.contains("URL_RECOGNIZER") || plugin.manifest.capabilities.contains("URL_RECOGNITION"))
        assertTrue(plugin.manifest.capabilities.contains("SETTINGS_PANEL"))

        val trusted = PluginManager.trustAndEnablePlugin("ytdlp-downloader")
        assertTrue(trusted)
        assertEquals(PluginStatus.ACTIVE, plugin.status)

        val downloadProviders = PluginManager.getAllDownloadProviders()
        assertTrue(downloadProviders.any { it.second.name.contains("yt-dlp") })

        val recognizers = PluginManager.getAllUrlRecognizers()
        assertTrue(recognizers.isNotEmpty())

        val uninstalled = PluginManager.uninstallPlugin("ytdlp-downloader")
        assertTrue(uninstalled)
    }
}
