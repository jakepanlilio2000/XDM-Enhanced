package xdm.torrent

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentStatus
import xdm.core.downloaders.CommitResult
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadHost
import xdm.core.downloaders.DownloadStatusInfo
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.PauseEvent
import xdm.core.storage.StorageFile
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TorrentDownloadEngineTest {

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
    fun testCompleteDownloadWithIntegrity(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")
        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "payload.bin", 128 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = downloaderManager.createTask(
                id = 101L,
                source = TorrentSource.TorrentBytes(torrentBytes, "payload.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    finishedLatch.countDown()
                }
            })

            task.start()
            // Connect seeder peer
            task.addPeer(seeder.endpoint)

            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS), "Download should finish within 20s")
            assertTrue(task.isCompleted, "Task should report isCompleted = true")

            val downloadedFile = File(destDir, "payload.bin")
            assertTrue(downloadedFile.exists(), "Downloaded file must exist")
            assertEquals(128 * 1024L, downloadedFile.length(), "File size must match")
            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(downloadedFile), "SHA-256 integrity must match")

            val snapshot = task.getSnapshot()
            assertEquals(100, snapshot.progressPercent)
            assertEquals(128 * 1024L, snapshot.downloadedBytes)
            assertEquals(ti.numPieces(), snapshot.verifiedPieces)
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testPauseAndResume(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")
        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "pause_resume.bin", 128 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val task = downloaderManager.createTask(
                id = 102L,
                source = TorrentSource.TorrentBytes(torrentBytes, "pause_resume.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task.start()
            task.addPeer(seeder.endpoint)

            // Pause
            task.pause()
            assertEquals(TorrentTaskState.PAUSED, task.state, "State should be PAUSED")

            // Wait brief moment while paused
            Thread.sleep(300)
            assertEquals(TorrentTaskState.PAUSED, task.state)

            val finishedLatch = CountDownLatch(1)
            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    finishedLatch.countDown()
                }
            })

            // Resume
            task.resume()
            assertTrue(task.state == TorrentTaskState.DOWNLOADING || task.state == TorrentTaskState.FINISHED)

            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS), "Resumed transfer should complete")
            val downloadedFile = File(destDir, "pause_resume.bin")
            assertTrue(downloadedFile.exists())
            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(downloadedFile))
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testCancellationWithoutDeletingFiles(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "cancel_keep.bin", 64 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = downloaderManager.createTask(
                id = 103L,
                source = TorrentSource.TorrentBytes(torrentBytes, "cancel_keep.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )
            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) { finishedLatch.countDown() }
            })
            task.start()
            task.addPeer(seeder.endpoint)
            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS))

            val downloadedFile = File(destDir, "cancel_keep.bin")
            assertTrue(downloadedFile.exists(), "File should exist after download")

            // Remove task without deleting files (default)
            task.remove(deleteFiles = false)
            assertEquals(TorrentTaskState.STOPPED, task.state)
            assertNull(task.getHandle())
            assertTrue(downloadedFile.exists(), "File must STILL exist when deleteFiles = false")
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testCancellationWithDeletingFiles(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "cancel_delete.bin", 64 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = downloaderManager.createTask(
                id = 104L,
                source = TorrentSource.TorrentBytes(torrentBytes, "cancel_delete.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )
            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) { finishedLatch.countDown() }
            })
            task.start()
            task.addPeer(seeder.endpoint)
            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS))

            val downloadedFile = File(destDir, "cancel_delete.bin")
            assertTrue(downloadedFile.exists())

            // Remove task WITH deleting files
            task.remove(deleteFiles = true)
            assertEquals(TorrentTaskState.STOPPED, task.state)

            // Allow filesystem brief moment for async delete to complete
            var deleted = false
            for (i in 0 until 30) {
                if (!downloadedFile.exists()) {
                    deleted = true
                    break
                }
                Thread.sleep(100)
            }
            assertTrue(deleted, "Downloaded file should be deleted from disk when deleteFiles = true")
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testFileSelectionBeforeDownload(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "multi_source")
        sourceDir.mkdirs()
        val (file1, hash1) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "file_a.bin", 64 * 1024)
        val (file2, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "file_b.bin", 64 * 1024)

        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(sourceDir)
        assertEquals(2, ti.files().numFiles())

        val destDir = File(tempDir, "multi_dest")
        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir.parentFile)
        try {
            val finishedLatch = CountDownLatch(1)
            val indexA = (0 until ti.files().numFiles()).first { ti.files().filePath(it).contains("file_a.bin") }
            val indexB = (0 until ti.files().numFiles()).first { ti.files().filePath(it).contains("file_b.bin") }
            val selection = List(ti.files().numFiles()) { idx -> idx == indexA }

            val task = downloaderManager.createTask(
                id = 105L,
                source = TorrentSource.TorrentBytes(torrentBytes, "multi_source"),
                saveDir = destDir,
                fileSelection = selection,
                initialPeers = listOf(seeder.endpoint)
            )

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    finishedLatch.countDown()
                }
            })

            task.start()
            task.addPeer(seeder.endpoint)

            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS), "Selected file transfer should complete")

            val downloadedFileA = File(destDir, "multi_source/file_a.bin")
            val downloadedFileB = File(destDir, "multi_source/file_b.bin")

            assertTrue(downloadedFileA.exists(), "Selected file A should exist")
            assertEquals(hash1, TorrentTransferTestFixture.sha256(downloadedFileA), "Selected file A hash should match")
            assertTrue(!downloadedFileB.exists() || downloadedFileB.length() == 0L, "De-selected file B should not be downloaded")
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testInvalidDestinationHandling(@TempDir tempDir: File) {
        // Create an existing regular file and use it as saveDir (invalid destination)
        val invalidDest = File(tempDir, "regular_file.txt")
        invalidDest.writeText("blocking file")

        val sourceDir = File(tempDir, "source")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "dummy.bin", 32 * 1024)
        val (torrentBytes, _) = TorrentTransferTestFixture.buildTorrent(testFile)

        val errorLatch = CountDownLatch(1)
        val errorReported = AtomicReference<String?>(null)

        val task = downloaderManager.createTask(
            id = 106L,
            source = TorrentSource.TorrentBytes(torrentBytes, "dummy.bin"),
            saveDir = invalidDest
        )

        task.addListener(object : TorrentTaskListener {
            override fun onError(task: TorrentDownloadTask, message: String, error: DownloadError?) {
                errorReported.set(message)
                errorLatch.countDown()
            }
        })

        task.start()

        assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "Error callback should be triggered for invalid destination")
        assertEquals(TorrentTaskState.ERROR, task.state)
        assertNotNull(errorReported.get())
        assertTrue(errorReported.get()!!.contains("Destination path exists and is a file"))
    }

    @Test
    fun testMultipleSimultaneousTasks(@TempDir tempDir: File) {
        val sourceDir1 = File(tempDir, "source1")
        val sourceDir2 = File(tempDir, "source2")
        val destDir1 = File(tempDir, "dest1")
        val destDir2 = File(tempDir, "dest2")

        val (file1, hash1) = TorrentTransferTestFixture.createDeterministicFile(sourceDir1, "task1.bin", 96 * 1024)
        val (file2, hash2) = TorrentTransferTestFixture.createDeterministicFile(sourceDir2, "task2.bin", 64 * 1024)

        val (bytes1, ti1) = TorrentTransferTestFixture.buildTorrent(file1)
        val (bytes2, ti2) = TorrentTransferTestFixture.buildTorrent(file2)

        val seeder1 = TorrentTransferTestFixture.startSeeder(ti1, sourceDir1)
        val seeder2 = TorrentTransferTestFixture.startSeeder(ti2, sourceDir2)

        try {
            val latch1 = CountDownLatch(1)
            val latch2 = CountDownLatch(1)

            val task1 = downloaderManager.createTask(
                id = 107L,
                source = TorrentSource.TorrentBytes(bytes1, "task1.bin"),
                saveDir = destDir1,
                initialPeers = listOf(seeder1.endpoint)
            )
            task1.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) { latch1.countDown() }
            })

            val task2 = downloaderManager.createTask(
                id = 108L,
                source = TorrentSource.TorrentBytes(bytes2, "task2.bin"),
                saveDir = destDir2,
                initialPeers = listOf(seeder2.endpoint)
            )
            task2.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) { latch2.countDown() }
            })

            task1.start()
            task2.start()
            task1.addPeer(seeder1.endpoint)
            task2.addPeer(seeder2.endpoint)

            assertTrue(latch1.await(20, TimeUnit.SECONDS), "Task 1 should complete")
            assertTrue(latch2.await(20, TimeUnit.SECONDS), "Task 2 should complete")

            val dl1 = File(destDir1, "task1.bin")
            val dl2 = File(destDir2, "task2.bin")
            assertTrue(dl1.exists())
            assertTrue(dl2.exists())
            assertEquals(hash1, TorrentTransferTestFixture.sha256(dl1))
            assertEquals(hash2, TorrentTransferTestFixture.sha256(dl2))
        } finally {
            seeder1.close()
            seeder2.close()
        }
    }

    @Test
    fun testPieceHashingAndForceRecheck(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "recheck.bin", 128 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = downloaderManager.createTask(
                id = 109L,
                source = TorrentSource.TorrentBytes(torrentBytes, "recheck.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )
            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) { finishedLatch.countDown() }
            })
            task.start()
            task.addPeer(seeder.endpoint)
            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS))

            // Trigger forceRecheck
            task.forceRecheck()
            var verified = false
            for (i in 0 until 50) {
                val h = task.getHandle()
                if (h != null && h.isValid) {
                    val st = try { h.status(TorrentHandle.QUERY_PIECES) } catch (_: Throwable) { null }
                    if (st != null && (st.isFinished || st.isSeeding)) {
                        val pieceCount = st.pieces()?.count() ?: 0
                        if (pieceCount == ti.numPieces()) {
                            verified = true
                            break
                        }
                    }
                }
                Thread.sleep(100)
            }
            assertTrue(verified, "All pieces should be verified after recheck completes")
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testDownloadHostIntegration(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "host_test.bin", 64 * 1024)
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val activated = AtomicBoolean(false)
            val initReceived = AtomicBoolean(false)
            val progressReported = AtomicBoolean(false)
            val successReported = AtomicBoolean(false)
            val successLatch = CountDownLatch(1)

            val mockHost = object : DownloadHost {
                override fun onDownloadActivated(id: Long) { activated.set(true) }
                override fun onDownloadInit(data: DownloadStatusInfo.InitInfo, downloadType: DownloadType) {
                    initReceived.set(true)
                    assertEquals(DownloadType.Torrent, downloadType)
                }
                override fun onDownloadProgress(event: DownloadStatusInfo.ProgressInfo) { progressReported.set(true) }
                override fun onAssembleStart(id: Long) {}
                override fun onAssembleProgress(event: DownloadStatusInfo.AssembleInfo) {}
                override fun onDownloadSuccess(event: DownloadStatusInfo.FinalInfo) {
                    successReported.set(true)
                    successLatch.countDown()
                }
                override fun onDownloadFailed(id: Long, error: DownloadError) {}
                override fun onDownloadPaused(id: Long, event: PauseEvent) {}
                override val appDir: String get() = tempDir.absolutePath
                override val applySpeedLimit: Boolean get() = false
                override val speedLimit: Int get() = 0
                override fun getTempDir(id: Long, url: String, contentType: String?, contentDisposition: String?): String = tempDir.absolutePath
                override fun commitOutputFile(id: Long, part: StorageFile, downloadType: DownloadType): CommitResult = CommitResult.Success("host_test.bin", destDir.absolutePath)
            }

            val task = downloaderManager.createTask(
                id = 110L,
                source = TorrentSource.TorrentBytes(torrentBytes, "host_test.bin"),
                saveDir = destDir,
                host = mockHost,
                initialPeers = listOf(seeder.endpoint)
            )

            task.start()
            task.addPeer(seeder.endpoint)

            assertTrue(successLatch.await(20, TimeUnit.SECONDS), "Host success callback should be called")
            assertTrue(activated.get(), "Host onDownloadActivated should be called")
            assertTrue(initReceived.get(), "Host onDownloadInit should be called")
            assertTrue(successReported.get(), "Host onDownloadSuccess should be called")
        } finally {
            seeder.close()
        }
    }
}

