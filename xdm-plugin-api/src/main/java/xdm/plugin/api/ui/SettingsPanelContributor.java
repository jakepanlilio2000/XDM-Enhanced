package xdm.plugin.api.ui;

import xdm.plugin.api.PluginContext;
import java.awt.Component;

public interface SettingsPanelContributor {
    String getTitle();
    Component createPanel(PluginContext context);
    void saveSettings();
}

