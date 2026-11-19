package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.rtf.RTFEditorKit

class PhaseM3NsisLicenseAuditTest {

    private fun findRepoRoot(): File {
        var current = File(".").canonicalFile
        while (current != null) {
            if (File(current, "packaging/windows/gpl-3.0-notice.rtf").exists()) {
                return current
            }
            current = current.parentFile
        }
        throw IllegalStateException("Repository root with packaging/windows/gpl-3.0-notice.rtf not found")
    }

    @Test
    fun testLicenseRtfSyntaxAndNoExposedControlWords() {
        val root = findRepoRoot()
        val rtfFile = File(root, "packaging/windows/gpl-3.0-notice.rtf")
        assertTrue(rtfFile.exists(), "gpl-3.0-notice.rtf must exist")

        val rawBytes = rtfFile.readBytes()
        val rawText = String(rawBytes, StandardCharsets.ISO_8859_1)

        // Must start with valid RTF header
        assertTrue(rawText.startsWith("{\\rtf1"), "RTF file must begin with {\\rtf1")
        assertTrue(rawText.endsWith("}\n") || rawText.endsWith("}"), "RTF file must close with }")

        // Must NOT contain double-backslash escaped \\widowctrl
        assertFalse(rawText.contains("\\\\widowctrl"), "Must NOT contain escaped double backslash \\\\widowctrl")
        assertFalse(rawText.contains("\\\\hyphauto"), "Must NOT contain escaped double backslash \\\\hyphauto")

        // Must preserve valid single-backslash control word
        assertTrue(rawText.contains("\\widowctrl"), "Must contain standard RTF \\widowctrl directive")
        assertTrue(rawText.contains("\\hyphauto"), "Must contain standard RTF \\hyphauto directive")

        // GPL-3.0 terms and attribution preservation
        assertTrue(rawText.contains("XDM Enhanced"), "Must contain XDM Enhanced identity")
        assertTrue(rawText.contains("GNU GENERAL PUBLIC LICENSE"), "Must contain GNU GENERAL PUBLIC LICENSE title")
        assertTrue(rawText.contains("Version 3, 29 June 2007"), "Must contain Version 3 date")
        assertTrue(rawText.contains("TERMS AND CONDITIONS"), "Must contain TERMS AND CONDITIONS header")
        assertTrue(rawText.contains("17. Interpretation of Sections 15 and 16"), "Must contain concluding GPL section 17")
        assertTrue(rawText.contains("https://github.com/jakepanlilio2000/XDM-Enhanced"), "Must contain repository URL")
    }

    @Test
    fun testRtfParserDoesNotExposeControlWordsInPlainText() {
        val root = findRepoRoot()
        val rtfFile = File(root, "packaging/windows/gpl-3.0-notice.rtf")
        assertTrue(rtfFile.exists())

        val rtfKit = RTFEditorKit()
        val doc = DefaultStyledDocument()
        val bytes = rtfFile.readBytes()

        rtfKit.read(ByteArrayInputStream(bytes), doc, 0)
        val plainText = doc.getText(0, doc.length)

        // Raw control words must NOT appear in parsed text
        assertFalse(plainText.contains("widowctrl"), "Parsed text must NOT contain 'widowctrl'")
        assertFalse(plainText.contains("hyphauto"), "Parsed text must NOT contain 'hyphauto'")
        assertFalse(plainText.contains("rtf1"), "Parsed text must NOT contain 'rtf1'")
        assertFalse(plainText.contains("fonttbl"), "Parsed text must NOT contain 'fonttbl'")
        assertFalse(plainText.contains("colortbl"), "Parsed text must NOT contain 'colortbl'")
        assertFalse(plainText.contains("pard"), "Parsed text must NOT contain 'pard'")

        // Required text content must be readable
        assertTrue(plainText.contains("XDM Enhanced"), "Plain text must contain 'XDM Enhanced'")
        assertTrue(plainText.contains("Free and Open-Source Software"), "Plain text must describe open source software")
        assertTrue(plainText.contains("GNU GENERAL PUBLIC LICENSE"), "Plain text must contain 'GNU GENERAL PUBLIC LICENSE'")
        assertTrue(plainText.contains("Preamble"), "Plain text must contain Preamble")
        assertTrue(plainText.contains("END OF TERMS AND CONDITIONS"), "Plain text must contain END OF TERMS AND CONDITIONS")
    }

    @Test
    fun testNsisScriptReferencesNoticeFileAndBuilds(@TempDir tempDir: File) {
        val root = findRepoRoot()
        val nsiFile = File(root, "packaging/windows/xdm-installer.nsi")
        assertTrue(nsiFile.exists(), "xdm-installer.nsi must exist")

        val nsiContent = nsiFile.readText(StandardCharsets.UTF_8)
        assertTrue(nsiContent.contains("!define LICENSE_FILE \"gpl-3.0-notice.rtf\""), "LICENSE_FILE must point to gpl-3.0-notice.rtf")
        assertTrue(nsiContent.contains("!insertmacro MUI_PAGE_LICENSE \"\${LICENSE_FILE}\""), "Must insert MUI_PAGE_LICENSE macro")

        // If makensis.exe is available, compile installer and ensure success
        val makensisCandidates = listOf(
            File("C:\\Program Files (x86)\\NSIS\\makensis.exe"),
            File("C:\\Program Files\\NSIS\\makensis.exe")
        )
        val makensis = makensisCandidates.firstOrNull { it.exists() }

        if (makensis != null) {
            val dummyImg = File(tempDir, "dummy-image").apply { mkdirs() }
            File(dummyImg, "xdm-app.exe").writeBytes(ByteArray(512))
            val outExe = File(tempDir, "test-installer.exe")

            val pb = ProcessBuilder(
                makensis.absolutePath,
                "/V2",
                "/DOUT_FILE=${outExe.absolutePath}",
                "/DAPP_IMAGE_DIR=${dummyImg.absolutePath}",
                "/DPRODUCT_VERSION=1.0.0",
                nsiFile.absolutePath
            ).redirectErrorStream(true)

            val proc = pb.start()
            val finished = proc.waitFor(30, TimeUnit.SECONDS)
            assertTrue(finished, "makensis must finish compilation within 30 seconds")
            assertEquals(0, proc.exitValue(), "makensis compilation of xdm-installer.nsi must exit with code 0")
            assertTrue(outExe.exists() && outExe.length() > 0, "makensis must produce non-empty test installer binary")
        }
    }

    @Test
    fun testLicenseRenderedInNsisRichEditDialog() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return

        val makensis = File("C:\\Program Files (x86)\\NSIS\\makensis.exe")
        if (!makensis.exists()) return

        val inspectScript = File("C:\\Users\\Administrator\\.gemini\\antigravity\\brain\\1021f44a-ff70-4fe4-af84-ab35c2654b54\\scratch\\inspect_nsis_license.ps1")
        if (!inspectScript.exists()) return

        val pb = ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-File", inspectScript.absolutePath)
            .redirectErrorStream(true)

        val proc = pb.start()
        val finished = proc.waitFor(45, TimeUnit.SECONDS)
        assertTrue(finished, "inspect_nsis_license.ps1 must complete within 45 seconds")
        val output = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.exitValue(), "PowerShell script should exit with 0. Output:\n$output")

        assertTrue(output.contains("Contains 'widowctrl': False"), "Rendered license must NOT contain widowctrl. Output:\n$output")
        assertTrue(output.contains("Contains 'hyphauto': False"), "Rendered license must NOT contain hyphauto. Output:\n$output")
        assertTrue(output.contains("Contains '\\': False"), "Rendered license must NOT contain raw backslashes. Output:\n$output")
        assertTrue(output.contains("Contains 'XDM Enhanced': True"), "Rendered license must contain XDM Enhanced. Output:\n$output")
        assertTrue(output.contains("Contains 'GNU GENERAL PUBLIC LICENSE': True"), "Rendered license must contain GNU GPL. Output:\n$output")
        assertTrue(output.contains("Screenshot successfully saved"), "Screenshot of license page must be captured")
    }
}
