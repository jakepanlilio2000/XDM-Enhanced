package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.utils.captureDestination
import xdm.app.utils.folderFor
import xdm.app.utils.resolveAccessibleFolder
import xdm.manager.DownloadCategory
import java.io.File
import java.nio.file.Files

/**
 * Regression tests for Issue #1426:
 * Default download folder persistence, category re-anchoring, custom folder preservation,
 * and graceful fallback for unavailable/unmounted drives without mutating user configuration.
 */
class DownloadFolderPersistenceTest {
    private lateinit var tempDir: File
    private lateinit var config: AppConfig

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("xdm-folder-test").toFile()
        config = AppConfig(tempDir.absolutePath).apply {
            defaultDownloadFolder = File(tempDir, "downloads").absolutePath
            categories = DownloadCategory.defaults(defaultDownloadFolder)
        }
        AppContext.config = config
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun videoCategory() = config.categories.first { it.id == "CAT_VIDEOS" }
    private fun documentCategory() = config.categories.first { it.id == "CAT_DOCUMENTS" }

    @Test
    fun changingDefaultDownloadFolder_updatesDefaultCategories() {
        val oldDefault = File(tempDir, "downloads").absolutePath
        val newDefault = File(File(tempDir, "new-drive"), "downloads").absolutePath

        // Initial default: video category is inside oldDefault/Video
        assertEquals(File(oldDefault, "Video").absolutePath, videoCategory().folder)
        assertEquals(File(oldDefault, "Documents").absolutePath, documentCategory().folder)

        // Change default download folder and re-anchor default categories
        config.updateCategoriesForNewBase(oldDefault, newDefault)
        config.defaultDownloadFolder = newDefault

        // Verify default categories re-anchored to the new default directory
        assertEquals(newDefault, config.defaultDownloadFolder)
        assertEquals(File(newDefault, "Video").absolutePath, videoCategory().folder)
        assertEquals(File(newDefault, "Documents").absolutePath, documentCategory().folder)

        // Verify browser capture destination matches new folder
        val (catId, targetFolder) = captureDestination("movie.mp4", "http://example.com/movie.mp4", null)
        assertEquals("CAT_VIDEOS", catId)
        assertEquals(File(newDefault, "Video").absolutePath, targetFolder)
    }

    @Test
    fun changingDefaultDownloadFolder_preservesCustomCategoryFolder() {
        val oldDefault = File(tempDir, "downloads").absolutePath
        val newDefault = File(tempDir, "new-location").absolutePath
        val customMusicFolder = File(tempDir, "custom-music-library").absolutePath

        // Set a custom folder on the Music category that is outside the default downloads path
        config.categories = config.categories.map { cat ->
            if (cat.id == "CAT_MUSIC") cat.copy(folder = customMusicFolder, predefined = false) else cat
        }

        // Change default folder
        config.updateCategoriesForNewBase(oldDefault, newDefault)
        config.defaultDownloadFolder = newDefault

        // The custom folder MUST NOT be overwritten
        val musicCategory = config.categories.first { it.id == "CAT_MUSIC" }
        assertEquals(customMusicFolder, musicCategory.folder, "Custom user folder must not be silently reset")

        // Predefined categories should follow new default
        assertEquals(File(newDefault, "Video").absolutePath, videoCategory().folder)
    }

    @Test
    fun folderPersistence_survivesSaveAndRestart() {
        val oldDefault = config.defaultDownloadFolder
        val newDefault = File(tempDir, "persisted-downloads").absolutePath
        config.updateCategoriesForNewBase(oldDefault, newDefault)
        config.defaultDownloadFolder = newDefault
        config.save()

        // Simulate application restart by loading a fresh config from the same directory
        val reloaded = AppConfig(tempDir.absolutePath).apply { load() }
        assertEquals(newDefault, reloaded.defaultDownloadFolder)
        val reloadedVideo = reloaded.categories.first { it.id == "CAT_VIDEOS" }
        assertEquals(File(newDefault, "Video").absolutePath, reloadedVideo.folder)
    }

    @Test
    fun unavailableDrive_fallsBackGracefully_withoutMutatingConfig() {
        // Use a path on a nonexistent root/drive (e.g. Z:\downloads on Windows, or nonexistent non-writable root)
        val invalidDrivePath = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "Z:\\NonExistentDrive\\Downloads"
        } else {
            "/nonexistent_mounted_volume/downloads"
        }

        // User sets an external drive that is currently unmounted
        config.defaultDownloadFolder = invalidDrivePath

        // Config must keep the user's setting intact (do not silently wipe user settings)
        assertEquals(invalidDrivePath, config.defaultDownloadFolder)

        // But resolving the folder for a download must fall back to an accessible directory
        val resolved = resolveAccessibleFolder(invalidDrivePath)
        assertNotNull(resolved)
        assertTrue(File(resolved).exists() || File(resolved).parentFile?.exists() == true, "Must resolve to accessible path")
        assertFalse(resolved.startsWith("Z:\\"), "Must not point to unavailable drive Z:")

        // Config setting remains untouched for when the drive is plugged back in
        assertEquals(invalidDrivePath, config.defaultDownloadFolder)
    }
}

