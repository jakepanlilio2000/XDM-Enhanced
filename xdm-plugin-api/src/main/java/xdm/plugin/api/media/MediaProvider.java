package xdm.plugin.api.media;

import java.util.List;
import java.util.Map;

/**
 * Optional extension point for plugins to provide media stream resolution.
 * Allows plugins to extract available qualities, audio tracks, and formats
 * for supported video URLs without requiring remotely executed browser scripts.
 */
public interface MediaProvider {
    /**
     * Unique identifier for this media provider.
     */
    String getProviderId();

    /**
     * Human-readable display name for this provider.
     */
    String getDisplayName();

    /**
     * Checks if this provider can handle or resolve the specified media or page URL.
     */
    boolean supportsUrl(String url);

    /**
     * Resolves available media streams and quality options for the given URL.
     *
     * @param url The media or page URL
     * @param contextHeaders Request headers or context from the browser
     * @return List of detected media streams, or empty list if none resolved
     */
    List<PluginMediaStream> resolveStreams(String url, Map<String, String> contextHeaders);
}

