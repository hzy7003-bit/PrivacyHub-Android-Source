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

        return URI(
            uri.scheme,
            uri.rawAuthority,
            uri.rawPath,
            keptQuery.ifBlank { null },
            uri.rawFragment
        ).toString()
    }
}
