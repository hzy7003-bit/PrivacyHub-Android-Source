package com.privacyhub.pro

class ProLicenseVerifier(
    private val publicKeyPem: String = ProLicensePublicKey.PEM
) {
    private val signedPayloadVerifier = SignedPayloadVerifier(publicKeyPem)

    fun verify(licenseText: String, nowMillis: Long = System.currentTimeMillis()): ProLicenseCheck {
        val signedPayload = signedPayloadVerifier.verify(licenseText, PREFIX, "License")
        if (!signedPayload.valid) {
            return ProLicenseCheck(active = false, message = signedPayload.message)
        }

        val fields = signedPayload.fields
        val licenseId = fields["licenseId"].orEmpty().trim()
        val holder = fields["holder"].orEmpty().trim()
        if (licenseId.isBlank()) {
            return ProLicenseCheck(active = false, message = "License 缺少编号。")
        }
        if (holder.isBlank()) {
            return ProLicenseCheck(active = false, message = "License 缺少持有人。")
        }

        val issuedAt = fields["issuedAt"]?.toLongOrNull()
        if (issuedAt != null && issuedAt > nowMillis + CLOCK_SKEW_MS) {
            return ProLicenseCheck(active = false, message = "License 生效时间异常。")
        }

        val expiresAt = fields["expiresAt"]?.takeIf { it != "never" }?.toLongOrNull()
        if (expiresAt != null && nowMillis > expiresAt) {
            return ProLicenseCheck(active = false, message = "License 已过期。")
        }

        val version = fields["version"]?.toIntOrNull() ?: LEGACY_VERSION
        if (version !in LEGACY_VERSION..CURRENT_VERSION) {
            return ProLicenseCheck(active = false, message = "License 版本不受支持。")
        }
        val createdAt = fields["createdAt"]?.toLongOrNull() ?: issuedAt
        if (createdAt != null && createdAt > nowMillis + CLOCK_SKEW_MS) {
            return ProLicenseCheck(active = false, message = "License 创建时间异常。")
        }
        val deviceLimit = when (version) {
            LEGACY_VERSION -> 0
            else -> fields["deviceLimit"]?.toIntOrNull() ?: DEFAULT_DEVICE_LIMIT
        }
        if (version >= ProLicense.DEVICE_BINDING_LICENSE_VERSION && deviceLimit !in 1..MAX_DEVICE_LIMIT) {
            return ProLicenseCheck(active = false, message = "License 设备数量设置无效。")
        }

        val featureTokens = fields["features"].orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val allFeatures = "all" in featureTokens
        val features = featureTokens.mapNotNull(ProFeature::fromId).toSet()
        if (!allFeatures && features.isEmpty()) {
            return ProLicenseCheck(active = false, message = "License 没有可用的 Pro 功能。")
        }

        val license = ProLicense(
            licenseId = licenseId,
            holder = holder,
            issuedAtMillis = issuedAt,
            expiresAtMillis = expiresAt,
            features = features,
            allFeatures = allFeatures,
            version = version,
            createdAtMillis = createdAt,
            deviceLimit = deviceLimit
        )
        return ProLicenseCheck(active = true, message = "Pro 已激活。", license = license)
    }

    companion object {
        const val PREFIX = "PHUB1"
        const val LEGACY_VERSION = 1
        const val CURRENT_VERSION = 2
        const val DEFAULT_DEVICE_LIMIT = 3
        const val MAX_DEVICE_LIMIT = 3
        private const val CLOCK_SKEW_MS = 5 * 60 * 1000L
    }
}
