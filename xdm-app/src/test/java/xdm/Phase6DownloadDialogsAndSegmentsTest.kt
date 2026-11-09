package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.screens.DownloadCompleteWindow
import xdm.app.ui.screens.NewDownloadWindow
import xdm.app.ui.screens.ProgressWindow
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.web.SegmentProgress
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.io.File
import javax.swing.JTabbedPane

class Phase6DownloadDialogsAndSegmentsTest : DownloadManagerTestBase() {

    @BeforeEach
    fun initBase() {
        xdm.app.I8N.loadTexts("en")
        AppContext.defaultDownloadFolder = dir.absolutePath
    }

    @Test
    fun testProgressWindowHttpSegmentsAndTable() {
        val recHttp = DbRecord(
            id = 701L, size = 6_000_000L, downloaded = 1_700_000L, progress = 28, date = 1000L,
            fileName = "sample_video.mp4", eta = 3L, speed = 2_000_000f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        appDB.add(recHttp)
        taskDB.saveHttpTask(
            HttpDownloadTaskInfo(
                id = 701L,
                url = "https://example.com/video/sample_video.mp4",
                fileName = "sample_video.mp4",
                respectFileName = false,
                cookie = null,
                headers = null,
                origin = null,
                categoryId = null,
                targetFolder = dir.absolutePath,
                maxPiece = 6,
                authInfo = null,
                knownFileSize = 10_000_000L
            )
        )

        val wnd = ProgressWindow(701L)

        // 1. Verify basic info and fields
        assertEquals("sample_video.mp4", wnd.lblFileName.text)
        assertEquals("https://example.com/video/sample_video.mp4", wnd.lblUrlOrSource.text)
        assertTrue(wnd.lblResumeCap.text.contains("Yes"), "Resume capability should be Yes")

        // 2. Verify Tabbed Pane structure (Reference A)
        val tabbedPane = findTabbedPane(wnd)
        assertNotNull(tabbedPane, "ProgressWindow must contain a JTabbedPane")
        assertEquals(3, tabbedPane!!.tabCount, "Must have 3 tabs: Download status, Speed Limiter, Options on completion")
        assertEquals(text("TAB_DOWNLOAD_STATUS"), tabbedPane.getTitleAt(0))
        assertEquals(text("TAB_SPEED_LIMITER"), tabbedPane.getTitleAt(1))
        assertEquals(text("TAB_OPTIONS_COMPLETION"), tabbedPane.getTitleAt(2))

        // 3. Update with 6 real segments (Reference A)
        val segs = listOf(
            SegmentProgress(0L, 1_000_000L, 1_000_000L, 1L, "Complete"),
            SegmentProgress(1_000_000L, 1_000_000L, 500_000L, 2L, "Receiving data..."),
            SegmentProgress(2_000_000L, 1_000_000L, 200_000L, 3L, "Receiving data..."),
            SegmentProgress(3_000_000L, 1_000_000L, 0L, 4L, "Connecting..."),
            SegmentProgress(4_000_000L, 1_000_000L, 0L, 5L, "Ready"),
            SegmentProgress(5_000_000L, 1_000_000L, 0L, 6L, "Ready")
        )

        wnd.updateProgress("sample_video.mp4", 1_700_000L, 6_000_000L, 2_000_000f, 3L, 28, segs)

        // Progress bar and labels
        assertEquals(28, wnd.progressBar.value)
        assertEquals("28%", wnd.progressBar.string)
        assertTrue(wnd.lblStat3.text.contains("Transfer rate:") || wnd.lblStat3.text.contains("Speed:"))
        assertTrue(wnd.lblStat4.text.contains("Time left:") || wnd.lblStat4.text.contains("ETA:"))
        assertTrue(wnd.lblStat2.text.contains("Downloaded:"))

        // Segment visualization and table
        assertTrue(wnd.segmentVisualizationBar.isVisible, "Segment visualization bar must be visible")
        assertTrue(wnd.segmentDetailTable.isVisible, "Segment detail table must be visible")
        assertEquals(6, wnd.segmentDetailTable.table.rowCount, "Detail table must show 6 rows for 6 segments")

        // Validate table content
        val table = wnd.segmentDetailTable.table
        assertEquals("1", table.getValueAt(0, 0))
        assertTrue(table.getValueAt(0, 1).toString().contains("100%"))
        assertEquals("Complete", table.getValueAt(0, 3))

        assertEquals("2", table.getValueAt(1, 0))
        assertTrue(table.getValueAt(1, 1).toString().contains("50%"))
        assertEquals("Receiving data...", table.getValueAt(1, 3))

        // 4. Test Details toggle (Show/Hide Details)
        assertTrue(wnd.detailsVisible, "Details should be visible initially")
        assertEquals(text("DWN_HIDE_DETAILS"), wnd.btnDetailsToggle.text)

        wnd.toggleDetails()
        assertFalse(wnd.detailsVisible, "Details should be hidden after toggle")
        assertEquals(text("DWN_SHOW_DETAILS"), wnd.btnDetailsToggle.text)

        wnd.toggleDetails()
        assertTrue(wnd.detailsVisible, "Details should be restored after second toggle")
        assertEquals(text("DWN_HIDE_DETAILS"), wnd.btnDetailsToggle.text)

        // 5. Test Pause / Resume controls
        wnd.btnPauseResume.doClick()
        assertEquals(text("MENU_RESUME"), wnd.btnPauseResume.text)

        // In real app, the engine transitions rec.status to PAUSED upon pause
        recHttp.status = RecordStatus.PAUSED
        appDB.update(recHttp)

        wnd.btnPauseResume.doClick()
        assertEquals(text("MENU_PAUSE"), wnd.btnPauseResume.text)

        wnd.dispose()
    }

    @Test
    fun testProgressWindowUnknownLengthAndStreaming() {
        val recStream = DbRecord(
            id = 702L, size = -1L, downloaded = 500_000L, progress = 0, date = 1000L,
            fileName = "live_stream.ts", eta = 0L, speed = 150_000f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Hls
        )
        appDB.add(recStream)

        val wnd = ProgressWindow(702L)
        val segs = listOf(
            SegmentProgress(0L, 500_000L, 500_000L, 1L, "Complete"),
            SegmentProgress(500_000L, 500_000L, 100_000L, 2L, "Receiving data...")
        )

        assertDoesNotThrow {
            wnd.updateProgress("live_stream.ts", 600_000L, -1L, 150_000f, 0L, 0, segs)
        }

        assertTrue(wnd.lblStat2.text.contains("600.0 KB") || wnd.lblStat2.text.contains("585"))
        wnd.dispose()
    }

    @Test
    fun testNewDownloadWindowDescriptionAndAccessibleShortcuts() {
        val dlg = NewDownloadWindow()
        assertNotNull(dlg.txtDescription, "Description field must be present (Reference B)")
        dlg.txtDescription.text = "Custom project reference file"
        assertEquals("Custom project reference file", dlg.txtDescription.text)

        val task = HttpDownloadTaskInfo(
            id = 703L,
            url = "https://example.com/downloads/report.pdf",
            fileName = "report.pdf",
            respectFileName = true,
            cookie = null,
            headers = null,
            origin = null,
            categoryId = null,
            targetFolder = dir.absolutePath,
            maxPiece = 8,
            authInfo = null,
            knownFileSize = 205030L
        )

        dlg.showWindow(task)
        assertEquals(8, (dlg.spnSegments.value as Number).toInt(), "spnSegments should reflect task maxPiece")
        assertNotNull(dlg.rootPane.defaultButton, "Default button must be set on dialog")
        assertTrue(dlg.rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
            .allKeys().any { it.keyCode == java.awt.event.KeyEvent.VK_ESCAPE },
            "ESC key must be bound to close NewDownloadWindow")

        dlg.dispose()
    }

    @Test
    fun testDownloadCompleteWindowWithSourceUrlAndOpenWith() {
        val wnd = DownloadCompleteWindow()
        wnd.setDetails("manual.pdf", dir.absolutePath, 209953L, null, "https://example.com/docs/manual.pdf")

        // 1. Verify byte size formatting
        assertTrue(wnd.lblFileSize.text.contains("209,953 Bytes"), "Exact bytes count must be displayed")
        assertTrue(wnd.lblFileSize.text.contains("Downloaded"), "Must indicate Downloaded status")

        // 2. Verify all action buttons
        assertNotNull(wnd.btnOpenFile, "Open File button must be present")
        assertNotNull(wnd.btnOpenWith, "Open With button must be present")
        assertNotNull(wnd.btnOpenFolder, "Open Folder button must be present")
        assertNotNull(wnd.btnClose, "Close button must be present")
        assertNotNull(wnd.chkDontShowAgain, "Don't show this dialog again checkbox must be present")

        // 3. Verify checkbox persistence
        val initialPref = AppContext.config.showDownloadCompleteWindow
        wnd.chkDontShowAgain.isSelected = true
        wnd.chkDontShowAgain.actionListeners.forEach { it.actionPerformed(java.awt.event.ActionEvent(wnd.chkDontShowAgain, 0, "")) }
        assertFalse(AppContext.config.showDownloadCompleteWindow, "showDownloadCompleteWindow should be false when checked")

        wnd.chkDontShowAgain.isSelected = false
        wnd.chkDontShowAgain.actionListeners.forEach { it.actionPerformed(java.awt.event.ActionEvent(wnd.chkDontShowAgain, 0, "")) }
        assertTrue(AppContext.config.showDownloadCompleteWindow, "showDownloadCompleteWindow should be true when unchecked")

        AppContext.config.showDownloadCompleteWindow = initialPref
        wnd.dispose()
    }

    @Test
    fun testSegmentVisualizationBarTooltipsAndDividers() {
        val bar = xdm.app.ui.components.SegmentVisualizationBar()
        bar.setSize(400, 24)

        val segs = listOf(
            SegmentProgress(0L, 250_000L, 250_000L, 1L, "Complete"),
            SegmentProgress(250_000L, 250_000L, 125_000L, 2L, "Receiving data..."),
            SegmentProgress(500_000L, 250_000L, 50_000L, 3L, "Receiving data..."),
            SegmentProgress(750_000L, 250_000L, 0L, 4L, "Connecting...")
        )
        bar.setValues(segs)

        // Mouse at x=50 (Segment 1: 0..100)
        val eventSeg1 = java.awt.event.MouseEvent(bar, java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 50, 10, 0, false)
        val tipSeg1 = bar.getToolTipText(eventSeg1)
        assertNotNull(tipSeg1)
        assertTrue(tipSeg1!!.contains("Segment #1"), "Tooltip should indicate Segment #1")
        assertTrue(tipSeg1.contains("100%"), "Tooltip should show 100% progress for completed segment")

        // Mouse at x=150 (Segment 2: 100..200)
        val eventSeg2 = java.awt.event.MouseEvent(bar, java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 150, 10, 0, false)
        val tipSeg2 = bar.getToolTipText(eventSeg2)
        assertNotNull(tipSeg2)
        assertTrue(tipSeg2!!.contains("Segment #2"), "Tooltip should indicate Segment #2")
        assertTrue(tipSeg2.contains("50%"), "Tooltip should show 50% progress for half-completed segment")

        // Test rendering without throwing
        val img = java.awt.image.BufferedImage(400, 24, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        assertDoesNotThrow { bar.paint(g2) }
        g2.dispose()
    }

    @Test
    fun testSpeedLimiterAndOptionsTabsPersistence() {
        val rec = DbRecord(
            id = 705L, size = 1_000_000L, downloaded = 100_000L, progress = 10, date = 1000L,
            fileName = "limiter_test.zip", eta = 10L, speed = 100_000f, selected = false,
            status = RecordStatus.DOWNLOADING, downloadType = DownloadType.Http
        )
        appDB.add(rec)

        val wnd = ProgressWindow(705L)
        val tabbedPane = findTabbedPane(wnd)!!
        assertEquals(3, tabbedPane.tabCount)

        // Speed limiter config
        val initialLimit = AppContext.config.speedLimit
        val initialLimiterEnabled = AppContext.config.speedLimiterEnabled

        AppContext.config.speedLimiterEnabled = true
        AppContext.config.speedLimit = 500
        assertEquals(true, AppContext.config.speedLimiterEnabled)
        assertEquals(500, AppContext.config.speedLimit)

        // Restore
        AppContext.config.speedLimiterEnabled = initialLimiterEnabled
        AppContext.config.speedLimit = initialLimit

        // Test Sound option in completion tab
        val initialSound = AppContext.config.playSound
        wnd.chkPlaySound.isSelected = !initialSound
        wnd.chkPlaySound.actionListeners.forEach { it.actionPerformed(java.awt.event.ActionEvent(wnd.chkPlaySound, 0, "")) }
        assertEquals(!initialSound, AppContext.config.playSound)

        AppContext.config.playSound = initialSound
        wnd.chkPlaySound.isSelected = initialSound
        wnd.chkPlaySound.actionListeners.forEach { it.actionPerformed(java.awt.event.ActionEvent(wnd.chkPlaySound, 0, "")) }

        wnd.dispose()
    }

    private fun findTabbedPane(comp: java.awt.Component): JTabbedPane? {
        if (comp is JTabbedPane) return comp
        if (comp is java.awt.Container) {
            for (child in comp.components) {
                val found = findTabbedPane(child)
                if (found != null) return found
            }
        }
        return null
    }
}

