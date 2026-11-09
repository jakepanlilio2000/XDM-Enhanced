package xdm.app.ui.components

import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.manager.getFileFolder
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.swing.JComponent
import javax.swing.TransferHandler

/**
 * Native drag-and-drop file transfer for completed downloads (Issue #1383).
 *
 * Allows completed files to be dragged from the download list to supported
 * file-manager drop targets (e.g. Windows Explorer, macOS Finder, desktop, folders).
 *
 * Safety requirements:
 * 1. Action is strictly COPY (`getSourceActions() == COPY`): prevents accidental move,
 *    accidental deletion, or external file managers moving files without updating XDM state.
 * 2. Incomplete or active downloads (DOWNLOADING, PAUSED, ASSEMBLING, READY, ERROR) are NEVER draggable.
 * 3. Unavailable / missing files (deleted externally from disk) are excluded gracefully.
 * 4. If no valid completed files exist on disk for the selection, drag initiation returns null.
 */
class DownloadListTransferHandler(
    private val selectedRecordsProvider: () -> List<DbRecord>
) : TransferHandler() {

    override fun getSourceActions(c: JComponent?): Int = COPY

    public override fun createTransferable(c: JComponent?): Transferable? {
        val records = selectedRecordsProvider()
        if (records.isEmpty()) return null

        val validFiles = records.filter { it.status == RecordStatus.FINISHED }
            .mapNotNull { rec ->
                val (fileName, folder) = getFileFolder(rec) ?: return@mapNotNull null
                if (fileName.isNullOrBlank() || folder.isNullOrBlank()) return@mapNotNull null
                val file = File(folder, fileName)
                if (file.exists() && file.isFile && file.canRead()) file else null
            }

        if (validFiles.isEmpty()) {
            Logger.info("Drag cancelled: no valid completed files exist on disk for selected records")
            return null
        }

        return FileListTransferable(validFiles)
    }

    class FileListTransferable(val files: List<File>) : Transferable {
        companion object {
            val FILE_LIST_FLAVOR: DataFlavor = DataFlavor.javaFileListFlavor
        }

        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(FILE_LIST_FLAVOR)

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
            flavor != null && flavor.equals(FILE_LIST_FLAVOR)

        override fun getTransferData(flavor: DataFlavor?): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return files
        }
    }
}

