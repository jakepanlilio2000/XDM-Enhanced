package xdm.plugin.api.network;

/**
 * Discloses the detection status and supported operations of an external VPN client.
 * Separates host detection from operating-system networking privileges.
 */
public class VpnClientCapabilities {

    private final boolean clientInstalled;
    private final boolean supportsProfiles;
    private final boolean canConnect;
    private final boolean canDisconnect;
    private final String clientVersion;
    private final String clientPath;

    public VpnClientCapabilities(boolean clientInstalled, boolean supportsProfiles,
                                boolean canConnect, boolean canDisconnect,
                                String clientVersion, String clientPath) {
        this.clientInstalled = clientInstalled;
        this.supportsProfiles = supportsProfiles;
        this.canConnect = canConnect;
        this.canDisconnect = canDisconnect;
        this.clientVersion = clientVersion;
        this.clientPath = clientPath;
    }

    public static VpnClientCapabilities uninstalled() {
        return new VpnClientCapabilities(false, false, false, false, null, null);
    }

    public static VpnClientCapabilities statusOnly(String version, String path) {
        return new VpnClientCapabilities(true, false, false, false, version, path);
    }

    public boolean isClientInstalled() {
        return clientInstalled;
    }

    public boolean isSupportsProfiles() {
        return supportsProfiles;
    }

    public boolean isCanConnect() {
        return canConnect;
    }

    public boolean isCanDisconnect() {
        return canDisconnect;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public String getClientPath() {
        return clientPath;
    }

    @Override
    public String toString() {
        return "VpnClientCapabilities{" +
                "installed=" + clientInstalled +
                ", profiles=" + supportsProfiles +
                ", connect=" + canConnect +
                ", disconnect=" + canDisconnect +
                (clientVersion != null ? ", version='" + clientVersion + '\'' : "") +
                '}';
    }
}
