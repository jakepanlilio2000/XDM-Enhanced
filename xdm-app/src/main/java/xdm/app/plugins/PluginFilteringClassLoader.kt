package xdm.app.plugins

import java.net.URL
import java.net.URLClassLoader

/**
 * Isolated classloader for XDM Enhanced plugins that strictly enforces public API boundaries.
 * Prohibits plugins from loading internal application, core, or manager classes
 * (xdm.app.*, xdm.core.*, xdm.manager.*), throwing ClassNotFoundException if attempted.
 */
class PluginFilteringClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
    private val pluginId: String
) : URLClassLoader(urls, parent) {

    companion object {
        private val FORBIDDEN_PREFIXES = listOf(
            "xdm.app.",
            "xdm.core.",
            "xdm.manager."
        )

        private val ALLOWED_EXCEPTIONS = listOf(
            // xdm.plugin.api.* is permitted
            "xdm.plugin.api."
        )
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Enforce boundary check against internal host classes
        if (isForbidden(name)) {
            throw ClassNotFoundException(
                "Security / Module Boundary: Plugin '$pluginId' is prohibited from accessing internal host class '$name'. Only public 'xdm.plugin.api.*' is accessible."
            )
        }
        return super.loadClass(name, resolve)
    }

    private fun isForbidden(className: String): Boolean {
        // Check if class starts with a permitted prefix
        for (allowed in ALLOWED_EXCEPTIONS) {
            if (className.startsWith(allowed)) {
                return false
            }
        }
        // Check if class starts with a forbidden internal prefix
        for (forbidden in FORBIDDEN_PREFIXES) {
            if (className.startsWith(forbidden)) {
                return true
            }
        }
        return false
    }
}
