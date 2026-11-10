package xdm.app.plugins

import xdm.app.AppContext
import xdm.app.update.UpdateChecker
import xdm.app.utils.showTrayNotification
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.util.Logger
import xdm.plugin.api.NotificationLevel
import xdm.plugin.api.PluginConfig
import xdm.plugin.api.PluginContext
import xdm.plugin.api.commands.PluginCommand
import xdm.plugin.api.download.DownloadProvider
import xdm.plugin.api.events.PostDownloadHook
import xdm.plugin.api.format.FormatDetector
import xdm.plugin.api.media.MediaProvider
import xdm.plugin.api.models.DownloadInfo
import xdm.plugin.api.models.PluginDownloadRequest
import xdm.plugin.api.models.PluginMetadata
import xdm.plugin.api.ui.ContextMenuContributor
import xdm.plugin.api.ui.SettingsPanelContributor
import xdm.plugin.api.ui.ToolbarContributor
import xdm.plugin.api.url.UrlRecognizer
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Host execution context provided to an active plugin.
 * Ensures isolation from internal database mutation.
 */
class PluginHostContext(
    private val plugin: LoadedPlugin,
    private val hostMetadata: PluginMetadata
) : PluginContext {

    private val dataDir = File(plugin.pluginDir, "data").apply { mkdirs() }
    private val hostConfig = PluginHostConfig(File(dataDir, "config.properties"))

    val downloadProviders = CopyOnWriteArrayList<DownloadProvider>()
    val mediaProviders = CopyOnWriteArrayList<MediaProvider>()
    val urlRecognizers = CopyOnWriteArrayList<UrlRecognizer>()
    val formatDetectors = CopyOnWriteArrayList<FormatDetector>()
    val postDownloadHooks = CopyOnWriteArrayList<PostDownloadHook>()
    val contextMenuContributors = CopyOnWriteArrayList<ContextMenuContributor>()
    val toolbarContributors = CopyOnWriteArrayList<ToolbarContributor>()
    val settingsPanels = CopyOnWriteArrayList<SettingsPanelContributor>()
    val commands = CopyOnWriteArrayList<PluginCommand>()

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

    override fun registerContextMenuContributor(contributor: ContextMenuContributor?) {
        if (contributor != null) contextMenuContributors.add(contributor)
    }

    override fun registerToolbarContributor(contributor: ToolbarContributor?) {
        if (contributor != null) toolbarContributors.add(contributor)
    }

    override fun registerSettingsPanel(panel: SettingsPanelContributor?) {
        if (panel != null) settingsPanels.add(panel)
    }

    override fun registerCommand(command: PluginCommand?) {
        if (command != null) commands.add(command)
    }

    override fun showNotification(title: String?, message: String?, level: NotificationLevel?) {
        val safeTitle = title ?: hostMetadata.name
        val safeMessage = message ?: ""
        logInfo("[Notification: $level] $safeTitle - $safeMessage")
        runCatching {
            showTrayNotification(safeTitle, safeMessage)
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

    fun clearRegistrations() {
        downloadProviders.clear()
        urlRecognizers.clear()
        formatDetectors.clear()
        postDownloadHooks.clear()
        contextMenuContributors.clear()
        toolbarContributors.clear()
        settingsPanels.clear()
        commands.clear()
    }
}
