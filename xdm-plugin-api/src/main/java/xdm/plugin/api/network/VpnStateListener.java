package xdm.plugin.api.network;

/**
 * Listener receiving telemetry updates when VPN connection state changes.
 */
@FunctionalInterface
public interface VpnStateListener {
    /**
     * Invoked when the active VPN status transitions.
     *
     * @param status Updated VPN connection telemetry
     */
    void onVpnStateChanged(VpnStatus status);
}
