package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.components.SegmentDetailTable
import xdm.app.ui.components.SegmentVisualizationBar
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.ProgressWindow
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.web.SegmentProgress
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

class PhaseM1ConnectionVisualizationTest : DownloadManagerTestBase() {

    @BeforeEach
    fun setUpEnv() {
        xdm.app.I8N.loadTexts("en")
        AppContext.defaultDownloadFolder = dir.absolutePath
    }

    @AfterEach
    fun tearDownEnv() {
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
        }
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

    private fun createHttpRecord(id: Long, fileName: String, size: Long, downloaded: Long, prg: Int, status: RecordStatus = RecordStatus.DOWNLOADING): DbRecord {
        val rec = DbRecord(
            id = id,
            size = size,
            downloaded = downloaded,
            progress = prg,
            date = 1000L,
            fileName = fileName,
            eta = 5L,
            speed = 1_000_000f,
            selected = false,
            status = status,
            downloadType = DownloadType.Http
        )
        appDB.add(rec)
        taskDB.saveHttpTask(
            HttpDownloadTaskInfo(
                id = id,
                url = "https://example.com/$fileName",
                fileName = fileName,
                respectFileName = false,
                cookie = null,
                headers = null,
                origin = null,
                categoryId = null,
                targetFolder = dir.absolutePath,
                maxPiece = 8,
                authInfo = null,
                knownFileSize = size
            )
        )
        return rec
    }

    @Test
    fun testTableColumnsAndNoByteRange() {
        runOnEdt {
            val detailTable = SegmentDetailTable()
            val table = detailTable.table

            // 1. Must have exactly 3 visible columns: N., Downloaded, Info / Status
            assertEquals(3, table.columnCount, "Table must have exactly 3 visible columns")
            assertEquals("N.", table.getColumnName(0))
            assertEquals("Downloaded", table.getColumnName(1))
            assertEquals("Info / Status", table.getColumnName(2))

            // 2. Ensure "Byte Range" column is completely absent
            for (i in 0 until table.columnCount) {
                val header = table.getColumnName(i)
                assertFalse(header.contains("Range", ignoreCase = true), "Column $i ('$header') must not be Byte Range")
            }

            // 3. Backward compatibility fallback: querying column 3 returns Info / Status without throwing
            val segs = listOf(
                SegmentProgress(start = 0L, length = 1_000_000L, downloaded = 1_000_000L, id = 1L, status = "Complete")
            )
            detailTable.updateSegments(segs, 1_000_000L)
            assertEquals("1", table.getValueAt(0, 0))
            assertTrue(table.getValueAt(0, 1).toString().contains("100%"))
            assertEquals("Complete", table.getValueAt(0, 2))
            assertEquals("Complete", table.getValueAt(0, 3), "Legacy index 3 query must safely return status")
        }
    }

    @Test
    fun testSingleStreamAndNoRangeServer() {
        runOnEdt {
            val rec = createHttpRecord(801L, "stream.bin", 10_000_000L, 3_500_000L, 35)
            val wnd = ProgressWindow(rec.id)
            try {
                // Single connection worker
                val singleSeg = listOf(
                    SegmentProgress(start = 0L, length = 10_000_000L, downloaded = 3_500_000L, id = 1L, status = "Receiving data...")
                )
                wnd.updateProgress(
                    fileName = "stream.bin",
                    downloaded = 3_500_000L,
                    size = 10_000_000L,
                    speed = 500_000f,
                    eta = 13L,
                    prg = 35,
                    segData = singleSeg
                )

                assertTrue(wnd.segmentVisualizationBar.isVisible)
                assertTrue(wnd.segmentDetailTable.isVisible)
                assertEquals("Connections: 1", wnd.lblActiveConnections.text)

                val table = wnd.segmentDetailTable.table
                assertEquals(1, table.rowCount, "Single stream must show exactly 1 connection row")
                assertEquals("1", table.getValueAt(0, 0))
                assertTrue(table.getValueAt(0, 1).toString().contains("35%"))
                assertEquals("Receiving data...", table.getValueAt(0, 2))
            } finally {
                wnd.dispose()
            }
        }
    }

    @Test
    fun testMultiConnectionDynamicCountsAndConsistency() {
        runOnEdt {
            val rec = createHttpRecord(802L, "large.iso", 80_000_000L, 35_000_000L, 43)
            val wnd = ProgressWindow(rec.id)
            try {
                val segs = (0 until 8).map { i ->
                    val len = 10_000_000L
                    val dl = when (i) {
                        0, 1 -> 10_000_000L
                        2, 3 -> 5_000_000L
                        4, 5 -> 2_500_000L
                        else -> 0L
                    }
                    val st = when {
                        dl == len -> "Complete"
                        dl > 0 -> "Receiving data..."
                        else -> "Connecting..."
                    }
                    SegmentProgress(start = i * len, length = len, downloaded = dl, id = (i + 1).toLong(), status = st)
                }

                val totalDownloaded = segs.sumOf { it.downloaded }
                assertEquals(35_000_000L, totalDownloaded)

                wnd.updateProgress(
                    fileName = "large.iso",
                    downloaded = totalDownloaded,
                    size = 80_000_000L,
                    speed = 4_000_000f,
                    eta = 11L,
                    prg = 43,
                    segData = segs
                )

                assertEquals("Connections: 8", wnd.lblActiveConnections.text)
                val table = wnd.segmentDetailTable.table
                assertEquals(8, table.rowCount, "Must reflect all 8 connection workers")

                for (i in 0 until 8) {
                    assertEquals((i + 1).toString(), table.getValueAt(i, 0))
                    val dlText = table.getValueAt(i, 1).toString()
                    when (i) {
                        0, 1 -> assertTrue(dlText.contains("100%"))
                        2, 3 -> assertTrue(dlText.contains("50%"))
                        4, 5 -> assertTrue(dlText.contains("25%"))
                        else -> assertTrue(dlText.contains("0%"))
                    }
                }
            } finally {
                wnd.dispose()
            }
        }
    }

    @Test
    fun testWorkerStatesConnectingReceivingRetryingPausedCompletedFailed() {
        runOnEdt {
            val detailTable = SegmentDetailTable()
            val segs = listOf(
                SegmentProgress(start = 0L, length = 1_000_000L, downloaded = 1_000_000L, id = 1L, status = "Complete"),
                SegmentProgress(start = 1_000_000L, length = 1_000_000L, downloaded = 600_000L, id = 2L, status = "Receiving data..."),
                SegmentProgress(start = 2_000_000L, length = 1_000_000L, downloaded = 200_000L, id = 3L, status = "Retrying..."),
                SegmentProgress(start = 3_000_000L, length = 1_000_000L, downloaded = 100_000L, id = 4L, status = "Connecting..."),
                SegmentProgress(start = 4_000_000L, length = 1_000_000L, downloaded = 50_000L, id = 5L, status = "Paused"),
                SegmentProgress(start = 5_000_000L, length = 1_000_000L, downloaded = 0L, id = 6L, status = "Failed")
            )

            detailTable.updateSegments(segs, 6_000_000L)
            val table = detailTable.table
            assertEquals(6, table.rowCount)

            assertEquals("Complete", table.getValueAt(0, 2))
            assertEquals("Receiving data...", table.getValueAt(1, 2))
            assertEquals("Retrying...", table.getValueAt(2, 2))
            assertEquals("Connecting...", table.getValueAt(3, 2))
            assertEquals("Paused", table.getValueAt(4, 2))
            assertEquals("Failed", table.getValueAt(5, 2))
        }
    }

    @Test
    fun testPauseResumeSynchronization() {
        runOnEdt {
            val rec = createHttpRecord(803L, "video.mp4", 4_000_000L, 1_700_000L, 42)
            val wnd = ProgressWindow(rec.id)
            try {
                val segs = listOf(
                    SegmentProgress(start = 0L, length = 1_000_000L, downloaded = 1_000_000L, id = 1L, status = "Complete"),
                    SegmentProgress(start = 1_000_000L, length = 1_000_000L, downloaded = 500_000L, id = 2L, status = "Receiving data..."),
                    SegmentProgress(start = 2_000_000L, length = 1_000_000L, downloaded = 200_000L, id = 3L, status = "Receiving data..."),
                    SegmentProgress(start = 3_000_000L, length = 1_000_000L, downloaded = 0L, id = 4L, status = "Connecting...")
                )
                wnd.updateProgress("video.mp4", 1_700_000L, 4_000_000L, 1_000_000f, 2L, 42, segs)

                // 1. Pause download
                wnd.showPaused()

                // Details must remain visible and synchronized!
                assertTrue(wnd.segmentVisualizationBar.isVisible, "Segment strip must stay visible on pause")
                assertTrue(wnd.segmentDetailTable.isVisible, "Connection table must stay visible on pause")
                assertTrue(wnd.lblActiveConnections.text.contains("Paused"))

                val table = wnd.segmentDetailTable.table
                assertEquals(4, table.rowCount)
                assertEquals("Complete", table.getValueAt(0, 2), "Worker 1 was complete; must stay Complete")
                assertEquals("Paused", table.getValueAt(1, 2), "Worker 2 must reflect Paused")
                assertEquals("Paused", table.getValueAt(2, 2), "Worker 3 must reflect Paused")
                assertEquals("Paused", table.getValueAt(3, 2), "Worker 4 must reflect Paused")

                // 2. Resume download
                wnd.showResuming()
                assertTrue(wnd.lblActiveConnections.text.contains("Resuming"))
                assertEquals("Complete", table.getValueAt(0, 2))
                assertEquals("Connecting...", table.getValueAt(1, 2))

                // 3. Download completing
                wnd.showCompleted()
                assertTrue(wnd.segmentDetailTable.isVisible)
                assertTrue(wnd.lblActiveConnections.text.contains("Complete"))
                for (i in 0 until 4) {
                    assertEquals("Complete", table.getValueAt(i, 2))
                }
            } finally {
                wnd.dispose()
            }
        }
    }

    @Test
    fun testZeroConnectionsEmptyState() {
        runOnEdt {
            val detailTable = SegmentDetailTable()
            detailTable.updateSegments(emptyList(), 0L)
            assertEquals(0, detailTable.table.rowCount)

            val rec = createHttpRecord(804L, "empty.bin", 0L, 0L, 0)
            val wnd = ProgressWindow(rec.id)
            try {
                wnd.updateProgress("empty.bin", 0L, 0L, 0f, 0L, 0, emptyList())
                assertFalse(wnd.segmentVisualizationBar.isVisible)
                assertFalse(wnd.segmentDetailTable.isVisible)
                assertEquals("", wnd.lblActiveConnections.text)
            } finally {
                wnd.dispose()
            }
        }
    }

    @Test
    fun testUnknownLengthServerDownload() {
        runOnEdt {
            val detailTable = SegmentDetailTable()
            // Unknown file length (length = 0L, totalSize = -1L)
            val segs = listOf(
                SegmentProgress(start = 0L, length = 0L, downloaded = 1_500_000L, id = 1L, status = "Receiving data...")
            )
            detailTable.updateSegments(segs, -1L)
            val table = detailTable.table
            assertEquals(1, table.rowCount)
            assertEquals("1", table.getValueAt(0, 0))

            val dlText = table.getValueAt(0, 1).toString()
            // Must format downloaded bytes without fake percentage
            assertFalse(dlText.contains("%"), "Must not display fake percentage for unknown length")
            assertTrue(dlText.contains("1.4") || dlText.contains("1.5") || dlText.contains("MB") || dlText.contains("KB"))
            assertEquals("Receiving data...", table.getValueAt(0, 2))
        }
    }

    @Test
    fun testSelectionPreservationAcrossTicks() {
        runOnEdt {
            val detailTable = SegmentDetailTable()
            val segs = listOf(
                SegmentProgress(start = 0L, length = 1_000_000L, downloaded = 500_000L, id = 1L),
                SegmentProgress(start = 1_000_000L, length = 1_000_000L, downloaded = 200_000L, id = 2L),
                SegmentProgress(start = 2_000_000L, length = 1_000_000L, downloaded = 100_000L, id = 3L)
            )
            detailTable.updateSegments(segs, 3_000_000L)
            val table = detailTable.table
            assertEquals(3, table.rowCount)

            // User selects row 1
            table.setRowSelectionInterval(1, 1)
            assertEquals(1, table.selectedRow)

            // Re-render tick with updated bytes
            val nextSegs = listOf(
                SegmentProgress(start = 0L, length = 1_000_000L, downloaded = 600_000L, id = 1L),
                SegmentProgress(start = 1_000_000L, length = 1_000_000L, downloaded = 350_000L, id = 2L),
                SegmentProgress(start = 2_000_000L, length = 1_000_000L, downloaded = 150_000L, id = 3L)
            )
            detailTable.updateSegments(nextSegs, 3_000_000L)

            // Selection must be preserved
            assertEquals(1, table.selectedRow, "Selection on row 1 must be preserved across ticks")
        }
    }

    private fun captureComponent(comp: Component, width: Int, height: Int, file: File) {
        comp.setSize(width, height)
        comp.addNotify()
        comp.validate()
        val target = (comp as? java.awt.Window)?.let { (it as? RootPaneContainer)?.contentPane } ?: comp
        target.setSize(width, height)
        target.doLayout()
        target.validate()

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        target.paint(g)
        g.dispose()

        ImageIO.write(img, "png", file)
        assertTrue(file.exists() && file.length() > 2000, "Screenshot created: ${file.name} (${file.length()} bytes)")
    }

    @Test
    fun captureConnectionVisualScreenshots() {
        val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")
        artifactDir.mkdirs()

        val recId = 9910L
        val totalSize = 250_000_000L
        val downloaded = 130_000_000L
        val rec = createHttpRecord(recId, "ReleaseCandidate_Installer_v1.0.iso", totalSize, downloaded, 52)

        val segs = listOf(
            SegmentProgress(0L, 31_250_000L, 31_250_000L, 1L, "Complete"),
            SegmentProgress(31_250_000L, 31_250_000L, 31_250_000L, 2L, "Complete"),
            SegmentProgress(62_500_000L, 31_250_000L, 25_000_000L, 3L, "Receiving data..."),
            SegmentProgress(93_750_000L, 31_250_000L, 20_000_000L, 4L, "Receiving data..."),
            SegmentProgress(125_000_000L, 31_250_000L, 12_500_000L, 5L, "Receiving data..."),
            SegmentProgress(156_250_000L, 31_250_000L, 10_000_000L, 6L, "Receiving data..."),
            SegmentProgress(187_500_000L, 31_250_000L, 0L, 7L, "Connecting..."),
            SegmentProgress(218_750_000L, 31_250_000L, 0L, 8L, "Retrying...")
        )

        // Dark theme screenshot
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 4_280_000f, 28L, 52, segs)
            val darkFile = File(artifactDir, "screenshot-phase-m1-progress-window-connections-dark.png")
            captureComponent(wnd, 600, 480, darkFile)
            wnd.dispose()
        }

        // Light theme screenshot
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 4_280_000f, 28L, 52, segs)
            val lightFile = File(artifactDir, "screenshot-phase-m1-progress-window-connections-light.png")
            captureComponent(wnd, 600, 480, lightFile)
            wnd.dispose()
        }
    }
}
