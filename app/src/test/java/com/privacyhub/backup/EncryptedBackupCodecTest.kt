package com.privacyhub.backup

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedBackupCodecTest {
    @Test
    fun encryptsAndDecryptsPayloadWithTheExportPassword() {
        val plain = "vault content".encodeToByteArray()

        val encrypted = EncryptedBackupCodec.encrypt(plain, "recovery-password".toCharArray())
        val decrypted = EncryptedBackupCodec.decrypt(encrypted, "recovery-password".toCharArray())

        assertTrue(encrypted.size > plain.size)
        assertEquals("vault content", decrypted.decodeToString())
    }

    @Test
    fun rejectsWrongPasswordAndTamperedBackup() {
        val encrypted = EncryptedBackupCodec.encrypt("secret".encodeToByteArray(), "correct".toCharArray())
        val tampered = encrypted.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }

        assertFailsWith<BackupAuthenticationException> {
            EncryptedBackupCodec.decrypt(encrypted, "wrong".toCharArray())
        }
        assertFailsWith<BackupAuthenticationException> {
            EncryptedBackupCodec.decrypt(tampered, "correct".toCharArray())
        }
    }

    @Test
    fun preservesVaultRecordsAndPortableSettings() {
        val payload = BackupPayload(
            createdAtMillis = 123L,
            items = listOf(
                VaultBackupItem(
                    backupId = "backup-1",
                    type = "text",
                    title = "验证码",
                    content = "123456",
                    normalizedLink = null,
                    originalLink = null,
                    accessCode = "aB9x",
                    sourceApp = "manual",
                    entryPoint = "MANUAL",
                    category = "密码",
                    note = "test",
                    favorite = true,
                    createdAtMillis = 1L,
                    updatedAtMillis = 2L,
                    lastOpenedAtMillis = null,
                    target = "UNKNOWN"
                )
            ),
            settings = PortableBackupSettings(
                customCategories = listOf("工作"),
                categoryAliases = mapOf("购物" to "工作"),
                homeTags = listOf("工作")
            ),
            autofillCredentials = listOf(
                AutofillBackupCredential(
                    backupId = "credential-1",
                    label = "工作邮箱",
                    username = "person@example.com",
                    password = "test-password",
                    packageName = null,
                    webDomain = "accounts.example.com",
                    createdAtMillis = 3L,
                    updatedAtMillis = 4L
                )
            )
        )

        assertEquals(payload, BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload)))
    }

    @Test
    fun decodesLegacyBackupWithoutAutofillCredentials() {
        val legacy = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(1)
                output.writeLong(123L)
                output.writeInt(0)
                output.writeInt(0)
                output.writeInt(0)
                output.writeInt(0)
            }
            bytes.toByteArray()
        }

        val restored = BackupPayloadCodec.decode(legacy)

        assertEquals(123L, restored.createdAtMillis)
        assertTrue(restored.items.isEmpty())
        assertTrue(restored.autofillCredentials.isEmpty())
    }

    @Test
    fun decodesVersion3AutofillCredentialWithoutWebsiteScope() {
        val legacy = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(3)
                output.writeLong(123L)
                output.writeInt(0) // vault items
                output.writeInt(0) // custom categories
                output.writeInt(0) // category aliases
                output.writeInt(0) // home tags
                output.writeInt(1) // autofill credentials
                listOf("credential-1", "legacy", "user", "password", "com.example.browser").forEach { value ->
                    val encoded = value.encodeToByteArray()
                    output.writeInt(encoded.size)
                    output.write(encoded)
                }
                output.writeLong(1L)
                output.writeLong(2L)
            }
            bytes.toByteArray()
        }

        val restored = BackupPayloadCodec.decode(legacy)

        assertEquals("com.example.browser", restored.autofillCredentials.single().packageName)
        assertNull(restored.autofillCredentials.single().webDomain)
        assertNull(restored.autofillCredentials.single().packageSigningCertificateSha256)
    }

    @Test
    fun version5RoundTripPreservesNativePackageSigningBinding() {
        val fingerprint = "0123456789ABCDEF".repeat(4)
        val payload = BackupPayload(
            createdAtMillis = 123L,
            items = emptyList(),
            settings = PortableBackupSettings(emptyList(), emptyMap(), emptyList()),
            autofillCredentials = listOf(
                AutofillBackupCredential(
                    backupId = "bound-credential",
                    label = "native",
                    username = "user",
                    password = "password",
                    packageName = "com.example.app",
                    createdAtMillis = 1L,
                    updatedAtMillis = 2L,
                    packageSigningCertificateSha256 = fingerprint
                )
            )
        )

        assertEquals(payload, BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload)))
    }

    @Test
    fun version4BackupRemainsReadableAndUnboundCredentialHasNoSigningIdentity() {
        val version4 = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(4)
                output.writeLong(123L)
                output.writeInt(0) // vault items
                output.writeInt(0) // custom categories
                output.writeInt(0) // category aliases
                output.writeInt(0) // home tags
                output.writeInt(1) // autofill credentials
                listOf("credential-4", "legacy", "user", "password").forEach { value ->
                    val encoded = value.encodeToByteArray()
                    output.writeInt(encoded.size)
                    output.write(encoded)
                }
                output.writeInt(-1) // package scope
                val domain = "accounts.example.com".encodeToByteArray()
                output.writeInt(domain.size)
                output.write(domain)
                output.writeLong(1L)
                output.writeLong(2L)
            }
            bytes.toByteArray()
        }

        val restored = BackupPayloadCodec.decode(version4)
        assertEquals("accounts.example.com", restored.autofillCredentials.single().webDomain)
        assertNull(restored.autofillCredentials.single().packageSigningCertificateSha256)
    }
}
