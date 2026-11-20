package xdm.sample.vpn;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.Registration;
import xdm.plugin.api.XdmPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the Sample VPN Status & Telemetry Provider plugin.
 */
public class SampleVpnPlugin implements XdmPlugin {

    private final List<Registration> registrations = new ArrayList<Registration>();
    private PluginContext context;

    @Override
    public String getId() {
        return "sample-vpn-status";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;

        // Register the VPN status provider
        Registration vpnReg = context.registerVpnStatusProvider(new MockVpnStatusProvider());
        registrations.add(vpnReg);

        // Register the settings panel
        Registration settingsReg = context.registerSettings(new VpnSettingsPanel());
        registrations.add(settingsReg);

        context.logInfo("SampleVpnPlugin initialized: registered VPN status provider and settings panel.");
    }

    @Override
    public void shutdown() {
        for (Registration reg : registrations) {
            try {
                reg.unregister();
            } catch (Exception ignored) {
            }
        }
        registrations.clear();

        if (context != null) {
            context.logInfo("SampleVpnPlugin shut down: cleaned up all registrations.");
        }
    }
}
