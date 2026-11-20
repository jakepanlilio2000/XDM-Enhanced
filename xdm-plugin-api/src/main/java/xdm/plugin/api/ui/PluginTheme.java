package xdm.plugin.api.ui;

import xdm.plugin.api.Registration;
import java.awt.Color;
import java.awt.Font;

/**
 * Exposes application theme, appearance mode, accent colors, custom skin identifiers,
 * and display scaling metrics to plugin UI components.
 */
public interface PluginTheme {

    /**
     * Indicates whether the active theme is a dark theme.
     */
    boolean isDark();

    /**
     * Returns the primary accent color selected by the user.
     */
    Color getAccentColor();

    /**
     * Calculates a high-contrast foreground text color (black or white) suitable for the specified background.
     *
     * @param background Target background color
     * @return Color.WHITE or dark text color maximizing legibility
     */
    Color getContrastForeground(Color background);

    /**
     * Returns the primary UI font configured for the application.
     */
    Font getBaseFont();

    /**
     * Scales an integer pixel measurement by the current UI zoom and DPI scaling factor.
     *
     * @param pixels Unscaled reference pixel value
     * @return Scaled pixel dimension
     */
    int scale(int pixels);

    /**
     * Scales a floating-point measurement by the current UI zoom and DPI scaling factor.
     *
     * @param value Unscaled reference value
     * @return Scaled value
     */
    float scale(float value);

    /**
     * Returns the unique identifier of the active skin (e.g. "enhanced", "classic", "dark-graphite").
     */
    String getActiveSkinId();

    /**
     * Subscribes a listener to theme, skin, or zoom appearance changes.
     * Callbacks are guaranteed to be dispatched on the Swing Event Dispatch Thread (EDT).
     *
     * @param listener Callback receiving theme updates
     * @return Disposable registration handle
     */
    Registration addThemeListener(PluginThemeListener listener);

    /**
     * Unsubscribes a theme listener.
     */
    void removeThemeListener(PluginThemeListener listener);
}
