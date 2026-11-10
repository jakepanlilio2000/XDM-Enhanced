package xdm.plugin.api.models;

import java.util.List;
import java.util.function.Consumer;

public class PluginAction {
    private final String id;
    private final String label;
    private final String tooltip;
    private final String iconName;
    private final Consumer<List<DownloadInfo>> handler;

    public PluginAction(String id, String label, String tooltip, String iconName, Consumer<List<DownloadInfo>> handler) {
        this.id = id;
        this.label = label;
        this.tooltip = tooltip;
        this.iconName = iconName;
        this.handler = handler;
    }

    public PluginAction(String id, String label, Consumer<List<DownloadInfo>> handler) {
        this(id, label, null, null, handler);
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getTooltip() { return tooltip; }
    public String getIconName() { return iconName; }
    public Consumer<List<DownloadInfo>> getHandler() { return handler; }
}

