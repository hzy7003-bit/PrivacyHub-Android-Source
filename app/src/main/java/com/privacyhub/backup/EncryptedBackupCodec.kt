package com.privacyhub.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedBackupCodec {
    private val magic = byteArrayOf('P'.code.toByte(), 'H'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val FORMAT_VERSION = 1
    private const val PBKDF2_ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 4 + 4 + SALT_BYTES + IV_BYTES
    private const val LENGTH_BYTES = 4
    private const val MAX_PLAINTEXT_BYTES = 32 * 1024 * 1024
    private const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + 1024

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        requirePassword(password)
        if (plain.size > MAX_PLAINTEXT_BYTES) {
            throw BackupFormatException("备份内容过大。")
        }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val header = buildHeader(PBKDF2_ITERATIONS, salt, iv)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(header)
            val encrypted = cipher.doFinal(plain)
            return ByteBuffer.allocate(header.size + LENGTH_BYTES + encrypted.size)
                .order(ByteOrder.BIG_ENDIAN)
                .put(header)
                .putInt(encrypted.size)
                .put(encrypted)
                .array()
        } catch (error: GeneralSecurityException) {
            throw BackupFormatException("无法加密备份。")
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        requirePassword(password)
        if (encrypted.size < HEADER_BYTES + LENGTH_BYTES + 16) {
            throw BackupFormatException("备份文件不完整。")
        }
        try {
            val header = encrypted.copyOfRange(0, HEADER_BYTES)
            val headerReader = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            val actualMagic = ByteArray(magic.size).also(headerReader::get)
            if (!actualMagic.contentEquals(magic)) {
                throw BackupFormatException("这不是隐私中转站备份文件。")
            }
            val version = headerReader.int
            if (version != FORMAT_VERSION) {
                throw BackupFormatException("不支持的备份文件版本。")
            }
            val iterations = headerReader.int
            if (iterations !in 100_000..1_000_000) {
                throw BackupFormatException("备份文件的加密参数无效。")
            }
            val salt = ByteArray(SALT_BYTES).also(headerReader::get)
            val iv = ByteArray(IV_BYTES).also(headerReader::get)
            val reader = ByteBuffer.wrap(encrypted).order(ByteOrder.BIG_ENDIAN).apply { position(HEADER_BYTES) }
            val cipherLength = reader.int
            if (cipherLength !in 16..MAX_CIPHERTEXT_BYTES || reader.remaining() != cipherLength) {
                throw BackupFormatException("备份文件长度无效。")
            }
            val cipherText = ByteArray(cipherLength).also(reader::get)
            val key = deriveKey(password, salt, iterations)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(header)
                return cipher.doFinal(cipherText)
            } finally {
                key.fill(0)
            }
        } catch (error: BackupFormatException) {
            throw error
        } catch (_: AEADBadTagException) {
            throw BackupAuthenticationException("备份口令错误或文件已损坏。")
        } catch (_: GeneralSecurityException) {
            throw BackupFormatException("无法验证备份文件。")
        } catch (_: IllegalArgumentException) {
            throw BackupFormatException("备份文件格式无效。")
        }
    }

    private fun buildHeader(iterations: Int, salt: ByteArray, iv: ByteArray): ByteArray {
        return ByteBuffer.allocate(HEADER_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(magic)
            .putInt(FORMAT_VERSION)
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .array()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (_: GeneralSecurityException) {
            throw BackupFormatException("当前设备不支持备份加密。")
        } finally {
            spec.clearPassword()
        }
    }

    private fun requirePassword(password: CharArray) {
        if (password.isEmpty()) {
            throw BackupFormatException("备份口令不能为空。")
        }
    }
}
