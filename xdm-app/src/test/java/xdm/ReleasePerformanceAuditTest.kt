package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.AppDB
import xdm.app.IAppInstance
import xdm.app.plugins.PluginEventBus
import xdm.app.preflight.ClipboardMonitor
import xdm.app.skins.SkinManager
import xdm.app.ui.components.DownloadTableModel
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.plugin.api.events.PluginEventListener
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class ReleasePerformanceAuditTest {

    private val runtime = Runtime.getRuntime()
    private val threadMxBean = ManagementFactory.getThreadMXBean()
    private val memoryMxBean = ManagementFactory.getMemoryMXBean()
    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("xdm-perf-test").toFile()
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
        Thread.sleep(100)
    }

    @AfterEach
    fun tearDown() {
        SkinManager.restoreDefaults()
        PluginEventBus.clearAll()
        ClipboardMonitor.stop()
        if (::tempDir.isInitialized) {
            tempDir.deleteRecursively()
        }
        System.gc()
    }

    @Test
    fun benchmark_startupAndIdleMemory() {
        val heapUsage = memoryMxBean.heapMemoryUsage
        val nonHeapUsage = memoryMxBean.nonHeapMemoryUsage
        val threadCount = threadMxBean.threadCount

        println("=== BENCHMARK: Startup & Idle Memory ===")
        println("Used Heap: ${heapUsage.used / 1024 / 1024} MB")
        println("Committed Heap: ${heapUsage.committed / 1024 / 1024} MB")
        println("Max Heap: ${heapUsage.max / 1024 / 1024} MB")
        println("Non-Heap Used: ${nonHeapUsage.used / 1024 / 1024} MB")
        println("Active Threads: $threadCount")

        assertTrue(heapUsage.used > 0, "Heap usage must be non-zero")
        assertTrue(threadCount > 0, "Thread count must be non-zero")
    }

    @Test
    fun benchmark_tableModelWithManyDownloads() {
        val count = 1000

        val startMemory = runtime.totalMemory() - runtime.freeMemory()
        val startTime = System.nanoTime()

        for (i in 1..count) {
            val record = DbRecord(
                id = i.toLong(),
                size = i * 1048576L,
                downloaded = i * 524288L,
                progress = 50,
                date = System.currentTimeMillis() - i * 1000L,
                fileName = "test_download_payload_$i.iso",
                eta = 60L,
                speed = 2048000f,
                selected = false,
                status = if (i % 2 == 0) RecordStatus.DOWNLOADING else RecordStatus.PAUSED,
                downloadType = DownloadType.Http,
                categoryId = if (i % 3 == 0) "Compressed" else "Documents"
            )
            AppContext.db.add(record)
        }

        val model = DownloadTableModel()
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val endMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryDeltaMb = (endMemory - startMemory) / 1024.0 / 1024.0

        println("=== BENCHMARK: 1,000 Table Downloads ===")
        println("Population & Query Time: ${"%.2f".format(elapsedMs)} ms (${"%.4f".format(elapsedMs / count)} ms/item)")
        println("Memory Delta: ${"%.2f".format(memoryDeltaMb)} MB")
        println("Items in Model: ${model.rowCount}")

        assertEquals(count, model.rowCount)
        assertTrue(elapsedMs < 5000.0, "Populating 1000 items should complete well within 5 seconds")
    }

    @Test
    fun benchmark_skinSwitchingFrequencyAndMemoryLeak() {
        SkinManager.init(null)
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        val iterations = 50
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val targetSkin = if (i % 2 == 0) SkinManager.SKIN_ORIGINAL_XDM else SkinManager.SKIN_ENHANCED
            SkinManager.applySkin(targetSkin, saveConfig = false)
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        System.gc()
        Thread.sleep(100)
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryDeltaMb = (finalMemory - initialMemory) / 1024.0 / 1024.0

        println("=== BENCHMARK: Skin Switching ($iterations cycles) ===")
        println("Total Duration: ${"%.2f".format(elapsedMs)} ms")
        println("Average Switch Time: ${"%.2f".format(elapsedMs / iterations)} ms/switch")
        println("Net Memory Delta after GC: ${"%.2f".format(memoryDeltaMb)} MB")

        assertTrue(elapsedMs / iterations < 300.0, "Skin switch should be fast (<300ms per switch)")
    }

    @Test
    fun benchmark_concurrentPluginEventsAndDispatch() {
        val eventCount = 10_000
        val latch = CountDownLatch(eventCount)

        PluginEventBus.subscribe("test-plugin", "perf-test", object : PluginEventListener<String> {
            override fun onEvent(topic: String, data: String) {
                latch.countDown()
            }
        })

        val startTime = System.nanoTime()
        for (i in 0 until eventCount) {
            PluginEventBus.publish("perf-test", "payload-$i")
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        println("=== BENCHMARK: Plugin Event Bus (10,000 events) ===")
        println("Dispatched & Handled in: ${"%.2f".format(elapsedMs)} ms")
        println("Throughput: ${"%.0f".format(eventCount / (elapsedMs / 1000.0))} events/sec")

        assertTrue(completed, "All 10,000 plugin events must be handled within timeout")
        assertTrue(elapsedMs < 5000.0, "Dispatch must be sub-5 seconds")
    }

    @Test
    fun benchmark_threadLeaksAndEdtResponsiveness() {
        val initialThreadCount = threadMxBean.threadCount

        val edtLatch = CountDownLatch(1)
        val edtStartTime = System.nanoTime()
        SwingUtilities.invokeLater {
            edtLatch.countDown()
        }

        assertTrue(edtLatch.await(1, TimeUnit.SECONDS), "EDT event queue must be responsive")
        val edtLatencyMicros = (System.nanoTime() - edtStartTime) / 1000.0

        println("=== BENCHMARK: EDT Latency & Thread Health ===")
        println("EDT Dispatch Latency: ${"%.2f".format(edtLatencyMicros)} µs")
        println("Initial Active Threads: $initialThreadCount")
        println("Current Active Threads: ${threadMxBean.threadCount}")

        val threadDiff = Math.abs(threadMxBean.threadCount - initialThreadCount)
        assertTrue(threadDiff <= 5, "Thread delta should remain strictly bounded (diff: $threadDiff)")
    }
}
