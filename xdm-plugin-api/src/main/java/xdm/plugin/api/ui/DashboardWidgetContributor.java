package xdm.plugin.api.ui;

import java.awt.Component;

/**
 * Contributes dynamic telemetry, status pills, or metric widgets to the application dashboard,
 * status area, or category sidebar.
 */
public interface DashboardWidgetContributor {

    /**
     * Unique identifier for this widget.
     */
    String getWidgetId();

    /**
     * Human-readable title for the widget.
     */
    String getTitle();

    /**
     * Instantiates the Swing component representing the widget.
     */
    Component createWidget();

    /**
     * Periodic or event-driven callback to refresh widget metrics.
     */
    default void updateMetrics() {}
}
