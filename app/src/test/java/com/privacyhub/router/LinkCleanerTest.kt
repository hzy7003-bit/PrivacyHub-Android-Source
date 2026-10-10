package com.privacyhub.router

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkCleanerTest {
    @Test
    fun removesTrackingParams() {
        assertEquals(
            "https://example.com/path?id=123",
            LinkCleaner.clean("https://example.com/path?id=123&utm_source=wx&spm=a.b.c&from=share")
        )
    }

    @Test
    fun keepsRepeatedUsefulParams() {
        assertEquals(
            "https://example.com/path?id=1&id=2",
            LinkCleaner.clean("https://example.com/path?id=1&id=2&utm_campaign=x")
        )
    }

    @Test
    fun keepsTaobaoShortLinkToken() {
        assertEquals(
            "https://e.tb.cn/h.85w3dcCGbp9NAhU?tk=PZsrgCchFyB",
            LinkCleaner.clean("https://e.tb.cn/h.85w3dcCGbp9NAhU?tk=PZsrgCchFyB")
        )
    }

    @Test
    fun preservesExistingPercentEscapesWhileRemovingTracking() {
        val original = "https://example.com/%E8%B7%AF?q=%E4%B8%AD&utm_source=app#%E7%AB%A0"
        assertEquals(
            "https://example.com/%E8%B7%AF?q=%E4%B8%AD#%E7%AB%A0",
            LinkCleaner.clean(original)
        )
    }
}
