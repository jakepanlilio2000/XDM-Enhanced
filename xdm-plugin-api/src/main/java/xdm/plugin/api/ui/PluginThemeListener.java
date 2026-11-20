package xdm.plugin.api.ui;

/**
 * Listener interface for receiving notifications when the application theme,
 * active skin, accent color, or UI scaling changes.
 */
@FunctionalInterface
public interface PluginThemeListener {
    /**
     * Invoked on the Swing Event Dispatch Thread when the application appearance changes.
     *
     * @param theme The updated theme information snapshot
     */
    void onThemeChanged(PluginTheme theme);
}
