package com.privacyhub.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaobaoDeepLinkBuilderTest {
    @Test
    fun convertsEShortLinkAndKeepsToken() {
        assertEquals(
            "taobao://e.tb.cn/h.85w3dcCGbp9NAhU?tk=PZsrgCchFyB",
            TaobaoDeepLinkBuilder.build(
                "https://e.tb.cn/h.85w3dcCGbp9NAhU?tk=PZsrgCchFyB"
            )
        )
    }

    @Test
    fun doesNotConvertOtherLinks() {
        assertNull(TaobaoDeepLinkBuilder.build("https://example.com/item?id=1"))
    }
}
