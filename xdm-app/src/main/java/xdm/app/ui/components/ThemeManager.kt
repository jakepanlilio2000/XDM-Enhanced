package xdm.app.ui.components

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.UIScale
import xdm.app.AppContext
import xdm.app.AppMain
import xdm.app.IAppConfig
import xdm.app.OS
import xdm.app.skins.SkinManager
import xdm.app.utils.linux.DesktopFontSettings
import xdm.app.utils.linux.LinuxPlaces
import xdm.app.utils.SystemTheme
import xdm.app.utils.WaylandToolkit
import xdm.app.utils.detectOS
import xdm.app.utils.windowIconImages
import xdm.core.util.Logger
import java.awt.Color
import java.awt.Insets
import java.awt.RenderingHints
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource

/**
 * Manages runtime theming, light/dark appearance modes, accent colors, and live OS appearance tracking.
 */
object ThemeManager {

    data class AccentPreset(val name: String, val hex: String, val color: Color)

    val PRESET_BLUE = AccentPreset("Blue", "#3B82F6", Color(0x3B, 0x82, 0xF6))
    val PRESET_CYAN = AccentPreset("Cyan", "#06B6D4", Color(0x06, 0xB6, 0xD4))
    val PRESET_GREEN = AccentPreset("Green", "#10B981", Color(0x10, 0xB9, 0x81))
    val PRESET_AMBER = AccentPreset("Amber", "#F59E0B", Color(0xF5, 0x9E, 0x0B))
    val PRESET_PURPLE = AccentPreset("Purple", "#8B5CF6", Color(0x8B, 0x5C, 0xF6))
    val PRESET_ROSE = AccentPreset("Rose", "#F43F5E", Color(0xF4, 0x3F, 0x5E))

    val PRESETS = listOf(
        PRESET_BLUE,
        PRESET_CYAN,
        PRESET_GREEN,
        PRESET_AMBER,
        PRESET_PURPLE,
        PRESET_ROSE
    )

    fun interface ThemeChangeListener {
        fun onThemeChanged()
    }

    private val listeners = CopyOnWriteArrayList<ThemeChangeListener>()
    private var systemWatcher: ScheduledExecutorService? = null
    private var lastResolvedDark: Boolean? = null

    var currentTheme: String = "system"
        private set

    var currentAccent: Color? = null
        private set

    var currentAccentHex: String? = null
        private set

    var currentZoom: Int = 100
        private set

    val isDark: Boolean
        get() = FlatLaf.isLafDark()

    /**
     * Default accent color for the active Look and Feel:
     * light blue (#6E9BFF) in dark theme, royal blue (#3B6FE0) in light theme.
     */
    val defaultAccentColor: Color
        get() = SkinManager.currentSkin.run {
            if (isDark) defaultDarkAccent else defaultLightAccent
        }

    /**
     * Relative luminance and contrast foreground calculation.
     * Returns dark text (#18181B) for bright backgrounds (luminance > 0.58, e.g. Amber, Yellow, Light Cyan, White)
     * and white text (#FFFFFF) for dark and saturated backgrounds (e.g. Blue, Purple, Rose, Black)
     * to guarantee maximum readability and contrast across all controls.
     */
    fun contrastForeground(bg: Color): Color {
        val luminance = (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue) / 255.0
        return if (luminance > 0.58) Color(0x18, 0x18, 0x1B) else Color.WHITE
    }

    /**
     * Checks if the graphics environment and window safely support translucency / opacity
     * without risk of crashing or unsupported native decorations on the host platform.
     */
    fun canApplyTranslucency(window: java.awt.Window? = null): Boolean = runCatching {
        val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        val gd = ge.defaultScreenDevice
        val supported = gd.isWindowTranslucencySupported(java.awt.GraphicsDevice.WindowTranslucency.TRANSLUCENT)
        if (!supported) return false
        if (window == null) return true
        // On decorated Frames, setOpacity throws IllegalComponentStateException in AWT
        if (window is java.awt.Frame && !window.isUndecorated && window.isDisplayable) return false
        true
    }.getOrDefault(false)

    /**
     * Safely sets window opacity if and only if the underlying platform and framework support it.
     */
    fun setWindowOpacitySafe(window: java.awt.Window, opacity: Float): Boolean = runCatching {
        if (canApplyTranslucency(window)) {
            window.opacity = opacity.coerceIn(0.1f, 1.0f)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    val listenerCount: Int get() = listeners.size

    fun addThemeListener(listener: ThemeChangeListener) {
        listeners.add(listener)
    }

    fun removeThemeListener(listener: ThemeChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach {
            runCatching { it.onThemeChanged() }.onFailure { Logger.error("Theme listener threw exception", it) }
        }
    }

    fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#")
        return runCatching {
            when (clean.length) {
                6 -> Color(clean.toInt(16))
                8 -> {
                    val argb = clean.toLong(16)
                    Color(((argb shr 16) and 0xFF).toInt(), ((argb shr 8) and 0xFF).toInt(), (argb and 0xFF).toInt(), ((argb shr 24) and 0xFF).toInt())
                }
                else -> null
            }
        }.getOrNull()
    }

    fun colorToHex(color: Color): String =
        if (color.alpha == 255) {
            String.format("#%02X%02X%02X", color.red, color.green, color.blue)
        } else {
            String.format("#%02X%02X%02X%02X", color.alpha, color.red, color.green, color.blue)
        }

    /**
     * Initializes the theme manager with application configuration and starts the OS appearance watcher.
     */
    fun init(config: IAppConfig) {
        SkinManager.init(config.skinId)
        currentTheme = config.theme
        currentAccentHex = config.accentColor
        currentAccent = parseHexColor(config.accentColor)
        currentZoom = config.uiScale

        applyTheme(currentTheme, currentAccent, currentZoom, saveConfig = false)
        startSystemWatcher()
    }

    private fun startSystemWatcher() {
        if (systemWatcher != null) return
        val executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "XDM-SystemThemeWatcher").apply { isDaemon = true }
        }
        systemWatcher = executor
        executor.scheduleWithFixedDelay({
            try {
                if (currentTheme.equals("system", ignoreCase = true)) {
                    val osDark = SystemTheme.isDark()
                    if (osDark != null && osDark != lastResolvedDark) {
                        Logger.info("OS appearance changed to ${if (osDark) "dark" else "light"}, updating theme")
                        SwingUtilities.invokeLater {
                            applyTheme("system", currentAccent, currentZoom, saveConfig = false)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }, 2, 2, TimeUnit.SECONDS)
    }

    fun setTheme(theme: String) {
        applyTheme(theme, currentAccent, currentZoom, saveConfig = true)
    }

    fun setAccentColor(accent: Color?) {
        applyTheme(currentTheme, accent, currentZoom, saveConfig = true)
    }

    fun setAccentColorHex(hex: String?) {
        setAccentColor(parseHexColor(hex))
    }

    /**
     * Applies the theme, accent color, and zoom factor immediately across all open windows.
     */
    fun applyTheme(
        theme: String,
        accent: Color?,
        zoomPercent: Int = 100,
        saveConfig: Boolean = false
    ) {
        currentTheme = theme
        currentAccent = accent
        currentAccentHex = accent?.let { colorToHex(it) }
        currentZoom = zoomPercent

        if (saveConfig) {
            runCatching {
                if (AppContext.hasConfig) {
                    val cfg = AppContext.config
                    cfg.theme = theme
                    cfg.accentColor = currentAccentHex
                    cfg.skinId = SkinManager.currentSkin.id
                    cfg.save()
                }
            }
        }

        // Font and rendering hints
        val desktop = if (WaylandToolkit.isActive) DesktopFontSettings.read() else null
        desktop?.font?.let { UIManager.put("defaultFont", FontUIResource(it)) }

        FlatLaf.registerCustomDefaultsSource("xdm.app.themes")

        if (zoomPercent != 100) UIScale.setZoomFactor(zoomPercent / 100f)

        val resolvedDark = AppMain.resolveDark(theme, SystemTheme::isDark)
        lastResolvedDark = resolvedDark

        // FlatLaf dynamic accent color callback
        val accentFg = accent?.let { contrastForeground(it) } ?: Color.WHITE
        if (accent != null) {
            FlatLaf.setSystemColorGetter { key ->
                when (key) {
                    "accent", "Component.accentColor", "XDM.accent" -> accent
                    "accent.foreground", "Component.accentColor.foreground", "XDM.accent.foreground" -> accentFg
                    else -> null
                }
            }
        } else {
            FlatLaf.setSystemColorGetter(null)
        }

        // Setup theme Look and Feel:
        // Original XDM and presets with square buttons use standard FlatDarkLaf/FlatLightLaf
        // to strictly respect rectangular geometry, avoiding macOS-style pill buttons.
        val activeSkin = SkinManager.currentSkin
        val useClassicLaf = activeSkin.id == SkinManager.SKIN_ORIGINAL_XDM || activeSkin.buttonArc == 0
        if (useClassicLaf) {
            if (resolvedDark) FlatDarkLaf.setup() else FlatLightLaf.setup()
        } else {
            if (resolvedDark) FlatMacDarkLaf.setup() else FlatMacLightLaf.setup()
        }

        // Apply active skin geometry, category colors, and UIManager overrides
        SkinManager.applyCustomDefaults(resolvedDark)

        Logger.info("Theme applied: ${if (FlatLaf.isLafDark()) "dark" else "light"} ($theme), accent: ${currentAccentHex ?: "default"}, zoom: $zoomPercent%")

        if (accent != null) {
            UIManager.put("Component.accentColor", accent)
            UIManager.put("XDM.accent", accent)
            UIManager.put("Button.default.background", accent)
            UIManager.put("Button.default.borderColor", accent)
            UIManager.put("Button.default.foreground", accentFg)
            UIManager.put("CheckBox.icon[filled].selectedBackground", accent)
            UIManager.put("CheckBox.icon[filled].checkmarkColor", accentFg)
            UIManager.put("ProgressBar.foreground", accent)
            UIManager.put("ProgressBar.selectionForeground", accentFg)
            UIManager.put("Slider.trackValueColor", accent)
            UIManager.put("Component.focusColor", accent)
        } else {
            UIManager.put("Component.accentColor", null)
            UIManager.put("XDM.accent", null)
            UIManager.put("Button.default.background", null)
            UIManager.put("Button.default.borderColor", null)
            UIManager.put("Button.default.foreground", null)
            UIManager.put("CheckBox.icon[filled].selectedBackground", null)
            UIManager.put("CheckBox.icon[filled].checkmarkColor", null)
            UIManager.put("ProgressBar.foreground", null)
            UIManager.put("ProgressBar.selectionForeground", null)
            UIManager.put("Slider.trackValueColor", null)
            UIManager.put("Component.focusColor", null)
        }

        desktop?.let {
            UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, it.textAntialiasing)
            UIManager.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140)
        }

        UIManager.put("TableHeader.cellMargins", Insets(0, 10, 0, 0))
        UIManager.put("SplitPaneDivider.gripDotCount", 0)
        UIManager.put("SplitPane.dividerSize", 10)
        if (WaylandToolkit.isActive) UIManager.put("ComboBoxUI", WaylandComboBoxUI::class.java.name)
        if (detectOS() == OS.Linux) LinuxPlaces.install()
        JOptionPane.getRootFrame().iconImages = windowIconImages

        // Update all component trees in every displayable Window
        FlatLaf.updateUI()

        // Notify custom components
        notifyListeners()
    }
}

