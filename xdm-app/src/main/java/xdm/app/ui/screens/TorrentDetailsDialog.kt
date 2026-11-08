package xdm.app.ui.screens

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.px
import xdm.app.utils.scaledSize
import xdm.core.util.FormatHelper.formatSize
import xdm.torrent.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.*
import javax.swing.table.DefaultTableModel

class TorrentDetailsDialog(
    parent: Window?,
    private val task: TorrentDownloadTask
) : JDialog(parent, "BitTorrent Details - ${task.name}", ModalityType.MODELESS) {

    private val tabbedPane = JTabbedPane()

    // --- Trackers ---
    private val trackerTableModel = DefaultTableModel(arrayOf("Tier", "Tracker URL", "Status", "Failures"), 0)
    private val trackerTable = JTable(trackerTableModel).apply {
        setRowHeight(24.px)
        font = font.deriveFont(11f.px)
        tableHeader.font = font.deriveFont(Font.BOLD, 11f.px)
        tableHeader.reorderingAllowed = false
    }

    // --- Peers ---
    private val peerTableModel = DefaultTableModel(arrayOf("IP Address", "Client", "Down Speed", "Up Speed", "Progress", "Source", "Flags"), 0)
    private val peerTable = JTable(peerTableModel).apply {
        setRowHeight(24.px)
        font = font.deriveFont(11f.px)
        tableHeader.font = font.deriveFont(Font.BOLD, 11f.px)
        tableHeader.reorderingAllowed = false
    }

    // --- Files ---
    private val fileTableModel = DefaultTableModel(arrayOf("#", "File Name", "Size", "Priority"), 0)
    private val fileTable = JTable(fileTableModel).apply {
        setRowHeight(24.px)
        font = font.deriveFont(11f.px)
        tableHeader.font = font.deriveFont(Font.BOLD, 11f.px)
        tableHeader.reorderingAllowed = false
    }

    // --- Limits & Seeding ---
    private val spnDownLimit = JSpinner(SpinnerNumberModel(task.downloadLimit / 1024, 0, 10_000_000, 50)).apply {
        preferredSize = Dimension(100.px, preferredSize.height)
    }
    private val spnUpLimit = JSpinner(SpinnerNumberModel(task.uploadLimit / 1024, 0, 10_000_000, 50)).apply {
        preferredSize = Dimension(100.px, preferredSize.height)
    }
    private val lblSeedingStats = JLabel("---")
    private val cmbSeedingMode = JComboBox(arrayOf("Never (stop when finished)", "Seed until ratio or time limit", "Unlimited seeding")).apply {
        selectedIndex = when (task.seedingRule.mode) {
            SeedingMode.NEVER -> 0
            SeedingMode.UNLIMITED -> 2
            else -> 1
        }
    }
    private val spnRatioLimit = JSpinner(SpinnerNumberModel(task.seedingRule.maxRatio.toDouble(), 0.1, 100.0, 0.1))
    private val spnTimeLimit = JSpinner(SpinnerNumberModel((task.seedingRule.maxTimeSeconds / 60L).toInt(), 1, 10080, 15))

    // --- Queue & Privacy ---
    private val lblQueuePos = JLabel(if (task.queuePosition >= 0) "Queue Position: #${task.queuePosition}" else "Queue Position: Inactive (not downloading)")
    private val lblPrivate = JLabel(if (task.isPrivate) "Private Torrent: Yes (DHT, PEX, and LSD are disabled)" else "Private Torrent: No (Public swarm discovery allowed)")

    private val refreshTimer = Timer(2000) { refreshAll() }

    init {
        size = scaledSize(680, 480)
        minimumSize = scaledSize(550, 380)
        setLocationRelativeTo(parent)

        initTabs()

        val bottomBar = Box.createHorizontalBox().apply {
            border = ScaledEmptyBorder(10, 14, 10, 14)
            add(Box.createHorizontalGlue())
            add(JButton("Close").apply {
                addActionListener { dispose() }
            })
        }

        contentPane.layout = BorderLayout()
        contentPane.add(tabbedPane, BorderLayout.CENTER)
        contentPane.add(bottomBar, BorderLayout.SOUTH)

        refreshAll()
        refreshTimer.start()

        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                refreshTimer.stop()
            }
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                refreshTimer.stop()
            }
        })
    }

    private fun initTabs() {
        tabbedPane.addTab("Trackers", FontIcon(RemixIcon.GLOBAL_LINE, 14.px), createTrackersTab())
        tabbedPane.addTab("Peers", FontIcon(RemixIcon.COMPUTER_FILL, 14.px), createPeersTab())
        tabbedPane.addTab("Files & Priorities", FontIcon(RemixIcon.FILE_LIST_2_LINE, 14.px), createFilesTab())
        tabbedPane.addTab("Limits & Seeding", FontIcon(RemixIcon.SPEED_LINE, 14.px), createLimitsTab())
        tabbedPane.addTab("Queue & Privacy", FontIcon(RemixIcon.LIST_CHECK_2, 14.px), createQueueTab())
    }

    private fun createTrackersTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 8.px)).apply {
            border = ScaledEmptyBorder(10, 10, 10, 10)
        }
        panel.add(JScrollPane(trackerTable), BorderLayout.CENTER)

        val btnAddTracker = JButton("Add Tracker...", FontIcon(RemixIcon.ADD_LINE, 14.px)).apply {
            addActionListener { promptAddTracker() }
        }
        val btnReannounce = JButton("Reannounce", FontIcon(RemixIcon.PROGRESS_2_LINE, 14.px)).apply {
            addActionListener {
                task.reannounce()
                JOptionPane.showMessageDialog(this@TorrentDetailsDialog, "Reannounce signal sent to all trackers.", "Reannounce", JOptionPane.INFORMATION_MESSAGE)
                refreshTrackers()
            }
        }
        val btnRefresh = JButton("Refresh").apply {
            addActionListener { refreshTrackers() }
        }

        val actionBox = Box.createHorizontalBox().apply {
            add(btnAddTracker)
            add(Box.createHorizontalStrut(6.px))
            add(btnReannounce)
            add(Box.createHorizontalGlue())
            add(btnRefresh)
        }
        panel.add(actionBox, BorderLayout.SOUTH)
        return panel
    }

    private fun createPeersTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 8.px)).apply {
            border = ScaledEmptyBorder(10, 10, 10, 10)
        }
        panel.add(JScrollPane(peerTable), BorderLayout.CENTER)

        val btnRefresh = JButton("Refresh").apply {
            addActionListener { refreshPeers() }
        }
        val actionBox = Box.createHorizontalBox().apply {
            add(Box.createHorizontalGlue())
            add(btnRefresh)
        }
        panel.add(actionBox, BorderLayout.SOUTH)
        return panel
    }

    private fun createFilesTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 8.px)).apply {
            border = ScaledEmptyBorder(10, 10, 10, 10)
        }
        panel.add(JScrollPane(fileTable), BorderLayout.CENTER)

        val cmbPriority = JComboBox(arrayOf("High Priority", "Normal Priority", "Low Priority", "Do Not Download (Skip)"))
        val btnSetPriority = JButton("Set Selected Priority").apply {
            addActionListener {
                val row = fileTable.selectedRow
                if (row >= 0) {
                    val priority = when (cmbPriority.selectedIndex) {
                        0 -> TorrentPriority.HIGH
                        1 -> TorrentPriority.NORMAL
                        2 -> TorrentPriority.LOW
                        else -> TorrentPriority.DO_NOT_DOWNLOAD
                    }
                    task.setFilePriority(row, priority)
                    refreshFiles()
                } else {
                    JOptionPane.showMessageDialog(this@TorrentDetailsDialog, "Select a file to change its priority.", "Notice", JOptionPane.INFORMATION_MESSAGE)
                }
            }
        }

        val actionBox = Box.createHorizontalBox().apply {
            add(JLabel("Priority for selected:"))
            add(Box.createHorizontalStrut(6.px))
            add(cmbPriority)
            add(Box.createHorizontalStrut(6.px))
            add(btnSetPriority)
            add(Box.createHorizontalGlue())
            add(JButton("Refresh").apply { addActionListener { refreshFiles() } })
        }
        panel.add(actionBox, BorderLayout.SOUTH)
        return panel
    }

    private fun createLimitsTab(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            border = ScaledEmptyBorder(14, 18, 14, 18)
        }
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(6.px, 6.px, 6.px, 6.px)
        }

        var row = 0

        // Download Limit
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3
        panel.add(JLabel("Download Rate Limit (KB/s):"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.5
        panel.add(spnDownLimit, gbc)
        gbc.gridx = 2; gbc.gridy = row; gbc.weightx = 0.2
        panel.add(JButton("Apply").apply {
            addActionListener {
                val kb = (spnDownLimit.value as? Number)?.toInt() ?: 0
                task.setDownloadLimit(kb * 1024)
                JOptionPane.showMessageDialog(this@TorrentDetailsDialog, "Download rate limit applied.", "Limit", JOptionPane.INFORMATION_MESSAGE)
            }
        }, gbc)

        row++

        // Upload Limit
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3
        panel.add(JLabel("Upload Rate Limit (KB/s):"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.5
        panel.add(spnUpLimit, gbc)
        gbc.gridx = 2; gbc.gridy = row; gbc.weightx = 0.2
        panel.add(JButton("Apply").apply {
            addActionListener {
                val kb = (spnUpLimit.value as? Number)?.toInt() ?: 0
                task.setUploadLimit(kb * 1024)
                JOptionPane.showMessageDialog(this@TorrentDetailsDialog, "Upload rate limit applied.", "Limit", JOptionPane.INFORMATION_MESSAGE)
            }
        }, gbc)

        row++

        // Seeding stats
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3
        panel.add(JLabel("Current Seeding Status:"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.7; gbc.gridwidth = 2
        panel.add(lblSeedingStats, gbc)
        gbc.gridwidth = 1

        row++

        // Manual seeding buttons
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3
        panel.add(JLabel("Manual Seeding Control:"), gbc)
        val seedBtnBox = Box.createHorizontalBox().apply {
            add(JButton("Start Seeding", FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 14.px)).apply {
                addActionListener {
                    task.startSeeding()
                    refreshLimits()
                }
            })
            add(Box.createHorizontalStrut(8.px))
            add(JButton("Stop Seeding", FontIcon(RemixIcon.CLOSE_CIRCLE_LINE, 14.px)).apply {
                addActionListener {
                    task.stopSeeding()
                    refreshLimits()
                }
            })
        }
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.7; gbc.gridwidth = 2
        panel.add(seedBtnBox, gbc)
        gbc.gridwidth = 1

        row++

        // Seeding Rule Mode
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3
        panel.add(JLabel("Seeding Rule:"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.7; gbc.gridwidth = 2
        panel.add(cmbSeedingMode, gbc)
        gbc.gridwidth = 1

        row++

        // Ratio limit
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3
        panel.add(JLabel("Share Ratio Limit:"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.7; gbc.gridwidth = 2
        panel.add(spnRatioLimit, gbc)
        gbc.gridwidth = 1

        row++

        // Time limit
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3
        panel.add(JLabel("Seeding Time Limit (minutes):"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.7; gbc.gridwidth = 2
        panel.add(spnTimeLimit, gbc)
        gbc.gridwidth = 1

        row++

        // Apply Seeding Rule
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.7; gbc.gridwidth = 2
        panel.add(JButton("Save Seeding Rules").apply {
            addActionListener {
                val mode = when (cmbSeedingMode.selectedIndex) {
                    0 -> SeedingMode.NEVER
                    2 -> SeedingMode.UNLIMITED
                    else -> SeedingMode.RATIO_OR_TIME
                }
                val ratio = ((spnRatioLimit.value as? Number)?.toFloat() ?: 1.0f).coerceAtLeast(0.1f)
                val minutes = ((spnTimeLimit.value as? Number)?.toInt() ?: 60).coerceAtLeast(1)
                task.seedingRule = SeedingRule(mode, ratio, minutes.toLong() * 60L)
                JOptionPane.showMessageDialog(this@TorrentDetailsDialog, "Seeding rules saved for this torrent.", "Rules", JOptionPane.INFORMATION_MESSAGE)
            }
        }, gbc)

        val outer = JPanel(BorderLayout()).apply {
            add(panel, BorderLayout.NORTH)
        }
        return outer
    }

    private fun createQueueTab(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            border = ScaledEmptyBorder(18, 20, 18, 20)
        }
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(8.px, 6.px, 8.px, 6.px)
        }

        var row = 0
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(lblQueuePos.apply { font = font.deriveFont(Font.BOLD, 13f.px) }, gbc)

        row++
        val queueActions = Box.createHorizontalBox().apply {
            add(JButton("Move Up", FontIcon(RemixIcon.ARROW_UP_DOWN_FILL, 14.px)).apply {
                addActionListener {
                    task.queueUp()
                    refreshQueue()
                }
            })
            add(Box.createHorizontalStrut(6.px))
            add(JButton("Move Down", FontIcon(RemixIcon.ARROW_DOWN_S_LINE, 14.px)).apply {
                addActionListener {
                    task.queueDown()
                    refreshQueue()
                }
            })
            add(Box.createHorizontalStrut(6.px))
            add(JButton("Move to Top", FontIcon(RemixIcon.ARROW_UP_DOWN_FILL, 14.px)).apply {
                addActionListener {
                    task.queueTop()
                    refreshQueue()
                }
            })
            add(Box.createHorizontalStrut(6.px))
            add(JButton("Move to Bottom", FontIcon(RemixIcon.ARROW_DOWN_CIRCLE_LINE, 14.px)).apply {
                addActionListener {
                    task.queueBottom()
                    refreshQueue()
                }
            })
        }
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(queueActions, gbc)

        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(JSeparator(), gbc)

        row++
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(lblPrivate.apply { font = font.deriveFont(12f.px) }, gbc)

        val outer = JPanel(BorderLayout()).apply {
            add(panel, BorderLayout.NORTH)
        }
        return outer
    }

    private fun promptAddTracker() {
        val url = JOptionPane.showInputDialog(
            this,
            "Enter Tracker URL (e.g. http://... or udp://...):",
            "Add Tracker",
            JOptionPane.PLAIN_MESSAGE
        )?.trim() ?: return

        if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("udp://", ignoreCase = true)
        ) {
            task.addTracker(url, 0)
            refreshTrackers()
        } else {
            JOptionPane.showMessageDialog(this, "Tracker URL must start with http://, https://, or udp://", "Invalid URL", JOptionPane.WARNING_MESSAGE)
        }
    }

    private fun refreshAll() {
        refreshTrackers()
        refreshPeers()
        refreshFiles()
        refreshLimits()
        refreshQueue()
    }

    private fun refreshTrackers() {
        val trackers = task.getTrackers()
        trackerTableModel.rowCount = 0
        for (tr in trackers) {
            val status = if (tr.isUpdating) "Updating..." else if (tr.isWorking) "Working" else if (tr.message.isNotBlank()) tr.message else "Inactive"
            trackerTableModel.addRow(arrayOf(tr.tier.toString(), tr.url, status, tr.fails.toString()))
        }
    }

    private fun refreshPeers() {
        val peers = task.getPeers()
        peerTableModel.rowCount = 0
        for (p in peers) {
            val downStr = if (p.downSpeed > 0) formatSize(p.downSpeed.toDouble()) + "/s" else "0 B/s"
            val upStr = if (p.upSpeed > 0) formatSize(p.upSpeed.toDouble()) + "/s" else "0 B/s"
            val progStr = "${p.progressPercent}%"
            peerTableModel.addRow(arrayOf(p.ip, p.client, downStr, upStr, progStr, p.source, p.flags))
        }
    }

    private fun refreshFiles() {
        val meta = task.getMetadata()
        val priorities = task.getFilePriorities()
        fileTableModel.rowCount = 0
        if (meta != null) {
            meta.files.forEachIndexed { idx, f ->
                val prio = priorities.getOrElse(idx) { TorrentPriority.NORMAL }
                val prioStr = when (prio) {
                    TorrentPriority.HIGH -> "High"
                    TorrentPriority.NORMAL -> "Normal"
                    TorrentPriority.LOW -> "Low"
                    TorrentPriority.DO_NOT_DOWNLOAD -> "Do Not Download"
                }
                fileTableModel.addRow(arrayOf(idx.toString(), f.path, formatSize(f.size.toDouble()), prioStr))
            }
        }
    }

    private fun refreshLimits() {
        val snap = task.getSnapshot()
        val uploadStr = formatSize(snap.totalUploadBytes.toDouble())
        val ratioStr = "%.2f".format(snap.shareRatio)
        val seedTimeStr = "${snap.seedingDurationSeconds / 60} min ${snap.seedingDurationSeconds % 60} sec"
        lblSeedingStats.text = "Uploaded: $uploadStr | Ratio: $ratioStr | Seeding Duration: $seedTimeStr"
    }

    private fun refreshQueue() {
        val pos = task.queuePosition
        lblQueuePos.text = if (pos >= 0) "Queue Position: #${pos}" else "Queue Position: Inactive (not downloading)"
    }
}
