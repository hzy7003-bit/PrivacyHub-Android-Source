package com.privacyhub.pro

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceActivationRequestCodecTest {
    @Test
    fun encodesOnlyHashedDeviceIdentityInRequest() {
        val license = ProLicense(
            licenseId = "test-device-001",
            holder = "Tester",
            issuedAtMillis = 1L,
            expiresAtMillis = null,
            features = emptySet(),
            allFeatures = true,
            version = 2,
            createdAtMillis = 1L,
            deviceLimit = 3
        )

        val request = DeviceActivationRequestCodec.create(license, "c".repeat(64))
        val payload = String(
            Base64.getUrlDecoder().decode(request.requestText.removePrefix("PHREQ1.")),
            StandardCharsets.UTF_8
        )

        assertTrue(request.requestText.startsWith("PHREQ1."))
        assertEquals("test-device-001", request.licenseId)
        assertTrue(payload.contains("deviceFingerprint=${"c".repeat(64)}"))
        assertTrue(!payload.contains("android-id"))
    }
}
