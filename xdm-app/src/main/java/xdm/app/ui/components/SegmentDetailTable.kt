package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.utils.px
import xdm.core.downloaders.web.SegmentProgress
import xdm.core.util.FormatHelper.formatSize
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Expandable connection detail table showing connection number (N.), downloaded bytes
 * (with percentage when meaningful), and real-time Info / Status for multi-connection and
 * single-connection downloads.
 */
class SegmentDetailTable : JPanel(BorderLayout()) {

    private val columnNames = arrayOf("N.", "Downloaded", "Info / Status")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false

        override fun getValueAt(row: Int, column: Int): Any? {
            // Backward-compatibility: if legacy callers request index 3 (the old Info / Status index),
            // redirect transparently to index 2 (Info / Status in the 3-column model).
            if (column >= columnCount && column == 3 && columnCount >= 3) {
                return super.getValueAt(row, 2)
            }
            return super.getValueAt(row, column)
        }

        override fun setValueAt(aValue: Any?, row: Int, column: Int) {
            if (column >= columnCount && column == 3 && columnCount >= 3) {
                super.setValueAt(aValue, row, 2)
            } else {
                super.setValueAt(aValue, row, column)
            }
        }
    }

    val table = object : JTable(tableModel) {
        override fun getValueAt(row: Int, column: Int): Any? {
            // Backward-compatibility fallback for JTable.getValueAt(row, 3)
            if (column >= columnCount && column == 3 && columnCount >= 3) {
                return getValueAt(row, 2)
            }
            return super.getValueAt(row, column)
        }
    }.apply {
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
            preferredWidth = 45.px
            maxWidth = 60.px
            cellRenderer = centerRenderer
        }
        columnModel.getColumn(1).apply {
            preferredWidth = 160.px
            cellRenderer = rightRenderer
        }
        columnModel.getColumn(2).apply {
            preferredWidth = 240.px
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

    /**
     * Updates the connection detail rows with real worker counts, per-connection received bytes,
     * and accurate Info / Status.
     */
    fun updateSegments(segments: Collection<SegmentProgress>, totalSize: Long = 0L) {
        val list = segments.toList()
        if (list.isEmpty()) {
            if (tableModel.rowCount > 0) tableModel.rowCount = 0
            return
        }

        val selectedRow = table.selectedRow

        // Adjust row count to match active connection workers
        if (tableModel.rowCount != list.size) {
            tableModel.rowCount = list.size
        }

        for ((index, seg) in list.withIndex()) {
            val connNumber = (index + 1).toString()
            val len = if (seg.length > 0) seg.length else 0L
            val dl = seg.downloaded.coerceAtLeast(0L)
            val pct = if (len > 0) ((dl.toDouble() / len) * 100).toInt().coerceIn(0, 100) else null

            val dlStr = if (pct != null) {
                "${formatSize(dl.toDouble())} ($pct%)"
            } else {
                formatSize(dl.toDouble())
            }

            val statusStr = when {
                seg.status.isNotBlank() -> seg.status
                len > 0 && dl >= len -> "Complete"
                else -> "Receiving data..."
            }

            if (tableModel.getValueAt(index, 0) != connNumber) tableModel.setValueAt(connNumber, index, 0)
            if (tableModel.getValueAt(index, 1) != dlStr) tableModel.setValueAt(dlStr, index, 1)
            if (tableModel.getValueAt(index, 2) != statusStr) tableModel.setValueAt(statusStr, index, 2)
        }

        if (selectedRow in 0 until list.size && table.selectedRow != selectedRow) {
            table.setRowSelectionInterval(selectedRow, selectedRow)
        }
    }

    /** Alias for [updateSegments] for connection-centric callers. */
    fun updateConnections(connections: Collection<SegmentProgress>, totalSize: Long = 0L) {
        updateSegments(connections, totalSize)
    }
}
