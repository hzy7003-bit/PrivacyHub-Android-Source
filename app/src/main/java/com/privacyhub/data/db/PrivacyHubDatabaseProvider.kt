package com.privacyhub.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.privacyhub.security.database.SqlCipherFactoryProvider

object PrivacyHubDatabaseProvider {
    @Volatile
    private var instance: PrivacyHubRoomDatabase? = null
    @Volatile
    private var sqlCipherLoaded: Boolean = false

    fun get(context: Context): PrivacyHubRoomDatabase {
        return instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    private fun build(context: Context): PrivacyHubRoomDatabase {
        var database: PrivacyHubRoomDatabase? = null
        return try {
            loadSqlCipher()
            database = Room.databaseBuilder(
                context,
                PrivacyHubRoomDatabase::class.java,
                "privacy_hub_vault.db"
            )
                .openHelperFactory(SqlCipherFactoryProvider.create(context))
                .addMigrations(*ALL_MIGRATIONS)
                .build()
            database.openHelper.writableDatabase
            database
        } catch (error: Throwable) {
            database?.close()
            if (error !is Exception && error !is LinkageError) throw error
            throw VaultDatabaseUnavailableException(error)
        }
    }

    private fun loadSqlCipher() {
        if (sqlCipherLoaded) return
        synchronized(this) {
            if (!sqlCipherLoaded) {
                System.loadLibrary("sqlcipher")
                sqlCipherLoaded = true
            }
        }
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE vault_items ADD COLUMN backup_id TEXT NOT NULL DEFAULT ''"
            )
            database.execSQL(
                "UPDATE vault_items SET backup_id = lower(hex(randomblob(16))) WHERE backup_id = ''"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_vault_items_backup_id ON vault_items(backup_id)"
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS autofill_credentials (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "label TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, " +
                    "package_name TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_autofill_credentials_package_name " +
                    "ON autofill_credentials(package_name)"
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE autofill_credentials ADD COLUMN backup_id TEXT NOT NULL DEFAULT ''"
            )
            database.execSQL(
                "UPDATE autofill_credentials SET backup_id = lower(hex(randomblob(16))) WHERE backup_id = ''"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_autofill_credentials_backup_id " +
                    "ON autofill_credentials(backup_id)"
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE vault_items ADD COLUMN access_code TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE autofill_credentials ADD COLUMN web_domain TEXT")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("UPDATE test_reports SET note = '诊断记录已脱敏'")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "UPDATE test_reports SET failure_reason = '历史失败原因已脱敏' " +
                    "WHERE failure_reason IS NOT NULL"
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Existing native app credentials deliberately remain unbound and fail closed until
            // the user edits/rebinds them to the installed app's current signing certificate.
            database.execSQL(
                "ALTER TABLE autofill_credentials " +
                    "ADD COLUMN package_signing_certificate_sha256 TEXT"
            )
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9
    )
}

class VaultDatabaseUnavailableException(cause: Throwable) :
    IllegalStateException("The encrypted vault database could not be opened without data loss.", cause)
