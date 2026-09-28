package com.privacyhub.domain.model

enum class LinkTarget(
    val displayName: String,
    val packageName: String?,
    val usesTaobaoSearchFallback: Boolean = false,
    val supportsAccessCodeAutomation: Boolean = false
) {
    TAOBAO("\u6dd8\u5b9d", "com.taobao.taobao", usesTaobaoSearchFallback = true),
    TAOBAO_PAYMENT("\u6dd8\u5b9d\u4ee3\u4ed8", "com.taobao.taobao"),
    TAOBAO_FLASH_DELIVERY("\u6dd8\u5b9d\u95ea\u8d2d\u4ee3\u4ed8", "com.taobao.taobao", usesTaobaoSearchFallback = true),
    JD("\u4eac\u4e1c", "com.jingdong.app.mall"),
    PINDUODUO("\u62fc\u591a\u591a", "com.xunmeng.pinduoduo"),
    XIAOHONGSHU("\u5c0f\u7ea2\u4e66", "com.xingin.xhs"),
    DOUYIN("\u6296\u97f3", "com.ss.android.ugc.aweme"),
    KUAISHOU("\u5feb\u624b", "com.smile.gifmaker"),
    BILIBILI("B\u7ad9", "tv.danmaku.bili"),
    WEIBO("\u5fae\u535a", "com.sina.weibo"),
    BAIDU_NETDISK("\u767e\u5ea6\u7f51\u76d8", "com.baidu.netdisk", supportsAccessCodeAutomation = true),
    QUARK_NETDISK("\u5938\u514b\u7f51\u76d8", "com.quark.browser", supportsAccessCodeAutomation = true),
    XUNLEI_NETDISK("\u8fc5\u96f7\u4e91\u76d8", "com.xunlei.downloadprovider", supportsAccessCodeAutomation = true),
    ALIYUN_DRIVE("\u963f\u91cc\u4e91\u76d8", "com.alicloud.databox"),
    LANZOU_CLOUD("\u84dd\u594f\u4e91", null),
    PAN123("123\u7f51\u76d8", "com.mfcloudcalculate.networkdisk"),
    TIANYI_CLOUD("\u5929\u7ffc\u4e91\u76d8", "com.cn21.ecloud"),
    CHINA_MOBILE_CLOUD("\u79fb\u52a8\u4e91\u76d8", "com.chinamobile.mcloud"),
    WEB("\u666e\u901a\u7f51\u9875", null),
    UNKNOWN("\u672a\u8bc6\u522b", null)
}
