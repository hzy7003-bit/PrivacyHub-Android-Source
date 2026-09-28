package com.privacyhub.pro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProLicenseStoreTest {
    @Test
    fun noStoredDataIsReportedAsNotActivatedWithoutWriting() {
        val preferences = FakePreferences()
        val store = store(preferences)

        val state = store.currentState()

        assertFalse(state.active)
        assertEquals("未激活 Pro。", state.message)
        assertFalse(store.isStoredDataUnreadable(state))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun unreadableOrphanDeviceActivationIsNotReportedAsNotActivated() {
        val preferences = FakePreferences().apply {
            values[ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT] = "broken-activation"
            values[ProLicenseStore.KEY_ACTIVATION_IV] = "activation-iv"
        }
        val store = store(preferences, cipher = FakeCipher(failDecryptFor = setOf("broken-activation")))

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("设备激活凭证：解密失败"))
        assertFalse(state.message.contains("未激活"))
        assertTrue(store.isStoredDataUnreadable(state))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun readsNormalStoredLicense() {
        val preferences = FakePreferences().apply {
            seedLicensePair("license-cipher", "license-iv")
        }
        val cipher = FakeCipher(mapOf("license-cipher" to "legacy-license"))
        val store = store(preferences, cipher = cipher)

        val state = store.currentState()

        assertTrue(state.active)
        assertEquals("legacy", state.licenseId)
        assertTrue(store.hasFeature(ProFeature.Autofill))
    }

    @Test
    fun partialCipherPairIsUnreadableAndIsNotCleared() {
        val preferences = FakePreferences().apply {
            values[ProLicenseStore.KEY_ENCRYPTED_LICENSE_TEXT] = "license-cipher"
        }
        val store = store(preferences)

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("License：密文或 IV 不完整"))
        assertTrue(store.isStoredDataUnreadable(state))
        assertTrue(preferences.updates.isEmpty())
        assertEquals("license-cipher", preferences.values[ProLicenseStore.KEY_ENCRYPTED_LICENSE_TEXT])
    }

    @Test
    fun ivWithoutCipherIsUnreadableAndIsNotCleared() {
        val preferences = FakePreferences().apply {
            values[ProLicenseStore.KEY_LICENSE_IV] = "license-iv"
        }
        val store = store(preferences)

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("License：密文或 IV 不完整"))
        assertTrue(store.isStoredDataUnreadable(state))
        assertTrue(preferences.updates.isEmpty())
        assertEquals("license-iv", preferences.values[ProLicenseStore.KEY_LICENSE_IV])
    }

    @Test
    fun missingKeystoreKeyIsDistinctFromMissingLicense() {
        val preferences = FakePreferences().apply {
            seedLicensePair("license-cipher", "license-iv")
        }
        val store = store(preferences, keystoreKeyExists = { false })

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("Keystore 密钥缺失"))
        assertFalse(state.message.contains("未激活"))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun licenseDecryptFailureIsReportedAsCorruptAndFeatureRemainsDisabled() {
        val preferences = FakePreferences().apply {
            seedLicensePair("broken-license", "license-iv")
        }
        val cipher = FakeCipher(failDecryptFor = setOf("broken-license"))
        val store = store(preferences, cipher = cipher)

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("License：解密失败，数据可能已损坏"))
        assertFalse(store.hasFeature(ProFeature.Autofill))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun activatingReplacementDoesNotOverwriteUnreadableLicenseData() {
        val preferences = FakePreferences().apply {
            seedLicensePair("broken-license", "license-iv")
        }
        val store = store(preferences, cipher = FakeCipher(failDecryptFor = setOf("broken-license")))

        val result = store.activate("new-license")

        assertFalse(result.active)
        assertTrue(store.isStoredDataUnreadable(result))
        assertTrue(preferences.updates.isEmpty())
        assertEquals("broken-license", preferences.values[ProLicenseStore.KEY_ENCRYPTED_LICENSE_TEXT])
        assertEquals("license-iv", preferences.values[ProLicenseStore.KEY_LICENSE_IV])
    }

    @Test
    fun corruptOneStepLicenseDoesNotFallBackToNormalLicenseOrClearData() {
        val preferences = FakePreferences().apply {
            values[ProLicenseStore.KEY_ENCRYPTED_ONE_STEP_TEXT] = "broken-one-step"
            values[ProLicenseStore.KEY_ONE_STEP_IV] = "one-step-iv"
            seedLicensePair("license-cipher", "license-iv")
        }
        val cipher = FakeCipher(
            plainTextByCipher = mapOf("license-cipher" to "legacy-license"),
            failDecryptFor = setOf("broken-one-step")
        )
        val store = store(preferences, cipher = cipher)

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("最终激活码：解密失败"))
        assertFalse(state.message.contains("未激活"))
        assertTrue(preferences.updates.isEmpty())
        assertEquals("broken-one-step", preferences.values[ProLicenseStore.KEY_ENCRYPTED_ONE_STEP_TEXT])
    }

    @Test
    fun corruptDeviceActivationIsUnreadableInsteadOfUnbound() {
        val preferences = FakePreferences().apply {
            seedLicensePair("bound-license-cipher", "license-iv")
            values[ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT] = "broken-activation"
            values[ProLicenseStore.KEY_ACTIVATION_IV] = "activation-iv"
        }
        val cipher = FakeCipher(
            plainTextByCipher = mapOf("bound-license-cipher" to "bound-license"),
            failDecryptFor = setOf("broken-activation")
        )
        val store = store(preferences, cipher = cipher)

        val state = store.currentState()

        assertFalse(state.active)
        assertEquals("bound", state.licenseId)
        assertFalse(state.deviceBinding?.active == true)
        assertTrue(state.message.contains("设备激活凭证：解密失败"))
        assertFalse(state.message.contains("需要导入"))
        assertTrue(store.isStoredDataUnreadable(state))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun replacingUnreadableDeviceActivationRequiresExplicitRemovalFirst() {
        val preferences = FakePreferences().apply {
            seedLicensePair("bound-license-cipher", "license-iv")
            values[ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT] = "broken-activation"
            values[ProLicenseStore.KEY_ACTIVATION_IV] = "activation-iv"
        }
        val cipher = FakeCipher(
            plainTextByCipher = mapOf("bound-license-cipher" to "bound-license"),
            failDecryptFor = setOf("broken-activation")
        )
        val store = store(preferences, cipher = cipher)

        val result = store.activateDevice("new-activation")

        assertFalse(result.active)
        assertTrue(store.isStoredDataUnreadable(result))
        assertTrue(preferences.updates.isEmpty())
        assertEquals("broken-activation", preferences.values[ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT])
        assertEquals("activation-iv", preferences.values[ProLicenseStore.KEY_ACTIVATION_IV])
    }

    @Test
    fun unavailableDeviceIdentityKeepsOneStepLicenseFailClosed() {
        val preferences = FakePreferences().apply {
            values[ProLicenseStore.KEY_ENCRYPTED_ONE_STEP_TEXT] = "one-step-cipher"
            values[ProLicenseStore.KEY_ONE_STEP_IV] = "one-step-iv"
        }
        val cipher = FakeCipher(mapOf("one-step-cipher" to "PHLIC1.valid"))
        val store = store(
            preferences,
            cipher = cipher,
            fingerprintProvider = { throw DeviceIdentityUnavailableException() }
        )

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("本机设备身份：Keystore 密钥缺失或身份文件损坏"))
        assertTrue(store.isStoredDataUnreadable(state))
        assertFalse(store.hasFeature(ProFeature.Autofill))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun unexpectedDeviceIdentityReadFailureIsNotReportedAsNotActivated() {
        val preferences = FakePreferences().apply {
            values[ProLicenseStore.KEY_ENCRYPTED_ONE_STEP_TEXT] = "one-step-cipher"
            values[ProLicenseStore.KEY_ONE_STEP_IV] = "one-step-iv"
        }
        val cipher = FakeCipher(mapOf("one-step-cipher" to "PHLIC1.valid"))
        val store = store(
            preferences,
            cipher = cipher,
            fingerprintProvider = { error("fixture identity read failure") }
        )

        val state = store.currentState()

        assertFalse(state.active)
        assertTrue(state.message.contains("本机设备身份：Keystore 密钥缺失或身份文件损坏"))
        assertFalse(state.message.contains("未激活"))
        assertTrue(store.isStoredDataUnreadable(state))
        assertFalse(store.hasFeature(ProFeature.Autofill))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun unavailableDeviceIdentityIsNotReportedAsUnbound() {
        val preferences = FakePreferences().apply {
            seedLicensePair("bound-license-cipher", "license-iv")
            values[ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT] = "activation-cipher"
            values[ProLicenseStore.KEY_ACTIVATION_IV] = "activation-iv"
        }
        val cipher = FakeCipher(
            mapOf(
                "bound-license-cipher" to "bound-license",
                "activation-cipher" to "stored-activation"
            )
        )
        val store = store(
            preferences,
            cipher = cipher,
            fingerprintProvider = { throw DeviceIdentityUnavailableException() }
        )

        val state = store.currentState()

        assertFalse(state.active)
        assertEquals("bound", state.licenseId)
        assertTrue(state.message.contains("本机设备身份：Keystore 密钥缺失或身份文件损坏"))
        assertFalse(state.message.contains("需要导入"))
        assertTrue(store.isStoredDataUnreadable(state))
        assertFalse(store.hasFeature(ProFeature.Autofill))
        assertTrue(preferences.updates.isEmpty())
    }

    @Test
    fun licensePairIsWrittenTogetherWithSynchronousResult() {
        val preferences = FakePreferences()
        val store = store(preferences)

        val result = store.activate("new-license")

        assertTrue(result.active)
        assertEquals(1, preferences.updates.size)
        val write = preferences.updates.single()
        assertEquals("encrypted:new-license", write.strings[ProLicenseStore.KEY_ENCRYPTED_LICENSE_TEXT])
        assertEquals("iv:new-license", write.strings[ProLicenseStore.KEY_LICENSE_IV])
        assertEquals(1234L, write.longs[ProLicenseStore.KEY_ACTIVATED_AT_MS])
    }

    @Test
    fun failedLicenseCommitReportsFailureAndRestoresOldPair() {
        val preferences = FakePreferences().apply {
            seedLicensePair("old-cipher", "old-iv")
            updateResults += listOf(false, true)
        }
        val cipher = FakeCipher(mapOf("old-cipher" to "old-license"))
        val store = store(preferences, cipher = cipher)

        val result = store.activate("new-license")

        assertFalse(result.active)
        assertTrue(result.message.contains("保存失败"))
        assertTrue(result.message.contains("原有授权数据已保留"))
        assertEquals("old-cipher", preferences.values[ProLicenseStore.KEY_ENCRYPTED_LICENSE_TEXT])
        assertEquals("old-iv", preferences.values[ProLicenseStore.KEY_LICENSE_IV])
        assertEquals(2, preferences.updates.size)
        assertTrue(preferences.updates.first().strings.keys.containsAll(
            listOf(ProLicenseStore.KEY_ENCRYPTED_LICENSE_TEXT, ProLicenseStore.KEY_LICENSE_IV)
        ))
    }

    @Test
    fun failedDeviceActivationCommitRestoresOldActivationPair() {
        val preferences = FakePreferences().apply {
            seedLicensePair("bound-license-cipher", "license-iv")
            values[ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT] = "old-activation-cipher"
            values[ProLicenseStore.KEY_ACTIVATION_IV] = "old-activation-iv"
            updateResults += listOf(false, true)
        }
        val cipher = FakeCipher(mapOf("bound-license-cipher" to "bound-license"))
        val store = store(preferences, cipher = cipher)

        val result = store.activateDevice("new-activation")

        assertFalse(result.active)
        assertTrue(result.message.contains("保存失败"))
        assertEquals("old-activation-cipher", preferences.values[ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT])
        assertEquals("old-activation-iv", preferences.values[ProLicenseStore.KEY_ACTIVATION_IV])
        assertTrue(preferences.updates.first().strings.keys.containsAll(
            listOf(ProLicenseStore.KEY_ENCRYPTED_ACTIVATION_TEXT, ProLicenseStore.KEY_ACTIVATION_IV)
        ))
    }

    private fun store(
        preferences: FakePreferences,
        cipher: FakeCipher = FakeCipher(),
        keystoreKeyExists: () -> Boolean = { true },
        fingerprintProvider: () -> String = { fingerprint }
    ): ProLicenseStore {
        return ProLicenseStore(
            preferences = preferences,
            verifyLicense = ::verifyLicense,
            verifyOneStepLicense = { text, _ ->
                if (text == "PHLIC1.valid") activeCheck(boundLicense)
                else ProLicenseCheck(false, "最终激活码无效。")
            },
            verifyDeviceActivation = { text ->
                if (text == "new-activation" || text == "stored-activation") {
                    DeviceActivationCheck(
                        active = true,
                        message = "设备激活凭证有效。",
                        activation = DeviceActivation(
                            licenseId = "bound",
                            activationId = "activation",
                            slotIndex = 1,
                            deviceLimit = 3,
                            deviceFingerprint = fingerprint,
                            createdAtMillis = 1L,
                            version = 1
                        )
                    )
                } else {
                    DeviceActivationCheck(false, "设备激活凭证无效。")
                }
            },
            encryptor = cipher,
            fingerprintProvider = fingerprintProvider,
            keystoreKeyExists = keystoreKeyExists,
            nowMillis = { 1234L }
        )
    }

    private fun verifyLicense(text: String): ProLicenseCheck {
        return when (text) {
            "legacy-license" -> activeCheck(legacyLicense)
            "old-license" -> activeCheck(legacyLicense.copy(licenseId = "old"))
            "new-license" -> activeCheck(legacyLicense.copy(licenseId = "new"))
            "bound-license" -> activeCheck(boundLicense)
            else -> ProLicenseCheck(false, "License 无效。")
        }
    }

    private fun activeCheck(license: ProLicense): ProLicenseCheck {
        return ProLicenseCheck(true, "Pro 已激活。", license)
    }

    private class FakeCipher(
        private val plainTextByCipher: Map<String, String> = emptyMap(),
        private val failDecryptFor: Set<String> = emptySet()
    ) : ProLicenseTextEncryptor {
        override fun encrypt(plainText: String): ProLicenseEncryptedText {
            return ProLicenseEncryptedText("encrypted:$plainText", "iv:$plainText")
        }

        override fun decrypt(cipherText: String, iv: String): String {
            if (cipherText in failDecryptFor) error("injected decrypt failure")
            return plainTextByCipher[cipherText]
                ?: cipherText.removePrefix("encrypted:")
        }
    }

    private class FakePreferences : ProLicensePreferences {
        val values = linkedMapOf<String, Any>()
        val updates = mutableListOf<PreferenceMutation>()
        val updateResults = mutableListOf<Boolean>()

        fun seedLicensePair(cipherText: String, iv: String) {
            values[ProLicenseStore.KEY_ENCRYPTED_LICENSE_TEXT] = cipherText
            values[ProLicenseStore.KEY_LICENSE_IV] = iv
        }

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun getString(key: String): String? = values[key] as? String

        override fun getLong(key: String): Long = values[key] as? Long ?: 0L

        override fun update(mutation: PreferenceMutation): Boolean {
            updates += mutation
            mutation.strings.forEach { (key, value) -> values[key] = value }
            mutation.longs.forEach { (key, value) -> values[key] = value }
            mutation.removals.forEach(values::remove)
            return if (updateResults.isEmpty()) true else updateResults.removeAt(0)
        }
    }

    companion object {
        private val fingerprint = "a".repeat(64)
        private val legacyLicense = ProLicense(
            licenseId = "legacy",
            holder = "Tester",
            issuedAtMillis = 1L,
            expiresAtMillis = null,
            features = setOf(ProFeature.Autofill),
            allFeatures = false,
            version = 1,
            createdAtMillis = 1L,
            deviceLimit = 0
        )
        private val boundLicense = legacyLicense.copy(
            licenseId = "bound",
            version = 2,
            deviceLimit = 3
        )
    }
}
