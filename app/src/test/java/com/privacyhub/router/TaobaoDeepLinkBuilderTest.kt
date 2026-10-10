package com.privacyhub.router

import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun uppercaseSharedSchemeReachesExistingTaobaoDeepLinkWithoutChangingTokenCase() {
        val sharedText = "商品 HTTPS://e.tb.cn/h.MixedCase?tk=KeepCase%2F&utm_source=share"
        val extracted = assertNotNull(LinkParser.extractFirstUrl(sharedText))
        val cleaned = assertNotNull(LinkCleaner.clean(extracted))

        assertEquals(LinkTarget.TAOBAO, LinkTargetDetector.detect(sharedText))
        assertEquals("https://e.tb.cn/h.MixedCase?tk=KeepCase%2F", cleaned)
        assertEquals("taobao://e.tb.cn/h.MixedCase?tk=KeepCase%2F", TaobaoDeepLinkBuilder.build(cleaned))
    }
}
