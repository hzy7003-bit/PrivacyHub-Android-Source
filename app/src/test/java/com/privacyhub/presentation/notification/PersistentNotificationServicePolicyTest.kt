package com.privacyhub.presentation.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentNotificationServicePolicyTest {
    @Test
    fun enabledStartStartsForeground() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.START_FOREGROUND,
            PersistentNotificationServicePolicy.decide(
                PersistentNotificationServicePolicy.ACTION_START,
                persistentEnabled = true,
                notificationPermissionGranted = true
            )
        )
    }

    @Test
    fun disabledStartStopsWithoutStartingForeground() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.STOP,
            PersistentNotificationServicePolicy.decide(
                PersistentNotificationServicePolicy.ACTION_START,
                persistentEnabled = false,
                notificationPermissionGranted = true
            )
        )
    }

    @Test
    fun enabledNullIntentRestartStartsForeground() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.START_FOREGROUND,
            PersistentNotificationServicePolicy.decide(
                action = null,
                persistentEnabled = true,
                notificationPermissionGranted = true
            )
        )
    }

    @Test
    fun disabledNullIntentRestartStops() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.STOP,
            PersistentNotificationServicePolicy.decide(
                action = null,
                persistentEnabled = false,
                notificationPermissionGranted = true
            )
        )
    }

    @Test
    fun stopAlwaysWins() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.STOP,
            PersistentNotificationServicePolicy.decide(
                PersistentNotificationServicePolicy.ACTION_STOP,
                persistentEnabled = true,
                notificationPermissionGranted = true
            )
        )
    }

    @Test
    fun deniedNotificationPermissionStopsEvenWhenEnabled() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.STOP,
            PersistentNotificationServicePolicy.decide(
                PersistentNotificationServicePolicy.ACTION_START,
                persistentEnabled = true,
                notificationPermissionGranted = false
            )
        )
        assertEquals(
            PersistentNotificationServicePolicy.Decision.STOP,
            PersistentNotificationServicePolicy.decide(
                action = null,
                persistentEnabled = true,
                notificationPermissionGranted = false
            )
        )
    }

    @Test
    fun reopeningTheSettingAllowsStartAgain() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.STOP,
            PersistentNotificationServicePolicy.decide(
                action = null,
                persistentEnabled = false,
                notificationPermissionGranted = true
            )
        )
        assertEquals(
            PersistentNotificationServicePolicy.Decision.START_FOREGROUND,
            PersistentNotificationServicePolicy.decide(
                PersistentNotificationServicePolicy.ACTION_START,
                persistentEnabled = true,
                notificationPermissionGranted = true
            )
        )
    }

    @Test
    fun unknownActionUsesTheSameCurrentStateGuard() {
        assertEquals(
            PersistentNotificationServicePolicy.Decision.STOP,
            PersistentNotificationServicePolicy.decide("unexpected", false, true)
        )
        assertEquals(
            PersistentNotificationServicePolicy.Decision.START_FOREGROUND,
            PersistentNotificationServicePolicy.decide("unexpected", true, true)
        )
    }
}
