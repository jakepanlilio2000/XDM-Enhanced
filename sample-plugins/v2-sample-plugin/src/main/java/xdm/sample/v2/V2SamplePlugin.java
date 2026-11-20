package xdm.sample.v2;

import xdm.plugin.api.NotificationLevel;
import xdm.plugin.api.PluginContext;
import xdm.plugin.api.Registration;
import xdm.plugin.api.XdmPlugin;
import xdm.plugin.api.events.EventSubscription;
import xdm.plugin.api.events.EventTopics;
import xdm.plugin.api.models.DownloadInfo;
import xdm.plugin.api.models.PluginAction;
import xdm.plugin.api.processors.PostDownloadProcessor;
import xdm.plugin.api.processors.ProcessingResult;
import xdm.plugin.api.ui.ContextMenuContributor;
import xdm.plugin.api.ui.DashboardWidgetContributor;
import xdm.plugin.api.ui.PluginTheme;
import xdm.plugin.api.ui.SettingsPanelContributor;
import xdm.plugin.api.ui.ToolbarContributor;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample plugin demonstrating SDK v2 public interfaces:
 * - PostDownloadProcessor for inspection/transformation pipeline
 * - Registration disposable handles
 * - EventSubscription for typed event topics
 * - DashboardWidgetContributor for custom UI widgets
 * - ContextMenuContributor for download list actions
 * - SettingsPanelContributor for custom configuration UI
 * - ToolbarContributor for top toolbar actions
 * - DownloadDetailsSectionContributor for metadata dialog tabs
 * - StatusIndicatorContributor for bottom status bar pills
 * - PluginTheme for appearance awareness
 */
public class V2SamplePlugin implements XdmPlugin, PostDownloadProcessor, DashboardWidgetContributor,
        ContextMenuContributor, ToolbarContributor {

    private PluginContext context;
    private final List<Registration> registrations = new ArrayList<Registration>();
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private EventSubscription downloadEventSub;

    @Override
    public String getId() {
        return "v2-sample-processor";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;

        // Register PostDownloadProcessor SPI using v2 Registration handle
        Registration procReg = context.registerPostDownloadProcessor(this);
        registrations.add(procReg);

        // Register DashboardWidgetContributor SPI using v2 Registration handle
        Registration widgetReg = context.registerDashboardWidget(this);
        registrations.add(widgetReg);

        // Register ContextMenuContributor using v2 Registration handle
        Registration menuReg = context.registerContextMenu(this);
        registrations.add(menuReg);

        // Register SettingsPanelContributor using v2 Registration handle
        Registration settingsReg = context.registerSettings(new SampleSettingsPanel());
        registrations.add(settingsReg);

        // Register ToolbarContributor using v2 Registration handle
        Registration toolbarReg = context.registerToolbar(this);
        registrations.add(toolbarReg);

        // Register SampleDetailsSection
        Registration detailsReg = context.registerDetailsSection(new SampleDetailsSection());
        registrations.add(detailsReg);

        // Register SampleStatusIndicator
        Registration indicatorReg = context.registerStatusIndicator(new SampleStatusIndicator());
        registrations.add(indicatorReg);

        // Register SampleProxyProvider for scoped network proxy routing
        Registration proxyReg = context.registerProxyProvider(new SampleProxyProvider());
        registrations.add(proxyReg);

        // Register SampleVpnProvider for external VPN status & telemetry
        Registration vpnReg = context.registerVpnStatusProvider(new SampleVpnProvider());
        registrations.add(vpnReg);

        // Theme change subscription
        PluginTheme theme = context.getTheme();
        if (theme != null) {
            Registration themeReg = theme.addThemeListener(t -> {
                context.logInfo("V2SamplePlugin observed theme update: isDark=" + t.isDark() + ", skin=" + t.getActiveSkinId());
            });
            registrations.add(themeReg);
        }

        // Subscribe to standardized event topic
        downloadEventSub = context.subscribeTopic(EventTopics.DOWNLOAD_COMPLETED, (topic, event) -> {
            context.logInfo("V2SamplePlugin observed event on topic: " + topic);
        });

        context.logInfo("V2SamplePlugin initialized successfully with v2 SPIs.");
    }

    @Override
    public void shutdown() {
        // Demonstrate explicit disposal of registration handles
        for (Registration reg : registrations) {
            try {
                reg.unregister();
            } catch (Exception ignored) {
            }
        }
        registrations.clear();

        if (downloadEventSub != null) {
            try {
                downloadEventSub.close();
            } catch (Exception ignored) {
            }
            downloadEventSub = null;
        }

        if (context != null) {
            context.logInfo("V2SamplePlugin shut down. Total processed downloads: " + processedCount.get());
        }
    }

    // -------------------------------------------------------------------------
    // PostDownloadProcessor SPI Implementation
    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return "v2-marker-processor";
    }

    @Override
    public int getOrder() {
        return 50; // Runs prior to default priority 100
    }

    @Override
    public boolean canProcess(DownloadInfo download, File downloadedFile) {
        return downloadedFile != null && downloadedFile.exists() && downloadedFile.isFile();
    }

    @Override
    public ProcessingResult process(DownloadInfo download, File downloadedFile) throws Exception {
        int count = processedCount.incrementAndGet();
        context.logInfo("V2SamplePlugin processing download #" + count + ": " + downloadedFile.getName());
        return ProcessingResult.success("Processed by V2SamplePlugin (#" + count + "): " + downloadedFile.getName());
    }

    // -------------------------------------------------------------------------
    // DashboardWidgetContributor SPI Implementation
    // -------------------------------------------------------------------------

    @Override
    public String getWidgetId() {
        return "v2-sample-widget";
    }

    @Override
    public String getTitle() {
        return "v2 Processor Telemetry";
    }

    @Override
    public Component createWidget() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JLabel label = new JLabel("v2 Processor Active. Processed: " + processedCount.get());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void updateMetrics() {
        // no-op for sample
    }

    // -------------------------------------------------------------------------
    // ContextMenuContributor SPI Implementation
    // -------------------------------------------------------------------------

    @Override
    public List<PluginAction> getContextMenuActions(List<DownloadInfo> selectedDownloads) {
        if (selectedDownloads == null || selectedDownloads.isEmpty()) {
            return Collections.emptyList();
        }

        PluginAction action = new PluginAction(
                "v2-inspect-processor",
                "Inspect with v2 Processor",
                "Shows processing status from the v2 sample plugin",
                null,
                downloads -> {
                    context.showNotification(
                            "v2 Processor Status",
                            "Selected: " + downloads.size() + " items. Processed total: " + processedCount.get(),
                            NotificationLevel.INFO
                    );
                }
        );

        return Collections.singletonList(action);
    }

    // -------------------------------------------------------------------------
    // ToolbarContributor SPI Implementation
    // -------------------------------------------------------------------------

    @Override
    public List<PluginAction> getToolbarActions() {
        PluginAction action = new PluginAction(
                "v2-sample-toolbar-action",
                "v2 Status",
                "Displays quick status popup from v2 Sample Plugin",
                null,
                downloads -> {
                    if (context != null) {
                        context.showNotification(
                                "v2 Plugin Toolbar",
                                "Processed total downloads: " + processedCount.get(),
                                NotificationLevel.INFO
                        );
                    }
                }
        );
        return Collections.singletonList(action);
    }
}
