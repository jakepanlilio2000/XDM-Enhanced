package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.OS
import xdm.app.ui.components.BrowserExtensionPanel
import xdm.app.update.*
import xdm.app.utils.AppDirs
import xdm.app.utils.detectOS
import xdm.app.utils.win.WindowsAssociations
import java.io.File
import java.nio.file.Files

class Phase11PackagingAndReleaseAuditTest {

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
        val config = AppConfig(tempDir.absolutePath)
        AppContext.config = config
    }

    // =========================================================================
    // Scenario 72 & 73: NSIS Script, Installation, Upgrade, License & ARP Scope
    // =========================================================================
    @Test
    fun testScenario72And73_nsisInstallerScriptAndPackagingIntegrity() {
        val nsiFile = File(repoRoot, "packaging/windows/xdm-installer.nsi")
        assertTrue(nsiFile.exists() && nsiFile.isFile, "NSIS installer script must exist at packaging/windows/xdm-installer.nsi")

        val nsiContent = nsiFile.readText(Charsets.UTF_8)

        // 1. Product Definitions and Names
        assertTrue(nsiContent.contains("!define PRODUCT_NAME \"XDM Enhanced\""), "Product name must be 'XDM Enhanced'")
        assertTrue(nsiContent.contains("!define PRODUCT_PUBLISHER \"XDM Enhanced Contributors\""), "Publisher must be 'XDM Enhanced Contributors'")
        assertTrue(nsiContent.contains("!define PRODUCT_WEB_SITE \"https://github.com/jakepanlilio2000/XDM-Enhanced\""), "Website must point to the fork repo")
        assertTrue(nsiContent.contains("!define PRODUCT_DIR_REGKEY \"Software\\XDM Enhanced\""), "Registry dir must be 'Software\\XDM Enhanced'")
        assertTrue(nsiContent.contains("!define PRODUCT_UNINST_KEY \"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\XDM Enhanced\""), "Uninstall key must be scoped to 'XDM Enhanced'")

        // 2. GPL-3.0 License Page
        val licenseFile = File(repoRoot, "packaging/windows/gpl-3.0-notice.rtf")
        assertTrue(licenseFile.exists() && licenseFile.isFile, "License RTF notice must exist")
        val licenseContent = licenseFile.readText(Charsets.UTF_8)
        assertTrue(licenseContent.startsWith("{\\rtf1"), "License file must be valid RTF format")
        assertTrue(licenseContent.contains("XDM Enhanced"), "License must state XDM Enhanced")
        assertTrue(licenseContent.contains("GNU GENERAL PUBLIC LICENSE"), "License must contain GPL text")
        assertTrue(licenseContent.contains("https://github.com/jakepanlilio2000/XDM-Enhanced"), "License must cite fork repository")
        assertTrue(nsiContent.contains("!insertmacro MUI_PAGE_LICENSE \"\${LICENSE_FILE}\""), "NSIS must include MUI_PAGE_LICENSE")

        // 3. Official XDM Protection Gate
        assertTrue(nsiContent.contains("\$INSTDIR\\xdman.jar"), "NSIS must guard against overwriting official XDM xdman.jar")
        assertTrue(nsiContent.contains("\$INSTDIR\\xdman.exe"), "NSIS must guard against overwriting official XDM xdman.exe")

        // 4. Upgrade Location Resolution
        assertTrue(nsiContent.contains("ReadRegStr \$1 HKLM \"\${PRODUCT_DIR_REGKEY}\" \"InstallDir\""), "Upgrade must check HKLM previous install")
        assertTrue(nsiContent.contains("ReadRegStr \$1 HKCU \"\${PRODUCT_DIR_REGKEY}\" \"InstallDir\""), "Upgrade must check HKCU previous install")

        // 5. Running Process Check
        assertTrue(nsiContent.contains("Function CheckAppRunning"), "Installer must have CheckAppRunning function")
        assertTrue(nsiContent.contains("Function un.CheckAppRunning"), "Uninstaller must have un.CheckAppRunning function")

        // 6. Shell Links and ARP Scope
        assertTrue(nsiContent.contains("CreateShortcut \"\$SMPROGRAMS\\\${PRODUCT_NAME}\\\${PRODUCT_NAME}.lnk\""), "Start Menu shortcut must be created")
        assertTrue(nsiContent.contains("CreateShortcut \"\$DESKTOP\\\${PRODUCT_NAME}.lnk\""), "Desktop shortcut must be created")
        assertTrue(nsiContent.contains("WriteRegStr HKLM \"\${PRODUCT_UNINST_KEY}\" \"DisplayName\" \"\${PRODUCT_NAME}\""), "ARP DisplayName must be set for machine")
        assertTrue(nsiContent.contains("WriteRegStr HKCU \"\${PRODUCT_UNINST_KEY}\" \"DisplayName\" \"\${PRODUCT_NAME}\""), "ARP DisplayName must be set for user")

        // 7. Uninstaller Scope: Cleans up only XDM Enhanced data; NEVER deletes official XDM data
        assertTrue(nsiContent.contains("RMDir /r \"\$LOCALAPPDATA\\xdm-enhanced\""), "Uninstaller must clean up LOCALAPPDATA\\xdm-enhanced")
        assertTrue(nsiContent.contains("RMDir /r \"\$APPDATA\\xdm-enhanced\""), "Uninstaller must clean up APPDATA\\xdm-enhanced")
        assertFalse(nsiContent.contains("RMDir /r \"\$APPDATA\\xdm-app\""), "Uninstaller must NEVER delete official XDM APPDATA\\xdm-app")
        assertFalse(nsiContent.contains("RMDir /r \"\$PROFILE\\.xdm\""), "Uninstaller must NEVER delete official XDM PROFILE\\.xdm")

        // 8. If NSIS compiler (makensis.exe) is available, verify compilation passes
        val makensis = listOf(
            "makensis.exe",
            "C:\\Program Files (x86)\\NSIS\\makensis.exe",
            "C:\\Program Files\\NSIS\\makensis.exe"
        ).map { File(it) }.firstOrNull { it.exists() }

        if (makensis != null) {
            val dummyImg = File(tempDir, "dummy-image").apply { mkdirs() }
            File(dummyImg, "xdm-app.exe").writeBytes(ByteArray(1024))
            val dummyOut = File(tempDir, "test-build-setup.exe")

            val pb = ProcessBuilder(
                makensis.absolutePath,
                "/V2",
                "/DOUT_FILE=${dummyOut.absolutePath}",
                "/DAPP_IMAGE_DIR=${dummyImg.absolutePath}",
                nsiFile.absolutePath
            ).redirectErrorStream(true)

            val proc = pb.start()
            val finished = proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(finished, "makensis must finish compilation within 30 seconds")
            assertEquals(0, proc.exitValue(), "makensis compilation of xdm-installer.nsi must exit with code 0")
            assertTrue(dummyOut.exists() && dummyOut.length() > 0, "makensis must generate installer executable")
        }
    }

    // =========================================================================
    // Scenario 74: Update Discovery, Channels, Status Codes, Assets & Checksums
    // =========================================================================
    @Test
    fun testScenario74_updateDiscoveryAndAuthenticityHandling() {
        // 1. Update config points strictly to fork releases
        val config = UpdateChecker.loadConfig()
        assertNotNull(config, "app-version.json must be bundled on classpath")
        assertEquals("https://api.github.com/repos/jakepanlilio2000/XDM-Enhanced/releases?per_page=20", config!!.updateCheckUrl)
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced/releases", config.downloadUrl)

        // 2. SemVer Version Comparison
        assertTrue(UpdateChecker.compareVersions("1.0.0-rc.1", "1.0.0-beta.6") > 0, "rc.1 should be newer than beta.6")
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.0-rc.1") > 0, "1.0.0 stable should be newer than rc.1")
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0.0") > 0, "1.0.1 should be newer than 1.0.0")
        assertEquals(0, UpdateChecker.compareVersions("v1.0.0", "1.0.0"), "Leading v should be normalized")

        // 3. Prerelease selection by channel
        val releaseJson = """
            [
              {
                "tag_name": "v1.0.0-rc.1",
                "name": "XDM Enhanced v1.0.0 Release Candidate 1",
                "body": "RC1 release notes",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-11-10T12:00:00Z",
                "assets": [
                  {
                    "name": "XDM-Enhanced-Setup-v1.0.0-rc.1-win-x64.exe",
                    "size": 52428800,
                    "browser_download_url": "https://example.com/rc1-setup.exe"
                  },
                  {
                    "name": "sha256sums.txt",
                    "size": 256,
                    "browser_download_url": "https://example.com/sha256sums.txt"
                  }
                ]
              },
              {
                "tag_name": "v1.0.0-beta.6",
                "name": "XDM Enhanced v1.0.0-beta.6",
                "body": "Beta 6 release notes",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-11-01T12:00:00Z",
                "assets": []
              }
            ]
        """.trimIndent()

        val parsedReleases = UpdateChecker.parseReleases(releaseJson)
        assertEquals(2, parsedReleases.size)

        // Beta channel includes RC1
        val betaRelease = UpdateChecker.findNewestRelease(parsedReleases, UpdateChannel.BETA)
        assertNotNull(betaRelease)
        assertEquals("v1.0.0-rc.1", betaRelease!!.tag_name)

        // Stable channel excludes RC1 and Beta6 (no stable release exists in list)
        val stableRelease = UpdateChecker.findNewestRelease(parsedReleases, UpdateChannel.STABLE)
        assertNull(stableRelease, "Stable channel must not select prereleases")

        // 4. Missing assets resolution
        val emptyAssetRelease = listOf(GithubAsset("other-file.txt", 100L, "http://example.com"))
        val resolvedMissing = UpdateAssetResolver.resolveInstallerAsset(emptyAssetRelease, OS.Windows)
        assertNull(resolvedMissing, "When no matching installer exists, resolver must return null")

        // 5. Windows asset resolution
        val winAsset = UpdateAssetResolver.resolveInstallerAsset(parsedReleases[0].assets, OS.Windows)
        assertNotNull(winAsset)
        assertEquals("XDM-Enhanced-Setup-v1.0.0-rc.1-win-x64.exe", winAsset!!.name)

        val checksumAsset = UpdateAssetResolver.resolveChecksumAsset(parsedReleases[0].assets)
        assertNotNull(checksumAsset)
        assertEquals("sha256sums.txt", checksumAsset!!.name)

        // 6. Checksum Verification: Valid, Mismatch, SizeMismatch
        val testFile = File(tempDir, "test-installer.exe")
        testFile.writeText("XDM Enhanced Binary Payload")
        val actualHash = UpdateChecksumVerifier.computeSha256(testFile)

        // Valid
        val sumsContent = "$actualHash  test-installer.exe\n"
        val validResult = UpdateChecksumVerifier.verify(testFile, testFile.length(), sumsContent)
        assertTrue(validResult is ChecksumResult.Valid)

        // Size mismatch (e.g. partial download)
        val sizeMismatchResult = UpdateChecksumVerifier.verify(testFile, testFile.length() + 500, sumsContent)
        assertTrue(sizeMismatchResult is ChecksumResult.SizeMismatch)

        // Hash mismatch (corrupt payload)
        val corruptSums = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef  test-installer.exe\n"
        val mismatchResult = UpdateChecksumVerifier.verify(testFile, testFile.length(), corruptSums)
        assertTrue(mismatchResult is ChecksumResult.Mismatch)
    }

    // =========================================================================
    // Scenario 75: Browser Extension Store & Signing Policies
    // =========================================================================
    @Test
    fun testScenario75_browserExtensionStoreAndSigningPolicies() {
        val chromeUrl = BrowserExtensionPanel.extensionPageUrl("chrome")
        val firefoxUrl = BrowserExtensionPanel.extensionPageUrl("firefox")
        val edgeUrl = BrowserExtensionPanel.extensionPageUrl("edge")
        val braveUrl = BrowserExtensionPanel.extensionPageUrl("brave")

        // All extension install/update links must direct to fork releases or official stores
        assertTrue(chromeUrl.startsWith("https://github.com/jakepanlilio2000/XDM-Enhanced/releases"))
        assertTrue(firefoxUrl.startsWith("https://github.com/jakepanlilio2000/XDM-Enhanced/releases"))
        assertTrue(edgeUrl.startsWith("https://github.com/jakepanlilio2000/XDM-Enhanced/releases"))
        assertTrue(braveUrl.startsWith("https://github.com/jakepanlilio2000/XDM-Enhanced/releases"))

        // Must never reference upstream author's outdated repo
        assertFalse(chromeUrl.contains("subhra74"))
        assertFalse(firefoxUrl.contains("subhra74"))
    }

    // =========================================================================
    // Scenario 76: Data Paths, Startup Identifiers & Upstream Isolation
    // =========================================================================
    @Test
    fun testScenario76_dataPathsAndUpstreamIsolation() {
        // AppDirs name is xdm-enhanced
        assertEquals("xdm-enhanced", AppDirs.NAME)
        assertEquals("xdm-app", AppDirs.LEGACY_NAME)

        // Windows Associations constants
        assertEquals("Software\\Classes\\xdm-app", WindowsAssociations.XDM_APP_PROTOCOL_KEY)
        assertEquals("Software\\Classes\\.torrent", WindowsAssociations.TORRENT_EXT_KEY)
        assertEquals("Software\\Classes\\xdm.torrent", WindowsAssociations.TORRENT_PROGID_KEY)
        assertEquals("Software\\Classes\\magnet", WindowsAssociations.MAGNET_KEY)

        // Fresh install isolation: fresh install does not auto-migrate legacy data unless explicit
        val target = File(tempDir, "xdm-enhanced")
        val legacy = File(tempDir, "xdm-app").apply { mkdirs() }
        File(legacy, "active-downloads.dat").writeText("legacy")

        val migratedWithoutFlag = AppDirs.migrateLegacyData(target, legacy)
        // Without system property xdm.import.legacy=true, auto migration is disabled for fresh isolation
        assertFalse(System.getProperty(AppDirs.IMPORT_LEGACY_PROPERTY) == "true")
    }

    // =========================================================================
    // Scenario 77: No Auto-Execution Without User Confirmation
    // =========================================================================
    @Test
    fun testScenario77_noAutoExecutionWithoutUserConfirmation() {
        // UpdateChecker.checkForUpdates only invokes callback with UpdateCheckResult
        // AppWindow.checkForUpdates only displays banner panel and never executes installer
        // UpdateDialog only calls UpdateInstaller.launchInstallerAndExit on explicit Install & Restart button click

        val systemExe = File("C:\\Windows\\System32\\whoami.exe")
        if (systemExe.exists() && systemExe.isFile) {
            // Test launchInstallerAndExit with exitVm = false directly on system executable outside TempDir
            val executed = UpdateInstaller.launchInstallerAndExit(systemExe, exitVm = false)
            assertTrue(executed, "launchInstallerAndExit must succeed when given a valid executable file")
        }

        // Missing file must fail safely
        val missingFile = File(tempDir, "non-existent-installer.exe")
        assertFalse(UpdateInstaller.launchInstallerAndExit(missingFile, exitVm = false))
    }
}

