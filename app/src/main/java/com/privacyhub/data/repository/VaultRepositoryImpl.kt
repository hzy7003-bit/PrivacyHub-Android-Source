package com.privacyhub.data.repository

import android.content.Context
import com.privacyhub.backup.BackupPayloadCodec
import com.privacyhub.data.db.PrivacyHubDatabaseProvider
import com.privacyhub.data.mapper.VaultItemMapper
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.SavedItem
import com.privacyhub.domain.model.VaultItem
import com.privacyhub.router.LinkCleaner
import com.privacyhub.router.LinkParser
import com.privacyhub.router.LinkTargetDetector

class VaultRepositoryImpl(context: Context) {
    private val dao = PrivacyHubDatabaseProvider.get(context).vaultItemDao()
    private val settingsStore = AppSettingsStore(context)

    fun saveText(
        entryPoint: EntryPoint,
        sourceApp: String,
        originalText: String,
        primaryUrl: String?,
        target: LinkTarget
    ): Long {
        BackupPayloadCodec.requireCompatibleText(originalText)
        return dao.insert(
            VaultItemMapper.newVaultEntity(
                entryPoint = entryPoint,
                sourceApp = sourceApp,
                originalText = originalText,
                primaryUrl = primaryUrl,
                target = target,
                resolveCategory = settingsStore::resolveSuggestedCategory
            )
        )
    }

    fun saveManual(
        title: String,
        content: String,
        category: String?,
        note: String?
    ): Long {
        listOfNotNull(title, content, note).forEach(BackupPayloadCodec::requireCompatibleText)
        val originalLink = LinkParser.extractFirstUrl(content)
        val target = LinkTargetDetector.detect(content)
        return dao.insert(
            VaultItemMapper.newVaultEntity(
                entryPoint = EntryPoint.MANUAL,
                sourceApp = "privacy_hub",
                originalText = content,
                primaryUrl = originalLink,
                target = target,
                title = title,
                category = settingsStore.normalizeCategoryForStorage(category),
                note = note,
                resolveCategory = settingsStore::resolveSuggestedCategory
            )
        )
    }

    fun recentSavedItems(limit: Int): List<SavedItem> {
        return dao.recent(limit).map(VaultItemMapper::toSavedItem)
    }

    fun favoriteSavedItems(limit: Int): List<SavedItem> {
        return dao.favorites(limit).map(VaultItemMapper::toSavedItem)
    }

    fun categorySavedItems(category: String, limit: Int): List<SavedItem> {
        val names = settingsStore.queryNamesForCategory(category)
        if (names.isEmpty()) return emptyList()
        return dao.byCategories(names, limit).map(VaultItemMapper::toSavedItem)
    }

    fun categories(): List<String> {
        return dao.categories()
    }

    fun renameCategory(oldCategory: String, newCategory: String): Int {
        return moveCategory(oldCategory, newCategory)
    }

    fun clearCategory(category: String): Int {
        return moveCategory(category, "其他")
    }

    fun moveCategory(category: String, newCategory: String): Int {
        val names = settingsStore.queryNamesForCategory(category)
        val normalizedNew = settingsStore.normalizeCategoryForStorage(newCategory) ?: return 0
        if (names.isEmpty()) return 0
        return dao.moveCategories(names, normalizedNew, System.currentTimeMillis())
    }

    fun countByCategory(category: String): Int {
        val names = settingsStore.queryNamesForCategory(category)
        if (names.isEmpty()) return 0
        return dao.countByCategories(names)
    }

    fun deleteByCategory(category: String): Int {
        val names = settingsStore.queryNamesForCategory(category)
        if (names.isEmpty()) return 0
        return dao.deleteByCategories(names)
    }

    fun search(query: String, limit: Int): List<VaultItem> {
        return dao.search(query, limit).map(VaultItemMapper::toVaultItem)
    }

    fun searchSavedItems(query: String, limit: Int): List<SavedItem> {
        return dao.search(query, limit).map(VaultItemMapper::toSavedItem)
    }

    fun getById(id: Long): VaultItem? {
        return dao.getById(id)?.let(VaultItemMapper::toVaultItem)
    }

    fun updateDetails(
        id: Long,
        title: String,
        content: String,
        accessCode: String?,
        category: String?,
        note: String?
    ) {
        listOfNotNull(title, content, note).forEach(BackupPayloadCodec::requireCompatibleText)
        val originalLink = LinkParser.extractFirstUrl(content)
        val target = LinkTargetDetector.detect(content)
        dao.updateDetails(
            id = id,
            title = title.ifBlank { if (originalLink.isNullOrBlank()) "文本" else "${target.displayName}链接" },
            content = content,
            normalizedLink = LinkCleaner.clean(originalLink),
            originalLink = originalLink,
            accessCode = accessCode?.trim()?.takeIf { it.length in 4..8 && it.all(Char::isLetterOrDigit) },
            category = settingsStore.normalizeCategoryForStorage(category),
            note = note?.ifBlank { null },
            target = target.name,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    fun delete(id: Long) {
        dao.deleteById(id)
    }
}
