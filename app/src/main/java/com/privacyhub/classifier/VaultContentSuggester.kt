package com.privacyhub.classifier

import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.router.LinkParser
import com.privacyhub.util.Formatters

object VaultContentSuggester {
    fun suggest(text: String, primaryUrl: String?, target: LinkTarget): VaultContentSuggestion {
        val normalized = text.lowercase()
        val url = primaryUrl ?: LinkParser.extractFirstUrl(text)
        val category = suggestCategory(text, normalized, url, target)
        val title = suggestTitle(text, url, target, category)
        return VaultContentSuggestion(title = title, category = category)
    }

    private fun suggestCategory(
        rawText: String,
        normalized: String,
        url: String?,
        target: LinkTarget
    ): String {
        return when {
            target in shoppingTargets -> CAT_SHOPPING
            target in contentTargets -> CAT_CONTENT
            target in netdiskTargets -> CAT_NETDISK
            hasIdentityCard(rawText) -> CAT_IDENTITY
            hasBankCard(rawText) -> CAT_BANK_CARD
            hasVerificationCode(rawText, normalized) -> CAT_VERIFICATION_CODE
            hasPasswordHint(normalized) -> CAT_PASSWORD
            hasAddressHint(rawText, normalized) -> CAT_ADDRESS
            !url.isNullOrBlank() -> CAT_LINK
            else -> CAT_TEXT
        }
    }

    private fun suggestTitle(text: String, url: String?, target: LinkTarget, category: String): String {
        return when {
            target != LinkTarget.UNKNOWN && !url.isNullOrBlank() -> "${target.displayName}\u94fe\u63a5"
            target != LinkTarget.UNKNOWN && url.isNullOrBlank() -> "${target.displayName}\u53e3\u4ee4"
            category == CAT_IDENTITY -> "\u8bc1\u4ef6\u63d0\u793a"
            category == CAT_BANK_CARD -> "\u94f6\u884c\u5361\u63d0\u793a"
            category == CAT_VERIFICATION_CODE -> "\u9a8c\u8bc1\u7801"
            category == CAT_PASSWORD -> "\u53e3\u4ee4\u5907\u6ce8"
            category == CAT_ADDRESS -> "\u5730\u5740"
            !url.isNullOrBlank() -> "\u7f51\u9875\u94fe\u63a5"
            else -> Formatters.preview(text, 24).ifBlank { CAT_TEXT }
        }
    }

    private fun hasIdentityCard(text: String): Boolean {
        return Regex("""(?<![0-9A-Za-z])\d{17}[\dXx](?![0-9A-Za-z])""").containsMatchIn(text) ||
            text.contains("\u8eab\u4efd\u8bc1") ||
            text.contains("\u8bc1\u4ef6\u53f7")
    }

    private fun hasBankCard(text: String): Boolean {
        val compactNumbers = Regex("""(?:\d[ -]?){16,19}""")
            .findAll(text)
            .map { it.value.filter(Char::isDigit) }
        return compactNumbers.any { it.length in 16..19 } ||
            text.contains("\u94f6\u884c\u5361") ||
            text.contains("\u5361\u53f7")
    }

    private fun hasVerificationCode(rawText: String, normalized: String): Boolean {
        return normalized.contains("\u9a8c\u8bc1\u7801") ||
            normalized.contains("\u6821\u9a8c\u7801") ||
            Regex("""(?<!\d)\d{4,8}(?!\d)""").containsMatchIn(rawText) &&
            (normalized.contains("code") || normalized.contains("\u52a8\u6001\u7801"))
    }

    private fun hasPasswordHint(normalized: String): Boolean {
        return listOf(
            "\u5bc6\u7801",
            "\u53e3\u4ee4",
            "\u8d26\u53f7",
            "\u8d26\u6237",
            "\u767b\u5f55",
            "login",
            "password",
            "passcode"
        ).any(normalized::contains)
    }

    private fun hasAddressHint(rawText: String, normalized: String): Boolean {
        val hasRegionWord = listOf(
            "\u7701",
            "\u5e02",
            "\u533a",
            "\u53bf",
            "\u9547",
            "\u8857\u9053",
            "\u5c0f\u533a",
            "\u8def",
            "\u53f7\u697c",
            "\u6536\u8d27\u5730\u5740"
        ).any(rawText::contains)
        val hasReceiverWord = listOf(
            "\u6536\u4ef6\u4eba",
            "\u6536\u8d27\u4eba",
            "\u5730\u5740",
            "\u95e8\u724c",
            "\u5feb\u9012"
        ).any(normalized::contains)
        return hasReceiverWord || (hasRegionWord && rawText.length >= 8)
    }

    private val shoppingTargets = setOf(
        LinkTarget.TAOBAO,
        LinkTarget.TAOBAO_PAYMENT,
        LinkTarget.TAOBAO_FLASH_DELIVERY,
        LinkTarget.JD,
        LinkTarget.PINDUODUO
    )
    private val contentTargets = setOf(
        LinkTarget.XIAOHONGSHU,
        LinkTarget.DOUYIN,
        LinkTarget.KUAISHOU,
        LinkTarget.BILIBILI,
        LinkTarget.WEIBO
    )
    private val netdiskTargets = setOf(
        LinkTarget.BAIDU_NETDISK,
        LinkTarget.QUARK_NETDISK,
        LinkTarget.XUNLEI_NETDISK,
        LinkTarget.ALIYUN_DRIVE,
        LinkTarget.LANZOU_CLOUD,
        LinkTarget.PAN123,
        LinkTarget.TIANYI_CLOUD,
        LinkTarget.CHINA_MOBILE_CLOUD
    )

    private const val CAT_SHOPPING = "\u8d2d\u7269"
    private const val CAT_CONTENT = "\u5185\u5bb9"
    private const val CAT_NETDISK = "\u7f51\u76d8"
    private const val CAT_IDENTITY = "\u8bc1\u4ef6"
    private const val CAT_BANK_CARD = "\u94f6\u884c\u5361"
    private const val CAT_VERIFICATION_CODE = "\u9a8c\u8bc1\u7801"
    private const val CAT_PASSWORD = "\u53e3\u4ee4"
    private const val CAT_ADDRESS = "\u5730\u5740"
    private const val CAT_LINK = "\u94fe\u63a5"
    private const val CAT_TEXT = "\u6587\u672c"
}

data class VaultContentSuggestion(
    val title: String,
    val category: String
)
