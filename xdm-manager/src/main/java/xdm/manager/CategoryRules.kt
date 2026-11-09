// Copyright 2026 Subhra Das Gupta
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-XDM-Android-Official-Store-Builds
// Additional terms and the exception: xdm-android/NOTICE

package xdm.manager

import xdm.core.util.FileUtils
import xdm.core.util.fixExtension
import xdm.core.util.getNameFromContentDisposition
import java.util.Locale

/**
 * Picks the category a browser-captured download is preselected into (docs/download-categories.md §3.2).
 *
 * - Patterns are case-insensitive globs: `*` is any run of characters, `?` one character. A pattern with
 *   no wildcard matches anywhere in the value ("contains"); one with a wildcard must match the whole value.
 * - URL patterns are tried against both the download URL and the page it came from.
 * - A category matches with Any (one pattern of any kind) or All (every non-empty kind) semantics.
 * - When several match, the most specific kind that matched wins (URL > file name > extension), then
 *   list order. Within extension tier, longer/more specific extensions (e.g. .tar.gz over .gz) win.
 * - If the file name is missing or has a script extension (.php, etc.), candidate file names in URL query
 *   parameters (e.g. ?file=doc.pdf) and trustworthy MIME types are evaluated.
 *
 * Compiled patterns are cached per category list instance: the config replaces the list as a whole on
 * every change, so a new instance means the snapshot is rebuilt, and readers on any thread see either
 * the old snapshot or the new one.
 */
object CategoryRules {
    private enum class Tier { EXTENSION, FILE_NAME, URL }

    private data class MatchScore(
        val tier: Tier,
        val specificity: Int = 0,
    ) : Comparable<MatchScore> {
        override fun compareTo(other: MatchScore): Int {
            val tc = tier.compareTo(other.tier)
            if (tc != 0) return tc
            return specificity.compareTo(other.specificity)
        }
    }

    private class Compiled(
        val category: DownloadCategory,
        val urls: List<Glob>,
        val names: List<Glob>,
    )

    private class Snapshot(val source: List<DownloadCategory>, val compiled: List<Compiled>)

    @Volatile
    private var snapshot: Snapshot? = null

    private val SCRIPT_EXTS = setOf("php", "asp", "aspx", "jsp", "cgi", "pl", "py", "do", "action", "ashx", "htm", "html", "cfm", "shtml")

    /**
     * Extracts a filename from URL query parameters (e.g. ?file=document.pdf or ?name=photo.jpg).
     */
    fun extractCandidateFileName(url: String?): String? {
        if (url.isNullOrBlank()) return null
        try {
            val qIdx = url.indexOf('?')
            if (qIdx >= 0 && qIdx < url.length - 1) {
                val query = url.substring(qIdx + 1).substringBefore('#')
                val params = query.split('&')
                val candidateKeys = listOf("file", "filename", "name", "attachment", "fn", "path", "target", "download", "f", "doc", "media", "url")
                for (param in params) {
                    val eq = param.indexOf('=')
                    if (eq > 0) {
                        val key = param.substring(0, eq).trim().lowercase(Locale.ROOT)
                        if (key in candidateKeys) {
                            val rawValue = param.substring(eq + 1).trim()
                            val decoded = FileUtils.decodeFileName(rawValue)
                            val sanitized = FileUtils.sanitizeFileName(decoded)
                            if (!sanitized.isNullOrBlank() && sanitized.contains('.') && !sanitized.endsWith('.')) {
                                return sanitized
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Resolves the best candidate file name from the given file name, url, content type, and content disposition.
     */
    fun resolveCandidateFileName(
        fileName: String?,
        url: String?,
        contentType: String? = null,
        contentDisposition: String? = null
    ): String? {
        val cdName = getNameFromContentDisposition(contentDisposition)?.let { FileUtils.sanitizeFileName(it) }
        var candidate = cdName ?: fileName?.takeIf { it.isNotBlank() && it != "FILE" }
        val ext = candidate?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (candidate == null || ext.isNullOrEmpty() || ext in SCRIPT_EXTS) {
            val fromUrl = extractCandidateFileName(url)
            if (fromUrl != null) {
                candidate = fromUrl
            } else if (!url.isNullOrBlank()) {
                val fromPath = FileUtils.getFileName(url)
                if (fromPath != "FILE" && fromPath.isNotBlank() && fromPath.contains('.')) {
                    val pathExt = fromPath.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (pathExt !in SCRIPT_EXTS) {
                        candidate = fromPath
                    }
                }
            }
        }
        if (candidate != null && contentType != null) {
            candidate = fixExtension(candidate, contentType, replace = false)
        }
        return candidate
    }

    /** The category [fileName] / [url] / [pageUrl] belongs to in [categories], or null for None. */
    fun match(
        fileName: String?,
        url: String?,
        pageUrl: String?,
        categories: List<DownloadCategory> = EngineContext.config.categories,
        contentType: String? = null,
        contentDisposition: String? = null,
    ): DownloadCategory? {
        val resolvedName = resolveCandidateFileName(fileName, url, contentType, contentDisposition)
        var best: Pair<MatchScore, DownloadCategory>? = null
        for (c in compiled(categories)) {
            val score = matchScore(c, resolvedName, url, pageUrl) ?: continue
            // Strictly greater: within a tier/score the first category in list order keeps the match.
            if (best == null || score > best.first) best = score to c.category
        }
        return best?.second
    }

    /** The most specific kind through which [c] matches, or null if it does not. */
    private fun matchScore(c: Compiled, fileName: String?, url: String?, pageUrl: String?): MatchScore? {
        val name = fileName?.takeIf { it.isNotBlank() }
        val matchingExt = if (name != null) c.category.matchingExtension(name) else null
        val ext = matchingExt != null
        val matchingNameGlob = if (name != null) c.names.firstOrNull { it.matches(name) } else null
        val byName = matchingNameGlob != null
        val matchingUrlGlob = if (!url.isNullOrBlank() || !pageUrl.isNullOrBlank()) {
            listOfNotNull(url, pageUrl).firstNotNullOfOrNull { u -> c.urls.firstOrNull { it.matches(u) } }
        } else null
        val byUrl = matchingUrlGlob != null

        if (c.category.matchAll) {
            if (c.category.extensions.isNotEmpty() && !ext) return null
            if (c.names.isNotEmpty() && !byName) return null
            if (c.urls.isNotEmpty() && !byUrl) return null
        }
        return when {
            byUrl -> MatchScore(Tier.URL, matchingUrlGlob?.patternLength ?: 0)
            byName -> MatchScore(Tier.FILE_NAME, matchingNameGlob?.patternLength ?: 0)
            ext -> MatchScore(Tier.EXTENSION, matchingExt?.length ?: 0)
            else -> null
        }
    }

    private fun compiled(categories: List<DownloadCategory>): List<Compiled> {
        snapshot?.takeIf { it.source === categories }?.let { return it.compiled }
        val fresh = categories.map { c ->
            Compiled(c, c.urlPatterns.mapNotNull { Glob.of(it) }, c.fileNamePatterns.mapNotNull { Glob.of(it) })
        }
        snapshot = Snapshot(categories, fresh)
        return fresh
    }

    /** A compiled pattern. Matching is linear in the value's length, so no pattern can stall the caller. */
    class Glob private constructor(private val pattern: String, private val contains: Boolean) {
        val patternLength: Int get() = pattern.length

        fun matches(value: String): Boolean {
            val v = value.lowercase(Locale.ROOT)
            return if (contains) v.contains(pattern) else wildcardMatch(pattern, v)
        }

        companion object {
            /** Null for a blank pattern, which matches nothing. */
            fun of(raw: String): Glob? {
                val p = raw.trim().lowercase(Locale.ROOT)
                if (p.isEmpty()) return null
                return Glob(p, contains = '*' !in p && '?' !in p)
            }

            /** Greedy wildcard match with backtracking to the last `*`: O(pattern × value) worst case. */
            private fun wildcardMatch(p: String, v: String): Boolean {
                var pi = 0
                var vi = 0
                var star = -1
                var mark = 0
                while (vi < v.length) {
                    when {
                        pi < p.length && (p[pi] == '?' || p[pi] == v[vi]) -> { pi++; vi++ }
                        pi < p.length && p[pi] == '*' -> { star = pi++; mark = vi }
                        star >= 0 -> { pi = star + 1; vi = ++mark }
                        else -> return false
                    }
                }
                while (pi < p.length && p[pi] == '*') pi++
                return pi == p.length
            }
        }
    }
}
