package xdm.plugin.ytdlp;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.download.DownloadProvider;
import xdm.plugin.api.download.PluginDownloadTask;
import xdm.plugin.api.models.PluginDownloadRequest;

/**
 * DownloadProvider implementation routing recognized media downloads to yt-dlp.
 */
public class YtDlpDownloadProvider implements DownloadProvider {

    private final PluginContext context;
    private final YtDlpConfig config;
    private final YtDlpUrlRecognizer urlRecognizer;

    public YtDlpDownloadProvider(PluginContext context, YtDlpConfig config, YtDlpUrlRecognizer urlRecognizer) {
        this.context = context;
        this.config = config;
        this.urlRecognizer = urlRecognizer;
    }

    @Override
    public String getName() {
        return "yt-dlp Media Downloader";
    }

    @Override
    public boolean canHandle(String url) {
        return urlRecognizer.canRecognize(url);
    }

    @Override
    public PluginDownloadTask createDownloadTask(PluginDownloadRequest request) throws Exception {
        return new YtDlpDownloadTask(context, config, request);
    }
}

