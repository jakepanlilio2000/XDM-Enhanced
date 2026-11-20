package xdm.plugin.api.ui;

import xdm.plugin.api.PluginContext;
import java.awt.Component;

/**
 * Contributes a custom telemetry indicator, status pill, or metric badge to the application status bar.
 */
public interface StatusIndicatorContributor {

    /**
     * Unique identifier for this status indicator.
     */
    String getIndicatorId();

    /**
     * Tooltip text describing the indicator and its current status.
     */
    default String getTooltip() {
        return "";
    }

    /**
     * Instantiates the Swing component representing this status indicator.
     * Guaranteed to be called on the Swing Event Dispatch Thread (EDT).
     *
     * @param context Active plugin context
     * @return Compact Swing component (e.g. JLabel, pill button, or icon)
     */
    Component createIndicator(PluginContext context);

    /**
     * Optional callback to refresh the indicator component's metrics or telemetry.
     * Dispatched periodically on the Swing EDT.
     */
    default void updateIndicator() {}
}
