package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.components.ThemeManager
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

class PhaseB4CategoryIconsVisualTest : DownloadManagerTestBase() {

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
    fun captureCategoryIconScreenshots() {
        val recId = 995L
        val totalSize = 150_000_000L
        val downloaded = 90_000_000L
        val rec = DbRecord(
            id = recId,
            size = totalSize,
            downloaded = downloaded,
            progress = 60,
            date = System.currentTimeMillis() - 120_000,
            fileName = "music_track_flac_audio.flac",
            eta = 20L,
            speed = 3_000_000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http,
            categoryId = "CAT_MUSIC"
        )
        appDB.add(rec)
        taskDB.saveHttpTask(
            HttpDownloadTaskInfo(
                id = recId,
                url = "https://example.com/audio/music_track_flac_audio.flac",
                fileName = "music_track_flac_audio.flac",
                respectFileName = true,
                cookie = null,
                headers = null,
                origin = null,
                categoryId = "CAT_MUSIC",
                targetFolder = dir.absolutePath,
                maxPiece = 8,
                authInfo = null,
                knownFileSize = totalSize
            )
        )

        val segs = listOf(
            SegmentProgress(0L, 25_000_000L, 25_000_000L, 1L, "Complete"),
            SegmentProgress(25_000_000L, 25_000_000L, 25_000_000L, 2L, "Complete"),
            SegmentProgress(50_000_000L, 25_000_000L, 25_000_000L, 3L, "Complete"),
            SegmentProgress(75_000_000L, 25_000_000L, 15_000_000L, 4L, "Receiving data...")
        )

        // 1. Add Download dialog with Category Icon (Dark theme)
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = NewDownloadWindow()
            wnd.txtUrl.text = "https://example.com/gallery/landscape_wallpaper.png"
            captureComponent(wnd, 540, 260, File(artifactDir, "screenshot-add-download-category-icons-dark.png"))
            wnd.dispose()
        }

        // 2. Add Download dialog with Category Icon (Light theme)
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val wnd = NewDownloadWindow()
            wnd.txtUrl.text = "https://example.com/videos/presentation.mp4"
            captureComponent(wnd, 540, 260, File(artifactDir, "screenshot-add-download-category-icons-light.png"))
            wnd.dispose()
        }

        // 3. Progress Window showing Category Icon & Category Name (Dark theme)
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 3_000_000f, 20L, 60, segs)
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-category-icons-dark.png"))
            wnd.dispose()
        }

        // 4. Progress Window showing Category Icon & Category Name (Light theme)
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val wnd = ProgressWindow(recId)
            wnd.updateProgress(rec.fileName, downloaded, totalSize, 3_000_000f, 20L, 60, segs)
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-category-icons-light.png"))
            wnd.dispose()
        }

        // 5. Progress Window with BitTorrent Magnet Category Icon (Dark theme)
        val torrentId = 996L
        val torrentRec = DbRecord(
            id = torrentId,
            size = 800_000_000L,
            downloaded = 400_000_000L,
            progress = 50,
            date = System.currentTimeMillis() - 60_000,
            fileName = "ubuntu-24.04-desktop-amd64.iso",
            eta = 90L,
            speed = 4_500_000f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Torrent,
            categoryId = null
        )
        appDB.add(torrentRec)

        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val wnd = ProgressWindow(torrentId)
            captureComponent(wnd, 600, 480, File(artifactDir, "screenshot-progress-torrent-icon-dark.png"))
            wnd.dispose()
        }
    }
}

