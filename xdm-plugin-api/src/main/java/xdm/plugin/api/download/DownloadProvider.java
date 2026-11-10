package xdm.plugin.api.download;

import xdm.plugin.api.models.PluginDownloadRequest;

public interface DownloadProvider {
    String getName();
    boolean canHandle(String url);
    PluginDownloadTask createDownloadTask(PluginDownloadRequest request) throws Exception;
}

