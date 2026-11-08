package xdm.app.ui.components

import xdm.app.AppContext
import xdm.core.downloaders.DownloadType
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import javax.swing.RowFilter

/** [filterCategory] is null for "All types". */
class MainListViewFilter(
    var filterState: FilterState,
    var filterCategory: DownloadCategory?,
    var searchText: String
) :
    RowFilter<Any, Int>() {
    override fun include(entry: Entry<out Any, out Int>): Boolean {
        val index = entry.identifier
        val model = entry.model
        val rec = when (model) {
            is MainListViewModel -> {
                if (index < 0 || index >= model.rowCount) return false
                model.getItemAt(index)
            }
            is IdmTableModel -> {
                if (index < 0 || index >= model.rowCount) return false
                model.getItemAt(index)
            }
            else -> null
        } ?: return false

        if (searchText.isNotBlank() && !rec.fileName.contains(searchText, ignoreCase = true)) {
            return false
        }
        return when (filterState) {
            FilterState.Incomplete -> {
                if (rec.status == RecordStatus.FINISHED) {
                    return false
                }
                matchCategory(rec.categoryId)
            }

            FilterState.Completed -> {
                if (rec.status != RecordStatus.FINISHED) {
                    return false
                }
                matchCategory(rec.categoryId)
            }

            FilterState.Torrents -> {
                if (rec.downloadType != DownloadType.Torrent) {
                    return false
                }
                matchCategory(rec.categoryId)
            }

            FilterState.Queues -> {
                if (rec.status != RecordStatus.READY && rec.status != RecordStatus.PAUSED) {
                    return false
                }
                matchCategory(rec.categoryId)
            }

            else -> matchCategory(rec.categoryId)
        }
    }

    /** A download is listed under the category it was saved with; None downloads only under All types. */
    private fun matchCategory(categoryId: String?): Boolean {
        val cat = filterCategory ?: return true
        if (cat.id == "__OTHER__") {
            return categoryId == null || !AppContext.config.categories.any { it.id == categoryId }
        }
        return cat.id == categoryId
    }
}
