package xdm.plugin.api.models;

public enum PluginCapability {
    DOWNLOAD_PROVIDER("Download Provider"),
    URL_RECOGNITION("URL Recognition"),
    FORMAT_DETECTION("Format Detection"),
    POST_DOWNLOAD_HOOK("Post-Download Automation"),
    CONTEXT_MENU("Context Menu Actions"),
    TOOLBAR("Toolbar Actions"),
    SETTINGS_PANEL("Settings Interface"),
    COMMANDS("Command Registration");

    private final String displayName;

    PluginCapability(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

