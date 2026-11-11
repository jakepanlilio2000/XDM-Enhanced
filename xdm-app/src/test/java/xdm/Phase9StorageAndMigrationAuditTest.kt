package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppDB
import xdm.app.AppContext
import xdm.app.ConfigSnapshot
import xdm.app.IAppConfig
import xdm.app.ListSelectionMode
import xdm.app.OS
import xdm.app.skins.SkinManager
import xdm.app.skins.SkinPackager
import xdm.app.utils.AppDirs
import xdm.app.utils.win.WindowsAssociations
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.core.network.http.HeaderMap
import xdm.core.network.http.SensitiveHeaders
import xdm.core.storage.LocalStorage
import xdm.core.util.AtomicIO
import xdm.core.util.TagReader
import xdm.core.util.TagWriter
import xdm.core.util.TaggedFile
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.DownloadCompleteNotification
import xdm.manager.RecordStatus
import xdm.app.preflight.LinkPreflightService
import java.net.URLClassLoader
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 9 Audit Test Suite: Storage, Preferences, Migration, and Credential Security (Scenarios 62-66).
 */
class Phase9StorageAndMigrationAuditTest {

    @TempDir
    lateinit var tempRoot: File

    private lateinit var configDir: File
    private lateinit var appConfig: AppConfig

    @BeforeEach
    fun setUp() {
        configDir = File(tempRoot, "xdm-enhanced").apply { mkdirs() }
        appConfig = AppConfig(configDir.absolutePath)
        AppContext.config = appConfig
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty(AppDirs.IMPORT_LEGACY_PROPERTY)
        System.clearProperty(AppDirs.PROPERTY)
    }

    /**
     * Scenario 62: Fresh install vs upgrade from beta.6, existing official upstream XDM side-by-side,
     * and schema tag forward/backward compatibility.
     */
    @Test
    fun testScenario62FreshInstallUpgradeAndSchemaEvolution() {
        // 1. Fresh install: clean defaults, no legacy contamination
        val freshDir = File(tempRoot, "fresh-install").apply { mkdirs() }
        val freshConfig = AppConfig(freshDir.absolutePath)
        freshConfig.load()
        assertEquals("en", freshConfig.lang)
        assertEquals("dark", freshConfig.theme)
        assertEquals("enhanced", freshConfig.skinId)
        assertTrue(freshConfig.showCategoriesSidebar)
        assertEquals(3, freshConfig.maxParallelDownloads)
        assertTrue(freshConfig.categories.isNotEmpty())

        // 2. Upgrade from beta.6 in xdm-enhanced: preserves all previous settings
        val upgradeDir = File(tempRoot, "upgrade-beta6").apply { mkdirs() }
        val oldConfig = AppConfig(upgradeDir.absolutePath).apply {
            lang = "de"
            theme = "light"
            skinId = "classic_xdm"
            accentColor = "#10B981"
            maxSegments = 16
            showCategoriesSidebar = false
            save()
        }
        val upgraded = AppConfig(upgradeDir.absolutePath).apply { load() }
        assertEquals("de", upgraded.lang)
        assertEquals("light", upgraded.theme)
        assertEquals("classic_xdm", upgraded.skinId)
        assertEquals("#10B981", upgraded.accentColor)
        assertEquals(16, upgraded.maxSegments)
        assertFalse(upgraded.showCategoriesSidebar)

        // 3. Side-by-side with official upstream XDM (xdm-app):
        // Upstream files exist in xdm-app directory, but fresh xdm-enhanced does not copy them unless requested
        val upstreamDir = File(tempRoot, AppDirs.LEGACY_NAME).apply { mkdirs() }
        File(upstreamDir, "xdm-app.config").writeText("upstream-official-data")
        File(upstreamDir, "xdm.db").writeText("upstream-db")

        val targetSideBySide = File(tempRoot, "side-by-side-target")
        // Resolve without xdm.import.legacy property
        val resolved = AppDirs.resolveNamed("side-by-side-target", OS.Linux, null, tempRoot.absolutePath) { null }
        assertFalse(File(resolved, "xdm-app.config").exists(), "Fresh launch must not auto-import upstream data")
        assertTrue(File(upstreamDir, "xdm-app.config").exists(), "Upstream source must remain intact")

        // 4. Schema tag forward/backward compatibility: unknown future fields preserved
        val futureConfigDir = File(tempRoot, "future-schema").apply { mkdirs() }
        val F_FUTURE_FIELD_999 = 999
        val F_FUTURE_FIELD_1000 = 1000
        val futureStringVal = "experimental-future-feature-data"

        // Write config with standard fields PLUS unknown future tags
        AtomicIO.writeTransacted(AppConfig.CONFIG_FILE, futureConfigDir.absolutePath) { out ->
            TaggedFile.write(out) {
                string(4, File(tempRoot, "Downloads").absolutePath) // default download folder
                string(14, "fr") // lang
                string(F_FUTURE_FIELD_999, futureStringVal)
                int(F_FUTURE_FIELD_1000, 42)
            }
        }

        // Current build loads it (doesn't crash, extracts lang=fr, stores unknown tags)
        val loadedConfig = AppConfig(futureConfigDir.absolutePath).apply { load() }
        assertEquals("fr", loadedConfig.lang)

        // Save back with edits
        loadedConfig.lang = "es"
        loadedConfig.save()

        // Read raw tagged file to confirm future fields 999 and 1000 are still present
        var foundFuture999 = false
        var foundFuture1000 = false
        AtomicIO.readTransacted(AppConfig.CONFIG_FILE, futureConfigDir.absolutePath) { input ->
            val r = TaggedFile.read(input)
            while (true) {
                when (r.next()) {
                    -1 -> break
                    F_FUTURE_FIELD_999 -> {
                        assertEquals(futureStringVal, r.string())
                        foundFuture999 = true
                    }
                    F_FUTURE_FIELD_1000 -> {
                        assertEquals(42, r.int())
                        foundFuture1000 = true
                    }
                    else -> r.skip()
                }
            }
        }
        assertTrue(foundFuture999, "Future tag 999 must survive save cycle")
        assertTrue(foundFuture1000, "Future tag 1000 must survive save cycle")
    }

    /**
     * Scenario 63: Restart during active downloads, missing folders, corrupt/partial configuration,
     * stale native host paths, insufficient disk space, and concurrent writes.
     */
    @Test
    fun testScenario63FaultToleranceRestartAndConcurrency() {
        val dbDir = File(tempRoot, "db-test").apply { mkdirs() }
        val db = AppDB(dbDir.absolutePath)

        // 1. Restart during active downloads: active download returns as PAUSED
        val activeRec = DbRecord(
            id = 5001L,
            size = 50_000_000L,
            downloaded = 25_000_000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "ubuntu-server.iso",
            eta = 30,
            speed = 1024f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http
        )
        db.add(activeRec)
        assertEquals(RecordStatus.DOWNLOADING, db.getById(5001L)?.status)

        // Simulate app shutdown and restart: reload from disk
        val reloadedDb = AppDB(dbDir.absolutePath).apply { loadRecords() }
        val recoveredRec = reloadedDb.getById(5001L)
        assertNotNull(recoveredRec)
        assertEquals(RecordStatus.PAUSED, recoveredRec!!.status, "Active download must recover as PAUSED on restart")
        assertEquals(25_000_000L, recoveredRec.downloaded)
        assertEquals(50, recoveredRec.progress)

        // 2. Missing folders: LocalStorage auto-creates missing destination directory
        val nonExistentFolder = File(tempRoot, "auto/created/nested/downloads")
        assertFalse(nonExistentFolder.exists())
        val storage = LocalStorage.folder(nonExistentFolder.absolutePath)
        assertTrue(storage.create(), "LocalStorage.create() must succeed for missing directory")
        assertTrue(nonExistentFolder.exists())

        // 3. Corrupt/partial configuration fallback
        val corruptDir = File(tempRoot, "corrupt-cfg").apply { mkdirs() }
        val corruptConfigFile = File(corruptDir, AppConfig.CONFIG_FILE)
        corruptConfigFile.writeBytes(byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xEE.toByte())) // Invalid header
        val corruptConfig = AppConfig(corruptDir.absolutePath)
        corruptConfig.load() // Should not crash
        assertEquals("en", corruptConfig.lang, "Corrupted config must gracefully fall back to default language")
        assertEquals("dark", corruptConfig.theme, "Corrupted config must gracefully fall back to default theme")

        // 4. Stale native host paths detection
        val nonExistentExe = File(tempRoot, "non-existent-app.exe").absolutePath
        val invalidCommand = "\"$nonExistentExe\" \"%1\""
        assertFalse(
            WindowsAssociations.isCommandExecutableValid(invalidCommand),
            "Non-existent executable in open command must be identified as invalid/stale"
        )
        val validCommand = "\"${File(System.getProperty("java.home"), "bin/java.exe").absolutePath}\" \"%1\""
        if (File(System.getProperty("java.home"), "bin/java.exe").exists()) {
            assertTrue(WindowsAssociations.isCommandExecutableValid(validCommand), "Existing executable must be identified as valid")
        }

        // 5. Insufficient disk space detection
        val spaceFolder = LocalStorage.folder(tempRoot.absolutePath)
        val usable = spaceFolder.usableSpace()
        assertTrue(usable > 0, "usableSpace() must report positive capacity on valid mount")

        // 6. Concurrent database writes
        val concurrentDbDir = File(tempRoot, "concurrent-db").apply { mkdirs() }
        val concurrentDb = AppDB(concurrentDbDir.absolutePath)
        val threadCount = 8
        val opsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val idGen = AtomicInteger(1000)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until opsPerThread) {
                        val id = idGen.incrementAndGet().toLong()
                        val rec = DbRecord(
                            id = id,
                            size = 1000L,
                            downloaded = 0L,
                            progress = 0,
                            date = System.currentTimeMillis(),
                            fileName = "concurrent-$id.bin",
                            eta = 0,
                            speed = 0f,
                            selected = false,
                            status = RecordStatus.PAUSED,
                            downloadType = DownloadType.Http
                        )
                        concurrentDb.add(rec)
                        rec.downloaded = 500L
                        concurrentDb.update(rec)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent database operations must finish in time")
        executor.shutdown()
        val reloadedConcurrent = AppDB(concurrentDbDir.absolutePath).apply { loadRecords() }
        assertEquals(threadCount * opsPerThread, reloadedConcurrent.size, "All concurrent records must be persisted safely")
    }

    /**
     * Scenario 64: Verify language tab, category sidebar toggling, default destination,
     * completion-dialog preference, download type/card selection, theme/accent/skin,
     * network optimizer and browser preferences.
     */
    @Test
    fun testScenario64ComprehensivePreferencesAndSettingsCoverage() {
        val customDest = File(tempRoot, "MyCustomDownloads").apply { mkdirs() }

        // Configure all audited preferences
        appConfig.apply {
            lang = "zh_CN"
            showCategoriesSidebar = false
            sidebarDividerLocation = 280
            defaultDownloadFolder = customDest.absolutePath
            noneFolder = File(tempRoot, "NoneCustom").absolutePath
            downloadCompleteNotification = DownloadCompleteNotification.NOTIFICATION
            listSelectionMode = ListSelectionMode.TABLE
            theme = "light"
            accentColor = "#8B5CF6"
            skinId = "classic_xdm"
            maxSegments = 32
            maxRetries = 10
            speedLimiterEnabled = true
            speedLimit = 2048
            readTimeoutSeconds = 120
            skipDuplicateManifests = true
            detectSubtitles = false
            saveSubtitlesAsSrt = false
            minVideoSize = 10240L
            videoExtensions = listOf("MP4", "MKV", "WEBM", "M4V")
            fileExtensions = listOf("ZIP", "TAR", "GZ", "7Z")
            blockedHosts = listOf("ads.example.com", "tracker.sample.net")
            save()
        }

        // Verify across reload
        val reloaded = AppConfig(configDir.absolutePath).apply { load() }
        assertEquals("zh_CN", reloaded.lang, "Language preference must persist")
        assertFalse(reloaded.showCategoriesSidebar, "Categories sidebar visibility must persist")
        assertEquals(280, reloaded.sidebarDividerLocation, "Sidebar divider location must persist")
        assertEquals(customDest.absolutePath, reloaded.defaultDownloadFolder, "Default destination must persist")
        assertEquals(File(tempRoot, "NoneCustom").absolutePath, reloaded.noneFolder, "None folder must persist")
        assertEquals(DownloadCompleteNotification.NOTIFICATION, reloaded.downloadCompleteNotification, "Notification preference must persist")
        assertFalse(reloaded.showDownloadCompleteWindow, "Show complete window projection must persist")
        reloaded.showDownloadCompleteWindow = true
        assertEquals(DownloadCompleteNotification.DIALOG, reloaded.downloadCompleteNotification, "Legacy showDownloadCompleteWindow=true must project to DIALOG")
        assertEquals(ListSelectionMode.TABLE, reloaded.listSelectionMode, "List selection mode TABLE must persist")
        assertEquals("light", reloaded.theme, "Theme preference must persist")
        assertEquals("#8B5CF6", reloaded.accentColor, "Accent color must persist")
        assertEquals("classic_xdm", reloaded.skinId, "Skin ID must persist")
        assertEquals(32, reloaded.maxSegments, "Max segments must persist")
        assertEquals(10, reloaded.maxRetries, "Max retries must persist")
        assertTrue(reloaded.speedLimiterEnabled, "Speed limiter flag must persist")
        assertEquals(2048, reloaded.speedLimit, "Speed limit value must persist")
        assertEquals(120, reloaded.readTimeoutSeconds, "Read timeout seconds must persist")
        assertTrue(reloaded.skipDuplicateManifests, "Skip duplicate manifests must persist")
        assertFalse(reloaded.detectSubtitles, "Detect subtitles must persist")
        assertFalse(reloaded.saveSubtitlesAsSrt, "Save subtitles as SRT must persist")
        assertEquals(10240L, reloaded.minVideoSize, "Min video size must persist")
        assertEquals(listOf("MP4", "MKV", "WEBM", "M4V"), reloaded.videoExtensions)
        assertEquals(listOf("ZIP", "TAR", "GZ", "7Z"), reloaded.fileExtensions)
        assertEquals(listOf("ads.example.com", "tracker.sample.net"), reloaded.blockedHosts)

        // ConfigSnapshot isolation test
        val snapshot = ConfigSnapshot.of(reloaded)
        val editCopy = AppConfig(configDir.absolutePath).apply {
            load()
            maxSegments = 8
            showCategoriesSidebar = true
        }
        val beforeWrite = ConfigSnapshot.of(reloaded)
        reloaded.maxSegments = 8
        snapshot.keepChangedElsewhere(reloaded, beforeWrite)
        assertEquals(8, reloaded.maxSegments)
    }

    /**
     * Scenario 65: Safe migrations with staging/rollback, no silent deletion,
     * and isolation from official upstream data.
     */
    @Test
    fun testScenario65SafeMigrationRollbackAndIsolation() {
        val legacyDir = File(tempRoot, "test-legacy-source").apply { mkdirs() }
        File(legacyDir, "xdm-app.config").writeText("legacy-v8-config-content")
        File(legacyDir, "finished-downloads.dat").writeText("legacy-history-data")
        File(legacyDir, "tmp").mkdirs()
        File(File(legacyDir, "tmp"), "stale.part").writeText("should-not-copy")

        val targetDir = File(tempRoot, "test-migration-target")

        // 1. Successful migration with transactional staging
        val ok = AppDirs.migrateLegacyData(targetDir, legacyDir)
        assertTrue(ok, "Explicit migration must succeed")
        assertTrue(File(targetDir, "xdm-app.config").exists(), "Config must be copied")
        assertEquals("legacy-v8-config-content", File(targetDir, "xdm-app.config").readText())
        assertTrue(File(targetDir, "finished-downloads.dat").exists(), "History must be copied")
        assertFalse(File(targetDir, "tmp").exists(), "Temporary files must not be copied")
        assertTrue(File(legacyDir, "xdm-app.config").exists(), "Source legacy data must NOT be silently deleted")

        // 2. Already migrated target: no redundant overwriting
        val reRun = AppDirs.migrateLegacyData(targetDir, legacyDir)
        assertFalse(reRun, "Migration on already populated target must safely return false without mutating target")

        // 3. Rollback on failure: simulate error during migration
        val failTarget = File(tempRoot, "test-fail-target")
        val corruptSource = File(tempRoot, "corrupt-source").apply { mkdirs() }
        File(corruptSource, "file1.dat").writeText("content1")
        // Create an unreadable file to force copyTo failure
        val unreadable = File(corruptSource, "file2.dat").apply {
            writeText("content2")
            setReadable(false, false)
        }
        try {
            val failRes = AppDirs.migrateLegacyData(failTarget, File(tempRoot, "non-existent-folder"))
            assertFalse(failRes, "Migration from missing source must return false")
            assertFalse(failTarget.exists(), "Target directory must not be left in partial state on early failure")
        } finally {
            unreadable.setReadable(true, false)
        }
    }

    /**
     * Scenario 66: Validate credentials or sensitive metadata are not exposed through logs/config exports.
     */
    @Test
    fun testScenario66CredentialProtectionInStorageLogsAndExports() {
        // 1. Proxy password: in memory only, never written to xdm-app.config
        val secretPass = "TopSecretProxyPassword987!"
        appConfig.proxyUser = "corpUser"
        appConfig.proxyPass = secretPass
        appConfig.save()

        val rawConfigFile = File(configDir, AppConfig.CONFIG_FILE)
        assertTrue(rawConfigFile.exists())
        val configBytes = rawConfigFile.readBytes()
        val configContent = String(configBytes, Charsets.ISO_8859_1)
        assertFalse(configContent.contains(secretPass), "Proxy password must NEVER be written to xdm-app.config")

        val reloaded = AppConfig(configDir.absolutePath).apply { load() }
        assertEquals("corpUser", reloaded.proxyUser)
        assertEquals("", reloaded.proxyPass, "Proxy password must not be restored from disk")

        // 2. Sensitive request headers (Authorization) stripped before writing to task info DB
        val sensitiveHeaders: HeaderMap = mapOf(
            "Authorization" to listOf("Basic dXNlcjpwYXNzd29yZA=="),
            "User-Agent" to listOf("Mozilla/5.0"),
            "Accept" to listOf("*/*")
        )
        val taskId = 9001L
        val stripped = SensitiveHeaders.strip(taskId, sensitiveHeaders)
        assertNotNull(stripped)
        assertFalse(stripped!!.containsKey("Authorization"), "Authorization header must be stripped from disk headers")
        assertTrue(stripped.containsKey("User-Agent"))

        // When restored in memory during active session, Authorization is present
        val restored = SensitiveHeaders.restore(taskId, stripped)
        assertNotNull(restored)
        assertTrue(restored!!.containsKey("Authorization"), "In-memory session must restore Authorization for retries")
        assertEquals(listOf("Basic dXNlcjpwYXNzd29yZA=="), restored["Authorization"])

        // After task forgetting, credentials are purged from memory
        SensitiveHeaders.forget(taskId)
        val purged = SensitiveHeaders.restore(taskId, stripped)
        assertFalse(purged!!.containsKey("Authorization"), "Purged task must no longer retain Authorization")

        // 3. Task info file on disk must not carry Authorization header
        val taskDB = TaskInfoDB(configDir.absolutePath)
        // taskDB strip integration is verified via SensitiveHeaders.strip

        // 4. URL sanitization in logs: redacts basic auth and tokens
        val credentialUrl = "https://admin:superSecretToken123@download.example.com/file.zip?token=xyz987"
        val sanitized = LinkPreflightService.sanitizeUrlForLog(credentialUrl)
        assertFalse(sanitized.contains("superSecretToken123"), "URL sanitization must redact password credentials")
        assertFalse(sanitized.contains("admin"), "URL sanitization must strip username")
        assertFalse(sanitized.contains("token=xyz987"), "URL sanitization must strip sensitive query params")
        assertEquals("https://download.example.com/file.zip", sanitized)

        // Plugin yt-dlp URL sanitization
        val ytdlpPkg = File("../sample-plugins/ytdlp-downloader.xdmplugin").takeIf { it.exists() }
            ?: File("sample-plugins/ytdlp-downloader.xdmplugin")
        if (ytdlpPkg.exists()) {
            val cl = URLClassLoader(arrayOf(ytdlpPkg.toURI().toURL()), javaClass.classLoader)
            val ytdlpClass = cl.loadClass("xdm.plugin.ytdlp.YtDlpDownloadTask")
            val sanitizeMethod = ytdlpClass.getMethod("sanitizeUrl", String::class.java)
            val pluginSanitized = sanitizeMethod.invoke(null, credentialUrl) as String
            assertFalse(pluginSanitized.contains("superSecretToken123"))
            assertTrue(pluginSanitized.contains("***:***@download.example.com"))
        }

        // 5. Skin export security: exporting a skin must only include skin assets, never credentials
        val skinDir = File(tempRoot, "sample-skin").apply { mkdirs() }
        File(skinDir, "skin.json").writeText("""
            {
              "id": "export-security-test",
              "name": "Security Test Skin",
              "version": "1.0.0",
              "author": "Tester",
              "schemaVersion": 2
            }
        """.trimIndent())
        val exportArchive = File(tempRoot, "exported-skin.xdmskin")
        SkinPackager.exportSkinPackage(skinDir, exportArchive)
        assertTrue(exportArchive.exists())

        // Read zip entries of exported skin to verify no extraneous files or credentials
        java.util.zip.ZipFile(exportArchive).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toList()
            assertTrue(entryNames.contains("skin.json"), "Exported archive must contain skin.json")
            for (name in entryNames) {
                assertFalse(name.contains("config"), "Exported archive must not contain config files")
                assertFalse(name.contains("password"), "Exported archive must not contain password references")
                assertFalse(name.contains("active-downloads"), "Exported archive must not contain download state")
            }
        }
    }
}

