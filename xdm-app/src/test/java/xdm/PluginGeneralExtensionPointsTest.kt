package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.I8N
import xdm.app.plugins.*
import xdm.plugin.api.enrichment.MetadataEnricher
import xdm.plugin.api.events.*
import xdm.plugin.api.models.DownloadInfo
import xdm.plugin.api.models.PluginMetadata
import xdm.plugin.api.processors.PostDownloadProcessor
import xdm.plugin.api.processors.ProcessingResult
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PluginGeneralExtensionPointsTest : DownloadManagerTestBase() {

    private lateinit var testPluginsDir: File

    @BeforeEach
    fun initPluginTest() {
        I8N.loadTexts("en")
        testPluginsDir = File(dir, "plugins").apply { mkdirs() }
        PluginManager.pluginsDir = testPluginsDir
        PluginEventBus.clearAll()
        PluginManager.init(safeMode = false)
    }

    @AfterEach
    fun cleanupPluginTest() {
        PluginManager.shutdown()
        PluginEventBus.clearAll()
        testPluginsDir.deleteRecursively()
    }

    private fun createTestPlugin(id: String, capabilities: List<String> = emptyList()): Pair<LoadedPlugin, PluginHostContext> {
        val manifest = PluginManifest(
            id = id,
            name = id.replace("-", " ").replaceFirstChar { it.uppercase() },
            version = "1.0.0",
            capabilities = capabilities
        )
        val pluginDir = File(testPluginsDir, id).apply { mkdirs() }
        val loaded = LoadedPlugin(
            manifest = manifest,
            pluginDir = pluginDir,
            status = PluginStatus.ACTIVE
        )
        val meta = PluginMetadata(
            manifest.id, manifest.name, manifest.version, manifest.author, manifest.description,
            manifest.minAppVersion, manifest.minApiVersion, manifest.entryPoint,
            manifest.capabilities.toSet(), manifest.dependencies,
            manifest.license, manifest.homepage, "", false
        )
        val ctx = PluginHostContext(loaded, meta)
        loaded.context = ctx
        return loaded to ctx
    }

    @Test
    fun testMultipleListenersOrderedDelivery() {
        val topic = EventTopics.DOWNLOAD_QUEUED
        val executionOrder = CopyOnWriteArrayList<String>()

        PluginEventBus.subscribe("plugin-a", topic, PluginEventListener<TaskLifecycleEvent> { _, event ->
            executionOrder.add("ListenerA:${event.taskId}")
        })

        PluginEventBus.subscribe("plugin-b", topic, PluginEventListener<TaskLifecycleEvent> { _, event ->
            executionOrder.add("ListenerB:${event.taskId}")
        })

        PluginEventBus.subscribe("plugin-c", topic, PluginEventListener<TaskLifecycleEvent> { _, event ->
            executionOrder.add("ListenerC:${event.taskId}")
        })

        val info = DownloadInfo(101L, "https://example.com/test.zip", "test.zip", "/tmp/test.zip", 1000L, 0L, 0, 0f, "QUEUED", "None", System.currentTimeMillis())
        val event = TaskLifecycleEvent(TaskLifecycleEvent.Type.QUEUED, info)

        PluginEventBus.publishSync(topic, event)

        assertEquals(3, executionOrder.size)
        assertEquals("ListenerA:101", executionOrder[0])
        assertEquals("ListenerB:101", executionOrder[1])
        assertEquals("ListenerC:101", executionOrder[2])
    }

    @Test
    fun testSingleSubscriberFifoOrderPreservation() {
        val topic = "ordered.stream"
        val receivedItems = CopyOnWriteArrayList<Int>()

        PluginEventBus.subscribe("ordered-subscriber", topic, PluginEventListener<Int> { _, data ->
            // Simulate variable processing latency
            if (data % 2 == 0) Thread.sleep(15)
            receivedItems.add(data)
        })

        for (i in 1..10) {
            PluginEventBus.publish(topic, i)
        }

        assertTrue(PluginEventBus.awaitIdle(2000, TimeUnit.MILLISECONDS))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), receivedItems, "Subscriber must process items in strict FIFO order")
    }

    @Test
    fun testSlowListenerIsolation() {
        val topic = EventTopics.DOWNLOAD_STARTED
        val fastListenerReceived = AtomicBoolean(false)
        val slowListenerFinished = AtomicBoolean(false)
        val fastLatch = CountDownLatch(1)
        val slowLatch = CountDownLatch(1)

        // Slow listener sleeps 300ms
        PluginEventBus.subscribe("plugin-slow", topic, PluginEventListener<TaskLifecycleEvent> { _, _ ->
            Thread.sleep(300)
            slowListenerFinished.set(true)
            slowLatch.countDown()
        })

        // Fast listener finishes immediately
        PluginEventBus.subscribe("plugin-fast", topic, PluginEventListener<TaskLifecycleEvent> { _, _ ->
            fastListenerReceived.set(true)
            fastLatch.countDown()
        })

        val info = DownloadInfo(201L, "https://example.com/file.bin", "file.bin", "/tmp/file.bin", 5000L, 0L, 0, 0f, "DOWNLOADING", "None", System.currentTimeMillis())
        val event = TaskLifecycleEvent(TaskLifecycleEvent.Type.STARTED, info)

        val startTime = System.currentTimeMillis()
        PluginEventBus.publish(topic, event)
        val publishDuration = System.currentTimeMillis() - startTime

        // Asynchronous publish should return immediately without blocking on the slow listener
        assertTrue(publishDuration < 150, "PluginEventBus.publish() must not block on slow listeners (took ${publishDuration}ms)")

        // Fast listener must receive event promptly (under 200ms despite slow listener sleeping 300ms)
        assertTrue(fastLatch.await(200, TimeUnit.MILLISECONDS), "Fast listener should receive event in <200ms despite slow listener running 300ms")
        assertTrue(fastListenerReceived.get())

        // Wait for slow listener to complete
        assertTrue(slowLatch.await(1000, TimeUnit.MILLISECONDS))
        assertTrue(slowListenerFinished.get())
        assertTrue(PluginEventBus.awaitIdle(1000, TimeUnit.MILLISECONDS))
    }

    @Test
    fun testRepeatedCompletionDeduplication() {
        val topic = EventTopics.DOWNLOAD_COMPLETED
        val completedCount = AtomicInteger(0)
        val latch = CountDownLatch(1)

        PluginEventBus.subscribe("plugin-dedup", topic, PluginEventListener<TaskLifecycleEvent> { _, event ->
            if (event.taskId == 301L) {
                completedCount.incrementAndGet()
                latch.countDown()
            }
        })

        val info = DownloadInfo(301L, "https://example.com/movie.mkv", "movie.mkv", "/tmp/movie.mkv", 10000L, 10000L, 100, 0f, "FINISHED", "None", System.currentTimeMillis())
        val event = TaskLifecycleEvent(TaskLifecycleEvent.Type.COMPLETED, info)

        // Publish multiple identical terminal events in rapid succession
        PluginEventBus.publish(topic, event)
        PluginEventBus.publish(topic, event)
        PluginEventBus.publish(topic, event)

        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS), "Terminal event must be received")
        assertTrue(PluginEventBus.awaitIdle(1000, TimeUnit.MILLISECONDS))

        // Deduplication should ensure only one terminal completion event is delivered
        assertEquals(1, completedCount.get(), "Duplicate completion events within dedup window must be coalesced")
    }

    @Test
    fun testProgressThrottlingAndGuaranteedTerminalProgress() {
        val topic = EventTopics.DOWNLOAD_PROGRESS
        val progressValues = CopyOnWriteArrayList<Int>()

        PluginEventBus.subscribe("plugin-throttle", topic, PluginEventListener<TaskLifecycleEvent> { _, event ->
            if (event.taskId == 401L && event.download != null) {
                progressValues.add(event.download.progressPercent)
            }
        })

        fun makeProgress(pct: Int): TaskLifecycleEvent {
            val info = DownloadInfo(401L, "https://example.com/big.zip", "big.zip", "/tmp/big.zip", 1000L, (10 * pct).toLong(), pct, 500f, "DOWNLOADING", "None", System.currentTimeMillis())
            return TaskLifecycleEvent(TaskLifecycleEvent.Type.PROGRESSED, info)
        }

        // Send 10 rapid intermediate progress events (<10ms)
        for (p in 1..10) {
            PluginEventBus.publish(topic, makeProgress(p * 5))
        }

        // Send 100% terminal progress
        PluginEventBus.publish(topic, makeProgress(100))

        assertTrue(PluginEventBus.awaitIdle(1000, TimeUnit.MILLISECONDS))

        // High frequency intermediates should be coalesced/throttled
        assertTrue(progressValues.size < 11, "High frequency progress updates should be throttled (received: ${progressValues.size})")
        // Terminal 100% progress must never be dropped by throttling
        assertTrue(progressValues.contains(100), "Terminal 100% progress must always be delivered")
    }

    @Test
    fun testSubscriptionCancellationViaHandle() {
        val topic = "custom.telemetry"
        val receivedCount = AtomicInteger(0)

        val (_, context) = createTestPlugin("dummy-plugin")

        val sub = context.subscribeTopic(topic, PluginEventListener<String> { _, _ ->
            receivedCount.incrementAndGet()
        })

        assertTrue(sub.isActive)
        assertEquals(topic, sub.topic)

        // First dispatch: should be received
        context.publish(topic, "Message 1")
        assertTrue(PluginEventBus.awaitIdle(1000, TimeUnit.MILLISECONDS))
        assertEquals(1, receivedCount.get())

        // Cancel subscription
        sub.unregister()
        assertFalse(sub.isActive)

        // Second dispatch: should NOT be received
        context.publish(topic, "Message 2")
        assertTrue(PluginEventBus.awaitIdle(1000, TimeUnit.MILLISECONDS))
        assertEquals(1, receivedCount.get(), "Unregistered listener must not receive further events")
    }

    @Test
    fun testPluginDisableCleansUpSubscriptionsAndEnrichers() {
        val (loaded, context) = createTestPlugin("test-enricher-plugin", listOf("METADATA_ENRICHER"))

        // Register enricher and subscription
        val enricher = object : MetadataEnricher {
            override fun getEnricherId(): String = "test-enricher"
            override fun suggestFileName(url: String, originalFileName: String, mimeType: String): String = "suggested-clean-name.zip"
            override fun suggestCategory(url: String, fileName: String, mimeType: String): String = "CAT_ARCHIVES"
        }
        context.registerMetadataEnricher(enricher)

        val eventsReceived = AtomicInteger(0)
        context.subscribe(EventTopics.DOWNLOAD_QUEUED, PluginEventListener<TaskLifecycleEvent> { _, _ ->
            eventsReceived.incrementAndGet()
        })

        PluginManager.installedPlugins.add(loaded)

        // Verify enricher is active
        val (suggestedName, suggestedCat) = PluginManager.suggestMetadata("https://example.com/archive.bin", "archive.bin", "application/zip")
        assertEquals("suggested-clean-name.zip", suggestedName)
        assertEquals("CAT_ARCHIVES", suggestedCat)

        // Deactivate plugin
        PluginManager.deactivatePlugin(loaded)
        assertEquals(PluginStatus.DISABLED, loaded.status)

        // Verify enricher is no longer active
        val (afterName, afterCat) = PluginManager.suggestMetadata("https://example.com/archive.bin", "archive.bin", "application/zip")
        assertNull(afterName, "Enricher must not be active after plugin deactivation")
        assertNull(afterCat, "Enricher must not be active after plugin deactivation")

        // Verify event subscription was cleared
        val info = DownloadInfo(501L, "https://example.com/archive.bin", "archive.bin", "/tmp/archive.bin", 100L, 0L, 0, 0f, "QUEUED", "None", System.currentTimeMillis())
        PluginEventBus.publishSync(EventTopics.DOWNLOAD_QUEUED, TaskLifecycleEvent(TaskLifecycleEvent.Type.QUEUED, info))
        assertEquals(0, eventsReceived.get(), "Subscribed events must not be delivered after plugin deactivation")
    }

    @Test
    fun testMetadataEnricherAppliedWhenUserHasNotModified() {
        val (loaded, context) = createTestPlugin("doc-enricher")

        context.registerMetadataEnricher(object : MetadataEnricher {
            override fun getEnricherId(): String = "doc-enricher"
            override fun getPriority(): Int = 200
            override fun suggestFileName(url: String, originalFileName: String, mimeType: String): String? {
                return if (url.contains("report")) "Quarterly_Financial_Report_2026.pdf" else null
            }
            override fun suggestCategory(url: String, fileName: String, mimeType: String): String? {
                return if (fileName.endsWith(".pdf", ignoreCase = true)) "CAT_DOCS" else null
            }
        })
        PluginManager.installedPlugins.add(loaded)

        val (name, cat) = PluginManager.suggestMetadata("https://corp.com/reports/get?id=99", "get.bin", "application/pdf")
        assertEquals("Quarterly_Financial_Report_2026.pdf", name)
        assertEquals("CAT_DOCS", cat)
    }

    @Test
    fun testMalformedMetadataAndTraversalRejection() {
        // Directory traversal attacks
        assertNull(PluginManager.sanitizeSuggestedFileName("../../etc/passwd"))
        assertNull(PluginManager.sanitizeSuggestedFileName("..\\..\\Windows\\System32\\calc.exe"))
        assertNull(PluginManager.sanitizeSuggestedFileName("/root/evil.sh"))
        assertNull(PluginManager.sanitizeSuggestedFileName("C:\\Windows\\System32\\cmd.exe"))
        assertNull(PluginManager.sanitizeSuggestedFileName("sub/dir/file.txt"))

        // Shell metacharacters and illegal characters
        assertNull(PluginManager.sanitizeSuggestedFileName("file*name.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("file|pipe.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("file?query.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("file<tag>.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("file>out.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("file\"quote.txt"))

        // Control characters and null bytes
        assertNull(PluginManager.sanitizeSuggestedFileName("file\u0000null.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("file\nnewline.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("file\rreturn.txt"))

        // Windows reserved device names
        assertNull(PluginManager.sanitizeSuggestedFileName("CON.txt"))
        assertNull(PluginManager.sanitizeSuggestedFileName("prn.pdf"))
        assertNull(PluginManager.sanitizeSuggestedFileName("AUX.bin"))
        assertNull(PluginManager.sanitizeSuggestedFileName("NUL"))
        assertNull(PluginManager.sanitizeSuggestedFileName("com1.log"))
        assertNull(PluginManager.sanitizeSuggestedFileName("LPT1.dat"))

        // Blank or empty
        assertNull(PluginManager.sanitizeSuggestedFileName(""))
        assertNull(PluginManager.sanitizeSuggestedFileName("   "))
        assertNull(PluginManager.sanitizeSuggestedFileName(null))

        // Legitimate user filenames
        assertEquals("Report (2026).pdf", PluginManager.sanitizeSuggestedFileName("Report (2026).pdf"))
        assertEquals("ubuntu-24.04-desktop-amd64.iso", PluginManager.sanitizeSuggestedFileName("ubuntu-24.04-desktop-amd64.iso"))
        assertEquals("My Document [Final].docx", PluginManager.sanitizeSuggestedFileName("My Document [Final].docx"))
        assertEquals("archive_v1.0.tar.gz", PluginManager.sanitizeSuggestedFileName("archive_v1.0.tar.gz"))
    }

    @Test
    fun testPathBoundaryValidationInPostDownloadProcessor() {
        val testFile = File(dir, "original_file.txt").apply { writeText("Original Content") }
        val outsideFile = File(dir.parentFile, "escaped_outside.txt").apply { writeText("Outside Content") }

        val (loaded, context) = createTestPlugin("path-traversal-plugin")

        // Processor attempts path traversal to an outside file
        context.registerPostDownloadProcessor(object : PostDownloadProcessor {
            override fun getName(): String = "malicious-traversal"
            override fun process(download: DownloadInfo, downloadedFile: File): ProcessingResult {
                return ProcessingResult.modified(outsideFile, "Attempting directory escape")
            }
        })
        PluginManager.installedPlugins.add(loaded)

        val completedLatch = CountDownLatch(1)
        var receivedInfo: DownloadInfo? = null
        PluginEventBus.subscribe("test-verifier", EventTopics.DOWNLOAD_COMPLETED, PluginEventListener<TaskLifecycleEvent> { _, event ->
            if (event.taskId == 7001L) {
                receivedInfo = event.download
                completedLatch.countDown()
            }
        })

        PluginManager.notifyDownloadCompleted(testFile.absolutePath, 7001L)

        assertTrue(completedLatch.await(1000, TimeUnit.MILLISECONDS))
        assertNotNull(receivedInfo)

        // Path boundary security check must prevent adoption of outsideFile
        assertEquals(testFile.name, receivedInfo!!.fileName, "Original filename must be preserved when traversal is attempted")
        assertEquals(testFile.absolutePath, receivedInfo!!.destinationPath, "Original destination path must be preserved")

        outsideFile.delete()
    }

    @Test
    fun testNonDestructiveFailureRecoveryPreservesFinishedStatus() {
        val testFile = File(dir, "safe_document.pdf").apply { writeText("PDF Mock") }

        val (loaded, context) = createTestPlugin("faulty-processor-plugin")

        // Processor throws unhandled exception
        context.registerPostDownloadProcessor(object : PostDownloadProcessor {
            override fun getName(): String = "crashing-processor"
            override fun process(download: DownloadInfo, downloadedFile: File): ProcessingResult {
                throw IllegalStateException("Simulated processing error in third-party plugin")
            }
        })
        PluginManager.installedPlugins.add(loaded)

        val completedLatch = CountDownLatch(1)
        var receivedInfo: DownloadInfo? = null
        PluginEventBus.subscribe("test-fault-isolation", EventTopics.DOWNLOAD_COMPLETED, PluginEventListener<TaskLifecycleEvent> { _, event ->
            if (event.taskId == 7002L) {
                receivedInfo = event.download
                completedLatch.countDown()
            }
        })

        // Must NOT throw exception to caller
        assertDoesNotThrow {
            PluginManager.notifyDownloadCompleted(testFile.absolutePath, 7002L)
        }

        assertTrue(completedLatch.await(1000, TimeUnit.MILLISECONDS))
        assertNotNull(receivedInfo)
        // Download status must remain FINISHED despite processor failure
        assertEquals("FINISHED", receivedInfo!!.status, "Download status must remain FINISHED on processor crash")
        assertTrue(testFile.exists(), "Original file must be preserved intact")
    }

    @Test
    fun testControlledLocalChecksumVerificationAction() {
        val content = "Antigravity Secure Checksum Test Payload 2026"
        val testFile = File(dir, "payload.dat").apply { writeText(content, StandardCharsets.UTF_8) }

        // Compute expected SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(StandardCharsets.UTF_8))
        val expectedSha256 = hashBytes.joinToString("") { "%02x".format(it) }

        val (loaded, context) = createTestPlugin("checksum-verifier-plugin")

        val verifiedLatch = CountDownLatch(1)
        val verificationStatus = AtomicBoolean(false)

        context.registerPostDownloadProcessor(object : PostDownloadProcessor {
            override fun getName(): String = "sha256-verifier"
            override fun process(download: DownloadInfo, downloadedFile: File): ProcessingResult {
                val fileDigest = MessageDigest.getInstance("SHA-256")
                val actualBytes = fileDigest.digest(downloadedFile.readBytes())
                val actualSha256 = actualBytes.joinToString("") { "%02x".format(it) }

                if (actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    verificationStatus.set(true)
                    verifiedLatch.countDown()
                    return ProcessingResult.success("Checksum verified successfully")
                } else {
                    return ProcessingResult.rejected("Checksum mismatch: expected $expectedSha256 got $actualSha256")
                }
            }
        })
        PluginManager.installedPlugins.add(loaded)

        PluginManager.notifyDownloadCompleted(testFile.absolutePath, 7003L)

        assertTrue(verifiedLatch.await(1000, TimeUnit.MILLISECONDS), "Checksum verification processor must execute and match")
        assertTrue(verificationStatus.get(), "SHA-256 hash calculation and verification succeeded")
    }

    @Test
    fun testAppStateEventsDelivered() {
        val startupLatch = CountDownLatch(1)
        val safeModeLatch = CountDownLatch(1)
        val shutdownLatch = CountDownLatch(1)
        val stateChangedLatch = CountDownLatch(1)

        PluginEventBus.subscribe("app-state-tester", EventTopics.APP_STARTUP, PluginEventListener<AppStateEvent> { _, event ->
            if (event.state == AppStateEvent.State.STARTUP && !event.isSafeMode) {
                startupLatch.countDown()
            }
        })

        PluginEventBus.subscribe("app-state-tester", EventTopics.APP_SAFE_MODE, PluginEventListener<AppStateEvent> { _, event ->
            if (event.state == AppStateEvent.State.SAFE_MODE && event.isSafeMode) {
                safeModeLatch.countDown()
            }
        })

        PluginEventBus.subscribe("app-state-tester", EventTopics.APP_STATE_CHANGED, PluginEventListener<AppStateEvent> { _, event ->
            if (event.state == AppStateEvent.State.STATE_CHANGED) {
                stateChangedLatch.countDown()
            }
        })

        PluginEventBus.subscribe("app-state-tester", EventTopics.APP_SHUTDOWN, PluginEventListener<AppStateEvent> { _, event ->
            if (event.state == AppStateEvent.State.SHUTDOWN) {
                shutdownLatch.countDown()
            }
        })

        // Emit events
        PluginEventBus.publishSync(EventTopics.APP_STARTUP, AppStateEvent(AppStateEvent.State.STARTUP, false))
        PluginEventBus.publishSync(EventTopics.APP_SAFE_MODE, AppStateEvent(AppStateEvent.State.SAFE_MODE, true))
        PluginEventBus.publishSync(EventTopics.APP_STATE_CHANGED, AppStateEvent(AppStateEvent.State.STATE_CHANGED, false))
        PluginEventBus.publishSync(EventTopics.APP_SHUTDOWN, AppStateEvent(AppStateEvent.State.SHUTDOWN, false))

        assertTrue(startupLatch.await(100, TimeUnit.MILLISECONDS))
        assertTrue(safeModeLatch.await(100, TimeUnit.MILLISECONDS))
        assertTrue(stateChangedLatch.await(100, TimeUnit.MILLISECONDS))
        assertTrue(shutdownLatch.await(100, TimeUnit.MILLISECONDS))
    }
}
