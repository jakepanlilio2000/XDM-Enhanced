package xdm.app.skins

import kotlinx.serialization.json.Json
import xdm.core.util.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Handles safe installation extraction and export packaging for .xdmskin packages.
 */
object SkinPackager {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    /**
     * Safely extracts and installs a .xdmskin package into [skinsDir].
     * Executes strict validation before and after extraction.
     */
    fun installSkinPackage(archiveFile: File, skinsDir: File): SkinManifest {
        // 1. Initial pre-extraction archive security validation
        val archiveCheck = SkinPackageValidator.validateArchive(archiveFile)
        if (!archiveCheck.isValid) {
            val msg = archiveCheck.errors.joinToString("; ")
            Logger.error("Failed archive validation: $msg")
            throw IllegalArgumentException("Invalid skin package: $msg")
        }

        val tempStaging = Files.createTempDirectory("xdm-skin-staging-").toFile()
        try {
            // 2. Safe extraction with Zip Slip protection
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val targetFile = File(tempStaging, entry.name).canonicalFile
                    if (!targetFile.toPath().startsWith(tempStaging.canonicalFile.toPath())) {
                        throw SecurityException("Hostile zip entry detected (Zip Slip): ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            // 3. Post-extraction asset & manifest validation
            val manifestFile = File(tempStaging, "skin.json")
            if (!manifestFile.exists()) {
                throw IllegalArgumentException("Extracted package is missing required root 'skin.json'.")
            }

            val manifest = json.decodeFromString<SkinManifest>(manifestFile.readText())
            val manifestCheck = SkinPackageValidator.validateManifest(manifest, skinRoot = tempStaging)
            if (!manifestCheck.isValid) {
                val msg = manifestCheck.errors.joinToString("; ")
                throw IllegalArgumentException("Skin manifest failed validation: $msg")
            }

            // 4. Move to target destination directory: skins/<skin.id>
            skinsDir.mkdirs()
            val targetSkinDir = File(skinsDir, manifest.id)
            if (targetSkinDir.exists()) {
                targetSkinDir.deleteRecursively()
            }
            targetSkinDir.mkdirs()

            tempStaging.copyRecursively(targetSkinDir, overwrite = true)
            Logger.info("Successfully installed custom skin '${manifest.name}' to ${targetSkinDir.absolutePath}")
            return manifest
        } finally {
            tempStaging.deleteRecursively()
        }
    }

    /**
     * Packages an installed skin folder into a distributable .xdmskin zip archive.
     */
    fun exportSkinPackage(skinDir: File, destinationArchive: File): File {
        if (!skinDir.exists() || !skinDir.isDirectory) {
            throw IllegalArgumentException("Skin directory does not exist: ${skinDir.absolutePath}")
        }
        val manifestFile = File(skinDir, "skin.json")
        if (!manifestFile.exists()) {
            throw IllegalArgumentException("Skin directory missing required 'skin.json': ${skinDir.absolutePath}")
        }

        destinationArchive.parentFile?.mkdirs()
        val tempArchive = File(destinationArchive.parentFile, "${destinationArchive.name}.tmp")
        if (tempArchive.exists()) tempArchive.delete()

        ZipOutputStream(FileOutputStream(tempArchive)).use { zos ->
            fun addDir(dir: File, basePrefix: String) {
                val files = dir.listFiles() ?: return
                for (file in files) {
                    val entryName = if (basePrefix.isEmpty()) file.name else "$basePrefix/${file.name}"
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$entryName/"))
                        zos.closeEntry()
                        addDir(file, entryName)
                    } else {
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { fis -> fis.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            addDir(skinDir, "")
        }

        Files.move(tempArchive.toPath(), destinationArchive.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Logger.info("Successfully exported skin package to ${destinationArchive.absolutePath}")
        return destinationArchive
    }
}

