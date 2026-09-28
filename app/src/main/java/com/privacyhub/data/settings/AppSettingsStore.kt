package com.privacyhub.data.settings

import android.content.Context
import com.privacyhub.backup.PortableBackupSettings

class AppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val categorySettings = CategorySettingsStore(preferences)

    fun lockTimeout(): LockTimeoutOption {
        val millis = preferences.getLong(KEY_LOCK_TIMEOUT_MS, LockTimeoutOption.TenSeconds.timeoutMs)
        return LockTimeoutOption.entries.firstOrNull { it.timeoutMs == millis } ?: LockTimeoutOption.TenSeconds
    }

    fun setLockTimeout(option: LockTimeoutOption) {
        preferences.edit()
            .putLong(KEY_LOCK_TIMEOUT_MS, option.timeoutMs)
            .apply()
    }

    fun isPersistentNotificationEnabled(): Boolean {
        return preferences.getBoolean(KEY_PERSISTENT_NOTIFICATION_ENABLED, false)
    }

    fun setPersistentNotificationEnabled(enabled: Boolean, synchronous: Boolean = false): Boolean {
        val editor = preferences.edit().putBoolean(KEY_PERSISTENT_NOTIFICATION_ENABLED, enabled)
        return if (synchronous) editor.commit() else {
            editor.apply()
            true
        }
    }

    fun isBootNotificationRestoreEnabled(): Boolean {
        return preferences.getBoolean(KEY_BOOT_NOTIFICATION_RESTORE_ENABLED, false)
    }

    fun setBootNotificationRestoreEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_BOOT_NOTIFICATION_RESTORE_ENABLED, enabled)
            .apply()
    }

    fun isSecurityLockEnabled(): Boolean {
        return preferences.getBoolean(KEY_SECURITY_LOCK_ENABLED, false)
    }

    fun setSecurityLockEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SECURITY_LOCK_ENABLED, enabled)
            .apply()
    }

    fun isAutofillEnabled(): Boolean {
        return preferences.getBoolean(KEY_AUTOFILL_ENABLED, false)
    }

    fun setAutofillEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_AUTOFILL_ENABLED, enabled)
            .apply()
    }

    fun isNetdiskAutomationEnabled(): Boolean {
        return preferences.getBoolean(KEY_NETDISK_AUTOMATION_ENABLED, false)
    }

    fun setNetdiskAutomationEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_NETDISK_AUTOMATION_ENABLED, enabled)
            .apply()
    }

    fun shouldRecordAutofillIncompatibleApp(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val key = KEY_AUTOFILL_INCOMPATIBLE_PREFIX + packageName
        val previous = preferences.getLong(key, 0L)
        if (nowMillis - previous < AUTOFILL_DIAGNOSTIC_INTERVAL_MS) return false
        preferences.edit().putLong(key, nowMillis).apply()
        return true
    }

    fun isHomePreviewEnabled(): Boolean {
        return preferences.getBoolean(KEY_HOME_PREVIEW_ENABLED, false)
    }

    fun setHomePreviewEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_HOME_PREVIEW_ENABLED, enabled)
            .apply()
    }

    fun isScreenshotProtectionEnabled(): Boolean {
        return preferences.getBoolean(KEY_SCREENSHOT_PROTECTION_ENABLED, false)
    }

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SCREENSHOT_PROTECTION_ENABLED, enabled)
            .apply()
    }

    fun isHideFromRecentsEnabled(): Boolean {
        return preferences.getBoolean(KEY_HIDE_FROM_RECENTS_ENABLED, false)
    }

    fun setHideFromRecentsEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_HIDE_FROM_RECENTS_ENABLED, enabled)
            .apply()
    }

    fun themeMode(): ThemeModeOption {
        val stored = preferences.getString(KEY_THEME_MODE, ThemeModeOption.FollowSystem.name)
        return ThemeModeOption.entries.firstOrNull { it.name == stored } ?: ThemeModeOption.FollowSystem
    }

    fun setThemeMode(option: ThemeModeOption) {
        preferences.edit()
            .putString(KEY_THEME_MODE, option.name)
            .apply()
    }

    fun lastSeenTrialNoticeVersion(): String? {
        return preferences.getString(KEY_LAST_SEEN_TRIAL_NOTICE_VERSION, null)
    }

    fun setLastSeenTrialNoticeVersion(versionName: String?) {
        preferences.edit()
            .putString(KEY_LAST_SEEN_TRIAL_NOTICE_VERSION, versionName)
            .apply()
    }

    fun customCategories(): List<String> {
        return categorySettings.customCategories()
    }

    fun categorySettingsReadError(): String? = categorySettings.settingsReadError()

    fun allKnownCategories(dbCategories: List<String>): List<String> {
        return categorySettings.allKnownCategories(dbCategories)
    }

    fun addCategory(name: String): Boolean {
        return categorySettings.addCategory(name)
    }

    fun removeCustomCategory(name: String) {
        categorySettings.removeCustomCategory(name)
    }

    fun renameCustomCategory(oldName: String, newName: String) {
        categorySettings.renameCustomCategory(oldName, newName)
    }

    fun resolveSuggestedCategory(category: String): String {
        return categorySettings.resolveSuggestedCategory(category)
    }

    fun setCategoryAlias(systemCategory: String, userCategory: String) {
        categorySettings.setCategoryAlias(systemCategory, userCategory)
    }

    fun normalizeCategoryForStorage(category: String?): String? {
        return categorySettings.normalizeCategoryForStorage(category)
    }

    fun queryNamesForCategory(category: String): List<String> {
        return categorySettings.queryNamesForCategory(category)
    }

    fun isSystemCategory(category: String): Boolean {
        return categorySettings.isSystemCategory(category)
    }

    fun homeTags(dbCategories: List<String>): List<String> {
        return categorySettings.homeTags(dbCategories)
    }

    fun setHomeTags(tags: List<String>) {
        categorySettings.setHomeTags(tags)
    }

    fun addHomeTag(category: String, dbCategories: List<String>): Boolean {
        return categorySettings.addHomeTag(category, dbCategories)
    }

    fun removeHomeTag(category: String) {
        categorySettings.removeHomeTag(category)
    }

    fun moveHomeTag(category: String, direction: Int, dbCategories: List<String>): Boolean {
        return categorySettings.moveHomeTag(category, direction, dbCategories)
    }

    fun portableBackupSettings(dbCategories: List<String>): PortableBackupSettings {
        return categorySettings.portableBackupSettings(dbCategories)
    }

    fun mergePortableBackupSettings(snapshot: PortableBackupSettings): Boolean =
        categorySettings.mergePortableBackupSettings(snapshot)

    fun replacePortableBackupSettings(snapshot: PortableBackupSettings): Boolean =
        categorySettings.replacePortableBackupSettings(snapshot)

    internal fun stagePortableRestoreRollback(operationId: String): Boolean =
        categorySettings.stagePortableRestoreRollback(operationId)

    internal fun rollbackPortableRestore(operationId: String): Boolean =
        categorySettings.rollbackPortableRestore(operationId)

    internal fun discardPortableRestoreRollback(operationId: String?): Boolean =
        categorySettings.discardPortableRestoreRollback(operationId)

    companion object {
        private const val PREFS_NAME = "privacy_hub_app_settings"
        private const val KEY_LOCK_TIMEOUT_MS = "lock_timeout_ms"
        private const val KEY_PERSISTENT_NOTIFICATION_ENABLED = "persistent_notification_enabled"
        private const val KEY_BOOT_NOTIFICATION_RESTORE_ENABLED = "boot_notification_restore_enabled"
        private const val KEY_SECURITY_LOCK_ENABLED = "security_lock_enabled"
        private const val KEY_AUTOFILL_ENABLED = "autofill_enabled"
        private const val KEY_NETDISK_AUTOMATION_ENABLED = "netdisk_automation_enabled"
        private const val KEY_AUTOFILL_INCOMPATIBLE_PREFIX = "autofill_incompatible_"
        private const val KEY_HOME_PREVIEW_ENABLED = "home_preview_enabled"
        private const val KEY_SCREENSHOT_PROTECTION_ENABLED = "screenshot_protection_enabled"
        private const val KEY_HIDE_FROM_RECENTS_ENABLED = "hide_from_recents_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_SEEN_TRIAL_NOTICE_VERSION = "last_seen_trial_notice_version"
        const val MAX_HOME_TAGS = CategorySettingsStore.MAX_HOME_TAGS
        private const val AUTOFILL_DIAGNOSTIC_INTERVAL_MS = 24 * 60 * 60 * 1000L

        val DEFAULT_CATEGORIES = CategorySettingsStore.DEFAULT_CATEGORIES
        val DEFAULT_HOME_TAGS = CategorySettingsStore.DEFAULT_HOME_TAGS
    }
}

enum class LockTimeoutOption(
    val timeoutMs: Long,
    val label: String,
    val description: String
) {
    Immediate(
        timeoutMs = 0L,
        label = "立即锁定",
        description = "离开应用后马上重新验证，隐私最强。"
    ),
    TenSeconds(
        timeoutMs = 10_000L,
        label = "10 秒后锁定",
        description = "默认设置，兼顾隐私和频繁切换。"
    ),
    OneMinute(
        timeoutMs = 60_000L,
        label = "1 分钟后锁定",
        description = "适合短时间切到微信或浏览器再回来。"
    ),
    FiveMinutes(
        timeoutMs = 5 * 60_000L,
        label = "5 分钟后锁定",
        description = "更方便，但旁人拿到手机时风险更高。"
    )
}

enum class ThemeModeOption(
    val label: String,
    val description: String
) {
    FollowSystem(
        label = "跟随系统",
        description = "手机切换深色或浅色时，隐私中转站自动跟随。"
    ),
    Light(
        label = "浅色模式",
        description = "始终使用白色和浅灰界面。"
    ),
    Dark(
        label = "深色模式",
        description = "始终使用低亮度深色界面，适合夜间。"
    )
}
