package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.screens.ClearDownloadsDialog
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.SingleWindows
import xdm.app.utils.px
import xdm.core.downloaders.DownloadType
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Window
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableModelEvent
import javax.swing.table.TableRowSorter

class DownloadTableView(
    final override val filter: MainListViewFilter = MainListViewFilter(FilterState.All, null, "")
) : JPanel(BorderLayout()), DownloadListView {

    private val model: DownloadTableModel = DownloadTableModel()
    private val table: JTable = JTable(model)
    private val sorter: TableRowSorter<DownloadTableModel> = TableRowSorter(model)
    private val scrollPane: JScrollPane
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val emptyPanel: JPanel
    private val emptyIconLabel = JLabel(FontIcon(RemixIcon.BOOK_SHELF_LINE, 72.px, AppTheme.sidebarIcon)).apply {
        alignmentX = Component.CENTER_ALIGNMENT
    }
    private val emptyTextLabel = JLabel("No downloads found", SwingConstants.CENTER).apply {
        alignmentX = Component.CENTER_ALIGNMENT
        foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        font = font.deriveFont(14f.px)
    }
    private val listeners = mutableListOf<() -> Unit>()

    override var bulkSelectionCallback: ((Boolean) -> Unit)? = null

    private val window: Window?
        get() = SwingUtilities.windowForComponent(this)

    override val component: Component
        get() = this

    override val records: List<DbRecord>
        get() = (0 until model.rowCount).mapNotNull { model.getItemAt(it) }

    override val selectedItems: List<DbRecord>
        get() {
            val rows = table.selectedRows
            if (rows.isEmpty()) return emptyList()
            return rows.toList().mapNotNull { viewRow: Int ->
                if (viewRow in 0 until table.rowCount) {
                    val modelRow = table.convertRowIndexToModel(viewRow)
                    model.getItemAt(modelRow)
                } else null
            }
        }

    override val selectedRowCount: Int
        get() = table.selectedRowCount

    init {
        table.fillsViewportHeight = true
        table.rowHeight = 26.px
        table.showHorizontalLines = true
        table.showVerticalLines = false
        table.gridColor = UIManager.getColor("Table.gridColor") ?: Color(0x38, 0x38, 0x38)
        table.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        table.tableHeader.reorderingAllowed = true
        table.tableHeader.resizingAllowed = true
        table.tableHeader.font = table.tableHeader.font.deriveFont(Font.BOLD, 11f.px)
        table.dragEnabled = true
        table.transferHandler = DownloadListTransferHandler { selectedItems }

        // Install Renderers
        table.columnModel.getColumn(DownloadTableModel.COL_FILE_NAME).cellRenderer = DownloadTableRenderers.FileNameCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_SIZE).cellRenderer = DownloadTableRenderers.SizeCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_TYPE).cellRenderer = DownloadTableRenderers.TypeCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_STATUS).cellRenderer = DownloadTableRenderers.StatusCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_PROGRESS).cellRenderer = DownloadTableRenderers.ProgressCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_TIME_LEFT).cellRenderer = DownloadTableRenderers.TimeLeftCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_TRANSFER_RATE).cellRenderer = DownloadTableRenderers.TransferRateCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_DATE_ADDED).cellRenderer = DownloadTableRenderers.DateAddedCellRenderer()
        table.columnModel.getColumn(DownloadTableModel.COL_SWARM).cellRenderer = DownloadTableRenderers.SwarmCellRenderer()

        // Set Preferred Column Widths
        table.columnModel.getColumn(DownloadTableModel.COL_FILE_NAME).preferredWidth = 240.px
        table.columnModel.getColumn(DownloadTableModel.COL_SIZE).preferredWidth = 85.px
        table.columnModel.getColumn(DownloadTableModel.COL_TYPE).preferredWidth = 80.px
        table.columnModel.getColumn(DownloadTableModel.COL_STATUS).preferredWidth = 95.px
        table.columnModel.getColumn(DownloadTableModel.COL_PROGRESS).preferredWidth = 140.px
        table.columnModel.getColumn(DownloadTableModel.COL_TIME_LEFT).preferredWidth = 85.px
        table.columnModel.getColumn(DownloadTableModel.COL_TRANSFER_RATE).preferredWidth = 95.px
        table.columnModel.getColumn(DownloadTableModel.COL_DATE_ADDED).preferredWidth = 130.px
        table.columnModel.getColumn(DownloadTableModel.COL_SWARM).preferredWidth = 110.px

        // Sorting & Filtering
        sorter.rowFilter = filter
        table.rowSorter = sorter
        sorter.sortKeys = listOf(RowSorter.SortKey(DownloadTableModel.COL_DATE_ADDED, SortOrder.DESCENDING))

        scrollPane = JScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
            viewportBorder = BorderFactory.createEmptyBorder()
        }

        // Empty state
        emptyPanel = JPanel(BorderLayout()).apply {
            background = table.background
            val emptyBox = Box.createVerticalBox()
            emptyBox.add(Box.createVerticalGlue())
            emptyBox.add(emptyIconLabel)
            emptyBox.add(Box.createRigidArea(java.awt.Dimension(0, 10.px)))
            emptyBox.add(emptyTextLabel)
            emptyBox.add(Box.createVerticalGlue())
            add(emptyBox, BorderLayout.CENTER)
        }

        cardPanel.add(scrollPane, "TABLE")
        cardPanel.add(emptyPanel, "EMPTY")
        add(cardPanel, BorderLayout.CENTER)

        // Mouse Listeners
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    if (row != -1) {
                        val modelRow = table.convertRowIndexToModel(row)
                        val rec = model.getItemAt(modelRow) ?: return
                        if (rec.status == RecordStatus.FINISHED) {
                            AppMenuHandler.openFile(rec, window)
                        } else {
                            AppContext.app.showProgressWindow(rec.id, rec.fileName)
                        }
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) {
                handlePopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                handlePopup(e)
            }

            private fun handlePopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val row = table.rowAtPoint(e.point)
                    if (row != -1 && !table.isRowSelected(row)) {
                        table.setRowSelectionInterval(row, row)
                    }
                    showContextMenu(e.x, e.y)
                }
            }
        })

        // Keyboard navigation
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val rec = selectedItems.firstOrNull() ?: return
                        if (rec.status == RecordStatus.FINISHED) {
                            AppMenuHandler.openFile(rec, window)
                        } else {
                            AppContext.app.showProgressWindow(rec.id, rec.fileName)
                        }
                        e.consume()
                    }
                    KeyEvent.VK_DELETE -> {
                        if (selectedItems.isNotEmpty()) {
                            AppMenuHandler.deleteSelectedDownloads(selectedItems, window)
                        }
                        e.consume()
                    }
                    KeyEvent.VK_F2 -> {
                        selectedItems.forEach { AppMenuHandler.resumeDownload(it) }
                        e.consume()
                    }
                    KeyEvent.VK_F4 -> {
                        selectedItems.forEach { AppMenuHandler.pauseDownload(it) }
                        e.consume()
                    }
                }
            }
        })

        // Selection Listener
        table.selectionModel.addListSelectionListener(ListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                bulkSelectionCallback?.invoke(selectedItems.size > 1)
            }
        })

        // Model Listeners
        model.addTableModelListener { e ->
            updateCardView()
            listeners.forEach { it.invoke() }
        }

        updateCardView()
    }

    private fun updateCardView() {
        if (table.rowCount == 0) {
            cardLayout.show(cardPanel, "EMPTY")
        } else {
            cardLayout.show(cardPanel, "TABLE")
        }
    }

    private fun showContextMenu(x: Int, y: Int) {
        val selected = selectedItems
        if (selected.isEmpty()) return

        val first = selected.first()
        val menu = JPopupMenu()

        if (selected.size == 1 && first.status == RecordStatus.FINISHED) {
            menu.add(JMenuItem("Open", FontIcon(RemixIcon.FILE_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.openFile(first, window) }
            })
            menu.add(JMenuItem("Open Folder", FontIcon(RemixIcon.FOLDER_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.openFolder(first, window) }
            })
            menu.addSeparator()
        }

        if (selected.any { it.status.isStopped }) {
            menu.add(JMenuItem("Resume", FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)).apply {
                addActionListener { selected.forEach { AppMenuHandler.resumeDownload(it) } }
            })
        }

        if (selected.any { it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY || it.status == RecordStatus.ASSEMBLING }) {
            menu.add(JMenuItem("Pause / Stop", FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)).apply {
                addActionListener { selected.forEach { AppMenuHandler.pauseDownload(it) } }
            })
        }

        if (selected.size == 1 && (first.status.isStopped || first.status == RecordStatus.FINISHED) &&
            first.downloadType != DownloadType.Torrent && first.downloadType != DownloadType.Batch
        ) {
            menu.add(JMenuItem("Restart", FontIcon(RemixIcon.ARROW_UP_DOWN_FILL, 16.px)).apply {
                addActionListener { AppMenuHandler.restartDownload(first) }
            })
        }

        menu.addSeparator()

        if (selected.size == 1) {
            menu.add(JMenuItem("Download Details...", FontIcon(RemixIcon.SETTINGS_4_LINE, 16.px)).apply {
                addActionListener { AppContext.app.showProgressWindow(first.id, first.fileName) }
            })
            if (first.downloadType == DownloadType.Torrent) {
                menu.add(JMenuItem("Torrent Options & Details...", FontIcon(RemixIcon.SETTINGS_4_LINE, 16.px)).apply {
                    addActionListener {
                        val task = AppContext.torrentManager.getTask(first.id)
                        if (task != null) {
                            xdm.app.ui.screens.TorrentDetailsDialog(window, task).isVisible = true
                        }
                    }
                })
            }
            menu.add(JMenuItem("Copy URL", FontIcon(RemixIcon.FILE_COPY_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.copyUrl(first) }
            })
            menu.addSeparator()
        }

        menu.add(JMenuItem("Delete", FontIcon(RemixIcon.DELETE_BIN_LINE, 16.px)).apply {
            addActionListener { AppMenuHandler.deleteSelectedDownloads(selected, window) }
        })

        menu.show(table, x, y)
    }

    override fun rowUpdated(index: Int) {
        model.fireTableRowsUpdated(index, index)
        updateCardView()
    }

    override fun rowDeleted(index: Int) {
        model.fireTableDataChanged()
        updateCardView()
    }

    override fun rowsRemoved() {
        model.fireTableDataChanged()
        updateCardView()
    }

    override fun rowAdded(index: Int) {
        model.fireTableRowsInserted(index, index)
        updateCardView()
    }

    override fun addRowsChangedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    override fun filterStateChanged(state: FilterState) {
        filter.filterState = state
        sorter.sort()
        updateCardView()
    }

    override fun filterCategoryChanged(category: DownloadCategory?) {
        filter.filterCategory = category
        sorter.sort()
        updateCardView()
    }

    override fun searchTextChanged(text: String) {
        filter.searchText = text
        sorter.sort()
        updateCardView()
    }

    override fun sort(sortKey: SortKey, ascending: Boolean) {
        val col = when (sortKey) {
            SortKey.NAME -> DownloadTableModel.COL_FILE_NAME
            SortKey.SIZE -> DownloadTableModel.COL_SIZE
            SortKey.TYPE -> DownloadTableModel.COL_TYPE
            SortKey.DATE -> DownloadTableModel.COL_DATE_ADDED
        }
        val order = if (ascending) SortOrder.ASCENDING else SortOrder.DESCENDING
        sorter.sortKeys = listOf(RowSorter.SortKey(col, order))
    }

    override fun clear() {
        SingleWindows.show(ClearDownloadsDialog::class) { ClearDownloadsDialog(window) }
    }

    fun selectById(id: Long) {
        val modelRow = model.findRowById(id)
        if (modelRow >= 0 && modelRow < model.rowCount) {
            val viewRow = table.convertRowIndexToView(modelRow)
            if (viewRow in 0 until table.rowCount) {
                table.setRowSelectionInterval(viewRow, viewRow)
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true))
            }
        }
    }

    override fun updateTheme() {
        val isDark = com.formdev.flatlaf.FlatLaf.isLafDark()
        table.gridColor = UIManager.getColor("Table.gridColor") ?: if (isDark) Color(0x2A, 0x2D, 0x34) else Color(0xDC, 0xDE, 0xE3)
        emptyPanel.background = table.background
        emptyIconLabel.icon = FontIcon(RemixIcon.BOOK_SHELF_LINE, 72.px, AppTheme.sidebarIcon)
        emptyTextLabel.foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        table.tableHeader.updateUI()
        table.tableHeader.font = table.tableHeader.font.deriveFont(Font.BOLD, 11f.px)
        (table.columnModel.getColumn(DownloadTableModel.COL_PROGRESS).cellRenderer as? DownloadTableRenderers.ProgressCellRenderer)?.updateUI()
        table.updateUI()
        repaint()
    }
}

