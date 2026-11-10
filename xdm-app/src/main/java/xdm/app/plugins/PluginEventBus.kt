package xdm.app.plugins

import xdm.core.util.Logger
import xdm.plugin.api.events.PluginEventListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Host-managed typed asynchronous event bus for inter-plugin and core-to-plugin communication.
 * Isolates subscriber exceptions so failing plugins cannot crash other listeners or the UI thread.
 */
object PluginEventBus {
    private val topicSubscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<SubscriptionRecord>>()

    private class SubscriptionRecord(
        val pluginId: String,
        val listener: PluginEventListener<Any?>
    )

    fun <T> subscribe(pluginId: String, topic: String, listener: PluginEventListener<T>) {
        val list = topicSubscribers.computeIfAbsent(topic) { CopyOnWriteArrayList() }
        @Suppress("UNCHECKED_CAST")
        list.add(SubscriptionRecord(pluginId, listener as PluginEventListener<Any?>))
    }

    fun <T> unsubscribe(pluginId: String, topic: String, listener: PluginEventListener<T>) {
        topicSubscribers[topic]?.removeIf { it.pluginId == pluginId && it.listener === listener }
    }

    fun clearForPlugin(pluginId: String) {
        topicSubscribers.values.forEach { list ->
            list.removeIf { it.pluginId == pluginId }
        }
    }

    fun clearAll() {
        topicSubscribers.clear()
    }

    fun <T> publish(topic: String, eventData: T) {
        val subscribers = topicSubscribers[topic] ?: return
        for (sub in subscribers) {
            try {
                sub.listener.onEvent(topic, eventData)
            } catch (t: Throwable) {
                Logger.error("Error dispatching event on topic '$topic' to plugin '${sub.pluginId}'", t)
            }
        }
    }
}

