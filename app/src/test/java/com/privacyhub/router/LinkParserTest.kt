package com.privacyhub.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkParserTest {
    @Test
    fun preservesBalancedParenthesesAtTheEndOfAUrl() {
        val url = "https://en.wikipedia.org/wiki/Function_(mathematics)"
        assertEquals(url, LinkParser.extractFirstUrl(url))
    }

    @Test
    fun preservesNestedParenthesesInPathsQueriesAndFragments() {
        listOf(
            "https://example.com/a_(b_(c))",
            "https://example.com/search?q=(a(b))",
            "https://example.com/page#section_(one)"
        ).forEach { url -> assertEquals(url, LinkParser.extractFirstUrl(url)) }
    }

    @Test
    fun removesOnlyTheSurroundingProseParenthesis() {
        val url = "https://example.com/a_(b)"
        assertEquals(url, LinkParser.extractFirstUrl("See ($url)."))
        assertEquals(url, LinkParser.extractFirstUrl("See (($url))."))
    }

    @Test
    fun separatesSentencePunctuationFromBalancedParentheses() {
        val url = "https://example.com/a_(b)"
        assertEquals(url, LinkParser.extractFirstUrl("$url!"))
        assertEquals(url, LinkParser.extractFirstUrl("See ($url.), next"))
    }

    @Test
    fun stillRemovesUnmatchedClosingParenthesesAndSentencePunctuation() {
        assertEquals("https://example.com/a", LinkParser.extractFirstUrl("(https://example.com/a),"))
        assertEquals("https://example.com/a", LinkParser.extractFirstUrl("https://example.com/a).;!?"))
    }

    @Test
    fun doesNotDecodeOrRewriteEscapedParentheses() {
        val url = "https://example.com/a%28b%29?q=%2F%3F#%29"
        assertEquals(url, LinkParser.extractFirstUrl("($url)"))
    }

    @Test
    fun preservesParenthesesBeforeTheEndOfTheUrl() {
        val url = "https://example.com/a_(b)?id=123#section"
        assertEquals(url, LinkParser.extractFirstUrl(url))
    }

    @Test
    fun keepsTheFirstLinkAndExistingChinesePunctuationBoundary() {
        assertEquals(
            "https://example.com/a_(b)",
            LinkParser.extractFirstUrl("看看这个：https://example.com/a_(b)。另一个 https://example.com/other")
        )
    }

    @Test
    fun returnsNullWhenNoHttpLinkExists() {
        assertNull(LinkParser.extractFirstUrl("普通文本（没有链接）"))
        assertNull(LinkParser.extractFirstUrl("ftp://example.com/file"))
    }

    @Test
    fun handlesLongRunsOfWrappingParentheses() {
        val url = "https://example.com/a_(b)"
        assertEquals(url, LinkParser.extractFirstUrl(url + ")".repeat(100_000) + "."))
    }

    @Test
    fun acceptsHttpSchemesInAnyAsciiCaseAndNormalizesOnlyTheScheme() {
        listOf("http", "HTTP", "hTtP", "https", "HTTPS", "hTtPs").forEach { scheme ->
            val suffix = "://Example.COM/CaseSensitive?q=KeepCase%2F#Section_(One)"
            assertEquals(
                scheme.lowercase() + suffix,
                LinkParser.extractFirstUrl(scheme + suffix)
            )
        }
    }

    @Test
    fun doesNotSkipAnUppercaseFirstLinkForALaterLowercaseOne() {
        assertEquals(
            "https://example.com/First",
            LinkParser.extractFirstUrl("HTTPS://example.com/First https://example.com/second")
        )
    }

    @Test
    fun doesNotBroadenAcceptedSchemes() {
        listOf("javascript:alert(1)", "file:///tmp/example", "taobao://e.tb.cn/h.demo", "httpſ://example.com").forEach {
            assertNull(LinkParser.extractFirstUrl(it))
        }
    }
}
