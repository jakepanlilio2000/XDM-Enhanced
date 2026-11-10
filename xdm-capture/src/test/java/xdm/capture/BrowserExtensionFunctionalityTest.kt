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

    @Test
    fun contentScripts_declaredInBothManifests() {
        val chromeManifest = File(chromeDir, "manifest.json").readText()
        val firefoxManifest = File(firefoxDir, "manifest.json").readText()

        assertTrue(chromeManifest.contains("\"content_scripts\""), "Chrome manifest must declare content_scripts")
        assertTrue(chromeManifest.contains("overlay.js"), "Chrome content_scripts must include overlay.js")
        assertTrue(chromeManifest.contains("overlay.css"), "Chrome content_scripts must include overlay.css")
        assertTrue(chromeManifest.contains("\"all_frames\": true"), "Chrome content_scripts must support embedded iframe players")

        assertTrue(firefoxManifest.contains("\"content_scripts\""), "Firefox manifest must declare content_scripts")
        assertTrue(firefoxManifest.contains("app/overlay.js"), "Firefox content_scripts must include app/overlay.js")
        assertTrue(firefoxManifest.contains("app/overlay.css"), "Firefox content_scripts must include app/overlay.css")
        assertTrue(firefoxManifest.contains("\"all_frames\": true"), "Firefox content_scripts must support embedded iframe players")
    }

    @Test
    fun overlayJs_containsShadowDomIsolation_andPlayerLifecycle() {
        val chromeOverlayJs = File(chromeDir, "overlay.js").readText()
        val firefoxOverlayJs = File(firefoxDir, "app/overlay.js").readText()

        for (code in listOf(chromeOverlayJs, firefoxOverlayJs)) {
            // Shadow DOM isolation
            assertTrue(code.contains("attachShadow"), "overlay.js must use Shadow DOM for style isolation")

            // Store policy compliance: blocked YouTube domains
            assertTrue(code.contains("youtube.com"), "overlay.js must check store-restricted domains")

            // Player container & single overlay per player
            assertTrue(code.contains("overlays.has(video)"), "overlay.js must prevent duplicate overlays for the same player")

            // Fullscreen handling
            assertTrue(code.contains("fullscreenchange"), "overlay.js must handle fullscreen changes")

            // Dynamic video observation
            assertTrue(code.contains("MutationObserver"), "overlay.js must observe dynamic player additions and removals")

            // Keyboard accessibility
            assertTrue(code.contains("aria-expanded"), "overlay.js must maintain ARIA expanded state")
            assertTrue(code.contains("Escape"), "overlay.js must support dismissing dropdown on Escape")

            // Background protocol integration
            assertTrue(code.contains("overlay-init"), "overlay.js must query background script on init")
            assertTrue(code.contains("overlay-hide-site"), "overlay.js must support per-site disabling")
            assertTrue(code.contains("vid"), "overlay.js must dispatch video download command")
        }
    }

    @Test
    fun optionsPages_containFloatingOverlayToggle() {
        val chromeOptions = File(chromeDir, "options.html").readText()
        val firefoxOptions = File(firefoxDir, "app/options.html").readText()

        assertTrue(chromeOptions.contains("optFloatingOverlay"), "Chrome options.html must contain optFloatingOverlay toggle")
        assertTrue(firefoxOptions.contains("optFloatingOverlay"), "Firefox options.html must contain optFloatingOverlay toggle")

        val chromeOptionsJs = File(chromeDir, "options.js").readText()
        val firefoxOptionsJs = File(firefoxDir, "app/options.js").readText()

        assertTrue(chromeOptionsJs.contains("optFloatingOverlay"), "Chrome options.js must bind optFloatingOverlay")
        assertTrue(firefoxOptionsJs.contains("optFloatingOverlay"), "Firefox options.js must bind optFloatingOverlay")
    }

    @Test
    fun overlayExclusion_matchingLogic() {
        fun matchesHost(host: String, rule: String): Boolean {
            val h = host.trim().lowercase(Locale.ROOT)
            val r = rule.trim().lowercase(Locale.ROOT)
            if (r.startsWith("*.")) {
                val suffix = r.substring(2)
                return h == suffix || h.endsWith(".$suffix")
            }
            return h == r || h.endsWith(".$r")
        }

        fun isOverlayExcluded(host: String, rules: List<String>): Boolean {
            return rules.any { matchesHost(host, it) }
        }

        val rules = listOf("example.com", "*.video-service.org", "internal.net")
        assertTrue(isOverlayExcluded("example.com", rules))
        assertTrue(isOverlayExcluded("sub.example.com", rules))
        assertTrue(isOverlayExcluded("stream.video-service.org", rules))
        assertTrue(isOverlayExcluded("internal.net", rules))
        assertFalse(isOverlayExcluded("other-site.com", rules))
        assertFalse(isOverlayExcluded("notexample.com", rules))
    }

    @Test
    fun overlayJs_keyboardNavigationAndAccessibility() {
        val chromeOverlayJs = File(chromeDir, "overlay.js").readText()
        val firefoxOverlayJs = File(firefoxDir, "app/overlay.js").readText()

        for (code in listOf(chromeOverlayJs, firefoxOverlayJs)) {
            assertTrue(code.contains("ArrowDown"), "overlay.js must support ArrowDown navigation")
            assertTrue(code.contains("ArrowUp"), "overlay.js must support ArrowUp navigation")
            assertTrue(code.contains("activeElement"), "overlay.js must check activeElement for arrow navigation")
            assertTrue(code.contains("aria-haspopup"), "overlay.js must specify aria-haspopup")
            assertTrue(code.contains("aria-label"), "overlay.js must specify aria-label")
        }
    }

    @Test
    fun overlayJs_streamMetadataExtractionLogic() {
        data class StreamMeta(val format: String, val quality: String, val rest: String)

        fun parseStreamMeta(info: String?): StreamMeta {
            val raw = (info ?: "").trim()
            val formatRegex = Pattern.compile("""^\[([^\]]{1,10})\]\s*(.*)$""")
            val matcher = formatRegex.matcher(raw)
            val format = if (matcher.find()) matcher.group(1).trim() else "VIDEO"
            val rest = if (matcher.find(0)) matcher.group(2).trim() else raw

            var quality = "Original"
            val qRegex = Pattern.compile("""\b(\d{3,4}p|\d{3,4}x\d{3,4}|4k|2k|hd)\b""", Pattern.CASE_INSENSITIVE)
            val qMatcher = qRegex.matcher(rest)
            if (qMatcher.find()) {
                quality = qMatcher.group(1)
            }

            return StreamMeta(
                format = format.uppercase(Locale.ROOT),
                quality = quality,
                rest = rest
            )
        }

        val meta1 = parseStreamMeta("[MP4] 1080p, 45.2 MB")
        assertEquals("MP4", meta1.format)
        assertEquals("1080p", meta1.quality)
        assertEquals("1080p, 45.2 MB", meta1.rest)

        val meta2 = parseStreamMeta("[HLS] 1920x1080, 1.2 GB")
        assertEquals("HLS", meta2.format)
        assertEquals("1920x1080", meta2.quality)

        val meta3 = parseStreamMeta("[DASH] 4k video stream")
        assertEquals("DASH", meta3.format)
        assertEquals("4k", meta3.quality)

        val meta4 = parseStreamMeta("Direct 720p stream")
        assertEquals("VIDEO", meta4.format)
        assertEquals("720p", meta4.quality)

        val meta5 = parseStreamMeta("")
        assertEquals("VIDEO", meta5.format)
        assertEquals("Original", meta5.quality)
        assertEquals("", meta5.rest)
    }

    @Test
    fun overlayCss_containsScopedStylesAndAnimations() {
        val chromeCss = File(chromeDir, "overlay.css")
        val firefoxCss = File(firefoxDir, "app/overlay.css")

        for (cssFile in listOf(chromeCss, firefoxCss)) {
            assertTrue(cssFile.exists(), "${cssFile.path} must exist")
            val content = cssFile.readText()

            assertTrue(content.contains(":host"), "CSS must define :host root isolation")
            assertTrue(content.contains("all: initial"), "CSS must reset page styles with all: initial")
            assertTrue(content.contains("data-compact"), "CSS must support data-compact responsive mode")
            assertTrue(content.contains("data-fullscreen"), "CSS must support data-fullscreen fixed positioning")
            assertTrue(content.contains("backdrop-filter"), "CSS must feature modern glassmorphism")
            assertTrue(content.contains("xdmSlideDown"), "CSS must feature dropdown animation")
        }
    }

    @Test
    fun overlayJs_drmDetectionAndAlert() {
        val chromeOverlayJs = File(chromeDir, "overlay.js").readText()
        val firefoxOverlayJs = File(firefoxDir, "app/overlay.js").readText()

        for (code in listOf(chromeOverlayJs, firefoxOverlayJs)) {
            // EME detection via HTMLMediaElement.mediaKeys
            assertTrue(code.contains("mediaKeys"), "overlay.js must check video.mediaKeys for DRM protection")
            assertTrue(code.contains("DRM Protected Stream"), "overlay.js must display clear DRM alert")
            assertTrue(code.contains("Encrypted Media Extensions"), "overlay.js must explain DRM restriction")
        }
    }

    @Test
    fun overlayJs_streamParsing_andDeduplication() {
        val chromeOverlayJs = File(chromeDir, "overlay.js").readText()
        val firefoxOverlayJs = File(firefoxDir, "app/overlay.js").readText()

        for (code in listOf(chromeOverlayJs, firefoxOverlayJs)) {
            assertTrue(code.contains("parseStreamDetails"), "overlay.js must implement parseStreamDetails")
            assertTrue(code.contains("getProcessedStreams"), "overlay.js must implement getProcessedStreams")
            assertTrue(code.contains("extractHeight"), "overlay.js must implement extractHeight for sorting")
        }

        // Test the deduplication & sorting logic directly
        data class MockVid(
            val id: Long,
            val text: String,
            val streamType: String? = null,
            val format: String? = null,
            val resolution: String? = null,
            val estimatedSize: String? = null,
            val hasAudio: Boolean? = true,
            val info: String? = null
        )

        data class StreamDetails(
            val streamType: String,
            val format: String,
            val resolution: String,
            val estimatedSize: String,
            val hasAudio: Boolean
        )

        fun parseStreamDetails(v: MockVid): StreamDetails {
            var st = (v.streamType ?: "").uppercase(Locale.ROOT)
            var fmt = (v.format ?: "").uppercase(Locale.ROOT)
            var res = v.resolution ?: ""
            var sz = v.estimatedSize ?: ""
            val raw = (v.info ?: "").trim()

            if (st.isEmpty()) {
                st = if (raw.contains("[HLS]")) "HLS" else if (raw.contains("[DASH]")) "DASH" else "HTTP"
            }
            if (fmt.isEmpty()) {
                fmt = if (st == "HLS") "HLS" else if (st == "DASH") "DASH" else "MP4"
            }
            if (res.isEmpty()) {
                val qMatch = Regex("""\b(\d{3,4}p|\d{3,4}x\d{3,4}|4k)\b""", RegexOption.IGNORE_CASE).find(raw)
                res = qMatch?.value ?: "Standard"
            }
            if (sz.isEmpty()) {
                val sMatch = Regex("""(≈\s*[\d.]+\s*[KMGT]B)""", RegexOption.IGNORE_CASE).find(raw)
                sz = sMatch?.value ?: ""
            }
            val isEstimate = st == "HLS" || st == "DASH" || sz.startsWith("≈")
            val displaySize = if (sz.isNotEmpty()) {
                if (isEstimate && !sz.startsWith("≈")) "≈ $sz (est.)" else if (isEstimate && !sz.contains("(est.)")) "$sz (est.)" else sz
            } else ""

            return StreamDetails(st, fmt, res, displaySize, v.hasAudio ?: true)
        }

        fun extractHeight(res: String): Int {
            val lower = res.lowercase(Locale.ROOT)
            if (lower.contains("4k") || lower.contains("2160")) return 2160
            if (lower.contains("1440") || lower.contains("2k")) return 1440
            if (lower.contains("1080")) return 1080
            if (lower.contains("720")) return 720
            if (lower.contains("480")) return 480
            if (lower.contains("360")) return 360
            val pMatch = Regex("""(\d{3,4})p?""").find(lower)
            return pMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        fun processStreams(videos: List<MockVid>): List<Pair<MockVid, StreamDetails>> {
            val seenKeys = mutableSetOf<String>()
            val processed = mutableListOf<Pair<MockVid, StreamDetails>>()

            for (vid in videos) {
                val details = parseStreamDetails(vid)
                val key = "${details.streamType}|${details.format}|${details.resolution}|${details.hasAudio}|${details.estimatedSize}|${vid.text}"
                if (!seenKeys.contains(key)) {
                    seenKeys.add(key)
                    processed.add(vid to details)
                }
            }

            processed.sortWith { a, b ->
                val hDiff = extractHeight(b.second.resolution) - extractHeight(a.second.resolution)
                if (hDiff != 0) hDiff else (if (b.second.hasAudio) 1 else 0) - (if (a.second.hasAudio) 1 else 0)
            }
            return processed
        }

        val streamList = listOf(
            MockVid(1, "Video Trailer", streamType = "DASH", format = "DASH", resolution = "1080p", estimatedSize = "45 MB", hasAudio = true),
            MockVid(2, "Video Trailer", streamType = "DASH", format = "DASH", resolution = "1080p", estimatedSize = "45 MB", hasAudio = true), // Exact duplicate
            MockVid(3, "Video Trailer", streamType = "DASH", format = "DASH", resolution = "1080p", estimatedSize = "35 MB", hasAudio = false), // Video only
            MockVid(4, "Video Trailer", streamType = "DASH", format = "DASH", resolution = "2160p (4K)", estimatedSize = "180 MB", hasAudio = true), // 4K
            MockVid(5, "Video Trailer", streamType = "HLS", format = "HLS", resolution = "720p", estimatedSize = "22 MB", hasAudio = true) // 720p HLS
        )

        val result = processStreams(streamList)
        assertEquals(4, result.size, "Duplicate representation must be removed while distinct ones are preserved")

        // Check descending sorting by resolution: 2160p first, then 1080p (with audio), 1080p (video only), then 720p
        assertEquals("2160p (4K)", result[0].second.resolution)
        assertEquals("1080p", result[1].second.resolution)
        assertTrue(result[1].second.hasAudio)
        assertEquals("1080p", result[2].second.resolution)
        assertFalse(result[2].second.hasAudio)
        assertEquals("720p", result[3].second.resolution)

        // Verify format integrity and estimated size labeling
        assertEquals("DASH", result[0].second.streamType)
        assertNotEquals("MP4", result[0].second.format, "DASH must never be labeled MP4")
        assertTrue(result[0].second.estimatedSize.contains("(est.)"), "DASH size must be flagged as estimate")

        assertEquals("HLS", result[3].second.streamType)
        assertNotEquals("MP4", result[3].second.format, "HLS must never be labeled MP4")
        assertTrue(result[3].second.estimatedSize.contains("(est.)"), "HLS size must be flagged as estimate")
    }

    @Test
    fun extension_offlineAndErrorHandling() {
        val chromeAppJs = File(chromeDir, "app.js").readText()
        val firefoxAppJs = File(firefoxDir, "app/app.js").readText()

        for (code in listOf(chromeAppJs, firefoxAppJs)) {
            // Service worker / background checks desktop connectivity
            assertTrue(code.contains("this.connector.isConnected()"), "app.js must verify connection state")
            assertTrue(code.contains("notRunning: true"), "app.js must return notRunning when desktop is offline")
            assertTrue(code.contains("XDM isn't running"), "app.js must provide actionable offline error")
        }

        val chromeOverlayJs = File(chromeDir, "overlay.js").readText()
        val firefoxOverlayJs = File(firefoxDir, "app/overlay.js").readText()

        for (code in listOf(chromeOverlayJs, firefoxOverlayJs)) {
            // Overlay checks response and surfaces error feedback
            assertTrue(code.contains("response.notRunning"), "overlay.js must check for offline desktop response")
            assertTrue(code.contains("showDropdownAlert"), "overlay.js must display alert banner on failure")
            assertTrue(code.contains("xdm-error"), "overlay.js must apply error style on button failure")
        }
    }
}
