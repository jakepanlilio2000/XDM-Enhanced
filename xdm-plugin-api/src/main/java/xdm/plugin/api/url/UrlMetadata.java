package xdm.plugin.api.url;

import java.util.Collections;
import java.util.Map;

public class UrlMetadata {
    private final String url;
    private final String suggestedFileName;
    private final String suggestedCategory;
    private final Map<String, String> extraData;

    public UrlMetadata(String url, String suggestedFileName, String suggestedCategory, Map<String, String> extraData) {
        this.url = url;
        this.suggestedFileName = suggestedFileName;
        this.suggestedCategory = suggestedCategory;
        this.extraData = extraData != null ? Collections.unmodifiableMap(extraData) : Collections.emptyMap();
    }

    public String getUrl() { return url; }
    public String getSuggestedFileName() { return suggestedFileName; }
    public String getSuggestedCategory() { return suggestedCategory; }
    public Map<String, String> getExtraData() { return extraData; }
}

