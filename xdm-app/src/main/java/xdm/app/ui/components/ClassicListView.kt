package xdm.app.ui.components

import xdm.app.AppContext
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.event.ListSelectionEvent

/**
 * The Classic Cards download list: visually separate, spacious download cards
 * with category icon badges, thicker progress indicators, quick action buttons,
 * and rich stats. See [ClassicCardPanel] and [ClassicListRow].
 */
class ClassicListView(
    filter: MainListViewFilter = MainListViewFilter(FilterState.All, null, "")
) : DownloadListBase(filter) {
    private val menu = DownloadRowMenu(withPrimaryActions = true)

    init {
        table.showHorizontalLines = false
        table.showVerticalLines = false
        table.intercellSpacing = Dimension(0, 0)

        val renderer = ClassicListRow(table)
        val editor = ClassicListRow(table, model)
        install(renderer, editor, renderer.height)

        table.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) {
                editor.selectionChanged()
                bulkSelectionCallback?.invoke(table.selectedRowCount > 1)
                val infos = selectedItems.map { xdm.app.plugins.PluginManager.toDownloadInfo(it) }
                xdm.app.plugins.PluginEventBus.publish(
                    xdm.plugin.api.events.EventTopics.SELECTION_CHANGED,
                    xdm.plugin.api.events.SelectionChangeEvent(infos)
                )
            }
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger || xdm.app.utils.isMacPopupTrigger(e) || SwingUtilities.isRightMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    if (row != -1) {
                        if (!table.isRowSelected(row)) {
                            table.changeSelection(row, 0, false, false)
                        }
                        DownloadContextMenu.showAt(selectedItems, table, e.x, e.y, window)
                    }
                    return
                }
                if (SwingUtilities.isLeftMouseButton(e) && table.rowAtPoint(e.point) == -1) {
                    table.clearSelection()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger || xdm.app.utils.isMacPopupTrigger(e) || SwingUtilities.isRightMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    if (row != -1) {
                        if (!table.isRowSelected(row)) {
                            table.changeSelection(row, 0, false, false)
                        }
                        DownloadContextMenu.showAt(selectedItems, table, e.x, e.y, window)
                    }
                }
            }
        })

        editor.apply {
            onMenuClick = { entry: DbRecord -> showMenuUnderButton(menu.forRow(entry, jsp)) }
            onContextMenu = { invoker, x, y -> DownloadContextMenu.showAt(selectedItems, invoker, x, y, window) }
            onOpen = { e: DbRecord ->
                if (e.status == RecordStatus.FINISHED) {
                    AppMenuHandler.openFile(e, window)
                } else {
                    AppContext.app.showProgressWindow(e.id, e.fileName)
                }
            }
            onOpenFolderClick = { e: DbRecord -> AppMenuHandler.openFolder(e, window) }
            onPauseClick = { e: DbRecord -> AppMenuHandler.pauseDownload(e) }
            onResumeClick = { e: DbRecord -> AppMenuHandler.resumeDownload(e) }
            onDeleteClick = { e: DbRecord -> AppMenuHandler.deleteDownload(e, window) }
        }
    }
}
