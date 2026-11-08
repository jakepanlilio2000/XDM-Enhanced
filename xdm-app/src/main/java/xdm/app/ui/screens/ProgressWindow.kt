package xdm.app.ui.screens

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.components.AppMenuHandler
import xdm.app.ui.components.CircularProgress
import xdm.app.ui.components.MessageBox
import xdm.app.ui.components.SegmentPanel
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.WEBSITE_URL
import xdm.app.utils.openFolderExternal
import xdm.app.utils.openInDefaultBrowser
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.scaledSize
import xdm.app.utils.windowIconImages
import xdm.core.downloaders.DownloadError
import xdm.core.downloaders.web.SegmentProgress
import xdm.core.util.FormatHelper.formatSize
import xdm.core.util.FormatHelper.toLongEta
import xdm.core.util.Logger
import xdm.core.util.MovePhase
import xdm.manager.BatchFileCounts
import xdm.manager.RecordStatus
import xdm.manager.getFileFolder
import xdm.torrent.TorrentProgressSnapshot
import xdm.torrent.TorrentTaskState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Taskbar
import java.awt.event.MouseAdapter
import java.io.File
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class ProgressWindow(val id: Long) : JFrame() {

    private companion object {
        val windowProgressTaskbar: Taskbar? by lazy {
            runCatching {
                if (!Taskbar.isTaskbarSupported()) return@runCatching null
                Taskbar.getTaskbar().takeIf { it.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW) }
            }.getOrNull()
        }
    }

    private var isError: Boolean = false
    private val textBuf = StringBuilder(100)

    val lblFileName = JLabel("---").apply {
        font = font.deriveFont(Font.BOLD, 13f.px)
    }

    val lblUrlOrSource = JLabel("---").apply {
        font = font.deriveFont(11f.px)
        foreground = Color.GRAY
    }

    val lblDestination = JLabel("---").apply {
        font = font.deriveFont(11f.px)
        foreground = Color.GRAY
    }

    val lblStat = JLabel(text("DWN_TITLE")).apply {
        font = font.deriveFont(11f.px)
        foreground = UIManager.getColor("Label.foreground")
    }

    val lblStat1 = JLabel("0%").apply {
        font = font.deriveFont(Font.BOLD, 11f.px)
    }

    val lblStat2 = JLabel("---").apply {
        font = font.deriveFont(11f.px)
    }

    val lblStat3 = JLabel("---").apply {
        font = font.deriveFont(11f.px)
    }

    val lblStat4 = JLabel("---").apply {
        font = font.deriveFont(11f.px)
    }

    val lblTorrentSwarm = JLabel("").apply {
        font = font.deriveFont(11f.px)
        foreground = Color(56, 139, 253)
        isVisible = false
    }

    val progressBar = JProgressBar(0, 100).apply {
        isStringPainted = true
        font = font.deriveFont(Font.BOLD, 10f.px)
        putClientProperty(FlatClientProperties.STYLE, "arc: 6")
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), 18.px)
    }

    val segPanel = SegmentPanel().apply {
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), 6.px)
        isVisible = false
    }

    // Keep CircularProgress instance for backward compatibility
    val prg = CircularProgress().apply {
        value = 0
        preferredSize = scaledSize(80, 80)
        minimumSize = scaledSize(80, 80)
    }

    private var helpUrl: String? = null
    private val lblHelp = JLabel(text("ERR_HELP_LINK")).apply {
        isVisible = false
        font = font.deriveFont(font.size2D - 1f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        foreground = UIManager.getColor("ProgressBar.foreground")
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                helpUrl?.let { openInDefaultBrowser(it) }
            }
        })
    }

    val btnPauseResume = JButton(text("MENU_PAUSE"), FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)).apply {
        addActionListener { togglePauseResume() }
    }

    val btnCancel = JButton("Cancel", FontIcon(RemixIcon.CLOSE_CIRCLE_LINE, 16.px)).apply {
        addActionListener { cancelDownload() }
    }

    val btnOpenFolder = JButton("Open Folder", FontIcon(RemixIcon.FOLDER_LINE, 16.px)).apply {
        addActionListener { openFolder() }
    }

    val btnTorrentDetails = JButton("Torrent Options...", FontIcon(RemixIcon.SETTINGS_4_LINE, 16.px)).apply {
        isVisible = false
        addActionListener { showTorrentDetails() }
    }

    val btnHide = JButton(text("DWN_HIDE")).apply {
        addActionListener { hideWindow() }
    }

    init {
        title = text("DWN_TITLE")
        iconImages = windowIconImages
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        initUI()
        loadInitialRecordInfo()

        rootPane.registerKeyboardAction(
            { hideWindow() },
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )

        addWindowListener(object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) {
                btnPauseResume.requestFocusInWindow()
            }
            override fun windowClosing(e: WindowEvent) {
                hideWindow()
            }
            override fun windowClosed(e: WindowEvent) {
                System.gc()
            }
        })
    }

    private fun initUI() {
        val root = JPanel(BorderLayout(0, 10.px)).apply {
            border = ScaledEmptyBorder(12, 16, 12, 16)
        }

        // Top info card
        val infoPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2.px, 4.px, 2.px, 4.px)
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        infoPanel.add(JLabel("File:").apply { font = font.deriveFont(Font.BOLD, 11f.px) }, gbc)
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0
        infoPanel.add(lblFileName, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        infoPanel.add(JLabel("Source:").apply { font = font.deriveFont(11f.px); foreground = Color.GRAY }, gbc)
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0
        infoPanel.add(lblUrlOrSource, gbc)

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
        infoPanel.add(JLabel("Save to:").apply { font = font.deriveFont(11f.px); foreground = Color.GRAY }, gbc)
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0
        infoPanel.add(lblDestination, gbc)

        root.add(infoPanel, BorderLayout.NORTH)

        // Center progress and details card
        val centerBox = Box.createVerticalBox()

        val progressBox = Box.createVerticalBox().apply {
            border = ScaledEmptyBorder(4, 0, 8, 0)
            add(progressBar)
            add(Box.createVerticalStrut(3.px))
            add(segPanel)
        }
        centerBox.add(progressBox)

        val statsGrid = JPanel(GridBagLayout()).apply {
            border = ScaledEmptyBorder(4, 4, 4, 4)
        }
        val sGbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2.px, 4.px, 2.px, 4.px)
        }

        sGbc.gridx = 0; sGbc.gridy = 0; sGbc.weightx = 0.5
        statsGrid.add(lblStat, sGbc)
        sGbc.gridx = 1; sGbc.gridy = 0; sGbc.weightx = 0.5
        statsGrid.add(lblStat2, sGbc)

        sGbc.gridx = 0; sGbc.gridy = 1; sGbc.weightx = 0.5
        statsGrid.add(lblStat3, sGbc)
        sGbc.gridx = 1; sGbc.gridy = 1; sGbc.weightx = 0.5
        statsGrid.add(lblStat4, sGbc)

        sGbc.gridx = 0; sGbc.gridy = 2; sGbc.weightx = 1.0; sGbc.gridwidth = 2
        statsGrid.add(lblTorrentSwarm, sGbc)

        sGbc.gridx = 0; sGbc.gridy = 3; sGbc.weightx = 1.0; sGbc.gridwidth = 2
        statsGrid.add(lblHelp, sGbc)

        centerBox.add(statsGrid)
        centerBox.add(Box.createVerticalGlue())

        root.add(centerBox, BorderLayout.CENTER)

        // Bottom control bar
        val bottomBar = JPanel(BorderLayout()).apply {
            border = ScaledEmptyBorder(8, 0, 0, 0)
            val leftActions = Box.createHorizontalBox().apply {
                add(btnOpenFolder)
                add(Box.createHorizontalStrut(6.px))
                add(btnTorrentDetails)
            }
            add(leftActions, BorderLayout.WEST)

            val rightActions = Box.createHorizontalBox().apply {
                add(btnPauseResume)
                add(Box.createHorizontalStrut(6.px))
                add(btnCancel)
                add(Box.createHorizontalStrut(6.px))
                add(btnHide)
            }
            add(rightActions, BorderLayout.EAST)
        }
        root.add(bottomBar, BorderLayout.SOUTH)

        contentPane.add(root)
        rootPane.defaultButton = btnPauseResume
        pack()
        size = scaledSize(480, 270)
        setLocationRelativeTo(null)
    }

    private fun loadInitialRecordInfo() {
        val rec = AppContext.db.getById(id) ?: return
        lblFileName.text = rec.fileName
        title = "[ ${rec.progress}% ] ${rec.fileName}"

        val defFolder = AppContext.config.defaultDownloadFolder
        val (file, folder) = getFileFolder(rec) ?: (rec.fileName to defFolder)
        lblDestination.text = folder ?: defFolder
        lblDestination.toolTipText = lblDestination.text

        if (rec.downloadType == xdm.core.downloaders.DownloadType.Torrent) {
            btnTorrentDetails.isVisible = true
            val task = AppContext.torrentManager.getTask(id)
            if (task != null) {
                updateTorrentProgress(task.getSnapshot())
            } else {
                lblUrlOrSource.text = "BitTorrent Transfer"
            }
        } else {
            val httpTask = AppContext.taskInfoDB.getHttpTask(id)
            if (httpTask != null) {
                lblUrlOrSource.text = httpTask.url
                lblUrlOrSource.toolTipText = httpTask.url
            } else {
                lblUrlOrSource.text = rec.fileName
                lblUrlOrSource.toolTipText = rec.fileName
            }
        }

        updatePauseResumeButton(rec.status)
    }

    private fun updatePauseResumeButton(status: RecordStatus) {
        if (status.isStopped) {
            btnPauseResume.text = text("MENU_RESUME")
            btnPauseResume.icon = FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)
        } else {
            btnPauseResume.text = text("MENU_PAUSE")
            btnPauseResume.icon = FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)
        }
    }

    private fun togglePauseResume() {
        if (isError) {
            hideWindow()
            return
        }
        val rec = AppContext.db.getById(id) ?: return
        if (rec.status == RecordStatus.DOWNLOADING || rec.status == RecordStatus.READY || rec.status == RecordStatus.ASSEMBLING) {
            AppMenuHandler.pauseDownload(rec)
            updatePauseResumeButton(RecordStatus.PAUSED)
        } else {
            AppMenuHandler.resumeDownload(rec)
            updatePauseResumeButton(RecordStatus.DOWNLOADING)
        }
    }

    private fun cancelDownload() {
        val rec = AppContext.db.getById(id) ?: return
        val confirmed = MessageBox.confirm(this, text("DEL_TITLE"), "Cancel and remove this download?")
        if (confirmed) {
            AppMenuHandler.deleteDownload(rec, this)
            hideWindow()
        }
    }

    private fun openFolder() {
        val rec = AppContext.db.getById(id) ?: return
        val (fileName, folder) = getFileFolder(rec) ?: (rec.fileName to lblDestination.text)
        val targetFolder = folder ?: lblDestination.text
        if (targetFolder.isNotBlank()) {
            try {
                val f = File(targetFolder, fileName.orEmpty())
                if (!fileName.isNullOrBlank() && f.exists()) {
                    openFolderExternal(fileName, targetFolder)
                } else if (File(targetFolder).exists()) {
                    openFolderExternal(null, targetFolder)
                }
            } catch (e: Throwable) {
                Logger.error("Failed to open destination folder", e)
            }
        }
    }

    private fun showTorrentDetails() {
        val task = AppContext.torrentManager.getTask(id) ?: return
        TorrentDetailsDialog(this, task).isVisible = true
    }

    private fun hideWindow() {
        AppContext.app.hideProgressWindow(id)
        dispose()
    }

    fun updateProgress(
        fileName: String?,
        downloaded: Long,
        size: Long,
        speed: Float,
        eta: Long,
        prg: Int,
        segData: Collection<SegmentProgress>,
        files: BatchFileCounts? = null,
    ) {
        lblStat.text = files?.let { batchFilesText(it) } ?: "Status: Downloading"
        fileName?.let {
            lblFileName.text = it
            title = "[ $prg% ] $it"
        }

        lblStat1.text = "$prg%"
        progressBar.value = prg
        progressBar.string = "$prg%"
        this.prg.value = prg

        textBuf.clear()
        if (downloaded > 0) {
            textBuf.append(formatSize(downloaded.toDouble()))
        } else {
            textBuf.append("---")
        }
        if (files == null || size <= 0 || downloaded <= size) {
            textBuf.append(" / ")
            if (size > 0) {
                textBuf.append(formatSize(size.toDouble()))
            } else {
                textBuf.append("---")
            }
        }
        lblStat2.text = textBuf.toString()

        lblStat3.text = if (speed > 0) "Speed: " + formatSize(speed.toDouble()) + "/s" else "Speed: ---"
        lblStat4.text = if (eta > 0) toLongEta(eta) + " left" else "ETA: ---"

        lblTorrentSwarm.isVisible = false
        if (segData.isNotEmpty()) {
            segPanel.isVisible = true
            segPanel.setValues(segData)
        } else {
            segPanel.isVisible = false
        }

        windowProgressTaskbar?.setWindowProgressValue(this, prg)
    }

    fun updateTorrentProgress(snapshot: TorrentProgressSnapshot) {
        lblFileName.text = snapshot.name
        title = "[ ${snapshot.progressPercent}% ] ${snapshot.name}"

        btnTorrentDetails.isVisible = true

        val sourceStr = if (snapshot.infoHash.isNotBlank()) "torrent://${snapshot.infoHash}" else "BitTorrent Transfer"
        lblUrlOrSource.text = sourceStr
        lblUrlOrSource.toolTipText = sourceStr

        val destFolder = AppContext.torrentManager.getTask(id)?.saveDir?.absolutePath ?: AppContext.config.defaultDownloadFolder
        lblDestination.text = destFolder
        lblDestination.toolTipText = destFolder

        val statusStr = when (snapshot.state) {
            TorrentTaskState.DOWNLOADING_METADATA -> "Downloading metadata..."
            TorrentTaskState.CHECKING_FILES -> "Checking files..."
            TorrentTaskState.DOWNLOADING -> "Downloading"
            TorrentTaskState.SEEDING -> "Seeding"
            TorrentTaskState.FINISHED -> "Finished"
            TorrentTaskState.PAUSED -> "Paused"
            TorrentTaskState.ERROR -> "Error"
            else -> snapshot.state.name
        }
        val privateTag = if (snapshot.isPrivate) "[Private] " else ""
        lblStat.text = "Status: $privateTag$statusStr"

        val downloadedStr = if (snapshot.downloadedBytes > 0) formatSize(snapshot.downloadedBytes.toDouble()) else "0 B"
        val totalStr = if (snapshot.totalBytes > 0) formatSize(snapshot.totalBytes.toDouble()) else "---"
        lblStat1.text = "${snapshot.progressPercent}%"
        lblStat2.text = "$downloadedStr / $totalStr"

        val downSpeedStr = if (snapshot.downloadRate > 0) formatSize(snapshot.downloadRate.toDouble()) + "/s" else "0 B/s"
        val upSpeedStr = if (snapshot.uploadRate > 0) formatSize(snapshot.uploadRate.toDouble()) + "/s" else "0 B/s"
        lblStat3.text = "Speed: ↓ $downSpeedStr | ↑ $upSpeedStr"
        lblStat4.text = if (snapshot.etaSeconds > 0) toLongEta(snapshot.etaSeconds) + " left" else "ETA: ---"

        val uploadedStr = formatSize(snapshot.totalUploadBytes.toDouble())
        val ratioStr = "%.2f".format(snapshot.shareRatio)
        val seedExtra = if (snapshot.state == TorrentTaskState.SEEDING) " | Seeded: ${snapshot.seedingDurationSeconds / 60}m" else ""
        val piecesPart = if (snapshot.totalPieces > 0) " | Pieces: ${snapshot.verifiedPieces} / ${snapshot.totalPieces} verified" else ""
        lblTorrentSwarm.isVisible = true
        lblTorrentSwarm.text = "Peers: ${snapshot.numPeers} | Seeds: ${snapshot.numSeeds}$piecesPart | ↑ $uploadedStr (Ratio: $ratioStr)$seedExtra"

        segPanel.isVisible = false
        progressBar.value = snapshot.progressPercent
        progressBar.string = "${snapshot.progressPercent}%"
        prg.value = snapshot.progressPercent

        if (snapshot.state == TorrentTaskState.PAUSED || snapshot.state == TorrentTaskState.STOPPED) {
            btnPauseResume.text = text("MENU_RESUME")
            btnPauseResume.icon = FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)
        } else {
            btnPauseResume.text = text("MENU_PAUSE")
            btnPauseResume.icon = FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)
        }

        windowProgressTaskbar?.setWindowProgressValue(this, snapshot.progressPercent)
    }

    fun showMoving(phase: MovePhase, prg: Int) {
        val status = moveText(phase, prg)
        lblStat.text = text("STAT_PUBLISHING")
        title = "[ $prg% ] $status"
        lblStat1.text = status
        lblStat3.text = ""
        lblStat4.text = ""
        btnPauseResume.isEnabled = phase != MovePhase.FINALIZING
        progressBar.value = prg
        progressBar.string = "$prg%"
        this.prg.value = prg
        windowProgressTaskbar?.setWindowProgressValue(this, prg)
    }

    fun showError(error: DownloadError) {
        this.isError = true
        lblStat4.text = ""
        lblStat.text = text("MSG_FAILED")
        val message = downloadErrorText(error)
        val escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        lblStat3.text = "<html><body style='width:220px'>$escaped</body></html>"
        lblStat4.isVisible = false
        helpUrl = downloadErrorHelpUrl(error)
        lblHelp.isVisible = helpUrl != null
        lblStat3.toolTipText = message
        btnPauseResume.text = text("LBL_CLOSE")
        btnPauseResume.icon = FontIcon(RemixIcon.CLOSE_CIRCLE_LINE, 16.px)
        btnPauseResume.isEnabled = true
        btnHide.isVisible = false
        validate()
        val needed = preferredSize.height
        if (needed > height) setSize(width, needed)
    }

    private fun batchFilesText(files: BatchFileCounts): String {
        val counts = text("BATCH_FILES_PROGRESS").format("%,d".format(files.done), "%,d".format(files.total))
        return if (files.failed > 0) "$counts · ${text("BATCH_FILES_FAILED").format("%,d".format(files.failed))}" else counts
    }
}

fun downloadErrorText(error: DownloadError): String {
    return when (error) {
        DownloadError.InvalidResponse -> text("ERR_INVALID_RESP")
        DownloadError.NetworkError -> text("ERR_CONN_FAILED")
        DownloadError.DiskSpaceError -> text("ERR_DISK_FAILED")
        DownloadError.ResumeNotSupported -> text("ERR_NO_RESUME")
        DownloadError.SessionExpired -> text("ERR_SESSION_FAILED")
        DownloadError.LinkExpired -> text("ERR_LINK_EXPIRED")
        DownloadError.TlsError -> text("ERR_TLS")
        DownloadError.DecryptionError -> text("ERR_DECRYPT")
        DownloadError.OutputWriteError -> text("ERR_OUTPUT_WRITE")
        DownloadError.MuxError -> text("ERR_MUX")
        else -> text("ERR_INTERNAL")
    }
}

fun downloadErrorHelpUrl(error: DownloadError): String? {
    val slug = when (error) {
        DownloadError.NetworkError -> "network-error"
        DownloadError.InvalidResponse -> "invalid-response"
        DownloadError.SessionExpired -> "session-expired"
        DownloadError.LinkExpired -> "link-expired"
        DownloadError.DiskSpaceError -> "disk-error"
        DownloadError.InternalError -> "internal-error"
        DownloadError.ResumeNotSupported -> "resume-not-supported"
        DownloadError.MuxError -> "video-merge-failed"
        DownloadError.TlsError -> "tls-error"
        DownloadError.DecryptionError -> "decryption-error"
        DownloadError.OutputWriteError -> "cannot-save-file"
        DownloadError.Cancelled -> return null
    }
    return "$WEBSITE_URL/help/errors/$slug/"
}
