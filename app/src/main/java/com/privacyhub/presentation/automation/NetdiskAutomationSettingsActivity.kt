package com.privacyhub.presentation.automation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.automation.NetdiskAccessibilityStatus
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.diagnostics.DiagnosticsActivity
import com.privacyhub.presentation.pro.ProLicenseActivity
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.pro.ProGateResult
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProGate
import com.privacyhub.util.ActivityTaskScope

class NetdiskAutomationSettingsActivity : SecureActivity() {
    private val settingsStore by lazy { AppSettingsStore(this) }
    private val tasks by lazy { ActivityTaskScope(this) }
    private var toggleInFlight = false

    override fun onSecureCreate(savedInstanceState: Bundle?) = Unit

    override fun onUnlocked() {
        // A pending toggle shares this task scope and must retain its completion callback.
        if (toggleInFlight) return
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { ProGate(app).check(ProFeature.AccessibilityAutomation) },
            onResult = { owner, result ->
                val activity = owner as NetdiskAutomationSettingsActivity
                val gate = result.getOrElse { error ->
                    ProGateResult(false, "暂时无法验证 Pro 授权", error.message ?: "请稍后重试。")
                }
                activity.render(gate)
            }
        )
    }

    override fun onDestroy() {
        tasks.close()
        super.onDestroy()
    }

    private fun render(gate: ProGateResult) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("网盘自动填码 Beta"))
        root.addView(body("仅在你点击“打开并填入提取码”后执行。不会点击确认、下载、登录、支付或发送。"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(card(
            "Pro 授权",
            if (gate.allowed) "当前授权可使用网盘自动填码。" else gate.message,
            if (gate.allowed) "已解锁" else "管理 Pro 授权"
        ) {
            if (!gate.allowed) startActivity(Intent(this, ProLicenseActivity::class.java))
        })

        val serviceEnabled = NetdiskAccessibilityStatus.isServiceEnabled(this)
        content.addView(card(
            "系统无障碍服务",
            if (serviceEnabled) {
                "系统服务已开启。它只在本次 60 秒任务内检查当前窗口的输入框标记。"
            } else {
                "需要在 Android 系统设置中手动开启。系统会提示该服务可查看当前屏幕内容，这是识别第三方输入框的必要能力。"
            },
            if (serviceEnabled) "已开启" else "打开系统设置",
            ButtonKind.Secondary
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        val masterEnabled = settingsStore.isNetdiskAutomationEnabled()
        content.addView(card(
            "自动填码总开关",
            if (masterEnabled) "已允许。关闭后即使系统无障碍仍开着，也不会执行填码。" else "默认关闭。开启前会校验 Pro 授权；系统无障碍仍需单独手动开启。",
            if (masterEnabled) "关闭自动填码" else "允许自动填码",
            if (masterEnabled) ButtonKind.Secondary else ButtonKind.Primary
        ) {
            toggleAutomation(masterEnabled, gate)
        })

        content.addView(card(
            "支持范围",
            "普通“打开链接”仍优先对应 App；“打开并填入提取码”会直接打开本机浏览器的分享页，以便只填写明确标注“提取码/访问码”的输入框。找不到就不填。",
            "查看本地诊断"
        ) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })

        root.addView(button("返回", ButtonKind.Secondary) { finish() })
        setContentView(root)
    }

    private fun toggleAutomation(masterEnabled: Boolean, displayedGate: ProGateResult) {
        if (toggleInFlight) return
        toggleInFlight = true
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
                val store = AppSettingsStore(app)
                val current = store.isNetdiskAutomationEnabled()
                if (current != masterEnabled) {
                    return@executeDisk ToggleAutomationResult(current)
                }
                val gate = if (current) displayedGate else ProGate(app).check(ProFeature.AccessibilityAutomation)
                if (!current && !gate.allowed) {
                    return@executeDisk ToggleAutomationResult(current, deniedGate = gate)
                }
                store.setNetdiskAutomationEnabled(!current)
                ToggleAutomationResult(!current)
            },
            onResult = { owner, result ->
                val activity = owner as NetdiskAutomationSettingsActivity
                activity.toggleInFlight = false
                result.fold(
                    onSuccess = { toggle ->
                        if (toggle.deniedGate != null) {
                            Toast.makeText(activity, toggle.deniedGate.message, Toast.LENGTH_SHORT).show()
                            activity.startActivity(Intent(activity, ProLicenseActivity::class.java))
                        } else {
                            activity.onUnlocked()
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(activity, error.message ?: "无法更改自动填码设置", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    private fun card(
        title: String,
        body: String,
        action: String,
        kind: ButtonKind = ButtonKind.Secondary,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPanel(this, radiusDp = 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) }
            addView(TextView(this@NetdiskAutomationSettingsActivity).apply {
                text = title; textSize = 16f; setTextColor(FrostedUi.textPrimary)
            })
            addView(body(body))
            addView(button(action, kind, onClick))
        }
    }

    private fun title(value: String) = TextView(this).apply {
        text = value; textSize = 26f; setTextColor(FrostedUi.textPrimary)
    }

    private fun body(value: String) = TextView(this).apply {
        text = value; textSize = 14f; setTextColor(FrostedUi.textSecondary); setPadding(0, dp(8), 0, dp(8))
    }

    private fun button(label: String, kind: ButtonKind, action: () -> Unit) = Button(this).apply {
        text = label
        when (kind) {
            ButtonKind.Primary -> FrostedUi.styleTonalButton(this)
            ButtonKind.Danger -> FrostedUi.styleDangerOutlinedButton(this)
            ButtonKind.Secondary, ButtonKind.Plain -> FrostedUi.styleOutlinedButton(this)
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class ToggleAutomationResult(
        val enabled: Boolean,
        val deniedGate: ProGateResult? = null
    )
}
