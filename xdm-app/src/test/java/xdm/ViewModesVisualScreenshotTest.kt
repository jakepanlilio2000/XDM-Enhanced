package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ListSelectionMode
import xdm.app.ui.components.*
import xdm.app.ui.screens.AppWindow
import xdm.app.utils.logoImage
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Component
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class ViewModesVisualScreenshotTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

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
    fun captureViewModesScreenshots() {
        artifactDir.mkdirs()

        // 1. Create a rich set of diverse mock downloads
        val records = listOf(
            DbRecord(
                id = 1L,
                size = 104_857_600L, // 100 MB
                downloaded = 68_157_440L,
                progress = 65,
                date = System.currentTimeMillis() - 7_200_000,
                fileName = "XDM-Enhanced-v1.0.0-beta.2.zip",
                eta = 42L,
                speed = 3_450_000f,
                selected = false,
                status = RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Http,
                categoryId = "CAT_COMPRESSED"
            ),
            DbRecord(
                id = 2L,
                size = 4_294_967_296L, // 4 GB
                downloaded = 1_073_741_824L,
                progress = 25,
                date = System.currentTimeMillis() - 3_600_000,
                fileName = "Ubuntu-24.04-desktop-amd64.iso",
                eta = 310L,
                speed = 8_500_000f,
                selected = true, // Selected card
                status = RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Torrent,
                categoryId = null
            ),
            DbRecord(
                id = 3L,
                size = 524_288_000L, // 500 MB
                downloaded = 524_288_000L,
                progress = 100,
                date = System.currentTimeMillis() - 86_400_000,
                fileName = "Kotlin_Swing_FlatLaf_Architecture_Guide.pdf",
                eta = 0L,
                speed = 0f,
                selected = false,
                status = RecordStatus.FINISHED,
                downloadType = DownloadType.Http,
                categoryId = "CAT_DOCUMENTS"
            ),
            DbRecord(
                id = 4L,
                size = 1_887_436_800L, // 1.75 GB
                downloaded = 754_974_720L,
                progress = 40,
                date = System.currentTimeMillis() - 14_400_000,
                fileName = "Nature_Documentary_4K_HDR.mp4",
                eta = 180L,
                speed = 0f,
                selected = false,
                status = RecordStatus.PAUSED,
                downloadType = DownloadType.Hls,
                categoryId = "CAT_VIDEOS"
            ),
            DbRecord(
                id = 5L,
                size = 85_983_232L, // 82 MB
                downloaded = 85_983_232L,
                progress = 100,
                date = System.currentTimeMillis() - 28_800_000,
                fileName = "Beethoven_Symphony_No_9_Lossless.flac",
                eta = 0L,
                speed = 0f,
                selected = false,
                status = RecordStatus.FINISHED,
                downloadType = DownloadType.Http,
                categoryId = "CAT_MUSIC"
            ),
            DbRecord(
                id = 6L,
                size = 150_000_000L,
                downloaded = 15_000_000L,
                progress = 10,
                date = System.currentTimeMillis() - 900_000,
                fileName = "Corrupted_Mirror_Package.tar.gz",
                eta = 0L,
                speed = 0f,
                selected = false,
                status = RecordStatus.ERROR,
                downloadType = DownloadType.Http,
                categoryId = "CAT_COMPRESSED"
            )
        )

        records.forEach { AppContext.db.add(it) }

        // --- DARK THEME SCREENSHOTS ---
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
        }

        // 1. Table View (Dark)
        val tableDarkFile = File(artifactDir, "screenshot-table-view-dark.png")
        runOnEdt {
            AppContext.config.listSelectionMode = ListSelectionMode.TABLE
            val win = AppWindow(logoImage(256), startHidden = true)
            win.applyListSelectionMode()
            (win.listView as? DownloadTableView)?.selectById(2L)
            captureComponent(win, 1020, 640, tableDarkFile)
            win.dispose()
        }

        // 2. Classic Cards (Dark)
        val cardsDarkFile = File(artifactDir, "screenshot-classic-cards-dark.png")
        runOnEdt {
            AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
            val win = AppWindow(logoImage(256), startHidden = true)
            win.applyListSelectionMode()
            (win.listView as? ClassicListView)?.selectByIds(setOf(2L))
            captureComponent(win, 1020, 640, cardsDarkFile)
            win.dispose()
        }

        // --- LIGHT THEME SCREENSHOTS ---
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
        }

        // 3. Table View (Light)
        val tableLightFile = File(artifactDir, "screenshot-table-view-light.png")
        runOnEdt {
            AppContext.config.listSelectionMode = ListSelectionMode.TABLE
            val win = AppWindow(logoImage(256), startHidden = true)
            win.applyListSelectionMode()
            (win.listView as? DownloadTableView)?.selectById(2L)
            captureComponent(win, 1020, 640, tableLightFile)
            win.dispose()
        }

        // 4. Classic Cards (Light)
        val cardsLightFile = File(artifactDir, "screenshot-classic-cards-light.png")
        runOnEdt {
            AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
            val win = AppWindow(logoImage(256), startHidden = true)
            win.applyListSelectionMode()
            (win.listView as? ClassicListView)?.selectByIds(setOf(2L))
            captureComponent(win, 1020, 640, cardsLightFile)
            win.dispose()
        }

        // 5. Generate Side-by-Side Comparison Image
        val comparisonFile = File(artifactDir, "screenshot-view-modes-comparison.png")
        val imgTable = ImageIO.read(tableDarkFile)
        val imgCards = ImageIO.read(cardsDarkFile)
        val compImg = BufferedImage(imgTable.width * 2, imgTable.height, BufferedImage.TYPE_INT_ARGB)
        val g2 = compImg.createGraphics() as Graphics2D
        g2.drawImage(imgTable, 0, 0, null)
        g2.drawImage(imgCards, imgTable.width, 0, null)
        g2.dispose()
        ImageIO.write(compImg, "png", comparisonFile)
        assertTrue(comparisonFile.exists() && comparisonFile.length() > 5000, "Comparison screenshot created")

        // 6. Empty States
        AppContext.db.removeWhere { true }
        val tableEmptyFile = File(artifactDir, "screenshot-table-view-empty.png")
        val cardsEmptyFile = File(artifactDir, "screenshot-classic-cards-empty.png")

        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            AppContext.config.listSelectionMode = ListSelectionMode.TABLE
            val winTable = AppWindow(logoImage(256), startHidden = true)
            winTable.applyListSelectionMode()
            captureComponent(winTable, 900, 500, tableEmptyFile)
            winTable.dispose()

            AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
            val winCards = AppWindow(logoImage(256), startHidden = true)
            winCards.applyListSelectionMode()
            captureComponent(winCards, 900, 500, cardsEmptyFile)
            winCards.dispose()
        }
    }
}
