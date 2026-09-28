package com.privacyhub.security.keystore

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DatabaseKeyManager private constructor(
    private val store: DatabaseKeyMaterialStore,
    private val cipher: DatabaseKeyCipher,
    private val processState: DatabaseKeyProcessState,
    private val randomPassphrase: (() -> ByteArray)?
) {
    constructor(context: Context) : this(
        store = SharedPreferencesDatabaseKeyMaterialStore(
            preferences = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ),
            databaseFile = context.applicationContext.getDatabasePath(DATABASE_NAME)
        ),
        cipher = AndroidKeystoreDatabaseKeyCipher(),
        processState = PROCESS_STATE,
        randomPassphrase = null
    )

    fun getOrCreateDatabasePassphrase(): ByteArray {
        return synchronized(processState.initializationLock) {
            val initializer = if (randomPassphrase == null) {
                DatabaseKeyInitializer(store, cipher, processState)
            } else {
                DatabaseKeyInitializer(store, cipher, processState, randomPassphrase)
            }
            initializer.getOrCreate()
        }
    }

    private class AndroidKeystoreDatabaseKeyCipher : DatabaseKeyCipher {
        override fun encrypt(rawPassphrase: ByteArray): EncryptedDatabaseKeyMaterial {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            return EncryptedDatabaseKeyMaterial(
                cipherText = Base64.encodeToString(cipher.doFinal(rawPassphrase), Base64.NO_WRAP),
                iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
        }

        override fun decrypt(material: EncryptedDatabaseKeyMaterial): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, Base64.decode(material.iv, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, getExistingSecretKey(), gcmSpec)
            return cipher.doFinal(Base64.decode(material.cipherText, Base64.NO_WRAP))
        }

        private fun getOrCreateSecretKey(): SecretKey {
            val keyStore = loadKeyStore()
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        private fun getExistingSecretKey(): SecretKey {
            val key = loadKeyStore().getKey(KEY_ALIAS, null)
            return key as? SecretKey
                ?: throw GeneralSecurityException("The database Keystore key is missing.")
        }

        private fun loadKeyStore(): KeyStore {
            return KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "privacy_hub_database_key"
        private const val PREFS_NAME = "privacy_hub_secure_keys"
        private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_database_passphrase"
        private const val KEY_IV = "database_passphrase_iv"
        private const val DATABASE_NAME = "privacy_hub_vault.db"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val PROCESS_STATE = DatabaseKeyProcessState()

        internal fun createForTest(
            store: DatabaseKeyMaterialStore,
            cipher: DatabaseKeyCipher,
            processState: DatabaseKeyProcessState,
            randomPassphrase: () -> ByteArray
        ): DatabaseKeyManager {
            return DatabaseKeyManager(store, cipher, processState, randomPassphrase)
        }
    }
}

internal enum class DatabaseKeyFailure {
    PARTIAL_PERSISTENCE,
    DATABASE_EXISTS_WITHOUT_KEY,
    UNREADABLE_KEY,
    PERSISTENCE_FAILED,
    PERSISTENCE_VERIFICATION_FAILED
}

internal class DatabaseKeyInitializationException(
    val failure: DatabaseKeyFailure,
    cause: Throwable? = null
) : IllegalStateException(
    "The encrypted database key could not be initialized safely: $failure",
    cause
)

internal data class EncryptedDatabaseKeyMaterial(
    val cipherText: String,
    val iv: String
)

internal data class StoredDatabaseKeyMaterial(
    val cipherText: String?,
    val iv: String?
)

internal interface DatabaseKeyMaterialStore {
    fun read(): StoredDatabaseKeyMaterial

    /** Returns true only when both values have been synchronously accepted for persistence. */
    fun write(material: EncryptedDatabaseKeyMaterial): Boolean

    fun databaseExists(): Boolean
}

internal interface DatabaseKeyCipher {
    fun encrypt(rawPassphrase: ByteArray): EncryptedDatabaseKeyMaterial

    fun decrypt(material: EncryptedDatabaseKeyMaterial): ByteArray
}

internal class DatabaseKeyProcessState {
    internal val initializationLock = Any()
    private val persistenceFailureLatched = AtomicBoolean(false)

    fun hasPersistenceFailure(): Boolean = persistenceFailureLatched.get()

    fun latchPersistenceFailure() {
        persistenceFailureLatched.set(true)
    }
}

internal class DatabaseKeyInitializer(
    private val store: DatabaseKeyMaterialStore,
    private val cipher: DatabaseKeyCipher,
    private val processState: DatabaseKeyProcessState,
    private val randomPassphrase: () -> ByteArray = {
        ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
    }
) {
    fun getOrCreate(): ByteArray {
        if (processState.hasPersistenceFailure()) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.PERSISTENCE_FAILED)
        }

        val stored = try {
            store.read()
        } catch (error: Throwable) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.PERSISTENCE_FAILED, error)
        }
        val hasCipherText = !stored.cipherText.isNullOrBlank()
        val hasIv = !stored.iv.isNullOrBlank()

        if (hasCipherText != hasIv) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.PARTIAL_PERSISTENCE)
        }

        if (hasCipherText) {
            val material = EncryptedDatabaseKeyMaterial(
                cipherText = stored.cipherText!!,
                iv = stored.iv!!
            )
            return decryptOrFail(material)
        }

        if (store.databaseExists()) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.DATABASE_EXISTS_WITHOUT_KEY)
        }

        val rawPassphrase = randomPassphrase()
        if (rawPassphrase.size != PASSPHRASE_BYTES) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.PERSISTENCE_FAILED)
        }
        val encrypted = try {
            cipher.encrypt(rawPassphrase)
        } catch (error: Throwable) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.PERSISTENCE_FAILED, error)
        }
        if (encrypted.cipherText.isBlank() || encrypted.iv.isBlank()) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.PERSISTENCE_FAILED)
        }

        val writeSucceeded = try {
            store.write(encrypted)
        } catch (error: Throwable) {
            failAfterPersistenceAttempt(DatabaseKeyFailure.PERSISTENCE_FAILED, error)
        }
        if (!writeSucceeded) {
            failAfterPersistenceAttempt(DatabaseKeyFailure.PERSISTENCE_FAILED)
        }

        val persisted = try {
            store.read()
        } catch (error: Throwable) {
            failAfterPersistenceAttempt(DatabaseKeyFailure.PERSISTENCE_VERIFICATION_FAILED, error)
        }
        if (
            persisted.cipherText != encrypted.cipherText ||
            persisted.iv != encrypted.iv
        ) {
            failAfterPersistenceAttempt(DatabaseKeyFailure.PERSISTENCE_VERIFICATION_FAILED)
        }

        val persistedMaterial = EncryptedDatabaseKeyMaterial(
            cipherText = persisted.cipherText!!,
            iv = persisted.iv!!
        )
        val verifiedPassphrase = try {
            decryptOrFail(persistedMaterial)
        } catch (error: DatabaseKeyInitializationException) {
            processState.latchPersistenceFailure()
            throw error
        }
        if (!MessageDigest.isEqual(rawPassphrase, verifiedPassphrase)) {
            failAfterPersistenceAttempt(DatabaseKeyFailure.PERSISTENCE_VERIFICATION_FAILED)
        }
        return verifiedPassphrase
    }

    private fun failAfterPersistenceAttempt(
        failure: DatabaseKeyFailure,
        cause: Throwable? = null
    ): Nothing {
        processState.latchPersistenceFailure()
        throw DatabaseKeyInitializationException(failure, cause)
    }

    private fun decryptOrFail(material: EncryptedDatabaseKeyMaterial): ByteArray {
        val passphrase = try {
            cipher.decrypt(material)
        } catch (error: Throwable) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.UNREADABLE_KEY, error)
        }
        if (passphrase.size != PASSPHRASE_BYTES) {
            throw DatabaseKeyInitializationException(DatabaseKeyFailure.UNREADABLE_KEY)
        }
        return passphrase
    }

    private companion object {
        const val PASSPHRASE_BYTES = 32
    }
}

private class SharedPreferencesDatabaseKeyMaterialStore(
    private val preferences: SharedPreferences,
    private val databaseFile: File
) : DatabaseKeyMaterialStore {
    override fun read(): StoredDatabaseKeyMaterial {
        return StoredDatabaseKeyMaterial(
            cipherText = preferences.getString(KEY_ENCRYPTED_PASSPHRASE, null),
            iv = preferences.getString(KEY_IV, null)
        )
    }

    override fun write(material: EncryptedDatabaseKeyMaterial): Boolean {
        return preferences.edit()
            .putString(KEY_ENCRYPTED_PASSPHRASE, material.cipherText)
            .putString(KEY_IV, material.iv)
            .commit()
    }

    override fun databaseExists(): Boolean {
        val path = databaseFile.path
        return databaseFile.exists() ||
            File("$path-wal").exists() ||
            File("$path-shm").exists() ||
            File("$path-journal").exists()
    }

    private companion object {
        const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_database_passphrase"
        const val KEY_IV = "database_passphrase_iv"
    }
}
