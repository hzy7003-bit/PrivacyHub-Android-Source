package com.privacyhub.presentation.vault

import android.content.Intent
import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import com.privacyhub.automation.NetdiskAccessibilityStatus
import com.privacyhub.automation.NetdiskAccessCodeTaskController
import com.privacyhub.automation.NetdiskHostPolicy
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.VaultItem
import com.privacyhub.presentation.automation.NetdiskAutomationSettingsActivity
import com.privacyhub.presentation.pro.ProLicenseActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProGate
import com.privacyhub.router.AppIntentRouter
import com.privacyhub.router.BrowserFallback
import com.privacyhub.router.LinkParser
import com.privacyhub.router.OpenResult
import com.privacyhub.util.AppExecutors

internal class VaultLinkActionPanelFactory(
    private val activity: Activity,
    private val store: LocalStore,
    private val settingsStore: AppSettingsStore,
    private val currentContent: () -> String,
    private val onOpened: () -> Unit = {}
) {
    fun create(item: VaultItem): LinearLayout? {
        val url = savedLink(item)
        if (url.isNullOrBlank() && item.target.packageName == null) return null
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
            addView(TextView(activity).apply {
                text = description(item, url)
                textSize = 13f
                setTextColor(FrostedUi.textSecondary)
                setPadding(0, dp(4), 0, dp(8))
            })
            addView(actionButton(
                openButtonLabel(url, item.target),
                ButtonKind.Secondary,
                backgroundColor = FrostedUi.primaryContainer
            ) {
                openSavedLink(item, url)
            }.apply {
                setTextColor(FrostedUi.accent)
            })
            if (item.target.supportsAccessCodeAutomation) {
                val accessCode = item.accessCode
                val label = if (accessCode.isNullOrBlank()) "保存提取码后可自动填入" else "打开并填入提取码"
                addView(actionButton(label, ButtonKind.Secondary, topMargin = 8) {
                    if (accessCode.isNullOrBlank()) {
                        Toast.makeText(activity, "请先填写提取码并保存", Toast.LENGTH_SHORT).show()
                    } else {
                        openAndFillAccessCode(item, url, accessCode)
                    }
                })
            }
        }
    }

    private fun description(item: VaultItem, url: String?): String {
        return if (url.isNullOrBlank()) {
            if (item.target.usesTaobaoSearchFallback) {
                "未提取到网页链接，将通过淘宝搜索 URL 发送口令；不会写入系统剪切板。"
            } else {
                "未提取到网页链接，将直接打开${item.target.displayName} App；不会自动复制口令。"
            }
        } else {
            "优先打开对应 App，失败时自动用浏览器打开。"
        }
    }

    private fun openAndFillAccessCode(item: VaultItem, url: String?, accessCode: String) {
        val factoryRef = WeakReference(this)
        val app = activity.applicationContext
        AppExecutors.executeDiskResult(
            task = { ProGate(app).check(ProFeature.AccessibilityAutomation) },
            onResult = { result ->
                val factory = factoryRef.get() ?: return@executeDiskResult
                if (factory.activity.isFinishing || factory.activity.isDestroyed) return@executeDiskResult
                result.fold(
                    onSuccess = { gate ->
                        if (!gate.allowed) {
                            Toast.makeText(factory.activity, gate.message, Toast.LENGTH_SHORT).show()
                            factory.activity.startActivity(Intent(factory.activity, ProLicenseActivity::class.java))
                        } else {
                            factory.continueOpenAndFillAccessCode(item, url, accessCode)
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            factory.activity,
                            error.message ?: "暂时无法验证 Pro 授权",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )
    }

    private fun continueOpenAndFillAccessCode(item: VaultItem, url: String?, accessCode: String) {
        val activity = activity
        if (!settingsStore.isNetdiskAutomationEnabled()) {
            Toast.makeText(activity, "请先开启网盘自动填码", Toast.LENGTH_SHORT).show()
            activity.startActivity(Intent(activity, NetdiskAutomationSettingsActivity::class.java))
            return
        }
        if (!NetdiskAccessibilityStatus.isServiceEnabled(activity)) {
            Toast.makeText(activity, "请先在系统设置开启网盘自动填码服务", Toast.LENGTH_SHORT).show()
            activity.startActivity(Intent(activity, NetdiskAutomationSettingsActivity::class.java))
            return
        }
        val targetHost = NetdiskHostPolicy.targetHost(url, item.target)
        if (targetHost == null) {
            recordAutomationFailure(item, "无法安全确认目标网页", "自动填码未执行；目标网页身份无法确认")
            Toast.makeText(activity, "无法安全确认网盘页面，未自动填码", Toast.LENGTH_SHORT).show()
            return
        }
        val browserPackage = BrowserFallback.resolveBrowserPackage(activity, url)
        if (browserPackage.isNullOrBlank()) {
            recordAutomationFailure(item, "未找到可用浏览器", "自动填码未执行；未能解析浏览器")
            Toast.makeText(activity, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
            return
        }
        val task = NetdiskAccessCodeTaskController.start(
            activity,
            accessCode,
            item.target,
            targetHost,
            browserPackage,
            fallbackBrowserPackageName = null
        )
        val result = BrowserFallback.open(activity, url)
        if (!result.success) {
            NetdiskAccessCodeTaskController.cancel(task.id)
            recordAutomationFailure(item, result.failureReason, "自动填码未执行；浏览器分享页未打开")
            Toast.makeText(activity, result.failureReason ?: "打开失败", Toast.LENGTH_SHORT).show()
            return
        }
        result.openedPackageName?.let { NetdiskAccessCodeTaskController.updateExpectedPackage(task.id, it) }
        onOpened()
        Toast.makeText(activity, "已在浏览器打开，正在等待提取码输入框", Toast.LENGTH_SHORT).show()
    }

    private fun recordAutomationFailure(item: VaultItem, reason: String?, note: String) {
        val target = item.target
        AppExecutors.executeDisk {
            runCatching { store.recordReport(EntryPoint.AUTOMATION, false, reason, target, note) }
        }
    }

    private fun savedLink(item: VaultItem): String? {
        return item.normalizedLink?.takeIf(String::isNotBlank)
            ?: item.originalLink?.takeIf(String::isNotBlank)
            ?: LinkParser.extractFirstUrl(currentContent())
    }

    private fun openSavedLink(item: VaultItem, url: String?) {
        val result = AppIntentRouter.openTargetAppThenFallback(activity, url, item.target, currentContent())
        if (result.success) onOpened()
        val entryPoint = item.entryPoint
        val success = result.success
        val failure = result.failureReason
        val target = item.target
        val note = result.routeNote ?: "从安全箱详情打开已保存链接"
        AppExecutors.executeDisk {
            runCatching { store.recordReport(entryPoint, success, failure, target, note) }
        }
        Toast.makeText(activity, toastForOpenResult(result), Toast.LENGTH_SHORT).show()
    }

    private fun openButtonLabel(url: String?, target: LinkTarget): String = when {
        target == LinkTarget.TAOBAO_PAYMENT -> "打开淘宝代付"
        url.isNullOrBlank() && target == LinkTarget.TAOBAO_FLASH_DELIVERY -> "去淘宝闪购代付"
        url.isNullOrBlank() && target.usesTaobaoSearchFallback -> "用淘宝搜索"
        url.isNullOrBlank() && target.packageName != null -> "打开${target.displayName}"
        target.packageName != null -> "打开${target.displayName}"
        else -> "打开链接"
    }

    private fun toastForOpenResult(result: OpenResult): String = when {
        result.success && result.usedFallback -> "目标 App 未接住，已用浏览器打开"
        result.success -> "已发起打开"
        else -> result.failureReason ?: "打开失败"
    }

    private fun title(label: String): TextView = TextView(activity).apply {
        text = label
        textSize = 16f
        setTextColor(FrostedUi.textPrimary)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun actionButton(
        label: String,
        kind: ButtonKind,
        topMargin: Int = 4,
        backgroundColor: Int? = null,
        action: () -> Unit
    ): Button {
        return Button(activity).apply {
            text = label
            FrostedUi.styleMaterialButton(
                this,
                if (backgroundColor == FrostedUi.primaryContainer) ButtonKind.Primary else kind
            )
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(topMargin), 0, 0)
            }
        }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
