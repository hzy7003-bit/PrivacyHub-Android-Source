package com.privacyhub.security.keystore

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class DatabaseKeyInitializerTest {
    @Test
    fun createsAndVerifiesKeyForFreshInstall() {
        val store = FakeStore()
        val cipher = RoundTripCipher()
        val expected = ByteArray(32) { it.toByte() }

        val result = initializer(store, cipher, DatabaseKeyProcessState(), expected).getOrCreate()

        assertContentEquals(expected, result)
        assertEquals(1, store.writeCount)
        assertEquals(1, cipher.decryptCount)
        assertFalse(store.databaseExists)
    }

    @Test
    fun reusesExistingKeyWithoutGeneratingOrWritingAgain() {
        val store = FakeStore()
        val cipher = RoundTripCipher()
        val expected = ByteArray(32) { (it + 1).toByte() }
        val processState = DatabaseKeyProcessState()
        val first = initializer(store, cipher, processState, expected).getOrCreate()

        val second = initializer(store, cipher, processState, ByteArray(32) { 99 }).getOrCreate()

        assertContentEquals(first, second)
        assertEquals(1, store.writeCount)
        assertEquals(2, cipher.decryptCount)
    }

    @Test
    fun rejectsCipherTextWithoutIvWithoutGeneratingOrOverwriting() {
        val store = FakeStore(cipherText = "existing-cipher", iv = null)
        val cipher = RoundTripCipher()

        val error = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, cipher, DatabaseKeyProcessState(), ByteArray(32)).getOrCreate()
        }

        assertEquals(DatabaseKeyFailure.PARTIAL_PERSISTENCE, error.failure)
        assertEquals(0, store.writeCount)
        assertEquals("existing-cipher", store.memoryCipherText)
    }

    @Test
    fun rejectsIvWithoutCipherTextWithoutGeneratingOrOverwriting() {
        val store = FakeStore(cipherText = null, iv = "existing-iv")
        val cipher = RoundTripCipher()

        val error = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, cipher, DatabaseKeyProcessState(), ByteArray(32)).getOrCreate()
        }

        assertEquals(DatabaseKeyFailure.PARTIAL_PERSISTENCE, error.failure)
        assertEquals(0, store.writeCount)
        assertEquals("existing-iv", store.memoryIv)
    }

    @Test
    fun rejectsUnreadableStoredMaterialWithoutGeneratingOrOverwriting() {
        val store = FakeStore(cipherText = "corrupt", iv = "iv")
        val cipher = RoundTripCipher(failDecrypt = true)

        val error = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, cipher, DatabaseKeyProcessState(), ByteArray(32)).getOrCreate()
        }

        assertEquals(DatabaseKeyFailure.UNREADABLE_KEY, error.failure)
        assertEquals(0, store.writeCount)
        assertEquals(0, cipher.encryptCount)
    }

    @Test
    fun failedCommitUpdatesMemoryViewButDoesNotReturnNewKey() {
        val store = FakeStore(commitResult = false)
        val cipher = RoundTripCipher()
        val expected = ByteArray(32) { 7 }

        val error = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, cipher, DatabaseKeyProcessState(), expected).getOrCreate()
        }

        assertEquals(DatabaseKeyFailure.PERSISTENCE_FAILED, error.failure)
        assertEquals(1, store.writeCount)
        assertNotNull(store.memoryCipherText)
        assertNotNull(store.memoryIv)
        assertEquals(null, store.durableCipherText)
        assertEquals(null, store.durableIv)
    }

    @Test
    fun sameManagerFailsBeforeReadingMemoryVisiblePairAfterFailedCommit() {
        val store = FakeStore(commitResult = false)
        val processState = DatabaseKeyProcessState()
        val manager = manager(store, RoundTripCipher(), processState, ByteArray(32) { 7 })

        assertFailsWith<DatabaseKeyInitializationException> {
            manager.getOrCreateDatabasePassphrase()
        }
        val readsAfterFailure = store.readCount
        val retry = assertFailsWith<DatabaseKeyInitializationException> {
            manager.getOrCreateDatabasePassphrase()
        }

        assertEquals(DatabaseKeyFailure.PERSISTENCE_FAILED, retry.failure)
        assertNotNull(store.memoryCipherText)
        assertNotNull(store.memoryIv)
        assertEquals(readsAfterFailure, store.readCount)
        assertEquals(1, store.writeCount)
    }

    @Test
    fun newManagerInSameProcessFailsBeforeReadingMemoryVisiblePair() {
        val store = FakeStore(commitResult = false)
        val processState = DatabaseKeyProcessState()

        assertFailsWith<DatabaseKeyInitializationException> {
            manager(
                store,
                RoundTripCipher(),
                processState,
                ByteArray(32) { 7 }
            ).getOrCreateDatabasePassphrase()
        }
        val readsAfterFailure = store.readCount
        val retry = assertFailsWith<DatabaseKeyInitializationException> {
            manager(
                store,
                RoundTripCipher(),
                processState,
                ByteArray(32) { 8 }
            ).getOrCreateDatabasePassphrase()
        }

        assertEquals(DatabaseKeyFailure.PERSISTENCE_FAILED, retry.failure)
        assertEquals(readsAfterFailure, store.readCount)
        assertEquals(1, store.writeCount)
    }

    @Test
    fun providerStyleRetryCannotCreateDatabaseAfterFailedCommit() {
        val store = FakeStore(commitResult = false)
        val processState = DatabaseKeyProcessState()
        val directory = Files.createTempDirectory("database-key-provider-test").toFile()
        val databaseFile = File(directory, "privacy_hub_vault.db")
        val provider = ProviderHarness(databaseFile) {
            manager(store, RoundTripCipher(), processState, ByteArray(32) { 7 })
        }

        try {
            assertFailsWith<DatabaseKeyInitializationException> { provider.get() }
            assertFailsWith<DatabaseKeyInitializationException> { provider.get() }

            assertFalse(databaseFile.exists())
            assertEquals(1, store.writeCount)
        } finally {
            databaseFile.delete()
            directory.delete()
        }
    }

    @Test
    fun newProcessCanInitializeWhenFailedCommitLeftDurableStoreEmpty() {
        val store = FakeStore(commitResult = false)
        val failedProcess = DatabaseKeyProcessState()

        assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, RoundTripCipher(), failedProcess, ByteArray(32) { 7 }).getOrCreate()
        }
        store.restartProcess()
        store.commitResult = true
        val expected = ByteArray(32) { 8 }

        val recovered = initializer(
            store,
            RoundTripCipher(),
            DatabaseKeyProcessState(),
            expected
        ).getOrCreate()

        assertContentEquals(expected, recovered)
        assertEquals(2, store.writeCount)
    }

    @Test
    fun newProcessAcceptsValidPairThatBecameDurableAfterFailedCommit() {
        val store = FakeStore(commitResult = false)
        val failedProcess = DatabaseKeyProcessState()
        val expected = ByteArray(32) { 9 }

        assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, RoundTripCipher(), failedProcess, expected).getOrCreate()
        }
        store.persistMemoryToDisk()
        store.restartProcess()

        val recovered = initializer(
            store,
            RoundTripCipher(),
            DatabaseKeyProcessState(),
            ByteArray(32) { 10 }
        ).getOrCreate()

        assertContentEquals(expected, recovered)
        assertEquals(1, store.writeCount)
    }

    @Test
    fun rejectsExistingDatabaseWithoutAnyKeyMaterial() {
        val store = FakeStore(databaseExists = true)
        val cipher = RoundTripCipher()

        val error = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, cipher, DatabaseKeyProcessState(), ByteArray(32)).getOrCreate()
        }

        assertEquals(DatabaseKeyFailure.DATABASE_EXISTS_WITHOUT_KEY, error.failure)
        assertEquals(0, store.writeCount)
        assertEquals(0, cipher.encryptCount)
    }

    @Test
    fun rejectsPartialRereadAfterAWrite() {
        val store = FakeStore(readAfterWrite = StoredDatabaseKeyMaterial("persisted-cipher", null))
        val cipher = RoundTripCipher()

        val error = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(
                store,
                cipher,
                DatabaseKeyProcessState(),
                ByteArray(32) { 3 }
            ).getOrCreate()
        }

        assertEquals(DatabaseKeyFailure.PERSISTENCE_VERIFICATION_FAILED, error.failure)
        assertEquals(1, store.writeCount)
    }

    @Test
    fun commitTrueIsNotAcceptedUntilReadbackDecryptsToGeneratedKey() {
        val store = FakeStore()
        val processState = DatabaseKeyProcessState()
        val cipher = RoundTripCipher(decryptedOverride = ByteArray(32) { 4 })

        val first = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, cipher, processState, ByteArray(32) { 3 }).getOrCreate()
        }
        val readsAfterFailure = store.readCount
        val retry = assertFailsWith<DatabaseKeyInitializationException> {
            initializer(store, cipher, processState, ByteArray(32) { 5 }).getOrCreate()
        }

        assertEquals(DatabaseKeyFailure.PERSISTENCE_VERIFICATION_FAILED, first.failure)
        assertEquals(DatabaseKeyFailure.PERSISTENCE_FAILED, retry.failure)
        assertEquals(readsAfterFailure, store.readCount)
        assertEquals(1, store.writeCount)
    }

    @Test
    fun existingUserKeepsSameKeyAcrossSimulatedProcessRestart() {
        val store = FakeStore()
        val expected = ByteArray(32) { (it + 11).toByte() }
        val first = initializer(
            store,
            RoundTripCipher(),
            DatabaseKeyProcessState(),
            expected
        ).getOrCreate()
        store.restartProcess()

        val second = initializer(
            store,
            RoundTripCipher(),
            DatabaseKeyProcessState(),
            ByteArray(32) { 99 }
        ).getOrCreate()

        assertContentEquals(first, second)
        assertEquals(1, store.writeCount)
    }

    private fun initializer(
        store: FakeStore,
        cipher: RoundTripCipher,
        processState: DatabaseKeyProcessState,
        expected: ByteArray
    ): DatabaseKeyInitializer {
        return DatabaseKeyInitializer(store, cipher, processState) { expected.copyOf() }
    }

    private fun manager(
        store: FakeStore,
        cipher: RoundTripCipher,
        processState: DatabaseKeyProcessState,
        expected: ByteArray
    ): DatabaseKeyManager {
        return DatabaseKeyManager.createForTest(store, cipher, processState) {
            expected.copyOf()
        }
    }

    private class FakeStore(
        cipherText: String? = null,
        iv: String? = null,
        var databaseExists: Boolean = false,
        var commitResult: Boolean = true,
        private val readAfterWrite: StoredDatabaseKeyMaterial? = null
    ) : DatabaseKeyMaterialStore {
        var memoryCipherText: String? = cipherText
            private set
        var memoryIv: String? = iv
            private set
        var durableCipherText: String? = cipherText
            private set
        var durableIv: String? = iv
            private set
        var writeCount = 0
            private set
        var readCount = 0
            private set

        private var hasWritten = false

        override fun read(): StoredDatabaseKeyMaterial {
            readCount++
            if (hasWritten && readAfterWrite != null) return readAfterWrite
            return StoredDatabaseKeyMaterial(memoryCipherText, memoryIv)
        }

        override fun write(material: EncryptedDatabaseKeyMaterial): Boolean {
            writeCount++
            hasWritten = true
            memoryCipherText = material.cipherText
            memoryIv = material.iv
            if (commitResult) persistMemoryToDisk()
            return commitResult
        }

        override fun databaseExists(): Boolean = databaseExists

        fun persistMemoryToDisk() {
            durableCipherText = memoryCipherText
            durableIv = memoryIv
        }

        fun restartProcess() {
            memoryCipherText = durableCipherText
            memoryIv = durableIv
            hasWritten = false
        }
    }

    private class RoundTripCipher(
        private val failDecrypt: Boolean = false,
        private val decryptedOverride: ByteArray? = null
    ) : DatabaseKeyCipher {
        var encryptCount = 0
            private set
        var decryptCount = 0
            private set

        override fun encrypt(rawPassphrase: ByteArray): EncryptedDatabaseKeyMaterial {
            encryptCount++
            return EncryptedDatabaseKeyMaterial(
                cipherText = rawPassphrase.joinToString(","),
                iv = "test-iv"
            )
        }

        override fun decrypt(material: EncryptedDatabaseKeyMaterial): ByteArray {
            decryptCount++
            if (failDecrypt) error("decrypt failed")
            decryptedOverride?.let { return it.copyOf() }
            return material.cipherText.split(',').map(String::toByte).toByteArray()
        }
    }

    private class ProviderHarness(
        private val databaseFile: File,
        private val manager: () -> DatabaseKeyManager
    ) {
        fun get() {
            manager().getOrCreateDatabasePassphrase()
            databaseFile.writeText("created")
        }
    }
}
