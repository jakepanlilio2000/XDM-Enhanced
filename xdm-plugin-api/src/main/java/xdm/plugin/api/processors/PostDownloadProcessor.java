package xdm.plugin.api.processors;

import xdm.plugin.api.models.DownloadInfo;
import java.io.File;

/**
 * Service Provider Interface for sequential post-download processing pipelines.
 * Unlike observational PostDownloadHook, PostDownloadProcessor can inspect, verify,
 * extract, rename, or reject downloaded files.
 */
public interface PostDownloadProcessor {

    /**
     * Unique identifier for this processor.
     */
    String getName();

    /**
     * Execution order in the processing pipeline (lower order runs earlier).
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Whether this processor can process the specified completed download.
     */
    default boolean canProcess(DownloadInfo download, File downloadedFile) {
        return true;
    }

    /**
     * Executes the post-download processing step.
     *
     * @param download Snapshot of the completed download record
     * @param downloadedFile The file on disk
     * @return ProcessingResult indicating success, file modification, rejection, or skip
     * @throws Exception on execution failure
     */
    ProcessingResult process(DownloadInfo download, File downloadedFile) throws Exception;
}
