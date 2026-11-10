package xdm.app.plugins

import kotlinx.serialization.json.Json
import xdm.app.update.UpdateChecker
import xdm.plugin.api.models.PluginCapability
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Validates .xdmplugin or plugin archive packages and plugin.json manifests against
 * security constraints, Zip Slip attacks, malicious native binaries, corrupted manifests, and schema invariants.
 */
object PluginManifestValidator {

    const val MAX_ENTRIES = 1000
    const val MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024 // 50 MB
    const val MAX_ENTRY_BYTES = 25L * 1024 * 1024 // 25 MB

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ID_REGEX = Regex("^[a-zA-Z0-9_-]{3,64}$")
    private val CLASS_REGEX = Regex("^[a-zA-Z_\$][a-zA-Z0-9_\$]*(\\.[a-zA-Z_\$][a-zA-Z0-9_\$]*)+$")

    private val DISALLOWED_EXTENSIONS = setOf(
        "exe", "dll", "so", "dylib", "bat", "cmd", "sh", "ps1",
        "vbs", "js", "py", "bin", "com", "scr", "msi", "appimage"
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val manifest: PluginManifest? = null,
        val sha256: String = ""
    )

    /**
     * Computes the SHA-256 hex digest of a file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read = input.read(buf)
            while (read > 0) {
                digest.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates a plugin archive prior to extraction or loading.
     */
    fun validateArchive(archiveFile: File): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!archiveFile.exists()) {
            return ValidationResult(false, listOf("Plugin file does not exist: ${archiveFile.absolutePath}"))
        }
        if (!archiveFile.isFile) {
            return ValidationResult(false, listOf("Plugin is not a regular file: ${archiveFile.absolutePath}"))
        }

        val sha256 = try {
            computeSha256(archiveFile)
        } catch (e: Exception) {
            return ValidationResult(false, listOf("Failed to compute plugin checksum: ${e.message}"))
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
                        return ValidationResult(false, errors, warnings, null, sha256)
                    }

                    if (entry.size > MAX_ENTRY_BYTES) {
                        errors.add("Archive entry '$name' exceeds maximum size of $MAX_ENTRY_BYTES bytes.")
                    }

                    totalBytes += entry.size.coerceAtLeast(0)
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        errors.add("Total uncompressed size exceeds maximum limit of $MAX_UNCOMPRESSED_BYTES bytes.")
                        return ValidationResult(false, errors, warnings, null, sha256)
                    }

                    if (!entry.isDirectory) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (DISALLOWED_EXTENSIONS.contains(ext)) {
                            errors.add("Security violation: native executable or script detected in plugin archive: '$name'")
                        }
                        if (name == "plugin.json" || name.endsWith("/plugin.json")) {
                            hasManifest = true
                            rawManifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            return ValidationResult(false, listOf("Invalid or corrupt ZIP/JAR archive: ${e.message}"), warnings, null, sha256)
        }

        if (!hasManifest || rawManifestBytes == null) {
            errors.add("Plugin package missing required 'plugin.json' manifest.")
            return ValidationResult(false, errors, warnings, null, sha256)
        }

        if (errors.isNotEmpty()) {
            return ValidationResult(false, errors, warnings, null, sha256)
        }

        val manifest = try {
            json.decodeFromString<PluginManifest>(rawManifestBytes.decodeToString())
        } catch (e: Throwable) {
            return ValidationResult(false, listOf("Failed to parse 'plugin.json': ${e.message}"), warnings, null, sha256)
        }

        val manifestValidation = validateManifest(manifest)
        val combinedErrors = (errors + manifestValidation.errors).toMutableList()
        val combinedWarnings = (warnings + manifestValidation.warnings).toMutableList()

        // If manifest specifies checksum, verify it matches
        if (!manifest.checksumSha256.isNullOrBlank() && !manifest.checksumSha256.equals(sha256, ignoreCase = true)) {
            combinedErrors.add("Manifest checksum '${manifest.checksumSha256}' does not match archive SHA-256 '$sha256'.")
        }

        return ValidationResult(combinedErrors.isEmpty(), combinedErrors, combinedWarnings, manifest, sha256)
    }

    /**
     * Validates a parsed [PluginManifest].
     */
    fun validateManifest(manifest: PluginManifest): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Identifier validation
        if (manifest.id.isBlank()) {
            errors.add("Manifest 'id' is required and cannot be blank.")
        } else if (!ID_REGEX.matches(manifest.id)) {
            errors.add("Manifest 'id' '${manifest.id}' is invalid. Must be 3-64 characters (letters, numbers, hyphens, underscores).")
        }

        // 2. Name, Version, Author
        if (manifest.name.isBlank()) errors.add("Manifest 'name' is required.")
        if (manifest.version.isBlank()) errors.add("Manifest 'version' is required.")
        if (manifest.author.isBlank()) errors.add("Manifest 'author' is required.")

        // 3. Entry point
        if (manifest.entryPoint.isBlank()) {
            errors.add("Manifest 'entryPoint' class name is required.")
        } else if (!CLASS_REGEX.matches(manifest.entryPoint)) {
            errors.add("Manifest 'entryPoint' '${manifest.entryPoint}' is not a valid fully-qualified Java class name.")
        }

        // 4. Minimum App Version compatibility
        val currentAppVersion = UpdateChecker.loadConfig()?.currentVersion ?: UpdateChecker.DEFAULT_VERSION
        if (manifest.minAppVersion.isNotBlank()) {
            try {
                val (currCore, _) = UpdateChecker.splitVersion(currentAppVersion)
                val (reqCore, reqPre) = UpdateChecker.splitVersion(manifest.minAppVersion)
                val cmp = UpdateChecker.compareVersions(currentAppVersion, manifest.minAppVersion)
                if (cmp < 0) {
                    val sameCore = currCore == reqCore
                    // If same core version (e.g. 1.0.0-beta.4 vs 1.0.0), accept unless manifest explicitly requires a higher prerelease
                    if (!sameCore || reqPre.isNotEmpty()) {
                        errors.add("Plugin requires application version '${manifest.minAppVersion}', but current version is '$currentAppVersion'.")
                    }
                }
            } catch (_: Throwable) {
                warnings.add("Could not parse minimum app version '${manifest.minAppVersion}'.")
            }
        }

        // 5. API Version compatibility and range handling
        val currentApiVersion = PluginManager.API_VERSION
        if (manifest.minApiVersion.isNotBlank()) {
            try {
                if (UpdateChecker.compareVersions(currentApiVersion, manifest.minApiVersion) < 0) {
                    errors.add("Plugin requires API version '>=${manifest.minApiVersion}', but host API version is '$currentApiVersion'.")
                }
            } catch (_: Throwable) {
                warnings.add("Could not parse minimum API version '${manifest.minApiVersion}'.")
            }
        }
        if (!manifest.maxApiVersion.isNullOrBlank()) {
            try {
                if (UpdateChecker.compareVersions(currentApiVersion, manifest.maxApiVersion) > 0) {
                    errors.add("Plugin is incompatible with API version '$currentApiVersion' (maximum supported API is '${manifest.maxApiVersion}').")
                }
            } catch (_: Throwable) {
                warnings.add("Could not parse maximum API version '${manifest.maxApiVersion}'.")
            }
        }
        if (!manifest.apiCompatibility.isNullOrBlank()) {
            val rangeCheck = evaluateVersionRange(currentApiVersion, manifest.apiCompatibility)
            if (!rangeCheck.first) {
                errors.add(rangeCheck.second ?: "Plugin API compatibility constraint '${manifest.apiCompatibility}' is not satisfied by host API version '$currentApiVersion'.")
            }
        }

        // 6. Capabilities check
        val knownCapabilities = PluginCapability.entries.map { it.name }
        manifest.capabilities.forEach { cap ->
            if (!knownCapabilities.contains(cap.uppercase())) {
                warnings.add("Unknown capability declared: '$cap'.")
            }
        }

        return ValidationResult(errors.isEmpty(), errors, warnings, manifest)
    }

    /**
     * Evaluates a version string against semver/range expressions such as "[1.0.0, 2.0.0)", ">=1.0.0 <2.0.0", "^1.0.0", or "~1.0.0".
     */
    fun evaluateVersionRange(currentVersion: String, rangeExpr: String): Pair<Boolean, String?> {
        val trimmed = rangeExpr.trim()
        try {
            // Interval notation: e.g. [1.0.0, 2.0.0) or (1.0.0, 2.0.0]
            if ((trimmed.startsWith("[") || trimmed.startsWith("(")) && (trimmed.endsWith("]") || trimmed.endsWith(")"))) {
                val includeStart = trimmed.startsWith("[")
                val includeEnd = trimmed.endsWith("]")
                val parts = trimmed.substring(1, trimmed.length - 1).split(",").map { it.trim() }
                if (parts.size == 2) {
                    val start = parts[0]
                    val end = parts[1]
                    if (start.isNotBlank()) {
                        val cmp = UpdateChecker.compareVersions(currentVersion, start)
                        if (if (includeStart) cmp < 0 else cmp <= 0) {
                            return Pair(false, "API version '$currentVersion' is below required range '$trimmed'.")
                        }
                    }
                    if (end.isNotBlank()) {
                        val cmp = UpdateChecker.compareVersions(currentVersion, end)
                        if (if (includeEnd) cmp > 0 else cmp >= 0) {
                            return Pair(false, "API version '$currentVersion' exceeds required range '$trimmed'.")
                        }
                    }
                    return Pair(true, null)
                }
            }

            // Comparison tokens e.g. ">=1.0.0 <2.0.0" or "^1.0.0"
            val tokens = trimmed.split(Regex("\\s+"))
            for (token in tokens) {
                when {
                    token.startsWith(">=") -> {
                        val v = token.removePrefix(">=")
                        if (UpdateChecker.compareVersions(currentVersion, v) < 0) {
                            return Pair(false, "API version '$currentVersion' is lower than required '$token'.")
                        }
                    }
                    token.startsWith("<=") -> {
                        val v = token.removePrefix("<=")
                        if (UpdateChecker.compareVersions(currentVersion, v) > 0) {
                            return Pair(false, "API version '$currentVersion' exceeds required '$token'.")
                        }
                    }
                    token.startsWith(">") -> {
                        val v = token.removePrefix(">")
                        if (UpdateChecker.compareVersions(currentVersion, v) <= 0) {
                            return Pair(false, "API version '$currentVersion' is not greater than '$token'.")
                        }
                    }
                    token.startsWith("<") -> {
                        val v = token.removePrefix("<")
                        if (UpdateChecker.compareVersions(currentVersion, v) >= 0) {
                            return Pair(false, "API version '$currentVersion' is not less than '$token'.")
                        }
                    }
                    token.startsWith("^") -> {
                        val v = token.removePrefix("^")
                        val (core, _) = UpdateChecker.splitVersion(v)
                        val major = core.getOrNull(0) ?: 1
                        val nextMajor = "${major + 1}.0.0"
                        if (UpdateChecker.compareVersions(currentVersion, v) < 0 || UpdateChecker.compareVersions(currentVersion, nextMajor) >= 0) {
                            return Pair(false, "API version '$currentVersion' does not satisfy caret range '$token' (expected >=$v <$nextMajor).")
                        }
                    }
                    token.startsWith("~") -> {
                        val v = token.removePrefix("~")
                        val (core, _) = UpdateChecker.splitVersion(v)
                        val major = core.getOrNull(0) ?: 1
                        val minor = core.getOrNull(1) ?: 0
                        val nextMinor = "$major.${minor + 1}.0"
                        if (UpdateChecker.compareVersions(currentVersion, v) < 0 || UpdateChecker.compareVersions(currentVersion, nextMinor) >= 0) {
                            return Pair(false, "API version '$currentVersion' does not satisfy tilde range '$token' (expected >=$v <$nextMinor).")
                        }
                    }
                    else -> {
                        if (UpdateChecker.compareVersions(currentVersion, token) < 0) {
                            return Pair(false, "API version '$currentVersion' does not satisfy required '$token'.")
                        }
                    }
                }
            }
            return Pair(true, null)
        } catch (e: Exception) {
            return Pair(false, "Failed to parse API compatibility expression '$rangeExpr': ${e.message}")
        }
    }
}
