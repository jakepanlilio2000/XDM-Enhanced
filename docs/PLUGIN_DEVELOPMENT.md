# XDM Enhanced — Plugin Developer Guide & Architecture Reference

This guide explains how to develop, test, package, and distribute third-party plugins for **XDM Enhanced**.

The plugin system allows developers to extend the application's core capabilities without forking or modifying the main codebase.

---

## 1. Architecture Overview

XDM Enhanced separates the plugin extension layer into a dedicated standalone Maven artifact: `xdm-plugin-api`.

```
┌────────────────────────────────────────────────────────┐
│                      xdm-app                           │
│        (Host Application / Core Download Engine)       │
└──────────────────────────┬─────────────────────────────┘
                           │ Runtime Host / Isolation
                           ▼
┌────────────────────────────────────────────────────────┐
│                   xdm-plugin-api                       │
│    (Public Interfaces, Models, and Extension Points)   │
└──────────────────────────▲─────────────────────────────┘
                           │ Compile Dependency (Provided)
                           │
┌──────────────────────────┴─────────────────────────────┐
│                 Third-Party Plugin                     │
│    (Custom protocols, post-processing, tools, UI)      │
└────────────────────────────────────────────────────────┘
```

### Key Architectural Tenets

1. **No Internal Application Leaks:** Plugins compile strictly against `xdm-plugin-api`. Internal classes (e.g. database transactions, Swing window instances) are never leaked to external plugins.
2. **Read-Only / Controlled Data Access:** Plugins receive immutable `DownloadInfo` snapshots and submit new download requests through `PluginDownloadRequest`. Plugins cannot directly mutate task database records.
3. **Fault Isolation:** All plugin lifecycles, hooks, context menus, and toolbar invocations are executed within fault-isolation wrappers. If a plugin throws an uncaught exception, it is quarantined and marked `FAULTED` without crashing XDM Enhanced or corrupting user data.
4. **Honest Security Posture:** In-process JVM plugins run with the host process's operating system privileges. XDM Enhanced **does not claim** impossible JVM sandboxing. Instead, plugins remain in an `UNTRUSTED` quarantine state upon installation until the user explicitly reviews author metadata, declared capabilities, and SHA-256 checksums, and approves execution.
5. **Recovery Mode:** If a faulty plugin causes problems, XDM Enhanced can be launched with `--safe-mode` or `--no-plugins` to quarantine all third-party plugins and permit diagnostic troubleshooting.

---

## 2. Plugin Structure & Package Format

Plugins can be packaged as:
1. `.xdmplugin` archive (ZIP/JAR format containing `plugin.json` at root).
2. Standard `.jar` file with root `plugin.json`.
3. Unpacked directory in the `plugins/` folder containing `plugin.json`.

### Recommended Directory Structure

```
my-custom-plugin/
├── plugin.json
├── xdm/
│   └── example/
│       └── MyCustomPlugin.class
└── resources/
    └── icon.png
```

---

## 3. The `plugin.json` Manifest

Every plugin package **must** include a valid `plugin.json` file in its root directory.

### Manifest Schema

```json
{
  "id": "my-custom-plugin",
  "name": "My Custom Plugin",
  "version": "1.0.0",
  "author": "Developer Name",
  "description": "Short explanation of features provided by this extension.",
  "minAppVersion": "1.0.0-beta.4",
  "minApiVersion": "1.0.0",
  "entryPoint": "com.example.xdm.MyCustomPlugin",
  "capabilities": [
    "POST_DOWNLOAD_HOOK",
    "CONTEXT_MENU",
    "TOOLBAR",
    "SETTINGS_PANEL"
  ],
  "dependencies": [],
  "license": "GPL-3.0-or-later",
  "homepage": "https://example.com/my-plugin",
  "checksumSha256": "optional-precalculated-sha256"
}
```

### Manifest Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | Unique identifier (3-64 characters, `^[a-zA-Z0-9_-]{3,64}$`). |
| `name` | String | Yes | Human-readable display name. |
| `version` | String | Yes | Semantic version string (e.g. `1.0.0`). |
| `author` | String | Yes | Author or organization name. |
| `description` | String | No | Description displayed in the Plugins Settings view. |
| `minAppVersion` | String | No | Minimum compatible XDM Enhanced version (e.g. `1.0.0-beta.6`). |
| `minApiVersion` | String | No | Minimum compatible plugin API version (`1.0.0`). |
| `maxApiVersion` | String | No | Maximum compatible plugin API version (e.g. `2.0.0`). |
| `apiCompatibility` | String | No | Semantic version range specification (e.g. `[1.0.0, 2.0.0)`, `>=1.0.0 <=1.1.0`, `^1.0.0`, `~1.0.0`). |
| `entryPoint` | String | Yes | Fully-qualified class name implementing `XdmPlugin`. |
| `capabilities` | Array | No | Declared capabilities for user trust review. |
| `license` | String | No | Open-source or proprietary software license identifier. |
| `homepage` | String | No | Project website or source code repository link. |

---

## 4. API Compatibility & Versioning Matrix

XDM Enhanced adopts semantic versioning for the `xdm-plugin-api`.

### API Version Matrix

| API Version | Application Version | Features Added | Backward Compatible With |
|---|---|---|---|
| **1.0.0** | v1.0.0-beta.4 | Initial public plugin SPI (`XdmPlugin`, `DownloadProvider`, `FormatDetector`, `UrlRecognizer`, `PostDownloadHook`, `ContextMenuContributor`, `ToolbarContributor`, `SettingsPanelContributor`, `PluginCommand`). Basic `submitDownload`. | N/A |
| **1.1.0** | v1.0.0-beta.6 | Central Task Adapter (`submitPluginTask`), Typed Event Bus (`subscribe`, `publish`, `unsubscribe`), Async worker pool (`executeAsync`), UI thread dispatch (`runOnUIThread`), Task pause/resume (`canPause`, `canResume`), Semantic Version Range validation, Upgrade data preservation. | **1.0.0** (100% binary & runtime compatible via default methods) |

### Version Range Syntax

Plugins can specify compatible host API ranges in `plugin.json` using either `minApiVersion`/`maxApiVersion` or the unified `apiCompatibility` property:

- **Bracket / Interval notation:** `[1.0.0, 2.0.0)` (inclusive 1.0.0, exclusive 2.0.0)
- **Comparison operators:** `>=1.0.0 <=1.1.0` or `>=1.0.0 <2.0.0`
- **Caret notation (`^`):** `^1.0.0` (compatible with all 1.x.x versions >= 1.0.0)
- **Tilde notation (`~`):** `~1.0.0` (compatible with patch updates within 1.0.x)

If a plugin requires an API outside the host's supported version, XDM Enhanced sets its status to `INCOMPATIBLE`, records a clear diagnostic explanation in the plugin manager, and disables execution to prevent runtime crashes.

---

## 5. Declared Capabilities

Plugins declare their required capabilities in `plugin.json`. These are shown to the user during the trust confirmation prompt:

- `DOWNLOAD_PROVIDER`: Adds support for custom network protocols and streaming sources.
- `URL_RECOGNITION`: Inspects and recognizes specialized URLs and links.
- `FORMAT_DETECTION`: Analyzes media streams and custom MIME types.
- `MEDIA_METADATA`: Queries and parses stream media metadata and format trees.
- `TASK_ADAPTER`: Submits and bridges custom download tasks into the central download engine.
- `EVENT_BUS`: Subscribes to and dispatches typed inter-plugin and system events.
- `POST_DOWNLOAD_HOOK`: Receives notifications when downloads finish (e.g. checksum calculation, file conversion, tagging).
- `CONTEXT_MENU`: Adds right-click menu actions to download rows.
- `TOOLBAR`: Adds custom action buttons to the main download toolbar.
- `SETTINGS_PANEL`: Provides a custom settings configuration interface in XDM Enhanced.
- `COMMANDS`: Registers custom CLI or internal commands.

---

## 6. Central Download Task Adapter (`submitPluginTask`)

In API 1.0.0, `submitDownload(PluginDownloadRequest)` was limited to standard HTTP tasks handled internally.
In **API 1.1.0**, plugins can manage their own download mechanics (e.g. subprocesses, custom network protocols) while fully integrating into the XDM Enhanced user experience via `PluginContext.submitPluginTask`:

### Key Task Adapter Features
1. **Central UI Visibility:** Tasks appear immediately in the main window download list, table/card views, and the Category sidebar under the **Plugin** category.
2. **Real-Time Progress & Progress Dialogs:** Emitting `DownloadProgressEvent` updates speed, ETA, percent, and progress dialogs exactly like native downloads.
3. **Pause & Resume Integration:** When the user clicks Pause or Resume in the toolbar, table context menu, or progress window, the host invokes `task.pause()` or `task.resume()`.
4. **Complete Persistence:** Task state and metadata are saved to `AppDB` and `TaskInfoDB`.
5. **Post-Download Notifications:** Upon completion, the task triggers native completion dialogs and `onDownloadCompleted` hooks.

```java
// Submitting a plugin-managed task into the host engine:
PluginDownloadRequest request = new PluginDownloadRequest(url, targetFolder, fileName);
PluginDownloadTask task = new MyCustomDownloadTask(request);

long taskId = context.submitPluginTask(request, task);
if (taskId < 0) {
    // Fallback for older API 1.0.0 hosts: run task standalone
    task.start();
}
```

---

## 7. Typed Event Bus & Asynchronous Execution

API 1.1.0 introduces a typed event bus and managed worker utilities on `PluginContext`:

### Subscribing and Publishing Events
```java
// Subscribe to a typed event topic
context.subscribe("media.stream.detected", (topic, data) -> {
    context.logInfo("Received event on " + topic + ": " + data);
});

// Publish a typed event payload
context.publish("media.stream.detected", new StreamInfo("https://example.com/stream.m3u8", "1080p"));
```

### Asynchronous Computation & UI Thread Dispatch
Plugins should never block Swing's Event Dispatch Thread (EDT) or monopolize worker threads:
```java
// Execute non-blocking background task using host daemon thread pool
context.executeAsync(() -> {
    return analyzeRemoteStream(url);
}).thenAccept(result -> {
    // Dispatch UI updates safely to the Swing EDT
    context.runOnUIThread(() -> {
        statusLabel.setText("Analysis complete: " + result);
    });
});
```

### Lifecycle Cleanup
When a plugin is disabled or reloaded:
- All topic subscriptions registered by that plugin are automatically unsubscribed.
- Active plugin download tasks are safely paused or canceled.
- All toolbar and menu contributors are cleared.

---

## 8. Data & Configuration Persistence Across Upgrades

Every plugin is allocated a dedicated, persistent directory via `context.getDataDirectory()` (pointing to `<plugin-dir>/data/`).
When a plugin package (`.xdmplugin`) is updated to a newer version:
1. The host backs up `<plugin-dir>/data/`.
2. The plugin code is cleanly replaced.
3. The `<plugin-dir>/data/` folder and `config.properties` are restored without data loss.

---

## 5. Plugin Implementation Example

Below is a complete, working plugin implementation implementing `PostDownloadHook`, `ContextMenuContributor`, and `SettingsPanelContributor`:

```java
package com.example.xdm;

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
import java.util.Collections;
import java.util.List;

public class MyCustomPlugin implements XdmPlugin, PostDownloadHook, ContextMenuContributor, SettingsPanelContributor {
    private PluginContext context;
    private boolean notifyEnabled = true;

    @Override
    public String getId() {
        return "my-custom-plugin";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;
        this.notifyEnabled = context.getConfig().getBoolean("notifyEnabled", true);

        // Register capabilities
        context.registerPostDownloadHook(this);
        context.registerContextMenuContributor(this);
        context.registerSettingsPanel(this);

        context.logInfo("MyCustomPlugin initialized.");
    }

    @Override
    public void shutdown() {
        context.logInfo("MyCustomPlugin shutting down.");
    }

    @Override
    public void onDownloadCompleted(DownloadInfo download) {
        if (!notifyEnabled) return;
        context.logInfo("Download completed: " + download.getFileName() + " (" + download.getTotalSize() + " bytes)");
        context.showNotification("Download Complete", download.getFileName() + " has finished.", NotificationLevel.INFO);
    }

    @Override
    public List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads) {
        if (selectedDownloads.isEmpty()) return Collections.emptyList();

        PluginAction action = new PluginAction(
            "custom-action",
            "Process with MyCustomPlugin",
            downloads -> {
                for (DownloadInfo d : downloads) {
                    context.logInfo("Selected file: " + d.getDestinationPath());
                }
            }
        );
        return Collections.singletonList(action);
    }

    @Override
    public String getTitle() {
        return "My Custom Plugin Configuration";
    }

    private JCheckBox chkNotify;

    @Override
    public Component createPanel(PluginContext context) {
        JPanel p = new JPanel(new BorderLayout());
        chkNotify = new JCheckBox("Show custom notification on completion", notifyEnabled);
        p.add(chkNotify, BorderLayout.NORTH);
        return p;
    }

    @Override
    public void saveSettings() {
        if (chkNotify != null) {
            notifyEnabled = chkNotify.isSelected();
            context.getConfig().setBoolean("notifyEnabled", notifyEnabled);
            context.getConfig().save();
        }
    }
}
```

---

## 6. Implementing Custom Download Providers & URL Recognizers

XDM Enhanced allows plugins to register custom download engines (e.g. specialized stream capture, cloud storage providers, or command-line wrappers like yt-dlp).

### Download Provider Architecture

To create a download provider:
1. Implement `DownloadProvider` to declare protocol/domain matching and task creation.
2. Implement `UrlRecognizer` to detect media URLs, determine media titles, and suggest formats.
3. Implement `PluginDownloadTask` to execute the transfer, handle cancellation, and emit `DownloadProgressEvent`s to registered `DownloadProgressListener`s.

```java
package com.example.xdm;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.download.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomDownloadProvider implements DownloadProvider {
    @Override
    public String getProviderId() {
        return "custom-provider";
    }

    @Override
    public String getDisplayName() {
        return "Custom Media Downloader";
    }

    @Override
    public boolean canHandle(String url) {
        return url != null && url.startsWith("https://custom-service.com/");
    }

    @Override
    public PluginDownloadTask createTask(PluginDownloadRequest request, PluginContext context) {
        return new CustomDownloadTask(request, context);
    }
}
```

### Implementing `PluginDownloadTask` with Progress Events

A download task manages its state (`READY`, `CONNECTING`, `DOWNLOADING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELED`), reports real-time metrics, and publishes `DownloadProgressEvent` updates:

```java
public class CustomDownloadTask implements PluginDownloadTask {
    private final PluginDownloadRequest request;
    private final PluginContext context;
    private final List<DownloadProgressListener> listeners = new CopyOnWriteArrayList<>();
    private volatile TaskState state = TaskState.READY;
    private volatile long downloadedBytes = 0;
    private volatile long totalBytes = -1;
    private volatile int progressPercent = 0;
    private volatile File outputFile;

    public CustomDownloadTask(PluginDownloadRequest request, PluginContext context) {
        this.request = request;
        this.context = context;
        this.outputFile = new File(request.getDestinationFolder(), request.getFileName());
    }

    @Override
    public void start() {
        state = TaskState.DOWNLOADING;
        // Run transfer in a background thread...
    }

    @Override
    public void pause() { state = TaskState.PAUSED; }

    @Override
    public void resume() { state = TaskState.DOWNLOADING; }

    @Override
    public void cancel() {
        state = TaskState.CANCELED;
        // Abort network connections / terminate child processes
    }

    @Override
    public TaskState getState() { return state; }

    @Override
    public long getDownloadedBytes() { return downloadedBytes; }

    @Override
    public long getTotalBytes() { return totalBytes; }

    @Override
    public int getProgressPercent() { return progressPercent; }

    @Override
    public File getOutputFile() { return outputFile; }

    @Override
    public void addProgressListener(DownloadProgressListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeProgressListener(DownloadProgressListener listener) {
        listeners.remove(listener);
    }

    private void notifyProgress(long downloaded, long total, int percent, long bytesPerSec, long etaSeconds) {
        DownloadProgressEvent event = new DownloadProgressEvent(
            request.getUrl(), downloaded, total, percent, bytesPerSec, etaSeconds, state
        );
        for (DownloadProgressListener l : listeners) {
            try {
                l.onProgress(event);
            } catch (Throwable ignored) {}
        }
    }
}
```

---

## 7. Process Execution & Security Guidelines

When a plugin invokes external command-line utilities (such as `yt-dlp` or `ffmpeg`), it **must** strictly adhere to security best practices:

### 1. Structured Process Arguments Only (No Shell Execution)
- **NEVER** use shell wrappers (`Runtime.getRuntime().exec("sh -c " + cmd)` or `cmd.exe /c`). Shell evaluation introduces command injection vulnerabilities when filenames or URLs contain spaces, quotes, or metacharacters (`&`, `|`, `;`, `$`).
- **ALWAYS** pass arguments as a structured array or `List<String>` to `ProcessBuilder`:
  ```java
  List<String> command = new ArrayList<>();
  command.add(validatedExecutablePath);
  command.add("--newline");
  command.add("--no-playlist");
  command.add("-o");
  command.add(destinationDirectory + File.separator + "%(title)s.%(ext)s");
  command.add(url); // Safe: passed as a distinct argument, never shell-evaluated

  ProcessBuilder pb = new ProcessBuilder(command);
  ```

### 2. Validating Executable Paths
- Verify that the executable file exists and is executable before attempting execution.
- Validate the binary version safely (e.g. `[path, "--version"]`) with a short timeout.
- Reject paths pointing to suspicious or non-executable files.

### 3. Graceful Cancellation & Process Cleanup
- In `cancel()`, interrupt read loops, close process streams, and call `process.destroy()`.
- If the child process does not terminate within a brief timeout (e.g. 2 seconds), forcibly terminate it using `process.destroyForcibly()`.
- Ensure all subprocess resources and temporary files are cleaned up in a `finally` block.

---

## 8. Reference Implementation: yt-dlp Downloader Plugin

A full-featured reference implementation is provided in `sample-plugins/ytdlp-downloader-plugin/`.

### Features Demonstrated
- **Plugin Manifest (`plugin.json`):** Declares `DOWNLOAD_PROVIDER`, `URL_RECOGNIZER`, `SETTINGS_PANEL`, `TOOLBAR_ACTION`, and `COMMAND` capabilities.
- **Executable Validation (`YtDlpExecutableValidator`):** Discovers and verifies `yt-dlp` or `yt-dlp.exe` on system PATH or user-configured directory.
- **URL Recognition (`YtDlpUrlRecognizer`):** Recognizes supported media domains (`youtube.com`, `youtu.be`, `vimeo.com`, `soundcloud.com`, `bandcamp.com`, etc.).
- **Download Execution (`YtDlpDownloadTask`):** Spawns `yt-dlp` with `--progress-template`, parses live download progress, speed, ETA, and resolved output filenames, and supports robust cancellation.
- **Settings Panel (`YtDlpSettingsPanel`):** UI for configuring executable paths, format quality preferences (e.g. Best Video+Audio, 1080p, 720p, Audio Only), output directories, and process timeouts.
- **Packaging:** Pre-built portable plugin package available at `sample-plugins/ytdlp-downloader.xdmplugin`.

---

## 9. Maven Build Configuration

To build a plugin, configure your `pom.xml` with `xdm-plugin-api` in `provided` scope:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-custom-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>xdm</groupId>
            <artifactId>xdm-plugin-api</artifactId>
            <version>1.0.0-beta.4</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <includes><include>**/*</include></includes>
            </resource>
        </resources>
    </build>
</project>
```

Package the plugin using standard Maven commands:
```bash
mvn package
```
Rename the resulting JAR or distribute it as `.xdmplugin`.

---

## 10. Installing and Testing Plugins

1. In XDM Enhanced, open **Settings > Plugins**.
2. Click **Install Plugin...** and select your `.xdmplugin` or `.jar`.
3. Review the security prompt displaying the plugin's metadata, author, capabilities, and SHA-256 checksum.
4. Click **Trust & Enable** to activate the plugin.
5. In case of issues, use the **Open Plugins Folder** button to inspect logs or remove corrupt plugins manually.


