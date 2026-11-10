package xdm.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.core.downloaders.DashDownloadTaskInfo
import xdm.core.downloaders.HlsDownloadTaskInfo
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.web.streaming.manifest.dash.DashSegment
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * A tab that reloads the same page is no longer cleared ([ClearTabFilter]), so the tracker drops
 * duplicate captures instead, keeping the newest.
 */
class CaptureDedupeTest {
    @BeforeEach
    fun setup() {
        FakeCaptureHost.install()
    }

    @Test
    fun clearTab_onlyWhenThePageChanges() {
        val filter = ClearTabFilter()
        assertTrue(filter.shouldClear("1", "https://site.example/"), "first load")
        repeat(20) { assertFalse(filter.shouldClear("1", "https://site.example/"), "iframe storm on the same page") }
        assertTrue(filter.shouldClear("1", "https://site.example/watch/9"), "another page")
        assertTrue(filter.shouldClear("2", "https://site.example/"), "tabs are tracked apart")
        assertTrue(filter.shouldClear("1", null), "no URL: clear as before")
        assertTrue(filter.shouldClear("1", "https://site.example/"), "back to the first page")
    }

    @Test
    fun clearTab_forgetsOldestTabs() {
        val filter = ClearTabFilter(maxTabs = 2)
        filter.shouldClear("1", "a"); filter.shouldClear("2", "b"); filter.shouldClear("3", "c")
        assertTrue(filter.shouldClear("1", "a"), "evicted tab counts as new")
    }

    private fun display(tab: String) = StreamingVideoDisplayInfo(tabId = tab)

    private fun http(id: Long, url: String) = HttpDownloadTaskInfo(
        id = id, url = url, fileName = "f$id.mp4", respectFileName = true, cookie = null, headers = null,
        origin = null, categoryId = null, targetFolder = "/tmp", maxPiece = 1, authInfo = null, knownFileSize = null,
    )

    private fun hls(id: Long, url: String, audio: String?) = HlsDownloadTaskInfo(
        id = id, fileName = "h$id.mp4", tempDir = "/tmp", respectFileName = true, cookie = null, headers = null,
        origin = null, categoryId = null, targetFolder = "/tmp", maxPiece = 1, authInfo = null,
        url = url, audioUrl = audio, independent = false,
    )

    private fun dash(id: Long, video: String, audio: String) = DashDownloadTaskInfo(
        id = id, fileName = "d$id.mp4", tempDir = "/tmp", respectFileName = true, cookie = null, headers = null,
        origin = null, categoryId = null, targetFolder = "/tmp", maxPiece = 1, authInfo = null,
        videoSegments = listOf(DashSegment(video.toHttpUrl(), 0L to 100L)), audioSegments = listOf(DashSegment(audio.toHttpUrl())),
        url = "https://h/manifest.mpd", audioMime = "audio/mp4", videoMime = "video/mp4",
    )

    @Test
    fun http_sameUrlSameTab_latestKept() {
        val t = CapturedVideoTracker()
        t.addVideoHttp(listOf(http(1, "https://h/a.mp4") to display("7")))
        t.addVideoHttp(listOf(http(2, "https://h/a.mp4") to display("7")))
        assertEquals(listOf(2L), t.videoList.map { it.id }, "the newer capture replaces the older")
    }

    @Test
    fun sameUrlInAnotherTab_kept() {
        val t = CapturedVideoTracker()
        t.addVideoHttp(listOf(http(1, "https://h/a.mp4") to display("7")))
        t.addVideoHttp(listOf(http(2, "https://h/a.mp4") to display("8")))
        assertEquals(setOf(1L, 2L), t.videoList.map { it.id }.toSet(), "one per tab")
    }

    @Test
    fun hls_urlPair() {
        val t = CapturedVideoTracker()
        t.addVideoHls(listOf(hls(1, "https://h/v720.m3u8", "https://h/aud.m3u8") to display("7")))
        t.addVideoHls(listOf(hls(2, "https://h/v1080.m3u8", "https://h/aud.m3u8") to display("7")))
        assertEquals(2, t.videoList.size, "different variant, same audio: both kept")
        t.addVideoHls(listOf(hls(3, "https://h/v720.m3u8", "https://h/aud.m3u8") to display("7")))
        assertEquals(setOf(2L, 3L), t.videoList.map { it.id }.toSet(), "same pair: the newest replaces it")
    }

    @Test
    fun dash_videoAudioPair() {
        val t = CapturedVideoTracker()
        t.addVideoDash(listOf(dash(1, "https://c/v.mp4", "https://c/a.mp4") to display("7")))
        t.addVideoDash(listOf(dash(2, "https://c/v.mp4", "https://c/a2.mp4") to display("7")))
        assertEquals(2, t.videoList.size, "different audio: both kept")
        t.addVideoDash(listOf(dash(3, "https://c/v.mp4", "https://c/a.mp4") to display("7")))
        assertEquals(setOf(2L, 3L), t.videoList.map { it.id }.toSet(), "same pair: the newest replaces it")
        assertEquals("https://c/a.mp4", t.getDashVideo(3)!!.audioSegments.first().toString(), "newest entry is the one kept")
    }

    @Test
    fun sameId_replacedInPlace() {
        val t = CapturedVideoTracker()
        t.addVideoDash(listOf(dash(1, "https://c/v480.mp4", "https://c/a.mp4") to display("7")))
        t.addVideoDash(listOf(dash(1, "https://c/v1080.mp4", "https://c/a.mp4") to display("7")))
        assertEquals(listOf(1L), t.videoList.map { it.id }, "an upgrade keeps its id")
        assertEquals("https://c/v1080.mp4", t.getDashVideo(1)!!.videoSegments.first().toString(), "upgraded")
    }

    @Test
    fun metadataEnrichment_andFormatIntegrity() {
        val t = CapturedVideoTracker()

        val dashItem = dash(10, "https://c/v1080.mp4", "https://c/a.mp4")
        val dashDisplay = StreamingVideoDisplayInfo(tabId = "1", height = 1080)
        t.addVideoDash(listOf(dashItem to dashDisplay))

        val hlsItem = hls(20, "https://h/v720.m3u8", "https://h/a.m3u8")
        val hlsDisplay = StreamingVideoDisplayInfo(tabId = "1", height = 720)
        t.addVideoHls(listOf(hlsItem to hlsDisplay))

        val httpItem = http(30, "https://cdn/sample.mp4")
        val httpDisplay = StreamingVideoDisplayInfo(tabId = "1", height = 1080, size = 10485760L)
        t.addVideoHttp(listOf(httpItem to httpDisplay))

        val list = t.videoList
        assertEquals(3, list.size)

        val dashInfo = list.first { it.id == 10L }
        assertEquals("DASH", dashInfo.streamType)
        assertEquals("DASH (Muxed)", dashInfo.format, "DASH with audio and video segments must be DASH (Muxed)")
        assertFalse(dashInfo.format.equals("MP4", ignoreCase = true), "DASH must never be labeled MP4")
        assertEquals("1080p", dashInfo.resolution)
        assertTrue(dashInfo.hasAudio)

        val hlsInfo = list.first { it.id == 20L }
        assertEquals("HLS", hlsInfo.streamType)
        assertEquals("HLS (Muxed)", hlsInfo.format, "HLS with audio URL must be HLS (Muxed)")
        assertFalse(hlsInfo.format.equals("MP4", ignoreCase = true), "HLS must never be labeled MP4")
        assertEquals("720p", hlsInfo.resolution)
        assertTrue(hlsInfo.hasAudio)

        val httpInfo = list.first { it.id == 30L }
        assertEquals("HTTP", httpInfo.streamType)
        assertEquals("MP4", httpInfo.format)
        assertEquals("1080p", httpInfo.resolution)
        assertTrue(httpInfo.hasAudio)
    }

    @Test
    fun addVideoDownload_validation() {
        val t = CapturedVideoTracker()
        val httpItem = http(100, "https://cdn/sample.mp4")
        t.addVideoHttp(listOf(httpItem to display("tab1")))

        // Valid registered ID must succeed
        assertTrue(t.addVideoDownload(100L), "Valid capture ID must return true")

        // Unknown or expired ID must fail safely
        assertFalse(t.addVideoDownload(99999L), "Unknown/expired capture ID must return false")
    }
}
