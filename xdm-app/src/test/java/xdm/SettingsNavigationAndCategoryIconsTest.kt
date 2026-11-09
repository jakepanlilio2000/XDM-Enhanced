package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.components.CategoryStyle
import xdm.app.ui.components.DestinationPicker
import xdm.app.ui.screens.NewDownloadWindow
import xdm.app.ui.screens.ProgressWindow
import xdm.app.utils.RemixIcon
import xdm.app.utils.win.WindowsAssociations
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Color
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.UIManager

class SettingsNavigationAndCategoryIconsTest : DownloadManagerTestBase() {

    @BeforeEach
    fun initTestBase() {
        xdm.app.I8N.loadTexts("en")
        AppContext.defaultDownloadFolder = dir.absolutePath
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

    // ==========================================
    // Part A: Windows Settings Navigation Tests
    // ==========================================

    @Test
    fun testOpenDefaultAppsSettingsDebounce() {
        if (!WindowsAssociations.isWindows) {
            // Non-windows must return false gracefully without throwing
            assertFalse(WindowsAssociations.openDefaultAppsSettings())
            return
        }

        // On Windows, rapid successive invocations must be debounced and succeed without error
        val first = WindowsAssociations.openDefaultAppsSettings()
        assertTrue(first, "First call to openDefaultAppsSettings should succeed")

        val second = WindowsAssociations.openDefaultAppsSettings()
        assertTrue(second, "Rapid debounced second call should return true without spawning extra process")
    }

    // ==========================================
    // Part B: Category Glyph and Color Mapping Tests
    // ==========================================

    @Test
    fun testCategoryStyleGlyphAndColorMapping() {
        // 1. Documents
        assertEquals(RemixIcon.FILE_TEXT_LINE, CategoryStyle.glyphForCategory("CAT_DOCUMENTS"))
        assertEquals("Documents", CategoryStyle.displayNameForCategory("CAT_DOCUMENTS"))
        val expectedDocColor = UIManager.getColor("XDM.category.CAT_DOCUMENTS") ?: Color(88, 166, 255)
        assertEquals(expectedDocColor, CategoryStyle.colorForCategory("CAT_DOCUMENTS"))

        // 2. Compressed
        assertEquals(RemixIcon.FILE_ZIP_LINE, CategoryStyle.glyphForCategory("CAT_COMPRESSED"))
        assertEquals("Compressed", CategoryStyle.displayNameForCategory("CAT_COMPRESSED"))
        val expectedZipColor = UIManager.getColor("XDM.category.CAT_COMPRESSED") ?: Color(210, 153, 34)
        assertEquals(expectedZipColor, CategoryStyle.colorForCategory("CAT_COMPRESSED"))

        // 3. Music
        assertEquals(RemixIcon.MUSIC_2_LINE, CategoryStyle.glyphForCategory("CAT_MUSIC"))
        assertEquals("Music", CategoryStyle.displayNameForCategory("CAT_MUSIC"))
        val expectedMusicColor = UIManager.getColor("XDM.category.CAT_MUSIC") ?: Color(240, 136, 62)
        assertEquals(expectedMusicColor, CategoryStyle.colorForCategory("CAT_MUSIC"))

        // 4. Videos
        assertEquals(RemixIcon.MOVIE_LINE, CategoryStyle.glyphForCategory("CAT_VIDEOS"))
        assertEquals("Videos", CategoryStyle.displayNameForCategory("CAT_VIDEOS"))
        val expectedVideoColor = UIManager.getColor("XDM.category.CAT_VIDEOS") ?: Color(163, 113, 247)
        assertEquals(expectedVideoColor, CategoryStyle.colorForCategory("CAT_VIDEOS"))

        // 5. Programs
        assertEquals(RemixIcon.TERMINAL_BOX_LINE, CategoryStyle.glyphForCategory("CAT_PROGRAMS"))
        assertEquals("Programs", CategoryStyle.displayNameForCategory("CAT_PROGRAMS"))
        val expectedProgColor = UIManager.getColor("XDM.category.CAT_PROGRAMS") ?: Color(230, 80, 80)
        assertEquals(expectedProgColor, CategoryStyle.colorForCategory("CAT_PROGRAMS"))

        // 6. Images
        assertEquals(RemixIcon.IMAGE_LINE, CategoryStyle.glyphForCategory("CAT_IMAGES"))
        assertEquals("Images", CategoryStyle.displayNameForCategory("CAT_IMAGES"))
        val expectedImgColor = UIManager.getColor("XDM.category.CAT_IMAGES") ?: Color(56, 189, 248)
        assertEquals(expectedImgColor, CategoryStyle.colorForCategory("CAT_IMAGES"))

        // 7. Torrents
        assertEquals(RemixIcon.MAGNET_LINE, CategoryStyle.glyphForCategory(null, downloadType = DownloadType.Torrent))
        assertEquals("Torrents", CategoryStyle.displayNameForCategory(null, downloadType = DownloadType.Torrent))
        assertEquals(CategoryStyle.EMERALD, CategoryStyle.colorForCategory(null, downloadType = DownloadType.Torrent))

        // 8. Other / Fallback
        assertEquals(RemixIcon.FILE_LINE, CategoryStyle.glyphForCategory(null))
        assertEquals("Other", CategoryStyle.displayNameForCategory(null))
        assertEquals(CategoryStyle.neutralIcon(), CategoryStyle.colorForCategory(null))
    }

    @Test
    fun testImageAndTorrentExtensionGlyphs() {
        // Image extensions should now map to IMAGE_FILL
        assertEquals(RemixIcon.IMAGE_FILL, CategoryStyle.glyphForFile("wallpaper.PNG"))
        assertEquals(RemixIcon.IMAGE_FILL, CategoryStyle.glyphForFile("photo.jpg"))
        assertEquals(RemixIcon.IMAGE_FILL, CategoryStyle.glyphForFile("graphic.svg"))
        assertEquals(RemixIcon.IMAGE_FILL, CategoryStyle.glyphForFile("animation.gif"))
        assertEquals(RemixIcon.IMAGE_FILL, CategoryStyle.glyphForFile("banner.webp"))

        // Torrent extension
        assertEquals(RemixIcon.MAGNET_FILL, CategoryStyle.glyphForFile("debian.torrent"))

        // Line variant mapping
        assertEquals(RemixIcon.IMAGE_LINE, CategoryStyle.lineVariant(RemixIcon.IMAGE_FILL))
        assertEquals(RemixIcon.MAGNET_LINE, CategoryStyle.lineVariant(RemixIcon.MAGNET_FILL))
    }

    // ==========================================
    // Part C: NewDownloadWindow Category Updates
    // ==========================================

    @Test
    fun testNewDownloadWindowCategoryIconUpdates() {
        runOnEdt {
            val wnd = NewDownloadWindow()

            // 1. Enter image URL -> auto-categorized as Images
            wnd.txtUrl.text = "https://example.com/gallery/landscape.png"
            assertEquals("landscape.png", wnd.txtFileName.text)
            assertNotNull(wnd.lblFileInfo.icon)

            // 2. Enter video URL -> auto-categorized as Videos
            wnd.txtUrl.text = "https://example.com/media/clip.mp4"
            assertEquals("clip.mp4", wnd.txtFileName.text)

            // 3. Manual override to Documents
            val picker = wnd.picker
            assertNotNull(picker)
            for (i in 0 until picker.categoryCombo.itemCount) {
                val item = picker.categoryCombo.getItemAt(i)
                if (item.category?.id == "CAT_DOCUMENTS") {
                    picker.categoryCombo.selectedIndex = i
                    break
                }
            }
            assertEquals("CAT_DOCUMENTS", picker.selectedCategory?.id)

            wnd.dispose()
        }
    }

    // ==========================================
    // Part D: ProgressWindow Category Display
    // ==========================================

    @Test
    fun testProgressWindowDisplaysCategoryIconAndLabel() {
        runOnEdt {
            val taskId = 901L
            val rec = DbRecord(
                id = taskId,
                size = 120_000_000L,
                downloaded = 60_000_000L,
                progress = 50,
                date = System.currentTimeMillis(),
                fileName = "presentation.pdf",
                eta = 15L,
                speed = 2_000_000f,
                selected = false,
                status = RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Http,
                categoryId = "CAT_DOCUMENTS"
            )
            appDB.add(rec)
            taskDB.saveHttpTask(
                HttpDownloadTaskInfo(
                    id = taskId,
                    url = "https://example.com/docs/presentation.pdf",
                    fileName = "presentation.pdf",
                    respectFileName = true,
                    cookie = null,
                    headers = null,
                    origin = null,
                    categoryId = "CAT_DOCUMENTS",
                    targetFolder = dir.absolutePath,
                    maxPiece = 8,
                    authInfo = null,
                    knownFileSize = 120_000_000L
                )
            )

            val wnd = ProgressWindow(taskId)

            // Verify category label and icon
            assertEquals("Documents", wnd.lblCategory.text)
            assertNotNull(wnd.lblCategoryIcon.icon)
            assertEquals(36, wnd.lblCategoryIcon.icon.iconWidth)
            assertEquals(36, wnd.lblCategoryIcon.icon.iconHeight)

            // Simulate record update changing category to Compressed
            rec.categoryId = "CAT_COMPRESSED"
            appDB.update(rec)
            wnd.onRecordUpdated(rec)

            assertEquals("Compressed", wnd.lblCategory.text)

            wnd.dispose()
        }
    }

    @Test
    fun testProgressWindowTorrentCategoryIconAndLabel() {
        runOnEdt {
            val taskId = 902L
            val rec = DbRecord(
                id = taskId,
                size = 500_000_000L,
                downloaded = 100_000_000L,
                progress = 20,
                date = System.currentTimeMillis(),
                fileName = "archlinux.iso",
                eta = 60L,
                speed = 3_000_000f,
                selected = false,
                status = RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Torrent,
                categoryId = null
            )
            appDB.add(rec)

            val wnd = ProgressWindow(taskId)

            // Torrents default to Torrents category with Magnet icon
            assertEquals("Torrents", wnd.lblCategory.text)
            assertNotNull(wnd.lblCategoryIcon.icon)

            wnd.dispose()
        }
    }
}

