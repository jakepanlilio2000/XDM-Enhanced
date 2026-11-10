package xdm.app.plugins

import xdm.core.util.Logger
import xdm.plugin.api.PluginConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Isolated persistent key-value configuration storage for a plugin.
 * Backed by a properties file in the plugin's private data directory.
 */
class PluginHostConfig(private val configFile: File) : PluginConfig {
    private val props = Properties()

    init {
        load()
    }

    private fun load() {
        if (configFile.exists() && configFile.isFile) {
            try {
                FileInputStream(configFile).use { props.load(it) }
            } catch (e: Exception) {
                Logger.error("Failed to load plugin config from ${configFile.absolutePath}", e)
            }
        }
    }

    override fun get(key: String, defaultValue: String?): String? {
        return props.getProperty(key, defaultValue)
    }

    override fun set(key: String, value: String?) {
        if (value == null) {
            props.remove(key)
        } else {
            props.setProperty(key, value)
        }
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        val v = props.getProperty(key) ?: return defaultValue
        return v.toIntOrNull() ?: defaultValue
    }

    override fun setInt(key: String, value: Int) {
        props.setProperty(key, value.toString())
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val v = props.getProperty(key) ?: return defaultValue
        return v.toBoolean()
    }

    override fun setBoolean(key: String, value: Boolean) {
        props.setProperty(key, value.toString())
    }

    @Synchronized
    override fun save() {
        try {
            configFile.parentFile?.mkdirs()
            FileOutputStream(configFile).use { out ->
                props.store(out, "XDM Enhanced Plugin Configuration")
            }
        } catch (e: Exception) {
            Logger.error("Failed to save plugin config to ${configFile.absolutePath}", e)
        }
    }
}

