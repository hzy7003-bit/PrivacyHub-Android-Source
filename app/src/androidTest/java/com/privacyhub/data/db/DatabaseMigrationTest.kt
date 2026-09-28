package com.privacyhub.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PrivacyHubRoomDatabase::class.java
    )

    @Test
    fun migrateVersion1To9_preservesVaultItemAndRedactsReports() = migrateAndVerify(1)

    @Test
    fun migrateVersion2To9_preservesVaultItemAndRedactsReports() = migrateAndVerify(2)

    @Test
    fun migrateVersion3To9_preservesVaultAndAutofillAndRedactsReports() = migrateAndVerify(3)

    @Test
    fun migrateVersion4To9_preservesVaultAndAutofillAndRedactsReports() = migrateAndVerify(4)

    @Test
    fun migrateVersion5To9_preservesVaultAndAutofillAndRedactsReports() = migrateAndVerify(5)

    @Test
    fun migrateVersion6To9_preservesVaultAndAutofillAndRedactsReports() = migrateAndVerify(6)

    @Test
    fun migrateVersion7To9_preservesVaultAndAutofillAndRedactsReports() = migrateAndVerify(7)

    @Test
    fun migrateVersion8To9_preservesWebDomainAndLeavesSigningIdentityUnbound() = migrateAndVerify(8)

    private fun migrateAndVerify(startVersion: Int) {
        helper.createDatabase(TEST_DATABASE, startVersion).use { database ->
            insertVaultItem(database, startVersion)
            if (startVersion >= 3) insertAutofillCredential(database, startVersion)
            insertTestReport(database, startVersion)
            assertStartingReportState(database, startVersion)
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            *PrivacyHubDatabaseProvider.ALL_MIGRATIONS
        ).use { database ->
            database.query(
                "SELECT title, content, backup_id, access_code FROM vault_items WHERE id = 7"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("迁移测试", cursor.getString(0))
                assertEquals(SENSITIVE_VAULT_URL, cursor.getString(1))
                assertFalse(cursor.getString(2).isNullOrBlank())
                assertNull(cursor.getString(3))
            }

            if (startVersion >= 3) {
                database.query(
                    "SELECT username, password, backup_id, web_domain, " +
                        "package_signing_certificate_sha256 FROM autofill_credentials WHERE id = 9"
                ).use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals("migration-user", cursor.getString(0))
                    assertEquals("migration-password", cursor.getString(1))
                    assertFalse(cursor.getString(2).isNullOrBlank())
                    assertEquals(
                        if (startVersion >= 6) "accounts.example.com" else null,
                        cursor.getString(3)
                    )
                    assertNull(cursor.getString(4))
                }
            }

            database.query("SELECT failure_reason, note FROM test_reports WHERE id = 11").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(REDACTED_FAILURE_REASON, cursor.getString(0))
                assertEquals(REDACTED_NOTE, cursor.getString(1))
                assertFalse(cursor.getString(0).contains("private.example"))
                assertFalse(cursor.getString(0).contains("SECRET"))
            }
            database.query("SELECT failure_reason, note FROM test_reports WHERE id = 12").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertNull(cursor.getString(0))
                assertEquals(REDACTED_NOTE, cursor.getString(1))
            }

            database.execSQL("DELETE FROM vault_items WHERE id = 7")
            database.query("SELECT COUNT(*) FROM vault_items WHERE id = 7").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT failure_reason, note FROM test_reports").use { cursor ->
                while (cursor.moveToNext()) {
                    assertFalse(cursor.getString(0).orEmpty().contains("private.example"))
                    assertFalse(cursor.getString(0).orEmpty().contains("SECRET123"))
                    assertFalse(cursor.getString(1).contains("private.example"))
                    assertFalse(cursor.getString(1).contains("SECRET123"))
                }
            }
        }
    }

    private fun assertStartingReportState(database: SupportSQLiteDatabase, startVersion: Int) {
        val expectedFailureReason = if (startVersion >= 8) {
            REDACTED_FAILURE_REASON
        } else {
            SENSITIVE_FAILURE_REASON
        }
        val expectedNote = if (startVersion >= 7) REDACTED_NOTE else SENSITIVE_NOTE
        database.query("SELECT failure_reason, note FROM test_reports WHERE id = 11").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(expectedFailureReason, cursor.getString(0))
            assertEquals(expectedNote, cursor.getString(1))
        }
    }

    private fun insertTestReport(database: SupportSQLiteDatabase, startVersion: Int) {
        val initialFailureReason = if (startVersion >= 8) {
            REDACTED_FAILURE_REASON
        } else {
            SENSITIVE_FAILURE_REASON
        }
        val initialNote = if (startVersion >= 7) REDACTED_NOTE else SENSITIVE_NOTE
        database.execSQL(
            "INSERT INTO test_reports " +
                "(id, created_at, entry_point, android_version, sdk_int, brand, manufacturer, model, rom, " +
                "success, failure_reason, target, note) VALUES " +
                "(11, 100, 'NOTIFICATION', 'Android test', 35, 'Google', 'Google', 'test', 'test', " +
                "0, '$initialFailureReason', 'WEB', " +
                "'$initialNote')"
        )
        database.execSQL(
            "INSERT INTO test_reports " +
                "(id, created_at, entry_point, android_version, sdk_int, brand, manufacturer, model, rom, " +
                "success, failure_reason, target, note) VALUES " +
                "(12, 101, 'TILE', 'Android test', 35, 'Google', 'Google', 'test', 'test', " +
                "1, NULL, 'UNKNOWN', '诊断记录已脱敏')"
        )
    }

    private fun insertVaultItem(database: SupportSQLiteDatabase, version: Int) {
        val backupColumn = if (version >= 2) "backup_id, " else ""
        val backupValue = if (version >= 2) "'vault-backup-7', " else ""
        database.execSQL(
            "INSERT INTO vault_items (id, $backupColumn" +
                "type, title, content, normalized_link, original_link, source_app, entry_point, " +
                "category, note, favorite, created_at, updated_at, last_opened_at, target) VALUES (" +
                "7, $backupValue'TEXT', '迁移测试', '$SENSITIVE_VAULT_URL', NULL, NULL, 'migration-test', 'MANUAL', " +
            "'其他', NULL, 0, 100, 100, NULL, 'WEB')"
        )
    }

    private fun insertAutofillCredential(database: SupportSQLiteDatabase, version: Int) {
        val backupColumn = if (version >= 4) "backup_id, " else ""
        val backupValue = if (version >= 4) "'autofill-backup-9', " else ""
        val webDomainColumn = if (version >= 6) ", web_domain" else ""
        val webDomainValue = if (version >= 6) ", 'accounts.example.com'" else ""
        database.execSQL(
            "INSERT INTO autofill_credentials (id, $backupColumn" +
                "label, username, password, package_name$webDomainColumn, created_at, updated_at) VALUES (" +
                "9, $backupValue'迁移凭据', 'migration-user', 'migration-password', " +
                "'com.example.target'$webDomainValue, 100, 100)"
        )
    }

    companion object {
        private const val TEST_DATABASE = "privacy-hub-migration-test"
        private const val SENSITIVE_VAULT_URL = "https://private.example/?token=SECRET123"
        private const val SENSITIVE_FAILURE_REASON =
            "Intent { dat=https://private.example/?token=SECRET123 }"
        private const val REDACTED_FAILURE_REASON = "历史失败原因已脱敏"
        private const val SENSITIVE_NOTE = "敏感诊断片段-should-be-redacted"
        private const val REDACTED_NOTE = "诊断记录已脱敏"
    }
}
