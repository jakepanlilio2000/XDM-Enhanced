package xdm.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.core.downloaders.web.ProgressTracker
import xdm.core.downloaders.web.http.Chunk
import xdm.core.downloaders.web.http.ChunkStatus
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class PhaseM2FastTransferRateTest {

    private fun createDummyChunk(id: Long, offset: Long = 0, length: Long = 10_000_000): Chunk {
        return Chunk(
            id = id,
            offset = offset,
            downloaded = AtomicLong(0),
            length = AtomicLong(length),
            status = AtomicReference(ChunkStatus.Downloading),
            fileHandle = AtomicReference(null),
            lastTakeOver = AtomicLong(0)
        )
    }

    @Test
    fun testSamplingIntervalAtApprox10Ms() {
        val tracker = ProgressTracker(singleFile = true)
        val chunks = mapOf(1L to createDummyChunk(1L))

        // Simulate 20 updates in rapid bursts (e.g. every 2-3 ms)
        val startTime = System.currentTimeMillis()
        for (i in 1..20) {
            Thread.sleep(3)
            tracker.update(downloadedBytes = 10_000L, totalSize = 10_000_000L, chunks = chunks)
        }
        val elapsed = System.currentTimeMillis() - startTime

        // Over ~60 ms with 20 updates, sampling at 10 ms should yield around 5 to 8 samples in the buffer
        val sampleCount = tracker.sampleCount()
        assertTrue(sampleCount in 3..12, "Samples count ($sampleCount) must reflect ~10 ms sampling cadence over ${elapsed}ms")

        // Effective window span should be within the ~400 ms rolling window
        val windowSpanMs = tracker.effectiveWindowSpanMs()
        assertTrue(windowSpanMs <= 450.0, "Rolling window span ($windowSpanMs ms) must be bounded by window duration")
    }

    @Test
    fun testRollingWindowThroughputAccuracy() {
        val tracker = ProgressTracker(singleFile = true)
        val chunks = mapOf(1L to createDummyChunk(1L))

        // Target: 1 MB/s = 10,000 bytes every 10 ms
        val stepBytes = 10_000L
        for (i in 1..40) {
            Thread.sleep(10)
            tracker.update(downloadedBytes = stepBytes, totalSize = 10_000_000L, chunks = chunks)
        }

        val speed = tracker.downloadSpeed
        // Expected ~1,000,000 B/s (allow ±30% due to thread sleep jitter on Windows)
        assertTrue(speed in 650_000f..1_450_000f, "Measured speed ($speed B/s) must accurately reflect ~1 MB/s throughput")
        assertTrue(tracker.totalDownloadedBytes == 400_000L, "Total downloaded bytes must equal 400,000")
    }

    @Test
    fun testStallAndZeroActivityDecay() {
        val tracker = ProgressTracker(singleFile = true)
        val chunks = mapOf(1L to createDummyChunk(1L))

        // Send active traffic
        for (i in 1..10) {
            Thread.sleep(10)
            tracker.update(downloadedBytes = 50_000L, totalSize = 10_000_000L, chunks = chunks)
        }
        assertTrue(tracker.downloadSpeed > 0f, "Speed must be non-zero during active transfer")

        // Sleep past stall threshold (500 ms)
        Thread.sleep(550)

        // Stalled state check
        val isStalled = tracker.checkStallDecay()
        assertTrue(isStalled, "Must detect stall after 550 ms without bytes")
        assertEquals(0f, tracker.downloadSpeed, "Stalled transfer must decay speed to 0 B/s")

        // Calling update with 0 bytes while stalled must retain 0 B/s
        tracker.update(downloadedBytes = 0L, totalSize = 10_000_000L, chunks = chunks)
        assertEquals(0f, tracker.downloadSpeed, "Zero-activity update must report 0 B/s")
    }

    @Test
    fun testPauseResumeSpeedReset() {
        val tracker = ProgressTracker(singleFile = true)
        val chunks = mapOf(1L to createDummyChunk(1L))

        for (i in 1..10) {
            Thread.sleep(10)
            tracker.update(downloadedBytes = 50_000L, totalSize = 10_000_000L, chunks = chunks)
        }
        assertTrue(tracker.downloadSpeed > 0f)

        // Pause event: invoke resetResumeTiming
        tracker.resetResumeTiming()

        assertEquals(0f, tracker.downloadSpeed, "Speed must immediately drop to 0 on pause")
        assertEquals(0L, tracker.eta, "ETA must be 0 on pause")
        assertEquals(0, tracker.sampleCount(), "Samples must be cleared on pause")

        // Simulate idle pause duration of 200 ms
        Thread.sleep(200)

        // Resume with new data
        tracker.update(downloadedBytes = 50_000L, totalSize = 10_000_000L, chunks = chunks)
        // Ensure paused time does not crush the speed to near-zero
        assertTrue(tracker.downloadSpeed > 0f, "Resumed download must calculate fresh throughput without pause time distortion")
    }

    @Test
    fun testDispatchCadence33Ms() {
        val tracker = ProgressTracker(singleFile = true)
        tracker.dispatchIntervalMs = 33L // Default ~30 fps visual cadence
        val chunks = mapOf(1L to createDummyChunk(1L))

        var dispatchCount = 0
        val totalCalls = 60
        for (i in 1..totalCalls) {
            Thread.sleep(5) // 5 ms interval (total ~300 ms)
            val shouldDispatch = tracker.update(downloadedBytes = 5_000L, totalSize = 10_000_000L, chunks = chunks)
            if (shouldDispatch) {
                dispatchCount++
            }
        }

        // Over ~300 ms, a 33 ms cadence should dispatch ~7 to 11 times, NOT 60 times!
        assertTrue(dispatchCount in 5..15, "Dispatch count ($dispatchCount) must match ~33 ms cadence over 300 ms, protecting EventQueue")
    }

    @Test
    fun testNeverDivideByAssumedTickDuration() {
        val tracker = ProgressTracker(singleFile = true)
        val chunks = mapOf(1L to createDummyChunk(1L))

        // Feed updates with irregular, varying time gaps
        Thread.sleep(15)
        tracker.update(downloadedBytes = 20_000L, totalSize = 10_000_000L, chunks = chunks)
        Thread.sleep(35)
        tracker.update(downloadedBytes = 50_000L, totalSize = 10_000_000L, chunks = chunks)
        Thread.sleep(10)
        tracker.update(downloadedBytes = 15_000L, totalSize = 10_000_000L, chunks = chunks)

        // The speed calculation must be non-zero and stable despite irregular intervals
        assertTrue(tracker.downloadSpeed > 0f)
        assertFalse(tracker.downloadSpeed.isNaN(), "Speed must never be NaN")
        assertFalse(tracker.downloadSpeed.isInfinite(), "Speed must never be Infinite")
    }
}
