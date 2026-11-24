package xdm.app

import xdm.app.power.CompletionCoordinator
import xdm.app.utils.KeepAwake
import xdm.app.utils.SoundPlayer
import xdm.app.utils.WorkingSetTrim
import xdm.core.downloaders.AssemblyNote
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.TaskInfoDB
import xdm.core.downloaders.web.SegmentProgress
import xdm.core.network.http.CertTrust
import xdm.core.network.http.HttpAuth
import xdm.core.util.Logger
import xdm.core.util.MovePhase
import xdm.manager.BatchFileCounts
import xdm.manager.DownloadSource
import xdm.manager.EngineConfig
import xdm.manager.EngineHost
import xdm.manager.PlatformHooks
import xdm.manager.UiSink
import java.io.File

/** The download lifecycle on the desktop: the app's config, the Swing UI and the OS layers. */
object DesktopEngineHost : EngineHost {
    override val config: EngineConfig
        get() = AppContext.config

    override val taskInfoDB: TaskInfoDB
        get() = AppContext.taskInfoDB

    override val ui: UiSink = DesktopUiSink

    override val hooks: PlatformHooks = DesktopPlatformHooks
}

/** Forwards to [IAppInstance], which marshals to the EDT itself. */
object DesktopUiSink : UiSink {
    private val app get() = AppContext.app

    internal fun resolveDownloadInfo(
        id: Long,
        fallbackName: String = "",
        fallbackSize: Long = 0L,
        fallbackDownloaded: Long = 0L,
        fallbackProgress: Int = 0,
        fallbackSpeed: Float = 0f,
        fallbackStatus: String = "DOWNLOADING"
    ): xdm.plugin.api.models.DownloadInfo {
        if (AppContext.hasDb && id > 0) {
            val rec = runCatching { AppContext.db.getById(id) }.getOrNull()
            if (rec != null) {
                return xdm.app.plugins.PluginManager.toDownloadInfo(rec)
            }
        }
        return xdm.plugin.api.models.DownloadInfo(
            id,
            "",
            fallbackName,
            fallbackName,
            fallbackSize,
            fallbackDownloaded,
            fallbackProgress,
            fallbackSpeed,
            fallbackStatus,
            "None",
            System.currentTimeMillis()
        )
    }

    override fun downloadAdded(id: Long) {
        app.addDownloadInView(id)
        val info = resolveDownloadInfo(id, fallbackStatus = "QUEUED")
        xdm.app.plugins.PluginEventBus.publish(
            xdm.plugin.api.events.EventTopics.DOWNLOAD_QUEUED,
            xdm.plugin.api.events.TaskLifecycleEvent(xdm.plugin.api.events.TaskLifecycleEvent.Type.QUEUED, info)
        )
    }

    override fun downloadUpdated(id: Long) = app.updateDownloadInView(id)

    override fun downloadRemoved(id: Long, position: Int) {
        val info = resolveDownloadInfo(id, fallbackStatus = "CANCELED")
        app.deleteDownloadInView(position)
        app.hideProgressWindow(id)
        val event = xdm.plugin.api.events.TaskLifecycleEvent(xdm.plugin.api.events.TaskLifecycleEvent.Type.CANCELED, info)
        xdm.app.plugins.PluginEventBus.publish(xdm.plugin.api.events.EventTopics.DOWNLOAD_CANCELED, event)
        xdm.app.plugins.PluginEventBus.publish(xdm.plugin.api.events.EventTopics.DOWNLOAD_DELETED, event)
    }

    override fun downloadsRemoved() = app.downloadsRemovedInView()

    override fun showProgress(id: Long, fileName: String) {
        app.showProgressWindow(id, fileName)
        val info = resolveDownloadInfo(id, fallbackName = fileName, fallbackStatus = "STARTED")
        xdm.app.plugins.PluginEventBus.publish(
            xdm.plugin.api.events.EventTopics.DOWNLOAD_STARTED,
            xdm.plugin.api.events.TaskLifecycleEvent(xdm.plugin.api.events.TaskLifecycleEvent.Type.STARTED, info)
        )
    }

    override fun hideProgress(id: Long) = app.hideProgressWindow(id)

    override fun downloadPaused(id: Long) {
        app.downloadPaused(id)
        val info = resolveDownloadInfo(id, fallbackStatus = "PAUSED")
        xdm.app.plugins.PluginEventBus.publish(
            xdm.plugin.api.events.EventTopics.DOWNLOAD_PAUSED,
            xdm.plugin.api.events.TaskLifecycleEvent(xdm.plugin.api.events.TaskLifecycleEvent.Type.PAUSED, info)
        )
    }

    override fun progress(
        id: Long, fileName: String?, downloaded: Long, size: Long, speed: Float, eta: Long, progress: Int,
        segments: Collection<SegmentProgress>, files: BatchFileCounts?,
    ) {
        app.updateProgressWindow(id, fileName, downloaded, size, speed, eta, progress, segments, files)
        val info = resolveDownloadInfo(
            id,
            fallbackName = fileName ?: "",
            fallbackSize = size,
            fallbackDownloaded = downloaded,
            fallbackProgress = progress,
            fallbackSpeed = speed,
            fallbackStatus = "DOWNLOADING"
        )
        xdm.app.plugins.PluginEventBus.publish(
            xdm.plugin.api.events.EventTopics.DOWNLOAD_PROGRESS,
            xdm.plugin.api.events.TaskLifecycleEvent(xdm.plugin.api.events.TaskLifecycleEvent.Type.PROGRESSED, info)
        )
    }

    override fun moveProgress(id: Long, phase: MovePhase, progress: Int) = app.updateMoveProgress(id, phase, progress)

    override fun downloadFailed(id: Long, error: DownloadError) {
        app.showProgressError(id, error)
        val info = resolveDownloadInfo(id, fallbackStatus = "FAILED")
        xdm.app.plugins.PluginEventBus.publish(
            xdm.plugin.api.events.EventTopics.DOWNLOAD_FAILED,
            xdm.plugin.api.events.TaskLifecycleEvent(xdm.plugin.api.events.TaskLifecycleEvent.Type.FAILED, info, error.name)
        )
    }

    override fun spaceWarning(folder: String, needed: Long, free: Long, isTempFolder: Boolean) =
        app.showSpaceWarning(folder, needed, free, isTempFolder)

    override fun downloadCompleteDialog(id: Long, folder: String, fileName: String, fileSize: Long) =
        app.showDownloadCompleteWindow(id, folder, fileName, fileSize)

    override fun downloadCompleteDialog(id: Long, folder: String, fileName: String, fileSize: Long, note: AssemblyNote?, noteDetail: String?) =
        app.showDownloadCompleteWindow(id, folder, fileName, fileSize, note?.let { noteText(it, noteDetail) })

    /** The one line the download-complete window shows for a download saved another way than the usual remux. */
    private fun noteText(note: AssemblyNote, detail: String?): String = when (note) {
        AssemblyNote.TS_FALLBACK -> I8N.text("CD_NOTE_TS")
        AssemblyNote.AS_IS -> I8N.text("CD_NOTE_AS_IS")
        AssemblyNote.PLAYLIST_FOLDER -> I8N.text("CD_NOTE_PLAYLIST")
        AssemblyNote.SEPARATE_FILES -> I8N.text("CD_NOTE_SEPARATE").format(detail.orEmpty())
    }

    override fun downloadCompleteNotification(folder: String, fileName: String) =
        app.showDownloadCompleteNotification(folder, fileName)
}

object DesktopPlatformHooks : PlatformHooks {
    override val httpAuth: HttpAuth
        get() = AppContext.httpAuth

    /** No checks at all while the user has "Ignore certificate errors" on. */
    override fun insecureTrust(): CertTrust? = InsecureTrust.ifEnabled(AppContext.config.ignoreCertErrors)

    override fun resolveProxy(id: Long, url: String?): xdm.core.network.ProxyConfig? {
        if (url == null) return null
        val uri = runCatching { java.net.URI.create(url) }.getOrNull() ?: return null
        val res = xdm.app.plugins.PluginManager.resolveProxy(uri, xdm.plugin.api.network.ProxyResolution.NetworkEngine.HTTP) ?: return null
        if (res.isBlocked) {
            return xdm.core.network.ProxyConfig(
                proxy = null,
                failClosed = true,
                isBlocked = true,
                blockedReason = res.blockedReason ?: "Proxy routing blocked by policy"
            )
        }
        return xdm.core.network.ProxyConfig(
            proxy = res.toJavaProxy(),
            user = res.username ?: "",
            pass = res.password ?: "",
            failClosed = res.fallbackPolicy == xdm.plugin.api.network.ProxyResolution.FallbackPolicy.FAIL_CLOSED,
            isBlocked = false
        )
    }

    override fun checkPrerequisites(id: Long, url: String?): Boolean {
        return xdm.app.plugins.VpnPolicyManager.isVpnPrerequisiteMet()
    }

    override fun keepAwake(on: Boolean) = if (on) KeepAwake.acquire() else KeepAwake.release()

    override fun usableSpace(folder: String): Long? = File(folder).usableSpace

    override fun markDownloadedFile(file: File, source: DownloadSource): Boolean =
        AppContext.platform.markDownloadedFile(file, source)

    override fun downloadFinished(filePath: String, id: Long) {
        CompletionCoordinator.markDownloadFinished()
        SoundPlayer.playIfEnabled(SoundPlayer.Sound.COMPLETE)
        if (AppContext.config.runVirusScan) {
            AppContext.platform.runVirusScan(filePath)
        }
        if (AppContext.config.runCommand) {
            AppContext.platform.runCustomCommand(filePath)
        }
        xdm.app.plugins.PluginManager.notifyDownloadCompleted(filePath, id)
    }

    override fun downloadFinished(filePath: String) {
        downloadFinished(filePath, -1L)
    }

    override fun downloadFailed(id: Long) {
        CompletionCoordinator.markDownloadFailed()
        SoundPlayer.playIfEnabled(SoundPlayer.Sound.FAILED)
        val info = DesktopUiSink.resolveDownloadInfo(id, fallbackStatus = "FAILED")
        xdm.app.plugins.PluginEventBus.publish(
            xdm.plugin.api.events.EventTopics.DOWNLOAD_FAILED,
            xdm.plugin.api.events.TaskLifecycleEvent(xdm.plugin.api.events.TaskLifecycleEvent.Type.FAILED, info)
        )
    }

    override fun allDownloadsDone() {
        CompletionCoordinator.onAllDownloadsDone()
    }

    override fun downloadDeleted(id: Long) {
        if (AppContext.hasScheduler && AppContext.scheduler.contains(id)) {
            AppContext.scheduler.removeEntry(id)
        }
    }
}
