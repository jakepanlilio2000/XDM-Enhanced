package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.OS
import xdm.app.utils.detectOS
import xdm.app.utils.win.Win32Registry
import xdm.app.utils.win.WindowsAssociations

class WindowsAssociationsTest {

    private var initialMagnetCmd: String? = null
    private var initialTorrentProgId: String? = null

    @BeforeEach
    fun checkWindows() {
        assumeTrue(detectOS() == OS.Windows, "Windows only")
        // Save pre-test state so we can restore it exactly
        initialMagnetCmd = Win32Registry.getString("${WindowsAssociations.MAGNET_KEY}\\shell\\open\\command", "")
        initialTorrentProgId = Win32Registry.getString(WindowsAssociations.TORRENT_EXT_KEY, "")
    }

    @AfterEach
    fun restoreState() {
        if (detectOS() == OS.Windows) {
            if (initialMagnetCmd != null) {
                WindowsAssociations.registerMagnet()
            } else {
                WindowsAssociations.unregisterMagnet()
            }
            if (initialTorrentProgId != null) {
                WindowsAssociations.registerTorrent()
            } else {
                WindowsAssociations.unregisterTorrent()
            }
        }
    }

    @Test
    fun openCommandIsFormed() {
        val cmd = WindowsAssociations.openCommand()
        assertNotNull(cmd, "openCommand should resolve an executable")
        assertTrue(cmd!!.contains("\"%1\""), "openCommand must append quoted '%1'")
    }

    @Test
    fun magnetRegistrationTogglesCleanly() {
        // Register
        assertTrue(WindowsAssociations.registerMagnet(), "registerMagnet should return true on success")
        assertTrue(WindowsAssociations.isMagnetRegistered(), "isMagnetRegistered should be true after registration")

        // Unregister
        assertTrue(WindowsAssociations.unregisterMagnet(), "unregisterMagnet should succeed")
        assertFalse(WindowsAssociations.isMagnetRegistered(), "isMagnetRegistered should be false after unregister")
    }

    @Test
    fun torrentRegistrationTogglesCleanly() {
        // Register
        assertTrue(WindowsAssociations.registerTorrent(), "registerTorrent should return true on success")
        assertTrue(WindowsAssociations.isTorrentRegistered(), "isTorrentRegistered should be true after registration")

        // Unregister
        assertTrue(WindowsAssociations.unregisterTorrent(), "unregisterTorrent should succeed")
        assertFalse(WindowsAssociations.isTorrentRegistered(), "isTorrentRegistered should be false after unregister")
    }
}

