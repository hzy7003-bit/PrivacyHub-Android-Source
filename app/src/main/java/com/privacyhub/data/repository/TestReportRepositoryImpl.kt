package com.privacyhub.data.repository

import android.content.Context
import com.privacyhub.data.db.PrivacyHubDatabaseProvider
import com.privacyhub.data.db.DatabaseAccessCoordinator
import com.privacyhub.data.db.entity.TestReportEntity
import com.privacyhub.data.mapper.VaultItemMapper
import com.privacyhub.diagnostics.DiagnosticNotePolicy
import com.privacyhub.device.DeviceInfoProvider
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.TestReport

class TestReportRepositoryImpl(context: Context) {
    private val appContext = context.applicationContext
    private val dao by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PrivacyHubDatabaseProvider.get(appContext).testReportDao()
    }

    fun record(
        entryPoint: EntryPoint,
        success: Boolean,
        failureReason: String?,
        target: LinkTarget?,
        note: String
    ): Long {
        return DatabaseAccessCoordinator.withDatabaseAccess {
            val device = DeviceInfoProvider.current()
            val safeText = diagnosticTextForPersistence(failureReason, note)
            dao.insert(
                TestReportEntity(
                    createdAtMillis = System.currentTimeMillis(),
                    entryPoint = entryPoint.name,
                    androidVersion = device.androidVersion,
                    sdkInt = device.sdkInt,
                    brand = device.brand,
                    manufacturer = device.manufacturer,
                    model = device.model,
                    rom = device.rom,
                    success = success,
                    failureReason = safeText.failureReason,
                    target = target?.name,
                    note = safeText.note
                )
            )
        }
    }

    fun recent(limit: Int): List<TestReport> {
        return DatabaseAccessCoordinator.withDatabaseAccess {
            dao.recent(limit).map(VaultItemMapper::toTestReport)
        }
    }
}

internal data class SafeDiagnosticText(
    val failureReason: String?,
    val note: String
)

/** Final write-boundary allowlist; caller-provided text is never persisted verbatim. */
internal fun diagnosticTextForPersistence(
    failureReason: String?,
    note: String
): SafeDiagnosticText = SafeDiagnosticText(
    failureReason = DiagnosticNotePolicy.safeFailureReason(failureReason),
    note = DiagnosticNotePolicy.safeNote(note)
)
