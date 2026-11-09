package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
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
import javax.swing.SwingUtilities

class PhaseB3VisualScreenshotTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @BeforeEach
    fun initBase() {
        xdm.app.I8N.loadTexts("en")
        AppContext.defaultDownloadFolder = dir.absolutePath
        artifactDir.mkdirs()
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
        target.paint(g)
        g.dispose()

        ImageIO.write(img, "png", file)
        assertTrue(file.exists() && file.length() > 2000, "Screenshot created: ${file.name} (${file.length()} bytes)")
    }

    @Test
    fun capturePersistentProgressDialogStates() {
        val recId = 991L
        val totalSize = 524_288_000L // 500 MB
        val downloaded = 314_572_800L // 60%
        val rec = DbRecord(
            id = recId,
            size = totalSize,
            downloaded = downloaded,
            progress = 60,
            date = System.currentTimeMillis() - 150_000,
            fileName = "ubuntu-24.04-desktop-amd64.iso",
            eta = 42L,
            speed = 5_000_000f, // 5 MB/s
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
            categoryId = "CAT_COMPRESSED"
        )
        appDB.add(rec)
        taskDB.saveHttpTask(
            HttpDownloadTaskInfo(
                id = recId,
                url = "https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso",
                fileName = "ubuntu-24.04-desktop-amd64.iso",
                respectFileName = true,
                cookie = null,
                headers = null,
                origin = null,
                categoryId = "CAT_COMPRESSED",
                targetFolder = dir.absolutePath,
                maxPiece = 8,
                authInfo = null,
                knownFileSize = totalSize
            )
        )

        val segs = listOf(
            SegmentProgress(0L, 65_536_000L, 65_536_000L, 1L, "Complete"),
            SegmentProgress(65_536_000L, 65_536_000L, 65_536_000L, 2L, "Complete"),
            SegmentProgress(131_072_000L, 65_536_000L, 65_536_000L, 3L, "Complete"),
            SegmentProgress(196_608_000L, 65_536_000L, 50_000_000L, 4L, "Receiving data..."),
            SegmentProgress(262_144_000L, 65_536_000L, 40_000_000L, 5L, "Receiving data..."),
            SegmentProgress(327_680_000L, 65_536_000L, 28_000_000L, 6L, "Receiving data..."),
            SegmentProgress(393_216_000L, 65_536_000L, 0L, 7L, "Connecting..."),
            SegmentProgress(458_752_000L, 65_536_000L, 0L, 8L, "Ready")
        )

        // 1. Downloading state (Dark theme)
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 5_000_000f, 42L, 60, segs)
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-downloading-dark.png"))
            wnd.dispose()
        }

        // 2. Pausing state (Dark theme)
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 5_000_000f, 42L, 60, segs)
            wnd.showPausing()
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-pausing-dark.png"))
            wnd.dispose()
        }

        // 3. Paused state (Dark theme)
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 5_000_000f, 42L, 60, segs)
            wnd.showPaused()
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-paused-dark.png"))
            wnd.dispose()
        }

        // 4. Paused state (Light theme)
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 5_000_000f, 42L, 60, segs)
            wnd.showPaused()
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-paused-light.png"))
            wnd.dispose()
        }

        // 5. Completed state (Dark theme)
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.showCompleted()
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-completed-dark.png"))
            wnd.dispose()
        }
    }
}

