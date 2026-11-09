package xdm.torrent

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.core.downloaders.TaskInfoDB
import java.io.File

class TorrentMagnetMetadataNamingTest {

    @Test
    fun testParseMagnetDisplayName() {
        // Standard dn with +
        val uri1 = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Ubuntu+Linux+24.04"
        assertEquals("Ubuntu Linux 24.04", TorrentMetadataParser.parseMagnetDisplayName(uri1))

        // dn with %20
        val uri2 = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Arch%20Linux%20ISO"
        assertEquals("Arch Linux ISO", TorrentMetadataParser.parseMagnetDisplayName(uri2))

        // dn with encoded symbols and plus sign: C++ Primer -> C%2B%2B+Primer
        val uri3 = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=C%2B%2B+Primer+5th"
        assertEquals("C++ Primer 5th", TorrentMetadataParser.parseMagnetDisplayName(uri3))

        // dn with illegal filesystem characters
        val uri4 = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Movie%3A+Part+1%2F2+%3C1080p%3E"
        val parsed4 = TorrentMetadataParser.parseMagnetDisplayName(uri4)
        assertNotNull(parsed4)
        assertFalse(parsed4!!.contains(":"))
        assertFalse(parsed4.contains("/"))
        assertFalse(parsed4.contains("<"))
        assertFalse(parsed4.contains(">"))

        // dn with Unicode characters
        val uri5 = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=%E6%97%A5%E6%9C%AC%E8%AA%9E%E3%82%A2%E3%83%8B%E3%83%A1"
        assertEquals("日本語アニメ", TorrentMetadataParser.parseMagnetDisplayName(uri5))

        // Missing dn
        val uriNoDn = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
        assertNull(TorrentMetadataParser.parseMagnetDisplayName(uriNoDn))

        // Invalid non-magnet
        assertNull(TorrentMetadataParser.parseMagnetDisplayName("https://example.com/file.torrent"))
    }

    @Test
    fun testIsPlaceholderName() {
        assertTrue(TorrentMetadataParser.isPlaceholderName(null))
        assertTrue(TorrentMetadataParser.isPlaceholderName(""))
        assertTrue(TorrentMetadataParser.isPlaceholderName("   "))
        assertTrue(TorrentMetadataParser.isPlaceholderName("Magnet Download"))
        assertTrue(TorrentMetadataParser.isPlaceholderName("magnet download"))
        assertTrue(TorrentMetadataParser.isPlaceholderName("torrent"))
        assertTrue(TorrentMetadataParser.isPlaceholderName("magnet"))
        assertTrue(TorrentMetadataParser.isPlaceholderName("Fetching metadata"))
        assertTrue(TorrentMetadataParser.isPlaceholderName("Fetching metadata (01234567...)"))
        assertTrue(TorrentMetadataParser.isPlaceholderName("magnet:?xt=urn:btih:..."))
        assertTrue(TorrentMetadataParser.isPlaceholderName("0123456789abcdef0123456789abcdef01234567")) // 40-char hex
        assertTrue(TorrentMetadataParser.isPlaceholderName("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")) // 64-char hex

        // Real file names should NOT be recognized as placeholders
        assertFalse(TorrentMetadataParser.isPlaceholderName("Ubuntu-24.04-desktop-amd64.iso"))
        assertFalse(TorrentMetadataParser.isPlaceholderName("My Holiday Video.mp4"))
        assertFalse(TorrentMetadataParser.isPlaceholderName("Custom Magnet Name"))
        assertFalse(TorrentMetadataParser.isPlaceholderName("日本語アニメ.mkv"))
    }

    @Test
    fun testMagnetSourceInitialDisplayName() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val uriWithDn = "magnet:?xt=urn:btih:$hash&dn=Debian+Netinst"
        val source1 = TorrentSource.MagnetUri(uriWithDn)
        assertEquals("Debian Netinst", source1.name)
        assertNotEquals("Magnet Download", source1.name)

        val uriNoDn = "magnet:?xt=urn:btih:$hash"
        val source2 = TorrentSource.MagnetUri(uriNoDn)
        assertEquals("Fetching metadata (${hash.take(8)}...)", source2.name)
        assertNotEquals("Magnet Download", source2.name)

        // If explicit displayName is passed to TorrentSource.MagnetUri
        val source3 = TorrentSource.MagnetUri(uriNoDn, displayName = "Custom Explicit Name")
        assertEquals("Custom Explicit Name", source3.name)
    }

    @Test
    fun testMetadataArrivalUpdatesTaskNameAndSanitizes(@TempDir tempDir: File) {
        val destDir = File(tempDir, "downloads")
        destDir.mkdirs()

        val sourceDir = File(tempDir, "source")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "Real_Linux_Distro_v1.0.iso", 64 * 1024
        )
        val (_, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = tempDir)

        val hash = ti.infoHash().toHex().lowercase()
        val magnetUri = "magnet:?xt=urn:btih:$hash"

        var metadataCallbackFired = false
        var metadataReceivedName = ""

        val task = manager.createTask(
            id = 501L,
            source = TorrentSource.MagnetUri(magnetUri),
            saveDir = destDir
        )

        task.addListener(object : TorrentTaskListener {
            override fun onMetadataReceived(task: TorrentDownloadTask, metadata: TorrentMetadata) {
                metadataCallbackFired = true
                metadataReceivedName = metadata.name
            }
        })

        // Initial state before metadata
        assertTrue(task.name.startsWith("Fetching metadata"))
        assertFalse(task.isCustomName)

        // Simulate metadata arrival with real TorrentInfo
        task.updateMetadata(ti.name(), ti)

        assertTrue(metadataCallbackFired, "onMetadataReceived listener must be called")
        assertEquals("Real_Linux_Distro_v1.0.iso", task.name)
        assertEquals("Real_Linux_Distro_v1.0.iso", metadataReceivedName)
        assertFalse(TorrentMetadataParser.isPlaceholderName(task.name))
    }

    @Test
    fun testCustomUserDisplayNamePreservedOnMetadataArrival(@TempDir tempDir: File) {
        val destDir = File(tempDir, "downloads")
        destDir.mkdirs()

        val sourceDir = File(tempDir, "source")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "Actual_Torrent_Release_Group.iso", 64 * 1024
        )
        val (_, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = tempDir)

        val hash = ti.infoHash().toHex().lowercase()
        val magnetUri = "magnet:?xt=urn:btih:$hash&dn=Original+Magnet+Title"

        val task = manager.createTask(
            id = 502L,
            source = TorrentSource.MagnetUri(magnetUri),
            saveDir = destDir,
            customName = "My Custom Renamed Linux ISO"
        )

        assertTrue(task.isCustomName, "isCustomName must be true when user custom name is given")
        assertEquals("My Custom Renamed Linux ISO", task.name)

        var receivedMetaName = ""
        task.addListener(object : TorrentTaskListener {
            override fun onMetadataReceived(task: TorrentDownloadTask, metadata: TorrentMetadata) {
                receivedMetaName = metadata.name
            }
        })

        // Fire metadata update
        task.updateMetadata(ti.name(), ti)

        // The task display name MUST NOT be overwritten by the metadata name
        assertEquals("My Custom Renamed Linux ISO", task.name)
        // But the metadata object itself reports the torrent's underlying content name
        assertEquals("Actual_Torrent_Release_Group.iso", receivedMetaName)
    }

    @Test
    fun testPersistenceAndRestorationOfResolvedName(@TempDir tempDir: File) {
        val destDir = File(tempDir, "downloads")
        destDir.mkdirs()

        val taskInfoDB = TaskInfoDB(tempDir.absolutePath)
        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = tempDir, taskInfoDB = taskInfoDB)

        val sourceDir = File(tempDir, "source")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "Resolved_Arch_2024.iso", 64 * 1024
        )
        val (_, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val hash = ti.infoHash().toHex().lowercase()
        val magnetUri = "magnet:?xt=urn:btih:$hash"

        val task = manager.createTask(
            id = 503L,
            source = TorrentSource.MagnetUri(magnetUri),
            saveDir = destDir
        )

        assertEquals("Fetching metadata (${hash.take(8)}...)", task.name)

        // Metadata arrives
        task.updateMetadata(ti.name(), ti)
        assertEquals("Resolved_Arch_2024.iso", task.name)

        // Verify taskInfo was written to disk
        val savedInfo = taskInfoDB.getTorrentTask(503L)
        assertNotNull(savedInfo)
        assertEquals("Resolved_Arch_2024.iso", savedInfo!!.name)

        // Now simulate app restart: create a new manager on same configDir
        val engine2 = LibtorrentEngine.createStandalone()
        val manager2 = TorrentDownloadManager(engine = engine2, configDir = tempDir, taskInfoDB = taskInfoDB)

        val restored = manager2.loadAllTasksFromDB()
        assertEquals(1, restored.size)
        val restoredTask = restored[0]
        assertEquals(503L, restoredTask.id)
        assertEquals("Resolved_Arch_2024.iso", restoredTask.name)
        assertFalse(TorrentMetadataParser.isPlaceholderName(restoredTask.name))
    }

    @Test
    fun testUnicodeAndIllegalCharacterSanitizationOnMetadataArrival(@TempDir tempDir: File) {
        val destDir = File(tempDir, "downloads")
        destDir.mkdirs()

        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = tempDir)

        val hash = "0123456789abcdef0123456789abcdef01234567"
        val magnetUri = "magnet:?xt=urn:btih:$hash"

        val task = manager.createTask(
            id = 504L,
            source = TorrentSource.MagnetUri(magnetUri),
            saveDir = destDir
        )

        // Raw metadata name with illegal chars, trailing dots, unicode
        val dirtyName = "日本語: Special <Episode> *2024* ? / \\ | \"Test\" ... "
        task.updateMetadata(dirtyName, null)

        assertFalse(task.name.contains(":"))
        assertFalse(task.name.contains("<"))
        assertFalse(task.name.contains(">"))
        assertFalse(task.name.contains("*"))
        assertFalse(task.name.contains("?"))
        assertFalse(task.name.contains("/"))
        assertFalse(task.name.contains("\\"))
        assertFalse(task.name.contains("|"))
        assertFalse(task.name.contains("\""))
        assertFalse(task.name.endsWith("."))
        assertTrue(task.name.contains("日本語"))
    }
}

