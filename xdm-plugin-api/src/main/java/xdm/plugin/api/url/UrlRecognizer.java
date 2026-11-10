package xdm.plugin.api.url;

public interface UrlRecognizer {
    boolean matches(String url);
    UrlMetadata analyze(String url);

    default boolean canRecognize(String url) {
        return matches(url);
    }

    default UrlMetadata recognize(String url) {
        return analyze(url);
    }
}
