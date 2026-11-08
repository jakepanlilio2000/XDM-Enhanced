package xdm.torrent

import org.libtorrent4j.TorrentInfo
import xdm.core.util.FileUtils
import java.io.File

object TorrentMetadataParser {

    /** Maximum allowed .torrent file size (50 MB) to prevent heap exhaustion / OOM */
    const val MAX_TORRENT_FILE_SIZE = 50L * 1024 * 1024

    /**
     * Parse metadata from a .torrent file on disk.
     */
    fun parse(file: File): TorrentMetadata {
        if (!file.exists() || !file.isFile) {
            throw TorrentEngineException("Torrent file does not exist or is not a file: ${file.absolutePath}")
        }
        if (file.length() > MAX_TORRENT_FILE_SIZE) {
            throw TorrentEngineException("Torrent file exceeds maximum allowed size ($MAX_TORRENT_FILE_SIZE bytes): ${file.length()} bytes")
        }
        TorrentNativeLoader.load()
        return try {
            val bytes = file.readBytes()
            val ti = TorrentInfo(bytes)
            fromTorrentInfo(ti, bytes)
        } catch (e: Throwable) {
            throw TorrentEngineException("Failed to parse torrent file '${file.name}': ${e.message}", e)
        }
    }

    /**
     * Parse metadata from raw .torrent bytes.
     */
    fun parse(bytes: ByteArray, defaultName: String = "torrent"): TorrentMetadata {
        if (bytes.isEmpty()) {
            throw TorrentEngineException("Cannot parse torrent metadata from empty byte array")
        }
        if (bytes.size > MAX_TORRENT_FILE_SIZE) {
            throw TorrentEngineException("Torrent bytes exceed maximum allowed size ($MAX_TORRENT_FILE_SIZE bytes): ${bytes.size} bytes")
        }
        TorrentNativeLoader.load()
        return try {
            val ti = TorrentInfo(bytes)
            fromTorrentInfo(ti, bytes)
        } catch (e: Throwable) {
            throw TorrentEngineException("Failed to parse torrent bytes: ${e.message}", e)
        }
    }

    /**
     * Convert libtorrent4j TorrentInfo into application TorrentMetadata.
     */
    fun fromTorrentInfo(ti: TorrentInfo, rawBytes: ByteArray? = null): TorrentMetadata {
        val filesStorage = ti.files()
        val numFiles = filesStorage.numFiles()
        val fileEntries = ArrayList<TorrentFileEntry>(numFiles)

        for (i in 0 until numFiles) {
            val rawPath = filesStorage.filePath(i)
            val sanitizedPath = sanitizeTorrentFilePath(rawPath, i)
            val size = filesStorage.fileSize(i)
            fileEntries.add(TorrentFileEntry(index = i, path = sanitizedPath, size = size, selected = true))
        }

        val rawName = ti.name()
        val safeName = FileUtils.sanitizeFileName(rawName)?.takeIf { it.isNotBlank() } ?: "torrent"

        return TorrentMetadata(
            name = safeName,
            infoHash = ti.infoHash().toHex().lowercase(),
            totalSize = ti.totalSize(),
            pieceLength = ti.pieceLength(),
            numPieces = ti.numPieces(),
            files = fileEntries,
            torrentBytes = rawBytes
        )
    }

    /**
     * Sanitizes a relative file path from torrent metadata, eliminating directory traversal
     * (e.g. `..` segments, leading slashes, Windows drive letters) and sanitizing each segment
     * against DOS reserved names and illegal filesystem characters.
     */
    fun sanitizeTorrentFilePath(rawPath: String, fallbackIndex: Int = 0): String {
        if (rawPath.isBlank()) return "file_$fallbackIndex"
        var normalized = rawPath.replace('\\', '/')
        // Strip Windows drive letters (e.g. "C:/", "D:\", "c:")
        normalized = normalized.replace(Regex("^[a-zA-Z]:[/\\\\]*"), "")
        // Strip leading slashes
        normalized = normalized.trimStart('/')

        val segments = normalized.split('/').map { it.trim() }.filter {
            it.isNotEmpty() && it != "." && it != ".."
        }

        if (segments.isEmpty()) {
            return "file_$fallbackIndex"
        }

        val sanitizedSegments = segments.mapIndexed { idx, segment ->
            FileUtils.sanitizeFileName(segment)?.takeIf { it.isNotBlank() } ?: "part_${idx + 1}"
        }

        return sanitizedSegments.joinToString("/")
    }

    /**
     * Extracts info hash hex string from a magnet URI, or null if invalid/malformed.
     */
    fun parseMagnetInfoHash(uri: String): String? {
        val trimmed = uri.trim()
        if (!trimmed.startsWith("magnet:?", ignoreCase = true)) return null
        return try {
            TorrentNativeLoader.load()
            val ec = org.libtorrent4j.swig.error_code()
            val p = org.libtorrent4j.swig.libtorrent.parse_magnet_uri(trimmed, ec)
            val ih = p?.info_hashes
            when {
                ih?.has_v1() == true -> ih.v1.to_hex().lowercase()
                ih?.has_v2() == true -> ih.v2.to_hex().lowercase()
                else -> parseMagnetInfoHashRegex(trimmed)
            }
        } catch (_: Throwable) {
            parseMagnetInfoHashRegex(trimmed)
        }
    }

    private fun parseMagnetInfoHashRegex(uri: String): String? {
        val hexMatch = Regex("xt=urn:btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE).find(uri)
        if (hexMatch != null) {
            return hexMatch.groupValues[1].lowercase()
        }
        val v2Match = Regex("xt=urn:btmh:1220([a-fA-F0-9]{64})", RegexOption.IGNORE_CASE).find(uri)
        if (v2Match != null) {
            return v2Match.groupValues[1].lowercase()
        }
        return null
    }
}

