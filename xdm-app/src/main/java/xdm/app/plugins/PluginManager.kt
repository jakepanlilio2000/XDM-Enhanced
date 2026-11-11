package xdm.app.plugins

import xdm.app.AppContext
import xdm.app.utils.AppDirs
import xdm.core.util.Logger
import xdm.plugin.api.XdmPlugin
import xdm.plugin.api.events.PostDownloadHook
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipFile

/**
 * Host lifecycle and extension manager for XDM Enhanced plugins.
 * Manages discovery, isolated class loading, user trust verification, fault isolation, and event dispatching.
 */
object PluginManager {
    const val API_VERSION = "1.1.0"

    var pluginsDir: File = File(AppDirs.root, "plugins")
    var isSafeMode: Boolean = false
        private set

    val installedPlugins = CopyOnWriteArrayList<LoadedPlugin>()
    var requiresRestart: Boolean = false
        private set

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

    private fun notifyStateChanged() {
        stateListeners.forEach { runCatching { it.onPluginStateChanged() } }
    }

    /**
     * Initializes the plugin subsystem.
     * When [safeMode] is true, third-party plugins are discovered but will not be initialized or executed.
     */
    fun init(safeMode: Boolean = false) {
        isSafeMode = safeMode
        installedPlugins.clear()

        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        discoverPlugins()

        if (!isSafeMode) {
            val enabledSet = AppContext.config.enabledPlugins.toSet()
            val trustedSet = AppContext.config.trustedPlugins.toSet()

            for (plugin in installedPlugins) {
                if (trustedSet.contains(plugin.manifest.id) && enabledSet.contains(plugin.manifest.id)) {
                    activatePlugin(plugin)
                } else if (!trustedSet.contains(plugin.manifest.id)) {
                    plugin.status = PluginStatus.UNTRUSTED
                } else {
                    plugin.status = PluginStatus.DISABLED
                }
            }
        } else {
            Logger.info("PluginManager: Running in Safe Mode. All third-party plugins are quarantined.")
            installedPlugins.forEach { it.status = PluginStatus.DISABLED }
        }

        notifyStateChanged()
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
     * Activates an installed plugin using an isolated URLClassLoader.
     */
    fun activatePlugin(plugin: LoadedPlugin): Boolean {
        if (isSafeMode) {
            plugin.status = PluginStatus.DISABLED
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
            val classLoader = URLClassLoader(urls.toTypedArray(), parentCl)
            plugin.classLoader = classLoader

            val pluginClass = Class.forName(plugin.manifest.entryPoint, true, classLoader)
            if (!XdmPlugin::class.java.isAssignableFrom(pluginClass)) {
                plugin.status = PluginStatus.FAULTED
                plugin.faultReason = "Entry point class '${plugin.manifest.entryPoint}' does not implement XdmPlugin."
                Logger.error("Plugin ${plugin.manifest.id} faulted: ${plugin.faultReason}")
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

            // Fault isolation: calling init inside try-catch protects host stability
            instance.init(context)
            plugin.status = PluginStatus.ACTIVE
            plugin.faultReason = null
            Logger.info("Plugin ${plugin.manifest.id} (${plugin.manifest.name} v${plugin.manifest.version}) activated successfully.")
            notifyStateChanged()
            return true
        } catch (t: Throwable) {
            plugin.status = PluginStatus.FAULTED
            plugin.faultReason = t.message ?: t.toString()
            Logger.error("Failed to activate plugin ${plugin.manifest.id}: ${plugin.faultReason}", t)
            deactivatePlugin(plugin, setStatus = false)
            notifyStateChanged()
            return false
        }
    }

    /**
     * Deactivates a running plugin and frees allocated resources.
     */
    fun deactivatePlugin(plugin: LoadedPlugin, setStatus: Boolean = true) {
        try {
            plugin.instance?.shutdown()
        } catch (t: Throwable) {
            Logger.error("Error shutting down plugin ${plugin.manifest.id}", t)
        }

        plugin.context?.clearRegistrations()
        plugin.instance = null
        plugin.context = null

        try {
            plugin.classLoader?.close()
        } catch (e: Exception) {
            Logger.error("Error closing classloader for ${plugin.manifest.id}", e)
        }
        plugin.classLoader = null

        if (setStatus) {
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

        // Extract archive contents into target directory with Zip Slip protection
        try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val destFile = File(targetDir, entry.name).canonicalFile
                    if (!destFile.toPath().startsWith(targetDir.canonicalFile.toPath())) {
                        throw SecurityException("Zip Slip attack detected in archive entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            // Restore preserved configuration and plugin data directory on upgrade
            if (dataBackup != null && dataBackup.exists()) {
                val restoredData = File(targetDir, "data")
                restoredData.mkdirs()
                dataBackup.copyRecursively(restoredData, overwrite = true)
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
     */
    fun uninstallPlugin(pluginId: String): Boolean {
        val plugin = installedPlugins.firstOrNull { it.manifest.id == pluginId } ?: return false
        deactivatePlugin(plugin)
        installedPlugins.remove(plugin)

        val success = plugin.pluginDir.deleteRecursively()

        val enabled = AppContext.config.enabledPlugins.toMutableList()
        enabled.remove(pluginId)
        AppContext.config.enabledPlugins = enabled

        val trusted = AppContext.config.trustedPlugins.toMutableList()
        trusted.remove(pluginId)
        AppContext.config.trustedPlugins = trusted
        AppContext.config.save()

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
     * Fault isolation: if a plugin hook throws an exception, it is logged and the plugin may be quarantined,
     * but the download itself and database remain unaffected.
     */
    fun notifyDownloadCompleted(filePath: String, id: Long) {
        if (installedPlugins.isEmpty()) return

        val file = File(filePath)
        val info = DownloadInfo(
            id,
            "",
            file.name,
            file.absolutePath,
            file.length(),
            file.length(),
            100,
            0f,
            "FINISHED",
            "None",
            System.currentTimeMillis()
        )

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
     * Gracefully shuts down all active plugins.
     */
    fun shutdown() {
        installedPlugins.forEach { deactivatePlugin(it, setStatus = false) }
    }
}
