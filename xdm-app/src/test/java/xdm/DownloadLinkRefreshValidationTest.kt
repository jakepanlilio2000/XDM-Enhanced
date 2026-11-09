package xdm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.partFileName
import xdm.core.downloaders.web.http.Chunk
import xdm.core.downloaders.web.http.ChunkStatus
import xdm.core.downloaders.web.http.HttpTaskContext
import xdm.core.downloaders.web.saveState
import xdm.manager.DbRecord
import xdm.manager.LinkValidationResult
import xdm.manager.RecordStatus
import xdm.app.ui.components.AppMenuHandler
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class DownloadLinkRefreshValidationTest : DownloadManagerTestBase() {

    private lateinit var mockServer: MockHttpProbeServer

    @BeforeEach
    fun initServer() {
        mockServer = MockHttpProbeServer()
    }

    @AfterEach
    fun shutdownServer() {
        mockServer.close()
    }

    @Test
    fun testValidateReplacementCompatible() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/expired")
        taskDB.saveHttpTask(task)

        val record = DbRecord(
            id = taskId,
            size = 1000L,
            downloaded = 200L,
            progress = 20,
            date = System.currentTimeMillis(),
            fileName = "test.bin",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http
        )
        appDB.add(record)

        mockServer.handler = { ex ->
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            ex.responseHeaders.add("Content-Range", "bytes 0-0/1000")
            ex.sendResponseHeaders(206, 1)
            ex.responseBody.write(byteArrayOf(0x01))
            ex.close()
        }

        val result = dm.validateReplacement(taskId, mockServer.url, null, null)
        assertTrue(result is LinkValidationResult.Compatible)
        val comp = result as LinkValidationResult.Compatible
        assertEquals(mockServer.url, comp.finalUrl)
        assertEquals(1000L, comp.contentLength)
    }

    @Test
    fun testValidateReplacementSizeMismatch() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/expired")
        taskDB.saveHttpTask(task)

        val record = DbRecord(
            id = taskId,
            size = 1000L,
            downloaded = 200L,
            progress = 20,
            date = System.currentTimeMillis(),
            fileName = "test.bin",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http
        )
        appDB.add(record)

        // Replacement URL returns 5000 bytes instead of 1000 bytes
        mockServer.handler = { ex ->
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            ex.responseHeaders.add("Content-Range", "bytes 0-0/5000")
            ex.sendResponseHeaders(206, 1)
            ex.responseBody.write(byteArrayOf(0x01))
            ex.close()
        }

        val result = dm.validateReplacement(taskId, mockServer.url, null, null)
        assertTrue(result is LinkValidationResult.Incompatible)
        val incomp = result as LinkValidationResult.Incompatible
        assertEquals(LinkValidationResult.IncompatibleReason.SIZE_MISMATCH, incomp.reason)
        assertEquals(5000L, incomp.replacementLength)
        assertEquals(1000L, incomp.expectedLength)
    }

    @Test
    fun testValidateReplacementNoRangeSupportWhenPartiallyDownloaded() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/expired")
        taskDB.saveHttpTask(task)

        val record = DbRecord(
            id = taskId,
            size = 1000L,
            downloaded = 350L, // already downloaded some bytes
            progress = 35,
            date = System.currentTimeMillis(),
            fileName = "test.bin",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http
        )
        appDB.add(record)

        // Server ignores Range header and returns 200 OK with declared and delivered length
        mockServer.handler = { ex ->
            val body = ByteArray(1000)
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.write(body)
            ex.close()
        }

        val result = dm.validateReplacement(taskId, mockServer.url, null, null)
        assertTrue(result is LinkValidationResult.Incompatible)
        val incomp = result as LinkValidationResult.Incompatible
        assertEquals(LinkValidationResult.IncompatibleReason.NO_RANGE_SUPPORT, incomp.reason)
    }

    @Test
    fun testValidateReplacementNotFound() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/expired")
        taskDB.saveHttpTask(task)

        mockServer.handler = { ex ->
            ex.sendResponseHeaders(404, -1)
            ex.close()
        }

        val result = dm.validateReplacement(taskId, mockServer.url, null, null)
        assertTrue(result is LinkValidationResult.Incompatible)
        val incomp = result as LinkValidationResult.Incompatible
        assertEquals(LinkValidationResult.IncompatibleReason.NOT_FOUND, incomp.reason)
    }

    @Test
    fun testValidateReplacementServerError() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/expired")
        taskDB.saveHttpTask(task)

        mockServer.handler = { ex ->
            ex.sendResponseHeaders(502, -1)
            ex.close()
        }

        val result = dm.validateReplacement(taskId, mockServer.url, null, null)
        assertTrue(result is LinkValidationResult.Incompatible)
        val incomp = result as LinkValidationResult.Incompatible
        assertEquals(LinkValidationResult.IncompatibleReason.SERVER_ERROR, incomp.reason)
    }

    @Test
    fun testValidateReplacementContentChanged() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/expired")
        taskDB.saveHttpTask(task)

        val record = DbRecord(
            id = taskId,
            size = 1000L,
            downloaded = 500L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "test.bin",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http
        )
        appDB.add(record)

        val context = HttpTaskContext(
            id = taskId,
            chunks = mutableMapOf(),
            init = AtomicBoolean(true),
            totalSize = 1000L,
            downloaded = AtomicLong(500L),
            url = "http://example.com/expired",
            contentType = null,
            headers = null,
            cookie = null,
            stopFlag = AtomicBoolean(false),
            completed = AtomicBoolean(false),
            tempFileName = partFileName(taskId),
            tempFileCreated = AtomicBoolean(true),
            diskError = AtomicBoolean(false),
            downloadHost = host(),
            tempFolder = task.targetFolder
        ).apply {
            validator = "old-etag-1"
            val chunk = Chunk(
                id = 0L,
                offset = 0L,
                length = AtomicLong(1000L),
                downloaded = AtomicLong(500L),
                status = AtomicReference(ChunkStatus.Downloading),
                fileHandle = AtomicReference(null),
                lastTakeOver = AtomicLong(0L),
                synced = AtomicLong(500L)
            )
            chunks[0L] = chunk
        }
        saveState(context, dir.absolutePath)

        // Replacement URL returns 200 OK because If-Range precondition failed (file modified)
        mockServer.handler = { ex ->
            val body = ByteArray(1000)
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.write(body)
            ex.close()
        }

        val result = dm.validateReplacement(taskId, mockServer.url, null, null)
        assertTrue(result is LinkValidationResult.Incompatible)
        val incomp = result as LinkValidationResult.Incompatible
        assertEquals(LinkValidationResult.IncompatibleReason.CONTENT_CHANGED, incomp.reason)
    }

    @Test
    fun testMenuValidationWithNullWindow() {
        // Must not crash or throw exceptions when menu action is invoked with null selection
        AppMenuHandler.openRefreshPage(null)
    }

    @Test
    fun testRestartDownloadWithNewLinkCleansPartAndState() {
        val taskId = nextId++
        val task = httpTask(taskId).copy(url = "http://example.com/old")
        taskDB.saveHttpTask(task)

        val targetDir = File(task.targetFolder)
        targetDir.mkdirs()
        val partFile = File(targetDir, partFileName(taskId))
        partFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertTrue(partFile.exists())

        val legacyPartFile = File(targetDir, "$taskId.xdmpart")
        legacyPartFile.writeBytes(byteArrayOf(6, 7, 8))
        assertTrue(legacyPartFile.exists())

        val stateFile = File(dir, "$taskId.state")
        stateFile.writeText("sample state")
        assertTrue(stateFile.exists())

        val record = DbRecord(
            id = taskId,
            size = 1000L,
            downloaded = 500L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = task.fileName,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.ERROR,
            downloadType = DownloadType.Http
        )
        appDB.add(record)

        val newUrl = "http://example.com/brand-new-link"
        dm.restartDownloadWithNewLink(taskId, newUrl, null, "cookie=fresh")

        assertFalse(partFile.exists(), "Standard part file should be deleted on controlled restart")
        assertFalse(legacyPartFile.exists(), "Legacy part file should also be deleted on controlled restart")
        assertFalse(stateFile.exists(), "State file should be deleted on controlled restart")

        val updatedRecord = appDB.getById(taskId)
        assertNotNull(updatedRecord)
        assertEquals(0L, updatedRecord!!.downloaded)
        assertEquals(0, updatedRecord.progress)
        assertEquals(RecordStatus.PAUSED, updatedRecord.status)

        val updatedTask = taskDB.getHttpTask(taskId)
        assertNotNull(updatedTask)
        assertEquals(newUrl, updatedTask!!.url)
        assertEquals("cookie=fresh", updatedTask.cookie)
    }

    private class MockHttpProbeServer : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var handler: (HttpExchange) -> Unit = {}

        init {
            server.createContext("/", { ex -> handler(ex) })
            server.start()
        }

        val url: String get() = "http://127.0.0.1:${server.address.port}/probe"

        override fun close() {
            server.stop(0)
        }
    }
}
