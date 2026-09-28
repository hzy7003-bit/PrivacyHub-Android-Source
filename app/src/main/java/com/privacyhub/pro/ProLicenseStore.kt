package com.privacyhub.pro

import android.content.Context
import android.content.SharedPreferences
import com.privacyhub.security.keystore.KeystoreTextEncryptor
import java.security.KeyStore

class ProLicenseStore private constructor(
    private val dependencies: Dependencies
) {
    constructor(context: Context) : this(defaultDependencies(context.applicationContext))

    internal constructor(
        preferences: ProLicensePreferences,
        verifyLicense: (String) -> ProLicenseCheck,
        verifyOneStepLicense: (String, String) -> ProLicenseCheck,
        verifyDeviceActivation: (String) -> DeviceActivationCheck,
        encryptor: ProLicenseTextEncryptor,
        fingerprintProvider: () -> String,
        keystoreKeyExists: () -> Boolean,
        nowMillis: () -> Long = System::currentTimeMillis
    ) : this(
        Dependencies(
            preferences = preferences,
            verifyLicense = verifyLicense,
            verifyOneStepLicense = verifyOneStepLicense,
            verifyDeviceActivation = verifyDeviceActivation,
            encryptor = encryptor,
            fingerprintProvider = fingerprintProvider,
            keystoreKeyExists = keystoreKeyExists,
            nowMillis = nowMillis
        )
    )

    fun currentState(): ProLicenseCheck {
        when (val oneStep = encryptedText(KEY_ENCRYPTED_ONE_STEP_TEXT, KEY_ONE_STEP_IV)) {
            EncryptedTextRead.Missing -> Unit
            is EncryptedTextRead.Available -> {
                val fingerprint = try {
                    dependencies.fingerprintProvider()
                } catch (_: DeviceIdentityUnavailableException) {
                    return storageFailure("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
                } catch (_: Exception) {
                    return storageFailure("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
                }
                return dependencies.verifyOneStepLicense(oneStep.value, fingerprint)
            }
            is EncryptedTextRead.Unreadable -> return storageFailure("最终激活码", oneStep.reason)
        }

        val storedLicense = when (val stored = encryptedText(KEY_ENCRYPTED_LICENSE_TEXT, KEY_LICENSE_IV)) {
            EncryptedTextRead.Missing -> when (
                val activation = encryptedText(KEY_ENCRYPTED_ACTIVATION_TEXT, KEY_ACTIVATION_IV)
            ) {
                EncryptedTextRead.Missing -> return ProLicenseCheck(active = false, message = "未激活 Pro。")
                is EncryptedTextRead.Available -> return storageFailure(
                    "设备激活凭证",
                    StorageFailureReason.LICENSE_MISSING
                )
                is EncryptedTextRead.Unreadable -> return storageFailure("设备激活凭证", activation.reason)
            }
            is EncryptedTextRead.Available -> stored.value
            is EncryptedTextRead.Unreadable -> return storageFailure("License", stored.reason)
        }
        val licenseCheck = dependencies.verifyLicense(storedLicense)
        val license = licenseCheck.license ?: return licenseCheck
        if (!license.requiresDeviceBinding) {
            return licenseCheck.copy(
                deviceBinding = DeviceBindingStatus(
                    required = false,
                    active = true,
                    message = "此 License 不需要设备绑定。"
                )
            )
        }

        val activationText = when (val activation = encryptedText(KEY_ENCRYPTED_ACTIVATION_TEXT, KEY_ACTIVATION_IV)) {
            EncryptedTextRead.Missing -> return bindingFailure(license, "需要导入本机设备激活凭证。")
            is EncryptedTextRead.Available -> activation.value
            is EncryptedTextRead.Unreadable -> {
                return bindingFailure(license, storageFailureMessage("设备激活凭证", activation.reason))
            }
        }
        val activationCheck = dependencies.verifyDeviceActivation(activationText)
        val activation = activationCheck.activation
            ?: return bindingFailure(license, activationCheck.message)
        if (activation.licenseId != license.licenseId) {
            return bindingFailure(license, "设备激活凭证不属于当前 License。")
        }
        if (activation.deviceLimit != license.deviceLimit) {
            return bindingFailure(license, "设备激活凭证与 License 的设备数量不一致。")
        }
        val fingerprint = try {
            dependencies.fingerprintProvider()
        } catch (_: DeviceIdentityUnavailableException) {
            return bindingFailure(
                license,
                storageFailureMessage("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
            )
        } catch (_: Exception) {
            return bindingFailure(
                license,
                storageFailureMessage("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
            )
        }
        if (activation.deviceFingerprint != fingerprint) {
            return bindingFailure(license, "设备激活凭证不属于这台设备。")
        }

        return licenseCheck.copy(
            deviceBinding = DeviceBindingStatus(
                required = true,
                active = true,
                deviceLimit = license.deviceLimit,
                slotIndex = activation.slotIndex,
                message = "本机已绑定为第 ${activation.slotIndex}/${license.deviceLimit} 台设备。"
            )
        )
    }

    fun activate(licenseText: String): ProLicenseCheck {
        val current = currentState()
        if (isStoredDataUnreadable(current)) return current

        val trimmed = licenseText.trim()
        if (trimmed.startsWith("${OneStepDeviceLicenseVerifier.PREFIX}.")) {
            val fingerprint = try {
                dependencies.fingerprintProvider()
            } catch (_: DeviceIdentityUnavailableException) {
                return storageFailure("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
            } catch (_: Exception) {
                return storageFailure("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
            }
            val check = dependencies.verifyOneStepLicense(trimmed, fingerprint)
            if (!check.active) return check

            val encrypted = encrypt(trimmed) ?: return writeFailure("最终激活码")
            val write = updatePreservingExisting(
                PreferenceMutation(
                    strings = mapOf(
                        KEY_ENCRYPTED_ONE_STEP_TEXT to encrypted.cipherText,
                        KEY_ONE_STEP_IV to encrypted.iv
                    ),
                    removals = setOf(
                        KEY_ENCRYPTED_LICENSE_TEXT,
                        KEY_LICENSE_IV,
                        KEY_ACTIVATED_AT_MS,
                        KEY_ENCRYPTED_ACTIVATION_TEXT,
                        KEY_ACTIVATION_IV
                    )
                )
            )
            if (!write.success) return writeFailure("最终激活码", write.restored)
            return currentState()
        }

        val previousLicenseId = currentStoredLicenseId()
        val check = dependencies.verifyLicense(licenseText)
        if (!check.active) return check

        val encrypted = encrypt(trimmed) ?: return writeFailure("License")
        val newLicenseId = check.license?.licenseId
        val activationRemovals = if (previousLicenseId != null && previousLicenseId != newLicenseId) {
            setOf(KEY_ENCRYPTED_ACTIVATION_TEXT, KEY_ACTIVATION_IV)
        } else {
            emptySet()
        }
        val write = updatePreservingExisting(
            PreferenceMutation(
                strings = mapOf(
                    KEY_ENCRYPTED_LICENSE_TEXT to encrypted.cipherText,
                    KEY_LICENSE_IV to encrypted.iv
                ),
                longs = mapOf(KEY_ACTIVATED_AT_MS to dependencies.nowMillis()),
                removals = activationRemovals + setOf(KEY_ENCRYPTED_ONE_STEP_TEXT, KEY_ONE_STEP_IV)
            )
        )
        if (!write.success) return writeFailure("License", write.restored)
        return currentState()
    }

    fun deviceCode(): Result<DeviceCode> = runCatching {
        DeviceCodeCodec.create(dependencies.fingerprintProvider())
    }

    fun createDeviceActivationRequest(): DeviceActivationRequestResult {
        val licenseCheck = storedLicenseCheck()
        val license = licenseCheck.license
            ?: return DeviceActivationRequestResult(false, licenseCheck.message)
        if (!license.requiresDeviceBinding) {
            return DeviceActivationRequestResult(false, "当前 License 不需要设备绑定。")
        }
        val request = try {
            DeviceActivationRequestCodec.create(license, dependencies.fingerprintProvider())
        } catch (_: DeviceIdentityUnavailableException) {
            return DeviceActivationRequestResult(
                false,
                storageFailureMessage("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
            )
        } catch (_: Exception) {
            return DeviceActivationRequestResult(false, "无法生成本机激活请求。")
        }
        return DeviceActivationRequestResult(true, "已生成本机激活请求。", request)
    }

    fun activateDevice(activationText: String): ProLicenseCheck {
        val current = currentState()
        if (isStoredDataUnreadable(current)) return current

        val licenseCheck = storedLicenseCheck()
        val license = licenseCheck.license ?: return licenseCheck
        if (!license.requiresDeviceBinding) {
            return ProLicenseCheck(false, "当前 License 不需要设备绑定。", license)
        }
        val activationCheck = dependencies.verifyDeviceActivation(activationText)
        val activation = activationCheck.activation
            ?: return ProLicenseCheck(false, activationCheck.message, license)
        if (activation.licenseId != license.licenseId) {
            return ProLicenseCheck(false, "设备激活凭证不属于当前 License。", license)
        }
        if (activation.deviceLimit != license.deviceLimit) {
            return ProLicenseCheck(false, "设备激活凭证与 License 的设备数量不一致。", license)
        }
        val fingerprint = try {
            dependencies.fingerprintProvider()
        } catch (_: DeviceIdentityUnavailableException) {
            return bindingFailure(
                license,
                storageFailureMessage("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
            )
        } catch (_: Exception) {
            return bindingFailure(
                license,
                storageFailureMessage("本机设备身份", StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE)
            )
        }
        if (activation.deviceFingerprint != fingerprint) {
            return ProLicenseCheck(false, "设备激活凭证不属于这台设备。", license)
        }

        val encrypted = encrypt(activationText.trim())
            ?: return writeFailure("设备激活凭证", license = license)
        val write = updatePreservingExisting(
            PreferenceMutation(
                strings = mapOf(
                    KEY_ENCRYPTED_ACTIVATION_TEXT to encrypted.cipherText,
                    KEY_ACTIVATION_IV to encrypted.iv
                )
            )
        )
        if (!write.success) return writeFailure("设备激活凭证", write.restored, license)
        return currentState()
    }

    fun removeDeviceActivation(): Boolean {
        return updatePreservingExisting(
            PreferenceMutation(removals = setOf(KEY_ENCRYPTED_ACTIVATION_TEXT, KEY_ACTIVATION_IV))
        ).success
    }

    fun clear(): Boolean {
        return updatePreservingExisting(
            PreferenceMutation(
                removals = setOf(
                    KEY_ENCRYPTED_LICENSE_TEXT,
                    KEY_LICENSE_IV,
                    KEY_ACTIVATED_AT_MS,
                    KEY_ENCRYPTED_ACTIVATION_TEXT,
                    KEY_ACTIVATION_IV,
                    KEY_ENCRYPTED_ONE_STEP_TEXT,
                    KEY_ONE_STEP_IV
                )
            )
        ).success
    }

    fun hasFeature(feature: ProFeature): Boolean {
        val state = currentState()
        return state.active && state.license?.allows(feature) == true
    }

    fun isStoredDataUnreadable(state: ProLicenseCheck): Boolean {
        return state.message.startsWith(STORAGE_FAILURE_PREFIX)
    }

    private fun storedLicenseCheck(): ProLicenseCheck {
        return when (val stored = encryptedText(KEY_ENCRYPTED_LICENSE_TEXT, KEY_LICENSE_IV)) {
            EncryptedTextRead.Missing -> ProLicenseCheck(active = false, message = "未激活 Pro。")
            is EncryptedTextRead.Available -> dependencies.verifyLicense(stored.value)
            is EncryptedTextRead.Unreadable -> storageFailure("License", stored.reason)
        }
    }

    private fun currentStoredLicenseId(): String? = storedLicenseCheck().license?.licenseId

    private fun encryptedText(cipherTextKey: String, ivKey: String): EncryptedTextRead {
        val pair = try {
            val hasCipherText = dependencies.preferences.contains(cipherTextKey)
            val hasIv = dependencies.preferences.contains(ivKey)
            if (!hasCipherText && !hasIv) return EncryptedTextRead.Missing
            val cipherText = dependencies.preferences.getString(cipherTextKey)
            val iv = dependencies.preferences.getString(ivKey)
            if (!hasCipherText || !hasIv || cipherText.isNullOrBlank() || iv.isNullOrBlank()) {
                return EncryptedTextRead.Unreadable(StorageFailureReason.INCOMPLETE_PAIR)
            }
            cipherText to iv
        } catch (_: Exception) {
            return EncryptedTextRead.Unreadable(StorageFailureReason.READ_FAILED)
        }

        val keyExists = try {
            dependencies.keystoreKeyExists()
        } catch (_: Exception) {
            null
        }
        if (keyExists == false) {
            return EncryptedTextRead.Unreadable(StorageFailureReason.KEYSTORE_KEY_MISSING)
        }
        return try {
            EncryptedTextRead.Available(dependencies.encryptor.decrypt(pair.first, pair.second))
        } catch (_: Exception) {
            EncryptedTextRead.Unreadable(StorageFailureReason.DECRYPTION_FAILED)
        }
    }

    private fun encrypt(plainText: String): ProLicenseEncryptedText? {
        return try {
            dependencies.encryptor.encrypt(plainText)
        } catch (_: Exception) {
            null
        }
    }

    private fun bindingFailure(license: ProLicense, message: String): ProLicenseCheck {
        return ProLicenseCheck(
            active = false,
            message = message,
            license = license,
            deviceBinding = DeviceBindingStatus(
                required = true,
                active = false,
                deviceLimit = license.deviceLimit,
                message = message
            )
        )
    }

    private fun storageFailure(label: String, reason: StorageFailureReason): ProLicenseCheck {
        return ProLicenseCheck(active = false, message = storageFailureMessage(label, reason))
    }

    private fun storageFailureMessage(label: String, reason: StorageFailureReason): String {
        val detail = when (reason) {
            StorageFailureReason.INCOMPLETE_PAIR -> "密文或 IV 不完整"
            StorageFailureReason.KEYSTORE_KEY_MISSING -> "Keystore 密钥缺失"
            StorageFailureReason.DECRYPTION_FAILED -> "解密失败，数据可能已损坏"
            StorageFailureReason.READ_FAILED -> "读取失败"
            StorageFailureReason.DEVICE_IDENTITY_UNAVAILABLE -> "Keystore 密钥缺失或身份文件损坏"
            StorageFailureReason.LICENSE_MISSING -> "设备激活数据存在，但对应 License 缺失"
        }
        return "$STORAGE_FAILURE_PREFIX$label：$detail。"
    }

    private fun writeFailure(
        label: String,
        restored: Boolean = true,
        license: ProLicense? = null
    ): ProLicenseCheck {
        val suffix = if (restored) "原有授权数据已保留。" else "原有授权数据状态无法确认，请重试。"
        return ProLicenseCheck(false, "$label 有效，但保存失败；$suffix", license)
    }

    private fun updatePreservingExisting(mutation: PreferenceMutation): PreferenceWriteResult {
        val touchedKeys = mutation.strings.keys + mutation.longs.keys + mutation.removals
        val snapshot = try {
            touchedKeys.associateWith(::storedPreferenceValue)
        } catch (_: Exception) {
            return PreferenceWriteResult(success = false, restored = false)
        }
        val committed = try {
            dependencies.preferences.update(mutation)
        } catch (_: Exception) {
            false
        }
        if (committed) return PreferenceWriteResult(success = true, restored = true)

        val restore = PreferenceMutation(
            strings = snapshot.mapNotNull { (key, value) ->
                (value as? StoredPreferenceValue.Text)?.let { key to it.value }
            }.toMap(),
            longs = snapshot.mapNotNull { (key, value) ->
                (value as? StoredPreferenceValue.Number)?.let { key to it.value }
            }.toMap(),
            removals = snapshot.filterValues { it == StoredPreferenceValue.Missing }.keys
        )
        val restored = try {
            dependencies.preferences.update(restore)
        } catch (_: Exception) {
            false
        }
        return PreferenceWriteResult(success = false, restored = restored)
    }

    private fun storedPreferenceValue(key: String): StoredPreferenceValue {
        if (!dependencies.preferences.contains(key)) return StoredPreferenceValue.Missing
        return if (key == KEY_ACTIVATED_AT_MS) {
            StoredPreferenceValue.Number(dependencies.preferences.getLong(key))
        } else {
            StoredPreferenceValue.Text(dependencies.preferences.getString(key).orEmpty())
        }
    }

    private data class Dependencies(
        val preferences: ProLicensePreferences,
        val verifyLicense: (String) -> ProLicenseCheck,
        val verifyOneStepLicense: (String, String) -> ProLicenseCheck,
        val verifyDeviceActivation: (String) -> DeviceActivationCheck,
        val encryptor: ProLicenseTextEncryptor,
        val fingerprintProvider: () -> String,
        val keystoreKeyExists: () -> Boolean,
        val nowMillis: () -> Long
    )

    private sealed interface EncryptedTextRead {
        data object Missing : EncryptedTextRead
        data class Available(val value: String) : EncryptedTextRead
        data class Unreadable(val reason: StorageFailureReason) : EncryptedTextRead
    }

    private enum class StorageFailureReason {
        INCOMPLETE_PAIR,
        KEYSTORE_KEY_MISSING,
        DECRYPTION_FAILED,
        READ_FAILED,
        DEVICE_IDENTITY_UNAVAILABLE,
        LICENSE_MISSING
    }

    private sealed interface StoredPreferenceValue {
        data object Missing : StoredPreferenceValue
        data class Text(val value: String) : StoredPreferenceValue
        data class Number(val value: Long) : StoredPreferenceValue
    }

    private data class PreferenceWriteResult(
        val success: Boolean,
        val restored: Boolean
    )

    companion object {
        private const val PREFS_NAME = "privacy_hub_pro_license"
        private const val KEYSTORE_ALIAS = "privacy_hub_pro_license_key"
        private const val STORAGE_FAILURE_PREFIX = "Pro 授权数据不可读取："
        internal const val KEY_ENCRYPTED_LICENSE_TEXT = "encrypted_license_text"
        internal const val KEY_LICENSE_IV = "license_iv"
        internal const val KEY_ACTIVATED_AT_MS = "activated_at_ms"
        internal const val KEY_ENCRYPTED_ACTIVATION_TEXT = "encrypted_activation_text"
        internal const val KEY_ACTIVATION_IV = "activation_iv"
        internal const val KEY_ENCRYPTED_ONE_STEP_TEXT = "encrypted_one_step_license_text"
        internal const val KEY_ONE_STEP_IV = "one_step_license_iv"

        private fun defaultDependencies(context: Context): Dependencies {
            val verifier = ProLicenseVerifier()
            val oneStepVerifier = OneStepDeviceLicenseVerifier()
            val activationVerifier = DeviceActivationVerifier()
            return Dependencies(
                preferences = SharedPreferencesAdapter(
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ),
                verifyLicense = { verifier.verify(it) },
                verifyOneStepLicense = { text, fingerprint -> oneStepVerifier.verify(text, fingerprint) },
                verifyDeviceActivation = { activationVerifier.verify(it) },
                encryptor = KeystoreTextEncryptorAdapter(KeystoreTextEncryptor(KEYSTORE_ALIAS)),
                fingerprintProvider = DeviceFingerprintProvider(context)::fingerprint,
                keystoreKeyExists = {
                    KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.containsAlias(KEYSTORE_ALIAS)
                },
                nowMillis = System::currentTimeMillis
            )
        }

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

internal data class ProLicenseEncryptedText(
    val cipherText: String,
    val iv: String
)

internal interface ProLicenseTextEncryptor {
    fun encrypt(plainText: String): ProLicenseEncryptedText
    fun decrypt(cipherText: String, iv: String): String
}

internal data class PreferenceMutation(
    val strings: Map<String, String> = emptyMap(),
    val longs: Map<String, Long> = emptyMap(),
    val removals: Set<String> = emptySet()
)

internal interface ProLicensePreferences {
    fun contains(key: String): Boolean
    fun getString(key: String): String?
    fun getLong(key: String): Long
    fun update(mutation: PreferenceMutation): Boolean
}

private class KeystoreTextEncryptorAdapter(
    private val encryptor: KeystoreTextEncryptor
) : ProLicenseTextEncryptor {
    override fun encrypt(plainText: String): ProLicenseEncryptedText {
        val encrypted = encryptor.encrypt(plainText)
        return ProLicenseEncryptedText(encrypted.cipherText, encrypted.iv)
    }

    override fun decrypt(cipherText: String, iv: String): String {
        return encryptor.decrypt(cipherText, iv)
    }
}

private class SharedPreferencesAdapter(
    private val preferences: SharedPreferences
) : ProLicensePreferences {
    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun getLong(key: String): Long = preferences.getLong(key, 0L)

    override fun update(mutation: PreferenceMutation): Boolean {
        val editor = preferences.edit()
        mutation.strings.forEach(editor::putString)
        mutation.longs.forEach(editor::putLong)
        mutation.removals.forEach(editor::remove)
        return editor.commit()
    }
}
