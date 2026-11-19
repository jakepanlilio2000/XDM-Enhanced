package xdm.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.core.downloaders.web.ProgressTracker

class ProgressTrackerAdjustmentTest {

    @Test
    fun testProgressTrackerResumeTimingReset() {
        val tracker = ProgressTracker(singleFile = true)
        val initialStart = tracker.ticksAtDownloadStartOrResume

        // Simulate passage of time during a pause (e.g. 100 seconds ago)
        tracker.ticksAtDownloadStartOrResume = initialStart - 100_000L
        tracker.downloadedBytesSinceStartOrResume = 50_000L
        tracker.speedHistory.add(10_000f)
        tracker.speedSum = 10_000.0

        // Call resetResumeTiming as done upon download resume
        tracker.resetResumeTiming()

        assertTrue(tracker.ticksAtDownloadStartOrResume >= initialStart, "ticksAtDownloadStartOrResume must be updated to current time")
        assertEquals(0L, tracker.downloadedBytesSinceStartOrResume, "downloadedBytesSinceStartOrResume must be reset to 0")
        assertEquals(0, tracker.speedHistory.size, "speedHistory must be cleared on resume")
        assertEquals(0.0, tracker.speedSum, "speedSum must be reset to 0.0")
    }
}

