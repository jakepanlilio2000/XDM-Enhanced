package xdm.plugin.api.models;

public enum PluginCapability {
    DOWNLOAD_PROVIDER("Download Provider"),
    URL_RECOGNITION("URL Recognition"),
    FORMAT_DETECTION("Format Detection"),
    POST_DOWNLOAD_HOOK("Post-Download Automation"),
    POST_DOWNLOAD_PROCESSOR("Post-Download Processing Pipeline"),
    NETWORK_PROXY("Network Proxy Provider"),
    VPN_STATUS("VPN Status & Telemetry"),
    CONTEXT_MENU("Context Menu Actions"),
    TOOLBAR("Toolbar Actions"),
    SETTINGS_PANEL("Settings Interface"),
    DASHBOARD_WIDGET("Dashboard Status Widget"),
    COMMANDS("Command Registration"),
    MEDIA_METADATA("Media Metadata Provider"),
    TASK_ADAPTER("Task Adapter & Progress"),
    EVENT_BUS("Event Bus & Messaging"),
    DETAILS_SECTION("Download Details Section"),
    STATUS_INDICATOR("Status Bar Indicator"),
    METADATA_ENRICHER("Metadata Enricher & Suggestions"),
    AUTOMATION_ACTION("Automation Rules & Actions");

    private final String displayName;

    PluginCapability(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Normalizes and parses capability identifiers, accommodating legacy aliases.
     *
     * @param rawName Raw string from plugin.json
     * @return Resolved PluginCapability, or null if unknown
     */
    public static PluginCapability parse(String rawName) {
        if (rawName == null) return null;
        String clean = rawName.trim().toUpperCase();
        switch (clean) {
            case "URL_RECOGNIZER":
                return URL_RECOGNITION;
            case "TOOLBAR_ACTION":
                return TOOLBAR;
            case "COMMAND":
                return COMMANDS;
            case "POST_DOWNLOAD":
                return POST_DOWNLOAD_HOOK;
            case "METADATA_ENRICHMENT":
            case "ENRICHER":
                return METADATA_ENRICHER;
            case "AUTOMATION":
                return AUTOMATION_ACTION;
            default:
                try {
                    return PluginCapability.valueOf(clean);
                } catch (IllegalArgumentException e) {
                    return null;
                }
        }
    }
}
