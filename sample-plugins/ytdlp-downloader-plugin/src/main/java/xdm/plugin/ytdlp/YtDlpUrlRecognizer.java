package xdm.plugin.ytdlp;

import xdm.plugin.api.url.UrlMetadata;
import xdm.plugin.api.url.UrlRecognizer;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Recognizes video, audio, and media hosting URLs supported by yt-dlp.
 */
public class YtDlpUrlRecognizer implements UrlRecognizer {

    private static final Set<String> SUPPORTED_DOMAINS = new HashSet<>(Arrays.asList(
            "youtube.com", "youtu.be",
            "vimeo.com",
            "soundcloud.com",
            "bandcamp.com",
            "dailymotion.com",
            "twitch.tv",
            "streamable.com",
            "bilibili.com",
            "tiktok.com",
            "facebook.com", "fb.watch",
            "instagram.com",
            "twitter.com", "x.com",
            "reddit.com"
    ));

    @Override
    public boolean matches(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : SUPPORTED_DOMAINS) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public UrlMetadata analyze(String url) {
        if (!matches(url)) return null;
        return new UrlMetadata(
                url,
                null,
                "CAT_VIDEO",
                Collections.singletonMap("provider", "ytdlp")
        );
    }
}
