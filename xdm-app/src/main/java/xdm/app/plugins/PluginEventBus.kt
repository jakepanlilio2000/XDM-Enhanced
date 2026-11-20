package xdm.app.plugins

import xdm.core.util.Logger
import xdm.plugin.api.events.EventTopics
import xdm.plugin.api.events.PluginEventListener
import xdm.plugin.api.events.TaskLifecycleEvent
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host-managed typed asynchronous event bus for inter-plugin and core-to-plugin communication.
 * Guarantees:
 * - Thread-safe asynchronous dispatching via dedicated daemon executor pool.
 * - Slow listener isolation: slow or blocked subscribers do not delay other listeners or callers.
 * - Per-subscriber sequential FIFO ordering via dedicated per-subscriber non-blocking queues.
 * - Deduplication for duplicate terminal lifecycle events within sliding window.
 * - Progress throttling and backpressure for high-frequency download progress updates.
 * - Order preservation and graceful subscription cleanup on plugin deactivation.
 */
object PluginEventBus {
    private val topicSubscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<SubscriptionRecord>>()

    private val threadIndex = AtomicInteger(1)
    private val executor = ThreadPoolExecutor(
        4, 16, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(4096),
        ThreadFactory { r ->
            Thread(r, "PluginEventBus-Worker-${threadIndex.getAndIncrement()}").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    // Deduplication tracking: key -> timestamp
    private val terminalEventCache = ConcurrentHashMap<String, Long>()
    private const val DEDUPLICATION_WINDOW_MS = 2000L

    // Progress throttling tracking: downloadId -> timestamp
    private val progressThrottleMap = ConcurrentHashMap<Long, Long>()
    private const val PROGRESS_THROTTLE_MS = 100L

    private class SubscriptionRecord(
        val pluginId: String,
        val listener: PluginEventListener<Any?>
    ) {
        val pendingQueue = ConcurrentLinkedQueue<Pair<String, Any?>>()
        val isProcessing = AtomicBoolean(false)
        @Volatile var isCancelled = false

        fun scheduleDrain(executor: Executor) {
            if (isCancelled) {
                pendingQueue.clear()
                return
            }
            if (isProcessing.compareAndSet(false, true)) {
                executor.execute {
                    drain(executor)
                }
            }
        }

        private fun drain(executor: Executor) {
            try {
                while (!isCancelled) {
                    val item = pendingQueue.poll() ?: break
                    try {
                        listener.onEvent(item.first, item.second)
                    } catch (t: Throwable) {
                        Logger.error("Error dispatching event on topic '${item.first}' to plugin '$pluginId'", t)
                    }
                }
            } finally {
                isProcessing.set(false)
                if (!isCancelled && !pendingQueue.isEmpty()) {
                    if (isProcessing.compareAndSet(false, true)) {
                        executor.execute { drain(executor) }
                    }
                }
            }
        }
    }

    fun <T> subscribe(pluginId: String, topic: String, listener: PluginEventListener<T>) {
        val list = topicSubscribers.computeIfAbsent(topic) { CopyOnWriteArrayList() }
        @Suppress("UNCHECKED_CAST")
        list.add(SubscriptionRecord(pluginId, listener as PluginEventListener<Any?>))
    }

    fun <T> unsubscribe(pluginId: String, topic: String, listener: PluginEventListener<T>) {
        topicSubscribers[topic]?.let { list ->
            list.removeIf { sub ->
                if (sub.pluginId == pluginId && sub.listener === listener) {
                    sub.isCancelled = true
                    sub.pendingQueue.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    fun clearForPlugin(pluginId: String) {
        topicSubscribers.values.forEach { list ->
            list.removeIf { sub ->
                if (sub.pluginId == pluginId) {
                    sub.isCancelled = true
                    sub.pendingQueue.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    fun clearAll() {
        topicSubscribers.values.forEach { list ->
            list.forEach { sub ->
                sub.isCancelled = true
                sub.pendingQueue.clear()
            }
        }
        topicSubscribers.clear()
        terminalEventCache.clear()
        progressThrottleMap.clear()
    }

    /**
     * Publishes an event to all subscribers of the specified topic.
     * Applies deduplication and progress throttling where appropriate.
     */
    fun <T> publish(topic: String, eventData: T) {
        val subscribers = topicSubscribers[topic]
        if (subscribers.isNullOrEmpty()) return

        // 1. Throttling check for high-frequency progress updates
        if (topic == EventTopics.DOWNLOAD_PROGRESS) {
            val taskId = extractTaskId(eventData)
            if (taskId != -1L) {
                val now = System.currentTimeMillis()
                val isTerminalProgress = isTerminalProgress(eventData)
                if (!isTerminalProgress) {
                    val last = progressThrottleMap[taskId] ?: 0L
                    if (now - last < PROGRESS_THROTTLE_MS) {
                        return // Throttle intermediate progress
                    }
                }
                progressThrottleMap[taskId] = now
            }
        }

        // 2. Deduplication check for terminal events (completed, failed, canceled)
        if (topic == EventTopics.DOWNLOAD_COMPLETED ||
            topic == EventTopics.DOWNLOAD_FAILED ||
            topic == EventTopics.DOWNLOAD_CANCELED) {
            val taskId = extractTaskId(eventData)
            if (taskId != -1L) {
                val now = System.currentTimeMillis()
                val dedupKey = "$topic:$taskId"
                val lastTime = terminalEventCache[dedupKey] ?: 0L
                if (now - lastTime < DEDUPLICATION_WINDOW_MS) {
                    return // Duplicate terminal event suppressed
                }
                terminalEventCache[dedupKey] = now
            }
        }

        // 3. Dispatch asynchronously and sequentially per subscriber
        for (sub in subscribers) {
            if (!sub.isCancelled) {
                sub.pendingQueue.add(Pair(topic, eventData))
                sub.scheduleDrain(executor)
            }
        }
    }

    /**
     * Synchronous publish for tests or immediate critical lifecycle steps.
     */
    fun <T> publishSync(topic: String, eventData: T) {
        val subscribers = topicSubscribers[topic] ?: return
        for (sub in subscribers) {
            if (!sub.isCancelled) {
                try {
                    sub.listener.onEvent(topic, eventData)
                } catch (t: Throwable) {
                    Logger.error("Error synchronously dispatching event on topic '$topic' to plugin '${sub.pluginId}'", t)
                }
            }
        }
    }

    /**
     * Awaits completion of queued event dispatches (useful for unit and integration testing).
     */
    fun awaitIdle(timeout: Long = 2000L, unit: TimeUnit = TimeUnit.MILLISECONDS): Boolean {
        Thread.sleep(25)
        val startTime = System.currentTimeMillis()
        val timeoutMs = unit.toMillis(timeout)
        while (executor.activeCount > 0 || !executor.queue.isEmpty() || hasPendingSubscriberTasks()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return false
            }
            Thread.sleep(10)
        }
        return true
    }

    private fun hasPendingSubscriberTasks(): Boolean {
        for (subscribers in topicSubscribers.values) {
            for (sub in subscribers) {
                if (sub.isProcessing.get() || !sub.pendingQueue.isEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun extractTaskId(eventData: Any?): Long = when (eventData) {
        is TaskLifecycleEvent -> eventData.taskId
        is xdm.plugin.api.models.DownloadInfo -> eventData.id
        is Long -> eventData
        else -> -1L
    }

    private fun isTerminalProgress(eventData: Any?): Boolean = when (eventData) {
        is TaskLifecycleEvent -> eventData.download != null && eventData.download.progressPercent >= 100
        is xdm.plugin.api.models.DownloadInfo -> eventData.progressPercent >= 100
        else -> false
    }
}
