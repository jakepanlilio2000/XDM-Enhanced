package xdm.plugin.api.enrichment;

/**
 * Service Provider Interface for suggesting refined filenames and category tags
 * when download links or media metadata are resolved.
 *
 * NOTE: User manual inputs and explicit overrides always take precedence over plugin suggestions.
 */
public interface MetadataEnricher {

    /**
     * Unique identifier for this enricher.
     */
    String getEnricherId();

    /**
     * Priority order for evaluation (higher value evaluated first).
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Checks whether this enricher can process the given URL.
     */
    default boolean canEnrich(String url) {
        return true;
    }

    /**
     * Suggests a refined, user-friendly file name for the download.
     * Returned filenames must be clean and free of path traversal sequences.
     *
     * @param url Download source URL
     * @param originalFileName Default file name extracted from HTTP headers or URL path
     * @param mimeType Resolved MIME type (if known, or empty)
     * @return Suggested file name, or null to leave unchanged
     */
    default String suggestFileName(String url, String originalFileName, String mimeType) {
        return null;
    }

    /**
     * Suggests an application category ID (e.g. "CAT_VIDEOS", "CAT_MUSIC", "CAT_DOCS", "CAT_ARCHIVES").
     *
     * @param url Download source URL
     * @param fileName Resolved or suggested file name
     * @param mimeType Resolved MIME type (if known, or empty)
     * @return Category identifier, or null to keep standard category rule matching
     */
    default String suggestCategory(String url, String fileName, String mimeType) {
        return null;
    }
}
