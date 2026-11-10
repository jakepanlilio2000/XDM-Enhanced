package xdm.plugin.api.models;

import java.util.Collections;
import java.util.Map;

public class PluginDownloadRequest {
    private final String url;
    private final String destinationFolder;
    private final String fileName;
    private final Map<String, String> headers;
    private final Map<String, String> cookies;
    private final Map<String, Object> metadata;

    public PluginDownloadRequest(String url, String destinationFolder, String fileName) {
        this(url, destinationFolder, fileName, null, null, null);
    }

    public PluginDownloadRequest(String url, String destinationFolder, String fileName,
                                 Map<String, String> headers, Map<String, String> cookies, Map<String, Object> metadata) {
        this.url = url;
        this.destinationFolder = destinationFolder;
        this.fileName = fileName;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        this.cookies = cookies != null ? Collections.unmodifiableMap(cookies) : Collections.emptyMap();
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    public String getUrl() { return url; }
    public String getDestinationFolder() { return destinationFolder; }
    public String getFileName() { return fileName; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getCookies() { return cookies; }
    public Map<String, Object> getMetadata() { return metadata; }
}
