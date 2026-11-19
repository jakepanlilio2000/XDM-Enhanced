package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.ui.components.SegmentDetailTable
import xdm.app.ui.screens.AboutDialog
import xdm.app.utils.*
import xdm.core.downloaders.web.SegmentProgress
import java.io.File
import javax.swing.JLabel
import javax.swing.SwingUtilities

class PhaseM1AdjustmentsAuditTest {

    @Test
    fun testAdjustment1SegmentDetailTableClosedRangeAndSelection() {
        val tableComp = SegmentDetailTable()
        val segs = listOf(
            SegmentProgress(start = 0L, length = 1_000_000L, downloaded = 500_000L, id = 1L, status = "Receiving data..."),
            SegmentProgress(start = 1_000_000L, length = 1_000_000L, downloaded = 1_000_000L, id = 2L, status = "Complete"),
            SegmentProgress(start = 2_000_000L, length = 0L, downloaded = 0L, id = 3L, status = "Connecting...")
        )

        tableComp.updateSegments(segs, 2_000_000L)
        val table = tableComp.table
        assertEquals(3, table.rowCount)

        // Check per-connection details and status
        assertEquals("Receiving data...", table.getValueAt(0, 2))
        assertEquals("Complete", table.getValueAt(1, 2))
        assertEquals("Connecting...", table.getValueAt(2, 2))
        assertTrue(table.getValueAt(0, 1).toString().contains("50%"))
        assertTrue(table.getValueAt(1, 1).toString().contains("100%"))

        // Selection preservation check
        table.setRowSelectionInterval(1, 1)
        assertEquals(1, table.selectedRow)

        // Re-run updateSegments with updated progress
        val updatedSegs = listOf(
            SegmentProgress(start = 0L, length = 1_000_000L, downloaded = 600_000L, id = 1L, status = "Receiving data..."),
            SegmentProgress(start = 1_000_000L, length = 1_000_000L, downloaded = 1_000_000L, id = 2L, status = "Complete"),
            SegmentProgress(start = 2_000_000L, length = 500_000L, downloaded = 100_000L, id = 3L, status = "Receiving data...")
        )
        tableComp.updateSegments(updatedSegs, 2_500_000L)

        // Selected row must be retained
        assertEquals(1, table.selectedRow, "Selected row 1 must be preserved across segment ticks")
        assertEquals("Receiving data...", table.getValueAt(2, 2))
    }

    @Test
    fun testAdjustment3AboutDialogGpl3LicenseNotice() {
        var about: AboutDialog? = null
        SwingUtilities.invokeAndWait {
            about = AboutDialog(null)
        }
        try {
            val allComponents = findAllComponents(about!!.contentPane)
            val labels = allComponents.filterIsInstance<JLabel>()
            val combinedText = labels.mapNotNull { it.text }.joinToString(" ")

            assertTrue(
                combinedText.contains("GNU General Public License v3.0 (GPL-3.0-or-later)"),
                "AboutDialog Overview must specify GPL-3.0-or-later"
            )
            assertTrue(
                combinedText.contains("GPL-3.0-or-later"),
                "AboutDialog Credits must cite GPL-3.0-or-later for upstream architecture"
            )
            assertFalse(
                combinedText.contains("GNU General Public License v2.0 (GPL-2.0)"),
                "AboutDialog must NOT contain obsolete GPL-2.0 string"
            )
        } finally {
            SwingUtilities.invokeAndWait {
                about?.dispose()
            }
        }
    }

    @Test
    fun testAdjustment4InstallerSilentAppCheckAndRtfFormat() {
        val repoRoot = File(System.getProperty("user.dir")).parentFile.let {
            if (File(it, "packaging").isDirectory) it else File(System.getProperty("user.dir"))
        }

        // 1. RTF header test
        val rtfFile = File(repoRoot, "packaging/windows/gpl-3.0-notice.rtf")
        assertTrue(rtfFile.exists() && rtfFile.isFile, "gpl-3.0-notice.rtf must exist")
        val rtfLines = rtfFile.readLines(Charsets.UTF_8)
        assertTrue(rtfLines[0].startsWith("{\\rtf1"), "Line 1 must be RTF header")
        assertEquals("\\widowctrl\\hyphauto", rtfLines[2], "Line 3 must NOT have double-escaped backslash")

        // 2. NSIS installer script silent taskkill loop test
        val nsiFile = File(repoRoot, "packaging/windows/xdm-installer.nsi")
        assertTrue(nsiFile.exists() && nsiFile.isFile, "xdm-installer.nsi must exist")
        val nsiContent = nsiFile.readText(Charsets.UTF_8)

        assertTrue(
            nsiContent.contains("taskkill /f /im \${APP_EXE}") || nsiContent.contains("process-manager.ps1"),
            "Installer must have running app management with force termination capability"
        )
        assertTrue(nsiContent.contains("Function CheckAppRunning"), "Installer must define CheckAppRunning")
        assertTrue(nsiContent.contains("Function un.CheckAppRunning"), "Uninstaller must define un.CheckAppRunning")
    }

    @Test
    fun testAdjustment5UrlConstantsAndMenuRouting() {
        assertEquals("https://jakepanlilio2000.github.io/XDM-Enhanced", FORK_WEBSITE_URL)
        assertEquals("https://jakepanlilio2000.github.io/XDM-Enhanced/docs/", WEBSITE_HELP_URL)
        assertEquals("https://jakepanlilio2000.github.io/XDM-Enhanced/troubleshooting.html", FORK_TROUBLESHOOTING_URL)
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced/issues", GITHUB_ISSUES_URL)
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced/wiki", GITHUB_WIKI_URL)
        assertEquals("https://xtremedownloadmanager.com", UPSTREAM_WEBSITE_URL)
    }

    private fun findAllComponents(container: java.awt.Container): List<java.awt.Component> {
        val list = mutableListOf<java.awt.Component>()
        for (c in container.components) {
            list.add(c)
            if (c is java.awt.Container) {
                list.addAll(findAllComponents(c))
            }
        }
        return list
    }
}
