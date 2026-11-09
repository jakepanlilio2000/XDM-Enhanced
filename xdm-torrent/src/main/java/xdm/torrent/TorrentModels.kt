package xdm.torrent

import xdm.core.downloaders.DownloadError
import java.io.File

/**
 * Source of torrent content to download.
 */
sealed class TorrentSource {
    data class TorrentFile(val file: File) : TorrentSource() {
        override val name: String get() = file.nameWithoutExtension
    }

    data class TorrentBytes(val bytes: ByteArray, val torrentName: String) : TorrentSource() {
        override val name: String get() = torrentName
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TorrentBytes) return false
            return bytes.contentEquals(other.bytes) && torrentName == other.torrentName
        }
        override fun hashCode(): Int = 31 * bytes.contentHashCode() + torrentName.hashCode()
    }

    data class MagnetUri(val uri: String, val displayName: String? = null) : TorrentSource() {
        override val name: String
            get() {
                if (!displayName.isNullOrBlank()) return displayName
                val dn = TorrentMetadataParser.parseMagnetDisplayName(uri)
                if (!dn.isNullOrBlank()) return dn
                val hash = TorrentMetadataParser.parseMagnetInfoHash(uri)
                return if (!hash.isNullOrBlank()) {
                    "Fetching metadata (${hash.take(8)}...)"
                } else {
                    "Fetching metadata"
                }
            }
    }

    abstract val name: String
}

/**
 * Represents a single file within a torrent.
 */
data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val size: Long,
    val selected: Boolean = true
)

/**
 * Metadata extracted from a torrent file or magnet.
 */
data class TorrentMetadata(
    val name: String,
    val infoHash: String,
    val totalSize: Long,
    val pieceLength: Int,
    val numPieces: Int,
    val files: List<TorrentFileEntry>,
    val torrentBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TorrentMetadata) return false
        return infoHash == other.infoHash
    }

    override fun hashCode(): Int = infoHash.hashCode()
}

/**
 * File priority levels for torrent multi-file downloads.
 */
enum class TorrentPriority(val level: Int) {
    DO_NOT_DOWNLOAD(0),
    LOW(1),
    NORMAL(4),
    HIGH(7);

    fun toLibtorrent(): org.libtorrent4j.Priority = when (this) {
        DO_NOT_DOWNLOAD -> org.libtorrent4j.Priority.IGNORE
        LOW -> org.libtorrent4j.Priority.LOW
        NORMAL -> org.libtorrent4j.Priority.DEFAULT
        HIGH -> org.libtorrent4j.Priority.TOP_PRIORITY
    }

    companion object {
        fun fromLibtorrent(p: org.libtorrent4j.Priority): TorrentPriority = when (p) {
            org.libtorrent4j.Priority.IGNORE -> DO_NOT_DOWNLOAD
            org.libtorrent4j.Priority.LOW -> LOW
            org.libtorrent4j.Priority.TOP_PRIORITY -> HIGH
            else -> NORMAL
        }
    }
}

/**
 * Connected peer statistics.
 */
data class TorrentPeerInfo(
    val ip: String,
    val client: String,
    val downSpeed: Int,          // bytes/sec
    val upSpeed: Int,            // bytes/sec
    val progressPercent: Int,    // 0-100
    val totalDownload: Long,
    val totalUpload: Long,
    val connectionType: String,  // TCP or uTP
    val flags: String,
    val source: String           // DHT, PEX, Tracker, LSD, Incoming
)

/**
 * Tracker status information.
 */
data class TorrentTrackerInfo(
    val url: String,
    val tier: Int,
    val isWorking: Boolean,
    val isUpdating: Boolean,
    val fails: Int,
    val message: String
)

/**
 * Seeding policy modes.
 */
enum class SeedingMode {
    NEVER,
    RATIO_OR_TIME,
    UNLIMITED
}

/**
 * Seeding rules for automatic seeding cut-off.
 */
data class SeedingRule(
    val mode: SeedingMode = SeedingMode.RATIO_OR_TIME,
    val maxRatio: Float = 1.0f,
    val maxTimeSeconds: Long = 3600L
) {
    val ratioLimit: Float get() = maxRatio
    val seedingTimeMinutes: Int get() = (maxTimeSeconds / 60L).toInt()
}

/**
 * Global BitTorrent engine configuration.
 */
data class TorrentSessionConfig(
    val downloadRateLimit: Int = 0,         // bytes/sec (0 = unlimited)
    val uploadRateLimit: Int = 0,           // bytes/sec (0 = unlimited)
    val maxConnections: Int = 200,
    val maxActiveDownloads: Int = 3,
    val maxActiveSeeds: Int = 3,
    val maxActiveTotal: Int = 5,
    val enableDht: Boolean = true,
    val enablePex: Boolean = true,
    val enableLsd: Boolean = true,
    val listenPort: Int = 0,                // 0 = dynamic OS port
    val enableUpnp: Boolean = false,        // Explicit user control: default off
    val enableNatPmp: Boolean = false,      // Explicit user control: default off
    val seedingMode: SeedingMode = SeedingMode.RATIO_OR_TIME,
    val seedingRatioLimit: Float = 1.0f,
    val seedingTimeLimitSeconds: Long = 3600L
) {
    val seedingRule: SeedingRule
        get() = SeedingRule(seedingMode, seedingRatioLimit, seedingTimeLimitSeconds)

    constructor(
        downloadRateLimit: Int = 0,
        uploadRateLimit: Int = 0,
        maxConnections: Int = 200,
        maxActiveDownloads: Int = 3,
        maxActiveSeeds: Int = 3,
        maxActiveTotal: Int = 5,
        enableDht: Boolean = true,
        enablePex: Boolean = true,
        enableLsd: Boolean = true,
        listenPort: Int = 0,
        seedingRule: SeedingRule,
        enableUpnp: Boolean = false,
        enableNatPmp: Boolean = false
    ) : this(
        downloadRateLimit,
        uploadRateLimit,
        maxConnections,
        maxActiveDownloads,
        maxActiveSeeds,
        maxActiveTotal,
        enableDht,
        enablePex,
        enableLsd,
        listenPort,
        enableUpnp,
        enableNatPmp,
        seedingRule.mode,
        seedingRule.maxRatio,
        seedingRule.maxTimeSeconds
    )
}

/**
 * State machine for a torrent download task.
 */
enum class TorrentTaskState {
    INITIALIZING,
    DOWNLOADING_METADATA,
    CHECKING_FILES,
    DOWNLOADING,
    PAUSED,
    FINISHED,
    SEEDING,
    STOPPED,
    ERROR
}

/**
 * Immutable real-time snapshot of a torrent transfer's progress and stats.
 */
data class TorrentProgressSnapshot(
    val id: Long,
    val infoHash: String,
    val name: String,
    val state: TorrentTaskState,
    val progressPercent: Int,
    val progressFraction: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val downloadRate: Int,
    val uploadRate: Int,
    val numPeers: Int,
    val numSeeds: Int,
    val verifiedPieces: Int,
    val totalPieces: Int,
    val etaSeconds: Long,
    val errorMessage: String? = null,
    val totalUploadBytes: Long = 0L,
    val shareRatio: Float = 0.0f,
    val seedingDurationSeconds: Long = 0L,
    val queuePosition: Int = -1,
    val isPrivate: Boolean = false,
    val checkingProgressPercent: Int = 0
)

/**
 * Listener for fine-grained BitTorrent events.
 */
interface TorrentTaskListener {
    fun onStateChanged(task: TorrentDownloadTask, oldState: TorrentTaskState, newState: TorrentTaskState) {}
    fun onProgress(task: TorrentDownloadTask, snapshot: TorrentProgressSnapshot) {}
    fun onPieceFinished(task: TorrentDownloadTask, pieceIndex: Int, verifiedCount: Int, totalPieces: Int) {}
    fun onFinished(task: TorrentDownloadTask) {}
    fun onError(task: TorrentDownloadTask, message: String, error: DownloadError?) {}
    fun onMetadataReceived(task: TorrentDownloadTask, metadata: TorrentMetadata) {}
}

