package xdm.sample.ui;

import xdm.plugin.api.NotificationLevel;
import xdm.plugin.api.PluginContext;
import xdm.plugin.api.models.PluginAction;
import xdm.plugin.api.ui.ToolbarContributor;

import java.util.Collections;
import java.util.List;

public class SampleToolbarContributor implements ToolbarContributor {

    private final PluginContext context;

    public SampleToolbarContributor(PluginContext context) {
        this.context = context;
    }

    @Override
    public List<PluginAction> getToolbarActions() {
        PluginAction action = new PluginAction(
            "sample-ui-toolbar-ping",
            "Plugin Status",
            "Pings the sample UI plugin status",
            null,
            downloads -> {
                if (context != null) {
                    context.showNotification(
                        "Sample UI Plugin",
                        "UI Plugin is active and responsive on the toolbar.",
                        NotificationLevel.INFO
                    );
                }
            }
        );
        return Collections.singletonList(action);
    }
}
