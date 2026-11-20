package xdm.plugin.api.events;

import xdm.plugin.api.Registration;

/**
 * Handle representing an active topic subscription on the plugin event bus.
 * Can be queried for topic and active state, and closed/unregistered when no longer needed.
 */
public interface EventSubscription extends Registration {
    /**
     * Gets the event bus topic name this subscription is bound to.
     */
    String getTopic();

    /**
     * Checks if this subscription is currently active.
     */
    boolean isActive();
}
