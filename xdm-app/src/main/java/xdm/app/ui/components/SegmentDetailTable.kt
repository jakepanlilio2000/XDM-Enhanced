package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.utils.px
import xdm.core.downloaders.web.SegmentProgress
import xdm.core.util.FormatHelper.formatSize
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Expandable connection detail table showing segment number, downloaded bytes, byte range,
 * and real-time status/info for multi-connection downloads.
 */
class SegmentDetailTable : JPanel(BorderLayout()) {

    private val columnNames = arrayOf("N.", "Downloaded", "Byte Range", "Info / Status")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    val table = JTable(tableModel).apply {
        rowHeight = 22.px
        font = font.deriveFont(11f.px)
        tableHeader.font = font.deriveFont(Font.BOLD, 10.5f.px)
        tableHeader.reorderingAllowed = false
        showHorizontalLines = true
        showVerticalLines = true

        // Alignments
        val centerRenderer = DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.CENTER }
        val rightRenderer = DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.RIGHT }
        val leftRenderer = DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.LEFT }

        columnModel.getColumn(0).apply {
            preferredWidth = 40.px
            maxWidth = 50.px
            cellRenderer = centerRenderer
        }
        columnModel.getColumn(1).apply {
            preferredWidth = 140.px
            cellRenderer = rightRenderer
        }
        columnModel.getColumn(2).apply {
            preferredWidth = 150.px
            cellRenderer = centerRenderer
        }
        columnModel.getColumn(3).apply {
            preferredWidth = 160.px
            cellRenderer = leftRenderer
        }
    }

    private val scrollPane = JScrollPane(table).apply {
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), 130.px)
        minimumSize = Dimension(0, 80.px)
        border = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") ?: java.awt.Color.GRAY, 1)
    }

    init {
        isOpaque = false
        add(scrollPane, BorderLayout.CENTER)
    }

    fun updateSegments(segments: Collection<SegmentProgress>, totalSize: Long) {
        val list = segments.toList()
        if (list.isEmpty()) {
            if (tableModel.rowCount > 0) tableModel.rowCount = 0
            return
        }

        // Adjust row count to match segments
        if (tableModel.rowCount != list.size) {
            tableModel.rowCount = list.size
        }

        for ((index, seg) in list.withIndex()) {
            val segNumber = (index + 1).toString()
            val len = if (seg.length > 0) seg.length else 0L
            val dl = seg.downloaded.coerceAtMost(if (len > 0) len else Long.MAX_VALUE)
            val pct = if (len > 0) ((dl.toDouble() / len) * 100).toInt().coerceIn(0, 100) else 0

            val dlStr = if (len > 0) {
                "${formatSize(dl.toDouble())} (${pct}%)"
            } else {
                formatSize(dl.toDouble())
            }

            val rangeStr = if (totalSize > 0) {
                "%,d - %,d".format(seg.start, seg.start + len)
            } else if (seg.start > 0 || len > 0) {
                "%,d - %,d".format(seg.start, seg.start + len)
            } else {
                "---"
            }

            val statusStr = seg.status.ifBlank {
                if (len > 0 && dl >= len) "Complete" else "Receiving data..."
            }

            tableModel.setValueAt(segNumber, index, 0)
            tableModel.setValueAt(dlStr, index, 1)
            tableModel.setValueAt(rangeStr, index, 2)
            tableModel.setValueAt(statusStr, index, 3)
        }
    }
}
