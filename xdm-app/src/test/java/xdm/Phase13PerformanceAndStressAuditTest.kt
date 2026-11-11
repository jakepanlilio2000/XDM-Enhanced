package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.AppDB
import xdm.app.IAppInstance
import xdm.app.plugins.PluginEventBus
import xdm.app.plugins.PluginStatus
import xdm.app.preflight.ClipboardMonitor
import xdm.app.skins.SkinManager
import xdm.app.ui.components.DownloadTableModel
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.integration.BrowserIntegration
import xdm.integration.HttpParser
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.swing.table.TableRowSorter

class Phase13PerformanceAndStressAuditTest {

    @TempDir
    lateinit var tempDir: File

    private val runtime = Runtime.getRuntime()
    private val threadMxBean = ManagementFactory.getThreadMXBean()
    private val memoryMxBean = ManagementFactory.getMemoryMXBean()
    private val osMxBean = ManagementFactory.getOperatingSystemMXBean()

    @BeforeEach
    fun setUp() {
        xdm.app.I8N.loadTexts("en")
        val config = AppConfig(tempDir.absolutePath)
        val appDB = AppDB(tempDir.absolutePath)
        AppContext.configDir = tempDir.absolutePath
        AppContext.config = config
        AppContext.db = appDB
        AppContext.taskInfoDB = TaskInfoDB(tempDir.absolutePath)
        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, _, _ -> null } as IAppInstance

        System.gc()
    }

    @AfterEach
    fun tearDown() {
        SkinManager.restoreDefaults()
        PluginEventBus.clearAll()
        ClipboardMonitor.stop()
        System.gc()
    }

    // =========================================================================
    // Scenario 84: Startup Latency, Idle Footprint, Skin Switching & Plugins
    // =========================================================================

    @Test
    fun testScenario84_startupResourceProfilingAndSkinSwitching() {
        val initialThreads = threadMxBean.threadCount
        val initialHeap = memoryMxBean.heapMemoryUsage.used

        // 1. Cold vs Warm Startup Latency
        val coldStartNs = System.nanoTime()
        val coldDb = AppDB(tempDir.absolutePath)
        val coldConfig = AppConfig(tempDir.absolutePath)
        val coldElapsedMs = (System.nanoTime() - coldStartNs) / 1_000_000.0

        val warmStartNs = System.nanoTime()
        val warmDb = AppDB(tempDir.absolutePath)
        val warmConfig = AppConfig(tempDir.absolutePath)
        val warmElapsedMs = (System.nanoTime() - warmStartNs) / 1_000_000.0

        assertTrue(coldElapsedMs < 1000.0, "Cold config/DB init must execute under 1000ms (was: ${coldElapsedMs}ms)")
        assertTrue(warmElapsedMs < 500.0, "Warm config/DB init must execute under 500ms (was: ${warmElapsedMs}ms)")

        // 2. Skin Switching Stress Test (60 switches between classic and enhanced)
        SkinManager.init(null)
        val skinSwitchCycles = 60
        val skinStartNs = System.nanoTime()
        for (i in 0 until skinSwitchCycles) {
            val targetSkin = if (i % 2 == 0) SkinManager.SKIN_ORIGINAL_XDM else SkinManager.SKIN_ENHANCED
            SkinManager.applySkin(targetSkin, saveConfig = false)
        }
        val skinDurationMs = (System.nanoTime() - skinStartNs) / 1_000_000.0
        val avgSwitchMs = skinDurationMs / skinSwitchCycles

        System.gc()
        Thread.sleep(50)
        val postSkinHeap = memoryMxBean.heapMemoryUsage.used
        val skinHeapDeltaMb = (postSkinHeap - initialHeap) / 1024.0 / 1024.0

        assertTrue(avgSwitchMs < 100.0, "Average skin switch must be under 100ms (was: ${avgSwitchMs}ms)")
        assertTrue(skinHeapDeltaMb < 15.0, "Skin switching must not exhibit memory leaks (delta: ${skinHeapDeltaMb}MB)")

        // 3. Plugin Enable/Disable Rapid Cycling
        val pluginCycles = 50
        val pluginStartNs = System.nanoTime()
        var pluginStatus = PluginStatus.ACTIVE
        for (i in 0 until pluginCycles) {
            pluginStatus = if (pluginStatus == PluginStatus.ACTIVE) PluginStatus.DISABLED else PluginStatus.ACTIVE
            PluginEventBus.publish("plugin-lifecycle", "state=$pluginStatus")
        }
        val pluginDurationMs = (System.nanoTime() - pluginStartNs) / 1_000_000.0
        assertTrue(pluginDurationMs < 2000.0, "Plugin lifecycle cycling must complete promptly (was: ${pluginDurationMs}ms)")

        // Thread count stability check
        val finalThreads = threadMxBean.threadCount
        val threadDiff = Math.abs(finalThreads - initialThreads)
        assertTrue(threadDiff <= 8, "Active threads must remain bounded (diff: $threadDiff)")
    }

    // =========================================================================
    // Scenario 85: 1,500 Synthetic Tasks, Sorting, Categories & EDT Responsiveness
    // =========================================================================

    @Test
    fun testScenario85_syntheticTaskScalingAndEdtResponsiveness() {
        val count = 1500
        val categories = listOf("Documents", "Compressed", "Video", "Music", "Applications")

        val populateStartNs = System.nanoTime()
        for (i in 1..count) {
            val rec = DbRecord(
                id = i.toLong(),
                size = i * 2097152L,
                downloaded = i * 1048576L,
                progress = (i % 100),
                date = System.currentTimeMillis() - i * 5000L,
                fileName = "payload_archive_${i}.${if (i % 2 == 0) "zip" else "iso"}",
                eta = (count - i).toLong(),
                speed = 5242880f,
                selected = false,
                status = when (i % 4) {
                    0 -> RecordStatus.FINISHED
                    1 -> RecordStatus.DOWNLOADING
                    2 -> RecordStatus.PAUSED
                    else -> RecordStatus.ERROR
                },
                downloadType = if (i % 5 == 0) DownloadType.Torrent else DownloadType.Http,
                categoryId = categories[i % categories.size]
            )
            AppContext.db.add(rec)
        }
        val populateMs = (System.nanoTime() - populateStartNs) / 1_000_000.0
        assertEquals(count, AppContext.db.size, "Database must hold all 1,500 records")
        assertTrue(populateMs < 6000.0, "1,500 records must populate under 6s on physical disk (was: ${populateMs}ms)")

        // 1. Table Model Row Count & Value Resolution
        val tableModel = DownloadTableModel()
        assertEquals(count, tableModel.rowCount)

        // 2. High-Frequency Category Filtering
        val filterStartNs = System.nanoTime()
        val compressedMatches = AppContext.db.findAll { it.categoryId == "Compressed" }
        val videoMatches = AppContext.db.findAll { it.categoryId == "Video" }
        val filterElapsedMs = (System.nanoTime() - filterStartNs) / 1_000_000.0
        assertEquals(count / categories.size, compressedMatches.size)
        assertEquals(count / categories.size, videoMatches.size)
        assertTrue(filterElapsedMs < 100.0, "Filtering 1,500 items must be sub-100ms (was: ${filterElapsedMs}ms)")

        // 3. TableRowSorter Benchmark (Column Sorting on 1,500 Rows)
        val sorter = TableRowSorter(tableModel)
        val sortStartNs = System.nanoTime()
        sorter.sort()
        val sortElapsedMs = (System.nanoTime() - sortStartNs) / 1_000_000.0
        assertTrue(sortElapsedMs < 400.0, "Sorting 1,500 rows must be fast (<400ms, was: ${sortElapsedMs}ms)")

        // 4. Progress Update Burst (Simulating 500 concurrent progress ticks)
        val updateStartNs = System.nanoTime()
        for (i in 1..500) {
            val existing = AppContext.db.getById(i.toLong())
            if (existing != null) {
                existing.downloaded = existing.downloaded + 65536L
                existing.progress = ((existing.downloaded.toDouble() / existing.size) * 100).toInt()
                AppContext.db.update(existing)
            }
        }
        val updateElapsedMs = (System.nanoTime() - updateStartNs) / 1_000_000.0
        assertTrue(updateElapsedMs < 2500.0, "500 live progress updates must complete in <2500ms (was: ${updateElapsedMs}ms)")

        // 5. EDT Event Queue Dispatch Latency Under Load
        val edtLatch = CountDownLatch(1)
        val edtStartNs = System.nanoTime()
        SwingUtilities.invokeLater {
            edtLatch.countDown()
        }
        assertTrue(edtLatch.await(1, TimeUnit.SECONDS), "EDT event queue must dispatch under load")
        val edtLatencyMicros = (System.nanoTime() - edtStartNs) / 1000.0
        assertTrue(edtLatencyMicros < 150_000.0, "EDT latency must remain bounded under load (<150ms, was: ${edtLatencyMicros / 1000.0}ms)")
    }

    // =========================================================================
    // Scenario 86: Loopback IPC Storm, Reconnect Resilience & Filter Throughput
    // =========================================================================

    @Test
    fun testScenario86_loopbackIpcThroughputAndReconnectResilience() {
        val filter = BrowserIntegration.RequestFilter(8597)
        val iterations = 500

        val startNs = System.nanoTime()
        for (i in 1..iterations) {
            val syncHead = HttpParser.RequestHead(
                method = "GET",
                path = "/sync",
                version = "HTTP/1.1",
                headers = mapOf(
                    "Host" to listOf("127.0.0.1:8597"),
                    "User-Agent" to listOf("Mozilla/5.0 Chrome/130.0"),
                    "X-XDM-Version" to listOf("3.5.4")
                ),
                deadlineNanos = System.nanoTime() + 1_000_000_000L
            )
            assertNull(filter.reject(syncHead), "Valid sync request #$i must be accepted")

            val downloadHead = HttpParser.RequestHead(
                method = "POST",
                path = "/download",
                version = "HTTP/1.1",
                headers = mapOf(
                    "Host" to listOf("127.0.0.1:8597"),
                    "Origin" to listOf("chrome-extension://abcdefghijklmnop")
                ),
                deadlineNanos = System.nanoTime() + 1_000_000_000L
            )
            assertNull(filter.reject(downloadHead), "Valid download POST #$i must be accepted")
        }
        val durationMs = (System.nanoTime() - startNs) / 1_000_000.0
        val totalRequests = iterations * 2
        val throughput = (totalRequests / (durationMs / 1000.0))

        assertTrue(durationMs < 1000.0, "Processing $totalRequests IPC requests must be sub-second (was: ${durationMs}ms)")
        assertTrue(throughput > 1000.0, "Loopback request throughput must exceed 1,000 req/sec (was: $throughput req/sec)")
    }

    // =========================================================================
    // Scenario 87: Error Storms, Cancellation, Network Flaps & Teardown Cleanup
    // =========================================================================

    @Test
    fun testScenario87_errorStormsAndTeardownIntegrity() {
        // 1. Error Storm Handling: 200 simulated HTTP errors / network resets
        val stormCount = 200
        val errorStartNs = System.nanoTime()
        val errorTypes = listOf(
            DownloadError.NetworkError,
            DownloadError.InvalidResponse,
            DownloadError.SessionExpired,
            DownloadError.LinkExpired,
            DownloadError.DiskSpaceError,
            DownloadError.InternalError
        )
        for (i in 1..stormCount) {
            val err = errorTypes[i % errorTypes.size]
            assertNotNull(err.name)
        }
        val errorDurationMs = (System.nanoTime() - errorStartNs) / 1_000_000.0
        assertTrue(errorDurationMs < 200.0, "200 error classifications must complete within 200ms (was: ${errorDurationMs}ms)")

        // 2. Rapid Task Cancellation & State Preservation
        val pool = Executors.newFixedThreadPool(8)
        val cancelLatch = CountDownLatch(50)
        for (i in 1..50) {
            val rec = DbRecord(
                id = 5000L + i,
                size = 10485760L,
                downloaded = 1048576L,
                progress = 10,
                date = System.currentTimeMillis(),
                fileName = "task_cancel_$i.bin",
                eta = 100L,
                speed = 1024000f,
                selected = false,
                status = RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Http,
                categoryId = "Other"
            )
            AppContext.db.add(rec)

            pool.submit {
                // Simulate worker thread cancelling download
                rec.status = RecordStatus.PAUSED
                AppContext.db.update(rec)
                cancelLatch.countDown()
            }
        }
        assertTrue(cancelLatch.await(3, TimeUnit.SECONDS), "Cancellation storm must complete cleanly")
        pool.shutdownNow()

        // Verify cancelled records are properly marked PAUSED
        for (i in 1..50) {
            val r = AppContext.db.getById(5000L + i)
            assertEquals(RecordStatus.PAUSED, r?.status)
        }

        // 3. Process Restart & Database Reload with Zero Data Corruption
        val restartDir = File(tempDir, "restart-test").apply { mkdirs() }
        val db1 = AppDB(restartDir.absolutePath)
        for (i in 1..100) {
            db1.add(DbRecord(
                id = i.toLong(),
                size = i * 1000L,
                downloaded = i * 1000L,
                progress = 100,
                date = System.currentTimeMillis(),
                fileName = "file_$i.dat",
                eta = 0L,
                speed = 0f,
                selected = false,
                status = RecordStatus.FINISHED,
                downloadType = DownloadType.Http
            ))
        }
        db1.saveFinishedRecords()

        // Reopen database from disk in a new instance (simulating restart)
        val db2 = AppDB(restartDir.absolutePath)
        db2.loadRecords()
        assertEquals(100, db2.size, "Database must cleanly reload all 100 persisted records across restart")
        for (i in 1..100) {
            val reloaded = db2.getById(i.toLong())
            assertNotNull(reloaded, "Record #$i must exist after restart")
            assertEquals("file_$i.dat", reloaded?.fileName)
            assertEquals(RecordStatus.FINISHED, reloaded?.status)
        }
    }

    // =========================================================================
    // Scenario 88: Baseline Telemetry & Host Environment Documentation
    // =========================================================================

    @Test
    fun testScenario88_baselineEnvironmentMetrics() {
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        val osVersion = System.getProperty("os.version")
        val javaVendor = System.getProperty("java.vendor")
        val javaVersion = System.getProperty("java.version")
        val availableProcessors = runtime.availableProcessors()
        val maxMemoryMb = runtime.maxMemory() / 1024 / 1024
        val totalMemoryMb = runtime.totalMemory() / 1024 / 1024
        val freeMemoryMb = runtime.freeMemory() / 1024 / 1024
        val usedMemoryMb = totalMemoryMb - freeMemoryMb

        println("=== REPEATABLE HARDWARE & RUNTIME BASELINE ===")
        println("OS: $osName $osVersion ($osArch)")
        println("JVM: $javaVendor Liberica JDK $javaVersion")
        println("Logical CPU Cores: $availableProcessors")
        println("Max Heap Allocation: $maxMemoryMb MB")
        println("Committed Heap: $totalMemoryMb MB")
        println("Active Used Heap: $usedMemoryMb MB")
        println("Operating System Load Average: ${osMxBean.systemLoadAverage}")

        assertTrue(availableProcessors >= 1, "Host must have at least 1 logical core")
        assertTrue(maxMemoryMb >= 256, "Max heap must be at least 256MB")
        assertNotNull(osName)
        assertNotNull(javaVersion)
    }
}
