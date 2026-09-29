package com.privacyhub.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.privacyhub.data.db.PrivacyHubDatabaseProvider
import com.privacyhub.data.db.DatabaseAccessCoordinator
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.device.DeviceInfoProvider
import com.privacyhub.domain.model.TestReport
import com.privacyhub.security.keystore.DatabaseKeyManager
import com.privacyhub.util.Formatters

class DiagnosticReportBuilder(private val context: Context) {
    private val appContext = context.applicationContext
    private val store = LocalStore(appContext)
    private val settingsStore = AppSettingsStore(appContext)

    fun build(): DiagnosticReport = DatabaseAccessCoordinator.withDatabaseAccess {
        val recentReports = store.recentReports(limit = 20)
        val failures = recentReports.filterNot { it.success }.take(20)
        DiagnosticReport(
            generatedAt = Formatters.time(System.currentTimeMillis()),
            appVersion = appVersion(),
            deviceInfo = deviceInfo(),
            securityInfo = securityInfo(),
            healthChecks = healthChecks(),
            recentFailures = failures.map(::reportLine),
            recentEvents = recentReports.map(::reportLine),
            lastCrash = CrashRecorder(appContext).lastCrashSummary() ?: "无"
        )
    }

    private fun appVersion(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return "${packageInfo.versionName} ($versionCode)"
    }

    private fun deviceInfo(): List<String> {
        val device = DeviceInfoProvider.current()
        return listOf(
            "Android ${device.androidVersion} / SDK ${device.sdkInt}",
            "品牌：${device.brand}",
            "厂商：${device.manufacturer}",
            "型号：${device.model}",
            "ROM：${device.rom}"
        )
    }

    private fun securityInfo(): List<String> {
        val notificationStatus = if (Build.VERSION.SDK_INT < 33) {
            "通知权限：系统无需运行时授权"
        } else if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            "通知权限：已允许"
        } else {
            "通知权限：未允许（PH-PERM-001）"
        }
        return listOf(
            notificationStatus,
            "通知栏保存按钮：${if (settingsStore.isPersistentNotificationEnabled()) "已开启（前台服务）" else "未开启"}",
            "开机恢复通知：${if (settingsStore.isBootNotificationRestoreEnabled()) "已启用" else "未启用"}",
            "后台策略：基础入口优先，低耗电实现为无轮询、无唤醒锁、无联网、无重复写库",
            "安全锁：${if (settingsStore.isSecurityLockEnabled()) "已启用" else "未启用"}",
            "自动锁定：${if (settingsStore.isSecurityLockEnabled()) settingsStore.lockTimeout().label else "未启用"}",
            "首页内容预览：${if (settingsStore.isHomePreviewEnabled()) "已显示" else "已隐藏"}",
            "显示模式：${settingsStore.themeMode().label}",
            "防截图：${if (settingsStore.isScreenshotProtectionEnabled()) "已开启" else "未开启"}",
            "后台隐藏：${if (settingsStore.isHideFromRecentsEnabled()) "已开启" else "未开启"}"
        )
    }

    private fun healthChecks(): List<String> {
        return listOf(
            "Keystore：${keystoreStatus()}",
            "SQLCipher 数据库：${databaseStatus()}"
        )
    }

    private fun keystoreStatus(): String {
        return runCatching {
            val passphrase = DatabaseKeyManager(appContext).getOrCreateDatabasePassphrase()
            if (passphrase.size == 32) "正常" else "异常（PH-KEY-002）"
        }.getOrElse {
            "异常（PH-KEY-001 ${it::class.java.simpleName}）"
        }
    }

    private fun databaseStatus(): String {
        return runCatching {
            val db = PrivacyHubDatabaseProvider.get(appContext)
            db.openHelper.readableDatabase.query("SELECT 1").close()
            "正常"
        }.getOrElse {
            "异常（PH-DB-001 ${it::class.java.simpleName}）"
        }
    }

    private fun reportLine(report: TestReport): String {
        val code = errorCode(report)
        val result = if (report.success) "成功" else "失败 $code"
        val target = report.target?.displayName ?: "无"
        return buildString {
            append("${Formatters.time(report.createdAtMillis)}  ${report.entryPoint.displayName}  $result")
            append("\n识别：$target")
            append("\n原因：${DiagnosticSanitizer.shortFailure(report.failureReason)}")
        }
    }

    private fun errorCode(report: TestReport): String {
        if (report.success) return ""
        val reason = report.failureReason.orEmpty()
        return when {
            reason.contains("通知权限") -> "PH-PERM-001"
            reason.contains("剪切板") || reason.contains("clipboard", ignoreCase = true) -> "PH-CLIP-001"
            reason.contains("Activity") || reason.contains("Intent") ||
                reason.contains("目标 App") || reason.contains("浏览器") ||
                reason.contains("淘宝搜索入口") -> "PH-ROUTE-001"
            reason.contains("SQL", ignoreCase = true) || reason.contains("Room", ignoreCase = true) -> "PH-DB-001"
            else -> "PH-GEN-001"
        }
    }
}

data class DiagnosticReport(
    val generatedAt: String,
    val appVersion: String,
    val deviceInfo: List<String>,
    val securityInfo: List<String>,
    val healthChecks: List<String>,
    val recentFailures: List<String>,
    val recentEvents: List<String>,
    val lastCrash: String
)
