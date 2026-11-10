package xdm.sample.plugin;

import xdm.plugin.api.NotificationLevel;
import xdm.plugin.api.PluginContext;
import xdm.plugin.api.XdmPlugin;
import xdm.plugin.api.events.PostDownloadHook;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginAction;
import xdm.plugin.api.ui.ContextMenuContributor;
import xdm.plugin.api.ui.SettingsPanelContributor;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

public class ChecksumVerifierPlugin implements XdmPlugin, PostDownloadHook, ContextMenuContributor, SettingsPanelContributor {
    private PluginContext context;
    private boolean autoVerifyOnComplete = true;
    private JCheckBox chkAutoVerify;

    @Override
    public String getId() {
        return "checksum-verifier";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;
        this.autoVerifyOnComplete = context.getConfig().getBoolean("autoVerifyOnComplete", true);

        context.registerPostDownloadHook(this);
        context.registerContextMenuContributor(this);
        context.registerSettingsPanel(this);

        context.logInfo("Checksum Verifier Plugin initialized successfully.");
    }

    @Override
    public void shutdown() {
        if (context != null) {
            context.logInfo("Checksum Verifier Plugin shut down.");
        }
    }

    @Override
    public void onDownloadCompleted(DownloadInfo download) {
        if (!autoVerifyOnComplete) return;

        File file = new File(download.getDestinationPath());
        if (!file.exists() || !file.isFile()) return;

        try {
            String sha256 = calculateSha256(file);
            context.logInfo("Download completed: " + file.getName() + " [SHA-256: " + sha256 + "]");
            String prefix = sha256.length() >= 16 ? sha256.substring(0, 16) : sha256;
            context.showNotification("Checksum Verified", file.getName() + "\nSHA-256: " + prefix + "...", NotificationLevel.INFO);
        } catch (Exception e) {
            context.logError("Failed to calculate checksum for " + file.getName(), e);
        }
    }

    @Override
    public List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads) {
        if (selectedDownloads == null || selectedDownloads.isEmpty()) {
            return Collections.emptyList();
        }

        PluginAction action = new PluginAction(
            "verify-checksum",
            "Calculate SHA-256 Checksum",
            "Calculates the SHA-256 checksum for the selected file",
            null,
            downloads -> {
                for (DownloadInfo d : downloads) {
                    File file = new File(d.getDestinationPath());
                    if (file.exists() && file.isFile()) {
                        try {
                            String sha = calculateSha256(file);
                            context.showNotification("SHA-256 Checksum", file.getName() + ":\n" + sha, NotificationLevel.INFO);
                        } catch (Exception e) {
                            context.logError("Checksum calculation failed", e);
                        }
                    }
                }
            }
        );

        return Collections.singletonList(action);
    }

    @Override
    public String getTitle() {
        return "Checksum Verifier Options";
    }

    @Override
    public Component createPanel(PluginContext context) {
        JPanel panel = new JPanel(new BorderLayout());
        chkAutoVerify = new JCheckBox("Automatically calculate SHA-256 on completed downloads", autoVerifyOnComplete);
        panel.add(chkAutoVerify, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public void saveSettings() {
        if (chkAutoVerify != null && context != null) {
            autoVerifyOnComplete = chkAutoVerify.isSelected();
            context.getConfig().setBoolean("autoVerifyOnComplete", autoVerifyOnComplete);
            context.getConfig().save();
        }
    }

    public static String calculateSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

