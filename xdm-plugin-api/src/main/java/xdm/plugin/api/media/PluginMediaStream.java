package xdm.plugin.api.media;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a detected or resolved media stream provided by a plugin or the media engine.
 */
public class PluginMediaStream {
    private final String streamId;
    private final String url;
    private final String title;
    private final String format;
    private final String resolution;
    private final String estimatedSize;
    private final boolean hasAudio;
    private final String frameRate;
    private final String streamType;
    private final Map<String, String> headers;

    public PluginMediaStream(
            String streamId,
            String url,
            String title,
            String format,
            String resolution,
            String estimatedSize,
            boolean hasAudio,
            String frameRate,
            String streamType,
            Map<String, String> headers
    ) {
        this.streamId = streamId;
        this.url = url;
        this.title = title;
        this.format = format;
        this.resolution = resolution;
        this.estimatedSize = estimatedSize;
        this.hasAudio = hasAudio;
        this.frameRate = frameRate;
        this.streamType = streamType;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
    }

    public String getStreamId() { return streamId; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getFormat() { return format; }
    public String getResolution() { return resolution; }
    public String getEstimatedSize() { return estimatedSize; }
    public boolean hasAudio() { return hasAudio; }
    public String getFrameRate() { return frameRate; }
    public String getStreamType() { return streamType; }
    public Map<String, String> getHeaders() { return headers; }

    @Override
    public String toString() {
        return "PluginMediaStream{" +
                "streamId='" + streamId + '\'' +
                ", format='" + format + '\'' +
                ", resolution='" + resolution + '\'' +
                ", streamType='" + streamType + '\'' +
                '}';
    }
}

