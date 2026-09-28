package com.privacyhub.domain.model

data class TestReport(
    val id: Long,
    val createdAtMillis: Long,
    val entryPoint: EntryPoint,
    val androidVersion: String,
    val sdkInt: Int,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val rom: String,
    val success: Boolean,
    val failureReason: String?,
    val target: LinkTarget?,
    val note: String
)
