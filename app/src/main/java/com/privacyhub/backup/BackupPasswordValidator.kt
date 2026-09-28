package com.privacyhub.backup

internal object BackupPasswordValidator {
    private const val MIN_PASSWORD_LENGTH = 12

    fun validationError(password: String, confirmation: String): String? {
        return when {
            password.length < MIN_PASSWORD_LENGTH -> "备份口令至少需要 $MIN_PASSWORD_LENGTH 个字符"
            password != confirmation -> "两次输入的备份口令不一致"
            else -> null
        }
    }
}
