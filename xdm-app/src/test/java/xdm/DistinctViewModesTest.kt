package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.*
import xdm.app.ui.components.*
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.manager.*
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import javax.swing.RowSorter
import javax.swing.SortOrder

class DistinctViewModesTest {
    private lateinit var tempDir: File
    private lateinit var config: AppConfig
    private lateinit var db: AppDB

    private fun record(
        id: Long,
        name: String,
        categoryId: String? = null,
        status: RecordStatus = RecordStatus.DOWNLOADING,
        size: Long = 1000L,
        downloaded: Long = 500L,
        progress: Int = 50,
        speed: Float = 1024f,
        eta: Long = 60L,
        date: Long = System.currentTimeMillis(),
        downloadType: DownloadType = DownloadType.Http
    ): DbRecord = DbRecord(
        id = id,
        size = size,
        downloaded = downloaded,
        progress = progress,
        date = date,
        fileName = name,
        eta = eta,
        speed = speed,
        selected = false,
        status = status,
        downloadType = downloadType
    ).apply {
        this.categoryId = categoryId
    }

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("xdm-view-modes-test").toFile()
        config = AppConfig(tempDir.absolutePath).apply {
            defaultDownloadFolder = tempDir.absolutePath
            categories = DownloadCategory.defaults(tempDir.absolutePath)
            listSelectionMode = ListSelectionMode.CLASSIC
        }
        AppContext.config = config
        AppContext.configDir = tempDir.absolutePath
        AppContext.taskInfoDB = TaskInfoDB(tempDir.absolutePath)
        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, _, _ -> null } as IAppInstance
        EngineContext.host = DesktopEngineHost

        db = AppDB(File(tempDir, "downloads.db").absolutePath)
        AppContext.db = db
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testViewModesAreDistinct() {
        val filter = MainListViewFilter(FilterState.All, null, "")
        val tableView = DownloadTableView(filter)
        val classicView = ClassicListView(filter)

        // Must be distinct types
        assertNotEquals(tableView::class, classicView::class, "TableView and ClassicView must be distinct classes")
        assertNotEquals(tableView.component, classicView.component, "Components must be distinct")

        // Table View must have compact rows (~26px) and a table header with 9 columns
        val table = findJTable(tableView.component)
        assertNotNull(table, "TableView must contain a JTable")
        assertEquals(26, table!!.rowHeight, "TableView must have compact 26px rows")
        assertNotNull(table.tableHeader, "TableView must have a table header")
        assertEquals(9, table.columnModel.columnCount, "TableView must have 9 columns")

        // Classic Cards must have generous row height (115px) and no table headers
        val classicTable = findJTable(classicView.component)
        assertNotNull(classicTable, "ClassicView must contain a JTable")
        assertEquals(115, classicTable!!.rowHeight, "Classic Cards must have generous 115px row height")
        assertNull(classicTable.tableHeader, "Classic Cards must not display table headers")
    }

    @Test
    fun testDownloadTableViewColumnVisibility() {
        val filter = MainListViewFilter(FilterState.All, null, "")
        val tableView = DownloadTableView(filter)

        // All 9 columns visible initially
        for (i in 0 until 9) {
            assertTrue(tableView.isColumnVisible(i), "Column $i should be visible initially")
        }

        // Hide column 1 (COL_SIZE)
        tableView.setColumnVisible(DownloadTableModel.COL_SIZE, false)
        assertFalse(tableView.isColumnVisible(DownloadTableModel.COL_SIZE), "Size column should now be hidden")
        val table = findJTable(tableView.component)!!
        assertEquals(8, table.columnModel.columnCount, "Table column count should be 8 after hiding one")

        // Hide column 4 (COL_PROGRESS)
        tableView.setColumnVisible(DownloadTableModel.COL_PROGRESS, false)
        assertFalse(tableView.isColumnVisible(DownloadTableModel.COL_PROGRESS), "Progress column should now be hidden")
        assertEquals(7, table.columnModel.columnCount, "Table column count should be 7 after hiding two")

        // Cannot hide COL_FILE_NAME (COL 0)
        tableView.setColumnVisible(DownloadTableModel.COL_FILE_NAME, false)
        assertTrue(tableView.isColumnVisible(DownloadTableModel.COL_FILE_NAME), "File Name column must remain visible")
        assertEquals(7, table.columnModel.columnCount, "Table column count should not change when trying to hide File Name")

        // Restore column 1 (COL_SIZE)
        tableView.setColumnVisible(DownloadTableModel.COL_SIZE, true)
        assertTrue(tableView.isColumnVisible(DownloadTableModel.COL_SIZE), "Size column should be restored")
        assertEquals(8, table.columnModel.columnCount, "Table column count should be 8 after restore")

        // Verify config persistence
        assertTrue(config.tableHiddenColumns.contains(DownloadTableModel.COL_PROGRESS.toString()),
            "Hidden columns should be saved in config")
    }

    @Test
    fun testDownloadTableViewNumericSorting() {
        val r1 = record(1L, "small.zip", size = 20_000L) // 20 KB
        val r2 = record(2L, "medium.iso", size = 100_000_000L) // 100 MB
        val r3 = record(3L, "huge.mkv", size = 5_000_000_000L) // 5 GB
        db.add(r1)
        db.add(r2)
        db.add(r3)

        val filter = MainListViewFilter(FilterState.All, null, "")
        val tableView = DownloadTableView(filter)
        val table = findJTable(tableView.component)!!
        val sorter = table.rowSorter!!

        // Sort by COL_SIZE ascending
        sorter.sortKeys = listOf(RowSorter.SortKey(DownloadTableModel.COL_SIZE, SortOrder.ASCENDING))

        // First row must be small (20 KB), second medium (100 MB), third huge (5 GB)
        val m0 = table.convertRowIndexToModel(0)
        val m1 = table.convertRowIndexToModel(1)
        val m2 = table.convertRowIndexToModel(2)

        assertEquals("small.zip", (table.model as DownloadTableModel).getItemAt(m0)?.fileName)
        assertEquals("medium.iso", (table.model as DownloadTableModel).getItemAt(m1)?.fileName)
        assertEquals("huge.mkv", (table.model as DownloadTableModel).getItemAt(m2)?.fileName)
    }

    @Test
    fun testClassicCardPanelRendering() {
        val panel = ClassicCardPanel()
        val rec = record(
            10L,
            "Document.pdf",
            categoryId = "CAT_DOCUMENTS",
            status = RecordStatus.DOWNLOADING,
            size = 10_000_000L,
            downloaded = 4_500_000L,
            progress = 45,
            speed = 2_000_000f,
            eta = 12L,
            downloadType = DownloadType.Http
        )

        panel.update(rec, isSelected = true, isHovered = false)

        assertEquals("Document.pdf", panel.lblTitle.text)
        assertEquals("HTTP", panel.lblTypeBadge.text)
        assertEquals("Documents", panel.lblCategoryBadge.text)
        assertTrue(panel.lblStatusPill.text.contains("Downloading 45%"))
        assertEquals(45, panel.prg.value)
        assertTrue(panel.lblDownloaded.text.contains("Downloaded:"))
        assertTrue(panel.lblSpeed.text.contains("Speed:"))
        assertTrue(panel.lblEta.text.contains("ETA:"))
        assertEquals("45%", panel.lblProgressPercent.text)
        assertTrue(panel.container.selected, "Container must reflect selected state")

        // Buttons
        assertTrue(panel.btnPause.isVisible, "Pause button should be visible for active download")
        assertFalse(panel.btnResume.isVisible, "Resume button should be hidden for active download")
        assertTrue(panel.btnOpenFolder.isVisible)
        assertTrue(panel.btnDelete.isVisible)
        assertTrue(panel.btnMenu.isVisible)
    }

    @Test
    fun testSharedModelAndRowUpdates() {
        val r1 = record(101L, "video.mp4", status = RecordStatus.DOWNLOADING, progress = 10)
        val r2 = record(102L, "music.mp3", status = RecordStatus.PAUSED, progress = 50)
        db.add(r1)
        db.add(r2)

        val filter = MainListViewFilter(FilterState.All, null, "")
        val tableView = DownloadTableView(filter)
        val classicView = ClassicListView(filter)

        assertEquals(2, tableView.records.size, "TableView must see 2 records from shared db")
        assertEquals(2, classicView.records.size, "ClassicView must see 2 records from shared db")

        // Update record in db
        r1.progress = 85
        r1.status = RecordStatus.FINISHED
        tableView.rowUpdated(0)
        classicView.rowUpdated(0)

        assertEquals(RecordStatus.FINISHED, tableView.records[0].status)
        assertEquals(85, tableView.records[0].progress)
        assertEquals(RecordStatus.FINISHED, classicView.records[0].status)
        assertEquals(85, classicView.records[0].progress)
    }

    @Test
    fun testPreserveSelectionAcrossViews() {
        val r1 = record(201L, "file1.zip")
        val r2 = record(202L, "file2.zip")
        val r3 = record(203L, "file3.zip")
        db.add(r1)
        db.add(r2)
        db.add(r3)

        val filter = MainListViewFilter(FilterState.All, null, "")
        val tableView = DownloadTableView(filter)
        val classicView = ClassicListView(filter)

        // Select items in TableView
        tableView.selectByIds(setOf(201L, 203L))
        val selectedInTable = tableView.selectedItems.map { it.id }.toSet()
        assertEquals(setOf(201L, 203L), selectedInTable, "TableView must select 201 and 203")

        // Transfer selection to ClassicView
        classicView.selectByIds(selectedInTable)
        val selectedInClassic = classicView.selectedItems.map { it.id }.toSet()
        assertEquals(setOf(201L, 203L), selectedInClassic, "ClassicView must select 201 and 203")
    }

    @Test
    fun testConfigPersistence() {
        config.listSelectionMode = ListSelectionMode.TABLE
        config.save()

        val reloaded1 = AppConfig(tempDir.absolutePath).apply { load() }
        assertEquals(ListSelectionMode.TABLE, reloaded1.listSelectionMode)

        config.listSelectionMode = ListSelectionMode.CLASSIC
        config.save()

        val reloaded2 = AppConfig(tempDir.absolutePath).apply { load() }
        assertEquals(ListSelectionMode.CLASSIC, reloaded2.listSelectionMode)
    }

    private fun findJTable(comp: java.awt.Component): javax.swing.JTable? {
        if (comp is javax.swing.JTable) return comp
        if (comp is java.awt.Container) {
            for (child in comp.components) {
                val found = findJTable(child)
                if (found != null) return found
            }
        }
        return null
    }
}

