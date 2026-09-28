package com.privacyhub.data.repository

import android.content.Context
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.SavedItem
import com.privacyhub.domain.model.TestReport
import com.privacyhub.domain.model.VaultItem
import com.privacyhub.util.AppExecutors

class LocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val vaultRepository = VaultRepositoryImpl(appContext)
    private val reportRepository = TestReportRepositoryImpl(appContext)
    private val settingsStore = AppSettingsStore(appContext)

    fun saveItem(
        entryPoint: EntryPoint,
        sourceApp: String,
        originalText: String,
        primaryUrl: String?,
        target: LinkTarget
    ): Long {
        return AppExecutors.runDiskBlocking {
            vaultRepository.saveText(entryPoint, sourceApp, originalText, primaryUrl, target)
        }
    }

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
    ): Long = vaultRepository.saveText(entryPoint, sourceApp, originalText, primaryUrl, target)

    fun saveManualItem(
        title: String,
        content: String,
        category: String?,
        note: String?
    ): Long {
        return AppExecutors.runDiskBlocking {
            vaultRepository.saveManual(title, content, category, note)
        }
    }

    fun recordReport(
        entryPoint: EntryPoint,
        success: Boolean,
        failureReason: String?,
        target: LinkTarget?,
        note: String
    ): Long {
        return AppExecutors.runDiskBlocking {
            reportRepository.record(entryPoint, success, failureReason, target, note)
        }
    }

    /** Performs the report write on the caller's disk worker. */
    internal fun recordReportOnDisk(
        entryPoint: EntryPoint,
        success: Boolean,
        failureReason: String?,
        target: LinkTarget?,
        note: String
    ): Long = reportRepository.record(entryPoint, success, failureReason, target, note)

    fun recentItems(limit: Int = 20): List<SavedItem> {
        return AppExecutors.runDiskBlocking { vaultRepository.recentSavedItems(limit) }
    }

    fun favoriteItems(limit: Int = 20): List<SavedItem> {
        return AppExecutors.runDiskBlocking { vaultRepository.favoriteSavedItems(limit) }
    }

    fun categoryItems(category: String, limit: Int = 20): List<SavedItem> {
        return AppExecutors.runDiskBlocking { vaultRepository.categorySavedItems(category, limit) }
    }

    fun categories(): List<String> {
        return AppExecutors.runDiskBlocking { settingsStore.allKnownCategories(vaultRepository.categories()) }
    }

    fun homeTags(): List<String> {
        return AppExecutors.runDiskBlocking { settingsStore.homeTags(vaultRepository.categories()) }
    }

    fun categorySettingsReadError(): String? =
        AppExecutors.runDiskBlocking { settingsStore.categorySettingsReadError() }

    fun addCategory(name: String): Boolean {
        return settingsStore.addCategory(name)
    }

    fun renameCategory(oldName: String, newName: String): Int {
        val old = oldName.trim()
        val new = newName.trim()
        if (old.isBlank() || new.isBlank() || old == new) return 0
        return AppExecutors.runDiskBlocking {
            requireCategorySettingsReadable()
            val normalizedNew = settingsStore.normalizeCategoryForStorage(new) ?: return@runDiskBlocking 0
            if (settingsStore.isSystemCategory(old)) {
                if (!settingsStore.isSystemCategory(normalizedNew)) {
                    settingsStore.addCategory(normalizedNew)
                }
                settingsStore.setCategoryAlias(old, normalizedNew)
            } else {
                settingsStore.renameCustomCategory(old, normalizedNew)
            }
            vaultRepository.renameCategory(old, new)
        }
    }

    fun removeCategory(name: String): Int {
        val category = name.trim()
        if (category.isBlank()) return 0
        return AppExecutors.runDiskBlocking {
            requireCategorySettingsReadable()
            settingsStore.removeCustomCategory(category)
            vaultRepository.clearCategory(category)
        }
    }

    fun isSystemCategory(name: String): Boolean {
        return settingsStore.isSystemCategory(name)
    }

    fun countByCategory(name: String): Int {
        return AppExecutors.runDiskBlocking { vaultRepository.countByCategory(name) }
    }

    fun moveCategory(oldName: String, newName: String): Int {
        val target = settingsStore.normalizeCategoryForStorage(newName) ?: return 0
        return AppExecutors.runDiskBlocking {
            requireCategorySettingsReadable()
            if (!settingsStore.isSystemCategory(target)) {
                settingsStore.addCategory(target)
            }
            vaultRepository.moveCategory(oldName, target)
        }
    }

    fun deleteCategoryItems(name: String): Int {
        return AppExecutors.runDiskBlocking { vaultRepository.deleteByCategory(name) }
    }

    fun deleteCustomCategory(name: String): Boolean {
        requireCategorySettingsReadable()
        val category = settingsStore.normalizeCategoryForStorage(name) ?: return false
        if (settingsStore.isSystemCategory(category)) return false
        settingsStore.removeCustomCategory(category)
        return true
    }

    fun addHomeTag(name: String): Boolean {
        return AppExecutors.runDiskBlocking { settingsStore.addHomeTag(name, vaultRepository.categories()) }
    }

    fun removeHomeTag(name: String) {
        settingsStore.removeHomeTag(name)
    }

    fun moveHomeTag(name: String, direction: Int): Boolean {
        return AppExecutors.runDiskBlocking {
            settingsStore.moveHomeTag(name, direction, vaultRepository.categories())
        }
    }

    fun setCategoryAlias(systemCategory: String, userCategory: String) {
        requireCategorySettingsReadable()
        settingsStore.setCategoryAlias(systemCategory, userCategory)
    }

    private fun requireCategorySettingsReadable() {
        settingsStore.categorySettingsReadError()?.let { message ->
            throw com.privacyhub.data.settings.EncryptedSettingsUnavailableException(message)
        }
    }

    fun searchItems(query: String, limit: Int = 20): List<SavedItem> {
        return AppExecutors.runDiskBlocking { vaultRepository.searchSavedItems(query, limit) }
    }

    fun getItem(id: Long): VaultItem? {
        return AppExecutors.runDiskBlocking { vaultRepository.getById(id) }
    }

    fun updateItem(
        id: Long,
        title: String,
        content: String,
        accessCode: String?,
        category: String?,
        note: String?
    ) {
        AppExecutors.runDiskBlocking {
            vaultRepository.updateDetails(id, title, content, accessCode, category, note)
        }
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        AppExecutors.runDiskBlocking { vaultRepository.setFavorite(id, favorite) }
    }

    fun deleteItem(id: Long) {
        AppExecutors.runDiskBlocking { vaultRepository.delete(id) }
    }

    fun recentReports(limit: Int = 50): List<TestReport> {
        return AppExecutors.runDiskBlocking { reportRepository.recent(limit) }
    }
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
