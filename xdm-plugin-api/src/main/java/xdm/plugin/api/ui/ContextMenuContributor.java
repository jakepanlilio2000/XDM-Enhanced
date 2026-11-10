package xdm.plugin.api.ui;

import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginAction;
import java.util.List;

public interface ContextMenuContributor {
    List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads);
}

