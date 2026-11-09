package xdm.app.power

import xdm.app.OS
import xdm.app.utils.LinuxUtils
import xdm.app.utils.MacUtils
import xdm.app.utils.detectOS
import xdm.core.util.Logger
import java.io.File
import java.util.concurrent.TimeUnit

interface IPowerManager {
    fun isSleepSupported(): Boolean
    fun isHibernateSupported(): Boolean
    fun sleep(): Boolean
    fun shutdown(): Boolean
    fun hibernate(): Boolean

    fun perform(action: CompletionAction): Boolean = when (action) {
        CompletionAction.DO_NOTHING -> true
        CompletionAction.SLEEP -> sleep()
        CompletionAction.SHUTDOWN -> shutdown()
        CompletionAction.HIBERNATE -> hibernate()
    }
}

object PowerManager {
    var current: IPowerManager = DefaultPowerManager

    fun isSleepSupported(): Boolean = current.isSleepSupported()
    fun isHibernateSupported(): Boolean = current.isHibernateSupported()
    fun perform(action: CompletionAction): Boolean = current.perform(action)
}

object DefaultPowerManager : IPowerManager {
    private val os by lazy { detectOS() }

    private val windowsSleepSupported: Boolean by lazy { checkWindowsState("Standby") }
    private val windowsHibernateSupported: Boolean by lazy { checkWindowsState("Hibernate") }

    private val linuxSleepSupported: Boolean by lazy { checkLinuxSleep() }
    private val linuxHibernateSupported: Boolean by lazy { checkLinuxHibernate() }

    override fun isSleepSupported(): Boolean = when (os) {
        OS.Windows -> windowsSleepSupported
        OS.Linux -> linuxSleepSupported
        OS.MacOS -> true
    }

    override fun isHibernateSupported(): Boolean = when (os) {
        OS.Windows -> windowsHibernateSupported
        OS.Linux -> linuxHibernateSupported
        OS.MacOS -> false
    }

    override fun sleep(): Boolean {
        Logger.info("Executing system sleep/suspend ($os)...")
        return when (os) {
            OS.Windows -> sleepWindows()
            OS.Linux -> sleepLinux()
            OS.MacOS -> sleepMac()
        }
    }

    override fun shutdown(): Boolean {
        Logger.info("Executing system shutdown ($os)...")
        return when (os) {
            OS.Windows -> shutdownWindows()
            OS.Linux -> shutdownLinux()
            OS.MacOS -> shutdownMac()
        }
    }

    override fun hibernate(): Boolean {
        Logger.info("Executing system hibernation ($os)...")
        return when (os) {
            OS.Windows -> hibernateWindows()
            OS.Linux -> hibernateLinux()
            OS.MacOS -> false
        }
    }

    // --- Windows -----------------------------------------------------------------------------

    private fun checkWindowsState(keyword: String): Boolean = runCatching {
        val proc = ProcessBuilder("powercfg", "/a").redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor(5, TimeUnit.SECONDS)

        var availableSection = false
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("The following sleep states are available", ignoreCase = true)) {
                availableSection = true
                continue
            }
            if (trimmed.startsWith("The following sleep states are not available", ignoreCase = true)) {
                availableSection = false
                continue
            }
            if (availableSection && trimmed.contains(keyword, ignoreCase = true)) {
                return true
            }
        }
        false
    }.getOrElse {
        Logger.error("Failed to check Windows sleep states via powercfg", it)
        // Default: standby is universally supported on Windows; hibernate requires check
        keyword.equals("Standby", ignoreCase = true)
    }

    private fun sleepWindows(): Boolean = runCatching {
        // Explicitly invoke Application.SetSuspendState with Suspend
        val pb = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-Command",
            "Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.Application]::SetSuspendState([System.Windows.Forms.PowerState]::Suspend, \$false, \$false)"
        ).redirectErrorStream(true)
        val proc = pb.start()
        val finished = proc.waitFor(10, TimeUnit.SECONDS)
        if (finished && proc.exitValue() == 0) return true

        // Fallback: rundll32 powrprof.dll
        ProcessBuilder("rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0").start()
        true
    }.getOrElse {
        Logger.error("Failed to put Windows to sleep", it)
        false
    }

    private fun shutdownWindows(): Boolean = runCatching {
        ProcessBuilder("shutdown", "/s", "/t", "0").start()
        true
    }.getOrElse {
        Logger.error("Failed to shut down Windows", it)
        false
    }

    private fun hibernateWindows(): Boolean = runCatching {
        ProcessBuilder("shutdown", "/h").start()
        true
    }.getOrElse {
        Logger.error("Failed to hibernate Windows", it)
        false
    }

    // --- Linux -------------------------------------------------------------------------------

    private fun checkLinuxSleep(): Boolean = runCatching {
        val sysFile = File("/sys/power/state")
        if (sysFile.exists() && sysFile.readText().contains("mem")) return true

        val proc = ProcessBuilder("systemctl", "can-suspend").redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().use { it.readText().trim() }
        proc.waitFor(3, TimeUnit.SECONDS) && text.equals("yes", ignoreCase = true)
    }.getOrElse { true }

    private fun checkLinuxHibernate(): Boolean = runCatching {
        val sysFile = File("/sys/power/state")
        if (sysFile.exists() && sysFile.readText().contains("disk")) return true

        val proc = ProcessBuilder("systemctl", "can-hibernate").redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().use { it.readText().trim() }
        proc.waitFor(3, TimeUnit.SECONDS) && text.equals("yes", ignoreCase = true)
    }.getOrElse { false }

    private val linuxSleepCmds = listOf(
        arrayOf("systemctl", "suspend"),
        arrayOf(
            "dbus-send", "--system", "--print-reply",
            "--dest=org.freedesktop.login1", "/org/freedesktop/login1",
            "org.freedesktop.login1.Manager.Suspend", "boolean:true"
        )
    )

    private val linuxHibernateCmds = listOf(
        arrayOf("systemctl", "hibernate"),
        arrayOf(
            "dbus-send", "--system", "--print-reply",
            "--dest=org.freedesktop.login1", "/org/freedesktop/login1",
            "org.freedesktop.login1.Manager.Hibernate", "boolean:true"
        )
    )

    private fun sleepLinux(): Boolean {
        for (cmd in linuxSleepCmds) {
            if (executeCommand(cmd, "suspend")) return true
        }
        return false
    }

    private fun hibernateLinux(): Boolean {
        for (cmd in linuxHibernateCmds) {
            if (executeCommand(cmd, "hibernate")) return true
        }
        return false
    }

    private fun shutdownLinux(): Boolean {
        LinuxUtils.initShutdown()
        return true
    }

    private fun executeCommand(cmd: Array<String>, actionName: String): Boolean = runCatching {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val finished = proc.waitFor(10, TimeUnit.SECONDS)
        if (finished && proc.exitValue() == 0) {
            Logger.info("Linux $actionName succeeded via ${cmd.joinToString(" ")}")
            return true
        }
        if (!finished) proc.destroy()
        false
    }.getOrElse {
        Logger.error("Failed executing Linux $actionName command: ${cmd.joinToString(" ")}", it)
        false
    }

    // --- MacOS -------------------------------------------------------------------------------

    private fun sleepMac(): Boolean = runCatching {
        val proc = ProcessBuilder("pmset", "sleepnow").redirectErrorStream(true).start()
        val finished = proc.waitFor(5, TimeUnit.SECONDS)
        if (finished && proc.exitValue() == 0) return true

        ProcessBuilder("osascript", "-e", "tell application \"System Events\" to sleep").start()
        true
    }.getOrElse {
        Logger.error("Failed to sleep macOS", it)
        false
    }

    private fun shutdownMac(): Boolean {
        MacUtils.initShutdown()
        return true
    }
}

