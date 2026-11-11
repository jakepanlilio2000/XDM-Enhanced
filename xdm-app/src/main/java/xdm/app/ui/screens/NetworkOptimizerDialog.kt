package xdm.app.ui.screens

import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.components.MessageBox
import xdm.app.ui.screens.settings.rounded
import xdm.app.ui.screens.settings.settingsMutedColor
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.createIcon
import xdm.app.utils.gbAdd
import xdm.app.utils.px
import xdm.app.utils.sameWidth
import xdm.app.utils.scaledInsets
import xdm.app.utils.scaledSize
import xdm.app.utils.windowIconImages
import xdm.core.CoreConfig
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Window
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.KeyStroke
import javax.swing.SpinnerNumberModel
import javax.swing.UIManager

/**
 * Network Optimizer dialog: tunes download parameters (concurrency, segment slicing,
 * read timeout, speed limits) to suit different network environments.
 */
class NetworkOptimizerDialog(owner: Window?) : JDialog(owner) {

    enum class Profile(
        val displayName: String,
        val segments: Int,
        val parallel: Int,
        val timeout: Int,
        val speedLimitKbps: Int? = null,
    ) {
        BROADBAND("High-Speed / Broadband (Cable, Fiber, 5G)", 32, 5, 45, null),
        BALANCED("Standard / Balanced (DSL, Wi-Fi)", 8, 3, 60, null),
        METERED("Low-Speed / Metered (Mobile, High Latency)", 4, 1, 90, 512),
        AGGRESSIVE("Maximum Performance (High Concurrency)", 64, 8, 30, null),
        CUSTOM("Custom Configuration", 0, 0, 0, null);

        override fun toString() = displayName
    }

    private var updatingUi = false

    private val cmbProfile = rounded(JComboBox(Profile.values()))

    val spnSegments = rounded(
        JSpinner(SpinnerNumberModel(8, 1, CoreConfig.MAX_SEGMENTS, 1))
    ).apply {
        preferredSize = Dimension(100.px, preferredSize.height)
    }

    val spnParallel = rounded(
        JSpinner(SpinnerNumberModel(3, 1, AppConfig.MAX_PARALLEL_DOWNLOADS, 1))
    ).apply {
        preferredSize = Dimension(100.px, preferredSize.height)
    }

    val spnTimeout = rounded(
        JSpinner(
            SpinnerNumberModel(
                CoreConfig.DEFAULT_READ_TIMEOUT_SECONDS,
                AppConfig.MIN_READ_TIMEOUT_SECONDS,
                AppConfig.MAX_READ_TIMEOUT_SECONDS,
                5
            )
        )
    ).apply {
        preferredSize = Dimension(100.px, preferredSize.height)
    }

    val chkSpeedLimiter = JCheckBox("Enable Download Speed Limiter")

    val spnSpeedLimit = rounded(
        JSpinner(SpinnerNumberModel(500, 10, 10_000_000, 50))
    ).apply {
        preferredSize = Dimension(100.px, preferredSize.height)
    }

    private val btnApply = JButton("Apply Optimization")
    private val btnCancel = JButton(text("ND_CANCEL"))

    init {
        title = "Network Optimizer"
        iconImages = windowIconImages
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isResizable = false

        initUI()
        loadCurrentConfig()

        size = scaledSize(540, 520)
        setLocationRelativeTo(owner)
    }

    private fun initUI() {
        val root = JPanel(BorderLayout())
        root.border = ScaledEmptyBorder(16, 20, 16, 20)

        // Header
        val header = JPanel(BorderLayout(12.px, 0)).apply {
            val iconLabel = JLabel(createIcon(RemixIcon.SPEED_LINE, 32, UIManager.getColor("Accent.color") ?: UIManager.getColor("Label.foreground")))
            add(iconLabel, BorderLayout.WEST)

            val textPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                val titleLabel = JLabel("Network & Concurrency Optimizer").apply {
                    font = font.deriveFont(Font.BOLD, 15.0f.px)
                }
                val subLabel = JLabel("Optimize parallel download streams, timeouts, and throughput.").apply {
                    font = font.deriveFont(Font.PLAIN, 12.0f.px)
                    foreground = settingsMutedColor()
                }
                add(titleLabel)
                add(Box.createRigidArea(scaledSize(0, 4)))
                add(subLabel)
            }
            add(textPanel, BorderLayout.CENTER)
        }

        // Form panel
        val form = JPanel(GridBagLayout())
        form.border = ScaledEmptyBorder(16, 0, 16, 0)

        var row = 0

        // Profile row
        gbAdd(
            JLabel("Network Preset:").apply { font = font.deriveFont(Font.BOLD) },
            form, gridX = 0, gridY = row, alignment = GridBagConstraints.WEST,
            padding = scaledInsets(0, 0, 8, 12)
        )
        gbAdd(
            cmbProfile, form, gridX = 1, gridY = row, weightX = 1.0,
            horizontalFill = true, padding = scaledInsets(0, 0, 8, 0)
        )
        row++

        // Max Segments row
        gbAdd(
            JLabel("Segments per Download:"),
            form, gridX = 0, gridY = row, alignment = GridBagConstraints.WEST,
            padding = scaledInsets(4, 0, 8, 12)
        )
        gbAdd(
            labeledField(spnSegments, "streams (1 - ${CoreConfig.MAX_SEGMENTS})"),
            form, gridX = 1, gridY = row, weightX = 1.0, horizontalFill = true,
            padding = scaledInsets(4, 0, 8, 0)
        )
        row++

        // Max Parallel Downloads row
        gbAdd(
            JLabel("Simultaneous Downloads:"),
            form, gridX = 0, gridY = row, alignment = GridBagConstraints.WEST,
            padding = scaledInsets(4, 0, 8, 12)
        )
        gbAdd(
            labeledField(spnParallel, "downloads (1 - ${AppConfig.MAX_PARALLEL_DOWNLOADS})"),
            form, gridX = 1, gridY = row, weightX = 1.0, horizontalFill = true,
            padding = scaledInsets(4, 0, 8, 0)
        )
        row++

        // Read Timeout row
        gbAdd(
            JLabel("Socket Read Timeout:"),
            form, gridX = 0, gridY = row, alignment = GridBagConstraints.WEST,
            padding = scaledInsets(4, 0, 8, 12)
        )
        gbAdd(
            labeledField(spnTimeout, "seconds"),
            form, gridX = 1, gridY = row, weightX = 1.0, horizontalFill = true,
            padding = scaledInsets(4, 0, 8, 0)
        )
        row++

        // Speed Limiter row
        gbAdd(
            chkSpeedLimiter,
            form, gridX = 0, gridY = row, colSpan = 2, alignment = GridBagConstraints.WEST,
            padding = scaledInsets(8, 0, 4, 0)
        )
        row++

        gbAdd(
            JLabel("Speed Limit Cap:"),
            form, gridX = 0, gridY = row, alignment = GridBagConstraints.WEST,
            padding = scaledInsets(4, 16, 8, 12)
        )
        gbAdd(
            labeledField(spnSpeedLimit, "KB/s"),
            form, gridX = 1, gridY = row, weightX = 1.0, horizontalFill = true,
            padding = scaledInsets(4, 0, 8, 0)
        )
        row++

        // Information banner
        val infoBanner = JPanel(BorderLayout(10.px, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") ?: java.awt.Color.GRAY, 1),
                ScaledEmptyBorder(10, 12, 10, 12)
            )
            background = UIManager.getColor("Panel.background")
            val infoIcon = JLabel(createIcon(RemixIcon.SPEED_LINE, 20, settingsMutedColor()))
            add(infoIcon, BorderLayout.WEST)
            val infoText = JLabel(
                "<html><body style='width: 340px; font-size: 11px;'>" +
                    "TCP Window Auto-Tuning is dynamically handled by your operating system kernel. " +
                    "XDM optimizes application socket concurrency, stream fragmentation, and timeout tolerances " +
                    "safely without tampering with system registry or network adapters." +
                    "</body></html>"
            ).apply { foreground = settingsMutedColor() }
            add(infoText, BorderLayout.CENTER)
        }

        val centerPanel = JPanel(BorderLayout()).apply {
            add(form, BorderLayout.NORTH)
            add(infoBanner, BorderLayout.CENTER)
        }

        // Button bar
        val btnBar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = ScaledEmptyBorder(12, 0, 0, 0)
            add(Box.createHorizontalGlue())
            add(btnCancel)
            add(Box.createRigidArea(scaledSize(10, 0)))
            add(btnApply)
        }
        sameWidth(btnCancel, btnApply)

        root.add(header, BorderLayout.NORTH)
        root.add(centerPanel, BorderLayout.CENTER)
        root.add(btnBar, BorderLayout.SOUTH)

        contentPane.add(root)
        rootPane.defaultButton = btnApply
        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )

        // Event listeners
        cmbProfile.addActionListener {
            if (updatingUi) return@addActionListener
            val selected = cmbProfile.selectedItem as? Profile ?: return@addActionListener
            if (selected != Profile.CUSTOM) {
                updatingUi = true
                spnSegments.value = selected.segments
                spnParallel.value = selected.parallel
                spnTimeout.value = selected.timeout
                if (selected.speedLimitKbps != null) {
                    chkSpeedLimiter.isSelected = true
                    spnSpeedLimit.isEnabled = true
                    spnSpeedLimit.value = selected.speedLimitKbps
                } else {
                    chkSpeedLimiter.isSelected = false
                    spnSpeedLimit.isEnabled = false
                }
                updatingUi = false
            }
        }

        val onSpinnerChanged = {
            if (!updatingUi) {
                updatingUi = true
                cmbProfile.selectedItem = Profile.CUSTOM
                updatingUi = false
            }
        }
        spnSegments.addChangeListener { onSpinnerChanged() }
        spnParallel.addChangeListener { onSpinnerChanged() }
        spnTimeout.addChangeListener { onSpinnerChanged() }

        chkSpeedLimiter.addActionListener {
            spnSpeedLimit.isEnabled = chkSpeedLimiter.isSelected
            onSpinnerChanged()
        }

        btnCancel.addActionListener { dispose() }
        btnApply.addActionListener { applyOptimization() }
    }

    private fun labeledField(component: JComponent, label: String): JPanel =
        JPanel(BorderLayout(6.px, 0)).apply {
            add(component, BorderLayout.WEST)
            add(JLabel(label).apply { foreground = settingsMutedColor() }, BorderLayout.CENTER)
        }

    fun loadCurrentConfig() {
        val config = AppContext.config
        updatingUi = true

        spnSegments.value = config.maxSegments.coerceIn(1, CoreConfig.MAX_SEGMENTS)
        spnParallel.value = config.maxParallelDownloads.coerceIn(1, AppConfig.MAX_PARALLEL_DOWNLOADS)
        spnTimeout.value = config.readTimeoutSeconds.coerceIn(AppConfig.MIN_READ_TIMEOUT_SECONDS, AppConfig.MAX_READ_TIMEOUT_SECONDS)
        chkSpeedLimiter.isSelected = config.speedLimiterEnabled
        spnSpeedLimit.value = config.speedLimit.coerceAtLeast(10)
        spnSpeedLimit.isEnabled = config.speedLimiterEnabled

        // Match against known preset
        val match = Profile.values().firstOrNull {
            it != Profile.CUSTOM &&
                it.segments == config.maxSegments &&
                it.parallel == config.maxParallelDownloads &&
                it.timeout == config.readTimeoutSeconds &&
                (if (it.speedLimitKbps != null) config.speedLimiterEnabled && config.speedLimit == it.speedLimitKbps else !config.speedLimiterEnabled)
        } ?: Profile.CUSTOM
        cmbProfile.selectedItem = match

        updatingUi = false
    }

    fun applyOptimization() {
        val config = AppContext.config
        val segments = (spnSegments.value as Number).toInt()
        val parallel = (spnParallel.value as Number).toInt()
        val timeout = (spnTimeout.value as Number).toInt()
        val limiterEnabled = chkSpeedLimiter.isSelected
        val speedLimit = (spnSpeedLimit.value as Number).toInt()

        config.maxSegments = segments
        config.maxParallelDownloads = parallel
        config.readTimeoutSeconds = timeout
        config.speedLimiterEnabled = limiterEnabled
        config.speedLimit = speedLimit
        config.save()

        MessageBox.show(
            this,
            "Network Optimizer",
            "Network configuration successfully optimized:\n" +
                "• Segments per download: $segments\n" +
                "• Simultaneous downloads: $parallel\n" +
                "• Read timeout: ${timeout}s\n" +
                (if (limiterEnabled) "• Speed limit: $speedLimit KB/s" else "• Speed limit: Uncapped")
        )
        dispose()
    }
}
