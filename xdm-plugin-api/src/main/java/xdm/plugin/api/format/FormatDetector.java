package xdm.plugin.api.format;

import java.util.Map;

public interface FormatDetector {
    boolean matchesFormat(String contentType, String url);
    FormatInfo detect(String url, Map<String, String> headers);
}

