package xdm.sample.v2;

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
 * Demonstrates:
 * - External client capability detection
 * - User-selectable connection profiles
 * - Real-time telemetry notification to VpnStateListeners
 * - Explicit user-directed connect/disconnect commands
 * - Strict boundary: no tunnel drivers, no credentials extraction, no OS firewall changes
 */
public class SampleVpnProvider implements VpnStatusProvider {

    private final List<VpnStateListener> listeners = new CopyOnWriteArrayList<VpnStateListener>();
    private volatile VpnStatus currentStatus;
    private volatile String activeProfileId = "profile-us-east";

    private final List<VpnProfile> profiles = Collections.unmodifiableList(Arrays.asList(
            new VpnProfile("profile-us-east", "US East (Virginia)", "us-east.wireguard.net", true),
            new VpnProfile("profile-eu-central", "EU Central (Frankfurt)", "eu-central.wireguard.net", false),
            new VpnProfile("profile-ap-tokyo", "Asia Pacific (Tokyo)", "ap-tokyo.wireguard.net", false)
    ));

    public SampleVpnProvider() {
        this.currentStatus = VpnStatus.connected(
                "wg0",
                "US East (Virginia)",
                "10.14.0.2",
                activeProfileId,
                true,
                "Active WireGuard Tunnel"
        );
    }

    @Override
    public String getProviderId() {
        return "mock-wireguard-client";
    }

    @Override
    public String getDisplayName() {
        return "WireGuard External Client (Mock)";
    }

    @Override
    public String getDescription() {
        return "Reference VPN telemetry and profile provider simulating external WireGuard client status.";
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
        List<VpnProfile> list = new ArrayList<VpnProfile>();
        for (VpnProfile p : profiles) {
            boolean isActive = p.getProfileId().equals(activeProfileId) && currentStatus.isConnected();
            list.add(new VpnProfile(p.getProfileId(), p.getDisplayName(), p.getServerLocation(), isActive));
        }
        return list;
    }

    @Override
    public VpnStatus getCurrentStatus() {
        return currentStatus;
    }

    @Override
    public boolean requestConnect(String profileId) {
        String targetProfile = profileId != null ? profileId : "profile-us-east";
        activeProfileId = targetProfile;
        VpnStatus connecting = VpnStatus.connecting(targetProfile, "Initiating external client connection...");
        updateStatus(connecting);

        // Transition to connected state
        VpnStatus connected = VpnStatus.connected(
                "wg0",
                "Selected Gateway",
                "10.14.0.5",
                targetProfile,
                true,
                "Connected via external WireGuard client"
        );
        updateStatus(connected);
        return true;
    }

    @Override
    public boolean requestDisconnect() {
        VpnStatus disconnected = VpnStatus.disconnected("Disconnected by user request");
        updateStatus(disconnected);
        return true;
    }

    /**
     * Test utility method allowing integration test fixtures to simulate status changes.
     */
    public void simulateStatus(VpnStatus status) {
        updateStatus(status);
    }

    private void updateStatus(VpnStatus status) {
        this.currentStatus = status;
        for (VpnStateListener listener : listeners) {
            try {
                listener.onVpnStateChanged(status);
            } catch (Exception ignored) {
            }
        }
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
}
