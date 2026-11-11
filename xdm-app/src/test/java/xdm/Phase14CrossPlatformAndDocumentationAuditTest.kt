package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.IAppInstance
import xdm.app.OS
import xdm.app.skins.SkinValidatorCli
import xdm.app.ui.screens.settings.AdvancedConfigPanel
import xdm.app.utils.WaylandToolkit
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.swing.SwingUtilities

class Phase14CrossPlatformAndDocumentationAuditTest {

    @TempDir
    lateinit var tempDir: File

    private val repoRoot: File = run {
        var cur = File(".").canonicalFile
        while (cur != null && !File(cur, "packaging").isDirectory) {
            cur = cur.parentFile
        }
        cur ?: File(".").canonicalFile
    }

    @BeforeEach
    fun setUp() {
        xdm.app.I8N.loadTexts("en")
        AppContext.configDir = tempDir.absolutePath
        AppContext.config = AppConfig(tempDir.absolutePath)
        AppContext.app = Proxy.newProxyInstance(
            IAppInstance::class.java.classLoader, arrayOf(IAppInstance::class.java)
        ) { _, _, _ -> null } as IAppInstance
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

    // =========================================================================
    // Check 89: Platform Settings, Wayland and File Chooser Options
    // =========================================================================

    @Test
    fun testCheck89_platformSettingsWaylandAndFileChooser() {
        // 1. Wayland support matrix evaluation across OS/runtime permutations
        assertEquals(
            WaylandToolkit.SupportStatus.NOT_LINUX,
            WaylandToolkit.checkSupport(OS.Windows, isWayland = true, hasToolkit = true)
        )
        assertEquals(
            WaylandToolkit.SupportStatus.NOT_LINUX,
            WaylandToolkit.checkSupport(OS.MacOS, isWayland = true, hasToolkit = true)
        )
        assertEquals(
            WaylandToolkit.SupportStatus.NOT_WAYLAND_SESSION,
            WaylandToolkit.checkSupport(OS.Linux, isWayland = false, hasToolkit = true)
        )
        assertEquals(
            WaylandToolkit.SupportStatus.RUNTIME_LACKS_TOOLKIT,
            WaylandToolkit.checkSupport(OS.Linux, isWayland = true, hasToolkit = false)
        )
        assertEquals(
            WaylandToolkit.SupportStatus.SUPPORTED,
            WaylandToolkit.checkSupport(OS.Linux, isWayland = true, hasToolkit = true)
        )

        // 2. Explanation strings must exist and accurately document runtime requirements
        for (status in WaylandToolkit.SupportStatus.values()) {
            val expl = WaylandToolkit.getExplanation(status)
            assertNotNull(expl)
            assertTrue(expl.isNotBlank(), "Explanation for $status must not be blank")
        }

        // 3. UI control states on the current platform (Windows)
        runOnEdt {
            val panel = AdvancedConfigPanel()
            panel.load()

            // Wayland control should be disabled on Windows with descriptive tooltip
            assertFalse(panel.tglWayland.isEnabled, "Wayland toggle must be disabled on Windows")
            assertEquals(
                "Native Wayland is only available on Linux desktop systems.",
                panel.tglWayland.toolTipText
            )

            // Swing file chooser override should be disabled on Windows
            assertFalse(panel.tglSwingChooser.isEnabled, "Swing file chooser toggle must be disabled on Windows")
            assertTrue(
                panel.tglSwingChooser.toolTipText!!.contains("only applicable on Linux"),
                "Chooser tooltip must document Linux-only applicability"
            )

            // Configuration persistence test: disabled controls must not erase user config values
            AppContext.config.nativeWayland = true
            AppContext.config.swingFileChooser = true
            panel.load()
            panel.save()
            assertTrue(AppContext.config.nativeWayland, "nativeWayland must be preserved when disabled")
            assertTrue(AppContext.config.swingFileChooser, "swingFileChooser must be preserved when disabled")
        }

        // 4. Wayland session detection with explicit X11 override
        val fakeRuntimeDir = File(tempDir, "runtime-test").apply { mkdirs() }
        File(fakeRuntimeDir, "wayland-0").createNewFile()

        assertFalse(
            WaylandToolkit.isWaylandSession(
                sessionType = "x11",
                display = "wayland-0",
                runtimeDir = fakeRuntimeDir.absolutePath
            ),
            "X11 session must reject Wayland even if socket exists"
        )
        assertTrue(
            WaylandToolkit.isWaylandSession(
                sessionType = "wayland",
                display = "wayland-0",
                runtimeDir = fakeRuntimeDir.absolutePath
            ),
            "Wayland session with existing socket must be detected"
        )
    }

    // =========================================================================
    // Check 90: Module Packaging, Icons & Packaging Structures
    // =========================================================================

    @Test
    fun testCheck90_modulePackagingAndIconsVerification() {
        // 1. App module definition (module-info.java)
        val moduleInfo = File(repoRoot, "packaging/module/module-info.java")
        assertTrue(moduleInfo.exists(), "packaging/module/module-info.java must exist")
        val moduleContent = moduleInfo.readText()
        assertTrue(moduleContent.contains("open module xdm.app"), "Must declare open module xdm.app")
        assertTrue(moduleContent.contains("requires java.desktop;"), "Must require java.desktop")
        assertTrue(moduleContent.contains("requires java.logging;"), "Must require java.logging")
        assertTrue(moduleContent.contains("requires jdk.unsupported;"), "Must require jdk.unsupported")
        assertTrue(moduleContent.contains("exports xdm.app;"), "Must export xdm.app")
        assertTrue(moduleContent.contains("uses com.formdev.flatlaf.FlatDefaultsAddon;"), "Must declare FlatDefaultsAddon service")

        // 2. ModularJar tool
        val modularJar = File(repoRoot, "packaging/ModularJar.java")
        assertTrue(modularJar.exists(), "packaging/ModularJar.java must exist")

        // 3. Icons: Windows xdm.ico
        val icoFile = File(repoRoot, "packaging/icons/xdm.ico")
        assertTrue(icoFile.exists(), "xdm.ico must exist")
        assertTrue(icoFile.length() > 32768, "xdm.ico must contain multi-resolution icons (>32KB, actual: ${icoFile.length()})")

        // Verify ICO header: 0x0000 0x0001 (ICO image type)
        FileInputStream(icoFile).use { fis ->
            val b1 = fis.read()
            val b2 = fis.read()
            val b3 = fis.read()
            val b4 = fis.read()
            assertEquals(0, b1)
            assertEquals(0, b2)
            assertEquals(1, b3)
            assertEquals(0, b4)
        }

        // 4. Icon generators and vector master
        val genIcons = File(repoRoot, "packaging/icons/GenerateIcons.java")
        val makeIco = File(repoRoot, "packaging/icons/MakeIco.java")
        val svgLogo = File(repoRoot, "packaging/icons/xdm-logo.svg")
        assertTrue(genIcons.exists(), "GenerateIcons.java must exist")
        assertTrue(makeIco.exists(), "MakeIco.java must exist")
        assertTrue(svgLogo.exists(), "xdm-logo.svg must exist")

        // Verify icon generator source and vector asset
        assertTrue(genIcons.readText().contains("renderMaster"), "GenerateIcons.java must declare renderMaster")
        assertTrue(svgLogo.readText().contains("<svg"), "xdm-logo.svg must be valid SVG")

        // 5. Packaging configurations
        val nsisScript = File(repoRoot, "packaging/windows/xdm-installer.nsi")
        val nfpmConfig = File(repoRoot, "packaging/linux/nfpm.yaml")
        assertTrue(nsisScript.exists(), "NSIS installer script must exist")
        assertTrue(nfpmConfig.exists(), "Linux nfpm.yaml must exist")

        val nsisContent = nsisScript.readText()
        assertTrue(nsisContent.contains("XDM Enhanced"), "NSIS script must name XDM Enhanced")
        assertTrue(nsisContent.contains("gpl-3.0-notice.rtf"), "NSIS script must present GPL-3.0 license")
    }

    // =========================================================================
    // Check 91: README Features, Official Links & Screenshot Integrity
    // =========================================================================

    @Test
    fun testCheck91_readmeFeaturesOfficialRepoAndScreenshots() {
        val readme = File(repoRoot, "README.md")
        assertTrue(readme.exists(), "README.md must exist in repo root")
        val text = readme.readText()

        // 1. Official repo and portal links
        assertTrue(
            text.contains("https://github.com/jakepanlilio2000/XDM-Enhanced"),
            "README must link to official GitHub repository"
        )
        assertTrue(
            text.contains("https://github.com/jakepanlilio2000/XDM-Enhanced/releases"),
            "README must link to official releases"
        )
        assertTrue(
            text.contains("https://jakepanlilio2000.github.io/XDM-Enhanced/"),
            "README must link to official website portal"
        )

        // 2. Verified features check (no fictional features)
        assertTrue(text.contains("Multi-Connection Dynamic Segmentation"), "Feature must be documented")
        assertTrue(text.contains("Native BitTorrent Engine"), "BitTorrent must be documented")
        assertTrue(text.contains("Companion Browser Extensions"), "Extensions must be documented")
        assertTrue(text.contains("Built-in & Custom Skins"), "Skins must be documented")
        assertTrue(text.contains("Decoupled Plugin SPI"), "Plugin SPI must be documented")
        assertTrue(text.contains("Post-Download Action Rules"), "Post-download rules must be documented")

        // 3. Noncommittal future plans disclaimer
        assertTrue(
            text.contains("These are ideas under consideration, not committed features or guaranteed future releases"),
            "Future improvements must include noncommittal disclaimer"
        )

        // 4. Validate all screenshots referenced in README actually exist on disk
        val imageRegex = Regex("!\\[.*?\\]\\((screenshots/[^)]+)\\)")
        val matches = imageRegex.findAll(text).toList()
        assertTrue(matches.isNotEmpty(), "README must reference screenshots")

        for (match in matches) {
            val relativePath = match.groupValues[1]
            val imgFile = File(repoRoot, relativePath)
            assertTrue(imgFile.exists(), "Referenced screenshot must exist: $relativePath")
            assertTrue(imgFile.length() > 1024, "Screenshot must be non-empty: $relativePath (${imgFile.length()} bytes)")
        }
    }

    // =========================================================================
    // Check 92: Developer Documentation & Working Sample Packages
    // =========================================================================

    @Test
    fun testCheck92_developerDocumentsAndWorkingSamplePackages() {
        // 1. Skin Developer Guide & CLI Validator on sample skin package
        val skinGuide = File(repoRoot, "docs/SKIN_DEVELOPMENT.md")
        assertTrue(skinGuide.exists(), "docs/SKIN_DEVELOPMENT.md must exist")
        assertTrue(skinGuide.length() > 5000, "SKIN_DEVELOPMENT.md must be comprehensive")

        val sampleSkinZip = File(repoRoot, "sample-skins/cyber-neon.xdmskin")
        val sampleSkinDir = File(repoRoot, "sample-skins/cyber-neon")
        assertTrue(sampleSkinZip.exists(), "sample-skins/cyber-neon.xdmskin must exist")
        assertTrue(sampleSkinDir.isDirectory, "sample-skins/cyber-neon folder must exist")

        // Validate archive using real SkinValidatorCli
        val zipRes = SkinValidatorCli.validateTarget(sampleSkinZip)
        assertTrue(zipRes.isValid, "cyber-neon.xdmskin must pass validation: ${zipRes.errors}")
        assertEquals("cyber-neon", zipRes.manifest?.id)
        assertEquals(2, zipRes.manifest?.schemaVersion)

        // Validate folder using real SkinValidatorCli
        val dirRes = SkinValidatorCli.validateTarget(sampleSkinDir)
        assertTrue(dirRes.isValid, "cyber-neon directory must pass validation: ${dirRes.errors}")
        assertEquals("cyber-neon", dirRes.manifest?.id)

        // 2. Plugin Developer Guide & Sample Plugins
        val pluginGuide = File(repoRoot, "docs/PLUGIN_DEVELOPMENT.md")
        assertTrue(pluginGuide.exists(), "docs/PLUGIN_DEVELOPMENT.md must exist")
        assertTrue(pluginGuide.length() > 5000, "PLUGIN_DEVELOPMENT.md must be comprehensive")

        val checksumPluginZip = File(repoRoot, "sample-plugins/checksum-verifier.xdmplugin")
        val ytdlpPluginZip = File(repoRoot, "sample-plugins/ytdlp-downloader.xdmplugin")
        assertTrue(checksumPluginZip.exists(), "checksum-verifier.xdmplugin must exist")
        assertTrue(ytdlpPluginZip.exists(), "ytdlp-downloader.xdmplugin must exist")

        // Verify plugin.json inside checksum-verifier.xdmplugin
        ZipFile(checksumPluginZip).use { zf ->
            val entry = zf.getEntry("plugin.json")
            assertNotNull(entry, "checksum-verifier.xdmplugin must contain plugin.json at root")
            val manifestText = zf.getInputStream(entry).bufferedReader().readText()
            assertTrue(manifestText.contains("\"id\": \"checksum-verifier\""))
        }

        // Verify plugin.json inside ytdlp-downloader.xdmplugin
        ZipFile(ytdlpPluginZip).use { zf ->
            val entry = zf.getEntry("plugin.json")
            assertNotNull(entry, "ytdlp-downloader.xdmplugin must contain plugin.json at root")
            val manifestText = zf.getInputStream(entry).bufferedReader().readText()
            assertTrue(manifestText.contains("\"id\": \"ytdlp-downloader\""))
        }

        // 3. Browser Connection Troubleshooting Guide
        val troubleshooterGuide = File(repoRoot, "docs/BROWSER_CONNECTION_TROUBLESHOOTING.md")
        assertTrue(troubleshooterGuide.exists(), "docs/BROWSER_CONNECTION_TROUBLESHOOTING.md must exist")
        val tsContent = troubleshooterGuide.readText()
        assertTrue(tsContent.contains("127.0.0.1:8597"), "Troubleshooting guide must document port 8597")
        assertTrue(tsContent.contains("xdm-app://"), "Troubleshooting guide must document protocol handler")
        assertTrue(tsContent.contains("curl -v http://127.0.0.1:8597/sync"), "Must include working diagnostic commands")
    }

    // =========================================================================
    // Check 93: Keeping Raw Issue Numbers Out of Main README
    // =========================================================================

    @Test
    fun testCheck93_readmeKeepsIssueNumbersOut() {
        val readme = File(repoRoot, "README.md")
        val text = readme.readText()

        // Match patterns like "#123", "Fixes #45", "(#99)"
        val issueRegex = Regex("(?<![a-zA-Z0-9_])#([0-9]{1,5})\\b")
        val matches = issueRegex.findAll(text).toList()

        assertTrue(
            matches.isEmpty(),
            "Main README must not contain raw issue numbers. Found: ${matches.map { it.value }}"
        )

        // Verify detailed resolved issue numbers are maintained in CHANGELOG.md instead
        val changelog = File(repoRoot, "CHANGELOG.md")
        assertTrue(changelog.exists(), "CHANGELOG.md must exist")
        val changelogText = changelog.readText()
        assertTrue(
            changelogText.contains("https://github.com/subhra74/xdm/issues/"),
            "CHANGELOG.md must maintain full issue tracking links"
        )
    }

    // =========================================================================
    // Check 94: Links, Licenses, NOTICE Files & Upstream Attribution
    // =========================================================================

    @Test
    fun testCheck94_licensesNoticeAndThirdPartyCredits() {
        // 1. Root LICENSE
        val licenseFile = File(repoRoot, "LICENSE")
        assertTrue(licenseFile.exists(), "Root LICENSE must exist")
        val licenseText = licenseFile.readText()
        assertTrue(licenseText.contains("GNU GENERAL PUBLIC LICENSE"), "License must be GPL")
        assertTrue(licenseText.contains("Version 3, 29 June 2007"), "License must be GPL-3.0")

        // 2. Upstream documentation preservation
        val upstreamReadme = File(repoRoot, "README.UPSTREAM.md")
        assertTrue(upstreamReadme.exists(), "README.UPSTREAM.md must be preserved")
        assertTrue(upstreamReadme.readText().contains("subhra74"), "Upstream author credit must be preserved in README.UPSTREAM.md")

        // 3. Submodule NOTICE files
        val coreNotice = File(repoRoot, "xdm-core/NOTICE")
        assertTrue(coreNotice.exists(), "xdm-core/NOTICE must exist")
        val coreNoticeText = coreNotice.readText()
        assertTrue(coreNoticeText.contains("Copyright 2026 Subhra Das Gupta"), "xdm-core copyright must be credited")
        assertTrue(coreNoticeText.contains("Apache License 2.0"), "Historical Apache 2.0 license notice must be preserved")

        val torrentNotice = File(repoRoot, "xdm-torrent/NOTICE")
        assertTrue(torrentNotice.exists(), "xdm-torrent/NOTICE must exist")
        val torrentNoticeText = torrentNotice.readText()
        assertTrue(torrentNoticeText.contains("libtorrent4j"), "libtorrent4j credit must exist")
        assertTrue(torrentNoticeText.contains("Alden Torres"), "Alden Torres credit must exist")
        assertTrue(torrentNoticeText.contains("libtorrent (rasterbar)"), "Rasterbar libtorrent credit must exist")
        assertTrue(torrentNoticeText.contains("Arvid Norberg"), "Arvid Norberg credit must exist")

        val androidNotice = File(repoRoot, "xdm-android/NOTICE")
        assertTrue(androidNotice.exists(), "xdm-android/NOTICE must exist")

        // 4. Packaging license notice
        val rtfNotice = File(repoRoot, "packaging/windows/gpl-3.0-notice.rtf")
        assertTrue(rtfNotice.exists(), "gpl-3.0-notice.rtf must exist")
        val rtfText = rtfNotice.readText()
        assertTrue(rtfText.contains("GNU GENERAL PUBLIC LICENSE"), "RTF notice must contain GPL-3.0")
        assertTrue(rtfText.contains("Subhra Das Gupta"), "RTF notice must credit upstream author")

        // 5. README Third-Party Software Notices
        val readmeText = File(repoRoot, "README.md").readText()
        assertTrue(readmeText.contains("Subhra Das Gupta"), "README must credit Subhra Das Gupta")
        assertTrue(readmeText.contains("libtorrent4j"), "README must credit libtorrent4j")
        assertTrue(readmeText.contains("Alden Torres"), "README must credit Alden Torres")
        assertTrue(readmeText.contains("Arvid Norberg"), "README must credit Arvid Norberg")
        assertTrue(readmeText.contains("FormDev Software GmbH"), "README must credit FlatLaf authors")
    }
}

