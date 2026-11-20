package xdm.sample.v2;

import xdm.plugin.api.network.ProxyProvider;
import xdm.plugin.api.network.ProxyResolution;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;

/**
 * Minimal example proxy configuration provider demonstrating scoped routing.
 * Routes controlled test fixture hostnames (*.proxy-fixture.local) through a SOCKS5/HTTP endpoint
 * with FAIL_CLOSED policy, while safely bypassing all unrelated network traffic.
 */
public class SampleProxyProvider implements ProxyProvider {

    private final String providerId;
    private final String proxyHost;
    private final int proxyPort;
    private final ProxyResolution.Type protocol;
    private final ProxyResolution.FallbackPolicy fallbackPolicy;

    public SampleProxyProvider(String providerId, String proxyHost, int proxyPort,
                               ProxyResolution.Type protocol,
                               ProxyResolution.FallbackPolicy fallbackPolicy) {
        this.providerId = providerId;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.protocol = protocol != null ? protocol : ProxyResolution.Type.SOCKS5;
        this.fallbackPolicy = fallbackPolicy != null ? fallbackPolicy : ProxyResolution.FallbackPolicy.FAIL_CLOSED;
    }

    public SampleProxyProvider() {
        this("v2-sample-proxy", "127.0.0.1", 1080, ProxyResolution.Type.SOCKS5, ProxyResolution.FallbackPolicy.FAIL_CLOSED);
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getDisplayName() {
        return "V2 Sample Proxy Provider (" + protocol + ")";
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
        // Route only specific test fixtures
        if (host.equals("proxy-fixture.local") || host.endsWith(".proxy-fixture.local")) {
            if (protocol == ProxyResolution.Type.HTTP) {
                return ProxyResolution.http(proxyHost, proxyPort, "testuser", "testpass", fallbackPolicy);
            } else if (protocol == ProxyResolution.Type.SOCKS4) {
                return ProxyResolution.socks4(proxyHost, proxyPort, fallbackPolicy);
            } else {
                return ProxyResolution.socks5(proxyHost, proxyPort, "testuser", "testpass", fallbackPolicy);
            }
        } else if (host.equals("failopen-fixture.local") || host.endsWith(".failopen-fixture.local")) {
            return ProxyResolution.http(proxyHost, proxyPort, null, null, ProxyResolution.FallbackPolicy.FAIL_OPEN);
        }

        // Return null for all other traffic to avoid capturing unrelated traffic
        return null;
    }
}
