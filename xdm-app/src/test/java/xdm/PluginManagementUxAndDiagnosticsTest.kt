package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.app.ui.screens.settings.PluginsPanel
import xdm.plugin.api.PluginContext
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.models.PluginCapability
import xdm.plugin.api.network.ProxyResolution
import xdm.plugin.api.ui.SettingsPanelContributor
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PluginManagementUxAndDiagnosticsTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun setupUxTest() {
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
    fun cleanupUxTest() {
        PluginManager.shutdown()
        PluginEventBus.clearAll()
        testPluginsDir.deleteRecursively()
        AppContext.config.trustedPlugins = emptyList()
        AppContext.config.enabledPlugins = emptyList()
        AppContext.config.vpnRequiredProviderId = ""
        AppContext.config.vpnPrerequisiteEnabled = false
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

    private fun findComponents(root: Container, predicate: (Component) -> Boolean): List<Component> {
        val matches = mutableListOf<Component>()
        fun recurse(c: Component) {
            if (predicate(c)) matches.add(c)
            if (c is Container) {
                for (child in c.components) {
                    recurse(child)
                }
            }
        }
        recurse(root)
        return matches
    }

    @Test
    fun testCleanInstallWorkflowAndMetadataDisplay() {
        val manifestJson = """
            {
              "id": "sample-ux-plugin",
              "name": "Sample UX Plugin",
              "version": "1.2.3",
              "author": "UX Master",
              "description": "A wonderful plugin for testing UX and management.",
              "license": "MIT",
              "minApiVersion": "1.0.0",
              "entryPoint": "xdm.MockUxPlugin",
              "capabilities": ["DOWNLOAD_PROVIDER", "SETTINGS_PANEL", "NETWORK_PROXY"]
            }
        """.trimIndent()

        val archive = File(dir, "sample-ux-plugin.xdmplugin")
        createZipArchive(archive, mapOf("plugin.json" to manifestJson.toByteArray(StandardCharsets.UTF_8)))

        val validation = PluginManifestValidator.validateArchive(archive)
        assertTrue(validation.isValid, "Archive validation must succeed: ${validation.errors}")

        val installRes = PluginManager.installPlugin(archive)
        assertTrue(installRes.isValid, "Installation must succeed: ${installRes.errors}")

        val installed = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "sample-ux-plugin" }
        assertNotNull(installed, "Installed plugin must appear in PluginManager")
        assertEquals("Sample UX Plugin", installed!!.manifest.name)
        assertEquals("1.2.3", installed.manifest.version)
        assertEquals("UX Master", installed.manifest.author)
        assertEquals("MIT", installed.manifest.license)
        assertEquals(PluginStatus.UNTRUSTED, installed.status)
        assertEquals(3, installed.manifest.capabilities.size)

        var panel: PluginsPanel? = null
        SwingUtilities.invokeAndWait {
            panel = PluginsPanel()
            val labels = findComponents(panel!!) { it is JLabel }.map { (it as JLabel).text }
            assertTrue(labels.any { it != null && it.contains("Sample UX Plugin") }, "Plugin name must appear in panel")
            assertTrue(labels.any { it != null && it.contains("v1.2.3") }, "Plugin version must appear in panel")
            assertTrue(labels.any { it != null && it.contains("UX Master") }, "Plugin author must appear in panel")
            assertTrue(labels.any { it != null && it.contains("License: MIT") }, "Plugin license must appear in panel")
            assertTrue(labels.any { it != null && it.contains("API Compatibility") }, "API compatibility must be disclosed")
            panel!!.dispose()
        }
    }

    @Test
    fun testInvalidPluginArchiveHandling() {
        // 1. Corrupt zero-byte archive
        val emptyZip = File(dir, "empty.xdmplugin").apply { writeBytes(ByteArray(0)) }
        val emptyVal = PluginManifestValidator.validateArchive(emptyZip)
        assertFalse(emptyVal.isValid, "Empty archive must be rejected")

        // 2. Archive without plugin.json
        val missingManifestZip = File(dir, "no-manifest.xdmplugin")
        createZipArchive(missingManifestZip, mapOf("readme.txt" to "no manifest here".toByteArray()))
        val missingVal = PluginManifestValidator.validateArchive(missingManifestZip)
        assertFalse(missingVal.isValid, "Archive without plugin.json must be rejected")
        assertTrue(missingVal.errors.any { it.contains("plugin.json") })

        // 3. Archive containing blacklisted native executable
        val maliciousBinZip = File(dir, "malicious.xdmplugin")
        val validManifest = """{"id": "bin-test", "name": "Bin Test", "version": "1.0.0", "license": "MIT", "entryPoint": "foo"}"""
        createZipArchive(maliciousBinZip, mapOf(
            "plugin.json" to validManifest.toByteArray(),
            "payload.exe" to "MZ...fake-exe".toByteArray()
        ))
        val binVal = PluginManifestValidator.validateArchive(maliciousBinZip)
        assertFalse(binVal.isValid, "Archive with .exe must be rejected")
        assertTrue(binVal.errors.any { it.contains("payload.exe") })

        // 4. Archive with reserved namespace ID
        val reservedZip = File(dir, "reserved.xdmplugin")
        val reservedManifest = """{"id": "system", "name": "System Impersonator", "version": "1.0.0", "license": "MIT", "entryPoint": "foo"}"""
        createZipArchive(reservedZip, mapOf("plugin.json" to reservedManifest.toByteArray()))
        val reservedVal = PluginManifestValidator.validateArchive(reservedZip)
        assertFalse(reservedVal.isValid, "Reserved plugin ID 'system' must be rejected")
        assertTrue(reservedVal.errors.any { it.contains("reserved") })
    }

    @Test
    fun testApiVersionMismatchHandling() {
        val futureManifest = """
            {
              "id": "future-plugin",
              "name": "Future Plugin",
              "version": "1.0.0",
              "author": "Time Traveler",
              "license": "GPL-3.0-or-later",
              "minApiVersion": "99.0.0",
              "entryPoint": "xdm.MockFuturePlugin"
            }
        """.trimIndent()

        val archive = File(dir, "future-plugin.xdmplugin")
        createZipArchive(archive, mapOf("plugin.json" to futureManifest.toByteArray(StandardCharsets.UTF_8)))

        // 1. Archive validation rejects incompatible API version upfront
        val valResult = PluginManifestValidator.validateArchive(archive)
        assertFalse(valResult.isValid, "Archive requiring future API must fail validation")
        assertTrue(valResult.errors.any { it.contains("API version") && it.contains("99.0.0") })

        // 2. Discovered directory with incompatible API is tagged INCOMPATIBLE
        val pluginDir = File(testPluginsDir, "future-plugin").apply { mkdirs() }
        File(pluginDir, "plugin.json").writeText(futureManifest)
        PluginManager.discoverPlugins()

        val installed = PluginManager.installedPlugins.first { it.manifest.id == "future-plugin" }
        assertEquals(PluginStatus.INCOMPATIBLE, installed.status, "Discovered plugin with future API requirement must be INCOMPATIBLE")
        assertTrue(installed.faultReason?.contains("99.0.0") == true, "Fault reason must disclose required API version")

        // Activating incompatible plugin must fail immediately
        val activated = PluginManager.activatePlugin(installed)
        assertFalse(activated, "Incompatible plugin activation must return false")
        assertEquals(PluginStatus.INCOMPATIBLE, installed.status)
    }

    @Test
    fun testRecoverablePluginFaultHandling() {
        RecoverableFaultyPlugin.failInit.set(true)
        val manifest = PluginManifest(
            id = "fault-tolerant-plugin",
            name = "Fault Tolerant Plugin",
            version = "1.0.0",
            author = "Auditor",
            license = "Apache-2.0",
            entryPoint = "xdm.RecoverableFaultyPlugin"
        )
        val pluginDir = File(testPluginsDir, "fault-tolerant-plugin").apply { mkdirs() }
        val loaded = LoadedPlugin(
            manifest = manifest,
            pluginDir = pluginDir,
            status = PluginStatus.DISCOVERED
        )
        PluginManager.installedPlugins.add(loaded)

        // Initial activation fails
        AppContext.config.trustedPlugins = listOf("fault-tolerant-plugin")
        val success1 = PluginManager.activatePlugin(loaded)
        assertFalse(success1, "Activation must fail while transient condition is active")
        assertEquals(PluginStatus.FAULTED, loaded.status)
        assertTrue(loaded.faultReason?.contains("Simulated transient connection fault") == true)

        // Diagnostic record must be written
        val diags = PluginManager.getDiagnostics().filter { it.pluginId == "fault-tolerant-plugin" }
        assertTrue(diags.isNotEmpty(), "Diagnostic record must be logged for faulted plugin")
        assertTrue(diags.last().faultReason.contains("Simulated transient connection fault"))

        // Resolve transient condition and retry
        RecoverableFaultyPlugin.failInit.set(false)
        val success2 = PluginManager.enablePlugin("fault-tolerant-plugin")
        assertTrue(success2, "Re-enabling plugin after resolving fault condition must succeed")
        assertEquals(PluginStatus.ACTIVE, loaded.status)
        assertNull(loaded.faultReason)
    }

    @Test
    fun testRepeatTogglingDoesNotLeakOrCorruptState() {
        ToggleableTestPlugin.initCount.set(0)
        ToggleableTestPlugin.shutdownCount.set(0)

        val manifest = PluginManifest(
            id = "toggle-plugin",
            name = "Toggle Plugin",
            version = "1.0.0",
            author = "Tester",
            license = "GPL-3.0-or-later",
            entryPoint = "xdm.ToggleableTestPlugin"
        )
        val loaded = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "toggle-plugin").apply { mkdirs() },
            status = PluginStatus.DISCOVERED
        )
        PluginManager.installedPlugins.add(loaded)
        AppContext.config.trustedPlugins = listOf("toggle-plugin")

        // Repeat enable/disable cycles
        for (i in 1..5) {
            val enabled = PluginManager.enablePlugin("toggle-plugin")
            assertTrue(enabled, "Cycle $i: enable must succeed")
            assertEquals(PluginStatus.ACTIVE, loaded.status)
            assertEquals(1, PluginManager.getSettingsPanels().size, "Cycle $i: exactly 1 settings panel must be registered")

            PluginManager.disablePlugin("toggle-plugin")
            assertEquals(PluginStatus.DISABLED, loaded.status, "Cycle $i: status must be DISABLED")
            assertEquals(0, PluginManager.getSettingsPanels().size, "Cycle $i: settings panel must be unregistered")
        }

        assertEquals(5, ToggleableTestPlugin.initCount.get(), "Init must be invoked exactly 5 times")
        assertEquals(5, ToggleableTestPlugin.shutdownCount.get(), "Shutdown must be invoked exactly 5 times")
    }

    @Test
    fun testEmptyStateRenderingAndComponents() {
        assertTrue(PluginManager.installedPlugins.isEmpty())

        var panel: PluginsPanel? = null
        SwingUtilities.invokeAndWait {
            panel = PluginsPanel()
            val labels = findComponents(panel!!) { it is JLabel }.map { (it as JLabel).text }
            assertTrue(labels.any { it != null && it.contains("No plugins installed") }, "Empty state title must be displayed")

            val buttons = findComponents(panel!!) { it is JButton }.map { (it as JButton).text }
            assertTrue(buttons.any { it != null && it.contains("Install from File") || it.contains("Install Plugin") }, "Empty state install button must be present")
            panel!!.dispose()
        }
    }

    @Test
    fun testKeyboardAccessibilityAndMnemonics() {
        var panel: PluginsPanel? = null
        SwingUtilities.invokeAndWait {
            panel = PluginsPanel()
            val buttons = findComponents(panel!!) { it is JButton }.map { it as JButton }

            val installBtn = buttons.firstOrNull { it.text.contains("Install from File") || it.text.contains("Install Plugin") }
            assertNotNull(installBtn, "Install button must exist")
            assertEquals(KeyEvent.VK_I, installBtn!!.mnemonic, "Install button must have Alt+I mnemonic")
            assertTrue(installBtn.isFocusable, "Install button must be keyboard focusable")
            assertNotNull(installBtn.accessibleContext.accessibleName, "Install button must have accessible name")

            val exportBtn = buttons.firstOrNull { it.text.contains("Export Diagnostics") }
            assertNotNull(exportBtn, "Export Diagnostics button must exist")
            assertEquals(KeyEvent.VK_E, exportBtn!!.mnemonic, "Export Diagnostics button must have Alt+E mnemonic")
            assertTrue(exportBtn.isFocusable, "Export button must be keyboard focusable")
            assertNotNull(exportBtn.accessibleContext.accessibleName, "Export button must have accessible name")

            val guideBtn = buttons.firstOrNull { it.text.contains("Capabilities Guide") }
            assertNotNull(guideBtn, "Capabilities Guide button must exist")
            assertEquals(KeyEvent.VK_H, guideBtn!!.mnemonic, "Guide button must have Alt+H mnemonic")
            assertTrue(guideBtn.isFocusable, "Guide button must be keyboard focusable")

            val folderBtn = buttons.firstOrNull { it.text.contains("Open Plugins Folder") }
            assertNotNull(folderBtn, "Open Folder button must exist")
            assertEquals(KeyEvent.VK_F, folderBtn!!.mnemonic, "Open Folder button must have Alt+F mnemonic")
            assertTrue(folderBtn.isFocusable, "Folder button must be keyboard focusable")

            panel!!.dispose()
        }
    }

    @Test
    fun testDiagnosticExportWithoutSecrets() {
        // Record sensitive diagnostics event with password and bearer token
        PluginManager.recordDiagnostic(
            "auth-tester",
            "Error connecting to http://admin:superSecretPass123@127.0.0.1:8080/api with Authorization: Bearer secretJwtTokenValue and password=unmaskedSecretValue"
        )
        AppContext.config.vpnRequiredProviderId = "mock-vpn-provider"
        AppContext.config.vpnPrerequisiteEnabled = true

        val report = PluginDiagnosticsExport.generateReport()
        assertNotNull(report)

        // 1. Structural headings verified
        assertTrue(report.contains("XDM ENHANCED - PLUGIN & INTEGRATION DIAGNOSTICS"))
        assertTrue(report.contains("INSTALLED PLUGINS ROSTER"))
        assertTrue(report.contains("DOWNLOAD PROVIDERS & EXTERNAL TOOLS DIAGNOSTICS"))
        assertTrue(report.contains("NETWORK & PROXY INTEGRATION DIAGNOSTICS"))
        assertTrue(report.contains("VPN CLIENT INTEGRATION & TELEMETRY DIAGNOSTICS"))
        assertTrue(report.contains("RECENT PLUGIN DIAGNOSTIC RECORDS"))

        // 2. Secret Redaction Guarantee: Zero raw passwords or tokens in export
        assertFalse(report.contains("superSecretPass123"), "Password in URL must be redacted")
        assertFalse(report.contains("secretJwtTokenValue"), "Bearer token must be redacted")
        assertFalse(report.contains("unmaskedSecretValue"), "Password key-value must be redacted")

        // 3. Redaction tokens present
        assertTrue(report.contains(":***@") || report.contains("***"), "Masked credentials token must appear")

        // 4. File export verification
        val targetFile = File(dir, "test-diagnostics-export.txt")
        val exported = PluginDiagnosticsExport.exportToFile(targetFile)
        assertTrue(exported.exists())
        val fileContent = exported.readText(StandardCharsets.UTF_8)
        assertTrue(fileContent.contains("XDM ENHANCED"))
        assertTrue(fileContent.contains("INSTALLED PLUGINS ROSTER"))
        assertFalse(fileContent.contains("superSecretPass123"))
    }

    @Test
    fun testCapabilitiesGuideAndMetadataInspection() {
        val caps = PluginCapabilityInfo.allKnownCapabilities()
        assertTrue(caps.size >= 10, "All primary plugin capabilities must be registered")

        val dl = caps.first { it.key == "DOWNLOAD_PROVIDER" }
        assertEquals("Download Engine", dl.category)
        assertTrue(dl.externalTools.contains("yt-dlp"))
        assertFalse(dl.restartRequired)
        val dlTooltip = dl.toHtmlTooltip()
        assertTrue(dlTooltip.contains("<b>Download Provider</b>"))
        assertTrue(dlTooltip.contains("External Tools:"))

        val proxy = caps.first { it.key == "NETWORK_PROXY" }
        assertEquals("Network & Routing", proxy.category)
        assertTrue(proxy.consentRequired.contains("approval") || proxy.consentRequired.contains("Settings"))
        assertFalse(proxy.restartRequired)

        val vpn = caps.first { it.key == "VPN_STATUS" }
        assertEquals("Network Tunnel Telemetry", vpn.category)
        assertTrue(vpn.externalTools.contains("VPN client"))
        assertFalse(vpn.restartRequired)

        val media = caps.first { it.key == "MEDIA_METADATA" }
        assertEquals("Media Parsing", media.category)
        assertEquals("Media Metadata Provider", media.displayName)

        val adapter = caps.first { it.key == "TASK_ADAPTER" }
        assertEquals("Download Engine", adapter.category)
        assertEquals("Task Adapter & Engine", adapter.displayName)
    }

    @Test
    fun testUnavailableDependencyHandlingAndReporting() {
        val manifest = PluginManifest(
            id = "dependent-plugin",
            name = "Dependent Plugin",
            version = "1.0.0",
            author = "Auditor",
            license = "MIT",
            entryPoint = "xdm.MockUxPlugin",
            dependencies = listOf("missing-core-plugin")
        )
        val loaded = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "dependent-plugin").apply { mkdirs() },
            status = PluginStatus.DISCOVERED
        )
        PluginManager.installedPlugins.add(loaded)
        AppContext.config.trustedPlugins = listOf("dependent-plugin")

        val activated = PluginManager.activatePlugin(loaded)
        assertFalse(activated, "Activation must fail when dependency is unavailable")
        assertEquals(PluginStatus.FAULTED, loaded.status)
        assertTrue(loaded.faultReason?.contains("missing-core-plugin") == true)

        val diags = PluginManager.getDiagnostics().filter { it.pluginId == "dependent-plugin" }
        assertTrue(diags.isNotEmpty(), "Dependency fault must be logged in diagnostics")
        assertEquals("dependency-check", diags.last().lastAction)
        assertTrue(diags.last().faultReason.contains("missing-core-plugin"))

        var panel: PluginsPanel? = null
        SwingUtilities.invokeAndWait {
            panel = PluginsPanel()
            val labels = findComponents(panel!!) { it is JLabel }.map { (it as JLabel).text }
            assertTrue(labels.any { it != null && it.contains("Fault Error:") && it.contains("missing-core-plugin") })
            panel!!.dispose()
        }
    }

    @Test
    fun testPermissionDenialForUntrustedPlugin() {
        val manifest = PluginManifest(
            id = "untrusted-test-plugin",
            name = "Untrusted Test Plugin",
            version = "1.0.0",
            author = "Auditor",
            license = "MIT",
            entryPoint = "xdm.MockUxPlugin"
        )
        val loaded = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "untrusted-test-plugin").apply { mkdirs() },
            status = PluginStatus.UNTRUSTED
        )
        PluginManager.installedPlugins.add(loaded)
        AppContext.config.trustedPlugins = emptyList()

        val activated = PluginManager.activatePlugin(loaded)
        assertFalse(activated, "Untrusted plugin must not activate without explicit user trust")
        assertEquals(PluginStatus.UNTRUSTED, loaded.status)
        assertTrue(loaded.faultReason?.contains("Permission denied") == true)
    }

    @Test
    fun testRestartRequiredLifecycleAndReset() {
        val manifest = PluginManifest(
            id = "restart-test-plugin",
            name = "Restart Test Plugin",
            version = "1.0.0",
            author = "Auditor",
            license = "MIT",
            entryPoint = "xdm.MockUxPlugin"
        )
        val loaded = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "restart-test-plugin").apply { mkdirs() },
            status = PluginStatus.NEEDS_RESTART
        )
        PluginManager.installedPlugins.add(loaded)
        PluginManager.requiresRestart = true

        var panel: PluginsPanel? = null
        SwingUtilities.invokeAndWait {
            panel = PluginsPanel()
            val labels = findComponents(panel!!) { it is JLabel }.map { (it as JLabel).text }
            assertTrue(labels.any { it != null && it.contains("Restart Required") }, "Restart banner or status must appear")

            PluginManager.resetRestartFlag()
            assertFalse(PluginManager.requiresRestart)
            assertEquals(PluginStatus.DISABLED, loaded.status)
            panel!!.dispose()
        }
    }

    @Test
    fun testNonBlockingEdtAndToolCaching() {
        PluginDiagnosticsExport.clearToolStatusCache()
        val start = System.currentTimeMillis()
        val s1 = PluginDiagnosticsExport.checkYtDlpToolStatus(queryVersion = false)
        val dur1 = System.currentTimeMillis() - start
        assertTrue(dur1 < 1000, "fast check must not block or hang: ${dur1}ms")

        val start2 = System.currentTimeMillis()
        val s2 = PluginDiagnosticsExport.checkYtDlpToolStatus(queryVersion = false)
        val dur2 = System.currentTimeMillis() - start2
        assertTrue(dur2 < 100, "cached check must be sub-100ms: ${dur2}ms")
        assertEquals(s1.detected, s2.detected)
        assertEquals(s1.configuredPath, s2.configuredPath)
    }
}

class RecoverableFaultyPlugin : XdmPlugin {
    override fun getId(): String = "fault-tolerant-plugin"
    override fun init(context: PluginContext?) {
        if (failInit.get()) {
            throw RuntimeException("Simulated transient connection fault")
        }
    }
    override fun shutdown() {}
    companion object {
        val failInit = AtomicBoolean(true)
    }
}

class ToggleableTestPlugin : XdmPlugin {
    override fun getId(): String = "toggle-plugin"
    override fun init(context: PluginContext?) {
        initCount.incrementAndGet()
        context?.registerSettingsPanel(object : SettingsPanelContributor {
            override fun getTitle(): String = "Toggle Settings"
            override fun createPanel(ctx: PluginContext): JComponent = JPanel()
            override fun saveSettings() {}
        })
    }
    override fun shutdown() {
        shutdownCount.incrementAndGet()
    }
    companion object {
        val initCount = AtomicInteger(0)
        val shutdownCount = AtomicInteger(0)
    }
}
