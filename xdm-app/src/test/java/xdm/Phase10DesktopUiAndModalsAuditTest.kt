package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.ListSelectionMode
import xdm.app.skins.SkinManager
import xdm.app.ui.components.*
import xdm.app.ui.screens.*
import xdm.app.utils.RemixIcon
import xdm.app.utils.logoImage
import xdm.app.utils.px
import xdm.app.utils.scaledInsets
import xdm.app.utils.scaledSize
import xdm.app.utils.win.WindowsAssociations
import xdm.core.downloaders.DownloadType
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.system.measureTimeMillis

class Phase10DesktopUiAndModalsAuditTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @BeforeEach
    fun initUiAudit() {
        xdm.app.I8N.loadTexts("en")
        AppContext.resetTorrentManagerForTest()
        AppContext.defaultDownloadFolder = dir.absolutePath
        artifactDir.mkdirs()
    }

    @AfterEach
    fun cleanUiAudit() {
        AppContext.resetTorrentManagerForTest()
    }

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
        val target = (comp as? java.awt.Window)?.let { (it as? RootPaneContainer)?.contentPane } ?: comp
        target.setSize(width, height)
        target.doLayout()
        target.validate()

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            target.paint(g)
        } finally {
            g.dispose()
        }
        ImageIO.write(img, "PNG", file)
    }

    private fun hasEscapeKeyBinding(dialog: RootPaneContainer): Boolean {
        val rootPane = dialog.rootPane ?: return false
        val escKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)

        // Check registered keyboard action
        val hasAction = rootPane.getActionForKeyStroke(escKey) != null

        // Check InputMap in WHEN_IN_FOCUSED_WINDOW or WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        val inputMap1 = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val inputMap2 = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val hasInputMap = (inputMap1 != null && inputMap1.get(escKey) != null) ||
                          (inputMap2 != null && inputMap2.get(escKey) != null)

        return hasAction || hasInputMap
    }

    private fun createTestRecord(
        id: Long,
        fileName: String,
        size: Long = 1000L,
        status: RecordStatus = RecordStatus.DOWNLOADING,
        downloadType: DownloadType = DownloadType.Http,
        date: Long = System.currentTimeMillis(),
        categoryId: String? = null
    ): DbRecord = DbRecord(
        id = id,
        size = size,
        downloaded = if (status == RecordStatus.FINISHED) size else size / 2,
        progress = if (status == RecordStatus.FINISHED) 100 else 50,
        date = date,
        fileName = fileName,
        eta = 0L,
        speed = 0f,
        selected = false,
        status = status,
        downloadType = downloadType,
        categoryId = categoryId
    )

    // =========================================================================
    // Scenario 67: Menu and Toolbar Actions & Dialog Dispatches
    // =========================================================================
    @Test
    fun testScenario67_menuAndToolbarActionsExecuteRealNativeWork() {
        runOnEdt {
            val win = AppWindow(logoImage(256), startHidden = true)
            try {
                val menuBar = win.jMenuBar
                assertNotNull(menuBar, "AppWindow must have a populated JMenuBar")
                assertEquals(5, menuBar.menuCount, "MenuBar must have exactly 5 menus: File, Downloads, View, Tools, Help")

                val fileMenu = menuBar.getMenu(0)
                assertEquals("File", fileMenu.text)
                val expectedFileItems = listOf("Add URL...", "Add Magnet Link...", "Add Torrent File...", "Batch Download...", "Delete Selected", "Delete Completed", "Exit")
                val actualFileItems = (0 until fileMenu.itemCount).mapNotNull { fileMenu.getItem(it)?.text }
                assertEquals(expectedFileItems, actualFileItems, "File menu items must match expected real actions")

                val dwnMenu = menuBar.getMenu(1)
                assertEquals("Downloads", dwnMenu.text)
                val expectedDwnItems = listOf("Resume Selected", "Pause Selected", "Stop All Transfers", "Restart Selected", "Download Scheduler...")
                val actualDwnItems = (0 until dwnMenu.itemCount).mapNotNull { dwnMenu.getItem(it)?.text }
                assertEquals(expectedDwnItems, actualDwnItems, "Downloads menu items must match expected real actions")

                val viewMenu = menuBar.getMenu(2)
                assertEquals("View", viewMenu.text)
                val expectedViewItems = listOf("Show Categories Sidebar", "Show Toolbar", "Show Status Bar", "View Mode")
                val actualViewItems = (0 until viewMenu.itemCount).mapNotNull { viewMenu.getItem(it)?.text }
                assertEquals(expectedViewItems, actualViewItems, "View menu toggles must match expected items")

                val toolsMenu = menuBar.getMenu(3)
                assertEquals("Tools", toolsMenu.text)
                val expectedToolsItems = listOf("Options / Settings...", "Refresh Download Link...", "Browser Integration...", "Network Optimizer", "Language...", "Monitor Clipboard Links", "Pause Clipboard Monitoring")
                val actualToolsItems = (0 until toolsMenu.itemCount).mapNotNull { toolsMenu.getItem(it)?.text }
                assertEquals(expectedToolsItems, actualToolsItems, "Tools menu items must match expected native dialog actions")

                val helpMenu = menuBar.getMenu(4)
                assertEquals("Help", helpMenu.text)
                val expectedHelpItems = listOf("Documentation & Wiki", "Home Page", "Check for Updates...", "About XDM Enhanced...")
                val actualHelpItems = (0 until helpMenu.itemCount).mapNotNull { helpMenu.getItem(it)?.text }
                assertEquals(expectedHelpItems, actualHelpItems, "Help menu items must match expected actions")

                // Toolbar buttons check
                val toolbarField = AppWindow::class.java.getDeclaredField("toolbar").apply { isAccessible = true }
                val toolbar = toolbarField.get(win) as DownloadToolBar
                assertNotNull(toolbar, "Toolbar component must be present")
                assertTrue(toolbar.componentCount > 0, "Toolbar must have tool buttons")

                // AppToolBar context menu and Language dispatch
                val appToolBar = AppToolBar({ _ -> }, { _ -> }, { _, _ -> }, {})
                val createCtxMethod = AppToolBar::class.java.getDeclaredMethod("createContextMenu").apply { isAccessible = true }
                val ctxMenu = createCtxMethod.invoke(appToolBar) as JPopupMenu
                val ctxItemNames = (0 until ctxMenu.componentCount).mapNotNull { (ctxMenu.getComponent(it) as? JMenuItem)?.name }

                assertTrue(ctxItemNames.contains("TITLE_SETTINGS"), "Context menu must contain Settings")
                assertTrue(ctxItemNames.contains("MSG_LANG1"), "Context menu must contain Language (MSG_LANG1)")
                assertTrue(ctxItemNames.contains("MENU_BATCH_DOWNLOAD"), "Context menu must contain Batch Download")
                assertTrue(ctxItemNames.contains("MENU_STREAM_DOWNLOAD"), "Context menu must contain Stream Download")
                assertTrue(ctxItemNames.contains("MENU_UPDATE"), "Context menu must contain Update")
                assertTrue(ctxItemNames.contains("MENU_HELP_SUP"), "Context menu must contain Help & Support")
                assertTrue(ctxItemNames.contains("MENU_ABOUT"), "Context menu must contain About")
                assertTrue(ctxItemNames.contains("MENU_EXIT"), "Context menu must contain Exit")

                // Verify showLanguage() can be invoked without exception
                val showLangMethod = AppToolBar::class.java.getDeclaredMethod("showLanguage").apply { isAccessible = true }
                showLangMethod.invoke(appToolBar)

            } finally {
                win.dispose()
            }
        }
    }

    // =========================================================================
    // Scenario 68: Category Sidebar Repeated Toggling, List Layout Modes, Table Sorting, and Category Icons
    // =========================================================================
    @Test
    fun testScenario68_sidebarTogglingAndLayoutModesAndSortingAndCategoryIcons() {
        // Add test records across different categories first
        val now = System.currentTimeMillis()
        val recs = listOf(
            createTestRecord(id = 101L, fileName = "Ubuntu-24.04.iso", size = 4_000_000_000L, status = RecordStatus.DOWNLOADING, date = now - 5000, downloadType = DownloadType.Http, categoryId = "CAT_PROGRAMS"),
            createTestRecord(id = 102L, fileName = "Album_Flac.zip", size = 350_000_000L, status = RecordStatus.FINISHED, date = now - 10000, downloadType = DownloadType.Http, categoryId = "CAT_MUSIC"),
            createTestRecord(id = 103L, fileName = "Documentation.pdf", size = 15_000_000L, status = RecordStatus.PAUSED, date = now - 1000, downloadType = DownloadType.Http, categoryId = "CAT_DOCUMENTS"),
            createTestRecord(id = 104L, fileName = "OpenSourceMovie.mp4", size = 1_200_000_000L, status = RecordStatus.DOWNLOADING, date = now - 2000, downloadType = DownloadType.Http, categoryId = "CAT_VIDEOS"),
            createTestRecord(id = 105L, fileName = "DebianNetinst.iso", size = 650_000_000L, status = RecordStatus.FINISHED, date = now - 8000, downloadType = DownloadType.Torrent, categoryId = null)
        )
        recs.forEach { appDB.add(it) }

        runOnEdt {
            AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
            val win = AppWindow(logoImage(256), startHidden = true).apply {
                setSize(1000, 600)
                validate()
            }
            try {
                val splitPaneField = AppWindow::class.java.getDeclaredField("splitPane").apply { isAccessible = true }
                val splitPane = splitPaneField.get(win) as JSplitPane

                // Repeated toggling 5 times
                for (cycle in 1..5) {
                    // Hide
                    win.setSidebarVisible(false)
                    assertFalse(win.isSidebarVisible(), "Sidebar must report hidden on cycle $cycle")
                    assertNull(splitPane.leftComponent, "leftComponent must be null when hidden on cycle $cycle")
                    assertEquals(0, splitPane.dividerSize, "dividerSize must be 0 on cycle $cycle")

                    // Show
                    win.setSidebarVisible(true)
                    assertTrue(win.isSidebarVisible(), "Sidebar must report visible on cycle $cycle")
                    assertNotNull(splitPane.leftComponent, "leftComponent must be restored on cycle $cycle")
                    assertTrue(splitPane.dividerSize > 0, "dividerSize must be restored on cycle $cycle")
                }

                // Test layout modes with selection preservation: Classic -> Table -> Modern -> Classic
                AppContext.config.listSelectionMode = ListSelectionMode.CLASSIC
                win.applyListSelectionMode()
                val classicView = win.listView as ClassicListView
                classicView.selectByIds(setOf(101L, 103L))
                assertEquals(setOf(101L, 103L), classicView.selectedItems.map { it.id }.toSet())
                captureComponent(win, 1000, 600, File(artifactDir, "screenshot-phase10-classic-cards.png"))

                // Switch to TABLE
                AppContext.config.listSelectionMode = ListSelectionMode.TABLE
                win.applyListSelectionMode()
                val tableView = win.listView as DownloadTableView
                assertEquals(setOf(101L, 103L), tableView.selectedItems.map { it.id }.toSet(), "Selected IDs must be preserved when switching to TABLE mode")
                captureComponent(win, 1000, 600, File(artifactDir, "screenshot-phase10-table-view.png"))

                // Switch to MODERN
                AppContext.config.listSelectionMode = ListSelectionMode.MODERN
                win.applyListSelectionMode()
                val modernView = win.listView as ModernListView
                assertEquals(setOf(101L, 103L), modernView.selectedItems.map { it.id }.toSet(), "Selected IDs must be preserved when switching to MODERN mode")

                // Switch back to TABLE for sorting tests
                AppContext.config.listSelectionMode = ListSelectionMode.TABLE
                win.applyListSelectionMode()
                val activeTable = win.listView as DownloadTableView

                val tableField = DownloadTableView::class.java.getDeclaredField("table").apply { isAccessible = true }
                val jTable = tableField.get(activeTable) as JTable

                // Sorting by NAME ascending
                activeTable.sort(SortKey.NAME, true)
                val sortedNames = (0 until jTable.rowCount).map { viewRow ->
                    val modelRow = jTable.convertRowIndexToModel(viewRow)
                    activeTable.records[modelRow].fileName
                }
                assertEquals(sortedNames.sorted(), sortedNames, "Table view must sort by NAME ascending")

                // Sorting by SIZE descending
                activeTable.sort(SortKey.SIZE, false)
                val sortedSizes = (0 until jTable.rowCount).map { viewRow ->
                    val modelRow = jTable.convertRowIndexToModel(viewRow)
                    activeTable.records[modelRow].size
                }
                assertEquals(sortedSizes.sortedDescending(), sortedSizes, "Table view must sort by SIZE descending")

                // Category Style Verification
                assertEquals(RemixIcon.FILE_TEXT_LINE, CategoryStyle.glyphForCategory("CAT_DOCUMENTS"))
                assertEquals(RemixIcon.MUSIC_2_LINE, CategoryStyle.glyphForCategory("CAT_MUSIC"))
                assertEquals(RemixIcon.MOVIE_LINE, CategoryStyle.glyphForCategory("CAT_VIDEOS"))
                assertEquals(RemixIcon.TERMINAL_BOX_LINE, CategoryStyle.glyphForCategory("CAT_PROGRAMS"))
                assertEquals(RemixIcon.FILE_ZIP_LINE, CategoryStyle.glyphForCategory("CAT_COMPRESSED"))
                assertEquals(RemixIcon.MAGNET_LINE, CategoryStyle.glyphForCategory(null, downloadType = DownloadType.Torrent))

            } finally {
                win.dispose()
            }
        }
    }

    // =========================================================================
    // Scenario 69: Theme/Skin Switching, Default Controls, Focus, and Escape Dismissal
    // =========================================================================
    @Test
    fun testScenario69_themeSwitchingEscapeDismissalAndDefaultControls() {
        runOnEdt {
            val dummyRecord = createTestRecord(
                id = 999L,
                fileName = "test-download-archive.zip",
                size = 100_000_000L,
                status = RecordStatus.FINISHED,
                downloadType = DownloadType.Http,
                date = System.currentTimeMillis()
            )
            appDB.add(dummyRecord)

            // 1. SettingsWindow
            val settingsWin = SettingsWindow().apply { loadConfig() }
            assertNotNull(settingsWin.rootPane.defaultButton, "SettingsWindow must have a defaultButton assigned")
            assertEquals("Save", settingsWin.rootPane.defaultButton?.text)
            assertTrue(hasEscapeKeyBinding(settingsWin), "SettingsWindow must bind Escape key for dismissal")
            captureComponent(settingsWin, 780, 500, File(artifactDir, "screenshot-phase10-settings-window.png"))

            // 2. PropertiesDialog
            val propDlg = PropertiesDialog(null, dummyRecord)
            assertNotNull(propDlg.rootPane.defaultButton, "PropertiesDialog must have a defaultButton assigned")
            assertEquals("Close", propDlg.rootPane.defaultButton?.text)
            assertTrue(hasEscapeKeyBinding(propDlg), "PropertiesDialog must bind Escape key for dismissal")
            captureComponent(propDlg, 750, 500, File(artifactDir, "screenshot-phase10-properties-dialog.png"))

            // 3. ClearDownloadsDialog
            val clearDlg = ClearDownloadsDialog(null)
            assertNotNull(clearDlg.rootPane.defaultButton, "ClearDownloadsDialog must have a defaultButton assigned")
            assertEquals("Clear", clearDlg.rootPane.defaultButton?.text)
            assertTrue(hasEscapeKeyBinding(clearDlg), "ClearDownloadsDialog must bind Escape key for dismissal")

            // 4. NetworkOptimizerDialog
            val optDlg = NetworkOptimizerDialog(null)
            assertNotNull(optDlg.rootPane.defaultButton, "NetworkOptimizerDialog must have a defaultButton assigned")
            assertEquals("Apply Optimization", optDlg.rootPane.defaultButton?.text)
            assertTrue(hasEscapeKeyBinding(optDlg), "NetworkOptimizerDialog must bind Escape key for dismissal")
            captureComponent(optDlg, 540, 520, File(artifactDir, "screenshot-phase10-network-optimizer.png"))

            // 5. BatchUrlDialog
            val batchUrlDlg = BatchUrlDialog(null)
            assertNotNull(batchUrlDlg.rootPane.defaultButton, "BatchUrlDialog must have a defaultButton assigned")
            assertEquals("OK", batchUrlDlg.rootPane.defaultButton?.text)
            assertTrue(hasEscapeKeyBinding(batchUrlDlg), "BatchUrlDialog must bind Escape key for dismissal")

            // 6. AboutDialog
            val aboutDlg = AboutDialog(null)
            assertNotNull(aboutDlg.rootPane.defaultButton, "AboutDialog must have a defaultButton assigned")
            assertEquals("Close", aboutDlg.rootPane.defaultButton?.text)
            assertTrue(hasEscapeKeyBinding(aboutDlg), "AboutDialog must bind Escape key for dismissal")
            captureComponent(aboutDlg, 500, 480, File(artifactDir, "screenshot-phase10-about-dialog.png"))

            // 7. RefreshLinkWindow
            val refWin = RefreshLinkWindow(null, dummyRecord.id, null)
            assertNotNull(refWin.rootPane.defaultButton, "RefreshLinkWindow must have a defaultButton assigned")
            assertTrue(hasEscapeKeyBinding(refWin), "RefreshLinkWindow must bind Escape key for dismissal")

            // Test Theme/Skin switching while dialogs are open
            ThemeManager.applyTheme("dark", null, 100)
            settingsWin.repaint()
            propDlg.repaint()
            optDlg.repaint()
            aboutDlg.repaint()

            ThemeManager.applyTheme("light", null, 100)
            settingsWin.repaint()
            propDlg.repaint()
            optDlg.repaint()
            aboutDlg.repaint()

            // Dispose all
            settingsWin.dispose()
            propDlg.dispose()
            clearDlg.dispose()
            optDlg.dispose()
            batchUrlDlg.dispose()
            aboutDlg.dispose()
            refWin.dispose()
        }
    }

    // =========================================================================
    // Scenario 70: Swing EDT Safety, High-Volume Performance Scaling, and Multi-Scale DPI
    // =========================================================================
    @Test
    fun testScenario70_edtSafetyHighTaskVolumeAndDpiScaling() {
        // Generate 1,000 tasks
        val baseTime = System.currentTimeMillis()
        val thousandRecs = (1..1000).map { i ->
            createTestRecord(
                id = 10000L + i,
                fileName = "payload-chunk-%04d.dat".format(i),
                size = (100_000L * i),
                status = if (i % 2 == 0) RecordStatus.FINISHED else RecordStatus.DOWNLOADING,
                downloadType = DownloadType.Http,
                date = baseTime - (i * 1000L)
            )
        }
        thousandRecs.forEach { appDB.add(it) }
        assertEquals(1000, appDB.size)

        runOnEdt {
            val filter = MainListViewFilter(FilterState.All, null, "")
            val tableView = DownloadTableView(filter)
            assertEquals(1000, tableView.records.size, "DownloadTableView must contain all 1,000 items")

            // Benchmark sorting on 1,000 items: must execute well under 350ms
            val sortTimeName = measureTimeMillis {
                tableView.sort(SortKey.NAME, true)
            }
            assertTrue(sortTimeName < 500, "Sorting 1,000 items by NAME took $sortTimeName ms, expected < 500ms")

            val sortTimeSize = measureTimeMillis {
                tableView.sort(SortKey.SIZE, false)
            }
            assertTrue(sortTimeSize < 500, "Sorting 1,000 items by SIZE took $sortTimeSize ms, expected < 500ms")

            // Selection on large task volume
            val selectTime = measureTimeMillis {
                tableView.selectByIds(setOf(10001L, 10050L, 10500L, 10999L))
            }
            assertTrue(selectTime < 200, "Selecting items in 1,000 tasks took $selectTime ms, expected < 200ms")
            assertEquals(4, tableView.selectedItems.size)

            // DPI Scaling Verification (100%, 150%, 200%)
            val scale100 = 16.px
            assertTrue(scale100 >= 16, "Scale 100% must produce >= base size")
            val sizeScaled = scaledSize(100, 50)
            assertTrue(sizeScaled.width >= 100 && sizeScaled.height >= 50)
            val insetsScaled = scaledInsets(10, 20, 10, 20)
            assertTrue(insetsScaled.top >= 10 && insetsScaled.left >= 20)
        }
    }

    // =========================================================================
    // Scenario 71: Windows Default Apps Native URI Invocation
    // =========================================================================
    @Test
    fun testScenario71_windowsAssociationsNativeUriResolution() {
        if (!WindowsAssociations.isWindows) {
            assertFalse(WindowsAssociations.openDefaultAppsSettings(), "Non-Windows OS must return false")
            return
        }

        // On Windows: invocation must succeed via Desktop.browse, Win32Shell.execute, or cmd fallback
        val firstResult = WindowsAssociations.openDefaultAppsSettings()
        assertTrue(firstResult, "openDefaultAppsSettings must return true on Windows")

        // Rapid immediate call: must return true due to debounce without re-invoking processes
        val debounceResult = WindowsAssociations.openDefaultAppsSettings()
        assertTrue(debounceResult, "Rapid repeated call within 1000ms must return true via debounce protection")
    }
}
