package xdm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.rules.PostDownloadRule
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.SettingsWindow
import xdm.app.ui.screens.settings.PostDownloadRuleDialog
import xdm.app.ui.screens.settings.PostDownloadTestDialog
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JList
import javax.swing.SwingUtilities

class PostDownloadActionScreenshotTest : DownloadManagerTestBase() {

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
    fun capturePostDownloadScreenshots() {
        artifactDir.mkdirs()
        xdm.app.I8N.loadTexts("en")

        val sampleRules = listOf(
            PostDownloadRule(
                name = "Transcode Video to MP4",
                extensions = listOf("mkv", "avi", "mov", "webm"),
                command = """"C:\tools\ffmpeg.exe" -i "%file%" -c copy "%file_directory%\out_%file_name%"""",
                enabled = true
            ),
            PostDownloadRule(
                name = "Extract Compressed Archives",
                extensions = listOf("zip", "7z", "tar.gz", "rar"),
                command = """"C:\tools\7z.exe" x "%file%" -o"%file_directory%"""",
                enabled = true
            ),
            PostDownloadRule(
                name = "Convert Audio to AAC",
                extensions = listOf("flac", "wav"),
                command = """ffmpeg -i %file% -c:a aac "%file_directory%\%file_name%.m4a"""",
                enabled = false
            )
        )

        AppContext.config.runCommand = true
        AppContext.config.postDownloadRules = sampleRules
        AppContext.config.customCommand = """"C:\tools\generic_handler.exe" %file%"""

        // 1. Dark Mode Settings Advanced tab showing Post-Download Actions
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val settings = SettingsWindow()
            settings.loadConfig()

            // Select Advanced tab (index 6)
            findNavList(settings)?.selectedIndex = 6
            captureComponent(settings, 820, 560, File(artifactDir, "screenshot-post-download-rules-dark.png"))
            settings.dispose()
        }

        // 2. Light Mode Settings Advanced tab
        runOnEdt {
            ThemeManager.applyTheme("light", null, 100)
            val settings = SettingsWindow()
            settings.loadConfig()

            findNavList(settings)?.selectedIndex = 6
            captureComponent(settings, 820, 560, File(artifactDir, "screenshot-post-download-rules-light.png"))
            settings.dispose()
        }

        // 3. Rule Edit Dialog with Variable Chips
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val dummyFrame = javax.swing.JFrame()
            val ruleDialog = PostDownloadRuleDialog(dummyFrame, sampleRules[0], false)
            captureComponent(ruleDialog, 560, 420, File(artifactDir, "screenshot-post-download-rule-dialog.png"))
            ruleDialog.dispose()
            dummyFrame.dispose()
        }

        // 4. Test Action Dialog
        runOnEdt {
            ThemeManager.applyTheme("dark", null, 100)
            val dummyFrame = javax.swing.JFrame()
            val testDialog = PostDownloadTestDialog(
                dummyFrame,
                { sampleRules[0].command },
                sampleRules[0].extensions
            )
            captureComponent(testDialog, 580, 480, File(artifactDir, "screenshot-post-download-test-dialog.png"))
            testDialog.dispose()
            dummyFrame.dispose()
        }
    }

    private fun findNavList(container: java.awt.Container): JList<*>? {
        for (i in 0 until container.componentCount) {
            val c = container.getComponent(i)
            if (c is JList<*>) return c
            if (c is java.awt.Container) {
                val found = findNavList(c)
                if (found != null) return found
            }
        }
        return null
    }
}
