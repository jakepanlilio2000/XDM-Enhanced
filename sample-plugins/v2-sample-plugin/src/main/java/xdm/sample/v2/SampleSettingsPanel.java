package xdm.sample.v2;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.ui.PluginTheme;
import xdm.plugin.api.ui.SettingsPanelContributor;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Contributes a custom settings configuration panel for the v2 sample plugin.
 */
public class SampleSettingsPanel implements SettingsPanelContributor {

    @Override
    public String getTitle() {
        return "v2 Processor Settings";
    }

    @Override
    public Component createPanel(PluginContext context) {
        JPanel panel = new JPanel(new BorderLayout());
        PluginTheme theme = context != null ? context.getTheme() : null;

        JLabel titleLabel = new JLabel("v2 Sample Processor Configuration");
        titleLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        if (theme != null) {
            titleLabel.setForeground(theme.getAccentColor());
            if (theme.getBaseFont() != null) {
                titleLabel.setFont(theme.getBaseFont().deriveFont(java.awt.Font.BOLD, 13.0f));
            }
        }

        panel.add(titleLabel, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public void saveSettings() {
        // Persist plugin configuration
    }
}
