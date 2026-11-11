package xdm.app.preflight

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import xdm.app.AppContext
import xdm.app.ui.screens.NewDownloadWindow
import xdm.app.utils.getClipBoardText
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.util.CoreUtils
import xdm.core.util.Logger
import java.awt.Toolkit
import java.awt.datatransfer.FlavorEvent
import java.awt.datatransfer.FlavorListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

object ClipboardMonitor {
    private val isRunningState = AtomicBoolean(false)
    private val isPausedState = AtomicBoolean(false)
    private var scheduler: ScheduledExecutorService? = null
    private var flavorListener: FlavorListener? = null

    @Volatile
    var lastProcessedText: String? = null

    @Volatile
    var lastProcessedUrl: String? = null

    private val activeModalUrls = ConcurrentHashMap.newKeySet<String>()

    val activeModalUrl: String?
        get() = activeModalUrls.firstOrNull()

    val isRunning: Boolean get() = isRunningState.get()

    var isPaused: Boolean
        get() = isPausedState.get()
        set(value) {
            isPausedState.set(value)
        }

    fun pause() {
        isPausedState.set(true)
    }

    fun resume() {
        isPausedState.set(false)
    }

    @Synchronized
    fun start() {
        if (!AppContext.hasConfig || !AppContext.config.monitorClipboard) {
            return
        }
        if (isRunningState.compareAndSet(false, true)) {
            // Seed current clipboard content so existing text is not treated as a new copy event
            try {
                lastProcessedText = getClipBoardText()
            } catch (_: Exception) {
            }

            try {
                val clip = Toolkit.getDefaultToolkit().systemClipboard
                val listener = FlavorListener { _: FlavorEvent ->
                    checkClipboardNow()
                }
                clip.addFlavorListener(listener)
                flavorListener = listener
            } catch (e: Exception) {
                Logger.info("PREFLIGHT", "AWT clipboard FlavorListener unavailable, using polling: ${e.message}")
            }

            scheduler = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "XDM-ClipboardMonitor").apply { isDaemon = true }
            }.apply {
                scheduleWithFixedDelay({ checkClipboardNow() }, 1000, 1000, TimeUnit.MILLISECONDS)
            }

            Logger.info("PREFLIGHT", "Clipboard monitor started")
        }
    }

    @Synchronized
    fun stop() {
        if (isRunningState.compareAndSet(true, false)) {
            try {
                flavorListener?.let {
                    Toolkit.getDefaultToolkit().systemClipboard.removeFlavorListener(it)
                }
            } catch (_: Exception) {
            }
            flavorListener = null

            try {
                scheduler?.shutdownNow()
            } catch (_: Exception) {
            }
            scheduler = null

            Logger.info("PREFLIGHT", "Clipboard monitor stopped")
        }
    }

    @Synchronized
    fun checkClipboardNow() {
        if (!isRunningState.get() || isPausedState.get()) return
        if (!AppContext.hasConfig || !AppContext.config.monitorClipboard) return

        val text = try {
            getClipBoardText()
        } catch (_: Exception) {
            null
        } ?: return

        if (text.isBlank() || text == lastProcessedText) {
            return
        }

        val trimmed = text.trim()

        // Strict Privacy: if not an eligible single HTTP/HTTPS link, do not record or log it
        if (!isEligibleHttpUrl(trimmed)) {
            lastProcessedText = text // Remember so we do not re-check this same text
            return
        }

        // Deduplication: Avoid prompt loops for the same URL or currently active modal
        if (trimmed == lastProcessedUrl || activeModalUrls.contains(trimmed) || NewDownloadWindow.isModalOpen(trimmed)) {
            lastProcessedText = text
            return
        }

        lastProcessedText = text
        lastProcessedUrl = trimmed

        processEligibleUrl(trimmed)
    }

    fun isEligibleHttpUrl(text: String): Boolean {
        if (text.length > 2048) return false
        // Exclude multiline text, tabs, and spaces
        if (text.contains('\n') || text.contains('\r') || text.contains('\t') || text.contains(' ')) {
            return false
        }
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false
        }
        return text.toHttpUrlOrNull() != null
    }

    private fun processEligibleUrl(url: String) {
        val hasDlExt = LinkPreflightService.hasDownloadableExtension(url)
        val isPluginHandled = runCatching {
            xdm.app.plugins.PluginManager.findDownloadProvider(url) != null ||
            xdm.app.plugins.PluginManager.findUrlRecognizer(url) != null
        }.getOrDefault(false)

        LinkPreflightService.preflight(url) { result ->
            if (!isRunningState.get() || isPausedState.get()) return@preflight
            if (hasDlExt || result.isDownloadable || isPluginHandled) {
                SwingUtilities.invokeLater {
                    if (!isRunningState.get() || isPausedState.get()) return@invokeLater
                    openDownloadModal(result)
                }
            }
        }
    }

    fun openDownloadModal(result: PreflightResult) {
        if (activeModalUrls.contains(result.originalUrl) || activeModalUrls.contains(result.finalUrl)
            || NewDownloadWindow.isModalOpen(result.originalUrl) || NewDownloadWindow.isModalOpen(result.finalUrl)) {
            return
        }
        activeModalUrls.add(result.originalUrl)
        activeModalUrls.add(result.finalUrl)

        val maxSegments = if (AppContext.hasConfig) AppContext.config.maxSegments else 8
        val (catId, targetFolder) = if (AppContext.hasConfig) {
            xdm.app.DesktopCaptureHost.destination(result.fileName, result.originalUrl, null)
        } else {
            Pair(null, System.getProperty("user.home"))
        }
        val task = HttpDownloadTaskInfo(
            id = CoreUtils.uniqueId(),
            url = result.originalUrl,
            fileName = result.fileName,
            respectFileName = false,
            cookie = null,
            headers = null,
            origin = null,
            categoryId = catId,
            targetFolder = targetFolder,
            maxPiece = maxSegments,
            authInfo = null,
            knownFileSize = result.fileSize,
            etag = result.etag
        )

        val dlg = NewDownloadWindow()
        dlg.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                activeModalUrls.remove(result.originalUrl)
                activeModalUrls.remove(result.finalUrl)
            }
        })
        dlg.showWindow(task)
    }

    fun resetForTests() {
        lastProcessedText = null
        lastProcessedUrl = null
        activeModalUrls.clear()
    }
}

