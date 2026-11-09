package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import xdm.app.AppContext
import xdm.app.utils.*
import xdm.core.downloaders.DownloadType
import xdm.core.util.FormatHelper
import xdm.manager.CategoryRules
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import xdm.torrent.TorrentTaskState
import java.awt.*
import java.awt.event.MouseListener
import javax.swing.*

/**
 * Visual card container for Classic Cards mode.
 * Paints an elevated, rounded card with generous spacing and a distinct selection border.
 */
class ClassicCardContainer : JPanel(BorderLayout()) {
    var selected: Boolean = false
    var hovered: Boolean = false
    var cardArc: Int = 10.px

    init {
        isOpaque = false
        border = ScaledEmptyBorder(5, 12, 5, 12)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val x = 12.px
        val y = 5.px
        val w = width - 24.px
        val h = height - 10.px

        if (w > 0 && h > 0) {
            val isDark = FlatLaf.isLafDark()
            val cardBg = when {
                selected -> AppTheme.listSelectionBackground
                hovered -> if (isDark) Color(0x28, 0x2C, 0x36) else Color(0xEE, 0xF2, 0xF8)
                else -> if (isDark) Color(0x21, 0x25, 0x2E) else Color(0xF8, 0xF9, 0xFB)
            }
            g2.color = cardBg
            g2.fillRoundRect(x, y, w, h, cardArc, cardArc)

            // Card border
            if (selected) {
                g2.color = AppTheme.accent
                g2.stroke = BasicStroke(2f.px)
                g2.drawRoundRect(x + 1, y + 1, w - 2, h - 2, cardArc, cardArc)
            } else if (hovered) {
                g2.color = if (isDark) Color(0x48, 0x50, 0x60) else Color(0xB8, 0xC4, 0xD2)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(x, y, w - 1, h - 1, cardArc, cardArc)
            } else {
                g2.color = if (isDark) Color(0x32, 0x37, 0x42) else Color(0xDA, 0xE0, 0xE7)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(x, y, w - 1, h - 1, cardArc, cardArc)
            }
        }
        g2.dispose()
        super.paintComponent(g)
    }
}

/**
 * A rich, spacious card representation of a download item for Classic Cards view mode.
 */
class ClassicCardPanel {
    val container = ClassicCardContainer()
    val cardBody = JPanel(BorderLayout(0, 8.px)).apply {
        isOpaque = false
        border = ScaledEmptyBorder(10, 14, 10, 14)
    }

    var entry: DbRecord? = null

    var onPauseClick: ActionCallBack? = null
    var onResumeClick: ActionCallBack? = null
    var onOpenFolderClick: ActionCallBack? = null
    var onDeleteClick: ActionCallBack? = null
    var onMenuClick: ActionCallBack? = null

    val iconBadge: JPanel
    val iconLabel: JLabel
    val lblTitle: JLabel
    val lblTypeBadge: JLabel
    val lblCategoryBadge: JLabel
    val lblStatusPill: JLabel
    val lblDateAdded: JLabel
    val lblSwarm: JLabel

    val btnPause: JButton
    val btnResume: JButton
    val btnOpenFolder: JButton
    val btnDelete: JButton
    val btnMenu: JButton

    val prg: JProgressBar

    val lblDownloaded: JLabel
    val lblSpeed: JLabel
    val lblEta: JLabel
    val lblProgressPercent: JLabel

    val height: Int = 115.px

    init {
        // --- 1. North: Header (Icon, Title, Badges, Status, and Actions) ---
        val headerPanel = JPanel(BorderLayout(12.px, 0)).apply { isOpaque = false }

        // Icon badge: 40x40 circular badge
        val badge = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = background
                g2.fillOval(0, 0, width, height)
                g2.dispose()
            }
        }.apply {
            isOpaque = false
            preferredSize = Dimension(40.px, 40.px)
            minimumSize = Dimension(40.px, 40.px)
            maximumSize = Dimension(40.px, 40.px)
        }
        iconLabel = JLabel().apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
        }
        badge.add(iconLabel, BorderLayout.CENTER)
        iconBadge = badge
        headerPanel.add(iconBadge, BorderLayout.WEST)

        // Middle title & info block
        val titleBox = Box.createVerticalBox()
        val row1 = Box.createHorizontalBox()
        lblTitle = JLabel("Filename.ext").apply {
            font = font.deriveFont(Font.BOLD, 13.5f.px)
            foreground = AppTheme.listTitle
        }
        lblTypeBadge = JLabel("HTTP").apply {
            font = font.deriveFont(Font.BOLD, 10f.px)
            border = ScaledEmptyBorder(1, 5, 1, 5)
            isOpaque = true
            putClientProperty(FlatClientProperties.STYLE, "arc: 6")
        }
        lblCategoryBadge = JLabel("General").apply {
            font = font.deriveFont(Font.PLAIN, 10.5f.px)
            border = ScaledEmptyBorder(1, 5, 1, 5)
            isOpaque = true
            putClientProperty(FlatClientProperties.STYLE, "arc: 6")
            background = Color(128, 128, 128, 35)
        }

        row1.add(lblTitle)
        row1.add(Box.createRigidArea(Dimension(8.px, 0)))
        row1.add(lblTypeBadge)
        row1.add(Box.createRigidArea(Dimension(6.px, 0)))
        row1.add(lblCategoryBadge)
        row1.add(Box.createHorizontalGlue())

        val row2 = Box.createHorizontalBox()
        lblStatusPill = JLabel("● Downloading").apply {
            font = font.deriveFont(Font.BOLD, 11f.px)
        }
        lblDateAdded = JLabel("Added: --").apply {
            font = font.deriveFont(Font.PLAIN, 11f.px)
            foreground = Color.GRAY
        }
        lblSwarm = JLabel("").apply {
            font = font.deriveFont(Font.PLAIN, 11f.px)
            foreground = Color.GRAY
            isVisible = false
        }

        row2.add(lblStatusPill)
        row2.add(Box.createRigidArea(Dimension(10.px, 0)))
        row2.add(lblDateAdded)
        row2.add(Box.createRigidArea(Dimension(10.px, 0)))
        row2.add(lblSwarm)
        row2.add(Box.createHorizontalGlue())

        titleBox.add(row1)
        titleBox.add(Box.createRigidArea(Dimension(0, 4.px)))
        titleBox.add(row2)
        headerPanel.add(titleBox, BorderLayout.CENTER)

        // Actions on the right
        val actionsBox = Box.createHorizontalBox()
        btnPause = createButton(RemixIcon.PAUSE_CIRCLE_LINE, "Pause") { entry?.let { onPauseClick?.invoke(it) } }
        btnResume = createButton(RemixIcon.PLAY_CIRCLE_LINE, "Resume") { entry?.let { onResumeClick?.invoke(it) } }
        btnOpenFolder = createButton(RemixIcon.FOLDER_6_LINE, "Open Folder") { entry?.let { onOpenFolderClick?.invoke(it) } }
        btnDelete = createButton(RemixIcon.DELETE_BIN_LINE, "Delete") { entry?.let { onDeleteClick?.invoke(it) } }
        btnMenu = createButton(RemixIcon.MORE_2_FILL, "More Options") { entry?.let { onMenuClick?.invoke(it) } }

        actionsBox.add(btnPause)
        actionsBox.add(Box.createRigidArea(Dimension(3.px, 0)))
        actionsBox.add(btnResume)
        actionsBox.add(Box.createRigidArea(Dimension(3.px, 0)))
        actionsBox.add(btnOpenFolder)
        actionsBox.add(Box.createRigidArea(Dimension(3.px, 0)))
        actionsBox.add(btnDelete)
        actionsBox.add(Box.createRigidArea(Dimension(3.px, 0)))
        actionsBox.add(btnMenu)
        headerPanel.add(actionsBox, BorderLayout.EAST)

        // --- 2. Center: Prominent Progress Bar ---
        prg = JProgressBar(0, 100).apply {
            preferredSize = Dimension(100, 8.px)
            maximumSize = Dimension(Int.MAX_VALUE, 8.px)
            putClientProperty(FlatClientProperties.STYLE, "arc: 8")
            isStringPainted = false
            foreground = AppTheme.accent
        }

        // --- 3. South: Metrics Footer (Size, Speed, ETA, Progress %) ---
        val metricsPanel = JPanel(GridLayout(1, 4, 8.px, 0)).apply { isOpaque = false }
        lblDownloaded = JLabel("Downloaded: --").apply {
            font = font.deriveFont(Font.PLAIN, 11f.px)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }
        lblSpeed = JLabel("Speed: --").apply {
            font = font.deriveFont(Font.PLAIN, 11f.px)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }
        lblEta = JLabel("ETA: --").apply {
            font = font.deriveFont(Font.PLAIN, 11f.px)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }
        lblProgressPercent = JLabel("0%", SwingConstants.RIGHT).apply {
            font = font.deriveFont(Font.BOLD, 12f.px)
            foreground = AppTheme.accent
        }

        metricsPanel.add(lblDownloaded)
        metricsPanel.add(lblSpeed)
        metricsPanel.add(lblEta)
        metricsPanel.add(lblProgressPercent)

        cardBody.add(headerPanel, BorderLayout.NORTH)
        cardBody.add(prg, BorderLayout.CENTER)
        cardBody.add(metricsPanel, BorderLayout.SOUTH)

        container.add(cardBody, BorderLayout.CENTER)
    }

    private fun createButton(iconName: RemixIcon, tooltip: String, action: () -> Unit): JButton {
        return JButton(FontIcon(iconName, 16.px, Color.GRAY)).apply {
            putClientProperty("JButton.buttonType", "toolBarButton")
            isFocusable = false
            toolTipText = tooltip
            preferredSize = Dimension(28.px, 28.px)
            minimumSize = Dimension(28.px, 28.px)
            maximumSize = Dimension(28.px, 28.px)
            addActionListener { action() }
        }
    }

    fun showMenuUnderButton(menu: JPopupMenu) {
        showMenu(btnMenu, menu)
    }

    fun setHovered(hovered: Boolean) {
        if (container.hovered != hovered) {
            container.hovered = hovered
            container.repaint()
        }
    }

    fun setSelected(selected: Boolean) {
        if (container.selected != selected) {
            container.selected = selected
            container.repaint()
        }
    }

    fun update(ent: DbRecord, isSelected: Boolean, isHovered: Boolean) {
        this.entry = ent
        container.selected = isSelected
        container.hovered = isHovered

        val isDark = FlatLaf.isLafDark()
        val accent = AppTheme.accent

        // Filename
        lblTitle.text = ent.fileName
        lblTitle.toolTipText = ent.fileName

        // Download type pill
        when (ent.downloadType) {
            DownloadType.Torrent -> {
                lblTypeBadge.text = "TORRENT"
                lblTypeBadge.foreground = if (isDark) Color(63, 185, 80) else Color(26, 127, 55)
                lblTypeBadge.background = if (isDark) Color(46, 160, 67, 50) else Color(46, 160, 67, 30)
            }
            DownloadType.Hls -> {
                lblTypeBadge.text = "HLS"
                lblTypeBadge.foreground = Color(240, 136, 62)
                lblTypeBadge.background = Color(240, 136, 62, 40)
            }
            DownloadType.Dash -> {
                lblTypeBadge.text = "DASH"
                lblTypeBadge.foreground = Color(163, 113, 247)
                lblTypeBadge.background = Color(163, 113, 247, 40)
            }
            DownloadType.Batch -> {
                lblTypeBadge.text = "BATCH"
                lblTypeBadge.foreground = Color(210, 153, 34)
                lblTypeBadge.background = Color(210, 153, 34, 40)
            }
            else -> {
                lblTypeBadge.text = "HTTP"
                lblTypeBadge.foreground = Color(56, 139, 253)
                lblTypeBadge.background = Color(56, 139, 253, 40)
            }
        }

        // Category pill
        val catName = resolveCategoryName(ent)
        lblCategoryBadge.text = catName

        val torrentTask = if (ent.downloadType == DownloadType.Torrent) AppContext.torrentManager.getTask(ent.id) else null
        val torrentSnap = torrentTask?.getSnapshot()

        // Status text and color
        if (torrentSnap?.state == TorrentTaskState.CHECKING_FILES) {
            lblStatusPill.text = "● Checking files ${torrentSnap.checkingProgressPercent}%"
            lblStatusPill.foreground = accent
        } else {
            when (ent.status) {
                RecordStatus.DOWNLOADING -> {
                    lblStatusPill.text = "● Downloading ${ent.progress}%"
                    lblStatusPill.foreground = accent
                }
                RecordStatus.FINISHED -> {
                    lblStatusPill.text = "● Completed"
                    lblStatusPill.foreground = if (isDark) Color(63, 185, 80) else Color(26, 127, 55)
                }
                RecordStatus.PAUSED -> {
                    lblStatusPill.text = "● Paused"
                    lblStatusPill.foreground = AppTheme.pausedForeground
                }
                RecordStatus.ERROR -> {
                    lblStatusPill.text = "● Error"
                    lblStatusPill.foreground = AppTheme.failedForeground
                }
                RecordStatus.ASSEMBLING -> {
                    lblStatusPill.text = "● Assembling..."
                    lblStatusPill.foreground = accent
                }
                RecordStatus.MOVING -> {
                    lblStatusPill.text = "● Moving..."
                    lblStatusPill.foreground = accent
                }
                RecordStatus.READY -> {
                    lblStatusPill.text = "● Queued"
                    lblStatusPill.foreground = Color.GRAY
                }
            }
        }

        // Date
        lblDateAdded.text = "Added: " + FormatHelper.formatDateShort(ent.date)

        // Swarm
        if (ent.downloadType == DownloadType.Torrent) {
            lblSwarm.isVisible = true
            lblSwarm.text = resolveSwarm(ent)
        } else {
            lblSwarm.isVisible = false
        }

        // Button states
        btnPause.isVisible = ent.canPause
        btnResume.isVisible = ent.canResume

        // Progress bar
        prg.foreground = accent
        if (ent.status == RecordStatus.MOVING || ent.status == RecordStatus.ASSEMBLING) {
            prg.isIndeterminate = true
        } else {
            prg.isIndeterminate = false
            prg.value = ent.progress
        }

        // Metrics
        val downStr = FormatHelper.formatSize(ent.downloaded.toDouble())
        val sizeStr = if (ent.size > 0) FormatHelper.formatSize(ent.size.toDouble()) else "--"
        lblDownloaded.text = "Downloaded: $downStr / $sizeStr"

        val speedStr = if (ent.status == RecordStatus.DOWNLOADING && ent.speed > 0) {
            FormatHelper.formatSize(ent.speed.toDouble()) + "/s"
        } else {
            "--"
        }
        lblSpeed.text = "Speed: $speedStr"

        val etaStr = if (ent.status == RecordStatus.FINISHED) {
            "Complete"
        } else if (ent.eta > 0) {
            FormatHelper.toLongEta(ent.eta)
        } else {
            "--"
        }
        lblEta.text = "ETA: $etaStr"

        if (torrentSnap?.state == TorrentTaskState.CHECKING_FILES) {
            lblProgressPercent.text = "${torrentSnap.checkingProgressPercent}%"
            lblProgressPercent.foreground = accent
            prg.value = torrentSnap.checkingProgressPercent
        } else {
            lblProgressPercent.text = if (ent.status == RecordStatus.FINISHED) "100%" else "${ent.progress}%"
            lblProgressPercent.foreground = if (ent.status == RecordStatus.FINISHED) {
                if (isDark) Color(63, 185, 80) else Color(26, 127, 55)
            } else {
                accent
            }
            prg.value = ent.progress
        }

        // Icon badge
        val cat = AppContext.config.categories.firstOrNull { it.id == ent.categoryId }
        val tile = AppTheme.tile(cat)
        val glyph = if (ent.downloadType == DownloadType.Torrent) {
            RemixIcon.DOWNLOAD_2_LINE
        } else {
            CategoryStyle.glyphForFile(ent.fileName)
        }
        iconBadge.background = tile.background
        iconLabel.icon = FontIcon(glyph, 22.px, tile.glyph)
    }

    private fun resolveCategoryName(rec: DbRecord): String {
        val categoryId = rec.categoryId
        if (categoryId != null) {
            val cat = AppContext.config.categories.firstOrNull { it.id == categoryId }
            if (cat != null) return cat.name
        }
        val matched = CategoryRules.match(rec.fileName, null, null)
        if (matched != null) return matched.name
        val ext = rec.fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("mp4", "mkv", "avi", "mov", "flv", "webm", "ts") -> "Video"
            ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a") -> "Audio"
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2") -> "Compressed"
            ext in setOf("pdf", "doc", "docx", "txt", "epub") -> "Document"
            ext in setOf("exe", "msi", "apk", "jar", "deb", "rpm") -> "Program"
            else -> "Other"
        }
    }

    private fun resolveSwarm(rec: DbRecord): String {
        if (rec.downloadType != DownloadType.Torrent) return ""
        return try {
            val task = AppContext.torrentManager.getTask(rec.id)
            if (task != null) {
                val snap = task.getSnapshot()
                "Peers: ${snap.numPeers} · Seeds: ${snap.numSeeds}"
            } else ""
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Recursively installs mouse listener on non-button components so clicks anywhere on the card
     * select or open it.
     */
    fun attachMouseListener(listener: MouseListener) {
        fun attach(comp: Component) {
            if (comp is JButton) return
            comp.addMouseListener(listener)
            if (comp is Container) {
                comp.components.forEach { attach(it) }
            }
        }
        attach(container)
    }
}

