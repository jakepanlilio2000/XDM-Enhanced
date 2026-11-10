package xdm.integration

import xdm.app.OS
import xdm.app.update.UpdateChecker
import xdm.app.utils.AppLauncher
import xdm.app.utils.Browser
import xdm.app.utils.detectOS
import xdm.app.utils.win.WindowsAssociations
import xdm.core.util.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Diagnostics and self-repair engine for XDM Enhanced browser integration.
 *
 * Provides non-destructive, authorized repairs scoped strictly to the current user profile.
 * Never attempts to rewrite browser internal policies or install unsigned extensions.
 */
object BrowserIntegrationRepair {

    const val TROUBLESHOOTING_URL =
        "https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/docs/BROWSER_CONNECTION_TROUBLESHOOTING.md"

    const val LOOPBACK_HOST = "127.0.0.1"
    const val DEFAULT_PORT = BrowserIntegration.PORT

    data class DiagnosticReport(
        val portListening: Boolean,
        val portResponding: Boolean,
        val protocolRegistered: Boolean,
        val protocolCommand: String?,
        val activeBrowsers: List<Browser>,
        val appVersion: String,
        val statusSummary: String,
        val details: List<String>,
        val canAutoRepair: Boolean
    )

    data class RepairResult(
        val success: Boolean,
        val message: String,
        val actionsTaken: List<String>,
        val manualSteps: List<String>
    )

    /**
     * Inspects loopback port, sync endpoint, URL protocol registration, and connected browsers.
     */
    fun diagnose(port: Int = DEFAULT_PORT): DiagnosticReport {
        val details = mutableListOf<String>()
        var listening = false
        var responding = false

        // 1. Socket probe
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(LOOPBACK_HOST, port), 500)
                listening = true
            }
        } catch (_: Exception) {
            listening = false
        }

        // 2. HTTP /sync probe
        if (listening) {
            try {
                val conn = java.net.URI.create("http://$LOOPBACK_HOST:$port/sync").toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
                conn.connectTimeout = 800
                conn.readTimeout = 1200
                conn.requestMethod = "GET"
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                    if (body.contains("\"instanceId\"") || body.contains("\"enabled\"")) {
                        responding = true
                    }
                }
            } catch (e: Exception) {
                Logger.info("BrowserIntegrationRepair", "Sync probe failed: ${e.message}")
            }
        }

        // 3. Protocol registration check
        var protocolRegistered = false
        var protocolCommand: String? = null

        val os = detectOS()
        if (os == OS.Windows) {
            protocolRegistered = WindowsAssociations.isProtocolRegistered()
            protocolCommand = WindowsAssociations.openCommand()
        } else if (os == OS.Linux) {
            // Check for desktop file with scheme handler in standard user or system paths
            val userDesktop = File(System.getProperty("user.home"), ".local/share/applications/xdm-app.desktop")
            val sysDesktop = File("/usr/share/applications/xdm-app.desktop")
            protocolRegistered = userDesktop.exists() || sysDesktop.exists()
            protocolCommand = if (protocolRegistered) "xdm-app %U" else null
        } else {
            // macOS / other
            protocolRegistered = true
        }

        // 4. Connected browsers
        val activeBrowsers = Browser.entries.filter { BrowserIntegration.isConnected(it) }

        // 5. Version
        val appVersion = UpdateChecker.loadConfig()?.currentVersion ?: "1.0.0-beta.6"

        // 6. Assemble details and summary
        var canAutoRepair = false
        val summary: String

        if (!listening) {
            summary = "Local loopback listener on port $port is not running."
            details.add("Port $port is closed. Ensure XDM Enhanced is running in the background.")
            details.add("Check whether security software or a local firewall is blocking 127.0.0.1 connections.")
        } else if (!responding) {
            summary = "Port $port is listening but refusing or returning unexpected synchronization responses."
            details.add("Another application may be bound to port $port, or the process is busy.")
        } else if (!protocolRegistered) {
            summary = "xdm-app:// protocol handler is missing or not registered for your user account."
            details.add("Browser extensions cannot launch XDM automatically without this protocol handler.")
            canAutoRepair = (os == OS.Windows)
        } else if (activeBrowsers.isEmpty()) {
            summary = "Desktop service is healthy, but no browser extension has checked in recently."
            details.add("Integration service is listening on http://$LOOPBACK_HOST:$port.")
            details.add("Protocol handler xdm-app:// is registered.")
            details.add("Open your browser and ensure the XDM Enhanced extension is enabled.")
        } else {
            summary = "Integration services are operating normally with ${activeBrowsers.size} active browser(s)."
            details.add("Listening on 127.0.0.1:$port (HTTP OK).")
            details.add("xdm-app:// protocol registered.")
            details.add("Active browser connections: " + activeBrowsers.joinToString(", ") { it.displayName })
        }

        return DiagnosticReport(
            portListening = listening,
            portResponding = responding,
            protocolRegistered = protocolRegistered,
            protocolCommand = protocolCommand,
            activeBrowsers = activeBrowsers,
            appVersion = appVersion,
            statusSummary = summary,
            details = details,
            canAutoRepair = canAutoRepair || (os == OS.Windows && !protocolRegistered)
        )
    }

    /**
     * Executes authorized per-user repairs.
     * On Windows, registers or repairs the `xdm-app://` handler in HKCU.
     */
    fun repair(port: Int = DEFAULT_PORT): RepairResult {
        val actions = mutableListOf<String>()
        val manual = mutableListOf<String>()
        val os = detectOS()

        if (os == OS.Windows) {
            try {
                val ok = WindowsAssociations.registerProtocol()
                if (ok) {
                    actions.add("Registered xdm-app:// protocol handler in HKCU\\Software\\Classes\\xdm-app.")
                } else {
                    manual.add("Failed to write to HKCU\\Software\\Classes\\xdm-app. Check user registry permissions.")
                }
            } catch (e: Exception) {
                Logger.error("BrowserIntegrationRepair", "Repair failed on Windows", e)
                manual.add("Error registering protocol handler: ${e.message}")
            }
        } else if (os == OS.Linux) {
            manual.add("On Linux, ensure xdm-app.desktop is installed in ~/.local/share/applications/ or /usr/share/applications/ with MimeType=x-scheme-handler/xdm-app.")
            manual.add("Run: xdg-mime default xdm-app.desktop x-scheme-handler/xdm-app")
        } else {
            manual.add("On macOS, launch the application from /Applications/XDM Enhanced.app once to register URL handlers.")
        }

        // Verify result
        val postDiag = diagnose(port)
        if (!postDiag.portListening) {
            manual.add("Loopback listener on port $port is not active in this process.")
        }
        val success = (os != OS.Windows || postDiag.protocolRegistered)

        val message = if (success) {
            "Browser integration repaired successfully."
        } else {
            "Browser integration repair incomplete. Some manual steps may be needed."
        }

        return RepairResult(
            success = success,
            message = message,
            actionsTaken = actions,
            manualSteps = manual
        )
    }
}

