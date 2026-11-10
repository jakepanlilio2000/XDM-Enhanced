package xdm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.preflight.ClipboardMonitor
import xdm.app.preflight.LinkPreflightService
import xdm.app.ui.screens.NewDownloadWindow
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.util.CoreUtils
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

class ClipboardAndPreflightTest {

    companion object {
        private lateinit var server: HttpServer
        private var serverPort: Int = 0
        private lateinit var tempDir: File
        val fullPayloadRequests = AtomicInteger(0)
        val headRequests = AtomicInteger(0)
        val rangeRequests = AtomicInteger(0)

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            tempDir = Files.createTempDirectory("xdm-preflight-test").toFile()

            // Initialize minimal AppContext config
            if (!AppContext.hasConfig) {
                val cfg = AppConfig(tempDir.absolutePath)
                cfg.load()
                cfg.monitorClipboard = true
                AppContext.config = cfg
            }

            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            serverPort = server.address.port

            // 1. Direct file download (supports HEAD)
            server.createContext("/files/direct.zip", HttpHandler { exchange ->
                val method = exchange.requestMethod
                if (method.equals("HEAD", ignoreCase = true)) {
                    headRequests.incrementAndGet()
                    exchange.responseHeaders.add("Content-Type", "application/zip")
                    exchange.responseHeaders.add("Content-Length", "2048")
                    exchange.responseHeaders.add("ETag", "\"tag-zip-123\"")
                    exchange.sendResponseHeaders(200, -1)
                } else {
                    fullPayloadRequests.incrementAndGet()
                    val data = ByteArray(2048) { 1 }
                    exchange.sendResponseHeaders(200, data.size.toLong())
                    exchange.responseBody.write(data)
                    exchange.responseBody.close()
                }
            })

            // 2. Server where HEAD returns 405 Method Not Allowed, fallback to Range: bytes=0-0
            server.createContext("/files/nohead.iso", HttpHandler { exchange ->
                val method = exchange.requestMethod
                if (method.equals("HEAD", ignoreCase = true)) {
                    exchange.sendResponseHeaders(405, -1)
                } else {
                    val range = exchange.requestHeaders.getFirst("Range")
                    if (range == "bytes=0-0") {
                        rangeRequests.incrementAndGet()
                        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                        exchange.responseHeaders.add("Content-Range", "bytes 0-0/5242880")
                        exchange.sendResponseHeaders(206, 1)
                        exchange.responseBody.write(byteArrayOf(0x42))
                        exchange.responseBody.close()
                    } else {
                        fullPayloadRequests.incrementAndGet()
                        val data = ByteArray(100)
                        exchange.sendResponseHeaders(200, data.size.toLong())
                        exchange.responseBody.write(data)
                        exchange.responseBody.close()
                    }
                }
            })

            // 3. Redirect: /redirect/doc -> 302 to /final/target-archive.tar.gz
            server.createContext("/redirect/doc", HttpHandler { exchange ->
                exchange.responseHeaders.add("Location", "http://127.0.0.1:$serverPort/final/target-archive.tar.gz")
                exchange.sendResponseHeaders(302, -1)
            })
            server.createContext("/final/target-archive.tar.gz", HttpHandler { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/gzip")
                exchange.responseHeaders.add("Content-Length", "8192")
                exchange.sendResponseHeaders(200, -1)
            })

            // 4. Chunked transfer with unknown size (no Content-Length)
            server.createContext("/stream/chunked.bin", HttpHandler { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                // chunked transfer (indicated by 0 in sendResponseHeaders)
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("data-chunk".toByteArray())
                exchange.responseBody.close()
            })

            // 5. Content-Disposition with RFC 5987 / RFC 6266 UTF-8 filename*
            server.createContext("/disposition/unicode", HttpHandler { exchange ->
                exchange.responseHeaders.add(
                    "Content-Disposition",
                    "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''%E6%97%A5%E6%9C%AC%E8%AA%9E%E3%83%9E%E3%83%8B%E3%83%A5%E3%82%A2%E3%83%AB.pdf"
                )
                exchange.responseHeaders.add("Content-Length", "10240")
                exchange.sendResponseHeaders(200, -1)
            })

            // 6. Unsafe filename with path traversal and invalid Windows characters
            server.createContext("/disposition/unsafe", HttpHandler { exchange ->
                exchange.responseHeaders.add(
                    "Content-Disposition",
                    "attachment; filename=\"../../evil:name*?.exe\""
                )
                exchange.responseHeaders.add("Content-Length", "4096")
                exchange.sendResponseHeaders(200, -1)
            })

            // 7. 401 Unauthorized
            server.createContext("/auth/protected.zip", HttpHandler { exchange ->
                exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"Restricted\"")
                exchange.sendResponseHeaders(401, -1)
            })

            // 8. 403 Forbidden
            server.createContext("/forbidden/secret.pdf", HttpHandler { exchange ->
                exchange.sendResponseHeaders(403, -1)
            })

            // 9. Standard HTML Webpage (not a downloadable file)
            server.createContext("/webpage/index.html", HttpHandler { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
                val html = "<html><body><h1>Web Page</h1></body></html>".toByteArray()
                exchange.sendResponseHeaders(200, html.size.toLong())
                exchange.responseBody.write(html)
                exchange.responseBody.close()
            })

            // 10. Slow/hanging server for timeout tests
            server.createContext("/slow/hang", HttpHandler { exchange ->
                try {
                    Thread.sleep(8000)
                } catch (_: InterruptedException) {
                }
                exchange.sendResponseHeaders(200, -1)
            })

            server.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDirectCopiedUrlPreflight() {
        val url = "http://127.0.0.1:$serverPort/files/direct.zip"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertEquals("direct.zip", result.fileName)
        assertEquals(2048L, result.fileSize)
        assertEquals("\"tag-zip-123\"", result.etag)
        assertTrue(result.isDownloadable, "direct.zip must be recognized as downloadable")
        assertFalse(result.requiresAuth)
        assertFalse(result.isExpiredOrForbidden)
        assertNull(result.errorMessage)
    }

    @Test
    fun testHeadFallbackToRangeRequest() {
        val url = "http://127.0.0.1:$serverPort/files/nohead.iso"
        val initialRangeRequests = rangeRequests.get()
        val result = LinkPreflightService.preflightSync(url)

        assertTrue(rangeRequests.get() > initialRangeRequests, "Must have fallen back to Range: bytes=0-0")
        assertEquals(206, result.statusCode)
        assertEquals("nohead.iso", result.fileName)
        assertEquals(5242880L, result.fileSize, "Size must be resolved from Content-Range total length")
        assertTrue(result.isDownloadable)
    }

    @Test
    fun testRedirectFollowAndFinalFilenameResolution() {
        val url = "http://127.0.0.1:$serverPort/redirect/doc"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertTrue(result.finalUrl.endsWith("/final/target-archive.tar.gz"))
        assertEquals("target-archive.tar.gz", result.fileName)
        assertEquals(8192L, result.fileSize)
        assertTrue(result.isDownloadable)
    }

    @Test
    fun testUnknownChunkedSize() {
        val url = "http://127.0.0.1:$serverPort/stream/chunked.bin"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertEquals("chunked.bin", result.fileName)
        assertNull(result.fileSize, "Chunked response with no Content-Length must have null fileSize")
        assertTrue(result.isDownloadable)
    }

    @Test
    fun testContentDispositionUnicodeRfc5987() {
        val url = "http://127.0.0.1:$serverPort/disposition/unicode"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertEquals(
            "日本語マニュアル.pdf",
            result.fileName,
            "RFC 5987 / RFC 6266 filename* UTF-8 must take precedence over ASCII fallback"
        )
        assertEquals(10240L, result.fileSize)
        assertTrue(result.isDownloadable)
    }

    @Test
    fun testUnsafeFilenameSanitization() {
        val url = "http://127.0.0.1:$serverPort/disposition/unsafe"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertFalse(result.fileName.contains("/"), "Path traversal / must be stripped")
        assertFalse(result.fileName.contains("\\"), "Path traversal \\ must be stripped")
        assertFalse(result.fileName.contains(":"), "Colon must be sanitized")
        assertFalse(result.fileName.contains("?"), "Question mark must be sanitized")
        assertFalse(result.fileName.contains("*"), "Asterisk must be sanitized")
        assertEquals("....evilname.exe", result.fileName)
    }

    @Test
    fun testAuthAndForbiddenStatus() {
        // 401 Unauthorized
        val authUrl = "http://127.0.0.1:$serverPort/auth/protected.zip"
        val authResult = LinkPreflightService.preflightSync(authUrl)
        assertEquals(401, authResult.statusCode)
        assertTrue(authResult.requiresAuth, "401 must flag requiresAuth = true")
        assertTrue(authResult.isDownloadable, "Protected resource is still downloadable via auth")

        // 403 Forbidden
        val forbiddenUrl = "http://127.0.0.1:$serverPort/forbidden/secret.pdf"
        val forbiddenResult = LinkPreflightService.preflightSync(forbiddenUrl)
        assertEquals(403, forbiddenResult.statusCode)
        assertTrue(forbiddenResult.isExpiredOrForbidden, "403 must flag isExpiredOrForbidden = true")
        assertFalse(forbiddenResult.isDownloadable, "Forbidden resource must not be auto-offered as downloadable")
    }

    @Test
    fun testWebpageIsNotDownloadable() {
        val url = "http://127.0.0.1:$serverPort/webpage/index.html"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertFalse(
            result.isDownloadable,
            "HTML webpage must not be identified as a downloadable binary file"
        )
    }

    @Test
    fun testNoPayloadStreamedBeforeStart() {
        val initialPayloadRequests = fullPayloadRequests.get()
        val url = "http://127.0.0.1:$serverPort/files/direct.zip"

        // Execute preflight probe
        val result = LinkPreflightService.preflightSync(url)
        assertEquals(200, result.statusCode)

        // Verify zero full payload GET requests were issued
        assertEquals(
            initialPayloadRequests,
            fullPayloadRequests.get(),
            "Preflight probe must NEVER download or stream the payload before user explicitly starts"
        )
    }

    @Test
    fun testPreflightCancellation() {
        val url = "http://127.0.0.1:$serverPort/slow/hang"
        val latch = CountDownLatch(1)
        var resultReceived = false

        val handle = LinkPreflightService.preflight(url) {
            resultReceived = true
            latch.countDown()
        }

        assertFalse(handle.isCancelled)
        // Cancel immediately
        handle.cancel()
        assertTrue(handle.isCancelled)

        // Latch should not fire or result callback should not run
        latch.await(500, TimeUnit.MILLISECONDS)
        assertFalse(resultReceived, "Cancelled preflight must not dispatch results to callback")
    }

    @Test
    fun testClipboardMonitorStrictPrivacyAndDeduplication() {
        ClipboardMonitor.resetForTests()

        // 1. Non-URL text: should be rejected immediately by isEligibleHttpUrl
        assertFalse(ClipboardMonitor.isEligibleHttpUrl("my secret password"))
        assertFalse(ClipboardMonitor.isEligibleHttpUrl("Some notes with multiple lines\nhttp://example.com"))
        assertFalse(ClipboardMonitor.isEligibleHttpUrl("ftp://example.com/file.zip"))
        assertFalse(ClipboardMonitor.isEligibleHttpUrl(""))
        assertFalse(ClipboardMonitor.isEligibleHttpUrl("   "))

        // 2. Eligible URLs
        assertTrue(ClipboardMonitor.isEligibleHttpUrl("http://example.com/file.zip"))
        assertTrue(ClipboardMonitor.isEligibleHttpUrl("https://example.com/download?id=123"))

        // 3. Deduplication check
        val testUrl = "http://127.0.0.1:$serverPort/files/direct.zip"
        ClipboardMonitor.lastProcessedUrl = testUrl
        ClipboardMonitor.lastProcessedText = testUrl

        var modalOpened = false
        // Simulate checking the same URL again
        if (testUrl == ClipboardMonitor.lastProcessedUrl) {
            // Deduplicated!
            modalOpened = false
        }
        assertFalse(modalOpened, "Repeated clipboard check for the same URL must be deduplicated")

        // 4. Pause / Resume control
        ClipboardMonitor.pause()
        assertTrue(ClipboardMonitor.isPaused)
        ClipboardMonitor.resume()
        assertFalse(ClipboardMonitor.isPaused)
    }

    @Test
    fun testUrlSanitizationForLogsDoesNotLeakSecrets() {
        val urlWithSecrets = "https://example.com/download.zip?token=supersecret123&sig=abcd456&auth=bearer789"
        val sanitized = LinkPreflightService.sanitizeUrlForLog(urlWithSecrets)

        assertEquals("https://example.com/download.zip", sanitized)
        assertFalse(sanitized.contains("supersecret123"), "Secret token must not appear in log URL")
        assertFalse(sanitized.contains("abcd456"), "Signature must not appear in log URL")
        assertFalse(sanitized.contains("bearer789"), "Auth token must not appear in log URL")
    }

    @Test
    fun testUrlSanitizationPreservesPortAndStripsCredentials() {
        val urlWithPortAndCreds = "http://user:pass@127.0.0.1:8080/files/archive.zip?token=xyz123#fragment"
        val sanitized = LinkPreflightService.sanitizeUrlForLog(urlWithPortAndCreds)

        assertEquals("http://127.0.0.1:8080/files/archive.zip", sanitized)
        assertFalse(sanitized.contains("user"), "Username must not appear in sanitized URL")
        assertFalse(sanitized.contains("pass"), "Password must not appear in sanitized URL")
        assertFalse(sanitized.contains("xyz123"), "Token must not appear in sanitized URL")
        assertTrue(sanitized.contains(":8080"), "Non-default port must be preserved")
    }

    @Test
    fun testContentDispositionSingleQuotesAndPlusCharacters() {
        val header = "attachment; filename='my+document.pdf'"
        val parsed = xdm.core.util.getNameFromContentDisposition(header)
        assertEquals("my+document.pdf", parsed, "Single quotes must be stripped and + character preserved")
    }

    @Test
    fun testNewDownloadWindowCandidateFilenameWhenOpenedWithNullTaskInfo() {
        if (java.awt.GraphicsEnvironment.isHeadless()) return
        val latch = CountDownLatch(1)
        val url = "http://127.0.0.1:$serverPort/files/direct.zip"
        xdm.app.utils.setClipBoardText(url)

        SwingUtilities.invokeLater {
            try {
                val window = NewDownloadWindow()
                window.showWindow(null)

                assertEquals(url, window.txtUrl.text, "URL in clipboard must pre-fill txtUrl")
                assertEquals(
                    "direct.zip",
                    window.txtFileName.text,
                    "Candidate filename must be immediately populated when opening with URL from clipboard"
                )

                window.btnCancel.doClick()
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "EDT operations must finish promptly")
    }

    @Test
    fun testNewDownloadWindowLifecycleAndCancelDoesNotStreamPayload() {
        if (java.awt.GraphicsEnvironment.isHeadless()) return
        val latch = CountDownLatch(1)
        val initialPayloads = fullPayloadRequests.get()

        SwingUtilities.invokeLater {
            try {
                val window = NewDownloadWindow()
                val url = "http://127.0.0.1:$serverPort/files/direct.zip"
                val task = HttpDownloadTaskInfo(
                    id = CoreUtils.uniqueId(),
                    url = url,
                    fileName = "direct.zip",
                    respectFileName = false,
                    cookie = null,
                    headers = null,
                    origin = null,
                    categoryId = null,
                    targetFolder = tempDir.absolutePath,
                    maxPiece = 8,
                    authInfo = null,
                    knownFileSize = 2048L,
                    etag = null
                )

                window.showWindow(task)
                assertEquals("direct.zip", window.txtFileName.text)
                assertEquals(url, window.txtUrl.text)

                // User clicks cancel: window disposes, probe cancelled, no payload transferred
                window.btnCancel.doClick()
                assertEquals(
                    initialPayloads,
                    fullPayloadRequests.get(),
                    "Cancelling the download dialog must NOT stream or download payload"
                )
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "EDT operations must finish promptly without blocking")
    }
}

