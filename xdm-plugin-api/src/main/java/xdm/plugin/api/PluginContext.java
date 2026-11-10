package xdm.plugin.api;

import xdm.plugin.api.commands.PluginCommand;
import xdm.plugin.api.download.DownloadProvider;
import xdm.plugin.api.events.PostDownloadHook;
import xdm.plugin.api.format.FormatDetector;
import xdm.plugin.api.media.MediaProvider;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginDownloadRequest;
import xdm.plugin.api.models.PluginMetadata;
import xdm.plugin.api.ui.ContextMenuContributor;
import xdm.plugin.api.ui.SettingsPanelContributor;
import xdm.plugin.api.ui.ToolbarContributor;
import xdm.plugin.api.url.UrlRecognizer;

import java.io.File;
import java.util.List;

/**
 * Controlled environment provided to plugins by XDM Enhanced.
 * Does not expose mutable database or internal engine internals.
 */
public interface PluginContext {
    PluginMetadata getMetadata();
    File getDataDirectory();
    PluginConfig getConfig();

    void registerDownloadProvider(DownloadProvider provider);
    void registerMediaProvider(MediaProvider provider);
    void registerUrlRecognizer(UrlRecognizer recognizer);
    void registerFormatDetector(FormatDetector detector);
    void registerPostDownloadHook(PostDownloadHook hook);
    void registerContextMenuContributor(ContextMenuContributor contributor);
    void registerToolbarContributor(ToolbarContributor contributor);
    void registerSettingsPanel(SettingsPanelContributor panel);
    void registerCommand(PluginCommand command);

    void showNotification(String title, String message, NotificationLevel level);
    void logInfo(String message);
    void logWarn(String message);
    void logError(String message, Throwable throwable);

    String getApplicationVersion();
    String getApiVersion();

    List<DownloadInfo> getDownloads();
    void submitDownload(PluginDownloadRequest request);
}

