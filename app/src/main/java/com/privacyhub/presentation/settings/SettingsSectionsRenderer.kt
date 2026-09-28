package com.privacyhub.presentation.settings

import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.data.settings.LockTimeoutOption
import com.privacyhub.data.settings.ThemeModeOption
import com.privacyhub.presentation.SettingsActivity
import com.privacyhub.presentation.autofill.AutofillCredentialActivity
import com.privacyhub.presentation.automation.NetdiskAutomationSettingsActivity
import com.privacyhub.presentation.backup.BackupActivity
import com.privacyhub.presentation.category.CategoryManagementActivity
import com.privacyhub.presentation.category.HomeTagManagementActivity
import com.privacyhub.presentation.diagnostics.BackgroundHealthActivity
import com.privacyhub.presentation.diagnostics.DiagnosticsActivity
import com.privacyhub.presentation.guide.TrialGuideActivity
import com.privacyhub.presentation.legal.ThirdPartyLicensesActivity
import com.privacyhub.presentation.pro.ProLicenseActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.pro.ProLicenseStore
import com.privacyhub.security.lock.AppLockSession
import com.privacyhub.security.lock.SecureWindow
import com.privacyhub.security.privacy.RecentsVisibility

internal class SettingsSectionsRenderer(
    private val activity: SettingsActivity,
    private val store: LocalStore,
    private val settings: AppSettingsStore,
    private val proLicenses: ProLicenseStore,
    private val views: SettingsViewFactory,
    private val rerender: () -> Unit,
    private val lockNow: () -> Unit,
    private val writeDemoClipboard: () -> Unit
) {
    fun render(content: LinearLayout) {
        renderCategories(content)
        renderDiagnostics(content)
        renderHelp(content)
        renderPro(content)
        renderAppearance(content)
        renderSecurity(content)
        renderPrivacy(content)
        renderTestTools(content)
        renderReports(content)
        renderLegal(content)
    }

    private fun renderCategories(content: LinearLayout) {
        views.renderSection(content, "分类") {
            content.addView(views.card(
                "分类管理",
                "管理自定义分类，以及把系统自动识别出的分类映射成你习惯的名字。",
                views.button("管理分类", ButtonKind.Secondary) { open(CategoryManagementActivity::class.java) }
            ))
            content.addView(views.card(
                "首页标签",
                "选择哪些标签显示在首页，调整顺序，或从首页移除。这里不会删除安全箱内容。",
                views.button("管理首页标签", ButtonKind.Secondary) { open(HomeTagManagementActivity::class.java) }
            ))
        }
    }

    private fun renderDiagnostics(content: LinearLayout) {
        views.renderSection(content, "检测") {
            content.addView(views.card(
                "通知和后台稳定性检测",
                "给试用用户截图反馈用。只检测本机状态，不联网，也不读取安全箱正文。",
                views.button("查看稳定性检测", ButtonKind.Secondary) { open(BackgroundHealthActivity::class.java) }
            ))
            content.addView(views.card(
                "本地诊断报告",
                "汇总最近的入口失败、通知状态和崩溃摘要，方便截图给开发者定位问题。",
                views.button("查看诊断报告", ButtonKind.Secondary) { open(DiagnosticsActivity::class.java) }
            ))
        }
    }

    private fun renderHelp(content: LinearLayout) {
        views.renderSection(content, "说明") {
            content.addView(views.card(
                "使用指南",
                "从中转开始，了解安全箱、通知入口、Pro 能力与隐私边界。",
                views.button("阅读使用指南", ButtonKind.Secondary) { open(TrialGuideActivity::class.java) }
            ))
        }
    }

    private fun renderPro(content: LinearLayout) {
        views.renderSection(content, "Pro") {
            val state = proLicenses.currentState()
            val license = state.license
            val needsManagement = state.active || license != null
            val body = when {
                state.active && license != null -> {
                    val features = if (license.allFeatures) "全部 Pro 功能" else license.features.joinToString("、") { it.label }
                    "已离线激活。持有人：${license.holder}\n授权编号：${license.licenseId}\n已解锁：$features"
                }
                license?.requiresDeviceBinding == true ->
                    "License 已保存，等待本机设备绑定。点击进入后分享本机激活请求，再导入开发者离线签发的设备激活凭证。"
                else ->
                    "离线 License 激活，不联网、不自动上传设备信息。需要设备绑定时，只有用户主动分享激活请求才会发送设备指纹哈希。"
            }
            content.addView(views.card(
                when {
                    state.active -> "Pro 已激活"
                    license?.requiresDeviceBinding == true -> "等待本机设备绑定"
                    else -> "Pro 授权"
                },
                body,
                views.button(
                    if (needsManagement) "管理 Pro 授权" else "输入 License",
                    if (needsManagement) ButtonKind.Secondary else ButtonKind.Primary
                ) { open(ProLicenseActivity::class.java) }
            ))
            content.addView(views.card(
                "加密离线备份",
                "Pro 可创建带独立口令的本地备份；恢复备份始终可用。备份不上传、不使用云端，也不会包含 Pro 授权或设备绑定。",
                views.button("管理加密离线备份", ButtonKind.Secondary) { open(BackupActivity::class.java) }
            ))
            content.addView(views.card(
                "系统自动填充 Beta",
                "Pro 功能。通过 Android 系统自动填充候选减少复制粘贴；不使用无障碍，不自动登录或提交。",
                views.button("管理系统自动填充", ButtonKind.Secondary) { open(AutofillCredentialActivity::class.java) }
            ))
            content.addView(views.card(
                "网盘自动填码 Beta",
                "Pro 功能。只在你主动打开网盘链接时填入提取码，需要在系统设置中手动开启无障碍服务；不会点击确认或读取后台内容。",
                views.button("管理网盘自动填码", ButtonKind.Secondary) { open(NetdiskAutomationSettingsActivity::class.java) }
            ))
        }
    }

    private fun renderAppearance(content: LinearLayout) {
        views.renderSection(content, "外观") {
            ThemeModeOption.entries.forEach { option ->
                val selected = settings.themeMode() == option
                content.addView(views.card(
                    if (selected) "${option.label}（当前）" else option.label,
                    option.description,
                    views.button(
                        if (selected) "正在使用" else "切换到${option.label}",
                        if (selected) ButtonKind.Primary else ButtonKind.Secondary
                    ) {
                        if (!selected) {
                            settings.setThemeMode(option)
                            FrostedUi.applyWindow(activity)
                            Toast.makeText(activity, "已切换到${option.label}", Toast.LENGTH_SHORT).show()
                            rerender()
                        }
                    }
                ))
            }
        }
    }

    private fun renderSecurity(content: LinearLayout) {
        views.renderSection(content, "安全锁") {
            if (!settings.isSecurityLockEnabled()) {
                content.addView(views.card(
                    "安全锁未开启",
                    "开启后，进入安全箱相关页面需要指纹、人脸或系统密码。通知栏保存不会被这个锁打断。",
                    views.button("开启安全锁", ButtonKind.Primary, lockNow)
                ))
            } else {
                content.addView(views.card(
                    "安全锁已开启",
                    "离开应用后会按下面的时间自动锁定。你也可以立即锁定安全箱。",
                    views.button("立即锁定安全箱", ButtonKind.Primary, lockNow),
                    views.button("关闭安全锁", ButtonKind.Danger) {
                        settings.setSecurityLockEnabled(false)
                        AppLockSession.markUnlocked()
                        Toast.makeText(activity, "已关闭安全锁", Toast.LENGTH_SHORT).show()
                        rerender()
                    }
                ))
                LockTimeoutOption.entries.forEach { content.addView(lockTimeoutCard(it)) }
            }
        }
    }

    private fun lockTimeoutCard(option: LockTimeoutOption): LinearLayout {
        val selected = settings.lockTimeout() == option
        return views.card(
            if (selected) "${option.label}（当前）" else option.label,
            option.description,
            views.button(
                if (selected) "正在使用" else "使用这个锁定时间",
                if (selected) ButtonKind.Primary else ButtonKind.Secondary
            ) {
                if (!selected) {
                    settings.setLockTimeout(option)
                    Toast.makeText(activity, "已设置为${option.label}", Toast.LENGTH_SHORT).show()
                    rerender()
                }
            }
        )
    }

    private fun renderPrivacy(content: LinearLayout) {
        views.renderSection(content, "隐私显示") {
            content.addView(views.card(
                "首页内容预览",
                if (settings.isHomePreviewEnabled()) "首页会显示最近保存内容的预览，查找更方便。" else "首页只显示标题和分类，不直接露出正文。",
                views.button(if (settings.isHomePreviewEnabled()) "隐藏首页预览" else "显示首页预览", ButtonKind.Secondary) {
                    settings.setHomePreviewEnabled(!settings.isHomePreviewEnabled())
                    rerender()
                }
            ))
            content.addView(views.card(
                "禁止截图",
                if (settings.isScreenshotProtectionEnabled()) "已开启。系统截图和最近任务缩略图会被系统阻止。" else "默认关闭，方便你给我发截图反馈问题。敏感信息较多时可以手动开启。",
                views.button(if (settings.isScreenshotProtectionEnabled()) "关闭禁止截图" else "开启禁止截图", ButtonKind.Secondary) {
                    settings.setScreenshotProtectionEnabled(!settings.isScreenshotProtectionEnabled())
                    SecureWindow.apply(activity)
                    rerender()
                }
            ))
            content.addView(views.card(
                "后台隐藏",
                if (settings.isHideFromRecentsEnabled()) "已开启。应用会尽量从最近任务列表隐藏，便于清后台时避开它。" else "关闭时，应用会正常出现在最近任务列表。",
                views.button(if (settings.isHideFromRecentsEnabled()) "关闭后台隐藏" else "开启后台隐藏", ButtonKind.Secondary) {
                    settings.setHideFromRecentsEnabled(!settings.isHideFromRecentsEnabled())
                    RecentsVisibility.apply(activity)
                    rerender()
                }
            ))
        }
    }

    private fun renderTestTools(content: LinearLayout) {
        views.renderSection(content, "本机测试") {
            content.addView(views.card(
                "写入测试剪切板",
                "写入一段测试文本，方便验证通知栏按钮、控制中心磁贴和读取失败记录。",
                views.button("写入测试文本", ButtonKind.Secondary, writeDemoClipboard)
            ))
        }
    }

    private fun renderReports(content: LinearLayout) {
        views.renderSection(content, "最近测试报告") {
            val reports = store.recentReports(8)
            if (reports.isEmpty()) {
                content.addView(views.emptyText("还没有测试记录。"))
            } else {
                reports.forEach { content.addView(views.reportCard(it)) }
            }
        }
    }

    private fun renderLegal(content: LinearLayout) {
        views.renderSection(content, "许可与开源组件") {
            content.addView(views.card(
                "第三方开源许可",
                "查看 SQLCipher Community Edition、Guava 和应用直接依赖的许可与归属说明。",
                views.button("查看许可", ButtonKind.Secondary) { open(ThirdPartyLicensesActivity::class.java) }
            ))
        }
    }

    private fun open(target: Class<*>) {
        activity.startActivity(Intent(activity, target))
    }
}
