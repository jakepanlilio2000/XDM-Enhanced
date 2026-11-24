package xdm.app.ui.screens

import xdm.app.AppContext
import xdm.app.AppContext.app
import xdm.app.I8N.text
import xdm.app.ListSelectionMode
import xdm.app.OS
import xdm.app.XDM_WINDOW_TITLE
import xdm.app.ui.components.*
import xdm.app.update.UpdateChannel
import xdm.app.update.UpdateCheckResult
import xdm.app.update.UpdateChecker
import xdm.app.utils.*
import xdm.core.util.Logger
import xdm.app.skins.SkinManager
import xdm.app.skins.SkinRegion
import xdm.app.skins.SkinStyleResolver
import xdm.app.ui.components.SkinBackgroundPanel
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
    private lateinit var rightPanel: JPanel
    private lateinit var toolbarHolder: JPanel
    private lateinit var toolbar: DownloadToolBar
    private lateinit var upstreamToolbar: AppToolBar
    internal lateinit var sidebar: CategorySidebar
    private lateinit var upstreamSidebar: FilterListPanel
    private lateinit var statusBar: DownloadStatusBar
    private lateinit var southPanel: JPanel
    private lateinit var splitPane: JSplitPane
    private lateinit var titleBar: SkinnedTitleBar
    private lateinit var mainMenuBar: JMenuBar
    private var preShadeSize: Dimension? = null
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
        title = if (SkinManager.currentSkin.layoutConfig.chromeMode.equals("upstream", ignoreCase = true)) {
            "Xtreme Download Manager"
        } else {
            XDM_WINDOW_TITLE
        }
        iconImages = windowIconImages
        SkinnedFrameChromeHelper.configurePreDisplay(this, SkinManager.currentSkin)
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
        if (!AppContext.config.autoUpdateCheck) {
            return
        }
        val channel = UpdateChannel.fromId(AppContext.config.updateChannel)
        UpdateChecker.checkForUpdates(channel) { result ->
            if (result is UpdateCheckResult.UpdateAvailable) {
                val info = result.releaseInfo
                SwingUtilities.invokeLater {
                    updatePanel.showUpdate(info.version, info.htmlUrl, info.isPreRelease, info)
                    if (!isVisible && AppContext.config.updateNotificationsEnabled) {
                        notifyUpdate(info.version)
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
        contentPane = SkinBackgroundPanel(BorderLayout(), region = SkinRegion.MAIN_FRAME)
        createMainMenu()

        titleBar = SkinnedTitleBar(this) { shaded -> applyWindowShade(shaded) }
        add(titleBar, BorderLayout.NORTH)

        sidebar = CategorySidebar { state, category ->
            listView.filterStateChanged(state)
            listView.filterCategoryChanged(category)
        }

        upstreamSidebar = FilterListPanel(
            stateChanged = { listView.filterStateChanged(it) },
            categoryChanged = { listView.filterCategoryChanged(it) }
        )

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

        upstreamToolbar = AppToolBar(
            searchCallback = { listView.searchTextChanged(it) },
            buttonCallback = this,
            sortCallback = { key, asc -> listView.sort(key, asc) },
            settingsSavedCallback = { applyListSelectionMode() }
        )
        upstreamToolbar.setMultiSelectView(false)

        statusBar = DownloadStatusBar()

        wireListView()

        listContainer = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val painted = SkinStyleResolver.paintRegionBackground(g, width, height, SkinRegion.DOWNLOAD_LIST)
                if (!painted) {
                    super.paintComponent(g)
                }
            }
        }.apply {
            background = AppTheme.listBackground
            add(listView.component)
        }

        toolbarHolder = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(toolbar, BorderLayout.CENTER)
        }

        rightPanel = JPanel(BorderLayout()).apply {
            add(toolbarHolder, BorderLayout.NORTH)
            add(listContainer, BorderLayout.CENTER)
            background = AppTheme.listBackground
        }

        val showSidebar = AppContext.config.showCategoriesSidebar
        val initialLoc = AppContext.config.sidebarDividerLocation.takeIf { it > 50 } ?: SkinManager.currentSkin.sidebarWidth.px
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

        southPanel = JPanel().apply {
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
                titleBar.stopStatsTicker()
            }
        })
        ThemeManager.addThemeListener(themeChangeListener)

        ToolTipManager.sharedInstance().initialDelay = 500

        TorrentUiBridge.syncSavedTorrents()
        TorrentUiBridge.applyTorrentConfig()

        upstreamSidebar.countsFrom { listView.records }
        updateTheme()
    }

    private fun isUpstreamChrome(): Boolean =
        SkinManager.currentSkin.layoutConfig.chromeMode.equals("upstream", ignoreCase = true)

    private fun activeSidebarComponent(): Component =
        if (isUpstreamChrome()) upstreamSidebar.component else sidebar

    private fun createListView(filter: MainListViewFilter): DownloadListView =
        when (AppContext.config.listSelectionMode) {
            ListSelectionMode.TABLE -> DownloadTableView(filter)
            ListSelectionMode.CLASSIC -> if (isUpstreamChrome()) UpstreamClassicListView(filter) else ClassicListView(filter)
            ListSelectionMode.MODERN -> ModernListView(filter)
        }

    private fun wireListView() {
        listView.addRowsChangedListener {
            sidebar.scheduleRecount()
            upstreamSidebar.scheduleRecount()
            toolbar.refreshGlobalStates()
            titleBar.updateStats()
        }
        listView.bulkSelectionCallback = {
            toolbar.updateSelectionState(listView.selectedItems)
            upstreamToolbar.setMultiSelectView(listView.selectedRowCount > 1)
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
            ListSelectionMode.CLASSIC -> if (isUpstreamChrome()) UpstreamClassicListView::class else ClassicListView::class
            ListSelectionMode.MODERN -> ModernListView::class
        }
        syncViewModeMenu()
        if (wanted.isInstance(listView)) return
        val old = listView.filter
        val oldSelectedIds = listView.selectedItems.map { it.id }.toSet()
        listView = createListView(MainListViewFilter(old.filterState, old.filterCategory, old.searchText))
        wireListView()
        listView.updateTheme()
        if (oldSelectedIds.isNotEmpty()) {
            listView.selectByIds(oldSelectedIds)
        }
        toolbar.updateSelectionState(listView.selectedItems)
        upstreamToolbar.setMultiSelectView(listView.selectedRowCount > 1)
        listContainer.removeAll()
        listContainer.add(listView.component)
        listContainer.revalidate()
        listContainer.repaint()
        sidebar.scheduleRecount()
        upstreamSidebar.countsFrom { listView.records }
    }

    fun setSidebarVisible(visible: Boolean) {
        val layout = SkinManager.currentSkin.layoutConfig
        val activeSidebar = activeSidebarComponent()
        if (visible && !layout.sidebarPosition.equals("hidden", ignoreCase = true)) {
            if (layout.sidebarPosition.equals("right", ignoreCase = true)) {
                splitPane.leftComponent = rightPanel
                splitPane.rightComponent = activeSidebar
            } else {
                splitPane.leftComponent = activeSidebar
                splitPane.rightComponent = rightPanel
            }
            activeSidebar.isVisible = true
            val defaultW = if (isUpstreamChrome()) 190.px else 210.px
            val loc = AppContext.config.sidebarDividerLocation.takeIf { it > 50 } ?: defaultW
            splitPane.dividerSize = defaultDividerSize
            splitPane.dividerLocation = loc
            SwingUtilities.invokeLater {
                if (AppContext.config.showCategoriesSidebar && (splitPane.leftComponent != null || splitPane.rightComponent == activeSidebar)) {
                    splitPane.dividerLocation = loc
                }
            }
        } else {
            val loc = splitPane.dividerLocation
            if (loc > 50) {
                AppContext.config.sidebarDividerLocation = loc
            }
            if (layout.sidebarPosition.equals("right", ignoreCase = true)) {
                splitPane.rightComponent = null
            } else {
                splitPane.leftComponent = null
            }
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

    fun isSidebarVisible(): Boolean {
        val activeSidebar = activeSidebarComponent()
        return AppContext.config.showCategoriesSidebar &&
            (splitPane.leftComponent == activeSidebar || splitPane.rightComponent == activeSidebar)
    }

    fun applyWindowShade(shaded: Boolean) {
        titleBar.setShadedState(shaded)
        splitPane.isVisible = !shaded
        southPanel.isVisible = !shaded
        val cfg = SkinManager.currentSkin.windowConfig
        if (shaded) {
            if (preShadeSize == null) preShadeSize = size
            val menuH = if (mainMenuBar.isVisible) mainMenuBar.preferredSize.height else 0
            val shadeH = (cfg?.windowShadeHeight ?: 58).px + menuH + (contentPane.insets.top + contentPane.insets.bottom)
            setSize(width, maxOf(shadeH, (cfg?.titleBarHeight ?: 34).px + 8))
        } else {
            preShadeSize?.let { setSize(it) }
            preShadeSize = null
        }
        revalidate()
        repaint()
    }

    fun updateTheme() {
        val os = detectOS()
        if (os == OS.Windows) {
            rootPane.putClientProperty("JRootPane.titleBarBackground", AppTheme.sidebarBackground)
        }
        val skin = SkinManager.currentSkin
        val layout = skin.layoutConfig
        val winCfg = skin.windowConfig
        val upstream = isUpstreamChrome()

        title = winCfg?.titleText?.takeIf { it.isNotBlank() }
            ?: if (upstream) "Xtreme Download Manager" else XDM_WINDOW_TITLE

        mainMenuBar.isVisible = layout.showMenuBar
        titleBar.updateFromSkin()
        (contentPane as? JComponent)?.border = if ((winCfg?.frameBorderWidth ?: 0) > 0) SkinnedFrameBorder() else null

        // Reset WindowShade automatically if skin stops defining customTitleBar
        if (titleBar.isShaded && winCfg?.customTitleBar != true) {
            applyWindowShade(false)
        }

        // Toolbar swap & position
        val activeToolbarComp: Component = if (upstream) upstreamToolbar.component else toolbar
        toolbarHolder.removeAll()
        toolbarHolder.add(activeToolbarComp, BorderLayout.CENTER)
        when (layout.toolbarPosition.lowercase()) {
            "bottom" -> {
                rightPanel.remove(toolbarHolder)
                rightPanel.add(toolbarHolder, BorderLayout.SOUTH)
                toolbarHolder.isVisible = true
            }
            "hidden" -> {
                toolbarHolder.isVisible = false
            }
            else -> {
                rightPanel.remove(toolbarHolder)
                rightPanel.add(toolbarHolder, BorderLayout.NORTH)
                toolbarHolder.isVisible = true
            }
        }
        rightPanel.border = if (upstream) ScaledEmptyBorder(7, 0, 0, 0) else null
        toolbar.setSearchVisible(layout.showSearchBar)
        upstreamToolbar.setSearchVisible(layout.showSearchBar)

        // Sidebar swap & position
        val activeSidebar = activeSidebarComponent()
        val showSidebar = AppContext.config.showCategoriesSidebar && !layout.sidebarPosition.equals("hidden", ignoreCase = true)
        if (showSidebar) {
            if (layout.sidebarPosition.equals("right", ignoreCase = true)) {
                splitPane.leftComponent = rightPanel
                splitPane.rightComponent = activeSidebar
            } else {
                splitPane.leftComponent = activeSidebar
                splitPane.rightComponent = rightPanel
            }
            defaultDividerSize = if (upstream) 1 else 5
            splitPane.dividerSize = defaultDividerSize
            val loc = if (upstream) {
                skin.sidebarWidth.px
            } else {
                AppContext.config.sidebarDividerLocation.takeIf { it > 50 } ?: skin.sidebarWidth.px
            }
            splitPane.dividerLocation = loc
        } else {
            splitPane.leftComponent = null
            splitPane.rightComponent = rightPanel
            splitPane.dividerSize = 0
        }

        statusBar.isVisible = layout.showStatusBar

        val hasWp = skin.hasWallpaper() || skin.hasRegionWallpaper(SkinRegion.MAIN_FRAME)
        splitPane.isOpaque = !hasWp
        rightPanel.isOpaque = !hasWp
        listContainer.isOpaque = !hasWp

        splitPane.border = MatteBorder(1, 0, 0, 0, AppTheme.sidebarBorder)
        splitPane.background = AppTheme.listBackground
        listContainer.background = AppTheme.listBackground
        rightPanel.background = AppTheme.listBackground

        applyListSelectionMode()
        sidebar.updateTheme()
        upstreamSidebar.updateTheme()
        toolbar.updateTheme()
        upstreamToolbar.updateTheme()
        statusBar.updateTheme()
        listView.updateTheme()
        revalidate()
        repaint()
    }

    override fun dispose() {
        ThemeManager.removeThemeListener(themeChangeListener)
        titleBar.stopStatsTicker()
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
        val name = (e.source as? JComponent)?.name ?: e.actionCommand ?: return
        when (name) {
            "TOOL_DOWNLOAD", "MENU_ADD_URL" -> app.addDownload(null)
            "TOOL_CLEAR" -> listView.clear()
            "TOOL_DELETE" -> AppMenuHandler.deleteSelectedDownloads(listView.selectedItems, this)
            "TOOL_SETTINGS" -> SingleWindows.show(SettingsWindow::class) {
                SettingsWindow().apply {
                    onSaved = { applyListSelectionMode() }
                    setLocationRelativeTo(this@AppWindow)
                    loadConfig()
                }
            }
        }
    }

    private fun createMainMenu() {
        val bar = JMenuBar()
        mainMenuBar = bar

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
                    toolbarHolder.isVisible = isSelected
                    toolbarHolder.revalidate()
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
            addSeparator()
            val chkMonitor = JCheckBoxMenuItem("Monitor Clipboard Links", AppContext.config.monitorClipboard).apply {
                addActionListener {
                    AppContext.config.monitorClipboard = isSelected
                    AppContext.config.save()
                    if (isSelected) xdm.app.preflight.ClipboardMonitor.start() else xdm.app.preflight.ClipboardMonitor.stop()
                }
            }
            val chkPause = JCheckBoxMenuItem("Pause Clipboard Monitoring", xdm.app.preflight.ClipboardMonitor.isPaused).apply {
                addActionListener {
                    xdm.app.preflight.ClipboardMonitor.isPaused = isSelected
                }
            }
            add(chkMonitor)
            add(chkPause)
        }

        // 5. Help Menu
        val helpMenu = JMenu("Help").apply {
            add(JMenuItem("Documentation & Wiki", FontIcon(RemixIcon.BOOK_SHELF_LINE, 16.px)).apply {
                addActionListener { openInDefaultBrowser(WEBSITE_HELP_URL) }
            })
            add(JMenuItem("Home Page", FontIcon(RemixIcon.GLOBAL_LINE, 16.px)).apply {
                addActionListener { openInDefaultBrowser(FORK_WEBSITE_URL) }
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
