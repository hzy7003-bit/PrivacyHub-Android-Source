package com.privacyhub.presentation.notification

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.data.settings.AppSettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device smoke for Android's null-intent service restart after the user disables the feature. */
@RunWith(AndroidJUnit4::class)
class PersistentNotificationDisabledRestartTest {
    @Test
    fun disabledNullIntentRestartStopsWithoutRepostingNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AppSettingsStore(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)

        assertFalse("Disable the notification through Settings UI before this smoke", settings.isPersistentNotificationEnabled())
        assertFalse(hasPersistentNotification(notificationManager, context.packageName))

        val restart = Intent(context, PersistentSaveNotificationService::class.java)
        assertNotNull(context.startService(restart))

        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var serviceRunning: Boolean
        do {
            serviceRunning = isServiceRunning(activityManager)
            if (serviceRunning) SystemClock.sleep(100L)
        } while (serviceRunning && SystemClock.elapsedRealtime() < deadline)

        assertFalse("Disabled null-intent restart must stop the service", serviceRunning)
        assertFalse("Disabled restart must not recreate the persistent notification", hasPersistentNotification(notificationManager, context.packageName))
        assertFalse(settings.isPersistentNotificationEnabled())
    }

    private fun hasPersistentNotification(manager: NotificationManager, packageName: String): Boolean =
        manager.activeNotifications.any {
            it.packageName == packageName && it.id == SaveNotificationManager.NOTIFICATION_ID
        }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(manager: ActivityManager): Boolean =
        manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PersistentSaveNotificationService::class.java.name }
}
