package com.privacyhub.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupPasswordValidatorTest {
    @Test
    fun rejectsPasswordsShorterThanTwelveCharacters() {
        assertEquals(
            "备份口令至少需要 12 个字符",
            BackupPasswordValidator.validationError("short", "short")
        )
    }

    @Test
    fun rejectsMismatchedPasswords() {
        assertEquals(
            "两次输入的备份口令不一致",
            BackupPasswordValidator.validationError("long-password", "other-password")
        )
    }

    @Test
    fun acceptsMatchingPasswordsWithTheMinimumLength() {
        assertNull(BackupPasswordValidator.validationError("123456789012", "123456789012"))
    }
}
