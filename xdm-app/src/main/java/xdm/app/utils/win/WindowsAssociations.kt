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
     * Checks if the `magnet:` protocol is registered to XDM in HKCU.
     */
    fun isMagnetRegistered(): Boolean {
        if (!isWindows) return false
        val cmd = Win32Registry.getString("$MAGNET_KEY\\shell\\open\\command", "") ?: return false
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
     * Opens Windows 10/11 Default Apps settings so the user can review and configure system-level associations.
     */
    fun openDefaultAppsSettings(): Boolean = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI("ms-settings:defaultapps"))
            true
        } else {
            ProcessBuilder("cmd.exe", "/c", "start", "ms-settings:defaultapps").start()
            true
        }
    }.getOrDefault(false)
}

