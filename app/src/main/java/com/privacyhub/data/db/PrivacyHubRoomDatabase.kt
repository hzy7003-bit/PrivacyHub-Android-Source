package com.privacyhub.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.privacyhub.data.db.entity.TestReportEntity
import com.privacyhub.data.db.entity.VaultItemEntity
import com.privacyhub.data.db.entity.AutofillCredentialEntity

@Database(
    entities = [
        VaultItemEntity::class,
        TestReportEntity::class,
        AutofillCredentialEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class PrivacyHubRoomDatabase : RoomDatabase() {
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun testReportDao(): TestReportDao
    abstract fun autofillCredentialDao(): AutofillCredentialDao
}
