package xdm.plugin.api;

/**
 * Structured exception carrying error classification for plugin discovery,
 * initialization, execution, and lifecycle operations.
 */
public class PluginException extends RuntimeException {

    public enum ErrorCode {
        INITIALIZATION_FAILED,
        SHUTDOWN_FAILED,
        INCOMPATIBLE_VERSION,
        MISSING_DEPENDENCY,
        DUPLICATE_ID,
        MALFORMED_MANIFEST,
        EXECUTION_FAILED,
        SECURITY_VIOLATION,
        NOT_FOUND
    }

    private final String pluginId;
    private final ErrorCode errorCode;

    public PluginException(String pluginId, ErrorCode errorCode, String message) {
        super(formatMessage(pluginId, errorCode, message));
        this.pluginId = pluginId;
        this.errorCode = errorCode;
    }

    public PluginException(String pluginId, ErrorCode errorCode, String message, Throwable cause) {
        super(formatMessage(pluginId, errorCode, message), cause);
        this.pluginId = pluginId;
        this.errorCode = errorCode;
    }

    private static String formatMessage(String pluginId, ErrorCode errorCode, String message) {
        return "[" + (pluginId != null ? pluginId : "unknown") + "][" + (errorCode != null ? errorCode.name() : "ERROR") + "] " + (message != null ? message : "");
    }

    public String getPluginId() {
        return pluginId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
