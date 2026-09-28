package com.privacyhub.data.mapper

import com.privacyhub.data.db.entity.TestReportEntity
import com.privacyhub.diagnostics.DiagnosticNotePolicy
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultItemMapperTest {
    @Test
    fun appliesCategoryResolverToSuggestedCategory() {
        val entity = VaultItemMapper.newVaultEntity(
            entryPoint = EntryPoint.MANUAL,
            sourceApp = "privacy_hub",
            originalText = "账号 demo@example.com 密码提示：生日加后缀",
            primaryUrl = null,
            target = LinkTarget.UNKNOWN,
            resolveCategory = { category ->
                if (category == "口令") "密码提示" else category
            }
        )

        assertEquals("密码提示", entity.category)
        assertEquals("口令备注", entity.title)
    }

    @Test
    fun testReportReadMappingNeverExposesUntrustedDiagnosticTextToUi() {
        val privateUrl = "https://private.example/?token=SECRET123"
        val report = VaultItemMapper.toTestReport(
            TestReportEntity(
                id = 1,
                createdAtMillis = 1,
                entryPoint = EntryPoint.NOTIFICATION.name,
                androidVersion = "test",
                sdkInt = 35,
                brand = "test",
                manufacturer = "test",
                model = "test",
                rom = "test",
                success = false,
                failureReason = "Intent { dat=$privateUrl }",
                target = LinkTarget.WEB.name,
                note = "sourceText prefix $privateUrl"
            )
        )

        assertEquals(DiagnosticNotePolicy.GENERIC_FAILURE_REASON, report.failureReason)
        assertEquals(DiagnosticNotePolicy.GENERIC_NOTE, report.note)
        assertEquals(false, report.failureReason.orEmpty().contains("private.example"))
        assertEquals(false, report.failureReason.orEmpty().contains("SECRET123"))
        assertEquals(false, report.note.contains("private.example"))
        assertEquals(false, report.note.contains("SECRET123"))
    }
}
