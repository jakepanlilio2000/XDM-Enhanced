package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.plugin.api.download.DownloadProgressEvent
import xdm.plugin.api.download.DownloadProgressListener
import xdm.plugin.api.download.PluginDownloadTask
import xdm.plugin.api.download.PluginDownloadTask.TaskState
import xdm.plugin.api.models.PluginDownloadRequest
import xdm.plugin.api.models.PluginMetadata
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginApiV2AndTaskAdapterTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initPluginEnv() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "test-plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.init(safeMode = false)
        PluginEventBus.clearAll()
        PluginDownloadBridge.cancelAll()
    }

    @AfterEach
    fun cleanPluginEnv() {
        PluginManager.shutdown()
        PluginEventBus.clearAll()
        PluginDownloadBridge.cancelAll()
        testPluginsDir.deleteRecursively()
    }

    @Test
    fun testSemanticVersionRangeEvaluation() {
        // Bracket / interval notation
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.1.0", "[1.0.0, 2.0.0)").first)
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.0.0", "[1.0.0, 2.0.0)").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("2.0.0", "[1.0.0, 2.0.0)").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("0.9.0", "[1.0.0, 2.0.0)").first)

        // Comparison operators
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.1.0", ">=1.0.0 <=1.1.0").first)
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.0.5", ">=1.0.0 <=1.1.0").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("1.2.0", ">=1.0.0 <=1.1.0").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("0.9.9", ">=1.0.0 <=1.1.0").first)

        // Caret notation (compatible with major version)
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.1.0", "^1.0.0").first)
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.9.9", "^1.0.0").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("2.0.0", "^1.0.0").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("0.9.9", "^1.0.0").first)

        // Tilde notation (patch updates only)
        assertTrue(PluginManifestValidator.evaluateVersionRange("1.0.5", "~1.0.0").first)
        assertFalse(PluginManifestValidator.evaluateVersionRange("1.1.0", "~1.0.0").first)

        // Min / max version combined validation
        val manifestLegacy = PluginManifest(
            id = "legacy-plugin",
            name = "Legacy Plugin",
            version = "1.0.0",
            author = "Dev",
            entryPoint = "com.example.Legacy",
            minApiVersion = "1.0.0"
        )
        val validLegacy = PluginManifestValidator.validateManifest(manifestLegacy)
        assertTrue(validLegacy.isValid, "Legacy plugin with minApiVersion 1.0.0 should be valid on 1.1.0: ${validLegacy.errors}")

        val manifestIncompatible = PluginManifest(
            id = "future-plugin",
            name = "Future Plugin",
            version = "1.0.0",
            author = "Dev",
            entryPoint = "com.example.Future",
            minApiVersion = "2.0.0"
        )
        val validFuture = PluginManifestValidator.validateManifest(manifestIncompatible)
        assertFalse(validFuture.isValid, "Future plugin requiring API 2.0.0 must be rejected on API 1.1.0")
        assertTrue(validFuture.errors.any { it.contains("requires API version") || it.contains("Incompatible API version") })
    }

    @Test
    fun testIncompatiblePluginStatusAndDiagnostics() {
        val pluginDir = File(testPluginsDir, "future-plugin").apply { mkdirs() }
        val manifestFile = File(pluginDir, "plugin.json")
        manifestFile.writeText(
            """
            {
              "id": "future-plugin",
              "name": "Future Plugin",
              "version": "1.0.0",
              "author": "XDM Team",
              "entryPoint": "com.example.FuturePlugin",
              "minApiVersion": "9.9.9"
            }
            """.trimIndent()
        )

        PluginManager.discoverPlugins()
        val plugin = PluginManager.installedPlugins.find { it.manifest.id == "future-plugin" }
        assertNotNull(plugin, "Incompatible plugin should be discovered and recorded")
        assertEquals(PluginStatus.INCOMPATIBLE, plugin!!.status, "Plugin status should be INCOMPATIBLE")
        assertNotNull(plugin.faultReason, "Incompatible plugin should have a diagnostic faultReason")
        assertTrue(plugin.faultReason!!.contains("9.9.9"), "Fault reason should mention 9.9.9: ${plugin.faultReason}")
    }

    @Test
    fun testDataPreservationOnPluginUpgrade() {
        // 1. Create v1.0.0 package
        val v1Package = File(dir, "counter-v1.xdmplugin")
        createPluginZip(
            v1Package,
            id = "counter-plugin",
            name = "Counter Plugin",
            version = "1.0.0",
            entryPoint = "com.example.Counter"
        )

        // 2. Install v1.0.0
        val installRes1 = PluginManager.installPlugin(v1Package)
        assertTrue(installRes1.isValid, "Installation of v1 should succeed: ${installRes1.errors}")
        val pluginDir = File(testPluginsDir, "counter-plugin")
        val dataDir = File(pluginDir, "data").apply { mkdirs() }
        val configFile = File(dataDir, "config.properties")
        configFile.writeText("user.custom.value=42\nlast.sync=2026-11-10\n")

        // 3. Create v1.0.1 package
        val v2Package = File(dir, "counter-v2.xdmplugin")
        createPluginZip(
            v2Package,
            id = "counter-plugin",
            name = "Counter Plugin",
            version = "1.0.1",
            entryPoint = "com.example.Counter"
        )

        // 4. Upgrade to v1.0.1
        val installRes2 = PluginManager.installPlugin(v2Package)
        assertTrue(installRes2.isValid, "Upgrade to v1.0.1 should succeed: ${installRes2.errors}")

        // 5. Verify dataDir and configuration are preserved
        assertTrue(configFile.exists(), "config.properties should be preserved across plugin upgrade")
        val content = configFile.readText()
        assertTrue(content.contains("user.custom.value=42"))
        assertTrue(content.contains("last.sync=2026-11-10"))
    }

    @Test
    fun testEventBusSubscriptionPublishAndIsolation() {
        val receivedEvents = CopyOnWriteArrayList<String>()
        val callCount = AtomicInteger(0)

        // Normal subscriber
        PluginEventBus.subscribe<String>("good-plugin", "media.found") { topic, data ->
            receivedEvents.add("$topic:$data")
            callCount.incrementAndGet()
        }

        // Failing subscriber (tests exception isolation)
        PluginEventBus.subscribe<String>("bad-plugin", "media.found") { _, _ ->
            throw RuntimeException("Intentional crash in bad plugin subscriber")
        }

        // Another normal subscriber
        PluginEventBus.subscribe<String>("another-plugin", "media.found") { topic, data ->
            receivedEvents.add("another:$topic:$data")
        }

        // Publish event
        assertDoesNotThrow {
            PluginEventBus.publish("media.found", "https://example.com/video.mp4")
        }

        assertEquals(1, callCount.get())
        assertTrue(receivedEvents.contains("media.found:https://example.com/video.mp4"))
        assertTrue(receivedEvents.contains("another:media.found:https://example.com/video.mp4"))

        // Test lifecycle cleanup
        PluginEventBus.clearForPlugin("good-plugin")
        receivedEvents.clear()

        PluginEventBus.publish("media.found", "https://example.com/second.mp4")
        assertFalse(receivedEvents.any { it.startsWith("media.found:") })
        assertTrue(receivedEvents.contains("another:media.found:https://example.com/second.mp4"))
    }

    @Test
    fun testPluginTaskAdapterBridgeLifecycle() {
        val manifest = PluginManifest(
            id = "test-downloader",
            name = "Test Downloader",
            version = "1.0.0",
            author = "Tester",
            entryPoint = "com.example.TestDownloader",
            capabilities = listOf("TASK_ADAPTER")
        )
        val loadedPlugin = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "test-downloader").apply { mkdirs() },
            classLoader = null,
            instance = object : xdm.plugin.api.XdmPlugin {
                override fun getId(): String = "test-downloader"
                override fun init(context: xdm.plugin.api.PluginContext) {}
                override fun shutdown() {}
            },
            status = PluginStatus.ACTIVE
        )
        val hostMetadata = PluginMetadata(
            manifest.id,
            manifest.name,
            manifest.version,
            manifest.author,
            manifest.description,
            manifest.minAppVersion,
            manifest.minApiVersion,
            manifest.entryPoint,
            manifest.capabilities.toSet(),
            manifest.dependencies,
            manifest.license,
            manifest.homepage,
            loadedPlugin.sha256,
            loadedPlugin.isBuiltIn
        )
        val hostContext = PluginHostContext(
            plugin = loadedPlugin,
            hostMetadata = hostMetadata
        )

        val taskStarted = AtomicBoolean(false)
        val taskPaused = AtomicBoolean(false)
        val taskResumed = AtomicBoolean(false)
        val taskCancelled = AtomicBoolean(false)
        val listeners = CopyOnWriteArrayList<DownloadProgressListener>()

        val dummyTask = object : PluginDownloadTask {
            override fun start() {
                taskStarted.set(true)
            }

            override fun canPause(): Boolean = true
            override fun canResume(): Boolean = true

            override fun pause() {
                taskPaused.set(true)
            }

            override fun resume() {
                taskResumed.set(true)
            }

            override fun cancel() {
                taskCancelled.set(true)
            }

            override fun getState(): TaskState = TaskState.DOWNLOADING
            override fun getDownloadedBytes(): Long = 500L
            override fun getTotalBytes(): Long = 1000L
            override fun getTransferSpeed(): Float = 250f
            override fun getErrorMessage(): String? = null
            override fun getOutputFile(): File? = null

            override fun addProgressListener(listener: DownloadProgressListener?) {
                if (listener != null) listeners.add(listener)
            }

            override fun removeProgressListener(listener: DownloadProgressListener?) {
                listeners.remove(listener)
            }
        }

        val request = PluginDownloadRequest(
            "https://example.com/file.zip",
            dir.absolutePath,
            "file.zip"
        )

        // 1. Submit plugin task through context
        val taskId = hostContext.submitPluginTask(request, dummyTask)
        assertNotEquals(-1L, taskId, "Task ID must not be -1 on successful submission")
        assertNotEquals(0L, taskId, "Task ID must not be 0 on successful submission")
        assertTrue(taskStarted.get(), "Task start() should be invoked on submission")

        // 2. Verify task is registered in downloader store and AppDB
        val record = AppContext.downloader.store.getById(taskId)
        assertNotNull(record, "Download record must be registered in downloader store")
        assertEquals(DownloadType.Plugin, record!!.downloadType)
        assertEquals("file.zip", record.fileName)
        assertEquals(RecordStatus.DOWNLOADING, record.status)

        // 3. Emit progress updates and verify record update
        val progressEvent = DownloadProgressEvent(
            750L,
            1000L,
            500f,
            2L,
            TaskState.DOWNLOADING,
            "Downloading..."
        )
        listeners.forEach { it.onProgress(progressEvent) }

        val updatedRecord = AppContext.db.getById(taskId)
        assertNotNull(updatedRecord)
        assertEquals(750L, updatedRecord!!.downloaded)
        assertEquals(1000L, updatedRecord.size)
        assertEquals(75, updatedRecord.progress)
        assertEquals(500f, updatedRecord.speed)

        // 4. Test Pause
        PluginDownloadBridge.pauseTask(taskId)
        assertTrue(taskPaused.get(), "pause() should be called on the plugin task")
        assertEquals(RecordStatus.PAUSED, AppContext.db.getById(taskId)?.status)

        // 5. Test Resume
        PluginDownloadBridge.resumeTask(taskId)
        assertTrue(taskResumed.get(), "resume() should be called on the plugin task")
        assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(taskId)?.status)

        // 6. Test Completion
        val completeEvent = DownloadProgressEvent(
            1000L,
            1000L,
            0f,
            0L,
            TaskState.FINISHED,
            "Completed"
        )
        listeners.forEach { it.onProgress(completeEvent) }
        val completedRecord = AppContext.db.getById(taskId)
        assertNotNull(completedRecord)
        assertEquals(RecordStatus.FINISHED, completedRecord!!.status)
        assertEquals(100, completedRecord.progress)

        // 7. Test Delete
        PluginDownloadBridge.deleteTask(taskId, fromDisk = false)
        assertNull(AppContext.db.getById(taskId), "Task should be removed from database on delete")
        assertNull(AppContext.downloader.store.getById(taskId), "Task should be removed from store on delete")
    }

    @Test
    fun testOutputFileRenamePropagatesOnComplete() {
        val manifest = PluginManifest(
            id = "rename-tester",
            name = "Rename Tester",
            version = "1.0.0",
            author = "Tester",
            entryPoint = "com.example.RenameTester",
            capabilities = listOf("TASK_ADAPTER")
        )
        val loadedPlugin = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "rename-tester").apply { mkdirs() },
            classLoader = null,
            instance = object : xdm.plugin.api.XdmPlugin {
                override fun getId(): String = "rename-tester"
                override fun init(context: xdm.plugin.api.PluginContext) {}
                override fun shutdown() {}
            },
            status = PluginStatus.ACTIVE
        )
        val hostMetadata = PluginMetadata(
            manifest.id, manifest.name, manifest.version, manifest.author,
            manifest.description, manifest.minAppVersion, manifest.minApiVersion,
            manifest.entryPoint, manifest.capabilities.toSet(), manifest.dependencies,
            manifest.license, manifest.homepage, loadedPlugin.sha256, loadedPlugin.isBuiltIn
        )
        val hostContext = PluginHostContext(loadedPlugin, hostMetadata)

        val listeners = CopyOnWriteArrayList<DownloadProgressListener>()
        val finalFile = File(dir, "resolved-video-title.mp4")

        val dummyTask = object : PluginDownloadTask {
            override fun start() {}
            override fun pause() {}
            override fun resume() {}
            override fun cancel() {}
            override fun getState(): TaskState = TaskState.DOWNLOADING
            override fun getDownloadedBytes(): Long = 500L
            override fun getTotalBytes(): Long = 1000L
            override fun getTransferSpeed(): Float = 250f
            override fun getErrorMessage(): String? = null
            override fun getOutputFile(): File = finalFile
            override fun addProgressListener(listener: DownloadProgressListener?) {
                if (listener != null) listeners.add(listener)
            }
            override fun removeProgressListener(listener: DownloadProgressListener?) {
                listeners.remove(listener)
            }
        }

        val request = PluginDownloadRequest(
            "https://example.com/watch?v=123",
            dir.absolutePath,
            "template.mp4"
        )

        val taskId = hostContext.submitPluginTask(request, dummyTask)
        assertNotEquals(-1L, taskId)

        val completeEvent = DownloadProgressEvent(1000L, 1000L, 0f, 0L, TaskState.FINISHED, "Finished")
        listeners.forEach { it.onProgress(completeEvent) }

        val rec = AppContext.db.getById(taskId)
        assertNotNull(rec)
        assertEquals("resolved-video-title.mp4", rec!!.fileName, "Record fileName should be updated from task.outputFile")
        val meta = AppContext.taskInfoDB.getPluginTask(taskId)
        assertNotNull(meta)
        assertEquals("resolved-video-title.mp4", meta!!.fileName, "TaskInfoDB fileName should be updated from task.outputFile")
    }

    @Test
    fun testResumeAfterRestartRecreatesPluginTask() {
        val restartedTaskStarted = AtomicBoolean(false)
        val dummyProvider = object : xdm.plugin.api.download.DownloadProvider {
            override fun getName(): String = "Test Provider"
            override fun canHandle(url: String?): Boolean = true
            override fun createDownloadTask(request: PluginDownloadRequest?): PluginDownloadTask {
                return object : PluginDownloadTask {
                    override fun start() { restartedTaskStarted.set(true) }
                    override fun pause() {}
                    override fun resume() {}
                    override fun cancel() {}
                    override fun getState(): TaskState = TaskState.DOWNLOADING
                    override fun getDownloadedBytes(): Long = 100L
                    override fun getTotalBytes(): Long = 500L
                    override fun getTransferSpeed(): Float = 50f
                    override fun getErrorMessage(): String? = null
                    override fun getOutputFile(): File? = null
                    override fun addProgressListener(listener: DownloadProgressListener?) {}
                    override fun removeProgressListener(listener: DownloadProgressListener?) {}
                }
            }
        }

        val manifest = PluginManifest(
            id = "resumable-plugin",
            name = "Resumable Plugin",
            version = "1.0.0",
            author = "Tester",
            entryPoint = "com.example.Resumable",
            capabilities = listOf("TASK_ADAPTER", "DOWNLOAD_PROVIDER")
        )
        val loadedPlugin = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "resumable-plugin").apply { mkdirs() },
            classLoader = null,
            instance = object : xdm.plugin.api.XdmPlugin {
                override fun getId(): String = "resumable-plugin"
                override fun init(context: xdm.plugin.api.PluginContext) {
                    context.registerDownloadProvider(dummyProvider)
                }
                override fun shutdown() {}
            },
            status = PluginStatus.ACTIVE
        )
        val hostMetadata = PluginMetadata(
            manifest.id, manifest.name, manifest.version, manifest.author,
            manifest.description, manifest.minAppVersion, manifest.minApiVersion,
            manifest.entryPoint, manifest.capabilities.toSet(), manifest.dependencies,
            manifest.license, manifest.homepage, loadedPlugin.sha256, loadedPlugin.isBuiltIn
        )
        val hostContext = PluginHostContext(loadedPlugin, hostMetadata)
        hostContext.registerDownloadProvider(dummyProvider)
        loadedPlugin.context = hostContext
        PluginManager.installedPlugins.add(loadedPlugin)

        // Seed a paused plugin task into AppDB and TaskInfoDB (as if restored on app launch)
        val taskId = 88889999L
        val taskInfo = xdm.core.downloaders.PluginDownloadTaskInfo(
            id = taskId,
            pluginId = "resumable-plugin",
            url = "https://example.com/resumable-file.bin",
            fileName = "resumable-file.bin",
            targetFolder = dir.absolutePath,
            categoryId = null,
            totalSize = 500L
        )
        AppContext.taskInfoDB.savePluginTask(taskInfo)
        val record = DbRecord(
            id = taskId,
            size = 500L,
            downloaded = 100L,
            progress = 20,
            date = System.currentTimeMillis(),
            fileName = "resumable-file.bin",
            eta = 0L,
            speed = 0f,
            status = RecordStatus.PAUSED,
            selected = false,
            downloadType = DownloadType.Plugin,
            categoryId = null
        )
        AppContext.db.add(record)
        AppContext.downloader.store.add(record)

        // Now invoke resumeTask(taskId) when activeTasks has no active entry in memory
        assertNull(PluginDownloadBridge.getTask(taskId), "Task should not be in memory prior to resume")
        PluginDownloadBridge.resumeTask(taskId)

        assertTrue(restartedTaskStarted.get(), "New task should be instantiated and start() invoked on resume")
        assertNotNull(PluginDownloadBridge.getTask(taskId), "Task should now be cached in activeTasks")
        assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(taskId)?.status)
    }

    @Test
    fun testUnsupportedPauseDistinctionVsCancellation() {
        val manifest = PluginManifest(
            id = "unsupported-pause-plugin",
            name = "No Pause Plugin",
            version = "1.0.0",
            author = "Tester",
            entryPoint = "com.example.NoPausePlugin",
            capabilities = listOf("TASK_ADAPTER")
        )
        val loadedPlugin = LoadedPlugin(
            manifest = manifest,
            pluginDir = File(testPluginsDir, "unsupported-pause-plugin").apply { mkdirs() },
            classLoader = null,
            instance = object : xdm.plugin.api.XdmPlugin {
                override fun getId(): String = "unsupported-pause-plugin"
                override fun init(context: xdm.plugin.api.PluginContext) {}
                override fun shutdown() {}
            },
            status = PluginStatus.ACTIVE
        )
        val hostMetadata = PluginMetadata(
            manifest.id, manifest.name, manifest.version, manifest.author,
            manifest.description, manifest.minAppVersion, manifest.minApiVersion,
            manifest.entryPoint, manifest.capabilities.toSet(), manifest.dependencies,
            manifest.license, manifest.homepage, loadedPlugin.sha256, loadedPlugin.isBuiltIn
        )
        val hostContext = PluginHostContext(
            plugin = loadedPlugin,
            hostMetadata = hostMetadata
        )

        val taskPaused = AtomicBoolean(false)
        val taskCancelled = AtomicBoolean(false)

        val noPauseTask = object : PluginDownloadTask {
            override fun start() {}
            override fun canPause(): Boolean = false
            override fun canResume(): Boolean = false
            override fun pause() {
                taskPaused.set(true)
            }
            override fun resume() {}
            override fun cancel() {
                taskCancelled.set(true)
            }
            override fun getState(): PluginDownloadTask.TaskState = PluginDownloadTask.TaskState.DOWNLOADING
            override fun getDownloadedBytes(): Long = 100L
            override fun getTotalBytes(): Long = 500L
            override fun getTransferSpeed(): Float = 50f
            override fun getErrorMessage(): String? = null
            override fun getOutputFile(): File? = null
        }

        val request = PluginDownloadRequest(
            "https://example.com/stream.m3u8",
            dir.absolutePath,
            "stream.ts"
        )
        val taskId = hostContext.submitPluginTask(request, noPauseTask)
        assertNotEquals(-1L, taskId)

        // Calling pauseTask on a task that does NOT support pause must invoke cancel() on the provider task,
        // distinguishing unsupported pause from a true pause without orphaning the process.
        PluginDownloadBridge.pauseTask(taskId)
        assertFalse(taskPaused.get(), "pause() must NOT be called on a task where canPause() == false")
        assertTrue(taskCancelled.get(), "cancel() must be called to terminate provider execution when pause is unsupported")

        // UI status is PAUSED so the user sees it stopped
        val record = AppContext.db.getById(taskId)
        assertNotNull(record)
        assertEquals(RecordStatus.PAUSED, record!!.status)
    }

    @Test
    fun testUiResolversForPluginType() {
        val rec = DbRecord(
            id = 77771111L,
            size = 2048L,
            downloaded = 1024L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "sample-plugin-media.mkv",
            eta = 5L,
            speed = 200f,
            status = RecordStatus.DOWNLOADING,
            selected = false,
            downloadType = DownloadType.Plugin,
            categoryId = null
        )
        val icon = xdm.app.ui.components.CategoryStyle.createCategoryIcon(
            categoryId = null,
            fileName = rec.fileName,
            downloadType = rec.downloadType,
            size = 36
        )
        assertNotNull(icon, "Category icon for Plugin type must be resolved")
        val label = xdm.app.ui.components.CategoryStyle.displayNameForCategory(null, rec.downloadType)
        assertEquals("Plugin", label, "Category display name for Plugin type must be 'Plugin'")
    }

    private fun createPluginZip(
        dest: File,
        id: String,
        name: String,
        version: String,
        entryPoint: String
    ) {
        val manifestJson = """
            {
              "id": "$id",
              "name": "$name",
              "version": "$version",
              "author": "Developer",
              "entryPoint": "$entryPoint",
              "minApiVersion": "1.0.0"
            }
        """.trimIndent()

        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            zos.putNextEntry(ZipEntry("plugin.json"))
            zos.write(manifestJson.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
    }
}
