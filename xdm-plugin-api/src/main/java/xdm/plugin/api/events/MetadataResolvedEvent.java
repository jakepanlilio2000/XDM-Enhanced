package xdm.plugin.api.events;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Structured event payload emitted when download metadata is resolved from preflight inspection or server probing.
 */
public class MetadataResolvedEvent {

    private final String url;
    private final String originalFileName;
    private final String suggestedFileName;
    private final String suggestedCategory;
    private final String mimeType;
    private final long contentLength;
    private final Map<String, String> headers;
    private final long timestamp;

    public MetadataResolvedEvent(
            String url,
            String originalFileName,
            String suggestedFileName,
            String suggestedCategory,
            String mimeType,
            long contentLength) {
        this(url, originalFileName, suggestedFileName, suggestedCategory, mimeType, contentLength, null);
    }

    public MetadataResolvedEvent(
            String url,
            String originalFileName,
            String suggestedFileName,
            String suggestedCategory,
            String mimeType,
            long contentLength,
            Map<String, String> headers) {
        this.url = url != null ? url : "";
        this.originalFileName = originalFileName != null ? originalFileName : "";
        this.suggestedFileName = suggestedFileName != null ? suggestedFileName : "";
        this.suggestedCategory = suggestedCategory != null ? suggestedCategory : "";
        this.mimeType = mimeType != null ? mimeType : "";
        this.contentLength = contentLength;
        if (headers != null && !headers.isEmpty()) {
            this.headers = Collections.unmodifiableMap(new HashMap<String, String>(headers));
        } else {
            this.headers = Collections.emptyMap();
        }
        this.timestamp = System.currentTimeMillis();
    }

    public String getUrl() {
        return url;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getSuggestedFileName() {
        return suggestedFileName;
    }

    public String getSuggestedCategory() {
        return suggestedCategory;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "MetadataResolvedEvent{" +
                "url='" + url + '\'' +
                ", fileName='" + (suggestedFileName.isEmpty() ? originalFileName : suggestedFileName) + '\'' +
                ", category='" + suggestedCategory + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", contentLength=" + contentLength +
                '}';
    }
}
