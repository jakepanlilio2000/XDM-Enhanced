package xdm.plugin.api.events;

import xdm.plugin.api.models.DownloadInfo;

/**
 * Structured event payload representing a lifecycle state change for a download task.
 */
public class TaskLifecycleEvent {

    public enum Type {
        QUEUED,
        STARTED,
        PROGRESSED,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELED
    }

    private final Type type;
    private final DownloadInfo download;
    private final long timestamp;
    private final String errorMessage;
    private final Throwable cause;

    public TaskLifecycleEvent(Type type, DownloadInfo download) {
        this(type, download, null, null);
    }

    public TaskLifecycleEvent(Type type, DownloadInfo download, String errorMessage) {
        this(type, download, errorMessage, null);
    }

    public TaskLifecycleEvent(Type type, DownloadInfo download, String errorMessage, Throwable cause) {
        this.type = type != null ? type : Type.QUEUED;
        this.download = download;
        this.errorMessage = errorMessage != null ? errorMessage : "";
        this.cause = cause;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() {
        return type;
    }

    public DownloadInfo getDownload() {
        return download;
    }

    public long getTaskId() {
        return download != null ? download.getId() : -1L;
    }

    public String getUrl() {
        return download != null ? download.getUrl() : "";
    }

    public String getFileName() {
        return download != null ? download.getFileName() : "";
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "TaskLifecycleEvent{" +
                "type=" + type +
                ", taskId=" + getTaskId() +
                ", fileName='" + getFileName() + '\'' +
                ", timestamp=" + timestamp +
                (errorMessage.isEmpty() ? "" : ", error='" + errorMessage + '\'') +
                '}';
    }
}
