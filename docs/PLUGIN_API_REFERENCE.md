# XDM Enhanced — Plugin API v2 Reference Specification

**Public Plugin API Version:** `2.0.0`  
**Host Runtime Compatibility:** `>=1.1.0`  
**Target Milestone:** `v1.1.0`  
**Maven Artifact Coordinates:**
```xml
<dependency>
    <groupId>xdm</groupId>
    <artifactId>xdm-plugin-api</artifactId>
    <version>1.1.0</version>
    <scope>provided</scope>
</dependency>
```

---

## 1. Core Lifecycle & Registration Interfaces

### `XdmPlugin`
The mandatory entry point interface for all in-process XDM plugins.

```java
package xdm.plugin.api;

public interface XdmPlugin {
    /**
     * Unique identifier matching plugin.json 'id'.
     */
    String getId();

    /**
     * Called when the plugin is activated.
     * Guaranteed to execute within a bounded timeout (default: 5000ms).
     *
     * @param context Active host context providing access to registration SPIs
     * @throws Exception If initialization fails (transitions plugin to FAULTED)
     */
    void init(PluginContext context) throws Exception;

    /**
     * Called when the plugin is disabled or uninstalled.
     * Must release background threads, cancel pending timers, and clean up resources.
     */
    void shutdown();
}
```

### `Registration`
Disposable handle returned by all `register*` methods in `PluginContext`. Allows modular unregistration at runtime without restarting the application.

```java
package xdm.plugin.api;

public interface Registration extends AutoCloseable {
    /**
     * Unregisters the contributed extension point from the host engine.
     */
    void unregister();

    /**
     * Alias for unregister() to support try-with-resources.
     */
    @Override
    default void close() {
        unregister();
    }
}
```

### `PluginContext`
The host interface provided to plugins during `init()`.

```java
package xdm.plugin.api;

import xdm.plugin.api.events.EventSubscription;
import xdm.plugin.api.events.PostDownloadHook;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginDownloadRequest;
import xdm.plugin.api.network.ProxyProvider;
import xdm.plugin.api.network.VpnStatusProvider;
import xdm.plugin.api.processors.PostDownloadProcessor;
import xdm.plugin.api.ui.*;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public interface PluginContext {
    // Identity & Storage
    PluginMetadata getMetadata();
    File getDataDirectory();
    PluginConfig getConfig();

    // UI Registration (Disposable Handles)
    Registration registerSettings(SettingsPanelContributor panel);
    Registration registerContextMenu(ContextMenuContributor contributor);
    Registration registerToolbar(ToolbarContributor contributor);
    Registration registerDashboardWidget(DashboardWidgetContributor widget);
    Registration registerDetailsSection(DownloadDetailsSectionContributor section);
    Registration registerStatusIndicator(StatusIndicatorContributor indicator);

    // Network & VPN Integration
    Registration registerProxyProvider(ProxyProvider provider);
    Registration registerVpnStatusProvider(VpnStatusProvider provider);

    // Automation & Pipeline
    Registration registerPostDownloadProcessor(PostDownloadProcessor processor);
    Registration registerMetadataEnricher(MetadataEnricher enricher);

    // Download Tasks & Recognition
    Registration registerDownloadProvider(DownloadProvider provider);
    Registration registerUrlRecognizer(UrlRecognizer recognizer);
    long submitPluginTask(PluginDownloadRequest request, PluginDownloadTask task);

    // Event Bus
    EventSubscription subscribeTopic(String topic, BiConsumer<String, Object> listener);
    void publishTopic(String topic, Object eventPayload);

    // Utilities & Logging
    void logInfo(String message);
    void logWarn(String message);
    void logError(String message, Throwable throwable);
    void showNotification(String title, String message, NotificationLevel level);
    PluginTheme getTheme();
    void runOnUIThread(Runnable runnable);
    <T> CompletableFuture<T> executeAsync(Supplier<T> task);
}
```

---

## 2. Download Provider & Task Adapter APIs

### `DownloadProvider`
Registers an external or protocol-specific download provider (e.g. `yt-dlp`, BitTorrent extension).

```java
package xdm.plugin.api;

import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginDownloadRequest;

public interface DownloadProvider {
    String getProviderId();
    String getName();
    int getPriority(); // Higher values evaluated earlier
    boolean canHandle(String url);
    DownloadTask createDownloadTask(PluginDownloadRequest request);
}
```

### `PluginDownloadTask`
Full lifecycle adapter allowing custom engines to report progress and receive user pause/resume commands.

```java
package xdm.plugin.api;

public interface PluginDownloadTask {
    void start(DownloadTaskListener listener);
    void pause();
    void resume();
    void cancel();
    boolean canPause();
    boolean canResume();
}
```

---

## 3. Scoped Network Proxy Integration API

### `ProxyProvider`
Discloses scoped network proxy configurations for designated downloads without modifying OS-wide proxy settings.

```java
package xdm.plugin.api.network;

import java.net.URI;
import java.util.Set;

public interface ProxyProvider {
    String getProviderId();
    String getDisplayName();
    int getPriority();
    Set<ProxyResolution.NetworkEngine> getApplicableEngines();

    /**
     * Resolves proxy routing for target URI.
     * Returns null if target URI is not handled by this provider.
     */
    ProxyResolution resolveProxy(URI targetUri, ProxyResolution.NetworkEngine engine);
}
```

### `ProxyResolution`
Defines protocol, endpoint address, credentials, and fail-closed security policies.

```java
package xdm.plugin.api.network;

public class ProxyResolution {
    public enum Type { DIRECT, HTTP, SOCKS4, SOCKS5, SYSTEM_DEFAULT, BLOCKED }
    public enum FallbackPolicy { FAIL_CLOSED, FAIL_OPEN }
    public enum NetworkEngine { HTTP, MEDIA, TORRENT, PLUGIN_TASK, UPDATE }

    public static ProxyResolution socks5(String host, int port, String user, String pass, FallbackPolicy policy);
    public static ProxyResolution http(String host, int port, String user, String pass, FallbackPolicy policy);
    public static ProxyResolution direct();
    public static ProxyResolution blocked(String reason);

    public String getSafeAddress(); // e.g. "127.0.0.1:1080"
    public String toRedactedString(); // Credentials masked as (user:***)
}
```

---

## 4. Non-Privileged VPN Client Telemetry API

### `VpnStatusProvider`
Provides external VPN client status, connection profiles, and disconnect event notifications.

```java
package xdm.plugin.api.network;

import java.util.List;

public interface VpnStatusProvider {
    String getProviderId();
    String getDisplayName();
    String getDescription();
    VpnClientCapabilities getCapabilities();
    List<VpnProfile> getAvailableProfiles();
    VpnStatus getCurrentStatus();

    boolean requestConnect(String profileId);
    boolean requestDisconnect();

    void addStateListener(VpnStateListener listener);
    void removeStateListener(VpnStateListener listener);
}
```

### `VpnStatus`
Separates UI state from real OS tunnel state.

```java
package xdm.plugin.api.network;

public class VpnStatus {
    public enum State { CONNECTED, CONNECTING, DISCONNECTED, ERROR, UNKNOWN }

    public static VpnStatus connected(String iface, String gateway, String ip, String profileId, boolean verified, String msg);
    public static VpnStatus disconnected(String reason);
    public static VpnStatus connecting(String profileId, String msg);

    public boolean isConnected();
    public boolean isTunnelVerified();
    public String getInterfaceName();
}
```

---

## 5. UI Enhancements & Theme Observation APIs

### `ContextMenuContributor` & `ToolbarContributor`
Modular context actions and toolbar buttons.

```java
package xdm.plugin.api.ui;

import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginAction;
import java.util.List;

public interface ContextMenuContributor {
    List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads);
}

public interface ToolbarContributor {
    List<PluginAction> getToolbarActions();
}
```

### `SettingsPanelContributor`
Embeds configuration forms inside the host Settings window.

```java
package xdm.plugin.api.ui;

import xdm.plugin.api.PluginContext;
import java.awt.Component;

public interface SettingsPanelContributor {
    String getTitle();
    Component createPanel(PluginContext context);
    void saveSettings();
}
```

### `DownloadDetailsSectionContributor` & `StatusIndicatorContributor`
Contributes tabs to download properties dialogs and status pills to the application status bar.

```java
package xdm.plugin.api.ui;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.models.DownloadInfo;
import java.awt.Component;

public interface DownloadDetailsSectionContributor {
    String getSectionId();
    String getTitle();
    default boolean isApplicable(DownloadInfo download) { return true; }
    Component createSection(DownloadInfo download, PluginContext context);
}

public interface StatusIndicatorContributor {
    String getIndicatorId();
    default String getTooltip() { return ""; }
    Component createIndicator(PluginContext context);
    default void updateIndicator() {}
}
```

### `PluginTheme` & `PluginThemeListener`
Enables components to observe active Light, Dark, or System FlatLaf skins.

```java
package xdm.plugin.api.ui;

import java.awt.Color;

public interface PluginTheme {
    boolean isDark();
    String getActiveSkinId();
    Color getAccentColor();
    Color getMutedColor();
    Color getBackgroundColor();
    Registration addThemeListener(PluginThemeListener listener);
}

@FunctionalInterface
public interface PluginThemeListener {
    void onThemeChanged(PluginTheme theme);
}
```

---

## 6. Automation & Processing Pipeline APIs

### `PostDownloadProcessor`
Sequenced post-completion transformation pipeline.

```java
package xdm.plugin.api.processors;

import xdm.plugin.api.models.DownloadInfo;
import java.io.File;

public interface PostDownloadProcessor {
    String getName();
    default int getOrder() { return 100; } // Low values run first
    boolean canProcess(DownloadInfo download, File downloadedFile);
    ProcessingResult process(DownloadInfo download, File downloadedFile) throws Exception;
}
```

### `ProcessingResult`
Non-destructive execution status.

```java
package xdm.plugin.api.processors;

import java.io.File;

public class ProcessingResult {
    public static ProcessingResult success(String message);
    public static ProcessingResult success(String message, File transformedFile);
    public static ProcessingResult skipped(String reason);
    public static ProcessingResult failure(String errorMessage, Throwable error);

    public boolean isSuccess();
    public File getOutputFile();
}
```

---

## 7. Typed Event Bus & Standard Topics

### Standard Event Topics (`EventTopics`)
```java
package xdm.plugin.api.events;

public final class EventTopics {
    public static final String DOWNLOAD_QUEUED     = "download.queued";
    public static final String DOWNLOAD_STARTED    = "download.started";
    public static final String DOWNLOAD_PROGRESS   = "download.progress";
    public static final String DOWNLOAD_PAUSED     = "download.paused";
    public static final String DOWNLOAD_COMPLETED  = "download.completed";
    public static final String DOWNLOAD_FAILED     = "download.failed";
    public static final String DOWNLOAD_CANCELED   = "download.canceled";
    public static final String SELECTION_CHANGED   = "ui.selection.changed";
    public static final String METADATA_RESOLVED   = "metadata.resolved";
    public static final String NETWORK_ONLINE      = "network.online";
    public static final String NETWORK_OFFLINE     = "network.offline";
    public static final String VPN_STATE_CHANGED   = "network.vpn.changed";
    public static final String APP_STARTUP         = "app.startup";
    public static final String APP_SHUTDOWN        = "app.shutdown";
    public static final String APP_SAFE_MODE       = "app.safemode";
    public static final String APP_STATE_CHANGED   = "app.state.changed";
}
```
