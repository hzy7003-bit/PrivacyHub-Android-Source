package com.privacyhub.presentation.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.notification.PersistentSaveNotificationService
import com.privacyhub.presentation.notification.SaveNotificationManager
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi

class BackgroundHealthActivity : SecureActivity() {
    private val settingsStore by lazy { AppSettingsStore(this) }

    override fun onUnlocked() {
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("通知和后台检测"))
        root.addView(subtitle("给试用用户截图反馈用，不联网，不读取安全箱原文"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val checks = buildChecks()
        content.addView(summaryCard(checks))
        checks.forEach { content.addView(checkCard(it)) }
        content.addView(guideCard())
        content.addView(actionCard())

        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) {
            finish()
        })
        setContentView(root)
    }

    private fun buildChecks(): List<HealthCheck> {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationEnabled = isNotificationPermissionGranted(notificationManager)
        val channelEnabled = isPersistentChannelEnabled(notificationManager)
        val notificationAlive = isPersistentNotificationVisible(notificationManager)
        val serviceAlive = isPersistentServiceRunning()
        val batteryUnrestricted = isIgnoringBatteryOptimizations()
        val appSwitchEnabled = settingsStore.isPersistentNotificationEnabled()
        val bootRestoreEnabled = settingsStore.isBootNotificationRestoreEnabled()

        return listOf(
            HealthCheck(
                title = "App 通知权限",
                ok = notificationEnabled,
                detail = if (notificationEnabled) "系统允许隐私中转站发通知。" else "系统通知权限未开启，通知栏按钮无法稳定显示。",
                fix = "到系统“应用通知”里允许通知。"
            ),
            HealthCheck(
                title = "通知渠道",
                ok = channelEnabled,
                detail = if (channelEnabled) "常驻保存按钮的通知渠道可用。" else "常驻保存按钮的通知渠道被关闭或被系统降到不可显示。",
                fix = "到系统通知设置里打开“常驻保存按钮”渠道。"
            ),
            HealthCheck(
                title = "App 内通知开关",
                ok = appSwitchEnabled,
                detail = if (appSwitchEnabled) "App 内已经开启通知栏保存按钮。" else "App 内没有开启通知栏保存按钮。",
                fix = "回到设置页，开启“通知栏保存按钮”。"
            ),
            HealthCheck(
                title = "前台服务状态",
                ok = serviceAlive,
                detail = if (serviceAlive) "前台服务当前正在运行。" else "前台服务当前不在运行，通知可能已经被系统清理。",
                fix = "点击本页“重启通知栏按钮”，再检查系统电池限制。"
            ),
            HealthCheck(
                title = "通知是否存在",
                ok = notificationAlive,
                detail = if (notificationAlive) "系统当前还能看到隐私中转站的常驻通知。" else "系统当前看不到常驻通知，可能被清理、被折叠或被权限拦截。",
                fix = "重启通知栏按钮；如果仍消失，检查电池和通知设置。"
            ),
            HealthCheck(
                title = "电池优化",
                ok = batteryUnrestricted,
                detail = if (batteryUnrestricted) {
                    "系统已把隐私中转站排除出电池优化，后台更稳定。"
                } else {
                    "系统仍可能按省电策略限制隐私中转站。三星、vivo、小米等 ROM 上这会明显影响通知常驻。"
                },
                fix = "到系统电池设置里设为“不受限制”“允许后台活动”或关闭省电限制。"
            ),
            HealthCheck(
                title = "开机/更新后恢复",
                ok = bootRestoreEnabled,
                detail = if (bootRestoreEnabled) "手机重启或 App 更新后，会尝试恢复通知栏保存按钮。" else "手机重启或 App 更新后，不会自动恢复通知栏保存按钮。",
                fix = "在设置页开启“开机恢复通知”。部分 ROM 还需要允许自启动。"
            )
        )
    }

    private fun isNotificationPermissionGranted(notificationManager: NotificationManager): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isPersistentChannelEnabled(notificationManager: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = notificationManager.getNotificationChannel(SaveNotificationManager.CHANNEL_ID)
            ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun isPersistentNotificationVisible(notificationManager: NotificationManager): Boolean {
        return runCatching {
            notificationManager.activeNotifications.any {
                it.id == SaveNotificationManager.NOTIFICATION_ID && it.packageName == packageName
            }
        }.getOrDefault(false)
    }

    private fun isPersistentServiceRunning(): Boolean {
        val manager = getSystemService(ActivityManager::class.java)
        return runCatching {
            @Suppress("DEPRECATION")
            manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.packageName == packageName &&
                    it.service.className == PersistentSaveNotificationService::class.java.name
            }
        }.getOrDefault(false)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return runCatching {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        }.getOrDefault(false)
    }

    private fun summaryCard(checks: List<HealthCheck>): LinearLayout {
        val failed = checks.count { !it.ok }
        return card {
            addView(cardTitle(if (failed == 0) "当前检测：基本正常" else "当前检测：$failed 项需要处理"))
            addView(cardBody(buildString {
                appendLine("设备：${Build.BRAND} ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android：${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
                appendLine("ROM：${Build.DISPLAY}")
                appendLine()
                append("如果朋友反馈通知栏消失，请让他打开这一页截图。")
            }))
        }
    }

    private fun checkCard(check: HealthCheck): LinearLayout {
        return card {
            addView(cardTitle("${if (check.ok) "正常" else "需处理"}：${check.title}"))
            addView(cardBody("${check.detail}\n\n建议：${check.fix}"))
        }
    }

    private fun guideCard(): LinearLayout {
        val samsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true) ||
            Build.BRAND.contains("samsung", ignoreCase = true)
        val brandLine = if (samsung) {
            "这台是三星设备，重点检查：电池“不受限制”、允许后台活动、通知渠道、最近任务里锁定 App。"
        } else {
            "不同 ROM 名称不一样，重点找：电池优化、自启动、后台活动、通知渠道、最近任务锁定。"
        }
        return card {
            addView(cardTitle("系统限制说明"))
            addView(cardBody(buildString {
                appendLine(brandLine)
                appendLine()
                appendLine("抖音这类 App 可能有系统白名单、媒体/播放类前台服务、推送通道或厂商合作策略。普通本地工具不能承诺同等保活。")
                appendLine()
                append("隐私中转站不会为了保活去申请无障碍、悬浮窗、Root、后台拉活或联网唤醒。通知栏按钮是增强入口，不是唯一入口。")
            }))
        }
    }

    private fun actionCard(): LinearLayout {
        return card {
            addView(cardTitle("一键排查"))
            addView(cardBody("这些按钮只打开系统设置或重启本地前台服务，不上传数据。"))
            addView(fullWidthButton("刷新检测", ButtonKind.Secondary) {
                render()
            })
            addView(fullWidthButton("重启通知栏按钮", ButtonKind.Primary) {
                settingsStore.setPersistentNotificationEnabled(true)
                SaveNotificationManager(this@BackgroundHealthActivity).showPersistentSaveNotification()
                Toast.makeText(this@BackgroundHealthActivity, "已尝试重启通知栏按钮", Toast.LENGTH_SHORT).show()
                render()
            })
            addView(fullWidthButton("打开应用通知设置", ButtonKind.Secondary) {
                openAppNotificationSettings()
            })
            addView(fullWidthButton("打开电池优化设置", ButtonKind.Secondary) {
                openBatterySettings()
            })
            addView(fullWidthButton("打开应用详情设置", ButtonKind.Secondary) {
                openAppDetailsSettings()
            })
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun openBatterySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.onFailure {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun card(configure: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPanel(this, radiusDp = 22)
            setPadding(dp(14), dp(12), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(12))
            }
            configure()
        }
    }

    private fun cardTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(FrostedUi.textPrimary)
    }

    private fun cardBody(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setPadding(0, dp(8), 0, dp(10))
    }

    private fun fullWidthButton(text: String, kind: ButtonKind, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        FrostedUi.styleMaterialButton(this, kind)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(4), 0, 0)
        }
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 26f
        setTextColor(FrostedUi.textPrimary)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun subtitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setPadding(0, dp(6), 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class HealthCheck(
        val title: String,
        val ok: Boolean,
        val detail: String,
        val fix: String
    )
}
