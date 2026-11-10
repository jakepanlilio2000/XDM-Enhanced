package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.download.DownloadProgressEvent
import xdm.plugin.api.download.PluginDownloadTask
import xdm.plugin.api.models.PluginDownloadRequest
import java.io.File
import javax.swing.JComponent

class YtDlpPluginIntegrationTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initYtDlpTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.init(safeMode = false)
    }

    @AfterEach
    fun cleanupYtDlpTest() {
        PluginManager.shutdown()
        testPluginsDir.deleteRecursively()
    }

    private fun resolveYtDlpPlugin(): File {
        val candidates = listOf(
            File("sample-plugins/ytdlp-downloader.xdmplugin"),
            File("../sample-plugins/ytdlp-downloader.xdmplugin"),
            File(System.getProperty("user.dir"), "sample-plugins/ytdlp-downloader.xdmplugin"),
            File(System.getProperty("user.dir"), "../sample-plugins/ytdlp-downloader.xdmplugin")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Cannot find ytdlp-downloader.xdmplugin. Checked: ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun testYtDlpPluginArchiveValidation() {
        val pkg = resolveYtDlpPlugin()
        assertTrue(pkg.exists(), "yt-dlp plugin package should exist: ${pkg.absolutePath}")

        val result = PluginManifestValidator.validateArchive(pkg)
        assertTrue(result.isValid, "Archive validation should succeed: ${result.errors}")
        assertNotNull(result.manifest)

        val manifest = result.manifest!!
        assertEquals("ytdlp-downloader", manifest.id)
        assertEquals("yt-dlp Media Downloader", manifest.name)
        assertEquals("xdm.plugin.ytdlp.YtDlpPlugin", manifest.entryPoint)
        assertTrue(manifest.capabilities.contains("DOWNLOAD_PROVIDER"))
        assertTrue(manifest.capabilities.contains("URL_RECOGNIZER"))
        assertTrue(manifest.capabilities.contains("SETTINGS_PANEL"))
        assertTrue(manifest.capabilities.contains("TOOLBAR_ACTION"))
        assertTrue(manifest.capabilities.contains("COMMAND"))
        assertTrue(result.sha256.isNotBlank(), "SHA-256 hash must be computed")
    }

    @Test
    fun testYtDlpPluginInstallationAndQuarantine() {
        val pkg = resolveYtDlpPlugin()
        val installResult = PluginManager.installPlugin(pkg)
        assertTrue(installResult.isValid, "Installation should succeed: ${installResult.errors}")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ytdlp-downloader" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.UNTRUSTED, plugin!!.status, "Newly installed plugin must be quarantined as UNTRUSTED")
        assertNull(plugin.instance, "Untrusted plugin must not be instantiated before approval")
    }

    @Test
    fun testYtDlpPluginTrustAndActivation() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)

        val success = PluginManager.trustAndEnablePlugin("ytdlp-downloader")
        assertTrue(success, "Trusting and enabling yt-dlp plugin should succeed")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ytdlp-downloader" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.ACTIVE, plugin!!.status)
        assertNotNull(plugin.instance)
        assertTrue(plugin.instance is XdmPlugin)
        assertNotNull(plugin.context)

        // Verify registered DownloadProvider via PluginManager helper
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(providerPair, "DownloadProvider must be resolved for YouTube URL")
        assertEquals("yt-dlp Media Downloader", providerPair!!.second.name)

        // Verify registered UrlRecognizer via PluginManager helper
        val recognizerPair = PluginManager.findUrlRecognizer("https://vimeo.com/76979871")
        assertNotNull(recognizerPair, "UrlRecognizer must be resolved for Vimeo URL")
        assertTrue(recognizerPair!!.second.matches("https://vimeo.com/76979871"))

        // Verify toolbar action
        val toolbarActions = PluginManager.getToolbarActions()
        assertTrue(toolbarActions.any { it.id == "ytdlp-action-toolbar" }, "Toolbar action must be registered")

        // Verify settings panel contributor
        val settingsPanels = PluginManager.getSettingsPanels()
        assertTrue(settingsPanels.any { it.first.manifest.id == "ytdlp-downloader" }, "Settings panel must be registered")
    }

    @Test
    fun testYtDlpUrlRecognition() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val recognizerPair = PluginManager.findUrlRecognizer("https://www.youtube.com/watch?v=test12345")
        assertNotNull(recognizerPair)
        val recognizer = recognizerPair!!.second

        assertTrue(recognizer.matches("https://www.youtube.com/watch?v=test12345"))
        assertTrue(recognizer.matches("https://youtu.be/test12345"))
        assertTrue(recognizer.matches("https://vimeo.com/12345678"))
        assertTrue(recognizer.matches("https://soundcloud.com/artist/track"))
        assertTrue(recognizer.matches("https://bandcamp.com/track/sample"))

        assertFalse(recognizer.matches("https://example.com/file.zip"))
        assertFalse(recognizer.matches("https://github.com/jakepanlilio2000/XDM-Enhanced"))
        assertFalse(recognizer.matches(""))

        val meta = recognizer.analyze("https://www.youtube.com/watch?v=test12345")
        assertNotNull(meta)
        assertEquals("CAT_VIDEO", meta.suggestedCategory)
        assertEquals("ytdlp", meta.extraData["provider"])
    }

    @Test
    fun testExecutableValidatorThroughClassLoader() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        val validatorClass = plugin.classLoader!!.loadClass("xdm.plugin.ytdlp.YtDlpExecutableValidator")
        val validateMethod = validatorClass.getMethod("validate", String::class.java)

        // Non-existent path
        val missingResult = validateMethod.invoke(null, "C:/non_existent_ytdlp_binary_test.exe")
        val resultClass = missingResult.javaClass
        val isValidMissing = resultClass.getMethod("isValid").invoke(missingResult) as Boolean
        val errorMissing = resultClass.getMethod("getErrorMessage").invoke(missingResult) as String
        assertFalse(isValidMissing)
        assertTrue(errorMissing.contains("does not exist") || errorMissing.contains("Failed to execute"))

        // Blank path
        val blankResult = validateMethod.invoke(null, "")
        val isValidBlank = blankResult.javaClass.getMethod("isValid").invoke(blankResult) as Boolean
        val errorBlank = blankResult.javaClass.getMethod("getErrorMessage").invoke(blankResult) as String
        assertFalse(isValidBlank)
        assertEquals("Executable path cannot be blank.", errorBlank)

        // Real binary execution (git on system PATH)
        val gitResult = validateMethod.invoke(null, "git")
        val isValidGit = resultClass.getMethod("isValid").invoke(gitResult) as Boolean
        val versionGit = resultClass.getMethod("getVersion").invoke(gitResult) as String
        assertTrue(isValidGit, "Executing 'git --version' through structured ProcessBuilder should succeed")
        assertTrue(versionGit.lowercase().contains("git"), "Reported version output should contain 'git': $versionGit")
    }

    @Test
    fun testYtDlpProgressParsingAndMetricsThroughClassLoader() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        val taskClass = plugin.classLoader!!.loadClass("xdm.plugin.ytdlp.YtDlpDownloadTask")

        val parseBytesMethod = taskClass.getMethod("parseBytes", String::class.java)
        assertEquals(1024L, parseBytesMethod.invoke(null, "1024B"))
        assertEquals(1024L * 1024L, parseBytesMethod.invoke(null, "1.0MiB"))
        assertEquals(512L * 1024L, parseBytesMethod.invoke(null, "512KiB"))
        assertEquals(2L * 1024L * 1024L * 1024L, parseBytesMethod.invoke(null, "2GiB"))
        assertEquals(-1L, parseBytesMethod.invoke(null, "UNKNOWN"))

        val parseSpeedMethod = taskClass.getMethod("parseSpeed", String::class.java)
        assertEquals(1024L * 1024L.toFloat(), parseSpeedMethod.invoke(null, "1.0MiB/s"))
        assertEquals(0f, parseSpeedMethod.invoke(null, "0B/s"))

        val parseEtaMethod = taskClass.getMethod("parseEta", String::class.java)
        assertEquals(75L, parseEtaMethod.invoke(null, "01:15"))
        assertEquals(3665L, parseEtaMethod.invoke(null, "01:01:05"))
        assertEquals(-1L, parseEtaMethod.invoke(null, "NA"))
    }

    @Test
    fun testYtDlpSimulatedProgressLineFeed() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val provider = providerPair!!.second

        val request = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=test_progress",
            dir.absolutePath,
            "simulated_video.mp4"
        )
        val task = provider.createDownloadTask(request)

        val events = mutableListOf<DownloadProgressEvent>()
        task.addProgressListener { event -> events.add(event) }

        val processMethod = task.javaClass.getMethod("processOutputLine", String::class.java)

        // Feed structured progress line
        processMethod.invoke(task, "download: 50.0%|200MiB|10MiB/s|00:10")
        assertEquals(104857600L, task.downloadedBytes)
        assertEquals(209715200L, task.totalBytes)
        assertEquals(10485760.0f, task.transferSpeed)
        assertTrue(events.isNotEmpty())
        assertEquals(50, events.last().progressPercent)

        // Feed destination line
        val expectedFile = File(dir, "simulated_video.mp4")
        processMethod.invoke(task, "[download] Destination: ${expectedFile.absolutePath}")
        assertEquals(expectedFile, task.outputFile)
    }

    @Test
    fun testYtDlpDownloadTaskLifecycleAndCancellation() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(providerPair)
        val provider = providerPair!!.second

        val request = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=test_lifecycle",
            dir.absolutePath,
            "test_output.mp4"
        )

        val task = provider.createDownloadTask(request)
        assertEquals(PluginDownloadTask.TaskState.READY, task.state)

        val receivedEvents = mutableListOf<DownloadProgressEvent>()
        task.addProgressListener { event ->
            receivedEvents.add(event)
        }

        // Cancel before start
        task.cancel()
        assertEquals(PluginDownloadTask.TaskState.CANCELED, task.state)
        assertTrue(receivedEvents.any { it.state == PluginDownloadTask.TaskState.CANCELED || it.message.contains("cancel") })

        // Calling start on a canceled task does not change its state
        task.start()
        assertEquals(PluginDownloadTask.TaskState.CANCELED, task.state)
    }

    @Test
    fun testYtDlpDownloadTaskErrorHandling() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val provider = providerPair!!.second

        // Configure a non-existent binary to trigger process startup error
        plugin.context!!.config.set("executable.path", "C:/non_existent_ytdlp_bin_path.exe")

        val request = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=test_error",
            dir.absolutePath,
            "error_output.mp4"
        )

        val task = provider.createDownloadTask(request)
        val receivedEvents = mutableListOf<DownloadProgressEvent>()
        task.addProgressListener { receivedEvents.add(it) }

        task.start()
        Thread.sleep(150)

        assertEquals(PluginDownloadTask.TaskState.FAILED, task.state)
        assertNotNull(task.errorMessage)
        assertTrue(task.errorMessage.contains("Failed to start") || task.errorMessage.contains("cannot find"))
        assertTrue(receivedEvents.any { it.state == PluginDownloadTask.TaskState.FAILED })
    }

    @Test
    fun testYtDlpSettingsPanelAndPersistence() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        val settingsContributor = PluginManager.getSettingsPanels().first { it.first.manifest.id == "ytdlp-downloader" }.second

        val panel = settingsContributor.createPanel(plugin.context)
        assertNotNull(panel)
        assertTrue(panel is JComponent)

        // Update settings via plugin config directly and save
        plugin.context!!.config.set("executable.path", "C:/tools/yt-dlp.exe")
        plugin.context!!.config.set("format.preference", "bestvideo[height<=1080]+bestaudio/best")
        plugin.context!!.config.set("output.directory", File(dir, "custom_media").absolutePath)
        plugin.context!!.config.setBoolean("extract.audio", true)
        plugin.context!!.config.setInt("timeout.seconds", 7200)
        plugin.context!!.config.save()

        // Verify persisted values
        assertEquals("C:/tools/yt-dlp.exe", plugin.context!!.config.get("executable.path", ""))
        assertEquals("bestvideo[height<=1080]+bestaudio/best", plugin.context!!.config.get("format.preference", ""))
        assertEquals(File(dir, "custom_media").absolutePath, plugin.context!!.config.get("output.directory", ""))
        assertTrue(plugin.context!!.config.getBoolean("extract.audio", false))
        assertEquals(7200, plugin.context!!.config.getInt("timeout.seconds", 0))

        // Modify UI field directly and test settingsContributor.saveSettings()
        val txtExecField = settingsContributor.javaClass.getDeclaredField("txtExecutable").apply { isAccessible = true }
        val txtExec = txtExecField.get(settingsContributor) as javax.swing.JTextField
        txtExec.text = "C:/tools/ytdlp_from_ui.exe"
        settingsContributor.saveSettings()

        assertEquals("C:/tools/ytdlp_from_ui.exe", plugin.context!!.config.get("executable.path", ""))
    }

    @Test
    fun testYtDlpPluginDisableAndUninstall() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        // Disable
        PluginManager.disablePlugin("ytdlp-downloader")
        val disabled = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        assertEquals(PluginStatus.DISABLED, disabled.status)
        assertNull(disabled.instance, "Disabled plugin instance must be cleaned up")

        // Uninstall
        val uninstalled = PluginManager.uninstallPlugin("ytdlp-downloader")
        assertTrue(uninstalled)
        assertFalse(PluginManager.installedPlugins.any { it.manifest.id == "ytdlp-downloader" })
        assertFalse(File(testPluginsDir, "ytdlp-downloader").exists())
    }

    @Test
    fun testCoreDownloadEnginesRemainFunctional() {
        // Verify core download notifications work unimpaired alongside yt-dlp plugin
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted(File(dir, "test.zip").absolutePath, 100L)
            val actions = PluginManager.getContextMenuActions(emptyList())
            assertNotNull(actions)
        }
    }

    @Test
    fun testYtDlpPluginDirectStartDownloadAndShutdown() {
        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        val ytdlpPlugin = plugin.instance as XdmPlugin

        // Configure a non-existent binary so process start fails quickly and safely without network
        plugin.context!!.config.set("executable.path", "C:/non_existent_ytdlp_bin_path.exe")

        val startDownloadMethod = ytdlpPlugin.javaClass.getMethod("startDownload", String::class.java, String::class.java)
        val task = startDownloadMethod.invoke(ytdlpPlugin, "https://www.youtube.com/watch?v=direct_test", "direct.mp4")
        assertNotNull(task)

        // Wait briefly for failure handling
        Thread.sleep(150)

        // Calling shutdown should cleanly cancel tasks and empty active task list
        ytdlpPlugin.shutdown()

        val activeTasksField = ytdlpPlugin.javaClass.getDeclaredField("activeTasks").apply { isAccessible = true }
        val activeTasks = activeTasksField.get(ytdlpPlugin) as List<*>
        assertTrue(activeTasks.isEmpty(), "Active tasks must be cleared upon plugin shutdown")
    }
}
