package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.ui.screens.ClearAge
import xdm.app.ui.screens.ClearCriteria
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.io.File
import java.nio.file.Files

/**
 * Regression tests for Issue #1417:
 * Clear dialog preferences persistence across restarts, criteria matching, and destructive choice handling.
 */
class ClearDialogPreferenceTest {
    private lateinit var tempDir: File
    private lateinit var config: AppConfig

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("xdm-clear-test").toFile()
        config = AppConfig(tempDir.absolutePath)
        AppContext.config = config
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun defaultPreferences_areSafeAndSensible() {
        // Defaults: idle states checked, age = ANY (0), destructive disk deletion = FALSE
        assertTrue(config.clearFinished)
        assertTrue(config.clearPaused)
        assertTrue(config.clearFailed)
        assertEquals(0, config.clearAge)
        assertFalse(config.clearDeleteFiles, "Destructive disk deletion must default to false")
    }

    @Test
    fun preferences_surviveSaveAndReloadAcrossRestart() {
        // User changes preferences: only clear finished items older than a week, and delete from disk
        config.clearFinished = true
        config.clearPaused = false
        config.clearFailed = false
        config.clearAge = ClearAge.WEEK.ordinal
        config.clearDeleteFiles = true
        config.save()

        // Simulate application restart
        val reloaded = AppConfig(tempDir.absolutePath).apply { load() }
        assertTrue(reloaded.clearFinished)
        assertFalse(reloaded.clearPaused)
        assertFalse(reloaded.clearFailed)
        assertEquals(ClearAge.WEEK.ordinal, reloaded.clearAge)
        assertTrue(reloaded.clearDeleteFiles)
    }

    @Test
    fun clearCriteria_matchesCorrectStatusesAndAges() {
        val criteria = ClearCriteria(
            statuses = setOf(RecordStatus.FINISHED),
            age = ClearAge.WEEK
        )

        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24 * 3600 * 1000L
        val tenDaysAgo = now - 10 * 24 * 3600 * 1000L

        val recentFinished = record(1, RecordStatus.FINISHED, oneDayAgo)
        val oldFinished = record(2, RecordStatus.FINISHED, tenDaysAgo)
        val oldPaused = record(3, RecordStatus.PAUSED, tenDaysAgo)

        assertFalse(criteria.matches(recentFinished, now), "Recent finished download should not match week-old age filter")
        assertTrue(criteria.matches(oldFinished, now), "Finished download older than a week should match")
        assertFalse(criteria.matches(oldPaused, now), "Paused download should not match since statuses only include FINISHED")
    }

    private fun record(id: Long, status: RecordStatus, date: Long) = DbRecord(
        id = id,
        size = 1000L,
        downloaded = 1000L,
        progress = 100,
        date = date,
        fileName = "test-$id.zip",
        eta = 0,
        speed = 0.0f,
        selected = false,
        status = status,
        downloadType = DownloadType.Http,
        movePhase = null,
        failedFiles = 0,
        categoryId = null,
        allocating = -1
    )
}

