package com.privacyhub.presentation.notification

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.privacyhub.data.settings.AppSettingsStore

class PersistentSaveNotificationService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val decision = PersistentNotificationServicePolicy.decide(
            action = intent?.action,
            persistentEnabled = AppSettingsStore(this).isPersistentNotificationEnabled(),
            notificationPermissionGranted = hasNotificationPermission()
        )
        return when (decision) {
            PersistentNotificationServicePolicy.Decision.STOP -> {
                stopForegroundCompat()
                cancelNotification()
                stopSelf()
                return START_NOT_STICKY
            }
            PersistentNotificationServicePolicy.Decision.START_FOREGROUND -> {
                val started = runCatching { startAsForeground() }.isSuccess
                if (!started) {
                    stopForegroundCompat()
                    cancelNotification()
                    stopSelf()
                    START_NOT_STICKY
                } else {
                    START_STICKY
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = SaveNotificationManager(this).buildPersistentSaveNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SaveNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SaveNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun cancelNotification() {
        getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(SaveNotificationManager.NOTIFICATION_ID)
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_START = PersistentNotificationServicePolicy.ACTION_START
        const val ACTION_STOP = PersistentNotificationServicePolicy.ACTION_STOP
    }
}
