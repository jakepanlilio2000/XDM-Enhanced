package xdm.sample.ui;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.ui.StatusIndicatorContributor;

import javax.swing.JLabel;
import java.awt.Component;

public class SampleStatusIndicatorContributor implements StatusIndicatorContributor {

    @Override
    public String getIndicatorId() {
        return "sample-status-pill";
    }

    @Override
    public Component createIndicator(PluginContext context) {
        return new JLabel("[UI Plugin Active]");
    }

    @Override
    public void updateIndicator() {
        // Can dynamically update status text or metrics
    }
}
