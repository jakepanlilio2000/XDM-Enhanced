package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.px
import xdm.core.downloaders.DownloadType
import xdm.core.util.FormatHelper
import xdm.manager.RecordStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.*

class DownloadStatusBar : JPanel(BorderLayout()) {

    private val lblSpeed = JLabel().apply {
        font = font.deriveFont(11f.px)
        border = ScaledEmptyBorder(2, 10, 2, 10)
    }
    private val lblTasks = JLabel().apply {
        font = font.deriveFont(11f.px)
        border = ScaledEmptyBorder(2, 10, 2, 10)
    }
    private val lblQueue = JLabel().apply {
        font = font.deriveFont(11f.px)
        border = ScaledEmptyBorder(2, 10, 2, 10)
    }
    private val lblDisk = JLabel().apply {
        font = font.deriveFont(11f.px)
        border = ScaledEmptyBorder(2, 10, 2, 10)
    }

    val speedText: String get() = lblSpeed.text
    val tasksText: String get() = lblTasks.text
    val queueText: String get() = lblQueue.text
    val diskText: String get() = lblDisk.text

    private val separators = mutableListOf<JSeparator>()
    private val pluginIndicatorsBox = Box.createHorizontalBox()
    private val timer = Timer(250) { updateMetrics() }

    init {
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), 26.px)
        background = AppTheme.sidebarBackground
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.sidebarBorder)

        lblSpeed.foreground = UIManager.getColor("Label.foreground")
        lblTasks.foreground = UIManager.getColor("Label.foreground")
        lblQueue.foreground = UIManager.getColor("Label.foreground")
        lblDisk.foreground = UIManager.getColor("Label.disabledForeground")

        val leftBox = Box.createHorizontalBox().apply {
            add(lblSpeed)
            add(createSeparator())
            add(lblTasks)
            add(createSeparator())
            add(lblQueue)
        }

        val rightBox = Box.createHorizontalBox().apply {
            add(pluginIndicatorsBox)
            add(lblDisk)
        }

        add(leftBox, BorderLayout.WEST)
        add(rightBox, BorderLayout.EAST)

        refreshPluginIndicators()
        xdm.app.plugins.PluginManager.addStateListener {
            SwingUtilities.invokeLater { refreshPluginIndicators() }
        }

        updateMetrics()
        timer.start()
    }

    fun refreshPluginIndicators() {
        pluginIndicatorsBox.removeAll()
        val indicators = xdm.app.plugins.PluginManager.getStatusIndicators()
        for ((plugin, contributor) in indicators) {
            try {
                val comp = contributor.createIndicator(plugin.context)
                if (comp != null) {
                    if (contributor.tooltip.isNotBlank()) {
                        (comp as? JComponent)?.toolTipText = contributor.tooltip
                    }
                    pluginIndicatorsBox.add(comp)
                    pluginIndicatorsBox.add(createSeparator())
                }
            } catch (t: Throwable) {
                xdm.core.util.Logger.error("Failed to render status indicator for plugin ${plugin.manifest.id}", t)
                val fallback = JLabel("!").apply {
                    foreground = java.awt.Color.ORANGE
                    toolTipText = "Indicator error (${plugin.manifest.name})"
                }
                pluginIndicatorsBox.add(fallback)
                pluginIndicatorsBox.add(createSeparator())
            }
        }
        pluginIndicatorsBox.revalidate()
        pluginIndicatorsBox.repaint()
    }

    private fun createSeparator(): JComponent {
        val sep = JSeparator(SwingConstants.VERTICAL).apply {
            maximumSize = Dimension(1.px, 16.px)
            foreground = AppTheme.sidebarBorder
        }
        separators.add(sep)
        return sep
    }

    fun updateTheme() {
        val skin = xdm.app.skins.SkinManager.currentSkin
        val hasWp = skin.hasWallpaper() || skin.hasRegionWallpaper(xdm.app.skins.SkinRegion.TOOLBAR)
        isOpaque = !hasWp
        background = AppTheme.sidebarBackground
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.sidebarBorder)
        separators.forEach { it.foreground = AppTheme.sidebarBorder }
        lblSpeed.foreground = UIManager.getColor("Label.foreground")
        lblTasks.foreground = UIManager.getColor("Label.foreground")
        lblQueue.foreground = UIManager.getColor("Label.foreground")
        lblDisk.foreground = UIManager.getColor("Label.disabledForeground")
        updateMetrics()
        repaint()
    }

    override fun paintComponent(g: java.awt.Graphics) {
        val painted = xdm.app.skins.SkinStyleResolver.paintRegionBackground(
            g, width, height, xdm.app.skins.SkinRegion.MAIN_FRAME
        )
        if (!painted) super.paintComponent(g)
    }

    fun updateMetrics() {
        val records = AppContext.db.filter { true }

        // Combined speed
        var downSpeed = 0f
        for (rec in records) {
            if (rec.status == RecordStatus.DOWNLOADING) {
                downSpeed += rec.speed
            }
        }

        // Upload speed from torrents
        var upSpeed = 0
        try {
            val tasks = AppContext.torrentManager.getAllTasks()
            for (t in tasks) {
                val snap = t.getSnapshot()
                upSpeed += snap.uploadRate
            }
        } catch (_: Throwable) {}

        val downStr = if (downSpeed > 0) FormatHelper.formatSize(downSpeed.toDouble()) + "/s" else "0 B/s"
        val upStr = if (upSpeed > 0) FormatHelper.formatSize(upSpeed.toDouble()) + "/s" else "0 B/s"
        lblSpeed.text = "Speed: $downStr ↓ | $upStr ↑"

        // Tasks
        val active = records.count { it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY || it.status == RecordStatus.ASSEMBLING }
        val paused = records.count { it.status.isStopped }
        val finished = records.count { it.status == RecordStatus.FINISHED }
        val total = records.size
        lblTasks.text = "Transfers: $active active | $paused paused | $finished done | $total total"

        // Queue
        val queueStatus = if (AppContext.hasScheduler && AppContext.scheduler.isRunning) "Running" else "Idle"
        lblQueue.text = "Queue: $queueStatus"

        // Disk space
        try {
            val folder = File(AppContext.config.defaultDownloadFolder)
            val free = folder.freeSpace
            val freeStr = if (free > 0) FormatHelper.formatSize(free.toDouble()) else "--"
            lblDisk.text = "Disk Free: $freeStr"
        } catch (_: Throwable) {
            lblDisk.text = "Disk Free: --"
        }

        // Plugin indicators update
        val indicators = xdm.app.plugins.PluginManager.getStatusIndicators()
        for ((_, contributor) in indicators) {
            runCatching { contributor.updateIndicator() }
        }
    }

    fun dispose() {
        timer.stop()
    }
}

