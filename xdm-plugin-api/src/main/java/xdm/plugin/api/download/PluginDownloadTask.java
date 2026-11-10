package xdm.plugin.api.download;

import java.io.File;

/**
 * Contract representing an active or queued download managed by a third-party provider.
 */
public interface PluginDownloadTask {
    enum TaskState {
        READY,
        DOWNLOADING,
        PAUSED,
        FINISHED,
        FAILED,
        CANCELED
    }

    /**
     * Starts or resumes the download process.
     */
    void start();

    /**
     * Pauses the download if supported by the provider.
     */
    default void pause() {}

    /**
     * Resumes the paused download.
     */
    default void resume() {}

    /**
     * Cancels the download and terminates associated processes or network connections.
     */
    void cancel();

    /**
     * Current state of the download task.
     */
    TaskState getState();

    /**
     * Total number of bytes downloaded so far.
     */
    long getDownloadedBytes();

    /**
     * Total expected size of the download in bytes, or -1 if unknown.
     */
    long getTotalBytes();

    /**
     * Instantaneous or moving average transfer speed in bytes per second.
     */
    float getTransferSpeed();

    /**
     * Error description if [getState] is [TaskState.FAILED], or null otherwise.
     */
    String getErrorMessage();

    /**
     * Resolved output file on disk, or null if not yet resolved.
     */
    default File getOutputFile() {
        return null;
    }

    /**
     * Subscribes a listener to receive real-time progress events.
     */
    default void addProgressListener(DownloadProgressListener listener) {}

    /**
     * Unsubscribes a progress listener.
     */
    default void removeProgressListener(DownloadProgressListener listener) {}
}
