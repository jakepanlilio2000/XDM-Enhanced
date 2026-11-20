package xdm.plugin.api.network;

import java.util.Objects;

/**
 * State descriptor for an external VPN connection.
 * <p>
 * Strictly separates the client's reported UI status from verified tunnel state.
 * An application-level status label does NOT guarantee that all operating-system
 * network traffic is routed through the tunnel.
 */
public class VpnStatus {

    public enum State {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        DISCONNECTING,
        RECONNECTING,
        ERROR,
        UNKNOWN
    }

    private final State state;
    private final String interfaceName;
    private final String serverLocation;
    private final String ipAddress;
    private final String activeProfileId;
    private final boolean tunnelVerified;
    private final String statusMessage;

    public VpnStatus(State state, String interfaceName, String serverLocation, String ipAddress,
                     String activeProfileId, boolean tunnelVerified, String statusMessage) {
        this.state = state != null ? state : State.UNKNOWN;
        this.interfaceName = interfaceName;
        this.serverLocation = serverLocation;
        this.ipAddress = ipAddress;
        this.activeProfileId = activeProfileId;
        this.tunnelVerified = tunnelVerified;
        this.statusMessage = statusMessage;
    }

    public VpnStatus(State state, String interfaceName, String serverLocation, String ipAddress) {
        this(state, interfaceName, serverLocation, ipAddress, null, state == State.CONNECTED, null);
    }

    public static VpnStatus connected(String interfaceName, String serverLocation, String ipAddress) {
        return new VpnStatus(State.CONNECTED, interfaceName, serverLocation, ipAddress, null, true, "Connected");
    }

    public static VpnStatus connected(String interfaceName, String serverLocation, String ipAddress,
                                     String activeProfileId, boolean tunnelVerified, String statusMessage) {
        return new VpnStatus(State.CONNECTED, interfaceName, serverLocation, ipAddress, activeProfileId, tunnelVerified, statusMessage);
    }

    public static VpnStatus disconnected() {
        return new VpnStatus(State.DISCONNECTED, null, null, null, null, false, "Disconnected");
    }

    public static VpnStatus disconnected(String statusMessage) {
        return new VpnStatus(State.DISCONNECTED, null, null, null, null, false, statusMessage);
    }

    public static VpnStatus connecting(String activeProfileId, String statusMessage) {
        return new VpnStatus(State.CONNECTING, null, null, null, activeProfileId, false, statusMessage);
    }

    public static VpnStatus error(String errorMessage) {
        return new VpnStatus(State.ERROR, null, null, null, null, false, errorMessage);
    }

    public static VpnStatus unknown(String statusMessage) {
        return new VpnStatus(State.UNKNOWN, null, null, null, null, false, statusMessage);
    }

    public State getState() {
        return state;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    /**
     * Indicates whether a real tunnel interface is active and verified,
     * distinguishing verified tunnel state from a simple UI label.
     */
    public boolean isTunnelActive() {
        return state == State.CONNECTED && (tunnelVerified || (interfaceName != null && !interfaceName.trim().isEmpty()));
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getServerLocation() {
        return serverLocation;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getActiveProfileId() {
        return activeProfileId;
    }

    public boolean isTunnelVerified() {
        return tunnelVerified;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VpnStatus vpnStatus = (VpnStatus) o;
        return tunnelVerified == vpnStatus.tunnelVerified &&
                state == vpnStatus.state &&
                Objects.equals(interfaceName, vpnStatus.interfaceName) &&
                Objects.equals(serverLocation, vpnStatus.serverLocation) &&
                Objects.equals(ipAddress, vpnStatus.ipAddress) &&
                Objects.equals(activeProfileId, vpnStatus.activeProfileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, interfaceName, serverLocation, ipAddress, activeProfileId, tunnelVerified);
    }

    @Override
    public String toString() {
        return "VpnStatus{" +
                "state=" + state +
                ", interface='" + (interfaceName != null ? interfaceName : "none") + '\'' +
                ", location='" + (serverLocation != null ? serverLocation : "unknown") + '\'' +
                ", verified=" + tunnelVerified +
                (statusMessage != null ? ", message='" + statusMessage + '\'' : "") +
                '}';
    }
}
