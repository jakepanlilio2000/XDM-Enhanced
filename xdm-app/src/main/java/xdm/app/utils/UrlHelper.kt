package xdm.app.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import xdm.app.OS
import xdm.core.util.Logger
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Upstream XDM website: retained for legacy reference and upstream attribution. */
const val UPSTREAM_WEBSITE_URL = "https://xtremedownloadmanager.com"
const val WEBSITE_URL = UPSTREAM_WEBSITE_URL

/** Fork website: live GitHub Pages documentation, user guide, and troubleshooting. */
const val FORK_WEBSITE_URL = "https://jakepanlilio2000.github.io/XDM-Enhanced"
const val FORK_DOCS_URL = "$FORK_WEBSITE_URL/docs/"
const val FORK_TROUBLESHOOTING_URL = "$FORK_WEBSITE_URL/troubleshooting.html"

/** Help URL used by Help menu and toolbar actions: points directly to fork documentation. */
const val WEBSITE_HELP_URL = FORK_DOCS_URL

/** Repository and releases URLs for XDM Enhanced fork. */
const val GITHUB_REPO_URL = "https://github.com/jakepanlilio2000/XDM-Enhanced"
const val GITHUB_RELEASES_URL = "https://github.com/jakepanlilio2000/XDM-Enhanced/releases"
const val GITHUB_ISSUES_URL = "https://github.com/jakepanlilio2000/XDM-Enhanced/issues"
const val GITHUB_WIKI_URL = "https://github.com/jakepanlilio2000/XDM-Enhanced/wiki"

/** Legacy fallback redirecting to GitHub fork releases. */
const val UPDATE_CHECK_PAGE_URL = GITHUB_RELEASES_URL

/**
 * An http(s) URL, parsed as browsers parse it. `java.net.URI` is stricter than browsers and servers
 * and refuses working links, e.g. with `[` or `]` in the file name.
 */
fun validateURL(url: String): Boolean = url.toHttpUrlOrNull() != null

/**
 * Validates [url] strictly: returns true only for valid, well-formed HTTP or HTTPS URLs.
 */
fun isHttpOrHttpsUrl(url: String): Boolean {
    val httpUrl = url.toHttpUrlOrNull() ?: return false
    return httpUrl.scheme.equals("https", ignoreCase = true) || httpUrl.scheme.equals("http", ignoreCase = true)
}

/**
 * Opens [url] in the default browser. Runs on its own thread: `Desktop.browse` can block for a moment
 * (and on some Linux desktops much longer).
 * Validates that [url] is an HTTP or HTTPS destination before opening.
 * On Windows, falls back to native `Win32Shell.execute` if `Desktop.browse` fails.
 * On Linux, falls back to `xdg-open` when AWT has no BROWSE action or `browse` fails.
 */
fun openInDefaultBrowser(url: String) {
    val httpUrl = url.toHttpUrlOrNull()
    if (httpUrl == null || (!httpUrl.scheme.equals("https", ignoreCase = true) && !httpUrl.scheme.equals("http", ignoreCase = true))) {
        Logger.error("XDM", "Blocked non-HTTP(S) external navigation target: $url")
        return
    }

    thread(isDaemon = true, name = "open-url") {
        try {
            val uri = try {
                URI(url)
            } catch (_: Exception) {
                URI(httpUrl.toUri().toASCIIString())
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                return@thread
            }
        } catch (e: Exception) {
            val os = detectOS()
            if (os == OS.Windows) {
                try {
                    if (xdm.app.utils.win.Win32Shell.execute(url)) {
                        return@thread
                    }
                } catch (winEx: Throwable) {
                    Logger.error("XDM", "Win32Shell.execute fallback failed for $url", winEx)
                }
            } else if (os != OS.Linux) {
                Logger.error("XDM", "Failed to open $url", e)
                return@thread
            }
            Logger.error("XDM", "Desktop.browse failed for $url, trying OS fallback", e)
        }
        if (detectOS() == OS.Linux) xdgOpen(url)
    }
}

/**
 * Runs `xdg-open` and logs a failing exit. On KDE/GNOME it hands off and exits at once, but its generic
 * path (`$BROWSER`, unknown desktop) runs a not-yet-running browser in the foreground, so the wait is
 * bounded: still running after the timeout means the browser started, and the process is left alone.
 */
private fun xdgOpen(url: String) {
    try {
        val p = ProcessBuilder("xdg-open", url)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() != 0) {
            Logger.error("XDM", "xdg-open exited with ${p.exitValue()} for $url")
        }
    } catch (e: Exception) {
        Logger.error("XDM", "Failed to open $url", e)
    }
}
