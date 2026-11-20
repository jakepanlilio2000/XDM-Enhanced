# Plugin API v2 Specification

The **XDM Enhanced Plugin Platform (API v2)** enables developers to build third-party download engines, network proxy providers, VPN telemetry listeners, and custom UI views completely decoupled from the desktop application runtime.

---

## 1. Maven Dependency Setup

To create an independent plugin, add `xdm-plugin-api` to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>xdm</groupId>
    <artifactId>xdm-plugin-api</artifactId>
    <version>1.0.2</version>
    <scope>provided</scope>
</dependency>
```

> [!TIP]
> The `xdm-plugin-api.jar` is available in official releases and locally in repository builds. Always specify `provided` scope because the host application supplies the API runtime at execution.

---

## 2. Plugin Manifest (`plugin.json`)

Every plugin package must include a `plugin.json` in `src/main/resources/`:

```json
{
  "id": "com.example.sample-plugin",
  "name": "Sample Verifier Plugin",
  "version": "1.0.0",
  "minApiVersion": "1.1.0",
  "author": "Community Developer",
  "description": "Demonstrates SDK v2 extension points and typed event bus.",
  "license": "Apache-2.0",
  "entryPoint": "com.example.plugin.SamplePlugin",
  "capabilities": [
    "SETTINGS_PANEL",
    "EVENT_BUS"
  ]
}
```

### Version Compatibility & Semantic Matching
- **`minApiVersion`:** Declares the minimum host release required (e.g. `"1.1.0"`).
- **`apiVersion`:** Supports Maven semantic range syntax (e.g. `"[2.0.0, 3.0.0)"`).
- **Legacy Compatibility:** Plugins implementing early v1 interfaces continue to operate through an automated `LegacyPluginAdapter` bridge.

---

## 3. Implementing `XdmPlugin` (SDK v2)

The entry point of your plugin implements `xdm.plugin.api.XdmPlugin`:

```java
package com.example.plugin;

import xdm.plugin.api.*;
import xdm.plugin.api.events.EventSubscription;
import xdm.plugin.api.events.EventTopics;
import java.util.ArrayList;
import java.util.List;

public class SamplePlugin implements XdmPlugin {

    private final List<Registration> registrations = new ArrayList<>();
    private EventSubscription eventSub;
    private PluginContext context;

    @Override
    public String getId() {
        return "com.example.sample-plugin";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;
        context.logInfo("SamplePlugin initialized successfully.");

        // Subscribe to decoupled progress/completion events via typed EventTopics
        eventSub = context.subscribeTopic(EventTopics.DOWNLOAD_COMPLETED, (topic, event) -> {
            context.logInfo("Received completion event on topic: " + topic);
            context.showNotification("Download Finished", "Task completed notification from sample plugin", NotificationLevel.INFO);
        });
    }

    @Override
    public void shutdown() {
        if (eventSub != null) {
            eventSub.close();
        }
        for (Registration reg : registrations) {
            reg.unregister();
        }
        registrations.clear();

        if (context != null) {
            context.logInfo("SamplePlugin shutting down.");
        }
    }
}
```

---

## 4. Custom Download Providers (`DownloadProvider`)

To integrate external download engines (such as `yt-dlp` or specialized protocol extractors) into the main download list:

```java
import xdm.plugin.api.download.DownloadProvider;
import xdm.plugin.api.download.PluginDownloadTask;
import java.io.File;
import java.util.Map;

public class MyDownloadProvider implements DownloadProvider {
    @Override
    public String getId() {
        return "my-downloader";
    }

    @Override
    public String getName() {
        return "Custom Media Downloader";
    }

    @Override
    public boolean canHandle(String url) {
        return url != null && url.contains("myservice.org/media/");
    }

    @Override
    public PluginDownloadTask createTask(String url, File destinationDir, Map<String, Object> options) {
        return new MyDownloadTask(url, destinationDir, options);
    }
}
```

Register your provider in `init()`:
```java
registrations.add(context.registerDownloadProvider(new MyDownloadProvider()));
```

---

## 5. Security & Subprocess Execution Guidelines

When invoking external helper binaries (such as `yt-dlp` or `ffmpeg`):

1. **In-Process Boundary:** Java plugins execute within the user's OS desktop account. Newly discovered plugins default to `UNTRUSTED` quarantine until explicitly approved.
2. **Never concatenate shell command strings:**
   ```java
   // UNSAFE — Vulnerable to shell injection:
   Runtime.getRuntime().exec("yt-dlp.exe " + userUrl);
   ```
3. **Always pass arguments via `ProcessBuilder` lists:**
   ```java
   // SAFE — ProcessBuilder with explicit array arguments:
   List<String> cmd = Arrays.asList(
       binaryPath,
       "--newline",
       "--no-colors",
       targetUrl
   );
   ProcessBuilder pb = new ProcessBuilder(cmd);
   ```
4. **Disaster Recovery Safe Mode:** If a plugin causes a crash, launching XDM Enhanced with `--safe-mode` or `--no-plugins` immediately disables third-party plugins.

---

## 6. Packaging & Distribution (`.xdmplugin`)

1. Compile and package your plugin with Maven:
   ```bash
   mvn clean package
   ```
2. Rename the resulting `.jar` archive to `.xdmplugin`:
   ```bash
   mv target/sample-plugin-1.0.0.jar target/sample-plugin.xdmplugin
   ```
3. Users can install your package via **Settings > Plugins > Install from File...** (`Alt+I`).

---

## 7. Authoritative References & Samples

- **Comprehensive Developer Guide:** [`docs/PLUGIN_DEVELOPMENT.md` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/docs/PLUGIN_DEVELOPMENT.md)
- **API Reference Specification:** [`docs/PLUGIN_API_REFERENCE.md` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/docs/PLUGIN_API_REFERENCE.md)
- **V1 to V2 Migration Guide:** [`docs/PLUGIN_V1_TO_V2_MIGRATION_GUIDE.md` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/docs/PLUGIN_V1_TO_V2_MIGRATION_GUIDE.md)
- **User Guide:** [`docs/PLUGIN_MANAGER_USER_GUIDE.md` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/docs/PLUGIN_MANAGER_USER_GUIDE.md)
- **5 Compilable Reference Plugins in Repo:** [`sample-plugins/` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-plugins)
  - `proxy-provider-sample`: SOCKS5/HTTP proxy management
  - `vpn-status-sample`: WireGuard/OpenVPN telemetry
  - `ui-notification-sample`: Custom tabs and desktop toast alerts
  - `v2-sample-plugin`: Lifecycle and event bus
  - `ytdlp-downloader-plugin`: External media extractor bridge
- **Community Wiki Quick Start:** [Plugin SDK Quick Start ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki/Plugin-SDK-Quick-Start)
