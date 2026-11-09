package xdm.app.update

import xdm.app.AppContext
import xdm.app.OS
import xdm.app.utils.detectOS
import xdm.core.util.Logger
import java.io.File
import kotlin.system.exitProcess

object UpdateInstaller {

    /**
     * Executes the installer file safely and exits the current application.
     * Never uses silent flags; prompts the user normally through the platform installer.
     */
    fun launchInstallerAndExit(installerFile: File, exitVm: Boolean = true): Boolean {
        if (!installerFile.exists() || !installerFile.isFile) {
            Logger.error("UPDATE", "Installer file not found: ${installerFile.absolutePath}")
            return false
        }

        val os = detectOS()
        val path = installerFile.absolutePath

        val pb = when (os) {
            OS.Windows -> {
                if (path.endsWith(".exe", ignoreCase = true)) {
                    ProcessBuilder(path)
                } else if (path.endsWith(".msi", ignoreCase = true)) {
                    ProcessBuilder("msiexec.exe", "/i", path)
                } else {
                    ProcessBuilder("explorer.exe", "/select,", path)
                }
            }
            OS.MacOS -> {
                ProcessBuilder("open", path)
            }
            OS.Linux -> {
                if (path.endsWith(".AppImage", ignoreCase = true)) {
                    runCatching { installerFile.setExecutable(true, false) }
                    ProcessBuilder(path)
                } else if (path.endsWith(".deb", ignoreCase = true)) {
                    ProcessBuilder("xdg-open", path)
                } else {
                    ProcessBuilder("xdg-open", installerFile.parentFile.absolutePath)
                }
            }
        }

        return try {
            Logger.info("UPDATE", "Launching installer process: ${pb.command()}")
            pb.start()

            // Gracefully flush state before exiting
            runCatching { AppContext.config.save() }
            runCatching { AppContext.torrentManager.shutdown(saveResumeData = true, timeoutMs = 2000) }

            if (exitVm) {
                exitProcess(0)
            }
            true
        } catch (e: Throwable) {
            Logger.error("UPDATE", "Failed to launch installer process", e)
            false
        }
    }
}

