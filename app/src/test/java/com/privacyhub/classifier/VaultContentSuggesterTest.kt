package com.privacyhub.classifier

import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultContentSuggesterTest {
    @Test
    fun suggestsNetdiskCategory() {
        val suggestion = VaultContentSuggester.suggest(
            text = "https://pan.baidu.com/s/abc123?pwd=9x8y \u63d0\u53d6\u7801\uff1a9x8y",
            primaryUrl = "https://pan.baidu.com/s/abc123?pwd=9x8y",
            target = LinkTarget.BAIDU_NETDISK
        )

        assertEquals("\u767e\u5ea6\u7f51\u76d8\u94fe\u63a5", suggestion.title)
        assertEquals("\u7f51\u76d8", suggestion.category)
    }

    @Test
    fun suggestsShoppingForEcommerceLinks() {
        val suggestion = VaultContentSuggester.suggest(
            text = "https://item.taobao.com/item.htm?id=123",
            primaryUrl = "https://item.taobao.com/item.htm?id=123",
            target = LinkTarget.TAOBAO
        )

        assertEquals("淘宝链接", suggestion.title)
        assertEquals("购物", suggestion.category)
    }

    @Test
    fun suggestsContentForMediaPlatforms() {
        val suggestion = VaultContentSuggester.suggest(
            text = "https://www.xiaohongshu.com/explore/demo",
            primaryUrl = "https://www.xiaohongshu.com/explore/demo",
            target = LinkTarget.XIAOHONGSHU
        )

        assertEquals("小红书链接", suggestion.title)
        assertEquals("内容", suggestion.category)
    }

    @Test
    fun suggestsIdentityCategory() {
        val suggestion = VaultContentSuggester.suggest(
            text = "身份证 110105199001011234",
            primaryUrl = null,
            target = LinkTarget.UNKNOWN
        )

        assertEquals("证件提示", suggestion.title)
        assertEquals("证件", suggestion.category)
    }

    @Test
    fun suggestsBankCardCategory() {
        val suggestion = VaultContentSuggester.suggest(
            text = "银行卡 6222 0202 0000 0000 000",
            primaryUrl = null,
            target = LinkTarget.UNKNOWN
        )

        assertEquals("银行卡提示", suggestion.title)
        assertEquals("银行卡", suggestion.category)
    }

    @Test
    fun suggestsVerificationCodeCategory() {
        val suggestion = VaultContentSuggester.suggest(
            text = "验证码 238914，十分钟内有效",
            primaryUrl = null,
            target = LinkTarget.UNKNOWN
        )

        assertEquals("验证码", suggestion.title)
        assertEquals("验证码", suggestion.category)
    }

    @Test
    fun suggestsPasswordCategory() {
        val suggestion = VaultContentSuggester.suggest(
            text = "账号 demo@example.com 密码提示：生日加后缀",
            primaryUrl = null,
            target = LinkTarget.UNKNOWN
        )

        assertEquals("口令备注", suggestion.title)
        assertEquals("口令", suggestion.category)
    }

    @Test
    fun suggestsAddressCategory() {
        val suggestion = VaultContentSuggester.suggest(
            text = "收货地址：上海市浦东新区世纪大道 100 号",
            primaryUrl = null,
            target = LinkTarget.UNKNOWN
        )

        assertEquals("地址", suggestion.title)
        assertEquals("地址", suggestion.category)
    }

    @Test
    fun suggestsLinkForPlainWeb() {
        val suggestion = VaultContentSuggester.suggest(
            text = "https://example.com/read",
            primaryUrl = "https://example.com/read",
            target = LinkTarget.WEB
        )

        assertEquals("普通网页链接", suggestion.title)
        assertEquals("链接", suggestion.category)
    }
}
