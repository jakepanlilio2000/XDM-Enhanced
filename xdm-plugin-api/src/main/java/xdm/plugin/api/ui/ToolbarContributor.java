package xdm.plugin.api.ui;

import xdm.plugin.api.models.PluginAction;
import java.util.List;

public interface ToolbarContributor {
    List<PluginAction> getToolbarActions();
}

