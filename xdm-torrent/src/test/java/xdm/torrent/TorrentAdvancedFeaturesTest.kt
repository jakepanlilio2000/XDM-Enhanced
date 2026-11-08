package xdm.torrent

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.core.downloaders.TaskInfoDB
import xdm.core.downloaders.TorrentDownloadTaskInfo
import java.io.File

class TorrentAdvancedFeaturesTest {

    @Test
    fun testPriorityMapping() {
        assertEquals(org.libtorrent4j.Priority.IGNORE, TorrentPriority.DO_NOT_DOWNLOAD.toLibtorrent())
        assertEquals(org.libtorrent4j.Priority.LOW, TorrentPriority.LOW.toLibtorrent())
        assertEquals(org.libtorrent4j.Priority.DEFAULT, TorrentPriority.NORMAL.toLibtorrent())
        assertEquals(org.libtorrent4j.Priority.TOP_PRIORITY, TorrentPriority.HIGH.toLibtorrent())

        assertEquals(TorrentPriority.DO_NOT_DOWNLOAD, TorrentPriority.fromLibtorrent(org.libtorrent4j.Priority.IGNORE))
        assertEquals(TorrentPriority.LOW, TorrentPriority.fromLibtorrent(org.libtorrent4j.Priority.LOW))
        assertEquals(TorrentPriority.NORMAL, TorrentPriority.fromLibtorrent(org.libtorrent4j.Priority.DEFAULT))
        assertEquals(TorrentPriority.HIGH, TorrentPriority.fromLibtorrent(org.libtorrent4j.Priority.TOP_PRIORITY))
    }

    @Test
    fun testTorrentSessionConfigDefaults() {
        val config = TorrentSessionConfig()
        assertEquals(0, config.downloadRateLimit)
        assertEquals(0, config.uploadRateLimit)
        assertEquals(200, config.maxConnections)
        assertEquals(3, config.maxActiveDownloads)
        assertEquals(3, config.maxActiveSeeds)
        assertEquals(5, config.maxActiveTotal)
        assertTrue(config.enableDht)
        assertTrue(config.enablePex)
        assertTrue(config.enableLsd)
        assertEquals(0, config.listenPort)
        assertFalse(config.enableUpnp, "UPnP must default to false for security and user-controlled configuration")
        assertFalse(config.enableNatPmp, "NAT-PMP must default to false for security and user-controlled configuration")
        assertEquals(SeedingMode.RATIO_OR_TIME, config.seedingRule.mode)
        assertEquals(1.0f, config.seedingRule.maxRatio)
        assertEquals(3600L, config.seedingRule.maxTimeSeconds)
    }

    @Test
    fun testEngineApplyConfig() {
        TorrentNativeLoader.load()
        val engine = LibtorrentEngine.createStandalone()
        engine.start()
        assertTrue(engine.isRunning())

        val customConfig = TorrentSessionConfig(
            downloadRateLimit = 1_048_576,
            uploadRateLimit = 524_288,
            maxConnections = 150,
            maxActiveDownloads = 4,
            maxActiveSeeds = 2,
            maxActiveTotal = 6,
            enableDht = true,
            enablePex = true,
            enableLsd = true,
            listenPort = 6881,
            enableUpnp = false,
            enableNatPmp = false
        )

        // Should apply without throwing
        assertDoesNotThrow {
            engine.applyConfig(customConfig)
        }

        // Test DHT status inquiry
        assertDoesNotThrow {
            val dhtActive = engine.isDhtRunning()
            val nodes = engine.dhtNodes()
            assertTrue(nodes >= 0, "DHT nodes count should be non-negative: $nodes")
        }

        engine.stop()
        assertFalse(engine.isRunning())
    }

    @Test
    fun testTaskLimitsAndQueueControls(@TempDir tempDir: File) {
        TorrentNativeLoader.load()
        val engine = LibtorrentEngine.createStandalone()
        engine.start()

        val task = TorrentDownloadTask(
            id = 1001L,
            source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
            saveDir = tempDir,
            engine = engine
        )
        task.start()

        // Verify initial limit defaults
        assertEquals(0, task.downloadLimit)
        assertEquals(0, task.uploadLimit)

        // Set limits
        task.setDownloadLimit(500_000)
        task.setUploadLimit(250_000)
        assertEquals(500_000, task.downloadLimit)
        assertEquals(250_000, task.uploadLimit)

        // Queue controls
        assertTrue(task.queuePosition >= 0, "Active task should have non-negative queue position: ${task.queuePosition}")
        assertDoesNotThrow {
            task.setQueuePosition(5)
            task.queueUp()
            task.queueDown()
            task.queueTop()
            task.queueBottom()
        }

        // Seeding rules
        task.seedingRule = SeedingRule(SeedingMode.UNLIMITED, 2.5f, 7200L)
        assertEquals(SeedingMode.UNLIMITED, task.seedingRule.mode)
        assertEquals(2.5f, task.seedingRule.maxRatio)
        assertEquals(7200L, task.seedingRule.maxTimeSeconds)

        // Trackers
        val initialTrackers = task.getTrackers()
        assertNotNull(initialTrackers)

        task.addTracker("udp://tracker.opentrackr.org:1337/announce", 1)
        assertDoesNotThrow {
            task.reannounce()
        }

        // Peers
        val peers = task.getPeers()
        assertNotNull(peers)

        // Snapshot metrics
        val snapshot = task.getSnapshot()
        assertNotNull(snapshot)
        assertEquals(0L, snapshot.totalUploadBytes)
        assertEquals(0.0f, snapshot.shareRatio)
        assertEquals(0L, snapshot.seedingDurationSeconds)
        assertFalse(snapshot.isPrivate)

        // File priorities
        val priorities = task.getFilePriorities()
        assertNotNull(priorities)
        if (priorities.isNotEmpty()) {
            task.setFilePriority(0, TorrentPriority.HIGH)
            val updated = task.getFilePriorities()
            assertEquals(TorrentPriority.HIGH, updated[0])
        }

        // Persistence conversion
        val taskInfo = task.toTaskInfo()
        assertEquals(1001L, taskInfo.id)
        assertEquals(500_000, taskInfo.downloadLimit)
        assertEquals(250_000, taskInfo.uploadLimit)
        assertEquals(2, taskInfo.seedingMode) // UNLIMITED is ordinal 2

        engine.stop()
    }

    @Test
    fun testTaskInfoDbPersistenceOfTorrentSettings(@TempDir tempDir: File) {
        val db = TaskInfoDB(tempDir.absolutePath)

        val taskInfo = TorrentDownloadTaskInfo(
            id = 2002L,
            name = "Test Torrent",
            targetFolder = tempDir.absolutePath,
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            sourceType = 0,
            sourceUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            fileSelection = listOf(true, true),
            totalSize = 1048576L,
            downloadLimit = 300_000,
            uploadLimit = 150_000,
            seedingMode = 1 // RATIO_OR_TIME
        )

        db.saveTorrentTask(taskInfo)

        val loaded = db.getTorrentTask(2002L)
        assertNotNull(loaded)
        assertEquals(2002L, loaded?.id)
        assertEquals("Test Torrent", loaded?.name)
        assertEquals(300_000, loaded?.downloadLimit)
        assertEquals(150_000, loaded?.uploadLimit)
        assertEquals(1, loaded?.seedingMode)
    }

    private fun createTestMetadata(data: ByteArray): TorrentMetadata {
        val pieceLen = 16384
        val pieceCount = (data.size + pieceLen - 1) / pieceLen
        return TorrentMetadata(
            name = "test-transfer.bin",
            infoHash = "1234567890123456789012345678901234567890",
            totalSize = data.size.toLong(),
            pieceLength = pieceLen,
            numPieces = pieceCount,
            files = listOf(TorrentFileEntry(0, "test-transfer.bin", data.size.toLong()))
        )
    }
}
