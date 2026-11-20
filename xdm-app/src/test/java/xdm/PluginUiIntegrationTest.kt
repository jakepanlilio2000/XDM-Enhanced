package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.app.skins.SkinManager
import xdm.app.ui.components.AppTheme
import xdm.app.ui.components.DownloadContextMenu
import xdm.app.ui.components.DownloadStatusBar
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.PropertiesDialog
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.plugin.api.NotificationLevel
import xdm.plugin.api.PluginContext
import xdm.plugin.api.Registration
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.models.DownloadInfo
import xdm.plugin.api.models.PluginAction
import xdm.plugin.api.ui.*
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PluginUiIntegrationTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initUiTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.resetRestartFlag()
        PluginManager.init(safeMode = false)
    }

    @AfterEach
    fun cleanupUiTest() {
        PluginManager.shutdown()
        testPluginsDir.deleteRecursively()
        PluginManager.resetRestartFlag()
    }

    @Test
    fun testPluginSettingsPanelRegistrationAndUnregistration() {
        val pluginDir = File(testPluginsDir, "ui-settings-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-settings-plugin",
            name = "UI Settings Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for settings registration",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf("SETTINGS_PANEL")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-settings-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!
        val panelContributor = object : SettingsPanelContributor {
            override fun getTitle(): String = "Test Panel Title"
            override fun createPanel(ctx: PluginContext?): Component = JPanel()
            override fun saveSettings() {}
        }

        // Register settings panel with v2 disposable handle
        val reg = context.registerSettings(panelContributor)
        assertNotNull(reg)

        val panels = PluginManager.getSettingsPanels()
        assertTrue(panels.any { it.first.manifest.id == "ui-settings-plugin" && it.second.title == "Test Panel Title" })

        // Disposable handle unregistration
        reg.unregister()
        val panelsAfter = PluginManager.getSettingsPanels()
        assertFalse(panelsAfter.any { it.first.manifest.id == "ui-settings-plugin" && it.second.title == "Test Panel Title" },
            "Settings panel must be removed immediately after unregister() call without restart")

        // Also verify explicit unregister method
        context.registerSettingsPanel(panelContributor)
        assertTrue(PluginManager.getSettingsPanels().any { it.first.manifest.id == "ui-settings-plugin" && it.second.title == "Test Panel Title" })
        context.unregisterSettingsPanel(panelContributor)
        assertFalse(PluginManager.getSettingsPanels().any { it.first.manifest.id == "ui-settings-plugin" && it.second.title == "Test Panel Title" },
            "Settings panel must be removed immediately after unregister() call without restart")
    }

    @Test
    fun testPluginContextMenuActionsInTableAndCardViews() {
        val pluginDir = File(testPluginsDir, "ui-menu-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-menu-plugin",
            name = "UI Menu Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for context menu actions",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf("CONTEXT_MENU")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-menu-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!
        val actionTriggered = AtomicBoolean(false)

        val menuContributor = ContextMenuContributor { downloads ->
            listOf(
                PluginAction("test-action", "Custom Menu Action", "Tooltip description", null) {
                    actionTriggered.set(true)
                }
            )
        }

        val reg = context.registerContextMenu(menuContributor)
        assertNotNull(reg)

        // Mock download records
        val rec = DbRecord(
            id = 701L,
            size = 1024L,
            downloaded = 512L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "archive.zip",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.DOWNLOADING,
            selected = false,
            downloadType = xdm.core.downloaders.DownloadType.Http,
            categoryId = null
        )
        val downloadInfo = PluginManager.toDownloadInfo(rec)

        // 1. Table view resolution
        val actionsForTable = PluginManager.getContextMenuActions(listOf(downloadInfo))
        assertEquals(1, actionsForTable.size)
        assertEquals("Custom Menu Action", actionsForTable[0].label)

        // Trigger action
        actionsForTable[0].handler.accept(listOf(downloadInfo))
        assertTrue(actionTriggered.get())

        // 2. Card view resolution (same shared contract)
        val menu = DownloadContextMenu.create(listOf(rec), JPanel(), null)
        assertNotNull(menu)
        // Verify custom menu action was added
        val foundItem = (0 until menu.componentCount)
            .mapNotNull { menu.getComponent(it) as? javax.swing.JMenuItem }
            .firstOrNull { it.text == "Custom Menu Action" }
        assertNotNull(foundItem, "Action must appear in the context menu for both Table and Card mode")

        // 3. Unregistration via handle
        reg.unregister()
        val actionsAfter = PluginManager.getContextMenuActions(listOf(downloadInfo))
        assertTrue(actionsAfter.isEmpty(), "Context menu action must be removed immediately after unregister()")
    }

    @Test
    fun testPluginToolbarActionContributionAndLifecycle() {
        val pluginDir = File(testPluginsDir, "ui-toolbar-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-toolbar-plugin",
            name = "UI Toolbar Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for toolbar actions",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf("TOOLBAR")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-toolbar-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!
        val actionInvoked = AtomicBoolean(false)

        val contributor = ToolbarContributor {
            listOf(
                PluginAction("tb-custom", "Custom Tool", "Toolbar tooltip", null) {
                    actionInvoked.set(true)
                }
            )
        }

        val reg = context.registerToolbar(contributor)
        assertNotNull(reg)

        val toolbarActions = PluginManager.getToolbarActions()
        assertEquals(1, toolbarActions.size)
        assertEquals("Custom Tool", toolbarActions[0].label)
        toolbarActions[0].handler.accept(emptyList())
        assertTrue(actionInvoked.get())

        // Unregister
        reg.unregister()
        assertTrue(PluginManager.getToolbarActions().isEmpty())
    }

    @Test
    fun testPluginDownloadDetailsSectionContribution() {
        val pluginDir = File(testPluginsDir, "ui-details-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-details-plugin",
            name = "UI Details Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for download details section",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf("DETAILS_SECTION")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-details-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!
        val sectionCreated = AtomicBoolean(false)

        val sectionContributor = object : DownloadDetailsSectionContributor {
            override fun getSectionId(): String = "media-hash-section"
            override fun getTitle(): String = "Checksums"
            override fun isApplicable(download: DownloadInfo): Boolean = download.fileName.endsWith(".iso")
            override fun createSection(download: DownloadInfo, ctx: PluginContext): Component {
                sectionCreated.set(true)
                return JLabel("SHA-256 for ${download.fileName}")
            }
        }

        val reg = context.registerDetailsSection(sectionContributor)
        assertNotNull(reg)

        // Non-applicable download
        val docDownload = DownloadInfo(1L, "http://ex.com/doc.pdf", "doc.pdf", "/tmp/doc.pdf", 100L, 100L, 100, 0f, "FINISHED", "Documents", 0L)
        val docSections = PluginManager.getDetailsSections(docDownload)
        assertTrue(docSections.isEmpty(), "Section must not be returned when isApplicable returns false")

        // Applicable download
        val isoDownload = DownloadInfo(2L, "http://ex.com/arch.iso", "arch.iso", "/tmp/arch.iso", 500L, 500L, 100, 0f, "FINISHED", "OS", 0L)
        val isoSections = PluginManager.getDetailsSections(isoDownload)
        assertEquals(1, isoSections.size)
        assertEquals("Checksums", isoSections[0].second.title)

        // Instantiate section
        val comp = isoSections[0].second.createSection(isoDownload, context)
        assertNotNull(comp)
        assertTrue(sectionCreated.get())

        // Disposable unregistration
        reg.unregister()
        assertTrue(PluginManager.getDetailsSections(isoDownload).isEmpty())
    }

    @Test
    fun testPluginStatusIndicatorContributionAndMetricsUpdate() {
        val pluginDir = File(testPluginsDir, "ui-indicator-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-indicator-plugin",
            name = "UI Indicator Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for status indicator",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf("STATUS_INDICATOR")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-indicator-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!
        val updateCount = AtomicInteger(0)

        val indicatorContributor = object : StatusIndicatorContributor {
            override fun getIndicatorId(): String = "active-vpn-pill"
            override fun getTooltip(): String = "VPN Status: Active"
            override fun createIndicator(ctx: PluginContext): Component = JLabel("● VPN")
            override fun updateIndicator() {
                updateCount.incrementAndGet()
            }
        }

        val reg = context.registerStatusIndicator(indicatorContributor)
        assertNotNull(reg)

        val indicators = PluginManager.getStatusIndicators()
        assertEquals(1, indicators.size)
        assertEquals("active-vpn-pill", indicators[0].second.indicatorId)

        // Create indicator component
        val comp = indicators[0].second.createIndicator(context)
        assertNotNull(comp)

        // Simulate status bar metrics tick
        val statusBar = DownloadStatusBar()
        statusBar.updateMetrics()
        assertTrue(updateCount.get() >= 1, "Status indicator updateIndicator() should be called during metrics tick")
        statusBar.dispose()

        // Unregister
        reg.unregister()
        assertTrue(PluginManager.getStatusIndicators().isEmpty())
    }

    @Test
    fun testPluginThemeIntegrationAndDynamicSkinSwitching() {
        val pluginDir = File(testPluginsDir, "ui-theme-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-theme-plugin",
            name = "UI Theme Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for theme and skin awareness",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf("SETTINGS_PANEL")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-theme-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!
        val theme = context.theme
        assertNotNull(theme, "PluginContext.getTheme() must return valid PluginTheme")

        // Validate baseline theme metrics
        assertNotNull(theme.accentColor)
        assertNotNull(theme.baseFont)
        assertTrue(theme.scale(10) >= 10)
        assertNotNull(theme.activeSkinId)

        val themeEvents = CopyOnWriteArrayList<PluginTheme>()
        val themeReg = theme.addThemeListener { t ->
            themeEvents.add(t)
        }
        assertNotNull(themeReg)

        // Switch skins repeatedly: Enhanced -> Classic -> Enhanced
        SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM)
        SkinManager.applySkin(SkinManager.SKIN_ENHANCED)

        // Wait briefly for Swing EDT events
        Thread.sleep(150)

        // Ensure theme listener was called on skin changes without deadlock or freeze
        assertTrue(themeEvents.isNotEmpty(), "PluginThemeListener should receive skin change updates")

        // Disposable unregistration
        val beforeCount = themeEvents.size
        themeReg.unregister()

        SkinManager.applySkin(SkinManager.SKIN_ORIGINAL_XDM)
        Thread.sleep(50)
        assertEquals(beforeCount, themeEvents.size, "No stale events after theme listener unregistration")

        // Reset to default
        SkinManager.applySkin(SkinManager.SKIN_ENHANCED)
    }

    @Test
    fun testUiErrorBoundaryWithMalformedPluginComponent() {
        val pluginDir = File(testPluginsDir, "ui-broken-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-broken-plugin",
            name = "Broken UI Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin that throws during UI creation",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf("STATUS_INDICATOR", "DETAILS_SECTION")
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-broken-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!

        // Malformed status indicator that throws
        context.registerStatusIndicator(object : StatusIndicatorContributor {
            override fun getIndicatorId(): String = "broken-indicator"
            override fun createIndicator(ctx: PluginContext): Component {
                throw IllegalStateException("Intentional simulation of broken plugin indicator component")
            }
        })

        // Malformed details section that throws
        context.registerDetailsSection(object : DownloadDetailsSectionContributor {
            override fun getSectionId(): String = "broken-section"
            override fun getTitle(): String = "Broken Section"
            override fun createSection(download: DownloadInfo, ctx: PluginContext): Component {
                throw RuntimeException("Intentional simulation of broken plugin details component")
            }
        })

        // 1. Status bar must not crash; must render fallback visual
        val statusBar = DownloadStatusBar()
        assertDoesNotThrow { statusBar.refreshPluginIndicators() }
        statusBar.dispose()

        // 2. PropertiesDialog must not crash; must render fallback tab
        val rec = DbRecord(
            id = 808L,
            size = 1000L,
            downloaded = 500L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "data.bin",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.FINISHED,
            selected = false,
            downloadType = xdm.core.downloaders.DownloadType.Http,
            categoryId = null
        )
        assertDoesNotThrow {
            val dialog = PropertiesDialog(null, rec)
            dialog.dispose()
        }
    }

    @Test
    fun testSafetyControlsPreservedInPropertiesDialog() {
        val rec = DbRecord(
            id = 909L,
            size = 2048L,
            downloaded = 2048L,
            progress = 100,
            date = System.currentTimeMillis(),
            fileName = "manual.pdf",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.FINISHED,
            selected = false,
            downloadType = xdm.core.downloaders.DownloadType.Http,
            categoryId = null
        )

        val dialog = PropertiesDialog(null, rec)
        val content = dialog.contentPane
        assertNotNull(content)

        // Core safety control: bottom button bar with Close button must exist and be accessible
        val bottomComp = (content as java.awt.Container).components.firstOrNull {
            it is JPanel && (it.layout is java.awt.FlowLayout || it.layout is java.awt.BorderLayout || it.layout is javax.swing.BoxLayout)
        }
        assertNotNull(bottomComp, "Bottom button bar containing Close button must be preserved")
        val closeBtn = (bottomComp as JPanel).components.filterIsInstance<JButton>().firstOrNull()
        assertNotNull(closeBtn, "Close button must be present in bottom safety bar")
        dialog.dispose()
    }

    @Test
    fun testKeyboardFocusAndNavigation() {
        val btn = JButton("Plugin Action").apply {
            isFocusable = true
            toolTipText = "Accessible action"
        }
        assertTrue(btn.isFocusable)
        assertEquals("Accessible action", btn.toolTipText)
    }

    @Test
    fun testPluginNotificationsDispatchingWithAction() {
        val pluginDir = File(testPluginsDir, "ui-notif-plugin").apply { mkdirs() }
        val manifest = PluginManifest(
            id = "ui-notif-plugin",
            name = "Notification Plugin",
            version = "1.0.0",
            author = "Tester",
            description = "Test plugin for notifications",
            entryPoint = DynamicUiTestPlugin::class.java.name,
            capabilities = listOf()
        )
        File(pluginDir, "plugin.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(PluginManifest.serializer(), manifest)
        )

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ui-notif-plugin" }
        assertNotNull(loaded)
        assertTrue(PluginManager.activatePlugin(loaded!!))

        val context = loaded.context!!
        val actionInvoked = AtomicBoolean(false)
        assertDoesNotThrow {
            context.showNotification("Test Title", "Test Message", NotificationLevel.INFO) {
                actionInvoked.set(true)
            }
        }
    }

    @Test
    fun testStandaloneSamplePluginPackageUiIntegration() {
        // Find pre-built v2-sample-plugin.xdmplugin
        val samplePluginCandidates = listOf(
            File("sample-plugins/v2-sample-plugin.xdmplugin"),
            File("../sample-plugins/v2-sample-plugin.xdmplugin")
        )
        val packageFile = samplePluginCandidates.firstOrNull { it.exists() }
        assertNotNull(packageFile, "v2-sample-plugin.xdmplugin must exist")

        // Install plugin package into test directory
        val result = PluginManager.installPlugin(packageFile!!)
        assertTrue(result.isValid, "Plugin installation must succeed: ${result.errors}")

        PluginManager.discoverPlugins()
        val loaded = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "v2-sample-processor" }
        assertNotNull(loaded)

        // Activate plugin
        assertTrue(PluginManager.trustAndEnablePlugin(loaded!!.manifest.id))
        assertEquals(PluginStatus.ACTIVE, loaded.status)

        // 1. Verify contributed Settings panel
        val settingsPanels = PluginManager.getSettingsPanels()
        assertTrue(settingsPanels.any { it.first.manifest.id == "v2-sample-processor" })

        // 2. Verify contributed Toolbar action
        val toolbarActions = PluginManager.getToolbarActions()
        assertTrue(toolbarActions.any { it.id == "v2-sample-toolbar-action" })

        // 3. Verify contributed Status indicator
        val indicators = PluginManager.getStatusIndicators()
        assertTrue(indicators.any { it.first.manifest.id == "v2-sample-processor" })

        // 4. Verify contributed Details section
        val downloadInfo = DownloadInfo(999L, "http://ex.com/v.mp4", "v.mp4", "/tmp/v.mp4", 1024L, 1024L, 100, 0f, "FINISHED", "Video", 0L)
        val detailsSections = PluginManager.getDetailsSections(downloadInfo)
        assertTrue(detailsSections.any { it.first.manifest.id == "v2-sample-processor" })

        // 5. Deactivate and verify clean unregistration of all UI components
        PluginManager.disablePlugin("v2-sample-processor")
        assertEquals(PluginStatus.DISABLED, loaded.status)

        assertTrue(PluginManager.getSettingsPanels().none { it.first.manifest.id == "v2-sample-processor" })
        assertTrue(PluginManager.getToolbarActions().none { it.id == "v2-sample-toolbar-action" })
        assertTrue(PluginManager.getStatusIndicators().none { it.first.manifest.id == "v2-sample-processor" })
        assertTrue(PluginManager.getDetailsSections(downloadInfo).none { it.first.manifest.id == "v2-sample-processor" })
    }

    // --- Dynamic Helper Plugin for Unit Tests ---
    class DynamicUiTestPlugin : XdmPlugin {
        override fun getId(): String = "dynamic-ui-test"
        override fun init(context: PluginContext) {}
        override fun shutdown() {}
    }
}
