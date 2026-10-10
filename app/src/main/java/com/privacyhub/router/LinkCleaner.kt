package com.privacyhub.router

import java.net.URI

object LinkCleaner {
    private val trackingParams = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "spm",
        "from",
        "share_id",
        "inviteCode"
    )

    fun clean(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val rawQuery = uri.rawQuery ?: return url

        val keptQuery = rawQuery
            .split("&")
            .filter { part ->
                val name = part.substringBefore("=")
                name !in trackingParams
            }
            .joinToString("&")

        // The URI component constructor escapes '%' again. Retain the original raw path and
        // fragment while replacing only the parsed query, so valid escapes stay byte-for-byte.
        val queryStart = url.indexOf('?')
        val fragmentStart = url.indexOf('#', queryStart + 1)
        val suffix = if (fragmentStart >= 0) url.substring(fragmentStart) else ""
        return url.substring(0, queryStart) +
            (if (keptQuery.isBlank()) "" else "?$keptQuery") + suffix
    }
}
