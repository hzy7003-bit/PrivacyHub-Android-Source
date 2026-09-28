package com.privacyhub.security.lock

import android.app.Activity
import android.view.WindowManager
import com.privacyhub.data.settings.AppSettingsStore

object SecureWindow {
    fun apply(activity: Activity) {
        if (AppSettingsStore(activity).isScreenshotProtectionEnabled()) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
