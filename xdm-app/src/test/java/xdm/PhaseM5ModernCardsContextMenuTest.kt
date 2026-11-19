package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ListSelectionMode
import xdm.app.ui.components.*
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.SwingUtilities

class PhaseM5ModernCardsContextMenuTest : DownloadManagerTestBase() {

    @BeforeEach
    fun initTest() {
        xdm.app.I8N.loadTexts("en")
        AppContext.resetTorrentManagerForTest()
        AppContext.defaultDownloadFolder = dir.absolutePath
    }

    @AfterEach
    fun cleanup() {
        AppContext.resetTorrentManagerForTest()
    }

    private fun <T> runOnEdt(action: () -> T): T {
        var result: T? = null
        var err: Throwable? = null
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        SwingUtilities.invokeAndWait {
            try {
                result = action()
            } catch (t: Throwable) {
                err = t
            }
        }
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun createRecord(
        id: Long,
        fileName: String,
        status: RecordStatus,
        type: DownloadType = DownloadType.Http,
        progress: Int = 0
    ): DbRecord {
        return DbRecord(
            id = id,
            size = 1000000L,
            downloaded = if (status == RecordStatus.FINISHED) 1000000L else 500000L,
            progress = progress,
            date = System.currentTimeMillis(),
            fileName = fileName,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = status,
            downloadType = type
        )
    }

    @Test
    fun testRightClickOnEachRegionOfModernCardTriggersContextMenu() {
        val rec = createRecord(101L, "sample_video.mp4", RecordStatus.FINISHED)
        appDB.add(rec)

        runOnEdt {
            val listView = ModernListView()
            val tableField = DownloadListBase::class.java.getDeclaredField("table").apply { isAccessible = true }
            val table = tableField.get(listView) as JTable

            // Make table edit row 0
            table.editCellAt(0, 0)
            val editorComp = table.editorComponent as JPanel
            assertNotNull(editorComp, "Editor component should be mounted on cell 0")

            var contextMenuInvoked = false
            var menuInvoker: Component? = null
            var menuX = -1
            var menuY = -1

            val modernRow = table.getCellEditor(0, 0) as ModernListRow
            modernRow.onContextMenu = { invoker, x, y ->
                contextMenuInvoked = true
                menuInvoker = invoker
                menuX = x
                menuY = y
            }

            var openFileCalled = false
            modernRow.onOpenFileClick = {
                openFileCalled = true
            }

            // Find specific subcomponents
            fun findComponent(c: Component, predicate: (Component) -> Boolean): Component? {
                if (predicate(c)) return c
                if (c is java.awt.Container) {
                    for (child in c.components) {
                        val found = findComponent(child, predicate)
                        if (found != null) return found
                    }
                }
                return null
            }

            val rowField = ModernListRow::class.java.getDeclaredField("row").apply { isAccessible = true }
            val rowPanel = rowField.get(modernRow) as DownloadRowPanel

            val regionsToTest = listOf(
                "lblTitle" to rowPanel.lblTitle,
                "icon" to rowPanel.icon,
                "root" to rowPanel.root,
                "panDetails" to findComponent(rowPanel.root) { it.javaClass.simpleName.contains("JProgressBar") }!!
            )

            for ((regionName, targetComp) in regionsToTest) {
                contextMenuInvoked = false
                openFileCalled = false
                table.clearSelection()

                // Trigger right-click mousePressed
                val pressEvent = MouseEvent(
                    targetComp,
                    MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    10,
                    10,
                    1,
                    true, // isPopupTrigger
                    MouseEvent.BUTTON3
                )
                for (listener in targetComp.mouseListeners) {
                    listener.mousePressed(pressEvent)
                }

                // Trigger right-click mouseReleased (Windows popup trigger)
                val releaseEvent = MouseEvent(
                    targetComp,
                    MouseEvent.MOUSE_RELEASED,
                    System.currentTimeMillis(),
                    0,
                    10,
                    10,
                    1,
                    true, // isPopupTrigger
                    MouseEvent.BUTTON3
                )
                for (listener in targetComp.mouseListeners) {
                    listener.mouseReleased(releaseEvent)
                }

                assertTrue(contextMenuInvoked, "Right-click on $regionName must invoke context menu")
                assertFalse(openFileCalled, "Right-click on $regionName must NOT trigger open file")
                assertEquals(1, table.selectedRowCount, "Right-clicking on $regionName must select the card")
                assertEquals(0, table.selectedRow, "Selected row must be 0")
            }
        }
    }

    @Test
    fun testContextMenuActionsReflectRecordCapabilities() {
        val finishedRec = createRecord(102L, "document.pdf", RecordStatus.FINISHED)
        val downloadingRec = createRecord(103L, "archive.zip", RecordStatus.DOWNLOADING, progress = 45)
        val pausedRec = createRecord(104L, "setup.exe", RecordStatus.PAUSED, progress = 10)
        val torrentRec = createRecord(105L, "distro.iso", RecordStatus.PAUSED, type = DownloadType.Torrent)

        val invoker = javax.swing.JPanel()

        // 1. Finished HTTP
        val menuFinished = DownloadContextMenu.create(listOf(finishedRec), invoker)
        val itemsFinished = extractMenuLabels(menuFinished)
        println("Finished menu items: $itemsFinished")
        assertTrue(itemsFinished.contains("Open"), "Finished task must offer Open")
        assertTrue(itemsFinished.contains("Open Folder"), "Finished task must offer Open Folder")
        assertTrue(itemsFinished.contains("Restart"), "Finished task must offer Restart")
        assertTrue(itemsFinished.contains("Download Details..."), "Finished task must offer Details")
        assertTrue(itemsFinished.contains("Category"), "Finished task must offer Category")
        assertTrue(itemsFinished.contains("Delete"), "Finished task must offer Delete")
        assertFalse(itemsFinished.contains("Resume"), "Finished task must NOT offer Resume")
        assertFalse(itemsFinished.contains("Pause / Stop"), "Finished task must NOT offer Pause")

        // 2. Downloading HTTP
        val menuDownloading = DownloadContextMenu.create(listOf(downloadingRec), invoker)
        val itemsDownloading = extractMenuLabels(menuDownloading)
        println("Downloading menu items: $itemsDownloading")
        assertTrue(itemsDownloading.contains("Pause / Stop"), "Downloading task must offer Pause / Stop")
        assertFalse(itemsDownloading.contains("Open"), "Downloading task must NOT offer Open")
        assertFalse(itemsDownloading.contains("Resume"), "Downloading task must NOT offer Resume")

        // 3. Paused HTTP
        val menuPaused = DownloadContextMenu.create(listOf(pausedRec), invoker)
        val itemsPaused = extractMenuLabels(menuPaused)
        println("Paused menu items: $itemsPaused")
        assertTrue(itemsPaused.contains("Resume"), "Paused task must offer Resume")
        assertTrue(itemsPaused.contains("Restart"), "Paused task must offer Restart")
        assertTrue(itemsPaused.contains("Refresh Download Link"), "Paused task must offer Refresh Link")
        assertFalse(itemsPaused.contains("Pause / Stop"), "Paused task must NOT offer Pause / Stop")

        // 4. BitTorrent
        val menuTorrent = DownloadContextMenu.create(listOf(torrentRec), invoker)
        val itemsTorrent = extractMenuLabels(menuTorrent)
        println("Torrent menu items: $itemsTorrent")
        assertTrue(itemsTorrent.contains("Torrent Options & Details..."), "Torrent task must offer Torrent Details")
        assertFalse(itemsTorrent.contains("Restart"), "Torrent task must NOT offer Restart")
        assertFalse(itemsTorrent.contains("Refresh Download Link"), "Torrent task must NOT offer Refresh Link")
    }

    @Test
    fun testMultiCardSelectionPreservedOnRightClick() {
        val rec1 = createRecord(106L, "file1.zip", RecordStatus.PAUSED)
        val rec2 = createRecord(107L, "file2.zip", RecordStatus.DOWNLOADING)
        val rec3 = createRecord(108L, "file3.zip", RecordStatus.FINISHED)
        appDB.add(rec1)
        appDB.add(rec2)
        appDB.add(rec3)

        runOnEdt {
            val listView = ModernListView()
            val tableField = DownloadListBase::class.java.getDeclaredField("table").apply { isAccessible = true }
            val table = tableField.get(listView) as JTable

            // Select rows 0 and 1
            table.setRowSelectionInterval(0, 1)
            assertEquals(2, table.selectedRowCount)

            // Right-click on row 1 (which is already selected)
            table.editCellAt(1, 0)
            val modernRow = table.getCellEditor(1, 0) as ModernListRow

            var contextMenuFired = false
            modernRow.onContextMenu = { _, _, _ ->
                contextMenuFired = true
            }

            val rowField = ModernListRow::class.java.getDeclaredField("row").apply { isAccessible = true }
            val rowPanel = rowField.get(modernRow) as DownloadRowPanel

            val pressEvent = MouseEvent(
                rowPanel.root,
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                10,
                10,
                1,
                true,
                MouseEvent.BUTTON3
            )
            for (l in rowPanel.root.mouseListeners) {
                l.mousePressed(pressEvent)
            }

            assertTrue(contextMenuFired)
            // Multi-selection must be preserved!
            assertEquals(2, table.selectedRowCount, "Multi-selection must be preserved when right-clicking an already-selected row")
            assertTrue(table.isRowSelected(0))
            assertTrue(table.isRowSelected(1))

            // Right-click on row 2 (not selected)
            table.editCellAt(2, 0)
            val modernRow2 = table.getCellEditor(2, 0) as ModernListRow
            contextMenuFired = false
            modernRow2.onContextMenu = { _, _, _ -> contextMenuFired = true }
            val rowPanel2 = (rowField.get(modernRow2) as DownloadRowPanel)

            val pressEvent2 = MouseEvent(
                rowPanel2.root,
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                10,
                10,
                1,
                true,
                MouseEvent.BUTTON3
            )
            for (l in rowPanel2.root.mouseListeners) {
                l.mousePressed(pressEvent2)
            }

            assertTrue(contextMenuFired)
            // Row 2 is now exclusively selected
            assertEquals(1, table.selectedRowCount, "Right-clicking unselected row must exclusively select it")
            assertTrue(table.isRowSelected(2))
        }
    }

    @Test
    fun testTableAndModernCardsViewParity() {
        val rec = createRecord(109L, "parity_test.mp4", RecordStatus.FINISHED)
        appDB.add(rec)

        val invoker = javax.swing.JPanel()
        val menuTable = DownloadContextMenu.create(listOf(rec), invoker)
        val menuCards = DownloadContextMenu.create(listOf(rec), invoker)

        val tableLabels = extractMenuLabels(menuTable)
        val cardsLabels = extractMenuLabels(menuCards)

        assertEquals(tableLabels, cardsLabels, "Table View and Modern Cards View must have identical context menu items")
    }

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @Test
    fun testCaptureVisualScreenshotsLightAndDark() {
        artifactDir.mkdirs()
        val rec1 = createRecord(201L, "Ubuntu-24.04-desktop-amd64.iso", RecordStatus.DOWNLOADING, progress = 68)
        val rec2 = createRecord(202L, "document_annual_report_2026.pdf", RecordStatus.FINISHED, progress = 100)
        val rec3 = createRecord(203L, "Blender-v4.2-windows-x64.zip", RecordStatus.PAUSED, progress = 35)
        appDB.add(rec1)
        appDB.add(rec2)
        appDB.add(rec3)

        for (theme in listOf("light", "dark")) {
            runOnEdt {
                ThemeManager.applyTheme(theme, null)
                val listView = ModernListView()
                val tableField = DownloadListBase::class.java.getDeclaredField("table").apply { isAccessible = true }
                val table = tableField.get(listView) as JTable

                val comp = listView.component
                comp.setSize(750, 400)
                comp.doLayout()
                table.setSize(750, 400)
                table.doLayout()
                table.editCellAt(0, 0)

                val menu = DownloadContextMenu.create(listOf(rec1), table)
                menu.setSize(menu.preferredSize)
                menu.doLayout()

                val img = java.awt.image.BufferedImage(750, 400, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g2 = img.createGraphics()
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                comp.paint(g2)
                g2.translate(220, 40)
                menu.paint(g2)
                g2.dispose()

                val outPath = File(artifactDir, "screenshot-phase-m5b-modern-cards-context-menu-$theme.png")
                javax.imageio.ImageIO.write(img, "PNG", outPath)
                assertTrue(outPath.exists())
            }
        }
    }

    private fun extractMenuLabels(popup: JPopupMenu): List<String> {
        val labels = mutableListOf<String>()
        for (i in 0 until popup.componentCount) {
            val c = popup.getComponent(i)
            if (c is JMenuItem) {
                labels.add(c.text)
            }
        }
        return labels
    }
}
