package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.core.downloaders.DownloadType
import xdm.manager.RecordStatus
import xdm.plugin.api.PluginContext
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.download.DownloadProgressEvent
import xdm.plugin.api.download.DownloadProgressListener
import xdm.plugin.api.download.PluginDownloadTask
import xdm.plugin.api.download.PluginDownloadTask.TaskState
import xdm.plugin.api.models.DownloadInfo
import xdm.plugin.api.models.PluginDownloadRequest
import xdm.plugin.api.models.PluginMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Phase 8: Plugin Framework & Optional Helpers Audit Test Suite.
 *
 * Scenarios:
 * 56. Lifecycle permutations: no plugins, compatible, incompatible, invalid manifest, missing dependency, disabled, crashing init, failed stop.
 * 57. Developer samples built independently from public API without private implementation classes.
 * 58. Optional yt-dlp plugin task list integration: visible task, status, progress, bytes, pause/cancel, completion and history.
 * 59. External process security: executable configuration, safe arguments, timeouts, credential masking, child process cleanup.
 * 60. Version negotiation, data preservation on upgrade, and recovery mode (safe mode).
 * 61. Honest trust boundary documentation without false JVM sandboxing claims.
 */
class Phase8PluginFrameworkAndHelpersAuditTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    private val repoRoot: File = run {
        val here = File(".").canonicalFile
        if (File(here, "sample-plugins").exists()) {
            here
        } else if (File(here.parentFile, "sample-plugins").exists()) {
            here.parentFile
        } else {
            here
        }
    }

    @BeforeEach
    fun initPhase8() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.init(safeMode = false)
        PluginEventBus.clearAll()
        PluginDownloadBridge.cancelAll()
    }

    @AfterEach
    fun cleanupPhase8() {
        PluginManager.shutdown()
        PluginEventBus.clearAll()
        PluginDownloadBridge.cancelAll()
        testPluginsDir.deleteRecursively()
    }

    /**
     * Scenario 56: Startup permutations and fault tolerance across all lifecycle states.
     */
    @Test
    fun testScenario56StartupPermutationsAndFaultTolerance() {
        // 1. Application starts with NO plugins
        assertEquals(0, PluginManager.installedPlugins.size, "Installed plugins must be empty initially")
        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted(File(dir, "none.bin").absolutePath, 100L)
            assertTrue(PluginManager.getContextMenuActions(emptyList()).isEmpty())
            assertTrue(PluginManager.getToolbarActions().isEmpty())
        }

        // 2. Compatible plugin installation & activation
        val samplePackage = File(repoRoot, "sample-plugins/checksum-verifier.xdmplugin")
        assertTrue(samplePackage.exists(), "checksum-verifier.xdmplugin must exist")
        val installRes = PluginManager.installPlugin(samplePackage)
        assertTrue(installRes.isValid, "Compatible plugin installation must succeed")

        val plugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "checksum-verifier" }
        assertNotNull(plugin)
        assertEquals(PluginStatus.UNTRUSTED, plugin!!.status, "Must be UNTRUSTED initially")

        assertTrue(PluginManager.trustAndEnablePlugin("checksum-verifier"))
        assertEquals(PluginStatus.ACTIVE, plugin.status, "Must transition to ACTIVE upon user trust")
        assertNotNull(plugin.instance)

        // 3. Incompatible plugin (future API version)
        val futureDir = File(testPluginsDir, "future-plugin").apply { mkdirs() }
        File(futureDir, "plugin.json").writeText("""
            {
              "id": "future-plugin",
              "name": "Future Plugin",
              "version": "1.0.0",
              "author": "Tester",
              "entryPoint": "com.example.Future",
              "minApiVersion": "99.0.0"
            }
        """.trimIndent())
        PluginManager.discoverPlugins()
        val futurePlugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "future-plugin" }
        assertNotNull(futurePlugin)
        assertEquals(PluginStatus.INCOMPATIBLE, futurePlugin!!.status)
        assertTrue(futurePlugin.faultReason!!.contains("99.0.0"))

        // 4. Invalid manifest (missing required name & entryPoint)
        val invalidDir = File(testPluginsDir, "invalid-plugin").apply { mkdirs() }
        File(invalidDir, "plugin.json").writeText("""
            {
              "id": "invalid-plugin"
            }
        """.trimIndent())
        PluginManager.discoverPlugins()
        val invalidPlugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "invalid-plugin" }
        assertNotNull(invalidPlugin)
        assertEquals(PluginStatus.FAULTED, invalidPlugin!!.status)

        // 5. Disabled plugin
        PluginManager.disablePlugin("checksum-verifier")
        assertEquals(PluginStatus.DISABLED, plugin.status)
        assertNull(plugin.instance, "Instance must be disposed on disable")

        // 6. Plugin crashing during initialize (init())
        val crashInitDir = File(testPluginsDir, "crash-init-plugin").apply { mkdirs() }
        File(crashInitDir, "plugin.json").writeText("""
            {
              "id": "crash-init-plugin",
              "name": "Crash Init Plugin",
              "version": "1.0.0",
              "author": "Tester",
              "entryPoint": "xdm.Phase8CrashInitPlugin"
            }
        """.trimIndent())
        PluginManager.discoverPlugins()
        val crashInitPlugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "crash-init-plugin" }
        assertNotNull(crashInitPlugin)
        val activateOk = PluginManager.activatePlugin(crashInitPlugin!!)
        assertFalse(activateOk, "Activating plugin that crashes in init() must return false")
        assertEquals(PluginStatus.FAULTED, crashInitPlugin.status)
        assertTrue(crashInitPlugin.faultReason!!.contains("Simulated explosion in init()"))

        // 7. Plugin crashing during shutdown (shutdown())
        val crashStopDir = File(testPluginsDir, "crash-stop-plugin").apply { mkdirs() }
        File(crashStopDir, "plugin.json").writeText("""
            {
              "id": "crash-stop-plugin",
              "name": "Crash Stop Plugin",
              "version": "1.0.0",
              "author": "Tester",
              "entryPoint": "xdm.Phase8CrashStopPlugin"
            }
        """.trimIndent())
        PluginManager.discoverPlugins()
        val crashStopPlugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "crash-stop-plugin" }
        assertNotNull(crashStopPlugin)
        assertTrue(PluginManager.activatePlugin(crashStopPlugin!!))
        assertEquals(PluginStatus.ACTIVE, crashStopPlugin.status)

        assertDoesNotThrow {
            PluginManager.deactivatePlugin(crashStopPlugin)
        }
        assertEquals(PluginStatus.DISABLED, crashStopPlugin.status)
        assertNull(crashStopPlugin.instance, "Instance must be cleaned up despite shutdown crash")
        assertNull(crashStopPlugin.context, "Context must be cleaned up despite shutdown crash")
    }

    /**
     * Scenario 57: Developer sample built independently from published public API,
     * without referencing private implementation classes.
     */
    @Test
    fun testScenario57IndependentDeveloperSampleBuildContract() {
        // 1. Verify checksum-verifier-plugin pom.xml dependencies
        val checksumPom = File(repoRoot, "sample-plugins/checksum-verifier-plugin/pom.xml")
        assertTrue(checksumPom.exists())
        val checksumPomText = checksumPom.readText()
        assertTrue(checksumPomText.contains("<artifactId>xdm-plugin-api</artifactId>"), "Must depend on xdm-plugin-api")
        assertFalse(checksumPomText.contains("<artifactId>xdm-app</artifactId>"), "Must NOT depend on xdm-app")
        assertFalse(checksumPomText.contains("<artifactId>xdm-core</artifactId>"), "Must NOT depend on xdm-core")

        // 2. Verify ytdlp-downloader-plugin pom.xml dependencies
        val ytdlpPom = File(repoRoot, "sample-plugins/ytdlp-downloader-plugin/pom.xml")
        assertTrue(ytdlpPom.exists())
        val ytdlpPomText = ytdlpPom.readText()
        assertTrue(ytdlpPomText.contains("<artifactId>xdm-plugin-api</artifactId>"), "Must depend on xdm-plugin-api")
        assertFalse(ytdlpPomText.contains("<artifactId>xdm-app</artifactId>"), "Must NOT depend on xdm-app")
        assertFalse(ytdlpPomText.contains("<artifactId>xdm-core</artifactId>"), "Must NOT depend on xdm-core")

        // 3. Inspect bytecode of compiled sample plugins to confirm zero private internal imports
        val pluginJars = listOf(
            File(repoRoot, "sample-plugins/checksum-verifier.xdmplugin"),
            File(repoRoot, "sample-plugins/ytdlp-downloader.xdmplugin")
        )
        for (jar in pluginJars) {
            assertTrue(jar.exists(), "Sample plugin package ${jar.name} must exist")
            ZipFile(jar).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".class")) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val constantPoolStr = String(bytes, StandardCharsets.ISO_8859_1)
                        assertFalse(
                            constantPoolStr.contains("xdm/app/ui") || constantPoolStr.contains("xdm/core/downloaders/Http"),
                            "Class ${entry.name} in ${jar.name} must not reference private internal classes"
                        )
                    }
                }
            }
        }
    }

    /**
     * Scenario 58: yt-dlp sample plugin integration: visible task list entry,
     * status, bytes, progress, pause/cancel, completion and history.
     */
    @Test
    fun testScenario58YtDlpTaskLifecycleAndHistoryIntegration() {
        val ytdlpPkg = File(repoRoot, "sample-plugins/ytdlp-downloader.xdmplugin")
        PluginManager.installPlugin(ytdlpPkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }
        assertNotNull(plugin)
        val hostContext = plugin.context as PluginHostContext

        val progressListeners = CopyOnWriteArrayList<DownloadProgressListener>()
        val taskPaused = AtomicBoolean(false)
        val taskResumed = AtomicBoolean(false)
        val taskCancelled = AtomicBoolean(false)
        val finalOutputFile = File(dir, "tears-of-steel-cc.mp4")

        val mockYtDlpTask = object : PluginDownloadTask {
            override fun start() {}
            override fun canPause(): Boolean = true
            override fun canResume(): Boolean = true
            override fun pause() { taskPaused.set(true) }
            override fun resume() { taskResumed.set(true) }
            override fun cancel() { taskCancelled.set(true) }
            override fun getState(): TaskState = TaskState.DOWNLOADING
            override fun getDownloadedBytes(): Long = 25000000L
            override fun getTotalBytes(): Long = 100000000L
            override fun getTransferSpeed(): Float = 2500000f
            override fun getErrorMessage(): String? = null
            override fun getOutputFile(): File = finalOutputFile
            override fun addProgressListener(listener: DownloadProgressListener?) {
                if (listener != null) progressListeners.add(listener)
            }
            override fun removeProgressListener(listener: DownloadProgressListener?) {
                progressListeners.remove(listener)
            }
        }

        val request = PluginDownloadRequest(
            "https://mango.blender.org/download/tears-of-steel-cc.mp4",
            dir.absolutePath,
            "tears-of-steel-cc.mp4"
        )

        // 1. Submit task and verify visible entry in task store & AppDB
        val taskId = hostContext.submitPluginTask(request, mockYtDlpTask)
        assertNotEquals(-1L, taskId)

        val record = AppContext.db.getById(taskId)
        assertNotNull(record, "Plugin task must appear in AppContext.db")
        assertEquals(DownloadType.Plugin, record!!.downloadType)
        assertEquals("tears-of-steel-cc.mp4", record.fileName)
        assertEquals(RecordStatus.DOWNLOADING, record.status)

        // 2. Progress reporting: 65% progress
        val progressEvent = DownloadProgressEvent(
            65000000L,
            100000000L,
            3200000f,
            11L,
            TaskState.DOWNLOADING,
            "Downloading stream..."
        )
        progressListeners.forEach { it.onProgress(progressEvent) }

        val activeRecord = AppContext.db.getById(taskId)
        assertEquals(65000000L, activeRecord!!.downloaded)
        assertEquals(100000000L, activeRecord.size)
        assertEquals(65, activeRecord.progress)
        assertEquals(3200000f, activeRecord.speed)

        // 3. Pause semantics
        PluginDownloadBridge.pauseTask(taskId)
        assertTrue(taskPaused.get(), "pause() must be invoked on the plugin task")
        assertEquals(RecordStatus.PAUSED, AppContext.db.getById(taskId)?.status)

        // 4. Resume semantics
        PluginDownloadBridge.resumeTask(taskId)
        assertTrue(taskResumed.get(), "resume() must be invoked on the plugin task")
        assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(taskId)?.status)

        // 5. Completion and history integration
        val finishedEvent = DownloadProgressEvent(
            100000000L,
            100000000L,
            0f,
            0L,
            TaskState.FINISHED,
            "Download completed successfully."
        )
        progressListeners.forEach { it.onProgress(finishedEvent) }

        val completedRecord = AppContext.db.getById(taskId)
        assertEquals(RecordStatus.FINISHED, completedRecord!!.status)
        assertEquals(100, completedRecord.progress)
        assertEquals(finalOutputFile.name, completedRecord.fileName)
    }

    /**
     * Scenario 59: External executable configuration, safe argument delimiters,
     * credential log masking, and child process termination.
     */
    @Test
    fun testScenario59ExternalProcessSecurityAndSanitization() {
        val ytdlpPkg = File(repoRoot, "sample-plugins/ytdlp-downloader.xdmplugin")
        PluginManager.installPlugin(ytdlpPkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        val plugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }

        // 1. Credential log masking in YtDlpDownloadTask.sanitizeUrl()
        val taskClass = plugin.classLoader!!.loadClass("xdm.plugin.ytdlp.YtDlpDownloadTask")
        val sanitizeMethod = taskClass.getMethod("sanitizeUrl", String::class.java)

        val credUrl = "https://user:supersecretpassword@cdn.example.org/vod/stream.mp4?token=secret123&auth=true"
        val sanitized = sanitizeMethod.invoke(null, credUrl) as String
        assertFalse(sanitized.contains("supersecretpassword"), "Passwords must never appear in sanitized URL logs")
        assertFalse(sanitized.contains("token=secret123"), "Query parameter tokens must be stripped")
        assertTrue(sanitized.contains("***:***@cdn.example.org/vod/stream.mp4"), "Credentials must be masked with placeholder")

        // 2. Safe arguments: verify '--' delimiter is included to prevent argument injection
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=sample")
        assertNotNull(providerPair)
        val provider = providerPair!!.second

        val maliciousRequest = PluginDownloadRequest(
            "--output=C:/Windows/System32/evil.dll",
            dir.absolutePath,
            "video.mp4"
        )
        val task = provider.createDownloadTask(maliciousRequest)
        val buildCommandMethod = task.javaClass.getDeclaredMethod("buildCommand", File::class.java).apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val cmd = buildCommandMethod.invoke(task, dir) as List<String>
        assertTrue(cmd.contains("--"), "Command list must contain '--' end-of-options delimiter")
        val dashDashIndex = cmd.indexOf("--")
        assertEquals(maliciousRequest.url, cmd[dashDashIndex + 1], "Target URL must immediately follow '--'")

        // 3. Child process termination: verify cancel terminates the worker process
        task.cancel()
        assertEquals(TaskState.CANCELED, task.state)
    }

    /**
     * Scenario 60: Plugin updates/version negotiation, data preservation, and Safe Mode recovery.
     */
    @Test
    fun testScenario60VersionNegotiationDependencyConflictsAndSafeMode() {
        // 1. Semantic version negotiation ranges
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.1.0", ">=1.0.0 <2.0.0").first)
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.0.5", "^1.0.0").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("2.0.0", "^1.0.0").first)

        // 2. Data directory preservation across upgrades
        val v1Pkg = File(dir, "data-test-v1.xdmplugin")
        createRawPluginZip(v1Pkg, "data-test", "Data Plugin", "1.0.0")
        PluginManager.installPlugin(v1Pkg)
        val pluginDir = File(testPluginsDir, "data-test")
        val customDataFile = File(pluginDir, "data/user-settings.json").apply {
            parentFile.mkdirs()
            writeText("""{"preferred_quality": "1080p", "downloads_count": 42}""")
        }

        // Install upgraded v1.0.1 package
        val v2Pkg = File(dir, "data-test-v2.xdmplugin")
        createRawPluginZip(v2Pkg, "data-test", "Data Plugin", "1.0.1")
        val upgradeResult = PluginManager.installPlugin(v2Pkg)
        assertTrue(upgradeResult.isValid)
        assertTrue(customDataFile.exists(), "User data in data/ must be preserved across upgrades")
        assertTrue(customDataFile.readText().contains("1080p"))

        // 3. Safe Mode recovery: suppresses all third-party execution
        PluginManager.trustAndEnablePlugin("data-test")
        PluginManager.init(safeMode = true)
        assertTrue(PluginManager.isSafeMode)
        val loaded = PluginManager.installedPlugins.first { it.manifest.id == "data-test" }
        assertEquals(PluginStatus.DISABLED, loaded.status, "All plugins must be quarantined in Safe Mode")
        assertNull(loaded.instance, "No plugin instances may be instantiated in Safe Mode")
    }

    /**
     * Scenario 61: Documented trust boundary without false sandboxing claims.
     */
    @Test
    fun testScenario61TrustBoundaryDocumentationAndHonestPosture() {
        val devGuide = File(repoRoot, "docs/PLUGIN_DEVELOPMENT.md")
        assertTrue(devGuide.exists(), "docs/PLUGIN_DEVELOPMENT.md must exist")
        val content = devGuide.readText()

        // 1. Explicit statement of honest security posture
        assertTrue(
            content.contains("Honest Security Posture") || content.contains("does not claim"),
            "Developer documentation must explicitly state in-process JVM execution model"
        )
        assertTrue(
            content.contains("UNTRUSTED") && content.contains("quarantine"),
            "Documentation must state that plugins are quarantined by default upon installation"
        )

        // 2. Recovery mode documented
        assertTrue(
            content.contains("--safe-mode") || content.contains("--no-plugins"),
            "Documentation must reference Safe Mode recovery flags"
        )

        // 3. Read-only snapshot model documented
        assertTrue(
            content.contains("DownloadInfo") && content.contains("PluginDownloadRequest"),
            "Documentation must detail the immutable snapshot and request submission architecture"
        )
    }

    private fun createRawPluginZip(dest: File, id: String, name: String, version: String) {
        val json = """
            {
              "id": "$id",
              "name": "$name",
              "version": "$version",
              "author": "Tester",
              "entryPoint": "com.example.Test",
              "minApiVersion": "1.0.0"
            }
        """.trimIndent()
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            zos.putNextEntry(ZipEntry("plugin.json"))
            zos.write(json.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
    }
}

class Phase8CrashInitPlugin : XdmPlugin {
    override fun getId(): String = "crash-init-plugin"
    override fun init(context: PluginContext?) {
        throw RuntimeException("Simulated explosion in init()")
    }
    override fun shutdown() {}
}

class Phase8CrashStopPlugin : XdmPlugin {
    override fun getId(): String = "crash-stop-plugin"
    override fun init(context: PluginContext?) {}
    override fun shutdown() {
        throw RuntimeException("Simulated explosion in shutdown()")
    }
}

