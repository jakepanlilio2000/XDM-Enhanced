package xdm.capture

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

class BrowserExtensionFunctionalityTest {

    private val rootDir = run {
        val cwd = File(".").canonicalFile
        if (File(cwd, "browser-extension").exists()) cwd else cwd.parentFile
    }
    private val chromeDir = File(rootDir, "browser-extension/chrome-extension")
    private val firefoxDir = File(rootDir, "browser-extension/firefox-extension")

    @Test
    fun chromeManifest_isValidAndCompliant() {
        val manifestFile = File(chromeDir, "manifest.json")
        assertTrue(manifestFile.exists(), "Chrome manifest.json should exist")
        val content = manifestFile.readText()

        assertTrue(content.contains("\"manifest_version\": 3"), "Chrome extension must be Manifest V3")
        assertTrue(content.contains("\"options_ui\""), "Chrome extension must declare options_ui for settings")
        assertTrue(content.contains("\"options.html\""), "options_ui must point to options.html")

        val optionsPage = File(chromeDir, "options.html")
        assertTrue(optionsPage.exists(), "options.html must exist")
    }

    @Test
    fun firefoxManifest_isValidAndCompliant() {
        val manifestFile = File(firefoxDir, "manifest.json")
        assertTrue(manifestFile.exists(), "Firefox manifest.json should exist")
        val content = manifestFile.readText()

        assertTrue(content.contains("\"manifest_version\": 2"), "Firefox extension is Manifest V2")
        assertTrue(content.contains("\"options_ui\""), "Firefox extension must declare options_ui for settings")
        assertTrue(content.contains("\"app/options.html\""), "options_ui must point to app/options.html")

        val optionsPage = File(firefoxDir, "app/options.html")
        assertTrue(optionsPage.exists(), "app/options.html must exist")
    }

    @Test
    fun optionsPages_containAllRequiredSections() {
        val chromeOptions = File(chromeDir, "options.html")
        val firefoxOptions = File(firefoxDir, "app/options.html")

        for (optFile in listOf(chromeOptions, firefoxOptions)) {
            assertTrue(optFile.exists(), "${optFile.path} must exist")
            val content = optFile.readText()

            assertTrue(content.contains("section-general"), "Must contain General section in ${optFile.name}")
            assertTrue(content.contains("section-detection"), "Must contain Download Detection section in ${optFile.name}")
            assertTrue(content.contains("section-exclusions"), "Must contain Website Exclusions section in ${optFile.name}")
            assertTrue(content.contains("section-keywords"), "Must contain Keyword Filters section in ${optFile.name}")
            assertTrue(content.contains("section-appearance"), "Must contain Appearance section in ${optFile.name}")
            assertTrue(content.contains("section-about"), "Must contain About section in ${optFile.name}")
        }
    }

    @Test
    fun popupUi_containsModernBrandingAndActions() {
        val chromePopup = File(chromeDir, "popup.html")
        val firefoxPopup = File(firefoxDir, "app/popup.html")

        for (popupFile in listOf(chromePopup, firefoxPopup)) {
            assertTrue(popupFile.exists(), "${popupFile.path} must exist")
            val content = popupFile.readText()

            assertTrue(content.contains("XDM Enhanced"), "Popup must display XDM Enhanced branding")
            assertTrue(content.contains("mediaCountBadge"), "Popup must display detected media count badge")
            assertTrue(content.contains("downloadCurrentMediaBtn"), "Popup must have Download Current Media quick action")
            assertTrue(content.contains("bulkShortcutHint"), "Popup must support dynamic shortcut display")
            assertTrue(content.contains("disconnectedNotice"), "Popup must have clear disconnected warning")
            assertTrue(content.contains("openSettingsBtn"), "Popup must link to unified options page")
        }
    }

    @Test
    fun popupJs_monitoringToggleDoesNotCloseWindow() {
        val chromePopupJs = File(chromeDir, "popup.js").readText()
        val firefoxPopupJs = File(firefoxDir, "app/popup.js").readText()

        for (code in listOf(chromePopupJs, firefoxPopupJs)) {
            // Find the monitoring toggle change listener
            val chkIndex = code.indexOf("""document.getElementById("chk")""")
            assertTrue(chkIndex >= 0, "chk toggle listener must exist")

            val snippet = code.substring(chkIndex, chkIndex + 300)
            assertFalse(snippet.contains("window.close()"),
                "Monitoring toggle must NOT close the popup window prematurely! Found in snippet: $snippet")
        }
    }

    @Test
    fun bulkJs_dismissesPickerOnEscape() {
        val chromeBulkJs = File(chromeDir, "bulk.js").readText()
        val firefoxBulkJs = File(firefoxDir, "app/bulk.js").readText()

        for (code in listOf(chromeBulkJs, firefoxBulkJs)) {
            assertTrue(code.contains("window.close()"), "bulk.js must call window.close on Escape")
            assertTrue(code.contains("""e.key === "Escape""""), "bulk.js must handle Escape key")
        }
    }

    @Test
    fun htmlPages_referenceOnlyExistingLocalFiles_andHaveNoInlineScripts() {
        val htmlFiles = (chromeDir.listFiles { f -> f.extension == "html" }?.toList() ?: emptyList()) +
                (File(firefoxDir, "app").listFiles { f -> f.extension == "html" }?.toList() ?: emptyList()) +
                listOf(File(firefoxDir, "main.html"))

        assertTrue(htmlFiles.isNotEmpty(), "HTML files should be found")

        val srcPattern = Pattern.compile("""<(?:script|link)[^>]+(?:src|href)=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val inlineScriptPattern = Pattern.compile("""<script(?:\s+[^>]*)?>[\s\S]*?</script>""", Pattern.CASE_INSENSITIVE)

        for (htmlFile in htmlFiles) {
            val content = htmlFile.readText()
            val matcher = srcPattern.matcher(content)

            while (matcher.find()) {
                val ref = matcher.group(1)
                assertFalse(ref.startsWith("http:") || ref.startsWith("https:") || ref.startsWith("//"),
                    "${htmlFile.name} loads remote code: $ref")

                val localTarget = File(htmlFile.parentFile, ref.removePrefix("./"))
                assertTrue(localTarget.exists(),
                    "${htmlFile.name} references non-existent file: $ref (resolved to ${localTarget.path})")
            }

            // Check no non-empty inline scripts
            val inlineMatcher = inlineScriptPattern.matcher(content)
            while (inlineMatcher.find()) {
                val tag = inlineMatcher.group(0)
                if (!tag.contains("src=", ignoreCase = true)) {
                    val scriptBody = tag.substringAfter(">").substringBeforeLast("</script>").trim()
                    assertTrue(scriptBody.isEmpty(), "${htmlFile.name} contains inline script violating CSP: $scriptBody")
                }
            }
        }
    }

    @Test
    fun extensionScripts_haveNoDynamicEval() {
        val jsFiles = (chromeDir.listFiles { f -> f.extension == "js" }?.toList() ?: emptyList()) +
                (File(firefoxDir, "app").listFiles { f -> f.extension == "js" }?.toList() ?: emptyList()) +
                listOf(File(firefoxDir, "main.js"))

        val forbiddenPattern = Pattern.compile("""\b(?:eval\s*\(|new\s+Function\s*\()""")

        for (file in jsFiles) {
            val content = file.readText()
            assertFalse(forbiddenPattern.matcher(content).find(),
                "${file.name} contains dynamic eval() or new Function(), violating CSP")
        }
    }

    @Test
    fun hostnameNormalization_andExclusionMatching() {
        fun normalizeHost(input: String?): String {
            if (input == null) return ""
            var s = input.trim().lowercase(Locale.ROOT)
            if (s.isEmpty()) return ""

            val schemeIdx = s.indexOf("://")
            if (schemeIdx != -1) s = s.substring(schemeIdx + 3)

            val atIdx = s.indexOf('@')
            if (atIdx != -1) s = s.substring(atIdx + 1)

            val slashIdx = s.indexOf('/')
            if (slashIdx != -1) s = s.substring(0, slashIdx)

            val queryIdx = s.indexOf('?')
            if (queryIdx != -1) s = s.substring(0, queryIdx)

            val hashIdx = s.indexOf('#')
            if (hashIdx != -1) s = s.substring(0, hashIdx)

            if (!s.startsWith("[")) {
                val colonIdx = s.indexOf(':')
                if (colonIdx != -1) s = s.substring(0, colonIdx)
            } else {
                val bracketEnd = s.indexOf(']')
                if (bracketEnd != -1) s = s.substring(0, bracketEnd + 1)
            }

            while (s.endsWith(".")) s = s.substring(0, s.length - 1)
            return s
        }

        fun normalizeHostRule(input: String?): String {
            if (input == null) return ""
            var trimmed = input.trim().lowercase(Locale.ROOT)
            val isWildcard = trimmed.startsWith("*.")
            if (isWildcard) trimmed = trimmed.substring(2)
            val host = normalizeHost(trimmed)
            if (host.isEmpty()) return ""
            return if (isWildcard) "*.$host" else host
        }

        fun matchesHostRule(host: String?, rule: String?): Boolean {
            if (host == null || rule == null) return false
            val nHost = normalizeHost(host)
            val nRule = normalizeHostRule(rule)
            if (nHost.isEmpty() || nRule.isEmpty()) return false

            if (nRule.startsWith("*.")) {
                val suffix = nRule.substring(2)
                return nHost.equals(suffix, ignoreCase = true) || nHost.endsWith(".$suffix", ignoreCase = true)
            }
            return nHost.equals(nRule, ignoreCase = true) || nHost.endsWith(".$nRule", ignoreCase = true)
        }

        assertEquals("example.com", normalizeHostRule("https://example.com/some/path?query=1"))
        assertEquals("*.domain.org", normalizeHostRule("*.DOMAIN.ORG:8080/"))
        assertEquals("sub.domain.co.uk", normalizeHost("http://user:pass@sub.domain.co.uk:9000/test"))

        assertTrue(matchesHostRule("example.com", "example.com"))
        assertTrue(matchesHostRule("sub.example.com", "example.com"))
        assertTrue(matchesHostRule("sub.example.com", "*.example.com"))
        assertFalse(matchesHostRule("notexample.com", "example.com"))
        assertFalse(matchesHostRule("attacker-example.com", "example.com"))
    }

    @Test
    fun multipleKeywordFiltering_andOrModes() {
        data class Item(val url: String, val filename: String, val text: String)

        fun createMatcher(kw: String): (Item) -> Boolean {
            val trimmed = kw.trim()
            if (trimmed.startsWith("/") && trimmed.lastIndexOf("/") > 0) {
                val lastSlash = trimmed.lastIndexOf("/")
                val patternStr = trimmed.substring(1, lastSlash)
                val flags = trimmed.substring(lastSlash + 1)
                val isCaseInsensitive = flags.contains("i")
                val p = Pattern.compile(patternStr, if (isCaseInsensitive) Pattern.CASE_INSENSITIVE else 0)
                return { item -> p.matcher(item.url).find() || p.matcher(item.filename).find() || p.matcher(item.text).find() }
            }

            if (trimmed.startsWith("*.") || trimmed.contains("*") || trimmed.contains("?")) {
                val regexPattern = "^" + Pattern.quote(trimmed).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "$"
                val p = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE)
                return { item -> p.matcher(item.url).find() || p.matcher(item.filename).find() || p.matcher(item.text).find() }
            }

            val needle = trimmed.lowercase(Locale.ROOT)
            return { item ->
                item.url.lowercase(Locale.ROOT).contains(needle) ||
                        item.filename.lowercase(Locale.ROOT).contains(needle) ||
                        item.text.lowercase(Locale.ROOT).contains(needle)
            }
        }

        fun compileKeywords(keywords: List<String>, mode: String): (Item) -> Boolean {
            if (keywords.isEmpty()) return { true }
            val matchers = keywords.map { createMatcher(it) }
            return if (mode.equals("OR", ignoreCase = true)) {
                { item -> matchers.any { it(item) } }
            } else {
                { item -> matchers.all { it(item) } }
            }
        }

        val item1 = Item("https://cdn.example.com/videos/movie_1080p.mp4", "movie_1080p.mp4", "Trailer 1080p")
        val item2 = Item("https://cdn.example.com/videos/movie_720p.mkv", "movie_720p.mkv", "Trailer 720p")
        val item3 = Item("https://cdn.example.com/images/poster.jpg", "poster.jpg", "Official Poster")

        val andFilter = compileKeywords(listOf("*.mp4", "1080p"), "AND")
        assertTrue(andFilter(item1), "Matches both *.mp4 and 1080p")
        assertFalse(andFilter(item2), "Matches 720p, not 1080p or mp4")

        val orFilter = compileKeywords(listOf("*.mp4", "*.mkv"), "OR")
        assertTrue(orFilter(item1), "mp4 matches")
        assertTrue(orFilter(item2), "mkv matches")
        assertFalse(orFilter(item3), "jpg does not match either")
    }
}
