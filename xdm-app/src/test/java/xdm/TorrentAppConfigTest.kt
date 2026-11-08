package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.ui.screens.settings.BitTorrentPanel
import java.io.File
import java.nio.file.Files

class TorrentAppConfigTest {
    private lateinit var dir: File
    private lateinit var config: AppConfig

    @BeforeEach
    fun setup() {
        dir = Files.createTempDirectory("xdm-torrent-cfg").toFile()
        config = AppConfig(dir.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun testDefaultTorrentConfigValues() {
        assertEquals(0, config.torrentDownloadLimit)
        assertEquals(0, config.torrentUploadLimit)
        assertEquals(200, config.torrentMaxConnections)
        assertEquals(3, config.torrentMaxActiveDownloads)
        assertEquals(3, config.torrentMaxActiveSeeds)
        assertEquals(5, config.torrentMaxActiveTotal)
        assertTrue(config.torrentDhtEnabled)
        assertTrue(config.torrentPexEnabled)
        assertTrue(config.torrentLsdEnabled)
        assertEquals(0, config.torrentListenPort)
        assertEquals("RATIO_OR_TIME", config.torrentSeedingMode)
        assertEquals(1.0f, config.torrentSeedingRatioLimit)
        assertEquals(60, config.torrentSeedingTimeLimitMinutes)
        assertFalse(config.torrentEnableUpnp, "UPnP must be false by default")
        assertFalse(config.torrentEnableNatPmp, "NAT-PMP must be false by default")
    }

    @Test
    fun testSaveAndReloadTorrentConfig() {
        config.torrentDownloadLimit = 524288
        config.torrentUploadLimit = 262144
        config.torrentMaxConnections = 150
        config.torrentMaxActiveDownloads = 4
        config.torrentMaxActiveSeeds = 2
        config.torrentMaxActiveTotal = 6
        config.torrentDhtEnabled = false
        config.torrentPexEnabled = false
        config.torrentLsdEnabled = false
        config.torrentListenPort = 51413
        config.torrentSeedingMode = "UNLIMITED"
        config.torrentSeedingRatioLimit = 3.5f
        config.torrentSeedingTimeLimitMinutes = 180
        config.torrentEnableUpnp = true
        config.torrentEnableNatPmp = true

        config.save()

        // Reload into a fresh AppConfig instance from the same directory
        val reloaded = AppConfig(dir.absolutePath)
        reloaded.load()

        assertEquals(524288, reloaded.torrentDownloadLimit)
        assertEquals(262144, reloaded.torrentUploadLimit)
        assertEquals(150, reloaded.torrentMaxConnections)
        assertEquals(4, reloaded.torrentMaxActiveDownloads)
        assertEquals(2, reloaded.torrentMaxActiveSeeds)
        assertEquals(6, reloaded.torrentMaxActiveTotal)
        assertFalse(reloaded.torrentDhtEnabled)
        assertFalse(reloaded.torrentPexEnabled)
        assertFalse(reloaded.torrentLsdEnabled)
        assertEquals(51413, reloaded.torrentListenPort)
        assertEquals("UNLIMITED", reloaded.torrentSeedingMode)
        assertEquals(3.5f, reloaded.torrentSeedingRatioLimit)
        assertEquals(180, reloaded.torrentSeedingTimeLimitMinutes)
        assertTrue(reloaded.torrentEnableUpnp)
        assertTrue(reloaded.torrentEnableNatPmp)
    }

    @Test
    fun testBitTorrentPanelLoadAndSave() {
        AppContext.config = config
        config.torrentDownloadLimit = 102400
        config.torrentUploadLimit = 51200
        config.torrentMaxConnections = 180
        config.torrentDhtEnabled = true
        config.torrentEnableUpnp = false

        val panel = BitTorrentPanel()
        panel.load()

        // Modify via save back to config
        panel.save()

        assertEquals(102400, config.torrentDownloadLimit)
        assertEquals(51200, config.torrentUploadLimit)
        assertEquals(180, config.torrentMaxConnections)
        assertTrue(config.torrentDhtEnabled)
        assertFalse(config.torrentEnableUpnp)
    }
}

