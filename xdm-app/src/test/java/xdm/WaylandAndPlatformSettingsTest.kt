package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.OS
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.components.WaylandComboBoxUI
import xdm.app.ui.screens.settings.AdvancedConfigPanel
import xdm.app.utils.WaylandToolkit
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JComboBox
import javax.swing.SwingUtilities

class WaylandAndPlatformSettingsTest : DownloadManagerTestBase() {

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
    fun testWaylandSupportMatrix() {
        // Non-Linux OS always resolves to NOT_LINUX
        assertEquals(
            WaylandToolkit.SupportStatus.NOT_LINUX,
            WaylandToolkit.checkSupport(OS.Windows, isWayland = true, hasToolkit = true)
        )
        assertEquals(
            WaylandToolkit.SupportStatus.NOT_LINUX,
            WaylandToolkit.checkSupport(OS.MacOS, isWayland = true, hasToolkit = true)
        )

        // Linux but not Wayland session (e.g. X11)
        assertEquals(
            WaylandToolkit.SupportStatus.NOT_WAYLAND_SESSION,
            WaylandToolkit.checkSupport(OS.Linux, isWayland = false, hasToolkit = true)
        )

        // Linux Wayland session, but runtime lacks WLToolkit (e.g. Temurin / standard OpenJDK)
        assertEquals(
            WaylandToolkit.SupportStatus.RUNTIME_LACKS_TOOLKIT,
            WaylandToolkit.checkSupport(OS.Linux, isWayland = true, hasToolkit = false)
        )

        // Linux Wayland session on JetBrains Runtime with WLToolkit
        assertEquals(
            WaylandToolkit.SupportStatus.SUPPORTED,
            WaylandToolkit.checkSupport(OS.Linux, isWayland = true, hasToolkit = true)
        )
    }

    @Test
    fun testWaylandExplanationMessages() {
        for (status in WaylandToolkit.SupportStatus.values()) {
            val msg = WaylandToolkit.getExplanation(status)
            assertNotNull(msg)
            assertTrue(msg.isNotBlank(), "Explanation for $status must not be blank")
        }
    }

    @Test
    fun testWaylandSessionDetectionWithX11SessionOverride() {
        val tempDir = Files.createTempDirectory("wayland-test").toFile()
        try {
            val fakeSocket = File(tempDir, "wayland-0")
            fakeSocket.createNewFile()

            // If session is explicitly x11, it is NOT a wayland session even if WAYLAND_DISPLAY socket exists
            assertFalse(
                WaylandToolkit.isWaylandSession(
                    sessionType = "x11",
                    display = "wayland-0",
                    runtimeDir = tempDir.absolutePath
                ),
                "X11 session must disqualify Wayland detection even if socket exists"
            )

            // If session is wayland and socket exists
            assertTrue(
                WaylandToolkit.isWaylandSession(
                    sessionType = "wayland",
                    display = "wayland-0",
                    runtimeDir = tempDir.absolutePath
                ),
                "Wayland session with existing socket must be detected"
            )

            // If socket does not exist
            assertFalse(
                WaylandToolkit.isWaylandSession(
                    sessionType = "wayland",
                    display = "non-existent-socket",
                    runtimeDir = tempDir.absolutePath
                ),
                "Non-existent socket must return false"
            )

            // Null or empty display
            assertFalse(
                WaylandToolkit.isWaylandSession(
                    sessionType = "wayland",
                    display = "",
                    runtimeDir = tempDir.absolutePath
                )
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAdvancedConfigPanelOnCurrentPlatform() {
        runOnEdt {
            val panel = AdvancedConfigPanel()
            panel.load()

            // Wayland control state
            val waylandExpectedEnabled = WaylandToolkit.isSupported
            assertEquals(waylandExpectedEnabled, panel.tglWayland.isEnabled)
            assertNotNull(panel.tglWayland.toolTipText)
            assertFalse(panel.tglWayland.toolTipText.isNullOrBlank())
            assertEquals(panel.tglWayland.toolTipText, panel.rowWayland.toolTipText)

            // Swing file chooser control state
            val chooserExpectedEnabled = (xdm.app.utils.detectOS() == OS.Linux)
            assertEquals(chooserExpectedEnabled, panel.tglSwingChooser.isEnabled)
            assertNotNull(panel.tglSwingChooser.toolTipText)
            assertFalse(panel.tglSwingChooser.toolTipText.isNullOrBlank())
        }
    }

    @Test
    fun testPlatformSettingsPreserveConfigOnSave() {
        runOnEdt {
            val panel = AdvancedConfigPanel()

            // Test nativeWayland preservation
            AppContext.config.nativeWayland = true
            panel.load()
            assertTrue(panel.tglWayland.isSelected, "Toggle should reflect config value even when disabled")

            // Simulate toggling in UI
            panel.tglWayland.isSelected = false
            panel.save()
            if (WaylandToolkit.isSupported) {
                assertFalse(AppContext.config.nativeWayland)
            } else {
                assertTrue(AppContext.config.nativeWayland, "Unsupported platform must not modify nativeWayland")
            }

            // Test swingFileChooser preservation
            AppContext.config.swingFileChooser = true
            panel.load()
            assertTrue(panel.tglSwingChooser.isSelected, "Toggle should reflect config value even when disabled")

            // Simulate toggling in UI
            panel.tglSwingChooser.isSelected = false
            panel.save()
            if (xdm.app.utils.detectOS() == OS.Linux) {
                assertFalse(AppContext.config.swingFileChooser)
            } else {
                assertTrue(AppContext.config.swingFileChooser, "Unsupported platform must not modify swingFileChooser")
            }
        }
    }

    @Test
    fun testWaylandComboBoxUIInstantiable() {
        runOnEdt {
            com.formdev.flatlaf.FlatLightLaf.setup()
            val cmb = JComboBox(arrayOf("Option 1", "Option 2"))
            val ui = WaylandComboBoxUI.createUI(cmb) as WaylandComboBoxUI
            assertNotNull(ui)
            cmb.ui = ui
            val popup = ui.createPopup()
            assertNotNull(popup)
        }
    }

    @Test
    fun testDownloadCompleteWindowActivationGuard() {
        runOnEdt {
            val win = xdm.app.ui.screens.DownloadCompleteWindow()
            val listener = win.windowListeners.firstOrNull { l ->
                l.javaClass.declaredFields.any { f -> f.name == "focused" }
            }
            assertNotNull(listener, "Activation guard listener must be registered on DownloadCompleteWindow")
            val focusedField = listener!!.javaClass.getDeclaredField("focused").apply { isAccessible = true }
            assertEquals(false, focusedField.getBoolean(listener))

            val event = java.awt.event.WindowEvent(win, java.awt.event.WindowEvent.WINDOW_ACTIVATED)
            listener.windowActivated(event)
            assertEquals(true, focusedField.getBoolean(listener))

            // Second activation should be ignored and remain guarded
            listener.windowActivated(event)
            assertEquals(true, focusedField.getBoolean(listener))
            win.dispose()
        }
    }

    @Test
    fun testProgressWindowActivationGuard() {
        runOnEdt {
            val win = xdm.app.ui.screens.ProgressWindow(12345L)
            val listener = win.windowListeners.firstOrNull { l ->
                l.javaClass.declaredFields.any { f -> f.name == "focused" }
            }
            assertNotNull(listener, "Activation guard listener must be registered on ProgressWindow")
            val focusedField = listener!!.javaClass.getDeclaredField("focused").apply { isAccessible = true }
            assertEquals(false, focusedField.getBoolean(listener))

            val event = java.awt.event.WindowEvent(win, java.awt.event.WindowEvent.WINDOW_ACTIVATED)
            listener.windowActivated(event)
            assertEquals(true, focusedField.getBoolean(listener))

            // Second activation should be ignored and remain guarded
            listener.windowActivated(event)
            assertEquals(true, focusedField.getBoolean(listener))
            win.dispose()
        }
    }

    private fun captureComponent(comp: Component, width: Int, height: Int, file: File) {
        comp.setSize(width, height)
        comp.addNotify()
        comp.validate()
        val target = (comp as? java.awt.Window)?.let { (it as? javax.swing.RootPaneContainer)?.contentPane } ?: comp
        target.setSize(width, height)
        target.doLayout()
        target.validate()

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        target.paint(g)
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
    }

    @Test
    fun testCaptureAdvancedSettingsPanels() {
        val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")
        runOnEdt {
            // Dark Theme
            AppContext.config.theme = "dark"
            ThemeManager.applyTheme("dark", null)
            val advPanelDark = AdvancedConfigPanel()
            advPanelDark.load()
            captureComponent(advPanelDark, 680, 560, File(artifactDir, "screenshot-advanced-settings-windows-dark.png"))

            // Light Theme
            AppContext.config.theme = "light"
            ThemeManager.applyTheme("light", null)
            val advPanelLight = AdvancedConfigPanel()
            advPanelLight.load()
            captureComponent(advPanelLight, 680, 560, File(artifactDir, "screenshot-advanced-settings-windows-light.png"))
        }
        assertTrue(File(artifactDir, "screenshot-advanced-settings-windows-dark.png").exists())
        assertTrue(File(artifactDir, "screenshot-advanced-settings-windows-light.png").exists())
    }
}

