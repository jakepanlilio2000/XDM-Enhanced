package xdm.app.update

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.OS
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class UpdateCheckerMockTest {

    @TempDir
    lateinit var tempDir: File

    private val sampleJson = """
        [
          {
            "tag_name": "v1.1.0-beta.1",
            "name": "XDM Enhanced v1.1.0-beta.1",
            "body": "## Beta 1 Release Notes\n- Feature A\n- Feature B",
            "html_url": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.1.0-beta.1",
            "prerelease": true,
            "draft": false,
            "published_at": "2026-11-10T00:00:00Z",
            "assets": [
              {
                "name": "XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe",
                "size": 52428800,
                "browser_download_url": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0-beta.1/XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe"
              },
              {
                "name": "XDM-Enhanced-v1.1.0-beta.1-windows-x64.zip",
                "size": 48000000,
                "browser_download_url": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0-beta.1/XDM-Enhanced-v1.1.0-beta.1-windows-x64.zip"
              },
              {
                "name": "xdm-1.1.0-beta.1-x86_64.AppImage",
                "size": 55000000,
                "browser_download_url": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0-beta.1/xdm-1.1.0-beta.1-x86_64.AppImage"
              },
              {
                "name": "XDM-Enhanced-1.1.0-beta.1.dmg",
                "size": 53000000,
                "browser_download_url": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0-beta.1/XDM-Enhanced-1.1.0-beta.1.dmg"
              },
              {
                "name": "SHA256SUMS.txt",
                "size": 512,
                "browser_download_url": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0-beta.1/SHA256SUMS.txt"
              }
            ]
          },
          {
            "tag_name": "v1.0.0",
            "name": "XDM Enhanced v1.0.0 Final",
            "body": "## Stable v1.0.0\nInitial official stable release.",
            "html_url": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.0",
            "prerelease": false,
            "draft": false,
            "published_at": "2026-11-01T00:00:00Z",
            "assets": [
              {
                "name": "XDM-Enhanced-Setup-v1.0.0-win-x64.exe",
                "size": 51000000,
                "browser_download_url": "https://example.com/setup-1.0.0.exe"
              }
            ]
          },
          {
            "tag_name": "v1.1.0-draft",
            "name": "Draft unreleased",
            "prerelease": true,
            "draft": true
          }
        ]
    """.trimIndent()

    @Test
    fun testParseReleases() {
        val releases = UpdateChecker.parseReleases(sampleJson)
        assertEquals(3, releases.size)
        assertEquals("v1.1.0-beta.1", releases[0].tag_name)
        assertTrue(releases[0].prerelease)
        assertFalse(releases[0].draft)
        assertEquals(5, releases[0].assets.size)

        assertEquals("v1.0.0", releases[1].tag_name)
        assertFalse(releases[1].prerelease)

        assertTrue(releases[2].draft)
    }

    @Test
    fun testChannelFilteringBetaChannel() {
        val releases = UpdateChecker.parseReleases(sampleJson)
        val newest = UpdateChecker.findNewestRelease(releases, UpdateChannel.BETA)
        assertNotNull(newest)
        assertEquals("v1.1.0-beta.1", newest?.tag_name, "Beta channel should include beta.1 since 1.1.0-beta.1 is higher than 1.0.0")
    }

    @Test
    fun testChannelFilteringStableChannel() {
        val releases = UpdateChecker.parseReleases(sampleJson)
        val newest = UpdateChecker.findNewestRelease(releases, UpdateChannel.STABLE)
        assertNotNull(newest)
        assertEquals("v1.0.0", newest?.tag_name, "Stable channel must ignore beta.1 and draft and pick v1.0.0")
        assertFalse(newest!!.prerelease)
    }

    @Test
    fun testCompareSemVerVersions() {
        assertTrue(UpdateChecker.compareVersions("1.0.0-beta.4", "1.0.0-beta.3") > 0)
        assertTrue(UpdateChecker.compareVersions("v1.0.0-beta.4", "1.0.0-beta.3") > 0)
        assertEquals(0, UpdateChecker.compareVersions("1.0.0-beta.3", "v1.0.0-beta.3"))
        assertTrue(UpdateChecker.compareVersions("1.0.0-beta.2", "1.0.0-beta.3") < 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.0-beta.4") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0.0") > 0)
    }

    @Test
    fun testAssetResolutionWindows() {
        val releases = UpdateChecker.parseReleases(sampleJson)
        val assets = releases[0].assets

        val winInstaller = UpdateAssetResolver.resolveInstallerAsset(assets, targetOs = OS.Windows, arch = "x64")
        assertNotNull(winInstaller)
        assertEquals("XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe", winInstaller?.name)

        val checksumAsset = UpdateAssetResolver.resolveChecksumAsset(assets)
        assertNotNull(checksumAsset)
        assertEquals("SHA256SUMS.txt", checksumAsset?.name)
    }

    @Test
    fun testAssetResolutionLinux() {
        val releases = UpdateChecker.parseReleases(sampleJson)
        val assets = releases[0].assets

        val linuxInstaller = UpdateAssetResolver.resolveInstallerAsset(assets, targetOs = OS.Linux, arch = "x64")
        assertNotNull(linuxInstaller)
        assertEquals("xdm-1.1.0-beta.1-x86_64.AppImage", linuxInstaller?.name)
    }

    @Test
    fun testAssetResolutionMac() {
        val releases = UpdateChecker.parseReleases(sampleJson)
        val assets = releases[0].assets

        val macInstaller = UpdateAssetResolver.resolveInstallerAsset(assets, targetOs = OS.MacOS, arch = "x64")
        assertNotNull(macInstaller)
        assertEquals("XDM-Enhanced-1.1.0-beta.1.dmg", macInstaller?.name)
    }

    @Test
    fun testChecksumVerification() {
        val testFile = File(tempDir, "test-installer.exe")
        testFile.writeText("This is dummy installer binary content for SHA-256 verification testing.")
        val computedHash = UpdateChecksumVerifier.computeSha256(testFile)

        val sha256SumsContent = """
            $computedHash  test-installer.exe
            9f83c605d4c82b3e9252329e4c04f91a82c074e55e5ff35d75cb11029da7e2f5  other-file.zip
        """.trimIndent()

        // 1. Valid matching checksum
        val validResult = UpdateChecksumVerifier.verify(
            file = testFile,
            expectedSize = testFile.length(),
            sha256SumsContent = sha256SumsContent,
            targetFileName = "test-installer.exe"
        )
        assertTrue(validResult is ChecksumResult.Valid)
        assertEquals(computedHash, (validResult as ChecksumResult.Valid).sha256)

        // 2. Mismatched checksum
        val corruptSums = """
            0000000000000000000000000000000000000000000000000000000000000000  test-installer.exe
        """.trimIndent()
        val mismatchResult = UpdateChecksumVerifier.verify(
            file = testFile,
            expectedSize = testFile.length(),
            sha256SumsContent = corruptSums,
            targetFileName = "test-installer.exe"
        )
        assertTrue(mismatchResult is ChecksumResult.Mismatch)

        // 3. Size mismatch
        val sizeMismatchResult = UpdateChecksumVerifier.verify(
            file = testFile,
            expectedSize = 999999L,
            sha256SumsContent = sha256SumsContent,
            targetFileName = "test-installer.exe"
        )
        assertTrue(sizeMismatchResult is ChecksumResult.SizeMismatch)

        // 4. Hash not in sums file
        val unlistedResult = UpdateChecksumVerifier.verify(
            file = testFile,
            expectedSize = testFile.length(),
            sha256SumsContent = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890  some-other-file.exe",
            targetFileName = "test-installer.exe"
        )
        assertTrue(unlistedResult is ChecksumResult.HashNotFoundInSums)
    }

    @Test
    fun testDownloaderCancellation() {
        val downloader = UpdateDownloader()
        assertFalse(downloader.isCancelled)
        downloader.cancel()
        assertTrue(downloader.isCancelled)
    }

    @Test
    fun testSettingsPersistence() {
        val config = AppConfig(tempDir.absolutePath)

        // Check defaults
        assertTrue(config.autoUpdateCheck)
        assertEquals("beta", config.updateChannel)
        assertTrue(config.updateNotificationsEnabled)

        // Change values and save
        config.autoUpdateCheck = false
        config.updateChannel = "stable"
        config.updateNotificationsEnabled = false
        config.save()

        // Reload from disk
        val reloaded = AppConfig(tempDir.absolutePath)
        reloaded.load()

        assertFalse(reloaded.autoUpdateCheck, "autoUpdateCheck should persist false")
        assertEquals("stable", reloaded.updateChannel, "updateChannel should persist 'stable'")
        assertFalse(reloaded.updateNotificationsEnabled, "updateNotificationsEnabled should persist false")
    }

    @Test
    fun testLiveForkReleasesIntegration() {
        val result = UpdateChecker.checkSync(UpdateChannel.BETA, currentVersion = "1.0.0-beta.3")
        when (result) {
            is UpdateCheckResult.UpToDate -> {
                assertEquals("1.0.0-beta.3", result.latestVersion)
                assertEquals("1.0.0-beta.3", result.currentVersion)
                assertEquals(UpdateChannel.BETA, result.channel)
            }
            is UpdateCheckResult.UpdateAvailable -> {
                assertTrue(UpdateChecker.compareVersions(result.releaseInfo.version, "1.0.0-beta.3") > 0)
            }
            is UpdateCheckResult.Error -> {
                assertTrue(result.isRateLimit || result.isNetworkError)
            }
        }
    }

    @Test
    fun testLiveForkReleasesDetectsNewerWhenOutdated() {
        val result = UpdateChecker.checkSync(UpdateChannel.BETA, currentVersion = "1.0.0-beta.1")
        when (result) {
            is UpdateCheckResult.UpdateAvailable -> {
                assertEquals("1.0.0-beta.3", result.releaseInfo.version)
                assertTrue(result.releaseInfo.isPreRelease)
                assertNotNull(result.releaseInfo.installerAsset)
                assertTrue(result.releaseInfo.installerAsset!!.name.endsWith(".exe") || result.releaseInfo.installerAsset!!.name.endsWith(".zip"))
                // Checksum asset is optional if not uploaded as a separate file asset
                result.releaseInfo.checksumAsset?.let {
                    assertEquals("SHA256SUMS.txt", it.name)
                }
            }
            is UpdateCheckResult.Error -> {
                assertTrue(result.isRateLimit || result.isNetworkError)
            }
            is UpdateCheckResult.UpToDate -> {
                fail("Version 1.0.0-beta.1 should not be up to date when 1.0.0-beta.3 is published")
            }
        }
    }
}

