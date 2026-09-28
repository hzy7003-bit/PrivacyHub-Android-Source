package com.privacyhub.presentation.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.privacyhub.R

class SaveNotificationManager(private val context: Context) {
    fun showPersistentSaveNotification() {
        val serviceIntent = Intent(context, PersistentSaveNotificationService::class.java).apply {
            action = PersistentSaveNotificationService.ACTION_START
        }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }.isSuccess
        if (started) return

        showPassiveSaveNotification()
    }

    fun showPassiveSaveNotification() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(notificationManager)
        notificationManager.notify(NOTIFICATION_ID, buildPersistentSaveNotification())
    }

    fun buildPersistentSaveNotification(): Notification {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(notificationManager)

        val saveIntent = actionPendingIntent(ACTION_SAVE_FROM_NOTIFICATION, requestCode = 1001)
        val openIntent = actionPendingIntent(ACTION_OPEN_FROM_NOTIFICATION, requestCode = 1002)
        val quickActions = RemoteViews(context.packageName, R.layout.notification_quick_actions).apply {
            setOnClickPendingIntent(R.id.notification_save, saveIntent)
            setOnClickPendingIntent(R.id.notification_save_open, openIntent)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(R.drawable.ic_notification_flower)
            .setContentTitle("隐私中转站")
            .setContentText("复制后可保存到安全箱，或保存并跳转链接")
            .setContentIntent(saveIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(quickActions)
            .setCustomBigContentView(quickActions)
            .build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationEntryActivity::class.java).apply {
            this.action = action
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showSaveNotification() {
        showPersistentSaveNotification()
    }

    fun cancel() {
        context.stopService(Intent(context, PersistentSaveNotificationService::class.java))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "常驻保存按钮",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "复制文字后点击常驻通知，启动透明 Activity 保存到安全箱"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_SAVE_FROM_NOTIFICATION = "com.privacyhub.action.SAVE_FROM_NOTIFICATION"
        const val ACTION_OPEN_FROM_NOTIFICATION = "com.privacyhub.action.OPEN_FROM_NOTIFICATION"
        const val CHANNEL_ID = "privacy_hub_persistent_save_service"
        const val NOTIFICATION_ID = 2001
    }
}
