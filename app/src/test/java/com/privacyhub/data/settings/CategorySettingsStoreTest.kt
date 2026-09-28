package com.privacyhub.data.settings

import com.privacyhub.security.keystore.KeystoreTextEncryptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategorySettingsStoreTest {
    @Test
    fun neverSavedSettingsUseDefaultsWithoutPersistingAnEmptySnapshot() {
        val preferences = MemorySharedPreferences()
        val store = categoryStore(preferences)

        assertEquals(emptyList(), store.customCategories())
        assertEquals(AppSettingsStore.DEFAULT_HOME_TAGS, store.homeTags(AppSettingsStore.DEFAULT_CATEGORIES))
        assertNull(store.settingsReadError())
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun validEncryptedSnapshotIsReadWithoutBeingReplaced() {
        val snapshot = CategorySettingsSnapshot(
            customCategories = listOf("个人收藏"),
            categoryAliases = mapOf("购物" to "消费"),
            homeTags = listOf("购物")
        )
        val encoded = SensitiveSettingsCodec.encodeSnapshot(snapshot)
        val preferences = MemorySharedPreferences().apply {
            putString(CIPHER_KEY, encoded)
            putString(IV_KEY, "valid-iv")
        }
        val store = categoryStore(preferences)

        val restored = store.portableBackupSettings(AppSettingsStore.DEFAULT_CATEGORIES)

        assertEquals(snapshot.customCategories, restored.customCategories)
        assertEquals(snapshot.categoryAliases, restored.categoryAliases)
        assertEquals(snapshot.homeTags, restored.homeTags)
        assertEquals(encoded, preferences.getString(CIPHER_KEY, null))
        assertEquals("valid-iv", preferences.getString(IV_KEY, null))
    }

    @Test
    fun unreadableEncryptedSnapshotUsesOnlyAnEphemeralReadFallback() {
        val preferences = corruptPreferences()
        val store = categoryStore(preferences)

        assertEquals(emptyList(), store.customCategories())
        assertEquals(AppSettingsStore.DEFAULT_HOME_TAGS, store.homeTags(AppSettingsStore.DEFAULT_CATEGORIES))
        assertNotNull(store.settingsReadError())
        assertEquals("old-cipher", preferences.getString(CIPHER_KEY, null))
        assertEquals("old-iv", preferences.getString(IV_KEY, null))
    }

    @Test
    fun mutationFailsClosedAndDoesNotReplaceUnreadableSnapshot() {
        val preferences = corruptPreferences()
        val store = categoryStore(preferences)

        assertFailsWith<EncryptedSettingsUnavailableException> { store.addCategory("新标签") }
        assertEquals("old-cipher", preferences.getString(CIPHER_KEY, null))
        assertEquals("old-iv", preferences.getString(IV_KEY, null))
        assertTrue(preferences.getStringSet(LEGACY_CATEGORIES, mutableSetOf()).orEmpty() == setOf("保留标签"))
    }

    @Test
    fun backupAndRestoreRollbackRefuseToInventAnEmptySnapshot() {
        val preferences = corruptPreferences()
        val store = categoryStore(preferences)

        assertFailsWith<EncryptedSettingsUnavailableException> {
            store.portableBackupSettings(AppSettingsStore.DEFAULT_CATEGORIES)
        }
        assertFailsWith<EncryptedSettingsUnavailableException> {
            store.stagePortableRestoreRollback("fixture-operation")
        }
        assertNull(preferences.getString("encrypted_category_settings_restore_rollback_v1_text", null))
        assertTrue(store.settingsReadError() != null)
    }

    @Test
    fun decoderRejectsMalformedNestedSectionsInsteadOfReturningAnEmptySnapshot() {
        val malformed = SensitiveSettingsCodec.encodeList(listOf("bad-list", "0;", "0;"))

        assertNull(SensitiveSettingsCodec.decodeSnapshot(malformed))
    }

    private fun corruptPreferences() = MemorySharedPreferences().apply {
        putString(CIPHER_KEY, "old-cipher")
        putString(IV_KEY, "old-iv")
        putStringSet(LEGACY_CATEGORIES, setOf("保留标签"))
    }

    private fun categoryStore(preferences: MemorySharedPreferences): CategorySettingsStore {
        val encryptedStore = EncryptedSettingsStore(
            preferences = preferences,
            encryptText = { KeystoreTextEncryptor.EncryptedText(it, "new-iv") },
            decryptText = { cipherText, _ -> cipherText }
        )
        return CategorySettingsStore(preferences, encryptedStore)
    }

    private companion object {
        const val CIPHER_KEY = "encrypted_category_settings_snapshot_v1_text"
        const val IV_KEY = "encrypted_category_settings_snapshot_v1_iv"
        const val LEGACY_CATEGORIES = "custom_categories"
    }
}
