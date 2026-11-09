package xdm.manager

import xdm.core.network.http.HeaderMap

/**
 * Result of validating a candidate replacement link for an interrupted or expired download.
 */
sealed class LinkValidationResult {
    data class Compatible(
        val finalUrl: String,
        val contentLength: Long?,
        val lastModified: String?,
        val eTag: String?,
        val acceptsRanges: Boolean,
        val headers: HeaderMap?,
        val cookie: String?,
    ) : LinkValidationResult()

    data class Incompatible(
        val reason: IncompatibleReason,
        val details: String,
        val replacementLength: Long?,
        val expectedLength: Long?,
    ) : LinkValidationResult()

    enum class IncompatibleReason {
        SIZE_MISMATCH,
        NO_RANGE_SUPPORT,
        CONTENT_CHANGED,
        NOT_FOUND,
        SERVER_ERROR,
        NETWORK_ERROR,
    }
}

