package com.privacyhub.autofill

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

internal object AppSigningCertificate {
    @Suppress("DEPRECATION")
    fun sha256Set(packageManager: PackageManager, packageName: String): String? = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners ?: return null
        } else {
            packageInfo.signatures ?: return null
        }
        if (signatures.isEmpty()) return null
        val signerSet = signatures.map { signature -> sha256(signature.toByteArray()) }.sorted().joinToString("")
        sha256(signerSet.encodeToByteArray())
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02X".format(Locale.ROOT, it) }
}
