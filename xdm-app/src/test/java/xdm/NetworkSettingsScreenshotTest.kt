package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.PluginManager
import xdm.app.plugins.PluginManifest
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.SettingsWindow
import xdm.plugin.api.PluginContext
import xdm.plugin.api.Registration
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.network.ProxyProvider
import xdm.plugin.api.network.ProxyResolution
import xdm.plugin.api.network.ProxyResolution.FallbackPolicy
import xdm.plugin.api.network.ProxyResolution.NetworkEngine
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.util.EnumSet
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class NetworkSettingsScreenshotTest : DownloadManagerTestBase() {

    private val artifactDir = File(System.getProperty("user.home"), ".gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")
    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initNetworkTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.init(safeMode = false)
        AppContext.config.disabledProxyProviders = emptyList()
    }

    @AfterEach
    fun teardownNetworkTest() {
        PluginManager.shutdown()
        testPluginsDir.deleteRecursively()
    }

    private fun captureComponent(comp: Component, width: Int, height: Int, file: File) {
        comp.setSize(width, height)
        comp.addNotify()
        comp.validate()
        val target = (comp as? java.awt.Window)?.let { (it as? javax.swing.RootPaneContainer)?.contentPane } ?: comp
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

    @Test
    fun captureNetworkSettingsWithPluginProfiles() {
        artifactDir.mkdirs()

        // Create and activate sample network plugin
        val pluginDir = File(testPluginsDir, "sample-network-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "sample-network-plugin",
            name = "Corporate Gateway SOCKS5",
            version = "1.2.0",
            author = "Security Ops",
            description = "Provides authenticated SOCKS5 routing for corporate internal repositories",
            entryPoint = SampleUiProxyPlugin::class.java.name,
            capabilities = listOf("NETWORK_PROXY", "VPN_STATUS")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "sample-network-plugin" }
        if (loaded != null) {
            PluginManager.activatePlugin(loaded)
        }

        AppContext.config.vpnPrerequisiteEnabled = true

        // 1. Dark Theme
        SwingUtilities.invokeAndWait {
            ThemeManager.applyTheme("dark", Color(0x3B, 0x82, 0xF6), 100)
            val window = SettingsWindow().apply {
                loadConfig()
                selectPage("SETTINGS_NETWORK")
            }
            scrollToPluginSection(window)
            captureComponent(window, 800, 780, File(artifactDir, "screenshot-settings-network-dark.png"))
            window.dispose()
        }

        // 2. Light Theme
        SwingUtilities.invokeAndWait {
            ThemeManager.applyTheme("light", Color(0x3B, 0x82, 0xF6), 100)
            val window = SettingsWindow().apply {
                loadConfig()
                selectPage("SETTINGS_NETWORK")
            }
            scrollToPluginSection(window)
            captureComponent(window, 800, 780, File(artifactDir, "screenshot-settings-network-light.png"))
            window.dispose()
        }
    }

    private fun scrollToPluginSection(comp: Component) {
        if (comp is javax.swing.JScrollPane) {
            comp.viewport.viewPosition = java.awt.Point(0, 320)
        } else if (comp is java.awt.Container) {
            for (c in comp.components) {
                scrollToPluginSection(c)
            }
        }
    }

    class SampleUiProxyPlugin : XdmPlugin, ProxyProvider, xdm.plugin.api.network.VpnStatusProvider {
        private var reg: Registration? = null
        private var vpnReg: Registration? = null
        override fun getId(): String = "sample-network-plugin"
        override fun init(context: PluginContext) {
            reg = context.registerProxyProvider(this)
            vpnReg = context.registerVpnStatusProvider(this)
        }
        override fun shutdown() {
            reg?.unregister()
            vpnReg?.unregister()
        }
        override fun getProviderId(): String = "corp-socks5-gateway"
        override fun getDisplayName(): String = "Corporate SOCKS5 Gateway"
        override fun getPriority(): Int = 50
        override fun getApplicableEngines(): Set<NetworkEngine> = EnumSet.of(NetworkEngine.HTTP, NetworkEngine.MEDIA, NetworkEngine.TORRENT, NetworkEngine.PLUGIN_TASK)
        override fun resolveProxy(targetUri: URI, engine: NetworkEngine): ProxyResolution? {
            return ProxyResolution.socks5("127.0.0.1", 1080, "testuser", "secretpass", FallbackPolicy.FAIL_CLOSED)
        }
        override fun getCurrentStatus(): xdm.plugin.api.network.VpnStatus =
            xdm.plugin.api.network.VpnStatus.connected("wg0", "US East (Virginia)", "10.14.0.2", "us-east-prod", true, "Active WireGuard Tunnel")
        override fun getCapabilities(): xdm.plugin.api.network.VpnClientCapabilities =
            xdm.plugin.api.network.VpnClientCapabilities(true, true, true, true, "v1.0.2026", "wireguard.exe")
        override fun getAvailableProfiles(): List<xdm.plugin.api.network.VpnProfile> = listOf(
            xdm.plugin.api.network.VpnProfile("us-east-prod", "US East (Production)", "us-east.corp.internal", true),
            xdm.plugin.api.network.VpnProfile("eu-central-backup", "EU Central (Backup)", "eu-central.corp.internal", false)
        )
        override fun addStateListener(listener: xdm.plugin.api.network.VpnStateListener?) {}
        override fun removeStateListener(listener: xdm.plugin.api.network.VpnStateListener?) {}
    }
}
