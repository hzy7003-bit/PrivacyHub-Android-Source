package com.privacyhub.backup

import android.content.Context
import android.util.Log
import com.privacyhub.autofill.AppSigningCertificate
import com.privacyhub.autofill.AutofillScopePolicy
import com.privacyhub.data.db.PrivacyHubDatabaseProvider
import com.privacyhub.data.db.PrivacyHubRoomDatabase
import com.privacyhub.data.db.AutofillCredentialDao
import com.privacyhub.data.db.DatabaseAccessCoordinator
import com.privacyhub.data.db.RestoreRecoveryPendingException
import com.privacyhub.data.db.VaultItemDao
import com.privacyhub.data.db.entity.AutofillCredentialEntity
import com.privacyhub.data.db.entity.VaultItemEntity
import com.privacyhub.data.settings.AppSettingsStore

class VaultBackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PrivacyHubDatabaseProvider.get(appContext)
    }
    private val vaultDao by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { database.vaultItemDao() }
    private val autofillCredentialDao by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { database.autofillCredentialDao() }
    private val settingsStore = AppSettingsStore(appContext)
    private val restoreProtocol by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RestoreProtocol(
            database = RoomRestoreDatabaseGateway(
                database = database,
                vaultDao = vaultDao,
                autofillCredentialDao = autofillCredentialDao,
                itemMapper = ::toEntity,
                credentialMapper = ::toCredentialEntity
            ),
            settings = AppSettingsRestoreGateway(settingsStore)
        )
    }

    fun createPayload(nowMillis: Long = System.currentTimeMillis()): BackupPayload {
        // Any legacy dangling references are repaired under the exclusive gate before the
        // immutable backup snapshot is read under the shared gate.
        DatabaseAccessCoordinator.withDatabaseWriteAccess {
            settingsStore.allKnownCategories(vaultDao.categories())
        }
        return DatabaseAccessCoordinator.withDatabaseAccess {
            val items = vaultDao.all().map(::toBackupItem)
            val credentials = autofillCredentialDao.all().map(::toBackupCredential)
            BackupPayload(
                createdAtMillis = nowMillis,
                items = items,
                settings = settingsStore.portableBackupSettings(vaultDao.categories()),
                autofillCredentials = credentials
            )
        }
    }

    fun restore(payload: BackupPayload, mode: RestoreMode): RestoreResult =
        DatabaseAccessCoordinator.withRestoreAccess {
            val protocol = try {
                restoreProtocol
            } catch (error: Exception) {
                DatabaseAccessCoordinator.markRestoreRecoveryPending(error)
                Log.e(SECURITY_DIAGNOSTIC_TAG, "restore_recovery_incomplete")
                throw error
            }
            if (DatabaseAccessCoordinator.isRestoreRecoveryPending()) {
                try {
                    protocol.recoverInterruptedRestore()
                    DatabaseAccessCoordinator.markRestoreRecoveryComplete()
                } catch (error: Exception) {
                    DatabaseAccessCoordinator.markRestoreRecoveryPending(error)
                    throw error
                }
            }
            try {
                protocol.restore(payload, mode)
            } catch (error: RestoreConsistencyException) {
                if (error.recoveryIncomplete) {
                    DatabaseAccessCoordinator.markRestoreRecoveryPending(error)
                    Log.e(SECURITY_DIAGNOSTIC_TAG, "restore_recovery_incomplete")
                }
                throw error
            }
        }

    internal fun <T> runCoordinatedCategoryMutation(
        prepareSettings: () -> Unit = {},
        applyRoom: () -> T,
        applySettings: () -> Boolean
    ): T = DatabaseAccessCoordinator.withDatabaseWriteAccess {
        val protocol = try {
            restoreProtocol
        } catch (error: Exception) {
            throw markCategoryRecoveryIncomplete(error)
        }
        prepareSettings()
        try {
            protocol.runCoordinatedMutation(applyRoom, applySettings)
        } catch (error: RestoreConsistencyException) {
            if (error.recoveryIncomplete) {
                throw markCategoryRecoveryIncomplete(error)
            }
            throw error
        }
    }

    fun recoverPendingRestore() {
        DatabaseAccessCoordinator.markRestoreRecoveryPending(RestoreRecoveryPendingException())
        try {
            DatabaseAccessCoordinator.withRestoreAccess {
                restoreProtocol.recoverInterruptedRestore()
                DatabaseAccessCoordinator.markRestoreRecoveryComplete()
            }
        } catch (error: Exception) {
            DatabaseAccessCoordinator.markRestoreRecoveryPending(error)
            Log.e(SECURITY_DIAGNOSTIC_TAG, "restore_recovery_incomplete")
            throw error
        }
    }

    private fun toBackupItem(entity: VaultItemEntity): VaultBackupItem {
        return VaultBackupItem(
            backupId = entity.backupId,
            type = entity.type,
            title = entity.title,
            content = entity.content,
            normalizedLink = entity.normalizedLink,
            originalLink = entity.originalLink,
            accessCode = entity.accessCode,
            sourceApp = entity.sourceApp,
            entryPoint = entity.entryPoint,
            category = entity.category,
            note = entity.note,
            favorite = entity.favorite,
            createdAtMillis = entity.createdAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
            lastOpenedAtMillis = entity.lastOpenedAtMillis,
            target = entity.target
        )
    }

    private fun toEntity(item: VaultBackupItem): VaultItemEntity {
        return VaultItemEntity(
            backupId = item.backupId,
            type = item.type,
            title = item.title,
            content = item.content,
            normalizedLink = item.normalizedLink,
            originalLink = item.originalLink,
            accessCode = item.accessCode,
            sourceApp = item.sourceApp,
            entryPoint = item.entryPoint,
            category = item.category,
            note = item.note,
            favorite = item.favorite,
            createdAtMillis = item.createdAtMillis,
            updatedAtMillis = item.updatedAtMillis,
            lastOpenedAtMillis = item.lastOpenedAtMillis,
            target = item.target
        )
    }

    private fun toBackupCredential(entity: AutofillCredentialEntity): AutofillBackupCredential {
        return AutofillBackupCredential(
            backupId = entity.backupId,
            label = entity.label,
            username = entity.username,
            password = entity.password,
            packageName = entity.packageName,
            webDomain = entity.webDomain,
            createdAtMillis = entity.createdAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
            packageSigningCertificateSha256 = entity.packageSigningCertificateSha256
        )
    }

    private fun toCredentialEntity(credential: AutofillBackupCredential): AutofillCredentialEntity {
        val normalizedPackage = credential.packageName?.trim()?.ifBlank { null }
        val importedFingerprint = credential.packageSigningCertificateSha256
            ?.filterNot { it == ':' || it.isWhitespace() }
            ?.uppercase()
        val currentFingerprint = normalizedPackage?.let {
            AppSigningCertificate.sha256Set(appContext.packageManager, it)
        }
        val trustedFingerprint = importedFingerprint
            ?.takeIf { it.matches(Regex("[0-9A-F]{64}")) && it == currentFingerprint }
        return AutofillCredentialEntity(
            backupId = credential.backupId,
            label = credential.label,
            username = credential.username,
            password = credential.password,
            packageName = normalizedPackage,
            webDomain = credential.webDomain?.let(AutofillScopePolicy::normalizeDomain),
            packageSigningCertificateSha256 = trustedFingerprint,
            createdAtMillis = credential.createdAtMillis,
            updatedAtMillis = credential.updatedAtMillis
        )
    }

    private fun markCategoryRecoveryIncomplete(error: Throwable): RestoreConsistencyException {
        val safeFailure = if (error is RestoreConsistencyException && error.recoveryIncomplete) {
            error
        } else {
            RestoreConsistencyException(
                "分类数据恢复尚未完成；数据库访问已暂停，请重新打开应用重试。",
                error,
                recoveryIncomplete = true
            )
        }
        DatabaseAccessCoordinator.markRestoreRecoveryPending(safeFailure)
        // State-only diagnostic: never include category names, item text, or exception data.
        Log.e(SECURITY_DIAGNOSTIC_TAG, "cross_store_category_recovery_incomplete")
        return safeFailure
    }

    private companion object {
        const val SECURITY_DIAGNOSTIC_TAG = "PrivacyHub.Security"
    }
}

enum class RestoreMode {
    Merge,
    Replace
}

data class RestoreResult(
    val insertedItems: Int,
    val skippedItems: Int,
    val insertedCredentials: Int,
    val skippedCredentials: Int
)

private class AppSettingsRestoreGateway(
    private val settings: AppSettingsStore
) : RestoreSettingsGateway {
    override fun stageRollback(operationId: String): Boolean =
        settings.stagePortableRestoreRollback(operationId)

    override fun apply(settings: PortableBackupSettings, mode: RestoreMode): Boolean {
        return when (mode) {
            RestoreMode.Merge -> this.settings.mergePortableBackupSettings(settings)
            RestoreMode.Replace -> this.settings.replacePortableBackupSettings(settings)
        }
    }

    override fun rollback(operationId: String): Boolean =
        settings.rollbackPortableRestore(operationId)

    override fun discardRollback(operationId: String?): Boolean =
        settings.discardPortableRestoreRollback(operationId)
}

private class RoomRestoreDatabaseGateway(
    private val database: PrivacyHubRoomDatabase,
    private val vaultDao: VaultItemDao,
    private val autofillCredentialDao: AutofillCredentialDao,
    private val itemMapper: (VaultBackupItem) -> VaultItemEntity,
    private val credentialMapper: (AutofillBackupCredential) -> AutofillCredentialEntity
) : RestoreDatabaseGateway {
    init {
        database.openHelper.writableDatabase.execSQL(CREATE_JOURNAL_TABLE)
    }

    override fun currentJournal(): RestoreJournal? {
        return database.openHelper.writableDatabase.query(
            "SELECT operation_id, restore_mode, state, started_at FROM $JOURNAL_TABLE WHERE slot = 1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val operation = RestoreJournalOperation.entries.firstOrNull { it.name == cursor.getString(1) }
                ?: throw RestoreConsistencyException("恢复日志包含无效操作。")
            val state = RestoreJournalState.entries.firstOrNull { it.name == cursor.getString(2) }
                ?: throw RestoreConsistencyException("恢复日志包含无效状态。")
            val startedAt = cursor.getLong(3)
            if (startedAt < 0L) throw RestoreConsistencyException("恢复日志时间无效。")
            RestoreJournal(
                operationId = cursor.getString(0),
                operation = operation,
                state = state,
                startedAtMillis = startedAt
            )
        }
    }

    override fun prepareJournal(journal: RestoreJournal) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO $JOURNAL_TABLE (slot, operation_id, restore_mode, state, started_at) " +
                "VALUES (1, ?, ?, ?, ?)",
            arrayOf(
                journal.operationId,
                journal.operation.name,
                journal.state.name,
                journal.startedAtMillis
            )
        )
    }

    override fun restoreInTransaction(
        payload: BackupPayload,
        mode: RestoreMode,
        operationId: String,
        applySettings: () -> Unit
    ): RestoreCounts {
        val sourceItems = payload.items.map(itemMapper)
        val sourceCredentials = payload.autofillCredentials.map(credentialMapper)
        return database.runInTransaction<RestoreCounts> {
            if (mode == RestoreMode.Replace) {
                vaultDao.deleteAll()
                autofillCredentialDao.deleteAll()
            }
            val counts = RestoreCounts(
                insertedItems = vaultDao.insertAllIgnore(sourceItems).count { it != -1L },
                insertedCredentials = autofillCredentialDao.insertAllIgnore(sourceCredentials).count { it != -1L }
            )
            applySettings()
            markDatabaseCommitted(operationId)
            counts
        }
    }

    override fun <T> runCoordinatedMutationInTransaction(
        operationId: String,
        applyRoom: () -> T,
        applySettings: () -> Unit
    ): T = database.runInTransaction<T> {
        val result = applyRoom()
        applySettings()
        markDatabaseCommitted(operationId)
        result
    }

    override fun clearJournal(operationId: String) {
        val deleted = database.openHelper.writableDatabase.compileStatement(
            "DELETE FROM $JOURNAL_TABLE WHERE slot = 1 AND operation_id = ?"
        ).run {
            bindString(1, operationId)
            executeUpdateDelete()
        }
        if (deleted != 1) throw RestoreConsistencyException("无法清理恢复日志。")
    }

    private fun markDatabaseCommitted(operationId: String) {
        val updated = database.openHelper.writableDatabase.compileStatement(
            "UPDATE $JOURNAL_TABLE SET state = ? " +
                "WHERE slot = 1 AND operation_id = ? AND state = ?"
        ).run {
            bindString(1, RestoreJournalState.DatabaseCommitted.name)
            bindString(2, operationId)
            bindString(3, RestoreJournalState.Prepared.name)
            executeUpdateDelete()
        }
        if (updated != 1) throw RestoreConsistencyException("无法提交数据一致性日志。")
    }

    private companion object {
        const val JOURNAL_TABLE = "privacy_hub_restore_journal"
        const val CREATE_JOURNAL_TABLE =
            "CREATE TABLE IF NOT EXISTS $JOURNAL_TABLE (" +
                "slot INTEGER NOT NULL PRIMARY KEY CHECK (slot = 1), " +
                "operation_id TEXT NOT NULL UNIQUE, " +
                "restore_mode TEXT NOT NULL, " +
                "state TEXT NOT NULL, " +
                "started_at INTEGER NOT NULL)"
    }
}
