package xdm.torrent

import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.FastresumeRejectedAlert
import org.libtorrent4j.alerts.FileErrorAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.SaveResumeDataFailedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.TorrentInfo
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadHost
import xdm.core.downloaders.DownloadStatusInfo
import xdm.core.downloaders.TaskInfoDB
import xdm.core.downloaders.TorrentDownloadTaskInfo
import xdm.core.downloaders.TorrentSourceTypes
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TorrentDownloadManager(
    val engine: TorrentEngine = LibtorrentEngine.instance,
    val configDir: File? = null,
    val taskInfoDB: TaskInfoDB? = configDir?.let { TaskInfoDB(it.absolutePath) }
) {

    private val tasksById = ConcurrentHashMap<Long, TorrentDownloadTask>()
    private val tasksByInfoHash = ConcurrentHashMap<String, TorrentDownloadTask>()

    private val alertListener = TorrentAlertDispatcher()
    private val isRunning = AtomicBoolean(false)
    private var pollerExecutor: ScheduledExecutorService? = null

    init {
        engine.addListener(alertListener)
        startPoller()
    }

    fun applyConfig(config: TorrentSessionConfig) {
        engine.applyConfig(config)
        val rule = SeedingRule(config.seedingMode, config.seedingRatioLimit, config.seedingTimeLimitSeconds)
        tasksById.values.forEach { task ->
            task.seedingRule = rule
        }
    }

    @Synchronized
    private fun startPoller() {
        if (isRunning.getAndSet(true)) return

        val executor = Executors.newSingleThreadScheduledExecutor { r ->
            val t = Thread(r, "XDM-Torrent-ProgressPoller")
            t.isDaemon = true
            t
        }
        pollerExecutor = executor

        executor.scheduleWithFixedDelay({
            try {
                pollProgress()
            } catch (_: Throwable) {}
        }, 250, 250, TimeUnit.MILLISECONDS)
    }

    private fun pollProgress() {
        if (!engine.isRunning()) return

        for (task in tasksById.values) {
            val state = task.state
            if (state == TorrentTaskState.DOWNLOADING ||
                state == TorrentTaskState.CHECKING_FILES ||
                state == TorrentTaskState.DOWNLOADING_METADATA ||
                state == TorrentTaskState.SEEDING
            ) {
                val snapshot = task.getSnapshot()

                task.host?.onDownloadProgress(
                    DownloadStatusInfo.ProgressInfo(
                        id = task.id,
                        progress = snapshot.progressPercent,
                        speed = snapshot.downloadRate.toFloat(),
                        eta = snapshot.etaSeconds,
                        downloaded = snapshot.downloadedBytes,
                        segments = listOf()
                    )
                )
            }
        }
    }

    fun createTask(
        id: Long,
        source: TorrentSource,
        saveDir: File,
        host: DownloadHost? = null,
        fileSelection: List<Boolean>? = null,
        initialPeers: List<TcpEndpoint>? = null,
        categoryId: String? = null
    ): TorrentDownloadTask {
        // Duplicate check by task ID
        tasksById[id]?.let { return it }

        val resumeFile = configDir?.let { File(it, "task-$id.resume") }

        val task = TorrentDownloadTask(
            id = id,
            source = source,
            saveDir = saveDir,
            engine = engine,
            host = host,
            initialFileSelection = fileSelection,
            peers = initialPeers,
            resumeFile = resumeFile,
            configDir = configDir,
            initialCategoryId = categoryId,
            onRemoveCallback = { t ->
                tasksById.remove(t.id)
                if (t.infoHash.isNotBlank()) {
                    tasksByInfoHash.remove(t.infoHash)
                }
            }
        )

        // Duplicate check by infoHash
        if (task.infoHash.isNotBlank()) {
            val existing = tasksByInfoHash[task.infoHash]
            if (existing != null) {
                return existing
            }
            tasksByInfoHash[task.infoHash] = task
        }

        tasksById[id] = task
        task.seedingRule = SeedingRule(engine.currentConfig.seedingMode, engine.currentConfig.seedingRatioLimit, engine.currentConfig.seedingTimeLimitSeconds)

        task.addListener(object : TorrentTaskListener {
            override fun onFinished(t: TorrentDownloadTask) {
                taskInfoDB?.saveTorrentTask(t.toTaskInfo(categoryId))
            }

            override fun onStateChanged(task: TorrentDownloadTask, oldState: TorrentTaskState, newState: TorrentTaskState) {
                if (newState == TorrentTaskState.PAUSED || newState == TorrentTaskState.FINISHED || newState == TorrentTaskState.SEEDING) {
                    taskInfoDB?.saveTorrentTask(task.toTaskInfo(categoryId))
                }
            }
        })

        backupTorrentSource(task)
        taskInfoDB?.saveTorrentTask(task.toTaskInfo(categoryId))

        return task
    }

    fun restoreTask(info: TorrentDownloadTaskInfo): TorrentDownloadTask? {
        tasksById[info.id]?.let { return it }
        if (info.infoHash.isNotBlank()) {
            tasksByInfoHash[info.infoHash.lowercase()]?.let { return it }
        }

        val source: TorrentSource? = when (info.sourceType) {
            TorrentSourceTypes.TORRENT_FILE -> {
                val originalFile = File(info.sourceUri)
                val backupFile = configDir?.let { File(it, "task-${info.id}.torrent") }
                when {
                    originalFile.exists() && originalFile.isFile -> TorrentSource.TorrentFile(originalFile)
                    backupFile != null && backupFile.exists() && backupFile.isFile -> TorrentSource.TorrentFile(backupFile)
                    else -> null
                }
            }
            TorrentSourceTypes.TORRENT_BYTES -> {
                val backupFile = configDir?.let { File(it, "task-${info.id}.torrent") }
                if (backupFile != null && backupFile.exists() && backupFile.isFile) {
                    TorrentSource.TorrentBytes(backupFile.readBytes(), info.name)
                } else null
            }
            TorrentSourceTypes.MAGNET_URI -> {
                if (info.sourceUri.isNotBlank()) {
                    TorrentSource.MagnetUri(info.sourceUri, info.name)
                } else null
            }
            else -> null
        }

        if (source == null) {
            return null
        }

        val saveDir = File(info.targetFolder)
        val resumeFile = configDir?.let { File(it, "task-${info.id}.resume") }

        val rawState = runCatching {
            TorrentTaskState.values().getOrNull(info.lastState) ?: TorrentTaskState.PAUSED
        }.getOrDefault(TorrentTaskState.PAUSED)

        val restoredState = when (rawState) {
            TorrentTaskState.DOWNLOADING,
            TorrentTaskState.CHECKING_FILES,
            TorrentTaskState.DOWNLOADING_METADATA -> TorrentTaskState.PAUSED
            else -> rawState
        }

        val task = TorrentDownloadTask(
            id = info.id,
            source = source,
            saveDir = saveDir,
            engine = engine,
            host = null,
            initialFileSelection = info.fileSelection,
            resumeFile = resumeFile,
            configDir = configDir,
            initialState = restoredState,
            initialCategoryId = info.categoryId,
            onRemoveCallback = { t ->
                tasksById.remove(t.id)
                if (t.infoHash.isNotBlank()) {
                    tasksByInfoHash.remove(t.infoHash)
                }
            }
        )

        // Duplicate check by infoHash
        if (task.infoHash.isNotBlank()) {
            val existing = tasksByInfoHash[task.infoHash]
            if (existing != null) {
                return existing
            }
            tasksByInfoHash[task.infoHash] = task
        }

        tasksById[task.id] = task

        if (info.downloadLimit > 0) {
            task.setDownloadLimit(info.downloadLimit)
        }
        if (info.uploadLimit > 0) {
            task.setUploadLimit(info.uploadLimit)
        }
        val seedingMode = if (info.seedingMode in SeedingMode.entries.indices) {
            SeedingMode.entries[info.seedingMode]
        } else {
            engine.currentConfig.seedingMode
        }
        task.seedingRule = SeedingRule(seedingMode, engine.currentConfig.seedingRatioLimit, engine.currentConfig.seedingTimeLimitSeconds)

        task.addListener(object : TorrentTaskListener {
            override fun onFinished(t: TorrentDownloadTask) {
                taskInfoDB?.saveTorrentTask(t.toTaskInfo(info.categoryId))
            }

            override fun onStateChanged(task: TorrentDownloadTask, oldState: TorrentTaskState, newState: TorrentTaskState) {
                if (newState == TorrentTaskState.PAUSED || newState == TorrentTaskState.FINISHED || newState == TorrentTaskState.SEEDING) {
                    taskInfoDB?.saveTorrentTask(task.toTaskInfo(info.categoryId))
                }
            }
        })

        return task
    }

    fun loadAllTasksFromDB(): List<TorrentDownloadTask> {
        val db = taskInfoDB ?: return emptyList()
        val restored = mutableListOf<TorrentDownloadTask>()
        for (id in db.allTaskIds) {
            val info = db.getTorrentTask(id) ?: continue
            val task = restoreTask(info)
            if (task != null) {
                restored.add(task)
            }
        }
        return restored
    }

    private fun backupTorrentSource(task: TorrentDownloadTask) {
        val dir = configDir ?: return
        if (!dir.exists()) dir.mkdirs()
        val backupFile = File(dir, "task-${task.id}.torrent")
        if (backupFile.exists()) return

        try {
            when (val s = task.source) {
                is TorrentSource.TorrentFile -> {
                    if (s.file.exists() && s.file.isFile) {
                        s.file.copyTo(backupFile, overwrite = true)
                    }
                }
                is TorrentSource.TorrentBytes -> {
                    backupFile.writeBytes(s.bytes)
                }
                is TorrentSource.MagnetUri -> {}
            }
        } catch (_: Throwable) {}
    }

    fun getTask(id: Long): TorrentDownloadTask? = tasksById[id]

    fun getTaskByInfoHash(infoHash: String): TorrentDownloadTask? = tasksByInfoHash[infoHash.lowercase()]

    fun getAllTasks(): Collection<TorrentDownloadTask> = tasksById.values

    fun removeTask(id: Long, deleteFiles: Boolean = false): Boolean {
        val task = tasksById.remove(id) ?: return false
        if (task.infoHash.isNotBlank()) {
            tasksByInfoHash.remove(task.infoHash)
        }
        task.remove(deleteFiles)
        taskInfoDB?.deleteRecord(id)
        val backup = configDir?.let { File(it, "task-$id.torrent") }
        if (backup != null && backup.exists()) {
            try { backup.delete() } catch (_: Throwable) {}
        }
        val resume = configDir?.let { File(it, "task-$id.resume") }
        if (resume != null && resume.exists()) {
            try { resume.delete() } catch (_: Throwable) {}
        }
        return true
    }

    @Synchronized
    fun shutdown(saveResumeData: Boolean = true, timeoutMs: Long = 3000) {
        isRunning.set(false)
        val executor = pollerExecutor
        pollerExecutor = null
        if (executor != null) {
            executor.shutdownNow()
            try {
                executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {}
        }

        if (saveResumeData) {
            for (task in tasksById.values) {
                try {
                    if (task.state == TorrentTaskState.DOWNLOADING ||
                        task.state == TorrentTaskState.SEEDING ||
                        task.state == TorrentTaskState.PAUSED ||
                        task.state == TorrentTaskState.FINISHED
                    ) {
                        task.saveResumeDataSync(1000)
                        taskInfoDB?.saveTorrentTask(task.toTaskInfo())
                    }
                } catch (_: Throwable) {}
            }
        }

        engine.removeListener(alertListener)
        for (task in tasksById.values) {
            task.stop()
        }
        tasksById.clear()
        tasksByInfoHash.clear()
    }

    private inner class TorrentAlertDispatcher : AlertListener {
        override fun types(): IntArray? {
            return intArrayOf(
                AlertType.ADD_TORRENT.swig(),
                AlertType.PIECE_FINISHED.swig(),
                AlertType.TORRENT_FINISHED.swig(),
                AlertType.TORRENT_ERROR.swig(),
                AlertType.FILE_ERROR.swig(),
                AlertType.METADATA_RECEIVED.swig(),
                AlertType.SAVE_RESUME_DATA.swig(),
                AlertType.SAVE_RESUME_DATA_FAILED.swig(),
                AlertType.FASTRESUME_REJECTED.swig()
            )
        }

        override fun alert(alert: Alert<*>?) {
            if (alert == null) return

            when (alert) {
                is AddTorrentAlert -> {
                    try {
                        val handle = alert.handle()
                        if (handle != null && handle.isValid) {
                            val hash = handle.infoHash().toHex().lowercase()
                            var task = tasksByInfoHash[hash]
                            if (task == null) {
                                task = tasksById.values.find { it.infoHash == hash || it.infoHash.isBlank() }
                                if (task != null) {
                                    tasksByInfoHash[hash] = task
                                }
                            }
                            task?.updateMetadata(handle.getName(), handle.torrentFile())
                        }
                    } catch (_: Throwable) {}
                }
                is PieceFinishedAlert -> {
                    try {
                        val handle = alert.handle()
                        if (handle != null && handle.isValid) {
                            val hash = handle.infoHash().toHex().lowercase()
                            val task = tasksByInfoHash[hash]
                            task?.onPieceFinished(alert.pieceIndex())
                        }
                    } catch (_: Throwable) {}
                }
                is TorrentFinishedAlert -> {
                    try {
                        val handle = alert.handle()
                        if (handle != null && handle.isValid) {
                            val hash = handle.infoHash().toHex().lowercase()
                            val task = tasksByInfoHash[hash]
                            val finalSize = handle.status().totalDone()
                            task?.onCompleted(finalSize)
                            task?.saveResumeDataSync(1000)
                        }
                    } catch (_: Throwable) {}
                }
                is TorrentErrorAlert -> {
                    try {
                        val handle = alert.handle()
                        if (handle != null && handle.isValid) {
                            val hash = handle.infoHash().toHex().lowercase()
                            val task = tasksByInfoHash[hash]
                            val msg = alert.error()?.message ?: "Torrent error occurred"
                            task?.handleFailure(msg, DownloadError.InternalError)
                        }
                    } catch (_: Throwable) {}
                }
                is FileErrorAlert -> {
                    try {
                        val handle = alert.handle()
                        if (handle != null && handle.isValid) {
                            val hash = handle.infoHash().toHex().lowercase()
                            val task = tasksByInfoHash[hash]
                            val msg = "File error on '${alert.filename()}': ${alert.error()?.message}"
                            task?.handleFailure(msg, DownloadError.OutputWriteError)
                        }
                    } catch (_: Throwable) {}
                }
                is MetadataReceivedAlert -> {
                    try {
                        val handle = alert.handle()
                        if (handle != null && handle.isValid) {
                            val hash = handle.infoHash().toHex().lowercase()
                            val task = tasksByInfoHash[hash]
                            task?.updateMetadata(handle.getName(), handle.torrentFile())
                        }
                    } catch (_: Throwable) {}
                }
                is SaveResumeDataAlert -> {
                    try {
                        val params = alert.params()
                        val bytes = AddTorrentParams.writeResumeDataBuf(params)
                        val handle = alert.handle()
                        val hash = if (handle != null && handle.isValid) {
                            handle.infoHash().toHex().lowercase()
                        } else {
                            params?.infoHashes?.best?.toHex()?.lowercase() ?: ""
                        }
                        val task = tasksByInfoHash[hash] ?: tasksById.values.find { it.infoHash == hash }
                        if (task != null && bytes != null) {
                            task.onSaveResumeDataSuccess(bytes)
                        }
                    } catch (_: Throwable) {}
                }
                is SaveResumeDataFailedAlert -> {
                    try {
                        val handle = alert.handle()
                        val hash = if (handle != null && handle.isValid) handle.infoHash().toHex().lowercase() else ""
                        val task = tasksByInfoHash[hash] ?: tasksById.values.find { it.infoHash == hash }
                        task?.onSaveResumeDataFailed(alert.error())
                    } catch (_: Throwable) {}
                }
                is FastresumeRejectedAlert -> {
                    try {
                        val handle = alert.handle()
                        val hash = if (handle != null && handle.isValid) handle.infoHash().toHex().lowercase() else ""
                        val task = tasksByInfoHash[hash] ?: tasksById.values.find { it.infoHash == hash }
                        task?.onFastresumeRejected(alert.error())
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    companion object {
        val instance: TorrentDownloadManager by lazy { TorrentDownloadManager() }
    }
}
