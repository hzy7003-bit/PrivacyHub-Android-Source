package com.privacyhub.router

import java.util.Locale

object LinkParser {
    // Match ASCII HTTP(S) only; Unicode case folding can admit lookalike schemes.
    private val urlRegex = Regex("""[hH][tT][tT][pP][sS]?://[^\s"'<>，。、“”‘’]+""")

    fun extractFirstUrl(text: String): String? {
        val url = urlRegex.find(text)?.value ?: return null
        val openingParentheses = url.count { it == '(' }
        var closingParentheses = url.count { it == ')' }
        var end = url.length

        // Keep parentheses that belong to the URL (for example, a Wikipedia title).
        // Only surplus closing parentheses can be surrounding prose. Count once so
        // long shared text is trimmed in linear time rather than rescanning each suffix.
        while (end > 0) {
            when (url[end - 1]) {
                ')' -> {
                    if (closingParentheses <= openingParentheses) break
                    closingParentheses -= 1
                }
                ',', '.', ';', '!', '?', '。', '！', '？' -> Unit
                else -> break
            }
            end -= 1
        }
        // Android Intent scheme matching is case-sensitive. Normalize only the
        // recognized HTTP(S) scheme; path, query, escapes and fragment are opaque.
        val schemeEnd = url.indexOf(':')
        return url.substring(0, schemeEnd).lowercase(Locale.ROOT) + url.substring(schemeEnd, end)
    }
}
