package xdm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.IAppInstance
import xdm.capture.CapturedVideoTracker
import xdm.capture.ExtensionMessage
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.integration.BrowserIntegration
import xdm.torrent.TorrentMetadataParser
import java.io.File
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LaunchArgsAndIntegrationTest {

    private lateinit var dir: File
    private val handedArgs = CopyOnWriteArrayList<List<String>>()
    private val incomingUris = CopyOnWriteArrayList<String>()
    private val httpDownloads = CopyOnWriteArrayList<HttpDownloadTaskInfo>()
    private val shownCount = AtomicInteger(0)
    private val strays = mutableListOf<ServerSocket>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @BeforeEach
    fun setup() {
        dir = Files.createTempDirectory("xdm-launch-args-test").toFile()
        AppContext.configDir = dir.absolutePath
        AppContext.config = AppConfig(dir.absolutePath)
        AppContext.videoTracker = CapturedVideoTracker()

        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, method, args ->
            when (method.name) {
                "showAppWindow" -> shownCount.incrementAndGet()
                "handleLaunchArgs" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args?.get(0) as? List<String>)?.let { handedArgs.add(it) }
                }
                "handleIncomingUriOrFile" -> {
                    (args?.get(0) as? String)?.let { incomingUris.add(it) }
                }
                "addDownload" -> {
                    (args?.get(0) as? HttpDownloadTaskInfo)?.let { httpDownloads.add(it) }
                }
            }
            null
        } as IAppInstance
    }

    @AfterEach
    fun tearDown() {
        releaseAcquiredPort()
        strays.forEach { runCatching { it.close() } }
        dir.deleteRecursively()
    }

    private fun releaseAcquiredPort() {
        val field = BrowserIntegration::class.java.getDeclaredField("serverSocket").apply { isAccessible = true }
        (field.get(BrowserIntegration) as ServerSocket?)?.let { runCatching { it.close() } }
        field.set(BrowserIntegration, null)
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun secondLaunchHandsCommandLineArgsToPrimaryInstance() {
        val port = freePort()
        assertEquals(BrowserIntegration.Acquired.Primary, BrowserIntegration.acquire(emptyArray(), port))
        BrowserIntegration.serve()

        val magnet = "magnet:?xt=urn:btih:da39a3ee5e6b4b0d3255bfef95601890afd80709&dn=test"
        val torrentPath = File(dir, "ubuntu.torrent").absolutePath
        val outcome = BrowserIntegration.acquire(arrayOf(magnet, torrentPath), port)

        assertEquals(BrowserIntegration.Acquired.AnotherInstance, outcome)
        assertEquals(1, shownCount.get(), "Primary instance should have been asked to show window")
        assertEquals(1, handedArgs.size, "Primary instance should have received handed arguments")
        assertEquals(listOf(magnet, torrentPath), handedArgs[0])
    }

    @Test
    fun magnetUriParserValidatesAndExtractsHash() {
        val validHex = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Ubuntu"
        val hash = TorrentMetadataParser.parseMagnetInfoHash(validHex)
        assertEquals("0123456789abcdef0123456789abcdef01234567", hash)

        // Invalid magnet with no xt param
        val invalidNoXt = "magnet:?dn=MissingXt"
        assertNull(TorrentMetadataParser.parseMagnetInfoHash(invalidNoXt))

        // Non-magnet string
        val notMagnet = "https://example.com/file.iso"
        assertNull(TorrentMetadataParser.parseMagnetInfoHash(notMagnet))

        // V2 SHA-256 Info Hash
        val v2Hash = "111122223333444455556666777788889999aaaabbbbccccddddeeeeffff0000"
        val v2Magnet = "magnet:?xt=urn:btmh:1220$v2Hash&dn=V2Torrent"
        val extractedV2 = TorrentMetadataParser.parseMagnetInfoHash(v2Magnet)
        assertEquals(v2Hash, extractedV2)
    }

    @Test
    fun handleLaunchArgsFiltersFlagsAndDispatchesTargets() {
        val app = xdm.app.AppInstance()
        val dispatched = mutableListOf<String>()

        // Test with subclass or mock
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=LaunchArgs"
        val filePath = "C:\\Downloads\\test.torrent"
        val args = listOf("--minimized", "-m", "  ", "\"$magnet\"", filePath, "--background")

        val appProxy = object : xdm.app.AppInstance() {
            override fun handleIncomingUriOrFile(raw: String) {
                dispatched.add(raw)
            }
        }
        appProxy.handleLaunchArgs(args)

        assertEquals(2, dispatched.size, "Flags and empty strings should be ignored")
        assertEquals(magnet, dispatched[0])
        assertEquals(filePath, dispatched[1])
    }

    @Test
    fun fileUriHandlingSupportsStandardAndPercentEncodedPaths() {
        val folder = File(dir, "Folder With Spaces")
        folder.mkdirs()
        val torrent = File(folder, "sample file.torrent")
        torrent.writeBytes("d4:infod6:lengthi512e4:name8:test.bin12:piece lengthi16384e6:pieces20:12345678901234567890ee".toByteArray())

        val fileUri = torrent.toURI().toString()
        assertTrue(fileUri.contains("%20"), "URI should contain percent-encoded spaces")

        // Candidate file resolution test
        val candidate = if (fileUri.startsWith("file:", ignoreCase = true)) {
            runCatching { File(java.net.URI(fileUri)) }.getOrElse {
                File(fileUri.removePrefix("file://").removePrefix("file:"))
            }
        } else File(fileUri)

        assertTrue(candidate.exists(), "candidateFile should exist on disk")
        assertEquals(torrent.canonicalPath, candidate.canonicalPath)
    }

    @Test
    fun browserDownloadMessageWithMagnetIsHandedToMagnetHandler() {
        val port = freePort()
        assertEquals(BrowserIntegration.Acquired.Primary, BrowserIntegration.acquire(emptyArray(), port))
        BrowserIntegration.serve()

        val magnet = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111&dn=BrowserMagnet"
        val msg = ExtensionMessage(
            url = magnet,
            tabUrl = "https://torrents.example/page",
            tabId = "10"
        )
        val body = json.encodeToString(msg).toByteArray(StandardCharsets.UTF_8)

        val conn = (URL("http://127.0.0.1:$port/download").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Host", "127.0.0.1:$port")
            setRequestProperty("Origin", "chrome-extension://xdm-test-extension")
            outputStream.use { it.write(body) }
        }

        assertEquals(200, conn.responseCode)
        conn.inputStream.use { it.readBytes() }

        assertEquals(1, incomingUris.size, "Browser magnet link should be handed to incoming URI handler")
        assertEquals(magnet, incomingUris[0])
        assertEquals(0, httpDownloads.size, "Magnet link should NOT be handled as plain HTTP download")
    }

    @Test
    fun browserDownloadMessageWithHttpRemainsHttpDownload() {
        val port = freePort()
        assertEquals(BrowserIntegration.Acquired.Primary, BrowserIntegration.acquire(emptyArray(), port))
        BrowserIntegration.serve()

        val httpUrl = "https://example.com/archive.zip"
        val msg = ExtensionMessage(
            url = httpUrl,
            filename = "archive.zip",
            tabUrl = "https://example.com",
            tabId = "12"
        )
        val body = json.encodeToString(msg).toByteArray(StandardCharsets.UTF_8)

        val conn = (URL("http://127.0.0.1:$port/download").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Host", "127.0.0.1:$port")
            setRequestProperty("Origin", "chrome-extension://xdm-test-extension")
            outputStream.use { it.write(body) }
        }

        assertEquals(200, conn.responseCode)
        conn.inputStream.use { it.readBytes() }

        assertEquals(0, incomingUris.size, "HTTP download should not go to magnet/incomingUri handler")
        assertEquals(1, httpDownloads.size, "HTTP download must be processed by addDownload")
        assertEquals(httpUrl, httpDownloads[0].url)
    }

    @Test
    fun torrentFileParserParsesValidTorrentBytes() {
        // Valid bencoded torrent dictionary:
        // d 4:info d 6:length i1024e 4:name 8:test.bin 12:piece length i16384e 6:pieces 20:12345678901234567890 e e
        val bencoded = "d4:infod6:lengthi1024e4:name8:test.bin12:piece lengthi16384e6:pieces20:12345678901234567890ee"
            .toByteArray(StandardCharsets.ISO_8859_1)

        val file = File(dir, "sample.torrent")
        file.writeBytes(bencoded)

        val meta = TorrentMetadataParser.parse(file)
        assertEquals("test.bin", meta.name)
        assertEquals(1024L, meta.totalSize)
        assertTrue(meta.infoHash.isNotBlank())
        assertEquals(1, meta.files.size)
        assertEquals("test.bin", meta.files[0].path)
        assertEquals(1024L, meta.files[0].size)
    }
}

