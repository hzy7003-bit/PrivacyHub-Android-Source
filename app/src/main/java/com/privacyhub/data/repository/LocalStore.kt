package com.privacyhub.data.repository

import android.content.Context
import com.privacyhub.data.db.DatabaseAccessCoordinator
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.SavedItem
import com.privacyhub.domain.model.TestReport
import com.privacyhub.domain.model.VaultItem
import androidx.annotation.WorkerThread
import com.privacyhub.backup.VaultBackupRepository
import com.privacyhub.data.settings.EncryptedSettingsWriteException

class LocalStore(context: Context) {
    private val appContext = context.applicationContext
    // Constructing the store from a UI event must not open SQLCipher as a side effect.
    private val vaultRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { VaultRepositoryImpl(appContext) }
    private val reportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { TestReportRepositoryImpl(appContext) }
    private val settingsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppSettingsStore(appContext) }
    private val backupRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { VaultBackupRepository(appContext) }

    @WorkerThread
    fun saveItem(
        entryPoint: EntryPoint,
        sourceApp: String,
        originalText: String,
        primaryUrl: String?,
        target: LinkTarget
    ): Long = databaseAccess { vaultRepository.saveText(entryPoint, sourceApp, originalText, primaryUrl, target) }

    /**
     * Performs the repository write on the caller's disk worker.
     * Callers must not invoke this from the main thread.
     */
    internal fun saveItemOnDisk(
        entryPoint: EntryPoint,
        sourceApp: String,
        originalText: String,
        primaryUrl: String?,
        target: LinkTarget
    ): Long = databaseAccess { vaultRepository.saveText(entryPoint, sourceApp, originalText, primaryUrl, target) }

    @WorkerThread
    fun saveManualItem(
        title: String,
        content: String,
        category: String?,
        note: String?
    ): Long = databaseAccess { vaultRepository.saveManual(title, content, category, note) }

    @WorkerThread
    fun recordReport(
        entryPoint: EntryPoint,
        success: Boolean,
        failureReason: String?,
        target: LinkTarget?,
        note: String
    ): Long = databaseAccess { reportRepository.record(entryPoint, success, failureReason, target, note) }

    /** Performs the report write on the caller's disk worker. */
    internal fun recordReportOnDisk(
        entryPoint: EntryPoint,
        success: Boolean,
        failureReason: String?,
        target: LinkTarget?,
        note: String
    ): Long = databaseAccess { reportRepository.record(entryPoint, success, failureReason, target, note) }

    @WorkerThread
    fun recentItems(limit: Int = 20): List<SavedItem> = databaseAccess { vaultRepository.recentSavedItems(limit) }

    @WorkerThread
    fun favoriteItems(limit: Int = 20): List<SavedItem> = databaseAccess { vaultRepository.favoriteSavedItems(limit) }

    @WorkerThread
    fun categoryItems(category: String, limit: Int = 20): List<SavedItem> = databaseAccess { vaultRepository.categorySavedItems(category, limit) }

    @WorkerThread
    fun categories(): List<String> = DatabaseAccessCoordinator.withDatabaseWriteAccess {
        settingsStore.allKnownCategories(vaultRepository.categories())
    }

    @WorkerThread
    fun homeTags(): List<String> = DatabaseAccessCoordinator.withDatabaseWriteAccess {
        settingsStore.homeTags(vaultRepository.categories())
    }

    @WorkerThread
    fun categorySettingsReadError(): String? =
        databaseAccess { settingsStore.categorySettingsReadError() }

    @WorkerThread
    fun addCategory(name: String): Boolean = DatabaseAccessCoordinator.withDatabaseWriteAccess {
        prepareCategorySettings()
        settingsStore.addCategory(name)
    }

    @WorkerThread
    fun renameCategory(oldName: String, newName: String): Int {
        val old = oldName.trim()
        val new = newName.trim()
        if (old.isBlank() || new.isBlank() || old == new) return 0
        val normalizedNew = settingsStore.normalizeCategoryForStorage(new) ?: return 0
        return backupRepository.runCoordinatedCategoryMutation(
            prepareSettings = ::prepareCategorySettings,
            applyRoom = { vaultRepository.renameCategory(old, new) },
            applySettings = {
                if (settingsStore.isSystemCategory(old)) {
                    ensureCategoryExists(normalizedNew)
                    requireCategorySettingsWrite(
                        settingsStore.setCategoryAlias(old, normalizedNew, vaultRepository.categories())
                    )
                } else {
                    requireCategorySettingsWrite(settingsStore.renameCustomCategory(old, normalizedNew))
                }
                true
            }
        )
    }

    @WorkerThread
    fun removeCategory(name: String): Int {
        val category = name.trim()
        if (category.isBlank()) return 0
        return backupRepository.runCoordinatedCategoryMutation(
            prepareSettings = ::prepareCategorySettings,
            applyRoom = { vaultRepository.clearCategory(category) },
            applySettings = {
                requireCategorySettingsWrite(settingsStore.removeCustomCategory(category))
                true
            }
        )
    }

    @WorkerThread
    fun isSystemCategory(name: String): Boolean {
        return databaseAccess { settingsStore.isSystemCategory(name) }
    }

    @WorkerThread
    fun countByCategory(name: String): Int {
        return databaseAccess { vaultRepository.countByCategory(name) }
    }

    @WorkerThread
    fun moveCategory(oldName: String, newName: String): Int {
        val target = settingsStore.normalizeCategoryForStorage(newName) ?: return 0
        return backupRepository.runCoordinatedCategoryMutation(
            prepareSettings = ::prepareCategorySettings,
            applyRoom = { vaultRepository.moveCategory(oldName, target) },
            applySettings = {
                ensureCategoryExists(target)
                true
            }
        )
    }

    /** Moves the rows and removes a custom source label as one recoverable operation. */
    @WorkerThread
    fun moveCategoryAndRemoveCustomSource(oldName: String, newName: String): Int {
        val old = oldName.trim()
        val target = settingsStore.normalizeCategoryForStorage(newName) ?: return 0
        if (old.isBlank() || old == target) return 0
        return backupRepository.runCoordinatedCategoryMutation(
            prepareSettings = ::prepareCategorySettings,
            applyRoom = { vaultRepository.moveCategory(old, target) },
            applySettings = {
                ensureCategoryExists(target)
                if (!settingsStore.isSystemCategory(old)) {
                    requireCategorySettingsWrite(settingsStore.removeCustomCategory(old))
                }
                true
            }
        )
    }

    @WorkerThread
    fun deleteCategoryItems(name: String): Int {
        return databaseAccess { vaultRepository.deleteByCategory(name) }
    }

    /** Deletes explicitly confirmed content and custom metadata in the same recoverable unit. */
    @WorkerThread
    fun deleteCategoryItemsAndMaybeRemoveCustomCategory(name: String, removeCustomCategory: Boolean): Int {
        val category = name.trim()
        if (category.isBlank()) return 0
        if (!removeCustomCategory) {
            return DatabaseAccessCoordinator.withDatabaseWriteAccess {
                vaultRepository.deleteByCategory(category)
            }
        }
        return backupRepository.runCoordinatedCategoryMutation(
            prepareSettings = ::prepareCategorySettings,
            applyRoom = { vaultRepository.deleteByCategory(category) },
            applySettings = {
                requireCategorySettingsWrite(settingsStore.removeCustomCategory(category))
                true
            }
        )
    }

    @WorkerThread
    fun deleteCustomCategory(name: String): Boolean {
        return DatabaseAccessCoordinator.withDatabaseWriteAccess {
            prepareCategorySettings()
            val category = settingsStore.normalizeCategoryForStorage(name) ?: return@withDatabaseWriteAccess false
            if (settingsStore.isSystemCategory(category)) return@withDatabaseWriteAccess false
            requireCategorySettingsWrite(settingsStore.removeCustomCategory(category))
            true
        }
    }

    @WorkerThread
    fun addHomeTag(name: String): Boolean {
        return DatabaseAccessCoordinator.withDatabaseWriteAccess {
            prepareCategorySettings()
            settingsStore.addHomeTag(name, vaultRepository.categories())
        }
    }

    @WorkerThread
    fun removeHomeTag(name: String) {
        DatabaseAccessCoordinator.withDatabaseWriteAccess {
            prepareCategorySettings()
            requireCategorySettingsWrite(settingsStore.removeHomeTag(name))
        }
    }

    @WorkerThread
    fun moveHomeTag(name: String, direction: Int): Boolean {
        return DatabaseAccessCoordinator.withDatabaseWriteAccess {
            prepareCategorySettings()
            settingsStore.moveHomeTag(name, direction, vaultRepository.categories())
        }
    }

    @WorkerThread
    fun setCategoryAlias(systemCategory: String, userCategory: String) {
        DatabaseAccessCoordinator.withDatabaseWriteAccess {
            prepareCategorySettings()
            requireCategorySettingsWrite(
                settingsStore.setCategoryAlias(systemCategory, userCategory, vaultRepository.categories())
            )
        }
    }

    private fun ensureCategoryExists(category: String) {
        if (settingsStore.isSystemCategory(category)) return
        if (category in settingsStore.customCategories() || category in vaultRepository.categories()) return
        requireCategorySettingsWrite(settingsStore.addCategory(category))
    }

    private fun prepareCategorySettings() {
        requireCategorySettingsReadable()
        settingsStore.allKnownCategories(vaultRepository.categories())
    }

    private fun requireCategorySettingsWrite(success: Boolean) {
        if (!success) throw EncryptedSettingsWriteException("分类设置未能保存，操作未完成。")
    }

    private fun requireCategorySettingsReadable() {
        settingsStore.categorySettingsReadError()?.let { message ->
            throw com.privacyhub.data.settings.EncryptedSettingsUnavailableException(message)
        }
    }

    @WorkerThread
    fun searchItems(query: String, limit: Int = 20): List<SavedItem> {
        return databaseAccess { vaultRepository.searchSavedItems(query, limit) }
    }

    @WorkerThread
    fun getItem(id: Long): VaultItem? {
        return databaseAccess { vaultRepository.getById(id) }
    }

    @WorkerThread
    fun updateItem(
        id: Long,
        title: String,
        content: String,
        accessCode: String?,
        category: String?,
        note: String?
    ) {
        databaseAccess { vaultRepository.updateDetails(id, title, content, accessCode, category, note) }
    }

    @WorkerThread
    fun setFavorite(id: Long, favorite: Boolean) {
        databaseAccess { vaultRepository.setFavorite(id, favorite) }
    }

    @WorkerThread
    fun deleteItem(id: Long) {
        databaseAccess { vaultRepository.delete(id) }
    }

    @WorkerThread
    fun recentReports(limit: Int = 50): List<TestReport> {
        return databaseAccess { reportRepository.recent(limit) }
    }

    private fun <T> databaseAccess(task: () -> T): T = DatabaseAccessCoordinator.withDatabaseAccess(task)
}

/** A diagnostics failure must never change the result of a completed core save. */
internal inline fun <T> saveWithBestEffortDiagnostic(
    save: () -> T,
    recordSuccess: (T) -> Unit
): T {
    val saved = save()
    runCatching { recordSuccess(saved) }
    return saved
}
