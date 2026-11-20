package xdm.app.plugins

import xdm.app.AppContext
import xdm.core.downloaders.DownloadType
import xdm.core.util.Logger
import xdm.plugin.api.network.VpnClientCapabilities
import xdm.plugin.api.network.VpnProfile
import xdm.plugin.api.network.VpnStateListener
import xdm.plugin.api.network.VpnStatus
import xdm.plugin.api.network.VpnStatusProvider
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages external VPN client integration, profile discovery, interface state verification,
 * and the optional "Pause downloads when VPN prerequisite is unmet" policy.
 *
 * Strictly separates reported UI status from real tunnel state.
 * Does not implement VPN protocols, tunnel drivers, or OS firewall manipulation.
 */
object VpnPolicyManager {

    private val vpnPausedTaskIds = ConcurrentHashMap.newKeySet<Long>()
    private val policyListeners = CopyOnWriteArrayList<(VpnStatus) -> Unit>()
    private var lastObservedStatus: VpnStatus? = null

    private val internalStateListener = VpnStateListener { status ->
        onVpnStateChanged(status)
    }

    private var currentAttachedProvider: VpnStatusProvider? = null

    fun addPolicyListener(listener: (VpnStatus) -> Unit) {
        policyListeners.add(listener)
    }

    fun removePolicyListener(listener: (VpnStatus) -> Unit) {
        policyListeners.remove(listener)
    }

    fun getPausedByPolicyCount(): Int = vpnPausedTaskIds.size

    fun getPausedByPolicyTaskIds(): Set<Long> = HashSet(vpnPausedTaskIds)

    fun isTaskPausedByVpnPolicy(id: Long): Boolean = vpnPausedTaskIds.contains(id)

    fun clearPausedTasks() {
        vpnPausedTaskIds.clear()
    }

    /**
     * Resolves the active VpnStatusProvider matching user configuration.
     */
    fun getActiveProvider(): Pair<LoadedPlugin, VpnStatusProvider>? {
        val providers = PluginManager.getVpnStatusProviders()
        if (providers.isEmpty()) return null
        val reqId = AppContext.config.vpnRequiredProviderId
        if (reqId.isNotBlank()) {
            val matching = providers.firstOrNull { it.second.providerId == reqId }
            if (matching != null) return matching
        }
        return providers.firstOrNull()
    }

    /**
     * Synchronizes listener attachment with the active provider.
     */
    fun syncActiveProviderListener() {
        val provider = getActiveProvider()?.second
        if (provider !== currentAttachedProvider) {
            currentAttachedProvider?.removeStateListener(internalStateListener)
            currentAttachedProvider = provider
            provider?.addStateListener(internalStateListener)
            val currentStatus = provider?.currentStatus ?: VpnStatus.disconnected("No active VPN status provider")
            onVpnStateChanged(currentStatus)
        }
    }

    /**
     * Returns the current VPN connection status.
     */
    fun getCurrentStatus(): VpnStatus {
        syncActiveProviderListener()
        val provider = getActiveProvider()?.second
        val status = provider?.currentStatus ?: VpnStatus.disconnected("No active VPN status provider")
        lastObservedStatus = status
        return status
    }

    /**
     * Discloses the capabilities of the active external VPN client.
     */
    fun getActiveClientCapabilities(): VpnClientCapabilities {
        val provider = getActiveProvider()?.second ?: return VpnClientCapabilities.uninstalled()
        return provider.capabilities
    }

    /**
     * Returns available connection profiles for the active VPN provider.
     */
    fun getAvailableProfiles(): List<VpnProfile> {
        val provider = getActiveProvider()?.second ?: return emptyList()
        return provider.availableProfiles
    }

    /**
     * Checks whether the user's VPN prerequisite is currently satisfied.
     * When vpnPrerequisiteEnabled is false, always returns true.
     */
    fun isVpnPrerequisiteMet(): Boolean {
        if (!AppContext.config.vpnPrerequisiteEnabled) {
            return true
        }
        val status = getCurrentStatus()
        if (!status.isConnected) {
            return false
        }
        val reqProfile = AppContext.config.vpnRequiredProfileId
        if (reqProfile.isNotBlank() && status.activeProfileId != reqProfile) {
            return false
        }
        return true
    }

    /**
     * Verifies whether the named local network interface exists and is up.
     * Provides honest separation between client-reported UI label and physical/virtual interface state.
     */
    fun verifyInterfaceActive(interfaceName: String?): Boolean {
        if (interfaceName.isNullOrBlank()) return false
        return runCatching {
            val nif = NetworkInterface.getByName(interfaceName)
            nif != null && nif.isUp
        }.getOrDefault(false)
    }

    /**
     * Invoked when the external VPN provider notifies a state transition.
     */
    fun onVpnStateChanged(status: VpnStatus) {
        lastObservedStatus = status
        Logger.info("VPN State changed: ${status.state} (${status.statusMessage}) [verified=${status.isTunnelVerified}]")

        if (AppContext.config.vpnPrerequisiteEnabled) {
            val reqProfile = AppContext.config.vpnRequiredProfileId
            val profileSatisfied = reqProfile.isBlank() || status.activeProfileId == reqProfile

            if (!status.isConnected || !profileSatisfied) {
                pauseDownloadsDueToVpn(status)
            } else if (status.isConnected && profileSatisfied) {
                handleVpnReconnected(status)
            }
        }

        policyListeners.forEach { runCatching { it.invoke(status) } }
    }

    private fun pauseDownloadsDueToVpn(status: VpnStatus) {
        val activeRecords = if (AppContext.hasDb) {
            AppContext.db.filter {
                it.status == xdm.manager.RecordStatus.DOWNLOADING ||
                it.status == xdm.manager.RecordStatus.READY ||
                it.status == xdm.manager.RecordStatus.ASSEMBLING
            }
        } else emptyList()

        for (rec in activeRecords) {
            vpnPausedTaskIds.add(rec.id)
            when (rec.downloadType) {
                DownloadType.Plugin -> {
                    PluginDownloadBridge.pauseTask(rec.id)
                }
                DownloadType.Torrent -> {
                    runCatching { AppContext.torrentManager.getTask(rec.id)?.pause() }
                    rec.status = xdm.manager.RecordStatus.PAUSED
                    AppContext.db.update(rec)
                    runCatching { AppContext.app.updateDownloadInView(rec.id) }
                }
                else -> {
                    if (AppContext.hasDownloader) {
                        AppContext.downloader.stopDownload(rec.id)
                    }
                }
            }
        }
        if (activeRecords.isNotEmpty()) {
            Logger.info("VPN prerequisite unmet (${status.state}): Paused ${activeRecords.size} downloads to prevent non-VPN traffic.")
        }
    }

    private fun handleVpnReconnected(status: VpnStatus) {
        if (vpnPausedTaskIds.isEmpty()) return
        if (AppContext.config.vpnAutoResumeOnReconnect) {
            val toResume = ArrayList(vpnPausedTaskIds)
            vpnPausedTaskIds.clear()
            for (id in toResume) {
                val rec = if (AppContext.hasDb) AppContext.db.getById(id) else null
                when (rec?.downloadType) {
                    DownloadType.Plugin -> {
                        PluginDownloadBridge.resumeTask(id)
                    }
                    DownloadType.Torrent -> {
                        runCatching { AppContext.torrentManager.getTask(id)?.resume() }
                        rec.status = xdm.manager.RecordStatus.DOWNLOADING
                        AppContext.db.update(rec)
                        runCatching { AppContext.app.updateDownloadInView(id) }
                    }
                    else -> {
                        if (AppContext.hasDownloader) {
                            AppContext.downloader.resumeDownload(id)
                        }
                    }
                }
            }
            Logger.info("VPN prerequisite re-established: Auto-resumed ${toResume.size} downloads.")
        } else {
            Logger.info("VPN prerequisite re-established: ${vpnPausedTaskIds.size} downloads remain paused. Awaiting explicit user resumption.")
        }
    }

    /**
     * Explicit user-directed connect request.
     */
    fun requestConnect(profileId: String?): Boolean {
        val provider = getActiveProvider()?.second ?: return false
        Logger.info("Explicit user request: Connect VPN via provider '${provider.providerId}', profile='$profileId'")
        return provider.requestConnect(profileId)
    }

    /**
     * Explicit user-directed disconnect request.
     */
    fun requestDisconnect(): Boolean {
        val provider = getActiveProvider()?.second ?: return false
        Logger.info("Explicit user request: Disconnect VPN via provider '${provider.providerId}'")
        return provider.requestDisconnect()
    }
}
