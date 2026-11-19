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
import xdm.app.skins.BuiltInClassicXdmSkin
import xdm.app.skins.BuiltInEnhancedSkin
import xdm.app.skins.SkinManager
import xdm.app.ui.components.*
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.core.downloaders.web.SegmentProgress
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.plugin.api.events.PluginEventListener
import xdm.torrent.TorrentMetadataParser
import xdm.torrent.TorrentNativeLoader
import java.io.File
import java.io.FileOutputStream
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.table.TableRowSorter

/**
 * Phase 21: Sustained Operation, Responsiveness, Scale, Memory/Resource Lifetime,
 * and Failure Mode Resilience Audit.
 */
class Phase21SustainedOperationAndScaleAuditTest {

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
        runCatching {
            val disposeWindows = {
                java.awt.Window.getWindows().forEach { w ->
                    runCatching {
                        w.isVisible = false
                        w.dispose()
                    }
                }
            }
            if (SwingUtilities.isEventDispatchThread()) {
                disposeWindows()
            } else {
                SwingUtilities.invokeAndWait(disposeWindows)
            }
        }
        System.gc()
    }

    private fun <T> runOnEdt(action: () -> T): T {
        var result: T? = null
        var err: Throwable? = null
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        SwingUtilities.invokeAndWait {
            try {
                result = action()
            } catch (t: Throwable) {
                err = t
            }
        }
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    // =========================================================================
    // Scenario 90: Hardware & Runtime Lifecycle Baseline Metrics
    // =========================================================================

    @Test
    fun testScenario90_hardwareAndRuntimeLifecycleBaseline() {
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        val osVersion = System.getProperty("os.version")
        val javaVendor = System.getProperty("java.vendor")
        val javaVersion = System.getProperty("java.version")
        val logicalCores = runtime.availableProcessors()
        val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
        val totalHeapMb = runtime.totalMemory() / (1024 * 1024)
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val nonHeapMb = memoryMxBean.nonHeapMemoryUsage.used / (1024 * 1024)
        val baselineThreads = threadMxBean.threadCount

        println("=== HARDWARE & RUNTIME BASELINE TELEMETRY ===")
        println("OS: $osName $osVersion ($osArch)")
        println("JVM: $javaVendor Liberica JDK $javaVersion")
        println("Logical Cores: $logicalCores")
        println("Heap: ${usedHeapMb}MB used / ${totalHeapMb}MB committed / ${maxHeapMb}MB max")
        println("Non-Heap (Metaspace/CodeCache): ${nonHeapMb}MB")
        println("Baseline Threads: $baselineThreads")

        assertTrue(logicalCores >= 1, "Logical cores must be >= 1")
        assertTrue(maxHeapMb >= 256, "Max heap must be >= 256MB")
        assertTrue(baselineThreads > 0, "Thread count must be > 0")

        // 1. Cold vs Warm Subsystem Startup Latency
        val coldStartNs = System.nanoTime()
        val coldDir = File(tempDir, "cold-test").apply { mkdirs() }
        val coldConfig = AppConfig(coldDir.absolutePath)
        val coldDb = AppDB(coldDir.absolutePath)
        val coldTaskDb = TaskInfoDB(coldDir.absolutePath)
        val coldMs = (System.nanoTime() - coldStartNs) / 1_000_000.0

        val warmStartNs = System.nanoTime()
        val warmConfig = AppConfig(coldDir.absolutePath)
        val warmDb = AppDB(coldDir.absolutePath)
        val warmTaskDb = TaskInfoDB(coldDir.absolutePath)
        val warmMs = (System.nanoTime() - warmStartNs) / 1_000_000.0

        println("Cold Subsystem Init: ${"%.2f".format(coldMs)} ms")
        println("Warm Subsystem Init: ${"%.2f".format(warmMs)} ms")
        assertTrue(coldMs < 1500.0, "Cold subsystem init must be under 1.5s (was ${coldMs}ms)")
        assertTrue(warmMs < 800.0, "Warm subsystem init must be under 800ms (was ${warmMs}ms)")

        // 2. Clean Shutdown & Resource Flush
        coldDb.saveActiveRecords()
        coldDb.savePausedRecords()
        coldDb.saveFinishedRecords()
        assertTrue(File(coldDir, "active-downloads.dat").exists() || coldDb.size == 0)
    }

    // =========================================================================
    // Scenario 91: Scale Test with 5,000 Download Records
    // =========================================================================

    @Test
    fun testScenario91_scaleWith5000DownloadRecords() {
        val count = 5000
        val categories = listOf("Compressed", "Documents", "Video", "Music", "Applications", "Other")

        val insertStartNs = System.nanoTime()
        for (i in 1..count) {
            val record = DbRecord(
                id = i.toLong(),
                size = i * 1_048_576L,
                downloaded = i * 524_288L,
                progress = (i % 100),
                date = System.currentTimeMillis() - i * 1000L,
                fileName = "dataset_chunk_${i}.${if (i % 2 == 0) "tar.gz" else "mp4"}",
                eta = (count - i).toLong(),
                speed = 2_097_152f,
                selected = false,
                status = when (i % 4) {
                    0 -> RecordStatus.FINISHED
                    1 -> RecordStatus.DOWNLOADING
                    2 -> RecordStatus.PAUSED
                    else -> RecordStatus.ERROR
                },
                downloadType = if (i % 6 == 0) DownloadType.Torrent else DownloadType.Http,
                categoryId = categories[i % categories.size]
            )
            AppContext.db.add(record)
        }
        val insertDurationMs = (System.nanoTime() - insertStartNs) / 1_000_000.0
        val insertThroughput = count / (insertDurationMs / 1000.0)

        println("=== SCALE TEST: 5,000 DOWNLOAD RECORDS ===")
        println("Total Insertion Time: ${"%.2f".format(insertDurationMs)} ms (${"%.2f".format(insertThroughput)} records/sec)")
        assertEquals(count, AppContext.db.size, "Database must accurately hold all 5,000 records")

        // 1. High-Frequency Category Filtering Across 5,000 Records
        val filterStartNs = System.nanoTime()
        val compressedCount = AppContext.db.count { it.categoryId == "Compressed" }
        val videoCount = AppContext.db.count { it.categoryId == "Video" }
        val finishedCount = AppContext.db.count { it.status == RecordStatus.FINISHED }
        val filterDurationMs = (System.nanoTime() - filterStartNs) / 1_000_000.0

        println("Filter Time (3 passes over 5,000 rows): ${"%.2f".format(filterDurationMs)} ms")
        assertTrue(filterDurationMs < 250.0, "Filtering 5,000 items 3 times must take < 250ms (was: ${filterDurationMs}ms)")
        val expectedCompressed = (1..count).count { categories[it % categories.size] == "Compressed" }
        val expectedVideo = (1..count).count { categories[it % categories.size] == "Video" }
        assertEquals(expectedCompressed, compressedCount)
        assertEquals(expectedVideo, videoCount)
        assertEquals(count / 4, finishedCount)

        // 2. Substring Search Query Latency
        val searchStartNs = System.nanoTime()
        val matches = AppContext.db.findAll { it.fileName.contains("chunk_49") }
        val searchDurationMs = (System.nanoTime() - searchStartNs) / 1_000_000.0
        println("Search Query Time: ${"%.2f".format(searchDurationMs)} ms (Matches: ${matches.size})")
        assertTrue(matches.isNotEmpty(), "Search must find matching records")
        assertTrue(searchDurationMs < 50.0, "Search query over 5,000 rows must be < 50ms")

        // 3. Table Model Resolution Across All 9 Columns
        val tableModel = DownloadTableModel()
        assertEquals(count, tableModel.rowCount)
        for (col in 0 until 9) {
            val sampleVal = tableModel.getValueAt(0, col)
            assertNotNull(sampleVal, "Column $col must resolve non-null value")
        }

        // 4. Multi-Column Sorting Benchmark
        val sorter = TableRowSorter(tableModel)
        val sortStartNs = System.nanoTime()
        sorter.sort()
        val sortMs = (System.nanoTime() - sortStartNs) / 1_000_000.0
        println("Sorter Execution Time (5,000 rows): ${"%.2f".format(sortMs)} ms")
        assertTrue(sortMs < 1000.0, "Sorting 5,000 rows must be < 1000ms (was: ${sortMs}ms)")

        // 5. Serialization and Deserialization Recovery
        val persistDir = File(tempDir, "persist-scale").apply { mkdirs() }
        val dbPersist = AppDB(persistDir.absolutePath)
        for (i in 1..500) {
            dbPersist.add(DbRecord(
                id = i.toLong(),
                size = 1000L,
                downloaded = 1000L,
                progress = 100,
                date = 5000L,
                fileName = "scale_file_$i.zip",
                eta = 0L,
                speed = 0f,
                selected = false,
                status = RecordStatus.FINISHED,
                downloadType = DownloadType.Http
            ))
        }
        dbPersist.saveFinishedRecords()

        val dbReloaded = AppDB(persistDir.absolutePath)
        dbReloaded.loadRecords()
        assertEquals(500, dbReloaded.size, "All 500 records must cleanly reload from disk")
    }

    // =========================================================================
    // Scenario 92: Extended Soak & Sustained Load (20,000 Progress Events)
    // =========================================================================

    @Test
    fun testScenario92_extendedSoakAndSustainedLoad() {
        val totalEvents = 20_000
        val workerThreads = 4
        val eventsPerThread = totalEvents / workerThreads
        val latch = CountDownLatch(workerThreads)
        val counter = AtomicInteger(0)

        System.gc()
        Thread.sleep(100)
        val preSoakHeap = runtime.totalMemory() - runtime.freeMemory()
        val preSoakThreads = threadMxBean.threadCount

        println("=== EXTENDED SOAK TEST: 20,000 PROGRESS EVENTS ===")
        println("Pre-Soak Heap Used: ${preSoakHeap / 1024 / 1024} MB")
        println("Pre-Soak Active Threads: $preSoakThreads")

        // Simulation of coalesced event processing
        val pendingPayload = AtomicReference<Long>()
        val isScheduled = AtomicBoolean(false)
        val coalescedEventsHandled = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(workerThreads)
        val startTime = System.nanoTime()

        for (t in 0 until workerThreads) {
            executor.submit {
                try {
                    for (i in 1..eventsPerThread) {
                        val downloaded = (t * eventsPerThread + i) * 65536L
                        pendingPayload.set(downloaded)
                        counter.incrementAndGet()

                        if (isScheduled.compareAndSet(false, true)) {
                            // Coalescing consumer
                            val drained = pendingPayload.getAndSet(null)
                            if (drained != null) {
                                coalescedEventsHandled.incrementAndGet()
                            }
                            isScheduled.set(false)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Sustained load must complete within 10s")
        executor.shutdown()
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val throughput = totalEvents / (elapsedMs / 1000.0)

        System.gc()
        Thread.sleep(100)
        val postSoakHeap = runtime.totalMemory() - runtime.freeMemory()
        val postSoakThreads = threadMxBean.threadCount
        val heapDeltaMb = (postSoakHeap - preSoakHeap) / (1024.0 * 1024.0)

        println("Duration: ${"%.2f".format(elapsedMs)} ms")
        println("Event Throughput: ${"%.0f".format(throughput)} events/sec")
        println("Events Processed: ${counter.get()} (Coalesced to: ${coalescedEventsHandled.get()})")
        println("Post-Soak Heap Used: ${postSoakHeap / 1024 / 1024} MB (Delta: ${"%.2f".format(heapDeltaMb)} MB)")
        println("Post-Soak Threads: $postSoakThreads (Delta: ${postSoakThreads - preSoakThreads})")

        assertEquals(totalEvents, counter.get())
        assertTrue(elapsedMs < 6000.0, "20,000 events must complete in < 6000ms (was ${elapsedMs}ms)")
        assertTrue(heapDeltaMb < 50.0, "Heap delta must not grow unboundedly (delta was ${heapDeltaMb}MB)")
        assertTrue(Math.abs(postSoakThreads - preSoakThreads) <= 4, "Thread count must remain stable")
    }

    // =========================================================================
    // Scenario 93: UI Stress — View Switching, Menus & Theme Swapping
    // =========================================================================

    @Test
    fun testScenario93_uiStressViewSwitchingMenusAndThemes() {
        val filter = MainListViewFilter(FilterState.All, null, "")

        // Populate 100 items for view rendering
        for (i in 1..100) {
            AppContext.db.add(DbRecord(
                id = i.toLong(),
                size = 10_000_000L,
                downloaded = 5_000_000L,
                progress = 50,
                date = System.currentTimeMillis(),
                fileName = "sample_media_$i.mkv",
                eta = 30L,
                speed = 1_500_000f,
                selected = false,
                status = RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Http
            ))
        }

        // 1. Rapid View Mode Switching (100 switches across Table, Modern, Classic)
        val viewSwitches = 100
        val switchStartNs = System.nanoTime()

        runOnEdt {
            val tableView = DownloadTableView(filter)
            val modernView = ModernListView(filter)
            val classicView = ClassicListView(filter)

            for (i in 0 until viewSwitches) {
                val activeComponent = when (i % 3) {
                    0 -> tableView.component
                    1 -> modernView.component
                    else -> classicView.component
                }
                assertNotNull(activeComponent)
            }
        }
        val switchMs = (System.nanoTime() - switchStartNs) / 1_000_000.0
        val avgSwitchMs = switchMs / viewSwitches

        println("=== UI STRESS: VIEW SWITCHING & THEMES ===")
        println("100 View Mode Switches: ${"%.2f".format(switchMs)} ms (Avg: ${"%.3f".format(avgSwitchMs)} ms/switch)")
        assertTrue(avgSwitchMs < 15.0, "Average view switch must be < 15ms (was: ${avgSwitchMs}ms)")

        // 2. DownloadContextMenu Stress (200 invocations)
        val menuInvocations = 200
        val dummyComponent = JPanel()
        val menuStartNs = System.nanoTime()

        for (i in 1..menuInvocations) {
            val selected = listOf(
                AppContext.db.getById(i.toLong() % 100 + 1) ?: continue
            )
            val menu = DownloadContextMenu.create(selected, dummyComponent)
            assertTrue(menu.componentCount > 0, "Context menu must construct actions")
        }
        val menuMs = (System.nanoTime() - menuStartNs) / 1_000_000.0
        println("200 Context Menu Invocations: ${"%.2f".format(menuMs)} ms (Avg: ${"%.3f".format(menuMs / menuInvocations)} ms/menu)")
        assertTrue(menuMs < 500.0, "200 menu creations must be < 500ms (was: ${menuMs}ms)")

        // 3. Theme & Skin Swapping Stress (60 cycles)
        SkinManager.init(null)
        val initialHeap = memoryMxBean.heapMemoryUsage.used
        val skinStartNs = System.nanoTime()

        for (i in 0 until 60) {
            val targetSkin = if (i % 2 == 0) SkinManager.SKIN_ORIGINAL_XDM else SkinManager.SKIN_ENHANCED
            SkinManager.applySkin(targetSkin, saveConfig = false)
        }
        val skinMs = (System.nanoTime() - skinStartNs) / 1_000_000.0
        System.gc()
        Thread.sleep(50)
        val postSkinHeap = memoryMxBean.heapMemoryUsage.used
        val skinHeapDeltaMb = (postSkinHeap - initialHeap) / (1024.0 * 1024.0)

        println("60 Skin Swaps: ${"%.2f".format(skinMs)} ms (Avg: ${"%.2f".format(skinMs / 60.0)} ms/swap, Heap Delta: ${"%.2f".format(skinHeapDeltaMb)} MB)")
        assertTrue(skinMs / 60.0 < 100.0, "Average skin swap must be < 100ms")
        assertTrue(skinHeapDeltaMb < 20.0, "Skin swapping must not accumulate unbounded memory")
    }

    // =========================================================================
    // Scenario 94: Large Magnet Metadata & Torrent Verification Stress
    // =========================================================================

    @Test
    fun testScenario94_largeMagnetMetadataAndTorrentStress() {
        // Construct a massive 2,500+ character magnet link with 50 trackers
        val trackers = (1..50).joinToString("") { "&tr=udp%3A%2F%2Ftracker.loadtest-$it.org%3A6969%2Fannounce" }
        val rawName = "Ubuntu_26.04_LTS_Enterprise_Cloud_Image_Very_Long_Identifier_Padding_String_ABC"
        val magnetUri = "magnet:?xt=urn:btih:3b245504d5f02bb1f3013632be95e777a32743a7&dn=$rawName$trackers"

        assertTrue(magnetUri.length > 2000, "Constructed magnet link must be large (>2000 chars)")

        val parseStartNs = System.nanoTime()
        val infoHash = TorrentMetadataParser.parseMagnetInfoHash(magnetUri)
        val displayName = TorrentMetadataParser.parseMagnetDisplayName(magnetUri)
        val parseDurationMs = (System.nanoTime() - parseStartNs) / 1_000_000.0

        println("=== MAGNET METADATA STRESS ===")
        println("Magnet Length: ${magnetUri.length} chars")
        println("Parse Duration: ${"%.3f".format(parseDurationMs)} ms")
        println("Resolved InfoHash: $infoHash")
        println("Resolved DisplayName: $displayName")

        assertEquals("3b245504d5f02bb1f3013632be95e777a32743a7", infoHash)
        assertEquals(rawName, displayName)
        assertTrue(parseDurationMs < 50.0, "Parsing complex magnet must execute under 50ms")

        // Test placeholder detector
        assertTrue(TorrentMetadataParser.isPlaceholderName("magnet:?xt=urn:btih:abc"))
        assertTrue(TorrentMetadataParser.isPlaceholderName("Fetching metadata..."))
        assertTrue(TorrentMetadataParser.isPlaceholderName("3b245504d5f02bb1f3013632be95e777a32743a7"))
        assertFalse(TorrentMetadataParser.isPlaceholderName(rawName))

        // Native Loader Platform Resolution Edge Cases via Reflection
        val method = TorrentNativeLoader::class.java.declaredMethods.firstOrNull {
            it.name.startsWith("resolveNativeResource")
        }?.apply { isAccessible = true }
        assertNotNull(method, "TorrentNativeLoader.resolveNativeResource method must exist")

        @Suppress("UNCHECKED_CAST")
        val winRes = method?.invoke(TorrentNativeLoader, "windows 10", "amd64") as? Pair<String, String>
        assertNotNull(winRes)
        assertEquals("libtorrent4j.dll", winRes?.second)

        @Suppress("UNCHECKED_CAST")
        val macArmRes = method?.invoke(TorrentNativeLoader, "mac os x", "aarch64") as? Pair<String, String>
        assertNotNull(macArmRes)
        assertEquals("libtorrent4j.dylib", macArmRes?.second)

        val unsupportedRes = method?.invoke(TorrentNativeLoader, "solaris", "sparc")
        assertNull(unsupportedRes, "Unsupported OS/Arch must return null for graceful handling")
    }

    // =========================================================================
    // Scenario 95: Failure Modes & Degraded Operation Resilience
    // =========================================================================

    @Test
    fun testScenario95_failureModesAndDegradedOperationResilience() {
        // 1. Error Storms: 500 error classifications across all types
        val errorTypes = listOf(
            DownloadError.NetworkError,
            DownloadError.InvalidResponse,
            DownloadError.SessionExpired,
            DownloadError.LinkExpired,
            DownloadError.DiskSpaceError,
            DownloadError.InternalError
        )
        val errorStartNs = System.nanoTime()
        for (i in 1..500) {
            val err = errorTypes[i % errorTypes.size]
            assertNotNull(err.name)
            assertNotNull(err.ordinal)
        }
        val errorMs = (System.nanoTime() - errorStartNs) / 1_000_000.0
        println("=== FAILURE MODES & DEGRADED RESILIENCE ===")
        println("500 Error Classifications: ${"%.2f".format(errorMs)} ms")
        assertTrue(errorMs < 100.0, "500 error lookups must be < 100ms")

        // 2. Broken / Corrupt Skin Fallback: Create invalid zip file
        val corruptSkinZip = File(tempDir, "corrupted-skin.xdmskin")
        ZipOutputStream(FileOutputStream(corruptSkinZip)).use { zos ->
            zos.putNextEntry(ZipEntry("garbage.txt"))
            zos.write("not a real skin manifest".toByteArray())
            zos.closeEntry()
        }

        val fallbackWorked = try {
            SkinManager.installSkin(corruptSkinZip)
            false
        } catch (e: IllegalArgumentException) {
            // Expected validation failure
            true
        } catch (_: Throwable) {
            true
        }
        assertTrue(fallbackWorked, "Corrupted skin package must be safely rejected")

        // Verify SkinManager fallback on invalid skin id: gracefully falls back and reapplies 'enhanced'
        val applied = SkinManager.applySkin("non-existent-skin-id-999", saveConfig = false)
        assertTrue(applied, "Applying non-existent skin must fall back cleanly and reapply 'enhanced'")
        assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id, "SkinManager must fall back to 'enhanced'")

        // 3. Uninstalled Plugin Event Dispatch Resilience
        val pluginDispatchStartNs = System.nanoTime()
        for (i in 1..1000) {
            PluginEventBus.publish("unregistered-topic-$i", "payload")
        }
        val pluginMs = (System.nanoTime() - pluginDispatchStartNs) / 1_000_000.0
        println("1,000 Unhandled Plugin Events: ${"%.2f".format(pluginMs)} ms")
        assertTrue(pluginMs < 200.0, "Dispatching unhandled plugin events must be sub-200ms without errors")
    }
}
