package xdm.torrent

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TorrentExistingDataRecheckTest {

    private lateinit var testEngine: TorrentEngine
    private lateinit var downloaderManager: TorrentDownloadManager

    @BeforeEach
    fun setUp() {
        TorrentNativeLoader.load()
        testEngine = LibtorrentEngine.createStandalone()
        testEngine.start()
        downloaderManager = TorrentDownloadManager(testEngine)
    }

    @AfterEach
    fun tearDown() {
        downloaderManager.shutdown()
        testEngine.stop()
    }

    @Test
    fun testExistingFileDetectionAndSafety(@TempDir tempDir: File) {
        val saveDir = File(tempDir, "downloads").apply { mkdirs() }
        val testFile = File(saveDir, "sample.bin")
        val data = ByteArray(64 * 1024) { (it % 250).toByte() }
        Files.write(testFile.toPath(), data)

        val meta = TorrentMetadata(
            name = "sample.bin",
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            totalSize = 64 * 1024L,
            pieceLength = 32 * 1024,
            numPieces = 2,
            files = listOf(
                TorrentFileEntry(0, "sample.bin", 64 * 1024L, true)
            )
        )

        val check = TorrentExistingDataDetector.detect(saveDir, meta)
        assertTrue(check.hasExistingData, "Should detect existing file")
        assertEquals(1, check.existingFilesCount)
        assertEquals(1, check.totalFilesCount)
        assertEquals(64 * 1024L, check.existingBytes)
        assertTrue(check.isSizeComplete)
        assertTrue(check.files[0].isSafe)
        assertTrue(check.files[0].exists)

        // Traversal path protection check
        val maliciousMeta = TorrentMetadata(
            name = "escape",
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            totalSize = 1024L,
            pieceLength = 1024,
            numPieces = 1,
            files = listOf(
                TorrentFileEntry(0, "../../evil.exe", 1024L, true)
            )
        )
        val maliciousCheck = TorrentExistingDataDetector.detect(saveDir, maliciousMeta)
        // Sanitization and canonical check prevent directory escape
        for (f in maliciousCheck.files) {
            val target = File(saveDir, f.relativePath)
            assertTrue(target.canonicalFile.toPath().startsWith(saveDir.canonicalFile.toPath()), "Path must not escape saveDir")
        }
    }

    @Test
    fun testFullyCompletedFileRecheck(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "complete_check.bin", 128 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile, pieceSize = 32 * 1024)

        // Pre-place the full completed file into destDir BEFORE starting download
        val existingTarget = File(destDir, "complete_check.bin")
        Files.copy(testFile.toPath(), existingTarget.toPath())
        assertEquals(128 * 1024L, existingTarget.length())

        val meta = TorrentMetadataParser.parse(torrentBytes)
        val check = TorrentExistingDataDetector.detect(destDir, meta)
        assertTrue(check.hasExistingData, "Detector should detect existing file")
        assertTrue(check.isSizeComplete, "File size should match 100%")

        val finishedLatch = CountDownLatch(1)
        val task = downloaderManager.createTask(
            id = 201L,
            source = TorrentSource.TorrentBytes(torrentBytes, "complete_check.bin"),
            saveDir = destDir,
            verifyExisting = true
        )

        task.addListener(object : TorrentTaskListener {
            override fun onFinished(task: TorrentDownloadTask) {
                finishedLatch.countDown()
            }
        })

        task.start()

        // Wait for libtorrent hash verification to complete (WITHOUT any seeder running!)
        val finished = finishedLatch.await(15, TimeUnit.SECONDS)
        assertTrue(finished, "Task should verify existing files and finish without peers")
        assertTrue(task.isCompleted, "Task should be completed")

        val snap = task.getSnapshot()
        assertEquals(100, snap.progressPercent, "Recovered progress should be 100%")
        assertEquals(128 * 1024L, snap.downloadedBytes)
        assertEquals(ti.numPieces(), snap.verifiedPieces)
        assertEquals(expectedHash, TorrentTransferTestFixture.sha256(existingTarget), "Integrity must match")
    }

    @Test
    fun testHalfDownloadedFileRecheckAndResume(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        // 4 pieces of 32 KB = 128 KB
        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "half_check.bin", 128 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile, pieceSize = 32 * 1024)

        // Pre-place the first 2 pieces (64 KB) into destDir
        val halfTarget = File(destDir, "half_check.bin")
        val fullData = Files.readAllBytes(testFile.toPath())
        val halfData = fullData.copyOfRange(0, 64 * 1024)
        Files.write(halfTarget.toPath(), halfData)
        assertEquals(64 * 1024L, halfTarget.length())

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = downloaderManager.createTask(
                id = 202L,
                source = TorrentSource.TorrentBytes(torrentBytes, "half_check.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint),
                verifyExisting = true
            )

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    println(">>> DEBUG onFinished! snap=" + task.getSnapshot())
                    finishedLatch.countDown()
                }
                override fun onStateChanged(task: TorrentDownloadTask, oldState: TorrentTaskState, newState: TorrentTaskState) {
                    println(">>> DEBUG stateChanged: $oldState -> $newState, snap=" + task.getSnapshot())
                }
            })

            task.start()
            task.addPeer(seeder.endpoint)

            // Let verification run and download missing 64 KB from seeder
            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS), "Task should finish missing pieces download")
            assertTrue(task.isCompleted, "Task should be completed")

            val snap = task.getSnapshot()
            assertEquals(100, snap.progressPercent)
            assertEquals(128 * 1024L, snap.downloadedBytes)
            assertEquals(ti.numPieces(), snap.verifiedPieces)
            task.getHandle()?.flushCache()
            Thread.sleep(1000)
            downloaderManager.removeTask(task.id, deleteFiles = false)

            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(halfTarget), "Final file integrity must match")
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testCorruptedPieceDetectionAndRepair(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "corrupted_piece.bin", 128 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile, pieceSize = 32 * 1024)

        // Copy full file, but corrupt piece 2 (offset 64KB..96KB)
        val corruptTarget = File(destDir, "corrupted_piece.bin")
        val fullData = Files.readAllBytes(testFile.toPath())
        for (i in (64 * 1024) until (96 * 1024)) {
            fullData[i] = (fullData[i].toInt() xor 0xFF).toByte()
        }
        Files.write(corruptTarget.toPath(), fullData)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = downloaderManager.createTask(
                id = 203L,
                source = TorrentSource.TorrentBytes(torrentBytes, "corrupted_piece.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint),
                verifyExisting = true
            )

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    println(">>> DEBUG onFinished! snap=" + task.getSnapshot())
                    finishedLatch.countDown()
                }
                override fun onStateChanged(task: TorrentDownloadTask, oldState: TorrentTaskState, newState: TorrentTaskState) {
                    println(">>> DEBUG stateChanged: $oldState -> $newState, snap=" + task.getSnapshot())
                }
            })

            task.start()
            task.addPeer(seeder.endpoint)

            assertTrue(finishedLatch.await(30, TimeUnit.SECONDS), "Corrupted piece should be repaired from seeder")
            assertTrue(task.isCompleted, "Task should be completed")
            task.stop()

            var currentHash = TorrentTransferTestFixture.sha256(corruptTarget)
            val deadline = System.currentTimeMillis() + 5000L
            while (currentHash != expectedHash && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
                currentHash = TorrentTransferTestFixture.sha256(corruptTarget)
            }

            assertEquals(expectedHash, currentHash, "File SHA-256 must match after repair")
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testMultiFileTorrentExistingFilesDetection(@TempDir tempDir: File) {
        val rootDir = File(tempDir, "multi_torrent").apply { mkdirs() }
        val fileA = File(rootDir, "fileA.txt").apply { writeText("Hello File A Data") }
        val subDir = File(rootDir, "sub").apply { mkdirs() }
        val fileB = File(subDir, "fileB.txt").apply { writeText("Hello File B Data In Subdirectory") }

        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(rootDir, pieceSize = 16 * 1024)
        val meta = TorrentMetadataParser.parse(torrentBytes)

        val destDir = File(tempDir, "dest_multi").apply { mkdirs() }
        // Pre-create only fileA
        val destFileA = File(destDir, meta.files[0].path)
        destFileA.parentFile.mkdirs()
        Files.copy(fileA.toPath(), destFileA.toPath())

        val check = TorrentExistingDataDetector.detect(destDir, meta)
        assertTrue(check.hasExistingData, "Should detect existing fileA")
        assertEquals(1, check.existingFilesCount, "Only 1 of 2 files should exist")
        assertEquals(2, check.totalFilesCount)
        assertFalse(check.isSizeComplete, "Should not be size-complete")
        assertTrue(check.files[0].exists)
        assertFalse(check.files[1].exists)
    }

    @Test
    fun testRemoveTaskKeepFilesAndReAdd(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "remove_readd.bin", 64 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile, pieceSize = 32 * 1024)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = downloaderManager.createTask(
                id = 204L,
                source = TorrentSource.TorrentBytes(torrentBytes, "remove_readd.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    println(">>> DEBUG onFinished! snap=" + task.getSnapshot())
                    finishedLatch.countDown()
                }
                override fun onStateChanged(task: TorrentDownloadTask, oldState: TorrentTaskState, newState: TorrentTaskState) {
                    println(">>> DEBUG stateChanged: $oldState -> $newState, snap=" + task.getSnapshot())
                }
            })

            task.start()
            task.addPeer(seeder.endpoint)
            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS), "First download should finish")

            // Remove task keeping files
            val removed = downloaderManager.removeTask(204L, deleteFiles = false)
            assertTrue(removed, "Task should be removed")
            val targetFile = File(destDir, "remove_readd.bin")
            assertTrue(targetFile.exists(), "Target file must still exist after removal")

            // Re-add the same torrent pointing to the same folder
            val recheckLatch = CountDownLatch(1)
            val task2 = downloaderManager.createTask(
                id = 205L,
                source = TorrentSource.TorrentBytes(torrentBytes, "remove_readd.bin"),
                saveDir = destDir,
                verifyExisting = true
            )
            task2.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    recheckLatch.countDown()
                }
            })

            task2.start()
            // Recheck verifies existing file without seeder
            assertTrue(recheckLatch.await(15, TimeUnit.SECONDS), "Re-added task should verify existing pieces and complete")
            assertTrue(task2.isCompleted)
            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(targetFile))
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testDuplicateTorrentTaskRejection(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "dup.bin", 32 * 1024)
        val (torrentBytes, _) = TorrentTransferTestFixture.buildTorrent(testFile)

        val task1 = downloaderManager.createTask(
            id = 206L,
            source = TorrentSource.TorrentBytes(torrentBytes, "dup.bin"),
            saveDir = destDir
        )
        assertNotNull(task1)

        // Try creating another task with same infoHash
        val task2 = downloaderManager.createTask(
            id = 207L,
            source = TorrentSource.TorrentBytes(torrentBytes, "dup.bin"),
            saveDir = destDir
        )
        assertSame(task1, task2, "Duplicate task should return original instance")
    }
}
