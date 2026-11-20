package xdm.plugin.api.events;

/**
 * Standardized topic constants for the XDM Enhanced plugin event bus.
 */
public final class EventTopics {

    private EventTopics() {}

    // Download task lifecycle events
    public static final String DOWNLOAD_ADDED = "download.added";
    public static final String DOWNLOAD_QUEUED = "download.queued";
    public static final String DOWNLOAD_STARTED = "download.started";
    public static final String DOWNLOAD_PROGRESS = "download.progress";
    public static final String DOWNLOAD_PAUSED = "download.paused";
    public static final String DOWNLOAD_COMPLETED = "download.completed";
    public static final String DOWNLOAD_FAILED = "download.failed";
    public static final String DOWNLOAD_DELETED = "download.deleted";
    public static final String DOWNLOAD_CANCELED = "download.canceled";

    // UI and interaction events
    public static final String SELECTION_CHANGED = "ui.selection.changed";

    // Metadata and enrichment events
    public static final String METADATA_RESOLVED = "metadata.resolved";

    // Network and connectivity events
    public static final String NETWORK_ONLINE = "network.online";
    public static final String NETWORK_OFFLINE = "network.offline";
    public static final String VPN_STATE_CHANGED = "network.vpn.changed";

    // Application state events
    public static final String APP_STARTUP = "app.startup";
    public static final String APP_SHUTDOWN = "app.shutdown";
    public static final String APP_SAFE_MODE = "app.safemode";
    public static final String APP_STATE_CHANGED = "app.state.changed";
    public static final String SYSTEM_SHUTDOWN = "system.shutdown";
    public static final String SYSTEM_IDLE = "system.idle";

    // Automation rule events
    public static final String AUTOMATION_TRIGGERED = "automation.triggered";
}
