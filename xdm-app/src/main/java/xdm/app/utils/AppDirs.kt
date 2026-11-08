package xdm.app.utils

import xdm.app.OS
import xdm.core.util.Logger
import java.io.File

/**
 * Where XDM Enhanced keeps its state (config, download lists, task state, logs and `tmp/` with the partial
 * downloads): the OS's per-user application data folder, named [NAME].
 *
 * - Windows: `%LOCALAPPDATA%\xdm-enhanced` - local, not roaming: partial downloads are large and machine bound.
 * - macOS: `~/Library/Application Support/xdm-enhanced`
 * - Linux: `$XDG_DATA_HOME/xdm-enhanced`, by default `~/.local/share/xdm-enhanced`. Not the cache dir: cleaners
 *   empty it, and `tmp/` holds the partial downloads.
 *
 * To avoid conflicts with official XDM installations and guarantee zero data loss, on first run
 * if [NAME] directory is not yet present, [migrateLegacyData] copies existing configuration and databases
 * from [LEGACY_NAME] (`xdm-app`) without deleting or altering the original official XDM files.
 *
 * `-D`[PROPERTY]`=<dir>` overrides it. The class-list recording uses that for a throwaway profile:
 * `-Duser.home` alone does not move `%LOCALAPPDATA%`, which comes from the environment.
 */
object AppDirs {
    const val NAME = "xdm-enhanced"
    const val LEGACY_NAME = "xdm-app"
    const val PROPERTY = "xdm.data.dir"

    val root: File by lazy {
        val target = resolve(detectOS(), System.getProperty(PROPERTY), System.getProperty("user.home"), System::getenv)
        migrateLegacyData(target)
        target
    }

    internal fun resolve(os: OS, override: String?, home: String, env: (String) -> String?): File =
        resolveNamed(NAME, os, override, home, env)

    internal fun resolveNamed(name: String, os: OS, override: String?, home: String, env: (String) -> String?): File {
        override?.takeIf { it.isNotBlank() }?.let { return File(it).absoluteFile }
        val base = when (os) {
            OS.Windows -> sequenceOf("LOCALAPPDATA", "APPDATA").mapNotNull(env).firstOrNull { it.isNotBlank() }
                ?.let(::File) ?: File(home)
            OS.MacOS -> File(home, "Library/Application Support")
            // The XDG spec ignores a relative XDG_DATA_HOME.
            OS.Linux -> env("XDG_DATA_HOME")?.takeIf { it.startsWith("/") || File(it).isAbsolute }?.let(::File)
                ?: File(home, ".local/share")
        }
        return File(base, name)
    }

    internal fun migrateLegacyData(
        target: File,
        legacy: File = resolveNamed(LEGACY_NAME, detectOS(), null, System.getProperty("user.home"), System::getenv)
    ) {
        if (!System.getProperty(PROPERTY).isNullOrBlank()) return
        if (target.exists() && (File(target, "xdm-app.config").exists() || File(target, "xdm.db").exists())) {
            return
        }
        if (legacy.exists() && legacy.isDirectory) {
            runCatching {
                target.mkdirs()
                val items = legacy.listFiles() ?: return@runCatching
                for (item in items) {
                    // Do not copy active logs or partial chunks in tmp/
                    if (item.name == "tmp" || item.name == "logs") continue
                    val dest = File(target, item.name)
                    if (!dest.exists()) {
                        item.copyTo(dest, overwrite = false)
                    }
                }
                Logger.info("AppDirs: migrated existing settings and download state from ${legacy.absolutePath} to ${target.absolutePath}")
            }.onFailure {
                Logger.error("AppDirs: failed to migrate legacy data from ${legacy.absolutePath}", it)
            }
        }
    }
}
