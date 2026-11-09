package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.DownloadCompleteWindow
import xdm.app.ui.screens.NewDownloadWindow
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

class Phase6VisualScreenshotTest : DownloadManagerTestBase() {

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
    fun captureAllPhase6Screenshots() {
        val recId = 880L
        val totalSize = 250_000_000L // 250 MB
        val downloaded = 130_000_000L // 52%
        val rec = DbRecord(
            id = recId,
            size = totalSize,
            downloaded = downloaded,
            progress = 52,
            date = System.currentTimeMillis() - 120_000,
            fileName = "XDM_Enhanced_Update_Package_v1.0.zip",
            eta = 28L,
            speed = 4_280_000f, // 4.28 MB/s
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
            categoryId = "CAT_COMPRESSED"
        )
        appDB.add(rec)
        taskDB.saveHttpTask(
            HttpDownloadTaskInfo(
                id = recId,
                url = "https://releases.xdm-enhanced.org/builds/v1.0.0/XDM_Enhanced_Update_Package_v1.0.zip",
                fileName = "XDM_Enhanced_Update_Package_v1.0.zip",
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
            SegmentProgress(0L, 31_250_000L, 31_250_000L, 1L, "Complete"),
            SegmentProgress(31_250_000L, 31_250_000L, 31_250_000L, 2L, "Complete"),
            SegmentProgress(62_500_000L, 31_250_000L, 25_000_000L, 3L, "Receiving data..."),
            SegmentProgress(93_750_000L, 31_250_000L, 20_000_000L, 4L, "Receiving data..."),
            SegmentProgress(125_000_000L, 31_250_000L, 12_500_000L, 5L, "Receiving data..."),
            SegmentProgress(156_250_000L, 31_250_000L, 10_000_000L, 6L, "Receiving data..."),
            SegmentProgress(187_500_000L, 31_250_000L, 0L, 7L, "Connecting..."),
            SegmentProgress(218_750_000L, 31_250_000L, 0L, 8L, "Ready")
        )

        // 1. Progress Window - Light Theme (Reference A comparison)
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 4_280_000f, 28L, 52, segs)
            val file = File(artifactDir, "screenshot-phase6-progress-window-light.png")
            captureComponent(wnd, 600, 480, file)
            wnd.dispose()
        }

        // 2. Progress Window - Dark Theme
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 4_280_000f, 28L, 52, segs)
            val file = File(artifactDir, "screenshot-phase6-progress-window-dark.png")
            captureComponent(wnd, 600, 480, file)
            wnd.dispose()
        }

        // 3. Progress Window - Collapsed / Details Hidden
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 4_280_000f, 28L, 52, segs)
            wnd.toggleDetails() // collapses details
            val file = File(artifactDir, "screenshot-phase6-progress-window-collapsed.png")
            captureComponent(wnd, 600, 240, file)
            wnd.dispose()
        }

        // 4. New Download Window (Reference B comparison)
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val dlg = NewDownloadWindow()
            val task = HttpDownloadTaskInfo(
                id = 881L,
                url = "https://releases.xdm-enhanced.org/builds/v1.0.0/XDM_Enhanced_Update_Package_v1.0.zip",
                fileName = "XDM_Enhanced_Update_Package_v1.0.zip",
                respectFileName = true,
                cookie = null,
                headers = null,
                origin = null,
                categoryId = "CAT_COMPRESSED",
                targetFolder = "C:\\Users\\Administrator\\Downloads\\Compressed",
                maxPiece = 8,
                authInfo = null,
                knownFileSize = 250_000_000L
            )
            dlg.showWindow(task)
            dlg.txtDescription.text = "Official XDM Enhanced Release Beta Update"
            val file = File(artifactDir, "screenshot-phase6-new-download-window.png")
            captureComponent(dlg, 540, 360, file)
            dlg.dispose()
        }

        // 5. Download Complete Window (Reference C comparison)
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val wnd = DownloadCompleteWindow()
            wnd.setDetails(
                file = "manual_guide_v1.0.pdf",
                folder = "C:\\Users\\Administrator\\Downloads\\Documents",
                fileSize = 209_953L,
                checksumSource = null,
                sourceUrl = "https://example.com/docs/manual_guide_v1.0.pdf"
            )
            val file = File(artifactDir, "screenshot-phase6-download-complete-window.png")
            captureComponent(wnd, 480, 250, file)
            wnd.dispose()
        }
    }
}

