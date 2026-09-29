package com.privacyhub.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoreProtocolTest {
    @Test
    fun replaceRestoresAllStoresAndClearsJournal() {
        val database = FakeDatabase(items = setOf("old-item"), credentials = setOf("old-login"))
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        val result = protocol.restore(payload(), RestoreMode.Replace)

        assertEquals(setOf("new-item"), database.items)
        assertEquals(setOf("new-login"), database.credentials)
        assertEquals(newSettings, settings.current)
        assertEquals(1, result.insertedItems)
        assertEquals(1, result.insertedCredentials)
        assertNull(database.journal)
        assertNull(settings.staged)
    }

    @Test
    fun mergePreservesExistingDataAndSkipsDuplicates() {
        val database = FakeDatabase(items = setOf("old-item", "new-item"), credentials = setOf("old-login"))
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        val result = protocol.restore(payload(), RestoreMode.Merge)

        assertEquals(setOf("old-item", "new-item"), database.items)
        assertEquals(setOf("old-login", "new-login"), database.credentials)
        assertEquals(listOf("旧分类", "新分类"), settings.current.customCategories)
        assertEquals(0, result.insertedItems)
        assertEquals(1, result.skippedItems)
        assertNull(database.journal)
    }

    @Test
    fun databaseTransactionFailureRollsBackReplaceDataAndSettings() {
        val database = FakeDatabase(setOf("old-item"), setOf("old-login")).apply {
            failTransactionAfterSettings = true
        }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        assertFailsWith<IllegalStateException> { protocol.restore(payload(), RestoreMode.Replace) }

        assertEquals(setOf("old-item"), database.items)
        assertEquals(setOf("old-login"), database.credentials)
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
    }

    @Test
    fun settingsCommitFalseDoesNotLoseReplaceData() {
        val database = FakeDatabase(setOf("old-item"), setOf("old-login"))
        val settings = FakeSettings(oldSettings).apply { applyReturnsFalse = true }
        val protocol = protocol(database, settings)

        assertFailsWith<RestoreConsistencyException> { protocol.restore(payload(), RestoreMode.Replace) }

        assertEquals(setOf("old-item"), database.items)
        assertEquals(setOf("old-login"), database.credentials)
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
    }

    @Test
    fun settingsEncryptionErrorDoesNotLoseReplaceData() {
        val database = FakeDatabase(setOf("old-item"), setOf("old-login"))
        val settings = FakeSettings(oldSettings).apply { applyError = SettingsEncryptionFailure() }
        val protocol = protocol(database, settings)

        assertFailsWith<SettingsEncryptionFailure> { protocol.restore(payload(), RestoreMode.Replace) }

        assertEquals(setOf("old-item"), database.items)
        assertEquals(setOf("old-login"), database.credentials)
        assertEquals(oldSettings, settings.current)
    }

    @Test
    fun unreadableSettingsPreventReplaceBeforeJournalOrDatabaseWrites() {
        val database = FakeDatabase(setOf("old-item"), setOf("old-login"))
        val settings = FakeSettings(oldSettings).apply { stageError = SettingsEncryptionFailure() }
        val protocol = protocol(database, settings)

        assertFailsWith<SettingsEncryptionFailure> { protocol.restore(payload(), RestoreMode.Replace) }

        assertNull(database.lastPreparedJournal)
        assertNull(database.journal)
        assertEquals(setOf("old-item"), database.items)
        assertEquals(setOf("old-login"), database.credentials)
        assertEquals(oldSettings, settings.current)
        assertNull(settings.staged)
    }

    @Test
    fun mergeSettingsFailureIsReportedAndDoesNotLeaveInsertedRows() {
        val database = FakeDatabase(setOf("old-item"), setOf("old-login"))
        val settings = FakeSettings(oldSettings).apply { applyReturnsFalse = true }
        val protocol = protocol(database, settings)

        assertFailsWith<RestoreConsistencyException> { protocol.restore(payload(), RestoreMode.Merge) }

        assertEquals(setOf("old-item"), database.items)
        assertEquals(setOf("old-login"), database.credentials)
        assertFalse("new-item" in database.items)
        assertFalse("new-login" in database.credentials)
    }

    @Test
    fun restartRecoversPreparedJournalInsteadOfTreatingItAsComplete() {
        val journal = journal(RestoreJournalState.Prepared)
        val database = FakeDatabase(setOf("old-item"), setOf("old-login"), journal)
        val settings = FakeSettings(newSettings).apply { staged = journal.operationId to oldSettings }

        protocol(database, settings)

        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
        assertNull(settings.staged)
    }

    @Test
    fun restartFinalizesCommittedJournalWithoutRollingBackCompletedRestore() {
        val journal = journal(RestoreJournalState.DatabaseCommitted)
        val database = FakeDatabase(setOf("new-item"), setOf("new-login"), journal)
        val settings = FakeSettings(newSettings).apply { staged = journal.operationId to oldSettings }

        protocol(database, settings)

        assertEquals(setOf("new-item"), database.items)
        assertEquals(newSettings, settings.current)
        assertNull(database.journal)
        assertNull(settings.staged)
    }

    @Test
    fun invalidPayloadIsRejectedBeforeJournalOrDataWrites() {
        val database = FakeDatabase(setOf("old-item"), setOf("old-login"))
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)
        val invalid = payload().copy(items = payload().items + payload().items.single())

        assertFailsWith<BackupFormatException> { protocol.restore(invalid, RestoreMode.Replace) }

        assertNull(database.lastPreparedJournal)
        assertEquals(setOf("old-item"), database.items)
        assertEquals(oldSettings, settings.current)
    }

    @Test
    fun journalMetadataNeverContainsPayloadContent() {
        val database = FakeDatabase(setOf("old-item"), setOf("old-login"))
        val settings = FakeSettings(oldSettings).apply { applyReturnsFalse = true }
        val protocol = protocol(database, settings)

        assertFailsWith<RestoreConsistencyException> { protocol.restore(payload(), RestoreMode.Replace) }

        val journalText = database.lastPreparedJournal.toString()
        assertFalse(journalText.contains("secret-body"))
        assertFalse(journalText.contains("secret-password"))
        assertTrue(journalText.contains("operation-1"))
    }

    @Test
    fun coordinatedRenameCommitsRoomAndSettingsTogether() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "旧分类" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        val count = protocol.runCoordinatedMutation(
            applyRoom = { database.categoryRows["item"] = "新分类"; 1 },
            applySettings = { settings.current = newSettings; true }
        )

        assertEquals(1, count)
        assertEquals("新分类", database.categoryRows["item"])
        assertEquals(newSettings, settings.current)
        assertNull(database.journal)
        assertNull(settings.staged)
    }

    @Test
    fun renameRoomFailureRollsBackAndPreservesItemCategory() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "旧分类" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        assertFailsWith<IllegalStateException> {
            protocol.runCoordinatedMutation(
                applyRoom = { database.categoryRows["item"] = "新分类"; error("injected room failure") },
                applySettings = { settings.current = newSettings; true }
            )
        }

        assertEquals("旧分类", database.categoryRows["item"])
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
    }

    @Test
    fun renameSettingsCommitFailureRollsBackRoomAndSettings() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "旧分类" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        assertFailsWith<RestoreConsistencyException> {
            protocol.runCoordinatedMutation(
                applyRoom = { database.categoryRows["item"] = "新分类"; 1 },
                applySettings = { settings.current = newSettings; false }
            )
        }

        assertEquals("旧分类", database.categoryRows["item"])
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
    }

    @Test
    fun renameRollbackFailureLeavesJournalForFailClosedRecovery() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "旧分类" }
        val settings = FakeSettings(oldSettings).apply { rollbackReturnsFalse = true }
        val protocol = protocol(database, settings)

        val error = assertFailsWith<RestoreConsistencyException> {
            protocol.runCoordinatedMutation(
                applyRoom = { database.categoryRows["item"] = "新分类"; 1 },
                applySettings = { settings.current = newSettings; false }
            )
        }

        assertTrue(error.recoveryIncomplete)
        assertEquals("旧分类", database.categoryRows["item"])
        assertEquals(newSettings, settings.current)
        assertEquals(RestoreJournalState.Prepared, database.journal?.state)
    }

    @Test
    fun moveRoomFailureKeepsContentUnderItsOriginalCategory() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "来源" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        assertFailsWith<IllegalStateException> {
            protocol.runCoordinatedMutation(
                applyRoom = { database.categoryRows["item"] = "目标"; error("injected move failure") },
                applySettings = { settings.current = newSettings; true }
            )
        }

        assertEquals("来源", database.categoryRows["item"])
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
    }

    @Test
    fun moveSettingsFailureRollsBackRoomMutation() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "来源" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        assertFailsWith<RestoreConsistencyException> {
            protocol.runCoordinatedMutation(
                applyRoom = { database.categoryRows["item"] = "目标"; 1 },
                applySettings = { false }
            )
        }

        assertEquals("来源", database.categoryRows["item"])
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
    }

    @Test
    fun removeCategorySettingsFailureDoesNotDeleteUserContent() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "自定义" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        assertFailsWith<RestoreConsistencyException> {
            protocol.runCoordinatedMutation(
                applyRoom = { database.categoryRows["item"] = "其他"; 1 },
                applySettings = { false }
            )
        }

        assertEquals(setOf("item"), database.items)
        assertEquals("自定义", database.categoryRows["item"])
        assertEquals(oldSettings, settings.current)
    }

    @Test
    fun confirmedDeleteCommitsContentAndMetadataTogether() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "自定义" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        val deleted = protocol.runCoordinatedMutation(
            applyRoom = { database.items.remove("item"); database.categoryRows.remove("item"); 1 },
            applySettings = { settings.current = PortableBackupSettings(emptyList(), emptyMap(), emptyList()); true }
        )

        assertEquals(1, deleted)
        assertTrue(database.items.isEmpty())
        assertTrue(database.categoryRows.isEmpty())
        assertEquals(PortableBackupSettings(emptyList(), emptyMap(), emptyList()), settings.current)
        assertNull(database.journal)
    }

    @Test
    fun confirmedDeleteSettingsFailureRollsBackContentDeletion() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply { categoryRows["item"] = "自定义" }
        val settings = FakeSettings(oldSettings)
        val protocol = protocol(database, settings)

        assertFailsWith<RestoreConsistencyException> {
            protocol.runCoordinatedMutation(
                applyRoom = { database.items.remove("item"); database.categoryRows.remove("item"); 1 },
                applySettings = { false }
            )
        }

        assertEquals(setOf("item"), database.items)
        assertEquals("自定义", database.categoryRows["item"])
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
    }

    @Test
    fun restartAfterInterruptedCategoryMutationRestoresPreOperationState() {
        val database = FakeDatabase(setOf("item"), emptySet()).apply {
            categoryRows["item"] = "旧分类"
            simulatedProcessDeathAfterSettings = true
        }
        val settings = FakeSettings(oldSettings)
        val firstProcess = protocol(database, settings)

        assertFailsWith<SimulatedProcessDeath> {
            firstProcess.runCoordinatedMutation(
                applyRoom = { database.categoryRows["item"] = "新分类"; 1 },
                applySettings = { settings.current = newSettings; true }
            )
        }
        assertEquals("旧分类", database.categoryRows["item"])
        assertEquals(RestoreJournalState.Prepared, database.journal?.state)
        assertEquals(newSettings, settings.current)

        protocol(database, settings)

        assertEquals("旧分类", database.categoryRows["item"])
        assertEquals(oldSettings, settings.current)
        assertNull(database.journal)
        assertNull(settings.staged)
    }

    private fun protocol(database: FakeDatabase, settings: FakeSettings) = RestoreProtocol(
        database = database,
        settings = settings,
        operationIdFactory = { "operation-1" },
        clock = { 100L }
    )

    private fun payload() = BackupPayload(
        createdAtMillis = 10L,
        items = listOf(
            VaultBackupItem(
                backupId = "new-item",
                type = "text",
                title = "title",
                content = "secret-body",
                normalizedLink = null,
                originalLink = null,
                sourceApp = "manual",
                entryPoint = "MANUAL",
                category = "新分类",
                note = null,
                favorite = false,
                createdAtMillis = 1L,
                updatedAtMillis = 2L,
                lastOpenedAtMillis = null,
                target = "UNKNOWN"
            )
        ),
        settings = newSettings,
        autofillCredentials = listOf(
            AutofillBackupCredential(
                backupId = "new-login",
                label = "website",
                username = "user",
                password = "secret-password",
                packageName = null,
                createdAtMillis = 1L,
                updatedAtMillis = 2L,
                webDomain = "accounts.example.com"
            )
        )
    )

    private fun journal(state: RestoreJournalState) = RestoreJournal(
        operationId = "operation-1",
        operation = RestoreJournalOperation.Replace,
        state = state,
        startedAtMillis = 100L
    )

    private class FakeDatabase(
        items: Set<String>,
        credentials: Set<String>,
        initialJournal: RestoreJournal? = null
    ) : RestoreDatabaseGateway {
        val items = items.toMutableSet()
        val credentials = credentials.toMutableSet()
        val categoryRows = mutableMapOf<String, String>()
        var journal: RestoreJournal? = initialJournal
        var lastPreparedJournal: RestoreJournal? = null
        var failTransactionAfterSettings = false
        var simulatedProcessDeathAfterSettings = false

        override fun currentJournal(): RestoreJournal? = journal

        override fun prepareJournal(journal: RestoreJournal) {
            check(this.journal == null)
            this.journal = journal
            lastPreparedJournal = journal
        }

        override fun restoreInTransaction(
            payload: BackupPayload,
            mode: RestoreMode,
            operationId: String,
            applySettings: () -> Unit
        ): RestoreCounts {
            val oldItems = items.toSet()
            val oldCredentials = credentials.toSet()
            val oldJournal = journal
            return try {
                if (mode == RestoreMode.Replace) {
                    items.clear()
                    credentials.clear()
                }
                val insertedItems = payload.items.count { items.add(it.backupId) }
                val insertedCredentials = payload.autofillCredentials.count { credentials.add(it.backupId) }
                applySettings()
                if (failTransactionAfterSettings) error("injected database transaction failure")
                journal = checkNotNull(journal).copy(state = RestoreJournalState.DatabaseCommitted)
                RestoreCounts(insertedItems, insertedCredentials)
            } catch (error: Exception) {
                items.clear()
                items.addAll(oldItems)
                credentials.clear()
                credentials.addAll(oldCredentials)
                journal = oldJournal
                throw error
            }
        }

        override fun <T> runCoordinatedMutationInTransaction(
            operationId: String,
            applyRoom: () -> T,
            applySettings: () -> Unit
        ): T {
            val oldItems = items.toSet()
            val oldRows = categoryRows.toMap()
            val oldJournal = journal
            return try {
                val result = applyRoom()
                applySettings()
                if (simulatedProcessDeathAfterSettings) throw SimulatedProcessDeath()
                if (failTransactionAfterSettings) error("injected database transaction failure")
                journal = checkNotNull(journal).copy(state = RestoreJournalState.DatabaseCommitted)
                result
            } catch (error: Throwable) {
                items.clear()
                items.addAll(oldItems)
                categoryRows.clear()
                categoryRows.putAll(oldRows)
                journal = oldJournal
                throw error
            }
        }

        override fun clearJournal(operationId: String) {
            check(journal?.operationId == operationId)
            journal = null
        }
    }

    private class FakeSettings(initial: PortableBackupSettings) : RestoreSettingsGateway {
        var current = initial
        var staged: Pair<String, PortableBackupSettings>? = null
        var stageError: RuntimeException? = null
        var applyReturnsFalse = false
        var applyError: RuntimeException? = null
        var rollbackReturnsFalse = false

        override fun stageRollback(operationId: String): Boolean {
            stageError?.let { throw it }
            staged = operationId to current
            return true
        }

        override fun apply(settings: PortableBackupSettings, mode: RestoreMode): Boolean {
            applyError?.let { throw it }
            if (applyReturnsFalse) return false
            current = when (mode) {
                RestoreMode.Replace -> settings
                RestoreMode.Merge -> PortableBackupSettings(
                    customCategories = (current.customCategories + settings.customCategories).distinct(),
                    categoryAliases = settings.categoryAliases + current.categoryAliases,
                    homeTags = (current.homeTags + settings.homeTags).distinct().take(5)
                )
            }
            return true
        }

        override fun rollback(operationId: String): Boolean {
            if (rollbackReturnsFalse) return false
            val rollback = staged ?: return false
            if (rollback.first != operationId) return false
            current = rollback.second
            return true
        }

        override fun discardRollback(operationId: String?): Boolean {
            val rollback = staged ?: return true
            if (operationId != null && rollback.first != operationId) return false
            staged = null
            return true
        }
    }

    private class SettingsEncryptionFailure : RuntimeException("injected encryption failure")
    private class SimulatedProcessDeath : Error("simulated process termination")

    private companion object {
        val oldSettings = PortableBackupSettings(
            customCategories = listOf("旧分类"),
            categoryAliases = mapOf("购物" to "旧分类"),
            homeTags = listOf("旧分类")
        )
        val newSettings = PortableBackupSettings(
            customCategories = listOf("新分类"),
            categoryAliases = mapOf("密码" to "新分类"),
            homeTags = listOf("新分类")
        )
    }
}
