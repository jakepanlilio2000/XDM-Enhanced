package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ListSelectionMode
import xdm.app.ui.components.ClassicListView
import xdm.app.ui.components.DownloadTableView
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.AppWindow
import xdm.app.ui.screens.NewTorrentDownloadWindow
import xdm.app.ui.screens.ProgressWindow
import xdm.app.ui.screens.TorrentUiBridge
import xdm.app.utils.logoImage
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import xdm.torrent.*
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class Phase7BExistingTorrentRecheckTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @BeforeEach
    fun initBase() {
        xdm.app.I8N.loadTexts("en")
        AppContext.resetTorrentManagerForTest()
        AppContext.defaultDownloadFolder = dir.absolutePath
        artifactDir.mkdirs()
    }

    @AfterEach
    fun cleanUp() {
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

    private fun captureComponent(comp: Component, width: Int, height: Int, file: File) {
        comp.setSize(width, height)
        comp.addNotify()
        comp.validate()
        val target = (comp as? java.awt.Window)?.let { (it as? javax.swing.RootPaneContainer)?.contentPane } ?: comp
        target.setSize(width, height)
        target.doLayout()
        target.validate()

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            target.paint(g)
        } finally {
            g.dispose()
        }
        ImageIO.write(img, "PNG", file)
    }

    @Test
    fun testNewTorrentWindowExistingFileDetectionAndWarning() {
        val destFolder = File(dir, "dest_check_test").apply { mkdirs() }
        val testExistingFile = File(destFolder, "sample_document.pdf").apply {
            writeBytes(ByteArray(1024 * 64) { 0x5A })
        }
        AppContext.config.defaultDownloadFolder = destFolder.absolutePath

        val meta = TorrentMetadata(
            name = "sample_document.pdf",
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            totalSize = 1024 * 128,
            pieceLength = 32 * 1024,
            numPieces = 4,
            files = listOf(TorrentFileEntry(0, "sample_document.pdf", 1024 * 128))
        )

        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val window = NewTorrentDownloadWindow(
                source = TorrentSource.TorrentBytes(ByteArray(10), "sample_document.pdf"),
                initialMetadata = meta,
                initialSaveDir = destFolder
            )
            try {
                // Assert detection panel is visible because sample_document.pdf exists
                assertTrue(window.pnlExistingData.isVisible, "pnlExistingData should be visible when existing file found")
                assertTrue(window.chkVerifyExisting.isSelected, "chkVerifyExisting should be checked by default")
                assertTrue(window.lblExistingDetails.text.contains("Found 1 of 1 files"), "Should detail found files: ${window.lblExistingDetails.text}")
                assertTrue(window.lblExistingDetails.text.contains("64.0 KB / 128.0 KB"), "Should show disk size: ${window.lblExistingDetails.text}")

                // Capture UI screenshot
                captureComponent(window, 740, 520, File(artifactDir, "screenshot-phase7b-new-torrent-window-existing-detected.png"))

                // Now test when folder has NO matching files
                val emptyFolder = File(dir, "empty_dest").apply { mkdirs() }
                window.txtFolder.text = emptyFolder.absolutePath
                assertFalse(window.pnlExistingData.isVisible, "pnlExistingData should be hidden when saveDir has no files")
            } finally {
                window.dispose()
            }
        }
    }

    @Test
    fun testExistingFileDetectionPathTraversalSecurity() {
        val safeDir = File(dir, "safe_target").apply { mkdirs() }
        val maliciousMeta = TorrentMetadata(
            name = "malicious_torrent",
            infoHash = "aabbccddeeff00112233445566778899aabbccdd",
            totalSize = 1024,
            pieceLength = 1024,
            numPieces = 1,
            files = listOf(
                TorrentFileEntry(0, "../../windows/system32/cmd.exe", 1024)
            )
        )

        val checkResult = TorrentExistingDataDetector.detect(safeDir, maliciousMeta)
        assertTrue(checkResult.hasUnsafePaths, "Should detect path traversal attempt")
        assertFalse(checkResult.hasExistingData, "Must not flag malicious path as valid existing data")
        assertEquals(0, checkResult.existingFilesCount)
    }

    @Test
    fun testProgressWindowCheckingStateDisplay() {
        val taskId = 7001L
        val rec = DbRecord(
            id = taskId,
            size = 4000000000L,
            downloaded = 1400000000L,
            progress = 35,
            date = System.currentTimeMillis(),
            fileName = "Ubuntu-24.04-Desktop.iso",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        val snapshot = TorrentProgressSnapshot(
            id = taskId,
            infoHash = "11223344556677889900aabbccddeeff11223344",
            name = "Ubuntu-24.04-Desktop.iso",
            state = TorrentTaskState.CHECKING_FILES,
            progressPercent = 35,
            progressFraction = 0.35f,
            downloadedBytes = 1400000000L,
            totalBytes = 4000000000L,
            downloadRate = 0,
            uploadRate = 0,
            numPeers = 0,
            numSeeds = 0,
            verifiedPieces = 140,
            totalPieces = 400,
            etaSeconds = 0,
            errorMessage = null,
            totalUploadBytes = 0,
            shareRatio = 0.0f,
            seedingDurationSeconds = 0,
            queuePosition = 0,
            isPrivate = false,
            checkingProgressPercent = 42
        )

        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val pw = ProgressWindow(taskId)
            try {
                pw.updateTorrentProgress(snapshot)

                // Verify checking display texts
                assertTrue(pw.lblStat.text.contains("Checking existing files (42%)"), "Status must reflect checking files: ${pw.lblStat.text}")
                assertEquals("42%", pw.lblStat1.text, "Stat1 should show verification progress percent")
                assertTrue(pw.lblStat2.text.contains("1.3") && pw.lblStat2.text.contains("35% recovered"), "Stat2 must show verified bytes and recovered percent: ${pw.lblStat2.text}")
                assertEquals("Checking 42%", pw.progressBar.string, "Progress bar string must show Checking percent")
                assertEquals(42, pw.progressBar.value, "Progress bar value must be checking progress")

                // Capture ProgressWindow screenshot
                captureComponent(pw, 580, 480, File(artifactDir, "screenshot-phase7b-progress-window-checking-state.png"))
            } finally {
                pw.dispose()
            }
        }
    }

    @Test
    fun testClassicCardAndTableViewCheckingRender() {
        val taskId = 7002L
        val rec = DbRecord(
            id = taskId,
            size = 4000000000L,
            downloaded = 2400000000L,
            progress = 60,
            date = System.currentTimeMillis(),
            fileName = "Debian-12-DVD.iso",
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = dir, taskInfoDB = taskDB)
        AppContext.torrentManager = manager

        val mockTask = manager.createTask(
            id = taskId,
            source = TorrentSource.TorrentBytes(ByteArray(10), "Debian-12-DVD.iso"),
            saveDir = dir,
            verifyExisting = true
        )
        mockTask.updateState(TorrentTaskState.CHECKING_FILES)

        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)

            // 1. Table View in AppWindow
            AppContext.config.listSelectionMode = ListSelectionMode.TABLE
            val winTable = AppWindow(logoImage(256), startHidden = true)
            winTable.applyListSelectionMode()
            (winTable.listView as? DownloadTableView)?.selectById(taskId)
            captureComponent(winTable, 1000, 600, File(artifactDir, "screenshot-phase7b-table-view-checking.png"))
            winTable.dispose()

            // 2. Classic Cards View in AppWindow
            AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
            val winCards = AppWindow(logoImage(256), startHidden = true)
            winCards.applyListSelectionMode()
            captureComponent(winCards, 1000, 600, File(artifactDir, "screenshot-phase7b-classic-cards-checking.png"))
            winCards.dispose()
        }
    }
}
