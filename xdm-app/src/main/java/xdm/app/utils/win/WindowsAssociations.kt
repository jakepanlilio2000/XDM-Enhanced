package xdm.app.utils.win

import xdm.app.OS
import xdm.app.utils.AppLauncher
import xdm.app.utils.detectOS
import xdm.core.util.Logger
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Manages Windows per-user file associations and protocol handlers in HKEY_CURRENT_USER\Software\Classes.
 *
 * Scoped strictly to HKCU so that:
 * 1. Administrator/UAC elevation is never required.
 * 2. Antivirus heuristics regarding global machine-wide registry changes are avoided.
 * 3. User consent is fully respected: associations are only modified when requested by the user.
 */
object WindowsAssociations {

    const val MAGNET_KEY = "Software\\Classes\\magnet"
    const val TORRENT_EXT_KEY = "Software\\Classes\\.torrent"
    const val TORRENT_PROGID_KEY = "Software\\Classes\\xdm.torrent"
    const val XDM_APP_PROTOCOL_KEY = "Software\\Classes\\xdm-app"

    val isWindows: Boolean get() = detectOS() == OS.Windows

    /**
     * Resolves the open command for XDM to register with Windows.
     * E.g. `"C:\Program Files\XDM\xdm-app.exe" "%1"`
     */
    fun openCommand(): String? {
        val launcher = AppLauncher.command() ?: run {
            val processCmd = ProcessHandle.current().info().command().orElse(null)
            val jar = File("target/xdm-app.jar").takeIf { it.isFile }
                ?: File("xdm-app/target/xdm-app.jar").takeIf { it.isFile }
            if (processCmd != null && jar != null) {
                listOf(processCmd, "-jar", jar.absolutePath)
            } else if (processCmd != null) {
                listOf(processCmd, "xdm.app.AppMain")
            } else {
                listOf("xdm-app.exe")
            }
        }
        val quotedPercentOne = "\"%1\""
        return AppLauncher.commandLine(launcher + listOf(quotedPercentOne))
    }

    /**
     * Extracts and validates the executable path from a shell open command string.
     * Returns true if the path is relative/unqualified or points to an existing file on disk.
     */
    fun isCommandExecutableValid(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false
        val exePath = if (trimmed.startsWith("\"")) {
            val endQuote = trimmed.indexOf('"', 1)
            if (endQuote > 1) trimmed.substring(1, endQuote) else trimmed.removePrefix("\"")
        } else {
            trimmed.substringBefore(" ")
        }
        val file = File(exePath)
        return !file.isAbsolute || file.exists()
    }

    /**
     * Checks if the `magnet:` protocol is registered to XDM in HKCU.
     */
    fun isMagnetRegistered(): Boolean {
        if (!isWindows) return false
        val cmd = Win32Registry.getString("$MAGNET_KEY\\shell\\open\\command", "") ?: return false
        if (!isCommandExecutableValid(cmd)) return false
        val launcher = AppLauncher.command()?.firstOrNull() ?: "xdm"
        return cmd.contains("xdm", ignoreCase = true) ||
                (launcher.isNotBlank() && cmd.contains(File(launcher).name, ignoreCase = true))
    }

    /**
     * Registers XDM as the `magnet:` URL protocol handler in HKCU.
     */
    fun registerMagnet(): Boolean {
        if (!isWindows) return false
        val cmd = openCommand() ?: return false
        val launcherPath = AppLauncher.command()?.firstOrNull().orEmpty()
        val icon = if (launcherPath.isNotBlank()) "$launcherPath,0" else ""

        val ok1 = Win32Registry.setString(MAGNET_KEY, "", "URL:BitTorrent Magnet URL")
        val ok2 = Win32Registry.setString(MAGNET_KEY, "URL Protocol", "")
        if (icon.isNotBlank()) {
            Win32Registry.setString("$MAGNET_KEY\\DefaultIcon", "", icon)
        }
        val ok3 = Win32Registry.setString("$MAGNET_KEY\\shell\\open\\command", "", cmd)
        Logger.info("WindowsAssociations: registered magnet protocol handler ($ok1, $ok2, $ok3)")
        return ok1 && ok3
    }

    /**
     * Unregisters the `magnet:` URL protocol handler in HKCU.
     */
    fun unregisterMagnet(): Boolean {
        if (!isWindows) return false
        val ok = Win32Registry.deleteTree(MAGNET_KEY)
        Logger.info("WindowsAssociations: unregistered magnet protocol handler ($ok)")
        return ok
    }

    /**
     * Checks if `.torrent` files are registered to XDM in HKCU.
     */
    fun isTorrentRegistered(): Boolean {
        if (!isWindows) return false
        val progId = Win32Registry.getString(TORRENT_EXT_KEY, "")
        if (progId != "xdm.torrent") return false
        val cmd = Win32Registry.getString("$TORRENT_PROGID_KEY\\shell\\open\\command", "") ?: return false
        if (!isCommandExecutableValid(cmd)) return false
        val launcher = AppLauncher.command()?.firstOrNull() ?: "xdm"
        return cmd.contains("xdm", ignoreCase = true) ||
                (launcher.isNotBlank() && cmd.contains(File(launcher).name, ignoreCase = true))
    }

    /**
     * Registers XDM for `.torrent` file association in HKCU.
     */
    fun registerTorrent(): Boolean {
        if (!isWindows) return false
        val cmd = openCommand() ?: return false
        val launcherPath = AppLauncher.command()?.firstOrNull().orEmpty()
        val icon = if (launcherPath.isNotBlank()) "$launcherPath,0" else ""

        val ok1 = Win32Registry.setString(TORRENT_EXT_KEY, "", "xdm.torrent")
        Win32Registry.setString(TORRENT_EXT_KEY, "Content Type", "application/x-bittorrent")
        val ok2 = Win32Registry.setString(TORRENT_PROGID_KEY, "", "BitTorrent File")
        if (icon.isNotBlank()) {
            Win32Registry.setString("$TORRENT_PROGID_KEY\\DefaultIcon", "", icon)
        }
        val ok3 = Win32Registry.setString("$TORRENT_PROGID_KEY\\shell\\open\\command", "", cmd)
        Logger.info("WindowsAssociations: registered .torrent file association ($ok1, $ok2, $ok3)")
        return ok1 && ok3
    }

    /**
     * Unregisters `.torrent` file association in HKCU.
     */
    fun unregisterTorrent(): Boolean {
        if (!isWindows) return false
        if (Win32Registry.getString(TORRENT_EXT_KEY, "") == "xdm.torrent") {
            Win32Registry.deleteTree(TORRENT_EXT_KEY)
        }
        val ok = Win32Registry.deleteTree(TORRENT_PROGID_KEY)
        Logger.info("WindowsAssociations: unregistered .torrent file association ($ok)")
        return ok
    }

    /**
     * Checks if the `xdm-app:` protocol is registered to XDM in HKCU.
     */
    fun isProtocolRegistered(): Boolean {
        if (!isWindows) return false
        val cmd = Win32Registry.getString("$XDM_APP_PROTOCOL_KEY\\shell\\open\\command", "") ?: return false
        if (!isCommandExecutableValid(cmd)) return false
        val launcher = AppLauncher.command()?.firstOrNull() ?: "xdm"
        return cmd.contains("xdm", ignoreCase = true) ||
                (launcher.isNotBlank() && cmd.contains(File(launcher).name, ignoreCase = true))
    }

    /**
     * Registers XDM as the `xdm-app:` URL protocol handler in HKCU.
     */
    fun registerProtocol(): Boolean {
        if (!isWindows) return false
        val cmd = openCommand() ?: return false
        val launcherPath = AppLauncher.command()?.firstOrNull().orEmpty()
        val icon = if (launcherPath.isNotBlank()) "$launcherPath,0" else ""

        val ok1 = Win32Registry.setString(XDM_APP_PROTOCOL_KEY, "", "URL:XDM Enhanced Protocol")
        val ok2 = Win32Registry.setString(XDM_APP_PROTOCOL_KEY, "URL Protocol", "")
        if (icon.isNotBlank()) {
            Win32Registry.setString("$XDM_APP_PROTOCOL_KEY\\DefaultIcon", "", icon)
        }
        val ok3 = Win32Registry.setString("$XDM_APP_PROTOCOL_KEY\\shell\\open\\command", "", cmd)
        Logger.info("WindowsAssociations: registered xdm-app protocol handler ($ok1, $ok2, $ok3)")
        return ok1 && ok3
    }

    /**
     * Unregisters the `xdm-app:` URL protocol handler in HKCU.
     */
    fun unregisterProtocol(): Boolean {
        if (!isWindows) return false
        val ok = Win32Registry.deleteTree(XDM_APP_PROTOCOL_KEY)
        Logger.info("WindowsAssociations: unregistered xdm-app protocol handler ($ok)")
        return ok
    }

    private var lastOpenTime = 0L

    /**
     * Opens Windows Default Apps settings so the user can review and configure system-level associations.
     * Tries Java Desktop browse, then native ShellExecuteEx via Win32Shell, before falling back to cmd.exe/control.exe.
     */
    fun openDefaultAppsSettings(): Boolean {
        if (!isWindows) {
            Logger.info("WindowsAssociations", "openDefaultAppsSettings invoked on non-Windows OS")
            return false
        }
        val now = System.currentTimeMillis()
        if (now - lastOpenTime < 1000L) {
            // Debounce rapid repeated clicks to prevent excessive process spawns
            return true
        }
        lastOpenTime = now

        // 1. Try standard Java Desktop API first
        val browseSuccess = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create("ms-settings:defaultapps"))
                Logger.info("WindowsAssociations: opened Windows Default Apps settings via Desktop.browse")
                true
            } else false
        }.getOrDefault(false)

        if (browseSuccess) return true

        // 2. Try native Win32Shell execute with ShellExecuteExW
        val win32Success = runCatching {
            Win32Shell.execute("ms-settings:defaultapps")
        }.getOrDefault(false)

        if (win32Success) {
            Logger.info("WindowsAssociations: opened Windows Default Apps settings via Win32Shell.execute")
            return true
        }

        // 3. Fallback to cmd.exe /c start "" ms-settings:defaultapps
        return runCatching {
            // "start" requires an explicit empty title "" before the target URI when arguments are quoted
            val pb = ProcessBuilder("cmd.exe", "/c", "start", "", "ms-settings:defaultapps")
                .redirectErrorStream(true)
            val proc = pb.start()
            val finished = proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (finished && proc.exitValue() != 0) {
                throw IllegalStateException("cmd start ms-settings exited with code ${proc.exitValue()}")
            }
            Logger.info("WindowsAssociations: opened Windows Default Apps settings via cmd start fallback")
            true
        }.recoverCatching { err ->
            Logger.info("WindowsAssociations", "ms-settings:defaultapps failed, trying Control Panel fallback: ${err.message}")
            val proc = ProcessBuilder("control.exe", "/name", "Microsoft.DefaultPrograms").start()
            proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            true
        }.getOrElse { e ->
            Logger.error("WindowsAssociations", "Failed to open Windows Default Apps settings", e)
            false
        }
    }
}

