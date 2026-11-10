package xdm.plugin.ytdlp;

import xdm.plugin.api.NotificationLevel;
import xdm.plugin.api.PluginContext;
import xdm.plugin.api.XdmPlugin;
import xdm.plugin.api.commands.PluginCommand;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginAction;
import xdm.plugin.api.models.PluginDownloadRequest;
import xdm.plugin.api.ui.ContextMenuContributor;
import xdm.plugin.api.ui.ToolbarContributor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reference implementation of an optional media downloader plugin integrating yt-dlp into XDM Enhanced.
 */
public class YtDlpPlugin implements XdmPlugin {

    public static final String PLUGIN_ID = "ytdlp-downloader";

    private PluginContext context;
    private YtDlpConfig config;
    private YtDlpUrlRecognizer urlRecognizer;
    private YtDlpDownloadProvider downloadProvider;
    private YtDlpSettingsPanel settingsPanel;

    private final List<YtDlpDownloadTask> activeTasks = new CopyOnWriteArrayList<>();

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public void init(PluginContext context) {
        this.context = context;
        this.config = new YtDlpConfig(context.getConfig());
        this.urlRecognizer = new YtDlpUrlRecognizer();
        this.downloadProvider = new YtDlpDownloadProvider(context, config, urlRecognizer);
        this.settingsPanel = new YtDlpSettingsPanel(config);

        // 1. Register Download Provider & URL Recognizer
        context.registerDownloadProvider(downloadProvider);
        context.registerUrlRecognizer(urlRecognizer);

        // 2. Register Settings Panel
        context.registerSettingsPanel(settingsPanel);

        // 3. Register Toolbar Action
        context.registerToolbarContributor(new ToolbarContributor() {
            @Override
            public List<PluginAction> getToolbarActions() {
                PluginAction action = new PluginAction(
                        "ytdlp-action-toolbar",
                        "yt-dlp",
                        "Download media using yt-dlp",
                        null,
                        items -> promptDownloadUrl()
                );
                return Collections.singletonList(action);
            }
        });

        // 4. Register Context Menu Action
        context.registerContextMenuContributor(new ContextMenuContributor() {
            @Override
            public List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads) {
                if (selectedDownloads == null || selectedDownloads.isEmpty()) {
                    return Collections.emptyList();
                }
                DownloadInfo item = selectedDownloads.get(0);
                if (item.getUrl() != null && urlRecognizer.matches(item.getUrl())) {
                    PluginAction action = new PluginAction(
                            "ytdlp-action-context",
                            "Download with yt-dlp",
                            "Queue authorized media download with yt-dlp",
                            null,
                            downloads -> {
                                for (DownloadInfo dl : downloads) {
                                    if (dl.getUrl() != null && !dl.getUrl().trim().isEmpty()) {
                                        startDownload(dl.getUrl(), dl.getFileName());
                                    }
                                }
                            }
                    );
                    return Collections.singletonList(action);
                }
                return Collections.emptyList();
            }
        });

        // 5. Register Plugin Commands
        context.registerCommand(new PluginCommand() {
            @Override
            public String getName() {
                return "ytdlp-version";
            }

            @Override
            public String getDescription() {
                return "Verifies and reports the installed yt-dlp version.";
            }

            @Override
            public void execute(String[] args, PluginContext ctx) {
                YtDlpExecutableValidator.ValidationResult res = YtDlpExecutableValidator.validate(config.getExecutablePath());
                ctx.logInfo(res.toString());
            }
        });

        context.logInfo("yt-dlp Media Downloader Plugin initialized successfully.");
    }

    private void promptDownloadUrl() {
        SwingUtilities.invokeLater(() -> {
            String url = JOptionPane.showInputDialog(
                    null,
                    "Enter video or media URL for yt-dlp authorized download:",
                    "Download with yt-dlp",
                    JOptionPane.QUESTION_MESSAGE
            );
            if (url != null && !url.trim().isEmpty()) {
                startDownload(url.trim(), null);
            }
        });
    }

    public synchronized YtDlpDownloadTask startDownload(String url, String fileName) {
        PluginDownloadRequest req = new PluginDownloadRequest(
                url,
                config.getOutputDirectory(),
                fileName
        );
        try {
            YtDlpDownloadTask task = (YtDlpDownloadTask) downloadProvider.createDownloadTask(req);
            activeTasks.add(task);
            task.addProgressListener(event -> {
                if (event.getState() == YtDlpDownloadTask.TaskState.FINISHED) {
                    activeTasks.remove(task);
                    context.showNotification("yt-dlp Complete", "Media download finished: " + url, NotificationLevel.INFO);
                } else if (event.getState() == YtDlpDownloadTask.TaskState.FAILED) {
                    activeTasks.remove(task);
                    context.showNotification("yt-dlp Failed", "Download error: " + event.getMessage(), NotificationLevel.ERROR);
                } else if (event.getState() == YtDlpDownloadTask.TaskState.CANCELED) {
                    activeTasks.remove(task);
                }
            });
            task.start();
            context.logInfo("Started yt-dlp task for: " + url);
            return task;
        } catch (Exception e) {
            context.logError("Failed to initiate yt-dlp download: " + e.getMessage(), e);
            context.showNotification("yt-dlp Error", "Failed to start download: " + e.getMessage(), NotificationLevel.ERROR);
            return null;
        }
    }

    @Override
    public synchronized void shutdown() {
        for (YtDlpDownloadTask task : activeTasks) {
            try {
                task.cancel();
            } catch (Exception ignored) {}
        }
        activeTasks.clear();
        context.logInfo("yt-dlp Media Downloader Plugin shut down.");
    }

    public YtDlpConfig getConfig() {
        return config;
    }

    public YtDlpDownloadProvider getDownloadProvider() {
        return downloadProvider;
    }

    public YtDlpUrlRecognizer getUrlRecognizer() {
        return urlRecognizer;
    }

    public YtDlpSettingsPanel getSettingsPanel() {
        return settingsPanel;
    }
}
