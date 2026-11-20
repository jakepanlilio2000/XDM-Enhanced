package xdm.sample.ui;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.ui.SettingsPanelContributor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

public class SampleSettingsPanel implements SettingsPanelContributor {

    private JCheckBox chkNotifications;

    @Override
    public String getTitle() {
        return "UI & Alerts";
    }

    @Override
    public Component createPanel(PluginContext context) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("Sample UI Plugin Settings");
        titleLabel.setFont(titleLabel.getFont().deriveFont(14.0f));

        chkNotifications = new JCheckBox("Show desktop notifications on task events", true);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(chkNotifications);

        return panel;
    }

    @Override
    public void saveSettings() {
    }
}
