package com.privacyhub.router

object LinkParser {
    private val urlRegex = Regex("""https?://[^\s"'<>，。、“”‘’]+""")
    private val trailingPunctuation = Regex("""[),.;!?。！？]+$""")

    fun extractFirstUrl(text: String): String? {
        return urlRegex.find(text)?.value?.replace(trailingPunctuation, "")
    }
}
