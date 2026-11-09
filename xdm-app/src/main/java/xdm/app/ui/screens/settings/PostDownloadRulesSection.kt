package xdm.app.ui.screens.settings

import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.rules.PostDownloadActionEngine
import xdm.app.rules.PostDownloadRule
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.createIcon
import xdm.app.utils.fixHeight
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.scaledSize
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.io.File
import java.util.Collections
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

/**
 * Settings section for configuring post-download actions and extension rules (upstream #1409).
 */
class PostDownloadRulesSection {

    val tglRunCmd = SettingsToggle()
    private val rules = mutableListOf<PostDownloadRule>()
    val txtDefaultCmd = rounded(JTextField()).apply {
        columns = 25
        fixHeight(this)
    }
    private val btnTestDefault = settingsButton(I8N.text("POST_ACTION_RULE_TEST")) { testDefaultCommand() }

    private val tableModel = RulesTableModel()
    private val table = JTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowHeight = 28.px
        tableHeader.reorderingAllowed = false
        putClientProperty("JTable.autoStartsEdit", false)
    }

    private val btnAdd = settingsButton(I8N.text("POST_ACTION_RULE_ADD")) { addRule() }
    private val btnEdit = settingsButton(I8N.text("POST_ACTION_RULE_EDIT")) { editSelectedRule() }
    private val btnDelete = settingsButton(I8N.text("POST_ACTION_RULE_DELETE")) { deleteSelectedRule() }
    private val btnUp = settingsButton(I8N.text("POST_ACTION_RULE_MOVE_UP")) { moveSelectedUp() }
    private val btnDown = settingsButton(I8N.text("POST_ACTION_RULE_MOVE_DOWN")) { moveSelectedDown() }
    private val btnTestRule = settingsButton(I8N.text("POST_ACTION_RULE_TEST")) { testSelectedRule() }

    private val emptyLabel = settingsHint(I8N.text("POST_ACTION_EMPTY")).apply {
        alignmentX = Component.LEFT_ALIGNMENT
    }

    val component: JComponent by lazy {
        initTableColumns()

        val tableScroll = rounded(JScrollPane(table)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(scaledSize(580, 160))
            maximumSize = Dimension(Int.MAX_VALUE, scaledSize(200, 200).height)
        }

        val buttonBar = Box.createHorizontalBox().apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(btnAdd)
            add(Box.createRigidArea(scaledSize(6, 0)))
            add(btnEdit)
            add(Box.createRigidArea(scaledSize(6, 0)))
            add(btnDelete)
            add(Box.createRigidArea(scaledSize(6, 0)))
            add(btnUp)
            add(Box.createRigidArea(scaledSize(6, 0)))
            add(btnDown)
            add(Box.createHorizontalGlue())
            add(btnTestRule)
        }

        val fallbackInputBox = Box.createHorizontalBox().apply {
            alignmentX = Component.LEFT_ALIGNMENT
            val h = txtDefaultCmd.preferredSize.height
            txtDefaultCmd.maximumSize = Dimension(Int.MAX_VALUE, h)
            add(txtDefaultCmd)
            add(Box.createRigidArea(scaledSize(8, 0)))
            add(btnTestDefault)
        }

        val varsHint = settingsHint(I8N.text("POST_ACTION_VARS_HINT")).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val rulesContainer = Box.createVerticalBox().apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(settingsDescription(I8N.text("POST_ACTION_RULES_DESC")))
            add(Box.createRigidArea(scaledSize(0, 8)))
            add(tableScroll)
            add(Box.createRigidArea(scaledSize(0, 6)))
            add(emptyLabel)
            add(Box.createRigidArea(scaledSize(0, 8)))
            add(buttonBar)
            add(Box.createRigidArea(scaledSize(0, 12)))
            add(varsHint)
        }

        table.selectionModel.addListSelectionListener { updateButtonStates() }
        tglRunCmd.addActionListener { updateEnabledState() }

        settingsSection(
            I8N.text("SETTINGS_SEC_POST_DOWNLOAD_ACTIONS"),
            settingsRow(
                I8N.text("MSG_RUN_POST_DOWNLOAD_ACTIONS"),
                I8N.text("MSG_RUN_POST_DOWNLOAD_ACTIONS_SUB"),
                tglRunCmd
            ),
            settingsFullRow(null, null, rulesContainer),
            settingsFullRow(
                I8N.text("POST_ACTION_DEFAULT_CMD"),
                I8N.text("POST_ACTION_DEFAULT_CMD_SUB"),
                fallbackInputBox
            )
        )
    }

    private fun initTableColumns() {
        val colEnabled = table.columnModel.getColumn(0)
        colEnabled.preferredWidth = 55.px
        colEnabled.maxWidth = 65.px
        colEnabled.minWidth = 50.px

        val colName = table.columnModel.getColumn(1)
        colName.preferredWidth = 120.px

        val colExts = table.columnModel.getColumn(2)
        colExts.preferredWidth = 110.px

        val colCmd = table.columnModel.getColumn(3)
        colCmd.preferredWidth = 260.px
    }

    private fun owner() = SwingUtilities.getWindowAncestor(table)

    private fun addRule() {
        val parent = owner() ?: return
        val dialog = PostDownloadRuleDialog(parent, null, true)
        dialog.isVisible = true
        dialog.result?.let { newRule ->
            rules.add(newRule)
            tableModel.fireTableDataChanged()
            val newIdx = rules.size - 1
            table.setRowSelectionInterval(newIdx, newIdx)
            updateButtonStates()
        }
    }

    private fun editSelectedRule() {
        val row = table.selectedRow
        if (row < 0 || row >= rules.size) return
        val parent = owner() ?: return
        val existing = rules[row]
        val dialog = PostDownloadRuleDialog(parent, existing, true)
        dialog.isVisible = true
        dialog.result?.let { updated ->
            rules[row] = updated
            tableModel.fireTableRowsUpdated(row, row)
            updateButtonStates()
        }
    }

    private fun deleteSelectedRule() {
        val row = table.selectedRow
        if (row < 0 || row >= rules.size) return
        rules.removeAt(row)
        tableModel.fireTableDataChanged()
        if (rules.isNotEmpty()) {
            val nextRow = row.coerceAtMost(rules.size - 1)
            table.setRowSelectionInterval(nextRow, nextRow)
        }
        updateButtonStates()
    }

    private fun moveSelectedUp() {
        val row = table.selectedRow
        if (row <= 0 || row >= rules.size) return
        Collections.swap(rules, row, row - 1)
        tableModel.fireTableDataChanged()
        table.setRowSelectionInterval(row - 1, row - 1)
        updateButtonStates()
    }

    private fun moveSelectedDown() {
        val row = table.selectedRow
        if (row < 0 || row >= rules.size - 1) return
        Collections.swap(rules, row, row + 1)
        tableModel.fireTableDataChanged()
        table.setRowSelectionInterval(row + 1, row + 1)
        updateButtonStates()
    }

    private fun testSelectedRule() {
        val row = table.selectedRow
        if (row < 0 || row >= rules.size) return
        val rule = rules[row]
        val parent = owner() ?: return
        val dlg = PostDownloadTestDialog(parent, { rule.command }, rule.extensions)
        dlg.isVisible = true
    }

    private fun testDefaultCommand() {
        val parent = owner() ?: return
        val dlg = PostDownloadTestDialog(parent, { txtDefaultCmd.text }, listOf("bin"))
        dlg.isVisible = true
    }

    fun updateEnabledState() {
        val on = tglRunCmd.isSelected
        table.isEnabled = on
        btnAdd.isEnabled = on
        txtDefaultCmd.isEnabled = on
        btnTestDefault.isEnabled = on && txtDefaultCmd.text.isNotBlank()
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val on = tglRunCmd.isSelected
        val row = table.selectedRow
        val hasSelection = on && (row >= 0 && row < rules.size)
        btnEdit.isEnabled = hasSelection
        btnDelete.isEnabled = hasSelection
        btnUp.isEnabled = hasSelection && row > 0
        btnDown.isEnabled = hasSelection && row < rules.size - 1
        btnTestRule.isEnabled = hasSelection
        emptyLabel.isVisible = rules.isEmpty()
    }

    fun load() {
        val config = AppContext.config
        tglRunCmd.isSelected = config.runCommand
        txtDefaultCmd.text = config.customCommand
        rules.clear()
        rules.addAll(config.postDownloadRules)
        tableModel.fireTableDataChanged()
        updateEnabledState()
    }

    fun save() {
        val config = AppContext.config
        config.runCommand = tglRunCmd.isSelected
        config.customCommand = txtDefaultCmd.text.trim()
        config.postDownloadRules = rules.toList()
    }

    private inner class RulesTableModel : AbstractTableModel() {
        private val colNames = arrayOf(
            I8N.text("POST_ACTION_COL_ENABLED"),
            I8N.text("POST_ACTION_COL_NAME"),
            I8N.text("POST_ACTION_COL_EXTENSIONS"),
            I8N.text("POST_ACTION_COL_COMMAND")
        )

        override fun getRowCount(): Int = rules.size
        override fun getColumnCount(): Int = colNames.size
        override fun getColumnName(column: Int): String = colNames[column]

        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            0 -> java.lang.Boolean::class.java
            else -> java.lang.String::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = (columnIndex == 0)

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val rule = rules[rowIndex]
            return when (columnIndex) {
                0 -> rule.enabled
                1 -> rule.name
                2 -> if (rule.extensions.isEmpty() || rule.extensions.contains("*")) "*" else PostDownloadRule.formatExtensions(rule.extensions)
                3 -> rule.command
                else -> ""
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0 && aValue is Boolean) {
                rules[rowIndex].enabled = aValue
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }
    }
}
