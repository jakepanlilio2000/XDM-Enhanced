package xdm.app.ui.components

import xdm.app.AppContext
import xdm.app.AppContext.downloader
import xdm.app.I8N.text
import xdm.app.MessageBoxResult
import xdm.app.ui.screens.AppWindow
import xdm.app.ui.screens.BatchUrlDialog
import xdm.app.ui.screens.ChangeFolderDialog
import xdm.app.ui.screens.NetworkOptimizerDialog
import xdm.app.ui.screens.SettingsWindow
import xdm.app.ui.screens.StreamDownloadDialog
import xdm.app.utils.SingleWindows
import xdm.app.utils.captureDestination
import xdm.app.utils.openFileExternal
import xdm.app.utils.openFolderExternal
import xdm.app.utils.setClipBoardText
import xdm.core.downloaders.DownloadType
import xdm.core.util.CoreUtils
import xdm.core.util.Logger
//import xdman.Config
//import xdman.constants.MessageBoxResult
//import xdman.ui.components.BatchPatternDialog
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.manager.getFileFolder
import java.awt.Window
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*
import javax.swing.JComboBox
import javax.swing.JOptionPane

object AppMenuHandler {

    data class SelectionResult(val approve: Boolean, val deleteFromDisk: Boolean)

    fun openFile(ent: DbRecord?, window: Window?) {
        if (ent == null) return
        if (ent.status == RecordStatus.FINISHED) {
            Logger.info("Opening file for id: " + ent.id)
            val (fileName: String?, folder: String?) = getFileFolder(ent) ?: return
            if (fileName != null && folder != null) {
                try {
                    openFileExternal(fileName, folder);
                } catch (e: FileNotFoundException) {
                    Logger.info(e);
                    MessageBox.show(
                        window,
                        text("ERR_MSG_FILE_NOT_FOUND"),
                        text("ERR_MSG_FILE_NOT_FOUND_MSG")
                    );
                } catch (e: Exception) {
                    Logger.error(e);
                }
            }
        }
    }

    fun openFolder(ent: DbRecord?, window: Window?) {
        if (ent == null) return
        if (ent.status == RecordStatus.FINISHED) {
            Logger.error("Opening folder for id: " + ent.id)
            val (fileName: String?, folder: String?) = getFileFolder(ent) ?: return
            if (folder != null) {
                try {
                    Logger.info("Folder: $folder File: $fileName")
                    openFolderExternal(fileName, folder)
                } catch (e: FileNotFoundException) {
                    Logger.error(e)
                    MessageBox.show(
                        window,
                        text("ERR_MSG_FILE_NOT_FOUND"),
                        text("ERR_MSG_FILE_NOT_FOUND_MSG")
                    )
                } catch (e: Exception) {
                    Logger.error(e)
                }
            }
        }
    }

    /**
     * Restart: submits the download again exactly like a new browser capture (docs/download-categories.md
     * §6.2): a new entry with a new id, its category and folder resolved afresh, the duplicate prompt as
     * usual. The old entry is left as it is. Offered for stopped and finished HTTP, HLS and DASH downloads.
     */
    fun restartDownload(ent: DbRecord?) {
        if (ent == null) return
        if (!(ent.status.isStopped || ent.status == RecordStatus.FINISHED)) return
        val db = AppContext.taskInfoDB
        when (ent.downloadType) {
            DownloadType.Http -> db.getHttpTask(ent.id)?.let { t ->
                val (categoryId, folder) = captureDestination(t.fileName, t.url, t.origin)
                AppContext.app.addDownload(
                    t.copy(
                        id = CoreUtils.uniqueId(), categoryId = categoryId, targetFolder = folder,
                        etag = null,
                    )
                )
            }

            DownloadType.Hls -> db.getHlsTask(ent.id)?.let { t ->
                val vid = AppContext.videoTracker.addRestart(t.copy(id = CoreUtils.uniqueId()))
                AppContext.videoTracker.addVideoDownload(vid)
            }

            DownloadType.Dash -> db.getDashTask(ent.id)?.let { t ->
                val vid = AppContext.videoTracker.addRestart(t.copy(id = CoreUtils.uniqueId()))
                AppContext.videoTracker.addVideoDownload(vid)
            }

            else -> {}
        }
    }

    /** Opens Change folder for a stopped download (docs/download-categories.md §6.1). */
    fun changeFolder(ent: DbRecord?, window: Window?) {
        if (ent == null || !ent.status.isStopped || ent.downloadType == DownloadType.Batch) return
        SingleWindows.show("change-folder" to ent.id) { ChangeFolderDialog(window, ent) }
    }

    /** Changes the category for the selected downloads. */
    fun changeCategory(records: List<DbRecord>, newCategoryId: String?) {
        records.forEach { ent ->
            if (ent.categoryId != newCategoryId) {
                downloader.changeCategory(ent.id, newCategoryId)
            }
        }
    }

    fun resumeDownload(ent: DbRecord?) {
        if (ent == null) return
        if (ent.downloadType == DownloadType.Torrent) {
            AppContext.torrentManager.getTask(ent.id)?.resume()
            ent.status = RecordStatus.DOWNLOADING
            AppContext.db.update(ent)
            AppContext.app.updateDownloadInView(ent.id)
        } else if (ent.downloadType == DownloadType.Plugin) {
            xdm.app.plugins.PluginDownloadBridge.resumeTask(ent.id)
            AppContext.app.updateDownloadInView(ent.id)
        } else {
            downloader.resumeDownload(ent.id)
        }
    }

    fun pauseDownload(ent: DbRecord?) {
        if (ent == null) return
        if (ent.downloadType == DownloadType.Torrent) {
            AppContext.torrentManager.getTask(ent.id)?.pause()
            ent.status = RecordStatus.PAUSED
            AppContext.db.update(ent)
            AppContext.app.updateDownloadInView(ent.id)
        } else if (ent.downloadType == DownloadType.Plugin) {
            xdm.app.plugins.PluginDownloadBridge.pauseTask(ent.id)
            AppContext.app.updateDownloadInView(ent.id)
        } else {
            downloader.stopDownload(ent.id)
        }
    }

    private fun showConfirmDeleteDialog(window: Window?): SelectionResult {
        val ret = MessageBox.confirmWithCheckBox(
            window,
            text("DEL_TITLE"),
            text("DEL_SEL_TEXT"),
            text("LBL_DELETE_FILE")
        )
        return SelectionResult(
            approve = ret != MessageBoxResult.CANCEL,
            deleteFromDisk = ret == MessageBoxResult.YES_WITH_SELECTION
        )
    }

    /** Tells the user that downloads being moved were left out of a delete. */
    private fun warnMovingSkipped(window: Window?) =
        MessageBox.show(window, text("CAT_DELETE"), text("MSG_DELETE_SKIPPED_MOVING"))

    fun deleteDownload(ent: DbRecord?, window: Window?) {
        if (ent == null) return
        if (downloader.isMoving(ent.id)) return warnMovingSkipped(window)
        val (approve, deleteFromDisk) = showConfirmDeleteDialog(window)
        if (approve) {
            Logger.info("XDM", "Deleting ${ent.id}")
            if (ent.downloadType == DownloadType.Torrent) {
                AppContext.torrentManager.removeTask(ent.id, deleteFromDisk)
                AppContext.db.removeItem(ent.id)
                AppContext.app.downloadsRemovedInView()
            } else if (ent.downloadType == DownloadType.Plugin) {
                xdm.app.plugins.PluginDownloadBridge.deleteTask(ent.id, deleteFromDisk)
                AppContext.app.downloadsRemovedInView()
            } else {
                downloader.deleteDownload(ent.id, deleteFromDisk)
            }
        }
    }

    fun deleteSelectedDownloads(selectedItems: List<DbRecord>, window: Window?) {
        if (selectedItems.isEmpty()) return
        val (approve, deleteFromDisk) = showConfirmDeleteDialog(window)
        if (approve) {
            Logger.info("XDM", "Deleting ${selectedItems.size} selected")
            val (torrents, nonTorrents) = selectedItems.partition { it.downloadType == DownloadType.Torrent }
            val (plugins, http) = nonTorrents.partition { it.downloadType == DownloadType.Plugin }
            for (t in torrents) {
                AppContext.torrentManager.removeTask(t.id, deleteFromDisk)
                AppContext.db.removeItem(t.id)
            }
            for (p in plugins) {
                xdm.app.plugins.PluginDownloadBridge.deleteTask(p.id, deleteFromDisk)
            }
            val (moving, rest) = http.partition { downloader.isMoving(it.id) }
            if (rest.isNotEmpty()) {
                downloader.deleteDownloads(rest.map { it.id }, deleteFromDisk)
            }
            if (moving.isNotEmpty()) warnMovingSkipped(window)
            if (torrents.isNotEmpty() || plugins.isNotEmpty()) {
                AppContext.app.downloadsRemovedInView()
            }
        }
    }

    fun stopQueue(name: String?) {
        //    String queueId = "";
        //    String[] arr = name.split(":");
        //    if (arr.length > 1) {
        //      queueId = arr[1].trim();
        //    }
        //    DownloadQueue q = XDMApp.getInstance().getQueueById(queueId);
        //    if (q != null) {
        //      q.stop();
        //    }
    }

    fun openTranslationPage() {
        //XDMUtils.browseURL("https://github.com/subhra74/xdm/wiki/Submitting-translations-for-XDM")
    }

    fun openSupportPage() {
        //XDMUtils.browseURL("https://github.com/subhra74/xdm/wiki")
    }

    fun openBugReportPage() {
        //XDMUtils.browseURL("https://github.com/subhra74/xdm/issues")
    }

    fun showNetworkOptimizer(window: Window?) {
        SingleWindows.show(NetworkOptimizerDialog::class) { NetworkOptimizerDialog(window) }
    }

    fun optimizeRWin() {
        showNetworkOptimizer(null)
    }

    /** "Batch download": asks for the links, then shows the batch dialog. */
    fun showBatchDialog(window: Window?) {
        SingleWindows.show(BatchUrlDialog::class) { BatchUrlDialog(window) }
    }

    /** "Stream download (HLS/DASH)": a non-modal dialog; each click opens another. */
    fun showStreamDialog(window: Window?) {
        StreamDownloadDialog(window).isVisible = true
    }

    fun openFile(window: AppWindow?) {
        //    var items = window.getSelectedDownloads();
        //    if (!items.isEmpty()) {
        //      openFile(items.get(0), window);
        //    }
    }

    fun showLanguageDlg(window: AppWindow?) {
        val settings = SingleWindows.show(SettingsWindow::class) {
            SettingsWindow().apply {
                onSaved = { window?.applyListSelectionMode() }
                setLocationRelativeTo(window)
                loadConfig()
            }
        }
        settings.selectPage("MSG_LANG1")
        settings.toFront()
        settings.requestFocus()
    }

    fun changeFile(window: AppWindow?) {
        //    var items = window.getSelectedDownloads();
        //    if (items == null || items.isEmpty()) {
        //      return;
        //    }
        //    var ent = items.get(0);
        //    if (ent.getState() == XDMConstants.FINISHED) {
        //      return;
        //    }
        //    JFileChooser jfc = new JFileChooser();
        //    jfc.setSelectedFile(
        //        new File(
        //            XDMApp.getInstance().getOutputFolder(ent.getId()),
        //            XDMApp.getInstance().getOutputFile(ent.getId(), false)));
        //    if (jfc.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
        //      File f = jfc.getSelectedFile();
        //      ent.setFolder(f.getParent());
        //      ent.setFile(f.getName());
        //      XDMApp.getInstance().fileNameChanged(ent.getId());
        //    }
    }

    fun openRefreshPage(window: AppWindow?) {
        val selected = window?.selectedDownloads?.firstOrNull()
        if (selected == null) {
            MessageBox.show(
                window,
                text("MENU_REFRESH_LINK"),
                "Please select an interrupted, paused, or failed download to refresh its link."
            )
            return
        }
        if (selected.downloadType != DownloadType.Http) {
            MessageBox.show(
                window,
                text("MENU_REFRESH_LINK"),
                text("ERR_NO_REFRESH")
            )
            return
        }
        if (selected.status == RecordStatus.FINISHED) {
            MessageBox.show(
                window,
                text("MENU_REFRESH_LINK"),
                "The selected download is already completed."
            )
            return
        }
        if (!selected.status.isStopped) {
            MessageBox.show(
                window,
                text("MENU_REFRESH_LINK"),
                "Please pause the download before refreshing its link."
            )
            return
        }
        AppContext.app.showRefreshWindow(selected.id)
    }



    fun startQueue(name: String?) {
        //    String queueId = "";
        //    String[] arr = name.split(":");
        //    if (arr.length > 1) {
        //      queueId = arr[1].trim();
        //    }
        //    DownloadQueue q = XDMApp.getInstance().getQueueById(queueId);
        //    if (q != null) {
        //      q.start();
        //    }
    }

    fun showProgressWindow(window: AppWindow?) {
        //    var items = window.getSelectedDownloads();
        //    if (items == null || items.isEmpty()) {
        //      return;
        //    }
        //    DownloadEntry ent = items.get(0);
        //    XDMApp.getInstance().showPrgWnd(ent.getId());
    }

    fun deleteCompleted(window: Window?) {
        val finished = AppContext.db.filter { it.status == RecordStatus.FINISHED }
        if (finished.isEmpty()) return
        val ret = MessageBox.confirm(window, text("DEL_TITLE"), text("DEL_FINISHED_TEXT"))
        if (ret) {
            val (torrents, nonTorrents) = finished.partition { it.downloadType == DownloadType.Torrent }
            val (plugins, http) = nonTorrents.partition { it.downloadType == DownloadType.Plugin }
            for (t in torrents) {
                AppContext.torrentManager.removeTask(t.id, false)
                AppContext.db.removeItem(t.id)
            }
            for (p in plugins) {
                xdm.app.plugins.PluginDownloadBridge.deleteTask(p.id, false)
            }
            if (http.isNotEmpty()) {
                downloader.deleteDownloads(http.map { it.id }, false)
            }
            AppContext.app.downloadsRemovedInView()
        }
    }

    fun copyUrl(record: DbRecord) {
        val url = when (record.downloadType) {
            DownloadType.Http -> AppContext.taskInfoDB.getHttpTask(record.id)?.url
            DownloadType.Hls -> AppContext.taskInfoDB.getHlsTask(record.id)?.url
            DownloadType.Dash -> AppContext.taskInfoDB.getDashTask(record.id)?.url
            DownloadType.Batch -> AppContext.taskInfoDB.getBatchTask(record.id)?.origin
            DownloadType.Plugin -> xdm.app.plugins.PluginDownloadBridge.getTaskUrl(record.id)
            DownloadType.Torrent -> {
                val task = AppContext.torrentManager.getTask(record.id)
                when {
                    task != null && task.source is xdm.torrent.TorrentSource.MagnetUri -> (task.source as xdm.torrent.TorrentSource.MagnetUri).uri
                    task != null && task.infoHash.isNotBlank() -> "magnet:?xt=urn:btih:${task.infoHash}"
                    else -> AppContext.taskInfoDB.getTorrentTask(record.id)?.let {
                        if (it.sourceUri.startsWith("magnet:?", ignoreCase = true)) it.sourceUri else if (it.infoHash.isNotBlank()) "magnet:?xt=urn:btih:${it.infoHash}" else null
                    }
                }
            }
            else -> null
        }
        if (url != null) {
            setClipBoardText(url)
        }
    }
}
