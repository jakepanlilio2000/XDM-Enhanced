package xdm.app.plugins

import xdm.plugin.api.XdmPlugin
import java.io.File
import java.net.URLClassLoader

enum class PluginStatus(val displayName: String) {
    DISCOVERED("Discovered"),
    ACTIVE("Active"),
    DISABLED("Disabled"),
    UNTRUSTED("Untrusted"),
    FAULTED("Faulted"),
    INCOMPATIBLE("Incompatible"),
    NEEDS_RESTART("Restart Required")
}

data class LoadedPlugin(
    val manifest: PluginManifest,
    val pluginDir: File,
    val archiveFile: File? = null,
    var instance: XdmPlugin? = null,
    var context: PluginHostContext? = null,
    var classLoader: URLClassLoader? = null,
    var status: PluginStatus = PluginStatus.UNTRUSTED,
    var faultReason: String? = null,
    val isBuiltIn: Boolean = false,
    var sha256: String = ""
)
