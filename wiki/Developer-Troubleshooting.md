# Developer Troubleshooting & Debugging Guide

Procedures for diagnosing thread deadlocks, memory pressure, classloader conflicts, and subprocess pipe blocks when developing for **XDM Enhanced**.

---

## 1. Diagnosing Event Dispatch Thread (EDT) Freezes

Swing interfaces freeze whenever long-running network or disk operations are mistakenly executed on the Event Dispatch Thread (EDT).

### Generating a Thread Dump:
To inspect what is blocking the UI:
1. Locate the running XDM process ID (PID):
   ```bash
   jps -l
   ```
2. Generate a thread dump using `jstack`:
   ```bash
   jstack <PID> > thread-dump.txt
   ```
3. Search for the `AWT-EventQueue-0` thread. If the stack trace shows socket reads, disk I/O, or `Process.waitFor()`, wrap that operation in an asynchronous worker thread:

```kotlin
// FIX: Dispatch blocking operations to a daemon thread
AppExecutor.ioPool.execute {
    val result = performBlockingNetworkQuery()
    SwingUtilities.invokeLater {
        updateUiComponent(result)
    }
}
```

---

## 2. Diagnosing Memory Leaks During Multi-Segment Transfers

During sustained gigabit downloads with 32 parallel streams, excessive memory consumption usually indicates buffered chunk retention:

1. Capture a heap dump:
   ```bash
   jcmd <PID> GC.heap_dump heap.hprof
   ```
2. Open `heap.hprof` in Eclipse Memory Analyzer (MAT) or VisualVM.
3. Verify that `ChunkBufferPool` recycles byte arrays rather than continuously allocating new `ByteArray` instances.

---

## 3. Debugging Plugin ClassLoader Conflicts

If an installed plugin throws `ClassNotFoundException`, `NoClassDefFoundError`, or `LinkageError`:

1. **Check Import Scope:** Ensure the plugin does not bundle duplicate copies of `xdm-plugin-api.jar` inside its own archive (it must be marked `<scope>provided</scope>` in Maven).
2. **Inspect Child ClassLoader:** Plugins load in isolated child classloaders. If your plugin requires external libraries (e.g. Jackson or Apache Commons), use the Maven Shade Plugin to relocate/shade those packages:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>com.fasterxml.jackson</pattern>
                        <shadedPattern>com.example.shaded.jackson</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 4. Subprocess Pipe Deadlocks

When running external helper binaries (e.g. `yt-dlp`):
- Operating system pipes typically have a small buffer (4 KB to 64 KB).
- If the subprocess writes verbose logs to `stdout` or `stderr` and the Java host does not read the stream immediately, the subprocess blocks permanently, freezing both processes.
- **Solution:** Always merge error streams (`redirectErrorStream(true)`) and consume output on a background daemon thread ([[Provider Task Integration|Provider-Task-Integration]]).

---

## Related Developer Articles

* [[Building from Source|Building-from-Source]]: Running tests and building debug builds.
* [[Architecture Overview|Architecture-Overview]]: Concurrency model.

