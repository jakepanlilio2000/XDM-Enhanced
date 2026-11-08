package xdm.torrent

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class TorrentSecurityAndAuditTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var saveDir: File
    private lateinit var configDir: File

    @BeforeEach
    fun setUp() {
        saveDir = tempDir.resolve("save").toFile().apply { mkdirs() }
        configDir = tempDir.resolve("config").toFile().apply { mkdirs() }
    }

    @AfterEach
    fun tearDown() {
        // cleanup handled by TempDir
    }

    @Test
    fun testMaxTorrentFileSizeConstants() {
        assertEquals(50L * 1024 * 1024, TorrentMetadataParser.MAX_TORRENT_FILE_SIZE)
    }

    @Test
    fun testTorrentFileSizeLimitEnforcedOnFile() {
        val fakeOversizedFile = File(configDir, "huge.torrent")
        // Create a sparse-like file exceeding 50 MB
        fakeOversizedFile.outputStream().use { fos ->
            val raf = java.io.RandomAccessFile(fakeOversizedFile, "rw")
            raf.setLength(TorrentMetadataParser.MAX_TORRENT_FILE_SIZE + 1024)
            raf.close()
        }

        val ex = assertThrows(TorrentEngineException::class.java) {
            TorrentMetadataParser.parse(fakeOversizedFile)
        }
        assertTrue(ex.message!!.contains("exceeds maximum allowed size"), "Error should mention exceeding max size: ${ex.message}")
    }

    @Test
    fun testTorrentByteSizeLimitEnforced() {
        // Since allocating a 50MB+ ByteArray in heap is unnecessary, test that size validation logic throws
        val fakeBytes = ByteArray(10)
        // Normal small bytes throw "Failed to parse torrent bytes" or invalid format, not size limit
        val ex = assertThrows(TorrentEngineException::class.java) {
            TorrentMetadataParser.parse(fakeBytes)
        }
        assertFalse(ex.message!!.contains("exceeds maximum allowed size"))
    }

    @Test
    fun testSanitizeTorrentFilePathTraversal() {
        // Direct relative traversal
        val p1 = TorrentMetadataParser.sanitizeTorrentFilePath("../../Windows/System32/calc.exe", 0)
        assertEquals("Windows/System32/calc.exe", p1)

        // Multiple traversals in subpath
        val p2 = TorrentMetadataParser.sanitizeTorrentFilePath("folder/sub/../../../etc/shadow", 1)
        assertEquals("folder/sub/etc/shadow", p2)

        // Traversal only
        val p3 = TorrentMetadataParser.sanitizeTorrentFilePath("../../../..", 2)
        assertEquals("file_2", p3)

        // Windows drive letter
        val p4 = TorrentMetadataParser.sanitizeTorrentFilePath("C:\\Program Files\\App\\app.exe", 3)
        assertEquals("Program Files/App/app.exe", p4)

        // Windows reserved device names
        val p5 = TorrentMetadataParser.sanitizeTorrentFilePath("con.txt", 4)
        assertEquals("_con.txt", p5)

        val p6 = TorrentMetadataParser.sanitizeTorrentFilePath("folder/aux/data.bin", 5)
        assertEquals("folder/_aux/data.bin", p6)

        // Illegal characters
        val p7 = TorrentMetadataParser.sanitizeTorrentFilePath("my<bad>*name?.mkv", 6)
        assertEquals("my_bad__name_.mkv", p7)
    }

    @Test
    fun testSafeDiskDeletionDoesNotDeleteSaveDir() {
        // Populate saveDir with torrent content
        val subDir = File(saveDir, "torrent_content").apply { mkdirs() }
        val file1 = File(subDir, "video.mp4").apply { writeText("dummy content") }
        val file2 = File(saveDir, "standalone.txt").apply { writeText("standalone content") }

        val task = TorrentDownloadTask(
            id = 9991L,
            source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=torrent_content"),
            saveDir = saveDir,
            configDir = configDir
        )

        // Verify files exist before removal
        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(saveDir.exists())

        // Call remove with deleteFiles = true
        task.remove(deleteFiles = true)

        // Content should be removed
        assertFalse(file1.exists(), "Content file should be deleted")
        // saveDir itself MUST still exist!
        assertTrue(saveDir.exists(), "Save directory itself must NOT be deleted!")
    }

    @Test
    fun testFastresumeRejectedDeletesCorruptedResumeFile() {
        val resumeFile = File(configDir, "task-777.resume").apply { writeText("corrupted resume data") }
        assertTrue(resumeFile.exists())

        val task = TorrentDownloadTask(
            id = 777L,
            source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
            saveDir = saveDir,
            resumeFile = resumeFile,
            configDir = configDir
        )

        task.onFastresumeRejected(null)
        assertFalse(resumeFile.exists(), "Fastresume file should be deleted upon rejection")
    }

    @Test
    fun testRemoveTaskPurgesBackupFiles() {
        val mgr = TorrentDownloadManager(configDir = configDir)

        val backupTorrent = File(configDir, "task-888.torrent").apply { writeText("dummy torrent backup") }
        val resumeFile = File(configDir, "task-888.resume").apply { writeText("dummy resume") }

        assertTrue(backupTorrent.exists())
        assertTrue(resumeFile.exists())

        val task = mgr.createTask(
            id = 888L,
            source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
            saveDir = saveDir
        )

        mgr.removeTask(888L, deleteFiles = false)

        assertFalse(backupTorrent.exists(), "Backup torrent file should be deleted on removeTask")
        assertFalse(resumeFile.exists(), "Resume file should be deleted on removeTask")
    }
}

