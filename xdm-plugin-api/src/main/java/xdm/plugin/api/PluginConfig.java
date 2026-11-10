package xdm.plugin.api;

/**
 * Isolated persistent key-value configuration storage for a plugin.
 */
public interface PluginConfig {
    String get(String key, String defaultValue);
    void set(String key, String value);

    default String getString(String key, String defaultValue) {
        return get(key, defaultValue);
    }

    default void setString(String key, String value) {
        set(key, value);
    }

    int getInt(String key, int defaultValue);
    void setInt(String key, int value);

    boolean getBoolean(String key, boolean defaultValue);
    void setBoolean(String key, boolean value);

    void save();
}
