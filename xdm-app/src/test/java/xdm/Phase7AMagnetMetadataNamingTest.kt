package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.IAppInstance
import xdm.app.ui.screens.TorrentUiBridge
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.LibtorrentEngine
import xdm.torrent.TorrentDownloadManager
import xdm.torrent.TorrentDownloadTask
import xdm.torrent.TorrentMetadata
import xdm.torrent.TorrentMetadataParser
import xdm.torrent.TorrentSource
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

class Phase7AMagnetMetadataNamingTest : DownloadManagerTestBase() {

    private val updatedViewIds = CopyOnWriteArrayList<Long>()
    private val updatedProgressWindowIds = CopyOnWriteArrayList<Long>()

    @BeforeEach
    fun initUiMocks() {
        xdm.app.I8N.loadTexts("en")
        AppContext.resetTorrentManagerForTest()
        updatedViewIds.clear()
        updatedProgressWindowIds.clear()

        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, method, args ->
            when (method.name) {
                "updateDownloadInView" -> {
                    if (args != null && args.isNotEmpty()) {
                        updatedViewIds.add(args[0] as Long)
                    }
                    null
                }
                "updateTorrentProgressWindow" -> {
                    if (args != null && args.isNotEmpty()) {
                        updatedProgressWindowIds.add(args[0] as Long)
                    }
                    null
                }
                else -> null
            }
        } as IAppInstance
    }

    @org.junit.jupiter.api.AfterEach
    fun cleanUpTorrentManager() {
        AppContext.resetTorrentManagerForTest()
    }

    @Test
    fun testInitialRecordNamingForMagnetWithDn() {
        val hash = "0123456789abcdef0123456789abcdef01234561"
        val magnetUri = "magnet:?xt=urn:btih:$hash&dn=Ubuntu+Desktop+24.04+LTS"
        val source = TorrentSource.MagnetUri(magnetUri)

        assertEquals("Ubuntu Desktop 24.04 LTS", source.name)
        assertNotEquals("Magnet Download", source.name)

        val taskId = 701L
        val task = TorrentDownloadTask(
            id = taskId,
            source = source,
            saveDir = dir,
            engine = LibtorrentEngine.createStandalone()
        )

        val rec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = task.name,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        assertEquals("Ubuntu Desktop 24.04 LTS", appDB.getById(taskId)?.fileName)
        assertNotEquals("Magnet Download", appDB.getById(taskId)?.fileName)
    }

    @Test
    fun testInitialRecordNamingForMagnetWithoutDn() {
        val hash = "0123456789abcdef0123456789abcdef01234562"
        val magnetUri = "magnet:?xt=urn:btih:$hash"
        val source = TorrentSource.MagnetUri(magnetUri)

        assertEquals("Fetching metadata (${hash.take(8)}...)", source.name)
        assertNotEquals("Magnet Download", source.name)

        val taskId = 702L
        val task = TorrentDownloadTask(
            id = taskId,
            source = source,
            saveDir = dir,
            engine = LibtorrentEngine.createStandalone()
        )

        val rec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = task.name,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        assertEquals("Fetching metadata (${hash.take(8)}...)", appDB.getById(taskId)?.fileName)
        assertTrue(TorrentMetadataParser.isPlaceholderName(appDB.getById(taskId)?.fileName))
    }

    @Test
    fun testMetadataArrivalUpdatesDbRecordAndUi() {
        val hash = "0123456789abcdef0123456789abcdef01234563"
        val magnetUri = "magnet:?xt=urn:btih:$hash"
        val source = TorrentSource.MagnetUri(magnetUri)

        val taskId = 703L
        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = dir, taskInfoDB = taskDB)

        val task = manager.createTask(
            id = taskId,
            source = source,
            saveDir = dir
        )
        TorrentUiBridge.attachTaskListener(task)

        val rec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = task.name,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        assertTrue(task.name.startsWith("Fetching metadata"))
        assertEquals(task.name, appDB.getById(taskId)?.fileName)

        // Simulate metadata resolution
        val resolvedName = "Debian-12.8.0-amd64-netinst.iso"
        task.updateMetadata(resolvedName, null)

        assertEquals(resolvedName, task.name)
        val updatedRec = appDB.getById(taskId)
        assertNotNull(updatedRec)
        assertEquals(resolvedName, updatedRec!!.fileName)
        assertFalse(TorrentMetadataParser.isPlaceholderName(updatedRec.fileName))

        // UI notifications fired
        assertTrue(updatedViewIds.contains(taskId), "updateDownloadInView must be called for task")
        assertTrue(updatedProgressWindowIds.contains(taskId), "updateTorrentProgressWindow must be called for task")

        // TaskInfoDB persisted the resolved name
        val savedTaskInfo = taskDB.getTorrentTask(taskId)
        assertNotNull(savedTaskInfo)
        assertEquals(resolvedName, savedTaskInfo!!.name)
    }

    @Test
    fun testUserCustomNamePreservedAcrossMetadataArrival() {
        val hash = "0123456789abcdef0123456789abcdef01234564"
        val magnetUri = "magnet:?xt=urn:btih:$hash&dn=Original+Torrent+Title"
        val source = TorrentSource.MagnetUri(magnetUri)

        val taskId = 704L
        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = dir, taskInfoDB = taskDB)

        val userChosenName = "User Custom Vacation Movie 2024"
        val task = manager.createTask(
            id = taskId,
            source = source,
            saveDir = dir,
            customName = userChosenName
        )
        TorrentUiBridge.attachTaskListener(task)

        assertTrue(task.isCustomName)
        assertEquals(userChosenName, task.name)

        val rec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = task.name,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        // Metadata arrives with another name
        task.updateMetadata("Release.Group.Title.720p.mkv", null)

        // Custom name must NOT be overwritten
        assertEquals(userChosenName, task.name)
        assertEquals(userChosenName, appDB.getById(taskId)?.fileName)
    }

    @Test
    fun testSanitizationOfDangerousCharsAndUnicodePreservation() {
        val hash = "0123456789abcdef0123456789abcdef01234565"
        val magnetUri = "magnet:?xt=urn:btih:$hash"
        val source = TorrentSource.MagnetUri(magnetUri)

        val taskId = 705L
        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = dir, taskInfoDB = taskDB)

        val task = manager.createTask(
            id = taskId,
            source = source,
            saveDir = dir
        )
        TorrentUiBridge.attachTaskListener(task)

        val rec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = task.name,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        // Dirty name with illegal characters and Unicode
        val dirtyName = "日本語: Anime Ep 01 *Special* ? <HD> | [1080p] ... "
        task.updateMetadata(dirtyName, null)

        val resolved = appDB.getById(taskId)?.fileName
        assertNotNull(resolved)
        assertFalse(resolved!!.contains(":"))
        assertFalse(resolved.contains("*"))
        assertFalse(resolved.contains("?"))
        assertFalse(resolved.contains("<"))
        assertFalse(resolved.contains(">"))
        assertFalse(resolved.contains("|"))
        assertFalse(resolved.endsWith("."))
        assertTrue(resolved.contains("日本語"))
    }

    @Test
    fun testSyncSavedTorrentsRestoresResolvedName() {
        val taskId = 706L
        val resolvedName = "Fedora-Workstation-41.iso"
        val hash = "0123456789abcdef0123456789abcdef01234566"
        val magnetUri = "magnet:?xt=urn:btih:$hash"

        // Simulate task already saved with resolved name in taskDB
        taskDB.saveTorrentTask(
            xdm.core.downloaders.TorrentDownloadTaskInfo(
                id = taskId,
                name = resolvedName,
                targetFolder = dir.absolutePath,
                infoHash = hash,
                sourceType = xdm.core.downloaders.TorrentSourceTypes.MAGNET_URI,
                sourceUri = magnetUri,
                lastState = xdm.torrent.TorrentTaskState.PAUSED.ordinal
            )
        )

        // DB record had old placeholder or desynced name
        val rec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = "Fetching metadata",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        // Restore via syncSavedTorrents
        TorrentUiBridge.syncSavedTorrents()

        val restoredRec = appDB.getById(taskId)
        assertNotNull(restoredRec)
        assertEquals(resolvedName, restoredRec!!.fileName)
    }

    @Test
    fun testProgressUpdateSafeguardForPlaceholder() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val magnetUri = "magnet:?xt=urn:btih:$hash"
        val source = TorrentSource.MagnetUri(magnetUri)

        val taskId = 707L
        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = dir, taskInfoDB = taskDB)

        val task = manager.createTask(
            id = taskId,
            source = source,
            saveDir = dir
        )
        TorrentUiBridge.attachTaskListener(task)

        val rec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = "Fetching metadata",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        // Metadata arrives on task
        task.updateMetadata("OpenSUSE-Tumbleweed.iso", null)

        // Trigger snapshot which notifies listeners
        task.getSnapshot()

        val updatedRec = appDB.getById(taskId)
        assertNotNull(updatedRec)
        assertEquals("OpenSUSE-Tumbleweed.iso", updatedRec!!.fileName)
    }
}
