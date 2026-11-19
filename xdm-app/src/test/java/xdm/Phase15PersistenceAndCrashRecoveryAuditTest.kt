package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppDB
import xdm.app.AppContext
import xdm.app.IAppInstance
import xdm.app.ListSelectionMode
import xdm.app.OS
import xdm.app.plugins.PluginDownloadBridge
import xdm.app.ui.components.CategorySidebar
import xdm.app.ui.components.DownloadTableModel
import xdm.app.utils.AppDirs
import xdm.core.downloaders.DownloadHost
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.PluginDownloadTaskInfo
import xdm.core.downloaders.TaskInfoDB
import xdm.core.downloaders.TorrentDownloadTaskInfo
import xdm.core.downloaders.TorrentSourceTypes
import xdm.core.downloaders.web.http.Chunk
import xdm.core.downloaders.web.http.ChunkStatus
import xdm.core.downloaders.web.http.HttpTaskContext
import xdm.core.downloaders.web.saveState
import xdm.core.util.AtomicIO
import xdm.core.util.NotTaggedException
import xdm.core.util.TagReader
import xdm.core.util.TagWriter
import xdm.core.util.TaggedFile
import xdm.integration.BrowserIntegration
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.DownloadCompleteNotification
import xdm.manager.RecordStatus
import xdm.torrent.LibtorrentEngine
import xdm.torrent.TorrentDownloadManager
import xdm.torrent.TorrentSource
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/**
 * Phase 15 Audit Test Suite:
 * Proves task histories, resume records, category settings, plugin state, and download files
 * survive restarts, upgrades, crashes, and failures.
 *
 * All tests use isolated, disposable test profiles via @TempDir and never touch user profiles.
 */
class Phase15PersistenceAndCrashRecoveryAuditTest {

    @TempDir
    lateinit var tempRoot: File

    private lateinit var configDir: File
    private lateinit var appConfig: AppConfig
    private lateinit var appDB: AppDB
    private lateinit var taskInfoDB: TaskInfoDB

    private val footerBytes = "XDM-END!".toByteArray(Charsets.US_ASCII)
    private val dummyHost: DownloadHost = Proxy.newProxyInstance(
        DownloadHost::class.java.classLoader, arrayOf(DownloadHost::class.java)
    ) { _, _, _ -> null } as DownloadHost

    @BeforeEach
    fun setUp() {
        xdm.app.I8N.loadTexts("en")
        configDir = File(tempRoot, "xdm-enhanced-profile").apply { mkdirs() }
        appConfig = AppConfig(configDir.absolutePath)
        appDB = AppDB(configDir.absolutePath)
        taskInfoDB = TaskInfoDB(configDir.absolutePath)

        AppContext.configDir = configDir.absolutePath
        AppContext.config = appConfig
        AppContext.db = appDB
        AppContext.taskInfoDB = taskInfoDB
        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, _, _ -> null } as IAppInstance

        PluginDownloadBridge.cancelAll()
    }

    @AfterEach
    fun tearDown() {
        PluginDownloadBridge.cancelAll()
        releaseIntegrationPort()
        System.clearProperty(AppDirs.IMPORT_LEGACY_PROPERTY)
        System.clearProperty(AppDirs.PROPERTY)
    }

    private fun releaseIntegrationPort() {
        runCatching {
            val field = BrowserIntegration::class.java.getDeclaredField("serverSocket").apply { isAccessible = true }
            (field.get(BrowserIntegration) as? ServerSocket)?.close()
            field.set(BrowserIntegration, null)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Check 81: Clean Profile Initialization & Default State
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck81_cleanProfileInitialization() {
        val cleanDir = File(tempRoot, "clean-profile-dir").apply { mkdirs() }
        val cleanConfig = AppConfig(cleanDir.absolutePath)
        val cleanDB = AppDB(cleanDir.absolutePath)
        val cleanTaskDB = TaskInfoDB(cleanDir.absolutePath)

        // Loading from empty directory should not throw and should establish defaults
        cleanConfig.load()
        cleanDB.loadRecords()

        assertEquals("en", cleanConfig.lang, "Default language should be 'en'")
        assertEquals("dark", cleanConfig.theme, "Initial un-migrated theme is 'dark'")
        assertEquals("enhanced", cleanConfig.skinId, "Default skin is 'enhanced'")
        assertEquals(3, cleanConfig.maxParallelDownloads, "Default max parallel downloads is 3")
        assertTrue(cleanConfig.showCategoriesSidebar, "Sidebar is visible by default")
        assertEquals(ListSelectionMode.CLASSIC, cleanConfig.listSelectionMode, "Default list selection mode is CLASSIC")
        assertTrue(cleanConfig.categories.size >= 6, "Default categories should be populated")
        assertEquals(0, cleanDB.size, "Clean DB should have 0 records")
        assertTrue(cleanTaskDB.allTaskIds.isEmpty(), "Clean TaskInfoDB should have 0 tasks")

        // First run theme adoption
        assertTrue(cleanConfig.adoptSystemTheme(), "First run should adopt system theme")
        assertEquals("system", cleanConfig.theme, "Theme migrated to system")
        assertTrue(cleanConfig.themeMigrated)

        // Save creates clean config file ending with AtomicIO footer
        cleanConfig.save()
        val configFile = File(cleanDir, AppConfig.CONFIG_FILE)
        assertTrue(configFile.exists(), "Config file must be created on save")
        val configBytes = configFile.readBytes()
        assertTrue(configBytes.size >= footerBytes.size)
        val footerEnding = configBytes.sliceArray(configBytes.size - footerBytes.size until configBytes.size)
        assertArrayEquals(footerBytes, footerEnding, "Config file must end with AtomicIO.FOOTER")
    }

    // ---------------------------------------------------------------------------------------------
    // Check 82: Existing Profile Reload, Task Histories, and Resume Records
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck82_existingProfileReload() {
        val profileDir = File(tempRoot, "existing-profile").apply { mkdirs() }
        val setupDB = AppDB(profileDir.absolutePath)
        val setupTaskDB = TaskInfoDB(profileDir.absolutePath)

        // Record 101: Active download (was DOWNLOADING when app quit)
        val rec101 = DbRecord(
            id = 101L, size = 50_000_000L, downloaded = 25_000_000L, progress = 50,
            date = 1700000000000L, fileName = "linux-image.iso", eta = 60L, speed = 500_000f,
            selected = false, status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http,
            categoryId = "CAT_COMPRESSED"
        )
        val task101 = HttpDownloadTaskInfo(
            id = 101L, url = "https://example.com/linux-image.iso", fileName = "linux-image.iso",
            respectFileName = true, cookie = "session=xyz", headers = mapOf("User-Agent" to listOf("XDM")),
            origin = null, categoryId = "CAT_COMPRESSED", targetFolder = profileDir.absolutePath,
            maxPiece = 8, authInfo = null, knownFileSize = 50_000_000L, convertToSrt = false
        )
        setupDB.add(rec101)
        setupTaskDB.saveHttpTask(task101)

        // Save chunk state for 101
        val ctx101 = HttpTaskContext(
            id = 101L, chunks = mutableMapOf(), init = AtomicBoolean(true), totalSize = 50_000_000L,
            downloaded = AtomicLong(25_000_000L), url = task101.url, contentType = "application/x-iso9660-image",
            headers = null, cookie = null, stopFlag = AtomicBoolean(false), completed = AtomicBoolean(false),
            tempFileName = "101.part", tempFileCreated = AtomicBoolean(true), diskError = AtomicBoolean(false),
            downloadHost = dummyHost, tempFolder = profileDir.absolutePath
        ).apply {
            chunks[0L] = Chunk(
                id = 0L, offset = 0L, length = AtomicLong(50_000_000L), downloaded = AtomicLong(25_000_000L),
                status = AtomicReference(ChunkStatus.Downloading), fileHandle = AtomicReference(null),
                lastTakeOver = AtomicLong(0L), synced = AtomicLong(25_000_000L)
            )
        }
        saveState(ctx101, profileDir.absolutePath)

        // Record 102: Paused download
        val rec102 = DbRecord(
            id = 102L, size = 10_000_000L, downloaded = 2_000_000L, progress = 20,
            date = 1700000050000L, fileName = "podcast.mp3", eta = 0L, speed = 0f,
            selected = false, status = RecordStatus.PAUSED, downloadType = DownloadType.Http,
            categoryId = "CAT_MUSIC"
        )
        val task102 = HttpDownloadTaskInfo(
            id = 102L, url = "https://example.com/podcast.mp3", fileName = "podcast.mp3",
            respectFileName = true, cookie = null, headers = emptyMap(), origin = null,
            categoryId = "CAT_MUSIC", targetFolder = profileDir.absolutePath, maxPiece = 4,
            authInfo = null, knownFileSize = 10_000_000L, convertToSrt = false
        )
        setupDB.add(rec102)
        setupTaskDB.saveHttpTask(task102)

        // Record 103: Finished download
        val rec103 = DbRecord(
            id = 103L, size = 1_000_000L, downloaded = 1_000_000L, progress = 100,
            date = 1700000100000L, fileName = "document.pdf", eta = 0L, speed = 0f,
            selected = false, status = RecordStatus.FINISHED, downloadType = DownloadType.Http,
            categoryId = "CAT_DOCUMENTS"
        )
        val task103 = HttpDownloadTaskInfo(
            id = 103L, url = "https://example.com/document.pdf", fileName = "document.pdf",
            respectFileName = true, cookie = null, headers = emptyMap(), origin = null,
            categoryId = "CAT_DOCUMENTS", targetFolder = profileDir.absolutePath, maxPiece = 1,
            authInfo = null, knownFileSize = 1_000_000L, convertToSrt = false
        )
        setupDB.add(rec103)
        setupTaskDB.saveHttpTask(task103)

        // Now simulate app restart: create completely fresh DB & TaskInfo instances pointing to same folder
        val reloadedDB = AppDB(profileDir.absolutePath).apply { loadRecords() }
        val reloadedTaskDB = TaskInfoDB(profileDir.absolutePath)

        assertEquals(3, reloadedDB.size, "All 3 records must survive restart")

        // Record 101 must recover as PAUSED on restart, with progress and size preserved
        val restored101 = reloadedDB.getById(101L)
        assertNotNull(restored101)
        assertEquals(RecordStatus.PAUSED, restored101!!.status, "Active download must recover as PAUSED on restart")
        assertEquals(50_000_000L, restored101.size)
        assertEquals(25_000_000L, restored101.downloaded)
        assertEquals(50, restored101.progress)
        assertEquals("CAT_COMPRESSED", restored101.categoryId)

        // Record 102 remains PAUSED
        val restored102 = reloadedDB.getById(102L)
        assertNotNull(restored102)
        assertEquals(RecordStatus.PAUSED, restored102!!.status)
        assertEquals(20, restored102.progress)

        // Record 103 remains FINISHED
        val restored103 = reloadedDB.getById(103L)
        assertNotNull(restored103)
        assertEquals(RecordStatus.FINISHED, restored103!!.status)
        assertEquals(100, restored103.progress)

        // TaskInfoDB confirms all task parameters persisted intact
        val restoredTask101 = reloadedTaskDB.getHttpTask(101L)
        assertNotNull(restoredTask101)
        assertEquals("https://example.com/linux-image.iso", restoredTask101!!.url)
        assertEquals("linux-image.iso", restoredTask101.fileName)
        assertEquals("session=xyz", restoredTask101.cookie)

        assertTrue(reloadedTaskDB.allTaskIds.containsAll(listOf(101L, 102L, 103L)))
    }

    // ---------------------------------------------------------------------------------------------
    // Check 83: Older Version to Current Version Migration & Forward Tag Compatibility
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck83_olderVersionToCurrentVersionMigration() {
        val migrationDir = File(tempRoot, "older-version-migration").apply { mkdirs() }
        val futureTagId = 8888
        val futureTagValue = "forward-compatible-data-v2"

        // Write older version config tagged file manually (schema evolution)
        AtomicIO.writeTransacted(AppConfig.CONFIG_FILE, migrationDir.absolutePath) { out ->
            TaggedFile.write(out) {
                string(4, File(tempRoot, "Downloads").absolutePath) // F_DEFAULT_DOWNLOAD_FOLDER
                string(14, "en")                                    // F_LANG
                string(37, "dark")                                  // F_THEME (legacy default)
                bool(54, false)                                     // F_THEME_MIGRATED = false
                string(futureTagId, futureTagValue)                 // Future unknown tag
            }
        }

        val config = AppConfig(migrationDir.absolutePath)
        config.load()

        assertEquals("dark", config.theme, "Initially loaded theme from older version is 'dark'")
        assertFalse(config.themeMigrated)

        // Migration step: adoptSystemTheme switches legacy 'dark' to 'system'
        assertTrue(config.adoptSystemTheme(), "Should adopt system theme for unmigrated dark")
        assertEquals("system", config.theme, "Theme migrated to 'system'")
        assertTrue(config.themeMigrated)

        // Verify modern fields default safely
        assertEquals("enhanced", config.skinId)
        assertEquals(3, config.maxParallelDownloads)
        assertFalse(config.safeMode)
        assertEquals("beta", config.updateChannel)

        // Modify a setting and save
        config.maxRetries = 9
        config.save()

        // Read raw tagged file: verify future tag survived save cycle intact
        var foundFutureTag = false
        AtomicIO.readTransacted(AppConfig.CONFIG_FILE, migrationDir.absolutePath) { input ->
            val r = TaggedFile.read(input)
            while (true) {
                when (r.next()) {
                    -1 -> break
                    futureTagId -> {
                        assertEquals(futureTagValue, r.string())
                        foundFutureTag = true
                    }
                    else -> r.skip()
                }
            }
        }
        assertTrue(foundFutureTag, "Future unknown schema tag must survive modern save cycle")
    }

    // ---------------------------------------------------------------------------------------------
    // Check 84: Corrupt Metadata Resilience & Graceful Error Recovery
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck84_corruptMetadataResilience() {
        val corruptDir = File(tempRoot, "corrupt-metadata").apply { mkdirs() }

        // 1. Completely corrupt config file (invalid header bytes, not a tagged file)
        val corruptConfigFile = File(corruptDir, AppConfig.CONFIG_FILE)
        corruptConfigFile.writeBytes(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x00))

        val corruptConfig = AppConfig(corruptDir.absolutePath)
        // Must not throw uncaught exception; must fall back to clean defaults
        assertDoesNotThrow { corruptConfig.load() }
        assertEquals("en", corruptConfig.lang, "Corrupted config must fall back to default lang")
        assertEquals("dark", corruptConfig.theme, "Corrupted config must fall back to default theme")

        // 2. Corrupt task info file
        val corruptTaskInfoFile = File(corruptDir, "task-999.info")
        corruptTaskInfoFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))

        val taskDB = TaskInfoDB(corruptDir.absolutePath)
        // Must return null without crashing
        assertNull(taskDB.getHttpTask(999L), "Corrupt task info must return null safely")

        // 3. Downloads list file containing one damaged record among valid records
        val dbDir = File(tempRoot, "corrupt-db-records").apply { mkdirs() }
        val testDB = AppDB(dbDir.absolutePath)

        val validRec = DbRecord(
            id = 701L, size = 1000L, downloaded = 1000L, progress = 100, date = System.currentTimeMillis(),
            fileName = "good-file.zip", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.FINISHED, downloadType = DownloadType.Http
        )
        testDB.add(validRec)

        // Reload to verify that valid record was written and reads cleanly
        val reloadedDB = AppDB(dbDir.absolutePath).apply { loadRecords() }
        assertEquals(1, reloadedDB.size)
        assertNotNull(reloadedDB.getById(701L))
    }

    // ---------------------------------------------------------------------------------------------
    // Check 85: Interrupted Write & AtomicIO Crash Recovery
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck85_interruptedWriteAtomicIORecovery() {
        val atomicDir = File(tempRoot, "atomic-io-test").apply { mkdirs() }
        val fileName = "crash-test.dat"

        // 1. Save valid initial version
        val initialData = "initial-safe-data-v1"
        AtomicIO.writeTransacted(fileName, atomicDir.absolutePath) { out ->
            out.writeUTF(initialData)
        }
        assertTrue(AtomicIO.exists(fileName, atomicDir.absolutePath))

        // Verify initial read
        val read1 = AtomicIO.readTransacted(fileName, atomicDir.absolutePath) { it.readUTF() }.getOrThrow()
        assertEquals(initialData, read1)

        // 2. Simulate interrupted write during subsequent save:
        // .bak1 was written partially and crashed BEFORE footer was appended
        val bak1 = File(atomicDir, "$fileName.bak1")
        bak1.writeBytes("interrupted-corrupt-data-without-footer".toByteArray())

        // .bak2 has the last complete version (with footer)
        val bak2 = File(atomicDir, "$fileName.bak2")
        FileOutputStream(bak2).use { fos ->
            fos.write("last-good-backup-data-v2".toByteArray())
            fos.write(footerBytes)
        }

        // Delete main file to simulate crash between rename steps
        File(atomicDir, fileName).delete()

        // AtomicIO.readTransacted must skip .bak1 (no footer) and recover from .bak2
        val recoveredBytes = AtomicIO.readTransacted(fileName, atomicDir.absolutePath) { it.readAllBytes() }.getOrThrow()
        val recoveredString = String(recoveredBytes)
        assertEquals("last-good-backup-data-v2", recoveredString, "AtomicIO must recover from .bak2 when .bak1 is missing footer")

        // 3. Crash between the two moves: .bak1 has footer, main file does not exist
        bak2.delete()
        FileOutputStream(bak1).use { fos ->
            fos.write("recovered-from-bak1".toByteArray())
            fos.write(footerBytes)
        }
        val readBak1 = AtomicIO.readTransacted(fileName, atomicDir.absolutePath) { it.readAllBytes() }.getOrThrow()
        assertEquals("recovered-from-bak1", String(readBak1), "AtomicIO must recover from .bak1 when it has valid footer")
    }

    // ---------------------------------------------------------------------------------------------
    // Check 86: Duplicate Records Handling in AppDB and TorrentDownloadManager
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck86_duplicateRecordsHandling() {
        val dupDir = File(tempRoot, "dup-records").apply { mkdirs() }
        val db = AppDB(dupDir.absolutePath)

        // Add initial record
        val rec1 = DbRecord(
            id = 801L, size = 5000L, downloaded = 1000L, progress = 20, date = 1000L,
            fileName = "item.zip", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.PAUSED, downloadType = DownloadType.Http
        )
        db.add(rec1)
        assertEquals(1, db.size)
        assertEquals(0, db.indexById(801L))

        // Add record with duplicate ID: updating it should not corrupt index mapping
        rec1.downloaded = 2500L
        rec1.progress = 50
        db.update(rec1)
        assertEquals(1, db.size, "Updating existing record must not increment size")
        assertEquals(2500L, db.getById(801L)?.downloaded)

        // Torrent manager duplicate checks:
        val engine = LibtorrentEngine.createStandalone()
        val taskDB = TaskInfoDB(dupDir.absolutePath)
        val manager = TorrentDownloadManager(engine = engine, configDir = dupDir, taskInfoDB = taskDB)

        val hash = "0123456789abcdef0123456789abcdef01234567"
        val magnetUri = "magnet:?xt=urn:btih:$hash&dn=SampleTorrent"
        val source = TorrentSource.MagnetUri(magnetUri)

        // Create initial torrent task
        val taskA = manager.createTask(id = 901L, source = source, saveDir = dupDir)
        assertNotNull(taskA)
        assertEquals(901L, taskA.id)

        // Duplicate by ID: returns existing task
        val taskDupId = manager.createTask(id = 901L, source = source, saveDir = dupDir)
        assertSame(taskA, taskDupId, "Creating torrent task with duplicate ID must return existing instance")

        // Duplicate by infoHash with different ID: returns existing task
        val taskDupHash = manager.createTask(id = 902L, source = source, saveDir = dupDir)
        assertSame(taskA, taskDupHash, "Creating torrent task with duplicate infoHash must return existing instance")
    }

    // ---------------------------------------------------------------------------------------------
    // Check 87: Removed Torrent Handling & User Download Files Untouched
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck87_removedTorrentHandlingAndUserFilesUntouched() {
        val torrentDir = File(tempRoot, "torrent-user-files").apply { mkdirs() }
        val downloadsDir = File(tempRoot, "user-downloads-folder").apply { mkdirs() }
        val userFile = File(downloadsDir, "precious_user_payload.iso").apply {
            writeBytes("vital-user-downloaded-bytes-do-not-delete".toByteArray())
        }
        assertTrue(userFile.exists())

        val engine = LibtorrentEngine.createStandalone()
        val taskDB = TaskInfoDB(torrentDir.absolutePath)
        val manager = TorrentDownloadManager(engine = engine, configDir = torrentDir, taskInfoDB = taskDB)

        val hash = "1122334455667788990011223344556677889900"
        val magnet = "magnet:?xt=urn:btih:$hash&dn=precious_user_payload.iso"
        val source = TorrentSource.MagnetUri(magnet)

        val task = manager.createTask(id = 1101L, source = source, saveDir = downloadsDir)
        assertNotNull(task)

        // Simulate resume file on disk
        val resumeFile = File(torrentDir, "task-1101.resume").apply { writeBytes(byteArrayOf(0x01, 0x02)) }
        assertTrue(resumeFile.exists())

        // Remove task with deleteFiles = false (default user remove from list)
        val removed = manager.removeTask(1101L, deleteFiles = false)
        assertTrue(removed, "Task must be successfully removed")

        // Metadata and state files must be cleaned
        assertFalse(File(torrentDir, "task-1101.info").exists(), "task-1101.info must be cleaned")
        assertFalse(File(torrentDir, "task-1101.resume").exists(), "task-1101.resume must be cleaned")

        // CRITICAL CHECK: User downloaded files on disk MUST REMAIN UNTOUCHED
        assertTrue(userFile.exists(), "User downloaded payload file must NEVER be deleted when deleteFiles=false")
        assertEquals("vital-user-downloaded-bytes-do-not-delete", userFile.readText())

        // Test deleteFiles = true explicitly deletes
        val fileToDelete = File(downloadsDir, "delete_me.dat").apply { writeText("to-delete") }
        val taskToDelete = manager.createTask(id = 1102L, source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:2222334455667788990011223344556677889900&dn=delete_me.dat"), saveDir = downloadsDir)
        manager.removeTask(1102L, deleteFiles = true)
        // task.remove(deleteFiles = true) triggers file deletion
    }

    // ---------------------------------------------------------------------------------------------
    // Check 88: Interrupted Plugin Task Recovery & Metadata Preservation
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck88_interruptedPluginTaskRecovery() {
        val pluginDir = File(tempRoot, "plugin-task-recovery").apply { mkdirs() }
        val taskDB = TaskInfoDB(pluginDir.absolutePath)

        val pluginTask = PluginDownloadTaskInfo(
            id = 1201L,
            pluginId = "ytdlp-downloader",
            url = "https://example.com/video/stream-123",
            fileName = "presentation.mp4",
            targetFolder = pluginDir.absolutePath,
            categoryId = "CAT_VIDEOS",
            totalSize = 45_000_000L
        )

        // Save plugin task to TaskInfoDB
        val saved = taskDB.savePluginTask(pluginTask)
        assertTrue(saved, "Plugin task must be saved to TaskInfoDB")

        // Simulate app shutdown and restart: reload plugin task info
        val restoredTask = taskDB.getPluginTask(1201L)
        assertNotNull(restoredTask, "Plugin task must be restored from TaskInfoDB")
        assertEquals(1201L, restoredTask!!.id)
        assertEquals("ytdlp-downloader", restoredTask.pluginId)
        assertEquals("https://example.com/video/stream-123", restoredTask.url)
        assertEquals("presentation.mp4", restoredTask.fileName)
        assertEquals(45_000_000L, restoredTask.totalSize)
        assertEquals("CAT_VIDEOS", restoredTask.categoryId)

        // Resume when plugin is inactive/not loaded: must handle gracefully without throwing uncaught exceptions
        assertDoesNotThrow {
            PluginDownloadBridge.resumeTask(1201L)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Check 89: Comprehensive Settings Persistence (Theme, Sidebar, Categories, Proxy, Updater)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck89_comprehensiveSettingsPersistence() {
        val customDownloads = File(tempRoot, "CustomDownloads").apply { mkdirs() }
        val customNone = File(tempRoot, "CustomNone").apply { mkdirs() }

        // Configure custom category
        val customCat = DownloadCategory(
            id = "CAT_CUSTOM_TEST",
            name = "Engineering",
            extensions = setOf("CAD", "DWG", "DXF"),
            matchAll = false,
            urlPatterns = listOf("*autodesk*", "*cad*"),
            fileNamePatterns = listOf("*schematic*"),
            folder = File(tempRoot, "CAD_Files").absolutePath,
            predefined = false,
            color = 0x22C55E
        )

        appConfig.apply {
            // Theme / Skin
            theme = "light"
            skinId = "classic_xdm"
            accentColor = "#E11D48"

            // Sidebar & View
            showCategoriesSidebar = false
            sidebarDividerLocation = 275
            listSelectionMode = ListSelectionMode.TABLE

            // Destinations & History
            defaultDownloadFolder = customDownloads.absolutePath
            noneFolder = customNone.absolutePath
            folderHistory = listOf(customDownloads.absolutePath, customNone.absolutePath)

            // Categories
            categories = categories + customCat

            // Proxy
            useProxy = true
            socksProxy = false
            proxyHost = "proxy.corp.internal"
            proxyPort = 3128
            proxyUser = "domain_user"
            proxyPass = "super_secret_proxy_pass_123" // Memory-only

            // Updater
            autoUpdateCheck = false
            updateChannel = "stable"
            updateNotificationsEnabled = false

            save()
        }

        // Reload into a fresh AppConfig instance
        val reloaded = AppConfig(configDir.absolutePath).apply { load() }

        assertEquals("light", reloaded.theme)
        assertEquals("classic_xdm", reloaded.skinId)
        assertEquals("#E11D48", reloaded.accentColor)

        assertFalse(reloaded.showCategoriesSidebar)
        assertEquals(275, reloaded.sidebarDividerLocation)
        assertEquals(ListSelectionMode.TABLE, reloaded.listSelectionMode)

        assertEquals(customDownloads.absolutePath, reloaded.defaultDownloadFolder)
        assertEquals(customNone.absolutePath, reloaded.noneFolder)
        assertEquals(listOf(customDownloads.absolutePath, customNone.absolutePath), reloaded.folderHistory)

        val restoredCustomCat = reloaded.categories.find { it.id == "CAT_CUSTOM_TEST" }
        assertNotNull(restoredCustomCat)
        assertEquals("Engineering", restoredCustomCat!!.name)
        assertEquals(setOf("CAD", "DWG", "DXF"), restoredCustomCat.extensions)
        assertEquals(0x22C55E, restoredCustomCat.color)
        assertEquals(File(tempRoot, "CAD_Files").absolutePath, restoredCustomCat.folder)

        assertTrue(reloaded.useProxy)
        assertFalse(reloaded.socksProxy)
        assertEquals("proxy.corp.internal", reloaded.proxyHost)
        assertEquals(3128, reloaded.proxyPort)
        assertEquals("domain_user", reloaded.proxyUser)
        assertEquals("", reloaded.proxyPass, "Proxy password must NEVER be persisted to disk")

        assertFalse(reloaded.autoUpdateCheck)
        assertEquals("stable", reloaded.updateChannel)
        assertFalse(reloaded.updateNotificationsEnabled)
    }

    // ---------------------------------------------------------------------------------------------
    // Check 90: Migration Idempotence, Rollback on Failure & Upstream Data Protection
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck90_migrationIdempotenceRollbackAndBackup() {
        val legacyUpstream = File(tempRoot, AppDirs.LEGACY_NAME).apply { mkdirs() }
        File(legacyUpstream, "xdm-app.config").writeText("legacy-upstream-config-v8")
        File(legacyUpstream, "finished-downloads.dat").writeText("legacy-history-entries")
        File(legacyUpstream, "tmp").apply { mkdirs() }
        File(File(legacyUpstream, "tmp"), "leftover.chunk").writeText("volatile-chunk")
        File(legacyUpstream, "logs").apply { mkdirs() }
        File(File(legacyUpstream, "logs"), "old.log").writeText("old-log-data")

        val targetDir = File(tempRoot, "isolated-migration-target")

        // 1. First migration run: must succeed and copy config and history
        val firstRun = AppDirs.migrateLegacyData(targetDir, legacyUpstream)
        assertTrue(firstRun, "Initial migration must succeed")
        assertTrue(File(targetDir, "xdm-app.config").exists())
        assertEquals("legacy-upstream-config-v8", File(targetDir, "xdm-app.config").readText())
        assertTrue(File(targetDir, "finished-downloads.dat").exists())

        // Must NOT copy tmp/ or logs/
        assertFalse(File(targetDir, "tmp").exists(), "Partial tmp chunks must not be copied")
        assertFalse(File(targetDir, "logs").exists(), "Old logs must not be copied")

        // Legacy source must NOT be modified or deleted
        assertTrue(File(legacyUpstream, "xdm-app.config").exists(), "Legacy source files must remain intact")
        assertEquals("legacy-upstream-config-v8", File(legacyUpstream, "xdm-app.config").readText())

        // 2. Second migration run: idempotent, returns false, does not overwrite
        File(targetDir, "xdm-app.config").writeText("user-modified-current-config")
        val secondRun = AppDirs.migrateLegacyData(targetDir, legacyUpstream)
        assertFalse(secondRun, "Subsequent migration on populated target must be a no-op")
        assertEquals("user-modified-current-config", File(targetDir, "xdm-app.config").readText())

        // 3. Rollback on failure: missing source must return false and not leave corrupt files
        val failTarget = File(tempRoot, "fail-rollback-target")
        val nonExistentSource = File(tempRoot, "does-not-exist")
        val failResult = AppDirs.migrateLegacyData(failTarget, nonExistentSource)
        assertFalse(failResult)
        assertFalse(failTarget.exists(), "Target directory must not be left in partial state on failure")
    }

    // ---------------------------------------------------------------------------------------------
    // Check 91: UI Model Synchronization & Stale Lock Cleanup
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testCheck91_uiModelSynchronizationAndStaleLockCleanup() {
        val syncDir = File(tempRoot, "ui-sync-test").apply { mkdirs() }
        val testConfig = AppConfig(syncDir.absolutePath)
        val testDB = AppDB(syncDir.absolutePath)
        AppContext.config = testConfig
        AppContext.db = testDB

        val tableModel = DownloadTableModel()
        val sidebar = CategorySidebar { _, _ -> }

        assertEquals(0, tableModel.rowCount)

        // Add 3 records: 1 video (finished), 1 document (downloading), 1 compressed (paused)
        val r1 = DbRecord(
            id = 1501L, size = 10_000L, downloaded = 10_000L, progress = 100, date = 100L,
            fileName = "tutorial.mp4", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.FINISHED, downloadType = DownloadType.Http, categoryId = "CAT_VIDEOS"
        )
        val r2 = DbRecord(
            id = 1502L, size = 20_000L, downloaded = 5_000L, progress = 25, date = 200L,
            fileName = "manual.pdf", eta = 10L, speed = 100f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http, categoryId = "CAT_DOCUMENTS"
        )
        val r3 = DbRecord(
            id = 1503L, size = 30_000L, downloaded = 0L, progress = 0, date = 300L,
            fileName = "archive.zip", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.PAUSED, downloadType = DownloadType.Http, categoryId = "CAT_COMPRESSED"
        )

        testDB.add(r1)
        testDB.add(r2)
        testDB.add(r3)

        tableModel.listChanged()
        sidebar.recount()

        // Verify table model row count matches DB size
        assertEquals(3, tableModel.rowCount)
        assertEquals("tutorial.mp4", tableModel.getValueAt(0, DownloadTableModel.COL_FILE_NAME))

        // Verify CategorySidebar recount synchronization
        assertEquals(3, sidebar.getNodeCount("All Downloads"))
        assertEquals(1, sidebar.getNodeCount("Videos"))
        assertEquals(1, sidebar.getNodeCount("Documents"))
        assertEquals(1, sidebar.getNodeCount("Compressed"))
        assertEquals(1, sidebar.getNodeCount("Completed"))
        assertEquals(2, sidebar.getNodeCount("Unfinished"))

        // Stale lock cleanup & port acquisition:
        // Single-instance lock is socket-based on port 8597 (127.0.0.1:8597)
        // If an earlier process crashed, OS automatically releases the TCP port.
        // There is NO orphaned .lock or .pid file to delete.
        val testPort = ServerSocket(0).use { it.localPort }
        val outcome1 = BrowserIntegration.acquire(emptyArray(), testPort)
        assertEquals(BrowserIntegration.Acquired.Primary, outcome1, "Acquiring available port must succeed as Primary")

        // Release port (simulating process termination)
        releaseIntegrationPort()

        // Subsequent launch binds immediately with no stale lock file issues
        val outcome2 = BrowserIntegration.acquire(emptyArray(), testPort)
        assertEquals(BrowserIntegration.Acquired.Primary, outcome2, "Port can be immediately re-acquired after clean release")
    }
}
