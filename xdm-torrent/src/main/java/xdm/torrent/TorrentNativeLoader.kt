package xdm.torrent

import org.libtorrent4j.swig.libtorrent
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TorrentNativeLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object TorrentNativeLoader {
    const val LIBTORRENT4J_VERSION = "2.1.0-35"

    private val loaded = AtomicBoolean(false)
    @Volatile
    private var loadedVersion: String? = null

    val isLoaded: Boolean
        get() = loaded.get()

    val version: String
        get() = loadedVersion ?: throw TorrentNativeLoadException("Torrent native library has not been loaded yet.")

    @Synchronized
    fun load(customDirectory: File? = null) {
        if (loaded.get()) return

        val existingPath = System.getProperty("libtorrent4j.jni.path")?.trim()
        if (!existingPath.isNullOrBlank()) {
            val file = File(existingPath).absoluteFile
            if (!file.exists() || !file.isFile) {
                throw TorrentNativeLoadException("Configured libtorrent4j.jni.path does not exist or is not a file: $existingPath")
            }
            try {
                val ver = libtorrent.version()
                loadedVersion = ver
                loaded.set(true)
                return
            } catch (t: Throwable) {
                throw TorrentNativeLoadException("Failed to initialize libtorrent4j with existing path: $existingPath", t)
            }
        }

        val osName = System.getProperty("os.name", "").lowercase(Locale.US)
        val osArch = System.getProperty("os.arch", "").lowercase(Locale.US)

        val (resourcePath, fileName) = resolveNativeResource(osName, osArch)
            ?: throw TorrentNativeLoadException(
                "Unsupported operating system or architecture for libtorrent4j: os.name='$osName', os.arch='$osArch'"
            )

        val targetDir = customDirectory ?: File(System.getProperty("java.io.tmpdir"), "xdm-torrent4j-native-$LIBTORRENT4J_VERSION")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            if (!targetDir.isDirectory) {
                throw TorrentNativeLoadException("Failed to create native library extraction directory: ${targetDir.absolutePath}")
            }
        }

        val targetFile = File(targetDir, fileName)

        try {
            val resourceStream = TorrentNativeLoader::class.java.getResourceAsStream(resourcePath)
                ?: throw TorrentNativeLoadException(
                    "Embedded native library resource not found on classpath: '$resourcePath' (os.name='$osName', os.arch='$osArch')"
                )

            extractResource(resourceStream, targetFile)
            System.setProperty("libtorrent4j.jni.path", targetFile.absolutePath)

            val ver = libtorrent.version()
            loadedVersion = ver
            loaded.set(true)
        } catch (e: TorrentNativeLoadException) {
            throw e
        } catch (t: Throwable) {
            throw TorrentNativeLoadException(
                "Failed to load libtorrent4j native library from '${targetFile.absolutePath}': ${t.message}",
                t
            )
        }
    }

    internal fun resolveNativeResource(osName: String, osArch: String): Pair<String, String>? {
        val isWindows = osName.contains("windows")
        val isMac = osName.contains("mac") || osName.contains("darwin")
        val isLinux = osName.contains("linux") || osName.contains("unix")

        val isX64 = osArch == "x86_64" || osArch == "amd64" || osArch == "x64"
        val isArm64 = osArch == "aarch64" || osArch == "arm64"
        val isArm32 = (osArch.startsWith("arm") || osArch == "armv7l" || osArch == "armv8l") && !isArm64

        return when {
            isWindows && isX64 -> Pair("/lib/x86_64/libtorrent4j.dll", "libtorrent4j.dll")
            isMac && isArm64 -> Pair("/lib/arm64/libtorrent4j.dylib", "libtorrent4j.dylib")
            isLinux && isX64 -> Pair("/lib/x86_64/libtorrent4j.so", "libtorrent4j.so")
            isLinux && isArm32 -> Pair("/lib/arm/libtorrent4j.so", "libtorrent4j.so")
            else -> null
        }
    }

    private fun extractResource(input: InputStream, targetFile: File) {
        input.use { src ->
            // If already extracted with valid content, reuse it
            if (targetFile.exists() && targetFile.length() > 0) {
                return
            }
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp-${System.currentTimeMillis()}")
            try {
                FileOutputStream(tempFile).use { dst ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (src.read(buffer).also { bytesRead = it } != -1) {
                        dst.write(buffer, 0, bytesRead)
                    }
                    dst.flush()
                }
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    throw TorrentNativeLoadException(
                        "Failed to extract native library to ${targetFile.absolutePath}: ${e.message}",
                        e
                    )
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }
}


