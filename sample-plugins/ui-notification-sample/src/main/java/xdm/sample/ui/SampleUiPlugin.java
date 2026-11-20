package xdm.sample.ui;

import xdm.plugin.api.NotificationLevel;
import xdm.plugin.api.PluginContext;
import xdm.plugin.api.Registration;
import xdm.plugin.api.XdmPlugin;
import xdm.plugin.api.events.EventSubscription;
import xdm.plugin.api.events.EventTopics;
import xdm.plugin.api.ui.PluginTheme;

import java.util.ArrayList;
import java.util.List;

public class SampleUiPlugin implements XdmPlugin {

    private final List<Registration> registrations = new ArrayList<Registration>();
    private EventSubscription eventSub;
    private PluginContext context;

    @Override
    public String getId() {
        return "sample-ui-notification";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;

        // Register UI extensions using disposable handles
        registrations.add(context.registerContextMenu(new SampleContextMenuContributor(context)));
        registrations.add(context.registerToolbar(new SampleToolbarContributor(context)));
        registrations.add(context.registerStatusIndicator(new SampleStatusIndicatorContributor()));
        registrations.add(context.registerDetailsSection(new SampleDetailsSectionContributor()));
        registrations.add(context.registerSettings(new SampleSettingsPanel()));

        // Theme observation
        PluginTheme theme = context.getTheme();
        if (theme != null) {
            registrations.add(theme.addThemeListener(t -> {
                context.logInfo("Theme changed: isDark=" + t.isDark() + ", skin=" + t.getActiveSkinId());
            }));
        }

        // Event subscription
        eventSub = context.subscribeTopic(EventTopics.DOWNLOAD_COMPLETED, (topic, event) -> {
            context.showNotification("Download Finished", "Observed completion event via EventBus", NotificationLevel.INFO);
        });

        context.logInfo("SampleUiPlugin initialized successfully with UI and event extensions.");
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

        if (eventSub != null) {
            try {
                eventSub.close();
            } catch (Exception ignored) {
            }
            eventSub = null;
        }

        if (context != null) {
            context.logInfo("SampleUiPlugin shut down.");
        }
    }
}
