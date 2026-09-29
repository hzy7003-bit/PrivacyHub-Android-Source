package com.privacyhub.data.repository

import android.content.Context
import com.privacyhub.autofill.AppSigningCertificate
import com.privacyhub.autofill.AutofillScopePolicy
import com.privacyhub.data.db.PrivacyHubDatabaseProvider
import com.privacyhub.data.db.DatabaseAccessCoordinator
import com.privacyhub.data.db.entity.AutofillCredentialEntity
import java.util.UUID

class AutofillCredentialRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PrivacyHubDatabaseProvider.get(appContext).autofillCredentialDao()
    }

    fun all(): List<AutofillCredentialEntity> = DatabaseAccessCoordinator.withDatabaseAccess { dao.all() }

    fun getById(id: Long): AutofillCredentialEntity? = DatabaseAccessCoordinator.withDatabaseAccess { dao.getById(id) }

    fun save(label: String, username: String, password: String, packageName: String?, webDomain: String?): Long {
        val normalizedDomain = normalizeDomain(webDomain)
        require(packageName.isNullOrBlank() || normalizedDomain == null) {
            "A credential cannot combine app and website scopes."
        }
        val normalizedPackageName = packageName?.trim()?.ifBlank { null }
        require((normalizedPackageName != null) xor (normalizedDomain != null)) {
            "A credential must have exactly one app or website scope."
        }
        val signingCertificate = normalizedPackageName?.let {
            AppSigningCertificate.sha256Set(appContext.packageManager, it)
        }
        require(normalizedPackageName == null || signingCertificate != null) {
            "The selected app must be installed and its signing identity must be available."
        }
        val now = System.currentTimeMillis()
        return DatabaseAccessCoordinator.withDatabaseAccess {
            dao.insert(
                AutofillCredentialEntity(
                    backupId = UUID.randomUUID().toString(),
                    label = label.trim(),
                    username = username,
                    password = password,
                    packageName = normalizedPackageName,
                    webDomain = normalizedDomain,
                    packageSigningCertificateSha256 = signingCertificate,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }
    }

    fun update(
        existing: AutofillCredentialEntity,
        label: String,
        username: String,
        password: String,
        packageName: String?,
        webDomain: String?
    ) {
        val normalizedDomain = normalizeDomain(webDomain)
        require(packageName.isNullOrBlank() || normalizedDomain == null) {
            "A credential cannot combine app and website scopes."
        }
        val normalizedPackageName = packageName?.trim()?.ifBlank { null }
        require((normalizedPackageName != null) xor (normalizedDomain != null)) {
            "A credential must have exactly one app or website scope."
        }
        val signingCertificate = normalizedPackageName?.let {
            AppSigningCertificate.sha256Set(appContext.packageManager, it)
        }
        require(normalizedPackageName == null || signingCertificate != null) {
            "The selected app must be installed and its signing identity must be available."
        }
        DatabaseAccessCoordinator.withDatabaseAccess {
            dao.update(
                existing.copy(
                    label = label.trim(),
                    username = username,
                    password = password,
                    packageName = normalizedPackageName,
                    webDomain = normalizedDomain,
                    packageSigningCertificateSha256 = signingCertificate,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun delete(id: Long) = DatabaseAccessCoordinator.withDatabaseAccess { dao.deleteById(id) }

    private fun normalizeDomain(value: String?): String? {
        val trimmed = value?.trim()?.ifBlank { null } ?: return null
        val normalized = AutofillScopePolicy.normalizeDomain(trimmed)
            ?: throw IllegalArgumentException("Invalid website domain.")
        require(AutofillScopePolicy.hasRegistrableDomain(normalized)) {
            "A public suffix cannot be used as a website credential scope."
        }
        return normalized
    }

}
