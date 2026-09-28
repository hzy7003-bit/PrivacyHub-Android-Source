package com.privacyhub.automation

import com.privacyhub.domain.model.LinkTarget
import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * Binds an automatic fill task to one exact, supported netdisk host.
 *
 * LinkTargetDetector is intentionally broad because it classifies user content. This policy is
 * deliberately narrower: it is an authorization boundary and never accepts arbitrary subdomains.
 */
internal object NetdiskHostPolicy {
    fun targetHost(url: String?, target: LinkTarget): String? {
        val candidate = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        return normalizeSupportedHost(target, uri.host)
    }

    fun normalizeSupportedHost(target: LinkTarget, value: String?): String? {
        val host = normalizeHost(value) ?: return null
        return host.takeIf { it in supportedHosts[target].orEmpty() }
    }

    fun normalizeHost(value: String?): String? {
        val unicodeHost = value
            ?.trim()
            ?.trimEnd('.', '\u3002', '\uFF0E', '\uFF61')
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (unicodeHost.any { it.isWhitespace() || it.isISOControl() } ||
            unicodeHost.any { it == '/' || it == '\\' || it == ':' || it == '@' || it == '[' || it == ']' }
        ) {
            return null
        }

        val asciiHost = runCatching {
            IDN.toASCII(unicodeHost, IDN.USE_STD3_ASCII_RULES)
        }.getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.trimEnd('.')
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_HOST_LENGTH }
            ?: return null

        val labels = asciiHost.split('.')
        if (labels.any { label ->
                label.isEmpty() ||
                    label.length > MAX_LABEL_LENGTH ||
                    label.first() == '-' ||
                    label.last() == '-' ||
                    label.any { character ->
                        character !in 'a'..'z' && character !in '0'..'9' && character != '-'
                    }
            }
        ) {
            return null
        }
        return asciiHost
    }

    private val supportedHosts: Map<LinkTarget, Set<String>> = mapOf(
        LinkTarget.BAIDU_NETDISK to setOf("pan.baidu.com"),
        LinkTarget.QUARK_NETDISK to setOf("pan.quark.cn", "drive.quark.cn"),
        LinkTarget.XUNLEI_NETDISK to setOf("pan.xunlei.com", "cloud.xunlei.com")
    )

    private const val MAX_HOST_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
}
