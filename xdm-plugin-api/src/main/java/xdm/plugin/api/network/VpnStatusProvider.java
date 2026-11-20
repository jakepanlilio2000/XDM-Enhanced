package xdm.plugin.api.network;

import java.util.Collections;
import java.util.List;

/**
 * Extension contract providing VPN connection state, profile discovery, and explicit user-directed
 * control integration with established external VPN clients.
 * <p>
 * This interface exposes telemetry and client status only. It does NOT provide internal tunnel drivers,
 * credential harvesting, or operating-system firewall manipulation.
 */
public interface VpnStatusProvider {

    /**
     * Unique identifier for this VPN provider.
     */
    String getProviderId();

    /**
     * Human-readable display name for this VPN service.
     */
    default String getDisplayName() {
        return getProviderId();
    }

    /**
     * Detailed description of the external VPN client integration.
     */
    default String getDescription() {
        return "";
    }

    /**
     * Discloses external client installation and capability detection.
     */
    default VpnClientCapabilities getCapabilities() {
        return new VpnClientCapabilities(true, false, false, false, null, null);
    }

    /**
     * Returns configured profiles available in the external VPN client.
     */
    default List<VpnProfile> getAvailableProfiles() {
        return Collections.emptyList();
    }

    /**
     * Returns the current snapshot of the VPN connection.
     */
    VpnStatus getCurrentStatus();

    /**
     * Explicit user-directed request to initiate connection using the specified profile.
     * Must only be invoked in response to explicit user interaction, never silently in the background.
     *
     * @param profileId Profile identifier, or null for default profile
     * @return true if the connection command was accepted by the external client
     */
    default boolean requestConnect(String profileId) {
        return false;
    }

    /**
     * Explicit user-directed request to disconnect the external VPN.
     * Must only be invoked in response to explicit user interaction.
     *
     * @return true if the disconnect command was accepted by the external client
     */
    default boolean requestDisconnect() {
        return false;
    }

    /**
     * Subscribes a listener to receive real-time VPN status changes.
     */
    void addStateListener(VpnStateListener listener);

    /**
     * Unsubscribes a listener.
     */
    void removeStateListener(VpnStateListener listener);
}
