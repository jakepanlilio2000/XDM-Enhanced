package xdm.sample.vpn;

import xdm.plugin.api.network.VpnClientCapabilities;
import xdm.plugin.api.network.VpnProfile;
import xdm.plugin.api.network.VpnStateListener;
import xdm.plugin.api.network.VpnStatus;
import xdm.plugin.api.network.VpnStatusProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Non-privileged reference implementation of VpnStatusProvider.
 * Strictly separates UI state from OS tunnel state.
 * Never manipulates OS firewall, routing tables, or credentials.
 */
public class MockVpnStatusProvider implements VpnStatusProvider {

    private final List<VpnStateListener> listeners = new CopyOnWriteArrayList<VpnStateListener>();
    private volatile VpnStatus currentStatus;
    private volatile String activeProfileId = "us-east-wireguard";

    private final List<VpnProfile> profiles = Collections.unmodifiableList(Arrays.asList(
        new VpnProfile("us-east-wireguard", "US East (New York)", "gateway-us.wireguard.org", true),
        new VpnProfile("eu-west-wireguard", "EU West (Amsterdam)", "gateway-eu.wireguard.org", false),
        new VpnProfile("ap-south-wireguard", "Asia (Singapore)", "gateway-ap.wireguard.org", false)
    ));

    public MockVpnStatusProvider() {
        this.currentStatus = VpnStatus.connected(
            "wg0",
            "US East (New York)",
            "10.14.0.2",
            activeProfileId,
            true,
            "Active WireGuard Tunnel"
        );
    }

    @Override
    public String getProviderId() {
        return "sample-mock-vpn";
    }

    @Override
    public String getDisplayName() {
        return "WireGuard Telemetry Client";
    }

    @Override
    public String getDescription() {
        return "Reference VPN telemetry provider simulating an external WireGuard client without privileged system operations.";
    }

    @Override
    public VpnClientCapabilities getCapabilities() {
        return new VpnClientCapabilities(
            true,   // clientInstalled
            true,   // supportsProfiles
            true,   // canConnect
            true,   // canDisconnect
            "1.0.2026",
            "C:\\Program Files\\WireGuard\\wireguard.exe"
        );
    }

    @Override
    public List<VpnProfile> getAvailableProfiles() {
        List<VpnProfile> result = new ArrayList<VpnProfile>();
        for (VpnProfile p : profiles) {
            boolean active = p.getProfileId().equals(activeProfileId) && currentStatus.isConnected();
            result.add(new VpnProfile(p.getProfileId(), p.getDisplayName(), p.getServerLocation(), active));
        }
        return result;
    }

    @Override
    public VpnStatus getCurrentStatus() {
        return currentStatus;
    }

    @Override
    public boolean requestConnect(String profileId) {
        String target = profileId != null ? profileId : "us-east-wireguard";
        activeProfileId = target;
        updateStatus(VpnStatus.connecting(target, "Connecting to " + target));
        updateStatus(VpnStatus.connected("wg0", "Selected Gateway", "10.14.0.5", target, true, "Tunnel Established"));
        return true;
    }

    @Override
    public boolean requestDisconnect() {
        updateStatus(VpnStatus.disconnected("Disconnected by user request"));
        return true;
    }

    @Override
    public void addStateListener(VpnStateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeStateListener(VpnStateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void updateStatus(VpnStatus newStatus) {
        this.currentStatus = newStatus;
        for (VpnStateListener l : listeners) {
            try {
                l.onVpnStateChanged(newStatus);
            } catch (Exception ignored) {
            }
        }
    }
}
