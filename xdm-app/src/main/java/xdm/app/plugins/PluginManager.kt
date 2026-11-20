package xdm.app.plugins

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xdm.app.AppContext
import xdm.app.utils.AppDirs
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.events.AppStateEvent
import xdm.plugin.api.events.EventTopics
import xdm.plugin.api.events.PostDownloadHook
import xdm.plugin.api.events.TaskLifecycleEvent
import xdm.plugin.api.models.DownloadInfo
import xdm.plugin.api.models.PluginAction
import xdm.plugin.api.models.PluginCapability
import xdm.plugin.api.models.PluginMetadata
import xdm.plugin.api.ui.ContextMenuContributor
import xdm.plugin.api.ui.SettingsPanelContributor
import xdm.plugin.api.ui.ToolbarContributor
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipFile

/**
 * State record tracking crash loops, startup failure counts, and quarantined plugins.
 */
@Serializable
data class CrashRecoveryState(
    var consecutiveStartupFailures: Int = 0,
    val pluginFailureCounts: MutableMap<String, Int> = mutableMapOf(),
    val quarantinedPlugins: MutableSet<String> = mutableSetOf()
)

/**
 * Diagnostic record capturing failure diagnostics without persisting secrets.
 */
@Serializable
data class PluginDiagnosticRecord(
    val pluginId: String,
    val timestamp: Long,
    val faultReason: String,
    val lastAction: String = "activate"
)

/**
 * Host lifecycle and extension manager for XDM Enhanced plugins.
 * Manages discovery, isolated class loading, user trust verification, fault isolation, and event dispatching.
 */
object PluginManager {
    const val API_VERSION = "1.1.0"
    const val INIT_TIMEOUT_MS = 5000L
    const val SHUTDOWN_TIMEOUT_MS = 3000L
    const val MAX_PLUGIN_FAILURES = 3
    const val CRASH_RECOVERY_FILE = "crash-recovery.json"
    const val DIAGNOSTICS_FILE = "plugin-diagnostics.json"
    const val PRESERVED_DATA_DIR = ".preserved-data"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    var pluginsDir: File = File(AppDirs.root, "plugins")
    var isSafeMode: Boolean = false
        internal set

    val installedPlugins = CopyOnWriteArrayList<LoadedPlugin>()
    var requiresRestart: Boolean = false
        internal set

    fun resetRestartFlag() {
        requiresRestart = false
        installedPlugins.forEach {
            if (it.status == PluginStatus.NEEDS_RESTART) {
                it.status = PluginStatus.DISABLED
            }
        }
        notifyStateChanged()
    }

    fun interface PluginStateChangeListener {
        fun onPluginStateChanged()
    }

    private val stateListeners = CopyOnWriteArrayList<PluginStateChangeListener>()

    fun addStateListener(listener: PluginStateChangeListener) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: PluginStateChangeListener) {
        stateListeners.remove(listener)
    }

    internal fun notifyStateChanged() {
        VpnPolicyManager.syncActiveProviderListener()
        PluginEventBus.publish(EventTopics.APP_STATE_CHANGED, AppStateEvent(AppStateEvent.State.STATE_CHANGED, isSafeMode))
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            stateListeners.forEach { runCatching { it.onPluginStateChanged() } }
        } else {
            javax.swing.SwingUtilities.invokeLater {
                stateListeners.forEach { runCatching { it.onPluginStateChanged() } }
            }
        }
    }

    fun loadCrashRecoveryState(): CrashRecoveryState {
        val file = File(pluginsDir, CRASH_RECOVERY_FILE)
        if (!file.exists()) return CrashRecoveryState()
        return try {
            json.decodeFromString<CrashRecoveryState>(file.readText())
        } catch (e: Exception) {
            Logger.error("Failed to read crash recovery state: ${e.message}")
            CrashRecoveryState()
        }
    }

    fun saveCrashRecoveryState(state: CrashRecoveryState) {
        val file = File(pluginsDir, CRASH_RECOVERY_FILE)
        try {
            file.writeText(json.encodeToString(state))
        } catch (e: Exception) {
            Logger.error("Failed to save crash recovery state: ${e.message}")
        }
    }

    fun markStartupSuccessful() {
        val state = loadCrashRecoveryState()
        if (state.consecutiveStartupFailures > 0) {
            state.consecutiveStartupFailures = 0
            saveCrashRecoveryState(state)
        }
    }

    fun sanitizeDiagnostics(message: String): String {
        var sanitized = message
        // 1. URLs with credentials: http(s)://user:password@host
        sanitized = sanitized.replace(Regex("(https?://[^:]+):[^@\\s]+@"), "$1:***@")
        // 2. Bearer tokens: Bearer <token>
        sanitized = sanitized.replace(Regex("(?i)(bearer\\s+)[a-zA-Z0-9_\\-\\.]+"), "$1***")
        // 3. Sensitive key-value pairs (password, token, secret, apiKey, etc.)
        sanitized = sanitized.replace(Regex("(?i)(password|token|secret|api[_-]?key|auth|access_token|credential)\\s*[:=]\\s*['\"]?[^&\\s'\",]+['\"]?"), "$1=***")
        return sanitized
    }

    fun recordDiagnostic(pluginId: String, faultReason: String, action: String = "activate") {
        val sanitizedReason = sanitizeDiagnostics(faultReason)
        val record = PluginDiagnosticRecord(
            pluginId = pluginId,
            timestamp = System.currentTimeMillis(),
            faultReason = sanitizedReason,
            lastAction = action
        )
        val file = File(pluginsDir, DIAGNOSTICS_FILE)
        val records = getDiagnostics().toMutableList()
        records.add(record)
        val trimmed = if (records.size > 100) records.takeLast(100) else records
        try {
            file.writeText(json.encodeToString(trimmed))
        } catch (e: Exception) {
            Logger.error("Failed to write diagnostic record: ${e.message}")
        }
    }

    fun getDiagnostics(): List<PluginDiagnosticRecord> {
        val file = File(pluginsDir, DIAGNOSTICS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<PluginDiagnosticRecord>>(file.readText())
        } catch (e: Exception) {
            Logger.error("Failed to read plugin diagnostics: ${e.message}")
            emptyList()
        }
    }

    private fun handlePluginFailure(plugin: LoadedPlugin, state: CrashRecoveryState, reason: String, action: String = "activate") {
        recordDiagnostic(plugin.manifest.id, reason, action)
        val failCount = (state.pluginFailureCounts[plugin.manifest.id] ?: 0) + 1
        state.pluginFailureCounts[plugin.manifest.id] = failCount
        if (failCount >= MAX_PLUGIN_FAILURES) {
            state.quarantinedPlugins.add(plugin.manifest.id)
            val enabled = AppContext.config.enabledPlugins.toMutableList()
            enabled.remove(plugin.manifest.id)
            AppContext.config.enabledPlugins = enabled
            runCatching { AppContext.config.save() }
            Logger.error("Plugin '${plugin.manifest.id}' failed $failCount consecutive times and is now quarantined.")
        }
        saveCrashRecoveryState(state)
    }

    fun checkDependencies(plugin: LoadedPlugin, candidatePlugins: List<LoadedPlugin>): String? {
        for (dep in plugin.manifest.dependencies) {
            val trimmed = dep.trim()
            if (trimmed.isBlank()) continue
            val parts = if (trimmed.contains("@")) trimmed.split("@", limit = 2)
            else if (trimmed.contains(":")) trimmed.split(":", limit = 2)
            else listOf(trimmed)
            val depId = parts[0].trim()
            val versionConstraint = parts.getOrNull(1)?.trim()

            val target = candidatePlugins.firstOrNull { it.manifest.id == depId }
            if (target == null) {
                return "Missing required dependency plugin '$depId'."
            }
            if (target.status == PluginStatus.FAULTED || target.status == PluginStatus.INCOMPATIBLE) {
                return "Required dependency plugin '$depId' is faulted: ${target.faultReason ?: "Unknown error"}"
            }
            if (!versionConstraint.isNullOrBlank()) {
                val check = PluginManifestValidator.evaluateVersionRange(target.manifest.version, versionConstraint)
                if (!check.first) {
                    return "Dependency '$depId' version '${target.manifest.version}' does not satisfy constraint '$versionConstraint'."
                }
            }
        }
        return null
    }

    fun sortPluginsByDependencies(plugins: List<LoadedPlugin>): List<LoadedPlugin> {
        val pluginMap = plugins.associateBy { it.manifest.id }
        val inDegree = mutableMapOf<String, Int>()
        val dependents = mutableMapOf<String, MutableList<String>>()

        plugins.forEach { p ->
            inDegree[p.manifest.id] = 0
            dependents[p.manifest.id] = mutableListOf()
        }

        plugins.forEach { p ->
            p.manifest.dependencies.forEach { dep ->
                val depId = dep.split("@", ":", limit = 2)[0].trim()
                if (pluginMap.containsKey(depId)) {
                    inDegree[p.manifest.id] = (inDegree[p.manifest.id] ?: 0) + 1
                    dependents[depId]?.add(p.manifest.id)
                }
            }
        }

        val queue = ArrayDeque<String>()
        inDegree.filter { it.value == 0 }.forEach { queue.add(it.key) }

        val sortedList = mutableListOf<LoadedPlugin>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            pluginMap[id]?.let { sortedList.add(it) }
            dependents[id]?.forEach { dependentId ->
                val newDegree = (inDegree[dependentId] ?: 1) - 1
                inDegree[dependentId] = newDegree
                if (newDegree == 0) {
                    queue.add(dependentId)
                }
            }
        }

        if (sortedList.size < plugins.size) {
            val remaining = plugins.filter { !sortedList.contains(it) }
            remaining.forEach { p ->
                p.status = PluginStatus.FAULTED
                p.faultReason = "Cyclic dependency detected involving plugin '${p.manifest.id}'."
                sortedList.add(p)
            }
        }

        return sortedList
    }

    /**
     * Initializes the plugin subsystem.
     * When [safeMode] is true or repeated startup crashes are detected,
     * third-party plugins are quarantined and will not be activated.
     */
    fun init(safeMode: Boolean = false) {
        installedPlugins.clear()

        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        val recoveryState = loadCrashRecoveryState()
        if (safeMode || recoveryState.consecutiveStartupFailures >= 3) {
            isSafeMode = true
            Logger.info("PluginManager: Running in Safe Mode. Consecutive startup failures: ${recoveryState.consecutiveStartupFailures}.")
        } else {
            isSafeMode = false
            recoveryState.consecutiveStartupFailures++
            saveCrashRecoveryState(recoveryState)
        }

        discoverPlugins()

        if (!isSafeMode) {
            val enabledSet = AppContext.config.enabledPlugins.toSet()
            val trustedSet = AppContext.config.trustedPlugins.toSet()

            val candidates = installedPlugins.filter {
                trustedSet.contains(it.manifest.id) && enabledSet.contains(it.manifest.id)
            }

            val sorted = sortPluginsByDependencies(candidates)

            for (plugin in sorted) {
                if (plugin.status != PluginStatus.FAULTED) {
                    activatePlugin(plugin)
                }
            }

            for (plugin in installedPlugins) {
                if (plugin.status == PluginStatus.DISCOVERED) {
                    if (!trustedSet.contains(plugin.manifest.id)) {
                        plugin.status = PluginStatus.UNTRUSTED
                    } else if (!enabledSet.contains(plugin.manifest.id)) {
                        plugin.status = PluginStatus.DISABLED
                    }
                }
            }
        } else {
            Logger.info("PluginManager: Running in Safe Mode. All third-party plugins are quarantined.")
            installedPlugins.forEach { it.status = PluginStatus.DISABLED }
        }

        notifyStateChanged()

        if (isSafeMode) {
            PluginEventBus.publish(EventTopics.APP_SAFE_MODE, AppStateEvent(AppStateEvent.State.SAFE_MODE, true))
        } else {
            PluginEventBus.publish(EventTopics.APP_STARTUP, AppStateEvent(AppStateEvent.State.STARTUP, false))
        }
    }

    /**
     * Scans [pluginsDir] for unpacked directories and archive packages.
     */
    fun discoverPlugins() {
        val dirs = pluginsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (dir in dirs) {
            val manifestFile = File(dir, "plugin.json")
            if (manifestFile.exists() && manifestFile.isFile) {
                try {
                    val rawJson = manifestFile.readText()
                    val manifest = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }.decodeFromString<PluginManifest>(rawJson)

                    val sha = runCatching {
                        val jar = dir.listFiles { f -> f.name.endsWith(".jar") }?.firstOrNull()
                        if (jar != null) PluginManifestValidator.computeSha256(jar) else ""
                    }.getOrDefault("")

                    val existing = installedPlugins.firstOrNull { it.manifest.id == manifest.id }
                    if (existing != null) {
                        val faultReason = "Duplicate plugin ID '${manifest.id}' detected from ${dir.name}. Already registered from ${existing.pluginDir.name}."
                        val loaded = LoadedPlugin(
                            manifest = manifest,
                            pluginDir = dir,
                            archiveFile = dir.listFiles { f -> f.name.endsWith(".jar") }?.firstOrNull(),
                            status = PluginStatus.FAULTED,
                            faultReason = faultReason,
                            sha256 = sha
                        )
                        installedPlugins.add(loaded)
                        Logger.error("Plugin discovery rejected: $faultReason")
                        continue
                    }

                    val validation = PluginManifestValidator.validateManifest(manifest)
                    if (validation.isValid) {
                        val loaded = LoadedPlugin(
                            manifest = manifest,
                            pluginDir = dir,
                            archiveFile = dir.listFiles { f -> f.name.endsWith(".jar") }?.firstOrNull(),
                            status = PluginStatus.DISCOVERED,
                            sha256 = sha
                        )
                        installedPlugins.add(loaded)
                    } else {
                        val status = if (validation.errors.any { it.contains("API version") || it.contains("application version") }) {
                            PluginStatus.INCOMPATIBLE
                        } else {
                            PluginStatus.FAULTED
                        }
                        val loaded = LoadedPlugin(
                            manifest = manifest,
                            pluginDir = dir,
                            archiveFile = dir.listFiles { f -> f.name.endsWith(".jar") }?.firstOrNull(),
                            status = status,
                            faultReason = validation.errors.joinToString("; "),
                            sha256 = sha
                        )
                        installedPlugins.add(loaded)
                        Logger.info("[WARN] Discovered plugin at ${dir.name} is ${status.displayName.lowercase()}: ${validation.errors}")
                    }
                } catch (e: Exception) {
                    Logger.error("Failed to load plugin manifest from ${dir.absolutePath}", e)
                    val dummyManifest = PluginManifest(
                        id = dir.name,
                        name = dir.name,
                        version = "unknown",
                        entryPoint = ""
                    )
                    val loaded = LoadedPlugin(
                        manifest = dummyManifest,
                        pluginDir = dir,
                        archiveFile = dir.listFiles { f -> f.name.endsWith(".jar") }?.firstOrNull(),
                        status = PluginStatus.FAULTED,
                        faultReason = "Failed to parse plugin.json: ${e.message}"
                    )
                    installedPlugins.add(loaded)
                }
            }
        }
    }

    /**
     * Activates an installed plugin using an isolated URLClassLoader and bounded execution timeout.
     */
    fun activatePlugin(plugin: LoadedPlugin): Boolean {
        if (isSafeMode) {
            plugin.status = PluginStatus.DISABLED
            return false
        }

        if (plugin.status == PluginStatus.INCOMPATIBLE) {
            Logger.error("Plugin ${plugin.manifest.id} activation rejected: incompatible API version.")
            return false
        }

        if (plugin.status == PluginStatus.UNTRUSTED && !AppContext.config.trustedPlugins.contains(plugin.manifest.id)) {
            plugin.faultReason = "Permission denied: plugin has not received explicit user approval."
            Logger.error("Plugin ${plugin.manifest.id} activation rejected: untrusted.")
            return false
        }

        val recoveryState = loadCrashRecoveryState()
        if (recoveryState.quarantinedPlugins.contains(plugin.manifest.id)) {
            plugin.status = PluginStatus.FAULTED
            plugin.faultReason = "Plugin is quarantined due to repeated crashes. Safe Mode recovery required."
            Logger.error("Plugin ${plugin.manifest.id} activation rejected: quarantined.")
            return false
        }

        if (plugin.manifest.targetPlatforms.isNotEmpty() && !PluginManifestValidator.isPlatformSupported(plugin.manifest.targetPlatforms)) {
            plugin.status = PluginStatus.FAULTED
            plugin.faultReason = "Plugin does not support current platform '${PluginManifestValidator.getCurrentPlatform()}'. Supported: ${plugin.manifest.targetPlatforms.joinToString(", ")}."
            recordDiagnostic(plugin.manifest.id, plugin.faultReason!!)
            Logger.error("Plugin ${plugin.manifest.id} faulted: ${plugin.faultReason}")
            return false
        }

        val depError = checkDependencies(plugin, installedPlugins)
        if (depError != null) {
            plugin.status = PluginStatus.FAULTED
            plugin.faultReason = depError
            recordDiagnostic(plugin.manifest.id, depError, "dependency-check")
            Logger.error("Plugin ${plugin.manifest.id} faulted: $depError")
            notifyStateChanged()
            return false
        }

        try {
            // Collect class path URLs (the plugin directory and any JARs inside it)
            val urls = mutableListOf<URL>()
            urls.add(plugin.pluginDir.toURI().toURL())
            plugin.pluginDir.listFiles { f -> f.name.endsWith(".jar") }?.forEach { jar ->
                urls.add(jar.toURI().toURL())
            }

            // Parent classloader: only exposed to XdmPlugin API interfaces and public standard library
            val parentCl = XdmPlugin::class.java.classLoader
            val classLoader = PluginFilteringClassLoader(urls.toTypedArray(), parentCl, plugin.manifest.id)
            plugin.classLoader = classLoader

            val pluginClass = Class.forName(plugin.manifest.entryPoint, true, classLoader)
            if (!XdmPlugin::class.java.isAssignableFrom(pluginClass)) {
                plugin.status = PluginStatus.FAULTED
                plugin.faultReason = "Entry point class '${plugin.manifest.entryPoint}' does not implement XdmPlugin."
                Logger.error("Plugin ${plugin.manifest.id} faulted: ${plugin.faultReason}")
                handlePluginFailure(plugin, recoveryState, plugin.faultReason!!)
                return false
            }

            val instance = pluginClass.getDeclaredConstructor().newInstance() as XdmPlugin
            plugin.instance = instance

            val metadata = PluginMetadata(
                plugin.manifest.id,
                plugin.manifest.name,
                plugin.manifest.version,
                plugin.manifest.author,
                plugin.manifest.description,
                plugin.manifest.minAppVersion,
                plugin.manifest.minApiVersion,
                plugin.manifest.entryPoint,
                plugin.manifest.capabilities.toSet(),
                plugin.manifest.dependencies,
                plugin.manifest.license,
                plugin.manifest.homepage,
                plugin.sha256,
                plugin.isBuiltIn
            )

            val context = PluginHostContext(plugin, metadata)
            plugin.context = context

            // Bounded lifecycle initialization with timeout
            val initFuture = PluginHostContext.pluginExecutor.submit(Callable {
                instance.init(context)
            })

            try {
                initFuture.get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                plugin.status = PluginStatus.ACTIVE
                plugin.faultReason = null
                if (recoveryState.pluginFailureCounts.containsKey(plugin.manifest.id)) {
                    recoveryState.pluginFailureCounts.remove(plugin.manifest.id)
                    saveCrashRecoveryState(recoveryState)
                }
                Logger.info("Plugin ${plugin.manifest.id} (${plugin.manifest.name} v${plugin.manifest.version}) activated successfully.")
                notifyStateChanged()
                return true
            } catch (te: TimeoutException) {
                initFuture.cancel(true)
                plugin.status = PluginStatus.FAULTED
                plugin.faultReason = "Plugin initialization timed out after ${INIT_TIMEOUT_MS}ms."
                Logger.error("Plugin ${plugin.manifest.id} activation timed out: ${plugin.faultReason}")
                handlePluginFailure(plugin, recoveryState, plugin.faultReason!!)
                deactivatePlugin(plugin, setStatus = false)
                notifyStateChanged()
                return false
            } catch (ee: ExecutionException) {
                val cause = ee.cause ?: ee
                plugin.status = PluginStatus.FAULTED
                plugin.faultReason = cause.message ?: cause.toString()
                Logger.error("Plugin ${plugin.manifest.id} crashed during init: ${plugin.faultReason}", cause)
                handlePluginFailure(plugin, recoveryState, plugin.faultReason!!)
                deactivatePlugin(plugin, setStatus = false)
                notifyStateChanged()
                return false
            }
        } catch (t: Throwable) {
            plugin.status = PluginStatus.FAULTED
            plugin.faultReason = t.message ?: t.toString()
            Logger.error("Failed to activate plugin ${plugin.manifest.id}: ${plugin.faultReason}", t)
            handlePluginFailure(plugin, recoveryState, plugin.faultReason!!)
            deactivatePlugin(plugin, setStatus = false)
            notifyStateChanged()
            return false
        }
    }

    /**
     * Deactivates a running plugin and frees allocated resources.
     */
    fun deactivatePlugin(plugin: LoadedPlugin, setStatus: Boolean = true) {
        var shutdownTimedOut = false
        if (plugin.instance != null) {
            val shutdownFuture = PluginHostContext.pluginExecutor.submit(Callable {
                plugin.instance?.shutdown()
            })
            try {
                shutdownFuture.get(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (te: TimeoutException) {
                shutdownFuture.cancel(true)
                shutdownTimedOut = true
                Logger.error("Plugin ${plugin.manifest.id} shutdown timed out after ${SHUTDOWN_TIMEOUT_MS}ms")
            } catch (t: Throwable) {
                Logger.error("Error shutting down plugin ${plugin.manifest.id}", t)
            }
        }

        plugin.context?.clearRegistrations()
        plugin.instance = null
        plugin.context = null

        val hasNativeBinaries = try {
            plugin.pluginDir.walkTopDown().any { file ->
                val ext = file.extension.lowercase()
                ext == "dll" || ext == "so" || ext == "dylib"
            }
        } catch (_: Exception) {
            false
        }

        try {
            plugin.classLoader?.close()
        } catch (e: Exception) {
            Logger.error("Error closing classloader for ${plugin.manifest.id}", e)
        }
        plugin.classLoader = null

        if (hasNativeBinaries || shutdownTimedOut) {
            requiresRestart = true
            if (setStatus) {
                plugin.status = PluginStatus.NEEDS_RESTART
                notifyStateChanged()
            }
        } else if (setStatus) {
            plugin.status = PluginStatus.DISABLED
            notifyStateChanged()
        }
    }

    /**
     * Installs a plugin package from an archive (.xdmplugin or .jar).
     */
    fun installPlugin(archiveFile: File): PluginManifestValidator.ValidationResult {
        val validation = PluginManifestValidator.validateArchive(archiveFile)
        if (!validation.isValid || validation.manifest == null) {
            return validation
        }

        val manifest = validation.manifest
        val targetDir = File(pluginsDir, manifest.id)
        val dataDir = File(targetDir, "data")
        val dataBackup = if (dataDir.exists() && dataDir.isDirectory) {
            val tmp = File.createTempFile("xdm-plugin-data-${manifest.id}", ".bak")
            tmp.delete()
            tmp.mkdirs()
            dataDir.copyRecursively(tmp, overwrite = true)
            tmp
        } else null

        if (targetDir.exists()) {
            // If already installed, shut it down before overwriting
            installedPlugins.firstOrNull { it.manifest.id == manifest.id }?.let { existing ->
                deactivatePlugin(existing)
                installedPlugins.remove(existing)
            }
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        // Extract archive contents into target directory with Zip Slip, symlink, and Zip bomb protection
        try {
            var extractedTotalBytes = 0L
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val normalizedName = entry.name.replace('\\', '/')
                    if (normalizedName.contains("..") || normalizedName.startsWith("/") || normalizedName.contains(":") || normalizedName.startsWith("~")) {
                        throw SecurityException("Zip Slip attack detected in archive entry: ${entry.name}")
                    }

                    val destFile = File(targetDir, normalizedName).canonicalFile
                    if (!destFile.toPath().startsWith(targetDir.canonicalFile.toPath())) {
                        throw SecurityException("Zip Slip attack detected in archive entry: ${entry.name}")
                    }
                    if (java.nio.file.Files.isSymbolicLink(destFile.toPath()) ||
                        (destFile.parentFile != null && java.nio.file.Files.isSymbolicLink(destFile.parentFile.toPath()))) {
                        throw SecurityException("Symbolic links are prohibited in plugin archives: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        val ext = destFile.extension.lowercase()
                        if (PluginManifestValidator.DISALLOWED_EXTENSIONS.contains(ext)) {
                            throw SecurityException("Disallowed executable extension detected: ${entry.name}")
                        }
                        var entryBytes = 0L
                        zip.getInputStream(entry).use { input ->
                            java.nio.file.Files.newOutputStream(
                                destFile.toPath(),
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                                java.nio.file.LinkOption.NOFOLLOW_LINKS
                            ).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead = input.read(buffer)
                                while (bytesRead != -1) {
                                    entryBytes += bytesRead
                                    extractedTotalBytes += bytesRead
                                    if (entryBytes > PluginManifestValidator.MAX_ENTRY_BYTES) {
                                        throw SecurityException("Decompressed entry '${entry.name}' exceeds maximum allowed size of ${PluginManifestValidator.MAX_ENTRY_BYTES} bytes (Zip bomb defense).")
                                    }
                                    if (extractedTotalBytes > PluginManifestValidator.MAX_UNCOMPRESSED_BYTES) {
                                        throw SecurityException("Total decompressed archive size exceeds limit of ${PluginManifestValidator.MAX_UNCOMPRESSED_BYTES} bytes (Zip bomb defense).")
                                    }
                                    output.write(buffer, 0, bytesRead)
                                    bytesRead = input.read(buffer)
                                }
                            }
                        }
                    }
                }
            }

            // Restore preserved configuration and plugin data directory on upgrade or re-installation
            val preservedDir = File(pluginsDir, "$PRESERVED_DATA_DIR/${manifest.id}")
            if (dataBackup != null && dataBackup.exists()) {
                val restoredData = File(targetDir, "data")
                restoredData.mkdirs()
                dataBackup.copyRecursively(restoredData, overwrite = true)
            } else if (preservedDir.exists() && preservedDir.isDirectory) {
                val restoredData = File(targetDir, "data")
                restoredData.mkdirs()
                preservedDir.copyRecursively(restoredData, overwrite = true)
                preservedDir.deleteRecursively()
                Logger.info("Restored preserved data for plugin '${manifest.id}'")
            }
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            return PluginManifestValidator.ValidationResult(
                false,
                listOf("Failed to extract plugin package: ${e.message}")
            )
        } finally {
            dataBackup?.deleteRecursively()
        }

        val loaded = LoadedPlugin(
            manifest = manifest,
            pluginDir = targetDir,
            archiveFile = archiveFile,
            status = PluginStatus.UNTRUSTED,
            sha256 = validation.sha256
        )
        installedPlugins.add(loaded)
        notifyStateChanged()
        return validation
    }

    /**
     * Uninstalls a plugin by ID, removing its directory and configuration.
     * Optionally preserves plugin data in `.preserved-data/<pluginId>`.
     */
    fun uninstallPlugin(pluginId: String, preserveData: Boolean = false): Boolean {
        val plugin = installedPlugins.firstOrNull { it.manifest.id == pluginId } ?: return false
        deactivatePlugin(plugin)
        installedPlugins.remove(plugin)

        if (preserveData) {
            val dataDir = File(plugin.pluginDir, "data")
            if (dataDir.exists() && dataDir.isDirectory) {
                val preservedDir = File(pluginsDir, "$PRESERVED_DATA_DIR/$pluginId")
                preservedDir.mkdirs()
                dataDir.copyRecursively(preservedDir, overwrite = true)
                Logger.info("Preserved data for plugin '$pluginId' in ${preservedDir.absolutePath}")
            }
        }

        val success = plugin.pluginDir.deleteRecursively()

        val enabled = AppContext.config.enabledPlugins.toMutableList()
        enabled.remove(pluginId)
        AppContext.config.enabledPlugins = enabled

        val trusted = AppContext.config.trustedPlugins.toMutableList()
        trusted.remove(pluginId)
        AppContext.config.trustedPlugins = trusted
        runCatching { AppContext.config.save() }

        val state = loadCrashRecoveryState()
        if (state.pluginFailureCounts.remove(pluginId) != null || state.quarantinedPlugins.remove(pluginId)) {
            saveCrashRecoveryState(state)
        }

        notifyStateChanged()
        return success
    }

    /**
     * Marks a plugin as trusted and enables it.
     */
    fun trustAndEnablePlugin(pluginId: String): Boolean {
        val plugin = installedPlugins.firstOrNull { it.manifest.id == pluginId } ?: return false

        val trusted = AppContext.config.trustedPlugins.toMutableList()
        if (!trusted.contains(pluginId)) {
            trusted.add(pluginId)
            AppContext.config.trustedPlugins = trusted
        }

        val enabled = AppContext.config.enabledPlugins.toMutableList()
        if (!enabled.contains(pluginId)) {
            enabled.add(pluginId)
            AppContext.config.enabledPlugins = enabled
        }
        AppContext.config.save()

        return activatePlugin(plugin)
    }

    /**
     * Enables an already-trusted plugin.
     */
    fun enablePlugin(pluginId: String): Boolean {
        val plugin = installedPlugins.firstOrNull { it.manifest.id == pluginId } ?: return false
        if (!AppContext.config.trustedPlugins.contains(pluginId)) {
            return false
        }

        val enabled = AppContext.config.enabledPlugins.toMutableList()
        if (!enabled.contains(pluginId)) {
            enabled.add(pluginId)
            AppContext.config.enabledPlugins = enabled
            AppContext.config.save()
        }

        return activatePlugin(plugin)
    }

    /**
     * Disables a plugin.
     */
    fun disablePlugin(pluginId: String) {
        val plugin = installedPlugins.firstOrNull { it.manifest.id == pluginId } ?: return
        deactivatePlugin(plugin)

        val enabled = AppContext.config.enabledPlugins.toMutableList()
        enabled.remove(pluginId)
        AppContext.config.enabledPlugins = enabled
        AppContext.config.save()
    }

    /**
     * Notifies all active plugins when a download completes.
     * Sequentially executes PostDownloadProcessors (ordered by getOrder()) allowing file inspection/transformation,
     * followed by observational PostDownloadHooks.
     * Fault isolation: if a plugin hook or processor throws an exception, it is logged and isolated,
     * keeping the download and database unaffected.
     */
    fun notifyDownloadCompleted(filePath: String, id: Long) {
        val dbRec = if (AppContext.hasDb && id > 0) AppContext.db.getById(id) else null
        val taskInfo = if (id > 0) runCatching { AppContext.taskInfoDB.getHttpTask(id) }.getOrNull() else null
        val sourceUrl = taskInfo?.url ?: ""
        val categoryId = dbRec?.categoryId ?: "None"
        var currentFile = File(filePath)
        val initialParent = currentFile.parentFile?.canonicalFile

        var info = DownloadInfo(
            id,
            sourceUrl,
            currentFile.name,
            currentFile.absolutePath,
            currentFile.length(),
            currentFile.length(),
            100,
            0f,
            "FINISHED",
            categoryId,
            System.currentTimeMillis()
        )

        if (installedPlugins.isNotEmpty()) {
            // 1. Run PostDownloadProcessors (sorted by getOrder() ascending)
            val processors = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.processors.PostDownloadProcessor>>()
            for (plugin in installedPlugins) {
                if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                    for (processor in plugin.context!!.postDownloadProcessors) {
                        processors.add(plugin to processor)
                    }
                }
            }
            processors.sortBy { it.second.order }

            for ((plugin, processor) in processors) {
                try {
                    if (processor.canProcess(info, currentFile)) {
                        val result = processor.process(info, currentFile)
                        if (result != null) {
                            when (result.status) {
                                xdm.plugin.api.processors.ProcessingResult.Status.SUCCESS,
                                xdm.plugin.api.processors.ProcessingResult.Status.SKIPPED -> {
                                    // continue processing pipeline
                                }
                                xdm.plugin.api.processors.ProcessingResult.Status.MODIFIED -> {
                                    if (result.resultingFile != null && result.resultingFile.exists()) {
                                        val resultCanonical = result.resultingFile.canonicalPath
                                        val parentCanonical = initialParent?.canonicalPath
                                        if (parentCanonical != null &&
                                            (resultCanonical == parentCanonical || resultCanonical.startsWith(parentCanonical + File.separator))) {
                                            currentFile = result.resultingFile
                                            info = DownloadInfo(
                                                id,
                                                info.url,
                                                currentFile.name,
                                                currentFile.absolutePath,
                                                currentFile.length(),
                                                currentFile.length(),
                                                100,
                                                0f,
                                                "FINISHED",
                                                info.category,
                                                System.currentTimeMillis()
                                            )
                                        } else {
                                            Logger.error("[SECURITY] PostDownloadProcessor '${processor.name}' attempted path traversal: '$resultCanonical' is outside '$parentCanonical'")
                                        }
                                    }
                                }
                                xdm.plugin.api.processors.ProcessingResult.Status.REJECTED -> {
                                    Logger.info("[WARN] PostDownloadProcessor '${processor.name}' in plugin '${plugin.manifest.id}' rejected file: ${result.message}")
                                    break
                                }
                                else -> {}
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Logger.error("PostDownloadProcessor '${processor.name}' error in plugin ${plugin.manifest.id}: ${t.message}", t)
                }
            }

            // 2. Run observational PostDownloadHooks
            for (plugin in installedPlugins) {
                if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                    for (hook in plugin.context!!.postDownloadHooks) {
                        try {
                            hook.onDownloadCompleted(info)
                        } catch (t: Throwable) {
                            Logger.error("PostDownloadHook error in plugin ${plugin.manifest.id}: ${t.message}", t)
                        }
                    }
                }
            }
        }

        // 3. Publish DOWNLOAD_COMPLETED event to PluginEventBus
        try {
            PluginEventBus.publish(
                EventTopics.DOWNLOAD_COMPLETED,
                TaskLifecycleEvent(
                    TaskLifecycleEvent.Type.COMPLETED,
                    info
                )
            )
        } catch (t: Throwable) {
            Logger.error("Failed to publish DOWNLOAD_COMPLETED event for task $id: ${t.message}", t)
        }
    }

    /**
     * Collects context menu actions contributed by active plugins for the selected downloads.
     */
    fun getContextMenuActions(selectedDownloads: List<DownloadInfo>): List<PluginAction> {
        val actions = mutableListOf<PluginAction>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (contributor in plugin.context!!.contextMenuContributors) {
                    try {
                        val contribActions = contributor.getContextMenuActions(selectedDownloads)
                        if (contribActions != null) {
                            actions.addAll(contribActions)
                        }
                    } catch (t: Throwable) {
                        Logger.error("ContextMenuContributor error in plugin ${plugin.manifest.id}", t)
                    }
                }
            }
        }
        return actions
    }

    /**
     * Collects toolbar actions contributed by active plugins.
     */
    fun getToolbarActions(): List<PluginAction> {
        val actions = mutableListOf<PluginAction>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (contributor in plugin.context!!.toolbarContributors) {
                    try {
                        val contribActions = contributor.getToolbarActions()
                        if (contribActions != null) {
                            actions.addAll(contribActions)
                        }
                    } catch (t: Throwable) {
                        Logger.error("ToolbarContributor error in plugin ${plugin.manifest.id}", t)
                    }
                }
            }
        }
        return actions
    }

    /**
     * Collects custom settings panels contributed by active plugins.
     */
    fun getSettingsPanels(): List<Pair<LoadedPlugin, SettingsPanelContributor>> {
        val panels = mutableListOf<Pair<LoadedPlugin, SettingsPanelContributor>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (panel in plugin.context!!.settingsPanels) {
                    panels.add(plugin to panel)
                }
            }
        }
        return panels
    }

    /**
     * Resolves an active download provider capable of handling the specified URL.
     */
    fun findDownloadProvider(url: String): Pair<LoadedPlugin, xdm.plugin.api.download.DownloadProvider>? {
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (provider in plugin.context!!.downloadProviders) {
                    try {
                        if (provider.canHandle(url)) {
                            return plugin to provider
                        }
                    } catch (t: Throwable) {
                        Logger.error("DownloadProvider.canHandle error in plugin ${plugin.manifest.id}", t)
                    }
                }
            }
        }
        return null
    }

    /**
     * Collects all active download providers.
     */
    fun getAllDownloadProviders(): List<Pair<LoadedPlugin, xdm.plugin.api.download.DownloadProvider>> {
        val providers = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.download.DownloadProvider>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (provider in plugin.context!!.downloadProviders) {
                    providers.add(plugin to provider)
                }
            }
        }
        return providers
    }

    /**
     * Collects all active URL recognizers.
     */
    fun getAllUrlRecognizers(): List<Pair<LoadedPlugin, xdm.plugin.api.url.UrlRecognizer>> {
        val recognizers = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.url.UrlRecognizer>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (rec in plugin.context!!.urlRecognizers) {
                    recognizers.add(plugin to rec)
                }
            }
        }
        return recognizers
    }

    /**
     * Resolves an active URL recognizer capable of recognizing the specified URL.
     */
    fun findUrlRecognizer(url: String): Pair<LoadedPlugin, xdm.plugin.api.url.UrlRecognizer>? {
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (recognizer in plugin.context!!.urlRecognizers) {
                    try {
                        if (recognizer.canRecognize(url)) {
                            return plugin to recognizer
                        }
                    } catch (t: Throwable) {
                        Logger.error("UrlRecognizer.canRecognize error in plugin ${plugin.manifest.id}", t)
                    }
                }
            }
        }
        return null
    }

    /**
     * Resolves an active media provider capable of handling the specified media URL.
     */
    fun findMediaProvider(url: String): Pair<LoadedPlugin, xdm.plugin.api.media.MediaProvider>? {
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (provider in plugin.context!!.mediaProviders) {
                    try {
                        if (provider.supportsUrl(url)) {
                            return plugin to provider
                        }
                    } catch (t: Throwable) {
                        Logger.error("MediaProvider.supportsUrl error in plugin ${plugin.manifest.id}", t)
                    }
                }
            }
        }
        return null
    }

    /**
     * Collects all active media providers.
     */
    fun getAllMediaProviders(): List<Pair<LoadedPlugin, xdm.plugin.api.media.MediaProvider>> {
        val providers = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.media.MediaProvider>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (provider in plugin.context!!.mediaProviders) {
                    providers.add(plugin to provider)
                }
            }
        }
        return providers
    }

    /**
     * Collects all registered proxy providers across all active plugins.
     */
    fun getActiveProxyProviders(): List<Pair<LoadedPlugin, xdm.plugin.api.network.ProxyProvider>> {
        val providers = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.network.ProxyProvider>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (provider in plugin.context!!.proxyProviders) {
                    providers.add(plugin to provider)
                }
            }
        }
        return providers
    }

    /**
     * Checks if a plugin-contributed proxy provider is user-approved and enabled.
     */
    fun isProxyProviderApproved(pluginId: String, providerId: String): Boolean {
        val key = "$pluginId:$providerId"
        return !AppContext.config.disabledProxyProviders.contains(key)
    }

    /**
     * Updates user approval state for a plugin-contributed proxy provider.
     */
    fun setProxyProviderApproved(pluginId: String, providerId: String, approved: Boolean) {
        val key = "$pluginId:$providerId"
        val current = AppContext.config.disabledProxyProviders.toMutableSet()
        if (approved) {
            current.remove(key)
        } else {
            current.add(key)
        }
        AppContext.config.disabledProxyProviders = current.toList()
        AppContext.config.save()
    }

    /**
     * Resolves proxy for the target URI and engine using active, user-approved ProxyProviders
     * ordered by priority descending. Enforces FAIL_CLOSED policy and engine constraints.
     */
    fun resolveProxy(
        targetUri: java.net.URI,
        engine: xdm.plugin.api.network.ProxyResolution.NetworkEngine = xdm.plugin.api.network.ProxyResolution.NetworkEngine.HTTP
    ): xdm.plugin.api.network.ProxyResolution? {
        val providers = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.network.ProxyProvider>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (provider in plugin.context!!.proxyProviders) {
                    providers.add(plugin to provider)
                }
            }
        }
        providers.sortByDescending { it.second.priority }
        for ((plugin, provider) in providers) {
            try {
                if (!isProxyProviderApproved(plugin.manifest.id, provider.providerId)) {
                    continue
                }
                if (!provider.isUserApproved) {
                    continue
                }
                if (!provider.applicableEngines.contains(engine)) {
                    continue
                }
                val res = provider.resolveProxy(targetUri, engine) ?: provider.resolveProxy(targetUri)
                if (res != null) {
                    if (res.isBlocked) {
                        Logger.info("Proxy resolution for ${targetUri.host} on engine $engine is BLOCKED: ${res.blockedReason}")
                        return res
                    }
                    if (!res.isEngineSupported(engine)) {
                        if (res.fallbackPolicy == xdm.plugin.api.network.ProxyResolution.FallbackPolicy.FAIL_CLOSED) {
                            val reason = "Engine $engine is not supported by proxy (${res.toRedactedString()}) with FAIL_CLOSED policy"
                            Logger.info("Proxy resolution for ${targetUri.host} rejected: $reason")
                            return xdm.plugin.api.network.ProxyResolution.blocked(reason)
                        } else {
                            Logger.info("Proxy resolution for ${targetUri.host}: engine $engine is unsupported by ${res.toRedactedString()}; falling back (FAIL_OPEN)")
                            continue
                        }
                    }
                    Logger.info("ProxyProvider '${provider.providerId}' resolved ${res.toRedactedString()} for ${targetUri.host} on engine $engine")
                    return res
                }
            } catch (t: Throwable) {
                Logger.error("ProxyProvider error in plugin ${plugin.manifest.id}", t)
            }
        }
        return null
    }

    /**
     * Collects all active VPN status providers.
     */
    fun getVpnStatusProviders(): List<Pair<LoadedPlugin, xdm.plugin.api.network.VpnStatusProvider>> {
        val list = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.network.VpnStatusProvider>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (provider in plugin.context!!.vpnStatusProviders) {
                    list.add(plugin to provider)
                }
            }
        }
        return list
    }

    /**
     * Collects all active dashboard widget contributors.
     */
    fun getDashboardWidgets(): List<Pair<LoadedPlugin, xdm.plugin.api.ui.DashboardWidgetContributor>> {
        val widgets = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.ui.DashboardWidgetContributor>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (w in plugin.context!!.dashboardWidgets) {
                    widgets.add(plugin to w)
                }
            }
        }
        return widgets
    }

    /**
     * Converts a database record into a public DownloadInfo snapshot.
     */
    fun toDownloadInfo(rec: xdm.manager.DbRecord): xdm.plugin.api.models.DownloadInfo {
        val taskInfo = runCatching { AppContext.taskInfoDB.getHttpTask(rec.id) }.getOrNull()
        val destPath = if (taskInfo != null) {
            File(taskInfo.targetFolder, rec.fileName).absolutePath
        } else {
            rec.fileName
        }
        val url = taskInfo?.url ?: ""
        return xdm.plugin.api.models.DownloadInfo(
            rec.id,
            url,
            rec.fileName,
            destPath,
            rec.size,
            rec.downloaded,
            rec.progress,
            rec.speed,
            rec.status.name,
            rec.categoryId ?: "None",
            rec.date
        )
    }

    /**
     * Collects all active download details section contributors applicable to the specified download.
     */
    fun getDetailsSections(download: xdm.plugin.api.models.DownloadInfo): List<Pair<LoadedPlugin, xdm.plugin.api.ui.DownloadDetailsSectionContributor>> {
        val sections = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.ui.DownloadDetailsSectionContributor>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (s in plugin.context!!.detailsSectionContributors) {
                    try {
                        if (s.isApplicable(download)) {
                            sections.add(plugin to s)
                        }
                    } catch (t: Throwable) {
                        Logger.error("Error evaluating details section applicability in plugin ${plugin.manifest.id}", t)
                    }
                }
            }
        }
        return sections
    }

    /**
     * Collects all active status indicator contributors.
     */
    fun getStatusIndicators(): List<Pair<LoadedPlugin, xdm.plugin.api.ui.StatusIndicatorContributor>> {
        val indicators = mutableListOf<Pair<LoadedPlugin, xdm.plugin.api.ui.StatusIndicatorContributor>>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                for (ind in plugin.context!!.statusIndicatorContributors) {
                    indicators.add(plugin to ind)
                }
            }
        }
        return indicators
    }

    fun sanitizeSuggestedFileName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Disallow null bytes and control characters
        if (trimmed.any { it.code < 32 || it.code == 127 }) return null

        // Disallow path separators, traversal sequences, and drive separators
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..") || trimmed.contains(":")) {
            return null
        }

        // Disallow characters forbidden in filenames on Windows / POSIX (< > " | ? *)
        val forbiddenChars = charArrayOf('<', '>', '"', '|', '?', '*')
        if (trimmed.any { it in forbiddenChars }) {
            return null
        }

        // Check Windows reserved device names
        val baseName = trimmed.substringBefore('.').uppercase()
        val reservedNames = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )
        if (reservedNames.contains(baseName)) {
            return null
        }

        return trimmed
    }

    fun suggestMetadata(url: String, currentFileName: String, mimeType: String): Pair<String?, String?> {
        var suggestedName: String? = null
        var suggestedCategory: String? = null

        val enrichers = mutableListOf<xdm.plugin.api.enrichment.MetadataEnricher>()
        for (plugin in installedPlugins) {
            if (plugin.status == PluginStatus.ACTIVE && plugin.context != null) {
                enrichers.addAll(plugin.context!!.metadataEnrichers)
            }
        }
        enrichers.sortByDescending { it.priority }

        for (enricher in enrichers) {
            try {
                if (enricher.canEnrich(url)) {
                    if (suggestedName == null) {
                        val rawName = enricher.suggestFileName(url, currentFileName, mimeType)
                        val sanitized = sanitizeSuggestedFileName(rawName)
                        if (!sanitized.isNullOrBlank()) {
                            suggestedName = sanitized
                        }
                    }
                    if (suggestedCategory == null) {
                        val cat = enricher.suggestCategory(url, suggestedName ?: currentFileName, mimeType)
                        if (!cat.isNullOrBlank()) {
                            suggestedCategory = cat.trim()
                        }
                    }
                }
            } catch (t: Throwable) {
                Logger.error("MetadataEnricher '${enricher.enricherId}' failed for URL $url: ${t.message}", t)
            }
        }

        return Pair(suggestedName, suggestedCategory)
    }

    /**
     * Gracefully shuts down all active plugins and marks startup as successful.
     */
    fun shutdown() {
        PluginEventBus.publish(EventTopics.APP_SHUTDOWN, AppStateEvent(AppStateEvent.State.SHUTDOWN, isSafeMode))
        installedPlugins.forEach { deactivatePlugin(it, setStatus = true) }
        installedPlugins.clear()
        PluginEventBus.clearAll()
        markStartupSuccessful()
    }
}
