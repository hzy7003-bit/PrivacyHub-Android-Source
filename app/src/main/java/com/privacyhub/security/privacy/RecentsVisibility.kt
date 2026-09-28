package com.privacyhub.security.privacy

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import com.privacyhub.data.settings.AppSettingsStore

object RecentsVisibility {
    fun apply(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val hideFromRecents = AppSettingsStore(activity).isHideFromRecentsEnabled()
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        runCatching {
            activityManager.appTasks.forEach { appTask ->
                appTask.setExcludeFromRecents(hideFromRecents)
            }
        }
    }
}
