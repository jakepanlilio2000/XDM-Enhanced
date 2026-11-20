# Provider Task Integration & Subprocess Safety

Learn how to implement custom download providers via the **SDK v2 `DownloadProvider` SPI** and safely bridge external media engines or subprocesses to XDM Enhanced.

---

## 1. The Central Download Provider Architecture

In SDK v2, download providers register directly through `PluginContext.registerDownloadProvider`:

```
[ External Engine / yt-dlp Subprocess ]
                   │
                   ▼ (Progress & Status Events)
┌──────────────────────────────────────────────┐
│ DownloadProvider & PluginDownloadTask        │
│   ├── canHandle(url) -> boolean              │
│   ├── createTask(url, destDir, options)      │
│   └── start(), pause(), resume(), cancel()   │
└──────────────────────────────────────────────┘
                   │
                   ▼
[ Main Transfer Table / Classic Cards / Tray ]
```

---

## 2. Implementing `DownloadProvider` and `PluginDownloadTask`

### 2.1 Implementing `DownloadProvider`

```java
package com.example.plugin;

import xdm.plugin.api.download.*;
import java.io.File;
import java.util.Map;

public class MyDownloadProvider implements DownloadProvider {
    @Override
    public String getId() {
        return "my-downloader";
    }

    @Override
    public String getName() {
        return "My Custom Media Downloader";
    }

    @Override
    public boolean canHandle(String url) {
        return url != null && (url.contains("example.com/video/") || url.contains("myservice.org/media/"));
    }

    @Override
    public PluginDownloadTask createTask(String url, File destinationDir, Map<String, Object> options) {
        return new MyDownloadTask(url, destinationDir, options);
    }
}
```

### 2.2 Implementing `PluginDownloadTask`

```java
package com.example.plugin;

import xdm.plugin.api.download.*;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class MyDownloadTask implements PluginDownloadTask {
    private final String id = UUID.randomUUID().toString();
    private final String url;
    private final File destinationDir;
    private final List<TaskProgressListener> listeners = new CopyOnWriteArrayList<>();
    private volatile DownloadStatus status = DownloadStatus.QUEUED;

    public MyDownloadTask(String url, File destinationDir, Map<String, Object> options) {
        this.url = url;
        this.destinationDir = destinationDir;
    }

    @Override public String getId() { return id; }
    @Override public String getUrl() { return url; }
    @Override public File getDestinationFile() { return new File(destinationDir, "download.mp4"); }
    @Override public DownloadStatus getStatus() { return status; }

    @Override
    public void addListener(TaskProgressListener listener) {
        listeners.add(listener);
    }

    @Override
    public void start() {
        this.status = DownloadStatus.DOWNLOADING;
        // Launch asynchronous transfer and notify listeners
    }

    @Override public void pause() { this.status = DownloadStatus.PAUSED; }
    @Override public void resume() { this.status = DownloadStatus.DOWNLOADING; }
    @Override public void cancel() { this.status = DownloadStatus.CANCELLED; }
}
```

---

## 3. Subprocess Execution Safety Guidelines

When invoking external helper binaries (e.g. `yt-dlp`, `ffmpeg`, `aria2c`):

### 1. Reject Command-Line String Concatenation:
```java
// VULNERABLE TO COMMAND INJECTION — NEVER DO THIS:
Runtime.getRuntime().exec("yt-dlp.exe " + userSuppliedUrl);
```

### 2. Use `ProcessBuilder` with Explicit Argument Arrays:
```java
// SAFE — Arguments passed as discrete array elements:
List<String> command = new ArrayList<>();
command.add(pathToExecutable);
command.add("--newline");
command.add("--no-colors");
command.add("--output");
command.add(destinationPath + "/%(title)s.%(ext)s");
command.add(userSuppliedUrl);

ProcessBuilder pb = new ProcessBuilder(command);
pb.redirectErrorStream(true); // Merge stderr into stdout
Process process = pb.start();
```

### 3. Consume Output Streams Asynchronously:
Never read process output synchronously on the calling thread. Always consume `process.getInputStream()` on a dedicated daemon thread to prevent OS pipe buffer deadlocks:

```java
Thread readerThread = new Thread(() -> {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
            parseProgressFromOutput(line);
        }
    } catch (IOException e) {
        context.logError("Failed reading process output", e);
    }
});
readerThread.setDaemon(true);
readerThread.start();
```

---

## Related Developer Articles & Official Guides

* Detailed Guide: `docs/PLUGIN_DEVELOPMENT.md`
* Complete API Reference: `docs/PLUGIN_API_REFERENCE.md`
* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Building your plugin.
* [[Plugin API Version Compatibility|Plugin-API-Version-Compatibility]]: Ensuring version range safety.
* Sample implementation: `sample-plugins/ytdlp-downloader-plugin/`.
