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
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.event.CellEditorListener
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Card row for the Classic Cards download list mode.
 * Renders a full floating card container with rich metadata, category icon badge,
 * larger progress bar, and contextual quick actions.
 */
class ClassicListRow(
    private val table: JTable,
    private val model: MainListViewModel? = null
) : TableCellRenderer, TableCellEditor {
    private val card = ClassicCardPanel()
    private var editEntry: DbRecord? = null

    var onPauseClick: ActionCallBack? by card::onPauseClick
    var onResumeClick: ActionCallBack? by card::onResumeClick
    var onOpenFolderClick: ActionCallBack? by card::onOpenFolderClick
    var onDeleteClick: ActionCallBack? by card::onDeleteClick
    var onMenuClick: ActionCallBack? by card::onMenuClick
    var onOpen: ActionCallBack? = null

    /** A right click on the row at ([x], [y]) in [invoker]; the row is already part of the selection. */
    var onContextMenu: ((invoker: Component, x: Int, y: Int) -> Unit)? = null

    private val editRow: Int
        get() = if (editEntry != null && table.isEditing) table.editingRow else -1

    init {
        model?.addTableModelListener { e: TableModelEvent ->
            val r = e.firstRow
            val vr = editRow
            if (vr == -1 || r < 0 || r >= table.rowCount) return@addTableModelListener
            if (table.convertRowIndexToView(r) == vr) {
                (model.getValueAt(r, 0) as? DbRecord)?.let { show(it, table.isRowSelected(vr), isHovered = true) }
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

            // Windows raises the popup trigger on release.
            override fun mouseReleased(e: MouseEvent) {
                val vr = editRow
                if (vr != -1 && e.isPopupTrigger) showContextMenu(e, vr)
            }

            override fun mouseEntered(e: MouseEvent) {
                setHovered(true)
            }

            override fun mouseExited(e: MouseEvent) {
                if (card.container.getMousePosition(true) == null) setHovered(false)
            }
        }
        card.attachMouseListener(mouseListener)
    }

    private fun showContextMenu(e: MouseEvent, vr: Int) {
        if (!table.isRowSelected(vr)) table.changeSelection(vr, 0, false, false)
        onContextMenu?.invoke(e.component, e.x, e.y)
    }

    private fun setHovered(hovered: Boolean) {
        card.setHovered(hovered)
    }

    /** Called by the list when the selection changes, so the open editor follows it. */
    fun selectionChanged() {
        val vr = editRow
        if (vr == -1) return
        card.setSelected(table.isRowSelected(vr))
    }

    val height: Int
        get() = card.height

    fun showMenuUnderButton(menu: javax.swing.JPopupMenu) {
        card.showMenuUnderButton(menu)
    }

    private fun show(ent: DbRecord, isSelected: Boolean, isHovered: Boolean) {
        card.update(ent, isSelected = isSelected, isHovered = isHovered)
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        editEntry = null
        card.entry = null
        show(value as DbRecord, isSelected, isHovered = false)
        return card.container
    }

    override fun getTableCellEditorComponent(
        table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int
    ): Component {
        editEntry = value as? DbRecord
        editEntry?.let { show(it, isSelected, isHovered = true) }
        return card.container
    }

    override fun getCellEditorValue(): Any = 99

    override fun isCellEditable(anEvent: EventObject?): Boolean = true

    override fun shouldSelectCell(anEvent: EventObject?): Boolean = false

    override fun stopCellEditing(): Boolean = true

    override fun cancelCellEditing() {
        // Noop
    }

    override fun addCellEditorListener(l: CellEditorListener) {
        // Noop
    }

    override fun removeCellEditorListener(l: CellEditorListener) {
        // Noop
    }
}
