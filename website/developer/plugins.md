# Plugin API v2 Specification

The **XDM Enhanced Plugin Platform (API v2)** enables developers to build third-party download providers, checksum tools, and media extractors without depending on internal application code.

---

## 1. Maven Dependency Setup

To create a plugin, add `xdm-plugin-api` to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>xdm</groupId>
    <artifactId>xdm-plugin-api</artifactId>
    <version>1.0.0-beta.6</version>
    <scope>provided</scope>
</dependency>
```

> [!TIP]
> The `xdm-plugin-api.jar` is available in the [v1.0.0-beta.6 GitHub Release](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/xdm-plugin-api-1.0.0-beta.6.jar). Mark the dependency scope as `provided` because the host application supplies the API runtime.

---

## 2. Plugin Manifest (`plugin.json`)

Every plugin archive must contain a `plugin.json` file in its root:

```json
{
  "id": "com.example.sample-downloader",
  "name": "Sample Video Downloader",
  "version": "1.0.0",
  "apiVersion": "[1.0.0, 2.0.0)",
  "author": "Community Developer",
  "description": "Integrates external video extractors into XDM Enhanced.",
  "entryClass": "com.example.downloader.SamplePlugin",
  "capabilities": ["DOWNLOAD_PROVIDER", "SETTINGS_PANEL"]
}
```

### Semantic Version Range Matching
The `apiVersion` field uses standard Maven range syntax:
- `[1.0.0, 2.0.0)` matches any host version from `1.0.0` up to (but not including) `2.0.0`.
- This ensures application updates from `1.0.0-beta.6` to `1.1.0` or `1.5.0` will never break your plugin.

---

## 3. Implementing `PluginService`

The entry point of your plugin must implement `xdm.plugin.api.PluginService`:

```java
package com.example.downloader;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.PluginService;
import xdm.plugin.api.events.PluginEventListener;
import xdm.plugin.api.events.PluginProgressEvent;

public class SamplePlugin implements PluginService {

    private PluginContext context;

    @Override
    public void onInitialize(PluginContext context) {
        this.context = context;
        context.getLogger().info("SamplePlugin initialized successfully.");

        // Subscribe to progress events on the decoupled Event Bus
        context.getEventBus().register(PluginProgressEvent.class, new PluginEventListener<PluginProgressEvent>() {
            @Override
            public void onEvent(PluginProgressEvent event) {
                context.getLogger().debug("Progress: " + event.getProgressPercent() + "%");
            }
        });
    }

    @Override
    public void onShutdown() {
        context.getLogger().info("SamplePlugin shutting down.");
    }
}
```

---

## 4. Routing Tasks to the Central Download List

To display your plugin's tasks in the main XDM Enhanced window, use the task adapter bridge:

```java
import xdm.plugin.api.models.PluginDownloadTask;

public void startDownload(String targetUrl, String destinationPath) {
    PluginDownloadTask task = new PluginDownloadTask(
        "task-" + System.currentTimeMillis(),
        "Sample Video File.mp4",
        targetUrl,
        destinationPath,
        0L // Size in bytes (or 0 if unknown)
    );

    // Submits the task to the central download scheduler
    context.getTaskBridge().submitTask(task);
}
```

---

## 5. Security & Subprocess Execution Guidelines

When invoking external binaries (such as `yt-dlp` or `ffmpeg`):
1. **Never use string concatenation with shell execution:**
   ```java
   // UNSAFE — Vulnerable to shell injection:
   Runtime.getRuntime().exec("yt-dlp.exe " + userUrl);
   ```
2. **Always pass arguments as explicit list elements:**
   ```java
   // SAFE — ProcessBuilder with array arguments:
   List<String> cmd = Arrays.asList(
       binaryPath,
       "--newline",
       "--no-colors",
       targetUrl
   );
   ProcessBuilder pb = new ProcessBuilder(cmd);
   ```
3. **Handle Process Streams:** Ensure `stdout` and `stderr` streams are consumed on separate daemon threads to prevent process deadlocks.

---

## 6. Packaging Your Plugin (`.xdmplugin`)

1. Compile your Java code into `.class` files.
2. Package the `plugin.json` and compiled classes into a standard ZIP archive.
3. Rename the archive extension from `.zip` to `.xdmplugin`.
4. Users can install your plugin via **Settings > Plugins > Install Plugin**.

---

## 7. Authoritative References & Samples

- **Authoritative Specification:** [`docs/PLUGIN_DEVELOPMENT.md` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/docs/PLUGIN_DEVELOPMENT.md)
- **Reference Plugins in Repo:** [`sample-plugins/` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-plugins)
- **Community Wiki Tutorial:** [Plugin SDK Quick Start ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki/Plugin-SDK-Quick-Start)
- **Plugin API JAR Download:** [GitHub Releases v1.0.0-beta.6 ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.0-beta.6)

