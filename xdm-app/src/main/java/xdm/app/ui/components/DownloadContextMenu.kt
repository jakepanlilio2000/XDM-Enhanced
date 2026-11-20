package xdm.app.ui.components

import xdm.app.AppContext
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.px
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Component
import java.awt.Window
import java.io.File
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.SwingUtilities

/**
 * Unified context menu builder providing feature-complete, consistent download
 * actions across Table View, Modern Cards View, and Classic Cards View.
 */
object DownloadContextMenu {

    fun create(
        selected: List<DbRecord>,
        invoker: Component,
        window: Window? = SwingUtilities.getWindowAncestor(invoker)
    ): JPopupMenu {
        val menu = JPopupMenu()
        if (selected.isEmpty()) return menu

        val first = selected.first()
        val win = window ?: SwingUtilities.getWindowAncestor(invoker)

        if (selected.size == 1 && first.status == RecordStatus.FINISHED) {
            menu.add(JMenuItem("Open", FontIcon(RemixIcon.FILE_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.openFile(first, win) }
            })
            menu.add(JMenuItem("Open Folder", FontIcon(RemixIcon.FOLDER_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.openFolder(first, win) }
            })
            menu.addSeparator()
        }

        if (selected.any { it.status.isStopped || it.canResume }) {
            menu.add(JMenuItem("Resume", FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)).apply {
                addActionListener {
                    selected.filter { it.status.isStopped || it.canResume }.forEach {
                        AppMenuHandler.resumeDownload(it)
                    }
                }
            })
        }

        if (selected.any { it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY || it.status == RecordStatus.ASSEMBLING || it.canPause }) {
            menu.add(JMenuItem("Pause / Stop", FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)).apply {
                addActionListener {
                    selected.filter { it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY || it.status == RecordStatus.ASSEMBLING || it.canPause }.forEach {
                        AppMenuHandler.pauseDownload(it)
                    }
                }
            })
        }

        if (selected.size == 1 && (first.status.isStopped || first.status == RecordStatus.FINISHED) &&
            first.downloadType != DownloadType.Torrent && first.downloadType != DownloadType.Batch
        ) {
            menu.add(JMenuItem("Restart", FontIcon(RemixIcon.ARROW_UP_DOWN_FILL, 16.px)).apply {
                addActionListener { AppMenuHandler.restartDownload(first) }
            })
        }

        if (selected.size == 1 && first.status.isStopped && !AppContext.refreshLinkInProgress.get() &&
            first.downloadType != DownloadType.Batch && first.downloadType != DownloadType.Torrent
        ) {
            menu.add(JMenuItem("Refresh Download Link", FontIcon(RemixIcon.GLOBAL_LINE, 16.px)).apply {
                addActionListener { AppContext.app.showRefreshWindow(first.id) }
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
                            xdm.app.ui.screens.TorrentDetailsDialog(win, task).isVisible = true
                        }
                    }
                })
            }
            menu.add(JMenuItem("Copy URL", FontIcon(RemixIcon.FILE_COPY_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.copyUrl(first) }
            })
            menu.addSeparator()
        }

        val catMenu = JMenu("Category").apply {
            icon = FontIcon(RemixIcon.FOLDER_LINE, 16.px)
            val currentCatId = if (selected.size == 1) first.categoryId else null
            val noneItem = JRadioButtonMenuItem("None", currentCatId == null).apply {
                addActionListener { AppMenuHandler.changeCategory(selected, null) }
            }
            add(noneItem)
            addSeparator()
            AppContext.config.categories.forEach { cat ->
                val item = JRadioButtonMenuItem(cat.name, currentCatId == cat.id).apply {
                    addActionListener { AppMenuHandler.changeCategory(selected, cat.id) }
                }
                add(item)
            }
        }
        menu.add(catMenu)

        // Plugin actions
        val infoList = selected.map { toDownloadInfo(it) }
        val pluginActions = xdm.app.plugins.PluginManager.getContextMenuActions(infoList)
        if (pluginActions.isNotEmpty()) {
            menu.addSeparator()
            for (action in pluginActions) {
                menu.add(JMenuItem(action.label).apply {
                    action.tooltip?.let { toolTipText = it }
                    addActionListener { action.handler.accept(infoList) }
                })
            }
        }

        menu.addSeparator()

        menu.add(JMenuItem("Delete", FontIcon(RemixIcon.DELETE_BIN_LINE, 16.px)).apply {
            addActionListener { AppMenuHandler.deleteSelectedDownloads(selected, win) }
        })

        return menu
    }

    fun showAt(
        selected: List<DbRecord>,
        invoker: Component,
        x: Int,
        y: Int,
        window: Window? = SwingUtilities.getWindowAncestor(invoker)
    ) {
        if (selected.isEmpty()) return
        val menu = create(selected, invoker, window)
        menu.show(invoker, x, y)
    }

    private fun toDownloadInfo(rec: DbRecord): xdm.plugin.api.models.DownloadInfo {
        val taskInfo = runCatching { AppContext.taskInfoDB.getHttpTask(rec.id) }.getOrNull()
        val destPath = if (taskInfo != null) {
            File(taskInfo.targetFolder, rec.fileName).absolutePath
        } else {
            rec.fileName
        }
        val url = taskInfo?.url ?: ""
        return xdm.plugin.api.models.DownloadInfo(
            rec.id,
            url,
            rec.fileName,
            destPath,
            rec.size,
            rec.downloaded,
            rec.progress,
            rec.speed,
            rec.status.name,
            rec.categoryId ?: "None",
            rec.date
        )
    }
}
