package com.privacyhub.router

import com.privacyhub.domain.model.LinkTarget
import java.net.URI
import java.util.Locale

object LinkTargetDetector {
    fun detect(text: String): LinkTarget {
        val url = LinkParser.extractFirstUrl(text)
        val host = url?.let { runCatching { URI(it).host.orEmpty() }.getOrDefault("") }
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val normalized = text.lowercase(Locale.ROOT)

        return when {
            isTaobaoFlashDeliveryPay(text) -> LinkTarget.TAOBAO_FLASH_DELIVERY
            isTaobaoPayment(text, host) -> LinkTarget.TAOBAO_PAYMENT
            host.contains("taobao.com") ||
                host.contains("tmall.com") ||
                host == "tb.cn" ||
                host.endsWith(".tb.cn") ||
                hasTaobaoToken(text) -> LinkTarget.TAOBAO
            host.contains("jd.com") || host.contains("jingdong.com") -> LinkTarget.JD
            host.contains("pinduoduo.com") || host.contains("yangkeduo.com") -> LinkTarget.PINDUODUO
            host.contains("xiaohongshu.com") || host.contains("xhslink.com") -> LinkTarget.XIAOHONGSHU
            host.contains("douyin.com") || host.contains("iesdouyin.com") || host.contains("amemv.com") -> LinkTarget.DOUYIN
            host.contains("kuaishou.com") || host.contains("gifshow.com") || host.contains("kshurl.com") -> LinkTarget.KUAISHOU
            host.contains("bilibili.com") || host == "b23.tv" || host.endsWith(".b23.tv") -> LinkTarget.BILIBILI
            host.contains("weibo.com") || host == "t.cn" || host.endsWith(".t.cn") -> LinkTarget.WEIBO
            host == "pan.baidu.com" || host.endsWith(".pan.baidu.com") -> LinkTarget.BAIDU_NETDISK
            host == "pan.quark.cn" || host == "drive.quark.cn" || host.endsWith(".pan.quark.cn") || host.endsWith(".drive.quark.cn") -> LinkTarget.QUARK_NETDISK
            host == "pan.xunlei.com" || host == "cloud.xunlei.com" || host.endsWith(".pan.xunlei.com") || host.endsWith(".cloud.xunlei.com") -> LinkTarget.XUNLEI_NETDISK
            host == "www.aliyundrive.com" || host == "aliyundrive.com" || host == "www.alipan.com" || host == "alipan.com" || host.endsWith(".alipan.com") -> LinkTarget.ALIYUN_DRIVE
            host.contains("lanzou") || host == "lanzn.com" || host.endsWith(".lanzn.com") -> LinkTarget.LANZOU_CLOUD
            host == "123pan.com" || host.endsWith(".123pan.com") || host == "123684.com" || host.endsWith(".123684.com") -> LinkTarget.PAN123
            host == "cloud.189.cn" || host.endsWith(".cloud.189.cn") -> LinkTarget.TIANYI_CLOUD
            host == "caiyun.139.com" || host == "yun.139.com" || host.endsWith(".caiyun.139.com") || host.endsWith(".yun.139.com") -> LinkTarget.CHINA_MOBILE_CLOUD
            url != null -> LinkTarget.WEB
            normalized.contains("http") -> LinkTarget.WEB
            else -> LinkTarget.UNKNOWN
        }
    }

    private fun hasTaobaoToken(text: String): Boolean {
        return text.contains("淘口令") ||
            text.contains("淘宝口令") ||
            text.contains("购物车里的好宝贝") ||
            Regex("""[￥¥¢₴£€₤][^￥¥¢₴£€₤\s]{4,80}[￥¥¢₴£€₤]""").containsMatchIn(text)
    }

    private fun isTaobaoFlashDeliveryPay(text: String): Boolean {
        if (!text.contains("淘宝闪购")) return false
        return listOf("帮我付", "找人代付", "请好友代付", "代付").any(text::contains)
    }

    private fun isTaobaoPayment(text: String, host: String): Boolean {
        val isTaobaoLink = host.contains("taobao.com") || host.contains("tmall.com") ||
            host == "tb.cn" || host.endsWith(".tb.cn")
        if (!isTaobaoLink) return false
        return listOf("拜托你帮我代付", "帮我代付", "找人代付", "请好友代付", "代付一笔").any(text::contains)
    }
}
