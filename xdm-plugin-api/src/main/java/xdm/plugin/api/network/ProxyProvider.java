package xdm.plugin.api.network;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;

/**
 * Service Provider Interface for dynamic per-URI proxy resolution.
 * Allows plugins to provide PAC script evaluation, SOCKS routing, or proxy rotation
 * with scoped engine support and user approval checking.
 */
public interface ProxyProvider {

    /**
     * Unique identifier for this proxy provider.
     */
    String getProviderId();

    /**
     * Human-readable display name for this provider.
     */
    default String getDisplayName() {
        return getProviderId();
    }

    /**
     * Priority of this provider (higher priority is evaluated first).
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Whether this provider has been approved/enabled by the user.
     */
    default boolean isUserApproved() {
        return true;
    }

    /**
     * Set of engines to which this provider applies (default: all engines).
     */
    default Set<ProxyResolution.NetworkEngine> getApplicableEngines() {
        return EnumSet.allOf(ProxyResolution.NetworkEngine.class);
    }

    /**
     * Resolves the proxy configuration for a target URI and download engine.
     *
     * @param targetUri The destination URI being requested
     * @param engine The engine making the request (HTTP, MEDIA, TORRENT, PLUGIN_TASK, UPDATE)
     * @return Resolved ProxyResolution, or null to fall through to the next provider / system default
     */
    default ProxyResolution resolveProxy(URI targetUri, ProxyResolution.NetworkEngine engine) {
        return resolveProxy(targetUri);
    }

    /**
     * Legacy resolution method for backward compatibility.
     *
     * @param targetUri The destination URI being requested
     * @return Resolved ProxyResolution, or null to fall through
     */
    default ProxyResolution resolveProxy(URI targetUri) {
        return null;
    }
}
