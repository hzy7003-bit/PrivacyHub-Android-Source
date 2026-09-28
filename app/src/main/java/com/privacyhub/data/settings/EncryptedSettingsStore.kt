package com.privacyhub.data.settings

import android.content.SharedPreferences
import com.privacyhub.security.keystore.KeystoreTextEncryptor

internal class EncryptedSettingsStore(
    private val preferences: SharedPreferences,
    private val encryptText: (String) -> KeystoreTextEncryptor.EncryptedText,
    private val decryptText: (String, String) -> String
) {
    constructor(preferences: SharedPreferences, encryptor: KeystoreTextEncryptor) :
        this(preferences, encryptor::encrypt, encryptor::decrypt)

    fun readOrMigrate(
        key: String,
        legacyValue: () -> String?,
        clearLegacy: SharedPreferences.Editor.() -> Unit,
        validatePayload: (String) -> Boolean = { true }
    ): String? {
        val (hasCipherText, hasIv) = storedPairPresence(key)
        if (hasCipherText != hasIv) {
            throw EncryptedSettingsUnavailableException("加密设置状态不完整。")
        }
        if (hasCipherText && hasIv) {
            val plainText = decryptStoredValue(key)
            requireValidPayload(plainText, validatePayload)
            if (!commitLegacyCleanup(clearLegacy)) {
                throw EncryptedSettingsWriteException("无法清理旧设置。")
            }
            return plainText
        }

        val plainText = try {
            legacyValue()
        } catch (error: Exception) {
            throw EncryptedSettingsUnavailableException("无法读取旧设置。", error)
        } ?: return null
        requireValidPayload(plainText, validatePayload)
        if (!write(key, plainText)) {
            throw EncryptedSettingsWriteException("无法迁移加密设置。")
        }
        if (!commitLegacyCleanup(clearLegacy)) {
            throw EncryptedSettingsWriteException("无法清理旧设置。")
        }
        return plainText
    }

    fun write(key: String, plainText: String): Boolean {
        val encrypted = try {
            encryptText(plainText)
        } catch (error: Exception) {
            throw EncryptedSettingsWriteException("无法加密设置。", error)
        }
        return try {
            preferences.edit()
                .putString(cipherKey(key), encrypted.cipherText)
                .putString(ivKey(key), encrypted.iv)
                .commit()
        } catch (error: Exception) {
            throw EncryptedSettingsWriteException("无法写入加密设置。", error)
        }
    }

    fun read(key: String): String? {
        val (hasCipherText, hasIv) = storedPairPresence(key)
        if (!hasCipherText && !hasIv) return null
        return decryptStoredValue(key)
    }

    fun remove(key: String): Boolean {
        val (hasCipherText, hasIv) = try {
            storedPairPresence(key)
        } catch (error: EncryptedSettingsUnavailableException) {
            throw EncryptedSettingsWriteException("无法读取加密设置恢复暂存。", error)
        }
        if (!hasCipherText && !hasIv) return true
        return try {
            preferences.edit()
                .remove(cipherKey(key))
                .remove(ivKey(key))
                .commit()
        } catch (error: Exception) {
            throw EncryptedSettingsWriteException("无法清理加密设置恢复暂存。", error)
        }
    }

    private fun cipherKey(key: String): String = "encrypted_${key}_text"

    private fun ivKey(key: String): String = "encrypted_${key}_iv"

    private fun storedPairPresence(key: String): Pair<Boolean, Boolean> = try {
        preferences.contains(cipherKey(key)) to preferences.contains(ivKey(key))
    } catch (error: Exception) {
        throw EncryptedSettingsUnavailableException("无法读取加密设置状态。", error)
    }

    private fun commitLegacyCleanup(action: SharedPreferences.Editor.() -> Unit): Boolean = try {
        preferences.edit().apply(action).commit()
    } catch (error: Exception) {
        throw EncryptedSettingsWriteException("无法清理旧设置。", error)
    }

    private fun decryptStoredValue(key: String): String {
        val cipherText = try {
            preferences.getString(cipherKey(key), null)
        } catch (error: Exception) {
            throw EncryptedSettingsUnavailableException("加密设置密文格式无效。", error)
        }
        val iv = try {
            preferences.getString(ivKey(key), null)
        } catch (error: Exception) {
            throw EncryptedSettingsUnavailableException("加密设置 IV 格式无效。", error)
        }
        if (cipherText.isNullOrBlank() || iv.isNullOrBlank()) {
            throw EncryptedSettingsUnavailableException("加密设置状态不完整。")
        }
        return try {
            decryptText(cipherText, iv)
        } catch (error: Exception) {
            throw EncryptedSettingsUnavailableException("无法解密加密设置。", error)
        }
    }

    private fun requireValidPayload(payload: String, validator: (String) -> Boolean) {
        val valid = try {
            validator(payload)
        } catch (error: Exception) {
            throw EncryptedSettingsUnavailableException("加密设置内容格式无效。", error)
        }
        if (!valid) throw EncryptedSettingsUnavailableException("加密设置内容格式无效。")
    }
}

open class EncryptedSettingsException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class EncryptedSettingsUnavailableException(message: String, cause: Throwable? = null) :
    EncryptedSettingsException(message, cause)

class EncryptedSettingsWriteException(message: String, cause: Throwable? = null) :
    EncryptedSettingsException(message, cause)
