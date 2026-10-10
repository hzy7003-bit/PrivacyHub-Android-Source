package com.privacyhub.presentation.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.privacyhub.clipboard.ClipboardReader
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.repository.saveWithBestEffortDiagnostic
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.diagnostics.DiagnosticNotePolicy
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.presentation.notification.NotificationEntryActivity
import com.privacyhub.router.LinkParser
import com.privacyhub.router.LinkTargetDetector
import com.privacyhub.util.AppExecutors

class SaveToVaultTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile("保存到安全箱")
    }

    override fun onClick() {
        super.onClick()
        val lockEnabled = AppSettingsStore(applicationContext).isSecurityLockEnabled()
        if (lockEnabled && isLocked) {
            unlockAndRun { readClipboardAndSave("磁贴点击后先解锁再读取剪切板") }
        } else {
            readClipboardAndSave("磁贴点击后直接读取剪切板")
        }
    }

    private fun readClipboardAndSave(note: String) {
        val result = ClipboardReader.readText(applicationContext)

        if (result.failureReason == ClipboardReader.TEXT_TOO_LARGE_REASON) {
            updateTile("内容过大")
            showToast(ClipboardReader.TEXT_TOO_LARGE_REASON)
            return
        }

        if (result.success) {
            val text = result.text.orEmpty()
            val app = applicationContext
            AppExecutors.executeDiskResult({
                val store = LocalStore(app)
                val target = LinkTargetDetector.detect(text)
                val url = LinkParser.extractFirstUrl(text)
                saveWithBestEffortDiagnostic(
                    save = {
                        store.saveItemOnDisk(
                            entryPoint = EntryPoint.TILE,
                            sourceApp = "system_clipboard",
                            originalText = text,
                            primaryUrl = url,
                            target = target
                        )
                    },
                    recordSuccess = {
                        store.recordReportOnDisk(
                            entryPoint = EntryPoint.TILE,
                            success = true,
                            failureReason = null,
                            target = target,
                            note = DiagnosticNotePolicy.TILE_SAVE_SUCCEEDED
                        )
                    }
                )
            }) { saveResult ->
                if (saveResult.isSuccess) {
                    updateTile("已保存")
                    showToast("已保存到安全箱")
                } else {
                    updateTile("保存失败")
                    showToast("保存失败")
                }
            }
        } else {
            AppExecutors.executeDiskResult({
                LocalStore(applicationContext).recordReportOnDisk(
                    entryPoint = EntryPoint.TILE,
                    success = false,
                    failureReason = result.failureReason,
                    target = LinkTarget.UNKNOWN,
                    note = "$note；直接读取失败，准备启动透明前台入口补读"
                )
            }) {
                updateTile("正在保存")
                showToast("正在尝试保存")
                launchTransparentFallback()
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchTransparentFallback() {
        val intent = Intent(this, NotificationEntryActivity::class.java).apply {
            action = NotificationEntryActivity.ACTION_SAVE_FROM_TILE
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_TILE_FALLBACK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure { error ->
            updateTile("读取失败")
            showToast("保存失败")
            AppExecutors.executeDiskResult({
                LocalStore(applicationContext).recordReportOnDisk(
                    entryPoint = EntryPoint.TILE,
                    success = false,
                    failureReason = "${error.javaClass.simpleName}：${error.message ?: "透明入口启动失败"}",
                    target = LinkTarget.UNKNOWN,
                    note = "磁贴直接读取失败后，透明前台入口启动失败"
                )
            }) { /* Diagnostics remain best effort. */ }
        }
    }

    private fun updateTile(label: String) {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            this.label = label
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "隐私中转站"
            }
            updateTile()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_TILE_FALLBACK = 2101
    }
}
