package xdm.plugin.api.network;

import java.util.Objects;

/**
 * Represents a configured VPN connection profile or gateway location.
 */
public class VpnProfile {
    private final String profileId;
    private final String displayName;
    private final String serverLocation;
    private final boolean active;

    public VpnProfile(String profileId, String displayName, String serverLocation, boolean active) {
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.displayName = displayName != null ? displayName : profileId;
        this.serverLocation = serverLocation != null ? serverLocation : "";
        this.active = active;
    }

    public VpnProfile(String profileId, String displayName, String serverLocation) {
        this(profileId, displayName, serverLocation, false);
    }

    public String getProfileId() {
        return profileId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getServerLocation() {
        return serverLocation;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VpnProfile that = (VpnProfile) o;
        return Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId);
    }

    @Override
    public String toString() {
        return displayName + " (" + serverLocation + ")" + (active ? " [active]" : "");
    }
}
