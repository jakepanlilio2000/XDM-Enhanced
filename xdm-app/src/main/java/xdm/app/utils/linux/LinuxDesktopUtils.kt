package xdm.app.utils.linux

import xdm.app.utils.AppLauncher
import xdm.core.util.Logger
import java.io.File
import java.io.FileOutputStream

object LinuxDesktopUtils {
    const val APP_NAME = "XDM Enhanced"
    const val GENERIC_NAME = "Download Manager"
    const val COMMENT = "XDM Enhanced Download Manager"
    const val ICON_NAME = "xdm-enhanced"
    const val LEGACY_ICON_NAME = "xdm-app"
    const val STARTUP_WM_CLASS = "xdm-app-AppMain"
    const val CATEGORIES = "Network;FileTransfer;"

    private val SYSTEM_ICON_DIRS = listOf(
        File("/usr/share/icons/hicolor/256x256/apps"),
        File("/usr/share/icons/hicolor/scalable/apps"),
        File("/usr/local/share/icons/hicolor/256x256/apps"),
        File("/usr/share/pixmaps")
    )

    fun getUserIconDirs(userHome: File = File(System.getProperty("user.home"))): List<File> = listOf(
        File(userHome, ".local/share/icons/hicolor/256x256/apps"),
        File(userHome, ".local/share/icons/hicolor/scalable/apps"),
        File(userHome, ".local/share/icons")
    )

    /**
     * Ensures that the XDM Enhanced icon exists in the user's local icon directory
     * (~/.local/share/icons/hicolor/256x256/apps/xdm-enhanced.png).
     * If the icon does not already exist on the system or in the user's directory,
     * extracts it from bundled resources so desktop environments can display it reliably.
     */
    fun ensureUserIconInstalled(userHome: File = File(System.getProperty("user.home"))): File? = runCatching {
        val targetDir = File(userHome, ".local/share/icons/hicolor/256x256/apps")
        val targetPng = File(targetDir, "$ICON_NAME.png")
        if (targetPng.isFile && targetPng.length() > 0) return targetPng

        // Check if already installed in system directories
        for (sysDir in SYSTEM_ICON_DIRS) {
            val sysPng = File(sysDir, "$ICON_NAME.png")
            if (sysPng.isFile && sysPng.length() > 0) return sysPng
        }

        targetDir.mkdirs()
        val resourceStream = LinuxDesktopUtils::class.java.getResourceAsStream("/images/xdm-logo-256.png")
        if (resourceStream != null) {
            resourceStream.use { input ->
                FileOutputStream(targetPng).use { output ->
                    input.copyTo(output)
                }
            }
            Logger.info("LinuxDesktopUtils: extracted $ICON_NAME.png to ${targetPng.absolutePath}")

            // Also copy to root ~/.local/share/icons/ for legacy fallbacks
            runCatching {
                val rootIcon = File(userHome, ".local/share/icons/$ICON_NAME.png")
                rootIcon.parentFile?.mkdirs()
                targetPng.copyTo(rootIcon, overwrite = true)
            }
            targetPng
        } else {
            Logger.info("LinuxDesktopUtils: bundled icon resource not found")
            null
        }
    }.getOrElse {
        Logger.error("LinuxDesktopUtils: failed to export user icon", it)
        null
    }

    /**
     * Resolves the icon reference for use in .desktop files.
     * In the FreeDesktop standard, icon names without extensions are looked up
     * in the system and user icon themes.
     */
    fun resolveDesktopIcon(userHome: File = File(System.getProperty("user.home"))): String {
        // Ensure user icon is installed if missing
        ensureUserIconInstalled(userHome)
        return ICON_NAME
    }

    /**
     * Generates standard FreeDesktop desktop entry content for XDM Enhanced autostart
     * and application menus (upstream issue #1412).
     */
    fun generateDesktopContent(
        cmd: List<String>,
        icon: String = ICON_NAME
    ): String = buildString {
        appendLine("[Desktop Entry]")
        appendLine("Type=Application")
        appendLine("Name=$APP_NAME")
        appendLine("GenericName=$GENERIC_NAME")
        appendLine("Comment=$COMMENT")
        appendLine("Exec=${AppLauncher.desktopExec(cmd)}")
        appendLine("Icon=$icon")
        appendLine("Terminal=false")
        appendLine("Categories=$CATEGORIES")
        appendLine("StartupNotify=false")
        appendLine("StartupWMClass=$STARTUP_WM_CLASS")
        appendLine("X-GNOME-Autostart-enabled=true")
    }
}

