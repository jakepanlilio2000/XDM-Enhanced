package xdm.app.skins

import xdm.app.AppContext
import xdm.app.ui.components.ThemeManager
import xdm.app.utils.AppDirs
import xdm.core.util.Logger
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon

/**
 * Manages active and installed visual skin presets, custom package imports, dynamic switching,
 * fallback recovery, and skin change listeners.
 */
object SkinManager {
    const val SKIN_ENHANCED = "enhanced"
    const val SKIN_ORIGINAL_XDM = "classic_xdm"

    val BUILTIN_ENHANCED: Skin = BuiltInEnhancedSkin()
    val BUILTIN_ORIGINAL_XDM: Skin = BuiltInClassicXdmSkin()

    var customSkinsDir: File = File(AppDirs.root, "skins")

    fun interface SkinChangeListener {
        fun onSkinChanged(skin: Skin)
    }

    private val skinListeners = CopyOnWriteArrayList<SkinChangeListener>()

    private val customSkinsCache = mutableListOf<CustomSkin>()

    val ALL_SKINS: List<Skin>
        get() = listOf(BUILTIN_ENHANCED, BUILTIN_ORIGINAL_XDM) + customSkinsCache

    var currentSkin: Skin = BUILTIN_ENHANCED
        private set

    /**
     * Rescans the user skins directory [customSkinsDir] and reloads custom skins.
     */
    fun refreshSkins() {
        customSkinsCache.clear()
        if (customSkinsDir.exists() && customSkinsDir.isDirectory) {
            val dirs = customSkinsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
            for (dir in dirs) {
                val skin = CustomSkin.loadFromDir(dir)
                if (skin != null) {
                    customSkinsCache.add(skin)
                }
            }
        }
    }

    fun getSkin(id: String?): Skin? {
        if (id.isNullOrBlank()) return null
        return ALL_SKINS.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    /**
     * Initializes the skin manager with the configured skin ID.
     * Scans custom skins and sets [currentSkin]. If invalid, defaults to [BUILTIN_ENHANCED].
     */
    fun init(skinId: String?) {
        refreshSkins()
        val resolved = getSkin(skinId) ?: run {
            Logger.info("Skin '$skinId' not found; defaulting to built-in 'enhanced' skin.")
            BUILTIN_ENHANCED
        }
        currentSkin = resolved
    }

    fun addSkinListener(listener: SkinChangeListener) {
        skinListeners.add(listener)
    }

    fun removeSkinListener(listener: SkinChangeListener) {
        skinListeners.remove(listener)
    }

    private fun notifySkinListeners(skin: Skin) {
        skinListeners.forEach {
            runCatching { it.onSkinChanged(skin) }.onFailure { e ->
                Logger.error("Skin listener threw exception: ${e.message}", e)
            }
        }
    }

    /**
     * Applies the given skin dynamically across all open windows without restart.
     * Disposes previous skin resources safely and falls back cleanly if errors occur.
     */
    fun applySkin(skinId: String, saveConfig: Boolean = true): Boolean {
        refreshSkins()
        val targetSkin = getSkin(skinId) ?: run {
            Logger.error("Requested skin '$skinId' not found. Falling back to 'enhanced'.")
            if (AppContext.hasConfig) {
                AppContext.config.skinId = SKIN_ENHANCED
                if (saveConfig) AppContext.config.save()
            }
            BUILTIN_ENHANCED
        }

        val oldSkin = currentSkin

        return try {
            currentSkin = targetSkin

            if (saveConfig && AppContext.hasConfig) {
                AppContext.config.skinId = targetSkin.id
                AppContext.config.save()
            }

            // Reapply theme and FlatLaf properties with the newly active skin
            ThemeManager.applyTheme(
                theme = ThemeManager.currentTheme,
                accent = ThemeManager.currentAccent,
                zoomPercent = ThemeManager.currentZoom,
                saveConfig = false
            )

            if (oldSkin != targetSkin) {
                oldSkin.dispose()
            }

            notifySkinListeners(targetSkin)
            Logger.info("Applied skin: '${targetSkin.name}' (id: ${targetSkin.id})")
            true
        } catch (e: Throwable) {
            Logger.error("Error applying skin '$skinId': ${e.message}. Executing emergency fallback to 'enhanced'.", e)
            if (targetSkin.id != SKIN_ENHANCED) {
                runCatching {
                    currentSkin = BUILTIN_ENHANCED
                    if (AppContext.hasConfig) {
                        AppContext.config.skinId = SKIN_ENHANCED
                        AppContext.config.save()
                    }
                    ThemeManager.applyTheme(
                        theme = ThemeManager.currentTheme,
                        accent = ThemeManager.currentAccent,
                        zoomPercent = ThemeManager.currentZoom,
                        saveConfig = false
                    )
                    notifySkinListeners(BUILTIN_ENHANCED)
                }
            }
            false
        }
    }

    /**
     * Installs a .xdmskin package into the user skins directory.
     * Returns the newly loaded [Skin].
     */
    fun installSkin(archiveFile: File): Skin {
        val manifest = SkinPackager.installSkinPackage(archiveFile, customSkinsDir)
        refreshSkins()
        return getSkin(manifest.id) ?: throw IllegalStateException("Installed skin '${manifest.id}' could not be loaded.")
    }

    /**
     * Removes an installed user skin from disk. Built-in skins cannot be removed.
     */
    fun uninstallSkin(skinId: String): Boolean {
        if (skinId.equals(SKIN_ENHANCED, ignoreCase = true) || skinId.equals(SKIN_ORIGINAL_XDM, ignoreCase = true)) {
            Logger.error("Cannot uninstall built-in skin '$skinId'")
            return false
        }

        // If the skin is active, switch to default enhanced skin first
        if (currentSkin.id.equals(skinId, ignoreCase = true)) {
            applySkin(SKIN_ENHANCED, saveConfig = true)
        }

        val targetDir = File(customSkinsDir, skinId)
        val success = if (targetDir.exists()) targetDir.deleteRecursively() else false
        refreshSkins()
        notifySkinListeners(currentSkin)
        return success
    }

    /**
     * Exports an installed skin package to [destArchive].
     */
    fun exportSkin(skinId: String, destArchive: File): File {
        val skin = getSkin(skinId) ?: throw IllegalArgumentException("Skin '$skinId' not found.")
        val dir = skin.skinDir ?: throw IllegalArgumentException("Built-in skins cannot be exported directly.")
        return SkinPackager.exportSkinPackage(dir, destArchive)
    }

    /**
     * Returns the root folder where user skins are stored, creating it if necessary.
     */
    fun getSkinsFolder(): File {
        if (!customSkinsDir.exists()) customSkinsDir.mkdirs()
        return customSkinsDir
    }

    /**
     * Resets visual appearance to default state:
     * - Skin: XDM Enhanced
     * - Theme: System
     * - Accent: Default (null)
     * - UI Scale: 100%
     */
    fun restoreDefaults() {
        currentSkin = BUILTIN_ENHANCED
        if (AppContext.hasConfig) {
            val cfg = AppContext.config
            cfg.skinId = SKIN_ENHANCED
            cfg.theme = "system"
            cfg.accentColor = null
            cfg.uiScale = 100
            cfg.save()
        }
        ThemeManager.applyTheme("system", null, 100, saveConfig = true)
        notifySkinListeners(BUILTIN_ENHANCED)
    }

    /**
     * Applies skin-specific defaults to UIManager.
     */
    fun applyCustomDefaults(isDark: Boolean) {
        currentSkin.applyCustomDefaults(isDark)
    }

    fun getCategoryIcon(categoryId: String?, size: Int): Icon? =
        currentSkin.getCategoryIcon(categoryId, size)

    fun getToolbarIcon(actionKey: String, size: Int): Icon? =
        currentSkin.getToolbarIcon(actionKey, size)
}
