package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.screens.DownloadCompleteWindow
import xdm.app.ui.screens.NewTorrentDownloadWindow
import xdm.app.ui.screens.ProgressWindow
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.web.SegmentProgress
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.*
import java.io.File
import javax.swing.JButton
import javax.swing.JTable

class DownloadDialogsAndProgressTest : DownloadManagerTestBase() {

    @org.junit.jupiter.api.BeforeEach
    fun initDialogs() {
        AppContext.defaultDownloadFolder = dir.absolutePath
    }

    @Test
    fun testNewTorrentDownloadWindowFileSelectionAndSize() {
        val file1 = TorrentFileEntry(0, "movie.mp4", 10000000L, true)
        val file2 = TorrentFileEntry(1, "subs.srt", 50000L, true)
        val file3 = TorrentFileEntry(2, "sample.nfo", 1000L, true)

        val metadata = TorrentMetadata(
            name = "TestMovie",
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            totalSize = 10051000L,
            pieceLength = 16384,
            numPieces = 614,
            files = listOf(file1, file2, file3)
        )

        val source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=TestMovie")
        val dialog = NewTorrentDownloadWindow(source, metadata)

        // Verify table and initial selection
        assertEquals(3, dialog.table.rowCount)
        assertEquals(true, dialog.table.getValueAt(0, 0))
        assertEquals(true, dialog.table.getValueAt(1, 0))
        assertEquals(true, dialog.table.getValueAt(2, 0))
        assertTrue(dialog.btnStart.isEnabled, "Start button must be enabled initially")
        assertTrue(dialog.btnQueue.isEnabled, "Queue button must be enabled initially")
        assertTrue(dialog.lblSummary.text.contains("3 of 3 files"))

        // Deselect first file (10,000,000 bytes)
        dialog.table.setValueAt(false, 0, 0)
        assertEquals(false, dialog.table.getValueAt(0, 0))
        assertTrue(dialog.lblSummary.text.contains("2 of 3 files"))
        assertTrue(dialog.btnStart.isEnabled)

        // Deselect remaining files
        dialog.table.setValueAt(false, 1, 0)
        dialog.table.setValueAt(false, 2, 0)
        assertEquals(false, dialog.table.getValueAt(1, 0))
        assertEquals(false, dialog.table.getValueAt(2, 0))
        assertTrue(dialog.lblSummary.text.contains("0 of 3 files"))
        assertFalse(dialog.btnStart.isEnabled, "Start button must be disabled when 0 files selected")
        assertFalse(dialog.btnQueue.isEnabled, "Queue button must be disabled when 0 files selected")

        dialog.dispose()
    }

    @Test
    fun testProgressWindowHttpUpdatesAndDetails() {
        val recHttp = DbRecord(
            id = 601L, size = 10000L, downloaded = 2000L, progress = 20, date = 1000L,
            fileName = "http_file.zip", eta = 16L, speed = 500f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        appDB.add(recHttp)

        val wnd = ProgressWindow(601L)

        // Verify initial state
        assertEquals("http_file.zip", wnd.lblFileName.text)
        assertEquals(RecordStatus.DOWNLOADING, appDB.getById(601L)?.status)

        // Update with real segments
        val segs = listOf(SegmentProgress(0L, 1000L, 2000L, 1L))
        wnd.updateProgress("http_file.zip", 2000L, 10000L, 500f, 16L, 20, segs)

        assertEquals(20, wnd.progressBar.value)
        assertEquals("20%", wnd.progressBar.string)
        assertTrue(wnd.lblStat3.text.contains("Transfer rate:") || wnd.lblStat3.text.contains("Speed:"))
        assertFalse(wnd.lblTorrentSwarm.isVisible, "Swarm label must be hidden for HTTP downloads")
        assertTrue(wnd.segPanel.isVisible, "Segment panel must be visible when segData is provided")

        // Clicking Open Folder should not throw even if the partial file is downloading
        assertDoesNotThrow { wnd.btnOpenFolder.doClick() }

        // Closing the progress window must NOT pause, cancel, or modify the download status
        wnd.dispose()
        assertEquals(RecordStatus.DOWNLOADING, appDB.getById(601L)?.status)
    }

    @Test
    fun testProgressWindowTorrentUpdatesAndSwarmDetails() {
        taskDB.saveTorrentTask(
            xdm.core.downloaders.TorrentDownloadTaskInfo(
                id = 602L,
                name = "Ubuntu.iso",
                targetFolder = dir.absolutePath,
                infoHash = "0123456789abcdef0123456789abcdef01234567",
                sourceType = 0,
                sourceUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                totalSize = 10000000L
            )
        )
        val recTorrent = DbRecord(
            id = 602L, size = 10000000L, downloaded = 4500000L, progress = 45, date = 2000L,
            fileName = "Ubuntu.iso", eta = 5L, speed = 1048576f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Torrent
        )
        appDB.add(recTorrent)

        val wnd = ProgressWindow(602L)

        val snapshot = TorrentProgressSnapshot(
            id = 602L,
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            name = "Ubuntu.iso",
            state = TorrentTaskState.DOWNLOADING,
            progressPercent = 45,
            progressFraction = 0.45f,
            downloadedBytes = 4500000L,
            totalBytes = 10000000L,
            downloadRate = 1048576,
            uploadRate = 65536,
            numPeers = 32,
            numSeeds = 18,
            verifiedPieces = 450,
            totalPieces = 1000,
            etaSeconds = 5L
        )

        wnd.updateTorrentProgress(snapshot)

        assertEquals(45, wnd.progressBar.value)
        assertEquals("45%", wnd.progressBar.string)
        assertTrue(wnd.lblTorrentSwarm.isVisible, "Swarm label must be visible for torrents")
        assertTrue(wnd.lblTorrentSwarm.text.contains("Peers: 32"))
        assertTrue(wnd.lblTorrentSwarm.text.contains("Seeds: 18"))
        assertTrue(wnd.lblTorrentSwarm.text.contains("450 / 1000 verified"))
        assertTrue(wnd.lblStat3.text.contains("↓") && wnd.lblStat3.text.contains("↑"))
        assertFalse(wnd.segPanel.isVisible, "Segment panel must remain hidden for torrents")

        assertNotNull(wnd.btnPauseResume, "Pause/Resume button must be present")
        assertNotNull(wnd.btnCancel, "Cancel button must be present")
        assertNotNull(wnd.btnOpenFolder, "Open Folder button must be present")
        assertNotNull(wnd.btnHide, "Hide button must be present")

        wnd.dispose()
    }

    @Test
    fun testDownloadCompleteWindowElements() {
        val wnd = DownloadCompleteWindow()
        wnd.setDetails("completed_file.mkv", dir.absolutePath, 52428800L, null)

        var hasOpenFile = false
        var hasOpenFolder = false
        var hasClose = false

        fun inspectButtons(comp: java.awt.Component) {
            if (comp is JButton) {
                val t = comp.text.orEmpty()
                if (t == text("CTX_OPEN_FILE")) hasOpenFile = true
                if (t == text("CTX_OPEN_FOLDER")) hasOpenFolder = true
                if (t == text("LBL_CLOSE")) hasClose = true
            }
            if (comp is java.awt.Container) {
                for (child in comp.components) inspectButtons(child)
            }
        }
        inspectButtons(wnd)

        assertTrue(hasOpenFile, "Open File button must be present")
        assertTrue(hasOpenFolder, "Open Folder button must be present")
        assertTrue(hasClose, "Close button must be present")

        wnd.dispose()
    }
}

