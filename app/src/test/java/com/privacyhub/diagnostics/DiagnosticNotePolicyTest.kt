package com.privacyhub.diagnostics

import com.privacyhub.domain.model.EntryPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticNotePolicyTest {
    private val sensitiveValues = listOf(
        "密码=CorrectHorseBatteryStaple",
        "验证码 482913",
        "Bearer secret-token-123456",
        "https://private.example/account?token=secret",
        "私人账号片段"
    )

    @Test
    fun notificationSaveNoteNeverContainsClipboardContentOrPrefix() {
        assertSafe(DiagnosticNotePolicy.saveSucceeded(EntryPoint.NOTIFICATION))
    }

    @Test
    fun tileSaveNoteNeverContainsClipboardContentOrPrefix() {
        assertSafe(DiagnosticNotePolicy.saveSucceeded(EntryPoint.TILE))
    }

    @Test
    fun typicalSensitiveValuesCannotEnterTheFixedNotes() {
        listOf(
            DiagnosticNotePolicy.NOTIFICATION_SAVE_SUCCEEDED,
            DiagnosticNotePolicy.TILE_SAVE_SUCCEEDED,
            DiagnosticNotePolicy.NOTIFICATION_SAVE_AND_OPEN_SUCCEEDED,
            DiagnosticNotePolicy.NOTIFICATION_SAVED_OPEN_FAILED,
            DiagnosticNotePolicy.NOTIFICATION_OPEN_FALLBACK_SUCCEEDED,
            DiagnosticNotePolicy.NOTIFICATION_OPEN_SUCCEEDED,
            DiagnosticNotePolicy.NOTIFICATION_OPEN_FAILED
        ).forEach(::assertSafe)
    }

    @Test
    fun successfulNotificationAndTileNotesAreStableNonSensitiveStatuses() {
        assertEquals("通知栏保存成功", DiagnosticNotePolicy.saveSucceeded(EntryPoint.NOTIFICATION))
        assertEquals("快捷磁贴保存成功", DiagnosticNotePolicy.saveSucceeded(EntryPoint.TILE))
        assertTrue(DiagnosticNotePolicy.NOTIFICATION_SAVE_SUCCEEDED.isNotBlank())
        assertTrue(DiagnosticNotePolicy.TILE_SAVE_SUCCEEDED.isNotBlank())
    }

    @Test
    fun deletingTheSavedItemDoesNotChangeOrReintroduceDiagnosticContent() {
        val notesBeforeDelete = setOf(
            DiagnosticNotePolicy.saveSucceeded(EntryPoint.NOTIFICATION),
            DiagnosticNotePolicy.saveSucceeded(EntryPoint.TILE)
        )
        val notesAfterDelete = notesBeforeDelete.toSet()
        notesAfterDelete.forEach(::assertSafe)
        assertEquals(notesBeforeDelete, notesAfterDelete)
    }

    @Test
    fun saveStatusPolicyDoesNotDependOnTheSavedContent() {
        val notificationNote = DiagnosticNotePolicy.saveSucceeded(EntryPoint.NOTIFICATION)
        val tileNote = DiagnosticNotePolicy.saveSucceeded(EntryPoint.TILE)
        assertFalse(notificationNote == tileNote)
        assertSafe(notificationNote)
        assertSafe(tileNote)
    }

    @Test
    fun arbitraryNotesAndFailureReasonsAreReplacedAsWholeValues() {
        val malicious = "失败 https://secret.example/?token=TOP_SECRET password=123456 SECRET_TOKEN"

        val note = DiagnosticNotePolicy.safeNote("用户内容 ABCDEFG $malicious")
        val failureReason = DiagnosticNotePolicy.safeFailureReason(malicious)

        assertEquals(DiagnosticNotePolicy.GENERIC_NOTE, note)
        assertEquals(DiagnosticNotePolicy.GENERIC_FAILURE_REASON, failureReason)
        listOf(note, failureReason.orEmpty()).forEach { safe ->
            listOf("secret.example", "TOP_SECRET", "123456", "ABCDEFG", "SECRET_TOKEN").forEach { secret ->
                assertFalse(safe.contains(secret))
            }
        }
    }

    @Test
    fun passwordVerificationCodeTokenAndUrlAreNeverAcceptedAsFailureText() {
        listOf(
            "password=PRIVATE_PASSWORD_987",
            "验证码=VERIFY_123456",
            "Bearer TOKEN_SECRET_ABC",
            "https://private.example/path?token=URL_SECRET_456"
        ).forEach { sensitive ->
            assertEquals(
                DiagnosticNotePolicy.GENERIC_FAILURE_REASON,
                DiagnosticNotePolicy.safeFailureReason(sensitive)
            )
            assertEquals(DiagnosticNotePolicy.GENERIC_NOTE, DiagnosticNotePolicy.safeNote(sensitive))
        }
    }

    @Test
    fun onlyExactFixedDiagnosticReasonsAreRetained() {
        assertEquals(
            "系统剪切板为空",
            DiagnosticNotePolicy.safeFailureReason("系统剪切板为空")
        )
        assertEquals(
            "目标 App 未安装",
            DiagnosticNotePolicy.safeFailureReason("目标 App 未安装")
        )
        assertEquals(
            DiagnosticNotePolicy.GENERIC_FAILURE_REASON,
            DiagnosticNotePolicy.safeFailureReason("目标 App 未安装: https://secret.example")
        )
        assertEquals(null, DiagnosticNotePolicy.safeFailureReason(null))
    }

    private fun assertSafe(note: String) {
        sensitiveValues.forEach { value ->
            assertFalse(note.contains(value), "Diagnostic note contained sensitive value: $value")
            value.take(8).takeIf { it.length >= 4 }?.let { prefix ->
                assertFalse(note.contains(prefix), "Diagnostic note contained sensitive prefix: $prefix")
            }
        }
    }
}
