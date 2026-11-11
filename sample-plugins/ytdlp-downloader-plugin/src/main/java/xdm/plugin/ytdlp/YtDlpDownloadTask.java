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

        String execPath = config.getExecutablePath();
        if (execPath == null || execPath.trim().isEmpty()) {
            state = TaskState.FAILED;
            errorMessage = "Failed to start yt-dlp: executable path is not configured.";
            notifyProgress(errorMessage);
            context.logError(errorMessage, null);
            return;
        }

        if (execPath.contains("/") || execPath.contains("\\")) {
            File f = new File(execPath.trim());
            if (!f.exists() || f.isDirectory()) {
                state = TaskState.FAILED;
                errorMessage = "Failed to start yt-dlp: executable not found: " + f.getAbsolutePath();
                notifyProgress(errorMessage);
                context.logError(errorMessage, null);
                return;
            }
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
                notifyProgress("Download completed successfully.", 100);
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

        String fn = request.getFileName();
        String template;
        if (fn != null && !fn.trim().isEmpty() && !fn.equalsIgnoreCase("watch") && !fn.endsWith(".html") && !fn.endsWith(".htm") && fn.contains(".")) {
            template = fn.trim();
        } else {
            template = "%(title)s.%(ext)s";
        }
        cmd.add("-o");
        cmd.add(template);

        // Structured progress template: percent#total_bytes#speed#eta#downloaded_bytes
        cmd.add("--progress-template");
        cmd.add("download:%(progress._percent_str)s#%(progress._total_bytes_str)s#%(progress._speed_str)s#%(progress._eta_str)s#%(progress._downloaded_bytes_str)s");

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
            // Format: download: 45.2%#150.2MiB#12.5MiB/s#00:15 or with pipes: ...|...
            String payload = line.substring("download:".length()).trim();
            String delimiter = payload.contains("#") ? "#" : "\\|";
            String[] parts = payload.split(delimiter);
            if (parts.length >= 4) {
                String percentStr = parts[0].replace("%", "").replace("~", "").trim();
                String totalStr = parts[1].replace("~", "").trim();
                String speedStr = parts[2].replace("~", "").trim();
                String etaStr = parts[3].replace("~", "").trim();

                try {
                    double pct = Double.parseDouble(percentStr);
                    int intPct = (int) Math.round(pct);
                    long total = parseBytes(totalStr);
                    if (total > 0) {
                        this.totalBytes = total;
                        this.downloadedBytes = (long) ((pct / 100.0) * total);
                    }
                    if (parts.length >= 5) {
                        long dl = parseBytes(parts[4].replace("~", "").trim());
                        if (dl > 0) {
                            this.downloadedBytes = dl;
                        }
                    } else if (this.downloadedBytes == 0 && total > 0) {
                        this.downloadedBytes = (long) ((pct / 100.0) * total);
                    }
                    this.transferSpeed = parseSpeed(speedStr);
                    this.etaSeconds = parseEta(etaStr);

                    notifyProgress("Downloading: " + intPct + "%", intPct);
                } catch (Exception ignored) {}
            }
        } else if (line.contains("Destination:")) {
            String pathPart = line.substring(line.indexOf("Destination:") + "Destination:".length()).trim();
            resolveDestination(pathPart, targetDir);
        } else if (line.contains("Merging formats into")) {
            String pathPart = line.substring(line.indexOf("into") + "into".length()).replace("\"", "").trim();
            resolveDestination(pathPart, targetDir);
        } else if (line.contains("has already been downloaded")) {
            String pathPart = line.replace("[download]", "").replace("has already been downloaded", "").trim();
            resolveDestination(pathPart, targetDir);
        } else if (line.startsWith("ERROR:") || line.toLowerCase(Locale.ROOT).contains("error:")) {
            this.errorMessage = line;
        }
    }

    private void resolveDestination(String pathPart, File targetDir) {
        if (pathPart == null || pathPart.trim().isEmpty()) return;
        pathPart = pathPart.trim().replace("\"", "");
        File resolved = new File(pathPart);
        if (!resolved.isAbsolute()) {
            resolved = new File(targetDir, pathPart);
        }
        this.outputFile = resolved;
        notifyProgress("Saving to: " + resolved.getName(), -1);
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
        notifyProgress(message, -1);
    }

    private void notifyProgress(String message, int explicitPercent) {
        DownloadProgressEvent event = new DownloadProgressEvent(
                downloadedBytes,
                totalBytes,
                transferSpeed,
                etaSeconds,
                state,
                message,
                explicitPercent
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
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canResume() {
        return true;
    }

    @Override
    public synchronized void pause() {
        if (state == TaskState.DOWNLOADING) {
            isCancelled.set(true);
            state = TaskState.PAUSED;
            cleanupProcess();
            if (workerThread != null && workerThread.isAlive()) {
                workerThread.interrupt();
            }
            notifyProgress("Paused by user.");
        }
    }

    @Override
    public synchronized void resume() {
        if (state == TaskState.PAUSED || state == TaskState.READY) {
            start();
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

    public static String sanitizeUrl(String url) {
        if (url == null) return "";
        String sanitized = url;
        try {
            int atIndex = sanitized.indexOf('@');
            int slashIndex = sanitized.indexOf("://");
            if (slashIndex > 0 && atIndex > slashIndex + 3) {
                String prefix = sanitized.substring(0, slashIndex + 3);
                String rest = sanitized.substring(atIndex + 1);
                sanitized = prefix + "***:***@" + rest;
            }
        } catch (Exception ignored) {}
        int q = sanitized.indexOf('?');
        return q > 0 ? sanitized.substring(0, q) : sanitized;
    }

    public static long parseBytes(String str) {
        if (str == null) return -1;
        str = str.replace("~", "").trim().toUpperCase(Locale.ROOT);
        if (str.isEmpty() || str.equals("NA") || str.equals("UNKNOWN") || str.contains("N/A")) return -1;
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
        str = str.replace("~", "").trim().toUpperCase(Locale.ROOT);
        if (str.isEmpty() || str.equals("NA") || str.equals("UNKNOWN") || str.contains("N/A")) return 0f;
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
        str = str.replace("~", "").trim();
        if (str.isEmpty() || str.equals("NA") || str.equals("UNKNOWN") || str.contains("N/A")) return -1;
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
