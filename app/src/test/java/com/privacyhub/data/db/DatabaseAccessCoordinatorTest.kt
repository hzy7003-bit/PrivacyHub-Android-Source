package com.privacyhub.data.db

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseAccessCoordinatorTest {
    @Test
    fun normalStartupAndSuccessfulRestoreAllowOrdinaryAccess() {
        DatabaseAccessCoordinator.markRestoreRecoveryComplete()
        assertEquals("startup", DatabaseAccessCoordinator.withDatabaseAccess { "startup" })
        assertEquals("restored", DatabaseAccessCoordinator.withRestoreAccess { "restored" })
        assertEquals("write", DatabaseAccessCoordinator.withDatabaseWriteAccess { "write" })
    }

    @Test
    fun ordinaryDatabaseAccessWaitsUntilRestoreFinishes() {
        val executor = Executors.newFixedThreadPool(2)
        val restoreStarted = CountDownLatch(1)
        val allowRestoreToFinish = CountDownLatch(1)
        val ordinaryAccessFinished = CountDownLatch(1)
        val ordinaryAccessEntered = AtomicBoolean(false)
        try {
            executor.execute {
                DatabaseAccessCoordinator.withRestoreAccess {
                    restoreStarted.countDown()
                    allowRestoreToFinish.await(2, TimeUnit.SECONDS)
                }
            }
            assertTrue(restoreStarted.await(2, TimeUnit.SECONDS))

            executor.execute {
                DatabaseAccessCoordinator.withDatabaseAccess {
                    ordinaryAccessEntered.set(true)
                }
                ordinaryAccessFinished.countDown()
            }

            assertFalse(ordinaryAccessFinished.await(100, TimeUnit.MILLISECONDS))
            assertFalse(ordinaryAccessEntered.get())
            allowRestoreToFinish.countDown()
            assertTrue(ordinaryAccessFinished.await(2, TimeUnit.SECONDS))
            assertTrue(ordinaryAccessEntered.get())
        } finally {
            allowRestoreToFinish.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun coordinatedWriteBlocksBackupStyleReadsUntilBothStoresAreUpdated() {
        val executor = Executors.newFixedThreadPool(2)
        val writeStarted = CountDownLatch(1)
        val allowWriteToFinish = CountDownLatch(1)
        val readFinished = CountDownLatch(1)
        val readEntered = AtomicBoolean(false)
        try {
            executor.execute {
                DatabaseAccessCoordinator.withDatabaseWriteAccess {
                    writeStarted.countDown()
                    allowWriteToFinish.await(2, TimeUnit.SECONDS)
                }
            }
            assertTrue(writeStarted.await(2, TimeUnit.SECONDS))

            executor.execute {
                DatabaseAccessCoordinator.withDatabaseAccess { readEntered.set(true) }
                readFinished.countDown()
            }

            assertFalse(readFinished.await(100, TimeUnit.MILLISECONDS))
            assertFalse(readEntered.get())
            allowWriteToFinish.countDown()
            assertTrue(readFinished.await(2, TimeUnit.SECONDS))
            assertTrue(readEntered.get())
        } finally {
            allowWriteToFinish.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun failedStartupRecoveryRejectsDatabaseAccessUntilRecoverySucceeds() {
        val recoveryError = IllegalStateException("incomplete restore journal")
        DatabaseAccessCoordinator.markRestoreRecoveryPending(recoveryError)
        try {
            val unavailable = assertFailsWith<VaultDatabaseUnavailableException> {
                DatabaseAccessCoordinator.withDatabaseAccess { "must not read" }
            }
            assertEquals(recoveryError, unavailable.cause)
            assertFailsWith<VaultDatabaseUnavailableException> {
                DatabaseAccessCoordinator.withDatabaseWriteAccess { "must not write" }
            }

            // A second failed recovery must keep the first failure's fail-closed state.
            val rollbackError = IllegalStateException("rollback failed")
            DatabaseAccessCoordinator.withRestoreAccess {
                DatabaseAccessCoordinator.markRestoreRecoveryPending(rollbackError)
            }
            assertEquals(rollbackError, assertFailsWith<VaultDatabaseUnavailableException> {
                DatabaseAccessCoordinator.withDatabaseAccess { "must not read" }
            }.cause)

            DatabaseAccessCoordinator.withRestoreAccess {
                DatabaseAccessCoordinator.markRestoreRecoveryComplete()
            }
            assertEquals("recovered", DatabaseAccessCoordinator.withDatabaseAccess { "recovered" })
        } finally {
            DatabaseAccessCoordinator.markRestoreRecoveryComplete()
        }
    }
}
