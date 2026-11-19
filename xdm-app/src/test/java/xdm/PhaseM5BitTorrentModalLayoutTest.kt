package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.screens.NewTorrentDownloadWindow
import xdm.torrent.TorrentFileEntry
import xdm.torrent.TorrentMetadata
import xdm.torrent.TorrentSource
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.SwingUtilities

class PhaseM5BitTorrentModalLayoutTest : DownloadManagerTestBase() {

    @BeforeEach
    fun setUp() {
        xdm.app.I8N.loadTexts("en")
        AppContext.resetTorrentManagerForTest()
        AppContext.defaultDownloadFolder = dir.absolutePath
    }

    @AfterEach
    fun cleanTorrent() {
        AppContext.resetTorrentManagerForTest()
    }

    private fun <T> runOnEdt(action: () -> T): T {
        var result: T? = null
        var err: Throwable? = null
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        SwingUtilities.invokeAndWait {
            try {
                result = action()
            } catch (t: Throwable) {
                err = t
            }
        }
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun testExtremelyLongMagnetUriFitsWithinMonitorWorkspace() {
        val trackers = (1..50).joinToString("") { "&tr=udp%3A%2F%2Ftracker$it.example.org%3A6969%2Fannounce" }
        val longUri = "magnet:?xt=urn:btih:3b245504d5f72b17a0073587514027610c596b40&dn=Ubuntu-22.04.1-desktop-amd64.iso$trackers"
        assertTrue(longUri.length > 2500, "Should have a very long magnet link (${longUri.length} chars)")

        val dialog = runOnEdt {
            NewTorrentDownloadWindow(TorrentSource.MagnetUri(longUri))
        }

        try {
            val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            val maxUsableW = gc.bounds.width - insets.left - insets.right
            val maxUsableH = gc.bounds.height - insets.top - insets.bottom

            println("Monitor usable workspace: ${maxUsableW}x$maxUsableH")
            println("Dialog dimensions with 2500+ char magnet link: ${dialog.width}x${dialog.height}")
            println("Dialog minimum dimensions: ${dialog.minimumSize.width}x${dialog.minimumSize.height}")
            println("Dialog preferred dimensions: ${dialog.preferredSize.width}x${dialog.preferredSize.height}")

            assertTrue(dialog.width <= maxUsableW, "Dialog width (${dialog.width}) must fit within usable workspace ($maxUsableW)")
            assertTrue(dialog.height <= maxUsableH, "Dialog height (${dialog.height}) must fit within usable workspace ($maxUsableH)")
            assertTrue(dialog.width in 450..850, "Dialog width should be in reasonable default bounds: ${dialog.width}")
            assertTrue(dialog.isResizable, "Dialog must be resizable by user")

            // Complete source must be preserved in txtSource without truncation
            assertEquals(longUri, dialog.fullSourceText, "Full magnet link must be preserved without truncation")
            assertEquals(longUri, dialog.txtSource.text, "txtSource text must hold the complete URI")
            assertEquals(0, dialog.txtSource.caretPosition, "Caret must start at index 0 for readability")
        } finally {
            runOnEdt { dialog.dispose() }
        }
    }

    @Test
    fun testCopySourceButtonCopiesFullMagnetLink() {
        val testUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=TestTorrent&tr=http%3A%2F%2Ftracker.com"
        val dialog = runOnEdt {
            NewTorrentDownloadWindow(TorrentSource.MagnetUri(testUri))
        }

        try {
            runOnEdt {
                dialog.btnCopySource.doClick()
            }

            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val copied = clipboard.getData(DataFlavor.stringFlavor) as String
            assertEquals(testUri, copied, "Clipboard must contain the exact, complete magnet link")
        } finally {
            runOnEdt { dialog.dispose() }
        }
    }

    @Test
    fun testExtremelyLongTorrentNameAndFolderDoesNotExpandDialog() {
        val longName = "A".repeat(800) + ".mkv"
        val longFolder = File(dir, "B".repeat(500)).absolutePath

        val metadata = TorrentMetadata(
            name = longName,
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            totalSize = 1024L * 1024L * 500L,
            pieceLength = 1024 * 1024,
            numPieces = 500,
            files = listOf(TorrentFileEntry(0, longName, 1024L * 1024L * 500L, true))
        )

        val dialog = runOnEdt {
            val d = NewTorrentDownloadWindow(
                source = TorrentSource.MagnetUri("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
                initialMetadata = metadata,
                initialSaveDir = File(longFolder)
            )
            d
        }

        try {
            val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            val maxUsableW = gc.bounds.width - insets.left - insets.right

            println("Dialog dimensions with 800-char name & 500-char path: ${dialog.width}x${dialog.height}")
            assertTrue(dialog.width <= maxUsableW, "Dialog width (${dialog.width}) must not exceed usable workspace ($maxUsableW)")
            assertTrue(dialog.width in 450..850, "Dialog width should remain in standard range")
            assertEquals(longName, dialog.txtName.text)
            assertEquals(longFolder, dialog.txtFolder.text)
        } finally {
            runOnEdt { dialog.dispose() }
        }
    }

    @Test
    fun testShortMagnetAndLocalTorrentFile() {
        val shortUri = "magnet:?xt=urn:btih:3b245504d5f72b17a0073587514027610c596b40"
        val dialog1 = runOnEdt {
            NewTorrentDownloadWindow(TorrentSource.MagnetUri(shortUri))
        }

        try {
            assertTrue(dialog1.width in 450..850)
            assertTrue(dialog1.height in 350..700)
            assertEquals(shortUri, dialog1.fullSourceText)
        } finally {
            runOnEdt { dialog1.dispose() }
        }

        val dummyFile = File(dir, "sample.torrent")
        dummyFile.writeBytes(byteArrayOf(0x64, 0x38, 0x3a))
        val dialog2 = runOnEdt {
            NewTorrentDownloadWindow(TorrentSource.TorrentFile(dummyFile))
        }

        try {
            assertTrue(dialog2.width in 450..850)
            assertTrue(dialog2.height in 350..700)
            assertEquals(dummyFile.absolutePath, dialog2.fullSourceText)
        } finally {
            runOnEdt { dialog2.dispose() }
        }
    }

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @Test
    fun testCaptureVisualScreenshotsLightAndDark() {
        artifactDir.mkdirs()
        val trackers = (1..20).joinToString("") { "&tr=udp%3A%2F%2Ftracker$it.example.org%3A6969%2Fannounce" }
        val longUri = "magnet:?xt=urn:btih:3b245504d5f72b17a0073587514027610c596b40&dn=Ubuntu-22.04.1-desktop-amd64.iso$trackers"

        // Light Theme
        runOnEdt {
            xdm.app.ui.components.ThemeManager.applyTheme("light", null)
            val dialog = NewTorrentDownloadWindow(TorrentSource.MagnetUri(longUri))
            val cp = dialog.contentPane
            val img = java.awt.image.BufferedImage(dialog.width, dialog.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g2 = img.createGraphics()
            cp.paint(g2)
            g2.dispose()
            javax.imageio.ImageIO.write(img, "PNG", File(artifactDir, "screenshot-phase-m5a-bittorrent-modal-light.png"))
            dialog.dispose()
        }

        // Dark Theme
        runOnEdt {
            xdm.app.ui.components.ThemeManager.applyTheme("dark", null)
            val dialog = NewTorrentDownloadWindow(TorrentSource.MagnetUri(longUri))
            val cp = dialog.contentPane
            val img = java.awt.image.BufferedImage(dialog.width, dialog.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g2 = img.createGraphics()
            cp.paint(g2)
            g2.dispose()
            javax.imageio.ImageIO.write(img, "PNG", File(artifactDir, "screenshot-phase-m5a-bittorrent-modal-dark.png"))
            dialog.dispose()
        }

        assertTrue(File(artifactDir, "screenshot-phase-m5a-bittorrent-modal-light.png").exists())
        assertTrue(File(artifactDir, "screenshot-phase-m5a-bittorrent-modal-dark.png").exists())
    }

    @Test
    fun testCancelButtonDisposesDialog() {
        val dialog = runOnEdt {
            NewTorrentDownloadWindow(TorrentSource.MagnetUri("magnet:?xt=urn:btih:3b245504d5f72b17a0073587514027610c596b40"))
        }

        runOnEdt {
            dialog.btnCancel.doClick()
        }

        assertFalse(dialog.isDisplayable, "Dialog should be disposed on cancel")
    }
}
