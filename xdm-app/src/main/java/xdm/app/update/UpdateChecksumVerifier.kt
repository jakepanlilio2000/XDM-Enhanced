package xdm.app.update

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

sealed class ChecksumResult {
    data class Valid(val sha256: String) : ChecksumResult()
    data class Mismatch(val expected: String, val actual: String) : ChecksumResult()
    data class SizeMismatch(val expected: Long, val actual: Long) : ChecksumResult()
    data class HashNotFoundInSums(val actualHash: String) : ChecksumResult()
    data class Error(val message: String, val cause: Throwable? = null) : ChecksumResult()
}

/**
 * Validates integrity of downloaded assets using SHA-256 checksums and file size.
 */
object UpdateChecksumVerifier {

    /**
     * Computes the SHA-256 hexadecimal hash of [file].
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses a SHA256SUMS file content into a map of filename (lowercase) to SHA-256 hash (lowercase).
     */
    fun parseChecksums(content: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Standard formats: "<hash>  <filename>", "<hash> *<filename>", or "<hash> <filename>"
            val parts = trimmed.split("\\s+".toRegex(), limit = 2)
            if (parts.size == 2 && parts[0].length == 64) {
                val hash = parts[0].lowercase().trim()
                val filename = parts[1].trim().removePrefix("*").removePrefix("./").lowercase()
                map[filename] = hash
            } else if (parts.size == 1 && parts[0].length == 64) {
                // If it's a standalone .sha256 file with just the hash
                map[""] = parts[0].lowercase().trim()
            }
        }
        return map
    }

    /**
     * Verifies [file] against expected size and optional [sha256SumsContent].
     */
    fun verify(
        file: File,
        expectedSize: Long,
        sha256SumsContent: String? = null,
        targetFileName: String = file.name
    ): ChecksumResult {
        if (!file.exists() || !file.isFile) {
            return ChecksumResult.Error("Target file does not exist: ${file.absolutePath}")
        }

        // 1. Check size if known
        if (expectedSize > 0 && file.length() != expectedSize) {
            return ChecksumResult.SizeMismatch(expected = expectedSize, actual = file.length())
        }

        // 2. Compute actual SHA-256
        val actualHash = try {
            computeSha256(file)
        } catch (e: Throwable) {
            return ChecksumResult.Error("Failed to calculate SHA-256: ${e.message}", e)
        }

        // 3. If no checksums content provided, size verification is sufficient
        if (sha256SumsContent.isNullOrBlank()) {
            return ChecksumResult.Valid(actualHash)
        }

        // 4. Parse checksums and match
        val checksums = parseChecksums(sha256SumsContent)
        val expectedHash = checksums[targetFileName.lowercase()]
            ?: checksums[targetFileName.substringAfterLast("/").lowercase()]
            ?: checksums[""] // Standalone single-hash file

        return when {
            expectedHash == null -> ChecksumResult.HashNotFoundInSums(actualHash)
            expectedHash.equals(actualHash, ignoreCase = true) -> ChecksumResult.Valid(actualHash)
            else -> ChecksumResult.Mismatch(expected = expectedHash, actual = actualHash)
        }
    }
}

