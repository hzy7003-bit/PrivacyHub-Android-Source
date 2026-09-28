package com.privacyhub.presentation.notification

/** Refreshing an opted-in entry after an update is independent of boot autostart. */
internal object NotificationRestorePolicy {
    fun shouldRestore(enabled: Boolean, bootRestore: Boolean, packageReplaced: Boolean): Boolean =
        enabled && (packageReplaced || bootRestore)
}
