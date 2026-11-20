package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.DesktopPlatformHooks
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.core.network.ProxyConfig
import xdm.plugin.api.PluginContext
import xdm.plugin.api.Registration
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.models.PluginDownloadRequest
import xdm.plugin.api.network.NetworkProfile
import xdm.plugin.api.network.ProxyProvider
import xdm.plugin.api.network.ProxyResolution
import xdm.plugin.api.network.ProxyResolution.FallbackPolicy
import xdm.plugin.api.network.ProxyResolution.NetworkEngine
import xdm.torrent.LibtorrentEngine
import xdm.torrent.TorrentProxyType
import xdm.torrent.TorrentSessionConfig
import java.io.File
import java.net.Proxy
import java.net.URI
import java.util.EnumSet

class PluginNetworkProxyIntegrationTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initProxyTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.resetRestartFlag()
        PluginManager.init(safeMode = false)
        AppContext.config.disabledProxyProviders = emptyList()
    }

    @AfterEach
    fun cleanupProxyTest() {
        PluginManager.shutdown()
        testPluginsDir.deleteRecursively()
        PluginManager.resetRestartFlag()
        AppContext.config.disabledProxyProviders = emptyList()
    }

    @Test
    fun testProxyResolutionModelsAndCredentialRedaction() {
        val httpRes = ProxyResolution.http("10.0.0.1", 8080, "myuser", "supersecretpassword", FallbackPolicy.FAIL_CLOSED)
        assertEquals(ProxyResolution.Type.HTTP, httpRes.type)
        assertEquals("10.0.0.1", httpRes.host)
        assertEquals(8080, httpRes.port)
        assertEquals("myuser", httpRes.username)
        assertEquals("supersecretpassword", httpRes.password)
        assertEquals(FallbackPolicy.FAIL_CLOSED, httpRes.fallbackPolicy)
        assertFalse(httpRes.isBlocked)

        // Verify password redaction in toString() and toRedactedString()
        val str = httpRes.toString()
        assertFalse(str.contains("supersecretpassword"), "Password must never appear in toString()")
        assertTrue(str.contains("10.0.0.1:8080"))
        assertTrue(str.contains("myuser:***") || str.contains("authenticated"))

        val redacted = httpRes.toRedactedString()
        assertFalse(redacted.contains("supersecretpassword"), "Password must never appear in redacted string")

        val socks5Res = ProxyResolution.socks5("127.0.0.1", 1080, "socksuser", "sockspass", FallbackPolicy.FAIL_CLOSED)
        assertEquals(ProxyResolution.Type.SOCKS5, socks5Res.type)
        val javaProxy = socks5Res.toJavaProxy()
        assertEquals(Proxy.Type.SOCKS, javaProxy.type())

        val blockedRes = ProxyResolution.blocked("Port blocked by security policy")
        assertTrue(blockedRes.isBlocked)
        assertEquals(ProxyResolution.Type.BLOCKED, blockedRes.type)
        assertEquals("Port blocked by security policy", blockedRes.blockedReason)
        assertEquals(Proxy.NO_PROXY, blockedRes.toJavaProxy())

        // Verify NetworkProfile
        val profile = NetworkProfile(
            "corp-vpn",
            "Corporate Proxy",
            "Routing for internal domains",
            ProxyResolution.Type.HTTP,
            "proxy.corp.internal",
            8080,
            "employee",
            "secrettoken123",
            FallbackPolicy.FAIL_CLOSED,
            EnumSet.of(NetworkEngine.HTTP, NetworkEngine.MEDIA),
            setOf("*.corp.internal", "git.internal"),
            true
        )
        assertTrue(profile.matchesUri(URI.create("https://app.corp.internal/data")))
        assertTrue(profile.matchesUri(URI.create("http://git.internal/repo")))
        assertFalse(profile.matchesUri(URI.create("https://public-web.org/file.zip")))
        assertFalse(profile.redactedSummary.contains("secrettoken123"), "Password must never appear in profile summary")
    }

    @Test
    fun testEngineCapabilityMatchingAndFailClosedVsFailOpen() {
        val pluginDir = File(testPluginsDir, "proxy-test-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "proxy-test-plugin",
            name = "Proxy Test Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin with capability matching",
            entryPoint = ScopedProxyPlugin::class.java.name,
            capabilities = listOf("NETWORK_PROXY")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "proxy-test-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        // 1. Resolve HTTP for supported domain: should resolve to HTTP proxy
        val httpUri = URI.create("http://restricted.service.internal/download.zip")
        val httpRes = PluginManager.resolveProxy(httpUri, NetworkEngine.HTTP)
        assertNotNull(httpRes)
        assertFalse(httpRes!!.isBlocked)
        assertEquals(ProxyResolution.Type.HTTP, httpRes.type)
        assertEquals("proxy.internal", httpRes.host)

        // 2. Resolve TORRENT for supported domain with FAIL_CLOSED policy:
        // ScopedProxyPlugin only declares HTTP and MEDIA engines.
        // BitTorrent engine is unsupported with FAIL_CLOSED -> MUST return blocked!
        val torrentRes = PluginManager.resolveProxy(httpUri, NetworkEngine.TORRENT)
        assertNotNull(torrentRes, "Unsupported engine under FAIL_CLOSED must return a blocked resolution")
        assertTrue(torrentRes!!.isBlocked, "Must be blocked to prevent leakage")
        assertTrue(torrentRes.blockedReason!!.contains("FAIL_CLOSED"), "Reason must cite FAIL_CLOSED policy")

        // 3. Resolve for fail-open domain:
        val failOpenUri = URI.create("http://failopen.service.internal/item")
        val failOpenTorrentRes = PluginManager.resolveProxy(failOpenUri, NetworkEngine.TORRENT)
        // With FAIL_OPEN policy and unsupported engine, it must fall back (return null) rather than blocking
        assertNull(failOpenTorrentRes, "FAIL_OPEN should fall back to next provider or direct")
    }

    @Test
    fun testUserApprovalAndConfigurationPersistence() {
        val pluginDir = File(testPluginsDir, "user-approval-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "user-approval-plugin",
            name = "User Approval Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for user approval toggles",
            entryPoint = ApprovingProxyPlugin::class.java.name,
            capabilities = listOf("NETWORK_PROXY")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "user-approval-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val uri = URI.create("http://routed.domain.com/file")

        // Default: Approved
        assertTrue(PluginManager.isProxyProviderApproved("user-approval-plugin", "test-approving-provider"))
        val activeRes = PluginManager.resolveProxy(uri, NetworkEngine.HTTP)
        assertNotNull(activeRes, "Active approved provider must resolve proxy")
        assertEquals(ProxyResolution.Type.SOCKS5, activeRes!!.type)

        // User disables provider via settings UI toggle
        PluginManager.setProxyProviderApproved("user-approval-plugin", "test-approving-provider", false)
        assertFalse(PluginManager.isProxyProviderApproved("user-approval-plugin", "test-approving-provider"))

        // Disabled provider must be bypassed
        val disabledRes = PluginManager.resolveProxy(uri, NetworkEngine.HTTP)
        assertNull(disabledRes, "Disabled proxy provider must be skipped during resolution")

        // Verify persistence: disabled state is stored in AppConfig.disabledProxyProviders
        assertTrue(AppContext.config.disabledProxyProviders.contains("user-approval-plugin:test-approving-provider"))

        // Re-enable provider
        PluginManager.setProxyProviderApproved("user-approval-plugin", "test-approving-provider", true)
        assertTrue(PluginManager.isProxyProviderApproved("user-approval-plugin", "test-approving-provider"))
        assertFalse(AppContext.config.disabledProxyProviders.contains("user-approval-plugin:test-approving-provider"))
        assertNotNull(PluginManager.resolveProxy(uri, NetworkEngine.HTTP))
    }

    @Test
    fun testDesktopPlatformHooksProxyTranslation() {
        val pluginDir = File(testPluginsDir, "hook-test-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "hook-test-plugin",
            name = "Hook Test Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for PlatformHooks integration",
            entryPoint = HookTestProxyPlugin::class.java.name,
            capabilities = listOf("NETWORK_PROXY")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "hook-test-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        // Normal proxy translation
        val normalConfig = DesktopPlatformHooks.resolveProxy(1L, "http://normal.hook-target.com/file.iso")
        assertNotNull(normalConfig)
        assertFalse(normalConfig!!.isBlocked)
        assertEquals(Proxy.Type.HTTP, normalConfig.proxy!!.type())
        assertEquals("proxy.hook.local", (normalConfig.proxy!!.address() as java.net.InetSocketAddress).hostString)
        assertEquals(8080, (normalConfig.proxy!!.address() as java.net.InetSocketAddress).port)
        assertEquals("user1", normalConfig.user)
        assertEquals("pass1", normalConfig.pass)

        // Blocked route translation
        val blockedConfig = DesktopPlatformHooks.resolveProxy(2L, "http://blocked.hook-target.com/file.iso")
        assertNotNull(blockedConfig)
        assertTrue(blockedConfig!!.isBlocked)
        assertTrue(blockedConfig.failClosed)
        assertNotNull(blockedConfig.blockedReason)
    }

    @Test
    fun testBitTorrentSessionProxyConfig() {
        val config = TorrentSessionConfig(
            proxyType = TorrentProxyType.SOCKS5_PW,
            proxyHost = "10.10.10.1",
            proxyPort = 1080,
            proxyUser = "torrentuser",
            proxyPass = "torrentpass",
            proxyPeerConnections = true,
            proxyTrackerConnections = true,
            proxyHostnames = true,
            anonymousMode = true
        )

        assertEquals(TorrentProxyType.SOCKS5_PW, config.proxyType)
        assertEquals("10.10.10.1", config.proxyHost)
        assertEquals(1080, config.proxyPort)
        assertEquals("torrentuser", config.proxyUser)
        assertEquals("torrentpass", config.proxyPass)
        assertTrue(config.proxyPeerConnections)
        assertTrue(config.proxyTrackerConnections)
        assertTrue(config.proxyHostnames)
        assertTrue(config.anonymousMode)
    }

    @Test
    fun testSampleProxyProviderV2Integration() {
        val provider = SampleProxyProviderFixture("v2-test-sample", "127.0.0.1", 1080, ProxyResolution.Type.SOCKS5, FallbackPolicy.FAIL_CLOSED)
        assertEquals("v2-test-sample", provider.providerId)
        assertTrue(provider.applicableEngines.contains(NetworkEngine.HTTP))
        assertTrue(provider.applicableEngines.contains(NetworkEngine.TORRENT))

        // Controlled test fixture URL
        val res = provider.resolveProxy(URI.create("http://download.proxy-fixture.local/archive.tar.gz"), NetworkEngine.HTTP)
        assertNotNull(res)
        assertEquals(ProxyResolution.Type.SOCKS5, res!!.type)
        assertEquals("127.0.0.1", res.host)
        assertEquals(1080, res.port)
        assertEquals("testuser", res.username)
        assertEquals("testpass", res.password)
        assertEquals(FallbackPolicy.FAIL_CLOSED, res.fallbackPolicy)

        // Controlled fail-open fixture URL
        val failOpenRes = provider.resolveProxy(URI.create("http://video.failopen-fixture.local/stream.m3u8"), NetworkEngine.MEDIA)
        assertNotNull(failOpenRes)
        assertEquals(FallbackPolicy.FAIL_OPEN, failOpenRes!!.fallbackPolicy)

        // Unrelated external domain: must NOT be intercepted! (honest boundary)
        val unrelatedRes = provider.resolveProxy(URI.create("https://wikipedia.org/wiki/BitTorrent"), NetworkEngine.HTTP)
        assertNull(unrelatedRes, "Unrelated external domains must not be intercepted")
    }

    @Test
    fun testExistingDirectDownloadsUnaffected() {
        // With no matching proxy provider, resolution returns null and falls back to system direct
        val publicUri = URI.create("https://releases.ubuntu.com/noble/ubuntu-24.04-desktop-amd64.iso")
        val res = PluginManager.resolveProxy(publicUri, NetworkEngine.HTTP)
        assertNull(res, "Direct public download should not resolve to any plugin proxy")
        val hookRes = DesktopPlatformHooks.resolveProxy(99L, publicUri.toString())
        assertNull(hookRes, "Direct public download hook should return null to allow default direct connection")
    }

    @Test
    fun testProviderApplicableEnginesFiltering() {
        val provider = object : ProxyProvider {
            override fun getProviderId(): String = "engine-filter-test"
            override fun getApplicableEngines(): Set<NetworkEngine> = EnumSet.of(NetworkEngine.HTTP)
            override fun resolveProxy(targetUri: URI, engine: NetworkEngine): ProxyResolution? {
                return ProxyResolution.http("127.0.0.1", 8080)
            }
        }
        assertTrue(provider.applicableEngines.contains(NetworkEngine.HTTP))
        assertFalse(provider.applicableEngines.contains(NetworkEngine.TORRENT))
    }

    // --- Test Plugin Classes ---

    class ScopedProxyPlugin : XdmPlugin, ProxyProvider {
        private var reg: Registration? = null

        override fun getId(): String = "proxy-test-plugin"

        override fun init(context: PluginContext) {
            reg = context.registerProxyProvider(this)
        }

        override fun shutdown() {
            reg?.unregister()
        }

        override fun getProviderId(): String = "scoped-proxy-provider"

        override fun resolveProxy(targetUri: URI, engine: NetworkEngine): ProxyResolution? {
            if (targetUri.host == "restricted.service.internal") {
                return ProxyResolution.http(
                    "proxy.internal", 8080, "test", "pass",
                    FallbackPolicy.FAIL_CLOSED,
                    EnumSet.of(NetworkEngine.HTTP, NetworkEngine.MEDIA)
                )
            } else if (targetUri.host == "failopen.service.internal") {
                return ProxyResolution.http(
                    "proxy.internal", 8080, "test", "pass",
                    FallbackPolicy.FAIL_OPEN,
                    EnumSet.of(NetworkEngine.HTTP)
                )
            }
            return null
        }
    }

    class ApprovingProxyPlugin : XdmPlugin, ProxyProvider {
        private var reg: Registration? = null

        override fun getId(): String = "user-approval-plugin"

        override fun init(context: PluginContext) {
            reg = context.registerProxyProvider(this)
        }

        override fun shutdown() {
            reg?.unregister()
        }

        override fun getProviderId(): String = "test-approving-provider"

        override fun resolveProxy(targetUri: URI, engine: NetworkEngine): ProxyResolution? {
            if (targetUri.host == "routed.domain.com") {
                return ProxyResolution.socks5("127.0.0.1", 1080)
            }
            return null
        }
    }

    class HookTestProxyPlugin : XdmPlugin, ProxyProvider {
        private var reg: Registration? = null

        override fun getId(): String = "hook-test-plugin"

        override fun init(context: PluginContext) {
            reg = context.registerProxyProvider(this)
        }

        override fun shutdown() {
            reg?.unregister()
        }

        override fun getProviderId(): String = "hook-test-provider"

        override fun resolveProxy(targetUri: URI, engine: NetworkEngine): ProxyResolution? {
            if (targetUri.host == "normal.hook-target.com") {
                return ProxyResolution.http("proxy.hook.local", 8080, "user1", "pass1", FallbackPolicy.FAIL_CLOSED)
            }
            if (targetUri.host == "blocked.hook-target.com") {
                return ProxyResolution.blocked("Blocked domain by corporate rule")
            }
            return null
        }
    }

    class SampleProxyProviderFixture(
        private val id: String,
        private val host: String,
        private val port: Int,
        private val protocol: ProxyResolution.Type,
        private val policy: FallbackPolicy
    ) : ProxyProvider {
        override fun getProviderId(): String = id
        override fun getApplicableEngines(): Set<NetworkEngine> = EnumSet.of(NetworkEngine.HTTP, NetworkEngine.MEDIA, NetworkEngine.TORRENT, NetworkEngine.PLUGIN_TASK)
        override fun resolveProxy(targetUri: URI, engine: NetworkEngine): ProxyResolution? {
            val h = targetUri.host?.lowercase() ?: return null
            if (h == "download.proxy-fixture.local" || h.endsWith(".proxy-fixture.local")) {
                return ProxyResolution.socks5(host, port, "testuser", "testpass", policy)
            }
            if (h == "video.failopen-fixture.local" || h.endsWith(".failopen-fixture.local")) {
                return ProxyResolution.http(host, port, null, null, FallbackPolicy.FAIL_OPEN)
            }
            return null
        }
    }
}
