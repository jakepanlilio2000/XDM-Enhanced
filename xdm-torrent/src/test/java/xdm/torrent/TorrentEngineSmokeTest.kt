package xdm.torrent

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.libtorrent4j.AlertListener
import org.libtorrent4j.alerts.Alert
import java.io.File

class TorrentEngineSmokeTest {

    @Test
    fun testNativeLibraryLoading() {
        TorrentNativeLoader.load()
        assertTrue(TorrentNativeLoader.isLoaded, "Native loader should report isLoaded = true")
        val ver = TorrentNativeLoader.version
        assertNotNull(ver, "Version string should not be null")
        assertTrue(ver.startsWith("2."), "Version should start with 2.x, got: $ver")
    }

    @Test
    fun testEngineLifecycle(@TempDir tempDir: File) {
        val engine = LibtorrentEngine.createStandalone()
        assertEquals(TorrentEngineState.UNINITIALIZED, engine.state)
        assertFalse(engine.isRunning())

        engine.init(tempDir)
        assertEquals(TorrentEngineState.INITIALIZED, engine.state)
        assertFalse(engine.isRunning())

        engine.start()
        assertEquals(TorrentEngineState.RUNNING, engine.state)
        assertTrue(engine.isRunning())

        val version = engine.version()
        assertNotNull(version)
        assertTrue(version.startsWith("2."), "Reported version should start with 2., was $version")

        assertNotNull(engine.sessionManager(), "sessionManager should not be null while running")

        engine.stop()
        assertEquals(TorrentEngineState.STOPPED, engine.state)
        assertFalse(engine.isRunning())
    }

    @Test
    fun testIdempotentStartAndStop() {
        val engine = LibtorrentEngine.createStandalone()
        engine.init()
        engine.start()
        assertTrue(engine.isRunning())

        // Redundant start call should be safe
        engine.start()
        assertTrue(engine.isRunning())

        engine.stop()
        assertFalse(engine.isRunning())

        // Redundant stop call should be safe
        engine.stop()
        assertFalse(engine.isRunning())
    }

    @Test
    fun testEngineRestartCycle() {
        val engine = LibtorrentEngine.createStandalone()
        engine.init()
        engine.start()
        assertTrue(engine.isRunning())
        assertEquals(TorrentEngineState.RUNNING, engine.state)

        engine.stop()
        assertFalse(engine.isRunning())
        assertEquals(TorrentEngineState.STOPPED, engine.state)

        // Starting again from STOPPED should allocate a fresh session and run
        engine.start()
        assertTrue(engine.isRunning())
        assertEquals(TorrentEngineState.RUNNING, engine.state)

        engine.stop()
        assertFalse(engine.isRunning())
        assertEquals(TorrentEngineState.STOPPED, engine.state)
    }

    @Test
    fun testRestartMethod() {
        val engine = LibtorrentEngine.createStandalone()
        engine.start()
        assertTrue(engine.isRunning())

        engine.restart()
        assertTrue(engine.isRunning())
        assertEquals(TorrentEngineState.RUNNING, engine.state)

        engine.stop()
        assertFalse(engine.isRunning())
    }

    @Test
    fun testListenerRegistration() {
        val engine = LibtorrentEngine.createStandalone()
        val dummyListener = object : AlertListener {
            override fun types(): IntArray? = null
            override fun alert(alert: Alert<*>?) {}
        }

        // Add before init/start
        engine.addListener(dummyListener)

        engine.start()
        assertTrue(engine.isRunning())

        // Remove listener while running
        engine.removeListener(dummyListener)

        engine.stop()
    }

    @Test
    fun testPlatformResolution() {
        // Supported platforms
        val winX64 = TorrentNativeLoader.resolveNativeResource("windows 11", "amd64")
        assertNotNull(winX64)
        assertEquals("/lib/x86_64/libtorrent4j.dll", winX64?.first)

        val macArm = TorrentNativeLoader.resolveNativeResource("mac os x", "aarch64")
        assertNotNull(macArm)
        assertEquals("/lib/arm64/libtorrent4j.dylib", macArm?.first)

        val linuxX64 = TorrentNativeLoader.resolveNativeResource("linux", "x86_64")
        assertNotNull(linuxX64)
        assertEquals("/lib/x86_64/libtorrent4j.so", linuxX64?.first)

        val linuxArm32 = TorrentNativeLoader.resolveNativeResource("linux", "arm")
        assertNotNull(linuxArm32)
        assertEquals("/lib/arm/libtorrent4j.so", linuxArm32?.first)

        // Unsupported platforms
        assertNull(TorrentNativeLoader.resolveNativeResource("solaris", "sparc"))
        assertNull(TorrentNativeLoader.resolveNativeResource("mac os x", "x86_64"), "macOS x86_64 is not bundled")
        assertNull(TorrentNativeLoader.resolveNativeResource("linux", "aarch64"), "Linux aarch64 is not bundled in desktop artifact")
    }
}
