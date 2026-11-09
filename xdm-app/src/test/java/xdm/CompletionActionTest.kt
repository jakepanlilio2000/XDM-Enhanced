package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.power.*
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class CompletionActionTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockPower: MockPowerManager
    private var originalPower: IPowerManager = PowerManager.current

    class MockPowerManager(
        var sleepSupported: Boolean = true,
        var hibernateSupported: Boolean = true
    ) : IPowerManager {
        var sleepCalls = 0
        var shutdownCalls = 0
        var hibernateCalls = 0

        override fun isSleepSupported(): Boolean = sleepSupported
        override fun isHibernateSupported(): Boolean = hibernateSupported
        override fun sleep(): Boolean { sleepCalls++; return true }
        override fun shutdown(): Boolean { shutdownCalls++; return true }
        override fun hibernate(): Boolean { hibernateCalls++; return true }
    }

    @BeforeEach
    fun setUp() {
        xdm.app.I8N.loadTexts("en")
        mockPower = MockPowerManager()
        PowerManager.current = mockPower
        CompletionCoordinator.reset()
        CompletionCoordinator.headlessOrTesting = true
    }

    @AfterEach
    fun tearDown() {
        PowerManager.current = originalPower
        CompletionCoordinator.reset()
    }

    @Test
    fun testCompletionActionEnum() {
        assertEquals(CompletionAction.DO_NOTHING, CompletionAction.fromId(0))
        assertEquals(CompletionAction.SLEEP, CompletionAction.fromId(1))
        assertEquals(CompletionAction.SHUTDOWN, CompletionAction.fromId(2))
        assertEquals(CompletionAction.HIBERNATE, CompletionAction.fromId(3))
        assertEquals(CompletionAction.DO_NOTHING, CompletionAction.fromId(99))
    }

    @Test
    fun testAppConfigPersistence() {
        val configDir = tempDir.resolve("config").toFile()
        configDir.mkdirs()

        val config1 = AppConfig(configDir.absolutePath)
        assertEquals(CompletionAction.DO_NOTHING, config1.completionAction)
        assertFalse(config1.haltAfterDownload)

        config1.completionAction = CompletionAction.SLEEP
        config1.save()

        val config2 = AppConfig(configDir.absolutePath)
        config2.load()
        assertEquals(CompletionAction.SLEEP, config2.completionAction)
        assertFalse(config2.haltAfterDownload)

        // Setting haltAfterDownload updates completionAction to SHUTDOWN
        config2.haltAfterDownload = true
        assertEquals(CompletionAction.SHUTDOWN, config2.completionAction)
        config2.save()

        val config3 = AppConfig(configDir.absolutePath)
        config3.load()
        assertEquals(CompletionAction.SHUTDOWN, config3.completionAction)
        assertTrue(config3.haltAfterDownload)

        // Setting completionAction to HIBERNATE preserves persistence
        config3.completionAction = CompletionAction.HIBERNATE
        config3.save()

        val config4 = AppConfig(configDir.absolutePath)
        config4.load()
        assertEquals(CompletionAction.HIBERNATE, config4.completionAction)
        assertFalse(config4.haltAfterDownload)
    }

    @Test
    fun testMockPowerManagerPerform() {
        mockPower.perform(CompletionAction.DO_NOTHING)
        assertEquals(0, mockPower.sleepCalls)
        assertEquals(0, mockPower.shutdownCalls)
        assertEquals(0, mockPower.hibernateCalls)

        mockPower.perform(CompletionAction.SLEEP)
        assertEquals(1, mockPower.sleepCalls)

        mockPower.perform(CompletionAction.SHUTDOWN)
        assertEquals(1, mockPower.shutdownCalls)

        mockPower.perform(CompletionAction.HIBERNATE)
        assertEquals(1, mockPower.hibernateCalls)
    }

    @Test
    fun testCompletionCoordinatorNoDownloadsProcessedDoesNotTrigger() {
        val configDir = tempDir.resolve("config").toFile()
        configDir.mkdirs()
        val config = AppConfig(configDir.absolutePath)
        config.completionAction = CompletionAction.SHUTDOWN
        AppContext.config = config

        // No downloads have run in this session
        assertFalse(CompletionCoordinator.hasProcessed())
        CompletionCoordinator.onAllDownloadsDone()

        assertEquals(0, mockPower.shutdownCalls)
    }

    @Test
    fun testCompletionCoordinatorDoNothingDoesNotTrigger() {
        val configDir = tempDir.resolve("config").toFile()
        configDir.mkdirs()
        val config = AppConfig(configDir.absolutePath)
        config.completionAction = CompletionAction.DO_NOTHING
        AppContext.config = config

        CompletionCoordinator.markDownloadStarted()
        CompletionCoordinator.markDownloadFinished()
        assertTrue(CompletionCoordinator.hasProcessed())

        CompletionCoordinator.onAllDownloadsDone()
        assertEquals(0, mockPower.shutdownCalls)
        assertEquals(0, mockPower.sleepCalls)
    }

    @Test
    fun testCompletionCoordinatorTriggersAndDeduplicates() {
        val configDir = tempDir.resolve("config").toFile()
        configDir.mkdirs()
        val config = AppConfig(configDir.absolutePath)
        config.completionAction = CompletionAction.SHUTDOWN
        AppContext.config = config

        CompletionCoordinator.markDownloadStarted()
        CompletionCoordinator.markDownloadFinished()

        // In headless test environment, onAllDownloadsDone executes directly
        CompletionCoordinator.onAllDownloadsDone()
        assertEquals(1, mockPower.shutdownCalls)

        // Repeated call must be deduplicated by the latch
        CompletionCoordinator.onAllDownloadsDone()
        assertEquals(1, mockPower.shutdownCalls, "Duplicate completion event should be ignored")

        // Re-arming via new download allows the next batch to trigger
        CompletionCoordinator.markDownloadStarted()
        CompletionCoordinator.markDownloadFinished()
        CompletionCoordinator.onAllDownloadsDone()
        assertEquals(2, mockPower.shutdownCalls, "Re-armed completion should trigger for next batch")
    }

    @Test
    fun testCompletionCoordinatorWithFailedDownloads() {
        val configDir = tempDir.resolve("config").toFile()
        configDir.mkdirs()
        val config = AppConfig(configDir.absolutePath)
        config.completionAction = CompletionAction.SLEEP
        AppContext.config = config

        CompletionCoordinator.markDownloadStarted()
        CompletionCoordinator.markDownloadFailed()
        assertEquals(1, CompletionCoordinator.currentFailedCount())

        CompletionCoordinator.onAllDownloadsDone()
        assertEquals(1, mockPower.sleepCalls)
        assertEquals(0, CompletionCoordinator.currentFailedCount())
    }

    @Test
    fun testCountdownDialogCancelAndPerform() {
        val cancelCalled = AtomicBoolean(false)
        val performCalled = AtomicBoolean(false)

        val dialog = CompletionCountdownDialog(
            parent = null,
            action = CompletionAction.SHUTDOWN,
            failedCount = 0,
            initialSeconds = 30,
            onPerform = { performCalled.set(true) },
            onCancel = { cancelCalled.set(true) }
        )

        // Cancel test
        dialog.cancelAction()
        assertTrue(cancelCalled.get())
        assertFalse(performCalled.get())

        // Perform test
        cancelCalled.set(false)
        val dialog2 = CompletionCountdownDialog(
            parent = null,
            action = CompletionAction.SHUTDOWN,
            failedCount = 1,
            initialSeconds = 30,
            onPerform = { performCalled.set(true) },
            onCancel = { cancelCalled.set(true) }
        )
        dialog2.performAction()
        assertTrue(performCalled.get())
        assertFalse(cancelCalled.get())
    }
}

