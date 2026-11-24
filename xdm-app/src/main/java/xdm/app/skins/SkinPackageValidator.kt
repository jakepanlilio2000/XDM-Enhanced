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
        val entryNames = mutableSetOf<String>()

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
                        val norm = name.trim().trimStart('/', '\\').replace('\\', '/')
                        entryNames.add(norm)
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

        val manifestValidation = validateManifest(manifest, skinRoot = null, archiveEntries = entryNames)
        val combinedErrors = errors + manifestValidation.errors
        return ValidationResult(combinedErrors.isEmpty(), combinedErrors, manifestValidation.warnings, manifest)
    }

    /**
     * Validates a parsed [SkinManifest] and optionally verifies declared local file assets against [skinRoot] or [archiveEntries].
     */
    fun validateManifest(manifest: SkinManifest, skinRoot: File? = null, archiveEntries: Set<String>? = null): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Schema Version validation
        if (manifest.schemaVersion !in 1..3) {
            errors.add("Unsupported schemaVersion '${manifest.schemaVersion}'. Supported versions are 1, 2, and 3.")
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
        if (l.chromeMode !in listOf("enhanced", "upstream", "custom")) {
            errors.add("chromeMode must be 'enhanced', 'upstream', or 'custom' (was '${l.chromeMode}').")
        }

        // 6b. Schema v3 Window & Widgets validation
        manifest.window?.let { w ->
            if (w.titleBarHeight !in 20..72) {
                errors.add("window.titleBarHeight must be between 20 and 72 (was ${w.titleBarHeight}).")
            }
            if (w.windowShadeHeight !in 24..160) {
                errors.add("window.windowShadeHeight must be between 24 and 160 (was ${w.windowShadeHeight}).")
            }
            if (w.frameBorderWidth !in 0..12) {
                errors.add("window.frameBorderWidth must be between 0 and 12 (was ${w.frameBorderWidth}).")
            }
            if (w.frameCornerArc !in 0..36) {
                errors.add("window.frameCornerArc must be between 0 and 36 (was ${w.frameCornerArc}).")
            }
            if (w.titleAlignment !in listOf("left", "center")) {
                errors.add("window.titleAlignment must be 'left' or 'center' (was '${w.titleAlignment}').")
            }
            if (w.controlButtonsStyle !in listOf("mac", "beveled", "anime", "steampunk", "minimal")) {
                errors.add("window.controlButtonsStyle is invalid (was '${w.controlButtonsStyle}').")
            }
            listOfNotNull(
                w.titleBarBackgroundDark?.let { "titleBarBackgroundDark" to it },
                w.titleBarBackgroundLight?.let { "titleBarBackgroundLight" to it },
                w.titleBarGradientStartDark?.let { "titleBarGradientStartDark" to it },
                w.titleBarGradientEndDark?.let { "titleBarGradientEndDark" to it },
                w.titleBarGradientStartLight?.let { "titleBarGradientStartLight" to it },
                w.titleBarGradientEndLight?.let { "titleBarGradientEndLight" to it },
                w.titleBarForegroundDark?.let { "titleBarForegroundDark" to it },
                w.titleBarForegroundLight?.let { "titleBarForegroundLight" to it },
                w.frameBorderDark?.let { "frameBorderDark" to it },
                w.frameBorderLight?.let { "frameBorderLight" to it },
                w.frameHighlightDark?.let { "frameHighlightDark" to it },
                w.frameHighlightLight?.let { "frameHighlightLight" to it },
                w.frameShadowDark?.let { "frameShadowDark" to it },
                w.frameShadowLight?.let { "frameShadowLight" to it }
            ).forEach { (prop, color) ->
                if (!HEX_COLOR_REGEX.matches(color)) {
                    errors.add("window.$prop must be a valid #RRGGBB or #AARRGGBB hex color (was '$color').")
                }
            }
        }
        manifest.widgets?.let { wg ->
            if (wg.progressBarStyle !in listOf("flat", "segmented_led", "gradient_glow", "brass_gauge", "striped_candy")) {
                errors.add("widgets.progressBarStyle is invalid (was '${wg.progressBarStyle}').")
            }
            if (wg.progressSegments !in 4..48) {
                errors.add("widgets.progressSegments must be between 4 and 48 (was ${wg.progressSegments}).")
            }
            if (wg.buttonBevelStyle !in listOf("flat", "beveled_3d", "pill_glow", "riveted_brass", "neon_outline")) {
                errors.add("widgets.buttonBevelStyle is invalid (was '${wg.buttonBevelStyle}').")
            }
            listOfNotNull(
                wg.progressTrackDark?.let { "progressTrackDark" to it },
                wg.progressTrackLight?.let { "progressTrackLight" to it },
                wg.progressFillStartDark?.let { "progressFillStartDark" to it },
                wg.progressFillEndDark?.let { "progressFillEndDark" to it },
                wg.progressFillStartLight?.let { "progressFillStartLight" to it },
                wg.progressFillEndLight?.let { "progressFillEndLight" to it },
                wg.buttonSurfaceDark?.let { "buttonSurfaceDark" to it },
                wg.buttonSurfaceLight?.let { "buttonSurfaceLight" to it },
                wg.buttonHighlightDark?.let { "buttonHighlightDark" to it },
                wg.buttonShadowDark?.let { "buttonShadowDark" to it },
                wg.buttonHighlightLight?.let { "buttonHighlightLight" to it },
                wg.buttonShadowLight?.let { "buttonShadowLight" to it }
            ).forEach { (prop, color) ->
                if (!HEX_COLOR_REGEX.matches(color)) {
                    errors.add("widgets.$prop must be a valid #RRGGBB or #AARRGGBB hex color (was '$color').")
                }
            }
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

        // 8. Declared file resources validation (if skinRoot or archiveEntries provided)
        if (archiveEntries != null || (skinRoot != null && skinRoot.isDirectory)) {
            fun checkAssetFile(relPath: String?, description: String) {
                if (relPath.isNullOrBlank()) return
                val norm = relPath.trim().trimStart('/', '\\').replace('\\', '/')
                val ext = norm.substringAfterLast('.', "").lowercase()
                if (DISALLOWED_EXTENSIONS.contains(ext)) {
                    errors.add("Security violation: declared $description '$relPath' has disallowed extension.")
                    return
                }
                if (archiveEntries != null) {
                    if (!archiveEntries.contains(norm)) {
                        errors.add("Declared $description file not found: '$relPath'")
                    }
                } else if (skinRoot != null && skinRoot.isDirectory) {
                    val file = File(skinRoot, norm)
                    if (!file.exists() || !file.isFile) {
                        errors.add("Declared $description file not found: '$relPath'")
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

            manifest.window?.let { w ->
                checkAssetFile(w.titleBarTexture?.dark, "window dark titleBarTexture")
                checkAssetFile(w.titleBarTexture?.light, "window light titleBarTexture")
                checkAssetFile(w.controlIcons.minimize, "window minimize control icon")
                checkAssetFile(w.controlIcons.maximize, "window maximize control icon")
                checkAssetFile(w.controlIcons.restore, "window restore control icon")
                checkAssetFile(w.controlIcons.shade, "window shade control icon")
                checkAssetFile(w.controlIcons.close, "window close control icon")
            }
        }

        return ValidationResult(errors.isEmpty(), errors, warnings, manifest)
    }
}
