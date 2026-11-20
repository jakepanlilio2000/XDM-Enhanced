package xdm.plugin.api.events;

import xdm.plugin.api.models.DownloadInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured event payload representing a user selection change in the download list or table.
 */
public class SelectionChangeEvent {

    private final List<DownloadInfo> selectedDownloads;
    private final long timestamp;

    public SelectionChangeEvent(List<DownloadInfo> selectedDownloads) {
        if (selectedDownloads != null && !selectedDownloads.isEmpty()) {
            this.selectedDownloads = Collections.unmodifiableList(new ArrayList<DownloadInfo>(selectedDownloads));
        } else {
            this.selectedDownloads = Collections.emptyList();
        }
        this.timestamp = System.currentTimeMillis();
    }

    public List<DownloadInfo> getSelectedDownloads() {
        return selectedDownloads;
    }

    public int getSelectedCount() {
        return selectedDownloads.size();
    }

    public boolean isEmpty() {
        return selectedDownloads.isEmpty();
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "SelectionChangeEvent{" +
                "count=" + selectedDownloads.size() +
                ", timestamp=" + timestamp +
                '}';
    }
}
