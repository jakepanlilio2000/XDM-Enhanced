package xdm.app.ui.screens.settings

import xdm.app.I8N
import xdm.app.rules.PostDownloadActionEngine
import xdm.app.rules.PostDownloadRule
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.fixHeight
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.scaledSize
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Window
import java.util.UUID
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Dialog to create or edit a [PostDownloadRule].
 */
class PostDownloadRuleDialog(
    parent: Window,
    private val existing: PostDownloadRule? = null,
    modal: Boolean = true
) : JDialog(parent) {

    var result: PostDownloadRule? = null
        private set

    private val txtName = rounded(JTextField()).apply {
        columns = 30
        fixHeight(this)
    }
    private val txtExtensions = rounded(JTextField()).apply {
        columns = 30
        fixHeight(this)
    }
    private val txtCommand = rounded(JTextField()).apply {
        columns = 30
        fixHeight(this)
    }
    private val chkEnabled = JCheckBox(I8N.text("POST_ACTION_COL_ENABLED"), true).apply {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private val btnTest = settingsButton(I8N.text("POST_ACTION_RULE_TEST")) { openTestDialog() }
    private val lblError = JLabel(" ").apply {
        font = font.deriveFont(Font.PLAIN, 11.0f.px)
        foreground = java.awt.Color(0xE0, 0x40, 0x40)
    }

    init {
        title = I8N.text(if (existing == null) "POST_ACTION_RULE_ADD" else "POST_ACTION_RULE_EDIT")
        isModal = modal
        layout = BorderLayout()

        existing?.let {
            txtName.text = it.name
            txtExtensions.text = PostDownloadRule.formatExtensions(it.extensions)
            txtCommand.text = it.command
            chkEnabled.isSelected = it.enabled
        }

        txtExtensions.putClientProperty("JTextField.placeholderText", I8N.text("POST_ACTION_EXTS_PLACEHOLDER"))
        txtCommand.putClientProperty("JTextField.placeholderText", I8N.text("POST_ACTION_CMD_PLACEHOLDER"))

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = ScaledEmptyBorder(18, 22, 12, 22)
            alignmentX = Component.LEFT_ALIGNMENT

            // Rule Name
            add(settingsLeftAligned(JLabel(I8N.text("POST_ACTION_FIELD_NAME")).apply {
                font = font.deriveFont(Font.BOLD, 12.0f.px)
            }))
            add(Box.createRigidArea(scaledSize(0, 4)))
            add(fullWidth(txtName))
            add(Box.createRigidArea(scaledSize(0, 12)))

            // Extensions
            add(settingsLeftAligned(JLabel(I8N.text("POST_ACTION_FIELD_EXTENSIONS")).apply {
                font = font.deriveFont(Font.BOLD, 12.0f.px)
            }))
            add(Box.createRigidArea(scaledSize(0, 2)))
            add(settingsLeftAligned(settingsHint(I8N.text("POST_ACTION_FIELD_EXTENSIONS_SUB"))))
            add(Box.createRigidArea(scaledSize(0, 4)))
            add(fullWidth(txtExtensions))
            add(Box.createRigidArea(scaledSize(0, 12)))

            // Command
            add(settingsLeftAligned(JLabel(I8N.text("POST_ACTION_FIELD_COMMAND")).apply {
                font = font.deriveFont(Font.BOLD, 12.0f.px)
            }))
            add(Box.createRigidArea(scaledSize(0, 2)))
            add(settingsLeftAligned(settingsHint(I8N.text("POST_ACTION_FIELD_COMMAND_SUB"))))
            add(Box.createRigidArea(scaledSize(0, 4)))
            add(fullWidth(txtCommand))
            add(Box.createRigidArea(scaledSize(0, 8)))

            // Variable helper pills
            val varsLabel = JLabel(I8N.text("POST_ACTION_INSERT_VARS")).apply {
                font = font.deriveFont(Font.BOLD, 11.0f.px)
                foreground = settingsMutedColor()
            }
            add(settingsLeftAligned(varsLabel))
            add(Box.createRigidArea(scaledSize(0, 4)))

            val chipsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6.px, 4.px)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                PostDownloadActionEngine.ALL_VARIABLES.forEach { varName ->
                    add(createVariableChip(varName))
                }
            }
            add(chipsPanel)
            add(Box.createRigidArea(scaledSize(0, 10)))

            // Enabled checkbox & Test button row
            val optionsRow = Box.createHorizontalBox().apply {
                alignmentX = Component.LEFT_ALIGNMENT
                add(chkEnabled)
                add(Box.createHorizontalGlue())
                add(btnTest)
            }
            add(optionsRow)
            add(Box.createRigidArea(scaledSize(0, 8)))
            add(settingsLeftAligned(lblError))
        }

        add(content, BorderLayout.CENTER)

        // Footer buttons
        val btnSave = settingsButton(I8N.text("DESC_SAVE_Q")) { onSave() }
        val btnCancel = settingsButton(I8N.text("ND_CANCEL")) { dispose() }
        val footer = Box.createHorizontalBox().apply {
            border = ScaledEmptyBorder(10, 22, 16, 22)
            add(Box.createHorizontalGlue())
            add(btnSave)
            add(Box.createRigidArea(scaledSize(10, 0)))
            add(btnCancel)
        }
        add(footer, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(parent)
    }

    private fun fullWidth(comp: Component): JPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, comp.preferredSize.height)
            add(comp, BorderLayout.CENTER)
        }

    private fun createVariableChip(varName: String): JButton =
        JButton(varName).apply {
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            toolTipText = I8N.text("POST_ACTION_INSERT_HINT") + " $varName"
            addActionListener {
                insertVariableAtCaret(varName)
            }
        }

    private fun insertVariableAtCaret(token: String) {
        val pos = txtCommand.caretPosition
        val text = txtCommand.text
        val newText = text.substring(0, pos) + token + text.substring(pos)
        txtCommand.text = newText
        txtCommand.caretPosition = pos + token.length
        txtCommand.requestFocusInWindow()
    }

    private fun openTestDialog() {
        val exts = PostDownloadRule.parseExtensions(txtExtensions.text)
        val dlg = PostDownloadTestDialog(this, { txtCommand.text }, exts)
        dlg.isVisible = true
    }

    private fun onSave() {
        val name = txtName.text.trim()
        val cmd = txtCommand.text.trim()
        val exts = PostDownloadRule.parseExtensions(txtExtensions.text)

        if (name.isEmpty()) {
            lblError.text = I8N.text("POST_ACTION_ERR_NAME_EMPTY")
            txtName.requestFocusInWindow()
            return
        }
        if (cmd.isEmpty()) {
            lblError.text = I8N.text("POST_ACTION_ERR_CMD_EMPTY")
            txtCommand.requestFocusInWindow()
            return
        }

        result = PostDownloadRule(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name,
            extensions = exts,
            command = cmd,
            enabled = chkEnabled.isSelected
        )
        dispose()
    }
}

