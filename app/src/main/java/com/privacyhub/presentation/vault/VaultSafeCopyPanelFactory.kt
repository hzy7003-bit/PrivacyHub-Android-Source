package com.privacyhub.presentation.vault

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.clipboard.SafeClipboard
import com.privacyhub.domain.model.VaultItem
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.UiMetrics

internal class VaultSafeCopyPanelFactory(
    private val activity: VaultDetailActivity,
    private val currentContent: () -> String
) {
    fun create(item: VaultItem): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPanel(this, radiusDp = 20)
            setPadding(dp(UiMetrics.Content), dp(12), dp(UiMetrics.Content), dp(12))
            addView(title("安全复制"))
            addView(TextView(activity).apply {
                text = "复制后 60 秒会尝试清空系统剪切板；输入法或系统剪切板历史不一定能清除。"
                textSize = 13f
                setTextColor(FrostedUi.textSecondary)
                setPadding(0, dp(4), 0, dp(8))
            })
            copyActions(item).forEachIndexed { index, action ->
                addView(copyButton(action, index))
            }
        }
    }

    private fun copyButton(action: CopyAction, index: Int) = Button(activity).apply {
        text = action.label
        val primary = action.label == "复制净化链接"
        if (primary) {
            FrostedUi.styleTonalButton(this)
            setTextColor(FrostedUi.accent)
        } else {
            FrostedUi.styleOutlinedButton(this)
        }
        setOnClickListener { safeCopy(action) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            if (index > 0) topMargin = dp(UiMetrics.Small)
        }
    }

    private fun copyActions(item: VaultItem): List<CopyAction> {
        val actions = mutableListOf<CopyAction>()
        item.normalizedLink?.takeIf(String::isNotBlank)?.let { actions += CopyAction("复制净化链接", it) }
        item.originalLink?.takeIf { it.isNotBlank() && it != item.normalizedLink }
            ?.let { actions += CopyAction("复制原始链接", it) }
        SensitiveContentExtractor.identityCards(item.content).forEachIndexed { index, value ->
            actions += CopyAction(if (index == 0) "复制身份证号" else "复制身份证号 ${index + 1}", value)
        }
        SensitiveContentExtractor.bankCards(item.content).forEachIndexed { index, value ->
            actions += CopyAction(if (index == 0) "复制银行卡号" else "复制银行卡号 ${index + 1}", value)
        }
        SensitiveContentExtractor.verificationCodes(item.content).firstOrNull()
            ?.let { actions += CopyAction("复制验证码", it) }
        SensitiveContentExtractor.password(item.content)?.let { actions += CopyAction("复制口令", it) }
        if (item.category == "地址") actions += CopyAction("复制地址", currentContent())
        actions += CopyAction("复制全部内容", currentContent())
        return actions.distinctBy { it.label to it.value }
    }

    private fun safeCopy(action: CopyAction) {
        val copied = SafeClipboard.copySensitiveText(activity, action.label, action.value)
        Toast.makeText(
            activity,
            if (copied) "已复制，60 秒后尝试清空系统剪切板" else "复制失败",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun title(label: String): TextView = TextView(activity).apply {
        text = label
        textSize = 16f
        setTextColor(FrostedUi.textPrimary)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private data class CopyAction(val label: String, val value: String)
}
