package xdm.plugin.api;

import xdm.plugin.api.commands.PluginCommand;
import xdm.plugin.api.download.DownloadProvider;
import xdm.plugin.api.events.EventSubscription;
import xdm.plugin.api.events.PluginEventListener;
import xdm.plugin.api.events.PostDownloadHook;
import xdm.plugin.api.format.FormatDetector;
import xdm.plugin.api.media.MediaProvider;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginDownloadRequest;
import xdm.plugin.api.models.PluginMetadata;
import xdm.plugin.api.network.ProxyProvider;
import xdm.plugin.api.network.VpnStatusProvider;
import xdm.plugin.api.processors.PostDownloadProcessor;
import xdm.plugin.api.ui.ContextMenuContributor;
import xdm.plugin.api.ui.DashboardWidgetContributor;
import xdm.plugin.api.ui.DownloadDetailsSectionContributor;
import xdm.plugin.api.ui.PluginTheme;
import xdm.plugin.api.ui.SettingsPanelContributor;
import xdm.plugin.api.ui.StatusIndicatorContributor;
import xdm.plugin.api.ui.ToolbarContributor;
import xdm.plugin.api.url.UrlRecognizer;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

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

    /**
     * Registers a context menu contributor and returns a disposable registration handle.
     */
    default Registration registerContextMenu(ContextMenuContributor contributor) {
        registerContextMenuContributor(contributor);
        return () -> unregisterContextMenuContributor(contributor);
    }

    /**
     * Unregisters a previously registered context menu contributor.
     */
    default void unregisterContextMenuContributor(ContextMenuContributor contributor) {}

    /**
     * Registers a toolbar contributor and returns a disposable registration handle.
     */
    default Registration registerToolbar(ToolbarContributor contributor) {
        registerToolbarContributor(contributor);
        return () -> unregisterToolbarContributor(contributor);
    }

    /**
     * Unregisters a previously registered toolbar contributor.
     */
    default void unregisterToolbarContributor(ToolbarContributor contributor) {}

    /**
     * Registers a settings panel contributor and returns a disposable registration handle.
     */
    default Registration registerSettings(SettingsPanelContributor panel) {
        registerSettingsPanel(panel);
        return () -> unregisterSettingsPanel(panel);
    }

    /**
     * Unregisters a previously registered settings panel contributor.
     */
    default void unregisterSettingsPanel(SettingsPanelContributor panel) {}

    /**
     * Registers a custom details section or tab for download inspection dialogs.
     */
    default Registration registerDetailsSection(DownloadDetailsSectionContributor section) {
        return () -> {};
    }

    /**
     * Registers a custom status indicator or pill for the application status bar.
     */
    default Registration registerStatusIndicator(StatusIndicatorContributor indicator) {
        return () -> {};
    }

    /**
     * Returns the application theme information and appearance scaling metrics.
     */
    default PluginTheme getTheme() {
        return null;
    }

    /**
     * Registers a custom proxy provider for dynamic per-URI routing.
     */
    default Registration registerProxyProvider(ProxyProvider provider) {
        return () -> {};
    }

    /**
     * Resolves proxy for a given URI and engine using registered providers and app configuration.
     */
    default xdm.plugin.api.network.ProxyResolution resolveProxy(java.net.URI targetUri, xdm.plugin.api.network.ProxyResolution.NetworkEngine engine) {
        return null;
    }

    default xdm.plugin.api.network.ProxyResolution resolveProxy(java.net.URI targetUri) {
        return resolveProxy(targetUri, xdm.plugin.api.network.ProxyResolution.NetworkEngine.HTTP);
    }

    /**
     * Registers a custom VPN status and telemetry provider.
     */
    default Registration registerVpnStatusProvider(VpnStatusProvider provider) {
        return () -> {};
    }

    /**
     * Checks whether the host application's VPN prerequisite policy is satisfied.
     * Returns true if no VPN prerequisite is required or if the required VPN is connected.
     */
    default boolean isVpnPrerequisiteMet() {
        return true;
    }

    /**
     * Returns the current snapshot of external VPN client status.
     */
    default xdm.plugin.api.network.VpnStatus getVpnStatus() {
        return xdm.plugin.api.network.VpnStatus.disconnected();
    }

    /**
     * Registers a post-download pipeline processor.
     */
    default Registration registerPostDownloadProcessor(PostDownloadProcessor processor) {
        return () -> {};
    }

    /**
     * Registers a custom dashboard telemetry widget.
     */
    default Registration registerDashboardWidget(DashboardWidgetContributor widget) {
        return () -> {};
    }

    /**
     * Registers a metadata enricher for suggested filenames and categories.
     * User's explicit manual decisions always supersede enricher suggestions.
     */
    default Registration registerMetadataEnricher(xdm.plugin.api.enrichment.MetadataEnricher enricher) {
        return () -> {};
    }

    void showNotification(String title, String message, NotificationLevel level);

    /**
     * Posts a system or in-app notification with an optional click callback.
     *
     * @param title Title of the notification
     * @param message Detailed body message
     * @param level Severity level (INFO, WARNING, ERROR)
     * @param onClick Optional callback invoked when the user clicks the notification
     */
    default void showNotification(String title, String message, NotificationLevel level, Runnable onClick) {
        showNotification(title, message, level);
    }
    void logInfo(String message);
    void logWarn(String message);
    void logError(String message, Throwable throwable);

    String getApplicationVersion();
    String getApiVersion();

    List<DownloadInfo> getDownloads();
    void submitDownload(PluginDownloadRequest request);

    /**
     * Prompts the user to confirm creation of a download using the standard host application
     * confirmation dialog. Allows the user to inspect/customize destination folder, file name,
     * category, and start behavior (start now or queue for later).
     *
     * @param request Suggested download parameters including source URL and optional target folder/filename
     */
    default void createDownloadWithConfirmation(PluginDownloadRequest request) {}

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
    default <T> void subscribe(String topic, PluginEventListener<T> listener) {}

    /**
     * Subscribes a listener to typed event notifications and returns a disposable EventSubscription.
     */
    default <T> EventSubscription subscribeTopic(String topic, PluginEventListener<T> listener) {
        subscribe(topic, listener);
        return new EventSubscription() {
            private volatile boolean active = true;

            @Override
            public String getTopic() {
                return topic;
            }

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public void unregister() {
                active = false;
                unsubscribe(topic, listener);
            }
        };
    }

    /**
     * Unsubscribes an event listener from a named topic.
     *
     * @param topic Target event topic
     * @param listener Callback to remove
     */
    default <T> void unsubscribe(String topic, PluginEventListener<T> listener) {}

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
    default <T> CompletableFuture<T> executeAsync(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
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
