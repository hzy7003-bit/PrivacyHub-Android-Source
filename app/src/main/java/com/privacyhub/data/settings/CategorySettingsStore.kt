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
        return (DEFAULT_CATEGORIES + customCategories() + dbCategories)
            .mapNotNull(::normalizeCategoryName)
            .distinct()
    }

    fun addCategory(name: String): Boolean {
        val normalized = normalizeCategoryName(name) ?: return false
        if (isSystemCategory(normalized)) return false
        val current = snapshot()
        val next = current.customCategories.toMutableSet()
        if (!next.add(normalized)) return false
        return save(current.copy(customCategories = next.sorted()))
    }

    fun removeCustomCategory(name: String) {
        val normalized = normalizeCategoryName(name) ?: return
        if (isSystemCategory(normalized)) return
        val current = snapshot()
        save(
            current.copy(
                customCategories = current.customCategories.filterNot { it == normalized },
                categoryAliases = current.categoryAliases.filterValues { it != normalized },
                homeTags = current.homeTags.filterNot { it == normalized }
            )
        )
    }

    fun renameCustomCategory(oldName: String, newName: String) {
        val old = normalizeCategoryName(oldName) ?: return
        val new = normalizeCategoryName(newName) ?: return
        if (isSystemCategory(old) || isSystemCategory(new)) return
        val current = snapshot()
        val categories = current.customCategories.filterNot { it == old }.toMutableSet().apply { add(new) }
        save(
            current.copy(
                customCategories = categories.sorted(),
                categoryAliases = current.categoryAliases.mapValues { (_, value) -> if (value == old) new else value },
                homeTags = current.homeTags.map { if (it == old) new else it }.distinct()
            )
        )
    }

    fun resolveSuggestedCategory(category: String): String {
        val normalized = normalizeCategoryName(category) ?: return CATEGORY_TEXT
        return readableSnapshotOrDefault().categoryAliases[normalized] ?: normalized
    }

    fun setCategoryAlias(systemCategory: String, userCategory: String) {
        val system = normalizeCategoryName(systemCategory) ?: return
        val user = normalizeCategoryName(userCategory) ?: return
        val current = snapshot()
        save(current.copy(categoryAliases = current.categoryAliases + (system to user)))
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
        val known = allKnownCategories(dbCategories).toSet()
        val stored = readableSnapshotOrDefault().homeTags
        val candidates = if (stored.isEmpty()) DEFAULT_HOME_TAGS else stored
        return candidates
            .mapNotNull(::normalizeCategoryName)
            .distinct()
            .filter { it in known }
            .take(MAX_HOME_TAGS)
    }

    fun setHomeTags(tags: List<String>) {
        val next = tags.mapNotNull(::normalizeCategoryName).distinct().take(MAX_HOME_TAGS)
        val current = snapshot()
        save(current.copy(homeTags = next))
    }

    fun addHomeTag(category: String, dbCategories: List<String>): Boolean {
        val normalized = normalizeCategoryName(category) ?: return false
        val next = homeTags(dbCategories).toMutableList()
        if (normalized in next || next.size >= MAX_HOME_TAGS) return false
        next.add(normalized)
        val current = snapshot()
        return save(current.copy(homeTags = next))
    }

    fun removeHomeTag(category: String) {
        val normalized = normalizeCategoryName(category) ?: return
        val current = snapshot()
        save(current.copy(homeTags = current.homeTags.filterNot { it == normalized }))
    }

    fun moveHomeTag(category: String, direction: Int, dbCategories: List<String>): Boolean {
        val normalized = normalizeCategoryName(category) ?: return false
        val next = homeTags(dbCategories).toMutableList()
        val index = next.indexOf(normalized)
        val newIndex = index + direction
        if (index < 0 || newIndex !in next.indices) return false
        val moved = next.removeAt(index)
        next.add(newIndex, moved)
        return save(snapshot().copy(homeTags = next))
    }

    fun portableBackupSettings(dbCategories: List<String>): PortableBackupSettings {
        val current = snapshot()
        return PortableBackupSettings(
            customCategories = customCategories(),
            categoryAliases = current.categoryAliases,
            homeTags = homeTags(dbCategories)
        )
    }

    fun mergePortableBackupSettings(backup: PortableBackupSettings): Boolean {
        val current = snapshot()
        val categories = (current.customCategories + backup.customCategories)
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
        val categories = backup.customCategories
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

    private fun normalizeHomeTags(tags: List<String>): List<String> {
        return tags.mapNotNull(::normalizeCategoryName).distinct().take(MAX_HOME_TAGS)
    }

    private fun normalizeCategoryName(name: String): String? {
        val trimmed = name.trim().take(MAX_CATEGORY_NAME_LENGTH)
        if (trimmed.isBlank()) return null
        return if (trimmed == LEGACY_CATEGORY_TOKEN) CATEGORY_PASSWORD else trimmed
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
