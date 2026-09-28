package com.privacyhub.data.repository

import com.privacyhub.diagnostics.DiagnosticNotePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestReportRepositoryPrivacyTest {
    @Test
    fun persistenceBoundaryNeverRetainsDynamicDiagnosticText() {
        val secret = "https://secret.example/path?token=TOP_SECRET password=123456 SECRET_TOKEN"

        val safe = diagnosticTextForPersistence(
            failureReason = "Intent data=$secret",
            note = "用户原文前缀 SECRET_TOKEN $secret"
        )

        assertEquals(DiagnosticNotePolicy.GENERIC_FAILURE_REASON, safe.failureReason)
        assertEquals(DiagnosticNotePolicy.GENERIC_NOTE, safe.note)
        listOf(safe.failureReason.orEmpty(), safe.note).forEach { text ->
            listOf("secret.example", "TOP_SECRET", "123456", "SECRET_TOKEN", "Intent data=").forEach { part ->
                assertFalse(text.contains(part))
            }
        }
    }

    @Test
    fun persistenceBoundaryKeepsOnlyApprovedStaticDescriptions() {
        val safe = diagnosticTextForPersistence(
            failureReason = "目标 App 未安装",
            note = "通知栏保存成功"
        )

        assertEquals("目标 App 未安装", safe.failureReason)
        assertEquals("通知栏保存成功", safe.note)
    }
}
