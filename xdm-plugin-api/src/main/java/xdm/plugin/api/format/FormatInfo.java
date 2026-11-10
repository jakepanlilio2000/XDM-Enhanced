package xdm.plugin.api.format;

public class FormatInfo {
    private final String formatName;
    private final String mimeType;
    private final String recommendedExtension;
    private final boolean isStream;

    public FormatInfo(String formatName, String mimeType, String recommendedExtension, boolean isStream) {
        this.formatName = formatName;
        this.mimeType = mimeType;
        this.recommendedExtension = recommendedExtension;
        this.isStream = isStream;
    }

    public String getFormatName() { return formatName; }
    public String getMimeType() { return mimeType; }
    public String getRecommendedExtension() { return recommendedExtension; }
    public boolean isStream() { return isStream; }
}

