package xdm.plugin.api.download;

/**
 * Structured event carrying real-time progress details from a download task to the host application.
 */
public class DownloadProgressEvent {
    private final long downloadedBytes;
    private final long totalBytes;
    private final float speedBytesPerSec;
    private final long etaSeconds;
    private final PluginDownloadTask.TaskState state;
    private final String message;
    private final int explicitProgressPercent;

    public DownloadProgressEvent(
            long downloadedBytes,
            long totalBytes,
            float speedBytesPerSec,
            long etaSeconds,
            PluginDownloadTask.TaskState state,
            String message) {
        this(downloadedBytes, totalBytes, speedBytesPerSec, etaSeconds, state, message, -1);
    }

    public DownloadProgressEvent(
            long downloadedBytes,
            long totalBytes,
            float speedBytesPerSec,
            long etaSeconds,
            PluginDownloadTask.TaskState state,
            String message,
            int progressPercent) {
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.speedBytesPerSec = speedBytesPerSec;
        this.etaSeconds = etaSeconds;
        this.state = state;
        this.message = message != null ? message : "";
        this.explicitProgressPercent = progressPercent;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public float getSpeedBytesPerSec() {
        return speedBytesPerSec;
    }

    public long getEtaSeconds() {
        return etaSeconds;
    }

    public PluginDownloadTask.TaskState getState() {
        return state;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Calculates or returns the integer completion percentage between 0 and 100.
     */
    public int getProgressPercent() {
        if (explicitProgressPercent >= 0) {
            return Math.min(100, Math.max(0, explicitProgressPercent));
        }
        if (totalBytes <= 0) return 0;
        return (int) Math.min(100, Math.max(0, (downloadedBytes * 100) / totalBytes));
    }

    @Override
    public String toString() {
        return "DownloadProgressEvent{" +
                "progress=" + getProgressPercent() + "%" +
                ", downloaded=" + downloadedBytes +
                ", total=" + totalBytes +
                ", speed=" + speedBytesPerSec + " B/s" +
                ", eta=" + etaSeconds + "s" +
                ", state=" + state +
                ", message='" + message + '\'' +
                '}';
    }
}

