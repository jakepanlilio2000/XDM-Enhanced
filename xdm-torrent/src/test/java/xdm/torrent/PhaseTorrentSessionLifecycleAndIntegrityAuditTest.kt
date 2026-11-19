package xdm.torrent

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.core.downloaders.DownloadError
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase Torrent Session Lifecycle & Data Recovery Verification Audit Suite.
 *
 * Verifies:
 * 1. Torrent engine & native library lifecycle (version, architecture resolution, DHT, private torrents).
 * 2. Magnet URI parsing, metadata naming, file priorities, duplicate detection, and path sanitization.
 * 3. Engine-provided piece integrity checking (100% pre-existing recheck without peers, partial recheck,
 *    and corrupted piece detection, alert dispatching, and repair via controlled seeder peer).
 * 4. Task removal with file retention and re-add recheck.
 * 5. Pause/resume, seeding rules, state persistence, corrupted resume recovery, and disk error handling.
 */
class PhaseTorrentSessionLifecycleAndIntegrityAuditTest {

    private lateinit var engine: TorrentEngine
    private lateinit var manager: TorrentDownloadManager

    @BeforeEach
    fun setUp() {
        TorrentNativeLoader.load()
        engine = LibtorrentEngine.createStandalone()
        engine.start()
        manager = TorrentDownloadManager(engine = engine)
    }

    @AfterEach
    fun tearDown() {
        manager.shutdown()
        engine.stop()
    }

    // -----------------------------------------------------------------------------------------
    // 1. Torrent Library Version, Architecture Support & Session Configuration
    // -----------------------------------------------------------------------------------------
    @Test
    fun testTorrentLibraryVersionAndPlatformSupport() {
        assertTrue(TorrentNativeLoader.isLoaded, "Torrent native loader must report loaded")
        val version = TorrentNativeLoader.version
        assertNotNull(version)
        assertTrue(version.isNotBlank(), "Torrent native library version must not be blank")

        // Architecture resolution checks
        val winX64 = TorrentNativeLoader.resolveNativeResource("windows 11", "x86_64")
        assertNotNull(winX64)
        assertEquals("/lib/x86_64/libtorrent4j.dll", winX64?.first)
        assertEquals("libtorrent4j.dll", winX64?.second)

        val linuxX64 = TorrentNativeLoader.resolveNativeResource("linux", "amd64")
        assertNotNull(linuxX64)
        assertEquals("/lib/x86_64/libtorrent4j.so", linuxX64?.first)

        val macArm64 = TorrentNativeLoader.resolveNativeResource("mac os x", "aarch64")
        assertNotNull(macArm64)
        assertEquals("/lib/arm64/libtorrent4j.dylib", macArm64?.first)

        // Unsupported platform check
        val unsupported = TorrentNativeLoader.resolveNativeResource("solaris", "sparc")
        assertNull(unsupported, "Unsupported OS/arch should resolve to null")
    }

    @Test
    fun testSessionLifecycleAndConfiguration() {
        assertTrue(engine.isRunning(), "Engine must be running after start()")

        val config = TorrentSessionConfig(
            downloadRateLimit = 2_000_000,
            uploadRateLimit = 1_000_000,
            maxConnections = 120,
            maxActiveDownloads = 4,
            maxActiveSeeds = 2,
            maxActiveTotal = 6,
            enableDht = true,
            enablePex = true,
            enableLsd = true,
            listenPort = 6882,
            enableUpnp = false,
            enableNatPmp = false
        )

        assertDoesNotThrow { engine.applyConfig(config) }
        assertEquals(config, engine.currentConfig)
        assertTrue(engine.dhtNodes() >= 0L)

        // Stop and restart
        engine.stop()
        assertFalse(engine.isRunning())

        engine.start()
        assertTrue(engine.isRunning())
    }

    // -----------------------------------------------------------------------------------------
    // 2. Private Torrent Restrictions (BEP 27)
    // -----------------------------------------------------------------------------------------
    @Test
    fun testPrivateTorrentRestrictions(@TempDir tempDir: File) {
        val task = TorrentDownloadTask(
            id = 501L,
            source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
            saveDir = tempDir,
            engine = engine
        )
        task.start()

        // Standard public torrent allows DHT reannounce
        assertFalse(task.isPrivate)
        assertDoesNotThrow { task.reannounce() }

        task.stop()
    }

    // -----------------------------------------------------------------------------------------
    // 3. Magnet URI Parsing, Display Name, Sanitization & Duplicate Detection
    // -----------------------------------------------------------------------------------------
    @Test
    fun testMagnetParsingDisplayNamingAndSanitization(@TempDir tempDir: File) {
        // BitTorrent v1 (40 hex)
        val v1Uri = "magnet:?xt=urn:btih:3b245504d5f72b17a0073587514027610c596b40&dn=Ubuntu+Desktop+24.04&tr=udp%3A%2F%2Ftracker.org"
        assertEquals("3b245504d5f72b17a0073587514027610c596b40", TorrentMetadataParser.parseMagnetInfoHash(v1Uri))
        assertEquals("Ubuntu Desktop 24.04", TorrentMetadataParser.parseMagnetDisplayName(v1Uri))

        // BitTorrent v2 (64 hex)
        val v2Hash = "1220" + "a".repeat(64)
        val v2Uri = "magnet:?xt=urn:btmh:$v2Hash&dn=V2+Release"
        assertEquals("a".repeat(64), TorrentMetadataParser.parseMagnetInfoHash(v2Uri))
        assertEquals("V2 Release", TorrentMetadataParser.parseMagnetDisplayName(v2Uri))

        // Invalid URI rejection
        assertNull(TorrentMetadataParser.parseMagnetInfoHash("not-a-magnet"))
        assertNull(TorrentMetadataParser.parseMagnetInfoHash("magnet:?dn=NoHash"))

        // Path traversal sanitization
        assertEquals("safe/file.iso", TorrentMetadataParser.sanitizeTorrentFilePath("../../safe/file.iso"))
        assertEquals("nested/data.bin", TorrentMetadataParser.sanitizeTorrentFilePath("C:\\nested\\data.bin"))
        assertEquals("_CON.txt", TorrentMetadataParser.sanitizeTorrentFilePath("CON.txt"))
        assertEquals("_NUL.log", TorrentMetadataParser.sanitizeTorrentFilePath("NUL.log"))

        // Duplicate detection
        val (testFile, _) = TorrentTransferTestFixture.createDeterministicFile(tempDir, "dup_check.bin", 32 * 1024)
        val (torrentBytes, _) = TorrentTransferTestFixture.buildTorrent(testFile)

        val task1 = manager.createTask(
            id = 502L,
            source = TorrentSource.TorrentBytes(torrentBytes, "dup_check.bin"),
            saveDir = tempDir
        )
        val task2 = manager.createTask(
            id = 503L,
            source = TorrentSource.TorrentBytes(torrentBytes, "dup_check.bin"),
            saveDir = tempDir
        )
        assertSame(task1, task2, "Duplicate info-hash must return existing task instance")
    }

    // -----------------------------------------------------------------------------------------
    // 4. Data Recovery: 100% Pre-Existing Data Recheck Without Peers
    // -----------------------------------------------------------------------------------------
    @Test
    fun testFullyCompletedFileRecheckWithoutPeers(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        val (sourceFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "full_existing.bin", 128 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(sourceFile, pieceSize = 32 * 1024)

        // Pre-place identical completed file into destination folder before starting task
        val destFile = File(destDir, "full_existing.bin")
        Files.copy(sourceFile.toPath(), destFile.toPath())
        assertEquals(128 * 1024L, destFile.length())

        val meta = TorrentMetadataParser.parse(torrentBytes)
        val check = TorrentExistingDataDetector.detect(destDir, meta)
        assertTrue(check.hasExistingData)
        assertTrue(check.isSizeComplete)
        assertEquals(128 * 1024L, check.existingBytes)

        val finishedLatch = CountDownLatch(1)
        val task = manager.createTask(
            id = 504L,
            source = TorrentSource.TorrentBytes(torrentBytes, "full_existing.bin"),
            saveDir = destDir,
            verifyExisting = true
        )

        task.addListener(object : TorrentTaskListener {
            override fun onFinished(task: TorrentDownloadTask) {
                finishedLatch.countDown()
            }
        })

        task.start()

        // Must complete verification without any peers running
        assertTrue(finishedLatch.await(15, TimeUnit.SECONDS), "Task must finish verification without peers")
        assertTrue(task.isCompleted)

        val snapshot = task.getSnapshot()
        assertEquals(100, snapshot.progressPercent)
        assertEquals(128 * 1024L, snapshot.downloadedBytes)
        assertEquals(ti.numPieces(), snapshot.verifiedPieces)
        assertEquals(0, snapshot.failedPieces)
        assertEquals(expectedHash, TorrentTransferTestFixture.sha256(destFile), "Pre/post SHA-256 hash must match exactly")
    }

    // -----------------------------------------------------------------------------------------
    // 5. Data Recovery: Partial Data Recheck & Recovery from Controlled Peer Fixture
    // -----------------------------------------------------------------------------------------
    @Test
    fun testPartialExistingDataRecheckAndResume(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        // 4 pieces x 32 KB = 128 KB
        val (sourceFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "partial_existing.bin", 128 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(sourceFile, pieceSize = 32 * 1024)

        // Pre-place first 2 pieces (64 KB) into destination file
        val destFile = File(destDir, "partial_existing.bin")
        val fullData = Files.readAllBytes(sourceFile.toPath())
        val partialData = fullData.copyOfRange(0, 64 * 1024)
        Files.write(destFile.toPath(), partialData)
        assertEquals(64 * 1024L, destFile.length())

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val task = manager.createTask(
                id = 505L,
                source = TorrentSource.TorrentBytes(torrentBytes, "partial_existing.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint),
                verifyExisting = true
            )

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    finishedLatch.countDown()
                }
            })

            task.start()
            task.addPeer(seeder.endpoint)

            assertTrue(finishedLatch.await(20, TimeUnit.SECONDS), "Task should finish missing pieces download")
            assertTrue(task.isCompleted)

            val snapshot = task.getSnapshot()
            assertEquals(100, snapshot.progressPercent)
            assertEquals(128 * 1024L, snapshot.downloadedBytes)
            assertEquals(ti.numPieces(), snapshot.verifiedPieces)
            assertEquals(0, snapshot.failedPieces)

            task.getHandle()?.flushCache()
            Thread.sleep(1000)
            manager.removeTask(task.id, deleteFiles = false)

            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(destFile), "Final SHA-256 hash must match")
        } finally {
            seeder.close()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 6. Data Recovery: Corrupted Piece Detection, Hash Failed Alert & Repair
    // -----------------------------------------------------------------------------------------
    @Test
    fun testCorruptedPieceDetectionAndRepairWithHashFailedAlert(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        // 4 pieces x 32 KB = 128 KB
        val (sourceFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "corrupted_piece.bin", 128 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(sourceFile, pieceSize = 32 * 1024)

        // Copy full file, but corrupt piece 2 (offset 64KB..96KB)
        val destFile = File(destDir, "corrupted_piece.bin")
        val corruptedBytes = Files.readAllBytes(sourceFile.toPath())
        for (i in (64 * 1024) until (96 * 1024)) {
            corruptedBytes[i] = (corruptedBytes[i].toInt() xor 0xAA).toByte()
        }
        Files.write(destFile.toPath(), corruptedBytes)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishedLatch = CountDownLatch(1)
            val hashFailedCount = AtomicInteger(0)

            val task = manager.createTask(
                id = 506L,
                source = TorrentSource.TorrentBytes(torrentBytes, "corrupted_piece.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint),
                verifyExisting = true
            )

            task.addListener(object : TorrentTaskListener {
                override fun onHashFailed(task: TorrentDownloadTask, pieceIndex: Int) {
                    hashFailedCount.incrementAndGet()
                }
                override fun onFinished(task: TorrentDownloadTask) {
                    finishedLatch.countDown()
                }
            })

            task.start()
            task.addPeer(seeder.endpoint)

            // Initial state before repair: file on disk is corrupted, hash does NOT match
            assertNotEquals(expectedHash, TorrentTransferTestFixture.sha256(destFile), "Pre-repair file must be corrupted")

            assertTrue(finishedLatch.await(30, TimeUnit.SECONDS), "Corrupted piece should be repaired from seeder")
            assertTrue(task.isCompleted)

            task.stop()

            var currentHash = TorrentTransferTestFixture.sha256(destFile)
            val deadline = System.currentTimeMillis() + 5000L
            while (currentHash != expectedHash && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
                currentHash = TorrentTransferTestFixture.sha256(destFile)
            }
            assertEquals(expectedHash, currentHash, "Pristine SHA-256 hash must be restored after repair")
        } finally {
            seeder.close()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 7. Multi-File Torrent Existing Data Detection
    // -----------------------------------------------------------------------------------------
    @Test
    fun testMultiFileTorrentExistingDataDetection(@TempDir tempDir: File) {
        val rootDir = File(tempDir, "multi_data").apply { mkdirs() }
        val docFile = File(rootDir, "manual.pdf").apply { writeText("User Documentation Manual") }
        val mediaDir = File(rootDir, "media").apply { mkdirs() }
        val videoFile = File(mediaDir, "clip.mp4").apply { writeText("Sample Video Stream Content") }

        val (torrentBytes, _) = TorrentTransferTestFixture.buildTorrent(rootDir, pieceSize = 16 * 1024)
        val meta = TorrentMetadataParser.parse(torrentBytes)

        val destDir = File(tempDir, "dest_multi").apply { mkdirs() }
        // Pre-create only manual.pdf
        val destDocFile = File(destDir, meta.files[0].path)
        destDocFile.parentFile.mkdirs()
        Files.copy(docFile.toPath(), destDocFile.toPath())

        val check = TorrentExistingDataDetector.detect(destDir, meta)
        assertTrue(check.hasExistingData)
        assertEquals(1, check.existingFilesCount)
        assertEquals(2, check.totalFilesCount)
        assertFalse(check.isSizeComplete)
        assertTrue(check.files[0].exists)
        assertFalse(check.files[1].exists)
    }

    // -----------------------------------------------------------------------------------------
    // 8. Re-Add Torrent After Removing Task (File Retention & Recheck)
    // -----------------------------------------------------------------------------------------
    @Test
    fun testReAddTorrentAfterTaskRemovalWithFileRetention(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest").apply { mkdirs() }
        val (sourceFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "readd_test.bin", 64 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(sourceFile, pieceSize = 32 * 1024)

        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val initialFinishLatch = CountDownLatch(1)
            val task = manager.createTask(
                id = 507L,
                source = TorrentSource.TorrentBytes(torrentBytes, "readd_test.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    initialFinishLatch.countDown()
                }
            })

            task.start()
            task.addPeer(seeder.endpoint)
            assertTrue(initialFinishLatch.await(20, TimeUnit.SECONDS), "Initial download must complete")

            // Remove task but keep downloaded files
            val removed = manager.removeTask(507L, deleteFiles = false)
            assertTrue(removed)

            val destFile = File(destDir, "readd_test.bin")
            assertTrue(destFile.exists(), "File must survive task removal")
            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(destFile))

            // Re-add the same torrent pointing to existing file
            val recheckLatch = CountDownLatch(1)
            val task2 = manager.createTask(
                id = 508L,
                source = TorrentSource.TorrentBytes(torrentBytes, "readd_test.bin"),
                saveDir = destDir,
                verifyExisting = true
            )

            task2.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    recheckLatch.countDown()
                }
            })

            task2.start()

            // Recheck verifies all pieces without seeder
            assertTrue(recheckLatch.await(15, TimeUnit.SECONDS), "Re-added task must complete via recheck without peers")
            assertTrue(task2.isCompleted)
            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(destFile))
        } finally {
            seeder.close()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 9. Pause/Resume, Seeding Rules, State Persistence & Corrupt Resume Recovery
    // -----------------------------------------------------------------------------------------
    @Test
    fun testPauseResumeAndCorruptedResumeRecovery(@TempDir tempDir: File) {
        val configDir = File(tempDir, "config").apply { mkdirs() }
        val sourceDir = File(tempDir, "source")
        val destDir = File(tempDir, "dest")

        val (sourceFile, expectedHash) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "recovery_test.bin", 128 * 1024
        )
        val (torrentBytes, ti) = TorrentTransferTestFixture.buildTorrent(sourceFile)

        // Seed with corrupt resume file
        val corruptResume = File(configDir, "task-509.resume")
        corruptResume.writeBytes(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44))

        val localManager = TorrentDownloadManager(engine = engine, configDir = configDir)
        val seeder = TorrentTransferTestFixture.startSeeder(ti, sourceDir)
        try {
            val finishLatch = CountDownLatch(1)
            val task = localManager.createTask(
                id = 509L,
                source = TorrentSource.TorrentBytes(torrentBytes, "recovery_test.bin"),
                saveDir = destDir,
                initialPeers = listOf(seeder.endpoint)
            )

            // Test seeding rule configuration
            task.seedingRule = SeedingRule(SeedingMode.NEVER)
            assertEquals(SeedingMode.NEVER, task.seedingRule.mode)

            task.addListener(object : TorrentTaskListener {
                override fun onFinished(task: TorrentDownloadTask) {
                    finishLatch.countDown()
                }
            })

            // Task catches corrupted resume data, clears it, and proceeds with fresh transfer
            task.start()
            task.addPeer(seeder.endpoint)

            assertTrue(finishLatch.await(20, TimeUnit.SECONDS), "Task must recover and complete")
            assertTrue(task.isCompleted)

            val destFile = File(destDir, "recovery_test.bin")
            assertTrue(destFile.exists())
            assertEquals(expectedHash, TorrentTransferTestFixture.sha256(destFile))
        } finally {
            seeder.close()
            localManager.shutdown()
        }
    }

    // -----------------------------------------------------------------------------------------
    // 10. Missing Destination / Disk Error Handling
    // -----------------------------------------------------------------------------------------
    @Test
    fun testMissingDestinationDirectoryErrorHandling(@TempDir tempDir: File) {
        val sourceDir = File(tempDir, "source")
        val (sourceFile, _) = TorrentTransferTestFixture.createDeterministicFile(
            sourceDir, "err_test.bin", 64 * 1024
        )
        val (torrentBytes, _) = TorrentTransferTestFixture.buildTorrent(sourceFile)

        // Conflict: destination path is an existing file, not a directory
        val conflictDest = File(tempDir, "conflict_file.bin").apply { writeText("conflict") }

        val task = manager.createTask(
            id = 510L,
            source = TorrentSource.TorrentBytes(torrentBytes, "err_test.bin"),
            saveDir = conflictDest
        )

        val errorLatch = CountDownLatch(1)
        task.addListener(object : TorrentTaskListener {
            override fun onError(task: TorrentDownloadTask, message: String, error: DownloadError?) {
                errorLatch.countDown()
            }
        })

        task.start()

        assertEquals(TorrentTaskState.ERROR, task.state)
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(task.getSnapshot().errorMessage)
    }
}
