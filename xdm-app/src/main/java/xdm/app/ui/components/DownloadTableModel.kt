package xdm.app.ui.components

import xdm.app.AppContext
import xdm.app.ListChangeListener
import xdm.core.downloaders.DownloadType
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import javax.swing.table.AbstractTableModel

class DownloadTableModel : AbstractTableModel(), ListChangeListener {

    companion object {
        val COLUMN_NAMES = arrayOf(
            "File Name",
            "Size",
            "Type",
            "Status",
            "Progress",
            "Time Left",
            "Transfer Rate",
            "Date Added",
            "Peers / Swarm"
        )

        const val COL_FILE_NAME = 0
        const val COL_SIZE = 1
        const val COL_TYPE = 2
        const val COL_STATUS = 3
        const val COL_PROGRESS = 4
        const val COL_TIME_LEFT = 5
        const val COL_TRANSFER_RATE = 6
        const val COL_DATE_ADDED = 7
        const val COL_SWARM = 8
    }

    private var rows = AppContext.db.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getRowCount(): Int = rows

    override fun getColumnName(column: Int): String = COLUMN_NAMES[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        COL_FILE_NAME -> String::class.java
        COL_SIZE -> java.lang.Long::class.java
        COL_TYPE -> String::class.java
        COL_STATUS -> RecordStatus::class.java
        COL_PROGRESS -> java.lang.Integer::class.java
        COL_TIME_LEFT -> java.lang.Long::class.java
        COL_TRANSFER_RATE -> java.lang.Float::class.java
        COL_DATE_ADDED -> java.lang.Long::class.java
        COL_SWARM -> String::class.java
        else -> Any::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    fun getItemAt(index: Int): DbRecord? = AppContext.db.getOrNull(index)

    fun findRowById(id: Long): Int = AppContext.db.indexById(id) ?: -1

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val rec = getItemAt(rowIndex) ?: return null
        return when (columnIndex) {
            COL_FILE_NAME -> rec.fileName
            COL_SIZE -> rec.size
            COL_TYPE -> resolveType(rec)
            COL_STATUS -> rec.status
            COL_PROGRESS -> rec.progress
            COL_TIME_LEFT -> rec.eta
            COL_TRANSFER_RATE -> rec.speed
            COL_DATE_ADDED -> rec.date
            COL_SWARM -> resolveSwarm(rec)
            else -> null
        }
    }

    private fun resolveType(rec: DbRecord): String {
        if (rec.downloadType == DownloadType.Torrent) return "Torrent"
        val categoryId = rec.categoryId
        if (categoryId != null) {
            val cat = AppContext.config.categories.firstOrNull { it.id == categoryId }
            if (cat != null) return cat.name
        }
        val ext = rec.fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("mp4", "mkv", "avi", "mov", "flv", "webm", "ts") -> "Video"
            ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a") -> "Audio"
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2") -> "Compressed"
            ext in setOf("pdf", "doc", "docx", "txt", "epub") -> "Document"
            ext in setOf("exe", "msi", "apk", "jar", "deb", "rpm") -> "Program"
            else -> "Other"
        }
    }

    private fun resolveSwarm(rec: DbRecord): String {
        if (rec.downloadType != DownloadType.Torrent) return "--"
        return try {
            val task = AppContext.torrentManager.getTask(rec.id)
            if (task != null) {
                val snap = task.getSnapshot()
                "P: ${snap.numPeers} | S: ${snap.numSeeds}"
            } else {
                "--"
            }
        } catch (_: Throwable) {
            "--"
        }
    }

    override fun listChanged() {
        val newRows = AppContext.db.size
        rows = newRows
        fireTableDataChanged()
    }

    override fun listItemUpdated(id: Long) {
        val index = AppContext.db.indexById(id)
        if (index != null && index < rows) {
            fireTableRowsUpdated(index, index)
        }
    }

    override fun fireTableChanged(e: javax.swing.event.TableModelEvent) {
        val size = AppContext.db.size
        val count = e.lastRow - e.firstRow + 1
        val event = when {
            e.firstRow == javax.swing.event.TableModelEvent.HEADER_ROW -> {
                rows = size
                e
            }
            e.type == javax.swing.event.TableModelEvent.INSERT && e.firstRow == rows && rows + count <= size -> {
                rows += count
                e
            }
            e.type == javax.swing.event.TableModelEvent.UPDATE && e.lastRow != Int.MAX_VALUE && e.lastRow < rows -> e
            else -> {
                rows = size
                javax.swing.event.TableModelEvent(this)
            }
        }
        super.fireTableChanged(event)
    }
}

