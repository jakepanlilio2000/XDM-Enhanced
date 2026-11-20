# XDM Enhanced — Plugin Developer Guide (SDK v2)

Welcome to the **XDM Enhanced Plugin SDK v2** developer guide. This guide explains how to build, test, package, and distribute third-party plugins for XDM Enhanced.

---

## 1. Quick Start

Create a minimal working plugin in 5 minutes:

### 1.1 Maven Coordinates (`pom.xml`)
Plugins compile against the public `xdm-plugin-api` artifact with `provided` scope:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-xdm-plugin</artifactId>
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
            <version>1.0.2</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### 1.2 Plugin Manifest (`src/main/resources/plugin.json`)
```json
{
  "id": "my-xdm-plugin",
  "name": "My Custom Plugin",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "Demonstrates SDK v2 extension capabilities.",
  "license": "Apache-2.0",
  "minApiVersion": "1.1.0",
  "entryPoint": "com.example.MyPlugin",
  "capabilities": [
    "CONTEXT_MENU",
    "SETTINGS_PANEL",
    "EVENT_BUS"
  ]
}
```

### 1.3 Plugin Class (`src/main/java/com/example/MyPlugin.java`)
```java
package com.example;

import xdm.plugin.api.*;
import xdm.plugin.api.events.EventSubscription;
import xdm.plugin.api.events.EventTopics;
import java.util.ArrayList;
import java.util.List;

public class MyPlugin implements XdmPlugin {
    private final List<Registration> registrations = new ArrayList<>();
    private EventSubscription eventSub;
    private PluginContext context;

    @Override
    public String getId() {
        return "my-xdm-plugin";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;

        // Register custom settings panel
        registrations.add(context.registerSettings(new MySettingsPanel()));

        // Subscribe to download events
        eventSub = context.subscribeTopic(EventTopics.DOWNLOAD_COMPLETED, (topic, event) -> {
            context.showNotification("Download Complete", "Handled by MyPlugin", NotificationLevel.INFO);
        });

        context.logInfo("MyPlugin initialized successfully.");
    }

    @Override
    public void shutdown() {
        for (Registration reg : registrations) {
            reg.unregister();
        }
        registrations.clear();

        if (eventSub != null) {
            eventSub.close();
        }
        if (context != null) {
            context.logInfo("MyPlugin shut down.");
        }
    }
}
```

---

## 2. Architecture & Public API Boundaries

XDM Enhanced separates the plugin extension layer into a standalone public interface module: `xdm-plugin-api`.

```
┌────────────────────────────────────────────────────────┐
│                      xdm-app                           │
│        (Host Application / Core Download Engine)       │
└──────────────────────────┬─────────────────────────────┘
                           │ Runtime Host / Filtering ClassLoader
                           ▼
┌────────────────────────────────────────────────────────┐
│                   xdm-plugin-api                       │
│    (Public Interfaces, Models, and Extension Points)   │
└──────────────────────────▲─────────────────────────────┘
                           │ Compile Dependency (Provided)
                           │
┌──────────────────────────┴─────────────────────────────┐
│                 Third-Party Plugin                     │
│   (Custom protocols, post-processing, tools, UI, VPN)  │
└────────────────────────────────────────────────────────┘
```

### Key Architectural Tenets
1. **Strict Classloader Boundary:** Third-party plugins load through `PluginFilteringClassLoader`. Plugins have access only to public interfaces in `xdm.plugin.api.*` and standard Java runtime libraries. Internal host application classes cannot be accessed or reflected.
2. **Controlled Access to Data:** Plugins receive immutable snapshots (`DownloadInfo`) and submit downloads via `PluginDownloadRequest`. Direct mutation of application databases is prohibited.
3. **Disposable Registration Handles:** Every extension registered with `PluginContext` returns a `Registration` handle. Plugins dispose their own resources upon shutdown via `Registration.unregister()`.
4. **EDT Safety:** UI component creation is dispatched to the Swing Event Dispatch Thread (EDT). Long-running operations, process checks, and network requests must be performed asynchronously via `context.executeAsync(...)`.

---

## 3. Plugin Manifest Specification (`plugin.json`)

The `plugin.json` file must reside in the root of the `.xdmplugin` or `.jar` archive.

```json
{
  "id": "sample-plugin-id",
  "name": "Human-Readable Plugin Name",
  "version": "1.0.0",
  "author": "Developer or Team Name",
  "description": "Clear explanation of what the plugin does.",
  "license": "GPL-3.0-or-later",
  "homepage": "https://example.com/plugin",
  "minAppVersion": "1.1.0-beta.1",
  "minApiVersion": "1.1.0",
  "maxApiVersion": "2.0.0",
  "apiCompatibility": ">=1.1.0 <2.0.0",
  "entryPoint": "com.example.MyPluginEntryPoint",
  "capabilities": [
    "DOWNLOAD_PROVIDER",
    "NETWORK_PROXY",
    "VPN_STATUS",
    "SETTINGS_PANEL"
  ],
  "dependencies": [
    "optional-prerequisite-plugin-id"
  ],
  "targetPlatforms": [
    "windows",
    "linux",
    "macos"
  ]
}
```

### Manifest Field Definitions

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | Unique plugin identifier (`^[a-zA-Z0-9_-]{3,64}$`). Reserved IDs: `system`, `app`, `core`, `internal`. |
| `name` | String | Yes | User-visible display name. |
| `version` | String | Yes | Semantic version string (e.g. `1.0.0`). |
| `author` | String | Yes | Developer or vendor identity. |
| `description` | String | Yes | Explanatory description shown in the Plugin Manager. |
| `license` | String | Yes | Software license (e.g. `Apache-2.0`, `GPL-3.0-or-later`, `MIT`). |
| `entryPoint` | String | Yes | Fully-qualified class name implementing `xdm.plugin.api.XdmPlugin`. |
| `minApiVersion` | String | Yes | Minimum compatible host API version (e.g. `1.1.0`). |
| `maxApiVersion` | String | No | Maximum compatible host API version. |
| `apiCompatibility` | String | No | Semantic version range (e.g. `>=1.1.0 <2.0.0`). |
| `capabilities` | Array | Yes | Declared capabilities for user trust review. |
| `dependencies` | Array | No | IDs of required prerequisite plugins. |
| `targetPlatforms`| Array | No | Allowed operating systems (`windows`, `linux`, `macos`). |

---

## 4. Complete Capabilities Catalog (All 19 Capabilities)

Every capability declared in `plugin.json` must be disclosed and mapped to its technical requirements:

| Capability | Category | Description | External Tools | OS Support | User Consent | Restart Behavior |
|---|---|---|---|---|---|---|
| `DOWNLOAD_PROVIDER` | Download Engine | Custom download protocols and streaming engines. | CLI binaries (yt-dlp, ffmpeg) | Cross-platform | Explicit user trust | Dynamic |
| `URL_RECOGNITION` | Preflight | Inspects clipboard and captured links. | None | In-process JVM | Manifest declaration | Dynamic |
| `FORMAT_DETECTION` | Media Parsing | Stream and container format resolution. | Optional media tools | In-process JVM | Manifest declaration | Dynamic |
| `MEDIA_METADATA` | Media Parsing | Title, format, chapter, and thumbnail extraction. | Optional media tools | In-process JVM | Manifest declaration | Dynamic |
| `TASK_ADAPTER` | Download Engine | Custom multi-segment transfer and progress tracking. | None | In-process JVM | Explicit user trust | Dynamic |
| `POST_DOWNLOAD_HOOK` | Automation | Non-destructive notifications and file tagging. | None for in-process | Cross-platform | User trust | Dynamic |
| `POST_DOWNLOAD_PROCESSOR`| Pipeline | Sequential file transformations (checksums, unpack). | Optional user scripts | Directory-scoped | Explicit user trust | Dynamic |
| `NETWORK_PROXY` | Network & Routing | SOCKS4/5 or HTTP scoped download proxies. | None | Engine-scoped | Explicit user approval | Dynamic |
| `VPN_STATUS` | Network Telemetry | Discovers external VPN client status and prerequisites. | External VPN client | Virtual tunnel ifaces | Explicit user policy | Dynamic |
| `CONTEXT_MENU` | UI | Download list right-click context menu actions. | None | Swing Desktop UI | Manifest declaration | Dynamic |
| `TOOLBAR` | UI | Action buttons in the primary application toolbar. | None | Swing Desktop UI | Manifest declaration | Dynamic |
| `SETTINGS_PANEL` | Configuration | Dedicated settings panel in Settings window. | None | Swing Desktop UI | Manifest declaration | Dynamic |
| `DASHBOARD_WIDGET` | UI | Summary card widgets on the main dashboard. | None | Swing Desktop UI | Manifest declaration | Dynamic |
| `DETAILS_SECTION` | UI | Custom inspection tabs in download properties. | None | Swing Desktop UI | Manifest declaration | Dynamic |
| `STATUS_INDICATOR` | UI | Telemetry items and pills in the bottom status bar. | None | Swing Desktop UI | Manifest declaration | Dynamic |
| `COMMANDS` | Extensibility | Invocable plugin commands for scripts or shortcuts. | None | In-process JVM | Manifest declaration | Dynamic |
| `EVENT_BUS` | Messaging | Typed lifecycle and system event bus. | None | Pure in-process JVM | Manifest declaration | Dynamic |
| `METADATA_ENRICHER` | Metadata | Filename and category suggestions from URL patterns. | None | In-process JVM | Manifest declaration | Dynamic |
| `AUTOMATION_ACTION` | Automation | User-configurable automation action targets. | User-enabled scripts | Path-contained | Explicit user rules | Dynamic |

---

## 5. Security & Trust Model

### 5.1 Honest Security Posture & Trust Boundaries
XDM Enhanced maintains an **Honest Security Posture**: it does not claim to sandbox in-process Java plugins inside a virtual machine or OS container. In-process Java plugins execute within the XDM Enhanced JVM with the operating system privileges of the logged-in user. **Manifest capabilities do not sandbox JVM code.** Instead, capabilities serve as declared contract intents that inform users what capabilities the plugin requests.

### 5.2 Quarantine and UNTRUSTED State on Installation
Whenever a new third-party plugin archive (`.xdmplugin` or `.jar`) is installed or discovered, it is placed in a **quarantine** state with status `UNTRUSTED`. The host requires explicit user consent before classloading or activation. During installation, the host displays a mandatory trust disclosure dialogue disclosing:
- Plugin name, ID, version, author, and declared license.
- Source path and calculated SHA-256 archive checksum.
- Full list of declared capabilities.
- Prominent warning regarding in-process execution privileges.

Until approved by the user, third-party plugins remain in quarantine (`UNTRUSTED`) and will not be activated or executed.

### 5.3 Recovery Mode (`--safe-mode` / `--no-plugins`)
If a third-party plugin causes unexpected errors, crashes, or hangs during application launch, XDM Enhanced provides a robust recovery mode:
- Launching the application with `--safe-mode` (or `--no-plugins`) automatically quarantines and disables all installed plugins, allowing the user to start the UI, access Settings > Plugins, and remove problematic plugins safely.

### 5.4 Read-Only Snapshot Model & Task Submission
Plugins cannot mutate or corrupt the host application's internal download queue directly. Instead, the host provides immutable read-only snapshots via `DownloadInfo`. To submit transfers or initiate downloads, plugins must submit a structured `PluginDownloadRequest` or implement the `DownloadProvider` SPI.

### 5.5 Archive Hardening & Defenses
- **Zip Slip Defense:** Directory traversal sequences (`..`, absolute paths, drive letters) in archives throw an immediate `SecurityException`.
- **Symlink Defense:** Symbolic links in archives are strictly forbidden (`LinkOption.NOFOLLOW_LINKS`).
- **Native Binary Defense:** Archives containing executable binaries (`.exe`, `.dll`, `.so`, `.sh`, `.bat`, `.cmd`) are rejected at validation and extraction time.
- **Zip Bomb Defense:** Maximum single entry size is limited to 250 MB; total uncompressed archive size is limited to 500 MB.

### 5.6 Subprocess Invocation Safety
If a plugin executes an external CLI tool (such as `yt-dlp` or `ffmpeg`):
- **ALWAYS** pass arguments as a structured `List<String>` or `String[]` using `ProcessBuilder`.
- **NEVER** interpolate user input, filenames, or URLs into a shell command line (`cmd /c` or `sh -c`), which introduces shell injection vulnerabilities.

```java
// SAFE: Arguments passed as an explicit array
List<String> command = new ArrayList<>();
command.add("yt-dlp");
command.add("--no-playlist");
command.add(url);
ProcessBuilder pb = new ProcessBuilder(command);
```

### 5.7 Zero Secrets Guarantee in Logs & Diagnostics
All URLs containing embedded credentials (`http://user:pass@host`), authorization headers (`Bearer ...`), and sensitive query parameters (`password=...`) must be sanitized. The host automatically scrubs these values to `***` before recording telemetry or exporting diagnostics.

---

## 6. UI Enhancements & Theme Observation

Plugins can contribute UI components that observe FlatLaf themes and system skins.

### 6.1 Contributing a Settings Panel
Implement `SettingsPanelContributor` and register it with `context.registerSettings(panel)`:

```java
public class MySettingsPanel implements SettingsPanelContributor {
    private JCheckBox chkOption;

    @Override
    public String getTitle() {
        return "My Plugin Settings";
    }

    @Override
    public Component createPanel(PluginContext context) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        chkOption = new JCheckBox("Enable advanced feature", true);
        panel.add(chkOption);
        return panel;
    }

    @Override
    public void saveSettings() {
        // Persist preferences
    }
}
```

### 6.2 Theme Observation (`PluginTheme`)
Components should adapt their foreground and background colors when the user switches skins:

```java
PluginTheme theme = context.getTheme();
if (theme != null) {
    theme.addThemeListener(t -> {
        boolean dark = t.isDark();
        Color accent = t.getAccentColor();
        myComponent.setForeground(accent);
    });
}
```

---

## 7. Scoped Network Proxy Integration

SDK v2 enables plugins to provide proxy configurations strictly scoped to supported download engines without altering the operating system proxy settings:

```java
public class MyProxyProvider implements ProxyProvider {
    @Override
    public String getProviderId() { return "my-proxy"; }

    @Override
    public String getDisplayName() { return "Corporate SOCKS5 Proxy"; }

    @Override
    public int getPriority() { return 100; }

    @Override
    public Set<ProxyResolution.NetworkEngine> getApplicableEngines() {
        return EnumSet.of(ProxyResolution.NetworkEngine.HTTP, ProxyResolution.NetworkEngine.MEDIA);
    }

    @Override
    public ProxyResolution resolveProxy(URI targetUri, ProxyResolution.NetworkEngine engine) {
        if (targetUri.getHost().endsWith(".internal.example.com")) {
            return ProxyResolution.socks5("127.0.0.1", 1080, "user", "pass", ProxyResolution.FallbackPolicy.FAIL_CLOSED);
        }
        return null; // Bypass proxy for all other hosts
    }
}
```

---

## 8. Non-Privileged VPN Client Integration

Plugins can report connectivity status and server profiles for established VPN clients:

```java
public class MyVpnProvider implements VpnStatusProvider {
    @Override
    public String getProviderId() { return "wireguard-client"; }

    @Override
    public String getDisplayName() { return "WireGuard Status"; }

    @Override
    public VpnStatus getCurrentStatus() {
        return VpnStatus.connected("wg0", "US Gateway", "10.14.0.2", "us-profile", true, "Active");
    }

    // Never modify OS routing tables or inject drivers!
}
```

When the user enables the **VPN Prerequisite Policy** in Settings > Network, XDM Enhanced pauses downloads if the VPN client reports disconnection, avoiding accidental data leakage on unencrypted interfaces.

---

## 9. Packaging & Distributing Plugins

### 9.1 Building `.xdmplugin` Archive
A `.xdmplugin` file is a ZIP archive containing `plugin.json` and compiled `.class` or `.jar` files:

```powershell
# Using Maven Jar Plugin
mvn clean package

# Renaming target jar to .xdmplugin
cp target/my-xdm-plugin-1.0.0.jar target/my-xdm-plugin.xdmplugin
```

### 9.2 Directory Layout in `plugins/`
Users can also drop unpacked plugin folders into the application plugins directory:
- **Windows:** `%APPDATA%\xdman\plugins\<plugin-id>\`
- **Linux:** `~/.config/xdman/plugins/<plugin-id>/`
- **macOS:** `~/Library/Application Support/xdman/plugins/<plugin-id>/`

---

## 10. Sample Projects Reference

Four complete, standalone sample projects are available under `sample-plugins/`:

1. [`sample-plugins/ytdlp-downloader-plugin`](../sample-plugins/ytdlp-downloader-plugin/): Full download provider with visible progress tasks and CLI subprocess integration.
2. [`sample-plugins/proxy-provider-sample`](../sample-plugins/proxy-provider-sample/): Scoped SOCKS5/HTTP proxy configuration with fail-closed security.
3. [`sample-plugins/vpn-status-sample`](../sample-plugins/vpn-status-sample/): Non-privileged VPN client status discovery and server profile telemetry.
4. [`sample-plugins/ui-notification-sample`](../sample-plugins/ui-notification-sample/): Context menus, toolbar actions, status pills, details tabs, and event notifications.
