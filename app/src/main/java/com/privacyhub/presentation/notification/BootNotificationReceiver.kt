package com.privacyhub.presentation.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.util.AppExecutors

class BootNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in RESTORE_ACTIONS &&
            !(isDebuggable(context) && action == ACTION_DEBUG_RESTORE_NOTIFICATION)
        ) {
            return
        }

        val appContext = context.applicationContext
        val settingsStore = AppSettingsStore(appContext)
        if (!NotificationRestorePolicy.shouldRestore(
                enabled = settingsStore.isPersistentNotificationEnabled(),
                bootRestore = settingsStore.isBootNotificationRestoreEnabled(),
                packageReplaced = action == Intent.ACTION_MY_PACKAGE_REPLACED
            )) {
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            recordRestoreResultAsync(appContext, action, reason = "Android 13+ 通知权限未允许")
            return
        }

        SaveNotificationManager(appContext).showPersistentSaveNotification()
    }

    private fun recordRestoreResultAsync(context: Context, action: String, reason: String?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        ReceiverAsyncWork.dispatch(
            schedule = { task -> AppExecutors.executeDisk(task) },
            work = {
                    LocalStore(appContext).recordReport(
                        entryPoint = EntryPoint.NOTIFICATION,
                        success = false,
                        failureReason = reason,
                        target = LinkTarget.UNKNOWN,
                        note = "系统事件恢复常驻通知：$action"
                    )
            },
            finish = pending::finish
        )
    }

    private fun isDebuggable(context: Context): Boolean {
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    companion object {
        const val ACTION_DEBUG_RESTORE_NOTIFICATION = "com.privacyhub.debug.RESTORE_NOTIFICATION"

        private val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}

/** Schedules receiver follow-up work without running it inline and finishes goAsync exactly once. */
internal object ReceiverAsyncWork {
    fun dispatch(
        schedule: (() -> Unit) -> Unit,
        work: () -> Unit,
        finish: () -> Unit
    ) {
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val finishOnce = { if (finished.compareAndSet(false, true)) finish() }
        try {
            schedule {
                try {
                    runCatching(work)
                } finally {
                    finishOnce()
                }
            }
        } catch (_: RuntimeException) {
            finishOnce()
        }
    }
}
