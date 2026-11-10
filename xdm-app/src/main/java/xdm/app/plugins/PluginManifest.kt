package xdm.app.plugins

import kotlinx.serialization.Serializable

/**
 * Metadata specification for a plugin package manifest (plugin.json).
 */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "",
    val description: String = "",
    val minAppVersion: String = "1.0.0-beta.1",
    val minApiVersion: String = "1.0.0",
    val entryPoint: String,
    val capabilities: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val license: String = "GPL-3.0-or-later",
    val homepage: String? = null,
    val checksumSha256: String? = null
)
