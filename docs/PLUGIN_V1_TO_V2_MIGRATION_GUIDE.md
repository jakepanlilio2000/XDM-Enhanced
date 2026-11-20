# XDM Enhanced — Plugin SDK v1 to v2 Migration Guide

This guide provides an honest, step-by-step roadmap for developers updating plugins written for the initial plugin API (v1.0.0) to the modernized **Plugin SDK v2 (API 2.0.0 / Host Runtime 1.1.0)**.

---

## 1. Executive Summary & Compatibility Policy

### Backward Compatibility Guarantee
Legacy v1 plugins **continue to function without modification**. The host application (`xdm-app`) provides runtime adapters and default interface methods for all v1 APIs. You are **not required** to migrate immediately unless you wish to take advantage of new SDK v2 capabilities (such as scoped SOCKS5 proxies, VPN telemetry, task adapters, or disposable UI registrations).

### Version Numbering Explained
- **Public Plugin API Specification:** `2.0.0` (tracks semantic changes in `xdm-plugin-api`).
- **Host Application Runtime API:** `1.1.0` (the minimum API runtime provided by XDM Enhanced `v1.1.0-beta.1`).
- **Application Release Version:** `v1.1.0-beta.1` (the host desktop application milestone).

---

## 2. Key Differences at a Glance

| Feature / Extension Point | SDK v1.0.0 | SDK v2.0.0 | Migration Action |
|---|---|---|---|
| **Registration Handles** | `void context.registerX(...)` | Returns `Registration` handle | Store handles and invoke `reg.unregister()` in `shutdown()` |
| **Download Tasks** | `submitDownload(request)` (limited HTTP) | `submitPluginTask(request, task)` (visible task, pause/resume, progress) | Implement `PluginDownloadTask` for custom engines |
| **Events & Messaging** | `context.subscribe(topic, listener)` (untyped) | `context.subscribeTopic(topic, biConsumer)` returning `EventSubscription` | Use `EventTopics` constants and close subscription in `shutdown()` |
| **Post-Download Actions** | `PostDownloadHook.onDownloadCompleted(d)` | `PostDownloadProcessor` pipeline with `ProcessingResult` | Migrate mutating file tasks to ordered pipeline processors |
| **UI Enhancements** | Context menu and toolbar actions only | Adds `DashboardWidgetContributor`, `DownloadDetailsSectionContributor`, `StatusIndicatorContributor` | Implement new UI contributor interfaces |
| **Theme Observation** | Static Swing components | `PluginTheme` and `PluginThemeListener` | Listen for dark/light skin changes and adapt colors dynamically |
| **Network & Proxies** | Global app proxy only | `ProxyProvider` (scoped SOCKS4/5 & HTTP routing per domain) | Implement `ProxyProvider` for domain-specific proxying |
| **VPN Integration** | Not supported | `VpnStatusProvider` and download pause policy | Implement non-privileged telemetry provider |
| **Process Execution** | Ad-hoc | Hardened `ProcessBuilder` with strict array arguments | Eliminate all shell commands (`cmd.exe /c` or `sh -c`) |

---

## 3. Migration Step-by-Step

### Step 1: Update Maven Dependency
Update your plugin's `pom.xml` to depend on `xdm-plugin-api` version `1.0.2` (or newer) with `provided` scope:

```xml
<dependency>
    <groupId>xdm</groupId>
    <artifactId>xdm-plugin-api</artifactId>
    <version>1.0.2</version>
    <scope>provided</scope>
</dependency>
```

### Step 2: Adopt Disposable `Registration` Handles
In SDK v1, extension registration methods were `void` and could only be unregistered by restarting the application. In SDK v2, every registration returns a `Registration` handle:

```java
// BEFORE (v1):
context.registerContextMenu(this);
context.registerSettings(this);

// AFTER (v2):
private final List<Registration> registrations = new ArrayList<>();

@Override
public void init(PluginContext context) throws Exception {
    registrations.add(context.registerContextMenu(this));
    registrations.add(context.registerSettings(this));
}

@Override
public void shutdown() {
    for (Registration reg : registrations) {
        reg.unregister();
    }
    registrations.clear();
}
```

### Step 3: Upgrade Download Tasks to `submitPluginTask`
In SDK v1, `context.submitDownload(request)` routed all downloads through the host HTTP engine. If your plugin managed an external tool (e.g. `yt-dlp`), the task was invisible to XDM's category list and progress windows.

In SDK v2, implement `PluginDownloadTask` and submit via `context.submitPluginTask`:

```java
// SDK v2: Visible in download list, supports Pause/Resume, displays progress
PluginDownloadRequest request = new PluginDownloadRequest(url, targetFolder, fileName);
PluginDownloadTask task = new MyCustomTask(request);
long taskId = context.submitPluginTask(request, task);
```

### Step 4: Migrate to Standard Event Topics & `EventSubscription`
Replace hardcoded string topics with `EventTopics` constants, and retain the `EventSubscription` handle for clean shutdown:

```java
// BEFORE (v1):
context.subscribe("download.complete", data -> { ... });

// AFTER (v2):
private EventSubscription sub;

@Override
public void init(PluginContext context) {
    sub = context.subscribeTopic(EventTopics.DOWNLOAD_COMPLETED, (topic, event) -> {
        // Handle typed TaskLifecycleEvent
    });
}

@Override
public void shutdown() {
    if (sub != null) {
        sub.close();
    }
}
```

### Step 5: Adopt `PostDownloadProcessor` for Sequential Pipelines
If your v1 plugin performed file transformation (such as checksum verification, decompression, or audio conversion) inside `PostDownloadHook`, migrate to `PostDownloadProcessor`:

```java
public class MyProcessor implements PostDownloadProcessor {
    @Override
    public String getName() { return "my-processor"; }

    @Override
    public int getOrder() { return 50; } // Runs before default priority 100

    @Override
    public boolean canProcess(DownloadInfo download, File downloadedFile) {
        return downloadedFile != null && downloadedFile.getName().endsWith(".zip");
    }

    @Override
    public ProcessingResult process(DownloadInfo download, File downloadedFile) {
        // Perform non-destructive operation
        return ProcessingResult.success("Unpacked successfully");
    }
}
```

### Step 6: Hardened Process Execution
If your plugin invokes external helper binaries:
- **Prohibited:** `Runtime.getRuntime().exec("yt-dlp " + url)` (vulnerable to argument injection).
- **Mandatory:** `new ProcessBuilder(Arrays.asList("yt-dlp", "--no-playlist", url))` (safe structured argument array).

---

## 4. Verification & Testing Checklist

Before distributing your migrated plugin:
- [ ] Compiles cleanly against `xdm-plugin-api:1.0.2` with zero host app dependencies.
- [ ] Declares all requested capabilities in `plugin.json` (`capabilities` array).
- [ ] Declares appropriate `minApiVersion: "1.1.0"`.
- [ ] Handles rapid enable/disable cycles without leaking background threads or listeners.
- [ ] Tested on both Light and Dark themes.
- [ ] Packaged as a clean `.xdmplugin` archive containing `plugin.json` at root.
