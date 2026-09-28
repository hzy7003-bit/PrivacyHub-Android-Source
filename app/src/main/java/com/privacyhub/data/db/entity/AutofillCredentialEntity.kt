package com.privacyhub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "autofill_credentials",
    indices = [Index(value = ["package_name"]), Index(value = ["backup_id"], unique = true)]
)
data class AutofillCredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "backup_id")
    val backupId: String,
    val label: String,
    val username: String,
    val password: String,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    @ColumnInfo(name = "web_domain")
    val webDomain: String?,
    @ColumnInfo(name = "package_signing_certificate_sha256")
    val packageSigningCertificateSha256: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtMillis: Long
)
