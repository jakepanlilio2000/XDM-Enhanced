package xdm.sample.proxy;

import xdm.plugin.api.network.ProxyProvider;
import xdm.plugin.api.network.ProxyResolution;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;

/**
 * Demonstrates scoped proxy routing with user-directed fallback semantics.
 * Routes specific domains (*.proxy-fixture.local) through a local proxy endpoint,
 * leaving all unrelated downloads untouched.
 */
public class FixedProxyProvider implements ProxyProvider {

    private final String proxyHost;
    private final int proxyPort;
    private final ProxyResolution.Type protocol;
    private final ProxyResolution.FallbackPolicy fallbackPolicy;

    public FixedProxyProvider(String proxyHost, int proxyPort,
                              ProxyResolution.Type protocol,
                              ProxyResolution.FallbackPolicy fallbackPolicy) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.protocol = protocol != null ? protocol : ProxyResolution.Type.SOCKS5;
        this.fallbackPolicy = fallbackPolicy != null ? fallbackPolicy : ProxyResolution.FallbackPolicy.FAIL_CLOSED;
    }

    public FixedProxyProvider() {
        this("127.0.0.1", 1080, ProxyResolution.Type.SOCKS5, ProxyResolution.FallbackPolicy.FAIL_CLOSED);
    }

    @Override
    public String getProviderId() {
        return "sample-fixed-proxy";
    }

    @Override
    public String getDisplayName() {
        return "Sample SOCKS5/HTTP Proxy (" + protocol + ")";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public Set<ProxyResolution.NetworkEngine> getApplicableEngines() {
        return EnumSet.of(
            ProxyResolution.NetworkEngine.HTTP,
            ProxyResolution.NetworkEngine.MEDIA,
            ProxyResolution.NetworkEngine.TORRENT,
            ProxyResolution.NetworkEngine.PLUGIN_TASK
        );
    }

    @Override
    public ProxyResolution resolveProxy(URI targetUri, ProxyResolution.NetworkEngine engine) {
        if (targetUri == null || targetUri.getHost() == null) {
            return null;
        }

        String host = targetUri.getHost().toLowerCase();
        // Route only specific domains
        if (host.equals("proxy-fixture.local") || host.endsWith(".proxy-fixture.local")) {
            if (protocol == ProxyResolution.Type.HTTP) {
                return ProxyResolution.http(proxyHost, proxyPort, "proxyUser", "secretToken", fallbackPolicy);
            } else {
                return ProxyResolution.socks5(proxyHost, proxyPort, "proxyUser", "secretToken", fallbackPolicy);
            }
        }

        // Return null for all other endpoints so standard application network routing applies
        return null;
    }
}
