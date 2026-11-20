package xdm.sample.ui;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.ui.DownloadDetailsSectionContributor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

public class SampleDetailsSectionContributor implements DownloadDetailsSectionContributor {

    @Override
    public String getSectionId() {
        return "sample-details-section";
    }

    @Override
    public String getTitle() {
        return "Plugin Details";
    }

    @Override
    public Component createSection(DownloadInfo download, PluginContext context) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Plugin Task Inspection Section");
        title.setFont(title.getFont().deriveFont(12.0f));

        String url = download != null ? download.getUrl() : "N/A";
        String status = download != null ? download.getStatus() : "N/A";

        panel.add(title);
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel("Target URL: " + url));
        panel.add(Box.createVerticalStrut(4));
        panel.add(new JLabel("Host Status: " + status));

        return panel;
    }
}
