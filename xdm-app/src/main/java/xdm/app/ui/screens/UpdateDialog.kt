package xdm.app.ui.screens

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.ui.components.AppTheme
import xdm.app.update.ChecksumResult
import xdm.app.update.GithubAsset
import xdm.app.update.ReleaseInfo
import xdm.app.update.UpdateChannel
import xdm.app.update.UpdateCheckResult
import xdm.app.update.UpdateChecker
import xdm.app.update.UpdateChecksumVerifier
import xdm.app.update.UpdateDownloader
import xdm.app.update.UpdateInstaller
import xdm.app.update.UpdateProgress
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.SingleWindows
import xdm.app.utils.logoIcon
import xdm.app.utils.openInDefaultBrowser
import xdm.app.utils.px
import xdm.app.utils.shownWindow
import xdm.app.utils.windowIconImages
import xdm.core.util.FormatHelper.formatSize
import xdm.core.util.Logger
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.*

/**
 * Dedicated Swing/FlatLaf update dialog for XDM Enhanced.
 * Supports checking, release note display, progress tracking, checksum verification, and safe installer launch.
 */
class UpdateDialog internal constructor(
    owner: Window?,
    startCheckImmediately: Boolean = false,
    preloadedRelease: ReleaseInfo? = null
) : JDialog(owner) {

    internal val cardLayout = CardLayout()
    internal val mainContainer = JPanel(cardLayout)

    private var currentReleaseInfo: ReleaseInfo? = preloadedRelease
    private var activeDownloader: UpdateDownloader? = null
    private var downloadedInstallerFile: File? = null

    // UI Components for CHECKING
    private val lblCheckingStatus = JLabel("Checking for updates...")
    private val checkingProgress = JProgressBar().apply { isIndeterminate = true }

    // UI Components for UP_TO_DATE
    private val lblUpToDateHeader = JLabel("You're up to date!")
    private val lblUpToDateDetails = JLabel()

    // UI Components for UPDATE_AVAILABLE
    private val lblNewVersionTitle = JLabel()
    private val lblVersionComparison = JLabel()
    private val lblChannelBadge = JLabel()
    private val lblAssetInfo = JLabel()
    private val txtReleaseNotes = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = UIManager.getFont("Label.font")?.deriveFont(12.5f.px)
    }

    // UI Components for DOWNLOADING
    private val lblDownloadingHeader = JLabel("Downloading update...")
    private val downloadProgressBar = JProgressBar(0, 100).apply {
        isStringPainted = true
    }
    private val lblDownloadStats = JLabel("Preparing download...")

    // UI Components for READY_TO_INSTALL
    private val lblReadyHeader = JLabel("Update ready to install")
    private val lblReadyDetails = JLabel()
    private val lblChecksumDetails = JLabel()

    // UI Components for ERROR
    private val lblErrorHeader = JLabel("Update check failed")
    private val lblErrorMessage = JLabel()

    init {
        title = "Software Update"
        runCatching { iconImages = windowIconImages }
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isModal = false
        isResizable = true
        minimumSize = Dimension(540.px, 420.px)
        preferredSize = Dimension(580.px, 460.px)

        val root = JPanel(BorderLayout()).apply {
            border = ScaledEmptyBorder(16, 20, 16, 20)
        }

        // Header Panel (Logo + App Name + Subtitle)
        val headerPanel = JPanel(BorderLayout(14.px, 0)).apply {
            border = ScaledEmptyBorder(0, 0, 14, 0)
            isOpaque = false
        }
        val logoImg = try { logoIcon(46.px) } catch (_: Throwable) { null }
        val lblLogo = JLabel(logoImg ?: FontIcon(RemixIcon.DOWNLOAD_2_LINE, 40.px, AppTheme.accent))
        headerPanel.add(lblLogo, BorderLayout.WEST)

        val headerTextPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val lblAppTitle = JLabel("XDM Enhanced Software Update").apply {
            font = font.deriveFont(Font.BOLD, 16f.px)
        }
        val currentVer = UpdateChecker.loadConfig()?.currentVersion ?: UpdateChecker.DEFAULT_VERSION
        val currentChannel = try { UpdateChannel.fromId(AppContext.config.updateChannel) } catch (_: Throwable) { UpdateChannel.BETA }
        val lblSubtitle = JLabel("Current version: v$currentVer (${currentChannel.displayName})").apply {
            font = font.deriveFont(Font.PLAIN, 12f.px)
            foreground = UIManager.getColor("Label.disabledForeground")
        }
        headerTextPanel.add(lblAppTitle)
        headerTextPanel.add(Box.createVerticalStrut(2.px))
        headerTextPanel.add(lblSubtitle)
        headerPanel.add(headerTextPanel, BorderLayout.CENTER)
        root.add(headerPanel, BorderLayout.NORTH)

        // Add State Cards
        mainContainer.add(createCheckingCard(), CARD_CHECKING)
        mainContainer.add(createUpToDateCard(), CARD_UP_TO_DATE)
        mainContainer.add(createUpdateAvailableCard(), CARD_AVAILABLE)
        mainContainer.add(createDownloadingCard(), CARD_DOWNLOADING)
        mainContainer.add(createReadyToInstallCard(), CARD_READY)
        mainContainer.add(createErrorCard(), CARD_ERROR)

        root.add(mainContainer, BorderLayout.CENTER)
        contentPane = root

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                activeDownloader?.cancel()
            }
        })

        rootPane.registerKeyboardAction(
            {
                activeDownloader?.cancel()
                dispose()
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )

        pack()
        setLocationRelativeTo(owner)

        when {
            preloadedRelease != null -> showUpdateAvailable(preloadedRelease, currentVer)
            startCheckImmediately -> startUpdateCheck()
            else -> showCard(CARD_CHECKING)
        }
    }

    internal fun showCard(name: String) {
        cardLayout.show(mainContainer, name)
    }

    // ==========================================
    // 1. CHECKING CARD
    // ==========================================
    private fun createCheckingCard(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = GridBagConstraints.RELATIVE
            insets = Insets(8.px, 0, 8.px, 0)
            anchor = GridBagConstraints.CENTER
        }

        lblCheckingStatus.font = lblCheckingStatus.font.deriveFont(Font.BOLD, 14f.px)
        checkingProgress.preferredSize = Dimension(280.px, 8.px)

        val btnCancel = JButton("Cancel").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { dispose() }
        }

        panel.add(lblCheckingStatus, gbc)
        panel.add(checkingProgress, gbc)
        panel.add(Box.createVerticalStrut(12.px), gbc)
        panel.add(btnCancel, gbc)
        return panel
    }

    // ==========================================
    // 2. UP TO DATE CARD
    // ==========================================
    private fun createUpToDateCard(): JPanel {
        val panel = JPanel(BorderLayout(0, 16.px))
        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(24, 0, 0, 0)
        }

        val iconLabel = JLabel(FontIcon(RemixIcon.CHECKBOX_CIRCLE_FILL, 44.px, Color(16, 185, 129))).apply {
            alignmentX = Component.CENTER_ALIGNMENT
        }
        lblUpToDateHeader.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.BOLD, 16f.px)
        }
        lblUpToDateDetails.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.PLAIN, 13f.px)
            foreground = UIManager.getColor("Label.disabledForeground")
        }

        center.add(iconLabel)
        center.add(Box.createVerticalStrut(10.px))
        center.add(lblUpToDateHeader)
        center.add(Box.createVerticalStrut(6.px))
        center.add(lblUpToDateDetails)
        panel.add(center, BorderLayout.CENTER)

        val bottom = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 0))
        val btnReleases = JButton("View Releases").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { openInDefaultBrowser(UpdateChecker.DEFAULT_DOWNLOAD_URL) }
        }
        val btnCheckAgain = JButton("Check Again").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { startUpdateCheck() }
        }
        val btnClose = JButton("Close").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { dispose() }
        }
        bottom.add(btnReleases)
        bottom.add(btnCheckAgain)
        bottom.add(btnClose)
        panel.add(bottom, BorderLayout.SOUTH)
        return panel
    }

    // ==========================================
    // 3. UPDATE AVAILABLE CARD
    // ==========================================
    private fun createUpdateAvailableCard(): JPanel {
        val panel = JPanel(BorderLayout(0, 12.px))

        val infoBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        lblNewVersionTitle.font = lblNewVersionTitle.font.deriveFont(Font.BOLD, 15f.px)
        val metaRow = Box.createHorizontalBox().apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(lblVersionComparison)
            add(Box.createHorizontalStrut(10.px))
            add(lblChannelBadge)
        }
        lblAssetInfo.font = lblAssetInfo.font.deriveFont(Font.PLAIN, 12f.px)
        lblAssetInfo.foreground = UIManager.getColor("Label.disabledForeground")

        infoBox.add(lblNewVersionTitle)
        infoBox.add(Box.createVerticalStrut(4.px))
        infoBox.add(metaRow)
        infoBox.add(Box.createVerticalStrut(4.px))
        infoBox.add(lblAssetInfo)
        panel.add(infoBox, BorderLayout.NORTH)

        // Release notes scroll area
        val notesWrapper = JPanel(BorderLayout(0, 4.px))
        val lblNotesHeader = JLabel("Release Notes:").apply {
            font = font.deriveFont(Font.BOLD, 12.5f.px)
        }
        notesWrapper.add(lblNotesHeader, BorderLayout.NORTH)
        val scrollPane = JScrollPane(txtReleaseNotes).apply {
            border = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") ?: Color.LIGHT_GRAY)
        }
        notesWrapper.add(scrollPane, BorderLayout.CENTER)
        panel.add(notesWrapper, BorderLayout.CENTER)

        // Actions
        val bottom = JPanel(BorderLayout())
        val leftActions = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        val btnViewGitHub = JButton("View on GitHub").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener {
                val url = currentReleaseInfo?.htmlUrl ?: UpdateChecker.DEFAULT_DOWNLOAD_URL
                openInDefaultBrowser(url)
            }
        }
        leftActions.add(btnViewGitHub)
        bottom.add(leftActions, BorderLayout.WEST)

        val rightActions = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 0))
        val btnRemindLater = JButton("Remind Me Later").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { dispose() }
        }
        val btnDownload = JButton("Download & Install").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            background = AppTheme.accent
            foreground = Color.WHITE
            addActionListener { startDownload() }
        }
        rightActions.add(btnRemindLater)
        rightActions.add(btnDownload)
        bottom.add(rightActions, BorderLayout.EAST)
        panel.add(bottom, BorderLayout.SOUTH)
        return panel
    }

    // ==========================================
    // 4. DOWNLOADING CARD
    // ==========================================
    private fun createDownloadingCard(): JPanel {
        val panel = JPanel(BorderLayout(0, 16.px))
        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(40, 20, 0, 20)
        }

        lblDownloadingHeader.apply {
            alignmentX = Component.LEFT_ALIGNMENT
            font = font.deriveFont(Font.BOLD, 15f.px)
        }
        downloadProgressBar.apply {
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(Int.MAX_VALUE, 12.px)
            maximumSize = Dimension(Int.MAX_VALUE, 12.px)
        }
        lblDownloadStats.apply {
            alignmentX = Component.LEFT_ALIGNMENT
            font = font.deriveFont(Font.PLAIN, 12.5f.px)
            foreground = UIManager.getColor("Label.disabledForeground")
        }

        center.add(lblDownloadingHeader)
        center.add(Box.createVerticalStrut(14.px))
        center.add(downloadProgressBar)
        center.add(Box.createVerticalStrut(8.px))
        center.add(lblDownloadStats)
        panel.add(center, BorderLayout.CENTER)

        val bottom = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 0))
        val btnCancelDownload = JButton("Cancel").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener {
                activeDownloader?.cancel()
                showCard(CARD_AVAILABLE)
            }
        }
        bottom.add(btnCancelDownload)
        panel.add(bottom, BorderLayout.SOUTH)
        return panel
    }

    // ==========================================
    // 5. READY TO INSTALL CARD
    // ==========================================
    private fun createReadyToInstallCard(): JPanel {
        val panel = JPanel(BorderLayout(0, 16.px))
        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(20, 10, 0, 10)
        }

        val iconLabel = JLabel(FontIcon(RemixIcon.SHIELD_CHECK_FILL, 44.px, Color(16, 185, 129))).apply {
            alignmentX = Component.CENTER_ALIGNMENT
        }
        lblReadyHeader.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.BOLD, 16f.px)
        }
        lblChecksumDetails.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.PLAIN, 12f.px)
            foreground = Color(16, 185, 129)
        }
        lblReadyDetails.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.PLAIN, 13f.px)
            text = "<html><center>The update package has been downloaded and verified.<br>Click <b>Install & Restart</b> to close XDM Enhanced and run the installer.<br>Your downloads and settings will remain preserved.</center></html>"
        }

        center.add(iconLabel)
        center.add(Box.createVerticalStrut(10.px))
        center.add(lblReadyHeader)
        center.add(Box.createVerticalStrut(6.px))
        center.add(lblChecksumDetails)
        center.add(Box.createVerticalStrut(10.px))
        center.add(lblReadyDetails)
        panel.add(center, BorderLayout.CENTER)

        val bottom = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 0))
        val btnLater = JButton("Install Later").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { dispose() }
        }
        val btnInstall = JButton("Install & Restart").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            background = AppTheme.accent
            foreground = Color.WHITE
            addActionListener {
                downloadedInstallerFile?.let { file ->
                    UpdateInstaller.launchInstallerAndExit(file)
                }
            }
        }
        bottom.add(btnLater)
        bottom.add(btnInstall)
        panel.add(bottom, BorderLayout.SOUTH)
        return panel
    }

    // ==========================================
    // 6. ERROR CARD
    // ==========================================
    private fun createErrorCard(): JPanel {
        val panel = JPanel(BorderLayout(0, 16.px))
        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(24, 10, 0, 10)
        }

        val iconLabel = JLabel(FontIcon(RemixIcon.ERROR_WARNING_FILL, 44.px, Color(239, 68, 68))).apply {
            alignmentX = Component.CENTER_ALIGNMENT
        }
        lblErrorHeader.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.BOLD, 16f.px)
        }
        lblErrorMessage.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.PLAIN, 13f.px)
            foreground = UIManager.getColor("Label.disabledForeground")
        }

        center.add(iconLabel)
        center.add(Box.createVerticalStrut(10.px))
        center.add(lblErrorHeader)
        center.add(Box.createVerticalStrut(8.px))
        center.add(lblErrorMessage)
        panel.add(center, BorderLayout.CENTER)

        val bottom = JPanel(FlowLayout(FlowLayout.RIGHT, 10.px, 0))
        val btnViewRepo = JButton("Open Releases Page").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { openInDefaultBrowser(UpdateChecker.DEFAULT_DOWNLOAD_URL) }
        }
        val btnRetry = JButton("Retry").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { startUpdateCheck() }
        }
        val btnClose = JButton("Close").apply {
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            addActionListener { dispose() }
        }
        bottom.add(btnViewRepo)
        bottom.add(btnRetry)
        bottom.add(btnClose)
        panel.add(bottom, BorderLayout.SOUTH)
        return panel
    }

    // ==========================================
    // BUSINESS LOGIC
    // ==========================================

    private fun startUpdateCheck() {
        showCard(CARD_CHECKING)
        lblCheckingStatus.text = "Checking for updates..."

        val channel = try {
            UpdateChannel.fromId(AppContext.config.updateChannel)
        } catch (_: Throwable) {
            UpdateChannel.BETA
        }

        UpdateChecker.checkForUpdates(channel) { result ->
            SwingUtilities.invokeLater {
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        currentReleaseInfo = result.releaseInfo
                        showUpdateAvailable(result.releaseInfo, result.currentVersion)
                    }
                    is UpdateCheckResult.UpToDate -> {
                        lblUpToDateHeader.text = "You're up to date!"
                        lblUpToDateDetails.text = "XDM Enhanced v${result.currentVersion} is currently the newest version available on the ${result.channel.displayName} channel."
                        showCard(CARD_UP_TO_DATE)
                    }
                    is UpdateCheckResult.Error -> {
                        lblErrorHeader.text = if (result.isRateLimit) "GitHub Rate Limit Exceeded" else "Update Check Failed"
                        lblErrorMessage.text = "<html><center>${result.message}</center></html>"
                        showCard(CARD_ERROR)
                    }
                }
            }
        }
    }

    private fun showUpdateAvailable(info: ReleaseInfo, currentVer: String) {
        lblNewVersionTitle.text = "A new version of XDM Enhanced is available: v${info.version}"
        lblVersionComparison.text = "v$currentVer  →  v${info.version}"

        val channel = if (info.isPreRelease) "Beta / Pre-release" else "Stable"
        lblChannelBadge.text = channel
        lblChannelBadge.foreground = if (info.isPreRelease) Color(245, 158, 11) else Color(16, 185, 129)
        lblChannelBadge.font = lblChannelBadge.font.deriveFont(Font.BOLD, 11.5f.px)

        val asset = info.installerAsset
        if (asset != null) {
            val sizeStr = if (asset.size > 0) formatSize(asset.size.toDouble()) else "Unknown size"
            lblAssetInfo.text = "Package: ${asset.name} ($sizeStr)"
        } else {
            lblAssetInfo.text = "No direct installer package detected for your OS. Manual download available on GitHub."
        }

        txtReleaseNotes.text = if (info.releaseNotes.isNotBlank()) info.releaseNotes else "No release notes provided for this version."
        txtReleaseNotes.caretPosition = 0

        showCard(CARD_AVAILABLE)
    }

    private fun startDownload() {
        val info = currentReleaseInfo ?: return
        val asset = info.installerAsset

        if (asset == null || asset.browser_download_url.isBlank()) {
            openInDefaultBrowser(info.htmlUrl)
            return
        }

        showCard(CARD_DOWNLOADING)
        downloadProgressBar.value = 0
        lblDownloadingHeader.text = "Downloading ${asset.name}..."
        lblDownloadStats.text = "Connecting..."

        val downloader = UpdateDownloader()
        activeDownloader = downloader

        Thread {
            val tempDir = UpdateDownloader.getUpdateTempDir()
            val targetFile = File(tempDir, asset.name)

            // Step 1: Download installer
            val downloadResult = downloader.downloadFile(
                url = asset.browser_download_url,
                targetFile = targetFile,
                expectedSize = asset.size
            ) { progress ->
                SwingUtilities.invokeLater {
                    downloadProgressBar.value = progress.percent
                    val downStr = formatSize(progress.downloadedBytes.toDouble())
                    val totalStr = if (progress.totalBytes > 0) formatSize(progress.totalBytes.toDouble()) else "--"
                    val speedStr = if (progress.speedBps > 0) formatSize(progress.speedBps.toDouble()) + "/s" else "--"
                    lblDownloadStats.text = "$downStr / $totalStr (${progress.percent}%)  -  $speedStr"
                }
            }

            if (downloadResult.isFailure) {
                val ex = downloadResult.exceptionOrNull()
                SwingUtilities.invokeLater {
                    if (downloader.isCancelled) {
                        showCard(CARD_AVAILABLE)
                    } else {
                        lblErrorHeader.text = "Download Failed"
                        lblErrorMessage.text = "<html><center>${ex?.message ?: "Failed to download update package."}</center></html>"
                        showCard(CARD_ERROR)
                    }
                }
                return@Thread
            }

            val downloaded = downloadResult.getOrThrow()
            downloadedInstallerFile = downloaded

            // Step 2: Fetch and verify checksum if available
            var checksumText: String? = null
            if (info.checksumAsset != null && info.checksumAsset.browser_download_url.isNotBlank()) {
                SwingUtilities.invokeLater {
                    lblDownloadStats.text = "Verifying checksum..."
                }
                checksumText = downloader.fetchText(info.checksumAsset.browser_download_url)
            }

            val verifyResult = UpdateChecksumVerifier.verify(
                file = downloaded,
                expectedSize = asset.size,
                sha256SumsContent = checksumText,
                targetFileName = asset.name
            )

            SwingUtilities.invokeLater {
                when (verifyResult) {
                    is ChecksumResult.Valid -> {
                        lblReadyHeader.text = "Update Ready to Install"
                        lblChecksumDetails.text = "✓ SHA-256 Checksum Verified (${verifyResult.sha256.take(12)}...)"
                        showCard(CARD_READY)
                    }
                    is ChecksumResult.HashNotFoundInSums -> {
                        lblReadyHeader.text = "Update Download Complete"
                        lblChecksumDetails.text = "Size verified. SHA-256: ${verifyResult.actualHash.take(12)}..."
                        showCard(CARD_READY)
                    }
                    is ChecksumResult.Mismatch -> {
                        runCatching { downloaded.delete() }
                        lblErrorHeader.text = "Checksum Verification Failed"
                        lblErrorMessage.text = "<html><center>The downloaded package did not match the official SHA-256 hash.<br>Expected: ${verifyResult.expected.take(16)}...<br>Actual: ${verifyResult.actual.take(16)}...<br>The file has been deleted for your safety.</center></html>"
                        showCard(CARD_ERROR)
                    }
                    is ChecksumResult.SizeMismatch -> {
                        runCatching { downloaded.delete() }
                        lblErrorHeader.text = "Download Incomplete"
                        lblErrorMessage.text = "<html><center>Expected ${verifyResult.expected} bytes but received ${verifyResult.actual} bytes.<br>The partial file has been deleted.</center></html>"
                        showCard(CARD_ERROR)
                    }
                    is ChecksumResult.Error -> {
                        lblErrorHeader.text = "Verification Error"
                        lblErrorMessage.text = "<html><center>${verifyResult.message}</center></html>"
                        showCard(CARD_ERROR)
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "xdm-update-downloader"
        }.start()
    }

    companion object {
        internal const val CARD_CHECKING = "CHECKING"
        internal const val CARD_UP_TO_DATE = "UP_TO_DATE"
        internal const val CARD_AVAILABLE = "AVAILABLE"
        internal const val CARD_DOWNLOADING = "DOWNLOADING"
        internal const val CARD_READY = "READY"
        internal const val CARD_ERROR = "ERROR"

        /**
         * Opens the update dialog or brings an existing one to front.
         */
        fun open(
            parent: Component?,
            manualCheck: Boolean = false,
            releaseInfo: ReleaseInfo? = null
        ): UpdateDialog = SingleWindows.show(UpdateDialog::class) {
            UpdateDialog(shownWindow(parent), manualCheck, releaseInfo)
        }
    }
}

