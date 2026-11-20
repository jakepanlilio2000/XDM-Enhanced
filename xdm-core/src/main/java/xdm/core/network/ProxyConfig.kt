package xdm.core.network

import java.net.Proxy

/**
 * Encapsulates dynamic or profile-based proxy routing configuration.
 */
data class ProxyConfig(
    val proxy: Proxy? = null,
    val user: String = "",
    val pass: String = "",
    val failClosed: Boolean = true,
    val isBlocked: Boolean = false,
    val blockedReason: String? = null
)
