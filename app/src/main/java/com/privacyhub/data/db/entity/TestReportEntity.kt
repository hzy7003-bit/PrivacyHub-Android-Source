package com.privacyhub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_reports")
data class TestReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAtMillis: Long,
    @ColumnInfo(name = "entry_point")
    val entryPoint: String,
    @ColumnInfo(name = "android_version")
    val androidVersion: String,
    @ColumnInfo(name = "sdk_int")
    val sdkInt: Int,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val rom: String,
    val success: Boolean,
    @ColumnInfo(name = "failure_reason")
    val failureReason: String?,
    val target: String?,
    val note: String
)
