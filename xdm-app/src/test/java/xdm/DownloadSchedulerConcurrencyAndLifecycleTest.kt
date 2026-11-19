package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.*
import xdm.core.downloaders.TaskInfoDB
import xdm.manager.DownloadCategory
import xdm.manager.EngineContext
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrency stress tests and lifecycle verification for [DownloadScheduler] and [AppInstance].
 */
class DownloadSchedulerConcurrencyAndLifecycleTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var configDir: File
    private lateinit var appDB: AppDB
    private lateinit var scheduler: DownloadScheduler

    @BeforeEach
    fun setUp() {
        configDir = tempDir.resolve("config").toFile().apply { mkdirs() }
        val config = AppConfig(configDir.absolutePath).apply {
            defaultDownloadFolder = configDir.absolutePath
            categories = DownloadCategory.defaults(configDir.absolutePath)
        }
        AppContext.config = config
        AppContext.configDir = configDir.absolutePath
        AppContext.taskInfoDB = TaskInfoDB(configDir.absolutePath)
        AppContext.tempDir = configDir.absolutePath
        AppContext.defaultDownloadFolder = configDir.absolutePath
        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, _, _ -> null } as IAppInstance
        EngineContext.host = DesktopEngineHost

        appDB = AppDB(configDir.absolutePath)
        AppContext.db = appDB
        scheduler = DownloadScheduler(appDB, configDir.absolutePath)
        AppContext.scheduler = scheduler
    }

    @AfterEach
    fun tearDown() {
        scheduler.stop()
    }

    @Test
    fun testConcurrentSchedulerAccessUnderContention() {
        val threadCount = 8
        val iterationsPerThread = 200
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        // Pre-populate some entries
        for (i in 1..20) {
            scheduler.addEntry(
                ScheduleEntry(
                    downloadId = i.toLong(),
                    scheduleType = ScheduleType.WEEKLY,
                    hour = 12,
                    minute = 0,
                    daysOfWeek = setOf(1, 2, 3),
                    epochMillis = -1L
                )
            )
        }

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until iterationsPerThread) {
                        val id = (i % 30 + 1).toLong()
                        when (i % 4) {
                            0 -> {
                                scheduler.contains(id)
                            }
                            1 -> {
                                scheduler.getEntryFor(id)
                            }
                            2 -> {
                                scheduler.addEntry(
                                    ScheduleEntry(
                                        downloadId = 1000L + id,
                                        scheduleType = ScheduleType.ONE_TIME,
                                        hour = 10,
                                        minute = 30,
                                        daysOfWeek = emptySet(),
                                        epochMillis = System.currentTimeMillis() + 100_000
                                    )
                                )
                            }
                            3 -> {
                                scheduler.removeEntry(1000L + id)
                            }
                        }
                        // Also interleave tick() calls to simulate background runner
                        if (i % 25 == 0) {
                            scheduler.tick()
                        }
                    }
                } catch (t: Throwable) {
                    errorCount.incrementAndGet()
                    t.printStackTrace()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(15, TimeUnit.SECONDS)
        executor.shutdownNow()

        assertTrue(finished, "Concurrent worker threads did not finish within timeout")
        assertEquals(0, errorCount.get(), "Concurrent access produced errors or ConcurrentModificationException")
    }

    @Test
    fun testGracefulShutdownStopsScheduler() {
        scheduler.start()
        assertTrue(scheduler.isRunning, "Scheduler must be running after start()")

        val appInstance = AppInstance()
        // Execute graceful shutdown without exiting VM
        appInstance.gracefulShutdown(timeoutMs = 1000, exitVm = false)

        assertFalse(scheduler.isRunning, "Scheduler must be stopped during gracefulShutdown()")
    }
}
