# XDM Enhanced — Plugin SDK v2 Technical Design & Architecture Specification

**Document Version:** 2.0.0-DRAFT  
**Author:** Senior Java Architect & Independent Plugin-System Auditor  
**Scope:** General-Purpose Extensibility Framework for XDM Enhanced  
**Status:** Architecture Design (Phase P1 — Non-Runtime Design & Specifications)  
**Target Release:** `v1.1.0-beta.1` / `xdm-plugin-api:2.0.0`

---

## 1. Executive Summary & Architectural Goals

The XDM Enhanced Plugin SDK v1 (audited in Phase P0) successfully established independent Maven packaging, task bridge integration for media downloads, and basic UI/post-download hooks. However, v1 had several structural limitations:
1. Heavily biased toward download providers (`DownloadProvider`, `PluginDownloadTask`).
2. Lacked first-class network, proxy, and VPN telemetry extension points.
3. Classloader parent delegation allowed reflective access to internal application classes (`GAP-01`).
4. Concurrency relied on `ForkJoinPool.commonPool()`, introducing thread starvation hazards (`GAP-03`).
5. Manifest capability string discrepancies caused spurious validation warnings (`GAP-02`).

**Plugin SDK v2** generalises the extension architecture into a **modular, composable, capability-oriented framework** that:
- De-couples extension points into independent SPI facets.
- Enforces a strict classloader filtering boundary (`PluginFilteringClassLoader`).
- Introduces dedicated worker thread pools.
- Preserves full backwards compatibility for existing v1 plugins through transparent host adapters.
- Honestly articulates the in-process JVM security posture without false sandboxing claims.

---

## 2. Public API Architecture (`xdm-plugin-api:2.0.0`)

### 2.1 Decoupling Principle
The public API module `xdm-plugin-api` remains a **pure Java library** targeting Java 8 bytecode compatibility to maximize third-party developer ergonomics. It contains:
- **Zero runtime dependencies** on external libraries.
- **Zero references** to internal application classes (`xdm.app.*`, `xdm.core.*`, `xdm.manager.*`).
- **Immutable snapshot models** (`DownloadInfo`, `ProxyResolution`, `VpnStatus`, `UrlMetadata`, `FormatInfo`).
- **Defensive API boundaries**: plugins interact strictly through `PluginContext` facades.

### 2.2 Package Hierarchy

```
xdm.plugin.api/
├── XdmPlugin.java                     (Core plugin lifecycle interface)
├── PluginContext.java                 (Host service registry facade)
├── PluginConfig.java                  (Type-safe key-value configuration)
├── NotificationLevel.java             (Severity levels)
├── download/
│   ├── DownloadProvider.java          (Custom protocol / URL download handler)
│   ├── PluginDownloadTask.java        (Active task contract: start, pause, resume, cancel)
│   ├── DownloadProgressListener.java  (Functional progress callback)
│   └── DownloadProgressEvent.java     (Telemetry: bytes, speed, ETA, status)
├── network/
│   ├── ProxyProvider.java             (Dynamic proxy resolution SPI)
│   ├── ProxyResolution.java           (DIRECT, HTTP, SOCKS4, SOCKS5)
│   ├── VpnStatusProvider.java         (VPN state / telemetry provider SPI)
│   ├── VpnStatus.java                 (State model: ACTIVE, INACTIVE, RECONNECTING)
│   └── VpnStateListener.java          (Telemetry state listener)
├── processors/
│   ├── PostDownloadProcessor.java     (Chained file processing & validation)
│   ├── ProcessingResult.java          (SUCCESS, MODIFIED, REJECTED)
│   ├── MediaStreamExtractor.java      (Multi-stream video/audio resolution)
│   └── FormatDetector.java            (Binary magic byte inspection)
├── ui/
│   ├── ContextMenuContributor.java    (Download list context menu actions)
│   ├── ToolbarContributor.java        (Primary toolbar action buttons)
│   ├── SettingsPanelContributor.java  (Custom settings panel component)
│   ├── DashboardWidgetContributor.java(Status bar / category sidebar widget)
│   └── PluginAction.java              (Executable action metadata)
├── events/
│   ├── PluginEventListener.java       (Generic typed event listener)
│   ├── EventTopics.java               (Standardized topic constants)
│   └── DownloadEvent.java             (Standard lifecycle event payload)
└── models/
    ├── DownloadInfo.java              (Immutable download record snapshot)
    ├── PluginDownloadRequest.java     (Download submission model)
    ├── PluginMetadata.java            (Manifest metadata descriptor)
    └── PluginCapability.java          (Formal capability enumeration)
```

---

## 3. Minimal Plugin Context (`PluginContext`) Specification

The host application supplies an instance of `PluginContext` to `XdmPlugin.init(context)`. The context is strictly an **extension registry and telemetry interface**:

```java
public interface PluginContext {
    // 1. Metadata & Isolation
    PluginMetadata getMetadata();
    File getDataDirectory();
    PluginConfig getConfig();
    String getApplicationVersion();
    String getApiVersion();

    // 2. Service Registrations (Composable Extension Points)
    void registerDownloadProvider(DownloadProvider provider);
    void registerProxyProvider(ProxyProvider provider);
    void registerVpnStatusProvider(VpnStatusProvider provider);
    void registerPostDownloadProcessor(PostDownloadProcessor processor);
    void registerMediaStreamExtractor(MediaStreamExtractor extractor);
    void registerFormatDetector(FormatDetector detector);
    void registerContextMenuContributor(ContextMenuContributor contributor);
    void registerToolbarContributor(ToolbarContributor contributor);
    void registerSettingsPanel(SettingsPanelContributor panel);
    void registerDashboardWidget(DashboardWidgetContributor widget);
    void registerCommand(PluginCommand command);

    // 3. Task Submission & Host Querying (Read-Only)
    List<DownloadInfo> getDownloads();
    void submitDownload(PluginDownloadRequest request);
    long submitPluginTask(PluginDownloadRequest request, PluginDownloadTask task);

    // 4. Asynchronous Messaging & Threading
    <T> void subscribe(String topic, PluginEventListener<T> listener);
    <T> void unsubscribe(String topic, PluginEventListener<T> listener);
    <T> void publish(String topic, T eventData);
    <T> CompletableFuture<T> executeAsync(Callable<T> task);
    void runOnUIThread(Runnable action);

    // 5. Diagnostics & Logging
    void showNotification(String title, String message, NotificationLevel level);
    void logInfo(String message);
    void logWarn(String message);
    void logError(String message, Throwable throwable);
}
```

---

## 4. Subsystem Contract Specifications

### 4.1 Download Providers (`DownloadProvider`)
- **Responsibility:** Handles downloads for custom protocols (e.g. `ipfs://`, `gemini://`, proprietary streams, or external tooling like `yt-dlp`).
- **Contract:**
  ```java
  public interface DownloadProvider {
      String getName();
      boolean canHandle(String url);
      PluginDownloadTask createDownloadTask(PluginDownloadRequest request) throws Exception;
  }
  ```
- **Lifecycle Integration:** The created `PluginDownloadTask` is adapted by `PluginDownloadBridge` into a native `DbRecord` (`DownloadType.Plugin`). It participates in the central queue, bandwidth throttler, speed graphs, pause/resume, and categories.

### 4.2 Network & Proxy Configuration (`ProxyProvider`)
- **Responsibility:** Allows plugins to resolve custom proxies dynamically per target URI (e.g. PAC script execution, rotating residential proxies, corporate authentication tunnels, Tor/SOCKS routing).
- **Contract:**
  ```java
  public interface ProxyProvider {
      String getProviderId();
      int getPriority(); // Higher priority queried first
      ProxyResolution resolveProxy(URI targetUri);
  }
  ```
- **Resolution Model:**
  ```java
  public class ProxyResolution {
      public enum Type { DIRECT, HTTP, SOCKS4, SOCKS5, SYSTEM_DEFAULT }
      public static ProxyResolution direct();
      public static ProxyResolution http(String host, int port, String user, String pass);
      public static ProxyResolution socks(String host, int port, String user, String pass);
  }
  ```
- **Engine Binding:** `xdm.core.net.WebClient` queries `PluginManager.resolveProxy(uri)`. If a plugin returns a non-null resolution, the core HTTP engine connects via the returned socket address.

### 4.3 VPN Status & Telemetry (`VpnStatusProvider`)
- **Responsibility:** Informs XDM of the active VPN connection state to power automated pause/kill-switch features and network interface binding.
- **Contract:**
  ```java
  public interface VpnStatusProvider {
      String getVpnName();
      boolean isConnected();
      String getAssignedInterface(); // e.g. "tun0", "wg0", or null
      void addStateListener(VpnStateListener listener);
      void removeStateListener(VpnStateListener listener);
  }
  ```
- **Kill-Switch Automation:** If the user enables "Pause on VPN Disconnect" in Settings, and an active `VpnStatusProvider` reports `isConnected() == false`, XDM automatically transitions all active downloads to `PAUSED` and displays a warning notification.

### 4.4 Post-Download Processing Pipeline (`PostDownloadProcessor`)
- **Responsibility:** Generalizes v1's read-only `PostDownloadHook` into an active processing pipeline (e.g. checksum calculation, malware scanning, automatic extraction, audio transcoding).
- **Contract:**
  ```java
  public interface PostDownloadProcessor {
      String getName();
      int getOrder(); // Pipeline execution order
      ProcessingResult process(DownloadInfo download, File downloadedFile) throws Exception;
  }
  ```
- **Result Semantics:**
  ```java
  public class ProcessingResult {
      public enum Status { SUCCESS, MODIFIED, REJECTED, SKIPPED }
      // MODIFIED: file was replaced/renamed (e.g. unzipped or transcoded)
      // REJECTED: file failed verification (e.g. checksum mismatch or virus detected)
  }
  ```

### 4.5 UI Extensions & Dashboard Widgets
- **Context Menus:** `ContextMenuContributor` dynamically provides actions based on current selection:
  ```java
  List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads);
  ```
- **Toolbar:** `ToolbarContributor` adds action buttons to the main toolbar:
  ```java
  List<PluginAction> getToolbarActions();
  ```
- **Settings:** `SettingsPanelContributor` renders custom Swing panels in **Settings > Plugins**:
  ```java
  Component createPanel(PluginContext context);
  void saveSettings();
  ```
- **Dashboard Widget:** `DashboardWidgetContributor` contributes real-time status components (e.g. VPN status pill, active proxy label, speed multiplier):
  ```java
  public interface DashboardWidgetContributor {
      String getWidgetId();
      Component createWidget();
      void updateMetrics();
  }
  ```

---

## 5. Negative Specification — What Plugins CANNOT Override

To safeguard application stability and prevent hostile takeovers, the Plugin SDK explicitly disallows the following:

| Disallowed Behavior | Architectural Rationale & Enforcement |
|---|---|
| **Raw Kernel / TAP Network Drivers** | Plugins cannot install kernel drivers or modify OS routing tables; VPN plugins must wrap user-space tools or inspect interface telemetry. |
| **Bypassing User Proxy Settings** | If the user explicitly sets "Always use Direct Connection" or locks a static proxy in XDM Settings, plugin `ProxyProvider` instances are bypassed. |
| **Mutating Core Download Database** | Plugins receive immutable `DownloadInfo` snapshots. Direct SQL/ObjectStore writes are strictly prohibited. |
| **Global Window Chrome / LookAndFeel** | Plugins cannot replace FlatLaf, modify window titlebars, or hide native window controls. |
| **Disabling Application Updates** | Plugins cannot intercept, alter, or suppress XDM's update checker or release verification. |
| **Arbitrary File Access Outside Data Sandbox** | `PluginConfig` is constrained to `plugins/<id>/data/`. Plugins cannot read or overwrite configuration belonging to XDM or other plugins. |
| **Preventing App Shutdown** | If a plugin's `shutdown()` hangs for more than 3,000 ms, the host forcefully terminates its threads and proceeds with application shutdown. |

---

## 6. Security Architecture & Boundary Verification

### 6.1 Honest Security Posture (In-Process JVM)
- **Fact:** In modern Java (Java 17–25), `SecurityManager` is removed. Code executing in the same JVM shares the process heap and runs with the user's OS permissions.
- **Honest UX Disclosure:** The trust gate in `PluginsPanel.kt` explicitly discloses:
  > *"Plugins execute in the Java Virtual Machine with your full user account privileges. Only enable plugins from sources you know and trust."*
- **Quarantine Invariant:** Every newly installed or upgraded plugin is placed in `PluginStatus.UNTRUSTED`. It is never loaded into memory until the user clicks **Trust & Enable**.

### 6.2 Classloader Isolation: `PluginFilteringClassLoader` (`GAP-01` Resolution)
To address the parent delegation leak identified in Phase P0, v2 replaces standard `URLClassLoader` with a custom `PluginFilteringClassLoader`:

```
               [ PluginFilteringClassLoader ]
                             │
            Does class name match allowed rules?
            ├── YES ─────────────────────────────┐
            │                                    ▼
       Standard Library / Public API?    Plugin Bundled JARs?
       - java.*, javax.swing.*, etc.     - Loads classes from
       - xdm.plugin.api.*                  plugin's own JAR
            │
            └── NO (Forbidden Prefix: xdm.app.*, xdm.core.*, xdm.manager.*)
                │
                ▼
       Throws ClassNotFoundException:
       "SecurityViolation: Plugin 'foo' attempted to access internal host class."
```

### 6.3 External Process Security (Separately Executed Adapters)
When a plugin wraps an external binary (e.g. `yt-dlp`, `ffmpeg`, CLI tools):
1. **Argument Delimitation:** Mandatory `--` delimiter prior to user-controlled URLs to prevent command-line option injection (`--output`, `--exec`).
2. **Credential Redaction:** URLs are sanitized using `sanitizeUrl(url)` before logging or process display, masking passwords and session tokens (`***:***@domain`).
3. **Process Watchdog & Lifetime Management:**
   - Child processes are registered in a concurrent process registry.
   - On download cancellation or application shutdown, `ProcessHandle.destroyForcibly()` is called to prevent orphaned background processes.

---

## 7. Lifecycle, Concurrency & Recovery Model

### 7.1 State Machine

```
      +-------------+
      | DISCOVERED  |
      +------+------+
             | (Validate manifest & archive)
             v
      +-------------+        Validation Error
      |  VALIDATED  +-------------------------> [ FAULTED / INCOMPATIBLE ]
      +------+------+
             | (Install archive)
             v
      +-------------+
      |  UNTRUSTED  | (Quarantine state)
      +------+------+
             | User confirms Trust & Enable
             v
      +-------------+        init() threw exception
      |   ACTIVE    +-------------------------> [ FAULTED ]
      +---+-----+---+
          |     |
  Disable |     | Suspend
          v     v
      +-------------+
      |  DISABLED   |
      +------+------+
             |
             v
      +-------------+
      | UNINSTALLED | (Purge directory & config)
      +-------------+
```

### 7.2 Dedicated Plugin Concurrency (`GAP-03` Resolution)
Instead of delegating to `ForkJoinPool.commonPool()`, `PluginHostContext` v2 uses a dedicated, bounded executor:
- **Pool Type:** `ThreadPoolExecutor` with a bounded queue (`ArrayBlockingQueue(256)`).
- **Sizing:** Core pool = 4 threads, Max pool = 16 threads, Keep-alive = 60s.
- **Thread Factory:** `PluginThreadFactory` generating daemon threads named `PluginWorker-<pluginId>-%d`.
- **Unhandled Exception Handler:** Logs uncaught exceptions to `Logger.error` and marks the offending plugin as `FAULTED` if thread death occurs.

### 7.3 Startup Recovery Mode (Safe Mode)
- **CLI Flags:** Launching with `--safe-mode` or `--no-plugins` immediately suppresses all third-party plugin loading.
- **Crash Loop Circuit Breaker:** If XDM crashes during startup 3 times consecutively within 60 seconds, Safe Mode is automatically triggered on the 4th launch, displaying a diagnostic dialog with offending plugin traces.

---

## 8. API Compatibility, Migration & Coexistence Policy

### 8.1 Semantic Versioning Policy
- **Host Engine:** Follows SemVer (`v1.0.2` $\rightarrow$ `v1.1.0-beta.1`).
- **Plugin API:** `xdm-plugin-api:2.0.0`.
- **Backwards Compatibility Guarantee:** Any minor release `2.x.y` will maintain binary and source compatibility for all `2.0.0` plugins.

### 8.2 V1 Plugin Coexistence via `LegacyV1PluginAdapter`
Existing plugins compiled against `xdm-plugin-api:1.0.x` (such as `checksum-verifier-plugin:1.0.2` and `ytdlp-downloader-plugin:1.0.2`) must continue running seamlessly:

```
[ Legacy v1 Plugin (.xdmplugin) ]
               │
               ▼ Implements xdm.plugin.api.XdmPlugin (v1)
┌────────────────────────────────────────────────────────┐
│             LegacyV1PluginAdapter                      │
│  - Wraps v1 plugin instance                            │
│  - Adapts v2 PluginContext to v1 PluginContext calls   │
│  - Maps PostDownloadHook to PostDownloadProcessor      │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼ Registers with v2 host
               [ PluginManager (v2) ]
```

### 8.3 Manifest Schema Migration (`plugin.json`)
The `plugin.json` schema is versioned via `manifestVersion`:
```json
{
  "manifestVersion": 2,
  "id": "my-advanced-plugin",
  "name": "Advanced Extension",
  "version": "2.0.0",
  "author": "Community Developer",
  "entryPoint": "com.example.AdvancedPlugin",
  "minAppVersion": "1.1.0-beta.1",
  "minApiVersion": "2.0.0",
  "apiCompatibility": "^2.0.0",
  "capabilities": [
    "DOWNLOAD_PROVIDER",
    "NETWORK_PROXY",
    "VPN_STATUS",
    "POST_DOWNLOAD_PROCESSOR",
    "UI_CONTRIBUTOR"
  ]
}
```
- If `manifestVersion` is missing or `1`, the validator automatically runs in **v1 Compatibility Mode**, normalizing legacy capability aliases (`URL_RECOGNIZER` $\rightarrow$ `URL_RECOGNITION`, `TOOLBAR_ACTION` $\rightarrow$ `TOOLBAR`) to resolve `GAP-02`.

---

## 9. Capability Classification Matrix

| Capability Name | Classification | Rationale & Boundaries |
|---|:---:|---|
| **Download Provider** | **CORE** | Native protocol / URL downloads; integrates with central scheduler. |
| **Post-Download Processor** | **CORE** | Sequential pipeline for checksum, anti-malware, transcoding, unarchiving. |
| **UI Action Contributors** | **CORE** | Context menus, toolbar buttons, and settings panels. |
| **Event Bus & Messaging** | **CORE** | Decoupled publish/subscribe bus for lifecycle events. |
| **Isolated Config Storage** | **CORE** | Persistent properties/JSON in private plugin directories. |
| **Network Proxy Provider** | **OPTIONAL** | Per-URL proxy resolution for PAC scripts, SOCKS tunnels, rotating proxies. |
| **VPN Telemetry & Kill-Switch** | **OPTIONAL** | State reporting and network interface selection for connection-bound tasks. |
| **Format Detection & Media Streams** | **OPTIONAL** | Custom magic byte matching and multi-track video stream extraction. |
| **Dashboard Widgets** | **OPTIONAL** | Real-time status widgets in status bar or sidebar. |
| **Kernel / TAP Network Drivers** | **OUT OF SCOPE** | Must be handled by native OS VPN software; plugins cannot install OS drivers. |
| **Global UI Chrome Replacement** | **OUT OF SCOPE** | Window decorations and core navigation cannot be replaced by plugins. |
| **Core Database SQL Overrides** | **OUT OF SCOPE** | Core schema and migrations remain host-owned and private. |

---

## 10. Proof-of-Concept Acceptance-Test Plan

The following automated acceptance test scenarios are defined for the Phase P2 implementation:

| Test ID | Scenario | Verification Criteria |
|---|---|---|
| **TC-V2-01** | **Legacy v1 Coexistence** | Verify that `checksum-verifier.xdmplugin` and `ytdlp-downloader.xdmplugin` install, activate, and execute under SDK v2 without modification or recompilation. |
| **TC-V2-02** | **Classloader Isolation Boundary** | Verify that a test plugin attempting `Class.forName("xdm.app.AppContext")` or `Class.forName("xdm.core.downloaders.HttpDownloader")` throws `ClassNotFoundException`. |
| **TC-V2-03** | **ProxyProvider Dynamic Resolution** | Register a mock `ProxyProvider` returning `SOCKS5:127.0.0.1:1080` for `*.onion` domains; verify core engine routes request to proxy and uses `DIRECT` for regular URLs. |
| **TC-V2-04** | **VpnStatusProvider & Kill-Switch** | Simulate VPN disconnect event from `VpnStatusProvider`; verify active plugin and HTTP downloads transition to `PAUSED` automatically. |
| **TC-V2-05** | **PostDownloadProcessor Pipeline** | Register two sequential processors: Processor A computes checksum; Processor B renames file. Verify output file on disk reflects modifications in the download database. |
| **TC-V2-06** | **Dedicated Worker Pool Isolation** | Run a task in `executeAsync` that performs a 5-second sleep; verify host UI remains responsive and JVM `ForkJoinPool` is unaffected. |
| **TC-V2-07** | **External Process Watchdog** | Spawn a long-running dummy CLI process via `YtDlpDownloadTask`; cancel task and verify process PID is completely terminated. |
| **TC-V2-08** | **Circuit Breaker Safe Mode Recovery** | Simulate 3 consecutive crash events during plugin initialization; verify 4th launch initializes with Safe Mode enabled and all plugins quarantined. |

---

## 11. Threat-Model & Security Evaluation

### 11.1 Threat Actors
1. **Malicious Plugin Author:** Deliberately packages malicious code attempting to read user files, inject network traffic, or hijack downloads.
2. **Compromised Dependency:** Third-party plugin includes a compromised transitive library.
3. **Buggy / Careless Plugin:** Causes CPU spikes, deadlocks, EDT freezing, or disk exhaustion.

### 11.2 Attack Vectors & Defense Architecture

```
+---------------------------+-----------------------------------+---------------------------------------------+
| Threat / Attack Vector    | Target Subsystem                  | Mitigation / Architectural Defense          |
+---------------------------+-----------------------------------+---------------------------------------------+
| Zip Slip Path Traversal   | Package Extraction                | Strict entry path verification in           |
|                           |                                   | PluginManifestValidator (rejects '..', ':') |
+---------------------------+-----------------------------------+---------------------------------------------+
| Native Executable Injection| Filesystem                        | Rejection of .exe, .dll, .so, .sh, .bat     |
|                           |                                   | in archive entries                          |
+---------------------------+-----------------------------------+---------------------------------------------+
| Argument Injection        | CLI Process Adapters (yt-dlp)     | Mandatory '--' argument separator and       |
|                           |                                   | array-based ProcessBuilder execution        |
+---------------------------+-----------------------------------+---------------------------------------------+
| Credential Leakage        | Application Logs / UI             | URL credential stripping via sanitizeUrl()  |
|                           |                                   | (masks user:pass and auth tokens)           |
+---------------------------+-----------------------------------+---------------------------------------------+
| Host Class Hijacking      | ClassLoader / Reflection          | PluginFilteringClassLoader blocks access to |
|                           |                                   | xdm.app.* and xdm.core.*                    |
+---------------------------+-----------------------------------+---------------------------------------------+
| Thread Pool Starvation    | Concurrency Subsystem             | Dedicated PluginThreadPoolExecutor          |
|                           |                                   | isolating plugin tasks from common pool     |
+---------------------------+-----------------------------------+---------------------------------------------+
| Startup Crash Loop        | Host Application Lifecycle        | Automated Circuit Breaker triggers Safe     |
|                           |                                   | Mode after 3 consecutive startup crashes    |
+---------------------------+-----------------------------------+---------------------------------------------+
| Uncaught Hook Exceptions  | Event Dispatcher / Download Queue | Fault isolation wrappers around every hook  |
|                           |                                   | (quarantines failing plugin as FAULTED)     |
+---------------------------+-----------------------------------+---------------------------------------------+
```
