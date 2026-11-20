package xdm.sample.v2;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.ui.DownloadDetailsSectionContributor;
import xdm.plugin.api.ui.PluginTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Contributes a custom "Plugin Metadata" tab to download inspection dialogs.
 */
public class SampleDetailsSection implements DownloadDetailsSectionContributor {

    @Override
    public String getSectionId() {
        return "v2-sample-metadata-section";
    }

    @Override
    public String getTitle() {
        return "Plugin Details";
    }

    @Override
    public boolean isApplicable(DownloadInfo download) {
        return download != null;
    }

    @Override
    public Component createSection(DownloadInfo download, PluginContext context) {
        JPanel panel = new JPanel(new BorderLayout());
        PluginTheme theme = context != null ? context.getTheme() : null;

        String themeInfo = theme != null ? " [Theme: " + (theme.isDark() ? "Dark" : "Light") + ", Skin: " + theme.getActiveSkinId() + "]" : "";
        JLabel label = new JLabel("Target: " + download.getFileName() + " (" + download.getTotalSize() + " bytes)" + themeInfo);
        label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        if (theme != null) {
            label.setForeground(theme.getAccentColor());
        }

        panel.add(label, BorderLayout.NORTH);
        return panel;
    }
}
