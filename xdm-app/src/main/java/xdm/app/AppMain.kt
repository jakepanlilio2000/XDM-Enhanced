package xdm.app

import xdm.capture.CapturedVideoTracker
import xdm.core.network.http.CertExceptions
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.util.UIScale
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import xdm.app.headless.CliParser
import xdm.app.headless.HeadlessCli
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.components.WaylandComboBoxUI
import xdm.app.utils.AppDirs
import xdm.app.utils.AssistiveTech
import xdm.app.utils.SystemTheme
import xdm.app.utils.ConscryptTls
import xdm.app.utils.GcScheduler
import xdm.app.utils.WaylandToolkit
import xdm.app.utils.detectOS
import xdm.app.utils.linux.DesktopFontSettings
import xdm.app.utils.linux.LinuxPlaces
import xdm.app.utils.windowIconImages
import xdm.core.downloaders.TaskInfoDB
import xdm.core.util.FileUtils
import xdm.core.util.Gc
import xdm.core.util.Logger
import xdm.manager.DownloadManager
import java.awt.Insets
import java.awt.RenderingHints
import java.io.File
import javax.swing.JOptionPane
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import kotlin.system.exitProcess

object AppMain {
    init {
        System.setProperty("http.KeepAlive.remainingData", "0")
        System.setProperty("http.KeepAlive.queuedConnections", "0")
        System.setProperty("swing.aatext", "true")
        System.setProperty("sun.java2d.d3d", "false")
        System.setProperty("sun.java2d.opengl", "false")
        System.setProperty("sun.java2d.xrender", "false")
        System.setProperty("sun.java2d.metal", "false")
        System.setProperty("sun.java2d.pmoffscreen", "false")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // Before anything else: headless mode builds no UI, binds no port and loads no app state.
        if (CliParser.isHeadless(args)) {
            // Quiet until HeadlessCli reads --verbose, and never on stdout (the CLI's own output).
            Logger.consoleEnabled = false
            Logger.infoToStderr = true
            ConscryptTls.install()
            exitProcess(HeadlessCli.run(args))
        }

        if (args.contains("--import-legacy")) {
            System.setProperty(AppDirs.IMPORT_LEGACY_PROPERTY, "true")
        }

        val configDir = AppDirs.root.path
        val tempDir = "$configDir${File.separatorChar}tmp"

        File(configDir).mkdirs()
        // Holds cookies, captured request headers and logs: other users of the machine stay out.
        FileUtils.restrictToOwner(File(configDir))
        Logger.init(File(configDir))
        // Before the AWT toolkit starts: it throws for an assistive technology it can't load (issue 1456).
        AssistiveTech.sanitize().takeIf { it.isNotEmpty() }?.let {
            Logger.info("Accessibility: ignoring assistive technologies not in this runtime: ${it.joinToString()}")
        }
        // Before anything resolves a name or opens a socket (TLS setup included): the JDK reads
        // preferIPv4Stack once, when its networking first loads, so a change needs a restart.
        if (AppConfig.readForceIpv4(configDir)) {
            System.setProperty("java.net.preferIPv4Stack", "true")
            Logger.info("Network: IPv4 only")
        }
        // Before any HTTP client or digest: the shared TLS context (SharedTls) is built once.
        ConscryptTls.install()
        // Certificates the user chose to trust; read by every HTTPS handshake.
        CertExceptions.init(configDir)
        // HotSpot appends "sharing" to java.vm.info while a CDS archive is mapped (APPCDS.md).
        Logger.info("CDS: archive " + if (System.getProperty("java.vm.info").orEmpty().contains("sharing")) "in use" else "not in use")

        Logger.info("Use OKHttp..")
        Logger.info("loading...")
        Logger.info(System.getProperty("java.version") + " " + System.getProperty("os.version"))

        System.setProperty("apple.awt.application.appearance", "system")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "XDM Enhanced")
        System.setProperty("apple.awt.enableTemplateImages", "true")

        Logger.info("Creating dir: $tempDir")
        File(tempDir).mkdirs()

        val appDB = AppDB(configDir)
        val taskDB = TaskInfoDB(configDir)

        AppContext.apply {
            db = appDB
            app = AppInstance()
            config = AppConfig(configDir)
            queue = QueueManager()
            platform = PlatformInvoke()
            downloader = DownloadManager(store = appDB, taskInfoDB = taskDB, configDir = configDir)
            videoTracker = CapturedVideoTracker()
            taskInfoDB = taskDB
            scheduler = DownloadScheduler(appDB, configDir)
        }.init(args, configDir, tempDir)

        AppContext.scheduler.start()
        // --no-gc: none of XDM's explicit GCs, the periodic ones nor xdm-core's after a client closes.
        if (args.contains("--no-gc")) Gc.install {} else GcScheduler.start()
    }

    /** Whether [theme] means dark; [systemIsDark] is only asked for "system". */
    fun resolveDark(theme: String, systemIsDark: () -> Boolean?): Boolean = when (theme.lowercase()) {
        "light" -> false
        "system" -> systemIsDark() == true
        else -> true
    }

    /**
     * Installs the FlatLaf look-and-feel for the configured theme ("system" follows the OS,
     * light when it can't be read) and whole-UI zoom in percent. Delegated to [ThemeManager].
     */
    @JvmStatic
    fun setupTheme(theme: String, zoomPercent: Int = 100) {
        ThemeManager.applyTheme(theme, null, zoomPercent, saveConfig = false)
    }
}
