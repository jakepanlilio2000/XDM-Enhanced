package xdm.plugin.api.events;

import xdm.plugin.api.models.DownloadInfo;

public interface PostDownloadHook {
    void onDownloadCompleted(DownloadInfo download);
}

