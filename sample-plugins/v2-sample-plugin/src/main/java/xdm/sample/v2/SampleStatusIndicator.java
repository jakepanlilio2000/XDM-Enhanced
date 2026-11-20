package xdm.sample.v2;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.ui.PluginTheme;
import xdm.plugin.api.ui.StatusIndicatorContributor;

import javax.swing.JLabel;
import java.awt.Component;

/**
 * Contributes a custom telemetry status pill to the application status bar.
 */
public class SampleStatusIndicator implements StatusIndicatorContributor {

    private JLabel label;

    @Override
    public String getIndicatorId() {
        return "v2-sample-status-pill";
    }

    @Override
    public String getTooltip() {
        return "v2 Sample Plugin: Status Indicator & Metrics";
    }

    @Override
    public Component createIndicator(PluginContext context) {
        label = new JLabel("● v2 Active");
        PluginTheme theme = context != null ? context.getTheme() : null;
        if (theme != null) {
            label.setForeground(theme.getAccentColor());
            if (theme.getBaseFont() != null) {
                label.setFont(theme.getBaseFont().deriveFont(11.0f));
            }
        }
        return label;
    }

    @Override
    public void updateIndicator() {
        if (label != null) {
            label.setText("● v2 Active");
        }
    }
}
