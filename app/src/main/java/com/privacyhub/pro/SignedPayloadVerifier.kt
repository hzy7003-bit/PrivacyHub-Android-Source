package com.privacyhub.pro

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal class SignedPayloadVerifier(
    private val publicKeyPem: String = ProLicensePublicKey.PEM
) {
    fun verify(value: String, prefix: String, label: String): SignedPayloadCheck {
        val normalized = value.trim().replace("\n", "").replace("\r", "")
        if (normalized.isBlank()) {
            return SignedPayloadCheck(valid = false, message = "请输入$label。")
        }

        val parts = normalized.split(".")
        if (parts.size != 3 || parts[0] != prefix) {
            return SignedPayloadCheck(valid = false, message = "$label 格式不正确。")
        }

        val payloadPart = parts[1]
        val signaturePart = parts[2]
        val verified = runCatching {
            val signatureBytes = Base64.getUrlDecoder().decode(signaturePart)
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey())
            signature.update(payloadPart.toByteArray(StandardCharsets.US_ASCII))
            signature.verify(signatureBytes)
        }.getOrDefault(false)
        if (!verified) {
            return SignedPayloadCheck(valid = false, message = "$label 签名无效。")
        }

        val payloadText = runCatching {
            String(Base64.getUrlDecoder().decode(payloadPart), StandardCharsets.UTF_8)
        }.getOrNull() ?: return SignedPayloadCheck(valid = false, message = "$label 内容无法读取。")

        return SignedPayloadCheck(
            valid = true,
            message = "$label 验签通过。",
            fields = parsePayload(payloadText)
        )
    }

    private fun publicKey() = KeyFactory.getInstance("EC").generatePublic(
        X509EncodedKeySpec(decodePem(publicKeyPem))
    )

    private fun decodePem(pem: String): ByteArray {
        val base64 = pem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .trim()
        return Base64.getDecoder().decode(base64)
    }

    private fun parsePayload(payloadText: String): Map<String, String> {
        return payloadText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()
    }

    companion object {
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}

internal data class SignedPayloadCheck(
    val valid: Boolean,
    val message: String,
    val fields: Map<String, String> = emptyMap()
)
