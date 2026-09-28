package com.privacyhub.router

import java.net.URI
import java.util.Locale

object TaobaoDeepLinkBuilder {
    fun build(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        if (uri.host.orEmpty().lowercase(Locale.ROOT) != "e.tb.cn") return null

        return "taobao://${url.substringAfter("://")}"
    }
}
