package xdm.plugin.ytdlp;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.download.DownloadProgressEvent;
import xdm.plugin.api.download.DownloadProgressListener;
import xdm.plugin.api.download.PluginDownloadTask;
import xdm.plugin.api.models.PluginDownloadRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles an authorized media download using yt-dlp with structured process execution.
 */
public class YtDlpDownloadTask implements PluginDownloadTask {

    private final PluginContext context;
    private final YtDlpConfig config;
    private final PluginDownloadRequest request;
    private final List<DownloadProgressListener> listeners = new CopyOnWriteArrayList<>();

    private volatile TaskState state = TaskState.READY;
    private volatile long downloadedBytes = 0;
    private volatile long totalBytes = -1;
    private volatile float transferSpeed = 0f;
    private volatile long etaSeconds = -1;
    private volatile String errorMessage = null;
    private volatile File outputFile = null;

    private Process process = null;
    private Thread workerThread = null;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public YtDlpDownloadTask(PluginContext context, YtDlpConfig config, PluginDownloadRequest request) {
        this.context = context;
        this.config = config;
        this.request = request;
    }

    @Override
    public synchronized void start() {
        if (state == TaskState.DOWNLOADING || state == TaskState.FINISHED || state == TaskState.CANCELED) return;

        state = TaskState.DOWNLOADING;
        isCancelled.set(false);

        workerThread = new Thread(this::runProcess, "YtDlpTask-" + System.currentTimeMillis());
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void runProcess() {
        File targetDir = determineTargetDirectory();
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        List<String> command = buildCommand(targetDir);
        context.logInfo("Executing yt-dlp structured command for: " + sanitizeUrl(request.getUrl()));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isCancelled.get()) break;
                    parseOutputLine(line.trim(), targetDir);
                }
            }

            int exitCode = process.waitFor();

            if (isCancelled.get()) {
                state = TaskState.CANCELED;
                errorMessage = "Download canceled by user.";
                notifyProgress("Download canceled.");
            } else if (exitCode == 0) {
                state = TaskState.FINISHED;
                downloadedBytes = totalBytes > 0 ? totalBytes : downloadedBytes;
                transferSpeed = 0f;
                etaSeconds = 0;
                notifyProgress("Download completed successfully.");
                context.logInfo("yt-dlp download completed successfully for: " + sanitizeUrl(request.getUrl()));
            } else {
                state = TaskState.FAILED;
                if (errorMessage == null) {
                    errorMessage = "yt-dlp exited with error code " + exitCode;
                }
                notifyProgress("Download failed: " + errorMessage);
                context.logError("yt-dlp error: " + errorMessage, null);
            }
        } catch (InterruptedException e) {
            state = TaskState.CANCELED;
            errorMessage = "Operation interrupted.";
            notifyProgress("Download canceled.");
        } catch (Exception e) {
            if (isCancelled.get()) {
                state = TaskState.CANCELED;
                errorMessage = "Download canceled by user.";
                notifyProgress("Download canceled.");
            } else {
                state = TaskState.FAILED;
                errorMessage = "Failed to start yt-dlp: " + e.getMessage();
                notifyProgress("Download error: " + errorMessage);
                context.logError("Process execution error: " + errorMessage, e);
            }
        } finally {
            cleanupProcess();
        }
    }

    private List<String> buildCommand(File targetDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.getExecutablePath());
        cmd.add("--newline");
        cmd.add("--no-colors");
        cmd.add("--no-playlist");

        // Format selection
        String format = config.getFormatPreference();
        if (format != null && !format.isEmpty()) {
            cmd.add("-f");
            cmd.add(format);
        }

        // Audio extraction if requested
        if (config.isExtractAudio()) {
            cmd.add("-x");
            cmd.add("--audio-format");
            cmd.add(config.getAudioFormat());
        }

        // Target directory and naming template
        cmd.add("-P");
        cmd.add(targetDir.getAbsolutePath());

        String template = (request.getFileName() != null && !request.getFileName().trim().isEmpty())
                ? request.getFileName()
                : "%(title)s.%(ext)s";
        cmd.add("-o");
        cmd.add(template);

        // Structured progress template
        cmd.add("--progress-template");
        cmd.add("download:%(progress._percent_str)s|%(progress._total_bytes_str)s|%(progress._speed_str)s|%(progress._eta_str)s");

        // End of options delimiter prevents argument injection
        cmd.add("--");
        cmd.add(request.getUrl());

        return cmd;
    }

    /**
     * Parses structured output lines from yt-dlp process stdout.
     */
    public void processOutputLine(String line) {
        if (line != null) {
            parseOutputLine(line.trim(), determineTargetDirectory());
        }
    }

    private void parseOutputLine(String line, File targetDir) {
        if (line.startsWith("download:")) {
            // Format: download: 45.2%|150.2MiB|12.5MiB/s|00:15
            String payload = line.substring("download:".length()).trim();
            String[] parts = payload.split("\\|");
            if (parts.length >= 4) {
                String percentStr = parts[0].replace("%", "").trim();
                String totalStr = parts[1].trim();
                String speedStr = parts[2].trim();
                String etaStr = parts[3].trim();

                try {
                    double pct = Double.parseDouble(percentStr);
                    long total = parseBytes(totalStr);
                    if (total > 0) {
                        this.totalBytes = total;
                        this.downloadedBytes = (long) ((pct / 100.0) * total);
                    }
                    this.transferSpeed = parseSpeed(speedStr);
                    this.etaSeconds = parseEta(etaStr);

                    notifyProgress("Downloading: " + (int) pct + "%");
                } catch (Exception ignored) {}
            }
        } else if (line.contains("[download] Destination:") || line.contains("[Merger] Merging formats into")) {
            // Resolve output filename from stdout
            String pathPart = line.contains("Destination:")
                    ? line.substring(line.indexOf("Destination:") + "Destination:".length()).trim()
                    : line.substring(line.indexOf("into") + "into".length()).replace("\"", "").trim();
            File resolved = new File(pathPart);
            if (!resolved.isAbsolute()) {
                resolved = new File(targetDir, pathPart);
            }
            this.outputFile = resolved;
            notifyProgress("Saving to: " + resolved.getName());
        } else if (line.startsWith("ERROR:") || line.toLowerCase(Locale.ROOT).contains("error:")) {
            this.errorMessage = line;
        }
    }

    private File determineTargetDirectory() {
        if (request.getDestinationFolder() != null && !request.getDestinationFolder().trim().isEmpty()) {
            return new File(request.getDestinationFolder().trim());
        }
        if (!config.getOutputDirectory().isEmpty()) {
            return new File(config.getOutputDirectory());
        }
        return context.getDataDirectory();
    }

    private void notifyProgress(String message) {
        DownloadProgressEvent event = new DownloadProgressEvent(
                downloadedBytes,
                totalBytes,
                transferSpeed,
                etaSeconds,
                state,
                message
        );
        for (DownloadProgressListener listener : listeners) {
            try {
                listener.onProgress(event);
            } catch (Throwable t) {
                context.logError("Error in progress listener: " + t.getMessage(), t);
            }
        }
    }

    @Override
    public synchronized void cancel() {
        if (state == TaskState.FINISHED || state == TaskState.CANCELED) return;

        isCancelled.set(true);
        state = TaskState.CANCELED;

        cleanupProcess();

        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }

        notifyProgress("Canceled by user.");
    }

    private synchronized void cleanupProcess() {
        if (process != null) {
            try {
                if (process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
            } catch (Exception ignored) {}
            process = null;
        }
    }

    @Override
    public TaskState getState() {
        return state;
    }

    @Override
    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    @Override
    public long getTotalBytes() {
        return totalBytes;
    }

    @Override
    public float getTransferSpeed() {
        return transferSpeed;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public void addProgressListener(DownloadProgressListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void removeProgressListener(DownloadProgressListener listener) {
        listeners.remove(listener);
    }

    private static String sanitizeUrl(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, q) : url;
    }

    public static long parseBytes(String str) {
        if (str == null) return -1;
        str = str.trim().toUpperCase(Locale.ROOT);
        if (str.isEmpty() || str.equals("NA") || str.equals("UNKNOWN")) return -1;
        try {
            if (str.endsWith("GIB") || str.endsWith("GB")) {
                double val = Double.parseDouble(str.replace("GIB", "").replace("GB", "").trim());
                return (long) (val * 1024L * 1024L * 1024L);
            } else if (str.endsWith("MIB") || str.endsWith("MB")) {
                double val = Double.parseDouble(str.replace("MIB", "").replace("MB", "").trim());
                return (long) (val * 1024L * 1024L);
            } else if (str.endsWith("KIB") || str.endsWith("KB")) {
                double val = Double.parseDouble(str.replace("KIB", "").replace("KB", "").trim());
                return (long) (val * 1024L);
            } else if (str.endsWith("B")) {
                return Long.parseLong(str.replace("B", "").trim());
            } else {
                return (long) Double.parseDouble(str);
            }
        } catch (Exception e) {
            return -1;
        }
    }

    public static float parseSpeed(String str) {
        if (str == null) return 0f;
        str = str.trim().toUpperCase(Locale.ROOT);
        if (str.isEmpty() || str.equals("NA") || str.equals("UNKNOWN")) return 0f;
        try {
            if (str.endsWith("/S")) {
                str = str.substring(0, str.length() - 2).trim();
            }
            long bytes = parseBytes(str);
            return bytes > 0 ? (float) bytes : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    public static long parseEta(String str) {
        if (str == null) return -1;
        str = str.trim();
        if (str.isEmpty() || str.equals("NA") || str.equals("UNKNOWN")) return -1;
        try {
            String[] parts = str.split(":");
            if (parts.length == 2) {
                return Long.parseLong(parts[0]) * 60L + Long.parseLong(parts[1]);
            } else if (parts.length == 3) {
                return Long.parseLong(parts[0]) * 3600L + Long.parseLong(parts[1]) * 60L + Long.parseLong(parts[2]);
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
