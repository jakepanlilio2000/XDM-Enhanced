package xdm.sample.proxy;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.Registration;
import xdm.plugin.api.XdmPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the Sample Proxy Profile Provider plugin.
 */
public class SampleProxyPlugin implements XdmPlugin {

    private final List<Registration> registrations = new ArrayList<Registration>();
    private PluginContext context;

    @Override
    public String getId() {
        return "sample-proxy-provider";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;

        // Register the scoped proxy provider
        Registration proxyReg = context.registerProxyProvider(new FixedProxyProvider());
        registrations.add(proxyReg);

        // Register the settings panel
        Registration settingsReg = context.registerSettings(new ProxySettingsPanel());
        registrations.add(settingsReg);

        context.logInfo("SampleProxyPlugin initialized: registered proxy provider and settings panel.");
    }

    @Override
    public void shutdown() {
        // Disposable registration cleanup
        for (Registration reg : registrations) {
            try {
                reg.unregister();
            } catch (Exception ignored) {
            }
        }
        registrations.clear();

        if (context != null) {
            context.logInfo("SampleProxyPlugin shut down: unregistered all extension points.");
        }
    }
}
