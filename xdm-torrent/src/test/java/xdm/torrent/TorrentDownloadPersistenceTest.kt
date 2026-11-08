package xdm.torrent

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.core.downloaders.DownloadError
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TorrentDownloadPersistenceTest {

    @Test
    fun testPauseExitRestartResume(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config")
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")

        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "pause_exit.bin", 128 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val engine1 = LibtorrentEngine.createStandalone()
            engine1.start()
            val manager1 = TorrentDownloadManager(engine = engine1, configDir = configDir)

            val task1 = manager1.createTask(
                id = 301L,
                source = TorrentSource.TorrentBytes(torrentBytes, "pause_exit.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task1.start()
            task1.addPeer(seeder.endpoint)
            task1.pause()
            assertEquals(TorrentTaskState.PAUSED, task1.state)

            // Graceful shutdown with resume data saved
            manager1.shutdown(saveResumeData = true)
            engine1.stop()

            assertTrue(File(configDir, "task-301.info").exists(), "task-301.info must exist")
            val resumeFile = File(configDir, "task-301.resume")
            assertTrue(resumeFile.exists(), "task-301.resume must exist")
            assertTrue(resumeFile.length() > 0, "Resume file must not be empty")

            // Restart app with Manager 2 on same configDir
            val engine2 = LibtorrentEngine.createStandalone()
            engine2.start()
            val manager2 = TorrentDownloadManager(engine = engine2, configDir = configDir)

            try {
                val restoredTasks = manager2.loadAllTasksFromDB()
                assertEquals(1, restoredTasks.size)
                val restoredTask = restoredTasks[0]
                assertEquals(301L, restoredTask.id)
                assertEquals(TorrentTaskState.PAUSED, restoredTask.state)

                val finishLatch = CountDownLatch(1)
                restoredTask.addListener(object : TorrentTaskListener {
                    override fun onFinished(task: TorrentDownloadTask) {
                        finishLatch.countDown()
                    }
                })

                restoredTask.resume()
                restoredTask.addPeer(seeder.endpoint)

                assertTrue(finishLatch.await(20, TimeUnit.SECONDS), "Restored task should finish within 20s")
                assertTrue(restoredTask.isCompleted)

                val downloadedFile = File(destDir, "pause_exit.bin")
                assertTrue(downloadedFile.exists())
                assertEquals(expectedHash, TorrentTransferTestFixture.sha256(downloadedFile))
            } finally {
                manager2.shutdown()
                engine2.stop()
            }
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testActiveDownloadRestartRecover(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config")
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")

        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "active_recover.bin", 256 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile, pieceSize = 32 * 1024)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val engine1 = LibtorrentEngine.createStandalone()
            engine1.start()
            val manager1 = TorrentDownloadManager(engine = engine1, configDir = configDir)

            val pieceLatch = CountDownLatch(2)
            val task1 = manager1.createTask(
                id = 302L,
                source = TorrentSource.TorrentBytes(torrentBytes, "active_recover.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task1.addListener(object : TorrentTaskListener {
                override fun onPieceFinished(task: TorrentDownloadTask, pieceIndex: Int, verifiedCount: Int, totalPieces: Int) {
                    pieceLatch.countDown()
                }
            })

            task1.start()
            task1.addPeer(seeder.endpoint)

            // Wait until some pieces are received
            pieceLatch.await(10, TimeUnit.SECONDS)

            // Shutdown session 1 while active
            manager1.shutdown(saveResumeData = true)
            engine1.stop()

            // Restart session 2
            val engine2 = LibtorrentEngine.createStandalone()
            engine2.start()
            val manager2 = TorrentDownloadManager(engine = engine2, configDir = configDir)

            try {
                val restoredTasks = manager2.loadAllTasksFromDB()
                assertEquals(1, restoredTasks.size)
                val restoredTask = restoredTasks[0]
                assertEquals(302L, restoredTask.id)

                val finishLatch = CountDownLatch(1)
                restoredTask.addListener(object : TorrentTaskListener {
                    override fun onFinished(task: TorrentDownloadTask) {
                        finishLatch.countDown()
                    }
                })

                restoredTask.resume()
                restoredTask.addPeer(seeder.endpoint)

                assertTrue(finishLatch.await(20, TimeUnit.SECONDS), "Restored task should finish within 20s")
                assertTrue(restoredTask.isCompleted)

                val downloadedFile = File(destDir, "active_recover.bin")
                assertTrue(downloadedFile.exists())
                assertEquals(expectedHash, TorrentTransferTestFixture.sha256(downloadedFile))
            } finally {
                manager2.shutdown()
                engine2.stop()
            }
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testCompletedDownloadRestart(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config")
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")

        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "completed.bin", 64 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val engine1 = LibtorrentEngine.createStandalone()
            engine1.start()
            val manager1 = TorrentDownloadManager(engine = engine1, configDir = configDir)

            val finishLatch = CountDownLatch(1)
            val task1 = manager1.createTask(
                id = 303L,
                source = TorrentSource.TorrentBytes(torrentBytes, "completed.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task1.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    finishLatch.countDown()
                }
            })

            task1.start()
            task1.addPeer(seeder.endpoint)
            assertTrue(finishLatch.await(20, TimeUnit.SECONDS))
            assertTrue(task1.isCompleted)

            manager1.shutdown(saveResumeData = true)
            engine1.stop()

            // Verify completed task restoration in new session
            val engine2 = LibtorrentEngine.createStandalone()
            engine2.start()
            val manager2 = TorrentDownloadManager(engine = engine2, configDir = configDir)

            try {
                val restoredTasks = manager2.loadAllTasksFromDB()
                assertEquals(1, restoredTasks.size)
                val restoredTask = restoredTasks[0]

                assertEquals(303L, restoredTask.id)
                assertTrue(restoredTask.isCompleted, "Restored task must be marked completed")
                assertEquals(TorrentTaskState.FINISHED, restoredTask.state)

                val snap = restoredTask.getSnapshot()
                assertEquals(100, snap.progressPercent)
                assertEquals(1.0f, snap.progressFraction)
                assertEquals(64 * 1024L, snap.downloadedBytes)
                assertEquals(64 * 1024L, snap.totalBytes)
                assertEquals(ti.numPieces(), snap.verifiedPieces)

                val downloadedFile = File(destDir, "completed.bin")
                assertTrue(downloadedFile.exists())
                assertEquals(expectedHash, TorrentTransferTestFixture.sha256(downloadedFile))
            } finally {
                manager2.shutdown()
                engine2.stop()
            }
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testMissingDownloadDirectory(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config")
        val sourceDir = File(tempDir, "source")
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "missing_dir.bin", 64 * 1024
        )
        val (torrentBytes, _) = TorrentTransferTestFixture.buildTorrent(testFile)

        val engine = LibtorrentEngine.createStandalone()
        engine.start()
        val manager = TorrentDownloadManager(engine = engine, configDir = configDir)

        try {
            // Create a conflict: destination is a file, not a directory
            val conflictFile = File(tempDir, "not_a_directory.dat")
            conflictFile.writeText("conflict")

            val task = manager.createTask(
                id = 304L,
                source = TorrentSource.TorrentBytes(torrentBytes, "missing_dir.bin"),
                saveDir = conflictFile
            )

            var errorNotified = false
            task.addListener(object : TorrentTaskListener {
                override fun onError(task: TorrentDownloadTask, message: String, error: DownloadError?) {
                    errorNotified = true
                }
            })

            task.start()

            assertEquals(TorrentTaskState.ERROR, task.state)
            assertTrue(errorNotified)
            assertNotNull(task.getSnapshot().errorMessage)

            // Change destination directory to a valid directory
            val validDir = File(tempDir, "valid_dest")
            val changed = task.changeSaveDir(validDir)
            assertTrue(changed)
            assertEquals(TorrentTaskState.PAUSED, task.state, "Changing saveDir from ERROR should transition to PAUSED")
            assertNull(task.getSnapshot().errorMessage)
        } finally {
            manager.shutdown()
            engine.stop()
        }
    }

    @Test
    fun testInvalidResumeData(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config")
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")

        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "invalid_resume.bin", 128 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val engine = LibtorrentEngine.createStandalone()
            engine.start()
            val manager = TorrentDownloadManager(engine = engine, configDir = configDir)

            try {
                // Pre-corrupt the resume file with invalid garbage bytes
                configDir.mkdirs()
                val resumeFile = File(configDir, "task-305.resume")
                resumeFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte()))

                val finishLatch = CountDownLatch(1)
                val task = manager.createTask(
                    id = 305L,
                    source = TorrentSource.TorrentBytes(torrentBytes, "invalid_resume.bin"),
                    saveDir = destDir,
                    initialPeers = listOf(seeder.endpoint)
                )

                task.addListener(object : TorrentTaskListener {
                    override fun onFinished(task: TorrentDownloadTask) {
                        finishLatch.countDown()
                    }
                })

                // When started, it catches IllegalArgumentException from corrupted resume data,
                // removes invalid resume file, and falls back to clean verification
                task.start()
                task.addPeer(seeder.endpoint)

                assertTrue(finishLatch.await(20, TimeUnit.SECONDS), "Task must successfully recover and complete")
                assertTrue(task.isCompleted)

                val downloadedFile = File(destDir, "invalid_resume.bin")
                assertTrue(downloadedFile.exists())
                assertEquals(expectedHash, TorrentTransferTestFixture.sha256(downloadedFile))
            } finally {
                manager.shutdown()
                engine.stop()
            }
        } finally {
            seeder.close()
        }
    }

    @Test
    fun testMultipleRestoredTorrents(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config")
        val sourceDir = File(tempDir, "source")
        val destDir1 = File(tempDir, "dest1")
        val destDir2 = File(tempDir, "dest2")

        val (file1, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "multi1.bin", 64 * 1024)
        val (bytes1, ti1) = TorrentTransferTestFixture.buildTorrent(file1)

        val (file2, _) = TorrentTransferTestFixture.createDeterministicFile(sourceDir, "multi2.bin", 96 * 1024)
        val (bytes2, ti2) = TorrentTransferTestFixture.buildTorrent(file2)

        val engine1 = LibtorrentEngine.createStandalone()
        engine1.start()
        val manager1 = TorrentDownloadManager(engine = engine1, configDir = configDir)

        val task1 = manager1.createTask(
            id = 306L,
            source = TorrentSource.TorrentBytes(bytes1, "multi1.bin"),
            saveDir = destDir1
        )
        val task2 = manager1.createTask(
            id = 307L,
            source = TorrentSource.TorrentBytes(bytes2, "multi2.bin"),
            saveDir = destDir2
        )

        assertNotEquals(task1.infoHash, task2.infoHash)
        manager1.shutdown(saveResumeData = true)
        engine1.stop()

        // Restart with manager 2
        val engine2 = LibtorrentEngine.createStandalone()
        engine2.start()
        val manager2 = TorrentDownloadManager(engine = engine2, configDir = configDir)

        try {
            val restored = manager2.loadAllTasksFromDB()
            assertEquals(2, restored.size)

            val r1 = manager2.getTask(306L)
            val r2 = manager2.getTask(307L)
            assertNotNull(r1)
            assertNotNull(r2)

            assertEquals("multi1.bin", r1!!.name)
            assertEquals("multi2.bin", r2!!.name)
            assertEquals(destDir1.absolutePath, r1.saveDir.absolutePath)
            assertEquals(destDir2.absolutePath, r2.saveDir.absolutePath)
            assertEquals(ti1.infoHash().toHex().lowercase(), r1.infoHash)
            assertEquals(ti2.infoHash().toHex().lowercase(), r2.infoHash)

            // Duplicate prevention: creating task with existing ID or infoHash returns existing task
            val dupById = manager2.createTask(306L, TorrentSource.TorrentBytes(bytes1, "multi1.bin"), destDir1)
            assertSame(r1, dupById, "Must return existing task by ID")

            val dupByHash = manager2.createTask(999L, TorrentSource.TorrentBytes(bytes1, "multi1.bin"), destDir1)
            assertSame(r1, dupByHash, "Must return existing task by infoHash")
        } finally {
            manager2.shutdown()
            engine2.stop()
        }
    }

    @Test
    fun testUncleanTerminationRecovery(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config")
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")

        val (testFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "unclean.bin", 256 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(testFile, pieceSize = 32 * 1024)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val engine1 = LibtorrentEngine.createStandalone()
            engine1.start()
            val manager1 = TorrentDownloadManager(engine = engine1, configDir = configDir)

            val pieceLatch = CountDownLatch(2)
            val task1 = manager1.createTask(
                id = 308L,
                source = TorrentSource.TorrentBytes(torrentBytes, "unclean.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task1.addListener(object : TorrentTaskListener {
                override fun onPieceFinished(task: TorrentDownloadTask, pieceIndex: Int, verifiedCount: Int, totalPieces: Int) {
                    pieceLatch.countDown()
                }
            })

            task1.start()
            task1.addPeer(seeder.endpoint)
            pieceLatch.await(10, TimeUnit.SECONDS)

            // Simulate unclean termination (kill): do NOT call manager1.shutdown(saveResumeData = true)
            // Abruptly stop engine
            engine1.stop()
            // Remove any resume file that might exist to simulate sudden power outage
            File(configDir, "task-308.resume").delete()

            // Task info file was safely persisted via AtomicIO at creation
            assertTrue(File(configDir, "task-308.info").exists())

            // Start manager 2
            val engine2 = LibtorrentEngine.createStandalone()
            engine2.start()
            val manager2 = TorrentDownloadManager(engine = engine2, configDir = configDir)

            try {
                val restoredTasks = manager2.loadAllTasksFromDB()
                assertEquals(1, restoredTasks.size)
                val restoredTask = restoredTasks[0]
                assertEquals(308L, restoredTask.id)

                val finishLatch = CountDownLatch(1)
                restoredTask.addListener(object : TorrentTaskListener {
                    override fun onFinished(task: TorrentDownloadTask) {
                        finishLatch.countDown()
                    }
                })

                // When resumed without resume file, it performs piece hash check on disk and continues
                restoredTask.resume()
                restoredTask.addPeer(seeder.endpoint)

                assertTrue(finishLatch.await(20, TimeUnit.SECONDS), "Must finish download after unclean recovery")
                assertTrue(restoredTask.isCompleted)

                val downloadedFile = File(destDir, "unclean.bin")
                assertTrue(downloadedFile.exists())
                assertEquals(expectedHash, TorrentTransferTestFixture.sha256(downloadedFile))
            } finally {
                manager2.shutdown()
                engine2.stop()
            }
        } finally {
            seeder.close()
        }
    }
}

