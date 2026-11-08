package xdm.torrent

import org.libtorrent4j.SessionManager
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class LocalSeeder(
    val session: SessionManager,
    val torrentInfo: TorrentInfo,
    val sourceDir: File
) : Closeable {
    val port: Int get() = session.swig().listen_port()
    val endpoint: TcpEndpoint get() = TcpEndpoint("127.0.0.1", port)

    fun handle(): TorrentHandle? = session.find(torrentInfo.infoHash())

    override fun close() {
        try {
            session.stop()
        } catch (_: Throwable) {}
    }
}

object TorrentTransferTestFixture {

    init {
        TorrentNativeLoader.load()
    }

    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = sha256(file.readBytes())

    fun createDeterministicFile(parentDir: File, fileName: String, sizeBytes: Int): Pair<File, String> {
        parentDir.mkdirs()
        val file = File(parentDir, fileName)
        val data = ByteArray(sizeBytes) { i -> (i % 251).toByte() }
        Files.write(file.toPath(), data)
        return Pair(file, sha256(data))
    }

    fun buildTorrent(target: File, pieceSize: Int = 32 * 1024): Pair<ByteArray, TorrentInfo> {
        val builder = TorrentBuilder()
            .path(target)
            .pieceSize(pieceSize)
        val result = builder.generate()
        val torrentBytes = result.entry().bencode()
        val ti = TorrentInfo.bdecode(torrentBytes)
        return Pair(torrentBytes, ti)
    }

    fun startSeeder(ti: TorrentInfo, sourceDir: File): LocalSeeder {
        val seederSession = SessionManager()
        seederSession.start()
        seederSession.download(ti, sourceDir)
        return LocalSeeder(seederSession, ti, sourceDir)
    }
}

