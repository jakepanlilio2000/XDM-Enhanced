package xdm.plugin.api.events;

/**
 * Listener interface for receiving asynchronous typed event notifications published on the plugin event bus.
 *
 * @param <T> Type of the event payload
 */
@FunctionalInterface
public interface PluginEventListener<T> {
    /**
     * Invoked when an event matching the subscribed topic is dispatched.
     *
     * @param topic Topic name on which the event was emitted
     * @param event Event payload
     */
    void onEvent(String topic, T event);
}

