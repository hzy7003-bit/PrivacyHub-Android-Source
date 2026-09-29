package com.privacyhub.presentation.pro

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.clipboard.SafeClipboard
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProLicenseStore
import com.privacyhub.pro.ProLicenseCheck
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.util.ActivityTaskScope

class ProLicenseActivity : SecureActivity() {
    private val tasks by lazy { ActivityTaskScope(this) }
    private lateinit var licenseInput: EditText
    private lateinit var activationInput: EditText
    private var operationInFlight = false

    override fun onUnlocked() {
        if (operationInFlight) return
        render()
    }

    override fun onDestroy() {
        tasks.close()
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("Pro 授权"))
        root.addView(subtitle("离线验证；只有你主动分享激活请求时，才会发送设备指纹哈希。"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply {
            text = "正在读取授权状态…"
            textSize = 14f
            setTextColor(FrostedUi.textSecondary)
        })
        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
        setContentView(root)

        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
                val store = ProLicenseStore(app)
                val state = store.currentState()
                LicenseScreenState(state, store.isStoredDataUnreadable(state), store.deviceCode().getOrNull())
            },
            onResult = { owner, result -> (owner as ProLicenseActivity).renderState(result) }
        )
    }

    private fun renderState(result: Result<LicenseScreenState>) {
        val screenState = result.getOrElse { error ->
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                FrostedUi.applyPageChrome(this)
                addView(title("暂时无法读取 Pro 授权"))
                addView(subtitle(error.message ?: "请稍后重试。"))
                addView(fullWidthButton("重试", ButtonKind.Primary) { render() })
                addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
            }
            setContentView(root)
            return
        }
        val state = screenState.license
        val storedDataUnreadable = screenState.storedDataUnreadable
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("Pro 授权"))
        root.addView(subtitle("离线验证；只有你主动分享激活请求时，才会发送设备指纹哈希。"))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(card(heading = statusHeading(state, storedDataUnreadable), body = buildStatusText(state)))

        val deviceCode = screenState.deviceCode
        content.addView(sectionTitle("本机设备码"))
        content.addView(
            card(
                "付款后发给开发者",
                deviceCode?.value ?: "无法生成本机设备码，请稍后重试。"
            )
        )
        if (deviceCode != null) {
            content.addView(fullWidthButton("复制或分享设备码", ButtonKind.Secondary) {
                shareDeviceCode(deviceCode)
            })
        }

        licenseInput = EditText(this).apply {
            hint = "粘贴最终激活码（PHLIC1...）或旧 License"
            FrostedUi.styleInput(this)
        }
        content.addView(sectionTitle("离线激活"))
        content.addView(licenseInput)
        content.addView(fullWidthButton("激活 Pro", ButtonKind.Primary) {
            activateLicense()
        })
        if (state.license != null || storedDataUnreadable) {
            content.addView(fullWidthButton("移除本机 License", ButtonKind.Danger) {
                confirmClearLicense()
            })
        }

        val license = state.license
        if (license?.requiresDeviceBinding == true) {
            val binding = state.deviceBinding
            content.addView(sectionTitle("离线设备绑定"))
            content.addView(
                card(
                    "本机绑定状态",
                    binding?.message ?: "需要为此设备导入开发者签发的激活凭证。"
                )
            )
            if (binding?.active == true) {
                content.addView(fullWidthButton("移除本机设备绑定", ButtonKind.Danger) {
                    confirmRemoveDeviceBinding()
                })
            } else if (storedDataUnreadable) {
                content.addView(fullWidthButton("移除不可读取的本机设备绑定", ButtonKind.Danger) {
                    confirmRemoveDeviceBinding()
                })
            } else {
                content.addView(fullWidthButton("分享本机激活请求", ButtonKind.Secondary) {
                    shareActivationRequest()
                })
                activationInput = EditText(this).apply {
                    hint = "粘贴设备激活凭证（PHACT1...）"
                    FrostedUi.styleInput(this)
                }
                content.addView(activationInput)
                content.addView(fullWidthButton("绑定本机设备", ButtonKind.Primary) {
                    activateDevice()
                })
            }
        }

        content.addView(sectionTitle("可解锁功能"))
        ProFeature.entries.forEach { feature ->
            content.addView(card(feature.label, feature.description))
        }

        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
        setContentView(root)
    }

    private fun buildStatusText(state: com.privacyhub.pro.ProLicenseCheck): String {
        val license = state.license
        if (license == null) return state.message
        val expires = license.expiresAtMillis?.let { "到期时间：${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(it))}" }
            ?: "有效期：永久"
        val features = if (license.allFeatures) {
            "功能：全部 Pro 功能"
        } else {
            "功能：" + license.features.joinToString("、") { it.label }
        }
        val deviceBinding = state.deviceBinding?.takeIf { it.required }?.let { binding ->
            "\n设备绑定：${binding.message}"
        }.orEmpty()
        return "持有人：${license.holder}\n编号：${license.licenseId}\n$expires\n$features$deviceBinding"
    }

    private fun activateLicense() {
        if (operationInFlight) return
        val text = licenseInput.text?.toString().orEmpty()
        val app = applicationContext
        operationInFlight = true
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { ProLicenseStore(app).activate(text) },
            onResult = { owner, result ->
                val activity = owner as ProLicenseActivity
                activity.operationInFlight = false
                result.fold(
                    onSuccess = { check ->
                        Toast.makeText(activity, check.message, Toast.LENGTH_SHORT).show()
                        activity.render()
                    },
                    onFailure = { error -> activity.showOperationFailure(error) }
                )
            }
        )
    }

    private fun shareActivationRequest() {
        if (operationInFlight) return
        operationInFlight = true
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { ProLicenseStore(app).createDeviceActivationRequest() },
            onResult = { owner, result ->
                val activity = owner as ProLicenseActivity
                activity.operationInFlight = false
                result.fold(
                    onSuccess = { activationResult -> activity.showActivationRequest(activationResult) },
                    onFailure = { error -> activity.showOperationFailure(error) }
                )
            }
        )
    }

    private fun showActivationRequest(result: com.privacyhub.pro.DeviceActivationRequestResult) {
        val request = result.request
        if (!result.success || request == null) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("本机激活请求")
            .setMessage("此请求仅包含 License 编号、设备指纹哈希和设备上限。发送给开发者后可获得本机激活凭证。\n\n${request.requestText}")
            .setNegativeButton("取消", null)
            .setNeutralButton("复制请求") { _, _ ->
                val copied = SafeClipboard.copySensitiveText(this, "Privacy Hub activation request", request.requestText)
                Toast.makeText(this, if (copied) "已安全复制，60 秒后自动清除" else "复制失败", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("分享") { _, _ ->
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, request.requestText)
                        },
                        "发送本机激活请求"
                    )
                )
            }
            .show()
    }

    private fun shareDeviceCode(deviceCode: com.privacyhub.pro.DeviceCode) {
        AlertDialog.Builder(this)
            .setTitle("本机设备码")
            .setMessage("设备码只包含本机设备指纹哈希。付款后发送给开发者，即可获得一条最终激活码。\n\n${deviceCode.value}")
            .setNegativeButton("取消", null)
            .setNeutralButton("复制设备码") { _, _ ->
                val copied = SafeClipboard.copySensitiveText(this, "Privacy Hub device code", deviceCode.value)
                Toast.makeText(this, if (copied) "已安全复制，60 秒后自动清除" else "复制失败", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("分享") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, deviceCode.value)
                }, "发送本机设备码"))
            }
            .show()
    }

    private fun activateDevice() {
        if (operationInFlight) return
        val text = activationInput.text?.toString().orEmpty()
        val app = applicationContext
        operationInFlight = true
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { ProLicenseStore(app).activateDevice(text) },
            onResult = { owner, result ->
                val activity = owner as ProLicenseActivity
                activity.operationInFlight = false
                result.fold(
                    onSuccess = { check ->
                        Toast.makeText(activity, check.message, Toast.LENGTH_SHORT).show()
                        activity.render()
                    },
                    onFailure = { error -> activity.showOperationFailure(error) }
                )
            }
        )
    }

    private fun confirmRemoveDeviceBinding() {
        AlertDialog.Builder(this)
            .setTitle("移除本机设备绑定")
            .setMessage("移除后，这台手机将不能使用此 License 的 Pro 功能。该操作不会自动释放开发者台账中的设备名额；重新绑定其它设备时，需要导入新的激活凭证。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移除") { _, _ ->
                runLicenseMutation(
                    task = { it.removeDeviceActivation() },
                    onSuccess = { activity, removed ->
                        Toast.makeText(activity, if (removed) "已移除本机设备绑定" else "移除失败，原有绑定数据已保留", Toast.LENGTH_SHORT).show()
                        activity.render()
                    }
                )
            }
            .show()
    }

    private fun confirmClearLicense() {
        AlertDialog.Builder(this)
            .setTitle("移除本机 License")
            .setMessage("只会移除这台手机保存的 License，不影响你手里的 License 文本。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移除") { _, _ ->
                runLicenseMutation(
                    task = { it.clear() },
                    onSuccess = { activity, removed ->
                        Toast.makeText(activity, if (removed) "已移除本机 License" else "移除失败，原有授权数据已保留", Toast.LENGTH_SHORT).show()
                        activity.render()
                    }
                )
            }
            .show()
    }

    private fun statusHeading(
        state: com.privacyhub.pro.ProLicenseCheck,
        storedDataUnreadable: Boolean
    ): String {
        return when {
            state.active -> "Pro 已激活"
            storedDataUnreadable -> "Pro 授权数据不可读取"
            state.license?.requiresDeviceBinding == true -> "等待本机设备绑定"
            else -> "Pro 未激活"
        }
    }

    private fun runLicenseMutation(
        task: (ProLicenseStore) -> Boolean,
        onSuccess: (ProLicenseActivity, Boolean) -> Unit
    ) {
        if (operationInFlight) return
        operationInFlight = true
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { task(ProLicenseStore(app)) },
            onResult = { owner, result ->
                val activity = owner as ProLicenseActivity
                activity.operationInFlight = false
                result.fold(onSuccess = { onSuccess(activity, it) }, onFailure = activity::showOperationFailure)
            }
        )
    }

    private fun showOperationFailure(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("操作未完成")
            .setMessage(error.message ?: "授权状态未更改，请重试。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun card(heading: String, body: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            FrostedUi.applyPanel(this, radiusDp = 24)
            addView(TextView(this@ProLicenseActivity).apply {
                text = heading
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(FrostedUi.textPrimary)
            })
            addView(TextView(this@ProLicenseActivity).apply {
                text = body
                textSize = 14f
                setTextColor(FrostedUi.textSecondary)
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1.0f)
            })
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
        }
    }

    private fun fullWidthButton(text: String, kind: ButtonKind, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        FrostedUi.styleMaterialButton(this, kind)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(12)
        }
    }

    private fun title(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(FrostedUi.textPrimary)
    }

    private fun subtitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setPadding(0, dp(6), 0, 0)
    }

    private fun sectionTitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(FrostedUi.textMuted)
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class LicenseScreenState(
        val license: ProLicenseCheck,
        val storedDataUnreadable: Boolean,
        val deviceCode: com.privacyhub.pro.DeviceCode?
    )
}
