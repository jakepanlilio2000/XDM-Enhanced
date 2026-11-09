package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.power.CompletionAction
import xdm.app.power.CompletionCountdownDialog
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.settings.AdvancedConfigPanel
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class CompletionVisualTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

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

        ImageIO.write(img, "png", file)
        assertTrue(file.exists() && file.length() > 2000, "Screenshot created: ${file.name} (${file.length()} bytes)")
    }

    @Test
    fun captureCompletionScreenshots() {
        artifactDir.mkdirs()

        // 1. Light Theme
        runOnEdt {
            AppContext.config.theme = "light"
            AppContext.config.completionAction = CompletionAction.SHUTDOWN
            ThemeManager.applyTheme("light", null)

            val advPanel = AdvancedConfigPanel()
            advPanel.load()
            captureComponent(
                advPanel,
                680,
                550,
                File(artifactDir, "screenshot-completion-advanced-light.png")
            )

            val dialogLight = CompletionCountdownDialog(
                parent = null,
                action = CompletionAction.SHUTDOWN,
                failedCount = 0,
                initialSeconds = 30,
                onPerform = {},
                onCancel = {}
            )
            captureComponent(
                dialogLight,
                380,
                240,
                File(artifactDir, "screenshot-completion-countdown-dialog-light.png")
            )
            dialogLight.dispose()
        }

        // 2. Dark Theme
        runOnEdt {
            AppContext.config.theme = "dark"
            AppContext.config.completionAction = CompletionAction.SLEEP
            ThemeManager.applyTheme("dark", null)

            val advPanel = AdvancedConfigPanel()
            advPanel.load()
            captureComponent(
                advPanel,
                680,
                550,
                File(artifactDir, "screenshot-completion-advanced-dark.png")
            )

            val dialogDark = CompletionCountdownDialog(
                parent = null,
                action = CompletionAction.SLEEP,
                failedCount = 1,
                initialSeconds = 25,
                onPerform = {},
                onCancel = {}
            )
            captureComponent(
                dialogDark,
                380,
                240,
                File(artifactDir, "screenshot-completion-countdown-dialog-dark.png")
            )
            dialogDark.dispose()
        }
    }
}

