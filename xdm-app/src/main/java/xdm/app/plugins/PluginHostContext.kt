package xdm.app.plugins

import xdm.app.AppContext
import xdm.app.update.UpdateChecker
import xdm.app.utils.showTrayNotification
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.util.Logger
import xdm.plugin.api.NotificationLevel
import xdm.plugin.api.PluginConfig
import xdm.plugin.api.PluginContext
import xdm.plugin.api.Registration
import xdm.plugin.api.commands.PluginCommand
import xdm.plugin.api.download.DownloadProvider
import xdm.plugin.api.download.PluginDownloadTask
import xdm.plugin.api.enrichment.MetadataEnricher
import xdm.plugin.api.events.EventSubscription
import xdm.plugin.api.events.PluginEventListener
import xdm.plugin.api.events.PostDownloadHook
import xdm.plugin.api.format.FormatDetector
import xdm.plugin.api.media.MediaProvider
import xdm.plugin.api.models.DownloadInfo
import xdm.plugin.api.models.PluginDownloadRequest
import xdm.plugin.api.models.PluginMetadata
import xdm.plugin.api.network.ProxyProvider
import xdm.plugin.api.network.ProxyResolution
import xdm.plugin.api.network.VpnStatus
import xdm.plugin.api.network.VpnStatusProvider
import xdm.plugin.api.processors.PostDownloadProcessor
import xdm.plugin.api.ui.ContextMenuContributor
import xdm.plugin.api.ui.DashboardWidgetContributor
import xdm.plugin.api.ui.DownloadDetailsSectionContributor
import xdm.plugin.api.ui.PluginTheme
import xdm.plugin.api.ui.PluginThemeListener
import xdm.plugin.api.ui.SettingsPanelContributor
import xdm.plugin.api.ui.StatusIndicatorContributor
import xdm.plugin.api.ui.ToolbarContributor
import xdm.app.skins.SkinManager
import xdm.app.ui.components.ThemeManager
import com.formdev.flatlaf.util.UIScale
import xdm.plugin.api.url.UrlRecognizer
import java.io.File
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/**
 * Host execution context provided to an active plugin.
 * Ensures isolation from internal database mutation.
 */
class PluginHostContext(
    private val plugin: LoadedPlugin,
    private val hostMetadata: PluginMetadata
) : PluginContext {

    companion object {
        /**
         * Dedicated daemon thread pool for asynchronous plugin background execution,
         * isolating plugin work from the host JVM ForkJoinPool.commonPool().
         */
        val pluginExecutor: ThreadPoolExecutor = ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS,
            LinkedBlockingQueue(256),
            object : ThreadFactory {
                private val count = AtomicInteger(1)
                override fun newThread(r: Runnable): Thread {
                    return Thread(r, "PluginWorker-${count.getAndIncrement()}").apply { isDaemon = true }
                }
            }
        )
    }

    private val dataDir = File(plugin.pluginDir, "data").apply { mkdirs() }
    private val hostConfig = PluginHostConfig(File(dataDir, "config.properties"))

    val downloadProviders = CopyOnWriteArrayList<DownloadProvider>()
    val mediaProviders = CopyOnWriteArrayList<MediaProvider>()
    val urlRecognizers = CopyOnWriteArrayList<UrlRecognizer>()
    val formatDetectors = CopyOnWriteArrayList<FormatDetector>()
    val postDownloadHooks = CopyOnWriteArrayList<PostDownloadHook>()
    val postDownloadProcessors = CopyOnWriteArrayList<PostDownloadProcessor>()
    val proxyProviders = CopyOnWriteArrayList<ProxyProvider>()
    val vpnStatusProviders = CopyOnWriteArrayList<VpnStatusProvider>()
    val contextMenuContributors = CopyOnWriteArrayList<ContextMenuContributor>()
    val toolbarContributors = CopyOnWriteArrayList<ToolbarContributor>()
    val settingsPanels = CopyOnWriteArrayList<SettingsPanelContributor>()
    val dashboardWidgets = CopyOnWriteArrayList<DashboardWidgetContributor>()
    val detailsSectionContributors = CopyOnWriteArrayList<DownloadDetailsSectionContributor>()
    val statusIndicatorContributors = CopyOnWriteArrayList<StatusIndicatorContributor>()
    val metadataEnrichers = CopyOnWriteArrayList<MetadataEnricher>()
    val commands = CopyOnWriteArrayList<PluginCommand>()
    val registrations = CopyOnWriteArrayList<Registration>()

    override fun getMetadata(): PluginMetadata = hostMetadata

    override fun getDataDirectory(): File = dataDir

    override fun getConfig(): PluginConfig = hostConfig

    override fun registerDownloadProvider(provider: DownloadProvider?) {
        if (provider != null) downloadProviders.add(provider)
    }

    override fun registerMediaProvider(provider: MediaProvider?) {
        if (provider != null) mediaProviders.add(provider)
    }

    override fun registerUrlRecognizer(recognizer: UrlRecognizer?) {
        if (recognizer != null) urlRecognizers.add(recognizer)
    }

    override fun registerFormatDetector(detector: FormatDetector?) {
        if (detector != null) formatDetectors.add(detector)
    }

    override fun registerPostDownloadHook(hook: PostDownloadHook?) {
        if (hook != null) postDownloadHooks.add(hook)
    }

    override fun registerPostDownloadProcessor(processor: PostDownloadProcessor?): Registration {
        if (processor == null) return Registration { }
        postDownloadProcessors.add(processor)
        val reg = Registration { postDownloadProcessors.remove(processor) }
        registrations.add(reg)
        return reg
    }

    override fun registerProxyProvider(provider: ProxyProvider?): Registration {
        if (provider == null) return Registration { }
        proxyProviders.add(provider)
        val reg = Registration { proxyProviders.remove(provider) }
        registrations.add(reg)
        return reg
    }

    override fun resolveProxy(targetUri: URI?, engine: ProxyResolution.NetworkEngine?): ProxyResolution? {
        if (targetUri == null) return null
        return PluginManager.resolveProxy(targetUri, engine ?: ProxyResolution.NetworkEngine.HTTP)
    }

    override fun resolveProxy(targetUri: URI?): ProxyResolution? {
        return resolveProxy(targetUri, ProxyResolution.NetworkEngine.HTTP)
    }

    override fun registerVpnStatusProvider(provider: VpnStatusProvider?): Registration {
        if (provider == null) return Registration { }
        vpnStatusProviders.add(provider)
        VpnPolicyManager.syncActiveProviderListener()
        val reg = Registration {
            vpnStatusProviders.remove(provider)
            VpnPolicyManager.syncActiveProviderListener()
        }
        registrations.add(reg)
        return reg
    }

    override fun isVpnPrerequisiteMet(): Boolean {
        return VpnPolicyManager.isVpnPrerequisiteMet()
    }

    override fun getVpnStatus(): VpnStatus {
        return VpnPolicyManager.getCurrentStatus()
    }

    override fun registerContextMenuContributor(contributor: ContextMenuContributor?) {
        if (contributor != null) {
            contextMenuContributors.add(contributor)
            PluginManager.notifyStateChanged()
        }
    }

    override fun unregisterContextMenuContributor(contributor: ContextMenuContributor?) {
        if (contributor != null) {
            contextMenuContributors.remove(contributor)
            PluginManager.notifyStateChanged()
        }
    }

    override fun registerContextMenu(contributor: ContextMenuContributor?): Registration {
        if (contributor == null) return Registration { }
        contextMenuContributors.add(contributor)
        PluginManager.notifyStateChanged()
        val reg = Registration {
            unregisterContextMenuContributor(contributor)
        }
        registrations.add(reg)
        return reg
    }

    override fun registerToolbarContributor(contributor: ToolbarContributor?) {
        if (contributor != null) {
            toolbarContributors.add(contributor)
            PluginManager.notifyStateChanged()
        }
    }

    override fun unregisterToolbarContributor(contributor: ToolbarContributor?) {
        if (contributor != null) {
            toolbarContributors.remove(contributor)
            PluginManager.notifyStateChanged()
        }
    }

    override fun registerToolbar(contributor: ToolbarContributor?): Registration {
        if (contributor == null) return Registration { }
        toolbarContributors.add(contributor)
        PluginManager.notifyStateChanged()
        val reg = Registration {
            unregisterToolbarContributor(contributor)
        }
        registrations.add(reg)
        return reg
    }

    override fun registerSettingsPanel(panel: SettingsPanelContributor?) {
        if (panel != null) {
            settingsPanels.add(panel)
            PluginManager.notifyStateChanged()
        }
    }

    override fun unregisterSettingsPanel(panel: SettingsPanelContributor?) {
        if (panel != null) {
            settingsPanels.remove(panel)
            PluginManager.notifyStateChanged()
        }
    }

    override fun registerSettings(panel: SettingsPanelContributor?): Registration {
        if (panel == null) return Registration { }
        settingsPanels.add(panel)
        PluginManager.notifyStateChanged()
        val reg = Registration {
            unregisterSettingsPanel(panel)
        }
        registrations.add(reg)
        return reg
    }

    override fun registerDetailsSection(section: DownloadDetailsSectionContributor?): Registration {
        if (section == null) return Registration { }
        detailsSectionContributors.add(section)
        PluginManager.notifyStateChanged()
        val reg = Registration {
            detailsSectionContributors.remove(section)
            PluginManager.notifyStateChanged()
        }
        registrations.add(reg)
        return reg
    }

    override fun registerStatusIndicator(indicator: StatusIndicatorContributor?): Registration {
        if (indicator == null) return Registration { }
        statusIndicatorContributors.add(indicator)
        PluginManager.notifyStateChanged()
        val reg = Registration {
            statusIndicatorContributors.remove(indicator)
            PluginManager.notifyStateChanged()
        }
        registrations.add(reg)
        return reg
    }

    private val hostTheme: PluginTheme = object : PluginTheme {
        override fun isDark(): Boolean = ThemeManager.isDark
        override fun getAccentColor(): java.awt.Color = ThemeManager.currentAccent ?: ThemeManager.defaultAccentColor
        override fun getContrastForeground(background: java.awt.Color): java.awt.Color = ThemeManager.contrastForeground(background)
        override fun getBaseFont(): java.awt.Font = javax.swing.UIManager.getFont("defaultFont") ?: java.awt.Font("Dialog", java.awt.Font.PLAIN, 12)
        override fun scale(pixels: Int): Int = UIScale.scale(pixels)
        override fun scale(value: Float): Float = UIScale.scale(value)
        override fun getActiveSkinId(): String = SkinManager.currentSkin.id

        override fun addThemeListener(listener: PluginThemeListener?): Registration {
            if (listener == null) return Registration { }
            val themeListener = ThemeManager.ThemeChangeListener {
                SwingUtilities.invokeLater {
                    runCatching { listener.onThemeChanged(this) }
                }
            }
            val skinListener = SkinManager.SkinChangeListener { _ ->
                SwingUtilities.invokeLater {
                    runCatching { listener.onThemeChanged(this) }
                }
            }
            ThemeManager.addThemeListener(themeListener)
            SkinManager.addSkinListener(skinListener)
            val reg = Registration {
                ThemeManager.removeThemeListener(themeListener)
                SkinManager.removeSkinListener(skinListener)
            }
            registrations.add(reg)
            return reg
        }

        override fun removeThemeListener(listener: PluginThemeListener?) {
            // Supported via Registration handle
        }
    }

    override fun getTheme(): PluginTheme = hostTheme

    override fun registerDashboardWidget(widget: DashboardWidgetContributor?): Registration {
        if (widget == null) return Registration { }
        dashboardWidgets.add(widget)
        val reg = Registration { dashboardWidgets.remove(widget) }
        registrations.add(reg)
        return reg
    }

    override fun registerMetadataEnricher(enricher: MetadataEnricher?): Registration {
        if (enricher == null) return Registration { }
        metadataEnrichers.add(enricher)
        val reg = Registration { metadataEnrichers.remove(enricher) }
        registrations.add(reg)
        return reg
    }

    override fun registerCommand(command: PluginCommand?) {
        if (command != null) commands.add(command)
    }

    override fun showNotification(title: String?, message: String?, level: NotificationLevel?) {
        showNotification(title, message, level, null)
    }

    override fun showNotification(title: String?, message: String?, level: NotificationLevel?, onClick: Runnable?) {
        val safeTitle = title ?: hostMetadata.name
        val safeMessage = message ?: ""
        logInfo("[Notification: $level] $safeTitle - $safeMessage")
        SwingUtilities.invokeLater {
            runCatching {
                showTrayNotification(
                    caption = safeTitle,
                    message = safeMessage,
                    onClick = onClick?.let { { it.run() } }
                )
            }
        }
    }

    override fun logInfo(message: String?) {
        Logger.info("[Plugin:${hostMetadata.id}] $message")
    }

    override fun logWarn(message: String?) {
        Logger.info("[Plugin:${hostMetadata.id}][WARN] $message")
    }

    override fun logError(message: String?, throwable: Throwable?) {
        Logger.error("[Plugin:${hostMetadata.id}] $message", throwable)
    }

    override fun getApplicationVersion(): String {
        return UpdateChecker.loadConfig()?.currentVersion ?: UpdateChecker.DEFAULT_VERSION
    }

    override fun getApiVersion(): String {
        return PluginManager.API_VERSION
    }

    override fun getDownloads(): List<DownloadInfo> {
        if (!AppContext.hasDownloader || !AppContext.hasDb) {
            return emptyList()
        }
        val records = AppContext.db.findAll { true }
        return records.map { rec ->
            val taskInfo = AppContext.taskInfoDB.getHttpTask(rec.id)
            val destPath = if (taskInfo != null) {
                File(taskInfo.targetFolder, rec.fileName).absolutePath
            } else {
                rec.fileName
            }
            val url = taskInfo?.url ?: ""
            DownloadInfo(
                rec.id,
                url,
                rec.fileName,
                destPath,
                rec.size,
                rec.downloaded,
                rec.progress,
                rec.speed,
                rec.status.name,
                rec.categoryId ?: "None",
                rec.date
            )
        }
    }

    override fun submitDownload(request: PluginDownloadRequest?) {
        if (request == null || !AppContext.hasDownloader) return
        val destFolder = if (request.destinationFolder.isNullOrBlank()) {
            AppContext.config.defaultDownloadFolder
        } else {
            request.destinationFolder
        }
        val cookieStr = request.cookies?.entries?.joinToString("; ") { "${it.key}=${it.value}" } ?: ""
        val headersMap = request.headers?.mapValues { listOf(it.value) } ?: emptyMap()
        val task = HttpDownloadTaskInfo(
            id = 0L,
            url = request.url,
            fileName = request.fileName ?: "",
            respectFileName = !request.fileName.isNullOrBlank(),
            cookie = cookieStr,
            headers = headersMap,
            origin = "",
            categoryId = null,
            targetFolder = destFolder,
            maxPiece = 8,
            authInfo = null,
            knownFileSize = null,
            etag = "",
            convertToSrt = false
        )
        AppContext.downloader.startHttpDownload(task, true)
    }

    override fun createDownloadWithConfirmation(request: PluginDownloadRequest?) {
        if (request == null) return
        runOnUIThread {
            val destFolder = if (request.destinationFolder.isNullOrBlank()) {
                AppContext.config.defaultDownloadFolder
            } else {
                request.destinationFolder
            }
            val cookieStr = request.cookies?.entries?.joinToString("; ") { "${it.key}=${it.value}" } ?: ""
            val headersMap = request.headers?.mapValues { listOf(it.value) } ?: emptyMap()
            val taskInfo = HttpDownloadTaskInfo(
                id = 0L,
                url = request.url,
                fileName = request.fileName ?: "",
                respectFileName = !request.fileName.isNullOrBlank(),
                cookie = cookieStr,
                headers = headersMap,
                origin = "",
                categoryId = null,
                targetFolder = destFolder,
                maxPiece = 8,
                authInfo = null,
                knownFileSize = null,
                etag = "",
                convertToSrt = false
            )
            val url = taskInfo.url.trim()
            val existing = if (url.isNotEmpty()) xdm.app.ui.screens.NewDownloadWindow.getOpenWindow(url) else null
            if (existing != null && existing.isDisplayable) {
                existing.toFront()
                existing.requestFocus()
            } else {
                val dlg = xdm.app.ui.screens.NewDownloadWindow()
                dlg.showWindow(taskInfo)
            }
        }
    }

    override fun submitPluginTask(request: PluginDownloadRequest?, task: PluginDownloadTask?): Long {
        if (request == null || task == null || !AppContext.hasDownloader) return -1L
        return PluginDownloadBridge.registerPluginTask(plugin, request, task)
    }

    override fun <T : Any?> subscribe(topic: String?, listener: PluginEventListener<T>?) {
        if (topic != null && listener != null) {
            PluginEventBus.subscribe(hostMetadata.id, topic, listener)
        }
    }

    override fun <T : Any?> subscribeTopic(topic: String?, listener: PluginEventListener<T>?): EventSubscription {
        if (topic == null || listener == null) {
            return object : EventSubscription {
                override fun getTopic(): String = topic ?: ""
                override fun isActive(): Boolean = false
                override fun unregister() {}
            }
        }
        PluginEventBus.subscribe(hostMetadata.id, topic, listener)
        val sub = object : EventSubscription {
            private val active = AtomicBoolean(true)
            override fun getTopic(): String = topic
            override fun isActive(): Boolean = active.get()
            override fun unregister() {
                if (active.compareAndSet(true, false)) {
                    PluginEventBus.unsubscribe(hostMetadata.id, topic, listener)
                }
            }
        }
        registrations.add(sub)
        return sub
    }

    override fun <T : Any?> unsubscribe(topic: String?, listener: PluginEventListener<T>?) {
        if (topic != null && listener != null) {
            PluginEventBus.unsubscribe(hostMetadata.id, topic, listener)
        }
    }

    override fun <T : Any?> publish(topic: String?, eventData: T) {
        if (topic != null) {
            PluginEventBus.publish(topic, eventData)
        }
    }

    override fun <T : Any?> executeAsync(task: Callable<T>?): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (task == null) {
            future.completeExceptionally(IllegalArgumentException("Task must not be null"))
            return future
        }
        pluginExecutor.execute {
            try {
                future.complete(task.call())
            } catch (t: Throwable) {
                logError("Asynchronous plugin task execution failed", t)
                future.completeExceptionally(t)
            }
        }
        return future
    }

    override fun runOnUIThread(action: Runnable?) {
        if (action == null) return
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                action.run()
            } catch (t: Throwable) {
                logError("Plugin UI action failed on EDT", t)
            }
        } else {
            SwingUtilities.invokeLater {
                try {
                    action.run()
                } catch (t: Throwable) {
                    logError("Plugin UI action failed on EDT", t)
                }
            }
        }
    }

    fun clearRegistrations() {
        registrations.forEach { runCatching { it.unregister() } }
        registrations.clear()
        PluginEventBus.clearForPlugin(hostMetadata.id)
        PluginDownloadBridge.cancelTasksForPlugin(hostMetadata.id)
        downloadProviders.clear()
        mediaProviders.clear()
        urlRecognizers.clear()
        formatDetectors.clear()
        postDownloadHooks.clear()
        postDownloadProcessors.clear()
        proxyProviders.clear()
        vpnStatusProviders.clear()
        contextMenuContributors.clear()
        toolbarContributors.clear()
        settingsPanels.clear()
        dashboardWidgets.clear()
        detailsSectionContributors.clear()
        statusIndicatorContributors.clear()
        metadataEnrichers.clear()
        commands.clear()
    }
}
