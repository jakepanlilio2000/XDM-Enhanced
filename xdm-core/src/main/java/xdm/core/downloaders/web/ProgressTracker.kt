// Copyright 2026 Subhra Das Gupta
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-XDM-Android-Official-Store-Builds
// Additional terms and the exception: xdm-android/NOTICE

package xdm.core.downloaders.web

import xdm.core.downloaders.web.http.Chunk
import xdm.core.downloaders.web.http.ChunkStatus
import xdm.core.downloaders.web.streaming.downloader.StreamingChunk
import xdm.core.util.FormatHelper.getEtaAsSec
import java.util.*

data class SegmentProgress(
    var start: Long = 0,
    var length: Long = 0,
    var downloaded: Long = 0,
    val id: Long = 0,
    var status: String = "Downloading",
    var speed: Float = 0f,
)

data class ByteSample(val nanoTime: Long, val totalBytes: Long)

class ProgressTracker(val singleFile: Boolean) {
    companion object {
        const val SAMPLING_INTERVAL_NS = 10_000_000L      // 10 ms sampling target
        const val WINDOW_DURATION_NS = 400_000_000L       // 400 ms rolling throughput window
        const val DEFAULT_DISPATCH_INTERVAL_MS = 33L      // ~30 fps visual cadence (within 16-50 ms safe range)
        const val STALL_TIMEOUT_NS = 500_000_000L         // 500 ms idle threshold for zero rate
        private const val STREAM_SLOT = 1000L
    }

    var init = false
    var totalDownloadedBytes: Long = 0
    var lastDownloadedBytes: Long = 0
    val speedHistory = LinkedList<Float>()
    var speedSum = 0.0
    var downloadedBytesSinceStartOrResume: Long = 0
    var ticksAtDownloadStartOrResume = System.currentTimeMillis()
    var lastProgressUpdatedAt: Long = 0
    var lastChunkStatsUpdated: Long = 0
    var progress = 0
    var downloadSpeed = 0f
    var eta: Long = 0
    var totalSize: Long? = null

    private val samples = ArrayDeque<ByteSample>()
    var startNanoTime = System.nanoTime()
    var lastByteArrivalNano = System.nanoTime()
    var lastChunkStatsUpdatedNano = 0L
    var dispatchIntervalMs: Long = DEFAULT_DISPATCH_INTERVAL_MS

    /**
     * Resets timing references and throughput counters when resuming a paused download
     * so that idle/paused duration does not artificially collapse average speed and ETA.
     */
    @Synchronized
    fun resetResumeTiming() {
        val now = System.currentTimeMillis()
        val nowNano = System.nanoTime()
        this.ticksAtDownloadStartOrResume = now
        this.startNanoTime = nowNano
        this.lastByteArrivalNano = nowNano
        this.downloadedBytesSinceStartOrResume = 0
        this.lastProgressUpdatedAt = 0
        this.lastChunkStatsUpdated = 0
        this.lastChunkStatsUpdatedNano = 0L
        this.speedHistory.clear()
        this.speedSum = 0.0
        this.samples.clear()
        this.downloadSpeed = 0f
        this.eta = 0L
    }

    @Synchronized
    fun sampleCount(): Int = samples.size

    @Synchronized
    fun effectiveWindowSpanMs(): Double =
        if (samples.size >= 2) (samples.last().nanoTime - samples.first().nanoTime) / 1_000_000.0 else 0.0

    @Synchronized
    fun checkStallDecay(nowNano: Long = System.nanoTime()): Boolean {
        if (nowNano - lastByteArrivalNano > STALL_TIMEOUT_NS) {
            downloadSpeed = 0f
            return true
        }
        return false
    }

    /**
     * Streaming only: the bytes the chunks hold, finished or partial. Unlike [totalDownloadedBytes]
     * (counted since this tracker was made) it includes what was downloaded before a resume.
     */
    var streamDownloadedBytes: Long = 0
    private val offsetComparator = compareBy<SegmentProgress> { it.start }
    private val segments = ArrayList<SegmentProgress>()
    private val sortedSegments = sortedSetOf(offsetComparator)

    /**
     * A snapshot for the progress window, which paints it on the EDT while the download threads keep
     * updating the live segments under this tracker's lock.
     */
    val segmentData: Collection<SegmentProgress>
        @Synchronized get() = (if (singleFile) sortedSegments else segments).map { it.copy() }

    /**
     * The download is done (about to assemble, or complete): takes the pieces' final state, which the
     * periodic updates may not have caught, so the final progress snapshot shows every piece finished.
     */
    @Synchronized
    fun finish(chunks: Map<Long, Chunk>) {
        updateChunkProgressDataSingleFile(chunks)
    }

    /** Streaming: captures the final chunk state before assembly. */
    @Synchronized
    fun finish(chunks: List<StreamingChunk>) {
        updateChunkProgressData(chunks)
    }

    /**
     * [prg] (percent) and [remainingBytes] are asked for on update ticks.
     * Throughput is derived from low-cost byte counter samples with monotonic timestamps
     * over a 400 ms rolling window, avoiding assumed exact tick duration divisions.
     */
    private fun update(
        ticks: Long,
        ticksElapsed: Long,
        downloadedBytes: Long,
        totalSize: Long?,
        prg: () -> Float?,
        remainingBytes: () -> Long?,
    ) {
        val nowNano = System.nanoTime()
        this.totalSize = totalSize
        this.totalDownloadedBytes += downloadedBytes
        this.downloadedBytesSinceStartOrResume += downloadedBytes

        if (downloadedBytes > 0) {
            this.lastByteArrivalNano = nowNano
        }

        // Low-cost byte counter sampling (~10 ms granularity)
        if (samples.isEmpty()) {
            samples.add(ByteSample(nowNano, this.totalDownloadedBytes))
        } else if (nowNano - samples.last().nanoTime >= SAMPLING_INTERVAL_NS) {
            samples.add(ByteSample(nowNano, this.totalDownloadedBytes))
        }

        // Prune samples older than the rolling window duration (400 ms)
        while (samples.size > 2 && nowNano - samples.first().nanoTime > WINDOW_DURATION_NS) {
            samples.removeFirst()
        }

        // Monotonic time-based rolling throughput estimate (never divide by an assumed exact tick duration)
        val firstSample = samples.first()
        val lastSample = samples.last()
        val deltaNano = lastSample.nanoTime - firstSample.nanoTime
        val deltaBytes = lastSample.totalBytes - firstSample.totalBytes

        val isStalled = (nowNano - lastByteArrivalNano > STALL_TIMEOUT_NS)
        val instantThroughput = if (!isStalled && deltaNano >= 20_000_000L && deltaBytes >= 0) {
            (deltaBytes.toDouble() * 1_000_000_000.0 / deltaNano).toFloat()
        } else if (isStalled) {
            0f
        } else {
            // Under 20 ms or initial sample: derive from session elapsed monotonic time
            val totalNano = nowNano - startNanoTime
            if (totalNano > 0 && downloadedBytesSinceStartOrResume > 0) {
                (downloadedBytesSinceStartOrResume.toDouble() * 1_000_000_000.0 / totalNano).toFloat()
            } else {
                this.downloadSpeed
            }
        }

        this.downloadSpeed = instantThroughput
        this.lastProgressUpdatedAt = ticks

        // Backward-compatibility support for speedHistory
        if (ticksElapsed > 0) {
            if (speedHistory.size > 8) {
                this.speedSum -= speedHistory.removeFirst()
            }
            speedHistory.add(instantThroughput)
            speedSum += instantThroughput
        }

        // Progress percentage
        val percent = prg() ?: return
        this.progress = percent.toInt()

        // Derive ETA from smoothed speed (weighted 70% rolling window, 30% session average)
        val remaining = remainingBytes()
        val totalNano = nowNano - startNanoTime
        val avgSpeed = if (totalNano > 0) (downloadedBytesSinceStartOrResume.toDouble() * 1_000_000_000.0 / totalNano).toFloat() else 0f
        val smoothedSpeed = if (instantThroughput > 0) (0.7f * instantThroughput + 0.3f * avgSpeed) else avgSpeed

        if (remaining != null) {
            if (smoothedSpeed > 0) {
                this.eta = getEtaAsSec(remaining.coerceAtLeast(0).toDouble(), smoothedSpeed)
            } else {
                this.eta = 0L
            }
        } else if (percent > 0 && ticksElapsed > 0) {
            this.eta = (ticksElapsed * (100 - percent) / (percent * 1000)).toLong()
        } else {
            this.eta = 0L
        }
    }

    @Synchronized
    fun update(downloadedBytes: Long, totalSize: Long?, chunks: Map<Long, Chunk>): Boolean {
        val prg = if (totalSize != null) (totalDownloadedBytes + downloadedBytes) * 100.0f / totalSize else null
        val ticks = System.currentTimeMillis()
        val ticksElapsed = ticks - this.ticksAtDownloadStartOrResume
        val nowNano = System.nanoTime()
        val dispatchNs = dispatchIntervalMs * 1_000_000L

        update(ticks, ticksElapsed, downloadedBytes, totalSize, { prg }, { totalSize?.minus(totalDownloadedBytes) })

        if (nowNano - this.lastChunkStatsUpdatedNano >= dispatchNs && ticksElapsed > 0) {
            updateChunkProgressDataSingleFile(chunks)
            this.lastChunkStatsUpdatedNano = nowNano
            this.lastChunkStatsUpdated = ticks
            return true
        }
        return false
    }

    /**
     * Streaming: progress by bytes when every chunk's size is known (byte-range segments, or each one
     * already answered), else by finished chunks. The ETA counts the bytes left, estimating unknown
     * chunk sizes from the known ones. [totalSize] wins when the caller knows it.
     */
    @Synchronized
    fun update(downloadedBytes: Long, totalSize: Long?, chunks: List<StreamingChunk>, completed: Int): Boolean {
        val ticks = System.currentTimeMillis()
        val ticksElapsed = ticks - this.ticksAtDownloadStartOrResume
        val nowNano = System.nanoTime()
        val dispatchNs = dispatchIntervalMs * 1_000_000L
        var size: StreamSize? = null
        fun size() = size ?: streamSize(chunks, totalSize).also {
            size = it
            streamDownloadedBytes = chunks.sumOf { c -> c.downloaded.get() }
        }
        update(
            ticks, ticksElapsed, downloadedBytes, totalSize,
            prg = {
                val s = size()
                if (s.exact && s.bytes > 0) (streamDownloadedBytes * 100.0f / s.bytes).coerceAtMost(100f)
                else completed * 100.0f / chunks.size
            },
            remainingBytes = { size().takeIf { it.bytes > 0 }?.let { it.bytes - streamDownloadedBytes } },
        )
        if (nowNano - this.lastChunkStatsUpdatedNano >= dispatchNs && ticksElapsed > 0) {
            updateChunkProgressData(chunks)
            this.lastChunkStatsUpdatedNano = nowNano
            this.lastChunkStatsUpdated = ticks
            return true
        }
        return false
    }

    /**
     * Speed, ETA and [progress] only, for a task that builds its own segment data. Returns true when
     * the caller should report progress according to the configured dispatch cadence.
     */
    @Synchronized
    fun update(downloadedBytes: Long, totalSize: Long?, progress: Float): Boolean {
        val ticks = System.currentTimeMillis()
        val ticksElapsed = ticks - this.ticksAtDownloadStartOrResume
        val nowNano = System.nanoTime()
        val dispatchNs = dispatchIntervalMs * 1_000_000L
        update(ticks, ticksElapsed, downloadedBytes, totalSize, { progress }, { totalSize?.minus(totalDownloadedBytes) })
        if (nowNano - this.lastChunkStatsUpdatedNano >= dispatchNs && ticksElapsed > 0) {
            this.lastChunkStatsUpdatedNano = nowNano
            this.lastChunkStatsUpdated = ticks
            return true
        }
        return false
    }

    /** A stream's size in bytes; [exact] when every chunk's size is known, else estimated (0: no idea yet). */
    private class StreamSize(val bytes: Long, val exact: Boolean)

    /**
     * Sums the chunk sizes that are known (from the response, or the segment's byte range) and gives
     * each unknown chunk the average of the known ones.
     */
    private fun streamSize(chunks: List<StreamingChunk>, totalSize: Long?): StreamSize {
        if (totalSize != null && totalSize > 0) return StreamSize(totalSize, true)
        var known = 0L
        var knownCount = 0
        for (c in chunks) {
            val len = c.length.get().takeIf { it > 0 } ?: c.byteRange?.second?.takeIf { it > 0 } ?: continue
            known += len
            knownCount++
        }
        return when (knownCount) {
            0 -> StreamSize(0, false)
            chunks.size -> StreamSize(known, true)
            else -> StreamSize(known + known / knownCount * (chunks.size - knownCount), false)
        }
    }

    private fun updateChunkProgressDataSingleFile(chunks: Map<Long, Chunk>) {
        while (true) {
            var segToRemove: SegmentProgress? = null
            for (seg in sortedSegments) {
                if (!chunks.containsKey(seg.id)) {
                    segToRemove = seg
                    break
                }
            }
            if (segToRemove == null) {
                break
            } else {
                sortedSegments.remove(segToRemove)
            }
        }
        for (chunk in chunks.values) {
            val seg = sortedSegments.find { it.id == chunk.id }
            val statusStr = statusLabel(chunk.status.get())
            if (seg == null) {
                sortedSegments.add(
                    SegmentProgress(
                        id = chunk.id,
                        start = chunk.offset,
                        downloaded = chunk.downloaded.get(),
                        length = chunk.length.get(),
                        status = statusStr,
                    )
                )
            } else {
                seg.start = chunk.offset
                seg.length = chunk.length.get()
                seg.downloaded = chunk.downloaded.get()
                seg.status = statusStr
            }
        }
    }

    /**
     * Streaming chunks are separate files whose sizes are mostly unknown up front, so each one gets an
     * equal slot of [STREAM_SLOT] units on the bar, filled by the fraction of it downloaded.
     */
    private fun updateChunkProgressData(chunks: List<StreamingChunk>) {
        while (segments.size < chunks.size) {
            segments.add(SegmentProgress(id = segments.size.toLong()))
        }
        // The chunk list can get shorter (a fresh playlist on resume): never report segments it no longer has.
        while (segments.size > chunks.size) {
            segments.removeAt(segments.size - 1)
        }
        for ((index, chunk) in chunks.withIndex()) {
            val len = chunk.length.get()
            val chunkStatus = chunk.status.get()
            val fraction = when {
                chunkStatus == ChunkStatus.Finished -> 1.0
                len > 0 -> (chunk.downloaded.get().toDouble() / len).coerceIn(0.0, 1.0)
                else -> 0.0
            }
            val s = segments[index]
            s.start = index * STREAM_SLOT
            s.length = STREAM_SLOT
            s.downloaded = (fraction * STREAM_SLOT).toLong()
            s.status = statusLabel(chunkStatus)
        }
    }

    private fun statusLabel(chunkStatus: ChunkStatus?): String = when (chunkStatus) {
        ChunkStatus.Finished -> "Complete"
        ChunkStatus.Downloading -> "Receiving data..."
        ChunkStatus.Ready -> "Connecting..."
        ChunkStatus.Retrying -> "Retrying..."
        ChunkStatus.Paused -> "Paused"
        ChunkStatus.Failed -> "Failed"
        ChunkStatus.Cancelled -> "Cancelled"
        ChunkStatus.Skipped -> "Skipped"
        null -> "Pending"
    }
}