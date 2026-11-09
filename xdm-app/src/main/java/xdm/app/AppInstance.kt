package xdm.app

import xdm.app.AppContext.db
import xdm.app.I8N.text
import xdm.app.ui.components.ChecksumPanel
import xdm.app.ui.components.MessageBox
import xdm.app.ui.screens.AboutDialog
import xdm.app.ui.screens.AppWindow
import xdm.app.ui.screens.BatchDownloadDialog
import xdm.app.ui.screens.BrowserSetupDialog
import xdm.app.ui.screens.DownloadCompleteWindow
import xdm.app.ui.screens.NewDownloadWindow
import xdm.app.ui.screens.NewVideoDownloadWindow
import xdm.app.ui.screens.ProgressWindow
import xdm.app.ui.screens.PropertiesDialog
import xdm.app.ui.screens.RefreshLinkWindow
import xdm.app.ui.screens.ScheduleWindow
import xdm.app.ui.screens.TorrentUiBridge
import xdm.app.ui.screens.NewTorrentDownloadWindow
import xdm.torrent.TorrentMetadataParser
import xdm.torrent.TorrentSource
import xdm.core.util.FileUtils
import xdm.core.util.CoreUtils
import java.io.File
import xdm.app.utils.SingleWindows
import xdm.app.utils.TextContextMenu
import xdm.app.utils.WorkingSetTrim
import xdm.app.utils.createTray
import xdm.app.utils.detectOS
import xdm.app.utils.logoImage
import xdm.app.utils.mac.MacNotifications
import xdm.app.utils.mainWindowOnScreen
import xdm.app.utils.offerMacNotificationSettings
import xdm.app.utils.openFolderExternal
import xdm.app.utils.Browser
import xdm.app.utils.showTrayNotification
import xdm.app.utils.windowIconImages
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.web.SegmentProgress
import xdm.core.network.http.HeaderMap
import xdm.core.util.FormatHelper.formatSize
import xdm.core.util.Logger
import xdm.core.util.MovePhase
import xdm.manager.BatchFileCounts
import xdm.manager.DbRecord
import xdm.manager.LinkValidationResult
import java.awt.Desktop
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.Taskbar
import java.awt.desktop.AppReopenedListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

interface IAppInstance {
    fun run(args: Array<String>)

    fun showAppWindow()

    /**
     * Reports a failure that stops XDM from starting at all. Blocks until the user dismisses it,
     * because the caller exits right afterwards.
     */
    fun showFatalError(message: String)

    fun showDownloadCompleteWindow(id: Long, folder: String, fileName: String, fileSize: Long)

    /**
     * Posts a quiet "<file> download finished" system notification instead of the full dialog. A click
     * opens [folder], as the dialog's Open folder does.
     */
    fun showDownloadCompleteNotification(folder: String, fileName: String)

    fun updateDownloadInView(id: Long)

    fun deleteDownloadInView(index: Int)

    /** Refreshes the list once after several downloads were removed together. */
    fun downloadsRemovedInView()

    fun addDownloadInView(id: Long)

    fun addDownload(downloadInfo: HttpDownloadTaskInfo?)

    fun addVideoDownload(vid: Long, fileName: String, fileSize: Long?, fileType: String?)

    fun showProgressWindow(id: Long, fileName: String)

    fun updateTorrentProgressWindow(id: Long, snapshot: xdm.torrent.TorrentProgressSnapshot)

    fun hideProgressWindow(id: Long)

    fun downloadPaused(id: Long)

    fun showProgressError(id: Long, error: DownloadError)

    /** Drives the progress window through the publish phase, where download events have stopped. */
    fun updateMoveProgress(id: Long, phase: MovePhase, prg: Int)

    /**
     * Advisory: the volume a starting download writes to looks too small. [isTempFolder] says whether that
     * is the temp folder (HLS/DASH segments) or the download's own folder (HTTP).
     */
    fun showSpaceWarning(folder: String, needed: Long, free: Long, isTempFolder: Boolean)

    fun updateProgressWindow(
        id: Long,
        fileName: String?,
        downloaded: Long,
        size: Long,
        speed: Float,
        eta: Long,
        prg: Int,
        segData: Collection<SegmentProgress>,
        /** A batch's file counts; null for other downloads. */
        files: BatchFileCounts? = null,
    )

    fun showRefreshWindow(id: Long)

    fun showSchedulerWindow(id: Long)

    fun showPropertiesWindow(ent: DbRecord)

    /** Opens the batch dialog for links sent by the browser's "Download all". */
    fun showBatchDialog(request: BatchRequest)

    /** The XDM extension in [browser] is older than this XDM expects: asks the user to update it. */
    fun showExtensionUpdate(browser: Browser)

    /** Handles command-line arguments (file paths, URLs, magnet URIs, flags) passed at startup or handed over. */
    fun handleLaunchArgs(args: List<String>)

    /** Handles an incoming URI (magnet, http/https, xdm-app) or local file path (.torrent). */
    fun handleIncomingUriOrFile(raw: String)
}

open class AppInstance : IAppInstance {
    private lateinit var appWindow: AppWindow
    private val prgWndMap = mutableMapOf<Long, ProgressWindow>()
    /**
     * Browser downloads started straight away that have not yet received a byte. The extension has
     * already cancelled the browser's copy, so if one of these fails there is no fallback - and with
     * no progress window open, nothing else would tell the user the file landed nowhere.
     */
    private val pendingHandoffs = ConcurrentHashMap.newKeySet<Long>()
    private var refreshLinkWindow: RefreshLinkWindow? = null

    /** EDT only. Whether this run already told the user that macOS has XDM's notifications off. */
    private var notificationsOffShown = false

    override fun run(args: Array<String>) {
        val minimized = args.contains(MINIMIZED_FLAG)
        installMacHandlers()
        runOnUIThread {
            TextContextMenu.install()
            val image = logoImage(256)
            // Closing the last window (the main one to the tray included): XDM's working set can go back to Windows.
            WorkingSetTrim.install()
            appWindow = AppWindow(image, startHidden = minimized)
            val hasTray = createTray(image)
            // Hidden even without a tray icon (GNOME without the AppIndicator extension, the Wayland
            // toolkit): the browser extension still works, and launching XDM again hands over to this
            // instance, which shows the window - the same state as closing the window.
            if (minimized) {
                Logger.info(if (hasTray) "Starting minimized to the system tray" else "Starting hidden (no system tray)")
                WorkingSetTrim.request("started hidden")
            } else {
                showAppWindow()
                if (AppContext.firstRun) {
                    BrowserSetupDialog(appWindow).isVisible = true
                }
            }
            handleLaunchArgs(args.toList())
        }
    }

    /**
     * On macOS a second launch does not start a second process: LaunchServices activates the
     * running app and delivers `xdm-app://...` as an Apple event, so the argv/`/show` handover the
     * other platforms use never happens.
     */
    private fun installMacHandlers() {
        if (detectOS() != OS.MacOS || !Desktop.isDesktopSupported()) {
            return
        }
        val desktop = Desktop.getDesktop()
        // Unsupported when not running from a bundle (i.e. in development), hence runCatching.
        runCatching {
            desktop.setOpenURIHandler { event ->
                handleIncomingUriOrFile(event.uri.toString())
            }
        }
        runCatching {
            desktop.setOpenFileHandler { event ->
                event.files?.forEach { file ->
                    handleIncomingUriOrFile(file.absolutePath)
                }
            }
        }
        runCatching { desktop.addAppEventListener(AppReopenedListener { showAppWindow() }) }
        // App menu → "About XDM Enhanced": XDM's own dialog instead of the generic one.
        // Called on the EDT; owned by the main window only while it is on screen.
        runCatching {
            desktop.setAboutHandler { AboutDialog.open(if (::appWindow.isInitialized) appWindow else null) }
        }
        installDockMenu()
    }

    /** Right-click on the Dock icon: Restore Window. macOS adds its own Quit below it. */
    private fun installDockMenu() {
        if (!Taskbar.isTaskbarSupported() || !Taskbar.getTaskbar().isSupported(Taskbar.Feature.MENU)) return
        runCatching {
            Taskbar.getTaskbar().menu = PopupMenu().apply {
                add(MenuItem(text("MSG_RESTORE")).apply { addActionListener { showAppWindow() } })
            }
        }.onFailure { Logger.error("Could not set the Dock menu", it) }
    }

    override fun showFatalError(message: String) {
        val show = Runnable {
            // Truly ownerless, so it has a taskbar button on Windows: with no main window yet, nothing else shows.
            JOptionPane(message, JOptionPane.ERROR_MESSAGE).createDialog("XDM Enhanced").apply {
                runCatching { iconImages = windowIconImages }
                isVisible = true
                dispose()
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            show.run()
        } else {
            // invokeAndWait, not invokeLater: the caller exits the process on return.
            runCatching { SwingUtilities.invokeAndWait(show) }
                .onFailure { Logger.error("Could not show the startup error dialog", it) }
        }
    }

    override fun showAppWindow() {
        runOnUIThread {
            appWindow.isVisible = true
            appWindow.toFront()
        }
    }

    override fun showDownloadCompleteWindow(id: Long, folder: String, fileName: String, fileSize: Long) {
        runOnUIThread {
            // Checksums only make sense for a file fetched from one URL as-is.
            val source = if (AppContext.db.getById(id)?.downloadType == DownloadType.Http) ChecksumPanel.sourceOf(id) else null
            val sourceUrl = AppContext.taskInfoDB.getHttpTask(id)?.url ?: AppContext.taskInfoDB.getTorrentTask(id)?.sourceUri ?: AppContext.db.getById(id)?.fileName
            DownloadCompleteWindow().apply { setDetails(fileName, folder, fileSize, source, sourceUrl) }.isVisible = true
        }
    }

    override fun showDownloadCompleteNotification(folder: String, fileName: String) {
        runOnUIThread {
            showTrayNotification(
                text("CD_TITLE"), fileName,
                onClick = { runCatching { openFolderExternal(fileName, folder) }.onFailure { Logger.error(it) } },
            ) {
                // macOS has XDM's notifications off: say so once per run rather than stay silent.
                if (MacNotifications.isAvailable && !notificationsOffShown) {
                    notificationsOffShown = true
                    offerMacNotificationSettings(appWindow)
                }
            }
        }
    }

    // Rows are resolved by id on the EDT: an index taken on the calling thread can point at a
    // different row once a delete runs before the EDT gets to it.
    override fun updateDownloadInView(id: Long) {
        runOnUIThread {
            db.indexById(id)?.let { appWindow.updateDownloadInView(it) }
            db.getById(id)?.let { rec -> prgWndMap[id]?.onRecordUpdated(rec) }
        }
    }

    override fun deleteDownloadInView(index: Int) {
        if (SwingUtilities.isEventDispatchThread()) {
            appWindow.deleteDownloadInView(index)
        } else {
            SwingUtilities.invokeAndWait { appWindow.deleteDownloadInView(index) }
        }
    }

    override fun downloadsRemovedInView() {
        runOnUIThread { appWindow.downloadsRemovedInView() }
    }

    override fun addDownloadInView(id: Long) {
        runOnUIThread { db.indexById(id)?.let { appWindow.addDownloadInView(it) } }
    }

    override fun addDownload(downloadInfo: HttpDownloadTaskInfo?) {
        if (downloadInfo != null && AppContext.refreshLinkInProgress.compareAndSet(true, false)) {
            val refreshId = AppContext.refreshLinkId.get()
            handleRefreshedLink(refreshId, downloadInfo.url, downloadInfo.headers, downloadInfo.cookie)
            return
        }
        if (downloadInfo != null && AppContext.config.startDownloadAutomatically) {
            pendingHandoffs.add(downloadInfo.id)
            AppContext.downloader.startHttpDownload(downloadInfo)
        } else {
            SwingUtilities.invokeLater {
                showNewDownloadWindowInternal(downloadInfo)
            }
        }
    }

    override fun addVideoDownload(vid: Long, fileName: String, fileSize: Long?, contentType: String?) {
        AppContext.videoTracker.getHttpVideo(vid)?.let { source ->
            if (AppContext.refreshLinkInProgress.compareAndSet(true, false)) {
                val refreshId = AppContext.refreshLinkId.get()
                handleRefreshedLink(refreshId, source.url, source.headers, source.cookie)
                return
            }
        }

        if (AppContext.config.startDownloadAutomatically) {
            val name = FileUtils.sanitizeFileName(fileName) ?: "video"
            val tracker = AppContext.videoTracker
            val http = tracker.getHttpVideo(vid)
            val hls = tracker.getHlsVideo(vid)
            val dash = tracker.getDashVideo(vid)
            val subtitle = tracker.getSubtitle(vid)
            val url = http?.url ?: subtitle?.task?.url ?: hls?.url ?: dash?.url
            val origin = http?.origin ?: subtitle?.task?.origin ?: hls?.origin ?: dash?.origin
            val (categoryId, targetFolder) = DesktopCaptureHost.destination(name, url, origin)
            val segments = AppContext.config.maxSegments
            val convertToSrt = AppContext.config.saveSubtitlesAsSrt
            AppContext.downloader.addVideoDownload(
                vid, name, categoryId, targetFolder, segments, null, convertToSrt
            )
        } else {
            SwingUtilities.invokeLater {
                showNewVideoDownloadWindowInternal(vid, fileName, fileSize, contentType)
            }
        }
    }

    private fun handleRefreshedLink(refreshId: Long, url: String, headers: HeaderMap?, cookie: String?) {
        val result = AppContext.downloader.validateReplacement(refreshId, url, headers, cookie)
        when (result) {
            is LinkValidationResult.Compatible -> {
                AppContext.downloader.updateDownloadLink(refreshId, result.finalUrl, result.headers, result.cookie)
                runOnUIThread { refreshLinkWindow?.showRefreshed() }
            }

            is LinkValidationResult.Incompatible -> {
                runOnUIThread {
                    val window = refreshLinkWindow ?: appWindow
                    when (result.reason) {
                        LinkValidationResult.IncompatibleReason.SIZE_MISMATCH,
                        LinkValidationResult.IncompatibleReason.NO_RANGE_SUPPORT,
                        LinkValidationResult.IncompatibleReason.CONTENT_CHANGED -> {
                            val options = arrayOf("Restart from Beginning", "Cancel")
                            val choice = JOptionPane.showOptionDialog(
                                window,
                                "The captured link cannot safely resume this download:\n${result.details}\n\nStarting over will clear previously downloaded data.\nWould you like to restart the download from the beginning with this new link?",
                                "Refresh Download Link",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.WARNING_MESSAGE,
                                null,
                                options,
                                options[1]
                            )
                            if (choice == 0) {
                                AppContext.downloader.restartDownloadWithNewLink(refreshId, url, headers, cookie)
                                refreshLinkWindow?.showRefreshed()
                            } else {
                                AppContext.refreshLinkInProgress.set(true)
                            }
                        }

                        else -> {
                            MessageBox.show(
                                window,
                                "Refresh Download Link",
                                "Unable to verify captured link:\n${result.details}",
                                JOptionPane.ERROR_MESSAGE
                            )
                            AppContext.refreshLinkInProgress.set(true)
                        }
                    }
                }
            }
        }
    }

    private fun showNewDownloadWindowInternal(downloadInfo: HttpDownloadTaskInfo?) {
        val dlg = NewDownloadWindow()
        dlg.showWindow(downloadInfo)
    }

    private fun showNewVideoDownloadWindowInternal(
        vid: Long, fileName: String, fileSize: Long?, contentType: String?
    ) {
        val dlg = NewVideoDownloadWindow()
        dlg.showWindow(vid, fileName, fileSize, contentType)
    }

    override fun showExtensionUpdate(browser: Browser) {
        runOnUIThread {
            if (::appWindow.isInitialized) {
                appWindow.showExtensionUpdate(browser)
            }
        }
    }

    private inline fun runOnUIThread(crossinline action: () -> Unit) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { action() }
        } else {
            action()
        }
    }

    override fun showProgressWindow(id: Long, fileName: String) {
        // Always deferred, even on the EDT: a download started from a dialog's action would otherwise
        // show its window before that dialog closes, and on Windows closing it then activates the main
        // window over the new progress window (and a modal dialog would block the window meanwhile).
        SwingUtilities.invokeLater {
            var prgWnd = prgWndMap[id]
            if (prgWnd != null) {
                prgWnd.isVisible = true
            } else {
                prgWnd = ProgressWindow(id).apply {
                    if (lblFileName.text == "---" && fileName.isNotBlank()) {
                        lblFileName.text = fileName
                        title = "[ 0% ] $fileName"
                    }
                    isVisible = true
                }
                prgWndMap[id] = prgWnd
            }
            prgWnd.toFront()
        }
    }

    override fun downloadPaused(id: Long) {
        runOnUIThread {
            prgWndMap[id]?.showPaused()
        }
    }

    override fun updateProgressWindow(
        id: Long,
        fileName: String?,
        downloaded: Long,
        size: Long,
        speed: Float,
        eta: Long,
        prg: Int,
        segData: Collection<SegmentProgress>,
        files: BatchFileCounts?,
    ) {
        if (downloaded > 0) pendingHandoffs.remove(id)
        runOnUIThread { prgWndMap[id]?.updateProgress(fileName, downloaded, size, speed, eta, prg, segData, files) }
    }

    override fun updateTorrentProgressWindow(id: Long, snapshot: xdm.torrent.TorrentProgressSnapshot) {
        if (snapshot.downloadedBytes > 0) pendingHandoffs.remove(id)
        runOnUIThread { prgWndMap[id]?.updateTorrentProgress(snapshot) }
    }

    override fun updateMoveProgress(id: Long, phase: MovePhase, prg: Int) {
        runOnUIThread { prgWndMap[id]?.showMoving(phase, prg) }
    }

    override fun showSpaceWarning(folder: String, needed: Long, free: Long, isTempFolder: Boolean) {
        runOnUIThread {
            MessageBox.show(
                appWindow,
                text("TITLE_TEMP_SPACE_LOW"),
                text(if (isTempFolder) "MSG_TEMP_SPACE_LOW" else "MSG_TARGET_SPACE_LOW")
                    .replace("%folder%", folder)
                    .replace("%needed%", formatSize(needed.toDouble()))
                    .replace("%free%", formatSize(free.toDouble())),
            )
        }
    }

    override fun hideProgressWindow(id: Long) {
        pendingHandoffs.remove(id)
        runOnUIThread {
            val wnd = prgWndMap.remove(id)
            wnd?.isVisible = false
            wnd?.dispose()
        }
    }

    override fun showProgressError(id: Long, error: DownloadError) {
        val handoff = pendingHandoffs.remove(id)
        runOnUIThread {
            val wnd = prgWndMap.remove(id)
            if (wnd != null) {
                wnd.showError(error)
            } else if (handoff) {
                showHandoffFailed(id)
            }
        }
    }

    private fun showHandoffFailed(id: Long) {
        val fileName = db.getById(id)?.fileName ?: return
        val message = text("MSG_BROWSER_DOWNLOAD_FAILED").format(fileName)
        showTrayNotification(text("TITLE_BROWSER_DOWNLOAD_FAILED"), message) {
            MessageBox.show(appWindow, text("TITLE_BROWSER_DOWNLOAD_FAILED"), message)
        }
    }

    override fun showRefreshWindow(id: Long) {
        runOnUIThread { showRefreshWindowInternal(id) }
    }

    private fun showRefreshWindowInternal(id: Long) {
        // Refreshing rewrites an HTTP task's link; there is nothing to refresh without one.
        if (AppContext.taskInfoDB.getHttpTask(id) == null) {
            MessageBox.show(appWindow, "XDM", text("ERR_NO_REFRESH"))
            return
        }
        // Null when the download carries no origin or Referer: the dialog then offers only the
        // manual link entry, since there is no page to send the browser back to.
        val page = AppContext.downloader.getRefererPage(id)
        // A finished refresh stays on screen with its success line until closed; one at a time.
        refreshLinkWindow?.dispose()
        AppContext.refreshLinkId.set(id)
        AppContext.refreshLinkInProgress.set(true)
        refreshLinkWindow = RefreshLinkWindow(appWindow, id, page).apply {
            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    // WINDOW_CLOSED arrives later: a replaced window must not end the refresh that replaced it.
                    if (refreshLinkWindow !== e.window) return
                    AppContext.refreshLinkInProgress.set(false)
                    AppContext.refreshLinkId.set(-1)
                    refreshLinkWindow = null
                    System.gc()
                }
            })
        }
        refreshLinkWindow?.isVisible = true
    }

    override fun showSchedulerWindow(id: Long) {
        runOnUIThread { SingleWindows.show("schedule" to id) { ScheduleWindow(appWindow, id, modal = false) } }
    }

    override fun showPropertiesWindow(ent: DbRecord) {
        runOnUIThread { SingleWindows.show("properties" to ent.id) { PropertiesDialog(appWindow, ent) } }
    }

    override fun showBatchDialog(request: BatchRequest) {
        runOnUIThread {
            BatchDownloadDialog(mainWindowOnScreen(), request).isVisible = true
        }
    }

    override fun handleLaunchArgs(args: List<String>) {
        if (args.isEmpty()) return
        for (arg in args) {
            val trimmed = arg.trim().removeSurrounding("\"")
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("-")) {
                // Command line flag (--minimized, -m, etc.), not a download target
                continue
            }
            if (trimmed.equals("xdm-app://launch", ignoreCase = true)) {
                showAppWindow()
                continue
            }
            handleIncomingUriOrFile(trimmed)
        }
    }

    override fun handleIncomingUriOrFile(raw: String) {
        val input = raw.trim().removeSurrounding("\"")
        if (input.isEmpty()) return

        runOnUIThread {
            showAppWindow()

            // 1. Magnet URI
            if (input.startsWith("magnet:", ignoreCase = true)) {
                handleMagnetUri(input)
                return@runOnUIThread
            }

            // 2. Local Torrent File or file:// URI
            val candidateFile = if (input.startsWith("file:", ignoreCase = true)) {
                runCatching { File(java.net.URI(input)) }.getOrElse {
                    File(input.removePrefix("file://").removePrefix("file:"))
                }
            } else {
                File(input)
            }

            if (candidateFile.name.endsWith(".torrent", ignoreCase = true) ||
                (candidateFile.exists() && candidateFile.isFile && isTorrentFile(candidateFile))
            ) {
                handleTorrentFile(candidateFile)
                return@runOnUIThread
            }

            // 3. HTTP / HTTPS URL
            if (input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)) {
                handleHttpUrl(input)
                return@runOnUIThread
            }

            // 4. Custom xdm-app:// protocol
            if (input.startsWith("xdm-app://", ignoreCase = true)) {
                val sub = input.removePrefix("xdm-app://").removePrefix("xdm-app:")
                if (sub.isNotBlank() && !sub.equals("launch", ignoreCase = true)) {
                    handleIncomingUriOrFile(sub)
                }
                return@runOnUIThread
            }

            // 5. Invalid / Unrecognized URI or file path
            Logger.info("Invalid or unrecognized download target: $input")
            MessageBox.show(
                if (::appWindow.isInitialized) appWindow else null,
                "Invalid Download Target",
                "The specified link or file could not be opened:\n$input"
            )
        }
    }

    private fun isTorrentFile(file: File): Boolean = runCatching {
        if (!file.exists() || !file.isFile || file.length() < 10) false
        else {
            val buf = ByteArray(1)
            file.inputStream().use { it.read(buf) }
            buf[0] == 'd'.code.toByte()
        }
    }.getOrDefault(false)

    private fun handleMagnetUri(uri: String) {
        val parent = if (::appWindow.isInitialized) appWindow else null
        val infoHash = TorrentMetadataParser.parseMagnetInfoHash(uri)
        if (infoHash == null) {
            Logger.info("Invalid magnet URI: $uri")
            MessageBox.show(
                parent,
                "Invalid Magnet Link",
                "The provided magnet URI is invalid or missing a valid torrent info hash:\n$uri"
            )
            return
        }

        // Duplicate check
        val existing = AppContext.torrentManager.getTaskByInfoHash(infoHash)
        if (existing != null) {
            Logger.info("Torrent already in list: id=${existing.id}, name=${existing.name}")
            MessageBox.show(
                parent,
                "Torrent Already Exists",
                "The torrent '${existing.name}' is already in your download list."
            )
            if (::appWindow.isInitialized) {
                appWindow.selectDownloadById(existing.id)
            }
            return
        }

        if (!TorrentUiBridge.checkEngineAvailable(parent)) return
        NewTorrentDownloadWindow(TorrentSource.MagnetUri(uri), null, parent).isVisible = true
    }

    private fun handleTorrentFile(file: File) {
        val parent = if (::appWindow.isInitialized) appWindow else null
        if (!file.exists() || !file.isFile) {
            Logger.info("Torrent file not found: ${file.absolutePath}")
            MessageBox.show(
                parent,
                "File Not Found",
                "The specified torrent file does not exist:\n${file.absolutePath}"
            )
            return
        }

        val metadata = try {
            TorrentMetadataParser.parse(file)
        } catch (e: Throwable) {
            Logger.error("Failed to parse torrent file: ${file.absolutePath}", e)
            MessageBox.show(
                parent,
                "Invalid Torrent File",
                "The file is corrupted or not a valid BitTorrent file:\n${e.message}"
            )
            return
        }

        // Duplicate check
        val existing = AppContext.torrentManager.getTaskByInfoHash(metadata.infoHash)
        if (existing != null) {
            Logger.info("Torrent already in list: id=${existing.id}, name=${existing.name}")
            MessageBox.show(
                parent,
                "Torrent Already Exists",
                "The torrent '${existing.name}' is already in your download list."
            )
            if (::appWindow.isInitialized) {
                appWindow.selectDownloadById(existing.id)
            }
            return
        }

        if (!TorrentUiBridge.checkEngineAvailable(parent)) return
        NewTorrentDownloadWindow(TorrentSource.TorrentFile(file), metadata, parent).isVisible = true
    }

    private fun handleHttpUrl(url: String) {
        val parent = if (::appWindow.isInitialized) appWindow else null
        try {
            java.net.URI(url)
        } catch (e: Exception) {
            MessageBox.show(parent, "Invalid URL", "The specified URL is invalid:\n$url")
            return
        }

        val fileName = FileUtils.sanitizeFileName(FileUtils.getFileName(url)) ?: "download"
        val (catId, targetFolder) = DesktopCaptureHost.destination(fileName, url, null)
        val task = HttpDownloadTaskInfo(
            id = CoreUtils.uniqueId(),
            url = url,
            fileName = fileName,
            respectFileName = false,
            cookie = null,
            headers = null,
            origin = null,
            categoryId = catId,
            targetFolder = targetFolder,
            maxPiece = AppContext.config.maxSegments,
            authInfo = null,
            knownFileSize = null,
        )
        addDownload(task)
    }
}
