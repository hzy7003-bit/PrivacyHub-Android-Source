package com.privacyhub.router

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

object NetdiskAccessCodeParser {
    private val parameterNames = setOf("pwd", "passcode", "access_code", "accesscode")
    private val labeledCode = Regex(
        pattern = "(?:提取码|访问码|提取密码|分享码|分享密码)\\s*(?:为|是)?\\s*[:：]?\\s*([A-Za-z0-9]{4,8})(?![A-Za-z0-9])",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun extract(text: String): String? {
        return extractFromUrl(LinkParser.extractFirstUrl(text))
            ?: labeledCode.find(text)?.groupValues?.getOrNull(1)?.takeIf(::isValid)
    }

    private fun extractFromUrl(url: String?): String? {
        val query = url?.let { runCatching { URI(it).rawQuery }.getOrNull() }.orEmpty()
        return query.split('&')
            .asSequence()
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = decode(part.substring(0, separator)).lowercase(Locale.ROOT)
                if (name !in parameterNames) return@mapNotNull null
                decode(part.substring(separator + 1)).takeIf(::isValid)
            }
            .firstOrNull()
    }

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    private fun isValid(value: String): Boolean {
        return value.length in 4..8 && value.all { it.isLetterOrDigit() }
    }
}
