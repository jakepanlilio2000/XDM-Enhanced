package xdm.torrent

import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

enum class TorrentEngineState {
    UNINITIALIZED,
    INITIALIZED,
    RUNNING,
    STOPPED
}

class TorrentEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface TorrentEngine {
    val state: TorrentEngineState
    val currentConfig: TorrentSessionConfig
    fun init(configDir: File? = null)
    fun start()
    fun stop()
    fun restart()
    fun isRunning(): Boolean
    fun version(): String
    fun sessionManager(): SessionManager
    fun addListener(listener: AlertListener)
    fun removeListener(listener: AlertListener)
    fun applyConfig(config: TorrentSessionConfig)
    fun isDhtRunning(): Boolean
    fun dhtNodes(): Long
}

class LibtorrentEngine private constructor() : TorrentEngine {

    private val sessionManagerRef = AtomicReference<SessionManager?>(null)
    private val currentState = AtomicReference(TorrentEngineState.UNINITIALIZED)
    private val activeConfig = AtomicReference(TorrentSessionConfig())
    private val listeners = CopyOnWriteArrayList<AlertListener>()
    private var lastConfigDir: File? = null

    override val state: TorrentEngineState
        get() = currentState.get()

    override val currentConfig: TorrentSessionConfig
        get() = activeConfig.get()

    @Synchronized
    override fun init(configDir: File?) {
        when (currentState.get()) {
            TorrentEngineState.INITIALIZED, TorrentEngineState.RUNNING -> return
            TorrentEngineState.STOPPED -> {
                sessionManagerRef.set(null)
            }
            TorrentEngineState.UNINITIALIZED -> {}
        }

        lastConfigDir = configDir

        try {
            TorrentNativeLoader.load(configDir?.let { File(it, "native") })
            val sm = SessionManager()
            for (listener in listeners) {
                sm.addListener(listener)
            }
            sessionManagerRef.set(sm)
            currentState.set(TorrentEngineState.INITIALIZED)
        } catch (e: Throwable) {
            currentState.set(TorrentEngineState.UNINITIALIZED)
            throw TorrentEngineException("Failed to initialize LibtorrentEngine: ${e.message}", e)
        }
    }

    @Synchronized
    override fun start() {
        when (currentState.get()) {
            TorrentEngineState.RUNNING -> return
            TorrentEngineState.UNINITIALIZED -> init(lastConfigDir)
            TorrentEngineState.STOPPED -> {
                val sm = SessionManager()
                for (listener in listeners) {
                    sm.addListener(listener)
                }
                sessionManagerRef.set(sm)
                currentState.set(TorrentEngineState.INITIALIZED)
            }
            TorrentEngineState.INITIALIZED -> {}
        }

        val sm = sessionManagerRef.get()
            ?: throw TorrentEngineException("SessionManager is null; engine must be initialized before start.")

        if (sm.isRunning) {
            currentState.set(TorrentEngineState.RUNNING)
            return
        }

        try {
            sm.start()
            currentState.set(TorrentEngineState.RUNNING)
            applyConfig(activeConfig.get())
        } catch (e: Throwable) {
            throw TorrentEngineException("Failed to start torrent SessionManager: ${e.message}", e)
        }
    }

    override fun applyConfig(config: TorrentSessionConfig) {
        activeConfig.set(config)
        val sm = sessionManagerRef.get() ?: return
        try {
            sm.downloadRateLimit(config.downloadRateLimit)
            sm.uploadRateLimit(config.uploadRateLimit)
            sm.maxConnections(config.maxConnections)
            sm.maxActiveDownloads(config.maxActiveDownloads)
            sm.maxActiveSeeds(config.maxActiveSeeds)

            val sp = SettingsPack()
            sp.activeLimit(config.maxActiveTotal)
            sp.setEnableDht(config.enableDht)
            sp.setEnableLsd(config.enableLsd)
            sp.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_upnp.swigValue(), config.enableUpnp)
            sp.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_natpmp.swigValue(), config.enableNatPmp)
            sp.setInteger(org.libtorrent4j.swig.settings_pack.int_types.share_ratio_limit.swigValue(), (config.seedingRatioLimit * 100).toInt())
            sp.setInteger(org.libtorrent4j.swig.settings_pack.int_types.seed_time_limit.swigValue(), config.seedingTimeLimitSeconds.toInt())

            val port = config.listenPort
            sp.listenInterfaces("0.0.0.0:$port,[::]:$port")

            // Libtorrent proxy integration
            val pType = when (config.proxyType) {
                TorrentProxyType.NONE -> org.libtorrent4j.swig.settings_pack.proxy_type_t.none
                TorrentProxyType.SOCKS4 -> org.libtorrent4j.swig.settings_pack.proxy_type_t.socks4
                TorrentProxyType.SOCKS5 -> if (config.proxyUser.isNotEmpty()) org.libtorrent4j.swig.settings_pack.proxy_type_t.socks5_pw else org.libtorrent4j.swig.settings_pack.proxy_type_t.socks5
                TorrentProxyType.SOCKS5_PW -> org.libtorrent4j.swig.settings_pack.proxy_type_t.socks5_pw
                TorrentProxyType.HTTP -> if (config.proxyUser.isNotEmpty()) org.libtorrent4j.swig.settings_pack.proxy_type_t.http_pw else org.libtorrent4j.swig.settings_pack.proxy_type_t.http
                TorrentProxyType.HTTP_PW -> org.libtorrent4j.swig.settings_pack.proxy_type_t.http_pw
            }
            sp.setInteger(org.libtorrent4j.swig.settings_pack.int_types.proxy_type.swigValue(), pType.swigValue())
            if (config.proxyHost.isNotEmpty() && config.proxyPort > 0) {
                sp.setString(org.libtorrent4j.swig.settings_pack.string_types.proxy_hostname.swigValue(), config.proxyHost)
                sp.setInteger(org.libtorrent4j.swig.settings_pack.int_types.proxy_port.swigValue(), config.proxyPort)
                if (config.proxyUser.isNotEmpty()) {
                    sp.setString(org.libtorrent4j.swig.settings_pack.string_types.proxy_username.swigValue(), config.proxyUser)
                    sp.setString(org.libtorrent4j.swig.settings_pack.string_types.proxy_password.swigValue(), config.proxyPass)
                }
                sp.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.proxy_peer_connections.swigValue(), config.proxyPeerConnections)
                sp.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.proxy_tracker_connections.swigValue(), config.proxyTrackerConnections)
                sp.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.proxy_hostnames.swigValue(), config.proxyHostnames)
                if (config.anonymousMode) {
                    sp.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.anonymous_mode.swigValue(), true)
                }
            }

            sm.applySettings(sp)

            if (config.enableDht && !sm.isDhtRunning) {
                sm.startDht()
            } else if (!config.enableDht && sm.isDhtRunning) {
                sm.stopDht()
            }
        } catch (_: Throwable) {}
    }

    override fun isDhtRunning(): Boolean = sessionManagerRef.get()?.isDhtRunning == true

    override fun dhtNodes(): Long = sessionManagerRef.get()?.dhtNodes() ?: 0L

    @Synchronized
    override fun stop() {
        val sm = sessionManagerRef.get() ?: return
        if (!sm.isRunning && currentState.get() == TorrentEngineState.STOPPED) {
            return
        }

        try {
            sm.stop()
        } catch (e: Throwable) {
            throw TorrentEngineException("Error occurred while stopping SessionManager: ${e.message}", e)
        } finally {
            currentState.set(TorrentEngineState.STOPPED)
        }
    }

    @Synchronized
    override fun restart() {
        stop()
        start()
    }

    override fun isRunning(): Boolean {
        return sessionManagerRef.get()?.isRunning == true
    }

    override fun version(): String {
        return if (TorrentNativeLoader.isLoaded) {
            TorrentNativeLoader.version
        } else {
            "Not initialized"
        }
    }

    override fun sessionManager(): SessionManager {
        return sessionManagerRef.get()
            ?: throw TorrentEngineException("SessionManager has not been initialized.")
    }

    override fun addListener(listener: AlertListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            sessionManagerRef.get()?.addListener(listener)
        }
    }

    override fun removeListener(listener: AlertListener) {
        listeners.remove(listener)
        sessionManagerRef.get()?.removeListener(listener)
    }

    companion object {
        val instance: TorrentEngine by lazy { LibtorrentEngine() }

        fun createStandalone(): TorrentEngine = LibtorrentEngine()
    }
}
