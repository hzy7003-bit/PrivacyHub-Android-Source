package com.privacyhub.pro

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProLicenseVerifierTest {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    @Test
    fun acceptsValidAllFeatureLicense() {
        val now = 1_700_000_000_000L
        val license = signLicense(
            """
            licenseId=test-001
            holder=Tester
            issuedAt=${now - 1_000}
            expiresAt=never
            features=all
            """.trimIndent()
        )

        val result = verifier().verify(license, now)

        assertTrue(result.active)
        assertEquals("test-001", result.license?.licenseId)
        assertTrue(result.license?.allows(ProFeature.AdvancedSearch) == true)
        assertTrue(result.license?.allFeatures == true)
    }

    @Test
    fun rejectsTamperedPayload() {
        val now = 1_700_000_000_000L
        val license = signLicense(
            """
            licenseId=test-002
            holder=Tester
            issuedAt=${now - 1_000}
            expiresAt=never
            features=advanced_search
            """.trimIndent()
        )
        val parts = license.split(".")
        val tamperedPayload = base64Url(
            """
            licenseId=test-002
            holder=Tester
            issuedAt=${now - 1_000}
            expiresAt=never
            features=all
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )

        val result = verifier().verify("${parts[0]}.$tamperedPayload.${parts[2]}", now)

        assertFalse(result.active)
        assertEquals("License 签名无效。", result.message)
    }

    @Test
    fun rejectsExpiredLicense() {
        val now = 1_700_000_000_000L
        val license = signLicense(
            """
            licenseId=test-003
            holder=Tester
            issuedAt=${now - 10_000}
            expiresAt=${now - 1}
            features=advanced_search
            """.trimIndent()
        )

        val result = verifier().verify(license, now)

        assertFalse(result.active)
        assertEquals("License 已过期。", result.message)
    }

    @Test
    fun acceptsV2LicenseWithThreeDeviceLimit() {
        val now = 1_700_000_000_000L
        val license = signLicense(
            """
            licenseId=test-device-001
            holder=Tester
            issuedAt=${now - 1_000}
            expiresAt=never
            features=all
            version=2
            createdAt=${now - 1_000}
            deviceLimit=3
            """.trimIndent()
        )

        val result = verifier().verify(license, now)

        assertTrue(result.active)
        assertEquals(2, result.license?.version)
        assertEquals(3, result.license?.deviceLimit)
        assertTrue(result.license?.requiresDeviceBinding == true)
    }

    @Test
    fun keepsLegacyLicenseCompatibleWithoutDeviceBinding() {
        val now = 1_700_000_000_000L
        val license = signLicense(
            """
            licenseId=test-legacy-001
            holder=Tester
            issuedAt=${now - 1_000}
            expiresAt=never
            features=all
            """.trimIndent()
        )

        val result = verifier().verify(license, now)

        assertTrue(result.active)
        assertEquals(1, result.license?.version)
        assertEquals(0, result.license?.deviceLimit)
        assertFalse(result.license?.requiresDeviceBinding == true)
    }

    private fun verifier(): ProLicenseVerifier {
        return ProLicenseVerifier(publicKeyPem = pem("PUBLIC KEY", keyPair.public.encoded))
    }

    private fun signLicense(payload: String): String {
        val payloadPart = base64Url(payload.toByteArray(StandardCharsets.UTF_8))
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payloadPart.toByteArray(StandardCharsets.US_ASCII))
            sign()
        }
        return "${ProLicenseVerifier.PREFIX}.$payloadPart.${base64Url(signature)}"
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun pem(label: String, bytes: ByteArray): String {
        val body = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(bytes)
        return "-----BEGIN $label-----\n$body\n-----END $label-----"
    }
}
