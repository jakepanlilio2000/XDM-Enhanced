package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.AppDB
import xdm.app.DesktopEngineHost
import xdm.app.ui.components.CategorySidebar
import xdm.app.ui.components.DestinationPicker
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.manager.CategoryRules
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.DownloadManager
import xdm.manager.EngineContext
import xdm.manager.RecordStatus
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import javax.swing.JDialog
import xdm.app.IAppInstance

class AutoDownloadCategorizationTest {
    private lateinit var tempDir: File
    private lateinit var config: AppConfig

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("xdm-auto-cat-test").toFile()
        config = AppConfig(tempDir.absolutePath).apply {
            defaultDownloadFolder = tempDir.absolutePath
            categories = DownloadCategory.defaults(tempDir.absolutePath)
        }
        AppContext.config = config
        AppContext.configDir = tempDir.absolutePath
        AppContext.taskInfoDB = TaskInfoDB(tempDir.absolutePath)
        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, _, _ -> null } as IAppInstance
        EngineContext.host = DesktopEngineHost
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun record(
        id: Long,
        name: String,
        categoryId: String? = null,
        status: RecordStatus = RecordStatus.FINISHED
    ) = DbRecord(
        id = id,
        size = 1000L,
        downloaded = 1000L,
        progress = 100,
        date = System.currentTimeMillis(),
        fileName = name,
        eta = 0L,
        speed = 0f,
        selected = false,
        status = status,
        downloadType = DownloadType.Http,
        categoryId = categoryId
    )

    private fun match(
        fileName: String?,
        url: String? = null,
        pageUrl: String? = null,
        contentType: String? = null,
        contentDisposition: String? = null
    ): String? = CategoryRules.match(
        fileName = fileName,
        url = url,
        pageUrl = pageUrl,
        contentType = contentType,
        contentDisposition = contentDisposition
    )?.id

    @Test
    fun defaultCategoryMatching_allSixBuiltIns() {
        // 1. Documents
        val docs = listOf("test.pdf", "doc.docx", "old.doc", "sheet.xls", "sheet.xlsx", "pres.ppt", "pres.pptx", "notes.txt", "data.csv")
        docs.forEach { assertEquals("CAT_DOCUMENTS", match(it), "Expected CAT_DOCUMENTS for $it") }

        // 2. Compressed
        val compressed = listOf("archive.zip", "data.rar", "files.7z", "package.tar", "file.gz", "tarball.tar.gz", "tarball.tar.bz2", "tarball.tar.xz")
        compressed.forEach { assertEquals("CAT_COMPRESSED", match(it), "Expected CAT_COMPRESSED for $it") }

        // 3. Music
        val music = listOf("song.mp3", "track.flac", "audio.wav", "clip.aac", "voice.ogg", "tune.m4a", "sound.opus")
        music.forEach { assertEquals("CAT_MUSIC", match(it), "Expected CAT_MUSIC for $it") }

        // 4. Videos
        val videos = listOf("movie.mp4", "show.mkv", "film.avi", "clip.mov", "stream.webm", "feed.ts", "cam.wmv")
        videos.forEach { assertEquals("CAT_VIDEOS", match(it), "Expected CAT_VIDEOS for $it") }

        // 5. Programs
        val programs = listOf("installer.exe", "setup.msi", "app.msix", "mobile.apk", "package.deb", "package.rpm", "app.dmg", "os.iso", "tool.appimage")
        programs.forEach { assertEquals("CAT_PROGRAMS", match(it), "Expected CAT_PROGRAMS for $it") }

        // 6. Images
        val images = listOf("photo.jpg", "photo.jpeg", "picture.png", "anim.gif", "graphic.webp", "vector.svg", "icon.bmp", "favicon.ico", "raw.tiff", "photo.avif", "photo.heic")
        images.forEach { assertEquals("CAT_IMAGES", match(it), "Expected CAT_IMAGES for $it") }

        // Other / Unrecognized
        val other = listOf("unknown.xyz", "blob.dat", "binary.bin", "README", "Makefile")
        other.forEach { assertNull(match(it), "Expected null (Other) for $it") }
    }

    @Test
    fun caseInsensitivity_matchesUppercaseAndMixedCase() {
        assertEquals("CAT_DOCUMENTS", match("REPORT.PDF"))
        assertEquals("CAT_COMPRESSED", match("BACKUP.ZIP"))
        assertEquals("CAT_MUSIC", match("SONG.MP3"))
        assertEquals("CAT_VIDEOS", match("VIDEO.MP4"))
        assertEquals("CAT_PROGRAMS", match("SETUP.EXE"))
        assertEquals("CAT_IMAGES", match("IMAGE.PNG"))
        assertEquals("CAT_IMAGES", match("Photo.JpEg"))
        assertEquals("CAT_COMPRESSED", match("Archive.Tar.Gz"))
    }

    @Test
    fun multipartExtensionPrecedence_longerExtensionWins() {
        // .tar.gz (7 chars) should match CAT_COMPRESSED with higher specificity than a single .gz (3 chars)
        val customGzCategory = DownloadCategory(
            id = "custom_gz",
            name = "GzOnly",
            extensions = setOf(".gz"),
            folder = File(tempDir, "Gz").absolutePath
        )
        // Put customGzCategory before the built-in CAT_COMPRESSED
        config.categories = listOf(customGzCategory) + DownloadCategory.defaults(tempDir.absolutePath)
        
        // For bundle.tar.gz, CAT_COMPRESSED has .tar.gz (len 7) while custom_gz only has .gz (len 3)
        // Therefore CAT_COMPRESSED must win on specificity despite list order
        val result = match("bundle.tar.gz")
        assertEquals("CAT_COMPRESSED", result, "Expected CAT_COMPRESSED to win due to longer extension match .tar.gz")

        // For plain file.gz, customGzCategory should win because it is first in list order and both have len 3
        val gzResult = match("single.gz")
        assertEquals("custom_gz", gzResult)
    }

    @Test
    fun urlQueryParameterAndScriptFallback() {
        // URL with script ending and candidate in query
        assertEquals("CAT_DOCUMENTS", match("download.php", url = "https://example.com/download.php?file=report.pdf"))
        assertEquals("CAT_PROGRAMS", match("get.aspx", url = "https://example.com/get.aspx?download=app.msi"))
        assertEquals("CAT_MUSIC", match("stream.jsp", url = "https://example.com/stream.jsp?name=track.flac"))
        assertEquals("CAT_IMAGES", match("index.cfm", url = "https://example.com/index.cfm?media=banner.webp"))
        assertEquals("CAT_COMPRESSED", match("handler.do", url = "https://example.com/handler.do?attachment=package.7z"))

        // URL path fallback when fileName has no extension
        assertEquals("CAT_VIDEOS", match("download", url = "https://example.com/media/videos/clip.mkv"))
    }

    @Test
    fun contentDispositionPrecedence() {
        // Content-Disposition provides original filename
        val cd = "attachment; filename=\"final_report_2026.docx\""
        assertEquals("CAT_DOCUMENTS", match("export.php", url = "https://example.com/export.php", contentDisposition = cd))

        val cdImage = "attachment; filename*=UTF-8''wallpaper%20nature.png"
        assertEquals("CAT_IMAGES", match("img", url = "https://example.com/get", contentDisposition = cdImage))
    }

    @Test
    fun trustworthyMimeTypeFallback() {
        // Name without extension + PDF MIME type
        assertEquals("CAT_DOCUMENTS", match("document", url = "https://example.com/doc", contentType = "application/pdf"))
        // Name without extension + PNG MIME type
        assertEquals("CAT_IMAGES", match("avatar", url = "https://example.com/user/avatar", contentType = "image/png"))
        // Untrusted / generic binary MIME type should not guess or falsely classify
        assertNull(match("rawfile", url = "https://example.com/blob", contentType = "application/octet-stream"))
    }

    @Test
    fun manualOverridePrecedence_andChangeCategory() {
        val db = AppDB(tempDir.absolutePath)
        AppContext.db = db
        val taskDb = TaskInfoDB(tempDir.absolutePath)
        AppContext.taskInfoDB = taskDb
        val dm = DownloadManager(db, taskDb, tempDir.absolutePath)
        AppContext.downloader = dm

        val rec = record(1001L, "manual.mp4", categoryId = "CAT_VIDEOS", status = RecordStatus.PAUSED)
        db.add(rec)

        // User manually changes category to CAT_DOCUMENTS
        dm.changeCategory(1001L, "CAT_DOCUMENTS")
        val updated = AppContext.db.getById(1001L)
        assertNotNull(updated)
        assertEquals("CAT_DOCUMENTS", updated?.categoryId)

        // User clears category to None (null)
        dm.changeCategory(1001L, null)
        val cleared = AppContext.db.getById(1001L)
        assertNotNull(cleared)
        assertNull(cleared?.categoryId)
    }

    @Test
    fun categorySidebar_countsImagesAndOtherCategories() {
        val db = AppDB(tempDir.absolutePath)
        AppContext.db = db

        // Add records across categories including CAT_IMAGES
        db.add(record(1L, "pic.png", categoryId = "CAT_IMAGES", status = RecordStatus.FINISHED))
        db.add(record(2L, "photo.jpg", categoryId = "CAT_IMAGES", status = RecordStatus.DOWNLOADING))
        db.add(record(3L, "doc.pdf", categoryId = "CAT_DOCUMENTS", status = RecordStatus.FINISHED))
        db.add(record(4L, "misc.xyz", categoryId = null, status = RecordStatus.FINISHED))

        val sidebar = CategorySidebar { _, _ -> }
        sidebar.recount()

        assertEquals(4, sidebar.getNodeCount("All Downloads"))
        assertEquals(2, sidebar.getNodeCount("Images"))
        assertEquals(1, sidebar.getNodeCount("Documents"))
        assertEquals(1, sidebar.getNodeCount("Other"))
        assertEquals(0, sidebar.getNodeCount("Videos"))
        assertEquals(0, sidebar.getNodeCount("Music"))
    }

    @Test
    fun restoredDownloads_autoCategorizeWhenCategoryIdMissing() {
        val db = AppDB(tempDir.absolutePath)
        AppContext.db = db

        // Create a record without categoryId and add to db
        val rec = record(2001L, "legacy_song.flac", categoryId = null, status = RecordStatus.FINISHED)
        db.add(rec)

        val inMemory = db.getById(2001L)
        assertNotNull(inMemory)
        assertEquals("CAT_MUSIC", inMemory?.categoryId, "Record added without category should be auto-categorized to CAT_MUSIC")

        // Reloading from disk via loadRecords resolves categoryId on readRecord
        val dbReloaded = AppDB(tempDir.absolutePath).apply { loadRecords() }
        val fromDisk = dbReloaded.getById(2001L)
        assertNotNull(fromDisk)
        assertEquals("CAT_MUSIC", fromDisk?.categoryId, "Legacy record reloaded from disk should be auto-categorized to CAT_MUSIC")
    }

    @Test
    fun destinationPicker_userPickedGuard() {
        val owner = JDialog()
        val picker = DestinationPicker(owner)
        picker.reset("sample.pdf", "https://example.com/sample.pdf", null)
        assertEquals("CAT_DOCUMENTS", picker.selectedCategory?.id)

        // Editing filename changes category if user has not manually picked
        picker.fileNameChanged("clip.mp4")
        assertEquals("CAT_VIDEOS", picker.selectedCategory?.id)

        // Simulating user picking a category manually via combo box
        // Find index for Music
        val musicIndex = (0 until picker.categoryCombo.itemCount).firstOrNull {
            picker.categoryCombo.getItemAt(it).category?.id == "CAT_MUSIC"
        }
        assertNotNull(musicIndex)
        picker.categoryCombo.selectedIndex = musicIndex!!
        assertTrue(picker.userPicked)
        assertEquals("CAT_MUSIC", picker.selectedCategory?.id)

        // Subsequent filename or URL edits MUST NOT override user's manual choice
        picker.fileNameChanged("newfile.pdf")
        assertEquals("CAT_MUSIC", picker.selectedCategory?.id)
        picker.urlChanged("https://example.com/movie.mkv")
        assertEquals("CAT_MUSIC", picker.selectedCategory?.id)

        owner.dispose()
    }
}
