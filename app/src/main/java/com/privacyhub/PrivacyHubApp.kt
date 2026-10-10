package com.privacyhub

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.privacyhub.backup.VaultBackupRepository
import com.privacyhub.data.db.DatabaseAccessCoordinator
import com.privacyhub.data.db.RestoreRecoveryPendingException
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.diagnostics.CrashRecorder
import com.privacyhub.presentation.notification.SaveNotificationManager
import com.privacyhub.security.lock.AppLockSession
import com.privacyhub.util.AppExecutors

class PrivacyHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecorder(this).install()
        AppLockSession.install(this)
        // Block ordinary vault access before the worker starts recovery.
        DatabaseAccessCoordinator.markRestoreRecoveryPending(RestoreRecoveryPendingException())
        AppExecutors.executeDisk { recoverPendingRestore() }
        refreshPersistentNotificationIfEnabled()
    }

    private fun recoverPendingRestore() {
        try {
            VaultBackupRepository(this).recoverPendingRestore()
        } catch (_: Exception) {
            // Keep the journal for a later retry; never treat an incomplete restore as successful.
            Log.e(TAG, "restore_recovery_incomplete")
        }
    }

    private fun refreshPersistentNotificationIfEnabled() {
        val settingsStore = AppSettingsStore(this)
        if (!settingsStore.isPersistentNotificationEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        SaveNotificationManager(this).showPersistentSaveNotification()
    }

    private companion object {
        const val TAG = "PrivacyHubRestore"
    }
}
