package xdm

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
import xdm.torrent.LibtorrentEngine
import xdm.torrent.TorrentDownloadManager
import xdm.torrent.TorrentProgressSnapshot
import xdm.torrent.TorrentSource
import xdm.torrent.TorrentTaskState
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JTextField
import javax.swing.SwingUtilities

class Phase7AGuiVerificationTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @BeforeEach
    fun initBase() {
        xdm.app.I8N.loadTexts("en")
        AppContext.resetTorrentManagerForTest()
        AppContext.defaultDownloadFolder = dir.absolutePath
        artifactDir.mkdirs()
    }

    @org.junit.jupiter.api.AfterEach
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
    fun testNewTorrentDownloadWindowWithAndWithoutDn() {
        runOnEdt {
            val hash1 = "0123456789abcdef0123456789abcdef01234571"
            val magnetWithDn = "magnet:?xt=urn:btih:$hash1&dn=ArchLinux+2024.11.01+x86_64"
            val wndWithDn = NewTorrentDownloadWindow(TorrentSource.MagnetUri(magnetWithDn))
            val txtNameWithDn = findTextField(wndWithDn) { it.isEditable && it.text.contains("ArchLinux") }
            assertNotNull(txtNameWithDn, "txtName must contain the parsed dn")
            assertEquals("ArchLinux 2024.11.01 x86_64", txtNameWithDn!!.text)
            captureComponent(wndWithDn, 600, 420, File(artifactDir, "screenshot-phase7a-new-torrent-window-with-dn.png"))
            wndWithDn.dispose()

            val hash2 = "0123456789abcdef0123456789abcdef01234572"
            val magnetNoDn = "magnet:?xt=urn:btih:$hash2"
            val wndNoDn = NewTorrentDownloadWindow(TorrentSource.MagnetUri(magnetNoDn))
            val txtNameNoDn = findTextField(wndNoDn) { it.isEditable && it.text.startsWith("Fetching metadata") }
            assertNotNull(txtNameNoDn, "txtName must contain Fetching metadata when dn is absent")
            assertTrue(txtNameNoDn!!.text.startsWith("Fetching metadata"))
            captureComponent(wndNoDn, 600, 420, File(artifactDir, "screenshot-phase7a-new-torrent-window-no-dn.png"))
            wndNoDn.dispose()
        }
    }

    @Test
    fun testProgressWindowUpdatesOnMetadataArrival() {
        val taskId = 801L
        val hash = "0123456789abcdef0123456789abcdef01234573"
        val magnetUri = "magnet:?xt=urn:btih:$hash"
        val source = TorrentSource.MagnetUri(magnetUri)

        val engine = LibtorrentEngine.createStandalone()
        val manager = TorrentDownloadManager(engine = engine, configDir = dir, taskInfoDB = taskDB)
        val task = manager.createTask(id = taskId, source = source, saveDir = dir)
        TorrentUiBridge.attachTaskListener(task)

        val initialRec = DbRecord(
            id = taskId,
            size = 0L,
            downloaded = 0L,
            progress = 0,
            date = System.currentTimeMillis(),
            fileName = task.name,
            eta = 0L,
            speed = 0f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(initialRec)

        runOnEdt {
            val progressWnd = ProgressWindow(taskId)
            assertEquals("Fetching metadata (${hash.take(8)}...)", progressWnd.lblFileName.text)
            captureComponent(progressWnd, 580, 460, File(artifactDir, "screenshot-phase7a-progress-window-before-metadata.png"))

            // Now metadata arrives
            val resolvedName = "Ubuntu-24.04-desktop-amd64.iso"
            task.updateMetadata(resolvedName, null)

            // Update ProgressWindow with new snapshot
            val snapshot = TorrentProgressSnapshot(
                id = taskId,
                infoHash = hash,
                name = resolvedName,
                state = TorrentTaskState.DOWNLOADING,
                progressPercent = 15,
                progressFraction = 0.15f,
                downloadedBytes = 1_500_000_000L,
                totalBytes = 10_000_000_000L,
                downloadRate = 3_500_000,
                uploadRate = 120_000,
                numPeers = 25,
                numSeeds = 8,
                verifiedPieces = 150,
                totalPieces = 1000,
                etaSeconds = 2400L
            )
            progressWnd.updateTorrentProgress(snapshot)

            assertEquals(resolvedName, progressWnd.lblFileName.text)
            captureComponent(progressWnd, 580, 460, File(artifactDir, "screenshot-phase7a-progress-window-after-metadata.png"))
            progressWnd.dispose()
        }
    }

    @Test
    fun testViewsRenderResolvedTorrentName() {
        val taskId = 802L
        val resolvedName = "Kubuntu-24.04-desktop-amd64.iso"
        val rec = DbRecord(
            id = taskId,
            size = 4_500_000_000L,
            downloaded = 2_250_000_000L,
            progress = 50,
            date = System.currentTimeMillis(),
            fileName = resolvedName,
            eta = 600L,
            speed = 4_000_000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent
        )
        appDB.add(rec)

        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)

            // 1. Table View in AppWindow
            AppContext.config.listSelectionMode = ListSelectionMode.TABLE
            val winTable = AppWindow(logoImage(256), startHidden = true)
            winTable.applyListSelectionMode()
            (winTable.listView as? DownloadTableView)?.selectById(taskId)
            captureComponent(winTable, 1000, 600, File(artifactDir, "screenshot-phase7a-table-view-resolved.png"))
            winTable.dispose()

            // 2. Classic Cards in AppWindow
            AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
            val winCards = AppWindow(logoImage(256), startHidden = true)
            winCards.applyListSelectionMode()
            (winCards.listView as? ClassicListView)?.selectByIds(setOf(taskId))
            captureComponent(winCards, 1000, 600, File(artifactDir, "screenshot-phase7a-classic-cards-resolved.png"))
            winCards.dispose()
        }
    }

    private fun findTextField(root: Component, predicate: (JTextField) -> Boolean): JTextField? {
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur is JTextField && predicate(cur)) return cur
            if (cur is Container) {
                for (child in cur.components) {
                    queue.add(child)
                }
            }
        }
        return null
    }
}
