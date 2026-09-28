package com.privacyhub.pro

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.privacyhub.security.keystore.KeystoreTextEncryptor
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class DeviceFingerprintProvider(context: Context) {
    private val appContext = context.applicationContext
    private val encryptor = KeystoreTextEncryptor(KEYSTORE_ALIAS)
    private val identityFile = File(appContext.noBackupFilesDir, INSTALL_ID_FILE_NAME)

    fun fingerprint(): String {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val buildIdentity = listOf(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.DEVICE)
            .joinToString("|") { it.orEmpty().trim().lowercase() }
        return DeviceFingerprintHasher.sha256Hex(
            listOf(FINGERPRINT_DOMAIN, androidId, buildIdentity, installationId()).joinToString("\n")
        )
    }

    private fun installationId(): String {
        if (identityFile.exists()) {
            try {
                val parts = identityFile.readText(Charsets.UTF_8).split("\n", limit = 2)
                require(parts.size == 2 && parts.all(String::isNotBlank))
                return encryptor.decrypt(parts[1], parts[0]).takeIf(String::isNotBlank)
                    ?: throw DeviceIdentityUnavailableException()
            } catch (error: DeviceIdentityUnavailableException) {
                throw error
            } catch (error: Exception) {
                throw DeviceIdentityUnavailableException(error)
            }
        }

        val installId = UUID.randomUUID().toString()
        val encrypted = encryptor.encrypt(installId)
        identityFile.parentFile?.mkdirs()
        identityFile.writeText("${encrypted.iv}\n${encrypted.cipherText}", Charsets.UTF_8)
        return installId
    }

    companion object {
        private const val KEYSTORE_ALIAS = "privacy_hub_device_identity_key"
        private const val INSTALL_ID_FILE_NAME = "pro_install_identity_v1"
        private const val FINGERPRINT_DOMAIN = "privacyhub-device-fingerprint-v1"
    }
}

class DeviceIdentityUnavailableException(cause: Throwable? = null) :
    IllegalStateException("本机设备绑定数据当前无法读取。", cause)

internal object DeviceFingerprintHasher {
    fun sha256Hex(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
