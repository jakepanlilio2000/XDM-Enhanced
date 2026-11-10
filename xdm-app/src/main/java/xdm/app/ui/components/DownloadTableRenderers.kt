package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.px
import xdm.core.downloaders.DownloadType
import xdm.core.util.FormatHelper
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.TorrentTaskState
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

object DownloadTableRenderers {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    open class SkinAwareCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val hasWallpaper = xdm.app.skins.SkinManager.currentSkin.hasWallpaper()
            isOpaque = isSelected || !hasWallpaper
            return comp
        }
    }

    class FileNameCellRenderer : SkinAwareCellRenderer() {
        init {
            border = ScaledEmptyBorder(0, 6, 0, 6)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            text = value as? String ?: ""

            val modelRow = table.convertRowIndexToModel(row)
            val model = table.model as? DownloadTableModel
            val rec = model?.getItemAt(modelRow)

            icon = if (rec != null) {
                getRecordIcon(rec)
            } else {
                FontIcon(RemixIcon.FILE_LINE, 16.px)
            }
            return this
        }

        private fun getRecordIcon(rec: DbRecord): Icon {
            if (rec.downloadType == DownloadType.Torrent) {
                return FontIcon(RemixIcon.DOWNLOAD_2_LINE, 16.px, Color(56, 139, 253))
            }
            if (rec.downloadType == DownloadType.Plugin) {
                return FontIcon(RemixIcon.PUZZLE_LINE, 16.px, Color(0x7C, 0x4D, 0xFF))
            }
            val ext = rec.fileName.substringAfterLast('.', "").lowercase()
            return when {
                ext in setOf("mp4", "mkv", "avi", "mov", "flv", "webm", "ts") ->
                    FontIcon(RemixIcon.MOVIE_LINE, 16.px, Color(163, 113, 247))
                ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a") ->
                    FontIcon(RemixIcon.MUSIC_2_LINE, 16.px, Color(240, 136, 62))
                ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2") ->
                    FontIcon(RemixIcon.FILE_ZIP_LINE, 16.px, Color(210, 153, 34))
                ext in setOf("pdf", "doc", "docx", "txt", "epub") ->
                    FontIcon(RemixIcon.FILE_TEXT_LINE, 16.px, Color(88, 166, 255))
                ext in setOf("exe", "msi", "apk", "jar", "deb", "rpm") ->
                    FontIcon(RemixIcon.TERMINAL_BOX_LINE, 16.px, Color(230, 80, 80))
                else -> FontIcon(RemixIcon.FILE_LINE, 16.px)
            }
        }
    }

    class SizeCellRenderer : SkinAwareCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.RIGHT
            border = ScaledEmptyBorder(0, 6, 0, 8)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val bytes = (value as? Number)?.toLong() ?: 0L
            text = if (bytes > 0) FormatHelper.formatSize(bytes.toDouble()) else "--"
            return this
        }
    }

    class TypeCellRenderer : SkinAwareCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.LEFT
            border = ScaledEmptyBorder(0, 6, 0, 6)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            text = value as? String ?: ""
            return this
        }
    }

    class StatusCellRenderer : SkinAwareCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.LEFT
            border = ScaledEmptyBorder(0, 6, 0, 6)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val modelRow = table.convertRowIndexToModel(row)
            val model = table.model as? DownloadTableModel
            val rec = model?.getItemAt(modelRow)
            val torrentTask = if (rec?.downloadType == DownloadType.Torrent) AppContext.torrentManager.getTask(rec.id) else null
            val torrentSnap = torrentTask?.getSnapshot()
            val isChecking = torrentSnap?.state == TorrentTaskState.CHECKING_FILES

            if (isChecking) {
                text = "Checking (${torrentSnap!!.checkingProgressPercent}%)"
                if (!isSelected) {
                    foreground = AppTheme.accent
                } else {
                    foreground = table.selectionForeground
                }
            } else {
                val status = value as? RecordStatus
                if (status != null) {
                    text = when (status) {
                        RecordStatus.DOWNLOADING -> "Downloading"
                        RecordStatus.FINISHED -> "Finished"
                        RecordStatus.PAUSED -> "Paused"
                        RecordStatus.ERROR -> "Error"
                        RecordStatus.ASSEMBLING -> "Assembling"
                        RecordStatus.MOVING -> "Moving"
                        RecordStatus.READY -> "Queued"
                    }
                    if (!isSelected) {
                        val isDark = com.formdev.flatlaf.FlatLaf.isLafDark()
                        foreground = when (status) {
                            RecordStatus.FINISHED -> if (isDark) Color(63, 185, 80) else Color(26, 127, 55)
                            RecordStatus.DOWNLOADING -> AppTheme.accent
                            RecordStatus.PAUSED -> AppTheme.pausedForeground
                            RecordStatus.ERROR -> AppTheme.failedForeground
                            else -> table.foreground
                        }
                    } else {
                        foreground = table.selectionForeground
                    }
                } else {
                    text = ""
                }
            }
            return this
        }
    }

    class ProgressCellRenderer : TableCellRenderer {
        private val progressBar = JProgressBar(0, 100).apply {
            isStringPainted = true
            font = font.deriveFont(Font.BOLD, 10f.px)
            putClientProperty(FlatClientProperties.PROGRESS_BAR_SQUARE, false)
            putClientProperty(FlatClientProperties.STYLE, "arc: 6")
        }
        private val panel = JPanel(java.awt.BorderLayout()).apply {
            border = ScaledEmptyBorder(3, 4, 3, 4)
            isOpaque = true
            add(progressBar, java.awt.BorderLayout.CENTER)
        }

        fun updateUI() {
            SwingUtilities.updateComponentTreeUI(panel)
            progressBar.font = progressBar.font.deriveFont(Font.BOLD, 10f.px)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val progress = (value as? Number)?.toInt() ?: 0
            val modelRow = table.convertRowIndexToModel(row)
            val model = table.model as? DownloadTableModel
            val rec = model?.getItemAt(modelRow)
            val torrentTask = if (rec?.downloadType == DownloadType.Torrent) AppContext.torrentManager.getTask(rec.id) else null
            val torrentSnap = torrentTask?.getSnapshot()
            val isChecking = torrentSnap?.state == TorrentTaskState.CHECKING_FILES

            if (isChecking) {
                progressBar.value = torrentSnap!!.checkingProgressPercent
                progressBar.string = "Checking ${torrentSnap.checkingProgressPercent}%"
            } else {
                progressBar.value = progress
                progressBar.string = if (rec?.status == RecordStatus.FINISHED) {
                    "100%"
                } else {
                    "$progress%"
                }
            }

            val hasWallpaper = xdm.app.skins.SkinManager.currentSkin.hasWallpaper()
            panel.isOpaque = isSelected || !hasWallpaper
            if (isSelected) {
                panel.background = table.selectionBackground
            } else {
                panel.background = table.background
            }
            return panel
        }
    }

    class TimeLeftCellRenderer : SkinAwareCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.CENTER
            border = ScaledEmptyBorder(0, 6, 0, 6)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val eta = (value as? Number)?.toLong() ?: 0L
            text = if (eta > 0) FormatHelper.toLongEta(eta) else "--"
            return this
        }
    }

    class TransferRateCellRenderer : SkinAwareCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.RIGHT
            border = ScaledEmptyBorder(0, 6, 0, 8)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val speed = (value as? Number)?.toFloat() ?: 0f
            text = if (speed > 0f) FormatHelper.formatSize(speed.toDouble()) + "/s" else "--"
            return this
        }
    }

    class DateAddedCellRenderer : SkinAwareCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.LEFT
            border = ScaledEmptyBorder(0, 6, 0, 6)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val timestamp = (value as? Number)?.toLong() ?: 0L
            text = if (timestamp > 0) dateFormat.format(Date(timestamp)) else "--"
            return this
        }
    }

    class SwarmCellRenderer : SkinAwareCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.CENTER
            border = ScaledEmptyBorder(0, 6, 0, 6)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            text = value as? String ?: "--"
            return this
        }
    }
}

