package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.IAppInstance
import xdm.app.I8N.text
import xdm.app.ui.screens.ProgressWindow
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.PauseEvent
import xdm.core.downloaders.web.SegmentProgress
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.TorrentProgressSnapshot
import xdm.torrent.TorrentTaskState
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

class PersistentProgressDialogTest : DownloadManagerTestBase() {

    @Test
    fun testHttpDownloadPauseKeepsProgressWindowVisibleAndAccurateState() {
        val hiddenWindows = CopyOnWriteArrayList<Long>()
        val pausedWindows = CopyOnWriteArrayList<Long>()

        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, method, args ->
            when (method.name) {
                "hideProgressWindow" -> hiddenWindows.add(args[0] as Long)
                "downloadPaused" -> pausedWindows.add(args[0] as Long)
            }
            null
        } as IAppInstance

        val taskId = 801L
        val rec = DbRecord(
            id = taskId,
            size = 10_000_000L,
            downloaded = 4_500_000L,
            progress = 45,
            date = System.currentTimeMillis(),
            fileName = "archive.tar.gz",
            eta = 30L,
            speed = 500_000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        val wnd = ProgressWindow(taskId)

        // 1. Initial downloading state
        assertEquals("archive.tar.gz", wnd.lblFileName.text)
        assertEquals(ProgressWindow.DialogState.DOWNLOADING, wnd.dialogState)
        assertEquals(text("MENU_PAUSE"), wnd.btnPauseResume.text)
        assertTrue(wnd.btnPauseResume.isEnabled)

        // Feed an active segment update
        val segs = listOf(SegmentProgress(0L, 0L, 4_500_000L, 1L))
        wnd.updateProgress("archive.tar.gz", 4_500_000L, 10_000_000L, 500_000f, 30L, 45, segs)
        assertEquals(45, wnd.progressBar.value)
        assertEquals("45%", wnd.progressBar.string)
        assertTrue(wnd.lblStat3.text.contains("Transfer rate:"))
        assertTrue(wnd.lblStat4.text.contains("Time left:"))

        // 2. Click Pause: transitions to PAUSING immediately
        wnd.btnPauseResume.doClick()
        assertEquals(ProgressWindow.DialogState.PAUSING, wnd.dialogState)
        assertEquals("Pausing...", wnd.btnPauseResume.text)
        assertFalse(wnd.btnPauseResume.isEnabled, "Pause button must be disabled to prevent duplicate commands")
        assertEquals("Status: Pausing...", wnd.lblStat.text)

        // Simulate backend pause event via onDownloadPaused
        val host = host()
        val pauseEvent = PauseEvent.PausedByUser
        host.onDownloadPaused(taskId, pauseEvent)

        // Verify backend did NOT hide or dispose the progress window
        assertFalse(hiddenWindows.contains(taskId), "Backend onDownloadPaused must NOT call hideProgressWindow")

        // ProgressWindow showPaused confirms transition to PAUSED
        wnd.showPaused()
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)
        assertEquals("Status: " + text("STAT_PAUSED"), wnd.lblStat.text)
        assertEquals("Transfer rate: ---", wnd.lblStat3.text, "Stale transfer rate must be cleared")
        assertEquals("Time left: ---", wnd.lblStat4.text, "Stale ETA must be cleared")
        assertEquals(45, wnd.progressBar.value, "Progress bar must remain at last confirmed value")
        assertEquals("45%", wnd.progressBar.string)
        assertEquals(text("MENU_RESUME"), wnd.btnPauseResume.text, "Button must show Resume when paused")
        assertTrue(wnd.btnPauseResume.isEnabled, "Resume button must be enabled")
        assertTrue(wnd.btnCancel.isVisible)
        assertTrue(wnd.btnCancel.isEnabled)
        assertTrue(wnd.btnHide.isVisible)
        assertTrue(wnd.btnHide.isEnabled)
        assertFalse(wnd.segPanel.isVisible, "Segment panel must be hidden while paused")

        wnd.dispose()
    }

    @Test
    fun testResumeTransitionsToResumingAndThenDownloading() {
        val taskId = 802L
        val rec = DbRecord(
            id = taskId,
            size = 20_000_000L,
            downloaded = 10_000_000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "dataset.csv",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        val wnd = ProgressWindow(taskId)
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)
        assertEquals(text("MENU_RESUME"), wnd.btnPauseResume.text)

        // Click Resume: enters pending RESUMING state
        wnd.btnPauseResume.doClick()
        assertEquals(ProgressWindow.DialogState.RESUMING, wnd.dialogState)
        assertEquals("Resuming...", wnd.btnPauseResume.text)
        assertFalse(wnd.btnPauseResume.isEnabled, "Resume button must be disabled while resuming")
        assertEquals("Status: Resuming...", wnd.lblStat.text)
        assertEquals("Transfer rate: ---", wnd.lblStat3.text)

        // Active transfer confirmation arrives via updateProgress
        wnd.updateProgress("dataset.csv", 10_500_000L, 20_000_000L, 600_000f, 15L, 52, emptyList())
        assertEquals(ProgressWindow.DialogState.DOWNLOADING, wnd.dialogState)
        assertEquals(text("MENU_PAUSE"), wnd.btnPauseResume.text)
        assertTrue(wnd.btnPauseResume.isEnabled)
        assertEquals("Status: Receiving data...", wnd.lblStat.text)
        assertEquals(52, wnd.progressBar.value)
        assertTrue(wnd.lblStat3.text.contains("Transfer rate:"))

        wnd.dispose()
    }

    @Test
    fun testReopeningPausedProgressWindow() {
        val taskId = 803L
        val rec = DbRecord(
            id = taskId,
            size = 50_000_000L,
            downloaded = 35_000_000L,
            progress = 70,
            date = System.currentTimeMillis(),
            fileName = "installer.iso",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        // Open progress window for already paused download
        val wnd = ProgressWindow(taskId)

        assertEquals("installer.iso", wnd.lblFileName.text)
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)
        assertEquals("Status: " + text("STAT_PAUSED"), wnd.lblStat.text)
        assertEquals(70, wnd.progressBar.value, "Progress bar must load confirmed progress (70%)")
        assertEquals("70%", wnd.progressBar.string)
        assertEquals("70%", wnd.lblStat1.text)
        assertTrue(wnd.lblStat2.text.contains("70%"), "Downloaded label must include 70%")
        assertEquals("Transfer rate: ---", wnd.lblStat3.text)
        assertEquals("Time left: ---", wnd.lblStat4.text)
        assertEquals(text("MENU_RESUME"), wnd.btnPauseResume.text)
        assertTrue(wnd.btnPauseResume.isEnabled)
        assertTrue(wnd.btnCancel.isVisible)
        assertTrue(wnd.btnHide.isVisible)

        wnd.dispose()
    }

    @Test
    fun testTorrentPauseAndResumeProgressWindow() {
        val taskId = 804L
        val rec = DbRecord(
            id = taskId,
            size = 100_000_000L,
            downloaded = 25_000_000L,
            progress = 25,
            date = System.currentTimeMillis(),
            fileName = "linux_distro.iso",
            eta = 60L,
            speed = 1_000_000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent,
        )
        appDB.add(rec)

        val wnd = ProgressWindow(taskId)

        // Downloading snapshot
        val snapDownloading = TorrentProgressSnapshot(
            id = taskId,
            infoHash = "abcdef0123456789abcdef0123456789abcdef01",
            name = "linux_distro.iso",
            state = TorrentTaskState.DOWNLOADING,
            progressPercent = 25,
            progressFraction = 0.25f,
            downloadedBytes = 25_000_000L,
            totalBytes = 100_000_000L,
            downloadRate = 1_000_000,
            uploadRate = 50_000,
            numPeers = 20,
            numSeeds = 10,
            verifiedPieces = 250,
            totalPieces = 1000,
            etaSeconds = 75L,
        )
        wnd.updateTorrentProgress(snapDownloading)
        assertEquals(ProgressWindow.DialogState.DOWNLOADING, wnd.dialogState)
        assertEquals(text("MENU_PAUSE"), wnd.btnPauseResume.text)
        assertTrue(wnd.btnPauseResume.isEnabled)

        // User clicks Pause
        wnd.showPausing()
        assertEquals(ProgressWindow.DialogState.PAUSING, wnd.dialogState)
        assertEquals("Pausing...", wnd.btnPauseResume.text)
        assertFalse(wnd.btnPauseResume.isEnabled)

        // Paused snapshot arrives from backend
        val snapPaused = snapDownloading.copy(
            state = TorrentTaskState.PAUSED,
            downloadRate = 0,
            uploadRate = 0,
            etaSeconds = 0L,
        )
        wnd.updateTorrentProgress(snapPaused)
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)
        assertTrue(wnd.lblStat.text.contains("Paused"))
        assertEquals(25, wnd.progressBar.value)
        assertEquals("25%", wnd.progressBar.string)
        assertEquals("Transfer rate: ---", wnd.lblStat3.text)
        assertEquals("Time left: ---", wnd.lblStat4.text)
        assertEquals(text("MENU_RESUME"), wnd.btnPauseResume.text)
        assertTrue(wnd.btnPauseResume.isEnabled)

        wnd.dispose()
    }

    @Test
    fun testClosingProgressWindowDoesNotCancelDownload() {
        val taskId = 805L
        val rec = DbRecord(
            id = taskId,
            size = 15_000_000L,
            downloaded = 5_000_000L,
            progress = 33,
            date = System.currentTimeMillis(),
            fileName = "document.pdf",
            eta = 20L,
            speed = 250_000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        val wnd = ProgressWindow(taskId)
        assertEquals(RecordStatus.DOWNLOADING, appDB.getById(taskId)?.status)

        // Closing the progress window via hideWindow() or dispose()
        wnd.btnHide.doClick()

        // Verify DB record and status are completely unaffected
        val recordAfterClose = appDB.getById(taskId)
        assertNotNull(recordAfterClose)
        assertEquals(RecordStatus.DOWNLOADING, recordAfterClose!!.status, "Closing window must not cancel download")
        assertEquals(33, recordAfterClose.progress)
        assertEquals(5_000_000L, recordAfterClose.downloaded)
    }

    @Test
    fun testCompletedDownloadProgressWindowControls() {
        val taskId = 806L
        val rec = DbRecord(
            id = taskId,
            size = 80_000_000L,
            downloaded = 80_000_000L,
            progress = 100,
            date = System.currentTimeMillis(),
            fileName = "completed_video.mp4",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.FINISHED,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        val wnd = ProgressWindow(taskId)

        assertEquals(ProgressWindow.DialogState.COMPLETED, wnd.dialogState)
        assertEquals("Status: " + text("STAT_FINISHED"), wnd.lblStat.text)
        assertEquals(100, wnd.progressBar.value)
        assertEquals("100%", wnd.progressBar.string)

        // Completed controls: Open File / Open Folder / Close
        assertTrue(wnd.btnOpenFile.isVisible, "Open File button must be visible when completed")
        assertTrue(wnd.btnOpenFile.isEnabled)
        assertEquals(text("CTX_OPEN_FILE"), wnd.btnOpenFile.text)

        assertTrue(wnd.btnOpenFolder.isVisible, "Open Folder button must be visible when completed")
        assertTrue(wnd.btnOpenFolder.isEnabled)

        assertTrue(wnd.btnHide.isVisible, "Close button must be visible when completed")
        assertEquals(text("LBL_CLOSE"), wnd.btnHide.text)

        assertFalse(wnd.btnPauseResume.isVisible, "Pause/Resume button must be hidden when completed")
        assertFalse(wnd.btnCancel.isVisible, "Cancel button must be hidden when completed")

        wnd.dispose()
    }

    @Test
    fun testRecordUpdatedSynchronizesOpenProgressWindow() {
        val taskId = 807L
        val rec = DbRecord(
            id = taskId,
            size = 30_000_000L,
            downloaded = 10_000_000L,
            progress = 33,
            date = System.currentTimeMillis(),
            fileName = "source.tar",
            eta = 40L,
            speed = 400_000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        val wnd = ProgressWindow(taskId)
        assertEquals(ProgressWindow.DialogState.DOWNLOADING, wnd.dialogState)

        // An external trigger (e.g. main toolbar pause) updates DB record to PAUSED
        rec.status = RecordStatus.PAUSED
        rec.speed = 0f
        rec.eta = 0L
        appDB.update(rec)

        // onRecordUpdated is invoked by updateDownloadInView
        wnd.onRecordUpdated(rec)
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)
        assertEquals("Status: " + text("STAT_PAUSED"), wnd.lblStat.text)
        assertEquals("Transfer rate: ---", wnd.lblStat3.text)
        assertEquals(text("MENU_RESUME"), wnd.btnPauseResume.text)
        assertTrue(wnd.btnPauseResume.isEnabled)

        wnd.dispose()
    }

    @Test
    fun testInFlightProgressUpdateDoesNotUnpauseWindow() {
        val taskId = 808L
        val rec = DbRecord(
            id = taskId,
            size = 40_000_000L,
            downloaded = 20_000_000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "bundle.zip",
            eta = 20L,
            speed = 1_000_000f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        val wnd = ProgressWindow(taskId)
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)

        // Stale in-flight update arriving on EDT while window is PAUSED
        wnd.updateProgress("bundle.zip", 20_050_000L, 40_000_000L, 950_000f, 19L, 50, emptyList())

        // Dialog must remain strictly PAUSED and not revert to DOWNLOADING
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)
        assertEquals("Status: " + text("STAT_PAUSED"), wnd.lblStat.text)
        assertEquals("Transfer rate: ---", wnd.lblStat3.text)
        assertEquals("Time left: ---", wnd.lblStat4.text)
        assertEquals(text("MENU_RESUME"), wnd.btnPauseResume.text)

        wnd.dispose()
    }

    @Test
    fun testExternalResumeTransitionsPausedWindowToDownloading() {
        val taskId = 809L
        val rec = DbRecord(
            id = taskId,
            size = 60_000_000L,
            downloaded = 30_000_000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = "external_resume.zip",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.PAUSED,
            downloadType = DownloadType.Http,
        )
        appDB.add(rec)
        taskDB.saveHttpTask(httpTask(taskId))

        val wnd = ProgressWindow(taskId)
        assertEquals(ProgressWindow.DialogState.PAUSED, wnd.dialogState)

        // Record transitions to DOWNLOADING (e.g. user resumed from main toolbar)
        rec.status = RecordStatus.DOWNLOADING
        appDB.update(rec)
        wnd.onRecordUpdated(rec)

        assertEquals(ProgressWindow.DialogState.DOWNLOADING, wnd.dialogState)
        assertEquals(text("MENU_PAUSE"), wnd.btnPauseResume.text)
        assertTrue(wnd.btnPauseResume.isEnabled)

        wnd.dispose()
    }
}
