package com.privacyhub.backup

data class BackupPayload(
    val createdAtMillis: Long,
    val items: List<VaultBackupItem>,
    val settings: PortableBackupSettings,
    val autofillCredentials: List<AutofillBackupCredential> = emptyList()
)

data class VaultBackupItem(
    val backupId: String,
    val type: String,
    val title: String,
    val content: String,
    val normalizedLink: String?,
    val originalLink: String?,
    val accessCode: String? = null,
    val sourceApp: String,
    val entryPoint: String,
    val category: String?,
    val note: String?,
    val favorite: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long?,
    val target: String
)

data class PortableBackupSettings(
    val customCategories: List<String>,
    val categoryAliases: Map<String, String>,
    val homeTags: List<String>
)

data class AutofillBackupCredential(
    val backupId: String,
    val label: String,
    val username: String,
    val password: String,
    val packageName: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val webDomain: String? = null,
    val packageSigningCertificateSha256: String? = null
)

open class BackupFormatException(message: String) : IllegalArgumentException(message)

class BackupAuthenticationException(message: String) : BackupFormatException(message)
