// Copyright 2026 Subhra Das Gupta
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-XDM-Android-Official-Store-Builds
// Additional terms and the exception: xdm-android/NOTICE

package xdm.core.util

import xdm.core.util.FileUtils.decodeFileName
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

private val URL_PATTERN by lazy { Regex("https?://([^/:]*)/?") }

/**
 * True when a refusal means the link itself stopped working (an expired signature, token or
 * session) rather than a server error: 403 Forbidden, 410 Gone, or a 401 whose challenge is not
 * Basic, NTLM or Negotiate. Those are a login the user can answer (see `HttpAuth`), so not an expiry.
 */
fun isLinkExpiredStatus(code: Int, wwwAuthenticate: String?): Boolean = when (code) {
    403, 410 -> true
    401 -> LOGIN_SCHEMES.none { wwwAuthenticate?.contains(it, ignoreCase = true) == true }
    else -> false
}

private val LOGIN_SCHEMES = listOf("basic", "ntlm", "negotiate")

fun getHeaders(key: String, headers: Map<String, List<String>>?): List<String>? {
    val keyName = headers?.keys?.find { StringUtils.equalsIgnoreCase(it, key) }
    return keyName?.let { return headers[keyName] }
}

fun getHeader(key: String, headers: Map<String, List<String>>?): String? {
    val values = getHeaders(key, headers)
    return values?.isNotEmpty()?.let { values[0] }
}


fun getContentLength(headers: Map<String, List<String>>?): Long? {
    // A malformed value is no length at all; it must not throw at the caller.
    return headers?.let { getHeader("content-length", headers)?.toLongOrNull() }
}

/**
 * The whole file's size. For a 206, Content-Length is only the range's, so the total from
 * Content-Range ("bytes 0-99/600") wins; otherwise Content-Length. Null when neither says.
 */
fun getFileSize(headers: Map<String, List<String>>?): Long? {
    val total = getHeader("content-range", headers)?.substringAfterLast('/', "")?.trim()?.toLongOrNull()
    return total ?: getContentLength(headers)
}

private val fmt by lazy {
    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }
}

fun getModifiedDate(headers: Map<String, List<String>>?): Long? {
    try {
        return getHeader("last-modified", headers)?.let { fmt.parse(it).time }
    } catch (ex: Exception) {
        Logger.info(ex)
    }
    return null
}

fun getRetryDelay(retryAfter: String?, defaultDelay: Long = 5): Long {
    if (retryAfter != null) {
        try {
            // First, try to parse as a delta-seconds value
            return retryAfter.toLong()
        } catch (e: NumberFormatException) {
            // If it's not a number, try to parse as an HTTP-date
            try {
                val date = fmt.parse(retryAfter)
                val delaySeconds = (date.time - System.currentTimeMillis()) / 1000
                return if (delaySeconds > 0) delaySeconds else 0
            } catch (ex: Exception) {
                Logger.error("Failed to parse Retry-After header as date: $retryAfter", ex)
            }
        }
    }
    return defaultDelay
}

fun getFileName(
    oldName: String,
    keepFileName: Boolean,
    url: String,
    contentDisposition: String? = null,
    contentType: String? = null
): String {
    val newFileName = getFileName(url, contentDisposition, contentType)
    if (!keepFileName) {
        return newFileName
    }

    if (newFileName.endsWith(".html") && !oldName.endsWith(".html")) {
        return "$oldName.html"
    }
    return fixExtension(oldName, contentType, replace = false)
}

private fun splitHeaderParameters(header: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var escape = false
    for (ch in header) {
        if (escape) {
            current.append(ch)
            escape = false
        } else if (ch == '\\') {
            escape = true
        } else if (ch == '"') {
            inQuotes = !inQuotes
            current.append(ch)
        } else if (ch == ';' && !inQuotes) {
            result.add(current.toString().trim())
            current.setLength(0)
        } else {
            current.append(ch)
        }
    }
    if (current.isNotEmpty()) {
        result.add(current.toString().trim())
    }
    return result
}

// Parses the RFC 5987/6266 extended form: filename*=charset'lang'percent-encoded-value
private fun getExtendedContentDisposition(header: String): String? {
    try {
        for (segment in splitHeaderParameters(header)) {
            val str = segment.trim { it <= ' ' }
            if (!str.lowercase(Locale.ROOT).startsWith("filename*")) continue
            val eq = str.indexOf('=')
            if (eq < 0) continue
            var value = str.substring(eq + 1).trim { it <= ' ' }.removeSurrounding("\"")
            // Strip the leading charset'lang' prefix (if present) up to the last quote.
            val firstQuote = value.indexOf('\'')
            val lastQuote = value.lastIndexOf('\'')
            val charset = if (firstQuote in 0 until lastQuote) {
                val cs = value.substring(0, firstQuote).trim()
                value = value.substring(lastQuote + 1)
                cs
            } else "UTF-8"
            value = value.replace("\"", "").removeSurrounding("'").trim { it <= ' ' }
            if (value.isEmpty()) continue
            val name = try {
                URLDecoder.decode(value.replace("+", "%2B"), charset.ifEmpty { "UTF-8" })
            } catch (_: Exception) {
                decodeFileName(value)
            }
            if (name.isNotEmpty()) return name
        }
    } catch (e: Exception) {
        Logger.info(e)
    }
    return null
}

fun getNameFromContentDisposition(header: String?): String? {
    try {
        if (header == null) return null
        val headerLow = header.lowercase(Locale.ROOT)
        if (headerLow.startsWith("attachment") || headerLow.startsWith("inline") || headerLow.contains("filename")) {
            // RFC 6266: prefer the extended (filename*) form when present.
            getExtendedContentDisposition(header)?.let { return it }
            val segments = splitHeaderParameters(header)
            for (str in segments) {
                val low = str.lowercase(Locale.ROOT)
                // Skip the extended form here; it is handled above.
                if (low.startsWith("filename") && !low.startsWith("filename*")) {
                    val index = str.indexOf('=')
                    if (index < 0) continue
                    val file = str.substring(index + 1).replace("\"", "").removeSurrounding("'").trim { it <= ' ' }
                    if (file.isEmpty()) continue
                    val name = try {
                        decodeFileName(file)
                    } catch (e: Exception) {
                        file
                    }
                    if (name.isNotEmpty()) return name
                }
            }
        }
    } catch (e: Exception) {
        //NOOP
    }
    return null
}

/**
 * A single download's name: Content-Disposition's, else the URL's. Like a browser, a name with no
 * extension gets the one its Content-Type implies; an existing extension is never replaced.
 *
 * The Content-Disposition name is the server's choice, so it is sanitized like the URL's: Windows
 * silently drops a trailing dot or space, so `setup.exe.` would be saved as `setup.exe` while XDM
 * (and the Zone.Identifier stream it writes) still addressed `setup.exe.`.
 */
fun getFileName(url: String, contentDisposition: String? = null, contentType: String? = null): String {
    getNameFromContentDisposition(contentDisposition)?.let { FileUtils.sanitizeFileName(it) }?.let {
        return fixExtension(it, contentType, replace = false)
    }
    val pathName = FileUtils.getFileName(url)
    val ext = pathName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext.isEmpty() || ext in SCRIPT_EXTENSIONS) {
        extractCandidateFileNameFromUrl(url)?.let {
            return fixExtension(it, contentType, replace = false)
        }
    }
    if (pathName.isNotEmpty() && pathName != "FILE") {
        if (StringUtils.containsIgnoreCase(contentType, "text/html")) {
            if (pathName.endsWith(".html")) {
                return pathName
            }
            return "$pathName.html"
        }
        return fixExtension(pathName, contentType, replace = false)
    }
    return "FILE"
}

//fun getFileName(uri: String?): String {
//    try {
//        if (uri == null) return "FILE"
//        if (uri == "/" || uri.isEmpty()) {
//            return "FILE"
//        }
//        var str = uri
//        var index = str.indexOf("?")
//        if (index != -1) {
//            str = str.substring(0, index)
//        }
//        if (str.endsWith("/")) {
//            return getFileNameFromUrlHost(str)
//        }
//
//        index = str.lastIndexOf("/")
//        if(index!=-1){
//            return
//        }
//        var path: String = uri
//        if (x > -1) {
//            path = uri.substring(x)
//        }
//        val qindex = path.indexOf("?")
//        if (qindex > -1) {
//            path = path.substring(0, qindex)
//        }
//        path = FileUtils.decodeFileName(path)
//        if (path.isEmpty()) return "FILE"
//        if (path == "/") return "FILE"
//        return FileUtils.sanitizeFileName(path)
//    } catch (e: Exception) {
//        Logger.log(e)
//        return "FILE"
//    }
//}
//
//fun getFileNameFromUrlHost(url: String): String {
//    URL_PATTERN.find(url)?.let {
//        if (it.groupValues.size > 1) return it.groupValues[1]
//    }
//    return "FILE"
//}