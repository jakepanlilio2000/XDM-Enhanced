package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.ui.components.DownloadStatusBar
import xdm.app.ui.screens.ProgressWindow
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.web.SegmentProgress
import xdm.manager.DbRecord
import xdm.manager.RecordStatus
import java.awt.Frame
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

class PhaseM2UiCoalescingAndLoadTest : DownloadManagerTestBase() {

    @BeforeEach
    fun setUpEnv() {
        xdm.app.I8N.loadTexts("en")
        AppContext.defaultDownloadFolder = dir.absolutePath
    }

    @AfterEach
    fun tearDownEnv() {
        runOnEdt {
            // cleanup if needed
        }
    }

    private fun <T> runOnEdt(action: () -> T): T {
        var result: T? = null
        var err: Throwable? = null
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        SwingUtilities.invokeAndWait {
            try {
                result = action()
            } catch (t: Throwable) {
                err = t
            }
        }
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun createHttpRecord(id: Long, fileName: String, size: Long, downloaded: Long, prg: Int, status: RecordStatus = RecordStatus.DOWNLOADING): DbRecord {
        val rec = DbRecord(
            id = id,
            size = size,
            downloaded = downloaded,
            progress = prg,
            date = 1000L,
            fileName = fileName,
            eta = 5L,
            speed = 1_000_000f,
            selected = false,
            status = status,
            downloadType = DownloadType.Http
        )
        appDB.add(rec)
        taskDB.saveHttpTask(
            HttpDownloadTaskInfo(
                id = id,
                url = "https://example.com/$fileName",
                fileName = fileName,
                respectFileName = false,
                cookie = null,
                headers = null,
                origin = null,
                categoryId = null,
                targetFolder = dir.absolutePath,
                maxPiece = 8,
                authInfo = null,
                knownFileSize = size
            )
        )
        return rec
    }

    @Test
    fun testProgressWindowStallWatchdogDecay() {
        val rec = createHttpRecord(901L, "stall-test.iso", 50_000_000L, 10_000_000L, 20)
        val wnd = runOnEdt {
            ProgressWindow(rec.id).apply {
                isVisible = true
            }
        }
        try {
            runOnEdt {
                wnd.updateProgress(
                    fileName = "stall-test.iso",
                    downloaded = 10_000_000L,
                    size = 50_000_000L,
                    speed = 4_500_000f, // 4.5 MB/s
                    eta = 9L,
                    prg = 20,
                    segData = listOf(SegmentProgress(0L, 50_000_000L, 10_000_000L, 1L, "Receiving data..."))
                )
                assertTrue(wnd.lblStat3.text.contains("Transfer rate: 4.29 MB/sec") || wnd.lblStat3.text.contains("MB/sec"))
                assertTrue(wnd.lblStat4.text.contains("Time left:"))
            }

            // Sleep past the 500 ms stall threshold
            Thread.sleep(700)

            runOnEdt {
                wnd.checkStall()
                // Stall watchdog must have cleared stale transfer rate and ETA
                assertEquals("Transfer rate: ---", wnd.lblStat3.text, "Stalled download must decay rate to 'Transfer rate: ---'")
                assertEquals("Time left: ---", wnd.lblStat4.text, "Stalled download must decay ETA to 'Time left: ---'")
            }
        } finally {
            runOnEdt { wnd.dispose() }
        }
    }

    @Test
    fun testPauseImmediatelyClearsTransferRate() {
        val rec = createHttpRecord(902L, "pause-test.iso", 50_000_000L, 10_000_000L, 20)
        val wnd = runOnEdt {
            ProgressWindow(rec.id).apply {
                isVisible = true
            }
        }
        try {
            runOnEdt {
                wnd.updateProgress(
                    fileName = "pause-test.iso",
                    downloaded = 10_000_000L,
                    size = 50_000_000L,
                    speed = 3_000_000f,
                    eta = 13L,
                    prg = 20,
                    segData = listOf(SegmentProgress(0L, 50_000_000L, 10_000_000L, 1L, "Receiving data..."))
                )
                assertTrue(wnd.lblStat3.text.contains("MB/sec"))

                // Trigger pause
                wnd.showPaused()

                // Must immediately clear stale non-zero speed
                assertEquals("Transfer rate: ---", wnd.lblStat3.text, "Paused state must not retain stale nonzero rate")
                assertEquals("Time left: ---", wnd.lblStat4.text, "Paused state must not retain stale ETA")
            }
        } finally {
            runOnEdt { wnd.dispose() }
        }
    }

    @Test
    fun testAppInstanceEventCoalescingSingleDownload() {
        val rec = createHttpRecord(903L, "coalesce-test.iso", 100_000_000L, 0L, 0)
        val app = AppContext.app
        runOnEdt {
            app.showProgressWindow(rec.id, rec.fileName)
        }

        try {
            // Blast 100 rapid progress updates from background threads within 20 ms
            val threads = (1..5).map { tIndex ->
                Thread {
                    for (i in 1..20) {
                        val dl = (tIndex * 20 + i) * 100_000L
                        val prg = (dl * 100 / 100_000_000L).toInt()
                        app.updateProgressWindow(
                            id = rec.id,
                            fileName = "coalesce-test.iso",
                            downloaded = dl,
                            size = 100_000_000L,
                            speed = 5_000_000f,
                            eta = 10L,
                            prg = prg,
                            segData = emptyList(),
                            files = null
                        )
                    }
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Allow the EDT to drain coalesced runnables
            val drainLatch = CountDownLatch(1)
            SwingUtilities.invokeLater { drainLatch.countDown() }
            assertTrue(drainLatch.await(2, TimeUnit.SECONDS))
        } finally {
            runOnEdt { app.hideProgressWindow(rec.id) }
        }
    }

    @Test
    fun testConcurrentDownloadsLoad1_5_20() {
        val countCases = listOf(1, 5, 20)
        val app = AppContext.app

        for (n in countCases) {
            val records = (1..n).map { i ->
                createHttpRecord(1000L + i, "stream-$i.bin", 50_000_000L, 0L, 0)
            }

            // Create and register progress windows via app
            runOnEdt {
                records.forEach { r ->
                    app.showProgressWindow(r.id, r.fileName)
                }
            }

            try {
                val stopSignal = java.util.concurrent.atomic.AtomicBoolean(false)
                val updateCounter = AtomicInteger(0)

                // Worker threads simulating concurrent active downloads at ~30 Hz each
                val workers = records.map { rec ->
                    Thread {
                        var bytes = 0L
                        while (!stopSignal.get()) {
                            bytes += 30_000L
                            updateCounter.incrementAndGet()
                            app.updateProgressWindow(
                                id = rec.id,
                                fileName = rec.fileName,
                                downloaded = bytes,
                                size = rec.size,
                                speed = 1_000_000f,
                                eta = 40L,
                                prg = (bytes * 100 / rec.size).toInt().coerceAtMost(100),
                                segData = emptyList(),
                                files = null
                            )
                            app.updateDownloadInView(rec.id)
                            try { Thread.sleep(30) } catch (_: InterruptedException) { break }
                        }
                    }
                }

                workers.forEach { it.start() }

                // Let the load run for 250 ms
                Thread.sleep(250)

                // Measure EDT latency during concurrent load
                val startNano = System.nanoTime()
                val edtMeasureLatch = CountDownLatch(1)
                SwingUtilities.invokeLater {
                    edtMeasureLatch.countDown()
                }
                val finishedInTime = edtMeasureLatch.await(500, TimeUnit.MILLISECONDS)
                val latencyMs = (System.nanoTime() - startNano) / 1_000_000.0

                stopSignal.set(true)
                workers.forEach { it.interrupt(); it.join(500) }

                assertTrue(finishedInTime, "EDT must remain responsive under $n concurrent downloads")
                // Under coalescing, EDT latency should be well under 30 ms even with 20 downloads!
                assertTrue(latencyMs < 30.0, "EDT latency ($latencyMs ms) under $n downloads must remain < 30 ms")
                assertTrue(updateCounter.get() > 0, "Update events were dispatched")
            } finally {
                runOnEdt {
                    records.forEach { r -> app.hideProgressWindow(r.id) }
                }
            }
        }
    }

    @Test
    fun testDownloadStatusBarSnappyCadence() {
        val rec = createHttpRecord(905L, "status-bar-test.bin", 10_000_000L, 5_000_000L, 50)
        rec.speed = 2_500_000f
        appDB.update(rec)

        val statusBar = runOnEdt {
            DownloadStatusBar().apply {
                updateMetrics()
            }
        }

        try {
            val speedText = statusBar.speedText
            assertTrue(speedText.contains("2.38 MB/s") || speedText.contains("MB/s"), "Status bar must show active download speed: $speedText")

            // Simulate pause
            rec.status = RecordStatus.PAUSED
            rec.speed = 0f
            appDB.update(rec)

            runOnEdt {
                statusBar.updateMetrics()
            }

            val pausedSpeedText = statusBar.speedText
            assertTrue(pausedSpeedText.contains("0 B/s ↓"), "Status bar must drop to 0 B/s on pause: $pausedSpeedText")
        } finally {
            runOnEdt { statusBar.dispose() }
        }
    }
}
