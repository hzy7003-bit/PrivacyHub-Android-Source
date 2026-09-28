package com.privacyhub.domain.model

data class SavedItem(
    val id: Long,
    val createdAtMillis: Long,
    val entryPoint: EntryPoint,
    val sourceApp: String,
    val originalText: String,
    val primaryUrl: String?,
    val accessCode: String?,
    val target: LinkTarget,
    val title: String = "",
    val favorite: Boolean = false,
    val category: String? = null
)
