package xdm.app.plugins

import xdm.plugin.api.models.PluginCapability

/**
 * Metadata and technical requirements specification for plugin capabilities.
 * Clearly articulates external tool requirements, OS support constraints,
 * user consent requirements, and application restart behavior.
 */
data class PluginCapabilityInfo(
    val key: String,
    val displayName: String,
    val category: String,
    val description: String,
    val externalTools: String,
    val osSupport: String,
    val consentRequired: String,
    val restartRequired: Boolean = false
) {
    fun toHtmlTooltip(): String = """
        <html>
        <body style="width: 260px; font-family: sans-serif; font-size: 11px;">
            <b>$displayName</b> ($category)<br/>
            <p style="margin-top: 4px; margin-bottom: 6px;">$description</p>
            <b>External Tools:</b> $externalTools<br/>
            <b>OS Support:</b> $osSupport<br/>
            <b>User Consent:</b> $consentRequired<br/>
            <b>Restart Required:</b> ${if (restartRequired) "Yes" else "No (Dynamic)"}
        </body>
        </html>
    """.trimIndent()

    fun toFormattedText(): String = """
        Capability: $displayName [$key]
        Category  : $category
        Summary   : $description
        Tools     : $externalTools
        OS Support: $osSupport
        Consent   : $consentRequired
        Restart   : ${if (restartRequired) "Required" else "Not required (Dynamic registration)"}
    """.trimIndent()

    companion object {
        private val CAPABILITIES_MAP: Map<String, PluginCapabilityInfo> = mapOf(
            "DOWNLOAD_PROVIDER" to PluginCapabilityInfo(
                key = "DOWNLOAD_PROVIDER",
                displayName = "Download Provider",
                category = "Download Engine",
                description = "Registers custom download execution providers to handle specific streaming protocols, media sites, or custom engines.",
                externalTools = "May require external CLI binaries (e.g. yt-dlp, ffmpeg) on system PATH or configured in settings.",
                osSupport = "Cross-platform desktop (Windows, Linux, macOS).",
                consentRequired = "Explicit user trust required to route tasks through third-party provider.",
                restartRequired = false
            ),
            "URL_RECOGNITION" to PluginCapabilityInfo(
                key = "URL_RECOGNITION",
                displayName = "URL Recognition",
                category = "Preflight & Capturing",
                description = "Inspects clipboard URLs and browser link captures to suggest file categories or specialized handlers.",
                externalTools = "None.",
                osSupport = "Cross-platform in-process regex/URL pattern matching.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "FORMAT_DETECTION" to PluginCapabilityInfo(
                key = "FORMAT_DETECTION",
                displayName = "Format Detection",
                category = "Media Parsing",
                description = "Inspects media streams and container metadata for video and audio format resolution.",
                externalTools = "Optional media analysis utilities.",
                osSupport = "Cross-platform JVM.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "POST_DOWNLOAD_HOOK" to PluginCapabilityInfo(
                key = "POST_DOWNLOAD_HOOK",
                displayName = "Post-Download Hook",
                category = "Automation",
                description = "Performs non-destructive post-download notifications, file tagging, or automated actions upon task completion.",
                externalTools = "None for in-process hooks; user-approved external scripts if configured.",
                osSupport = "Cross-platform file system.",
                consentRequired = "Explicit user approval required for mutating filesystem actions.",
                restartRequired = false
            ),
            "POST_DOWNLOAD_PROCESSOR" to PluginCapabilityInfo(
                key = "POST_DOWNLOAD_PROCESSOR",
                displayName = "Post-Download Processor",
                category = "Automation Pipeline",
                description = "Executes sequential file transformation pipelines (e.g. checksum verification, metadata extraction, archive unpacking).",
                externalTools = "Depends on processor; external processes require explicit user enablement.",
                osSupport = "Path-contained to downloaded file directory.",
                consentRequired = "User consent required for file transformation operations.",
                restartRequired = false
            ),
            "NETWORK_PROXY" to PluginCapabilityInfo(
                key = "NETWORK_PROXY",
                displayName = "Network Proxy Provider",
                category = "Network & Routing",
                description = "Provides SOCKS4, SOCKS5, or HTTP proxy routing profiles for supported XDM download engines.",
                externalTools = "None required; requires reachable proxy endpoint.",
                osSupport = "Cross-platform; strictly scoped to supported downloads, never modifies OS-wide proxies.",
                consentRequired = "Explicit user profile approval in Settings > Network.",
                restartRequired = false
            ),
            "VPN_STATUS" to PluginCapabilityInfo(
                key = "VPN_STATUS",
                displayName = "VPN Status & Prerequisite",
                category = "Network Tunnel Telemetry",
                description = "Discovers external VPN client status (e.g. WireGuard, OpenVPN, Tailscale) and supports pause-on-disconnect download policies.",
                externalTools = "Requires supported external VPN client installed and configured on the OS.",
                osSupport = "OS network interface detection (tun/tap/wintun/wireguard).",
                consentRequired = "Explicit user selection of VPN prerequisite policy.",
                restartRequired = false
            ),
            "CONTEXT_MENU" to PluginCapabilityInfo(
                key = "CONTEXT_MENU",
                displayName = "Context Menu Actions",
                category = "User Interface",
                description = "Adds context menu actions to the download list and task details views.",
                externalTools = "None.",
                osSupport = "Swing desktop UI; theme and skin-aware.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "TOOLBAR" to PluginCapabilityInfo(
                key = "TOOLBAR",
                displayName = "Toolbar Actions",
                category = "User Interface",
                description = "Adds interactive action buttons and dropdowns to the primary application toolbar.",
                externalTools = "None.",
                osSupport = "Swing desktop UI; theme and skin-aware.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "SETTINGS_PANEL" to PluginCapabilityInfo(
                key = "SETTINGS_PANEL",
                displayName = "Settings Interface",
                category = "Configuration",
                description = "Provides dedicated configuration panels embedded inside the Settings window.",
                externalTools = "None.",
                osSupport = "Swing desktop UI; theme and skin-aware.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "DASHBOARD_WIDGET" to PluginCapabilityInfo(
                key = "DASHBOARD_WIDGET",
                displayName = "Dashboard Widget",
                category = "User Interface",
                description = "Contributes visual dashboard widgets and summary cards to the application home screen.",
                externalTools = "None.",
                osSupport = "Swing desktop UI.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "COMMANDS" to PluginCapabilityInfo(
                key = "COMMANDS",
                displayName = "Plugin Commands",
                category = "Extensibility",
                description = "Registers invocable plugin commands for automation scripts or user shortcuts.",
                externalTools = "None.",
                osSupport = "In-process JVM.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "EVENT_BUS" to PluginCapabilityInfo(
                key = "EVENT_BUS",
                displayName = "Event Bus & Messaging",
                category = "Event Notification",
                description = "Publishes and subscribes to typed application lifecycle and task events.",
                externalTools = "None.",
                osSupport = "Pure in-process JVM asynchronous event queue.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "DETAILS_SECTION" to PluginCapabilityInfo(
                key = "DETAILS_SECTION",
                displayName = "Download Details Section",
                category = "User Interface",
                description = "Adds custom tab sections to download properties and task inspection dialogs.",
                externalTools = "None.",
                osSupport = "Swing desktop UI.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "STATUS_INDICATOR" to PluginCapabilityInfo(
                key = "STATUS_INDICATOR",
                displayName = "Status Bar Indicator",
                category = "User Interface",
                description = "Contributes status items and telemetry indicators to the bottom status bar.",
                externalTools = "None.",
                osSupport = "Swing desktop UI.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "METADATA_ENRICHER" to PluginCapabilityInfo(
                key = "METADATA_ENRICHER",
                displayName = "Metadata Enricher",
                category = "Metadata & Naming",
                description = "Suggests filenames, categories, and tags based on URL and media stream inspection.",
                externalTools = "None.",
                osSupport = "In-process string and URL processing.",
                consentRequired = "Standard manifest declaration; user choices always take precedence.",
                restartRequired = false
            ),
            "AUTOMATION_ACTION" to PluginCapabilityInfo(
                key = "AUTOMATION_ACTION",
                displayName = "Automation Actions",
                category = "Automation Rules",
                description = "Provides user-configurable automation action targets executed when trigger conditions are met.",
                externalTools = "External process actions require explicit user enablement.",
                osSupport = "Cross-platform OS.",
                consentRequired = "Explicit user configuration in Automation Rules.",
                restartRequired = false
            ),
            "MEDIA_METADATA" to PluginCapabilityInfo(
                key = "MEDIA_METADATA",
                displayName = "Media Metadata Provider",
                category = "Media Parsing",
                description = "Extracts media stream titles, formats, thumbnails, and chapter structures prior to downloading.",
                externalTools = "Optional media analysis utilities.",
                osSupport = "Cross-platform JVM.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            ),
            "TASK_ADAPTER" to PluginCapabilityInfo(
                key = "TASK_ADAPTER",
                displayName = "Task Adapter & Engine",
                category = "Download Engine",
                description = "Implements custom download execution logic, multi-segment connections, and custom progress reporting.",
                externalTools = "None; pure Java or protocol implementation.",
                osSupport = "Cross-platform JVM.",
                consentRequired = "Explicit user trust required to execute download tasks.",
                restartRequired = false
            )
        )

        fun forCapability(cap: String): PluginCapabilityInfo {
            val normalized = cap.trim().uppercase()
            return CAPABILITIES_MAP[normalized] ?: PluginCapabilityInfo(
                key = normalized,
                displayName = normalized.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                category = "General Capability",
                description = "Declared extension facet: $normalized",
                externalTools = "None known.",
                osSupport = "Cross-platform desktop.",
                consentRequired = "Standard manifest declaration.",
                restartRequired = false
            )
        }

        fun allKnownCapabilities(): List<PluginCapabilityInfo> = CAPABILITIES_MAP.values.toList()
    }
}
