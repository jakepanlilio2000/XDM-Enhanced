package xdm.app.update

import kotlinx.serialization.Serializable

/**
 * Release channels supported by XDM Enhanced.
 */
enum class UpdateChannel(val id: String, val displayName: String) {
    STABLE("stable", "Stable"),
    BETA("beta", "Beta / Pre-release");

    companion object {
        fun fromId(id: String?): UpdateChannel = when (id?.lowercase()?.trim()) {
            "stable" -> STABLE
            else -> BETA
        }
    }
}

/**
 * Represents a release asset from the GitHub Releases API.
 */
@Serializable
data class GithubAsset(
    val name: String = "",
    val size: Long = 0L,
    val browser_download_url: String = "",
    val content_type: String = ""
)

/**
 * Represents a release from the GitHub Releases API.
 */
@Serializable
data class GithubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val published_at: String = "",
    val assets: List<GithubAsset> = emptyList()
)

/**
 * Enriched release info ready for presentation in the UI.
 */
data class ReleaseInfo(
    val version: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val isPreRelease: Boolean,
    val publishedAt: String,
    val installerAsset: GithubAsset?,
    val checksumAsset: GithubAsset?
)

/**
 * Progress details during an asset download.
 */
data class UpdateProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val percent: Int
)

/**
 * Result of checking for updates.
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(val releaseInfo: ReleaseInfo, val currentVersion: String) : UpdateCheckResult()
    data class UpToDate(val latestVersion: String, val currentVersion: String, val channel: UpdateChannel) : UpdateCheckResult()
    data class Error(
        val message: String,
        val isRateLimit: Boolean = false,
        val isNetworkError: Boolean = false,
        val cause: Throwable? = null
    ) : UpdateCheckResult()
}

