package com.privacyhub.security.lock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.privacyhub.data.settings.AppSettingsStore

object AppLockSession {
    @Volatile
    private var installed = false

    @Volatile
    private var unlocked = false

    @Volatile
    private var authInProgress = false

    private var startedActivities = 0
    private var backgroundedAtMillis = 0L
    private lateinit var settingsStore: AppSettingsStore

    fun install(application: Application) {
        if (installed) return
        installed = true
        settingsStore = AppSettingsStore(application)
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0 && backgroundedAtMillis > 0L && !authInProgress) {
                    val duration = SystemClock.elapsedRealtime() - backgroundedAtMillis
                    if (settingsStore.isSecurityLockEnabled() &&
                        LockPolicy.shouldLockAfterBackground(duration, settingsStore.lockTimeout().timeoutMs)
                    ) {
                        unlocked = false
                    }
                }
                startedActivities += 1
                backgroundedAtMillis = 0L
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0 && !authInProgress) {
                    backgroundedAtMillis = SystemClock.elapsedRealtime()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun isUnlocked(): Boolean = unlocked

    fun markUnlocked() {
        unlocked = true
        authInProgress = false
        backgroundedAtMillis = 0L
    }

    fun lockNow() {
        unlocked = false
        backgroundedAtMillis = 0L
    }

    fun setAuthInProgress(inProgress: Boolean) {
        authInProgress = inProgress
    }
}
