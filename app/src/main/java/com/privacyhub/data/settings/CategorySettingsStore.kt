package com.privacyhub.data.settings

import android.content.SharedPreferences
import com.privacyhub.backup.PortableBackupSettings
import com.privacyhub.security.keystore.KeystoreTextEncryptor

internal class CategorySettingsStore(
    private val preferences: SharedPreferences,
    private val encryptedStore: EncryptedSettingsStore = EncryptedSettingsStore(
        preferences,
        KeystoreTextEncryptor(KEYSTORE_ALIAS)
    )
) {
    fun customCategories(): List<String> {
        return readableSnapshotOrDefault().customCategories
            .mapNotNull(::normalizeCategoryName)
            .filterNot(::isSystemCategory)
            .distinct()
            .sorted()
    }

    fun allKnownCategories(dbCategories: List<String>): List<String> {
        val current = normalizedReferences(dbCategories)
        return (DEFAULT_CATEGORIES + current.customCategories + dbCategories)
            .mapNotNull(::normalizeCategoryName)
            .distinct()
    }

    fun addCategory(name: String): Boolean {
        val normalized = normalizeCategoryName(name) ?: return false
        if (isSystemCategory(normalized)) return false
        val current = snapshot()
        val next = current.customCategories.toMutableSet()
        if (!next.add(normalized)) return false
        persistOrThrow(current.copy(customCategories = next.sorted()), "无法保存自定义分类。")
        return true
    }

    fun removeCustomCategory(name: String): Boolean {
        val normalized = normalizeCategoryName(name) ?: return false
        if (isSystemCategory(normalized)) return true
        val current = snapshot()
        return save(
            current.copy(
                customCategories = current.customCategories.filterNot { it == normalized },
                categoryAliases = current.categoryAliases.filterKeys { it != normalized }.filterValues { it != normalized },
                homeTags = current.homeTags.filterNot { it == normalized }
            )
        )
    }

    fun renameCustomCategory(oldName: String, newName: String): Boolean {
        val old = normalizeCategoryName(oldName) ?: return false
        val new = normalizeCategoryName(newName) ?: return false
        if (isSystemCategory(old)) return false
        val current = snapshot()
        val categories = current.customCategories.filterNot { it == old }.toMutableSet()
        if (!isSystemCategory(new)) categories.add(new)
        val aliases = linkedMapOf<String, String>()
        current.categoryAliases.forEach { (source, destination) ->
            val renamedSource = if (source == old) new else source
            val renamedDestination = if (destination == old) new else destination
            val previousDestination = aliases.putIfAbsent(renamedSource, renamedDestination)
            if (previousDestination != null && previousDestination != renamedDestination) {
                throw EncryptedSettingsUnavailableException("分类别名存在冲突，管理操作已停用。")
            }
        }
        return save(
            current.copy(
                customCategories = categories.sorted(),
                categoryAliases = aliases,
                homeTags = current.homeTags.map { if (it == old) new else it }.distinct()
            )
        )
    }

    fun resolveSuggestedCategory(category: String): String {
        val normalized = normalizeCategoryName(category) ?: return CATEGORY_TEXT
        return readableSnapshotOrDefault().categoryAliases[normalized] ?: normalized
    }

    fun setCategoryAlias(systemCategory: String, userCategory: String, dbCategories: List<String>): Boolean {
        val system = normalizeCategoryName(systemCategory) ?: return false
        val user = normalizeCategoryName(userCategory) ?: return false
        if (!isSystemCategory(system) || user !in allKnownCategories(dbCategories)) return false
        val current = snapshot()
        return save(current.copy(categoryAliases = current.categoryAliases + (system to user)))
    }

    fun normalizeCategoryForStorage(category: String?): String? = category?.let(::normalizeCategoryName)

    fun queryNamesForCategory(category: String): List<String> {
        val normalized = normalizeCategoryName(category) ?: return emptyList()
        return if (normalized == CATEGORY_PASSWORD) {
            listOf(CATEGORY_PASSWORD, LEGACY_CATEGORY_TOKEN)
        } else {
            listOf(normalized)
        }
    }

    fun isSystemCategory(category: String): Boolean {
        val normalized = normalizeCategoryName(category) ?: return false
        return normalized in DEFAULT_CATEGORIES
    }

    fun homeTags(dbCategories: List<String>): List<String> {
        val current = normalizedReferences(dbCategories)
        val known = (DEFAULT_CATEGORIES + current.customCategories + dbCategories).toSet()
        val stored = current.homeTags
        val candidates = if (stored.isEmpty()) DEFAULT_HOME_TAGS else stored
        return candidates
            .mapNotNull(::normalizeCategoryName)
            .distinct()
            .filter { it in known }
            .take(MAX_HOME_TAGS)
    }

    fun setHomeTags(tags: List<String>): Boolean {
        val next = tags.mapNotNull(::normalizeCategoryName).distinct().take(MAX_HOME_TAGS)
        val current = snapshot()
        val categories = (current.customCategories + next.filterNot(::isSystemCategory)).distinct().sorted()
        return save(current.copy(customCategories = categories, homeTags = next))
    }

    fun addHomeTag(category: String, dbCategories: List<String>): Boolean {
        val normalized = normalizeCategoryName(category) ?: return false
        val known = allKnownCategories(dbCategories).toSet()
        if (normalized !in known) return false
        val next = homeTags(dbCategories).toMutableList()
        if (normalized in next || next.size >= MAX_HOME_TAGS) return false
        next.add(normalized)
        val current = snapshot()
        persistOrThrow(current.copy(homeTags = next), "无法保存首页分类。")
        return true
    }

    fun removeHomeTag(category: String): Boolean {
        val normalized = normalizeCategoryName(category) ?: return false
        val current = snapshot()
        return save(current.copy(homeTags = current.homeTags.filterNot { it == normalized }))
    }

    fun moveHomeTag(category: String, direction: Int, dbCategories: List<String>): Boolean {
        val normalized = normalizeCategoryName(category) ?: return false
        val next = homeTags(dbCategories).toMutableList()
        val index = next.indexOf(normalized)
        val newIndex = index + direction
        if (index < 0 || newIndex !in next.indices) return false
        val moved = next.removeAt(index)
        next.add(newIndex, moved)
        persistOrThrow(snapshot().copy(homeTags = next), "无法调整首页分类顺序。")
        return true
    }

    fun portableBackupSettings(dbCategories: List<String>): PortableBackupSettings {
        val current = normalizedReferences(dbCategories)
        val known = (DEFAULT_CATEGORIES + current.customCategories + dbCategories).toSet()
        val tags = if (current.homeTags.isEmpty()) DEFAULT_HOME_TAGS else current.homeTags
        return PortableBackupSettings(
            customCategories = current.customCategories,
            categoryAliases = current.categoryAliases,
            homeTags = tags.filter { it in known }.take(MAX_HOME_TAGS)
        )
    }

    fun mergePortableBackupSettings(backup: PortableBackupSettings): Boolean {
        val current = normalizedReferences(emptyList())
        val categories = (current.customCategories + current.categoryAliases.values + current.homeTags +
            backup.customCategories + backup.categoryAliases.values + backup.homeTags)
            .mapNotNull(::normalizeCategoryName)
            .filterNot(::isSystemCategory)
            .distinct()
            .sorted()
        val aliases = current.categoryAliases.toMutableMap()
        backup.categoryAliases.forEach { (source, destination) ->
            val normalizedSource = normalizeCategoryName(source)
            val normalizedDestination = normalizeCategoryName(destination)
            if (normalizedSource != null && normalizedDestination != null) {
                aliases.putIfAbsent(normalizedSource, normalizedDestination)
            }
        }
        val homeTags = if (current.homeTags.isEmpty()) {
            backup.homeTags
        } else {
            current.homeTags + backup.homeTags.filterNot(current.homeTags::contains)
        }
        return save(current.copy(customCategories = categories, categoryAliases = aliases, homeTags = normalizeHomeTags(homeTags)))
    }

    fun replacePortableBackupSettings(backup: PortableBackupSettings): Boolean {
        val categories = (backup.customCategories + backup.categoryAliases.values + backup.homeTags)
            .mapNotNull(::normalizeCategoryName)
            .filterNot(::isSystemCategory)
            .distinct()
            .sorted()
        val aliases = backup.categoryAliases.mapNotNull { (source, destination) ->
            val normalizedSource = normalizeCategoryName(source)
            val normalizedDestination = normalizeCategoryName(destination)
            if (normalizedSource == null || normalizedDestination == null) null
            else normalizedSource to normalizedDestination
        }.toMap()
        return save(CategorySettingsSnapshot(categories, aliases, normalizeHomeTags(backup.homeTags)))
    }

    fun stagePortableRestoreRollback(operationId: String): Boolean {
        val payload = SensitiveSettingsCodec.encodeList(
            listOf(operationId, SensitiveSettingsCodec.encodeSnapshot(snapshot()))
        )
        return encryptedStore.write(ENCRYPTED_RESTORE_ROLLBACK_KEY, payload)
    }

    fun rollbackPortableRestore(operationId: String): Boolean {
        val staged = stagedRollback(operationId) ?: return false
        return save(staged)
    }

    fun discardPortableRestoreRollback(operationId: String?): Boolean {
        if (operationId != null) stagedRollback(operationId)
        return encryptedStore.remove(ENCRYPTED_RESTORE_ROLLBACK_KEY)
    }

    private fun stagedRollback(operationId: String): CategorySettingsSnapshot? {
        val payload = encryptedStore.read(ENCRYPTED_RESTORE_ROLLBACK_KEY) ?: return null
        val sections = SensitiveSettingsCodec.decodeList(payload)
        if (sections.size != 2 || sections[0] != operationId) {
            throw EncryptedSettingsWriteException("设置恢复暂存与恢复任务不匹配。")
        }
        return SensitiveSettingsCodec.decodeSnapshot(sections[1])
            ?: throw EncryptedSettingsWriteException("设置恢复暂存格式无效。")
    }

    private fun snapshot(): CategorySettingsSnapshot {
        val payload = encryptedStore.readOrMigrate(
            key = ENCRYPTED_SNAPSHOT_KEY,
            legacyValue = { legacySnapshotOrNull()?.let(SensitiveSettingsCodec::encodeSnapshot) },
            clearLegacy = {
                remove(LEGACY_KEY_CUSTOM_CATEGORIES)
                remove(LEGACY_KEY_CATEGORY_ALIASES)
                remove(LEGACY_KEY_HOME_TAGS)
            },
            validatePayload = { SensitiveSettingsCodec.decodeSnapshot(it) != null }
        )
        if (payload == null) return CategorySettingsSnapshot()
        return SensitiveSettingsCodec.decodeSnapshot(payload)
            ?: throw EncryptedSettingsUnavailableException("本地分类设置内容格式无效。")
    }

    /** Restores valid dangling references as custom metadata instead of silently dropping them. */
    private fun normalizedReferences(dbCategories: List<String>): CategorySettingsSnapshot {
        val current = snapshot()
        val custom = current.customCategories
            .map(::normalizeStoredCategoryName)
            .filterNot(::isSystemCategory)
            .toMutableSet()
        val aliases = linkedMapOf<String, String>()
        current.categoryAliases.forEach { (sourceName, destinationName) ->
            val source = normalizeStoredCategoryName(sourceName)
            val destination = normalizeStoredCategoryName(destinationName)
            val previousDestination = aliases.putIfAbsent(source, destination)
            if (previousDestination != null && previousDestination != destination) {
                throw EncryptedSettingsUnavailableException("分类别名存在冲突，管理操作已停用。")
            }
            if (!isSystemCategory(destination) && destination !in dbCategories) custom.add(destination)
        }
        if (current.homeTags.size > MAX_HOME_TAGS) {
            throw EncryptedSettingsUnavailableException("首页分类设置数量无效，管理操作已停用。")
        }
        val tags = current.homeTags.map(::normalizeStoredCategoryName).distinct()
        tags.filterNot(::isSystemCategory).filterNot(dbCategories::contains).forEach(custom::add)
        val normalized = CategorySettingsSnapshot(
            customCategories = custom.sorted(),
            categoryAliases = aliases,
            homeTags = tags
        )
        if (normalized != current && !save(normalized)) {
            throw EncryptedSettingsWriteException("无法修复分类引用，管理操作已停用。")
        }
        return normalized
    }

    private fun readableSnapshotOrDefault(): CategorySettingsSnapshot =
        try {
            snapshot()
        } catch (_: EncryptedSettingsException) {
            CategorySettingsSnapshot()
        }

    fun settingsReadError(): String? = try {
        snapshot()
        null
    } catch (_: EncryptedSettingsException) {
        "本地分类设置当前无法读取，原数据已保留。管理操作已停用，请先尝试重启应用或从备份恢复。"
    }

    private fun legacySnapshotOrNull(): CategorySettingsSnapshot? {
        val hasLegacyData = listOf(
            LEGACY_KEY_CUSTOM_CATEGORIES,
            LEGACY_KEY_CATEGORY_ALIASES,
            LEGACY_KEY_HOME_TAGS
        ).any(preferences::contains)
        if (!hasLegacyData) return null

        try {
            return legacySnapshot()
        } catch (error: Exception) {
            throw EncryptedSettingsUnavailableException("无法读取旧分类设置。", error)
        }
    }

    private fun legacySnapshot(): CategorySettingsSnapshot {
        val categories = preferences.getStringSet(LEGACY_KEY_CUSTOM_CATEGORIES, emptySet()).orEmpty().toList()
        val aliases = preferences.getStringSet(LEGACY_KEY_CATEGORY_ALIASES, emptySet()).orEmpty()
            .mapNotNull { encoded ->
                val index = encoded.indexOf(LEGACY_ALIAS_SEPARATOR)
                if (index <= 0 || index >= encoded.lastIndex) null
                else encoded.substring(0, index) to encoded.substring(index + 1)
            }.toMap()
        val tags = preferences.getString(LEGACY_KEY_HOME_TAGS, null).orEmpty()
            .split(LEGACY_ORDER_SEPARATOR)
            .filter(String::isNotBlank)
        return CategorySettingsSnapshot(categories, aliases, tags)
    }

    private fun save(snapshot: CategorySettingsSnapshot): Boolean {
        return encryptedStore.write(ENCRYPTED_SNAPSHOT_KEY, SensitiveSettingsCodec.encodeSnapshot(snapshot))
    }

    private fun persistOrThrow(snapshot: CategorySettingsSnapshot, message: String) {
        if (!save(snapshot)) throw EncryptedSettingsWriteException(message)
    }

    private fun normalizeHomeTags(tags: List<String>): List<String> {
        return tags.mapNotNull(::normalizeCategoryName).distinct().take(MAX_HOME_TAGS)
    }

    private fun normalizeCategoryName(name: String): String? {
        val trimmed = name.trim().take(MAX_CATEGORY_NAME_LENGTH)
        if (trimmed.isBlank()) return null
        return if (trimmed == LEGACY_CATEGORY_TOKEN) CATEGORY_PASSWORD else trimmed
    }

    private fun normalizeStoredCategoryName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_CATEGORY_NAME_LENGTH) {
            throw EncryptedSettingsUnavailableException("分类设置内容无效，管理操作已停用。")
        }
        return normalizeCategoryName(trimmed)
            ?: throw EncryptedSettingsUnavailableException("分类设置内容无效，管理操作已停用。")
    }

    companion object {
        const val MAX_HOME_TAGS = 5
        val DEFAULT_CATEGORIES = listOf(
            "购物", "内容", "网盘", "密码", "证件", "银行卡",
            "验证码", "地址", "链接", "文本", "其他"
        )
        val DEFAULT_HOME_TAGS = listOf("购物", "内容", "网盘", "密码", "证件")

        private const val KEYSTORE_ALIAS = "privacy_hub_category_settings_key"
        private const val ENCRYPTED_SNAPSHOT_KEY = "category_settings_snapshot_v1"
        private const val ENCRYPTED_RESTORE_ROLLBACK_KEY = "category_settings_restore_rollback_v1"
        private const val LEGACY_KEY_CUSTOM_CATEGORIES = "custom_categories"
        private const val LEGACY_KEY_CATEGORY_ALIASES = "category_aliases"
        private const val LEGACY_KEY_HOME_TAGS = "home_tags_order"
        private const val LEGACY_ALIAS_SEPARATOR = '\u001F'
        private const val LEGACY_ORDER_SEPARATOR = '\u001E'
        private const val MAX_CATEGORY_NAME_LENGTH = 16
        private const val CATEGORY_PASSWORD = "密码"
        private const val CATEGORY_TEXT = "文本"
        private const val LEGACY_CATEGORY_TOKEN = "口令"
    }
}
