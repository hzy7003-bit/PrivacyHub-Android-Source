package com.privacyhub.pro

import java.nio.charset.StandardCharsets
import java.util.Base64

internal object DeviceActivationRequestCodec {
    private const val PREFIX = "PHREQ1"

    fun create(license: ProLicense, fingerprint: String): DeviceActivationRequest {
        val payload = buildString {
            appendLine("version=1")
            appendLine("product=privacy_hub")
            appendLine("licenseId=${license.licenseId}")
            appendLine("deviceFingerprint=$fingerprint")
            appendLine("deviceLimit=${license.deviceLimit}")
        }.trimEnd()
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return DeviceActivationRequest(
            requestText = "$PREFIX.$encoded",
            deviceFingerprint = fingerprint,
            licenseId = license.licenseId
        )
    }
}
