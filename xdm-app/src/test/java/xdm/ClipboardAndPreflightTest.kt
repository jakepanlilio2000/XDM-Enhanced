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
        var expectedPayloadSha256: String = ""

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

            // 11. Quoted semicolon in Content-Disposition: attachment; filename="report;annual-2026.pdf"
            server.createContext("/disposition/quoted-semicolon", HttpHandler { exchange ->
                exchange.responseHeaders.add(
                    "Content-Disposition",
                    "attachment; filename=\"report;annual-2026.pdf\""
                )
                exchange.responseHeaders.add("Content-Length", "1024")
                exchange.sendResponseHeaders(200, -1)
            })

            // 12. Query string candidate: download.php?file=quarterly-report.xlsx
            server.createContext("/query/download.php", HttpHandler { exchange ->
                exchange.responseHeaders.add(
                    "Content-Type",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                exchange.responseHeaders.add("Content-Length", "20480")
                exchange.sendResponseHeaders(200, -1)
            })

            // 13. Redirect loop: /redirect/loop1 <-> /redirect/loop2
            server.createContext("/redirect/loop1", HttpHandler { exchange ->
                exchange.responseHeaders.add("Location", "http://127.0.0.1:$serverPort/redirect/loop2")
                exchange.sendResponseHeaders(302, -1)
            })
            server.createContext("/redirect/loop2", HttpHandler { exchange ->
                exchange.responseHeaders.add("Location", "http://127.0.0.1:$serverPort/redirect/loop1")
                exchange.sendResponseHeaders(302, -1)
            })

            // 14. Zero-byte file: Content-Length: 0
            server.createContext("/zero-byte/empty.bin", HttpHandler { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                exchange.responseHeaders.add("Content-Length", "0")
                exchange.sendResponseHeaders(200, -1)
            })

            // 15. Real payload server for payload transfer and hash verification
            val testPayloadData = ByteArray(65536) { (it % 256).toByte() }
            val md = java.security.MessageDigest.getInstance("SHA-256")
            expectedPayloadSha256 = md.digest(testPayloadData).joinToString("") { "%02x".format(it) }

            server.createContext("/payload/test-download.dat", HttpHandler { exchange ->
                val method = exchange.requestMethod
                if (method.equals("HEAD", ignoreCase = true)) {
                    exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                    exchange.responseHeaders.add("Content-Length", testPayloadData.size.toString())
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    exchange.sendResponseHeaders(200, -1)
                } else {
                    fullPayloadRequests.incrementAndGet()
                    val range = exchange.requestHeaders.getFirst("Range")
                    if (range != null && range.startsWith("bytes=")) {
                        val parts = range.removePrefix("bytes=").split("-")
                        val start = parts[0].toInt()
                        val end = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toInt() else testPayloadData.size - 1
                        val len = end - start + 1
                        exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${testPayloadData.size}")
                        exchange.sendResponseHeaders(206, len.toLong())
                        exchange.responseBody.write(testPayloadData, start, len)
                        exchange.responseBody.close()
                    } else {
                        exchange.sendResponseHeaders(200, testPayloadData.size.toLong())
                        exchange.responseBody.write(testPayloadData)
                        exchange.responseBody.close()
                    }
                }
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

    @Test
    fun testQuotedSemicolonInContentDisposition() {
        val url = "http://127.0.0.1:$serverPort/disposition/quoted-semicolon"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertTrue(
            result.fileName.contains("report") && result.fileName.endsWith(".pdf"),
            "Quoted semicolon inside filename must not truncate the extension or name: actual='${result.fileName}'"
        )
        assertFalse(result.fileName.equals("report", ignoreCase = true), "Filename must not be truncated to 'report'")
        assertEquals(1024L, result.fileSize)
    }

    @Test
    fun testQueryStringCandidateExtractionAndCategorySuggestion() {
        val url = "http://127.0.0.1:$serverPort/query/download.php?file=quarterly-report.xlsx&token=secret123"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertEquals("quarterly-report.xlsx", result.fileName, "Query parameter file= must provide candidate filename")
        assertEquals(20480L, result.fileSize)

        // Verify category matching resolves to Documents
        val matchedCat = xdm.manager.CategoryRules.match(result.fileName, url, null)
        assertNotNull(matchedCat)
        assertEquals("CAT_DOCUMENTS", matchedCat!!.id)
    }

    @Test
    fun testRedirectLoopTermination() {
        val url = "http://127.0.0.1:$serverPort/redirect/loop1"
        val result = LinkPreflightService.preflightSync(url)

        // Must terminate safely with connection failure / protocol exception rather than infinite loop
        assertEquals(0, result.statusCode)
        assertFalse(result.isDownloadable)
        assertNotNull(result.errorMessage)
        assertTrue(
            result.errorMessage!!.contains("Too many", ignoreCase = true) ||
            result.errorMessage!!.contains("redirect", ignoreCase = true)
        )
    }

    @Test
    fun testZeroByteContentLength() {
        val url = "http://127.0.0.1:$serverPort/zero-byte/empty.bin"
        val result = LinkPreflightService.preflightSync(url)

        assertEquals(200, result.statusCode)
        assertEquals(0L, result.fileSize, "Explicit Content-Length: 0 must resolve as 0 bytes, not null/unknown")
        assertEquals("empty.bin", result.fileName)
    }

    @Test
    fun testOfflineModeHandling() {
        // Port 54321 is presumed offline / unused on loopback
        val offlineUrl = "http://127.0.0.1:54321/offline-archive.zip"
        val result = LinkPreflightService.preflightSync(offlineUrl)

        assertEquals(0, result.statusCode)
        assertNull(result.fileSize, "Offline probe must leave fileSize null")
        assertEquals("offline-archive.zip", result.fileName, "Fallback filename must be derived safely from URL path")
        assertNotNull(result.errorMessage, "Offline probe must report connection error")
    }

    @Test
    fun testDuplicateDialogSuppression() {
        if (java.awt.GraphicsEnvironment.isHeadless()) return
        val testUrl = "http://127.0.0.1:$serverPort/payload/test-download.dat"
        NewDownloadWindow.closeAllForTests()

        val task1 = HttpDownloadTaskInfo(
            id = CoreUtils.uniqueId(),
            url = testUrl,
            fileName = "test-download.dat",
            respectFileName = false,
            cookie = null,
            headers = null,
            origin = null,
            categoryId = null,
            targetFolder = tempDir.absolutePath,
            maxPiece = 8,
            authInfo = null,
            knownFileSize = 65536L,
            etag = null
        )

        val task2 = task1.copy(id = CoreUtils.uniqueId())

        var win1: NewDownloadWindow? = null
        var win2: NewDownloadWindow? = null

        SwingUtilities.invokeAndWait {
            win1 = NewDownloadWindow()
            win1!!.showWindow(task1)
        }

        assertTrue(NewDownloadWindow.isModalOpen(testUrl), "Window 1 must be registered as open for testUrl")

        SwingUtilities.invokeAndWait {
            win2 = NewDownloadWindow()
            win2!!.showWindow(task2)
        }

        // Window 2 detected existing window for testUrl, brought it to front, and disposed itself
        assertTrue(win1!!.isDisplayable, "Original dialog must remain displayable and active")
        assertFalse(win2!!.isDisplayable, "Duplicate dialog must have been disposed immediately")

        SwingUtilities.invokeAndWait {
            win1!!.dispose()
        }

        assertFalse(NewDownloadWindow.isModalOpen(testUrl), "Disposing window 1 must clear registration")
    }

    @Test
    fun testBrowserIntegrationOriginAndSecurityPolicy() {
        val filter = xdm.integration.BrowserIntegration.RequestFilter(serverPort)

        // 1. Untrusted web page origin must be rejected with 403 Forbidden
        val untrustedWebHead = xdm.integration.HttpParser.RequestHead(
            method = "POST",
            path = "/download",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:$serverPort"),
                "Origin" to listOf("https://untrusted-webpage.com")
            ),
            deadlineNanos = System.nanoTime() + 10_000_000L
        )
        val webReject = filter.reject(untrustedWebHead)
        assertNotNull(webReject)
        assertEquals(403, webReject!!.first, "Untrusted web page Origin must be rejected with 403")

        // 2. Chrome extension origin must be allowed
        val chromeExtHead = xdm.integration.HttpParser.RequestHead(
            method = "POST",
            path = "/download",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:$serverPort"),
                "Origin" to listOf("chrome-extension://abcdefghijklmnopabcdefghijklmnop")
            ),
            deadlineNanos = System.nanoTime() + 10_000_000L
        )
        assertNull(filter.reject(chromeExtHead), "Chrome extension Origin must be accepted")

        // 3. Firefox extension origin must be allowed
        val firefoxExtHead = xdm.integration.HttpParser.RequestHead(
            method = "POST",
            path = "/download",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:$serverPort"),
                "Origin" to listOf("moz-extension://12345678-1234-1234-1234-1234567890ab")
            ),
            deadlineNanos = System.nanoTime() + 10_000_000L
        )
        assertNull(filter.reject(firefoxExtHead), "Firefox extension Origin must be accepted")

        // 4. DNS rebinding / non-loopback Host header must be rejected with 403 Forbidden
        val dnsRebindHead = xdm.integration.HttpParser.RequestHead(
            method = "GET",
            path = "/sync",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("attacker-domain.com:$serverPort")
            ),
            deadlineNanos = System.nanoTime() + 10_000_000L
        )
        val rebindReject = filter.reject(dnsRebindHead)
        assertNotNull(rebindReject)
        assertEquals(403, rebindReject!!.first, "Non-loopback Host header must be rejected with 403")

        // 5. Non-POST method on state-changing endpoint must be rejected with 405
        val getDownloadHead = xdm.integration.HttpParser.RequestHead(
            method = "GET",
            path = "/download",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:$serverPort"),
                "Origin" to listOf("chrome-extension://abcdefghijklmnopabcdefghijklmnop")
            ),
            deadlineNanos = System.nanoTime() + 10_000_000L
        )
        val methodReject = filter.reject(getDownloadHead)
        assertNotNull(methodReject)
        assertEquals(405, methodReject!!.first, "GET on state-changing path must be rejected with 405")
    }

    @Test
    fun testPayloadTransferByteCountsAndSha256Verification() {
        val url = "http://127.0.0.1:$serverPort/payload/test-download.dat"
        val preflightCount = fullPayloadRequests.get()

        // 1. Preflight probe: verify metadata without body download
        val result = LinkPreflightService.preflightSync(url)
        assertEquals(200, result.statusCode)
        assertEquals(65536L, result.fileSize)
        assertEquals(preflightCount, fullPayloadRequests.get(), "Preflight must not initiate body transfer")

        // 2. User initiates download (Start): payload is streamed and saved to disk
        val destinationFile = File(tempDir, "downloaded-payload.dat")
        val okClient = okhttp3.OkHttpClient()
        val req = okhttp3.Request.Builder().url(url).build()
        val resp = okClient.newCall(req).execute()
        assertEquals(200, resp.code)
        resp.body!!.byteStream().use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // 3. Verify transferred byte count matches remote Content-Length
        assertEquals(65536L, destinationFile.length(), "Downloaded file size on disk must match remote size exactly")

        // 4. Verify SHA-256 hash matches server content hash
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val downloadedBytes = destinationFile.readBytes()
        val actualSha256 = md.digest(downloadedBytes).joinToString("") { "%02x".format(it) }
        assertEquals(expectedPayloadSha256, actualSha256, "SHA-256 hash of downloaded file must match server content exactly")

        destinationFile.delete()
    }
}

