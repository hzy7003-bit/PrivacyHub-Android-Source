package com.privacyhub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_items",
    indices = [Index(value = ["backup_id"], unique = true)]
)
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "backup_id")
    val backupId: String,
    val type: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "normalized_link")
    val normalizedLink: String?,
    @ColumnInfo(name = "original_link")
    val originalLink: String?,
    @ColumnInfo(name = "access_code")
    val accessCode: String? = null,
    @ColumnInfo(name = "source_app")
    val sourceApp: String,
    @ColumnInfo(name = "entry_point")
    val entryPoint: String,
    val category: String?,
    val note: String?,
    val favorite: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtMillis: Long,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAtMillis: Long?,
    val target: String
)
