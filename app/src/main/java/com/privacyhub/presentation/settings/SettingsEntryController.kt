package com.privacyhub.presentation.settings

import android.Manifest
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.LinearLayout
import android.widget.Toast
import com.privacyhub.R
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.presentation.SettingsActivity
import com.privacyhub.presentation.notification.SaveNotificationManager
import com.privacyhub.presentation.tile.SaveToVaultTileService
import com.privacyhub.presentation.ui.ButtonKind

internal class SettingsEntryController(
    private val activity: SettingsActivity,
    private val store: LocalStore,
    private val settings: AppSettingsStore,
    private val views: SettingsViewFactory,
    private val rerender: () -> Unit
) {
    fun render(content: LinearLayout) {
        views.renderSection(content, "保存入口") {
            renderNotification(content)
            renderBootRestore(content)
            renderTile(content)
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            showSaveNotification()
        } else {
            store.recordReport(
                entryPoint = EntryPoint.NOTIFICATION,
                success = false,
                failureReason = "POST_NOTIFICATIONS permission denied",
                target = null,
                note = "用户拒绝通知权限"
            )
            Toast.makeText(activity, "没有通知权限，无法显示通知栏按钮", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun renderNotification(content: LinearLayout) {
        val enabled = settings.isPersistentNotificationEnabled()
        val hideAction = if (enabled) {
            { hideSaveNotification() }
        } else {
            null
        }
        content.addView(views.notificationCard(
            enabled = enabled,
            body = "复制文字后下拉通知栏，点“保存到安全箱”或“保存并跳转”。不轮询、不读后台、不联网。",
            primaryLabel = if (enabled) "刷新通知栏按钮" else "开启通知栏按钮",
            primaryAction = {
                showSaveNotification()
            },
            dangerAction = hideAction
        ))
    }

    private fun renderBootRestore(content: LinearLayout) {
        val enabled = settings.isBootNotificationRestoreEnabled()
        content.addView(views.card(
            "解锁后恢复通知栏按钮",
            if (enabled) {
                "已开启。手机重启或解锁后，系统允许时会尝试恢复通知栏按钮。"
            } else {
                "关闭时，如果系统清掉通知栏按钮，需要手动回到这里重新开启。"
            },
            views.button(if (enabled) "关闭自动恢复" else "开启自动恢复", ButtonKind.Secondary) {
                val newValue = !enabled
                settings.setBootNotificationRestoreEnabled(newValue)
                if (newValue) {
                    settings.setPersistentNotificationEnabled(true)
                    SaveNotificationManager(activity).showPersistentSaveNotification()
                }
                Toast.makeText(activity, if (newValue) "已开启自动恢复" else "已关闭自动恢复", Toast.LENGTH_SHORT).show()
                rerender()
            }
        ))
    }

    private fun renderTile(content: LinearLayout) {
        content.addView(views.card(
            "控制中心磁贴",
            "添加后可在控制中心点“保存到安全箱”。部分国产 ROM 会限制磁贴读取剪切板，结果会写入测试报告。",
            views.button("添加控制中心按钮", ButtonKind.Secondary) { requestQuickSettingsTile() }
        ))
    }

    private fun showSaveNotification() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            store.recordReport(
                entryPoint = EntryPoint.NOTIFICATION,
                success = false,
                failureReason = "POST_NOTIFICATIONS permission missing",
                target = null,
                note = "用户需要先允许通知权限"
            )
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
            return
        }

        settings.setPersistentNotificationEnabled(true)
        SaveNotificationManager(activity).showPersistentSaveNotification()
        store.recordReport(EntryPoint.NOTIFICATION, true, null, null, "已请求显示常驻通知栏保存按钮")
        Toast.makeText(activity, "通知栏保存按钮已开启", Toast.LENGTH_SHORT).show()
        rerender()
    }

    private fun hideSaveNotification() {
        if (!settings.setPersistentNotificationEnabled(false, synchronous = true)) {
            Toast.makeText(activity, "通知栏按钮设置保存失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        SaveNotificationManager(activity).cancel()
        store.recordReport(EntryPoint.NOTIFICATION, true, null, null, "用户手动关闭常驻通知栏保存按钮")
        Toast.makeText(activity, "通知栏保存按钮已关闭", Toast.LENGTH_SHORT).show()
        rerender()
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showManualTileInstructions("当前 Android 版本不支持应用内直接请求添加磁贴。")
            store.recordReport(
                EntryPoint.TILE,
                false,
                "requestAddTileService requires Android 13+",
                null,
                "需要用户手动编辑控制中心添加磁贴"
            )
            return
        }

        val manager = activity.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            showManualTileInstructions("系统没有返回控制中心服务。")
            store.recordReport(EntryPoint.TILE, false, "StatusBarManager unavailable", null, "需要用户手动编辑控制中心添加磁贴")
            return
        }

        manager.requestAddTileService(
            ComponentName(activity, SaveToVaultTileService::class.java),
            "保存到安全箱",
            Icon.createWithResource(activity, R.drawable.ic_privacy_hub),
            activity.mainExecutor
        ) { result -> activity.runOnUiThread { handleTileAddResult(result) } }
    }

    private fun handleTileAddResult(result: Int) {
        val (success, message, failure) = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> Triple(true, "控制中心按钮已添加", null)
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> Triple(true, "控制中心按钮已经存在", null)
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Triple(false, "系统没有添加控制中心按钮", "tile not added")
            StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> Triple(false, "请保持应用在前台后再试", "app not in foreground")
            else -> Triple(false, "系统拒绝添加控制中心按钮", "tile request result=$result")
        }
        store.recordReport(EntryPoint.TILE, success, failure, null, message)
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        rerender()
    }

    private fun showManualTileInstructions(reason: String) {
        AlertDialog.Builder(activity)
            .setTitle("手动添加控制中心按钮")
            .setMessage("$reason\n\n请下拉控制中心，进入编辑按钮，找到“保存到安全箱”并拖到常用区域。")
            .setPositiveButton("知道了", null)
            .show()
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 3001
    }
}
