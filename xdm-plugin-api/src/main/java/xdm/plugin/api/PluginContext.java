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

    /**
     * Submits a provider-managed download task to the central XDM Enhanced download engine.
     * The task is integrated into the active download list, card/table views, category sidebar,
     * progress windows, and persistence store.
     *
     * @param request Download destination, filename, and source request parameters
     * @param task Provider-managed active download task
     * @return Generated unique task ID, or -1 if the request could not be registered
     */
    default long submitPluginTask(PluginDownloadRequest request, xdm.plugin.api.download.PluginDownloadTask task) {
        return -1L;
    }

    /**
     * Subscribes a listener to typed event notifications dispatched on a named topic.
     *
     * @param topic Target event topic
     * @param listener Callback invoked when an event is dispatched
     */
    default <T> void subscribe(String topic, xdm.plugin.api.events.PluginEventListener<T> listener) {}

    /**
     * Unsubscribes an event listener from a named topic.
     *
     * @param topic Target event topic
     * @param listener Callback to remove
     */
    default <T> void unsubscribe(String topic, xdm.plugin.api.events.PluginEventListener<T> listener) {}

    /**
     * Dispatches a typed event notification to all listeners subscribed to the specified topic.
     *
     * @param topic Target event topic
     * @param eventData Event payload
     */
    default <T> void publish(String topic, T eventData) {}

    /**
     * Executes a background task asynchronously using the host application's daemon worker pool.
     *
     * @param task Callable task to execute
     * @return CompletableFuture representing the asynchronous computation
     */
    default <T> java.util.concurrent.CompletableFuture<T> executeAsync(java.util.concurrent.Callable<T> task) {
        java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
        try {
            future.complete(task.call());
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    /**
     * Dispatches a task to the UI Event Dispatch Thread (EDT) safely without blocking background threads.
     *
     * @param action Action to execute on the EDT
     */
    default void runOnUIThread(Runnable action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(action);
        }
    }
}

