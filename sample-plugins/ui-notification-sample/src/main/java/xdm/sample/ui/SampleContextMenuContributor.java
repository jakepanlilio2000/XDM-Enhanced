package xdm.sample.ui;

import xdm.plugin.api.NotificationLevel;
import xdm.plugin.api.PluginContext;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginAction;
import xdm.plugin.api.ui.ContextMenuContributor;

import java.util.Collections;
import java.util.List;

public class SampleContextMenuContributor implements ContextMenuContributor {

    private final PluginContext context;

    public SampleContextMenuContributor(PluginContext context) {
        this.context = context;
    }

    @Override
    public List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads) {
        if (selectedDownloads == null || selectedDownloads.isEmpty()) {
            return Collections.emptyList();
        }

        PluginAction action = new PluginAction(
            "sample-ui-inspect",
            "Inspect with Sample Plugin",
            "Displays summary of selected download tasks",
            null,
            downloads -> {
                if (context != null) {
                    context.showNotification(
                        "Task Inspection",
                        "Inspected " + downloads.size() + " download(s). First: " + downloads.get(0).getFileName(),
                        NotificationLevel.INFO
                    );
                }
            }
        );

        return Collections.singletonList(action);
    }
}
