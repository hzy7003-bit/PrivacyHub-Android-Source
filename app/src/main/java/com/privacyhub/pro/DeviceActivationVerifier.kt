package com.privacyhub.pro

class DeviceActivationVerifier(
    private val publicKeyPem: String = ProLicensePublicKey.PEM
) {
    private val signedPayloadVerifier = SignedPayloadVerifier(publicKeyPem)

    fun verify(activationText: String, nowMillis: Long = System.currentTimeMillis()): DeviceActivationCheck {
        val signedPayload = signedPayloadVerifier.verify(activationText, PREFIX, "设备激活凭证")
        if (!signedPayload.valid) {
            return DeviceActivationCheck(active = false, message = signedPayload.message)
        }

        val fields = signedPayload.fields
        val version = fields["version"]?.toIntOrNull()
            ?: return DeviceActivationCheck(active = false, message = "设备激活凭证缺少版本。")
        if (version != CURRENT_VERSION) {
            return DeviceActivationCheck(active = false, message = "设备激活凭证版本不受支持。")
        }

        val licenseId = fields["licenseId"].orEmpty().trim()
        val activationId = fields["activationId"].orEmpty().trim()
        val fingerprint = fields["deviceFingerprint"].orEmpty().trim().lowercase()
        val slotIndex = fields["slotIndex"]?.toIntOrNull()
        val deviceLimit = fields["deviceLimit"]?.toIntOrNull()
        val createdAt = fields["createdAt"]?.toLongOrNull()
        if (licenseId.isBlank() || activationId.isBlank() || slotIndex == null || deviceLimit == null || createdAt == null) {
            return DeviceActivationCheck(active = false, message = "设备激活凭证信息不完整。")
        }
        if (!FINGERPRINT_PATTERN.matches(fingerprint)) {
            return DeviceActivationCheck(active = false, message = "设备激活凭证指纹无效。")
        }
        if (deviceLimit !in 1..ProLicenseVerifier.MAX_DEVICE_LIMIT || slotIndex !in 1..deviceLimit) {
            return DeviceActivationCheck(active = false, message = "设备激活凭证槽位无效。")
        }
        if (createdAt > nowMillis + CLOCK_SKEW_MS) {
            return DeviceActivationCheck(active = false, message = "设备激活凭证创建时间异常。")
        }

        return DeviceActivationCheck(
            active = true,
            message = "设备激活凭证有效。",
            activation = DeviceActivation(
                licenseId = licenseId,
                activationId = activationId,
                slotIndex = slotIndex,
                deviceLimit = deviceLimit,
                deviceFingerprint = fingerprint,
                createdAtMillis = createdAt,
                version = version
            )
        )
    }

    companion object {
        const val PREFIX = "PHACT1"
        const val CURRENT_VERSION = 1
        private const val CLOCK_SKEW_MS = 5 * 60 * 1000L
        private val FINGERPRINT_PATTERN = Regex("[a-f0-9]{64}")
    }
}
