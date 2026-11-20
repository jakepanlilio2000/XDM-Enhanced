package xdm.plugin.api;

/**
 * Disposable handle representing an active registration of a plugin extension, hook, or contribution.
 * Invoking unregister() or close() removes the associated contribution cleanly from the host.
 */
@FunctionalInterface
public interface Registration extends AutoCloseable {
    /**
     * Unregisters and removes this contribution from the host runtime.
     */
    void unregister();

    /**
     * Standard AutoCloseable implementation delegating to unregister().
     */
    @Override
    default void close() {
        unregister();
    }
}
