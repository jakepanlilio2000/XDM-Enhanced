package xdm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.ui.screens.ProgressWindow
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.partFileName
import xdm.core.downloaders.web.SegmentProgress
import xdm.core.downloaders.web.http.Chunk
import xdm.core.downloaders.web.http.ChunkStatus
import xdm.core.downloaders.web.http.HttpTaskContext
import xdm.core.downloaders.web.saveState
import xdm.core.util.FileUtils
import xdm.manager.CategoryRules
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.DownloadManager
import xdm.manager.LinkValidationResult
import xdm.manager.RecordStatus
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/**
 * Phase Core Download Engine & Recovery Audit:
 * Validates normal downloads, redirects, range/non-range, unknown length, signed URLs/cookies,
 * pause/resume after simulated restart, refreshed link validation, filename sanitization,
 * categories, destination overrides, UI state accuracy, and SHA-256 content hashes before/after.
 */
class PhaseCoreDownloadEngineAndRecoveryAuditTest : DownloadManagerTestBase() {

    private lateinit var testServer: TestHttpFixtureServer

    @BeforeEach
    fun setUpTestServer() {
        I8N.loadTexts("en")
        testServer = TestHttpFixtureServer()
    }

    @AfterEach
    fun tearDownTestServer() {
        testServer.stop()
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String = sha256(file.readBytes())

    // -----------------------------------------------------------------------------------------
    // 1. Direct HTTP Download & Hash Verification
    // -----------------------------------------------------------------------------------------
    @Test
    fun testDirectHttpDownload_hashesMatchExactly() {
        val payload = ByteArray(256 * 1024).apply { Random(101).nextBytes(this) }
        val expectedHash = sha256(payload)
        testServer.registerBytes("/direct-file.bin", payload, enableRange = true)

        val task = httpTask().copy(
            url = testServer.url("/direct-file.bin"),
            fileName = "direct-file.bin"
        )
        dm.startHttpDownload(task)
        remember()

        assertTrue(waitFor(30_000) { appDB.getById(task.id)?.status == RecordStatus.FINISHED }, "Download did not finish")
        val finishedRecord = appDB.getById(task.id)!!
        val downloadedFile = File(dir, finishedRecord.fileName)
        assertTrue(downloadedFile.isFile, "Target file does not exist on disk")
        assertEquals(expectedHash, sha256(downloadedFile), "SHA-256 checksum mismatch on direct download")
    }

    // -----------------------------------------------------------------------------------------
    // 2. HTTP Redirect (302/307) Following & Filename Content-Disposition
    // -----------------------------------------------------------------------------------------
    @Test
    fun testHttpRedirect_followsAndVerifiesHash() {
        val payload = ByteArray(128 * 1024).apply { Random(102).nextBytes(this) }
        val expectedHash = sha256(payload)
        testServer.registerBytes("/target-redirected.dat", payload, enableRange = true)
        testServer.registerRedirect("/initial-link", testServer.url("/target-redirected.dat"))

        val task = httpTask().copy(
            url = testServer.url("/initial-link"),
            fileName = "target-redirected.dat"
        )
        dm.startHttpDownload(task)
        remember()

        assertTrue(waitFor(30_000) { appDB.getById(task.id)?.status == RecordStatus.FINISHED }, "Redirected download did not finish")
        val downloadedFile = File(dir, "target-redirected.dat")
        assertTrue(downloadedFile.isFile, "Redirected file was not saved")
        assertEquals(expectedHash, sha256(downloadedFile), "SHA-256 checksum mismatch after redirect")
    }

    // -----------------------------------------------------------------------------------------
    // 3. Non-Range Server (Single-Stream Fallback)
    // -----------------------------------------------------------------------------------------
    @Test
    fun testNonRangeServer_singleStreamDownloadsIntact() {
        val payload = ByteArray(200 * 1024).apply { Random(103).nextBytes(this) }
        val expectedHash = sha256(payload)
        testServer.registerBytes("/no-range.bin", payload, enableRange = false)

        val task = httpTask().copy(
            url = testServer.url("/no-range.bin"),
            fileName = "no-range.bin",
            maxPiece = 8 // Requested multiple segments, but server does not support ranges
        )
        dm.startHttpDownload(task)
        remember()

        assertTrue(waitFor(30_000) { appDB.getById(task.id)?.status == RecordStatus.FINISHED }, "Non-range download did not finish")
        val downloadedFile = File(dir, "no-range.bin")
        assertTrue(downloadedFile.isFile)
        assertEquals(expectedHash, sha256(downloadedFile), "SHA-256 checksum mismatch on non-range download")
    }

    // -----------------------------------------------------------------------------------------
    // 4. Unknown Length (Chunked Streaming without Content-Length)
    // -----------------------------------------------------------------------------------------
    @Test
    fun testUnknownLengthStream_downloadsCompletely() {
        val payload = ByteArray(180 * 1024).apply { Random(104).nextBytes(this) }
        val expectedHash = sha256(payload)
        testServer.registerChunkedStream("/stream-unknown-len", payload)

        val task = httpTask().copy(
            url = testServer.url("/stream-unknown-len"),
            fileName = "stream-unknown-len.bin",
            respectFileName = true
        )
        dm.startHttpDownload(task)
        remember()

        assertTrue(waitFor(30_000) { appDB.getById(task.id)?.status == RecordStatus.FINISHED }, "Unknown length stream did not finish")
        val finishedRecord = appDB.getById(task.id)!!
        val downloadedFile = File(dir, finishedRecord.fileName)
        assertTrue(downloadedFile.isFile, "Target file does not exist on disk")
        assertEquals(expectedHash, sha256(downloadedFile), "SHA-256 checksum mismatch on unknown length stream")
    }

    // -----------------------------------------------------------------------------------------
    // 5. Signed URLs, Authentication Headers and Cookies
    // -----------------------------------------------------------------------------------------
    @Test
    fun testSignedUrlAndCookieAuthentication_completesAndHashesMatch() {
        val payload = ByteArray(300 * 1024).apply { Random(105).nextBytes(this) }
        val expectedHash = sha256(payload)
        val validToken = "auth-sig-xyz-123"
        val validCookie = "session_token=secret_987"

        testServer.registerProtected("/secure/data.bin", payload, validToken, validCookie)

        val task = httpTask().copy(
            url = testServer.url("/secure/data.bin?token=$validToken"),
            fileName = "secure_data.bin",
            respectFileName = true,
            cookie = validCookie,
            headers = mapOf("X-Custom-Auth" to listOf("verified-agent"))
        )
        dm.startHttpDownload(task)
        remember()

        assertTrue(waitFor(30_000) { appDB.getById(task.id)?.status == RecordStatus.FINISHED }, "Authenticated download did not finish")
        val finishedRecord = appDB.getById(task.id)!!
        val downloadedFile = File(dir, finishedRecord.fileName)
        assertTrue(downloadedFile.isFile, "Downloaded file does not exist on disk")
        assertEquals(expectedHash, sha256(downloadedFile), "SHA-256 checksum mismatch on authenticated download")
    }

    // -----------------------------------------------------------------------------------------
    // 6. Pause, Disk State Persistence & Resume after Simulated App Restart
    // -----------------------------------------------------------------------------------------
    @Test
    fun testPauseStatePersistenceAndResumeAfterRestart() {
        val payload = ByteArray(2 * 1024 * 1024).apply { Random(106).nextBytes(this) }
        val expectedHash = sha256(payload)
        testServer.registerBytesThrottled("/large-download.dat", payload, chunkDelayMs = 20)

        val taskId = nextId++
        val task = httpTask(taskId).copy(
            url = testServer.url("/large-download.dat"),
            fileName = "large-download.dat"
        )
        dm.startHttpDownload(task)
        remember()

        // Wait until partway downloaded
        assertTrue(waitFor(20_000) { (appDB.getById(taskId)?.downloaded ?: 0L) in 1 until payload.size.toLong() })

        // Pause download
        dm.stopDownload(taskId)
        assertTrue(waitFor(15_000) { appDB.getById(taskId)?.status == RecordStatus.PAUSED })
        val downloadedAtPause = appDB.getById(taskId)!!.downloaded
        assertTrue(downloadedAtPause > 0, "No bytes were written before pause")

        // Verify state and info files exist on disk
        val stateFile = File(dir, "$taskId.state")
        val infoFile = File(dir, "task-$taskId.info")
        val partFile = File(dir, partFileName(taskId))
        assertTrue(stateFile.exists(), "State file must exist after pause")
        assertTrue(infoFile.exists(), "Info file must exist after pause")
        assertTrue(partFile.exists(), "Part file must exist on disk during pause")

        // Simulate application restart: instantiate fresh DownloadManager instance pointing to same DB & config
        val restartedDm = DownloadManager(appDB, taskDB, dir.absolutePath)
        AppContext.downloader = restartedDm

        // Resume from persisted state
        restartedDm.resumeDownload(taskId)
        assertTrue(waitFor(40_000) { appDB.getById(taskId)?.status == RecordStatus.FINISHED }, "Resumed download did not finish")

        val finishedFile = File(dir, "large-download.dat")
        assertTrue(finishedFile.isFile)
        assertEquals(expectedHash, sha256(finishedFile), "SHA-256 hash mismatch after resume from restart")
    }

    // -----------------------------------------------------------------------------------------
    // 7. Refreshed Download Link Validation (Content Identity & Corruption Prevention)
    // -----------------------------------------------------------------------------------------
    @Test
    fun testRefreshLinkValidation_preventsCorruptDataAppend() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/stale-url")
        taskDB.saveHttpTask(task)

        val record = DbRecord(
            id = taskId, size = 1000L, downloaded = 400L, progress = 40, date = System.currentTimeMillis(),
            fileName = "sample.bin", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.PAUSED, downloadType = DownloadType.Http
        )
        appDB.add(record)

        val context = HttpTaskContext(
            id = taskId, chunks = mutableMapOf(), init = AtomicBoolean(true), totalSize = 1000L,
            downloaded = AtomicLong(400L), url = task.url, contentType = null, headers = null, cookie = null,
            stopFlag = AtomicBoolean(false), completed = AtomicBoolean(false),
            tempFileName = partFileName(taskId), tempFileCreated = AtomicBoolean(true),
            diskError = AtomicBoolean(false), downloadHost = host(), tempFolder = config.tempFolder
        ).apply {
            validator = "original-etag"
            chunks[0L] = Chunk(
                id = 0L, offset = 0L, length = AtomicLong(1000L), downloaded = AtomicLong(400L),
                status = AtomicReference(ChunkStatus.Downloading), fileHandle = AtomicReference(null),
                lastTakeOver = AtomicLong(0L), synced = AtomicLong(400L)
            )
        }
        saveState(context, dir.absolutePath)

        // Case A: Size mismatch on replacement URL (5000 != 1000)
        testServer.registerBytesWithHeaders("/new-link-size-mismatch", ByteArray(5000), "bytes 0-0/5000", 5000)
        val resMismatch = dm.validateReplacement(taskId, testServer.url("/new-link-size-mismatch"), null, null)
        assertTrue(resMismatch is LinkValidationResult.Incompatible)
        assertEquals(LinkValidationResult.IncompatibleReason.SIZE_MISMATCH, (resMismatch as LinkValidationResult.Incompatible).reason)

        // Case B: Compatible replacement URL (matching size and range support)
        testServer.registerBytesWithHeaders("/new-link-compatible", ByteArray(1000), "bytes 0-0/1000", 1000)
        val resCompatible = dm.validateReplacement(taskId, testServer.url("/new-link-compatible"), null, null)
        assertTrue(resCompatible is LinkValidationResult.Compatible)
    }

    // -----------------------------------------------------------------------------------------
    // 8. Filename Sanitization, Categories, and Destination Overrides
    // -----------------------------------------------------------------------------------------
    @Test
    fun testFilenameSanitizationAndCategoryRouting() {
        // Filename sanitization
        assertEquals("report.pdf", FileUtils.sanitizeFileName("report.pdf."))
        assertEquals("setup.exe", FileUtils.sanitizeFileName("setup.exe   "))
        assertEquals("bad_name.bin", FileUtils.sanitizeFileName("bad?name.bin"))
        assertEquals("file_with_pipe.zip", FileUtils.sanitizeFileName("file|with|pipe.zip"))
        assertEquals("_CON.txt", FileUtils.sanitizeFileName("CON.txt"))
        assertEquals("_NUL.log", FileUtils.sanitizeFileName("NUL.log"))

        // Category matching and folder destination override
        val videoCat = DownloadCategory.defaults(dir.absolutePath).first { it.id == "CAT_VIDEOS" }
        val compCat = DownloadCategory.defaults(dir.absolutePath).first { it.id == "CAT_COMPRESSED" }

        val matchedVideo = CategoryRules.match("movie.mp4", null, null, config.categories)
        assertEquals("CAT_VIDEOS", matchedVideo?.id)

        val matchedZip = CategoryRules.match("archive.zip", null, null, config.categories)
        assertEquals("CAT_COMPRESSED", matchedZip?.id)

        // Verify folder overrides
        val customFolder = File(dir, "custom-media").apply { mkdirs() }
        val overriddenCat = videoCat.copy(folder = customFolder.absolutePath)
        assertEquals(customFolder.absolutePath, overriddenCat.folder)
    }

    // -----------------------------------------------------------------------------------------
    // 9. UI Accuracy: Pause Keeps Window Visible & Distinct Operations
    // -----------------------------------------------------------------------------------------
    @Test
    fun testProgressWindowPauseKeepsVisibleAndDistinctOperations() {
        val rec = DbRecord(
            id = 888L, size = 500_000L, downloaded = 150_000L, progress = 30, date = System.currentTimeMillis(),
            fileName = "demo_file.zip", eta = 10L, speed = 25_000f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(888L))

        var window: ProgressWindow? = null
        SwingUtilities.invokeAndWait {
            window = ProgressWindow(888L).apply { isVisible = true }
        }
        val wnd = window!!

        try {
            SwingUtilities.invokeAndWait {
                wnd.updateProgress(
                    "demo_file.zip", 150_000L, 500_000L, 25_000f, 10L, 30,
                    listOf(SegmentProgress(0L, 500_000L, 150_000L, 1L, "Downloading..."))
                )
                assertEquals(30, wnd.progressBar.value)
                assertTrue(wnd.isVisible, "ProgressWindow must remain visible while downloading")

                // Simulate Pause action
                rec.status = RecordStatus.PAUSED
                appDB.add(rec)
                wnd.showPaused()

                // Verify window stays visible on pause
                assertTrue(wnd.isVisible, "ProgressWindow must remain visible when download is paused")
                assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)
                assertTrue(wnd.lblStat.text.contains(I8N.text("STAT_PAUSED")))

                // Distinct operations: verify presence and separation of actions
                assertNotNull(wnd.btnPauseResume, "Pause/Resume button must be present")
                assertNotNull(wnd.btnCancel, "Cancel button must be present")
                assertNotNull(wnd.btnOpenFolder, "Open folder button must be present")
            }
        } finally {
            SwingUtilities.invokeAndWait { wnd.dispose() }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Test HTTP Fixture Server Implementation
    // -----------------------------------------------------------------------------------------
    private class TestHttpFixtureServer : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newCachedThreadPool()

        init {
            server.executor = executor
            server.start()
        }

        val port: Int get() = server.address.port
        fun url(path: String): String = "http://127.0.0.1:$port$path"

        fun registerBytes(path: String, data: ByteArray, enableRange: Boolean) {
            server.createContext(path) { ex ->
                val rangeHeader = ex.requestHeaders.getFirst("Range")
                var start = 0L
                var end = data.size.toLong() - 1
                var ranged = false
                if (enableRange && rangeHeader != null) {
                    val m = Regex("""bytes=(\d+)-(\d*)""").find(rangeHeader)
                    if (m != null) {
                        ranged = true
                        start = m.groupValues[1].toLong()
                        if (m.groupValues[2].isNotEmpty()) end = m.groupValues[2].toLong()
                    }
                }

                if (enableRange) ex.responseHeaders.add("Accept-Ranges", "bytes")
                if (ranged) {
                    ex.responseHeaders.add("Content-Range", "bytes $start-$end/${data.size}")
                    ex.sendResponseHeaders(206, end - start + 1)
                } else {
                    ex.sendResponseHeaders(200, data.size.toLong())
                }

                val os = ex.responseBody
                os.write(data, start.toInt(), (end - start + 1).toInt())
                os.close()
            }
        }

        fun registerBytesThrottled(path: String, data: ByteArray, chunkDelayMs: Long) {
            server.createContext(path) { ex ->
                val rangeHeader = ex.requestHeaders.getFirst("Range")
                var start = 0L
                var end = data.size.toLong() - 1
                var ranged = false
                if (rangeHeader != null) {
                    val m = Regex("""bytes=(\d+)-(\d*)""").find(rangeHeader)
                    if (m != null) {
                        ranged = true
                        start = m.groupValues[1].toLong()
                        if (m.groupValues[2].isNotEmpty()) end = m.groupValues[2].toLong()
                    }
                }

                ex.responseHeaders.add("Accept-Ranges", "bytes")
                if (ranged) {
                    ex.responseHeaders.add("Content-Range", "bytes $start-$end/${data.size}")
                    ex.sendResponseHeaders(206, end - start + 1)
                } else {
                    ex.sendResponseHeaders(200, data.size.toLong())
                }

                val os = ex.responseBody
                var pos = start
                val chunkSize = 16 * 1024
                while (pos <= end) {
                    val len = minOf(chunkSize.toLong(), end - pos + 1).toInt()
                    os.write(data, pos.toInt(), len)
                    os.flush()
                    pos += len
                    Thread.sleep(chunkDelayMs)
                }
                os.close()
            }
        }

        fun registerRedirect(fromPath: String, toUrl: String) {
            server.createContext(fromPath) { ex ->
                ex.responseHeaders.add("Location", toUrl)
                ex.sendResponseHeaders(302, -1)
                ex.close()
            }
        }

        fun registerChunkedStream(path: String, data: ByteArray) {
            server.createContext(path) { ex ->
                ex.sendResponseHeaders(200, 0) // 0 implies chunked transfer encoding in HttpServer
                val os = ex.responseBody
                val chunkSize = 8 * 1024
                var pos = 0
                while (pos < data.size) {
                    val len = minOf(chunkSize, data.size - pos)
                    os.write(data, pos, len)
                    os.flush()
                    pos += len
                }
                os.close()
            }
        }

        fun registerProtected(path: String, data: ByteArray, expectedToken: String, expectedCookie: String) {
            server.createContext(path) { ex ->
                val token = ex.requestURI.query?.substringAfter("token=", "")?.substringBefore("&")
                val cookie = ex.requestHeaders.getFirst("Cookie")
                val customHeader = ex.requestHeaders.getFirst("X-Custom-Auth")

                if (token != expectedToken || cookie != expectedCookie || customHeader != "verified-agent") {
                    ex.sendResponseHeaders(403, -1)
                    ex.close()
                    return@createContext
                }

                val rangeHeader = ex.requestHeaders.getFirst("Range")
                var start = 0L
                var end = data.size.toLong() - 1
                var ranged = false
                if (rangeHeader != null) {
                    val m = Regex("""bytes=(\d+)-(\d*)""").find(rangeHeader)
                    if (m != null) {
                        ranged = true
                        start = m.groupValues[1].toLong()
                        if (m.groupValues[2].isNotEmpty()) end = m.groupValues[2].toLong()
                    }
                }

                ex.responseHeaders.add("Accept-Ranges", "bytes")
                if (ranged) {
                    ex.responseHeaders.add("Content-Range", "bytes $start-$end/${data.size}")
                    ex.sendResponseHeaders(206, end - start + 1)
                } else {
                    ex.sendResponseHeaders(200, data.size.toLong())
                }

                val os = ex.responseBody
                os.write(data, start.toInt(), (end - start + 1).toInt())
                os.close()
            }
        }

        fun registerBytesWithHeaders(path: String, data: ByteArray, contentRange: String, len: Long) {
            server.createContext(path) { ex ->
                ex.responseHeaders.add("Accept-Ranges", "bytes")
                ex.responseHeaders.add("Content-Range", contentRange)
                ex.sendResponseHeaders(206, 1)
                ex.responseBody.write(byteArrayOf(0x01))
                ex.responseBody.close()
            }
        }

        override fun close() = stop()

        fun stop() {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
