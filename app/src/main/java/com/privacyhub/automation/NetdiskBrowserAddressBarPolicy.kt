package com.privacyhub.automation

import java.net.URI
import java.util.Locale

internal data class NetdiskAddressBarCandidate(
    val viewIdResourceName: String?,
    val text: String?,
    val hintText: String?,
    val contentDescription: String?
)

/**
 * Extracts a host only from a known browser address-bar node. Arbitrary page text is never an
 * identity signal because it can contain links, instructions, or attacker-controlled text.
 */
internal object NetdiskBrowserAddressBarPolicy {
    private val supportedBrowserPackages = setOf(
        "com.android.chrome",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.vivo.browser",
        "org.mozilla.firefox",
        "com.quark.browser"
    )

    private val trustedAddressBarIds = setOf(
        "url_bar",
        "location_bar_edit_text",
        "mozac_browser_toolbar_url_view",
        "browser_url"
    )

    fun isSupportedBrowserPackage(packageName: String?): Boolean =
        packageName != null && packageName in supportedBrowserPackages

    fun extractHost(
        packageName: String,
        candidates: Iterable<NetdiskAddressBarCandidate>
    ): String? {
        if (packageName !in supportedBrowserPackages) return null
        val hosts = candidates
            .filter(::isTrustedAddressBar)
            .mapNotNull { parseHost(it.text ?: it.contentDescription ?: it.hintText) }
            .toSet()
        return hosts.singleOrNull()
    }

    private fun isTrustedAddressBar(candidate: NetdiskAddressBarCandidate): Boolean {
        val id = candidate.viewIdResourceName
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.lowercase(Locale.ROOT)
        return id in trustedAddressBarIds
    }

    private fun parseHost(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        return NetdiskHostPolicy.normalizeHost(uri.host)
    }
}
