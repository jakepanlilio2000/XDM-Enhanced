package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.plugins.PluginManager
import xdm.app.plugins.PluginManifestValidator
import xdm.app.rules.PostDownloadActionEngine
import xdm.app.rules.PostDownloadRule
import xdm.app.skins.SkinPackager
import xdm.app.update.UpdateCheckResult
import xdm.app.update.UpdateChecker
import xdm.app.utils.OriginMarker
import xdm.core.network.http.SensitiveHeaders
import xdm.core.util.FileUtils
import xdm.core.util.Logger
import xdm.integration.BrowserIntegration
import xdm.integration.HttpParser
import xdm.torrent.TorrentMetadataParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Phase12SecurityAndLicensingAuditTest {

    @TempDir
    lateinit var tempDir: File

    private val repoRoot: File = run {
        var cur = File(".").canonicalFile
        while (cur != null && !File(cur, "packaging").isDirectory) {
            cur = cur.parentFile
        }
        cur ?: File(".").canonicalFile
    }

    @BeforeEach
    fun setUp() {
        xdm.app.I8N.loadTexts("en")
        AppContext.config = AppConfig(tempDir.absolutePath)
    }

    // =========================================================================
    // Check 78: Path Traversal, Zip Slip, Metadata & Command Injection Defenses
    // =========================================================================

    @Test
    fun testCheck78_zipSlipPrefixCollisionPrevention() {
        val targetDir = File(tempDir, "plugin-target").apply { mkdirs() }
        val siblingDir = File(tempDir, "plugin-target-evil").apply { mkdirs() }

        // Create hostile archive with Zip Slip prefix collision entry
        val maliciousZip = File(tempDir, "hostile.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../plugin-target-evil/pwned.txt"))
            zos.write("malicious payload".toByteArray())
            zos.closeEntry()
        }

        // PluginManifestValidator must catch this entry upfront
        val validation = PluginManifestValidator.validateArchive(maliciousZip)
        assertFalse(validation.isValid, "Validator must reject relative directory traversal")
        assertTrue(validation.errors.any { it.contains("Hostile archive entry path detected") })

        // PluginManager direct extraction must reject using canonical Path comparison
        val installResult = PluginManager.installPlugin(maliciousZip)
        assertFalse(installResult.isValid, "PluginManager must not extract hostile Zip Slip entry")
        assertFalse(File(siblingDir, "pwned.txt").exists(), "Target outside staging directory must never be written")
    }

    @Test
    fun testCheck78_torrentMetadataPathSanitization() {
        // Path traversal attempts in torrent metadata
        val sanitized1 = TorrentMetadataParser.sanitizeTorrentFilePath("../../Windows/System32/evil.dll", 0)
        assertFalse(sanitized1.contains(".."), "Must eliminate directory traversal dots")
        assertFalse(sanitized1.startsWith("/"), "Must eliminate root prefixes")

        val sanitized2 = TorrentMetadataParser.sanitizeTorrentFilePath("folder/sub/../../../etc/shadow", 1)
        assertFalse(sanitized2.contains(".."))

        // Windows reserved device names in torrent relative paths
        val sanitizedCon = TorrentMetadataParser.sanitizeTorrentFilePath("con.txt", 2)
        assertTrue(sanitizedCon.startsWith("_"), "con.txt must be prefixed as _con.txt")

        val sanitizedAux = TorrentMetadataParser.sanitizeTorrentFilePath("folder/aux/data.bin", 3)
        assertTrue(sanitizedAux.contains("_aux"), "aux path segment must be sanitized to _aux")
    }

    @Test
    fun testCheck78_filenameSanitizationDeviceNamesAndControlChars() {
        // Reserved Windows device names
        assertEquals("_CON.txt", FileUtils.sanitizeFileName("CON.txt"))
        assertEquals("_prn.pdf", FileUtils.sanitizeFileName("prn.pdf"))
        assertEquals("_AUX", FileUtils.sanitizeFileName("AUX"))
        assertEquals("_NUL.zip", FileUtils.sanitizeFileName("NUL.zip"))
        assertEquals("_com1.exe", FileUtils.sanitizeFileName("com1.exe"))

        // Control characters and illegal filesystem characters
        assertEquals("report_final_.pdf", FileUtils.sanitizeFileName("report\u0000final\n.pdf"))
        assertEquals("my_bad_name_.mp4", FileUtils.sanitizeFileName("my:bad*name?.mp4"))

        // Trailing dots and spaces (Windows silent truncation vulnerability)
        assertEquals("setup.exe", FileUtils.sanitizeFileName("setup.exe.  "))
    }

    @Test
    fun testCheck78_safeCommandExecutionNoShellInterpolation() {
        val sample = File(tempDir, "sample.mkv").apply { writeText("dummy content") }

        // Command with shell characters must be tokenized as discrete arguments, not shell interpolated
        val template = "vlc --play-and-exit; rm -rf / & echo evil [file]"
        val argv = PostDownloadActionEngine.buildArguments(template, sample)

        // Verifying that arguments are discrete tokens without invoking cmd.exe / sh
        assertEquals("vlc", argv[0])
        assertEquals("--play-and-exit;", argv[1])
        assertEquals("rm", argv[2])
        assertEquals("-rf", argv[3])
        assertEquals("/", argv[4])
        assertEquals("&", argv[5])
        assertEquals("echo", argv[6])
        assertEquals("evil", argv[7])
        assertEquals(sample.path, argv[8])

        // Verify that running downloaded executable directly is strictly prohibited
        val downloadedExe = File(tempDir, "malware.exe").apply { writeText("binary") }
        assertThrows(SecurityException::class.java) {
            PostDownloadActionEngine.validateCommand(listOf(downloadedExe.path), downloadedExe)
        }
    }

    // =========================================================================
    // Check 79: Least Privilege, Explicit Consent & Trust Boundaries
    // =========================================================================

    @Test
    fun testCheck79_loopbackLeastPrivilegeAndDnsRebindingProtection() {
        val filter = BrowserIntegration.RequestFilter(8597)

        // Helper for request head creation
        fun createHead(method: String, path: String, headers: Map<String, List<String>>) = HttpParser.RequestHead(
            method = method,
            path = path,
            version = "HTTP/1.1",
            headers = headers,
            deadlineNanos = System.nanoTime() + 1_000_000_000L
        )

        // 1. Valid loopback host
        val validHead = createHead(
            method = "GET",
            path = "/sync",
            headers = mapOf("Host" to listOf("127.0.0.1:8597"))
        )
        assertNull(filter.reject(validHead), "GET /sync from loopback Host must be allowed")

        // 2. DNS rebinding attempt
        val rebindingHead = createHead(
            method = "GET",
            path = "/sync",
            headers = mapOf("Host" to listOf("attacker-domain.org:8597"))
        )
        val rebindingReject = filter.reject(rebindingHead)
        assertNotNull(rebindingReject)
        assertEquals(403, rebindingReject?.first, "DNS rebinding Host header must return 403 Forbidden")

        // 3. Web page origin access attempt (CSRF prevention)
        val webOriginHead = createHead(
            method = "POST",
            path = "/download",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:8597"),
                "Origin" to listOf("https://evil-site.com")
            )
        )
        val originReject = filter.reject(webOriginHead)
        assertNotNull(originReject)
        assertEquals(403, originReject?.first, "Non-extension web Origin must return 403 Forbidden")

        // 4. Valid extension origin allowed
        val extensionHead = createHead(
            method = "POST",
            path = "/download",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:8597"),
                "Origin" to listOf("chrome-extension://abcdefghijklmnop")
            )
        )
        assertNull(filter.reject(extensionHead), "Browser extension origin must be permitted")

        // 5. State-changing path with GET method rejected (CSRF <img> tag defense)
        val getDownloadHead = createHead(
            method = "GET",
            path = "/download",
            headers = mapOf("Host" to listOf("127.0.0.1:8597"))
        )
        val getReject = filter.reject(getDownloadHead)
        assertNotNull(getReject)
        assertEquals(405, getReject?.first, "State-changing routes must reject GET with 405 Method Not Allowed")
    }

    // =========================================================================
    // Check 80: Sensitive Credentials, Tokens & Privacy Protection
    // =========================================================================

    @Test
    fun testCheck80_loggerCredentialsAndTokenScrubbing() {
        // Embedded URL password scrubbing
        val rawUrlLog = "Connecting to http://alice:SuperSecretPassword123@myproxy.internal:8080/data.iso"
        val sanitizedUrlLog = Logger.sanitizeLog(rawUrlLog)
        assertFalse(sanitizedUrlLog.contains("SuperSecretPassword123"), "URL password must never be logged")
        assertTrue(sanitizedUrlLog.contains("http://alice:***@myproxy.internal:8080/data.iso"))

        // Authorization header scrubbing
        val rawAuthLog = "Request headers: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secret"
        val sanitizedAuthLog = Logger.sanitizeLog(rawAuthLog)
        assertFalse(sanitizedAuthLog.contains("secret"), "Bearer token must be redacted")
        assertTrue(sanitizedAuthLog.contains("Authorization: Bearer [REDACTED]"))

        // Proxy-Authorization scrubbing
        val rawProxyAuthLog = "Proxy-Authorization: Basic dXNlcjpwYXNz"
        val sanitizedProxyAuthLog = Logger.sanitizeLog(rawProxyAuthLog)
        assertFalse(sanitizedProxyAuthLog.contains("dXNlcjpwYXNz"))
        assertTrue(sanitizedProxyAuthLog.contains("Proxy-Authorization: Basic [REDACTED]"))

        // Cookie scrubbing
        val rawCookieLog = "Cookie: session_id=abc123secret; user_pref=dark"
        val sanitizedCookieLog = Logger.sanitizeLog(rawCookieLog)
        assertFalse(sanitizedCookieLog.contains("abc123secret"))
        assertTrue(sanitizedCookieLog.contains("Cookie: [REDACTED]"))
    }

    @Test
    fun testCheck80_sensitiveHeadersMemoryOnlyIsolation() {
        val taskId = 42L
        val headers = mapOf(
            "Authorization" to listOf("Bearer token123"),
            "Proxy-Authorization" to listOf("Basic c2VjcmV0"),
            "User-Agent" to listOf("XDM-Test"),
            "Referer" to listOf("https://example.com")
        )

        // Stripping for disk persistence
        val stripped = SensitiveHeaders.strip(taskId, headers)
        assertNotNull(stripped)
        assertNull(stripped?.get("Authorization"), "Authorization must be stripped before disk write")
        assertNull(stripped?.get("Proxy-Authorization"), "Proxy-Authorization must be stripped before disk write")
        assertEquals(listOf("XDM-Test"), stripped?.get("User-Agent"))
        assertEquals(listOf("https://example.com"), stripped?.get("Referer"))

        // Restoring from in-memory cache for active download session
        val restored = SensitiveHeaders.restore(taskId, stripped)
        assertEquals(listOf("Bearer token123"), restored?.get("Authorization"))
        assertEquals(listOf("Basic c2VjcmV0"), restored?.get("Proxy-Authorization"))

        // Forgetting download removes cached credentials
        SensitiveHeaders.forget(taskId)
        val afterForget = SensitiveHeaders.restore(taskId, stripped)
        assertNull(afterForget?.get("Authorization"), "Forgotten download must not restore Authorization")
        assertNull(afterForget?.get("Proxy-Authorization"), "Forgotten download must not restore Proxy-Authorization")
    }

    @Test
    fun testCheck80_originMarkerUserinfoStripping() {
        val urlWithCredentials = "https://user:secretPassword@example.com/downloads/file.zip"
        val cleaned = OriginMarker.cleanUrl(urlWithCredentials)
        assertEquals("https://example.com/downloads/file.zip", cleaned)
        assertFalse(cleaned!!.contains("secretPassword"))
        assertFalse(cleaned.contains("user:"))
    }

    // =========================================================================
    // Check 81 & 82: Licensing, Attribution, DRM & Browser Access Controls
    // =========================================================================

    @Test
    fun testCheck81_upstreamAttributionAndLicensingNotices() {
        // README.UPSTREAM.md must exist and preserve Subhra Das Gupta attribution
        val upstreamReadme = File(repoRoot, "README.UPSTREAM.md")
        assertTrue(upstreamReadme.exists() && upstreamReadme.isFile, "README.UPSTREAM.md must be preserved")
        val upstreamContent = upstreamReadme.readText(Charsets.UTF_8)
        assertTrue(upstreamContent.contains("Subhra Das Gupta") || upstreamContent.contains("subhra74"),
            "Original author credit must be preserved in README.UPSTREAM.md")

        // LICENSE must exist (GPLv3)
        val licenseFile = File(repoRoot, "LICENSE")
        assertTrue(licenseFile.exists() && licenseFile.isFile, "LICENSE must exist")
        assertTrue(licenseFile.readText(Charsets.UTF_8).contains("GNU GENERAL PUBLIC LICENSE"))

        // xdm-core/NOTICE must exist and contain historical Apache 2.0 attribution
        val coreNotice = File(repoRoot, "xdm-core/NOTICE")
        assertTrue(coreNotice.exists() && coreNotice.isFile, "xdm-core/NOTICE must exist")
        val noticeContent = coreNotice.readText(Charsets.UTF_8)
        assertTrue(noticeContent.contains("Subhra Das Gupta"))
        assertTrue(noticeContent.contains("Apache License 2.0"))
    }

    @Test
    fun testCheck82_drmComplianceAndNoBypass() {
        // Verify overlay.js detects mediaKeys and displays honest DRM warning rather than attempting decryption
        val chromeOverlay = File(repoRoot, "browser-extension/chrome-extension/overlay.js")
        assertTrue(chromeOverlay.exists())
        val overlayContent = chromeOverlay.readText(Charsets.UTF_8)

        assertTrue(overlayContent.contains("mediaKeys"), "Overlay must inspect mediaKeys for DRM protection")
        assertTrue(overlayContent.contains("DRM Protected Stream"), "Overlay must display DRM protected stream notice")
        assertFalse(overlayContent.contains("decryptWidevine") || overlayContent.contains("bypassDrm"),
            "Code must never attempt DRM circumvention")
    }

    // =========================================================================
    // Check 83: Coordinated Vulnerability Disclosure Policy (SECURITY.md)
    // =========================================================================

    @Test
    fun testCheck83_securityPolicyAndPrivateDisclosureChannel() {
        val securityMd = File(repoRoot, "SECURITY.md")
        assertTrue(securityMd.exists() && securityMd.isFile, "SECURITY.md must exist in root repository")
        val content = securityMd.readText(Charsets.UTF_8)

        assertTrue(content.contains("https://github.com/jakepanlilio2000/XDM-Enhanced/security/advisories/new"),
            "SECURITY.md must provide private GitHub Security Advisories reporting link")
        assertTrue(content.contains("Do NOT Open a Public Issue") || content.contains("do not open a public issue", ignoreCase = true),
            "SECURITY.md must guide reporters away from public zero-day issue creation")
    }
}

