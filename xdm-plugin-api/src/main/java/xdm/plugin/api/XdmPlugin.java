package xdm.plugin.api;

/**
 * Main extension lifecycle contract for XDM Enhanced plugins.
 * Implementations may be registered through Java's standard {@link java.util.ServiceLoader}
 * or declared as the entry point in plugin.json.
 */
public interface XdmPlugin {
    /**
     * Unique identifier for this plugin (e.g. "sample-checksum-verifier").
     * Must match ^[a-z0-9_-]{3,64}$ and match plugin.json ID.
     */
    String getId();

    /**
     * Initializes the plugin with the provided execution context.
     * Use [context] to register extension points, hooks, UI contributions, and settings.
     *
     * @param context the secure host context provided by XDM Enhanced
     * @throws Exception if plugin initialization fails
     */
    void init(PluginContext context) throws Exception;

    /**
     * Called when the plugin is disabled or during application shutdown.
     * The plugin must release all allocated background threads, timers, sockets, or native resources.
     */
    void shutdown();
}

