package xdm.app.plugins

import xdm.app.AppContext
import xdm.app.DesktopCaptureHost
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.DownloaderTask
import xdm.core.downloaders.PluginDownloadTaskInfo
import xdm.core.util.CoreUtils
import xdm.core.util.FileUtils
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.EngineContext
import xdm.manager.RecordStatus
import xdm.plugin.api.download.DownloadProgressEvent
import xdm.plugin.api.download.DownloadProgressListener
import xdm.plugin.api.download.PluginDownloadTask
import xdm.plugin.api.download.PluginDownloadTask.TaskState
import xdm.plugin.api.models.PluginDownloadRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Task adapter and bridge connecting third-party [PluginDownloadTask] instances to the
 * central XDM Enhanced download manager, UI views, categories, progress dialogs, and persistence.
 */
object PluginDownloadBridge {
    private val activeTasks = ConcurrentHashMap<Long, PluginDownloadTask>()
    private val activeAdapters = ConcurrentHashMap<Long, PluginTaskAdapter>()
    private val taskMetadata = ConcurrentHashMap<Long, PluginDownloadTaskInfo>()

    class PluginTaskAdapter(
        val id: Long,
        val pluginTask: PluginDownloadTask,
        val folder: String,
        val fileName: String
    ) : DownloaderTask {
        override fun start() {
            pluginTask.start()
        }

        override fun stop() {
            pluginTask.pause()
        }

        override fun resume() {
            pluginTask.resume()
        }

        override fun deleteTemp() {
            pluginTask.cancel()
        }
    }

    /**
     * Bridges an active [PluginDownloadTask] into XDM Enhanced's central download store and UI.
     */
    fun registerPluginTask(
        plugin: LoadedPlugin,
        request: PluginDownloadRequest,
        task: PluginDownloadTask,
        startNow: Boolean = true
    ): Long {
        var id = CoreUtils.uniqueId()
        if (id == -1L) id = -2L
        val defaultFolder = AppContext.config.defaultDownloadFolder
        val reqFolder = request.destinationFolder?.ifBlank { null } ?: defaultFolder
        val reqFile = request.fileName?.ifBlank { null } ?: FileUtils.getFileName(request.url)

        val recognizerCat = runCatching {
            val raw = PluginManager.findUrlRecognizer(request.url)?.second?.analyze(request.url)?.suggestedCategory
            if (raw == "CAT_VIDEO") "CAT_VIDEOS" else raw
        }.getOrNull()

        val (catId, targetFolder) = DesktopCaptureHost.destination(reqFile, request.url, null)
        val finalCatId = catId ?: recognizerCat
        val categoryFolder = finalCatId?.let { cid -> AppContext.config.categories.firstOrNull { it.id == cid }?.folder }
        val finalFolder = reqFolder.ifBlank { categoryFolder ?: targetFolder }

        val taskInfo = PluginDownloadTaskInfo(
            id = id,
            pluginId = plugin.manifest.id,
            url = request.url,
            fileName = reqFile,
            targetFolder = finalFolder,
            categoryId = finalCatId,
            totalSize = task.totalBytes
        )
        taskMetadata[id] = taskInfo
        AppContext.taskInfoDB.savePluginTask(taskInfo)

        val initialDownloaded = task.downloadedBytes.coerceAtLeast(0)
        val initialSize = task.totalBytes.coerceAtLeast(0)
        val initialProgress = if (initialSize > 0) ((initialDownloaded * 100) / initialSize).toInt().coerceIn(0, 100) else 0

        val initialStatus = if (startNow) RecordStatus.DOWNLOADING else RecordStatus.PAUSED

        val record = DbRecord(
            id = id,
            size = initialSize,
            downloaded = initialDownloaded,
            progress = initialProgress,
            date = System.currentTimeMillis(),
            fileName = reqFile,
            eta = 0L,
            speed = task.transferSpeed.coerceAtLeast(0f),
            status = initialStatus,
            selected = false,
            downloadType = DownloadType.Plugin,
            categoryId = finalCatId
        )

        AppContext.downloader.store.add(record)
        EngineContext.ui.downloadAdded(id)

        if (startNow && AppContext.config.showDownloadProgressWindow) {
            AppContext.app.showProgressWindow(id, reqFile)
        }

        val adapter = PluginTaskAdapter(id, task, finalFolder, reqFile)
        activeTasks[id] = task
        activeAdapters[id] = adapter

        task.addProgressListener(object : DownloadProgressListener {
            override fun onProgress(event: DownloadProgressEvent) {
                handleProgress(id, record, event, finalFolder)
            }
        })

        if (startNow) {
            try {
                task.start()
            } catch (t: Throwable) {
                Logger.error("Failed to start PluginDownloadTask $id", t)
                record.status = RecordStatus.ERROR
                AppContext.db.update(record)
                EngineContext.ui.downloadUpdated(id)
                EngineContext.ui.downloadFailed(id, DownloadError.InternalError)
            }
        }

        return id
    }

    private fun handleProgress(
        id: Long,
        record: DbRecord,
        event: DownloadProgressEvent,
        folder: String
    ) {
        val downloaded = event.downloadedBytes.coerceAtLeast(0)
        val size = event.totalBytes.coerceAtLeast(0)
        val speed = event.speedBytesPerSec.coerceAtLeast(0f)
        val progress = if (event.progressPercent >= 0) {
            event.progressPercent.coerceIn(0, 100)
        } else if (size > 0) {
            ((downloaded * 100) / size).toInt().coerceIn(0, 100)
        } else 0
        val eta = if (event.etaSeconds > 0) event.etaSeconds else if (speed > 0 && size > downloaded) ((size - downloaded) / speed).toLong() else 0L

        record.downloaded = downloaded
        record.size = size
        record.speed = speed
        record.progress = progress
        record.eta = eta

        when (event.state) {
            TaskState.DOWNLOADING -> {
                record.status = RecordStatus.DOWNLOADING
                AppContext.db.update(record)
                EngineContext.ui.downloadUpdated(id)
                EngineContext.ui.progress(id, record.fileName, downloaded, size, speed, eta, progress, emptyList())
            }
            TaskState.PAUSED -> {
                record.status = RecordStatus.PAUSED
                AppContext.db.update(record)
                EngineContext.ui.downloadUpdated(id)
                EngineContext.ui.downloadPaused(id)
            }
            TaskState.FINISHED -> {
                record.status = RecordStatus.FINISHED
                record.progress = 100
                record.downloaded = if (size > 0) size else downloaded
                val task = activeTasks.remove(id)
                activeAdapters.remove(id)

                val resolvedFile = task?.outputFile ?: File(folder, record.fileName)
                val finalFileName = resolvedFile.name
                if (finalFileName.isNotBlank() && finalFileName != record.fileName) {
                    record.fileName = finalFileName
                    if (record.categoryId == null) {
                        record.categoryId = DesktopCaptureHost.destination(finalFileName, null, null).first
                    }
                    val meta = taskMetadata[id] ?: AppContext.taskInfoDB.getPluginTask(id)
                    if (meta != null) {
                        val updated = meta.copy(fileName = finalFileName, categoryId = record.categoryId)
                        taskMetadata[id] = updated
                        AppContext.taskInfoDB.savePluginTask(updated)
                    }
                }
                AppContext.db.update(record)
                EngineContext.ui.downloadUpdated(id)

                EngineContext.ui.downloadCompleteDialog(id, resolvedFile.parent ?: folder, finalFileName, record.size)
                PluginManager.notifyDownloadCompleted(resolvedFile.absolutePath, id)
            }
            TaskState.FAILED -> {
                record.status = RecordStatus.ERROR
                AppContext.db.update(record)
                activeTasks.remove(id)
                activeAdapters.remove(id)
                EngineContext.ui.downloadUpdated(id)
                EngineContext.ui.downloadFailed(id, DownloadError.InternalError)
            }
            TaskState.CANCELED -> {
                record.status = RecordStatus.PAUSED
                AppContext.db.update(record)
                activeTasks.remove(id)
                activeAdapters.remove(id)
                EngineContext.ui.downloadUpdated(id)
                EngineContext.ui.downloadPaused(id)
            }
            else -> {}
        }
    }

    fun pauseTask(id: Long) {
        activeTasks[id]?.pause()
        AppContext.db.getById(id)?.let { rec ->
            rec.status = RecordStatus.PAUSED
            AppContext.db.update(rec)
            EngineContext.ui.downloadUpdated(id)
            EngineContext.ui.downloadPaused(id)
        }
    }

    fun resumeTask(id: Long) {
        val task = activeTasks[id]
        if (task != null) {
            task.resume()
        } else {
            val meta = taskMetadata[id] ?: AppContext.taskInfoDB.getPluginTask(id)
            if (meta != null) {
                val pair = PluginManager.installedPlugins
                    .find { it.manifest.id == meta.pluginId && it.status == PluginStatus.ACTIVE }
                    ?.let { plugin ->
                        val provider = plugin.context?.downloadProviders?.find { it.canHandle(meta.url) }
                            ?: plugin.context?.downloadProviders?.firstOrNull()
                        if (provider != null) plugin to provider else null
                    }
                if (pair != null) {
                    val (plugin, provider) = pair
                    try {
                        val req = PluginDownloadRequest(meta.url, meta.targetFolder, meta.fileName)
                        val newTask = provider.createDownloadTask(req)
                        val adapter = PluginTaskAdapter(id, newTask, meta.targetFolder, meta.fileName)
                        activeTasks[id] = newTask
                        activeAdapters[id] = adapter
                        val record = AppContext.db.getById(id)
                        if (record != null) {
                            newTask.addProgressListener(object : DownloadProgressListener {
                                override fun onProgress(event: DownloadProgressEvent) {
                                    handleProgress(id, record, event, meta.targetFolder)
                                }
                            })
                        }
                        newTask.start()
                    } catch (t: Throwable) {
                        Logger.error("Failed to recreate resumed plugin task $id", t)
                    }
                } else {
                    Logger.info("Plugin '${meta.pluginId}' is not active or has no provider for resumed download $id")
                }
            }
        }
        AppContext.db.getById(id)?.let { rec ->
            rec.status = RecordStatus.DOWNLOADING
            AppContext.db.update(rec)
            EngineContext.ui.downloadUpdated(id)
        }
    }

    fun deleteTask(id: Long, fromDisk: Boolean) {
        val task = activeTasks.remove(id)
        activeAdapters.remove(id)
        task?.cancel()

        val rec = AppContext.db.getById(id)
        val meta = taskMetadata.remove(id) ?: AppContext.taskInfoDB.getPluginTask(id)
        if (fromDisk) {
            val fileName = rec?.fileName ?: meta?.fileName
            val folder = meta?.targetFolder ?: AppContext.config.defaultDownloadFolder
            if (!fileName.isNullOrBlank()) {
                File(folder, fileName).delete()
            }
            task?.outputFile?.delete()
        }
        AppContext.taskInfoDB.deleteRecord(id)
        AppContext.db.removeItem(id)
        EngineContext.ui.downloadRemoved(id, 0)
    }

    fun getTaskUrl(id: Long): String? = taskMetadata[id]?.url ?: AppContext.taskInfoDB.getPluginTask(id)?.url

    fun getTask(id: Long): PluginDownloadTask? = activeTasks[id]

    fun cancelTasksForPlugin(pluginId: String) {
        val matchingIds = taskMetadata.filterValues { it.pluginId == pluginId }.keys
        for (id in matchingIds) {
            val task = activeTasks.remove(id)
            activeAdapters.remove(id)
            task?.let { runCatching { it.cancel() } }
            AppContext.db.getById(id)?.let { rec ->
                rec.status = RecordStatus.PAUSED
                AppContext.db.update(rec)
                EngineContext.ui.downloadUpdated(id)
                EngineContext.ui.downloadPaused(id)
            }
        }
    }

    fun cancelAll() {
        activeTasks.values.forEach { runCatching { it.cancel() } }
        activeTasks.clear()
        activeAdapters.clear()
    }
}
