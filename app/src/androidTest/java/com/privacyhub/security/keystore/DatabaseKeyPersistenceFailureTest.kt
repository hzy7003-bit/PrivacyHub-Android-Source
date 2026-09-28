package com.privacyhub.security.keystore

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseKeyPersistenceFailureTest {
    @Test
    fun failedCommitMemoryPairCannotOpenDatabaseOnSameProcessRetry() {
        val store = MemoryVisibleFailedCommitStore()
        val processState = DatabaseKeyProcessState()
        val databaseFile = File.createTempFile("db-key-failure-", ".db").apply { delete() }
        val firstManager = manager(store, processState, ByteArray(32) { 1 })

        try {
            assertInitializationFails { firstManager.getOrCreateDatabasePassphrase() }
            val readsAfterInitialFailure = store.readCount

            // Mirrors the provider boundary: a DB open is attempted only after a key is returned.
            assertInitializationFails {
                manager(store, processState, ByteArray(32) { 2 })
                    .getOrCreateDatabasePassphrase()
                    .also { databaseFile.writeBytes(it) }
            }

            assertEquals(1, store.writeCount)
            assertEquals(readsAfterInitialFailure, store.readCount)
            assertNotNull(store.memoryMaterial?.cipherText)
            assertNotNull(store.memoryMaterial?.iv)
            assertNull(store.durableMaterial)
            assertFalse(databaseFile.exists())
        } finally {
            databaseFile.delete()
        }
    }

    private fun manager(
        store: MemoryVisibleFailedCommitStore,
        processState: DatabaseKeyProcessState,
        passphrase: ByteArray
    ): DatabaseKeyManager = DatabaseKeyManager.createForTest(
        store = store,
        cipher = RoundTripCipher(),
        processState = processState
    ) { passphrase.copyOf() }

    private fun assertInitializationFails(action: () -> Unit) {
        try {
            action()
            fail("Database key initialization should fail closed")
        } catch (_: DatabaseKeyInitializationException) {
            // Expected: persistence failure must block database initialization.
        }
    }

    private class MemoryVisibleFailedCommitStore : DatabaseKeyMaterialStore {
        var memoryMaterial: EncryptedDatabaseKeyMaterial? = null
            private set
        var durableMaterial: EncryptedDatabaseKeyMaterial? = null
            private set
        var readCount: Int = 0
            private set
        var writeCount: Int = 0
            private set

        override fun read(): StoredDatabaseKeyMaterial {
            readCount++
            return StoredDatabaseKeyMaterial(
                cipherText = memoryMaterial?.cipherText,
                iv = memoryMaterial?.iv
            )
        }

        override fun write(material: EncryptedDatabaseKeyMaterial): Boolean {
            writeCount++
            // SharedPreferencesImpl updates its in-memory map before a failed disk write is reported.
            memoryMaterial = material
            return false
        }

        override fun databaseExists(): Boolean = false
    }

    private class RoundTripCipher : DatabaseKeyCipher {
        override fun encrypt(rawPassphrase: ByteArray): EncryptedDatabaseKeyMaterial =
            EncryptedDatabaseKeyMaterial(
                cipherText = rawPassphrase.joinToString(","),
                iv = "instrumentation-test-iv"
            )

        override fun decrypt(material: EncryptedDatabaseKeyMaterial): ByteArray =
            material.cipherText.split(',').map(String::toByte).toByteArray()
    }
}
