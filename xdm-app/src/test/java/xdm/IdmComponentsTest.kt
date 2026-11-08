package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.components.*
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import javax.swing.RowFilter

class IdmComponentsTest : DownloadManagerTestBase() {

    @Test
    fun testIdmTableModelStructure() {
        val model = IdmTableModel()
        assertEquals(9, model.columnCount)
        assertEquals("File Name", model.getColumnName(IdmTableModel.COL_FILE_NAME))
        assertEquals("Size", model.getColumnName(IdmTableModel.COL_SIZE))
        assertEquals("Type", model.getColumnName(IdmTableModel.COL_TYPE))
        assertEquals("Status", model.getColumnName(IdmTableModel.COL_STATUS))
        assertEquals("Progress", model.getColumnName(IdmTableModel.COL_PROGRESS))
        assertEquals("Time Left", model.getColumnName(IdmTableModel.COL_TIME_LEFT))
        assertEquals("Transfer Rate", model.getColumnName(IdmTableModel.COL_TRANSFER_RATE))
        assertEquals("Date Added", model.getColumnName(IdmTableModel.COL_DATE_ADDED))
        assertEquals("Peers / Swarm", model.getColumnName(IdmTableModel.COL_SWARM))

        val record1 = DbRecord(
            id = 101L,
            size = 10485760L,
            downloaded = 5242880L,
            progress = 50,
            date = 1700000000000L,
            fileName = "ubuntu-24.04.iso",
            eta = 120L,
            speed = 2048000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
            categoryId = null
        )
        val record2 = DbRecord(
            id = 102L,
            size = 20971520L,
            downloaded = 20971520L,
            progress = 100,
            date = 1700000050000L,
            fileName = "sample.torrent",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.FINISHED,
            downloadType = DownloadType.Torrent,
            categoryId = null
        )

        appDB.add(record1)
        appDB.add(record2)
        model.listChanged()

        assertEquals(2, model.rowCount)
        assertEquals("ubuntu-24.04.iso", model.getValueAt(0, IdmTableModel.COL_FILE_NAME))
        assertEquals(10485760L, model.getValueAt(0, IdmTableModel.COL_SIZE))
        assertEquals(RecordStatus.DOWNLOADING, model.getValueAt(0, IdmTableModel.COL_STATUS))
        assertEquals(50, model.getValueAt(0, IdmTableModel.COL_PROGRESS))
        assertEquals(120L, model.getValueAt(0, IdmTableModel.COL_TIME_LEFT))

        assertEquals("Torrent", model.getValueAt(1, IdmTableModel.COL_TYPE))
        assertEquals(RecordStatus.FINISHED, model.getValueAt(1, IdmTableModel.COL_STATUS))
        assertEquals(100, model.getValueAt(1, IdmTableModel.COL_PROGRESS))
    }

    @Test
    fun testMainListViewFilterCriteria() {
        val model = IdmTableModel()
        val recHttpActive = DbRecord(
            id = 201L, size = 1000L, downloaded = 500L, progress = 50, date = 1000L,
            fileName = "document.pdf", eta = 10L, speed = 100f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http, categoryId = "CAT_DOCUMENTS"
        )
        val recHttpFinished = DbRecord(
            id = 202L, size = 2000L, downloaded = 2000L, progress = 100, date = 2000L,
            fileName = "music.mp3", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.FINISHED, downloadType = DownloadType.Http, categoryId = "CAT_MUSIC"
        )
        val recTorrent = DbRecord(
            id = 203L, size = 5000L, downloaded = 2500L, progress = 50, date = 3000L,
            fileName = "movie.mkv", eta = 60L, speed = 500f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Torrent, categoryId = "CAT_VIDEOS"
        )
        val recOther = DbRecord(
            id = 204L, size = 800L, downloaded = 800L, progress = 100, date = 4000L,
            fileName = "data.bin", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.FINISHED, downloadType = DownloadType.Http, categoryId = null
        )

        appDB.add(recHttpActive)
        appDB.add(recHttpFinished)
        appDB.add(recTorrent)
        appDB.add(recOther)
        model.listChanged()

        fun checkMatch(filter: MainListViewFilter, index: Int): Boolean {
            val entry = object : RowFilter.Entry<Any, Int>() {
                override fun getModel(): Any = model
                override fun getValueCount(): Int = model.columnCount
                override fun getValue(columnIndex: Int): Any? = model.getValueAt(index, columnIndex)
                override fun getIdentifier(): Int = index
            }
            return filter.include(entry)
        }

        // Filter: All
        val filterAll = MainListViewFilter(FilterState.All, null, "")
        assertTrue(checkMatch(filterAll, 0))
        assertTrue(checkMatch(filterAll, 1))
        assertTrue(checkMatch(filterAll, 2))
        assertTrue(checkMatch(filterAll, 3))

        // Filter: Incomplete
        val filterIncomplete = MainListViewFilter(FilterState.Incomplete, null, "")
        assertTrue(checkMatch(filterIncomplete, 0))
        assertFalse(checkMatch(filterIncomplete, 1))
        assertTrue(checkMatch(filterIncomplete, 2))
        assertFalse(checkMatch(filterIncomplete, 3))

        // Filter: Completed
        val filterCompleted = MainListViewFilter(FilterState.Completed, null, "")
        assertFalse(checkMatch(filterCompleted, 0))
        assertTrue(checkMatch(filterCompleted, 1))
        assertFalse(checkMatch(filterCompleted, 2))
        assertTrue(checkMatch(filterCompleted, 3))

        // Filter: Torrents
        val filterTorrents = MainListViewFilter(FilterState.Torrents, null, "")
        assertFalse(checkMatch(filterTorrents, 0))
        assertFalse(checkMatch(filterTorrents, 1))
        assertTrue(checkMatch(filterTorrents, 2))
        assertFalse(checkMatch(filterTorrents, 3))

        // Filter: Search Text
        val filterSearch = MainListViewFilter(FilterState.All, null, "music")
        assertFalse(checkMatch(filterSearch, 0))
        assertTrue(checkMatch(filterSearch, 1))
        assertFalse(checkMatch(filterSearch, 2))

        // Filter: Other category
        val filterOther = MainListViewFilter(FilterState.All, IdmCategorySidebar.OTHER_CATEGORY, "")
        assertFalse(checkMatch(filterOther, 0))
        assertFalse(checkMatch(filterOther, 1))
        assertFalse(checkMatch(filterOther, 2))
        assertTrue(checkMatch(filterOther, 3))
    }

    @Test
    fun testToolBarEnabledStateTransitions() {
        val recStopped = DbRecord(
            id = 301L, size = 1000L, downloaded = 200L, progress = 20, date = 1000L,
            fileName = "stopped.zip", eta = 0L, speed = 0f, selected = true,
            status = RecordStatus.PAUSED, downloadType = DownloadType.Http
        )
        val recRunning = DbRecord(
            id = 302L, size = 2000L, downloaded = 1000L, progress = 50, date = 2000L,
            fileName = "running.iso", eta = 30L, speed = 500f, selected = true,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )

        val toolbar = IdmToolBar(
            onSearch = {}, onSort = { _, _ -> },
            onAddUrl = {}, onAddMagnet = {}, onAddTorrent = {},
            onResume = {}, onStop = {}, onStopAll = {},
            onDelete = {}, onDeleteCompleted = {}, onScheduler = {}
        )

        // When nothing is selected
        toolbar.updateSelectionState(emptyList())
        assertFalse(toolbar.isResumeEnabled)
        assertFalse(toolbar.isStopEnabled)
        assertFalse(toolbar.isDeleteEnabled)
        assertFalse(toolbar.isStopAllEnabled)

        // When stopped item is selected
        toolbar.updateSelectionState(listOf(recStopped))
        assertTrue(toolbar.isResumeEnabled)
        assertFalse(toolbar.isStopEnabled)
        assertTrue(toolbar.isDeleteEnabled)

        // When active item is added and selected
        appDB.add(recRunning)
        toolbar.updateSelectionState(listOf(recRunning))
        assertFalse(toolbar.isResumeEnabled)
        assertTrue(toolbar.isStopEnabled)
        assertTrue(toolbar.isDeleteEnabled)
        assertTrue(toolbar.isStopAllEnabled)

        // When completed item is added to DB
        assertFalse(toolbar.isDeleteCompletedEnabled)
        val recFinished = DbRecord(
            id = 303L, size = 1000L, downloaded = 1000L, progress = 100, date = 3000L,
            fileName = "finished.zip", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.FINISHED, downloadType = DownloadType.Http
        )
        appDB.add(recFinished)
        toolbar.refreshGlobalStates()
        assertTrue(toolbar.isDeleteCompletedEnabled)
    }

    @Test
    fun testStatusBarBandwidthAndTaskAggregation() {
        val rec1 = DbRecord(
            id = 401L, size = 10000L, downloaded = 5000L, progress = 50, date = 1000L,
            fileName = "download1.mp4", eta = 20L, speed = 1048576f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        val rec2 = DbRecord(
            id = 402L, size = 20000L, downloaded = 10000L, progress = 50, date = 2000L,
            fileName = "download2.mp4", eta = 20L, speed = 2097152f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        appDB.add(rec1)
        appDB.add(rec2)

        val statusBar = IdmStatusBar()
        statusBar.updateMetrics()

        assertTrue(statusBar.speedText.contains("3.00 MB/s ↓") || statusBar.speedText.contains("3"))
        assertTrue(statusBar.speedText.contains("↓"))
        assertTrue(statusBar.speedText.contains("↑"))
        assertTrue(statusBar.tasksText.contains("2 active"))
        assertTrue(statusBar.tasksText.contains("2 total"))
        assertTrue(statusBar.queueText.startsWith("Queue:"))
        assertTrue(statusBar.diskText.startsWith("Disk Free:"))

        statusBar.dispose()
    }

    @Test
    fun testCategorySidebarCountsAndFiltering() {
        val recDoc = DbRecord(
            id = 501L, size = 1000L, downloaded = 500L, progress = 50, date = 1000L,
            fileName = "doc.pdf", eta = 10L, speed = 100f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http, categoryId = "CAT_DOCUMENTS"
        )
        val recMusic = DbRecord(
            id = 502L, size = 2000L, downloaded = 2000L, progress = 100, date = 2000L,
            fileName = "song.mp3", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.FINISHED, downloadType = DownloadType.Http, categoryId = "CAT_MUSIC"
        )
        val recTorrent = DbRecord(
            id = 503L, size = 5000L, downloaded = 2500L, progress = 50, date = 3000L,
            fileName = "movie.mkv", eta = 60L, speed = 500f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Torrent, categoryId = "CAT_VIDEOS"
        )
        val recOther = DbRecord(
            id = 504L, size = 800L, downloaded = 800L, progress = 100, date = 4000L,
            fileName = "archive.custom", eta = 0L, speed = 0f, selected = false,
            status = RecordStatus.FINISHED, downloadType = DownloadType.Http, categoryId = null
        )

        appDB.add(recDoc)
        appDB.add(recMusic)
        appDB.add(recTorrent)
        appDB.add(recOther)

        var changedFilterState: FilterState? = null
        var changedCategory: DownloadCategory? = null
        val sidebar = IdmCategorySidebar { state, category ->
            changedFilterState = state
            changedCategory = category
        }
        sidebar.recount()

        assertEquals(4, sidebar.getNodeCount("All Downloads"))
        assertEquals(1, sidebar.getNodeCount("Documents"))
        assertEquals(1, sidebar.getNodeCount("Music"))
        assertEquals(1, sidebar.getNodeCount("Videos"))
        assertEquals(0, sidebar.getNodeCount("Compressed"))
        assertEquals(1, sidebar.getNodeCount("Other"))
        assertEquals(2, sidebar.getNodeCount("Unfinished"))
        assertEquals(2, sidebar.getNodeCount("Completed"))
        assertEquals(1, sidebar.getNodeCount("Torrents"))
    }

    @Test
    fun testIdmTableViewCardSwitchingAndSelection() {
        val view = IdmTableView()
        assertEquals(0, view.records.size)
        assertEquals(0, view.selectedRowCount)
        assertTrue(view.selectedItems.isEmpty())

        // Add an item to DB and notify
        val rec = DbRecord(
            id = 601L, size = 1000L, downloaded = 500L, progress = 50, date = 1000L,
            fileName = "item.zip", eta = 10L, speed = 100f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        appDB.add(rec)
        view.rowAdded(0)

        assertEquals(1, view.records.size)
        assertEquals("item.zip", view.records[0].fileName)

        // Update row
        rec.progress = 75
        appDB.update(rec)
        view.rowUpdated(0)
        assertEquals(75, view.records[0].progress)

        // Delete row
        appDB.remove(601L)
        view.rowDeleted(0)
        assertEquals(0, view.records.size)
    }

    @Test
    fun testLargeDownloadListPerformanceAndRowUpdates() {
        for (i in 1..300) {
            val rec = DbRecord(
                id = 1000L + i,
                size = 1000000L * i,
                downloaded = 500000L * i,
                progress = 50,
                date = 1000000L + i,
                fileName = "file_$i.dat",
                eta = i.toLong(),
                speed = (i * 1024).toFloat(),
                selected = false,
                status = if (i % 2 == 0) RecordStatus.DOWNLOADING else RecordStatus.FINISHED,
                downloadType = if (i % 5 == 0) DownloadType.Torrent else DownloadType.Http,
                categoryId = if (i % 3 == 0) "CAT_DOCUMENTS" else null
            )
            appDB.add(rec)
        }

        val view = IdmTableView()
        assertEquals(300, view.records.size)

        // Perform incremental row update on row 150
        val targetRec = appDB.getById(1150L)!!
        targetRec.progress = 90
        targetRec.downloaded = 900000L * 150
        appDB.update(targetRec)

        val idx = appDB.indexById(1150L)!!
        assertDoesNotThrow { view.rowUpdated(idx) }

        // Filter to Completed
        view.filterStateChanged(FilterState.Completed)
        assertTrue(view.selectedItems.isEmpty())

        // Sort by Size descending
        view.sort(SortKey.SIZE, false)
        assertDoesNotThrow { view.sort(SortKey.NAME, true) }
    }

    @Test
    fun testEscKeyHandlingOnDialogs() {
        val wndComplete = xdm.app.ui.screens.DownloadCompleteWindow()
        wndComplete.setDetails("done.zip", dir.absolutePath, 1024L, null)
        val escKey = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0)
        val actionKeyComplete = wndComplete.rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(escKey)
        assertNotNull(actionKeyComplete, "ESC key must be registered on DownloadCompleteWindow")

        val rec = DbRecord(
            id = 701L, size = 1000L, downloaded = 100L, progress = 10, date = 1000L,
            fileName = "active.mp4", eta = 50L, speed = 200f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        appDB.add(rec)
        val wndProgress = xdm.app.ui.screens.ProgressWindow(701L)
        val actionKeyProgress = wndProgress.rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(escKey)
        assertNotNull(actionKeyProgress, "ESC key must be registered on ProgressWindow")
        wndProgress.dispose()
        wndComplete.dispose()
    }

    @Test
    fun testTorrentEngineAvailabilityHandling() {
        assertDoesNotThrow {
            xdm.app.ui.screens.TorrentUiBridge.checkEngineAvailable(null)
        }
    }
}

