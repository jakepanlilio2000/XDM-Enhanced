# Provider Task Integration & Subprocess Safety

Learn how to route external download tasks into the central **XDM Enhanced** transfer scheduler and invoke subprocesses safely.

---

## 1. The Central Download Task Bridge

The `PluginDownloadBridge` acts as an adapter connecting third-party download providers (such as `yt-dlp` or custom cloud clients) directly into the main desktop interface:

```
[ External Provider / yt-dlp Subprocess ]
                   │
                   ▼ (Progress & Status Events)
┌──────────────────────────────────────────────┐
│ PluginDownloadBridge                         │
│   ├── Maps PluginDownloadTask to native Task │
│   ├── Assigns stable task UUID               │
│   └── Sets category to CAT_VIDEOS            │
└──────────────────────────────────────────────┘
                   │
                   ▼
[ Main Transfer Table / Classic Cards / Tray ]
```

---

## 2. Registering and Submitting a Task

To add a task to the central download list:

```java
import xdm.plugin.api.models.PluginDownloadTask;

public void initiateTransfer(String mediaUrl, String targetDirectory) {
    String taskId = "ytdlp-" + System.currentTimeMillis();
    String estimatedFileName = "Video Stream.mp4";
    long initialSize = 0L; // 0 if unknown prior to extraction

    PluginDownloadTask task = new PluginDownloadTask(
        taskId,
        estimatedFileName,
        mediaUrl,
        targetDirectory,
        initialSize
    );

    // Submits the task to the host scheduler
    context.getTaskBridge().submitTask(task);
}
```

---

## 3. Synchronizing Progress & Status Events

As your background worker receives byte updates from the network or a subprocess:

```java
import xdm.plugin.api.events.PluginProgressEvent;

public void onChunkReceived(String taskId, long downloadedBytes, long totalBytes, double speedBytesPerSec) {
    int percent = (int) ((downloadedBytes * 100) / totalBytes);

    PluginProgressEvent event = new PluginProgressEvent(
        taskId,
        percent,
        downloadedBytes,
        totalBytes,
        speedBytesPerSec
    );

    // Publishes event to the decoupled event bus
    context.getEventBus().publish(event);
}
```

---

## 4. Subprocess Execution Safety Guidelines

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
            parseProgressFromOutput(taskId, line);
        }
    } catch (IOException e) {
        context.getLogger().error("Failed reading process output", e);
    }
});
readerThread.setDaemon(true);
readerThread.start();
```

---

## Related Developer Articles

* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Building your plugin.
* [[Plugin API Version Compatibility|Plugin-API-Version-Compatibility]]: Ensuring version range safety.
* Reference implementation: `xdm-app/src/main/java/xdm/app/plugins/PluginDownloadBridge.kt`.

