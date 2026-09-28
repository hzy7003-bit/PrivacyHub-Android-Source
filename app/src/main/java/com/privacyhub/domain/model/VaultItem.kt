package com.privacyhub.domain.model

data class VaultItem(
    val id: Long,
    val type: String,
    val title: String,
    val content: String,
    val normalizedLink: String?,
    val originalLink: String?,
    val accessCode: String?,
    val sourceApp: String,
    val entryPoint: EntryPoint,
    val category: String?,
    val note: String?,
    val favorite: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long?,
    val target: LinkTarget
)
