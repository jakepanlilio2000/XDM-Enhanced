package xdm.app.rules

import java.util.UUID

/**
 * A user-configured post-download rule that matches files by extension and executes a custom command.
 */
data class PostDownloadRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var extensions: List<String> = emptyList(),
    var command: String = "",
    var enabled: Boolean = true
) {
    /**
     * Checks if this rule matches a given file name or extension.
     * Matching is case-insensitive and normalizes leading dots.
     * Supports compound extensions (e.g. "tar.gz").
     * If [extensions] contains "*" or is empty, it acts as a match-all rule.
     */
    fun matches(fileName: String): Boolean {
        if (!enabled) return false
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) return false
        if (extensions.isEmpty()) return true

        val lowerName = trimmed.lowercase()
        return extensions.any { rawExt ->
            val ext = rawExt.trim().lowercase().removePrefix(".")
            when {
                ext.isEmpty() -> false
                ext == "*" -> true
                lowerName.endsWith(".$ext") -> true
                lowerName == ext -> true
                else -> false
            }
        }
    }

    companion object {
        /**
         * Parses a comma-, semicolon-, or whitespace-delimited string into a clean list of normalized extensions.
         */
        fun parseExtensions(raw: String): List<String> =
            raw.split(',', ';', ' ', '\n', '\t')
                .map { it.trim().removePrefix(".").lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()

        /**
         * Formats a list of extensions into a readable comma-separated string for display and editing.
         */
        fun formatExtensions(extensions: List<String>): String =
            extensions.joinToString(", ")
    }
}

