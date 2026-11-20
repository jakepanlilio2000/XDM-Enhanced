package xdm.sample.vpn;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.ui.SettingsPanelContributor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

public class VpnSettingsPanel implements SettingsPanelContributor {

    @Override
    public String getTitle() {
        return "VPN Telemetry";
    }

    @Override
    public Component createPanel(PluginContext context) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("Sample External VPN Client Telemetry");
        titleLabel.setFont(titleLabel.getFont().deriveFont(14.0f));

        JLabel infoLabel = new JLabel("Reports connection status, profile metadata, and interface metrics from external client.");
        JLabel interfaceLabel = new JLabel("Virtual Network Interface: wg0 (Detected)");

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(infoLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(interfaceLabel);

        return panel;
    }

    @Override
    public void saveSettings() {
    }
}
