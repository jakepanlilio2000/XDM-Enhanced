package xdm.app.skins

import kotlinx.serialization.json.Json
import xdm.app.update.UpdateChecker
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Validates .xdmskin archive packages and skin.json manifests against security constraints,
 * Zip Slip attacks, malicious payloads, corrupted assets, schema invariants, and region contracts.
 */
object SkinPackageValidator {

    const val MAX_ENTRIES = 500
    const val MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024 // 50 MB
    const val MAX_ENTRY_BYTES = 15L * 1024 * 1024 // 15 MB

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val ID_REGEX = Regex("^[a-zA-Z0-9_-]{3,64}$")
    private val HEX_COLOR_REGEX = Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")
    private val DISALLOWED_EXTENSIONS = setOf(
        "exe", "dll", "so", "dylib", "class", "jar", "bat", "cmd", "sh", "ps1",
        "vbs", "js", "py", "bin", "com", "scr", "msi", "appimage", "c", "cpp", "h"
    )
    private val ALLOWED_EXTENSIONS = setOf(
        "json", "png", "jpg", "jpeg", "gif", "webp", "ttf", "otf", "txt", "md"
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val manifest: SkinManifest? = null
    )

    /**
     * Validates a .xdmskin ZIP archive prior to extraction.
     */
    fun validateArchive(archiveFile: File): ValidationResult {
        val errors = mutableListOf<String>()

        if (!archiveFile.exists()) {
            return ValidationResult(false, listOf("Skin archive file does not exist: ${archiveFile.absolutePath}"))
        }
        if (!archiveFile.isFile) {
            return ValidationResult(false, listOf("Skin archive is not a regular file: ${archiveFile.absolutePath}"))
        }

        var entryCount = 0
        var totalBytes = 0L
        var hasManifest = false
        var rawManifestBytes: ByteArray? = null

        try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++

                    if (entryCount > MAX_ENTRIES) {
                        errors.add("Archive exceeds maximum entry count of $MAX_ENTRIES files.")
                        break
                    }

                    val name = entry.name
                    // Zip Slip protection: reject any relative directory traversal or root prefix
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":")) {
                        errors.add("Hostile archive entry path detected (Zip Slip): '$name'")
                        return ValidationResult(false, errors)
                    }

                    if (entry.size > MAX_ENTRY_BYTES) {
                        errors.add("Archive entry '$name' exceeds maximum size of $MAX_ENTRY_BYTES bytes.")
                    }

                    totalBytes += entry.size.coerceAtLeast(0)
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        errors.add("Total uncompressed size exceeds maximum limit of $MAX_UNCOMPRESSED_BYTES bytes.")
                        return ValidationResult(false, errors)
                    }

                    if (!entry.isDirectory) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (DISALLOWED_EXTENSIONS.contains(ext)) {
                            errors.add("Security violation: executable or script file detected in archive: '$name'")
                        }
                        if (name == "skin.json") {
                            hasManifest = true
                            rawManifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            return ValidationResult(false, listOf("Invalid or corrupt ZIP archive: ${e.message}"))
        }

        if (!hasManifest || rawManifestBytes == null) {
            errors.add("Skin package missing required root 'skin.json' manifest.")
            return ValidationResult(false, errors)
        }

        if (errors.isNotEmpty()) {
            return ValidationResult(false, errors)
        }

        val manifest = try {
            json.decodeFromString<SkinManifest>(rawManifestBytes.decodeToString())
        } catch (e: Throwable) {
            return ValidationResult(false, listOf("Failed to parse 'skin.json': ${e.message}"))
        }

        val manifestValidation = validateManifest(manifest, skinRoot = null)
        val combinedErrors = errors + manifestValidation.errors
        return ValidationResult(combinedErrors.isEmpty(), combinedErrors, manifestValidation.warnings, manifest)
    }

    /**
     * Validates a parsed [SkinManifest] and optionally verifies declared local file assets against [skinRoot].
     */
    fun validateManifest(manifest: SkinManifest, skinRoot: File? = null): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Schema Version validation
        if (manifest.schemaVersion !in 1..2) {
            errors.add("Unsupported schemaVersion '${manifest.schemaVersion}'. Supported versions are 1 and 2.")
        }

        // 2. Identifier validation
        if (manifest.id.isBlank()) {
            errors.add("Manifest 'id' is required and cannot be blank.")
        } else if (!ID_REGEX.matches(manifest.id)) {
            errors.add("Manifest 'id' '${manifest.id}' is invalid. Must be 3-64 characters (letters, numbers, hyphens, underscores).")
        } else if (manifest.id.equals(SkinManager.SKIN_ENHANCED, ignoreCase = true) ||
            manifest.id.equals(SkinManager.SKIN_ORIGINAL_XDM, ignoreCase = true)
        ) {
            errors.add("Manifest 'id' '${manifest.id}' conflicts with reserved built-in skin identifier.")
        }

        // 3. Name, Version, Author
        if (manifest.name.isBlank()) errors.add("Manifest 'name' is required.")
        if (manifest.version.isBlank()) errors.add("Manifest 'version' is required.")
        if (manifest.author.isBlank()) errors.add("Manifest 'author' is required.")

        // 4. Minimum App Version compatibility
        val currentAppVersion = UpdateChecker.loadConfig()?.currentVersion ?: UpdateChecker.DEFAULT_VERSION
        if (manifest.minAppVersion.isNotBlank()) {
            try {
                if (UpdateChecker.compareVersions(currentAppVersion, manifest.minAppVersion) < 0) {
                    errors.add("Skin requires application version '${manifest.minAppVersion}', but current version is '$currentAppVersion'.")
                }
            } catch (_: Throwable) {
                warnings.add("Could not parse semver for minAppVersion: '${manifest.minAppVersion}'.")
            }
        }

        // 5. Geometry bounds
        val g = manifest.geometry
        if (g.componentArc !in 0..48) errors.add("componentArc must be between 0 and 48 pixels (was ${g.componentArc}).")
        if (g.buttonArc !in 0..48) errors.add("buttonArc must be between 0 and 48 pixels (was ${g.buttonArc}).")
        if (g.rowHeight !in 20..120) errors.add("rowHeight must be between 20 and 120 pixels (was ${g.rowHeight}).")
        if (g.sidebarWidth !in 100..400) errors.add("sidebarWidth must be between 100 and 400 pixels (was ${g.sidebarWidth}).")

        // 6. Layout options
        val l = manifest.layout
        if (l.toolbarPosition !in listOf("top", "bottom", "hidden")) {
            errors.add("toolbarPosition must be 'top', 'bottom', or 'hidden' (was '${l.toolbarPosition}').")
        }
        if (l.sidebarPosition !in listOf("left", "right", "hidden")) {
            errors.add("sidebarPosition must be 'left', 'right', or 'hidden' (was '${l.sidebarPosition}').")
        }

        // 7. Schema v2 Regions validation
        manifest.regions.forEach { (slotId, r) ->
            if (SkinRegion.fromSlotId(slotId) == null) {
                warnings.add("Unknown or non-standard region slot '$slotId'.")
            }
            r.borderWidth?.let {
                if (it !in 0..16) errors.add("Region '$slotId' borderWidth must be between 0 and 16 (was $it).")
            }
            r.cornerArc?.let {
                if (it !in 0..48) errors.add("Region '$slotId' cornerArc must be between 0 and 48 (was $it).")
            }
            r.buttonArc?.let {
                if (it !in 0..48) errors.add("Region '$slotId' buttonArc must be between 0 and 48 (was $it).")
            }
            r.cardArc?.let {
                if (it !in 0..48) errors.add("Region '$slotId' cardArc must be between 0 and 48 (was $it).")
            }
            r.progressBarArc?.let {
                if (it !in 0..48) errors.add("Region '$slotId' progressBarArc must be between 0 and 48 (was $it).")
            }
            r.height?.let {
                if (it !in 20..800) errors.add("Region '$slotId' height must be between 20 and 800 (was $it).")
            }
            r.width?.let {
                if (it !in 20..800) errors.add("Region '$slotId' width must be between 20 and 800 (was $it).")
            }
            r.padding?.let { p ->
                if (p.top !in 0..64 || p.left !in 0..64 || p.bottom !in 0..64 || p.right !in 0..64) {
                    errors.add("Region '$slotId' padding values must each be between 0 and 64.")
                }
            }
            listOfNotNull(
                r.background?.let { "background" to it },
                r.foreground?.let { "foreground" to it },
                r.mutedForeground?.let { "mutedForeground" to it },
                r.border?.let { "border" to it },
                r.selectionBackground?.let { "selectionBackground" to it },
                r.selectionForeground?.let { "selectionForeground" to it },
                r.hoverBackground?.let { "hoverBackground" to it }
            ).forEach { (prop, color) ->
                if (!HEX_COLOR_REGEX.matches(color)) {
                    errors.add("Region '$slotId' property '$prop' must be a valid #RRGGBB or #AARRGGBB hex color (was '$color').")
                }
            }
        }

        // 8. Declared file resources validation (if skinRoot provided)
        if (skinRoot != null && skinRoot.isDirectory) {
            fun checkAssetFile(relPath: String?, description: String) {
                if (relPath.isNullOrBlank()) return
                val file = File(skinRoot, relPath)
                if (!file.exists() || !file.isFile) {
                    errors.add("Declared $description file not found: '$relPath'")
                } else {
                    val ext = file.extension.lowercase()
                    if (DISALLOWED_EXTENSIONS.contains(ext)) {
                        errors.add("Security violation: declared $description '$relPath' has disallowed extension.")
                    }
                }
            }

            checkAssetFile(manifest.wallpaper?.dark, "dark wallpaper")
            checkAssetFile(manifest.wallpaper?.light, "light wallpaper")
            checkAssetFile(manifest.font?.resolvedFile, "custom font")

            manifest.icons.toolbar.forEach { (action, path) ->
                checkAssetFile(path, "toolbar icon for '$action'")
            }
            manifest.icons.categories.forEach { (cat, path) ->
                checkAssetFile(path, "category icon for '$cat'")
            }

            manifest.regions.forEach { (slotId, r) ->
                checkAssetFile(r.wallpaper?.dark, "region '$slotId' dark wallpaper")
                checkAssetFile(r.wallpaper?.light, "region '$slotId' light wallpaper")
                checkAssetFile(r.customIcon, "region '$slotId' custom icon")
            }
        }

        return ValidationResult(errors.isEmpty(), errors, warnings, manifest)
    }
}
