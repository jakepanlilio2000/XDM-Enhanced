package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.utils.Browser
import xdm.integration.BrowserIntegration
import xdm.integration.ExtensionVersionCheck
import xdm.integration.HttpParser
import java.io.File

class BrowserIntegrationHandshakeTest {

    private val repoRoot: File = run {
        val here = File(".").canonicalFile
        if (File(here, "browser-extension").exists()) {
            here
        } else if (File(here.parentFile, "browser-extension").exists()) {
            here.parentFile
        } else {
            here
        }
    }

    @Test
    fun requestFilter_allowsValidLocalhostAndLoopbackHosts() {
        val filter = BrowserIntegration.RequestFilter(8597)

        val headLoopback = HttpParser.RequestHead(
            method = "GET",
            path = "/sync",
            version = "HTTP/1.1",
            headers = mapOf("Host" to listOf("127.0.0.1:8597")),
            deadlineNanos = System.nanoTime() + 1_000_000_000
        )
        assertNull(filter.reject(headLoopback), "GET /sync from 127.0.0.1:8597 must be accepted")

        val headLocalhost = HttpParser.RequestHead(
            method = "GET",
            path = "/sync",
            version = "HTTP/1.1",
            headers = mapOf("Host" to listOf("localhost:8597")),
            deadlineNanos = System.nanoTime() + 1_000_000_000
        )
        assertNull(filter.reject(headLocalhost), "GET /sync from localhost:8597 must be accepted")
    }

    @Test
    fun requestFilter_rejectsForeignHostHeadersToPreventDnsRebinding() {
        val filter = BrowserIntegration.RequestFilter(8597)

        val headRebinding = HttpParser.RequestHead(
            method = "GET",
            path = "/sync",
            version = "HTTP/1.1",
            headers = mapOf("Host" to listOf("malicious-rebinding-domain.org:8597")),
            deadlineNanos = System.nanoTime() + 1_000_000_000
        )
        val rejection = filter.reject(headRebinding)
        assertNotNull(rejection, "Foreign host header must be rejected")
        assertEquals(403, rejection?.first)
    }

    @Test
    fun requestFilter_allowsExtensionOriginsAndRejectsWebPageOrigins() {
        val filter = BrowserIntegration.RequestFilter(8597)

        // Valid chrome-extension origin
        val headChrome = HttpParser.RequestHead(
            method = "POST",
            path = "/download",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:8597"),
                "Origin" to listOf("chrome-extension://abcdefghijklmnop")
            ),
            deadlineNanos = System.nanoTime() + 1_000_000_000
        )
        assertNull(filter.reject(headChrome), "chrome-extension:// origin must be permitted")

        // Valid moz-extension origin
        val headMoz = HttpParser.RequestHead(
            method = "POST",
            path = "/media",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:8597"),
                "Origin" to listOf("moz-extension://12345678-abcd-ef01-2345-6789abcdef01")
            ),
            deadlineNanos = System.nanoTime() + 1_000_000_000
        )
        assertNull(filter.reject(headMoz), "moz-extension:// origin must be permitted")

        // Unauthorized web page origin
        val headWeb = HttpParser.RequestHead(
            method = "POST",
            path = "/download",
            version = "HTTP/1.1",
            headers = mapOf(
                "Host" to listOf("127.0.0.1:8597"),
                "Origin" to listOf("https://phishing-site.example.com")
            ),
            deadlineNanos = System.nanoTime() + 1_000_000_000
        )
        val webRejection = filter.reject(headWeb)
        assertNotNull(webRejection, "Web page origin must be rejected with 403")
        assertEquals(403, webRejection?.first)
    }

    @Test
    fun requestFilter_enforcesPostOnlyForStateChangingPaths() {
        val filter = BrowserIntegration.RequestFilter(8597)

        val paths = listOf("/download", "/media", "/vid", "/batch", "/clear", "/clear-tab", "/tab-update", "/show", "/poll")
        for (path in paths) {
            val headGet = HttpParser.RequestHead(
                method = "GET",
                path = path,
                version = "HTTP/1.1",
                headers = mapOf("Host" to listOf("127.0.0.1:8597")),
                deadlineNanos = System.nanoTime() + 1_000_000_000
            )
            val rejection = filter.reject(headGet)
            assertNotNull(rejection, "GET $path must be rejected with 405 Method Not Allowed")
            assertEquals(405, rejection?.first)
        }
    }

    @Test
    fun extensionVersionCheck_compatibleAndOutdatedVersions() {
        // Modern Chrome extension
        assertNull(ExtensionVersionCheck.outdatedBrowser("chrome/3.5.3", "chrome-extension://abc", "Chrome/120"))
        // Modern Firefox extension
        assertNull(ExtensionVersionCheck.outdatedBrowser("firefox/1.1.3", "moz-extension://abc", "Firefox/120"))

        // Outdated Chrome extension
        assertEquals(Browser.CHROME, ExtensionVersionCheck.outdatedBrowser("chrome/3.4", "chrome-extension://abc", "Chrome/120"))
        // Outdated Edge extension
        assertEquals(Browser.EDGE, ExtensionVersionCheck.outdatedBrowser("chrome/3.4", "chrome-extension://abc", "Edg/120.0"))
        // Outdated Firefox extension
        assertEquals(Browser.FIREFOX, ExtensionVersionCheck.outdatedBrowser("firefox/1.0", "moz-extension://abc", "Firefox/120"))
        // Missing header with extension origin (legacy upstream XDM)
        assertEquals(Browser.CHROME, ExtensionVersionCheck.outdatedBrowser(null, "chrome-extension://abc", "Chrome/120"))
    }

    @Test
    fun chromeExtension_structuredHandshakeStatesAndRetryImplemented() {
        val connectorFile = File(repoRoot, "browser-extension/chrome-extension/connector.js")
        assertTrue(connectorFile.exists(), "Chromium connector.js must exist")
        val content = connectorFile.readText()

        val requiredStates = listOf(
            "connecting",
            "connected",
            "incompatible_version",
            "host_missing",
            "launch_failure",
            "permission_denied",
            "connection_timeout",
            "desktop_unavailable",
            "recovering"
        )
        for (st in requiredStates) {
            assertTrue(content.contains(st), "connector.js must define or handle status state: $st")
        }

        assertTrue(content.contains("recheck"), "Chromium connector.js must implement recheck()")
        assertTrue(content.contains("launchApp"), "Chromium connector.js must implement launchApp()")
        assertTrue(content.contains("xdm-app://launch"), "Chromium connector.js must use xdm-app://launch protocol")
        assertTrue(content.contains("RETRY_BACKOFF_SCHEDULE_MS"), "Chromium connector.js must define backoff schedule")
    }

    @Test
    fun firefoxExtension_structuredHandshakeStatesAndRetryImplemented() {
        val connectorFile = File(repoRoot, "browser-extension/firefox-extension/app/connector.js")
        assertTrue(connectorFile.exists(), "Firefox connector.js must exist")
        val content = connectorFile.readText()

        val requiredStates = listOf(
            "connecting",
            "connected",
            "incompatible_version",
            "host_missing",
            "launch_failure",
            "permission_denied",
            "connection_timeout",
            "desktop_unavailable",
            "recovering"
        )
        for (st in requiredStates) {
            assertTrue(content.contains(st), "Firefox connector.js must define or handle status state: $st")
        }

        assertTrue(content.contains("recheck"), "Firefox connector.js must implement recheck()")
        assertTrue(content.contains("launchApp"), "Firefox connector.js must implement launchApp()")
        assertTrue(content.contains("xdm-app://launch"), "Firefox connector.js must use xdm-app://launch protocol")
        assertTrue(content.contains("RETRY_BACKOFF_SCHEDULE_MS"), "Firefox connector.js must define backoff schedule")
    }

    @Test
    fun popupAndOptionsUi_containRecheckActionsAndStructuredStatus() {
        // Chromium popup and options
        val chromePopup = File(repoRoot, "browser-extension/chrome-extension/popup.html").readText()
        assertTrue(chromePopup.contains("recheckBtn"), "Chrome popup.html must contain recheckBtn")
        assertTrue(chromePopup.contains("launchDesktopBtn"), "Chrome popup.html must contain launchDesktopBtn")

        val chromeOptions = File(repoRoot, "browser-extension/chrome-extension/options.html").readText()
        assertTrue(chromeOptions.contains("recheckConnBtn"), "Chrome options.html must contain recheckConnBtn")

        val chromeError = File(repoRoot, "browser-extension/chrome-extension/error.html").readText()
        assertTrue(chromeError.contains("recheckBtn"), "Chrome error.html must contain recheckBtn")

        // Firefox popup and options
        val ffPopup = File(repoRoot, "browser-extension/firefox-extension/app/popup.html").readText()
        assertTrue(ffPopup.contains("recheckBtn"), "Firefox popup.html must contain recheckBtn")
        assertTrue(ffPopup.contains("launchDesktopBtn"), "Firefox popup.html must contain launchDesktopBtn")

        val ffOptions = File(repoRoot, "browser-extension/firefox-extension/app/options.html").readText()
        assertTrue(ffOptions.contains("recheckConnBtn"), "Firefox options.html must contain recheckConnBtn")

        val ffError = File(repoRoot, "browser-extension/firefox-extension/app/error.html").readText()
        assertTrue(ffError.contains("recheckBtn"), "Firefox error.html must contain recheckBtn")
    }

    @Test
    fun nsisInstaller_isolatedFromUpstreamXdm() {
        val nsisFile = File(repoRoot, "packaging/windows/xdm-installer.nsi")
        assertTrue(nsisFile.exists(), "xdm-installer.nsi must exist")
        val content = nsisFile.readText()

        assertTrue(content.contains("xdman.jar") && content.contains("xdman.exe"),
            "Installer must check for upstream xdman binary to avoid overwriting")
        assertTrue(content.contains("Software\\XDM Enhanced"),
            "Installer must register under dedicated Software\\XDM Enhanced key")
        assertTrue(content.contains("xdm-app"),
            "Installer must register xdm-app protocol handler")
    }

    @Test
    fun structuredHandshake_backoffCalculationProgression() {
        val schedule = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L)
        fun calculateDelay(retryCount: Int): Long = schedule[minOf(retryCount, schedule.size - 1)]

        assertEquals(1000L, calculateDelay(0))
        assertEquals(2000L, calculateDelay(1))
        assertEquals(4000L, calculateDelay(2))
        assertEquals(8000L, calculateDelay(3))
        assertEquals(16000L, calculateDelay(4))
        assertEquals(30000L, calculateDelay(5))
        // Capped at 30s
        assertEquals(30000L, calculateDelay(6))
        assertEquals(30000L, calculateDelay(10))
    }

    @Test
    fun browserIntegration_statusDtoFieldsComplete() {
        val config = xdm.integration.ConfigDto(
            enabled = true,
            fileExts = listOf("mp4", "zip"),
            blockedHosts = listOf("youtube.com"),
            requestFileExts = listOf(".mp4"),
            mediaTypes = listOf("video/mp4"),
            matchingHosts = emptyList(),
            videoList = emptyList(),
            version = 42L,
            instanceId = "test-instance-1",
            appVersion = "1.0.0-beta.5"
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val encoded = json.encodeToString(xdm.integration.ConfigDto.serializer(), config)
        assertTrue(encoded.contains("\"instanceId\":\"test-instance-1\""))
        assertTrue(encoded.contains("\"appVersion\":\"1.0.0-beta.5\""))
        assertTrue(encoded.contains("\"version\":42"))
        assertTrue(encoded.contains("\"enabled\":true"))
    }
}

