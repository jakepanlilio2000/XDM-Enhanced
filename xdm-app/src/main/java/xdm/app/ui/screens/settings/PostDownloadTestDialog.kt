package xdm.app.ui.screens.settings

import xdm.app.I8N
import xdm.app.rules.PostDownloadActionEngine
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.chooseFile
import xdm.app.utils.fixHeight
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.scaledSize
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Window
import java.io.File
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * Dialog for manually testing a post-download command template against a sample or selected file.
 */
class PostDownloadTestDialog(
    parent: Window,
    private val commandTemplateSupplier: () -> String,
    initialExtensions: List<String> = emptyList()
) : JDialog(parent) {

    private val txtTestFile = rounded(JTextField()).apply {
        columns = 30
        fixHeight(this)
    }
    private val btnBrowse = settingsButton(I8N.text("SETTINGS_FOLDER_CHANGE")) { pickTestFile() }
    private val btnRun = settingsButton(I8N.text("POST_ACTION_TEST_RUN")) { runTest() }.apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }
    private val lblStatus = JLabel(" ").apply {
        font = font.deriveFont(Font.BOLD, 12.0f.px)
    }
    private val txtTokens = JTextArea(3, 40).apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val txtOutput = JTextArea(8, 40).apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        title = I8N.text("POST_ACTION_TEST_TITLE")
        isModal = true
        layout = BorderLayout()

        val sampleExt = initialExtensions.firstOrNull { it != "*" }?.removePrefix(".") ?: "mp4"
        val sample = getOrCreateSampleFile(sampleExt)
        txtTestFile.text = sample.absolutePath

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(16, 20, 16, 20)
            alignmentX = Component.LEFT_ALIGNMENT

            // Test File row
            add(settingsLeftAligned(JLabel(I8N.text("POST_ACTION_TEST_FILE")).apply {
                font = font.deriveFont(Font.BOLD, 12.0f.px)
            }))
            add(Box.createRigidArea(scaledSize(0, 6)))
            val fileBox = Box.createHorizontalBox().apply {
                alignmentX = Component.LEFT_ALIGNMENT
                val h = txtTestFile.preferredSize.height
                txtTestFile.maximumSize = Dimension(Int.MAX_VALUE, h)
                add(txtTestFile)
                add(Box.createRigidArea(scaledSize(8, 0)))
                add(btnBrowse)
                add(Box.createRigidArea(scaledSize(8, 0)))
                add(btnRun)
            }
            add(fileBox)
            add(Box.createRigidArea(scaledSize(0, 14)))

            // Resolved command tokens
            add(settingsLeftAligned(JLabel(I8N.text("POST_ACTION_TEST_RESOLVED_CMD")).apply {
                font = font.deriveFont(Font.BOLD, 12.0f.px)
            }))
            add(Box.createRigidArea(scaledSize(0, 4)))
            val tokensScroll = rounded(JScrollPane(txtTokens)).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                preferredSize = Dimension(scaledSize(520, 70))
            }
            add(tokensScroll)
            add(Box.createRigidArea(scaledSize(0, 14)))

            // Status indicator
            val statusBox = Box.createHorizontalBox().apply {
                alignmentX = Component.LEFT_ALIGNMENT
                add(JLabel(I8N.text("POST_ACTION_TEST_STATUS") + " ").apply {
                    font = font.deriveFont(Font.BOLD, 12.0f.px)
                })
                add(lblStatus)
            }
            add(statusBox)
            add(Box.createRigidArea(scaledSize(0, 8)))

            // Execution Output / Errors
            add(settingsLeftAligned(JLabel(I8N.text("POST_ACTION_TEST_OUTPUT")).apply {
                font = font.deriveFont(Font.BOLD, 12.0f.px)
            }))
            add(Box.createRigidArea(scaledSize(0, 4)))
            val outputScroll = rounded(JScrollPane(txtOutput)).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                preferredSize = Dimension(scaledSize(520, 150))
            }
            add(outputScroll)
        }

        add(content, BorderLayout.CENTER)

        val footer = Box.createHorizontalBox().apply {
            border = ScaledEmptyBorder(10, 20, 14, 20)
            add(Box.createHorizontalGlue())
            add(settingsButton(I8N.text("ND_CANCEL")) { dispose() })
        }
        add(footer, BorderLayout.SOUTH)

        updateTokensPreview()
        pack()
        setLocationRelativeTo(parent)
    }

    private fun pickTestFile() {
        chooseFile(this, directoriesOnly = false)?.let {
            txtTestFile.text = it.absolutePath
            updateTokensPreview()
        }
    }

    private fun updateTokensPreview() {
        val file = File(txtTestFile.text.trim())
        val template = commandTemplateSupplier()
        val argv = runCatching { PostDownloadActionEngine.buildArguments(template, file) }.getOrDefault(emptyList())
        txtTokens.text = if (argv.isEmpty()) {
            I8N.text("POST_ACTION_TEST_NO_CMD")
        } else {
            argv.mapIndexed { idx, arg -> "[$idx] $arg" }.joinToString("\n")
        }
    }

    private fun runTest() {
        val file = File(txtTestFile.text.trim())
        val template = commandTemplateSupplier().trim()
        if (template.isEmpty()) {
            lblStatus.text = I8N.text("POST_ACTION_ERR_EMPTY_CMD")
            lblStatus.foreground = Color(0xE0, 0x40, 0x40)
            return
        }

        updateTokensPreview()
        btnRun.isEnabled = false
        lblStatus.text = I8N.text("POST_ACTION_TEST_RUNNING")
        lblStatus.foreground = settingsAccentColor()
        txtOutput.text = ""

        Thread {
            val result = PostDownloadActionEngine.testCommand(template, file)
            SwingUtilities.invokeLater {
                btnRun.isEnabled = true
                if (result.success) {
                    lblStatus.text = "${I8N.text("POST_ACTION_TEST_SUCCESS")} (exit code 0)"
                    lblStatus.foreground = Color(0x30, 0xB0, 0x50)
                } else {
                    val msg = result.errorMessage ?: "Exit code ${result.exitCode ?: -1}"
                    lblStatus.text = "${I8N.text("POST_ACTION_TEST_FAILED")}: $msg"
                    lblStatus.foreground = Color(0xE0, 0x40, 0x40)
                }
                txtOutput.text = result.output.ifEmpty { "(No console output recorded)" }
            }
        }.start()
    }

    private fun getOrCreateSampleFile(ext: String): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "xdm_test")
        tempDir.mkdirs()
        val sample = File(tempDir, "sample_test_download.$ext")
        if (!sample.exists()) {
            runCatching { sample.writeText("XDM Enhanced test download content\n") }
        }
        return sample
    }
}
