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

class DeviceActivationVerifierTest {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    @Test
    fun acceptsValidDeviceActivation() {
        val now = 1_700_000_000_000L
        val activation = signActivation(
            """
            version=1
            licenseId=test-device-001
            activationId=activation-001
            slotIndex=2
            deviceLimit=3
            deviceFingerprint=${"a".repeat(64)}
            createdAt=${now - 1_000}
            """.trimIndent()
        )

        val result = verifier().verify(activation, now)

        assertTrue(result.active)
        assertEquals("test-device-001", result.activation?.licenseId)
        assertEquals(2, result.activation?.slotIndex)
        assertEquals(3, result.activation?.deviceLimit)
    }

    @Test
    fun rejectsActivationWithInvalidSlot() {
        val now = 1_700_000_000_000L
        val activation = signActivation(
            """
            version=1
            licenseId=test-device-001
            activationId=activation-001
            slotIndex=4
            deviceLimit=3
            deviceFingerprint=${"b".repeat(64)}
            createdAt=${now - 1_000}
            """.trimIndent()
        )

        val result = verifier().verify(activation, now)

        assertFalse(result.active)
        assertEquals("设备激活凭证槽位无效。", result.message)
    }

    private fun verifier(): DeviceActivationVerifier {
        return DeviceActivationVerifier(publicKeyPem = pem("PUBLIC KEY", keyPair.public.encoded))
    }

    private fun signActivation(payload: String): String {
        val payloadPart = base64Url(payload.toByteArray(StandardCharsets.UTF_8))
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payloadPart.toByteArray(StandardCharsets.US_ASCII))
            sign()
        }
        return "${DeviceActivationVerifier.PREFIX}.$payloadPart.${base64Url(signature)}"
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun pem(label: String, bytes: ByteArray): String {
        val body = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(bytes)
        return "-----BEGIN $label-----\n$body\n-----END $label-----"
    }
}
