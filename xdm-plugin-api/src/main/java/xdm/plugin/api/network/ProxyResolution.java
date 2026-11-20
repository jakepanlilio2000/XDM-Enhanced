package xdm.plugin.api.network;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resolved proxy configuration instruction returned by a ProxyProvider.
 * Defines protocol, endpoint, credentials, fail-closed/fail-open fallback policy,
 * and supported engine constraints.
 */
public class ProxyResolution {

    public enum Type {
        DIRECT,
        HTTP,
        SOCKS4,
        SOCKS5,
        SYSTEM_DEFAULT,
        BLOCKED
    }

    public enum FallbackPolicy {
        /**
         * Connection MUST abort if proxy endpoint is unreachable or unsupported.
         * Traffic is strictly forbidden from silently leaking to direct route.
         */
        FAIL_CLOSED,

        /**
         * Permitted to fall back to direct connection or next provider when proxy is unavailable,
         * emitting a clear security diagnostic warning.
         */
        FAIL_OPEN
    }

    public enum NetworkEngine {
        HTTP("HTTP/HTTPS Engine"),
        MEDIA("Media/Stream Grabber"),
        TORRENT("BitTorrent Engine"),
        PLUGIN_TASK("Plugin-Owned Transfer"),
        UPDATE("Update Checker");

        private final String displayName;

        NetworkEngine(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Type type;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final FallbackPolicy fallbackPolicy;
    private final Set<NetworkEngine> supportedEngines;
    private final String blockedReason;

    public ProxyResolution(Type type, String host, int port, String username, String password,
                           FallbackPolicy fallbackPolicy, Set<NetworkEngine> supportedEngines,
                           String blockedReason) {
        this.type = type != null ? type : Type.DIRECT;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fallbackPolicy = fallbackPolicy != null ? fallbackPolicy : FallbackPolicy.FAIL_CLOSED;
        this.supportedEngines = supportedEngines != null && !supportedEngines.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(supportedEngines))
                : Collections.unmodifiableSet(EnumSet.allOf(NetworkEngine.class));
        this.blockedReason = blockedReason;
    }

    public ProxyResolution(Type type, String host, int port, String username, String password) {
        this(type, host, port, username, password, FallbackPolicy.FAIL_CLOSED, EnumSet.allOf(NetworkEngine.class), null);
    }

    public static ProxyResolution direct() {
        return new ProxyResolution(Type.DIRECT, null, 0, null, null, FallbackPolicy.FAIL_OPEN, EnumSet.allOf(NetworkEngine.class), null);
    }

    public static ProxyResolution systemDefault() {
        return new ProxyResolution(Type.SYSTEM_DEFAULT, null, 0, null, null, FallbackPolicy.FAIL_OPEN, EnumSet.allOf(NetworkEngine.class), null);
    }

    public static ProxyResolution blocked(String reason) {
        return new ProxyResolution(Type.BLOCKED, null, 0, null, null, FallbackPolicy.FAIL_CLOSED, Collections.emptySet(), reason);
    }

    public static ProxyResolution http(String host, int port) {
        return new ProxyResolution(Type.HTTP, host, port, null, null);
    }

    public static ProxyResolution http(String host, int port, String username, String password) {
        return new ProxyResolution(Type.HTTP, host, port, username, password);
    }

    public static ProxyResolution http(String host, int port, String username, String password, FallbackPolicy fallbackPolicy) {
        return new ProxyResolution(Type.HTTP, host, port, username, password, fallbackPolicy, EnumSet.allOf(NetworkEngine.class), null);
    }

    public static ProxyResolution http(String host, int port, String username, String password,
                                        FallbackPolicy fallbackPolicy, Set<NetworkEngine> supportedEngines) {
        return new ProxyResolution(Type.HTTP, host, port, username, password, fallbackPolicy, supportedEngines, null);
    }

    public static ProxyResolution socks4(String host, int port) {
        return new ProxyResolution(Type.SOCKS4, host, port, null, null, FallbackPolicy.FAIL_CLOSED, EnumSet.allOf(NetworkEngine.class), null);
    }

    public static ProxyResolution socks4(String host, int port, FallbackPolicy fallbackPolicy) {
        return new ProxyResolution(Type.SOCKS4, host, port, null, null, fallbackPolicy, EnumSet.allOf(NetworkEngine.class), null);
    }

    public static ProxyResolution socks5(String host, int port) {
        return new ProxyResolution(Type.SOCKS5, host, port, null, null);
    }

    public static ProxyResolution socks5(String host, int port, String username, String password) {
        return new ProxyResolution(Type.SOCKS5, host, port, username, password);
    }

    public static ProxyResolution socks5(String host, int port, String username, String password, FallbackPolicy fallbackPolicy) {
        return new ProxyResolution(Type.SOCKS5, host, port, username, password, fallbackPolicy, EnumSet.allOf(NetworkEngine.class), null);
    }

    public static ProxyResolution socks5(String host, int port, String username, String password,
                                        FallbackPolicy fallbackPolicy, Set<NetworkEngine> supportedEngines) {
        return new ProxyResolution(Type.SOCKS5, host, port, username, password, fallbackPolicy, supportedEngines, null);
    }

    public Type getType() {
        return type;
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

    public FallbackPolicy getFallbackPolicy() {
        return fallbackPolicy;
    }

    public Set<NetworkEngine> getSupportedEngines() {
        return supportedEngines;
    }

    public boolean isEngineSupported(NetworkEngine engine) {
        return supportedEngines != null && supportedEngines.contains(engine);
    }

    public boolean isBlocked() {
        return type == Type.BLOCKED;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public String getSafeAddress() {
        if (host != null && port > 0) {
            return host + ":" + port;
        }
        return type.name();
    }

    public String toRedactedString() {
        if (type == Type.DIRECT) return "DIRECT";
        if (type == Type.SYSTEM_DEFAULT) return "SYSTEM_DEFAULT";
        if (type == Type.BLOCKED) return "BLOCKED (" + (blockedReason != null ? blockedReason : "policy") + ")";
        String auth = (username != null && !username.isEmpty()) ? " (" + username + ":***)" : "";
        return type + " " + host + ":" + port + auth + " [" + fallbackPolicy + "]";
    }

    public Proxy toJavaProxy() {
        if (type == Type.DIRECT || type == Type.SYSTEM_DEFAULT || type == Type.BLOCKED || host == null || port <= 0) {
            return Proxy.NO_PROXY;
        }
        if (type == Type.HTTP) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        }
        if (type == Type.SOCKS4 || type == Type.SOCKS5) {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
        }
        return Proxy.NO_PROXY;
    }

    @Override
    public String toString() {
        return toRedactedString();
    }
}
