package xdm.app.power

import xdm.app.AppContext
import xdm.app.utils.WorkingSetTrim
import xdm.core.util.Logger
import xdm.torrent.TorrentTaskState
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

object CompletionCoordinator {
    private val hasProcessedDownloads = AtomicBoolean(false)
    private val isArmed = AtomicBoolean(true)
    private val failedCount = AtomicInteger(0)
    var headlessOrTesting: Boolean = false

    fun markDownloadStarted() {
        hasProcessedDownloads.set(true)
        isArmed.set(true)
    }

    fun markDownloadFinished() {
        hasProcessedDownloads.set(true)
    }

    fun markDownloadFailed() {
        hasProcessedDownloads.set(true)
        failedCount.incrementAndGet()
    }

    fun rearm() {
        isArmed.set(true)
    }

    fun reset() {
        hasProcessedDownloads.set(false)
        isArmed.set(true)
        failedCount.set(0)
    }

    internal fun isCurrentlyArmed(): Boolean = isArmed.get()
    internal fun hasProcessed(): Boolean = hasProcessedDownloads.get()
    internal fun currentFailedCount(): Int = failedCount.get()

    fun onAllDownloadsDone(parentWindow: Window? = null, countdownSeconds: Int = 30) {
        WorkingSetTrim.request("all downloads finished")

        if (!hasProcessedDownloads.get()) {
            Logger.info("Completion action skipped: no downloads processed in this session")
            return
        }

        val action = AppContext.config.completionAction
        if (action == CompletionAction.DO_NOTHING) {
            return
        }

        if (AppContext.hasDownloader && AppContext.downloader.hasPendingDownloads()) {
            Logger.info("Completion action postponed: downloader has pending downloads")
            return
        }

        val hasTorrents = runCatching {
            AppContext.torrentManager.getAllTasks().any {
                it.state == TorrentTaskState.DOWNLOADING ||
                it.state == TorrentTaskState.DOWNLOADING_METADATA ||
                it.state == TorrentTaskState.CHECKING_FILES
            }
        }.getOrDefault(false)

        if (hasTorrents) {
            Logger.info("Completion action postponed: active torrent tasks still downloading")
            return
        }

        if (!isArmed.compareAndSet(true, false)) {
            Logger.info("Completion action already triggered or disarmed")
            return
        }

        val fails = failedCount.getAndSet(0)
        hasProcessedDownloads.set(false)

        if (headlessOrTesting || GraphicsEnvironment.isHeadless()) {
            Logger.info("Headless or test mode: executing power action $action directly")
            PowerManager.perform(action)
            return
        }

        SwingUtilities.invokeLater {
            try {
                val dialog = CompletionCountdownDialog(
                    parent = parentWindow,
                    action = action,
                    failedCount = fails,
                    initialSeconds = countdownSeconds,
                    onPerform = { chosenAction ->
                        PowerManager.perform(chosenAction)
                    },
                    onCancel = {
                        Logger.info("Completion action cancelled by user; disarmed until next download")
                    }
                )
                dialog.isVisible = true
            } catch (e: Exception) {
                Logger.error("Failed to display completion countdown dialog", e)
                PowerManager.perform(action)
            }
        }
    }
}

