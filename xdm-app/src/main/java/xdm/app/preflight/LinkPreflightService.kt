package xdm.app.preflight

import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import xdm.app.AppContext
import xdm.core.util.FileUtils
import xdm.core.util.Logger
import xdm.core.util.getExtension
import xdm.core.util.getFileName
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class PreflightResult(
    val originalUrl: String,
    val finalUrl: String,
    val fileName: String,
    val fileSize: Long?,
    val contentType: String?,
    val contentDisposition: String?,
    val etag: String?,
    val statusCode: Int,
    val isDownloadable: Boolean,
    val requiresAuth: Boolean = false,
    val isExpiredOrForbidden: Boolean = false,
    val errorMessage: String? = null,
    val responseHeaders: Map<String, List<String>>? = null
)

interface PreflightHandle {
    fun cancel()
    val isCancelled: Boolean
}

object LinkPreflightService {
    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "XDM-PreflightService").apply { isDaemon = true }
    }

    private val HOP_BY_HOP_HEADERS = setOf(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailers", "transfer-encoding", "upgrade", "range", "content-length"
    )

    fun preflight(
        url: String,
        headers: Map<String, List<String>>? = null,
        cookie: String? = null,
        customClient: OkHttpClient? = null,
        onResult: (PreflightResult) -> Unit
    ): PreflightHandle {
        val cancelled = AtomicBoolean(false)
        val activeCall = AtomicReference<Call?>(null)

        val handle = object : PreflightHandle {
            override fun cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    activeCall.get()?.cancel()
                }
            }
            override val isCancelled: Boolean get() = cancelled.get()
        }

        executor.submit {
            try {
                val result = preflightSync(
                    url = url,
                    headers = headers,
                    cookie = cookie,
                    isCancelled = { cancelled.get() },
                    callRef = activeCall,
                    customClient = customClient
                )
                if (!cancelled.get()) {
                    onResult(result)
                }
            } catch (t: Throwable) {
                if (!cancelled.get()) {
                    val fallbackName = FileUtils.sanitizeFileName(FileUtils.getFileName(url)) ?: "download"
                    onResult(
                        PreflightResult(
                            originalUrl = url,
                            finalUrl = url,
                            fileName = fallbackName,
                            fileSize = null,
                            contentType = null,
                            contentDisposition = null,
                            etag = null,
                            statusCode = 0,
                            isDownloadable = hasDownloadableExtension(url),
                            errorMessage = t.message ?: "Failed to probe resource"
                        )
                    )
                }
            }
        }

        return handle
    }

    fun preflightSync(
        url: String,
        headers: Map<String, List<String>>? = null,
        cookie: String? = null,
        isCancelled: () -> Boolean = { false },
        callRef: AtomicReference<Call?>? = null,
        customClient: OkHttpClient? = null
    ): PreflightResult {
        val okClient = customClient ?: defaultClient
        val parsedUrl = url.toHttpUrlOrNull()
        if (parsedUrl == null) {
            val fallbackName = FileUtils.sanitizeFileName(FileUtils.getFileName(url)) ?: "download"
            return PreflightResult(
                originalUrl = url,
                finalUrl = url,
                fileName = fallbackName,
                fileSize = null,
                contentType = null,
                contentDisposition = null,
                etag = null,
                statusCode = 0,
                isDownloadable = false,
                errorMessage = "Invalid URL"
            )
        }

        if (isCancelled()) return cancelledResult(url)

        // Step 1: Attempt non-destructive HEAD request
        val headReqBuilder = Request.Builder().url(parsedUrl).head()
        applySafeHeaders(headReqBuilder, headers, cookie)
        val headCall = okClient.newCall(headReqBuilder.build())
        callRef?.set(headCall)

        var response: Response? = null
        try {
            response = headCall.execute()
        } catch (e: IOException) {
            if (isCancelled() || headCall.isCanceled()) return cancelledResult(url)
            return connectionFailedResult(url, e)
        }

        // Step 2: If HEAD is unsupported (400, 405, 501), fallback to safe limited GET with Range: bytes=0-0
        val code = response.code
        if (code in listOf(400, 405, 501)) {
            response.close()
            if (isCancelled()) return cancelledResult(url)

            val getReqBuilder = Request.Builder()
                .url(parsedUrl)
                .get()
                .header("Range", "bytes=0-0")
            applySafeHeaders(getReqBuilder, headers, cookie)
            val getCall = okClient.newCall(getReqBuilder.build())
            callRef?.set(getCall)

            try {
                response = getCall.execute()
            } catch (e: IOException) {
                if (isCancelled() || getCall.isCanceled()) return cancelledResult(url)
                return connectionFailedResult(url, e)
            }
        }

        return try {
            val finalUrl = response.request.url.toString()
            val statusCode = response.code
            val respHeaders = response.headers.toMultimap()
            val cd = response.header("Content-Disposition")
            val ct = response.header("Content-Type")
            val etag = response.header("ETag")

            // Content-Length resolution: prefer Content-Range total if present
            var size: Long? = null
            val cr = response.header("Content-Range")
            if (cr != null) {
                val totalStr = cr.substringAfterLast('/', "").trim()
                size = totalStr.toLongOrNull()?.takeIf { it > 0 }
            }
            if (size == null) {
                val cl = response.header("Content-Length")?.toLongOrNull()
                if (cl != null && cl > 0) {
                    size = cl
                }
            }

            val fileName = resolveFileName(finalUrl, cd, ct)
            val downloadable = isLikelyDownloadable(url, finalUrl, ct, cd, statusCode)

            logPreflightResult(url, finalUrl, statusCode, size, fileName)

            PreflightResult(
                originalUrl = url,
                finalUrl = finalUrl,
                fileName = fileName,
                fileSize = size,
                contentType = ct,
                contentDisposition = cd,
                etag = etag,
                statusCode = statusCode,
                isDownloadable = downloadable,
                requiresAuth = (statusCode == 401),
                isExpiredOrForbidden = (statusCode == 403 || statusCode == 410),
                errorMessage = if (statusCode >= 400 && statusCode != 401) "HTTP $statusCode" else null,
                responseHeaders = respHeaders
            )
        } finally {
            // Guarantee NO payload body is streamed or read into disk/memory during preflight
            try {
                response.body?.close()
                response.close()
            } catch (_: Exception) {
            }
        }
    }

    fun resolveFileName(finalUrl: String, contentDisposition: String?, contentType: String?): String {
        return getFileName(finalUrl, contentDisposition, contentType)
    }

    fun isLikelyDownloadable(
        originalUrl: String,
        finalUrl: String,
        contentType: String?,
        contentDisposition: String?,
        statusCode: Int
    ): Boolean {
        if (statusCode !in 200..399 && statusCode != 401) {
            return false
        }
        // Explicit attachment/filename in Content-Disposition means it is a downloadable file
        if (contentDisposition != null) {
            val cdLow = contentDisposition.lowercase(Locale.ROOT)
            if (cdLow.contains("attachment") || cdLow.contains("filename")) {
                return true
            }
        }

        // Check Content-Type
        val type = contentType?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim()
        if (type != null) {
            // HTML / Webpages are NOT treated as downloadable files unless URL explicitly has a downloadable extension
            if (type == "text/html" || type == "application/xhtml+xml") {
                return hasDownloadableExtension(finalUrl) || hasDownloadableExtension(originalUrl)
            }
            if (type.startsWith("video/") ||
                type.startsWith("audio/") ||
                type.startsWith("image/") ||
                type == "application/octet-stream" ||
                type == "application/x-zip-compressed" ||
                type.contains("zip") ||
                type.contains("pdf") ||
                type.contains("compressed") ||
                type.contains("download") ||
                type.contains("stream") ||
                type.contains("torrent") ||
                type.contains("tar") ||
                type.contains("package") ||
                type.contains("binary")
            ) {
                return true
            }
        }

        return hasDownloadableExtension(finalUrl) || hasDownloadableExtension(originalUrl)
    }

    fun hasDownloadableExtension(url: String): Boolean {
        val name = FileUtils.getFileName(url)
        val rawExt = getExtension(name) ?: return false
        val ext = rawExt.removePrefix(".").lowercase(Locale.ROOT)
        if (ext.isEmpty() || ext == "htm" || ext == "html" || ext == "php" || ext == "asp" || ext == "jsp" || ext == "aspx") {
            return false
        }
        val commonExtensions = setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "dmg", "exe", "msi", "bin", "apk",
            "deb", "rpm", "pkg", "jar", "pdf", "docx", "xlsx", "pptx", "mp4", "mkv", "avi", "mov",
            "flv", "wmv", "webm", "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "torrent",
            "img", "vmdk", "qcow2", "epub", "csv"
        )
        if (commonExtensions.contains(ext)) return true
        if (AppContext.hasConfig) {
            if (AppContext.config.fileExtensions.any { it.equals(ext, ignoreCase = true) }) return true
            if (AppContext.config.videoExtensions.any { it.equals(ext, ignoreCase = true) }) return true
        }
        return false
    }

    private fun applySafeHeaders(
        builder: Request.Builder,
        headers: Map<String, List<String>>?,
        cookie: String?
    ) {
        val cookies = LinkedHashSet<String>()
        headers?.forEach { (name, values) ->
            val low = name.lowercase(Locale.ROOT)
            if (low == "cookie") {
                cookies.addAll(values)
            } else if (!HOP_BY_HOP_HEADERS.contains(low)) {
                for (v in values) {
                    builder.addHeader(name, v)
                }
            }
        }
        if (!cookie.isNullOrBlank()) {
            cookies.add(cookie)
        }
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies.joinToString("; "))
        }
    }

    private fun cancelledResult(url: String): PreflightResult {
        val fallbackName = FileUtils.sanitizeFileName(FileUtils.getFileName(url)) ?: "download"
        return PreflightResult(
            originalUrl = url,
            finalUrl = url,
            fileName = fallbackName,
            fileSize = null,
            contentType = null,
            contentDisposition = null,
            etag = null,
            statusCode = 0,
            isDownloadable = false,
            errorMessage = "Preflight cancelled"
        )
    }

    private fun connectionFailedResult(url: String, e: IOException): PreflightResult {
        val fallbackName = FileUtils.sanitizeFileName(FileUtils.getFileName(url)) ?: "download"
        return PreflightResult(
            originalUrl = url,
            finalUrl = url,
            fileName = fallbackName,
            fileSize = null,
            contentType = null,
            contentDisposition = null,
            etag = null,
            statusCode = 0,
            isDownloadable = hasDownloadableExtension(url),
            errorMessage = e.message ?: "Connection failed"
        )
    }

    private fun logPreflightResult(
        originalUrl: String,
        finalUrl: String,
        statusCode: Int,
        size: Long?,
        fileName: String
    ) {
        // Redact query secrets / signed tokens from log
        val sanitizedOriginal = sanitizeUrlForLog(originalUrl)
        val sanitizedFinal = sanitizeUrlForLog(finalUrl)
        Logger.info(
            "PREFLIGHT",
            "url=$sanitizedOriginal final=$sanitizedFinal status=$statusCode size=${size ?: "unknown"} file=$fileName"
        )
    }

    fun sanitizeUrlForLog(url: String): String {
        return try {
            val httpUrl = url.toHttpUrlOrNull() ?: return url.substringBefore('?')
            httpUrl.newBuilder()
                .query(null)
                .fragment(null)
                .username("")
                .password("")
                .build()
                .toString()
        } catch (_: Exception) {
            url.substringBefore('?')
        }
    }
}

