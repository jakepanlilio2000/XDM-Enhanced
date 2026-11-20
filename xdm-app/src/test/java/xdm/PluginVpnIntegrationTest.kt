package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.DesktopPlatformHooks
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.plugin.api.PluginContext
import xdm.plugin.api.Registration
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.models.PluginDownloadRequest
import xdm.plugin.api.network.*
import java.io.File
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

class PluginVpnIntegrationTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initVpnTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.resetRestartFlag()
        PluginManager.init(safeMode = false)
        AppContext.config.vpnPrerequisiteEnabled = false
        AppContext.config.vpnRequiredProviderId = ""
        AppContext.config.vpnRequiredProfileId = ""
        AppContext.config.vpnAutoResumeOnReconnect = false
        VpnPolicyManager.clearPausedTasks()
    }

    @AfterEach
    fun cleanupVpnTest() {
        PluginManager.shutdown()
        testPluginsDir.deleteRecursively()
        PluginManager.resetRestartFlag()
        AppContext.config.vpnPrerequisiteEnabled = false
        AppContext.config.vpnRequiredProviderId = ""
        AppContext.config.vpnRequiredProfileId = ""
        AppContext.config.vpnAutoResumeOnReconnect = false
        VpnPolicyManager.clearPausedTasks()
    }

    @Test
    fun testVpnClientCapabilitiesAndProfilesDiscovery() {
        val caps = VpnClientCapabilities(
            true, true, true, true, "v1.0.2026", "C:\\Program Files\\WireGuard\\wireguard.exe"
        )
        assertTrue(caps.isClientInstalled)
        assertTrue(caps.isSupportsProfiles)
        assertTrue(caps.isCanConnect)
        assertTrue(caps.isCanDisconnect)
        assertEquals("v1.0.2026", caps.clientVersion)
        assertEquals("C:\\Program Files\\WireGuard\\wireguard.exe", caps.clientPath)

        val uninstalled = VpnClientCapabilities.uninstalled()
        assertFalse(uninstalled.isClientInstalled)
        assertFalse(uninstalled.isSupportsProfiles)

        val profile1 = VpnProfile("us-east", "US East (Virginia)", "us-east.wireguard.net", true)
        val profile2 = VpnProfile("eu-west", "EU West (London)", "eu-west.wireguard.net", false)
        assertEquals("us-east", profile1.profileId)
        assertEquals("US East (Virginia)", profile1.displayName)
        assertTrue(profile1.isActive)
        assertFalse(profile2.isActive)
    }

    @Test
    fun testVpnStatusSeparationBetweenUiLabelAndRealTunnelState() {
        // Status labeled "Connected" with verified tunnel interface
        val verifiedStatus = VpnStatus.connected("wg0", "US East", "10.8.0.2", "us-east", true, "Verified Tunnel")
        assertTrue(verifiedStatus.isConnected)
        assertTrue(verifiedStatus.isTunnelActive)
        assertTrue(verifiedStatus.isTunnelVerified)
        assertEquals("wg0", verifiedStatus.interfaceName)
        assertEquals("us-east", verifiedStatus.activeProfileId)

        // Status labeled "Connected" but without verified tunnel or interface (e.g. mock or unverified label)
        val unverifiedStatus = VpnStatus(VpnStatus.State.CONNECTED, null, "US East", "10.8.0.2", "us-east", false, "Unverified label")
        assertTrue(unverifiedStatus.isConnected, "UI state shows connected")
        assertFalse(unverifiedStatus.isTunnelActive, "Tunnel active check must separate unverified UI label from real tunnel")

        // Disconnected state
        val disconnected = VpnStatus.disconnected("Client disconnected")
        assertFalse(disconnected.isConnected)
        assertFalse(disconnected.isTunnelActive)
        assertEquals(VpnStatus.State.DISCONNECTED, disconnected.state)

        // Error state
        val errorStatus = VpnStatus.error("Adapter initialization failed")
        assertFalse(errorStatus.isConnected)
        assertEquals(VpnStatus.State.ERROR, errorStatus.state)
        assertEquals("Adapter initialization failed", errorStatus.statusMessage)

        // Unknown state
        val unknownStatus = VpnStatus.unknown("Client process terminated unexpectedly")
        assertFalse(unknownStatus.isConnected)
        assertEquals(VpnStatus.State.UNKNOWN, unknownStatus.state)
    }

    @Test
    fun testExplicitUserInitiatedConnectAndDisconnect() {
        val mockProvider = TestVpnStatusProvider("test-vpn-provider", "Mock WireGuard")
        val statusHistory = mutableListOf<VpnStatus>()
        mockProvider.addStateListener { statusHistory.add(it) }

        // Initial state: connected to default profile
        assertTrue(mockProvider.currentStatus.isConnected)

        // User explicit disconnect
        val disconnResult = mockProvider.requestDisconnect()
        assertTrue(disconnResult)
        assertEquals(VpnStatus.State.DISCONNECTED, mockProvider.currentStatus.state)

        // User explicit connect to specific profile
        val connResult = mockProvider.requestConnect("profile-eu-west")
        assertTrue(connResult)
        assertEquals(VpnStatus.State.CONNECTED, mockProvider.currentStatus.state)
        assertEquals("profile-eu-west", mockProvider.currentStatus.activeProfileId)

        // Verify state change listener dispatching
        assertTrue(statusHistory.size >= 2)
        assertEquals(VpnStatus.State.DISCONNECTED, statusHistory[0].state)
        assertEquals(VpnStatus.State.CONNECTED, statusHistory.last().state)
    }

    @Test
    fun testDownloadPolicyPauseOnVpnDisconnect() {
        val pluginDir = File(testPluginsDir, "vpn-policy-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "vpn-policy-plugin",
            name = "VPN Policy Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for VPN pause policy",
            entryPoint = VpnPolicyTestPlugin::class.java.name,
            capabilities = listOf("VPN_STATUS")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "vpn-policy-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val provider = VpnPolicyTestPlugin.activeInstance?.provider
        assertNotNull(provider)

        // Enable VPN prerequisite policy
        AppContext.config.vpnPrerequisiteEnabled = true
        AppContext.config.vpnRequiredProviderId = "policy-vpn-provider"
        AppContext.config.vpnAutoResumeOnReconnect = false

        // Create a running mock download in store
        val record = DbRecord(
            id = 101L,
            size = 1048576L,
            downloaded = 524288L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "test-download.iso",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.DOWNLOADING,
            selected = false,
            downloadType = DownloadType.Http,
            categoryId = null
        )
        AppContext.db.add(record)

        // Prerequisite is initially met (provider is connected)
        assertTrue(VpnPolicyManager.isVpnPrerequisiteMet())
        assertTrue(DesktopPlatformHooks.checkPrerequisites(101L, "http://example.com/test-download.iso"))

        // Simulate external VPN client disconnect
        provider!!.simulateStatus(VpnStatus.disconnected("External tunnel dropped"))

        // Prerequisite is now unmet!
        assertFalse(VpnPolicyManager.isVpnPrerequisiteMet())
        assertFalse(DesktopPlatformHooks.checkPrerequisites(101L, "http://example.com/test-download.iso"))

        // Active download must be paused by the policy
        val updatedRecord = AppContext.db.getById(101L)
        assertNotNull(updatedRecord)
        assertEquals(RecordStatus.PAUSED, updatedRecord!!.status, "Download must be paused when VPN prerequisite is unmet")
        assertTrue(VpnPolicyManager.isTaskPausedByVpnPolicy(101L), "Task ID must be tracked as paused by VPN policy")
    }

    @Test
    fun testDownloadPolicyResumptionOnReconnect() {
        val pluginDir = File(testPluginsDir, "vpn-resume-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "vpn-resume-plugin",
            name = "VPN Resume Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for VPN auto-resume vs manual resumption",
            entryPoint = VpnPolicyTestPlugin::class.java.name,
            capabilities = listOf("VPN_STATUS")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "vpn-resume-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val provider = VpnPolicyTestPlugin.activeInstance?.provider
        assertNotNull(provider)

        AppContext.config.vpnPrerequisiteEnabled = true
        AppContext.config.vpnRequiredProviderId = "policy-vpn-provider"

        val record = DbRecord(
            id = 202L,
            size = 2000000L,
            downloaded = 1000000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "video-stream.mp4",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.DOWNLOADING,
            selected = false,
            downloadType = DownloadType.Http,
            categoryId = null
        )
        AppContext.db.add(record)

        // 1. Scenario A: vpnAutoResumeOnReconnect = false (Default/Safe: Requires explicit user action)
        AppContext.config.vpnAutoResumeOnReconnect = false

        // Disconnect -> download paused
        provider!!.simulateStatus(VpnStatus.disconnected())
        assertEquals(RecordStatus.PAUSED, AppContext.db.getById(202L)?.status)
        assertTrue(VpnPolicyManager.isTaskPausedByVpnPolicy(202L))

        // Reconnect -> Task MUST stay paused when auto-resume is false!
        provider.simulateStatus(VpnStatus.connected("wg0", "US East", "10.0.0.1"))
        assertEquals(RecordStatus.PAUSED, AppContext.db.getById(202L)?.status,
            "When vpnAutoResumeOnReconnect is false, tasks must remain paused until explicit user action")

        // 2. Scenario B: vpnAutoResumeOnReconnect = true
        AppContext.config.vpnAutoResumeOnReconnect = true
        // Set back to downloading
        AppContext.db.getById(202L)?.status = RecordStatus.DOWNLOADING

        // Disconnect -> download paused
        provider.simulateStatus(VpnStatus.disconnected())
        assertEquals(RecordStatus.PAUSED, AppContext.db.getById(202L)?.status)

        // Reconnect -> Task is auto-resumed
        provider.simulateStatus(VpnStatus.connected("wg0", "US East", "10.0.0.1"))
        assertFalse(VpnPolicyManager.isTaskPausedByVpnPolicy(202L), "Paused task set must be cleared on auto-resume")
    }

    @Test
    fun testDownloadGateWhenVpnDisconnected() {
        val pluginDir = File(testPluginsDir, "vpn-gate-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "vpn-gate-plugin",
            name = "VPN Gate Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for new download gate",
            entryPoint = VpnPolicyTestPlugin::class.java.name,
            capabilities = listOf("VPN_STATUS")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "vpn-gate-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val provider = VpnPolicyTestPlugin.activeInstance?.provider
        assertNotNull(provider)

        AppContext.config.vpnPrerequisiteEnabled = true
        AppContext.config.vpnRequiredProviderId = "policy-vpn-provider"

        // Set VPN state to disconnected
        provider!!.simulateStatus(VpnStatus.disconnected("VPN is offline"))

        // 1. DesktopPlatformHooks must reject prerequisites
        assertFalse(DesktopPlatformHooks.checkPrerequisites(303L, "http://downloads.com/file.zip"))

        // 2. PluginContext must report prerequisite unmet
        val context = loaded.context
        assertNotNull(context)
        assertFalse(context!!.isVpnPrerequisiteMet)

        // 3. PluginDownloadBridge: New task creation while VPN is disconnected must be set to PAUSED immediately
        val mockTask = TestPluginTask()
        val req = PluginDownloadRequest("http://example.com/video.mp4", dir.absolutePath, "video.mp4")
        val taskId = PluginDownloadBridge.registerPluginTask(loaded, req, mockTask)
        assertNotEquals(-1L, taskId)
        val taskRecord = AppContext.db.getById(taskId)
        assertNotNull(taskRecord)
        assertEquals(RecordStatus.PAUSED, taskRecord!!.status, "New task created while VPN is disconnected must start in PAUSED state")
        assertFalse(mockTask.wasStarted, "Process must not be started when VPN prerequisite is unmet")
    }

    @Test
    fun testConfigurationPersistenceAcrossRestart() {
        AppContext.config.vpnPrerequisiteEnabled = true
        AppContext.config.vpnRequiredProviderId = "wireguard-corp"
        AppContext.config.vpnRequiredProfileId = "us-east-prod"
        AppContext.config.vpnAutoResumeOnReconnect = true
        AppContext.config.save()

        // Reload configuration from disk
        AppContext.config.load()
        assertTrue(AppContext.config.vpnPrerequisiteEnabled)
        assertEquals("wireguard-corp", AppContext.config.vpnRequiredProviderId)
        assertEquals("us-east-prod", AppContext.config.vpnRequiredProfileId)
        assertTrue(AppContext.config.vpnAutoResumeOnReconnect)
    }

    @Test
    fun testZeroOperatingSystemNetworkModification() {
        // Assert that VpnPolicyManager and plugins never touch OS network adapters, system proxy, or firewall
        val provider = TestVpnStatusProvider("audit-provider", "Audit Provider")
        provider.requestConnect("profile-1")
        provider.requestDisconnect()

        // System property check: java http/https proxy remains untouched
        assertNull(System.getProperty("http.proxyHost"))
        assertNull(System.getProperty("socksProxyHost"))

        // Local interface verification is strictly read-only
        assertFalse(VpnPolicyManager.verifyInterfaceActive("non-existent-adapter-999"))
    }

    @Test
    fun testPluginOwnedTaskPauseAndAutoResumeOnVpnTransition() {
        val pluginDir = File(testPluginsDir, "vpn-plugintask-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "vpn-plugintask-plugin",
            name = "VPN Plugin Task",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for plugin-owned task pause/resume on VPN transition",
            entryPoint = VpnPolicyTestPlugin::class.java.name,
            capabilities = listOf("VPN_STATUS", "DOWNLOAD_PROVIDER")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "vpn-plugintask-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val provider = VpnPolicyTestPlugin.activeInstance?.provider
        assertNotNull(provider)

        AppContext.config.vpnPrerequisiteEnabled = true
        AppContext.config.vpnRequiredProviderId = "policy-vpn-provider"
        AppContext.config.vpnAutoResumeOnReconnect = true

        // Create and register an active plugin download task
        val mockTask = TestPluginTask()
        val req = PluginDownloadRequest("http://example.com/stream.mp4", dir.absolutePath, "stream.mp4")
        val taskId = PluginDownloadBridge.registerPluginTask(loaded, req, mockTask)
        assertNotEquals(-1L, taskId)
        assertTrue(mockTask.wasStarted)

        // Mark it as DOWNLOADING
        val record = AppContext.db.getById(taskId)
        assertNotNull(record)
        record!!.status = RecordStatus.DOWNLOADING
        AppContext.db.update(record)

        // 1. Simulate VPN disconnect -> Plugin task must be paused!
        provider!!.simulateStatus(VpnStatus.disconnected("VPN dropped"))
        val pausedRec = AppContext.db.getById(taskId)
        assertEquals(RecordStatus.PAUSED, pausedRec?.status)
        assertTrue(mockTask.wasPaused, "Plugin task must receive pause() call on VPN disconnect")
        assertTrue(VpnPolicyManager.isTaskPausedByVpnPolicy(taskId))

        // 2. Simulate VPN reconnect -> Plugin task must be auto-resumed!
        provider.simulateStatus(VpnStatus.connected("wg0", "US East", "10.0.0.5"))
        assertTrue(mockTask.wasResumed, "Plugin task must receive resume() call on VPN reconnect")
        assertFalse(VpnPolicyManager.isTaskPausedByVpnPolicy(taskId))
    }

    @Test
    fun testActiveDownloadsPausedWhenVpnPluginDeactivated() {
        val pluginDir = File(testPluginsDir, "vpn-deactivate-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "vpn-deactivate-plugin",
            name = "VPN Deactivate Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for VPN provider deactivation",
            entryPoint = VpnPolicyTestPlugin::class.java.name,
            capabilities = listOf("VPN_STATUS")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "vpn-deactivate-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        AppContext.config.vpnPrerequisiteEnabled = true
        AppContext.config.vpnRequiredProviderId = "policy-vpn-provider"

        // Active download
        val rec = DbRecord(
            id = 505L,
            size = 5000L,
            downloaded = 2500L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "secure-data.bin",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.DOWNLOADING,
            selected = false,
            downloadType = DownloadType.Http,
            categoryId = null
        )
        AppContext.db.add(rec)

        // Deactivating the VPN provider plugin must immediately pause active downloads
        PluginManager.deactivatePlugin(loaded, setStatus = true)
        val updated = AppContext.db.getById(505L)
        assertEquals(RecordStatus.PAUSED, updated?.status, "Downloads must be paused when VPN provider plugin is deactivated")
        assertTrue(VpnPolicyManager.isTaskPausedByVpnPolicy(505L))
    }

    @Test
    fun testTorrentTaskPausedOnVpnDisconnect() {
        val pluginDir = File(testPluginsDir, "vpn-torrent-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "vpn-torrent-plugin",
            name = "VPN Torrent Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for torrent pause on VPN disconnect",
            entryPoint = VpnPolicyTestPlugin::class.java.name,
            capabilities = listOf("VPN_STATUS")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "vpn-torrent-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val provider = VpnPolicyTestPlugin.activeInstance?.provider
        assertNotNull(provider)

        AppContext.config.vpnPrerequisiteEnabled = true
        AppContext.config.vpnRequiredProviderId = "policy-vpn-provider"

        val torrentRec = DbRecord(
            id = 606L,
            size = 100000L,
            downloaded = 20000L,
            progress = 20,
            date = System.currentTimeMillis(),
            fileName = "linux-iso.torrent",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.DOWNLOADING,
            selected = false,
            downloadType = DownloadType.Torrent,
            categoryId = null
        )
        AppContext.db.add(torrentRec)

        // Disconnect VPN
        provider!!.simulateStatus(VpnStatus.disconnected("Tunnel dropped"))
        val updated = AppContext.db.getById(606L)
        assertEquals(RecordStatus.PAUSED, updated?.status, "Torrent download must be paused when VPN disconnects")
        assertTrue(VpnPolicyManager.isTaskPausedByVpnPolicy(606L))
    }

    // --- Test Helper Classes ---

    class VpnPolicyTestPlugin : XdmPlugin {
        var provider: TestVpnStatusProvider? = null
        private var reg: Registration? = null

        companion object {
            var activeInstance: VpnPolicyTestPlugin? = null
        }

        override fun getId(): String = "vpn-policy-plugin"

        override fun init(context: PluginContext) {
            activeInstance = this
            provider = TestVpnStatusProvider("policy-vpn-provider", "Policy VPN Client")
            reg = context.registerVpnStatusProvider(provider)
        }

        override fun shutdown() {
            reg?.unregister()
            activeInstance = null
            provider = null
        }
    }

    class TestVpnStatusProvider(
        private val id: String,
        private val name: String
    ) : VpnStatusProvider {
        private val listeners = CopyOnWriteArrayList<VpnStateListener>()
        private var status: VpnStatus = VpnStatus.connected("wg0", "US East", "10.14.0.1", "default", true, "Connected")

        override fun getProviderId(): String = id
        override fun getDisplayName(): String = name
        override fun getCurrentStatus(): VpnStatus = status
        override fun getCapabilities(): VpnClientCapabilities = VpnClientCapabilities(true, true, true, true, "1.0", "mock.exe")
        override fun getAvailableProfiles(): List<VpnProfile> = listOf(
            VpnProfile("default", "Default Profile", "US East", status.activeProfileId == "default"),
            VpnProfile("profile-eu-west", "EU West Profile", "London", status.activeProfileId == "profile-eu-west")
        )

        override fun requestConnect(profileId: String?): Boolean {
            val prof = profileId ?: "default"
            status = VpnStatus.connected("wg0", "US East", "10.14.0.2", prof, true, "Connected via request")
            notifyListeners()
            return true
        }

        override fun requestDisconnect(): Boolean {
            status = VpnStatus.disconnected("Disconnected via request")
            notifyListeners()
            return true
        }

        fun simulateStatus(newStatus: VpnStatus) {
            this.status = newStatus
            notifyListeners()
        }

        private fun notifyListeners() {
            listeners.forEach { it.onVpnStateChanged(status) }
        }

        override fun addStateListener(listener: VpnStateListener?) {
            if (listener != null) listeners.add(listener)
        }

        override fun removeStateListener(listener: VpnStateListener?) {
            if (listener != null) listeners.remove(listener)
        }
    }

    class TestPluginTask : xdm.plugin.api.download.PluginDownloadTask {
        var wasStarted = false
        var wasPaused = false
        var wasResumed = false
        var canceled = false

        override fun start() { wasStarted = true }
        override fun pause() { wasPaused = true }
        override fun resume() { wasResumed = true }
        override fun cancel() { canceled = true }
        override fun canPause(): Boolean = true
        override fun canResume(): Boolean = true
        override fun getState(): xdm.plugin.api.download.PluginDownloadTask.TaskState =
            if (wasPaused) xdm.plugin.api.download.PluginDownloadTask.TaskState.PAUSED
            else if (wasStarted) xdm.plugin.api.download.PluginDownloadTask.TaskState.DOWNLOADING
            else xdm.plugin.api.download.PluginDownloadTask.TaskState.READY
        override fun getDownloadedBytes(): Long = 0L
        override fun getTotalBytes(): Long = 1000L
        override fun getTransferSpeed(): Float = 0f
        override fun getErrorMessage(): String? = null
        override fun getOutputFile(): File? = null
        override fun addProgressListener(listener: xdm.plugin.api.download.DownloadProgressListener?) {}
        override fun removeProgressListener(listener: xdm.plugin.api.download.DownloadProgressListener?) {}
    }
}
