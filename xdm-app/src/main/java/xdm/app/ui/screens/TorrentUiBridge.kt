package xdm.app.ui.screens

import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.components.MessageBox
import xdm.app.utils.captureCategory
import xdm.app.utils.getClipBoardText
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadType
import xdm.core.util.CoreUtils
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.*
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

object TorrentUiBridge {

    private val lastUpdateTimes = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    fun attachTaskListener(task: TorrentDownloadTask) {
        task.addListener(object : TorrentTaskListener {
            override fun onProgress(task: TorrentDownloadTask, snapshot: TorrentProgressSnapshot) {
                val now = System.currentTimeMillis()
                val lastTime = lastUpdateTimes[task.id] ?: 0L
                val isTerminal = snapshot.state == TorrentTaskState.FINISHED ||
                        snapshot.state == TorrentTaskState.PAUSED ||
                        snapshot.state == TorrentTaskState.STOPPED ||
                        snapshot.state == TorrentTaskState.ERROR

                if (!isTerminal && now - lastTime < 250) {
                    AppContext.app.updateTorrentProgressWindow(task.id, snapshot)
                    return
                }
                lastUpdateTimes[task.id] = now

                val rec = AppContext.db.getById(task.id) ?: return
                if (!task.isCustomName && rec.fileName != snapshot.name && snapshot.name.isNotBlank() && !TorrentMetadataParser.isPlaceholderName(snapshot.name)) {
                    rec.fileName = snapshot.name
                }
                rec.downloaded = snapshot.downloadedBytes
                rec.size = snapshot.totalBytes
                rec.progress = snapshot.progressPercent
                rec.speed = snapshot.downloadRate.toFloat()
                rec.eta = snapshot.etaSeconds
                rec.status = when (snapshot.state) {
                    TorrentTaskState.FINISHED, TorrentTaskState.SEEDING -> RecordStatus.FINISHED
                    TorrentTaskState.PAUSED, TorrentTaskState.STOPPED -> RecordStatus.PAUSED
                    TorrentTaskState.ERROR -> RecordStatus.ERROR
                    else -> RecordStatus.DOWNLOADING
                }
                AppContext.db.update(rec)
                AppContext.app.updateDownloadInView(task.id)
                AppContext.app.updateTorrentProgressWindow(task.id, snapshot)
            }

            override fun onMetadataReceived(task: TorrentDownloadTask, metadata: TorrentMetadata) {
                val rec = AppContext.db.getById(task.id) ?: return
                var updated = false
                if (!task.isCustomName && rec.fileName != task.name && task.name.isNotBlank() && !TorrentMetadataParser.isPlaceholderName(task.name)) {
                    rec.fileName = task.name
                    updated = true
                }
                if (metadata.totalSize > 0 && rec.size != metadata.totalSize) {
                    rec.size = metadata.totalSize
                    updated = true
                }
                if (rec.categoryId == null) {
                    val cat = captureCategory(task.name, null, null)?.id
                    if (cat != null) {
                        rec.categoryId = cat
                        updated = true
                    }
                }
                if (updated) {
                    AppContext.db.update(rec)
                    AppContext.app.updateDownloadInView(task.id)
                }
                AppContext.app.updateTorrentProgressWindow(task.id, task.getSnapshot())
            }

            override fun onFinished(task: TorrentDownloadTask) {
                lastUpdateTimes.remove(task.id)
                val rec = AppContext.db.getById(task.id) ?: return
                val snap = task.getSnapshot()
                rec.progress = 100
                rec.status = RecordStatus.FINISHED
                rec.speed = 0f
                rec.eta = 0
                AppContext.db.update(rec)
                AppContext.app.updateDownloadInView(task.id)
                AppContext.app.hideProgressWindow(task.id)
                AppContext.app.showDownloadCompleteWindow(task.id, task.saveDir.absolutePath, task.name, snap.totalBytes)
            }

            override fun onError(task: TorrentDownloadTask, message: String, error: DownloadError?) {
                lastUpdateTimes.remove(task.id)
                val rec = AppContext.db.getById(task.id) ?: return
                rec.status = RecordStatus.ERROR
                rec.speed = 0f
                AppContext.db.update(rec)
                AppContext.app.updateDownloadInView(task.id)
                AppContext.app.showProgressError(task.id, error ?: DownloadError.InternalError)
            }
        })
    }

    fun syncSavedTorrents() {
        try {
            val tasks = AppContext.torrentManager.loadAllTasksFromDB()
            for (t in tasks) {
                attachTaskListener(t)
                val snap = t.getSnapshot()
                val existing = AppContext.db.getById(t.id)
                if (existing == null) {
                    val newRec = DbRecord(
                        id = t.id,
                        size = snap.totalBytes,
                        downloaded = snap.downloadedBytes,
                        progress = snap.progressPercent,
                        date = System.currentTimeMillis(),
                        fileName = t.name,
                        eta = snap.etaSeconds,
                        speed = 0f,
                        selected = false,
                        status = if (t.isCompleted) RecordStatus.FINISHED else RecordStatus.PAUSED,
                        downloadType = DownloadType.Torrent,
                        categoryId = captureCategory(t.name, null, null)?.id
                    )
                    AppContext.db.add(newRec)
                } else if (existing.fileName != t.name && !TorrentMetadataParser.isPlaceholderName(t.name)) {
                    existing.fileName = t.name
                    if (snap.totalBytes > 0 && existing.size != snap.totalBytes) {
                        existing.size = snap.totalBytes
                    }
                    AppContext.db.update(existing)
                }
            }
        } catch (e: Throwable) {
            Logger.error("Error syncing saved torrents", e)
        }
    }

    fun applyTorrentConfig() {
        try {
            val cfg = AppContext.config
            val sessionConfig = TorrentSessionConfig(
                downloadRateLimit = cfg.torrentDownloadLimit,
                uploadRateLimit = cfg.torrentUploadLimit,
                maxConnections = cfg.torrentMaxConnections,
                maxActiveDownloads = cfg.torrentMaxActiveDownloads,
                maxActiveSeeds = cfg.torrentMaxActiveSeeds,
                maxActiveTotal = cfg.torrentMaxActiveTotal,
                enableDht = cfg.torrentDhtEnabled,
                enablePex = cfg.torrentPexEnabled,
                enableLsd = cfg.torrentLsdEnabled,
                listenPort = cfg.torrentListenPort,
                seedingRule = SeedingRule(
                    mode = try {
                        SeedingMode.valueOf(cfg.torrentSeedingMode)
                    } catch (_: Throwable) {
                        SeedingMode.RATIO_OR_TIME
                    },
                    maxRatio = cfg.torrentSeedingRatioLimit,
                    maxTimeSeconds = cfg.torrentSeedingTimeLimitMinutes.toLong() * 60L
                ),
                enableUpnp = cfg.torrentEnableUpnp,
                enableNatPmp = cfg.torrentEnableNatPmp
            )
            AppContext.torrentManager.applyConfig(sessionConfig)
        } catch (e: Throwable) {
            Logger.error("Failed to apply torrent config", e)
        }
    }

    fun checkEngineAvailable(parent: Window? = null): Boolean {
        return try {
            TorrentNativeLoader.load()
            true
        } catch (e: Throwable) {
            Logger.error("BitTorrent native library unavailable", e)
            MessageBox.show(parent, "BitTorrent Unavailable", "BitTorrent engine could not be initialized: ${e.message}")
            false
        }
    }

    fun addMagnet(parent: Window?) {
        if (!checkEngineAvailable(parent)) return

        var defaultText = ""
        try {
            val clip = getClipBoardText()?.trim().orEmpty()
            if (clip.startsWith("magnet:?", ignoreCase = true)) {
                defaultText = clip
            }
        } catch (_: Throwable) {}

        val uri = JOptionPane.showInputDialog(
            parent,
            "Enter Magnet Link (magnet:?xt=urn:btih:...):",
            "Add Magnet Link",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            defaultText
        ) as? String ?: return

        val trimmed = uri.trim()
        if (!trimmed.startsWith("magnet:?", ignoreCase = true)) {
            MessageBox.show(parent, "Invalid Magnet Link", "Please enter a valid magnet URI starting with 'magnet:?'.")
            return
        }

        NewTorrentDownloadWindow(TorrentSource.MagnetUri(trimmed), null, parent).isVisible = true
    }

    fun addTorrentFile(parent: Window?) {
        if (!checkEngineAvailable(parent)) return

        val fc = JFileChooser().apply {
            dialogTitle = "Select .torrent File"
            fileFilter = FileNameExtensionFilter("BitTorrent Files (*.torrent)", "torrent")
        }

        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return
        val file = fc.selectedFile ?: return

        try {
            val metadata = TorrentMetadataParser.parse(file)
            NewTorrentDownloadWindow(TorrentSource.TorrentFile(file), metadata, parent).isVisible = true
        } catch (e: Throwable) {
            Logger.error("Failed to add torrent file", e)
            MessageBox.show(parent, "Torrent Error", "Failed to load torrent: ${e.message}")
        }
    }
}
