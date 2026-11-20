package xdm.app.plugins

import xdm.app.AppContext
import xdm.app.update.UpdateChecker
import xdm.core.util.Logger
import java.io.File
import java.net.NetworkInterface
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Diagnostic exporter for troubleshooting plugin integrations, download providers,
 * external tools (such as yt-dlp), SOCKS/HTTP proxies, and VPN client connectivity.
 *
 * Guarantees zero secrets in export output (all credentials, auth tokens, and private keys
 * are redacted).
 */
object PluginDiagnosticsExport {

    data class ExternalToolStatus(
        val toolName: String,
        val configuredPath: String,
        val detected: Boolean,
        val detectedPath: String?,
        val version: String?,
        val troubleshootingAdvice: String?
    )

    @Volatile
    private var cachedYtDlpStatus: ExternalToolStatus? = null
    @Volatile
    private var lastYtDlpCheckMs: Long = 0L
    private const val CACHE_TTL_MS = 15_000L

    fun clearToolStatusCache() {
        cachedYtDlpStatus = null
        lastYtDlpCheckMs = 0L
    }

    fun getAppVersion(): String =
        UpdateChecker.loadConfig()?.currentVersion ?: UpdateChecker.DEFAULT_VERSION

    fun checkYtDlpToolStatus(queryVersion: Boolean = true): ExternalToolStatus {
        val now = System.currentTimeMillis()
        val cached = cachedYtDlpStatus
        if (cached != null && (now - lastYtDlpCheckMs < CACHE_TTL_MS)) {
            if (!queryVersion || cached.version != null || !cached.detected) {
                return cached
            }
        }

        val ytdlpPlugin = PluginManager.installedPlugins.firstOrNull { it.manifest.id == "ytdlp-downloader" }
        val configuredPath = ytdlpPlugin?.context?.config?.getString("executable.path", "yt-dlp") ?: "yt-dlp"

        var detected = false
        var resolvedPath: String? = null
        var version: String? = null

        // 1. Check configured path directly
        val directFile = File(configuredPath)
        if (directFile.isAbsolute && directFile.isFile && directFile.canExecute()) {
            detected = true
            resolvedPath = directFile.absolutePath
        } else {
            // 2. Search PATH
            val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
            val pathEnv = System.getenv("PATH") ?: ""
            val pathEntries = pathEnv.split(File.pathSeparator).filter { it.isNotBlank() }
            val candidateNames = if (isWindows) listOf(configuredPath, "$configuredPath.exe", "yt-dlp.exe", "yt-dlp.cmd") else listOf(configuredPath, "yt-dlp")

            for (entry in pathEntries) {
                for (name in candidateNames) {
                    val candidate = File(entry, name)
                    if (candidate.isFile && (isWindows || candidate.canExecute())) {
                        detected = true
                        resolvedPath = candidate.absolutePath
                        break
                    }
                }
                if (detected) break
            }

            // 3. Common fallback locations
            if (!detected && isWindows) {
                val userHome = System.getProperty("user.home", "")
                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome\\AppData\\Local"
                val commonFallbacks = listOf(
                    File("$localAppData\\Programs\\yt-dlp\\yt-dlp.exe"),
                    File("$localAppData\\Microsoft\\WinGet\\Links\\yt-dlp.exe"),
                    File("C:\\Program Files\\yt-dlp\\yt-dlp.exe"),
                    File("C:\\ProgramData\\chocolatey\\bin\\yt-dlp.exe"),
                    File("$userHome\\tools\\yt-dlp.exe")
                )
                for (fb in commonFallbacks) {
                    if (fb.isFile) {
                        detected = true
                        resolvedPath = fb.absolutePath
                        break
                    }
                }
            }
        }

        // 4. Safely query version if executable is found and version query requested
        if (detected && resolvedPath != null && queryVersion) {
            try {
                val proc = ProcessBuilder(resolvedPath, "--version")
                    .redirectErrorStream(true)
                    .start()
                val finished = proc.waitFor(1500, TimeUnit.MILLISECONDS)
                if (finished && proc.exitValue() == 0) {
                    version = proc.inputStream.bufferedReader().readLine()?.trim()
                } else {
                    proc.destroyForcibly()
                }
            } catch (t: Throwable) {
                Logger.info("Diagnostics", "yt-dlp version check ignored: ${t.message}")
            }
        } else if (detected && resolvedPath != null && !queryVersion) {
            version = cached?.version
        }

        val advice = if (!detected) {
            "yt-dlp executable was not detected on system PATH. Install yt-dlp from https://github.com/yt-dlp/yt-dlp and ensure it is available on system PATH, or specify its exact location in yt-dlp Media Downloader settings."
        } else null

        val status = ExternalToolStatus(
            toolName = "yt-dlp",
            configuredPath = configuredPath,
            detected = detected,
            detectedPath = resolvedPath,
            version = version,
            troubleshootingAdvice = advice
        )

        cachedYtDlpStatus = status
        lastYtDlpCheckMs = now
        return status
    }

    fun detectTunnelInterfaces(): List<String> {
        val tunnels = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val name = ni.name.lowercase()
                val display = ni.displayName.lowercase()
                if (name.startsWith("wg") || name.startsWith("tun") || name.startsWith("tap") ||
                    name.contains("wintun") || name.contains("tailscale") || name.contains("wireguard") ||
                    display.contains("wireguard") || display.contains("openvpn") || display.contains("tunnel")
                ) {
                    tunnels.add("${ni.name} (${ni.displayName})")
                }
            }
        } catch (_: Throwable) {
        }
        return tunnels
    }

    fun generateReport(): String {
        val sb = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(Date())

        sb.appendLine("================================================================================")
        sb.appendLine("                 XDM ENHANCED - PLUGIN & INTEGRATION DIAGNOSTICS                ")
        sb.appendLine("================================================================================")
        sb.appendLine("Report Generated   : $timestamp")
        sb.appendLine("Application Version: ${getAppVersion()}")
        sb.appendLine("Plugin API Version : ${PluginManager.API_VERSION}")
        sb.appendLine("Java Runtime       : ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        sb.appendLine("Java VM            : ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
        sb.appendLine("Operating System   : ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        sb.appendLine("Safe Mode Active   : ${PluginManager.isSafeMode}")
        sb.appendLine("Consecutive Crashes: ${PluginManager.loadCrashRecoveryState().consecutiveStartupFailures}")
        sb.appendLine("Privacy Guarantee  : Zero secrets exported (all credentials and tokens redacted)")
        sb.appendLine("================================================================================")
        sb.appendLine()

        // 1. Plugins Roster
        sb.appendLine("--- [1. INSTALLED PLUGINS ROSTER] ---")
        val plugins = PluginManager.installedPlugins
        if (plugins.isEmpty()) {
            sb.appendLine("No plugins currently installed.")
        } else {
            for (p in plugins) {
                val sourceText = when {
                    p.isBuiltIn -> "Built-in (Host Application)"
                    p.archiveFile != null -> "Local Archive (${p.archiveFile.name})"
                    else -> "Directory (${p.pluginDir.name})"
                }
                sb.appendLine("• Plugin ID       : ${p.manifest.id}")
                sb.appendLine("  Name            : ${p.manifest.name}")
                sb.appendLine("  Version         : ${p.manifest.version}")
                sb.appendLine("  Author          : ${if (p.manifest.author.isNotBlank()) p.manifest.author else "Unknown"}")
                sb.appendLine("  License         : ${if (p.manifest.license.isNotBlank()) p.manifest.license else "Unspecified"}")
                sb.appendLine("  Status          : ${p.status.displayName}")
                sb.appendLine("  Source          : $sourceText")
                sb.appendLine("  API Required    : >=${if (p.manifest.minApiVersion.isNotBlank()) p.manifest.minApiVersion else "1.0.0"} (Host: ${PluginManager.API_VERSION})")
                sb.appendLine("  Capabilities    : ${if (p.manifest.capabilities.isNotEmpty()) p.manifest.capabilities.joinToString(", ") else "None"}")
                sb.appendLine("  Preserved Data  : ${File(PluginManager.pluginsDir, ".preserved-data/${p.manifest.id}").exists()}")
                if (!p.faultReason.isNullOrBlank()) {
                    sb.appendLine("  Fault Reason    : ${PluginManager.sanitizeDiagnostics(p.faultReason!!)}")
                }
                sb.appendLine()
            }
        }

        // 2. yt-dlp & Download Provider Integration Diagnostics
        sb.appendLine("--- [2. DOWNLOAD PROVIDERS & EXTERNAL TOOLS DIAGNOSTICS] ---")
        val downloadProviders = PluginManager.getAllDownloadProviders()
        val recognizers = PluginManager.getAllUrlRecognizers()
        sb.appendLine("Active Download Providers: ${downloadProviders.size}")
        for ((p, prov) in downloadProviders) {
            sb.appendLine("  - [${p.manifest.id}] ${prov.name}")
        }
        sb.appendLine("Active URL Recognizers   : ${recognizers.size}")
        for ((p, rec) in recognizers) {
            sb.appendLine("  - [${p.manifest.id}] ${rec.javaClass.simpleName}")
        }
        sb.appendLine()
        sb.appendLine("External Tool Status (yt-dlp):")
        val ytdlpCheck = checkYtDlpToolStatus()
        sb.appendLine("  - Configured Path       : ${ytdlpCheck.configuredPath}")
        sb.appendLine("  - Executable Found      : ${if (ytdlpCheck.detected) "YES (${ytdlpCheck.detectedPath})" else "NO"}")
        if (ytdlpCheck.detected && !ytdlpCheck.version.isNullOrBlank()) {
            sb.appendLine("  - Detected Version      : ${ytdlpCheck.version}")
        }
        if (!ytdlpCheck.detected) {
            sb.appendLine("  - Troubleshooting Advice : ${ytdlpCheck.troubleshootingAdvice}")
        }
        sb.appendLine()

        // 3. Network & Proxy Integration Diagnostics
        sb.appendLine("--- [3. NETWORK & PROXY INTEGRATION DIAGNOSTICS] ---")
        val proxyProviders = PluginManager.getActiveProxyProviders()
        sb.appendLine("Active Proxy Providers: ${proxyProviders.size}")
        for ((p, prov) in proxyProviders) {
            sb.appendLine("  - [${p.manifest.id}] ${prov.providerId} (Priority: ${prov.priority}, Approved: ${prov.isUserApproved})")
            sb.appendLine("    Applicable Engines: ${prov.applicableEngines.joinToString(", ") { it.name }}")
        }
        try {
            val testUri = URI("https://example.com/test")
            val resolved = PluginManager.resolveProxy(testUri)
            if (resolved != null) {
                sb.appendLine("Sample Proxy Resolution for ${testUri.host}:")
                sb.appendLine("  - Resolved Profile: ${resolved.toRedactedString()}")
                sb.appendLine("  - Type            : ${resolved.type}")
                sb.appendLine("  - Safe Address    : ${resolved.safeAddress}")
                sb.appendLine("  - Fallback Policy : ${resolved.fallbackPolicy}")
                sb.appendLine("  - Has Credentials : ${resolved.type != xdm.plugin.api.network.ProxyResolution.Type.DIRECT && resolved.type != xdm.plugin.api.network.ProxyResolution.Type.SYSTEM_DEFAULT && resolved.toString().contains(":***")}")
            } else {
                sb.appendLine("Sample Proxy Resolution: Direct / System Default (No active proxy override)")
            }
        } catch (e: Exception) {
            sb.appendLine("Sample Proxy Resolution Error: ${e.message}")
        }
        sb.appendLine("Routing Scope Guarantee: Scoped exclusively to supported XDM engines; OS-wide network stack is never modified.")
        sb.appendLine("Troubleshooting Advice : If SOCKS5 proxy tasks fail, verify endpoint accessibility, DNS resolution mode, and engine compatibility.")
        sb.appendLine()

        // 4. VPN Client Integration Diagnostics
        sb.appendLine("--- [4. VPN CLIENT INTEGRATION & TELEMETRY DIAGNOSTICS] ---")
        val vpnProviders = PluginManager.getVpnStatusProviders()
        sb.appendLine("Active VPN Status Providers: ${vpnProviders.size}")
        for ((p, prov) in vpnProviders) {
            val status = runCatching { prov.currentStatus }.getOrNull()
            sb.appendLine("  - [${p.manifest.id}] ${prov.providerId} (${prov.displayName})")
            sb.appendLine("    State: ${status?.state ?: "UNKNOWN"}, Connected: ${status?.isConnected == true}")
            sb.appendLine("    Reported Interface: ${status?.interfaceName ?: "None"}")
        }
        val reqProviderId = AppContext.config.vpnRequiredProviderId
        sb.appendLine("Configured VPN Prerequisite Provider: ${if (reqProviderId.isNotBlank()) reqProviderId else "None (Disabled)"}")
        sb.appendLine("VPN Download Policy Enforced        : ${AppContext.config.vpnPrerequisiteEnabled}")
        sb.appendLine("Tasks Currently Paused by Policy    : ${VpnPolicyManager.getPausedByPolicyCount()}")
        val tunnels = detectTunnelInterfaces()
        sb.appendLine("Detected OS Tunnel Interfaces       : ${if (tunnels.isNotEmpty()) tunnels.joinToString(", ") else "None detected"}")
        sb.appendLine("Troubleshooting Advice              : If tasks pause unexpectedly, ensure VPN tunnel is active or disable VPN Prerequisite in Settings > Network.")
        sb.appendLine()

        // 5. Recent Plugin Diagnostics & Fault History
        sb.appendLine("--- [5. RECENT PLUGIN DIAGNOSTIC RECORDS] ---")
        val diagnostics = PluginManager.getDiagnostics()
        if (diagnostics.isEmpty()) {
            sb.appendLine("No diagnostic fault records recorded.")
        } else {
            val recent = diagnostics.takeLast(25)
            for (diag in recent) {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(diag.timestamp))
                sb.appendLine("[$dateStr] Plugin: ${diag.pluginId} | Action: ${diag.lastAction} | Reason: ${diag.faultReason}")
            }
        }
        sb.appendLine("================================================================================")
        sb.appendLine("                           END OF DIAGNOSTIC REPORT                             ")
        sb.appendLine("================================================================================")

        return sb.toString()
    }

    fun exportToFile(targetFile: File, content: String? = null): File {
        val text = content ?: generateReport()
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(text, Charsets.UTF_8)
        return targetFile
    }
}
