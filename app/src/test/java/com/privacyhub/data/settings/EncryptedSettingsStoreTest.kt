package com.privacyhub.data.settings

import android.content.SharedPreferences
import com.privacyhub.security.keystore.KeystoreTextEncryptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedSettingsStoreTest {
    @Test
    fun missingCipherAndIvWithoutLegacyIsANormalEmptyState() {
        val preferences = MemorySharedPreferences()
        val store = store(preferences)

        assertNull(store.readOrMigrate(KEY, legacyValue = { null }, clearLegacy = {}))
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun preferenceStateReadFailureRemainsAnExplicitUnavailableState() {
        val preferences = MemorySharedPreferences().apply { failContains = true }

        assertFailsWith<EncryptedSettingsUnavailableException> {
            store(preferences).readOrMigrate(KEY, legacyValue = { null }, clearLegacy = {})
        }
    }

    @Test
    fun completeCipherAndIvReadNormally() {
        val preferences = MemorySharedPreferences().apply {
            putString(cipherKey(KEY), "encoded-settings")
            putString(ivKey(KEY), "iv")
        }
        val store = store(preferences)

        assertEquals(
            "encoded-settings",
            store.readOrMigrate(KEY, legacyValue = { null }, clearLegacy = {})
        )
    }

    @Test
    fun cipherWithoutIvFailsAndLeavesCipherUntouched() {
        val preferences = MemorySharedPreferences().apply { putString(cipherKey(KEY), "cipher") }

        assertFailsWith<EncryptedSettingsUnavailableException> {
            store(preferences).readOrMigrate(KEY, legacyValue = { null }, clearLegacy = {})
        }
        assertEquals("cipher", preferences.getString(cipherKey(KEY), null))
    }

    @Test
    fun ivWithoutCipherFailsAndLeavesIvUntouched() {
        val preferences = MemorySharedPreferences().apply { putString(ivKey(KEY), "iv") }

        assertFailsWith<EncryptedSettingsUnavailableException> {
            store(preferences).readOrMigrate(KEY, legacyValue = { null }, clearLegacy = {})
        }
        assertEquals("iv", preferences.getString(ivKey(KEY), null))
    }

    @Test
    fun decryptFailureIsNotReportedAsMissingAndDoesNotClearLegacy() {
        val preferences = MemorySharedPreferences().apply {
            putString(cipherKey(KEY), "cipher")
            putString(ivKey(KEY), "iv")
            putString(LEGACY_KEY, "legacy")
        }

        assertFailsWith<EncryptedSettingsUnavailableException> {
            store(preferences, decrypt = { _, _ -> error("fixture decrypt failure") })
                .readOrMigrate(KEY, legacyValue = { "legacy" }, clearLegacy = { remove(LEGACY_KEY) })
        }
        assertEquals("cipher", preferences.getString(cipherKey(KEY), null))
        assertEquals("iv", preferences.getString(ivKey(KEY), null))
        assertEquals("legacy", preferences.getString(LEGACY_KEY, null))
    }

    @Test
    fun invalidDecodedPayloadDoesNotClearLegacyOrReplaceEncryptedData() {
        val preferences = MemorySharedPreferences().apply {
            putString(cipherKey(KEY), "old-cipher")
            putString(ivKey(KEY), "old-iv")
            putString(LEGACY_KEY, "legacy")
        }

        assertFailsWith<EncryptedSettingsUnavailableException> {
            store(preferences)
                .readOrMigrate(
                    key = KEY,
                    legacyValue = { "legacy" },
                    clearLegacy = { remove(LEGACY_KEY) },
                    validatePayload = { false }
                )
        }

        assertEquals("old-cipher", preferences.getString(cipherKey(KEY), null))
        assertEquals("old-iv", preferences.getString(ivKey(KEY), null))
        assertEquals("legacy", preferences.getString(LEGACY_KEY, null))
    }

    @Test
    fun writeFailureDoesNotReturnSuccessOrReplaceExistingPair() {
        val preferences = MemorySharedPreferences().apply {
            putString(cipherKey(KEY), "old-cipher")
            putString(ivKey(KEY), "old-iv")
            commitResult = false
        }

        assertFalse(store(preferences).write(KEY, "new-payload"))
        assertEquals("old-cipher", preferences.getString(cipherKey(KEY), null))
        assertEquals("old-iv", preferences.getString(ivKey(KEY), null))
    }

    private fun store(
        preferences: SharedPreferences,
        decrypt: (String, String) -> String = { cipherText, _ -> cipherText }
    ) = EncryptedSettingsStore(
        preferences = preferences,
        encryptText = { KeystoreTextEncryptor.EncryptedText(it, "fixture-iv") },
        decryptText = decrypt
    )

    private companion object {
        const val KEY = "settings"
        const val LEGACY_KEY = "legacy_settings"

        fun cipherKey(key: String) = "encrypted_${key}_text"
        fun ivKey(key: String) = "encrypted_${key}_iv"
    }
}

internal class MemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    var commitResult: Boolean = true
    var failContains: Boolean = false
    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean {
        if (failContains) error("injected preference state read failure")
        return values.containsKey(key)
    }
    override fun edit(): SharedPreferences.Editor = MemoryEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    fun putString(key: String, value: String?) {
        values[key] = value
    }

    fun putStringSet(key: String, value: Set<String>?) {
        values[key] = value?.toSet()
    }

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value; removed -= key }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = values?.toSet(); removed -= key }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value; removed -= key }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value; removed -= key }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value; removed -= key }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value; removed -= key }
        override fun remove(key: String): SharedPreferences.Editor = apply { pending.remove(key); removed += key }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            if (!commitResult) return false
            if (clear) values.clear()
            removed.forEach(values::remove)
            values.putAll(pending)
            return true
        }
        override fun apply() { commit() }
    }
}
