package com.privacyhub.presentation.notification

import org.junit.Assert.*
import org.junit.Test

class NotificationRestorePolicyTest {
    @Test fun updateRefreshesEnabledNotificationEvenWhenBootRestoreIsOff() {
        assertTrue(NotificationRestorePolicy.shouldRestore(true, false, true))
    }
    @Test fun bootStillRespectsOptOut() {
        assertFalse(NotificationRestorePolicy.shouldRestore(true, false, false))
    }
    @Test fun disabledNotificationIsNeverEnabledByUpdate() {
        assertFalse(NotificationRestorePolicy.shouldRestore(false, true, true))
        assertFalse(NotificationRestorePolicy.shouldRestore(false, false, true))
    }
    @Test fun enabledBootRestoreStillWorks() {
        assertTrue(NotificationRestorePolicy.shouldRestore(true, true, false))
    }
}
