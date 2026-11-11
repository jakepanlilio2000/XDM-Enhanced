package xdm.app

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.EngineContext
import xdm.manager.RecordStatus
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Path

class DownloadSchedulerTorrentTest {

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
    }

    @AfterEach
    fun tearDown() {
        scheduler.stop()
    }

    @Test
    fun testSchedulerEntryLifecycleAndPersistence() {
        val entry = ScheduleEntry(
            downloadId = 5555L,
            scheduleType = ScheduleType.ONE_TIME,
            hour = 12,
            minute = 30,
            daysOfWeek = emptySet(),
            epochMillis = System.currentTimeMillis() + 60000
        )

        scheduler.addEntry(entry)
        assertTrue(scheduler.contains(5555L))
        assertEquals(entry, scheduler.getEntryFor(5555L))

        // Create new scheduler from same configDir to test persistence reload
        val reloaded = DownloadScheduler(appDB, configDir.absolutePath)
        assertTrue(reloaded.contains(5555L))
        val loadedEntry = reloaded.getEntryFor(5555L)
        assertNotNull(loadedEntry)
        assertEquals(5555L, loadedEntry!!.downloadId)

        // Remove entry
        scheduler.removeEntry(5555L)
        assertFalse(scheduler.contains(5555L))

        val reloadedAfterRemove = DownloadScheduler(appDB, configDir.absolutePath)
        assertFalse(reloadedAfterRemove.contains(5555L))
    }

    @Test
    fun testSchedulerTorrentRecordHandling() {
        val torrentRecord = DbRecord(
            id = 7001L,
            size = 1024L * 1024L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = "ubuntu-24.04.iso",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Torrent
        )
        appDB.add(torrentRecord)

        val now = System.currentTimeMillis()
        val entry = ScheduleEntry(
            downloadId = 7001L,
            scheduleType = ScheduleType.ONE_TIME,
            hour = 0,
            minute = 0,
            daysOfWeek = emptySet(),
            epochMillis = now // Matches current minute bucket
        )
        scheduler.addEntry(entry)

        // Initializing AppContext configDir and db for safe lookup
        AppContext.configDir = configDir.absolutePath
        AppContext.db = appDB
        AppContext.tempDir = configDir.absolutePath
        AppContext.defaultDownloadFolder = configDir.absolutePath

        // Invoking tick() should process the entry without unhandled exceptions
        assertDoesNotThrow {
            scheduler.tick()
        }
    }
}

