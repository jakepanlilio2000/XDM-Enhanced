package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import xdm.app.I8N
import xdm.app.ui.components.BrowserExtensionPanel
import xdm.app.utils.BrandIcon
import xdm.app.utils.Browser
import java.awt.Color
import java.awt.Cursor
import javax.swing.SwingUtilities

class BrowserExtensionPanelTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            I8N.loadTexts("en")
        }
    }

    @Test
    fun browserTile_initializesAndUpdatesStatus() {
        SwingUtilities.invokeAndWait {
            val tile = BrowserExtensionPanel.BrowserTile(
                Browser.CHROME,
                BrandIcon.CHROME,
                Color(0x4285F4),
                "http://example.com"
            )

            assertTrue(tile.isFocusable, "Browser tile must be focusable for keyboard navigation")
            assertEquals(Cursor.HAND_CURSOR, tile.cursor.type, "Browser tile should show hand cursor")

            // Test status transitions
            tile.updateStatus(installed = true, connected = false)
            assertTrue(tile.isInstalled)
            assertFalse(tile.isConnected)
            assertTrue(tile.toolTipText.contains("Detected on system"), "Tooltip should reflect installed status")

            tile.updateStatus(installed = true, connected = true)
            assertTrue(tile.isInstalled)
            assertTrue(tile.isConnected)
            assertTrue(tile.toolTipText.contains("Active & connected"), "Tooltip should reflect active connection")

            tile.updateStatus(installed = false, connected = false)
            assertFalse(tile.isInstalled)
            assertFalse(tile.isConnected)
            assertTrue(tile.toolTipText.contains("Not found"), "Tooltip should reflect not detected status")
        }
    }

    @Test
    fun browserExtensionPanel_instantiatesQuickly() {
        SwingUtilities.invokeAndWait {
            val panel = BrowserExtensionPanel()
            assertNotNull(panel)
            assertTrue(panel.componentCount > 0)
        }
    }
}

