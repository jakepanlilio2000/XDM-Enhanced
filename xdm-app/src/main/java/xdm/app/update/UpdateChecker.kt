package xdm.app.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import xdm.app.AppContext
import xdm.core.network.http.impl.HttpClientImpl
import xdm.core.util.Logger
import java.io.InputStream

/**
 * Bundled update configuration, read from `/app-version.json` on the classpath.
 */
@Serializable
data class AppVersionConfig(
    val currentVersion: String,
    val updateCheckUrl: String,
    val downloadUrl: String
)

/** A release version found by the check; [isPreRelease] is GitHub's flag or a `-suffix` on the tag. */
internal data class ReleaseVersion(val version: String, val isPreRelease: Boolean)

/**
 * Result handed to the UI when a newer release is found.
 */
data class UpdateInfo(val latestVersion: String, val downloadUrl: String, val isPreRelease: Boolean)

object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    const val DEFAULT_VERSION = "1.0.0-beta.6"
    const val DEFAULT_UPDATE_URL = "https://api.github.com/repos/jakepanlilio2000/XDM-Enhanced/releases?per_page=20"
    const val DEFAULT_DOWNLOAD_URL = "https://github.com/jakepanlilio2000/XDM-Enhanced/releases"

    /** Loads the bundled [AppVersionConfig], or null if it is missing/invalid. */
    fun loadConfig(): AppVersionConfig? {
        return try {
            val stream: InputStream? = UpdateChecker::class.java.getResourceAsStream("/app-version.json")
            if (stream == null) {
                Logger.info("UPDATE", "app-version.json not found on classpath")
                return null
            }
            stream.use { json.decodeFromString<AppVersionConfig>(it.readBytes().decodeToString()) }
        } catch (e: Exception) {
            Logger.error("UPDATE", "Failed to read app-version.json", e)
            null
        }
    }

    /**
     * Checks for a newer release on a background thread using the configured or given [channel].
     */
    fun checkForUpdates(channel: UpdateChannel? = null, callback: (UpdateCheckResult) -> Unit) {
        Thread {
            val effChannel = channel ?: try {
                UpdateChannel.fromId(AppContext.config.updateChannel)
            } catch (_: Throwable) {
                UpdateChannel.BETA
            }
            val result = checkSync(effChannel)
            callback(result)
        }.apply {
            isDaemon = true
            name = "xdm-update-checker"
        }.start()
    }

    /**
     * Backward-compatible check for callers expecting [UpdateInfo].
     */
    fun checkForUpdate(callback: (UpdateInfo?) -> Unit) {
        checkForUpdates { result ->
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val config = loadConfig()
                    callback(
                        UpdateInfo(
                            latestVersion = result.releaseInfo.version,
                            downloadUrl = config?.downloadUrl ?: DEFAULT_DOWNLOAD_URL,
                            isPreRelease = result.releaseInfo.isPreRelease
                        )
                    )
                }
                else -> callback(null)
            }
        }
    }

    /**
     * Synchronously checks for updates from GitHub Releases API for the given [channel].
     */
    fun checkSync(channel: UpdateChannel, currentVersion: String? = null): UpdateCheckResult {
        val config = loadConfig()
        val effCurrent = currentVersion ?: config?.currentVersion ?: DEFAULT_VERSION
        val url = config?.updateCheckUrl ?: DEFAULT_UPDATE_URL

        val (statusCode, body) = fetchReleasesWithStatus(url)

        if (statusCode == 403) {
            return UpdateCheckResult.Error(
                message = "GitHub API rate limit exceeded. Please try again later or visit the releases page directly.",
                isRateLimit = true
            )
        }

        if (statusCode !in 200..299 || body == null) {
            val msg = if (statusCode == -1) "Unable to connect to GitHub. Please check your internet connection."
            else "Update check failed with server status HTTP $statusCode."
            return UpdateCheckResult.Error(message = msg, isNetworkError = (statusCode == -1))
        }

        val releases = try {
            parseReleases(body)
        } catch (e: Exception) {
            return UpdateCheckResult.Error("Failed to parse release information: ${e.message}", cause = e)
        }

        val latest = findNewestRelease(releases, channel)
            ?: return UpdateCheckResult.UpToDate(effCurrent, effCurrent, channel)

        val cleanTag = latest.tag_name.ifBlank { latest.name }.trim()
        val latestVer = cleanTag.removePrefix("v").removePrefix("V")
        val isPre = latest.prerelease || splitVersion(cleanTag).second.isNotEmpty()

        return if (compareVersions(cleanTag, effCurrent) > 0) {
            Logger.info("UPDATE", "Newer version available: $cleanTag (current $effCurrent, channel ${channel.displayName})")
            val installer = UpdateAssetResolver.resolveInstallerAsset(latest.assets)
            val checksum = UpdateAssetResolver.resolveChecksumAsset(latest.assets)

            val info = ReleaseInfo(
                version = latestVer,
                releaseName = latest.name.ifBlank { cleanTag },
                releaseNotes = latest.body,
                htmlUrl = latest.html_url.ifBlank { config?.downloadUrl ?: DEFAULT_DOWNLOAD_URL },
                isPreRelease = isPre,
                publishedAt = latest.published_at,
                installerAsset = installer,
                checksumAsset = checksum
            )
            UpdateCheckResult.UpdateAvailable(info, effCurrent)
        } else {
            Logger.info("UPDATE", "Already up to date (current $effCurrent, latest $latestVer, channel ${channel.displayName})")
            UpdateCheckResult.UpToDate(latestVer, effCurrent, channel)
        }
    }

    /**
     * Parses release JSON response into a list of [GithubRelease].
     */
    fun parseReleases(body: String): List<GithubRelease> {
        val element = json.parseToJsonElement(body)
        val items: List<JsonElement> = if (element is JsonArray) element else listOf(element)
        return items.mapNotNull { runCatching { json.decodeFromJsonElement<GithubRelease>(it) }.getOrNull() }
    }

    /**
     * Selects the newest release for the specified [channel].
     * Drafts are always ignored.
     * On [UpdateChannel.STABLE], pre-releases are filtered out.
     */
    fun findNewestRelease(releases: List<GithubRelease>, channel: UpdateChannel): GithubRelease? {
        return releases
            .filterNot { it.draft }
            .filter { release ->
                val tag = release.tag_name.ifBlank { release.name }.trim()
                if (tag.isEmpty()) return@filter false
                if (channel == UpdateChannel.STABLE) {
                    !release.prerelease && splitVersion(tag).second.isEmpty()
                } else {
                    true
                }
            }
            .maxWithOrNull { a, b ->
                val tagA = a.tag_name.ifBlank { a.name }.trim()
                val tagB = b.tag_name.ifBlank { b.name }.trim()
                compareVersions(tagA, tagB)
            }
    }

    /**
     * Preserved for legacy callers and tests: picks highest versioned release regardless of channel.
     */
    internal fun newestRelease(body: String): ReleaseVersion? {
        val releases = try { parseReleases(body) } catch (_: Exception) { return null }
        val newest = findNewestRelease(releases, UpdateChannel.BETA) ?: return null
        val tag = newest.tag_name.ifBlank { newest.name }.trim()
        return ReleaseVersion(tag, newest.prerelease || splitVersion(tag).second.isNotEmpty())
    }

    /**
     * Executes GET request to GitHub API returning status code and response body.
     */
    private fun fetchReleasesWithStatus(url: String): Pair<Int, String?> {
        val client = try {
            val config = AppContext.config
            HttpClientImpl(
                1, config.toProxy(), readTimeoutSeconds = 12,
                proxyUser = config.proxyUser, proxyPassword = config.proxyPass
            )
        } catch (_: Throwable) {
            HttpClientImpl(1, null, readTimeoutSeconds = 12)
        }

        return try {
            val headers = mapOf(
                "User-Agent" to listOf("XDM-Update-Checker"),
                "Accept" to listOf("application/vnd.github+json")
            )
            val responseResult = client.getResponse(url, headers, null, null)
            val response = responseResult.getOrThrow()
            response.use { res ->
                val status = res.statusCode
                val body = if (status in 200..299) res.inputStream.readBytes().decodeToString() else null
                Pair(status, body)
            }
        } catch (e: Exception) {
            Logger.error("UPDATE", "Failed to fetch releases from $url", e)
            Pair(-1, null)
        } finally {
            runCatching { client.close() }
        }
    }

    /**
     * Compares two versions semver-style (an optional leading `v` is ignored).
     * Returns > 0 if [v1] is newer than [v2], < 0 if older, 0 if equal.
     *
     * The dotted numeric core is compared first. With equal cores a release beats a pre-release
     * (`9.1.0` > `9.1.0-rc.1`); two pre-releases compare their suffix piece by piece, numbers
     * numerically (`beta.2` < `beta.10`) and text alphabetically (`alpha` < `beta` < `rc`).
     */
    fun compareVersions(v1: String, v2: String): Int {
        val (coreA, preA) = splitVersion(v1)
        val (coreB, preB) = splitVersion(v2)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val x = coreA.getOrElse(i) { 0 }
            val y = coreB.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        when {
            preA.isEmpty() && preB.isEmpty() -> return 0
            preA.isEmpty() -> return 1
            preB.isEmpty() -> return -1
        }
        for (i in 0 until minOf(preA.size, preB.size)) {
            val c = comparePreReleasePart(preA[i], preB[i])
            if (c != 0) return c
        }
        return preA.size.compareTo(preB.size)
    }

    /** Numeric parts sort before text parts, as in semver. */
    private fun comparePreReleasePart(a: String, b: String): Int {
        val x = a.toIntOrNull()
        val y = b.toIntOrNull()
        return when {
            x != null && y != null -> x.compareTo(y)
            x != null -> -1
            y != null -> 1
            else -> a.compareTo(b, ignoreCase = true)
        }
    }

    /**
     * Splits `v9.1.0-beta.2` into its numeric core `[9, 1, 0]` and pre-release parts `[beta, 2]`.
     * Build metadata (`+...`) is dropped.
     */
    internal fun splitVersion(v: String): Pair<List<Int>, List<String>> {
        val s = v.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val dash = s.indexOf('-')
        val core = if (dash < 0) s else s.substring(0, dash)
        val pre = if (dash < 0) "" else s.substring(dash + 1)
        return core.split('.').mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() } to
                pre.split('.', '-', '_').filter { it.isNotEmpty() }
    }
}
