package xdm.app.ui.screens

import xdm.app.AppContext
import xdm.app.AppContext.app
import xdm.app.I8N.text
import xdm.app.ListSelectionMode
import xdm.app.OS
import xdm.app.XDM_WINDOW_TITLE
import xdm.app.ui.components.*
import xdm.app.update.UpdateChecker
import xdm.app.utils.*
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.MatteBorder

/**
 * [startHidden]: XDM started minimized (at login). The update check then waits [HIDDEN_UPDATE_CHECK_DELAY_MS],
 * or until the window is first shown, and a found update is announced with a system notification.
 */
class AppWindow(image: Image, startHidden: Boolean = false) : JFrame(), ActionListener {
    internal var listView: DownloadListView = createListView(MainListViewFilter(FilterState.All, null, ""))
    private lateinit var listContainer: JPanel
    private lateinit var toolbar: DownloadToolBar
    private lateinit var sidebar: CategorySidebar
    private lateinit var statusBar: DownloadStatusBar
    private lateinit var splitPane: JSplitPane
    private var defaultDividerSize = 5
    private var chkSidebar: JCheckBoxMenuItem? = null
    private val updatePanel = UpdatePanel()
    private val autoStartPanel = AutoStartPanel()
    /** The banners at the bottom of the window, stacked. */
    private val bannerStack = JPanel()
    /** EDT only. One "extension out of date" banner per browser, made when first needed. */
    private val extensionUpdatePanels = mutableMapOf<Browser, ExtensionUpdatePanel>()
    /** EDT only. The delayed update check of a hidden start, until it runs. */
    private var pendingUpdateCheck: Timer? = null
    private val themeChangeListener = ThemeManager.ThemeChangeListener { updateTheme() }
    private var optTableViewItem: JRadioButtonMenuItem? = null
    private var optClassicCardsItem: JRadioButtonMenuItem? = null
    private var optModernCardsItem: JRadioButtonMenuItem? = null

    init {
        title = XDM_WINDOW_TITLE
        iconImages = windowIconImages
        val os = detectOS()
        if (os == OS.Windows) {
            getRootPane().putClientProperty("JRootPane.titleBarBackground", AppTheme.sidebarBackground)
        } else if (os == OS.MacOS) {
            applyMacOSWindowCustomizations(image)
        }

        setWindowSizeAndPosition()
        initWindow()
        if (startHidden) {
            scheduleUpdateCheck()
        } else {
            checkForUpdates()
        }
    }

    /**
     * A start at login stays off the network for a while: a process that starts with the session
     * and goes online at once is what behaviour-based antivirus heuristics look hardest at, and the
     * machine is busy logging in anyway. Showing the window cuts the wait short.
     */
    private fun scheduleUpdateCheck() {
        pendingUpdateCheck = Timer(HIDDEN_UPDATE_CHECK_DELAY_MS) { runPendingUpdateCheck() }.apply {
            isRepeats = false
            start()
        }
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) = runPendingUpdateCheck()
        })
    }

    private fun runPendingUpdateCheck() {
        val timer = pendingUpdateCheck ?: return
        timer.stop()
        pendingUpdateCheck = null
        checkForUpdates()
    }

    /**
     * Kicks off a background update check; if a newer release (pre-releases included) is found,
     * reveals the update banner at the bottom of the window (on the EDT), and when the window is
     * not on screen also announces it with a system notification.
     */
    private fun checkForUpdates() {
        UpdateChecker.checkForUpdate { info ->
            if (info != null) {
                SwingUtilities.invokeLater {
                    updatePanel.showUpdate(info.latestVersion, info.downloadUrl, info.isPreRelease)
                    if (!isVisible) {
                        notifyUpdate(info.latestVersion)
                    }
                }
            }
        }
    }

    /**
     * The banner is in a window nobody sees, so post a notification instead - once per version, or
     * every start at login would repeat it. A click brings up the window, banner included.
     */
    private fun notifyUpdate(version: String) {
        val config = AppContext.config
        if (config.notifiedUpdateVersion == version) {
            return
        }
        config.notifiedUpdateVersion = version
        config.save()
        showTrayNotification(text("UPDATE_NOTIFY_TITLE"), String.format(text("UPDATE_NOTIFY_MSG"), version))
    }

    /**
     * Shows the "extension out of date" banner for [browser], and when the window is not on screen
     * also posts a notification; a click on it brings up the window, banner included. EDT only.
     */
    fun showExtensionUpdate(browser: Browser) {
        val panel = extensionUpdatePanels.getOrPut(browser) {
            ExtensionUpdatePanel(browser).also { bannerStack.add(it) }
        }
        panel.showBanner()
        if (!isVisible) {
            showTrayNotification(
                text("EXT_UPDATE_NOTIFY_TITLE"),
                String.format(text("EXT_UPDATE_NOTIFY_MSG"), browser.displayName)
            )
        }
    }

    private fun initWindow() {
        createMainMenu()

        sidebar = CategorySidebar { state, category ->
            listView.filterStateChanged(state)
            listView.filterCategoryChanged(category)
        }

        toolbar = DownloadToolBar(
            onSearch = { listView.searchTextChanged(it) },
            onSort = { key, asc -> listView.sort(key, asc) },
            onAddUrl = { app.addDownload(null) },
            onAddMagnet = { TorrentUiBridge.addMagnet(this) },
            onAddTorrent = { TorrentUiBridge.addTorrentFile(this) },
            onResume = { listView.selectedItems.forEach { AppMenuHandler.resumeDownload(it) } },
            onStop = { listView.selectedItems.forEach { AppMenuHandler.pauseDownload(it) } },
            onStopAll = {
                AppContext.db.filter { it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY }
                    .forEach { AppMenuHandler.pauseDownload(it) }
            },
            onDelete = { AppMenuHandler.deleteSelectedDownloads(listView.selectedItems, this) },
            onDeleteCompleted = { AppMenuHandler.deleteCompleted(this) },
            onScheduler = { AppContext.app.showSchedulerWindow(0) },
            onSettingsSaved = { applyListSelectionMode() }
        )

        statusBar = DownloadStatusBar()

        wireListView()

        listContainer = JPanel(BorderLayout()).apply {
            background = AppTheme.listBackground
            add(listView.component)
        }

        val rightPanel = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(listContainer, BorderLayout.CENTER)
            background = AppTheme.listBackground
        }

        val showSidebar = AppContext.config.showCategoriesSidebar
        val initialLoc = AppContext.config.sidebarDividerLocation.takeIf { it > 50 } ?: 210.px
        splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            border = MatteBorder(1, 0, 0, 0, AppTheme.sidebarBorder)
            defaultDividerSize = dividerSize
            dividerLocation = initialLoc
            leftComponent = if (showSidebar) sidebar else null
            if (!showSidebar) {
                dividerSize = 0
            }
            rightComponent = rightPanel
            background = AppTheme.listBackground
            addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) { evt ->
                val loc = evt.newValue as? Int ?: return@addPropertyChangeListener
                if (AppContext.config.showCategoriesSidebar && leftComponent != null && loc > 50) {
                    AppContext.config.sidebarDividerLocation = loc
                }
            }
        }

        add(splitPane, BorderLayout.CENTER)

        val southPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(statusBar)
            add(bannerStack.apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(autoStartPanel)
                add(updatePanel)
            })
        }
        add(southPanel, BorderLayout.SOUTH)

        addWindowListener(object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) = autoStartPanel.refresh()
            override fun windowClosing(e: WindowEvent) {
                ThemeManager.removeThemeListener(themeChangeListener)
                statusBar.dispose()
            }
        })
        ThemeManager.addThemeListener(themeChangeListener)

        ToolTipManager.sharedInstance().initialDelay = 500

        TorrentUiBridge.syncSavedTorrents()
        TorrentUiBridge.applyTorrentConfig()
    }

    private fun createListView(filter: MainListViewFilter): DownloadListView =
        when (AppContext.config.listSelectionMode) {
            ListSelectionMode.TABLE -> DownloadTableView(filter)
            ListSelectionMode.CLASSIC -> ClassicListView(filter)
            ListSelectionMode.MODERN -> ModernListView(filter)
        }

    private fun wireListView() {
        listView.addRowsChangedListener {
            sidebar.scheduleRecount()
            toolbar.refreshGlobalStates()
        }
        listView.bulkSelectionCallback = {
            toolbar.updateSelectionState(listView.selectedItems)
        }
    }

    private fun syncViewModeMenu() {
        val mode = AppContext.config.listSelectionMode
        optTableViewItem?.isSelected = (mode == ListSelectionMode.TABLE)
        optClassicCardsItem?.isSelected = (mode == ListSelectionMode.CLASSIC)
        optModernCardsItem?.isSelected = (mode == ListSelectionMode.MODERN)
    }

    /**
     * After Settings is saved: swaps in the other list if the selection mode changed. The new list
     * keeps the filter, search text, and preserves selected item IDs.
     */
    internal fun applyListSelectionMode() {
        val wanted = when (AppContext.config.listSelectionMode) {
            ListSelectionMode.TABLE -> DownloadTableView::class
            ListSelectionMode.CLASSIC -> ClassicListView::class
            ListSelectionMode.MODERN -> ModernListView::class
        }
        syncViewModeMenu()
        if (wanted.isInstance(listView)) return
        val old = listView.filter
        val oldSelectedIds = listView.selectedItems.map { it.id }.toSet()
        listView = createListView(MainListViewFilter(old.filterState, old.filterCategory, old.searchText))
        wireListView()
        if (oldSelectedIds.isNotEmpty()) {
            listView.selectByIds(oldSelectedIds)
        }
        toolbar.updateSelectionState(listView.selectedItems)
        listContainer.removeAll()
        listContainer.add(listView.component)
        listContainer.revalidate()
        listContainer.repaint()
        sidebar.scheduleRecount()
    }

    fun setSidebarVisible(visible: Boolean) {
        if (visible) {
            splitPane.leftComponent = sidebar
            sidebar.isVisible = true
            val loc = AppContext.config.sidebarDividerLocation.takeIf { it > 50 } ?: 210.px
            splitPane.dividerSize = defaultDividerSize
            splitPane.dividerLocation = loc
            SwingUtilities.invokeLater {
                if (AppContext.config.showCategoriesSidebar && splitPane.leftComponent != null) {
                    splitPane.dividerLocation = loc
                }
            }
        } else {
            val loc = splitPane.dividerLocation
            if (loc > 50) {
                AppContext.config.sidebarDividerLocation = loc
            }
            splitPane.leftComponent = null
            splitPane.dividerSize = 0
        }
        if (chkSidebar?.isSelected != visible) {
            chkSidebar?.isSelected = visible
        }
        AppContext.config.showCategoriesSidebar = visible
        AppContext.config.save()
        splitPane.revalidate()
        splitPane.repaint()
    }

    fun isSidebarVisible(): Boolean = AppContext.config.showCategoriesSidebar && splitPane.leftComponent != null

    fun updateTheme() {
        val os = detectOS()
        if (os == OS.Windows) {
            rootPane.putClientProperty("JRootPane.titleBarBackground", AppTheme.sidebarBackground)
        }
        splitPane.border = MatteBorder(1, 0, 0, 0, AppTheme.sidebarBorder)
        splitPane.background = AppTheme.listBackground
        listContainer.background = AppTheme.listBackground
        sidebar.updateTheme()
        toolbar.updateTheme()
        statusBar.updateTheme()
        listView.updateTheme()
        revalidate()
        repaint()
    }

    override fun dispose() {
        ThemeManager.removeThemeListener(themeChangeListener)
        super.dispose()
    }

    fun updateDownloadInView(index: Int) {
        listView.rowUpdated(index)
    }

    fun deleteDownloadInView(index: Int) {
        listView.rowDeleted(index)
    }

    fun downloadsRemovedInView() {
        listView.rowsRemoved()
    }

    fun addDownloadInView(index: Int) {
        listView.rowAdded(index)
    }

    override fun actionPerformed(e: ActionEvent) {
        Logger.info("XDM", "Command: ${e.actionCommand}")
    }

    private fun createMainMenu() {
        val bar = JMenuBar()

        // 1. File Menu
        val fileMenu = JMenu("File").apply {
            add(JMenuItem("Add URL...", FontIcon(RemixIcon.GLOBAL_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK)
                addActionListener { app.addDownload(null) }
            })
            add(JMenuItem("Add Magnet Link...", FontIcon(RemixIcon.DOWNLOAD_2_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK)
                addActionListener { TorrentUiBridge.addMagnet(this@AppWindow) }
            })
            add(JMenuItem("Add Torrent File...", FontIcon(RemixIcon.FILE_ZIP_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK)
                addActionListener { TorrentUiBridge.addTorrentFile(this@AppWindow) }
            })
            add(JMenuItem("Batch Download...", FontIcon(RemixIcon.FILE_LIST_2_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK)
                addActionListener { AppMenuHandler.showBatchDialog(this@AppWindow) }
            })
            addSeparator()
            add(JMenuItem("Delete Selected", FontIcon(RemixIcon.DELETE_BIN_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
                addActionListener { AppMenuHandler.deleteSelectedDownloads(listView.selectedItems, this@AppWindow) }
            })
            add(JMenuItem("Delete Completed", FontIcon(RemixIcon.BRUSH_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.deleteCompleted(this@AppWindow) }
            })
            addSeparator()
            add(JMenuItem("Exit", FontIcon(RemixIcon.CLOSE_CIRCLE_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK)
                addActionListener { kotlin.system.exitProcess(0) }
            })
        }

        // 2. Downloads Menu
        val dwnMenu = JMenu("Downloads").apply {
            add(JMenuItem("Resume Selected", FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)
                addActionListener { listView.selectedItems.forEach { AppMenuHandler.resumeDownload(it) } }
            })
            add(JMenuItem("Pause Selected", FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0)
                addActionListener { listView.selectedItems.forEach { AppMenuHandler.pauseDownload(it) } }
            })
            add(JMenuItem("Stop All Transfers", FontIcon(RemixIcon.CLOSE_CIRCLE_LINE, 16.px)).apply {
                addActionListener {
                    AppContext.db.filter { it.status == RecordStatus.DOWNLOADING || it.status == RecordStatus.READY }
                        .forEach { AppMenuHandler.pauseDownload(it) }
                }
            })
            add(JMenuItem("Restart Selected", FontIcon(RemixIcon.ARROW_UP_DOWN_FILL, 16.px)).apply {
                addActionListener { listView.selectedItems.forEach { AppMenuHandler.restartDownload(it) } }
            })
            addSeparator()
            add(JMenuItem("Download Scheduler...", FontIcon(RemixIcon.LIST_CHECK_2, 16.px)).apply {
                addActionListener { AppContext.app.showSchedulerWindow(0) }
            })
        }

        // 3. View Menu
        val viewMenu = JMenu("View").apply {
            val chk = JCheckBoxMenuItem("Show Categories Sidebar", AppContext.config.showCategoriesSidebar).apply {
                addActionListener { setSidebarVisible(isSelected) }
            }
            chkSidebar = chk
            val chkToolbar = JCheckBoxMenuItem("Show Toolbar", true).apply {
                addActionListener {
                    toolbar.isVisible = isSelected
                    toolbar.revalidate()
                }
            }
            val chkStatusBar = JCheckBoxMenuItem("Show Status Bar", true).apply {
                addActionListener {
                    statusBar.isVisible = isSelected
                    statusBar.revalidate()
                }
            }
            chkSidebar?.let { add(it) }
            add(chkToolbar)
            add(chkStatusBar)
            addSeparator()

            val modeMenu = JMenu("View Mode")
            val group = ButtonGroup()
            val optTable = JRadioButtonMenuItem("Table View", AppContext.config.listSelectionMode == ListSelectionMode.TABLE).apply {
                addActionListener {
                    AppContext.config.listSelectionMode = ListSelectionMode.TABLE
                    AppContext.config.save()
                    applyListSelectionMode()
                }
            }
            val optClassic = JRadioButtonMenuItem("Classic Cards", AppContext.config.listSelectionMode == ListSelectionMode.CLASSIC).apply {
                addActionListener {
                    AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
                    AppContext.config.save()
                    applyListSelectionMode()
                }
            }
            val optModern = JRadioButtonMenuItem("Modern Cards", AppContext.config.listSelectionMode == ListSelectionMode.MODERN).apply {
                addActionListener {
                    AppContext.config.listSelectionMode = ListSelectionMode.MODERN
                    AppContext.config.save()
                    applyListSelectionMode()
                }
            }
            optTableViewItem = optTable
            optClassicCardsItem = optClassic
            optModernCardsItem = optModern
            group.add(optTable)
            group.add(optClassic)
            group.add(optModern)
            modeMenu.add(optTable)
            modeMenu.add(optClassic)
            modeMenu.add(optModern)
            add(modeMenu)
        }

        // 4. Tools Menu
        val toolsMenu = JMenu("Tools").apply {
            add(JMenuItem("Options / Settings...", FontIcon(RemixIcon.SETTINGS_4_LINE, 16.px)).apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)
                addActionListener {
                    SingleWindows.show(SettingsWindow::class) {
                        SettingsWindow().apply {
                            onSaved = { applyListSelectionMode() }
                            setLocationRelativeTo(this@AppWindow)
                            loadConfig()
                        }
                    }
                }
            })
            add(JMenuItem("Refresh Download Link...", FontIcon(RemixIcon.ARROW_UP_DOWN_FILL, 16.px)).apply {
                addActionListener { AppMenuHandler.openRefreshPage(this@AppWindow) }
            })
            add(JMenuItem("Browser Integration...", FontIcon(RemixIcon.CHROME_FILL, 16.px)).apply {
                addActionListener { BrowserSetupDialog(this@AppWindow).isVisible = true }
            })
            add(JMenuItem("Network Optimizer", FontIcon(RemixIcon.SPEED_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.optimizeRWin() }
            })
            add(JMenuItem("Language...", FontIcon(RemixIcon.GLOBAL_LINE, 16.px)).apply {
                addActionListener { AppMenuHandler.showLanguageDlg(this@AppWindow) }
            })
        }

        // 5. Help Menu
        val helpMenu = JMenu("Help").apply {
            add(JMenuItem("Documentation & Wiki", FontIcon(RemixIcon.BOOK_SHELF_LINE, 16.px)).apply {
                addActionListener { openInDefaultBrowser(WEBSITE_HELP_URL) }
            })
            add(JMenuItem("Home Page", FontIcon(RemixIcon.GLOBAL_LINE, 16.px)).apply {
                addActionListener { openInDefaultBrowser(WEBSITE_URL) }
            })
            add(JMenuItem("Check for Updates...", FontIcon(RemixIcon.NOTIFICATION_3_LINE, 16.px)).apply {
                addActionListener {
                    UpdateChecker.checkForUpdate { info ->
                        SwingUtilities.invokeLater {
                            if (info != null) {
                                updatePanel.showUpdate(info.latestVersion, info.downloadUrl, info.isPreRelease)
                            } else {
                                MessageBox.show(this@AppWindow, "Update Check", "You are running the latest version of XDM Enhanced.")
                            }
                        }
                    }
                }
            })
            addSeparator()
            add(JMenuItem("About XDM Enhanced...", FontIcon(RemixIcon.FILE_SHIELD_LINE, 16.px)).apply {
                addActionListener { AboutDialog.open(this@AppWindow) }
            })
        }

        bar.add(fileMenu)
        bar.add(dwnMenu)
        bar.add(viewMenu)
        bar.add(toolsMenu)
        bar.add(helpMenu)

        jMenuBar = bar
    }

    /** Back where the user left it (monitor, position, size, maximized), else 980x580 centered. */
    private fun setWindowSizeAndPosition() {
        WindowGeometry.restore(this, "main", 980, 580)
    }

    val selectedDownloads: List<DbRecord>
        get() = listView.selectedItems

    fun selectDownloadById(id: Long) {
        (listView as? DownloadTableView)?.selectById(id)
    }
}

/** How long a start at login waits before its update check. */
private const val HIDDEN_UPDATE_CHECK_DELAY_MS = 5 * 60 * 1000
