package com.privacyhub.router

import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkTargetDetectorTest {
    @Test
    fun detectsTaobao() {
        assertEquals(
            LinkTarget.TAOBAO,
            LinkTargetDetector.detect("https://item.taobao.com/item.htm?id=123&spm=demo")
        )
    }

    @Test
    fun detectsTaobaoEShortLinkFromSharedProductText() {
        assertEquals(
            LinkTarget.TAOBAO,
            LinkTargetDetector.detect(
                "【淘宝】7天无理由退货 https://e.tb.cn/h.85w3dcCGbp9NAhU?tk=PZsrgCchFyB CZ028 " +
                    "「WOSPORT直销 战地荣耀护耳版面具 户外骑行透气钢丝战术面具」"
            )
        )
    }

    @Test
    fun detectsTaobaoTokenWithoutUrl() {
        assertEquals(
            LinkTarget.TAOBAO,
            LinkTargetDetector.detect("69 HU287 2:/₴jLVGgmfCPPH£三快来看我购物车里的好宝贝~")
        )
    }

    @Test
    fun detectsTaobaoFlashDeliveryPayTokenWithoutUrl() {
        assertEquals(
            LinkTarget.TAOBAO_FLASH_DELIVERY,
            LinkTargetDetector.detect(
                "17 MF278 2:/\uD83D\uDDDD1glbgLEb4hU\uD83D\uDD10\u4e09\u6211\u6b63\u5728\u4f7f\u7528\u6dd8\u5b9d\u95ea\u8d2d\uff0c\u8bf7\u4f60\u5e2e\u6211\u4ed8\u4e2a\u6b3e\u5427\uff5e"
            )
        )
    }

    @Test
    fun detectsTaobaoPaymentLink() {
        assertEquals(
            LinkTarget.TAOBAO_PAYMENT,
            LinkTargetDetector.detect(
                "08🗝3bRAgpFemHP£ https://m.tb.cn/h.RzLrD9a CZ007 拜托你帮我代付一笔￥2098.00元的订单"
            )
        )
    }

    @Test
    fun doesNotTreatOrdinaryTaobaoFlashDeliveryTextAsPayRequest() {
        assertEquals(
            LinkTarget.UNKNOWN,
            LinkTargetDetector.detect("淘宝闪购今日\u4f18\u60e0")
        )
    }

    @Test
    fun detectsJd() {
        assertEquals(
            LinkTarget.JD,
            LinkTargetDetector.detect("https://item.jd.com/100012043978.html")
        )
    }

    @Test
    fun detectsPinduoduo() {
        assertEquals(
            LinkTarget.PINDUODUO,
            LinkTargetDetector.detect("https://mobile.yangkeduo.com/goods.html?goods_id=123")
        )
    }

    @Test
    fun detectsXiaohongshu() {
        assertEquals(
            LinkTarget.XIAOHONGSHU,
            LinkTargetDetector.detect("https://www.xiaohongshu.com/explore/demo")
        )
    }

    @Test
    fun detectsDouyin() {
        assertEquals(
            LinkTarget.DOUYIN,
            LinkTargetDetector.detect("https://v.douyin.com/demo/")
        )
    }

    @Test
    fun detectsKuaishou() {
        assertEquals(
            LinkTarget.KUAISHOU,
            LinkTargetDetector.detect("https://v.kuaishou.com/demo")
        )
    }

    @Test
    fun detectsBilibili() {
        assertEquals(
            LinkTarget.BILIBILI,
            LinkTargetDetector.detect("https://b23.tv/demo")
        )
    }

    @Test
    fun detectsWeibo() {
        assertEquals(
            LinkTarget.WEIBO,
            LinkTargetDetector.detect("https://weibo.com/123/demo")
        )
    }

    @Test
    fun detectsBaiduNetdisk() {
        assertEquals(
            LinkTarget.BAIDU_NETDISK,
            LinkTargetDetector.detect("https://pan.baidu.com/s/abc123?pwd=9x8y")
        )
    }

    @Test
    fun detectsQuarkNetdisk() {
        assertEquals(
            LinkTarget.QUARK_NETDISK,
            LinkTargetDetector.detect("https://pan.quark.cn/s/demo")
        )
    }

    @Test
    fun detectsXunleiNetdisk() {
        assertEquals(
            LinkTarget.XUNLEI_NETDISK,
            LinkTargetDetector.detect("https://pan.xunlei.com/s/demo")
        )
    }

    @Test
    fun detectsAliyunDrive() {
        assertEquals(
            LinkTarget.ALIYUN_DRIVE,
            LinkTargetDetector.detect("https://www.alipan.com/s/demo")
        )
    }

    @Test
    fun detectsLanzouCloud() {
        assertEquals(
            LinkTarget.LANZOU_CLOUD,
            LinkTargetDetector.detect("https://wwp.lanzouj.com/iDemo")
        )
    }

    @Test
    fun detectsPlainWeb() {
        assertEquals(
            LinkTarget.WEB,
            LinkTargetDetector.detect("https://example.com/a?utm_source=test")
        )
    }

    @Test
    fun extractsUrlWithoutChinesePunctuation() {
        assertEquals(
            "https://example.com/a",
            LinkParser.extractFirstUrl("看看这个：https://example.com/a。")
        )
    }
}
