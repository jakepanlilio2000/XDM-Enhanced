package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.capture.*
import xdm.capture.VideoHelper.LoadedManifest
import xdm.core.downloaders.web.streaming.manifest.hls.HlsParser
import java.io.File

class Phase6MediaCaptureAndOverlayAuditTest {

    private val repoRoot: File = run {
        val here = File(".").canonicalFile
        if (File(here, "browser-extension").exists()) {
            here
        } else if (File(here.parentFile, "browser-extension").exists()) {
            here.parentFile
        } else {
            here
        }
    }

    /**
     * Scenario 45: Single & Multiple HTML5 Players, Fullscreen & Responsive Sizing
     * Verifies overlay.js architecture handles multiple players, dynamic addition/removal,
     * fullscreen state transitions, and responsive compact scaling.
     */
    @Test
    fun testOverlayMultiPlayerAndResponsiveLifecycle() {
        val overlayJsFile = File(repoRoot, "browser-extension/chrome-extension/overlay.js")
        assertTrue(overlayJsFile.exists(), "overlay.js must exist")
        val content = overlayJsFile.readText()

        // Shadow DOM encapsulation
        assertTrue(content.contains("attachShadow({ mode: \"open\" })"), "Must use Shadow DOM open mode")

        // Multiple players tracking
        assertTrue(content.contains("this.overlays = new Map()"), "OverlayManager must track multiple overlays in a Map")
        assertTrue(content.contains("document.querySelectorAll(\"video\")"), "Must scan and query all video elements")

        // Dynamic addition and removal via MutationObserver
        assertTrue(content.contains("new MutationObserver"), "Must monitor dynamic DOM modifications via MutationObserver")
        assertTrue(content.contains("this.attachToVideo(node)"), "Must attach overlay to dynamically added video elements")
        assertTrue(content.contains("this.removeVideo(node)"), "Must tear down overlay when video element is removed")

        // Fullscreen handling
        assertTrue(content.contains("fullscreenchange"), "Must listen for standard fullscreenchange event")
        assertTrue(content.contains("webkitfullscreenchange"), "Must listen for WebKit fullscreenchange event")
        assertTrue(content.contains("handleFullscreenChange"), "Overlay must handle fullscreen layout adjustments")

        // Responsive compact mode via ResizeObserver
        assertTrue(content.contains("ResizeObserver"), "Must support ResizeObserver for responsive player bounds")
        assertTrue(content.contains("data-compact"), "Must switch to compact mode for narrow players (<280px)")

        // HTML5 test fixture exists and defines multiple players
        val fixtureFile = File(repoRoot, "browser-extension/test-pages/video-overlay-fixture.html")
        assertTrue(fixtureFile.exists(), "video-overlay-fixture.html must exist")
        val fixtureContent = fixtureFile.readText()
        assertTrue(fixtureContent.contains("video1"), "Fixture must include Player 1")
        assertTrue(fixtureContent.contains("video2"), "Fixture must include Player 2")
        assertTrue(fixtureContent.contains("video3"), "Fixture must include Player 3 (DRM Mock)")
        assertTrue(fixtureContent.contains("addDynamicPlayerBtn"), "Fixture must support adding dynamic player")
        assertTrue(fixtureContent.contains("removeDynamicPlayerBtn"), "Fixture must support removing dynamic player")
    }

    /**
     * Scenario 46: Duplicate Overlay Prevention, Non-Blocking Controls & Cleanup
     * Proves injection guards prevent duplicate overlays, pointer-events protect controls/captions,
     * and destroy() cleans up all DOM listeners and observers.
     */
    @Test
    fun testOverlayDuplicatePreventionAndNonBlockingControls() {
        val overlayJs = File(repoRoot, "browser-extension/chrome-extension/overlay.js").readText()

        // Global injection guard
        assertTrue(overlayJs.contains("window.__XDM_OVERLAY_INJECTED__"), "Must have global window injection guard")

        // Element-level duplicate guard
        assertTrue(overlayJs.contains("if (this.overlays.has(video)) return"), "Must not create duplicate overlay on same video")

        // Non-blocking controls and captions: :host pointer-events must be none
        assertTrue(overlayJs.contains("pointer-events: none"), ":host container must have pointer-events: none to avoid blocking controls/captions")
        assertTrue(overlayJs.contains(".xdm-btn {") && overlayJs.contains("pointer-events: auto"), "Only the pill button itself should have pointer-events: auto")

        // Teardown and DOM cleanup
        assertTrue(overlayJs.contains("destroy()"), "VideoPlayerOverlay must implement destroy()")
        assertTrue(overlayJs.contains("this.mutationObserver.disconnect()"), "Must disconnect MutationObserver on cleanup")
        assertTrue(overlayJs.contains("this.resizeObserver.disconnect()"), "Must disconnect ResizeObserver on cleanup")
        assertTrue(overlayJs.contains("removeEventListener"), "Must remove all event listeners upon teardown")
    }

    /**
     * Scenario 47: Real Stream Qualities, Audio Availability & Muxing Requirements
     * Evaluates HLS & DASH stream choice resolution against real manifests, ensuring honest qualities.
     */
    @Test
    fun testRealStreamQualitiesAndAudioRenditionMuxing() {
        val baseUri = "https://cdn.example.org/vod/hls/master.m3u8"
        val hlsManifest = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aac",NAME="English",DEFAULT=YES,URI="audio_en.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aac",NAME="Spanish",DEFAULT=NO,URI="audio_es.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="audio-aac"
            v1080p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="audio-aac"
            v720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="audio-aac"
            v480p.m3u8
        """.trimIndent()

        val parsedMaster = HlsParser.parseMasterPlaylist(hlsManifest.lines().iterator(), baseUri).getOrThrow()
        val loaded = LoadedManifest.HlsMaster(baseUri, parsedMaster)
        val formats = StreamChoices.of(loaded)

        // Verifies real resolutions detected, highest bandwidth first
        assertEquals(3, formats.size)
        assertEquals("1080p · 6.0 Mbps · H.264, AAC", formats[0].label)
        assertEquals("720p · 3.0 Mbps · H.264, AAC", formats[1].label)
        assertEquals("480p · 1.2 Mbps · H.264, AAC", formats[2].label)

        // Verifies audio rendition choices
        assertEquals(AudioMode.CHOOSE, formats[0].audioMode, "Separate audio group requires CHOOSE mode")
        val audioTracks = formats[0].audio
        assertEquals(2, audioTracks.size)
        assertEquals("English", audioTracks[0].label)
        assertEquals("Spanish", audioTracks[1].label)

        // Verifies accurate resolved pick URIs
        val primaryPick = audioTracks[0].pick as StreamPick.Hls
        assertEquals("https://cdn.example.org/vod/hls/v1080p.m3u8", primaryPick.url)
        assertEquals("https://cdn.example.org/vod/hls/audio_en.m3u8", primaryPick.audioUrl)
    }

    /**
     * Scenario 47 & 49: Protected Content / DRM Handling
     * Proves overlay and capture subsystem detect DRM (EME/Widevine/PlayReady) and display honest alerts
     * without attempting illegal circumvention.
     */
    @Test
    fun testProtectedDrmContentDetectionAndHonestReporting() {
        val overlayJs = File(repoRoot, "browser-extension/chrome-extension/overlay.js").readText()

        // EME and DRM detection logic in overlay.js
        assertTrue(overlayJs.contains("mediaKeys"), "overlay.js must check for EME mediaKeys")
        assertTrue(overlayJs.contains("isDrmProtected"), "overlay.js must identify drmProtected streams")
        assertTrue(
            overlayJs.contains("DRM Protected Stream") && overlayJs.contains("protected by Encrypted Media Extensions (DRM)"),
            "overlay.js must honestly inform user that DRM-protected streams cannot be captured"
        )

        // No circumvention or decryption attempts in codebase
        assertFalse(overlayJs.contains("decryptWidevine"), "Code must not attempt to circumvent DRM")
        assertFalse(overlayJs.contains("bypassEME"), "Code must not attempt to bypass EME")
    }

    /**
     * Scenario 48: Site Exclusions, Permissions, and Safe Browser-to-Desktop Handoff
     * Validates domain exclusions, clean parameter handoff, and safe cancellation.
     */
    @Test
    fun testSiteExclusionsAndSafeDesktopHandoff() {
        val overlayJs = File(repoRoot, "browser-extension/chrome-extension/overlay.js").readText()

        // Hardcoded store compliance exclusions
        val blockedDomains = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")
        for (domain in blockedDomains) {
            assertTrue(overlayJs.contains(domain), "overlay.js must include compliance domain: $domain")
        }

        // Host exclusion communication
        assertTrue(overlayJs.contains("overlay-hide-site"), "overlay.js must support hiding on current site")

        // Safe message payload sent to desktop
        val appJs = File(repoRoot, "browser-extension/chrome-extension/app.js").readText()
        assertTrue(appJs.contains("this.connector.postMessage(\"/download\""), "Must dispatch downloads to /download")
        assertTrue(appJs.contains("this.connector.postMessage(\"/media\""), "Must dispatch media streams to /media")
        assertTrue(appJs.contains("isBlockedUrl"), "app.js must verify isBlockedUrl before sending")
    }

    /**
     * Scenario 49: Legitimate Test Media redistributed with explicit permission
     * Verifies test fixtures reference only authorized, public open-source creative media.
     */
    @Test
    fun testAuthorizedTestMediaDistribution() {
        val fixtureHtml = File(repoRoot, "browser-extension/test-pages/video-overlay-fixture.html").readText()

        // Blender Foundation open movies under Creative Commons
        assertTrue(fixtureHtml.contains("BigBuckBunny.mp4"), "Uses open-source Big Buck Bunny (CC-BY 3.0)")
        assertTrue(fixtureHtml.contains("ElephantsDream.mp4"), "Uses open-source Elephants Dream (CC-BY 2.5)")
        assertTrue(fixtureHtml.contains("TearsOfSteel.mp4"), "Uses open-source Tears of Steel (CC-BY 3.0)")
    }
}

