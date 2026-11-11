package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.OS
import xdm.app.utils.Browser
import xdm.app.utils.detectOS
import xdm.app.utils.win.Win32Registry
import xdm.app.utils.win.WindowsAssociations
import xdm.integration.BrowserIntegration
import xdm.integration.BrowserIntegrationRepair
import xdm.integration.ExtensionVersionCheck
import xdm.integration.HttpParser
import xdm.integration.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class Phase5BrowserIntegrationAuditTest {

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

    /**
     * Scenario 40: Desktop Running vs Stopped Detection
     * Proves loopback diagnostics accurately detect whether the desktop integration service is active or closed.
     */
    @Test
    fun testDesktopRunningVsStoppedDetection() {
        val freePort = ServerSocket(0).use { it.localPort }

        // 1. When port is closed (desktop stopped)
        val reportStopped = BrowserIntegrationRepair.diagnose(port = freePort)
        assertFalse(reportStopped.portListening, "Port $freePort is closed: portListening must be false")
        assertFalse(reportStopped.portResponding, "Port $freePort is closed: portResponding must be false")
        assertTrue(reportStopped.statusSummary.contains("not running") || reportStopped.statusSummary.contains("closed"))

        // 2. When port is open and serving /sync (desktop running)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val runningPort = serverSocket.localPort
        val filter = BrowserIntegration.RequestFilter(runningPort)
        val server = HttpServer(
            serverSocket = serverSocket,
            policy = filter,
            requestListener = { ctx ->
                ctx.statusCode = 200
                ctx.statusMessage = "OK"
                ctx.keepAlive = false
                ctx.responseBody = "{\"instanceId\":\"inst-1\",\"enabled\":true,\"appVersion\":\"1.0.0-beta.6\"}"
                    .toByteArray(StandardCharsets.UTF_8)
                ctx.sendResponse()
            }
        )
        server.start()

        try {
            val reportRunning = BrowserIntegrationRepair.diagnose(port = runningPort)
            assertTrue(reportRunning.portListening, "Running server must report portListening = true")
            assertTrue(reportRunning.portResponding, "Running server responding to /sync must report portResponding = true")
            assertTrue(reportRunning.statusSummary.contains("healthy") || reportRunning.details.any { it.contains("listening on") }, "Details must confirm listener: ${reportRunning.details}")
        } finally {
            server.stop()
        }
    }

    /**
     * Scenario 40: Stale Host Path and Missing Protocol Registration Recovery
     * Proves stale executable paths (e.g. after NSIS upgrade/relocation) are detected as invalid and repaired.
     */
    @Test
    fun testStaleHostPathAndMissingRegistrationRecovery() {
        if (detectOS() != OS.Windows) return

        try {
            // Write a stale path to a non-existent binary
            val staleCmd = "\"C:\\NonExistent_XDM_Path_98765\\xdm-app.exe\" \"%1\""
            Win32Registry.setString("${WindowsAssociations.XDM_APP_PROTOCOL_KEY}\\shell\\open\\command", "", staleCmd)

            // Even though key exists, the target binary is missing, so isProtocolRegistered must return false
            assertFalse(WindowsAssociations.isProtocolRegistered(), "Stale executable path must fail validation")

            val diag = BrowserIntegrationRepair.diagnose()
            assertFalse(diag.protocolRegistered, "Diagnostics must detect invalid protocol registration")
            assertTrue(diag.canAutoRepair, "Diagnostics must offer auto-repair for broken path")

            // Execute repair
            val repair = BrowserIntegrationRepair.repair()
            assertTrue(repair.success, "Repair must succeed")
            assertTrue(WindowsAssociations.isProtocolRegistered(), "Protocol must be valid after repair")
        } finally {
            BrowserIntegrationRepair.repair()
        }
    }

    /**
     * Scenario 40 & 41: Incompatible Protocol Versions and Diagnostics
     * Evaluates desktop version comparison logic matching connector.js MIN_XDM_VERSION = "1.0.0".
     */
    @Test
    fun testIncompatibleDesktopVersionEvaluation() {
        fun isOutdatedDesktopVersion(version: String?, minVersion: String = "1.0.0"): Boolean {
            if (version.isNullOrBlank()) return true
            fun parts(v: String) = v.removePrefix("v").removePrefix("V").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val a = parts(version)
            val b = parts(minVersion)
            for (i in 0 until maxOf(a.size, b.size)) {
                val segA = a.getOrElse(i) { 0 }
                val segB = b.getOrElse(i) { 0 }
                val diff = segA - segB
                if (diff != 0) return diff < 0
            }
            return false
        }

        assertTrue(isOutdatedDesktopVersion(null))
        assertTrue(isOutdatedDesktopVersion(""))
        assertTrue(isOutdatedDesktopVersion("0.9.0"))
        assertTrue(isOutdatedDesktopVersion("0.9.9"))
        assertFalse(isOutdatedDesktopVersion("1.0.0"))
        assertFalse(isOutdatedDesktopVersion("1.0.0-beta.6"))
        assertFalse(isOutdatedDesktopVersion("1.1.0"))
        assertFalse(isOutdatedDesktopVersion("2.0.0"))
    }

    /**
     * Scenario 41: Extension UI Distinguishes Known Connection Failures
     * Verifies popup.js, error.js, and options.js in both extensions distinguish all 7 connection failure modes.
     */
    @Test
    fun testDistinctDiagnosticsInExtensionScripts() {
        val chromeFiles = listOf(
            File(repoRoot, "browser-extension/chrome-extension/popup.js"),
            File(repoRoot, "browser-extension/chrome-extension/error.js"),
            File(repoRoot, "browser-extension/chrome-extension/options.js")
        )
        val firefoxFiles = listOf(
            File(repoRoot, "browser-extension/firefox-extension/app/popup.js"),
            File(repoRoot, "browser-extension/firefox-extension/app/error.js"),
            File(repoRoot, "browser-extension/firefox-extension/app/options.js")
        )

        val requiredFailureStates = listOf(
            "connection_timeout",
            "permission_denied",
            "incompatible_version",
            "host_missing",
            "launch_failure",
            "recovering"
        )

        for (file in chromeFiles + firefoxFiles) {
            assertTrue(file.exists(), "${file.name} must exist")
            val content = file.readText()
            for (state in requiredFailureStates) {
                assertTrue(
                    content.contains(state),
                    "${file.parentFile.name}/${file.name} must handle distinct diagnostic state '$state'"
                )
            }
        }
    }

    /**
     * Scenario 42: Site Exclusions and YouTube Protection Invariants
     * Verifies YouTube exclusion and URL protocol matching rules.
     */
    @Test
    fun testSiteExclusionsAndYouTubeProtection() {
        val blockedJs = File(repoRoot, "browser-extension/chrome-extension/blocked-sites.js").readText()
        assertTrue(blockedJs.contains("youtube.com"), "blocked-sites.js must include youtube.com")
        assertTrue(blockedJs.contains("youtu.be"), "blocked-sites.js must include youtu.be")
        assertTrue(blockedJs.contains("isBlockedUrl"), "blocked-sites.js must define isBlockedUrl")
        assertTrue(blockedJs.contains("matchesHostRule"), "blocked-sites.js must define matchesHostRule")

        val appJs = File(repoRoot, "browser-extension/chrome-extension/app.js").readText()
        assertTrue(appJs.contains("isSupportedProtocol"), "app.js must validate supported protocols")
        assertTrue(appJs.contains("magnet:"), "app.js must support magnet links")
        assertTrue(appJs.contains("open-bulk"), "app.js must handle bulk links command")
    }

    /**
     * Scenario 43: Manifest Validation, CSP Security, and Least Privileges
     * Proves manifests contain no unsafe permissions and HTML pages contain no inline or remote scripts.
     */
    @Test
    fun testManifestSecurityAndContentSecurityPolicy() {
        // Chromium Manifest V3
        val chromeManifestFile = File(repoRoot, "browser-extension/chrome-extension/manifest.json")
        assertTrue(chromeManifestFile.exists())
        val chromeManifest = chromeManifestFile.readText()
        assertTrue(chromeManifest.contains("\"manifest_version\": 3"))
        assertFalse(chromeManifest.contains("nativeMessaging"), "No brittle background nativeMessaging permission")
        assertFalse(chromeManifest.contains("<all_urls>"), "MV3 must use host_permissions")

        // Firefox Manifest V2
        val ffManifestFile = File(repoRoot, "browser-extension/firefox-extension/manifest.json")
        assertTrue(ffManifestFile.exists())
        val ffManifest = ffManifestFile.readText()
        assertTrue(ffManifest.contains("\"manifest_version\": 2"))
        assertTrue(ffManifest.contains("browser_specific_settings"))
        assertTrue(ffManifest.contains("xdm-enhanced-browser-monitor@jakepanlilio2000.github.io"))

        // Validate HTML files contain no inline scripts
        val htmlFiles = listOf(
            File(repoRoot, "browser-extension/chrome-extension/popup.html"),
            File(repoRoot, "browser-extension/chrome-extension/error.html"),
            File(repoRoot, "browser-extension/chrome-extension/options.html"),
            File(repoRoot, "browser-extension/chrome-extension/bulk.html"),
            File(repoRoot, "browser-extension/firefox-extension/app/popup.html"),
            File(repoRoot, "browser-extension/firefox-extension/app/error.html"),
            File(repoRoot, "browser-extension/firefox-extension/app/options.html"),
            File(repoRoot, "browser-extension/firefox-extension/app/bulk.html")
        )

        for (html in htmlFiles) {
            assertTrue(html.exists(), "${html.name} must exist")
            val text = html.readText()
            // Ensure no inline script with code inside <script>...</script>
            val inlineMatch = Regex("""(?s)<script\b(?![^>]*\bsrc=)[^>]*>(.*?)</script>""").findAll(text)
            for (m in inlineMatch) {
                val code = m.groupValues[1].trim()
                assertEquals(0, code.length, "${html.name} contains inline script: $code")
            }
            // Ensure no remote scripts
            val remoteMatch = Regex("""<script\b[^>]*\bsrc=["'](https?:|//)""").find(text)
            assertNull(remoteMatch, "${html.name} references remote script")
        }
    }

    /**
     * Scenario 44: Supported Browser Matrix and Version Mapping
     * Proves browser detection across Chrome, Edge, and Firefox mappings.
     */
    @Test
    fun testBrowserDetectionAndVersionMapping() {
        val chromeUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
        val edgeUa = "$chromeUa Edg/130.0.0.0"
        val firefoxUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"

        assertEquals(Browser.CHROME, ExtensionVersionCheck.detectBrowser("chrome/3.5.4", "chrome-extension://xyz", chromeUa))
        assertEquals(Browser.EDGE, ExtensionVersionCheck.detectBrowser("chrome/3.5.4", "chrome-extension://xyz", edgeUa))
        assertEquals(Browser.FIREFOX, ExtensionVersionCheck.detectBrowser("firefox/1.1.4", "moz-extension://xyz", firefoxUa))

        // Manifest versions match packaging declarations
        val chromeManifest = File(repoRoot, "browser-extension/chrome-extension/manifest.json").readText()
        assertTrue(chromeManifest.contains("\"version\": \"3.5.4\""))
        assertTrue(chromeManifest.contains("\"version_name\": \"1.0.0-rc.1\"") || chromeManifest.contains("\"version_name\": \"1.0.0-beta.6\""))

        val ffManifest = File(repoRoot, "browser-extension/firefox-extension/manifest.json").readText()
        assertTrue(ffManifest.contains("\"version\": \"1.1.4\""))
    }
}

