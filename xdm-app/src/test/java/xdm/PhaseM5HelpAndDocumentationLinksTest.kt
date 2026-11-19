package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.I8N
import xdm.app.ui.components.FilterListPanel
import xdm.app.ui.components.ThemeManager
import xdm.app.ui.screens.AboutDialog
import xdm.app.ui.screens.AppWindow
import xdm.app.utils.*
import xdm.integration.BrowserIntegrationRepair
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PhaseM5HelpAndDocumentationLinksTest : DownloadManagerTestBase() {

    private val artifactDir = File("C:/Users/Administrator/.gemini/antigravity/brain/1021f44a-ff70-4fe4-af84-ab35c2654b54")

    @BeforeEach
    fun setUpHelpAudit() {
        I8N.loadTexts("en")
        artifactDir.mkdirs()
    }

    private fun <T> runOnEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: T? = null
        var err: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                result = block()
            } catch (t: Throwable) {
                err = t
            }
        }
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun findAllComponents(container: Container): List<Component> {
        val list = mutableListOf<Component>()
        for (c in container.components) {
            list.add(c)
            if (c is Container) {
                list.addAll(findAllComponents(c))
            }
        }
        return list
    }

    private fun captureComponent(comp: Component, width: Int, height: Int, file: File) {
        comp.size = Dimension(width, height)
        comp.addNotify()
        comp.validate()
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            comp.paint(g)
        } finally {
            g.dispose()
        }
        ImageIO.write(img, "PNG", file)
    }

    // =========================================================================
    // 1. Authoritative URL & HTTPS Validation Audit
    // =========================================================================
    @Test
    fun test01_authoritativeUrlConstantsAndHttpsIntegrity() {
        // Fork primary URLs
        assertEquals("https://jakepanlilio2000.github.io/XDM-Enhanced", FORK_WEBSITE_URL)
        assertEquals("https://jakepanlilio2000.github.io/XDM-Enhanced/docs/", FORK_DOCS_URL)
        assertEquals("https://jakepanlilio2000.github.io/XDM-Enhanced/troubleshooting.html", FORK_TROUBLESHOOTING_URL)
        assertEquals(FORK_DOCS_URL, WEBSITE_HELP_URL, "Help URL must route directly to canonical fork docs")

        // Fork GitHub assets
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced", GITHUB_REPO_URL)
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced/releases", GITHUB_RELEASES_URL)
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced/issues", GITHUB_ISSUES_URL)
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced/wiki", GITHUB_WIKI_URL)

        // Upstream reference URLs (preserved for attribution)
        assertEquals("https://xtremedownloadmanager.com", UPSTREAM_WEBSITE_URL)

        // Strict HTTPS helper tests
        assertTrue(isHttpOrHttpsUrl("https://jakepanlilio2000.github.io/XDM-Enhanced/"))
        assertTrue(isHttpOrHttpsUrl("http://localhost:8597/ping"))
        assertFalse(isHttpOrHttpsUrl("file:///C:/Windows/System32/calc.exe"))
        assertFalse(isHttpOrHttpsUrl("javascript:alert(1)"))
        assertFalse(isHttpOrHttpsUrl("not a valid url"))
        assertFalse(isHttpOrHttpsUrl(""))
    }

    // =========================================================================
    // 2. Real HTTP Connectivity & Identity Verification
    // =========================================================================
    @Test
    fun test02_liveEndpointsRespondWithHttp200() {
        val targets = listOf(
            FORK_WEBSITE_URL,
            FORK_DOCS_URL,
            FORK_TROUBLESHOOTING_URL,
            GITHUB_WIKI_URL,
            GITHUB_ISSUES_URL,
            "https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/SUPPORT.md"
        )

        for (target in targets) {
            val url = URI(target).toURL()
            var code = -1
            var connectionFailed = false
            for (attempt in 1..3) {
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) XDM-Enhanced-Test")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true

                try {
                    code = conn.responseCode
                    if (code in 200..399) break
                } catch (e: Exception) {
                    connectionFailed = true
                } finally {
                    conn.disconnect()
                }
                if (attempt < 3) Thread.sleep(500)
            }

            // In CI/offline or when upstream GitHub returns transient errors (e.g. 503, 429), skip rather than fail
            if (connectionFailed || code in listOf(429, 500, 502, 503, 504) || code == -1) {
                assumeTrue(false, "Remote endpoint $target returned transient code $code or connection failed; skipping live probe")
            } else {
                assertTrue(
                    code in 200..399,
                    "Target URL $target must respond with HTTP 2xx/3xx, got: $code"
                )
            }
        }
    }

    // =========================================================================
    // 3. UI Help Menu & Toolbar Action Verification
    // =========================================================================
    @Test
    fun test03_appWindowHelpMenuParityAndDispatch() {
        runOnEdt {
            val win = AppWindow(logoImage(256), startHidden = true)
            try {
                val menuBar = win.jMenuBar
                assertNotNull(menuBar, "AppWindow must have a menu bar")

                val helpMenu = menuBar.getMenu(4)
                assertEquals("Help", helpMenu.text)

                val menuItems = (0 until helpMenu.itemCount).mapNotNull { helpMenu.getItem(it) }
                val itemTexts = menuItems.map { it.text }

                val expectedItems = listOf("Documentation & Wiki", "Home Page", "Check for Updates...", "About XDM Enhanced...")
                assertEquals(expectedItems, itemTexts)

                val docItem = menuItems.find { it.text == "Documentation & Wiki" }
                assertNotNull(docItem)
                assertEquals(1, docItem!!.actionListeners.size, "Documentation & Wiki must have an action listener")

                val homeItem = menuItems.find { it.text == "Home Page" }
                assertNotNull(homeItem)
                assertEquals(1, homeItem!!.actionListeners.size, "Home Page must have an action listener")
            } finally {
                win.dispose()
            }
        }
    }

    // =========================================================================
    // 4. Sidebar & Browser Integration Troubleshooting Links
    // =========================================================================
    @Test
    fun test04_sidebarAndBrowserTroubleshootingTargets() {
        // 1. Sidebar "Stay in touch" link in FilterListPanel
        val filterField = FilterListPanel::class.java.getDeclaredField("SUPPORT_URL").apply { isAccessible = true }
        val supportUrl = filterField.get(null) as String
        assertEquals(
            "https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/SUPPORT.md",
            supportUrl,
            "Sidebar 'Stay in touch' link must point to fork SUPPORT.md"
        )
        assertFalse(supportUrl.contains("subhra74"), "Sidebar link must not point to upstream author repo")

        // 2. Browser Integration Repair Troubleshooting URL
        assertEquals(
            FORK_TROUBLESHOOTING_URL,
            BrowserIntegrationRepair.TROUBLESHOOTING_URL,
            "Browser integration repair must point to live fork troubleshooting guide"
        )
    }

    // =========================================================================
    // 5. About Dialog Upstream Attribution & Fork Navigation Preservation
    // =========================================================================
    @Test
    fun test05_aboutDialogAttributionIntegrity() {
        runOnEdt {
            val about = AboutDialog(null)
            try {
                val all = findAllComponents(about.contentPane)
                val buttons = all.filterIsInstance<JButton>()

                val starBtn = buttons.find { it.text == "Star on GitHub" }
                assertNotNull(starBtn, "Must have Star on GitHub button")
                assertEquals(GITHUB_REPO_URL, starBtn!!.toolTipText)

                val sourceBtn = buttons.find { it.text == "Source Code" }
                assertNotNull(sourceBtn, "Must have Source Code button")
                assertEquals(GITHUB_REPO_URL, sourceBtn!!.toolTipText)

                val upstreamBtn = buttons.find { it.text == "Upstream Website" }
                assertNotNull(upstreamBtn, "Must have Upstream Website attribution button")
                assertEquals(UPSTREAM_WEBSITE_URL, upstreamBtn!!.toolTipText)
            } finally {
                about.dispose()
            }
        }
    }

    // =========================================================================
    // 6. Visual Theme Snapshot Capture (Light & Dark)
    // =========================================================================
    @Test
    fun test06_visualThemeSnapshotCapture() {
        runOnEdt {
            val win = AppWindow(logoImage(256), startHidden = true)
            try {
                val menuBar = win.jMenuBar

                // Render in Light Theme
                ThemeManager.applyTheme("light", null, 100)
                val lightPanel = JPanel().apply {
                    layout = java.awt.BorderLayout()
                    add(menuBar, java.awt.BorderLayout.NORTH)
                    size = Dimension(720, 240)
                }
                captureComponent(lightPanel, 720, 240, File(artifactDir, "screenshot-phase-m5-help-menu-light.png"))

                // Render in Dark Theme
                ThemeManager.applyTheme("dark", null, 100)
                val darkPanel = JPanel().apply {
                    layout = java.awt.BorderLayout()
                    add(menuBar, java.awt.BorderLayout.NORTH)
                    size = Dimension(720, 240)
                }
                captureComponent(darkPanel, 720, 240, File(artifactDir, "screenshot-phase-m5-help-menu-dark.png"))

                // Reset theme to light
                ThemeManager.applyTheme("light", null, 100)
            } finally {
                win.dispose()
            }
        }
    }
}
