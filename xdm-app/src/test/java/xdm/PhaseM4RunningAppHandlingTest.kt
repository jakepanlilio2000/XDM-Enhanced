package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.AppDB
import xdm.app.AppInstance
import xdm.capture.CapturedVideoTracker
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.TaskInfoDB
import xdm.manager.DbRecord
import xdm.manager.DownloadManager
import xdm.manager.RecordStatus
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class PhaseM4RunningAppHandlingTest {

    @TempDir
    lateinit var tempFolder: File

    private lateinit var configDir: File

    @BeforeEach
    fun setup() {
        configDir = File(tempFolder, "config").apply { mkdirs() }
        AppContext.configDir = configDir.absolutePath
        AppContext.config = AppConfig(configDir.absolutePath)
        AppContext.videoTracker = CapturedVideoTracker()
        AppContext.db = AppDB(configDir.absolutePath)
        AppContext.taskInfoDB = TaskInfoDB(configDir.absolutePath)
        AppContext.downloader = DownloadManager(AppContext.db, AppContext.taskInfoDB, configDir.absolutePath)
        AppContext.app = AppInstance()
    }

    @AfterEach
    fun tearDown() {
    }

    private fun findRepoRoot(): File {
        var current = File(".").canonicalFile
        while (current != null) {
            if (File(current, "packaging/windows/xdm-installer.nsi").exists()) {
                return current
            }
            current = current.parentFile
        }
        throw IllegalStateException("Repository root with packaging/windows/xdm-installer.nsi not found")
    }

    @Test
    fun testProcessManagerNeverTerminatesUnrelatedProcesses() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return

        val root = findRepoRoot()
        val script = File(root, "packaging/windows/resources/process-manager.ps1")
        assertTrue(script.exists(), "process-manager.ps1 must exist")

        // Start an unrelated test process (notepad.exe)
        val unrelatedPb = ProcessBuilder("notepad.exe")
        val unrelatedProc = unrelatedPb.start()
        try {
            assertTrue(unrelatedProc.isAlive, "Unrelated process should be running")

            // Query process-manager with a non-matching target directory
            val checkPb = ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.absolutePath,
                "-TargetDir", File(tempFolder, "isolated-target").absolutePath,
                "-Action", "check"
            ).redirectErrorStream(true)

            val checkProc = checkPb.start()
            val finished = checkProc.waitFor(15, TimeUnit.SECONDS)
            assertTrue(finished)
            val output = checkProc.inputStream.bufferedReader().readText().trim()

            assertEquals("NOT_RUNNING", output, "Check must return NOT_RUNNING for unrelated directory")
            assertTrue(unrelatedProc.isAlive, "Unrelated process must remain alive and untouched")
        } finally {
            unrelatedProc.destroyForcibly()
        }
    }

    @Test
    fun testProcessManagerReturnsClosedWhenNoTargetRunning() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return

        val root = findRepoRoot()
        val script = File(root, "packaging/windows/resources/process-manager.ps1")
        assertTrue(script.exists(), "process-manager.ps1 must exist")

        val shutdownPb = ProcessBuilder(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath,
            "-TargetDir", File(tempFolder, "isolated-target").absolutePath,
            "-Action", "shutdown",
            "-TimeoutSec", "2"
        ).redirectErrorStream(true)

        val proc = shutdownPb.start()
        val finished = proc.waitFor(10, TimeUnit.SECONDS)
        assertTrue(finished)
        val output = proc.inputStream.bufferedReader().readText().trim()
        assertEquals("CLOSED", output, "Shutdown action must immediately report CLOSED if instance is not running")
    }

    @Test
    fun testGracefulShutdownPausesActiveDownloadsAndSavesConfig() {
        // Create an active download record in DB
        val rec = DbRecord(
            id = 9991L,
            size = 10_000L,
            downloaded = 2_000L,
            progress = 20,
            date = System.currentTimeMillis(),
            fileName = "active-test-file.iso",
            eta = 16L,
            speed = 500f,
            selected = false,
            status = RecordStatus.DOWNLOADING,
            downloadType = DownloadType.Http
        )
        AppContext.db.add(rec)
        assertEquals(RecordStatus.DOWNLOADING, AppContext.db.getById(9991L)?.status)

        // Execute graceful shutdown without exiting JVM
        AppContext.app.gracefulShutdown(timeoutMs = 1000, exitVm = false)

        // Verify active download was paused
        val updated = AppContext.db.getById(9991L)
        assertNotNull(updated)
        assertEquals(RecordStatus.PAUSED, updated?.status, "Active download must transition to PAUSED on graceful shutdown")
    }

    @Test
    fun testHandleLaunchArgsDetectsShutdownFlag() {
        var shutdownInvoked = false
        val testApp = object : AppInstance() {
            override fun gracefulShutdown(timeoutMs: Long, exitVm: Boolean) {
                shutdownInvoked = true
            }
        }

        testApp.handleLaunchArgs(listOf("--shutdown"))
        assertTrue(shutdownInvoked, "--shutdown launch argument must trigger gracefulShutdown()")

        shutdownInvoked = false
        testApp.handleLaunchArgs(listOf("--exit"))
        assertTrue(shutdownInvoked, "--exit launch argument must trigger gracefulShutdown()")

        shutdownInvoked = false
        testApp.handleLaunchArgs(listOf("--quit"))
        assertTrue(shutdownInvoked, "--quit launch argument must trigger gracefulShutdown()")
    }

    @Test
    fun testNsisScriptIncludesProcessManagerAndGracefulEscalation() {
        val root = findRepoRoot()
        val nsiFile = File(root, "packaging/windows/xdm-installer.nsi")
        assertTrue(nsiFile.exists())

        val content = nsiFile.readText(StandardCharsets.UTF_8)

        // Check required NSIS macros and variables
        assertTrue(content.contains("Var RunningPid"), "Installer must declare Var RunningPid")
        assertTrue(content.contains("Var ForceCloseOpt"), "Installer must declare Var ForceCloseOpt")
        assertTrue(content.contains("process-manager.ps1"), "Installer must reference process-manager.ps1")

        // Check graceful shutdown request
        assertTrue(content.contains("-Action shutdown"), "Installer must request -Action shutdown")
        assertTrue(content.contains("-TimeoutSec 6"), "Installer must enforce bounded wait timeout")

        // Check escalation logic
        assertTrue(content.contains("/FORCE_CLOSE"), "Installer must parse /FORCE_CLOSE for silent escalation")
        assertTrue(content.contains("MessageBox MB_YESNOCANCEL"), "Installer must prompt user before force termination")
        assertTrue(content.contains("loss of active transfer progress"), "Prompt must explain possible loss of progress")

        // Uninstaller must mirror the check
        assertTrue(content.contains("Function un.CheckAppRunning"), "Uninstaller must have un.CheckAppRunning")
        assertTrue(content.contains("UnSilentEscalation"), "Uninstaller must support silent escalation")
    }

    @Test
    fun testNsisInstallerCompilationWithM4Changes(@TempDir buildDir: File) {
        val makensis = File("C:\\Program Files (x86)\\NSIS\\makensis.exe")
        if (!makensis.exists()) return

        val root = findRepoRoot()
        val nsiFile = File(root, "packaging/windows/xdm-installer.nsi")
        val dummyImg = File(buildDir, "dummy-image").apply { mkdirs() }
        File(dummyImg, "xdm-app.exe").writeBytes(ByteArray(512))
        val outExe = File(buildDir, "test-m4-setup.exe")

        val pb = ProcessBuilder(
            makensis.absolutePath,
            "/V2",
            "/DOUT_FILE=${outExe.absolutePath}",
            "/DAPP_IMAGE_DIR=${dummyImg.absolutePath}",
            "/DPRODUCT_VERSION=1.0.0",
            nsiFile.absolutePath
        ).redirectErrorStream(true)

        val proc = pb.start()
        val finished = proc.waitFor(30, TimeUnit.SECONDS)
        assertTrue(finished, "makensis must finish compilation within 30 seconds")
        val output = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.exitValue(), "makensis compilation must succeed. Output:\n$output")
        assertTrue(outExe.exists() && outExe.length() > 0, "makensis must generate non-empty installer binary")
    }

    @Test
    fun testLiveRuntimeSilentInstallExitCodes(@TempDir buildDir: File) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return

        val makensis = File("C:\\Program Files (x86)\\NSIS\\makensis.exe")
        if (!makensis.exists()) return

        val root = findRepoRoot()
        val nsiFile = File(root, "packaging/windows/xdm-installer.nsi")
        val dummyImg = File(buildDir, "dummy-image").apply { mkdirs() }
        File(dummyImg, "xdm-app.exe").writeBytes(ByteArray(512))
        val outExe = File(buildDir, "test-m4-live.exe")

        val compilePb = ProcessBuilder(
            makensis.absolutePath,
            "/V2",
            "/DOUT_FILE=${outExe.absolutePath}",
            "/DAPP_IMAGE_DIR=${dummyImg.absolutePath}",
            "/DPRODUCT_VERSION=1.0.0",
            nsiFile.absolutePath
        ).redirectErrorStream(true)
        val compileProc = compilePb.start()
        assertTrue(compileProc.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, compileProc.exitValue())

        val targetDir = File(buildDir, "target-install").apply { mkdirs() }
        val targetExe = File(targetDir, "xdm-app.exe")
        val systemPing = File("C:\\Windows\\System32\\ping.exe")
        if (!systemPing.exists()) return
        systemPing.copyTo(targetExe, overwrite = true)

        // Case A: App is running, silent install WITHOUT /FORCE_CLOSE -> must exit with code 2
        val runningProc = ProcessBuilder(targetExe.absolutePath, "-n", "100", "127.0.0.1").start()
        try {
            assertTrue(runningProc.isAlive)
            Thread.sleep(400)

            val installPb = ProcessBuilder(outExe.absolutePath, "/S", "/D=${targetDir.absolutePath}")
            val installProc = installPb.start()
            val finished = installProc.waitFor(20, TimeUnit.SECONDS)
            assertTrue(finished)
            assertEquals(2, installProc.exitValue(), "Silent install without /FORCE_CLOSE must exit with code 2")
            assertTrue(runningProc.isAlive, "Simulated app must remain alive when install aborts without force-close")
        } finally {
            runningProc.destroyForcibly()
        }

        // Case B: App is running, silent install WITH /FORCE_CLOSE -> must terminate app and exit with code 0
        val runningProc2 = ProcessBuilder(targetExe.absolutePath, "-n", "100", "127.0.0.1").start()
        try {
            assertTrue(runningProc2.isAlive)
            Thread.sleep(400)

            val installPb = ProcessBuilder(outExe.absolutePath, "/S", "/FORCE_CLOSE", "/D=${targetDir.absolutePath}")
            val installProc = installPb.start()
            val finished = installProc.waitFor(20, TimeUnit.SECONDS)
            assertTrue(finished)
            assertEquals(0, installProc.exitValue(), "Silent install with /FORCE_CLOSE must succeed with code 0")
            Thread.sleep(600)
            assertFalse(runningProc2.isAlive, "Simulated app must be terminated when /FORCE_CLOSE is specified")
        } finally {
            if (runningProc2.isAlive) {
                runningProc2.destroyForcibly()
            }
        }
    }
}
