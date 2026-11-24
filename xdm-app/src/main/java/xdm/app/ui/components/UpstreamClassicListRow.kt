package xdm.app.ui.components

import xdm.app.AppContext
import xdm.app.utils.isMacPopupTrigger
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.event.CellEditorListener
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * 1:1 upstream subhra74/xdm classic row using [RowWashPanel] + [DownloadRowPanel].
 * Used when the built-in "Original XDM" skin/layout is active with classic list view.
 */
class UpstreamClassicListRow(
    private val table: JTable,
    private val model: MainListViewModel? = null
) : TableCellRenderer, TableCellEditor {
    private val wash = RowWashPanel()
    private val row = DownloadRowPanel(wash)
    private var editEntry: DbRecord? = null

    var onPauseClick: ActionCallBack? by row::onPauseClick
    var onResumeClick: ActionCallBack? by row::onResumeClick
    var onOpenFolderClick: ActionCallBack? by row::onOpenFolderClick
    var onDeleteClick: ActionCallBack? by row::onDeleteClick
    var onMenuClick: ActionCallBack? by row::onMenuClick
    var onOpen: ActionCallBack? = null
    var onContextMenu: ((invoker: Component, x: Int, y: Int) -> Unit)? = null

    private val editRow: Int
        get() = if (editEntry != null && table.isEditing) table.editingRow else -1

    init {
        model?.addTableModelListener { e: TableModelEvent ->
            val r = e.firstRow
            val vr = editRow
            if (vr == -1 || r < 0 || r >= table.rowCount) return@addTableModelListener
            if (table.convertRowIndexToView(r) == vr) {
                (model.getValueAt(r, 0) as? DbRecord)?.let { show(it, table.isRowSelected(vr)) }
            }
        }

        val shortcut = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        val mouseListener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val vr = editRow
                if (vr == -1) return
                table.requestFocusInWindow()
                if (e.isPopupTrigger || isMacPopupTrigger(e)) {
                    showContextMenu(e, vr)
                    return
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return
                val toggle = (e.modifiersEx and shortcut) != 0
                table.changeSelection(vr, 0, toggle, e.isShiftDown)
                if (e.clickCount == 2 && !toggle && !e.isShiftDown) {
                    editEntry?.let {
                        if (it.status == RecordStatus.FINISHED) {
                            onOpen?.invoke(it)
                        } else {
                            AppContext.app.showProgressWindow(it.id, it.fileName)
                        }
                    }
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                val vr = editRow
                if (vr != -1 && e.isPopupTrigger) showContextMenu(e, vr)
            }

            override fun mouseEntered(e: MouseEvent) = setHovered(true)

            override fun mouseExited(e: MouseEvent) {
                if (wash.getMousePosition(true) == null) setHovered(false)
            }
        }
        row.attachMouseListener(mouseListener)
    }

    private fun showContextMenu(e: MouseEvent, vr: Int) {
        if (!table.isRowSelected(vr)) table.changeSelection(vr, 0, false, false)
        onContextMenu?.invoke(e.component, e.x, e.y)
    }

    private fun setHovered(hovered: Boolean) {
        if (wash.hovered == hovered) return
        wash.hovered = hovered
        wash.repaint()
    }

    fun selectionChanged() {
        val vr = editRow
        if (vr == -1) return
        val selected = table.isRowSelected(vr)
        if (wash.selected != selected) {
            wash.selected = selected
            row.selected = selected
            wash.repaint()
        }
    }

    val height: Int
        get() = row.height

    fun showMenuUnderButton(menu: JPopupMenu) = row.showMenuUnderButton(menu)

    private fun show(ent: DbRecord, isSelected: Boolean) {
        row.showFileIcon(ent)
        row.update(ent, showButtons = true)
        wash.selected = isSelected
        row.selected = isSelected
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        editEntry = null
        this.row.entry = null
        wash.hovered = false
        show(value as DbRecord, isSelected)
        return wash
    }

    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        editEntry = value as? DbRecord
        this.row.entry = editEntry
        wash.hovered = true
        editEntry?.let { show(it, isSelected) }
        return wash
    }

    override fun getCellEditorValue(): Any = 99
    override fun isCellEditable(anEvent: EventObject?): Boolean = true
    override fun shouldSelectCell(anEvent: EventObject?): Boolean = false
    override fun stopCellEditing(): Boolean = true
    override fun cancelCellEditing() {}
    override fun addCellEditorListener(l: CellEditorListener) {}
    override fun removeCellEditorListener(l: CellEditorListener) {}
}
