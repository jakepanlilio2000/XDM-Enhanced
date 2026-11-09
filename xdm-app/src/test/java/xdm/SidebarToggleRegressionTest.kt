package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.ui.components.CategorySidebar
import xdm.app.ui.screens.AppWindow
import xdm.app.utils.logoImage
import xdm.app.utils.px
import java.io.File
import java.nio.file.Files
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath

class SidebarToggleRegressionTest : DownloadManagerTestBase() {

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
    fun testSidebarConfigDefaultsAndPersistence() {
        // Defaults
        assertTrue(config.showCategoriesSidebar, "Sidebar should be visible by default")
        assertEquals(210, config.sidebarDividerLocation, "Default divider location should be 210")

        // Mutate and save
        config.showCategoriesSidebar = false
        config.sidebarDividerLocation = 285
        config.save()

        // Reload fresh config from disk
        val reloaded = AppConfig(dir.absolutePath)
        reloaded.load()
        assertFalse(reloaded.showCategoriesSidebar, "Reloaded config should reflect hidden sidebar")
        assertEquals(285, reloaded.sidebarDividerLocation, "Reloaded config should preserve custom divider width")
    }

    @Test
    fun testSidebarRepeatedTogglingState() {
        // Verify multiple toggles preserve divider width and state
        config.sidebarDividerLocation = 320
        config.showCategoriesSidebar = true
        config.save()

        // Toggle 1: hide
        config.showCategoriesSidebar = false
        config.save()
        val c1 = AppConfig(dir.absolutePath).apply { load() }
        assertFalse(c1.showCategoriesSidebar)
        assertEquals(320, c1.sidebarDividerLocation)

        // Toggle 2: show
        config.showCategoriesSidebar = true
        config.save()
        val c2 = AppConfig(dir.absolutePath).apply { load() }
        assertTrue(c2.showCategoriesSidebar)
        assertEquals(320, c2.sidebarDividerLocation)

        // Toggle 3: hide again
        config.showCategoriesSidebar = false
        config.save()
        val c3 = AppConfig(dir.absolutePath).apply { load() }
        assertFalse(c3.showCategoriesSidebar)
        assertEquals(320, c3.sidebarDividerLocation)
    }

    @Test
    fun testAppWindowSidebarToggleGuiOperations() {
        val window = runOnEdt {
            AppWindow(logoImage(256), startHidden = true).apply {
                setSize(1000, 600)
                validate()
            }
        }
        try {
            val splitPaneField = AppWindow::class.java.getDeclaredField("splitPane").apply { isAccessible = true }
            val splitPane = splitPaneField.get(window) as JSplitPane

            // Initially visible
            assertTrue(window.isSidebarVisible())
            assertNotNull(splitPane.leftComponent)
            assertTrue(splitPane.dividerSize > 0)

            // Hide sidebar
            runOnEdt { window.setSidebarVisible(false) }
            assertFalse(window.isSidebarVisible())
            assertNull(splitPane.leftComponent, "leftComponent must be null when sidebar is hidden")
            assertEquals(0, splitPane.dividerSize, "dividerSize must be 0 when sidebar is hidden")
            assertFalse(AppContext.config.showCategoriesSidebar)

            // Re-show sidebar
            runOnEdt { window.setSidebarVisible(true) }
            assertTrue(window.isSidebarVisible())
            assertNotNull(splitPane.leftComponent, "leftComponent must be restored when sidebar is shown")
            assertTrue(splitPane.dividerSize > 0, "dividerSize must be restored when sidebar is shown")
            assertTrue(AppContext.config.showCategoriesSidebar)
            assertTrue(splitPane.dividerLocation >= 200, "dividerLocation must be restored to saved width")
        } finally {
            runOnEdt { window.dispose() }
        }
    }

    @Test
    fun testAppWindowRapidToggleAndSelectionPreservation() {
        val window = runOnEdt {
            AppWindow(logoImage(256), startHidden = true).apply {
                setSize(1000, 600)
                validate()
            }
        }
        try {
            val sidebarField = AppWindow::class.java.getDeclaredField("sidebar").apply { isAccessible = true }
            val sidebar = sidebarField.get(window) as CategorySidebar

            val treeField = CategorySidebar::class.java.getDeclaredField("tree").apply { isAccessible = true }
            val tree = treeField.get(sidebar) as JTree

            val initialPath = runOnEdt { tree.selectionPath }
            assertNotNull(initialPath, "Sidebar tree should have an initial selected path")

            // Rapidly toggle 5 times
            runOnEdt {
                repeat(5) {
                    window.setSidebarVisible(false)
                    window.setSidebarVisible(true)
                }
            }

            assertTrue(window.isSidebarVisible())
            val pathAfter = runOnEdt { tree.selectionPath }
            assertEquals(initialPath, pathAfter, "Tree selection path must remain preserved across rapid toggles")
        } finally {
            runOnEdt { window.dispose() }
        }
    }

    @Test
    fun testAppWindowStartupWithSidebarHidden() {
        config.showCategoriesSidebar = false
        config.sidebarDividerLocation = 310
        config.save()

        val window = runOnEdt {
            AppWindow(logoImage(256), startHidden = true).apply {
                setSize(1000, 600)
                validate()
            }
        }
        try {
            val splitPaneField = AppWindow::class.java.getDeclaredField("splitPane").apply { isAccessible = true }
            val splitPane = splitPaneField.get(window) as JSplitPane

            assertFalse(window.isSidebarVisible(), "Sidebar must start hidden when showCategoriesSidebar is false")
            assertNull(splitPane.leftComponent)
            assertEquals(0, splitPane.dividerSize)

            // Show it
            runOnEdt { window.setSidebarVisible(true) }
            assertTrue(window.isSidebarVisible())
            assertNotNull(splitPane.leftComponent)
            assertTrue(splitPane.dividerSize > 0)
            assertEquals(310, splitPane.dividerLocation, "Divider location must match saved location 310")
        } finally {
            runOnEdt { window.dispose() }
        }
    }
}
