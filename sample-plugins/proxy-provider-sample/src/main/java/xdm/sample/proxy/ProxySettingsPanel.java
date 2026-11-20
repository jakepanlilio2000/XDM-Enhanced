package xdm.sample.proxy;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.ui.SettingsPanelContributor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

public class ProxySettingsPanel implements SettingsPanelContributor {

    private JCheckBox chkEnabled;

    @Override
    public String getTitle() {
        return "Proxy Routing";
    }

    @Override
    public Component createPanel(PluginContext context) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("Sample Proxy Configuration");
        titleLabel.setFont(titleLabel.getFont().deriveFont(14.0f));

        JLabel infoLabel = new JLabel("Routes *.proxy-fixture.local domains through 127.0.0.1:1080 (SOCKS5/HTTP).");

        boolean enabled = context != null && context.getConfig() != null && context.getConfig().getBoolean("proxy.enabled", true);
        chkEnabled = new JCheckBox("Enable scoped proxy profile for test domains", enabled);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(infoLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(chkEnabled);

        return panel;
    }

    @Override
    public void saveSettings() {
        // Persist setting if context is available
    }
}
