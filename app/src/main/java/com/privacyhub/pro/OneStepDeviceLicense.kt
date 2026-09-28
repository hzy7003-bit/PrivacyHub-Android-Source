package com.privacyhub.pro

import java.nio.charset.StandardCharsets
import java.util.Base64

data class DeviceCode(val value: String, val fingerprint: String)

object DeviceCodeCodec {
    private const val PREFIX = "PHDVC1"

    fun create(fingerprint: String): DeviceCode {
        val payload = "version=1\nproduct=privacy_hub\ndeviceFingerprint=$fingerprint"
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return DeviceCode("$PREFIX.$encoded", fingerprint)
    }
}

class OneStepDeviceLicenseVerifier(
    private val publicKeyPem: String = ProLicensePublicKey.PEM
) {
    private val signedPayloadVerifier = SignedPayloadVerifier(publicKeyPem)

    fun verify(
        activationText: String,
        expectedFingerprint: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ProLicenseCheck {
        val signed = signedPayloadVerifier.verify(activationText, PREFIX, "最终激活码")
        if (!signed.valid) return ProLicenseCheck(false, signed.message)
        val fields = signed.fields
        val activationVersion = fields["version"]?.toIntOrNull()
            ?: return ProLicenseCheck(false, "最终激活码缺少版本。")
        if (activationVersion != CURRENT_VERSION) {
            return ProLicenseCheck(false, "最终激活码版本不受支持。")
        }
        val licenseId = fields["licenseId"].orEmpty().trim()
        val holder = fields["holder"].orEmpty().trim()
        val issuedAt = fields["issuedAt"]?.toLongOrNull()
        val createdAt = fields["createdAt"]?.toLongOrNull()
        val deviceLimit = fields["deviceLimit"]?.toIntOrNull()
        val slotIndex = fields["slotIndex"]?.toIntOrNull()
        val fingerprint = fields["deviceFingerprint"].orEmpty().lowercase()
        val activationId = fields["activationId"].orEmpty().trim()
        if (licenseId.isBlank() || holder.isBlank() || issuedAt == null || createdAt == null ||
            deviceLimit == null || slotIndex == null || activationId.isBlank()
        ) return ProLicenseCheck(false, "最终激活码信息不完整。")
        if (issuedAt <= 0 || createdAt <= 0 || createdAt > nowMillis + CLOCK_SKEW_MS) {
            return ProLicenseCheck(false, "最终激活码时间异常。")
        }
        if (!FINGERPRINT_PATTERN.matches(fingerprint) || fingerprint != expectedFingerprint.lowercase()) {
            return ProLicenseCheck(false, "最终激活码不属于这台设备。")
        }
        if (deviceLimit !in 1..ProLicenseVerifier.MAX_DEVICE_LIMIT || slotIndex !in 1..deviceLimit) {
            return ProLicenseCheck(false, "最终激活码设备槽位无效。")
        }
        val expiresAt = fields["expiresAt"].orEmpty().let { if (it == "never") null else it.toLongOrNull() }
        if (fields["expiresAt"].orEmpty() != "never" && expiresAt == null) return ProLicenseCheck(false, "最终激活码有效期异常。")
        if (expiresAt != null && expiresAt < nowMillis) return ProLicenseCheck(false, "最终激活码已过期。")
        val tokens = fields["features"].orEmpty().split(",").map(String::trim).filter(String::isNotBlank)
        val allFeatures = "all" in tokens
        val features = tokens.mapNotNull(ProFeature::fromId).toSet()
        if (!allFeatures && features.isEmpty()) return ProLicenseCheck(false, "最终激活码没有可用的 Pro 功能。")
        val license = ProLicense(
            licenseId = licenseId,
            holder = holder,
            issuedAtMillis = issuedAt,
            expiresAtMillis = expiresAt,
            features = features,
            allFeatures = allFeatures,
            version = ProLicense.DEVICE_BINDING_LICENSE_VERSION,
            createdAtMillis = createdAt,
            deviceLimit = deviceLimit
        )
        return ProLicenseCheck(
            active = true,
            message = "Pro 已激活。",
            license = license,
            deviceBinding = DeviceBindingStatus(
                required = true,
                active = true,
                deviceLimit = deviceLimit,
                slotIndex = slotIndex,
                message = "本机已绑定为第 $slotIndex/$deviceLimit 台设备。"
            )
        )
    }

    companion object {
        const val PREFIX = "PHLIC1"
        private const val CURRENT_VERSION = 1
        private const val CLOCK_SKEW_MS = 5 * 60 * 1000L
        private val FINGERPRINT_PATTERN = Regex("[a-f0-9]{64}")
    }
}
