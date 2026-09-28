package com.privacyhub.presentation.notification

/**
 * Decides whether a service start may recreate the user-opted-in notification.
 *
 * The setting and notification permission are checked on every service start, including the
 * null-intent restart Android may issue for a sticky service. STOP always wins over both.
 */
internal object PersistentNotificationServicePolicy {
    const val ACTION_START = "com.privacyhub.action.START_PERSISTENT_SAVE_NOTIFICATION"
    const val ACTION_STOP = "com.privacyhub.action.STOP_PERSISTENT_SAVE_NOTIFICATION"

    enum class Decision {
        START_FOREGROUND,
        STOP
    }

    fun decide(
        action: String?,
        persistentEnabled: Boolean,
        notificationPermissionGranted: Boolean
    ): Decision {
        if (action == ACTION_STOP) return Decision.STOP
        return if (persistentEnabled && notificationPermissionGranted) {
            Decision.START_FOREGROUND
        } else {
            Decision.STOP
        }
    }
}
