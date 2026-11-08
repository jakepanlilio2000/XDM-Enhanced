package xdm.torrent

import org.libtorrent4j.ErrorCode
import org.libtorrent4j.Priority
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.swig.session_handle
import org.libtorrent4j.swig.torrent_flags_t
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadHost
import xdm.core.downloaders.DownloadStatusInfo
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.DownloaderTask
import xdm.core.downloaders.PauseEvent
import xdm.core.downloaders.TorrentDownloadTaskInfo
import xdm.core.downloaders.TorrentSourceTypes
import xdm.core.util.FileUtils
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TorrentDownloadTask(
    val id: Long,
    val source: TorrentSource,
    saveDir: File,
    val engine: TorrentEngine = LibtorrentEngine.instance,
    val host: DownloadHost? = null,
    initialFileSelection: List<Boolean>? = null,
    peers: List<TcpEndpoint>? = null,
    var resumeFile: File? = null,
    val configDir: File? = null,
    initialState: TorrentTaskState? = null,
    val initialCategoryId: String? = null,
    val initialDateAdded: Long? = null,
    private val onRemoveCallback: ((TorrentDownloadTask) -> Unit)? = null
) : DownloaderTask {

    @Volatile
    var saveDir: File = saveDir
        private set

    private val listeners = CopyOnWriteArrayList<TorrentTaskListener>()
    private val stateRef = AtomicReference(initialState ?: TorrentTaskState.INITIALIZING)
    private val finishedNotified = AtomicBoolean(initialState == TorrentTaskState.FINISHED || initialState == TorrentTaskState.SEEDING)
    private val lastError = AtomicReference<String?>(null)
    private val isRemoved = AtomicBoolean(false)

    private val pendingResumeLatch = AtomicReference<CountDownLatch?>()
    private val lastResumeData = AtomicReference<ByteArray?>()

    private var parsedInfo: TorrentInfo? = null
    private var infoHashHex: String = ""
    private var taskName: String = source.name

    val fileSelection = CopyOnWriteArrayList<Boolean>()
    val knownPeers = CopyOnWriteArrayList<TcpEndpoint>()

    var downloadLimit: Int = 0
        private set
    var uploadLimit: Int = 0
        private set
    var seedingRule: SeedingRule = SeedingRule()

    val isPrivate: Boolean
        get() = parsedInfo?.isPrivate() ?: false

    init {
        peers?.let { knownPeers.addAll(it) }
        initialFileSelection?.let { fileSelection.addAll(it) }
        initMetadataFromSource()
    }

    val state: TorrentTaskState
        get() = stateRef.get()

    val name: String
        get() = taskName

    val infoHash: String
        get() = infoHashHex

    val isCompleted: Boolean
        get() = stateRef.get() == TorrentTaskState.FINISHED || stateRef.get() == TorrentTaskState.SEEDING

    fun getTorrentInfo(): TorrentInfo? = parsedInfo

    fun getMetadata(): TorrentMetadata? {
        val ti = parsedInfo ?: return null
        return try {
            TorrentMetadataParser.fromTorrentInfo(ti)
        } catch (_: Throwable) {
            null
        }
    }

    fun getHandle(): TorrentHandle? {
        if (isRemoved.get() || stateRef.get() == TorrentTaskState.STOPPED) return null
        if (infoHashHex.isBlank() || !engine.isRunning()) return null

        return try {
            val sha = Sha1Hash.parseHex(infoHashHex)
            val found = engine.sessionManager().find(sha)
            if (found != null && found.isValid) found else null
        } catch (_: Throwable) {
            null
        }
    }

    internal fun updateMetadata(newName: String?, ti: TorrentInfo?) {
        if (!newName.isNullOrBlank()) {
            val safe = FileUtils.sanitizeFileName(newName)?.takeIf { it.isNotBlank() }
            if (safe != null && (taskName.isBlank() || taskName == "torrent" || taskName == "magnet")) {
                taskName = safe
            }
        }
        if (parsedInfo == null && ti != null && ti.isValid) {
            parsedInfo = ti
            val safe = FileUtils.sanitizeFileName(ti.name())?.takeIf { it.isNotBlank() }
            if (safe != null) {
                taskName = safe
            }
            initFileSelectionIfEmpty(ti.files().numFiles())
        }
    }

    private fun initMetadataFromSource() {
        when (source) {
            is TorrentSource.TorrentFile -> {
                if (source.file.exists() && source.file.isFile) {
                    if (source.file.length() > TorrentMetadataParser.MAX_TORRENT_FILE_SIZE) {
                        lastError.set("Torrent file exceeds maximum allowed size (${TorrentMetadataParser.MAX_TORRENT_FILE_SIZE} bytes): ${source.file.length()} bytes")
                        return
                    }
                    try {
                        TorrentNativeLoader.load()
                        val bytes = source.file.readBytes()
                        val ti = TorrentInfo(bytes)
                        parsedInfo = ti
                        taskName = FileUtils.sanitizeFileName(ti.name())?.takeIf { it.isNotBlank() } ?: "torrent"
                        infoHashHex = ti.infoHash().toHex().lowercase()
                        initFileSelectionIfEmpty(ti.files().numFiles())
                    } catch (e: Throwable) {
                        lastError.set("Failed to read torrent file: ${e.message}")
                    }
                }
            }
            is TorrentSource.TorrentBytes -> {
                if (source.bytes.size > TorrentMetadataParser.MAX_TORRENT_FILE_SIZE) {
                    lastError.set("Torrent bytes exceed maximum allowed size (${TorrentMetadataParser.MAX_TORRENT_FILE_SIZE} bytes): ${source.bytes.size} bytes")
                    return
                }
                try {
                    TorrentNativeLoader.load()
                    val ti = TorrentInfo(source.bytes)
                    parsedInfo = ti
                    taskName = FileUtils.sanitizeFileName(ti.name())?.takeIf { it.isNotBlank() } ?: "torrent"
                    infoHashHex = ti.infoHash().toHex().lowercase()
                    initFileSelectionIfEmpty(ti.files().numFiles())
                } catch (e: Throwable) {
                    lastError.set("Failed to read torrent bytes: ${e.message}")
                }
            }
            is TorrentSource.MagnetUri -> {
                taskName = FileUtils.sanitizeFileName(source.name)?.takeIf { it.isNotBlank() } ?: "magnet"
                try {
                    TorrentNativeLoader.load()
                    val ec = org.libtorrent4j.swig.error_code()
                    val p = org.libtorrent4j.swig.libtorrent.parse_magnet_uri(source.uri, ec)
                    if (p != null) {
                        val ih = p.info_hashes
                        if (ih != null) {
                            if (ih.has_v1()) {
                                infoHashHex = ih.v1.to_hex().lowercase()
                            } else if (ih.has_v2()) {
                                infoHashHex = ih.v2.to_hex().lowercase()
                            }
                        }
                        if (!p.name.isNullOrBlank()) {
                            val safe = FileUtils.sanitizeFileName(p.name)?.takeIf { it.isNotBlank() }
                            if (safe != null) {
                                taskName = safe
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun initFileSelectionIfEmpty(numFiles: Int) {
        if (fileSelection.isEmpty()) {
            for (i in 0 until numFiles) {
                fileSelection.add(true)
            }
        }
    }

    fun changeSaveDir(newDir: File): Boolean {
        if (newDir.exists() && !newDir.isDirectory) {
            return false
        }
        if (!newDir.exists() && !newDir.mkdirs()) {
            if (!newDir.isDirectory) return false
        }
        this.saveDir = newDir
        if (stateRef.get() == TorrentTaskState.ERROR) {
            lastError.set(null)
            updateState(TorrentTaskState.PAUSED)
        }
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.moveStorage(newDir.absolutePath)
            } catch (_: Throwable) {
                return false
            }
        }
        return true
    }

    @Synchronized
    override fun start() {
        val cur = stateRef.get()
        if (cur == TorrentTaskState.DOWNLOADING || cur == TorrentTaskState.SEEDING) {
            return
        }
        if (cur == TorrentTaskState.PAUSED) {
            val handle = getHandle()
            if (handle != null && handle.isValid) {
                resume()
                return
            }
        }

        // Validate destination directory
        if (saveDir.exists() && !saveDir.isDirectory) {
            val err = "Destination path exists and is a file, not a directory: ${saveDir.absolutePath}"
            handleFailure(err, DownloadError.OutputWriteError)
            return
        }
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            if (!saveDir.isDirectory) {
                val err = "Failed to create destination directory: ${saveDir.absolutePath}"
                handleFailure(err, DownloadError.OutputWriteError)
                return
            }
        }

        try {
            if (!engine.isRunning()) {
                engine.start()
            }

            val sm = engine.sessionManager()

            when (source) {
                is TorrentSource.TorrentFile, is TorrentSource.TorrentBytes -> {
                    val ti = parsedInfo ?: throw TorrentEngineException("TorrentInfo is null; cannot start download")
                    val priorities = if (fileSelection.isNotEmpty() && fileSelection.size == ti.files().numFiles()) {
                        Array(fileSelection.size) { idx ->
                            if (fileSelection[idx]) Priority.DEFAULT else Priority.IGNORE
                        }
                    } else null

                    val actualResumeFile = when {
                        resumeFile != null && resumeFile!!.exists() && resumeFile!!.length() > 0 -> resumeFile
                        configDir != null -> {
                            val f = File(configDir, "task-$id.resume")
                            if (f.exists() && f.length() > 0) f else null
                        }
                        else -> null
                    }

                    try {
                        sm.download(ti, saveDir, actualResumeFile, priorities, knownPeers.toList(), torrent_flags_t())
                    } catch (e: IllegalArgumentException) {
                        // Corrupted resume data: delete invalid resume file and fall back to clean piece verification
                        if (actualResumeFile != null) {
                            try { actualResumeFile.delete() } catch (_: Throwable) {}
                            this.resumeFile = null
                            sm.download(ti, saveDir, null, priorities, knownPeers.toList(), torrent_flags_t())
                        } else {
                            throw e
                        }
                    }

                    if (infoHashHex.isBlank()) {
                        infoHashHex = ti.infoHash().toHex().lowercase()
                    }
                }
                is TorrentSource.MagnetUri -> {
                    sm.download(source.uri, saveDir, torrent_flags_t())
                }
            }

            val handle = getHandle()
            if (handle != null && handle.isValid) {
                if (downloadLimit > 0) {
                    try { handle.downloadLimit = downloadLimit } catch (_: Throwable) {}
                }
                if (uploadLimit > 0) {
                    try { handle.uploadLimit = uploadLimit } catch (_: Throwable) {}
                }
            }

            updateState(TorrentTaskState.DOWNLOADING)

            host?.onDownloadActivated(id)
            val totalSize = parsedInfo?.totalSize() ?: 0L
            host?.onDownloadInit(
                DownloadStatusInfo.InitInfo(
                    id = id,
                    url = if (source is TorrentSource.MagnetUri) source.uri else "torrent://$infoHashHex",
                    isRedirect = false,
                    fileSize = totalSize,
                    contentDisposition = null,
                    contentType = "application/x-bittorrent"
                ),
                DownloadType.Torrent
            )
        } catch (t: Throwable) {
            handleFailure("Failed to start torrent transfer: ${t.message}", DownloadError.InternalError, t)
        }
    }

    @Synchronized
    fun pause() {
        val cur = stateRef.get()
        if (cur == TorrentTaskState.PAUSED || cur == TorrentTaskState.STOPPED) return

        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.pause()
            } catch (_: Throwable) {}
        }
        if (cur != TorrentTaskState.FINISHED) {
            updateState(TorrentTaskState.PAUSED)
            host?.onDownloadPaused(id, PauseEvent.PausedByUser)
        }
    }

    @Synchronized
    override fun resume() {
        val cur = stateRef.get()
        if (cur == TorrentTaskState.DOWNLOADING) return

        if (cur == TorrentTaskState.STOPPED || cur == TorrentTaskState.INITIALIZING) {
            start()
            return
        }

        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.resume()
            } catch (t: Throwable) {
                handleFailure("Failed to resume torrent: ${t.message}", DownloadError.InternalError, t)
                return
            }
        } else {
            // No active handle, restart
            start()
            return
        }
        updateState(TorrentTaskState.DOWNLOADING)
        host?.onDownloadActivated(id)
    }

    @Synchronized
    override fun stop() {
        val cur = stateRef.get()
        if (cur == TorrentTaskState.STOPPED) return

        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.pause()
            } catch (_: Throwable) {}
        }
        if (cur != TorrentTaskState.FINISHED) {
            updateState(TorrentTaskState.STOPPED)
            host?.onDownloadPaused(id, PauseEvent.PausedByUser)
        }
    }

    override fun deleteTemp() {
        // BitTorrent writes directly to target files via piece cache.
        // No separate temp directory to purge.
    }

    @Synchronized
    fun remove(deleteFiles: Boolean = false) {
        if (isRemoved.getAndSet(true)) return
        updateState(TorrentTaskState.STOPPED)

        var handleDeleted = false
        if (engine.isRunning() && infoHashHex.isNotBlank()) {
            try {
                val sm = engine.sessionManager()
                val sha = Sha1Hash.parseHex(infoHashHex)
                val handle = sm.find(sha)
                if (handle != null && handle.isValid) {
                    if (deleteFiles) {
                        sm.remove(handle, session_handle.delete_files)
                        handleDeleted = true
                    } else {
                        sm.remove(handle)
                    }
                }
            } catch (_: Throwable) {}
        }
        if (deleteFiles && !handleDeleted) {
            deleteFilesSafelyFromDisk()
        }
        val resume = resumeFile ?: configDir?.let { File(it, "task-$id.resume") }
        if (resume != null && resume.exists()) {
            try { resume.delete() } catch (_: Throwable) {}
        }
        onRemoveCallback?.invoke(this)
    }

    /**
     * Safely deletes downloaded files from disk, strictly verifying that target paths
     * are descendants of [saveDir] and never deleting [saveDir] itself.
     */
    internal fun deleteFilesSafelyFromDisk() {
        val canonicalSaveDir = try { saveDir.canonicalFile } catch (_: Throwable) { saveDir.absoluteFile }
        val canonicalSaveDirPath = canonicalSaveDir.path
        val ti = parsedInfo
        if (ti != null) {
            val fs = ti.files()
            for (i in 0 until fs.numFiles()) {
                val rel = fs.filePath(i)
                val safeRel = TorrentMetadataParser.sanitizeTorrentFilePath(rel, i)
                val f = File(canonicalSaveDir, safeRel)
                try {
                    val canonicalFile = f.canonicalFile
                    if (canonicalFile.path.startsWith(canonicalSaveDirPath + File.separator) && canonicalFile != canonicalSaveDir) {
                        if (canonicalFile.exists() && canonicalFile.isFile) {
                            canonicalFile.delete()
                        }
                    }
                } catch (_: Throwable) {}
            }
            val rawName = ti.name()
            val safeName = FileUtils.sanitizeFileName(rawName)?.takeIf { it.isNotBlank() }
            if (safeName != null) {
                val topFolder = File(canonicalSaveDir, safeName)
                try {
                    val canonicalTop = topFolder.canonicalFile
                    if (canonicalTop.path.startsWith(canonicalSaveDirPath + File.separator) &&
                        canonicalTop != canonicalSaveDir &&
                        canonicalTop.exists()
                    ) {
                        if (canonicalTop.isDirectory) {
                            canonicalTop.deleteRecursively()
                        } else if (canonicalTop.isFile) {
                            canonicalTop.delete()
                        }
                    }
                } catch (_: Throwable) {}
            }
        } else if (taskName.isNotBlank()) {
            val safeName = FileUtils.sanitizeFileName(taskName)?.takeIf { it.isNotBlank() }
            if (safeName != null) {
                val f = File(canonicalSaveDir, safeName)
                try {
                    val canonicalFile = f.canonicalFile
                    if (canonicalFile.path.startsWith(canonicalSaveDirPath + File.separator) &&
                        canonicalFile != canonicalSaveDir &&
                        canonicalFile.exists()
                    ) {
                        if (canonicalFile.isDirectory) {
                            canonicalFile.deleteRecursively()
                        } else if (canonicalFile.isFile) {
                            canonicalFile.delete()
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun forceRecheck() {
        val handle = getHandle() ?: return
        try {
            if (handle.isValid) {
                updateState(TorrentTaskState.CHECKING_FILES)
                handle.forceRecheck()
            }
        } catch (_: Throwable) {}
    }

    fun setDownloadLimit(limitBytesPerSec: Int) {
        this.downloadLimit = limitBytesPerSec
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.downloadLimit = limitBytesPerSec
            } catch (_: Throwable) {}
        }
    }

    fun setUploadLimit(limitBytesPerSec: Int) {
        this.uploadLimit = limitBytesPerSec
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.uploadLimit = limitBytesPerSec
            } catch (_: Throwable) {}
        }
    }

    val queuePosition: Int
        get() = getHandle()?.queuePosition() ?: -1

    fun queueUp() {
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try { handle.queuePositionUp() } catch (_: Throwable) {}
        }
    }

    fun queueDown() {
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try { handle.queuePositionDown() } catch (_: Throwable) {}
        }
    }

    fun queueTop() {
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try { handle.queuePositionTop() } catch (_: Throwable) {}
        }
    }

    fun queueBottom() {
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try { handle.queuePositionBottom() } catch (_: Throwable) {}
        }
    }

    fun setQueuePosition(pos: Int) {
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try { handle.queuePositionSet(pos) } catch (_: Throwable) {}
        }
    }

    fun getTrackers(): List<TorrentTrackerInfo> {
        val handle = getHandle() ?: return emptyList()
        return try {
            if (!handle.isValid) return emptyList()
            handle.trackers().map { entry ->
                val ep = entry.endpoints().firstOrNull()
                val v1 = ep?.infohashV1()
                TorrentTrackerInfo(
                    url = entry.url(),
                    tier = entry.tier(),
                    isWorking = v1?.isWorking ?: false,
                    isUpdating = v1?.updating() ?: false,
                    fails = v1?.fails()?.toInt() ?: 0,
                    message = v1?.message().orEmpty()
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun addTracker(url: String, tier: Int = 0): Boolean {
        val handle = getHandle() ?: return false
        return try {
            if (!handle.isValid) return false
            val entry = org.libtorrent4j.AnnounceEntry(url).apply { tier(tier.toShort()) }
            handle.addTracker(entry)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun reannounce() {
        val handle = getHandle() ?: return
        try {
            if (handle.isValid) {
                handle.forceReannounce()
                if (!isPrivate) {
                    handle.forceDHTAnnounce()
                }
            }
        } catch (_: Throwable) {}
    }

    fun getPeers(): List<TorrentPeerInfo> {
        val handle = getHandle() ?: return emptyList()
        return try {
            if (!handle.isValid) return emptyList()
            handle.peerInfo().map { p ->
                val srcMask = p.source().toInt()
                val sources = mutableListOf<String>()
                if (srcMask and 1 != 0) sources.add("Tracker")
                if (srcMask and 2 != 0) sources.add("DHT")
                if (srcMask and 4 != 0) sources.add("PEX")
                if (srcMask and 8 != 0) sources.add("LSD")
                if (srcMask and 32 != 0) sources.add("Incoming")
                val sourceStr = if (sources.isNotEmpty()) sources.joinToString(", ") else "Peer"

                TorrentPeerInfo(
                    ip = p.ip(),
                    client = p.client().ifBlank { "Unknown" },
                    downSpeed = p.downSpeed(),
                    upSpeed = p.upSpeed(),
                    progressPercent = (p.progress() * 100).toInt().coerceIn(0, 100),
                    totalDownload = p.totalDownload(),
                    totalUpload = p.totalUpload(),
                    connectionType = p.connectionType()?.name ?: "TCP",
                    flags = p.flags().toString(),
                    source = sourceStr
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun getFilePriorities(): List<TorrentPriority> {
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                return handle.filePriorities().map { TorrentPriority.fromLibtorrent(it) }
            } catch (_: Throwable) {}
        }
        return fileSelection.map { if (it) TorrentPriority.NORMAL else TorrentPriority.DO_NOT_DOWNLOAD }
    }

    fun setFilePriority(fileIndex: Int, priority: TorrentPriority) {
        if (fileIndex in fileSelection.indices) {
            fileSelection[fileIndex] = (priority != TorrentPriority.DO_NOT_DOWNLOAD)
            val handle = getHandle()
            if (handle != null && handle.isValid) {
                try {
                    handle.filePriority(fileIndex, priority.toLibtorrent())
                } catch (_: Throwable) {}
            }
        }
    }

    fun setFilePriority(fileIndex: Int, selected: Boolean) {
        setFilePriority(fileIndex, if (selected) TorrentPriority.NORMAL else TorrentPriority.DO_NOT_DOWNLOAD)
    }

    fun startSeeding() {
        if (!isCompleted) return
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.resume()
                updateState(TorrentTaskState.SEEDING)
            } catch (_: Throwable) {}
        }
    }

    fun stopSeeding() {
        val handle = getHandle()
        if (handle != null && handle.isValid) {
            try {
                handle.pause()
            } catch (_: Throwable) {}
        }
        updateState(TorrentTaskState.FINISHED)
    }

    fun addPeer(endpoint: TcpEndpoint) {
        if (!knownPeers.contains(endpoint)) {
            knownPeers.add(endpoint)
        }
        val handle = getHandle()
        if (handle != null) {
            try {
                if (handle.isValid) {
                    handle.swig().connect_peer(endpoint.swig())
                }
            } catch (_: Throwable) {}
        }
    }

    fun addPeers(endpoints: Collection<TcpEndpoint>) {
        endpoints.forEach { addPeer(it) }
    }

    fun saveResumeDataSync(timeoutMs: Long = 3000): ByteArray? {
        val handle = getHandle()
        if (handle == null || !handle.isValid) {
            val target = resumeFile ?: configDir?.let { File(it, "task-$id.resume") }
            return if (target != null && target.exists()) {
                runCatching { target.readBytes() }.getOrNull()
            } else null
        }

        val latch = CountDownLatch(1)
        pendingResumeLatch.set(latch)
        try {
            handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return lastResumeData.get()
        } catch (_: Throwable) {
            return null
        } finally {
            pendingResumeLatch.set(null)
        }
    }

    internal fun onSaveResumeDataSuccess(bytes: ByteArray) {
        lastResumeData.set(bytes)
        persistResumeBytes(bytes)
        pendingResumeLatch.get()?.countDown()
    }

    internal fun onSaveResumeDataFailed(error: ErrorCode?) {
        pendingResumeLatch.get()?.countDown()
    }

    internal fun onFastresumeRejected(error: ErrorCode?) {
        // Fastresume rejected by libtorrent (e.g. file size mismatch on disk).
        // Remove corrupted resume file so subsequent runs recheck cleanly.
        val target = resumeFile ?: configDir?.let { File(it, "task-$id.resume") }
        if (target != null && target.exists()) {
            try { target.delete() } catch (_: Throwable) {}
        }
    }

    private fun persistResumeBytes(bytes: ByteArray) {
        val target = when {
            resumeFile != null -> resumeFile!!
            configDir != null -> File(configDir, "task-$id.resume")
            else -> null
        } ?: return

        try {
            val parent = target.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val tmp = File(parent, "${target.name}.tmp")
            tmp.outputStream().use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            try {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Throwable) {
                if (target.exists()) target.delete()
                tmp.renameTo(target)
            }
        } catch (_: Throwable) {}
    }

    fun toTaskInfo(categoryId: String? = null): TorrentDownloadTaskInfo {
        val cat = categoryId ?: initialCategoryId
        val (sourceType, sourceUri) = when (val s = source) {
            is TorrentSource.TorrentFile -> Pair(TorrentSourceTypes.TORRENT_FILE, s.file.absolutePath)
            is TorrentSource.TorrentBytes -> Pair(TorrentSourceTypes.TORRENT_BYTES, "")
            is TorrentSource.MagnetUri -> Pair(TorrentSourceTypes.MAGNET_URI, s.uri)
        }
        val snap = getSnapshot()
        val effectiveState = if (isCompleted) {
            if (state == TorrentTaskState.SEEDING) TorrentTaskState.SEEDING.ordinal else TorrentTaskState.FINISHED.ordinal
        } else {
            state.ordinal
        }
        return TorrentDownloadTaskInfo(
            id = id,
            name = name,
            targetFolder = saveDir.absolutePath,
            infoHash = infoHash,
            sourceType = sourceType,
            sourceUri = sourceUri,
            fileSelection = fileSelection.toList(),
            totalSize = if (snap.totalBytes > 0) snap.totalBytes else (parsedInfo?.totalSize() ?: 0L),
            categoryId = cat,
            lastState = effectiveState,
            downloadLimit = downloadLimit,
            uploadLimit = uploadLimit,
            seedingMode = seedingRule.mode.ordinal
        )
    }

    @Synchronized
    fun getSnapshot(): TorrentProgressSnapshot {
        val handle = getHandle()
        val st: TorrentStatus? = if (handle != null) {
            try {
                if (handle.isValid) handle.status(TorrentHandle.QUERY_PIECES) else null
            } catch (_: Throwable) {
                null
            }
        } else null

        if (st == null) {
            val total = parsedInfo?.totalSize() ?: 0L
            val numPieces = parsedInfo?.numPieces() ?: 0
            return TorrentProgressSnapshot(
                id = id,
                infoHash = infoHashHex,
                name = taskName,
                state = stateRef.get(),
                progressPercent = if (isCompleted) 100 else 0,
                progressFraction = if (isCompleted) 1.0f else 0.0f,
                downloadedBytes = if (isCompleted) total else 0L,
                totalBytes = total,
                downloadRate = 0,
                uploadRate = 0,
                numPeers = 0,
                numSeeds = 0,
                verifiedPieces = if (isCompleted) numPieces else 0,
                totalPieces = numPieces,
                etaSeconds = 0L,
                errorMessage = lastError.get()
            )
        }
        val totalDone = st.totalDone()
        val totalWantedDone = st.totalWantedDone()
        val totalWanted = st.totalWanted()
        val total = parsedInfo?.totalSize() ?: st.total()

        val isDone = isCompleted || st.isFinished || st.isSeeding
        val effectiveTotal = if (totalWanted > 0) totalWanted else total
        val effectiveDone = if (isDone) {
            if (effectiveTotal > 0) effectiveTotal else totalDone
        } else {
            if (totalWantedDone > 0) totalWantedDone else totalDone
        }

        val progressFraction = if (isDone) {
            1.0f
        } else if (effectiveTotal > 0) {
            (effectiveDone.toDouble() / effectiveTotal.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
        } else {
            st.progress()
        }
        val progressPercent = if (isDone) 100 else (progressFraction * 100).toInt().coerceIn(0, 100)

        val downRate = st.downloadPayloadRate()
        val upRate = st.uploadPayloadRate()
        val peers = st.numPeers()
        val seeds = st.numSeeds()
        val numPieces = if (parsedInfo != null && parsedInfo!!.numPieces() > 0) parsedInfo!!.numPieces() else st.numPieces()
        val verified = if (isDone) {
            numPieces
        } else {
            try {
                val p = st.pieces()
                if (p != null && p.count() > 0) p.count() else (st.verifiedPieces()?.count() ?: 0)
            } catch (_: Throwable) { 0 }
        }

        val eta = if (downRate > 0 && effectiveTotal > effectiveDone) {
            (effectiveTotal - effectiveDone) / downRate
        } else 0L

        val totalUp = st.totalPayloadUpload()
        val totalDown = kotlin.math.max(1L, st.totalPayloadDownload())
        val ratio = (totalUp.toDouble() / totalDown.toDouble()).toFloat()
        val seedSecs = st.seedingDuration()
        val queuePos = st.queuePosition()

        val derivedState = when {
            stateRef.get() == TorrentTaskState.PAUSED -> TorrentTaskState.PAUSED
            stateRef.get() == TorrentTaskState.STOPPED -> TorrentTaskState.STOPPED
            stateRef.get() == TorrentTaskState.ERROR -> TorrentTaskState.ERROR
            st.isFinished -> TorrentTaskState.FINISHED
            st.isSeeding -> TorrentTaskState.SEEDING
            st.state() == TorrentStatus.State.CHECKING_FILES || st.state() == TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentTaskState.CHECKING_FILES
            st.state() == TorrentStatus.State.DOWNLOADING_METADATA -> TorrentTaskState.DOWNLOADING_METADATA
            st.state() == TorrentStatus.State.DOWNLOADING -> TorrentTaskState.DOWNLOADING
            else -> stateRef.get()
        }

        if (derivedState != stateRef.get() &&
            stateRef.get() != TorrentTaskState.PAUSED &&
            stateRef.get() != TorrentTaskState.STOPPED &&
            stateRef.get() != TorrentTaskState.ERROR
        ) {
            updateState(derivedState)
        }

        if (stateRef.get() == TorrentTaskState.SEEDING) {
            if (seedingRule.mode == SeedingMode.NEVER) {
                try { handle?.pause() } catch (_: Throwable) {}
                updateState(TorrentTaskState.FINISHED)
            } else if (seedingRule.mode == SeedingMode.RATIO_OR_TIME) {
                val ratioHit = seedingRule.maxRatio > 0f && ratio >= seedingRule.maxRatio
                val timeHit = seedingRule.maxTimeSeconds > 0L && seedSecs >= seedingRule.maxTimeSeconds
                if (ratioHit || timeHit) {
                    try { handle?.pause() } catch (_: Throwable) {}
                    updateState(TorrentTaskState.FINISHED)
                }
            }
        }

        if (st.isFinished && !finishedNotified.getAndSet(true)) {
            onCompleted(effectiveDone)
        }

        val snapshot = TorrentProgressSnapshot(
            id = id,
            infoHash = infoHashHex,
            name = taskName,
            state = stateRef.get(),
            progressPercent = progressPercent,
            progressFraction = progressFraction,
            downloadedBytes = effectiveDone,
            totalBytes = effectiveTotal,
            downloadRate = downRate,
            uploadRate = upRate,
            numPeers = peers,
            numSeeds = seeds,
            verifiedPieces = verified,
            totalPieces = numPieces,
            etaSeconds = eta,
            errorMessage = lastError.get(),
            totalUploadBytes = totalUp,
            shareRatio = ratio,
            seedingDurationSeconds = seedSecs,
            queuePosition = queuePos,
            isPrivate = isPrivate
        )
        listeners.forEach { it.onProgress(this, snapshot) }
        return snapshot
    }

    internal fun onPieceFinished(pieceIndex: Int) {
        val handle = getHandle()
        val verifiedCount = try { handle?.status()?.verifiedPieces()?.count() ?: 0 } catch (_: Throwable) { 0 }
        val totalPieces = parsedInfo?.numPieces() ?: 0
        listeners.forEach { it.onPieceFinished(this, pieceIndex, verifiedCount, totalPieces) }
    }

    internal fun onCompleted(finalSize: Long) {
        try {
            getHandle()?.flushCache()
        } catch (_: Throwable) {}
        if (seedingRule.mode == SeedingMode.NEVER) {
            try { getHandle()?.pause() } catch (_: Throwable) {}
            updateState(TorrentTaskState.FINISHED)
        } else {
            updateState(TorrentTaskState.SEEDING)
        }
        host?.onDownloadSuccess(
            DownloadStatusInfo.FinalInfo(
                id = id,
                fileSize = finalSize,
                finalFileName = taskName,
                finalOutputFolder = saveDir.absolutePath
            )
        )
        listeners.forEach { it.onFinished(this) }
    }

    internal fun handleFailure(message: String, error: DownloadError, cause: Throwable? = null) {
        lastError.set(message)
        updateState(TorrentTaskState.ERROR)
        host?.onDownloadFailed(id, error)
        listeners.forEach { it.onError(this, message, error) }
    }

    fun addListener(listener: TorrentTaskListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: TorrentTaskListener) {
        listeners.remove(listener)
    }

    private fun updateState(newState: TorrentTaskState) {
        val old = stateRef.getAndSet(newState)
        if (old != newState) {
            listeners.forEach { it.onStateChanged(this, old, newState) }
        }
    }
}
