package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.app.preflight.ClipboardMonitor
import xdm.app.ui.components.*
import xdm.app.ui.screens.NewDownloadWindow
import xdm.app.utils.RemixIcon
import xdm.app.utils.captureCategory
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import xdm.plugin.api.download.DownloadProgressEvent
import xdm.plugin.api.download.PluginDownloadTask
import xdm.plugin.api.models.PluginDownloadRequest
import java.io.File
import javax.swing.SwingUtilities

class YtDlpDownloadListIntegrationTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File
    private lateinit var loadedPlugin: LoadedPlugin
    private lateinit var fakeYtDlpExe: File

    @BeforeEach
    fun initYtDlpIntegration() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginManager.init(safeMode = false)

        val pkg = resolveYtDlpPlugin()
        PluginManager.installPlugin(pkg)
        PluginManager.trustAndEnablePlugin("ytdlp-downloader")

        loadedPlugin = PluginManager.installedPlugins.first { it.manifest.id == "ytdlp-downloader" }

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        fakeYtDlpExe = File(dir, if (isWindows) "fake_ytdlp.exe" else "fake_ytdlp.sh")
        compileFakeYtDlp(fakeYtDlpExe, isWindows)

        loadedPlugin.context!!.config.set("executable.path", fakeYtDlpExe.absolutePath)
        loadedPlugin.context!!.config.save()
    }

    @AfterEach
    fun cleanupYtDlpIntegration() {
        PluginManager.shutdown()
        PluginEventBus.clearAll()
        PluginDownloadBridge.cancelAll()
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
            ?: throw IllegalStateException("Cannot find ytdlp-downloader.xdmplugin")
    }

    private fun compileFakeYtDlp(targetFile: File, isWindows: Boolean) {
        if (isWindows) {
            val csFile = File(dir, "FakeYtDlp.cs").apply {
                writeText(
                    """
                    using System;
                    using System.IO;

                    class FakeYtDlp {
                        static int Main(string[] args) {
                            if (args.Length > 0 && args[0] == "--version") {
                                Console.WriteLine("2026.03.01");
                                return 0;
                            }
                            string allArgs = string.Join(" ", args);
                            if (allArgs.IndexOf("error_fail_video", StringComparison.OrdinalIgnoreCase) >= 0) {
                                Console.WriteLine("ERROR: [youtube] error_fail_video: Video unavailable. This video is private.");
                                return 1;
                            }
                            string outDir = AppDomain.CurrentDomain.BaseDirectory;
                            Console.WriteLine("[download] Destination: " + Path.Combine(outDir, "Resolved_Video_Title.mp4"));
                            Console.WriteLine("download: 25.0%#104857600B#5.0MiB/s#00:15#26214400B");
                            Console.WriteLine("download: 75.0%#104857600B#10.0MiB/s#00:03#78643200B");
                            Console.WriteLine("download: 100.0%#104857600B#10.0MiB/s#00:00#104857600B");
                            return 0;
                        }
                    }
                    """.trimIndent()
                )
            }
            val cscPath = "C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe"
            val pb = ProcessBuilder(cscPath, "/nologo", "/out:${targetFile.absolutePath}", csFile.absolutePath)
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.waitFor()
        } else {
            targetFile.writeText(
                """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                    echo "2026.03.01"
                    exit 0
                fi
                case "$*" in
                    *error_fail_video*)
                        echo "ERROR: [youtube] error_fail_video: Video unavailable. This video is private."
                        exit 1
                        ;;
                esac
                echo "[download] Destination: $(dirname "$0")/Resolved_Video_Title.mp4"
                echo "download: 25.0%#104857600B#5.0MiB/s#00:15#26214400B"
                echo "download: 75.0%#104857600B#10.0MiB/s#00:03#78643200B"
                echo "download: 100.0%#104857600B#10.0MiB/s#00:00#104857600B"
                exit 0
                """.trimIndent()
            )
            targetFile.setExecutable(true)
        }
    }

    private fun awaitStatus(taskId: Long, targetStatus: RecordStatus, timeoutMs: Long = 5000): DbRecord? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val r = AppContext.downloader.store.getById(taskId)
            if (r?.status == targetStatus) return r
            Thread.sleep(50)
        }
        return AppContext.downloader.store.getById(taskId)
    }

    @Test
    fun testNewDownloadWindowRoutesToYtDlpAndRegistersInList() {
        var taskId: Long? = null
        SwingUtilities.invokeAndWait {
            val window = NewDownloadWindow()
            val url = "https://www.youtube.com/watch?v=sample123"
            window.txtUrl.text = url
            window.txtFileName.text = "watch"
            window.picker.urlChanged(url)

            // Category should auto-resolve to Videos via plugin recognizer
            assertEquals("CAT_VIDEOS", window.picker.selectedCategory?.id)

            // Submit download now
            taskId = window.createDownload(now = true)
            window.dispose()
        }

        assertNotNull(taskId)
        assertNotEquals(-1L, taskId!!)

        // Await deterministic process double completion
        val record = awaitStatus(taskId!!, RecordStatus.FINISHED)
        assertNotNull(record, "Plugin download must be registered in downloader store")
        assertEquals(DownloadType.Plugin, record!!.downloadType)
        assertEquals("CAT_VIDEOS", record.categoryId, "Should be categorized under CAT_VIDEOS")
        assertEquals(RecordStatus.FINISHED, record.status)
        assertEquals(100, record.progress)
        assertEquals("Resolved_Video_Title.mp4", record.fileName)

        // Verify persisted metadata in TaskInfoDB
        val meta = AppContext.taskInfoDB.getPluginTask(taskId!!)
        assertNotNull(meta)
        assertEquals("ytdlp-downloader", meta!!.pluginId)
        assertEquals("https://www.youtube.com/watch?v=sample123", meta.url)
        assertEquals("CAT_VIDEOS", meta.categoryId)
    }

    @Test
    fun testDownloadLaterPreservesInListAsPaused() {
        var taskId: Long? = null
        SwingUtilities.invokeAndWait {
            val window = NewDownloadWindow()
            val url = "https://vimeo.com/987654321"
            window.txtUrl.text = url
            window.txtFileName.text = "987654321"
            window.picker.urlChanged(url)

            // Submit download later (now = false)
            taskId = window.createDownload(now = false)
            window.dispose()
        }

        assertNotNull(taskId)
        assertNotEquals(-1L, taskId!!)

        val record = AppContext.downloader.store.getById(taskId!!)
        assertNotNull(record)
        assertEquals(DownloadType.Plugin, record!!.downloadType)
        assertEquals(RecordStatus.PAUSED, record.status, "Download later must register task as PAUSED")

        // Resume task via PluginDownloadBridge
        PluginDownloadBridge.resumeTask(taskId!!)
        val resumedRecord = AppContext.downloader.store.getById(taskId!!)
        assertNotNull(resumedRecord)
        assertTrue(
            resumedRecord!!.status == RecordStatus.DOWNLOADING || resumedRecord.status == RecordStatus.FINISHED,
            "Resumed record must transition to DOWNLOADING or FINISHED"
        )
    }

    @Test
    fun testStructuredLiveProgressReportingWithoutFaking() {
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=progress_test")!!
        val provider = providerPair.second
        val req = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=progress_test",
            dir.absolutePath,
            "sample.mp4"
        )
        val task = provider.createDownloadTask(req)
        val taskId = loadedPlugin.context!!.submitPluginTask(req, task)
        assertNotEquals(-1L, taskId)

        val feedMethod = task.javaClass.getMethod("processOutputLine", String::class.java)

        // Simulate structured line 1: 25% of 100MiB at 5MiB/s, ETA 15s
        feedMethod.invoke(task, "download: 25.0%#100.0MiB#5.0MiB/s#00:15#25.0MiB")

        val r1 = AppContext.downloader.store.getById(taskId)!!
        assertEquals(25, r1.progress)
        assertEquals(26214400L, r1.downloaded)
        assertEquals(104857600L, r1.size)
        assertEquals(5242880f, r1.speed)
        assertEquals(15L, r1.eta)

        // Simulate structured line 2: 75% of 100MiB at 10MiB/s, ETA 3s
        feedMethod.invoke(task, "download: 75.0%#100.0MiB#10.0MiB/s#00:03#78643200B")

        val r2 = AppContext.downloader.store.getById(taskId)!!
        assertEquals(75, r2.progress)
        assertEquals(78643200L, r2.downloaded)
        assertEquals(104857600L, r2.size)
        assertEquals(10485760f, r2.speed)
        assertEquals(3L, r2.eta)
    }

    @Test
    fun testOutputFilenameResolutionAndCompletionRetention() {
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=finish_test")!!
        val provider = providerPair.second
        val req = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=finish_test",
            dir.absolutePath,
            "watch"
        )
        val task = provider.createDownloadTask(req)
        val taskId = loadedPlugin.context!!.submitPluginTask(req, task)
        assertNotEquals(-1L, taskId)

        // Await completion from process double
        val record = awaitStatus(taskId, RecordStatus.FINISHED)
        assertNotNull(record, "Completed task must remain in the download list")
        assertEquals(RecordStatus.FINISHED, record!!.status)
        assertEquals(100, record.progress)
        assertEquals("Resolved_Video_Title.mp4", record.fileName, "Filename must be synchronized to resolved output file")
        assertEquals("CAT_VIDEOS", record.categoryId)

        // Verify task info updated
        val meta = AppContext.taskInfoDB.getPluginTask(taskId)
        assertEquals("Resolved_Video_Title.mp4", meta?.fileName)
    }

    @Test
    fun testProcessErrorHandlingKeepsRecordInList() {
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=error_fail_video")!!
        val provider = providerPair.second
        val req = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=error_fail_video",
            dir.absolutePath,
            "error_test.mp4"
        )
        val task = provider.createDownloadTask(req)
        val taskId = loadedPlugin.context!!.submitPluginTask(req, task)

        // Await error state from process double
        val record = awaitStatus(taskId, RecordStatus.ERROR)

        // Record must NOT vanish; it must stay visible with status ERROR
        assertNotNull(record, "Failed task must not disappear from download list")
        assertEquals(RecordStatus.ERROR, record!!.status)
    }

    @Test
    fun testUiFilteringAndMultiViewVisibility() {
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=ui_view_test")!!
        val provider = providerPair.second
        val req = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=ui_view_test",
            dir.absolutePath,
            "sample_view.mp4"
        )
        val task = provider.createDownloadTask(req)
        val taskId = loadedPlugin.context!!.submitPluginTask(req, task)
        val record = AppContext.downloader.store.getById(taskId)!!

        val listModel = MainListViewModel()

        // 1. Test FilterState.All
        val filterAll = MainListViewFilter(FilterState.All, null, "")
        assertTrue(filterMatches(filterAll, listModel, taskId))

        // 2. Test Category Filter "CAT_VIDEOS"
        val videoCat = AppContext.config.categories.first { it.id == "CAT_VIDEOS" }
        val filterVideos = MainListViewFilter(FilterState.All, videoCat, "")
        assertTrue(filterMatches(filterVideos, listModel, taskId), "Task should match CAT_VIDEOS filter")

        // 3. Test Category Filter "CAT_MUSIC" (should not match)
        val musicCat = AppContext.config.categories.first { it.id == "CAT_MUSIC" }
        val filterMusic = MainListViewFilter(FilterState.All, musicCat, "")
        assertFalse(filterMatches(filterMusic, listModel, taskId), "Task should not match CAT_MUSIC filter")

        // 4. Test DownloadTableModel column presentation
        val tableModel = DownloadTableModel()
        val rowIdx = tableModel.findRowById(taskId)
        assertTrue(rowIdx >= 0)
        assertEquals("Plugin", tableModel.getValueAt(rowIdx, DownloadTableModel.COL_TYPE))

        // 5. Verify CategoryStyle category name display
        assertEquals("Videos", CategoryStyle.displayNameForCategory(record.categoryId, record.downloadType))
        assertEquals(RemixIcon.PUZZLE_LINE, CategoryStyle.glyphForCategory(record.categoryId, record.fileName, record.downloadType))
    }

    @Test
    fun testPersistenceAndResumeAcrossAppReload() {
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=reload_test")!!
        val provider = providerPair.second
        val req = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=reload_test",
            dir.absolutePath,
            "persisted_video.mp4"
        )
        val task = provider.createDownloadTask(req)
        val taskId = loadedPlugin.context!!.submitPluginTask(req, task)

        // Pause download
        PluginDownloadBridge.pauseTask(taskId)
        assertEquals(RecordStatus.PAUSED, AppContext.downloader.store.getById(taskId)!!.status)

        // Simulate app shutdown and memory wipe of active adapters
        PluginDownloadBridge.cancelAll()
        assertNull(PluginDownloadBridge.getTask(taskId), "In-memory active task should be cleared on reload")

        // Verify task metadata still exists on disk in TaskInfoDB
        val persistedMeta = AppContext.taskInfoDB.getPluginTask(taskId)
        assertNotNull(persistedMeta, "Task metadata must be persisted across reload in TaskInfoDB")
        assertEquals("ytdlp-downloader", persistedMeta!!.pluginId)
        assertEquals("https://www.youtube.com/watch?v=reload_test", persistedMeta.url)

        // Resume download (re-instantiates task from TaskInfoDB via PluginDownloadBridge)
        PluginDownloadBridge.resumeTask(taskId)

        val resumedRecord = AppContext.downloader.store.getById(taskId)
        assertNotNull(resumedRecord)
        assertTrue(
            resumedRecord!!.status == RecordStatus.DOWNLOADING || resumedRecord.status == RecordStatus.FINISHED,
            "Resumed record must transition to DOWNLOADING or FINISHED"
        )
    }

    @Test
    fun testUserCancellationDistinctionVsProcessFailure() {
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=cancel_distinction")!!
        val provider = providerPair.second
        val req = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=cancel_distinction",
            dir.absolutePath,
            "cancel_test.mp4"
        )
        val task = provider.createDownloadTask(req)
        val taskId = loadedPlugin.context!!.submitPluginTask(req, task)

        // Cancel task explicitly
        task.cancel()

        val record = AppContext.downloader.store.getById(taskId)
        assertNotNull(record)
        // User cancellation must NOT be recorded as ERROR
        assertNotEquals(RecordStatus.ERROR, record!!.status, "User cancellation must not be recorded as ERROR")
        assertEquals(RecordStatus.PAUSED, record.status, "Canceled task must be safely recorded as PAUSED/stopped")
    }

    @Test
    fun testDeleteFromDiskRemovesResolvedOutputFile() {
        val providerPair = PluginManager.findDownloadProvider("https://www.youtube.com/watch?v=delete_disk_test")!!
        val provider = providerPair.second
        val req = PluginDownloadRequest(
            "https://www.youtube.com/watch?v=delete_disk_test",
            dir.absolutePath,
            "watch"
        )
        val task = provider.createDownloadTask(req)
        val taskId = loadedPlugin.context!!.submitPluginTask(req, task)

        // Await completion from process double
        val record = awaitStatus(taskId, RecordStatus.FINISHED)
        assertNotNull(record)
        assertEquals("Resolved_Video_Title.mp4", record!!.fileName)

        val outputFile = File(dir, "Resolved_Video_Title.mp4").apply {
            if (!exists()) writeText("dummy video content for deletion test")
        }
        assertTrue(outputFile.exists(), "Target file must exist prior to deletion")

        // Delete from list and disk
        PluginDownloadBridge.deleteTask(taskId, fromDisk = true)

        assertNull(AppContext.downloader.store.getById(taskId), "Record must be removed from store")
        assertNull(AppContext.taskInfoDB.getPluginTask(taskId), "Record must be removed from taskInfoDB")
        assertFalse(outputFile.exists(), "Target file must be deleted from disk when fromDisk=true")
    }

    @Test
    fun testClipboardEligibilityAndCategoryResolutionForMediaUrl() {
        val mediaUrl = "https://www.youtube.com/watch?v=clip_recognize_test"
        assertTrue(ClipboardMonitor.isEligibleHttpUrl(mediaUrl), "YouTube link must be recognized as eligible HTTP URL")

        val match = PluginManager.findUrlRecognizer(mediaUrl)
        assertNotNull(match, "Active yt-dlp plugin recognizer must match YouTube URL")
        val metadata = match!!.second.analyze(mediaUrl)
        assertNotNull(metadata)
        assertEquals("CAT_VIDEOS", metadata!!.suggestedCategory)

        val category = captureCategory("watch", mediaUrl, null)
        assertNotNull(category, "captureCategory must resolve Videos category for plugin media URL")
        assertEquals("CAT_VIDEOS", category!!.id)
    }

    private fun filterMatches(filter: MainListViewFilter, model: MainListViewModel, id: Long): Boolean {
        val rowIdx = AppContext.db.indexById(id) ?: return false
        val entry = object : javax.swing.RowFilter.Entry<Any, Int>() {
            override fun getModel(): Any = model
            override fun getValueCount(): Int = 1
            override fun getValue(index: Int): Any = model.getItemAt(rowIdx)!!
            override fun getIdentifier(): Int = rowIdx
        }
        return filter.include(entry)
    }
}
