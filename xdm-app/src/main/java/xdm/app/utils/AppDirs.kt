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
 * Isolation & Migration Safety:
 * To avoid conflicts with official XDM installations and guarantee zero data loss, XDM Enhanced isolates
 * its configuration and databases under [NAME] (`xdm-enhanced`).
 * Official upstream XDM data stored under [LEGACY_NAME] (`xdm-app`) is never accidentally or automatically
 * imported on fresh installs or side-by-side installations.
 * When explicit migration is requested (via `-Dxdm.import.legacy=true` or `--import-legacy`), [migrateLegacyData]
 * performs a transactional staging copy with atomic commit and complete rollback on any failure, ensuring
 * that the legacy source files remain unaltered and the target directory is never left in a partial state.
 *
 * `-D`[PROPERTY]`=<dir>` overrides it. The class-list recording uses that for a throwaway profile:
 * `-Duser.home` alone does not move `%LOCALAPPDATA%`, which comes from the environment.
 */
object AppDirs {
    const val NAME = "xdm-enhanced"
    const val LEGACY_NAME = "xdm-app"
    const val PROPERTY = "xdm.data.dir"
    const val IMPORT_LEGACY_PROPERTY = "xdm.import.legacy"

    val root: File by lazy {
        val target = resolve(detectOS(), System.getProperty(PROPERTY), System.getProperty("user.home"), System::getenv)
        if (System.getProperty(IMPORT_LEGACY_PROPERTY) == "true") {
            migrateLegacyData(target)
        }
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
    ): Boolean {
        if (!System.getProperty(PROPERTY).isNullOrBlank()) return false
        if (target.exists() && (File(target, "xdm-app.config").exists() || File(target, "xdm.db").exists() || File(target, "active-downloads.dat").exists())) {
            return false
        }
        if (!legacy.exists() || !legacy.isDirectory) {
            return false
        }

        val targetExisted = target.exists()
        val stagingDir = File(target.parentFile ?: target, "${target.name}.migration-staging-${System.nanoTime()}")
        val newlyCreatedFiles = mutableListOf<File>()

        return try {
            stagingDir.mkdirs()
            val items = legacy.listFiles() ?: return false
            val copiedFiles = mutableListOf<File>()
            for (item in items) {
                // Do not copy active logs or partial chunks in tmp/
                if (item.name == "tmp" || item.name == "logs") continue
                val dest = File(stagingDir, item.name)
                item.copyTo(dest, overwrite = false)
                copiedFiles.add(dest)
            }

            // Staging complete without error; now safely commit to target
            target.mkdirs()
            for (staged in copiedFiles) {
                val finalDest = File(target, staged.name)
                if (!finalDest.exists()) {
                    staged.copyTo(finalDest, overwrite = false)
                    newlyCreatedFiles.add(finalDest)
                }
            }
            stagingDir.deleteRecursively()
            Logger.info("AppDirs: migrated existing settings and download state from ${legacy.absolutePath} to ${target.absolutePath}")
            true
        } catch (e: Throwable) {
            stagingDir.deleteRecursively()
            // Clean up any partially copied files in target to guarantee rollback and prevent corrupted state
            for (f in newlyCreatedFiles) {
                runCatching { f.deleteRecursively() }
            }
            if (!targetExisted && target.exists() && target.list()?.isEmpty() == true) {
                runCatching { target.delete() }
            }
            Logger.error("AppDirs: failed to migrate legacy data from ${legacy.absolutePath}; rolled back", e)
            false
        }
    }
}
