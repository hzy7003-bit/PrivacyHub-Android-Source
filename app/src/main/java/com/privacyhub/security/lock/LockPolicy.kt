package com.privacyhub.security.lock

object LockPolicy {
    const val DEFAULT_BACKGROUND_LOCK_TIMEOUT_MS = 10_000L

    fun shouldLockAfterBackground(
        backgroundDurationMs: Long,
        timeoutMs: Long = DEFAULT_BACKGROUND_LOCK_TIMEOUT_MS
    ): Boolean {
        return backgroundDurationMs >= timeoutMs.coerceAtLeast(0L)
    }
}
