package com.privacyhub.diagnostics

import android.content.Context

class CrashRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is PrivacyHubCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(PrivacyHubCrashHandler(previous))
    }

    fun lastCrashSummary(): String? {
        return preferences.getString(KEY_LAST_CRASH_SUMMARY, null)
    }

    private inner class PrivacyHubCrashHandler(
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            preferences.edit()
                .putLong(KEY_LAST_CRASH_AT, System.currentTimeMillis())
                .putString(KEY_LAST_CRASH_SUMMARY, DiagnosticSanitizer.crashSummary(throwable))
                .apply()
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val PREFS_NAME = "privacy_hub_diagnostics"
        private const val KEY_LAST_CRASH_AT = "last_crash_at"
        private const val KEY_LAST_CRASH_SUMMARY = "last_crash_summary"
    }
}
