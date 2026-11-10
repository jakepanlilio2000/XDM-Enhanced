package xdm.plugin.api.download;

/**
 * Listener receiving structured real-time progress events from an active download task.
 */
@FunctionalInterface
public interface DownloadProgressListener {
    /**
     * Invoked when download progress, transfer speed, completion state, or status message changes.
     *
     * @param event Structured event containing current metrics
     */
    void onProgress(DownloadProgressEvent event);
}

