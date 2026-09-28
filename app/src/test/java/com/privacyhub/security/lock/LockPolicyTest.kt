package com.privacyhub.security.lock

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockPolicyTest {
    @Test
    fun doesNotLockBeforeTimeout() {
        assertFalse(LockPolicy.shouldLockAfterBackground(LockPolicy.DEFAULT_BACKGROUND_LOCK_TIMEOUT_MS - 1))
    }

    @Test
    fun locksAtTimeout() {
        assertTrue(LockPolicy.shouldLockAfterBackground(LockPolicy.DEFAULT_BACKGROUND_LOCK_TIMEOUT_MS))
    }

    @Test
    fun locksImmediatelyWhenTimeoutIsZero() {
        assertTrue(LockPolicy.shouldLockAfterBackground(backgroundDurationMs = 0, timeoutMs = 0))
    }

    @Test
    fun respectsCustomTimeout() {
        assertFalse(LockPolicy.shouldLockAfterBackground(backgroundDurationMs = 59_999, timeoutMs = 60_000))
        assertTrue(LockPolicy.shouldLockAfterBackground(backgroundDurationMs = 60_000, timeoutMs = 60_000))
    }
}
