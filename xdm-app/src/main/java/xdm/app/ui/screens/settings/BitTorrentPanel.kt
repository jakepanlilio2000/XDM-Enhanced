package xdm.app.ui.screens.settings

import xdm.app.AppContext
import xdm.app.utils.px
import xdm.app.utils.scaledSize
import xdm.app.utils.win.WindowsAssociations
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class BitTorrentPanel : SettingsPanel() {

    private val spnDownloadLimit = rounded(JSpinner(SpinnerNumberModel(0, 0, 10_000_000, 50))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val spnUploadLimit = rounded(JSpinner(SpinnerNumberModel(0, 0, 10_000_000, 50))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val spnMaxActiveDownloads = rounded(JSpinner(SpinnerNumberModel(3, 1, 100, 1))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val spnMaxActiveSeeds = rounded(JSpinner(SpinnerNumberModel(3, 1, 100, 1))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val spnMaxActiveTotal = rounded(JSpinner(SpinnerNumberModel(5, 1, 200, 1))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val spnMaxConnections = rounded(JSpinner(SpinnerNumberModel(200, 10, 2000, 10))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val tglDht = SettingsToggle()
    private val tglPex = SettingsToggle()
    private val tglLsd = SettingsToggle()

    private val cmbSeedingMode = rounded(JComboBox<String>()).apply {
        addItem("Stop immediately after download")
        addItem("Seed until share ratio or time limit")
        addItem("Seed indefinitely (unlimited)")
        preferredSize = Dimension(240.px, preferredSize.height)
        maximumSize = preferredSize
        addActionListener { updateSeedingLimitsEnabled() }
    }

    private val spnRatioLimit = rounded(JSpinner(SpinnerNumberModel(1.0, 0.1, 100.0, 0.1))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val spnTimeLimit = rounded(JSpinner(SpinnerNumberModel(60, 1, 10080, 15))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val spnListenPort = rounded(JSpinner(SpinnerNumberModel(0, 0, 65535, 1))).apply {
        preferredSize = Dimension(110.px, preferredSize.height)
        maximumSize = preferredSize
    }

    private val tglUpnp = SettingsToggle()
    private val tglNatPmp = SettingsToggle()
    private val tglMagnet = SettingsToggle()
    private val tglTorrent = SettingsToggle()
    private val btnDefaultApps = JButton("Open Windows Settings").apply {
        addActionListener { WindowsAssociations.openDefaultAppsSettings() }
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        add(settingsTitle("BitTorrent"))

        // Section 1: Transfer Speeds
        add(
            settingsSection(
                "Transfer Speeds",
                settingsRow(
                    "Global Download Speed Limit",
                    "Maximum download rate for all torrents (0 = unlimited)",
                    withSuffix(spnDownloadLimit, "KB/s")
                ),
                settingsRow(
                    "Global Upload Speed Limit",
                    "Maximum upload rate for all torrents (0 = unlimited)",
                    withSuffix(spnUploadLimit, "KB/s")
                ),
            )
        )
        add(settingsGap())

        // Section 2: Concurrency & Queues
        add(
            settingsSection(
                "Concurrency & Queues",
                settingsRow(
                    "Max Active Downloads",
                    "Maximum number of torrents downloading concurrently",
                    spnMaxActiveDownloads
                ),
                settingsRow(
                    "Max Active Seeds",
                    "Maximum number of torrents seeding concurrently",
                    spnMaxActiveSeeds
                ),
                settingsRow(
                    "Max Active Total",
                    "Maximum total active torrents (downloading + seeding)",
                    spnMaxActiveTotal
                ),
                settingsRow(
                    "Max Peer Connections",
                    "Maximum total peer connections across all torrents",
                    spnMaxConnections
                ),
            )
        )
        add(settingsGap())

        // Section 3: Peer & Network Discovery
        add(
            settingsSection(
                "Peer & Network Discovery",
                settingsRow(
                    "Distributed Hash Table (DHT)",
                    "Find peers without a central tracker using the mainline DHT network",
                    tglDht
                ),
                settingsRow(
                    "Peer Exchange (PEX)",
                    "Exchange peer lists directly with connected peers",
                    tglPex
                ),
                settingsRow(
                    "Local Peer Discovery (LSD)",
                    "Discover peers on your local network (LAN)",
                    tglLsd
                ),
            )
        )
        add(settingsGap())

        // Section 4: Seeding Rules
        add(
            settingsSection(
                "Seeding & Share Ratio",
                settingsRow(
                    "Seeding Behavior",
                    "Action to take when a torrent completes downloading",
                    cmbSeedingMode
                ),
                settingsRow(
                    "Share Ratio Limit",
                    "Target ratio (uploaded / downloaded) before seeding stops",
                    withSuffix(spnRatioLimit, "ratio")
                ),
                settingsRow(
                    "Seeding Duration Limit",
                    "Maximum seeding time after download completion",
                    withSuffix(spnTimeLimit, "minutes")
                ),
            )
        )
        add(settingsGap())

        // Section 5: Port & Port Forwarding
        add(
            settingsSection(
                "Port & Port Forwarding",
                settingsRow(
                    "Incoming Listening Port",
                    "TCP/UDP port for incoming peer connections (0 = dynamic random port)",
                    spnListenPort
                ),
                settingsRow(
                    "UPnP Port Forwarding",
                    "Request automatic port mapping from UPnP-enabled routers (default off)",
                    tglUpnp
                ),
                settingsRow(
                    "NAT-PMP Port Forwarding",
                    "Request automatic port mapping via NAT-PMP (default off)",
                    tglNatPmp
                ),
            )
        )

        // Section 6: OS File & Protocol Associations
        if (WindowsAssociations.isWindows) {
            add(settingsGap())
            add(
                settingsSection(
                    "System & Protocol Associations",
                    settingsRow(
                        "Handle magnet: Links",
                        "Associate XDM Enhanced with BitTorrent magnet URLs in Windows",
                        tglMagnet
                    ),
                    settingsRow(
                        "Handle .torrent Files",
                        "Associate XDM Enhanced with .torrent files in Windows Explorer",
                        tglTorrent
                    ),
                    settingsRow(
                        "Default Apps",
                        "Configure protocol defaults in Windows Settings",
                        btnDefaultApps
                    )
                )
            )
        }

        add(Box.createVerticalGlue())
    }

    private fun withSuffix(control: JComponent, suffix: String): JComponent =
        Box.createHorizontalBox().apply {
            add(control)
            add(Box.createRigidArea(scaledSize(8, 0)))
            add(JLabel(suffix).apply { foreground = settingsMutedColor() })
        }

    private fun updateSeedingLimitsEnabled() {
        val isRatioOrTime = cmbSeedingMode.selectedIndex == 1
        spnRatioLimit.isEnabled = isRatioOrTime
        spnTimeLimit.isEnabled = isRatioOrTime
    }

    fun load() {
        val config = AppContext.config
        spnDownloadLimit.value = config.torrentDownloadLimit / 1024
        spnUploadLimit.value = config.torrentUploadLimit / 1024
        spnMaxActiveDownloads.value = config.torrentMaxActiveDownloads
        spnMaxActiveSeeds.value = config.torrentMaxActiveSeeds
        spnMaxActiveTotal.value = config.torrentMaxActiveTotal
        spnMaxConnections.value = config.torrentMaxConnections
        tglDht.isSelected = config.torrentDhtEnabled
        tglPex.isSelected = config.torrentPexEnabled
        tglLsd.isSelected = config.torrentLsdEnabled

        cmbSeedingMode.selectedIndex = when (config.torrentSeedingMode) {
            "NEVER" -> 0
            "UNLIMITED" -> 2
            else -> 1
        }
        spnRatioLimit.value = config.torrentSeedingRatioLimit.toDouble()
        spnTimeLimit.value = config.torrentSeedingTimeLimitMinutes
        spnListenPort.value = config.torrentListenPort
        tglUpnp.isSelected = config.torrentEnableUpnp
        tglNatPmp.isSelected = config.torrentEnableNatPmp

        if (WindowsAssociations.isWindows) {
            tglMagnet.isSelected = WindowsAssociations.isMagnetRegistered()
            tglTorrent.isSelected = WindowsAssociations.isTorrentRegistered()
        }

        updateSeedingLimitsEnabled()
    }

    fun save() {
        val config = AppContext.config
        val downKb = (spnDownloadLimit.value as? Number)?.toInt() ?: 0
        val upKb = (spnUploadLimit.value as? Number)?.toInt() ?: 0
        config.torrentDownloadLimit = downKb * 1024
        config.torrentUploadLimit = upKb * 1024
        config.torrentMaxActiveDownloads = (spnMaxActiveDownloads.value as? Number)?.toInt() ?: 3
        config.torrentMaxActiveSeeds = (spnMaxActiveSeeds.value as? Number)?.toInt() ?: 3
        config.torrentMaxActiveTotal = (spnMaxActiveTotal.value as? Number)?.toInt() ?: 5
        config.torrentMaxConnections = (spnMaxConnections.value as? Number)?.toInt() ?: 200
        config.torrentDhtEnabled = tglDht.isSelected
        config.torrentPexEnabled = tglPex.isSelected
        config.torrentLsdEnabled = tglLsd.isSelected

        config.torrentSeedingMode = when (cmbSeedingMode.selectedIndex) {
            0 -> "NEVER"
            2 -> "UNLIMITED"
            else -> "RATIO_OR_TIME"
        }
        config.torrentSeedingRatioLimit = ((spnRatioLimit.value as? Number)?.toFloat() ?: 1.0f).coerceAtLeast(0.1f)
        config.torrentSeedingTimeLimitMinutes = ((spnTimeLimit.value as? Number)?.toInt() ?: 60).coerceAtLeast(1)
        config.torrentListenPort = ((spnListenPort.value as? Number)?.toInt() ?: 0).coerceIn(0, 65535)
        config.torrentEnableUpnp = tglUpnp.isSelected
        config.torrentEnableNatPmp = tglNatPmp.isSelected

        if (WindowsAssociations.isWindows) {
            if (tglMagnet.isSelected != WindowsAssociations.isMagnetRegistered()) {
                if (tglMagnet.isSelected) WindowsAssociations.registerMagnet()
                else WindowsAssociations.unregisterMagnet()
            }
            if (tglTorrent.isSelected != WindowsAssociations.isTorrentRegistered()) {
                if (tglTorrent.isSelected) WindowsAssociations.registerTorrent()
                else WindowsAssociations.unregisterTorrent()
            }
        }
    }
}

