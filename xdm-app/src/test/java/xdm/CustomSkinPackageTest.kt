package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.skins.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CustomSkinPackageTest : DownloadManagerTestBase() {

    private lateinit var testSkinsDir: File

    @BeforeEach
    fun initTest() {
        I8N.loadTexts("en")
        testSkinsDir = File(dir, "skins").apply { mkdirs() }
        SkinManager.customSkinsDir = testSkinsDir
    }

    @AfterEach
    fun cleanupTest() {
        SkinManager.applySkin(SkinManager.SKIN_ENHANCED, saveConfig = false)
    }

    @Test
    fun testInstallValidSkinPackage() {
        val zipFile = File(dir, "valid.xdmskin")
        createZip(zipFile, mapOf(
            "skin.json" to validManifestJson("test-theme", "Test Theme"),
            "backgrounds/bg.png" to createDummyPng(),
            "icons/categories/documents.png" to createDummyPng()
        ))

        val validation = SkinPackageValidator.validateArchive(zipFile)
        assertTrue(validation.isValid, "Valid package should pass validation: ${validation.errors}")

        val skin = SkinManager.installSkin(zipFile)
        assertNotNull(skin)
        assertEquals("test-theme", skin.id)
        assertEquals("Test Theme", skin.name)
        assertTrue(skin is CustomSkin)

        val found = SkinManager.getSkin("test-theme")
        assertNotNull(found)
        assertEquals("test-theme", found?.id)
    }

    @Test
    fun testRejectZipSlip() {
        val zipFile = File(dir, "zipslip.xdmskin")
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("malicious content".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("skin.json"))
            zos.write(validManifestJson("evil-skin", "Evil").toByteArray())
            zos.closeEntry()
        }
        zipFile.writeBytes(baos.toByteArray())

        val validation = SkinPackageValidator.validateArchive(zipFile)
        assertFalse(validation.isValid, "Zip slip archive must fail validation")
        assertTrue(validation.errors.any { it.contains("Zip Slip", ignoreCase = true) })
    }

    @Test
    fun testRejectExecutableFile() {
        val zipFile = File(dir, "malware.xdmskin")
        createZip(zipFile, mapOf(
            "skin.json" to validManifestJson("malware-skin", "Malware"),
            "payload.exe" to "MZ\u0000\u0000binary".toByteArray()
        ))

        val validation = SkinPackageValidator.validateArchive(zipFile)
        assertFalse(validation.isValid, "Executable file must fail validation")
        assertTrue(validation.errors.any { it.contains("executable or script", ignoreCase = true) })
    }

    @Test
    fun testRejectMissingSkinJson() {
        val zipFile = File(dir, "no-manifest.xdmskin")
        createZip(zipFile, mapOf(
            "backgrounds/bg.png" to createDummyPng()
        ))

        val validation = SkinPackageValidator.validateArchive(zipFile)
        assertFalse(validation.isValid, "Archive without skin.json must fail validation")
        assertTrue(validation.errors.any { it.contains("missing required root 'skin.json'", ignoreCase = true) })
    }

    @Test
    fun testRejectBuiltInIdCollision() {
        val zipFile = File(dir, "enhanced-impostor.xdmskin")
        createZip(zipFile, mapOf(
            "skin.json" to validManifestJson("enhanced", "Enhanced Impostor")
        ))

        val validation = SkinPackageValidator.validateArchive(zipFile)
        assertFalse(validation.isValid, "Manifest with built-in id 'enhanced' must fail validation")
        assertTrue(validation.errors.any { it.contains("conflicts with reserved built-in", ignoreCase = true) })
    }

    @Test
    fun testRejectInvalidIdFormat() {
        val zipFile = File(dir, "bad-id.xdmskin")
        createZip(zipFile, mapOf(
            "skin.json" to validManifestJson("Invalid Name With Spaces!", "Bad ID")
        ))

        val validation = SkinPackageValidator.validateArchive(zipFile)
        assertFalse(validation.isValid, "Manifest with invalid id regex must fail validation")
        assertTrue(validation.errors.any { it.contains("is invalid. Must be 3-64 characters", ignoreCase = true) })
    }

    @Test
    fun testSkinValidatorCliTarget() {
        val zipFile = File(dir, "cli-test.xdmskin")
        createZip(zipFile, mapOf(
            "skin.json" to validManifestJson("cli-skin", "CLI Skin")
        ))

        val resultValid = SkinValidatorCli.validateTarget(zipFile)
        assertTrue(resultValid.isValid, "CLI validation of valid archive should pass")

        val nonExistent = File(dir, "does-not-exist.xdmskin")
        val resultMissing = SkinValidatorCli.validateTarget(nonExistent)
        assertFalse(resultMissing.isValid, "CLI validation of nonexistent file must fail")
    }

    @Test
    fun testSkinLifecycleApplyWallpaperAndUninstall() {
        val zipFile = File(dir, "lifecycle.xdmskin")
        val manifest = """
        {
          "id": "lifecycle-test",
          "name": "Lifecycle Test",
          "version": "1.0.0",
          "author": "Lifecycle Author",
          "minAppVersion": "1.0.0-beta.4",
          "wallpaper": {
            "dark": "backgrounds/bg.png",
            "mode": "fill",
            "opacity": 0.2
          },
          "layout": {
            "toolbarPosition": "bottom",
            "sidebarPosition": "right",
            "showStatusBar": false
          }
        }
        """.trimIndent()

        createZip(zipFile, mapOf(
            "skin.json" to manifest,
            "backgrounds/bg.png" to createDummyPng()
        ))

        val skin = SkinManager.installSkin(zipFile) as CustomSkin
        assertEquals("lifecycle-test", skin.id)
        assertEquals("bottom", skin.layoutConfig.toolbarPosition)
        assertEquals("right", skin.layoutConfig.sidebarPosition)
        assertFalse(skin.layoutConfig.showStatusBar)

        // Switch to this custom skin
        val applied = SkinManager.applySkin("lifecycle-test", saveConfig = true)
        assertTrue(applied)
        assertEquals("lifecycle-test", SkinManager.currentSkin.id)

        // Wallpaper painting test
        val img = BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val painted = skin.paintWallpaper(g, 400, 300, isDark = true)
        g.dispose()
        assertTrue(painted, "Wallpaper should paint successfully")

        // Export test
        val exportDest = File(dir, "exported.xdmskin")
        SkinManager.exportSkin("lifecycle-test", exportDest)
        assertTrue(exportDest.exists())
        assertTrue(exportDest.length() > 0)

        val exportedValidation = SkinPackageValidator.validateArchive(exportDest)
        assertTrue(exportedValidation.isValid, "Exported skin package must be valid: ${exportedValidation.errors}")

        // Uninstall test: built-ins cannot be uninstalled
        assertFalse(SkinManager.uninstallSkin(SkinManager.SKIN_ENHANCED))
        assertFalse(SkinManager.uninstallSkin(SkinManager.SKIN_ORIGINAL_XDM))

        // Uninstall active custom skin: switches back to enhanced safely
        val uninstalled = SkinManager.uninstallSkin("lifecycle-test")
        assertTrue(uninstalled)
        assertEquals(SkinManager.SKIN_ENHANCED, SkinManager.currentSkin.id)
        assertNull(SkinManager.getSkin("lifecycle-test"))
    }

    private fun validManifestJson(id: String, name: String): String = """
    {
      "id": "$id",
      "name": "$name",
      "version": "1.0.0",
      "author": "Tester",
      "description": "Test manifest",
      "minAppVersion": "1.0.0-beta.4",
      "license": "GPL-3.0-or-later"
    }
    """.trimIndent()

    private fun createDummyPng(): ByteArray {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val baos = ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }

    private fun createZip(dest: File, entries: Map<String, Any>) {
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                when (content) {
                    is String -> zos.write(content.toByteArray(StandardCharsets.UTF_8))
                    is ByteArray -> zos.write(content)
                    else -> error("Unsupported content type")
                }
                zos.closeEntry()
            }
        }
    }
}

