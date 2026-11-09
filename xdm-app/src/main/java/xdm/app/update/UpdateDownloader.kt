package xdm.app.update

import xdm.app.AppContext
import xdm.core.network.http.impl.HttpClientImpl
import xdm.core.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class UpdateDownloader(
    private val clientProvider: () -> HttpClientImpl = { createDefaultClient() }
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    val isCancelled: Boolean
        get() = cancelled.get()

    /**
     * Downloads file from [url] into [targetFile]. Invokes [onProgress] periodically.
     */
    fun downloadFile(
        url: String,
        targetFile: File,
        expectedSize: Long = 0L,
        onProgress: (UpdateProgress) -> Unit
    ): Result<File> {
        cancelled.set(false)
        targetFile.parentFile?.mkdirs()

        // Remove any previous leftover
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val client = clientProvider()
        return try {
            val headers = mapOf(
                "User-Agent" to listOf("XDM-Update-Checker"),
                "Accept" to listOf("application/octet-stream, */*")
            )
            val responseResult = client.getResponse(url, headers, null, null)
            val response = responseResult.getOrThrow()

            response.use { res ->
                if (res.statusCode !in 200..299) {
                    throw IllegalStateException("Download failed with HTTP ${res.statusCode}")
                }

                val totalBytes: Long = if (expectedSize > 0) expectedSize else (res.contentLength ?: 0L)
                val buffer = ByteArray(16384)
                var downloaded = 0L
                var lastSpeedTime = System.currentTimeMillis()
                var bytesSinceLastSpeed = 0L
                var currentSpeed = 0L

                FileOutputStream(targetFile).use { out ->
                    val input = res.inputStream
                    while (!cancelled.get()) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        bytesSinceLastSpeed += read

                        val now = System.currentTimeMillis()
                        val diff = now - lastSpeedTime
                        if (diff >= 400) {
                            val elapsedSec = diff / 1000.0
                            currentSpeed = if (elapsedSec > 0) (bytesSinceLastSpeed / elapsedSec).toLong() else 0L
                            lastSpeedTime = now
                            bytesSinceLastSpeed = 0L

                            val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                            onProgress(UpdateProgress(downloaded, totalBytes, currentSpeed, pct))
                        }
                    }
                }

                if (cancelled.get()) {
                    runCatching { targetFile.delete() }
                    throw CancellationException("Download cancelled by user")
                }

                // Final 100% progress call
                val finalPct = if (totalBytes > 0) 100 else 0
                onProgress(UpdateProgress(downloaded, totalBytes, 0L, finalPct))

                Result.success(targetFile)
            }
        } catch (e: Throwable) {
            if (cancelled.get() || e is CancellationException) {
                runCatching { targetFile.delete() }
                Result.failure(CancellationException("Download cancelled by user"))
            } else {
                Logger.error("UPDATE", "Failed to download update asset from $url", e)
                Result.failure(e)
            }
        } finally {
            runCatching { client.close() }
        }
    }

    /**
     * Fetches plain text (e.g. SHA256SUMS.txt) from [url].
     */
    fun fetchText(url: String): String? {
        val client = clientProvider()
        return try {
            val headers = mapOf(
                "User-Agent" to listOf("XDM-Update-Checker"),
                "Accept" to listOf("text/plain, */*")
            )
            client.getResponse(url, headers, null, null).getOrNull()?.use { res ->
                if (res.statusCode in 200..299) {
                    res.inputStream.readBytes().decodeToString()
                } else null
            }
        } catch (e: Throwable) {
            Logger.error("UPDATE", "Failed to fetch text from $url", e)
            null
        } finally {
            runCatching { client.close() }
        }
    }

    companion object {
        fun createDefaultClient(readTimeoutSec: Int = 30): HttpClientImpl {
            return try {
                val config = AppContext.config
                HttpClientImpl(
                    1, config.toProxy(), readTimeoutSeconds = readTimeoutSec,
                    proxyUser = config.proxyUser, proxyPassword = config.proxyPass
                )
            } catch (_: Throwable) {
                HttpClientImpl(1, null, readTimeoutSeconds = readTimeoutSec)
            }
        }

        fun getUpdateTempDir(): File {
            val base = try {
                File(AppContext.config.tempFolder).takeIf { it.exists() && it.canWrite() }
            } catch (_: Throwable) {
                null
            } ?: File(System.getProperty("java.io.tmpdir"))
            val dir = File(base, "xdm-enhanced-updates")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }
}

