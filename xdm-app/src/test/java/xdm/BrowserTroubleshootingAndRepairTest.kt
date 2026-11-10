package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.OS
import xdm.app.utils.detectOS
import xdm.app.utils.win.Win32Registry
import xdm.app.utils.win.WindowsAssociations
import xdm.integration.BrowserIntegrationRepair
import java.io.File
import java.net.ServerSocket

class BrowserTroubleshootingAndRepairTest {

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
    fun breakAndRepairProtocolRegistration_restoresValidCommandWithoutAlteringUnrelatedKeys() {
        if (detectOS() != OS.Windows) return

        // 1. Setup baseline unrelated keys
        val unrelatedKey = "Software\\Classes\\.sample_unrelated_ext"
        Win32Registry.setString(unrelatedKey, "", "sample.progid")

        try {
            // 2. Intentionally break / unregister protocol
            WindowsAssociations.unregisterProtocol()
            assertFalse(WindowsAssociations.isProtocolRegistered(), "Protocol must be unregistered after deliberate removal")

            val diagBefore = BrowserIntegrationRepair.diagnose()
            assertFalse(diagBefore.protocolRegistered, "Diagnostics must detect missing protocol handler")
            assertTrue(diagBefore.canAutoRepair, "Diagnostics must indicate auto-repair is available")

            // 3. Perform repair
            val repairResult = BrowserIntegrationRepair.repair()
            assertTrue(repairResult.success, "Repair must report success: ${repairResult.message}")
            assertTrue(repairResult.actionsTaken.isNotEmpty(), "Actions taken must be listed")

            // 4. Verify restored state
            assertTrue(WindowsAssociations.isProtocolRegistered(), "Protocol must be registered after repair")
            val diagAfter = BrowserIntegrationRepair.diagnose()
            assertTrue(diagAfter.protocolRegistered, "Diagnostics must report protocol registered after repair")

            // 5. Verify unrelated key was NOT modified or removed
            val unrelatedVal = Win32Registry.getString(unrelatedKey, "")
            assertEquals("sample.progid", unrelatedVal, "Repair must NEVER touch or delete unrelated registry associations")
        } finally {
            Win32Registry.deleteTree(unrelatedKey)
        }
    }

    @Test
    fun breakRegistrationWithMissingBinary_detectsStaleHandlerAndRepairs() {
        if (detectOS() != OS.Windows) return

        try {
            // Intentionally write a stale command pointing to a deleted / non-existent executable
            val fakeCmd = "\"C:\\NonExistent_Directory_12345\\xdm-app.exe\" \"%1\""
            Win32Registry.setString("${WindowsAssociations.XDM_APP_PROTOCOL_KEY}\\shell\\open\\command", "", fakeCmd)

            // Even though "xdm" substring is present in the fake command, the target file is missing,
            // so isProtocolRegistered must return false
            assertFalse(
                WindowsAssociations.isProtocolRegistered(),
                "Stale registration with missing executable must be detected as not registered"
            )

            val diag = BrowserIntegrationRepair.diagnose()
            assertFalse(diag.protocolRegistered, "Diagnostics must detect broken handler with missing binary")
            assertTrue(diag.canAutoRepair, "Diagnostics must offer auto-repair for broken handler")

            // Repair should re-register using the current valid command
            val repair = BrowserIntegrationRepair.repair()
            assertTrue(repair.success, "Repair must succeed")
            assertTrue(WindowsAssociations.isProtocolRegistered(), "Protocol must be valid after repair")
        } finally {
            BrowserIntegrationRepair.repair()
        }
    }

    @Test
    fun diagnose_whenPortNotListening_reportsAccurateClosedStatus() {
        // Find an unused local port
        val testPort = ServerSocket(0).use { it.localPort }

        val report = BrowserIntegrationRepair.diagnose(port = testPort)
        assertFalse(report.portListening, "Port $testPort is closed; must report portListening = false")
        assertFalse(report.portResponding, "Port $testPort is closed; must report portResponding = false")
        assertTrue(
            report.statusSummary.contains("not running") || report.statusSummary.contains("closed"),
            "Summary must state port is closed: ${report.statusSummary}"
        )
        assertTrue(
            report.details.any { it.contains("Port $testPort is closed") || it.contains("closed") },
            "Details must explain port closure: ${report.details}"
        )
    }

    @Test
    fun diagnose_whenPortListening_reportsActiveStatus() {
        ServerSocket(0).use { ss ->
            val activePort = ss.localPort
            val report = BrowserIntegrationRepair.diagnose(port = activePort)
            assertTrue(report.portListening, "Port $activePort is open; must report portListening = true")
            // Since ss is a bare ServerSocket not speaking HTTP /sync, responding should be false
            assertFalse(report.portResponding, "Bare ServerSocket does not answer /sync; portResponding must be false")
        }
    }

    @Test
    fun incompatibleVersionCheck_identifiesOutdatedDesktopVersions() {
        fun isOutdated(v: String?, minV: String = "1.0.0"): Boolean {
            if (v.isNullOrBlank()) return true
            val partsA = v.trim().removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
            val partsB = minV.trim().removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(partsA.size, partsB.size)
            for (i in 0 until maxLen) {
                val a = partsA.getOrElse(i) { 0 }
                val b = partsB.getOrElse(i) { 0 }
                if (a != b) return a < b
            }
            return false
        }

        assertTrue(isOutdated("0.9.8"), "0.9.8 is older than 1.0.0")
        assertTrue(isOutdated("0.1.0-alpha"), "0.1.0 is older than 1.0.0")
        assertTrue(isOutdated(null), "null version is treated as outdated")
        assertTrue(isOutdated(""), "empty version is treated as outdated")

        assertFalse(isOutdated("1.0.0"), "1.0.0 is compatible")
        assertFalse(isOutdated("1.0.0-beta.6"), "1.0.0-beta.6 is compatible")
        assertFalse(isOutdated("1.2.0"), "1.2.0 is compatible")
        assertFalse(isOutdated("2.0.0"), "2.0.0 is compatible")
    }

    @Test
    fun extensionUi_containsTroubleshootingLinkAndActionControls() {
        val chromePopup = File(repoRoot, "browser-extension/chrome-extension/popup.html").readText()
        assertTrue(chromePopup.contains("troubleshootLink"), "Chrome popup.html must contain troubleshootLink")
        assertTrue(chromePopup.contains("Troubleshooting Guide"), "Chrome popup.html must contain Troubleshooting Guide text")

        val ffPopup = File(repoRoot, "browser-extension/firefox-extension/app/popup.html").readText()
        assertTrue(ffPopup.contains("troubleshootLink"), "Firefox popup.html must contain troubleshootLink")
        assertTrue(ffPopup.contains("Troubleshooting Guide"), "Firefox popup.html must contain Troubleshooting Guide text")

        val chromeOptions = File(repoRoot, "browser-extension/chrome-extension/options.html").readText()
        assertTrue(chromeOptions.contains("optionsTroubleshootLink"), "Chrome options.html must contain optionsTroubleshootLink")
        assertTrue(chromeOptions.contains("launchAppBtn"), "Chrome options.html must contain launchAppBtn")
        assertTrue(chromeOptions.contains("connTroubleNotice"), "Chrome options.html must contain connTroubleNotice")

        val ffOptions = File(repoRoot, "browser-extension/firefox-extension/app/options.html").readText()
        assertTrue(ffOptions.contains("optionsTroubleshootLink"), "Firefox options.html must contain optionsTroubleshootLink")
        assertTrue(ffOptions.contains("launchAppBtn"), "Firefox options.html must contain launchAppBtn")
        assertTrue(ffOptions.contains("connTroubleNotice"), "Firefox options.html must contain connTroubleNotice")

        val chromeError = File(repoRoot, "browser-extension/chrome-extension/error.html").readText()
        assertTrue(chromeError.contains("troubleshootLink"), "Chrome error.html must contain troubleshootLink")

        val ffError = File(repoRoot, "browser-extension/firefox-extension/app/error.html").readText()
        assertTrue(ffError.contains("troubleshootLink"), "Firefox error.html must contain troubleshootLink")
    }

    @Test
    fun troubleshootingDocumentation_coversAllRequiredSectionsAndNoPrivateTokens() {
        val docFile = File(repoRoot, "docs/BROWSER_CONNECTION_TROUBLESHOOTING.md")
        assertTrue(docFile.exists(), "docs/BROWSER_CONNECTION_TROUBLESHOOTING.md must exist")
        val content = docFile.readText()

        val requiredSections = listOf(
            "Quick Diagnostic Checklist",
            "Architecture & Privacy Guarantee",
            "Supported Browser & OS Matrix",
            "Common Error States & Solutions",
            "Advanced Developer Diagnostics",
            "Safe Desktop Self-Repair",
            "Clean Reinstallation & Upgrade Guide",
            "Reporting Issues & Log Sanitization"
        )
        for (sec in requiredSections) {
            assertTrue(content.contains(sec), "Troubleshooting doc must contain section: $sec")
        }

        // Verify supported browsers are documented
        val requiredBrowsers = listOf("Google Chrome", "Mozilla Firefox", "Microsoft Edge", "Brave")
        for (b in requiredBrowsers) {
            assertTrue(content.contains(b), "Troubleshooting doc must document browser: $b")
        }

        // Verify error codes are documented
        val requiredErrors = listOf(
            "desktop_unavailable",
            "permission_denied",
            "connection_timeout",
            "incompatible_version",
            "host_missing",
            "launch_failure"
        )
        for (err in requiredErrors) {
            assertTrue(content.contains(err), "Troubleshooting doc must document error code: $err")
        }

        // Verify privacy and sanitization warnings
        assertTrue(content.contains("100% Local Loopback"), "Doc must state 100% Local Loopback guarantee")
        assertTrue(content.contains("Redact Download URLs"), "Doc must emphasize redacting download URLs")
        assertTrue(content.contains("Redact Passwords & Tokens"), "Doc must emphasize redacting tokens and passwords")

        // Verify no sensitive tokens or private credentials leaked
        assertFalse(content.contains("ghp_"), "Must not leak personal GitHub access tokens")
        assertFalse(content.contains("Bearer eyJ"), "Must not leak JWT tokens")

        // Verify README links to troubleshooting guide
        val readmeContent = File(repoRoot, "README.md").readText()
        assertTrue(
            readmeContent.contains("docs/BROWSER_CONNECTION_TROUBLESHOOTING.md"),
            "README.md must link to docs/BROWSER_CONNECTION_TROUBLESHOOTING.md"
        )
    }
}

