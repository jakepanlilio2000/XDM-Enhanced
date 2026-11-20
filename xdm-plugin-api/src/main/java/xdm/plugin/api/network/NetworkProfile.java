package xdm.plugin.api.network;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A user-approved proxy profile contributed by a plugin.
 * Encapsulates scoped routing rules, credentials, fallback policy, and engine constraints.
 */
public class NetworkProfile {

    private final String profileId;
    private final String displayName;
    private final String description;
    private final ProxyResolution.Type protocol;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final ProxyResolution.FallbackPolicy fallbackPolicy;
    private final Set<ProxyResolution.NetworkEngine> supportedEngines;
    private final Set<String> targetDomains;
    private boolean enabled;

    public NetworkProfile(String profileId, String displayName, String description,
                          ProxyResolution.Type protocol, String host, int port,
                          String username, String password,
                          ProxyResolution.FallbackPolicy fallbackPolicy,
                          Set<ProxyResolution.NetworkEngine> supportedEngines,
                          Set<String> targetDomains, boolean enabled) {
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.displayName = displayName != null ? displayName : profileId;
        this.description = description != null ? description : "";
        this.protocol = protocol != null ? protocol : ProxyResolution.Type.HTTP;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fallbackPolicy = fallbackPolicy != null ? fallbackPolicy : ProxyResolution.FallbackPolicy.FAIL_CLOSED;
        this.supportedEngines = supportedEngines != null && !supportedEngines.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(supportedEngines))
                : Collections.unmodifiableSet(EnumSet.allOf(ProxyResolution.NetworkEngine.class));
        this.targetDomains = targetDomains != null ? new HashSet<>(targetDomains) : Collections.emptySet();
        this.enabled = enabled;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public ProxyResolution.Type getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public ProxyResolution.FallbackPolicy getFallbackPolicy() {
        return fallbackPolicy;
    }

    public Set<ProxyResolution.NetworkEngine> getSupportedEngines() {
        return supportedEngines;
    }

    public Set<String> getTargetDomains() {
        return Collections.unmodifiableSet(targetDomains);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean matchesUri(URI uri) {
        if (!enabled) return false;
        if (targetDomains.isEmpty()) return true;
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase();
        for (String pattern : targetDomains) {
            String p = pattern.toLowerCase().trim();
            if (p.startsWith("*.")) {
                String suffix = p.substring(2);
                if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
            } else if (host.equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    public ProxyResolution toProxyResolution() {
        return new ProxyResolution(protocol, host, port, username, password, fallbackPolicy, supportedEngines, null);
    }

    public String getRedactedSummary() {
        String auth = (username != null && !username.isEmpty()) ? " (" + username + ":***)" : "";
        return protocol + " " + (host != null ? host : "none") + ":" + port + auth + " [" + fallbackPolicy + "]";
    }

    @Override
    public String toString() {
        return getDisplayName() + " [" + getRedactedSummary() + "]";
    }
}
