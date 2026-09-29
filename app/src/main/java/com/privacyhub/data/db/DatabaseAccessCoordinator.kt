package com.privacyhub.data.db

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Serializes restore/recovery against every normal repository access in this process. */
object DatabaseAccessCoordinator {
    private val lock = ReentrantReadWriteLock(true)
    @Volatile private var restoreRecoveryFailure: Throwable? = null

    fun <T> withDatabaseAccess(task: () -> T): T = lock.read {
        restoreRecoveryFailure?.let { throw VaultDatabaseUnavailableException(it) }
        task()
    }

    /** Serializes a Room + encrypted settings mutation against every normal read and write. */
    fun <T> withDatabaseWriteAccess(task: () -> T): T = lock.write {
        restoreRecoveryFailure?.let { throw VaultDatabaseUnavailableException(it) }
        task()
    }

    fun <T> withRestoreAccess(task: () -> T): T = lock.write(task)

    fun markRestoreRecoveryPending(cause: Throwable) {
        restoreRecoveryFailure = cause
    }

    fun markRestoreRecoveryComplete() {
        restoreRecoveryFailure = null
    }

    fun isRestoreRecoveryPending(): Boolean = restoreRecoveryFailure != null
}

internal class RestoreRecoveryPendingException :
    IllegalStateException("Vault access is unavailable until restore recovery succeeds.")
