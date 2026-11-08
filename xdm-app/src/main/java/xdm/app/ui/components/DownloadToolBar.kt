package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.ui.screens.AboutDialog
import xdm.app.ui.screens.SettingsWindow
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.SingleWindows
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.showMenu
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.Window
import java.awt.event.ActionListener
import javax.swing.*

class DownloadToolBar(
    private val onSearch: (String) -> Unit,
    private val onSort: (SortKey, Boolean) -> Unit,
    private val onAddUrl: () -> Unit,
    private val onAddMagnet: () -> Unit,
    private val onAddTorrent: () -> Unit,
    private val onResume: () -> Unit,
    private val onStop: () -> Unit,
    private val onStopAll: () -> Unit,
    private val onDelete: () -> Unit,
    private val onDeleteCompleted: () -> Unit,
    private val onScheduler: () -> Unit,
    private val onSettingsSaved: () -> Unit = {}
) : JPanel(BorderLayout()) {

    private val toolbar = JToolBar().apply {
        isFloatable = false
        isOpaque = false
        border = BorderFactory.createEmptyBorder(2.px, 4.px, 2.px, 4.px)
    }

    private val btnAddUrl: JButton
    private val btnAddMagnet: JButton
    private val btnAddTorrent: JButton
    private val btnResume: JButton
    private val btnStop: JButton
    private val btnStopAll: JButton
    private val btnDelete: JButton
    private val btnDeleteCompleted: JButton
    private val btnScheduler: JButton
    private val btnSettings: JButton
    private val btnSort: JButton
    private val txtSearch: JTextField

    private val sortMenu: JPopupMenu

    init {
        isOpaque = false
        border = BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.sidebarBorder)

        btnAddUrl = createToolButton(RemixIcon.GLOBAL_LINE, "Add URL", "Download from web address", onAddUrl)
        btnAddMagnet = createToolButton(RemixIcon.DOWNLOAD_2_LINE, "Add Magnet", "Download via BitTorrent magnet link", onAddMagnet)
        btnAddTorrent = createToolButton(RemixIcon.FILE_ZIP_LINE, "Add Torrent", "Open .torrent file", onAddTorrent)
        btnResume = createToolButton(RemixIcon.PLAY_CIRCLE_LINE, "Resume", "Resume selected downloads", onResume).apply { isEnabled = false }
        btnStop = createToolButton(RemixIcon.PAUSE_CIRCLE_LINE, "Stop", "Pause selected downloads", onStop).apply { isEnabled = false }
        btnStopAll = createToolButton(RemixIcon.CLOSE_CIRCLE_LINE, "Stop All", "Pause all active downloads", onStopAll).apply { isEnabled = false }
        btnDelete = createToolButton(RemixIcon.DELETE_BIN_LINE, "Delete", "Delete selected downloads", onDelete).apply { isEnabled = false }
        btnDeleteCompleted = createToolButton(RemixIcon.BRUSH_LINE, "Completed", "Delete completed downloads", onDeleteCompleted)
        btnScheduler = createToolButton(RemixIcon.LIST_CHECK_2, "Scheduler", "Download queues and schedule", onScheduler)
        btnSettings = createToolButton(RemixIcon.SETTINGS_4_LINE, "Settings", "Options and settings") { showSettings() }

        sortMenu = createSortMenu()
        btnSort = JButton(FontIcon(RemixIcon.SORT_DESC, 18.px)).apply {
            toolTipText = "Sort downloads"
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
            margin = Insets(4.px, 6.px, 4.px, 6.px)
            isFocusable = false
            addActionListener { showMenu(btnSort, sortMenu) }
        }

        txtSearch = JTextField().apply {
            putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search downloads...")
            putClientProperty(FlatClientProperties.STYLE, "arc: 12")
            preferredSize = Dimension(170.px, 26.px)
            maximumSize = Dimension(220.px, 26.px)
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onSearch(text)
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onSearch(text)
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onSearch(text)
            })
        }

        toolbar.add(btnAddUrl)
        toolbar.add(btnAddMagnet)
        toolbar.add(btnAddTorrent)
        toolbar.addSeparator(Dimension(8.px, 28.px))
        toolbar.add(btnResume)
        toolbar.add(btnStop)
        toolbar.add(btnStopAll)
        toolbar.addSeparator(Dimension(8.px, 28.px))
        toolbar.add(btnDelete)
        toolbar.add(btnDeleteCompleted)
        toolbar.addSeparator(Dimension(8.px, 28.px))
        toolbar.add(btnScheduler)
        toolbar.add(btnSettings)

        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = ScaledEmptyBorder(0, 4, 0, 8)
            add(txtSearch)
            add(Box.createRigidArea(Dimension(4.px, 0)))
            add(btnSort)
        }

        add(toolbar, BorderLayout.WEST)
        add(rightPanel, BorderLayout.EAST)

        refreshGlobalStates()
    }

    fun updateTheme() {
        border = BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.sidebarBorder)
        repaint()
    }

    private fun createToolButton(
        icon: RemixIcon,
        title: String,
        tooltip: String,
        action: () -> Unit
    ): JButton {
        return JButton(title, FontIcon(icon, 20.px)).apply {
            toolTipText = tooltip
            verticalTextPosition = SwingConstants.BOTTOM
            horizontalTextPosition = SwingConstants.CENTER
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
            font = font.deriveFont(11f.px)
            margin = Insets(4.px, 6.px, 4.px, 6.px)
            isFocusable = false
            addActionListener { action() }
        }
    }

    private fun createSortMenu(): JPopupMenu {
        val menu = JPopupMenu()
        val group = ButtonGroup()

        fun addSortOption(name: String, key: SortKey, asc: Boolean, isDefault: Boolean = false) {
            val item = JRadioButtonMenuItem(name, isDefault).apply {
                addActionListener { onSort(key, asc) }
            }
            group.add(item)
            menu.add(item)
        }

        addSortOption("Date (Newest first)", SortKey.DATE, false, isDefault = true)
        addSortOption("Date (Oldest first)", SortKey.DATE, true)
        menu.addSeparator()
        addSortOption("Name (A to Z)", SortKey.NAME, true)
        addSortOption("Name (Z to A)", SortKey.NAME, false)
        menu.addSeparator()
        addSortOption("Size (Largest first)", SortKey.SIZE, false)
        addSortOption("Size (Smallest first)", SortKey.SIZE, true)
        return menu
    }

    val isResumeEnabled: Boolean get() = btnResume.isEnabled
    val isStopEnabled: Boolean get() = btnStop.isEnabled
    val isStopAllEnabled: Boolean get() = btnStopAll.isEnabled
    val isDeleteEnabled: Boolean get() = btnDelete.isEnabled
    val isDeleteCompletedEnabled: Boolean get() = btnDeleteCompleted.isEnabled

    fun updateSelectionState(selected: List<DbRecord>) {
        btnResume.isEnabled = selected.any { it.status.isStopped }
        btnStop.isEnabled = selected.any {
            it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY || it.status == RecordStatus.ASSEMBLING
        }
        btnDelete.isEnabled = selected.isNotEmpty()
        refreshGlobalStates()
    }

    fun refreshGlobalStates() {
        btnStopAll.isEnabled = AppContext.db.count {
            it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY || it.status == RecordStatus.ASSEMBLING
        } > 0
        btnDeleteCompleted.isEnabled = AppContext.db.count { it.status == RecordStatus.FINISHED } > 0
    }

    private fun showSettings() {
        SingleWindows.show(SettingsWindow::class) {
            SettingsWindow().apply {
                onSaved = onSettingsSaved
                setLocationRelativeTo(SwingUtilities.windowForComponent(this@DownloadToolBar))
                loadConfig()
            }
        }
    }
}

