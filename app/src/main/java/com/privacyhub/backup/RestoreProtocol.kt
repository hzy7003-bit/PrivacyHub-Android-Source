package com.privacyhub.backup

import com.privacyhub.autofill.AutofillScopePolicy
import java.util.UUID

internal enum class RestoreJournalState {
    Prepared,
    DatabaseCommitted
}

/** Values persisted in the existing restore journal; this does not change the backup payload. */
internal enum class RestoreJournalOperation {
    Merge,
    Replace,
    CategoryMutation
}

internal data class RestoreJournal(
    val operationId: String,
    val operation: RestoreJournalOperation,
    val state: RestoreJournalState,
    val startedAtMillis: Long
)

internal data class RestoreCounts(
    val insertedItems: Int,
    val insertedCredentials: Int
)

internal interface RestoreDatabaseGateway {
    fun currentJournal(): RestoreJournal?
    fun prepareJournal(journal: RestoreJournal)
    fun restoreInTransaction(
        payload: BackupPayload,
        mode: RestoreMode,
        operationId: String,
        applySettings: () -> Unit
    ): RestoreCounts
    fun <T> runCoordinatedMutationInTransaction(
        operationId: String,
        applyRoom: () -> T,
        applySettings: () -> Unit
    ): T
    fun clearJournal(operationId: String)
}

internal interface RestoreSettingsGateway {
    fun stageRollback(operationId: String): Boolean
    fun apply(settings: PortableBackupSettings, mode: RestoreMode): Boolean
    fun rollback(operationId: String): Boolean
    fun discardRollback(operationId: String?): Boolean
}

internal class RestoreProtocol(
    private val database: RestoreDatabaseGateway,
    private val settings: RestoreSettingsGateway,
    private val operationIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis
) {
    init {
        synchronized(PROCESS_LOCK) {
            recoverInterruptedRestoreLocked()
        }
    }

    fun restore(payload: BackupPayload, mode: RestoreMode): RestoreResult = synchronized(PROCESS_LOCK) {
        restoreLocked(payload, mode)
    }

    private fun restoreLocked(payload: BackupPayload, mode: RestoreMode): RestoreResult {
        BackupPayloadValidator.validate(payload)
        val operationId = operationIdFactory()
        try {
            requireSettingsWrite(
                settings.stageRollback(operationId),
                "无法保护恢复前的设置。"
            )
            database.prepareJournal(
                RestoreJournal(
                    operationId = operationId,
                    operation = mode.toJournalOperation(),
                    state = RestoreJournalState.Prepared,
                    startedAtMillis = clock()
                )
            )
        } catch (error: Exception) {
            throw recoverAfterFailure(error, "恢复失败，自动回滚尚未完成；请重新打开备份页后重试。")
        }

        val counts = try {
            database.restoreInTransaction(payload, mode, operationId) {
                requireSettingsWrite(
                    settings.apply(payload.settings, mode),
                    "恢复设置未能持久化。"
                )
            }
        } catch (error: Exception) {
            throw recoverAfterFailure(error, "恢复失败，自动回滚尚未完成；请重新打开备份页后重试。")
        }

        val result = RestoreResult(
            insertedItems = counts.insertedItems,
            skippedItems = payload.items.size - counts.insertedItems,
            insertedCredentials = counts.insertedCredentials,
            skippedCredentials = payload.autofillCredentials.size - counts.insertedCredentials
        )
        try {
            finalizeCommittedRestore(operationId)
        } catch (error: Exception) {
            val recoveryError = runCatching { recoverInterruptedRestoreLocked() }.exceptionOrNull()
            if (recoveryError != null) {
                error.addSuppressed(recoveryError)
                throw RestoreConsistencyException(
                    "恢复失败，自动回滚尚未完成；请重新打开备份页后重试。",
                    error,
                    recoveryIncomplete = true
                )
            }
            // Recovery observed the committed phase and completed finalization; restore succeeded.
        }
        return result
    }

    /**
     * Coordinates category metadata in encrypted settings with Room rows using the same staged
     * rollback and durable journal as restore. Room rolls back the data mutation and journal phase
     * together; the journal then decides whether startup recovery restores or keeps settings.
     */
    fun <T> runCoordinatedMutation(
        applyRoom: () -> T,
        applySettings: () -> Boolean
    ): T = synchronized(PROCESS_LOCK) {
        val existingJournal = try {
            database.currentJournal()
        } catch (error: Exception) {
            throw RestoreConsistencyException(
                "无法确认上次数据一致性操作状态；数据库访问已暂停，请重新打开应用重试。",
                error,
                recoveryIncomplete = true
            )
        }
        if (existingJournal != null) {
            try {
                recoverInterruptedRestoreLocked()
            } catch (error: Exception) {
                throw RestoreConsistencyException(
                    "上次数据一致性恢复尚未完成；数据库访问已暂停，请重新打开应用重试。",
                    error,
                    recoveryIncomplete = true
                )
            }
        }
        val operationId = operationIdFactory()
        try {
            requireSettingsWrite(settings.stageRollback(operationId), "无法保护操作前的分类设置。")
            database.prepareJournal(
                RestoreJournal(
                    operationId = operationId,
                    operation = RestoreJournalOperation.CategoryMutation,
                    state = RestoreJournalState.Prepared,
                    startedAtMillis = clock()
                )
            )
        } catch (error: Exception) {
            throw recoverAfterFailure(
                error,
                completeMessage = "分类操作未完成，已恢复原状态；请重试。"
            )
        }

        val result = try {
            database.runCoordinatedMutationInTransaction(operationId, applyRoom) {
                requireSettingsWrite(applySettings(), "分类设置未能保存，操作已回滚。")
            }
        } catch (error: Exception) {
            throw recoverAfterFailure(
                error,
                completeMessage = "分类操作未完成，已恢复原状态；请重试。"
            )
        }

        try {
            finalizeCommittedRestore(operationId)
        } catch (error: Exception) {
            val recoveryError = runCatching { recoverInterruptedRestoreLocked() }.exceptionOrNull()
            if (recoveryError != null) {
                error.addSuppressed(recoveryError)
                throw RestoreConsistencyException(
                    "分类操作已提交，但安全恢复尚未完成；数据库访问已暂停，请重新打开应用重试。",
                    error,
                    recoveryIncomplete = true
                )
            }
            // A committed operation was successfully finalized by recovery. Report it as success.
        }
        result
    }

    fun recoverInterruptedRestore() = synchronized(PROCESS_LOCK) {
        recoverInterruptedRestoreLocked()
    }

    private fun recoverInterruptedRestoreLocked() {
        val journal = database.currentJournal()
        if (journal == null) {
            requireSettingsWrite(
                settings.discardRollback(null),
                "无法清理未关联的设置恢复暂存。"
            )
            return
        }
        when (journal.state) {
            RestoreJournalState.Prepared -> {
                requireSettingsWrite(
                    settings.rollback(journal.operationId),
                    "无法恢复中断前的设置。"
                )
                database.clearJournal(journal.operationId)
                requireSettingsWrite(
                    settings.discardRollback(journal.operationId),
                    "无法清理设置恢复暂存。"
                )
            }
            RestoreJournalState.DatabaseCommitted -> finalizeCommittedRestore(journal.operationId)
        }
    }

    private fun finalizeCommittedRestore(operationId: String) {
        requireSettingsWrite(
            settings.discardRollback(operationId),
            "无法清理设置恢复暂存。"
        )
        database.clearJournal(operationId)
    }

    private fun recoverAfterFailure(
        error: Exception,
        incompleteMessage: String = "分类操作失败，自动恢复尚未完成；数据库访问已暂停，请重新打开应用重试。",
        completeMessage: String? = null
    ): Exception {
        val recoveryError = runCatching { recoverInterruptedRestoreLocked() }.exceptionOrNull()
        if (recoveryError == null) {
            return completeMessage?.let { RestoreConsistencyException(it, error) } ?: error
        }
        error.addSuppressed(recoveryError)
        return RestoreConsistencyException(
            incompleteMessage,
            error,
            recoveryIncomplete = true
        )
    }

    private fun requireSettingsWrite(success: Boolean, message: String) {
        if (!success) throw RestoreConsistencyException(message)
    }

    private fun RestoreMode.toJournalOperation(): RestoreJournalOperation = when (this) {
        RestoreMode.Merge -> RestoreJournalOperation.Merge
        RestoreMode.Replace -> RestoreJournalOperation.Replace
    }

    private companion object {
        /** Serializes restores across repository instances in this process. */
        val PROCESS_LOCK = Any()
    }
}

internal object BackupPayloadValidator {
    private const val MAX_RECORD_COUNT = 50_000
    private const val MAX_SETTINGS_COUNT = 1_000
    private const val MAX_CATEGORY_NAME_LENGTH = 16
    private const val MAX_HOME_TAGS = 5

    fun validate(payload: BackupPayload) {
        if (payload.createdAtMillis < 0L) invalid("备份时间无效。")
        if (payload.items.size > MAX_RECORD_COUNT || payload.autofillCredentials.size > MAX_RECORD_COUNT) {
            invalid("备份记录数量无效。")
        }
        validateUniqueIds(payload.items.map(VaultBackupItem::backupId), "内容")
        validateUniqueIds(payload.autofillCredentials.map(AutofillBackupCredential::backupId), "登录信息")
        payload.items.forEach(::validateItem)
        payload.autofillCredentials.forEach(::validateCredential)
        validateSettings(payload.settings)
    }

    private fun validateItem(item: VaultBackupItem) {
        listOf(item.backupId, item.type, item.sourceApp, item.entryPoint, item.target).forEach {
            if (it.isBlank()) invalid("备份内容包含缺失的必填字段。")
        }
        validateTimestamps(item.createdAtMillis, item.updatedAtMillis, item.lastOpenedAtMillis)
        listOfNotNull(
            item.backupId,
            item.type,
            item.title,
            item.content,
            item.normalizedLink,
            item.originalLink,
            item.accessCode,
            item.sourceApp,
            item.entryPoint,
            item.category,
            item.note,
            item.target
        ).forEach(::validateText)
    }

    private fun validateCredential(credential: AutofillBackupCredential) {
        if (credential.backupId.isBlank() || credential.label.isBlank()) {
            invalid("备份登录信息包含缺失的必填字段。")
        }
        if (credential.packageName != null && credential.packageName.isBlank()) {
            invalid("备份登录信息包含无效的 App 作用域。")
        }
        if (credential.webDomain != null && credential.webDomain.isBlank()) {
            invalid("备份登录信息包含无效的网站作用域。")
        }
        if (credential.packageName != null && credential.webDomain != null) {
            invalid("备份登录信息同时包含 App 和网站作用域。")
        }
        if (credential.webDomain != null && AutofillScopePolicy.normalizeDomain(credential.webDomain) == null) {
            invalid("备份登录信息包含无效的网站域名。")
        }
        validateTimestamps(credential.createdAtMillis, credential.updatedAtMillis, null)
        listOfNotNull(
            credential.backupId,
            credential.label,
            credential.username,
            credential.password,
            credential.packageName,
            credential.webDomain
        ).forEach(::validateText)
    }

    private fun validateSettings(settings: PortableBackupSettings) {
        if (settings.customCategories.size > MAX_SETTINGS_COUNT ||
            settings.categoryAliases.size > MAX_SETTINGS_COUNT ||
            settings.homeTags.size > MAX_HOME_TAGS
        ) {
            invalid("备份分类设置数量无效。")
        }
        val categories = settings.customCategories.map(::validateCategory)
        val homeTags = settings.homeTags.map(::validateCategory)
        if (categories.distinct().size != categories.size || homeTags.distinct().size != homeTags.size) {
            invalid("备份分类设置包含重复项。")
        }
        settings.categoryAliases.forEach { (source, destination) ->
            validateCategory(source)
            validateCategory(destination)
        }
    }

    private fun validateCategory(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_CATEGORY_NAME_LENGTH) {
            invalid("备份分类名称无效。")
        }
        validateText(value)
        return trimmed
    }

    private fun validateUniqueIds(ids: List<String>, label: String) {
        if (ids.any(String::isBlank)) invalid("备份${label}缺少恢复标识。")
        if (ids.distinct().size != ids.size) invalid("备份${label}包含重复的恢复标识。")
    }

    private fun validateTimestamps(createdAt: Long, updatedAt: Long, lastOpenedAt: Long?) {
        if (createdAt < 0L || updatedAt < createdAt || (lastOpenedAt != null && lastOpenedAt < 0L)) {
            invalid("备份记录时间无效。")
        }
    }

    private fun validateText(value: String) {
        if (value.encodeToByteArray().size > BackupPayloadCodec.MAX_TEXT_BYTES) invalid("单条备份内容过大。")
    }

    private fun invalid(message: String): Nothing = throw BackupFormatException(message)
}

class RestoreConsistencyException(
    message: String,
    cause: Throwable? = null,
    val recoveryIncomplete: Boolean = false
) :
    IllegalStateException(message, cause)
