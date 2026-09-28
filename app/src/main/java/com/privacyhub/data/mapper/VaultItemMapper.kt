package com.privacyhub.data.mapper

import com.privacyhub.classifier.VaultContentSuggester
import com.privacyhub.data.db.entity.TestReportEntity
import com.privacyhub.data.db.entity.VaultItemEntity
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.SavedItem
import com.privacyhub.domain.model.TestReport
import com.privacyhub.domain.model.VaultItem
import com.privacyhub.diagnostics.DiagnosticNotePolicy
import com.privacyhub.router.LinkCleaner
import com.privacyhub.router.NetdiskAccessCodeParser
import java.util.UUID

object VaultItemMapper {
    fun toVaultItem(entity: VaultItemEntity): VaultItem = VaultItem(
        id = entity.id,
        type = entity.type,
        title = entity.title,
        content = entity.content,
        normalizedLink = entity.normalizedLink,
        originalLink = entity.originalLink,
        accessCode = entity.accessCode,
        sourceApp = entity.sourceApp,
        entryPoint = enumValueOrDefault(entity.entryPoint, EntryPoint.SHARE),
        category = entity.category,
        note = entity.note,
        favorite = entity.favorite,
        createdAtMillis = entity.createdAtMillis,
        updatedAtMillis = entity.updatedAtMillis,
        lastOpenedAtMillis = entity.lastOpenedAtMillis,
        target = enumValueOrDefault(entity.target, LinkTarget.UNKNOWN)
    )

    fun toSavedItem(entity: VaultItemEntity): SavedItem = SavedItem(
        id = entity.id,
        createdAtMillis = entity.createdAtMillis,
        entryPoint = enumValueOrDefault(entity.entryPoint, EntryPoint.SHARE),
        sourceApp = entity.sourceApp,
        originalText = entity.content,
        primaryUrl = entity.originalLink,
        accessCode = entity.accessCode,
        target = enumValueOrDefault(entity.target, LinkTarget.UNKNOWN),
        title = entity.title,
        favorite = entity.favorite,
        category = entity.category
    )

    fun toTestReport(entity: TestReportEntity): TestReport = TestReport(
        id = entity.id,
        createdAtMillis = entity.createdAtMillis,
        entryPoint = enumValueOrDefault(entity.entryPoint, EntryPoint.SHARE),
        androidVersion = entity.androidVersion,
        sdkInt = entity.sdkInt,
        brand = entity.brand,
        manufacturer = entity.manufacturer,
        model = entity.model,
        rom = entity.rom,
        success = entity.success,
        failureReason = DiagnosticNotePolicy.safeFailureReason(entity.failureReason),
        target = entity.target?.let { enumValueOrDefault(it, LinkTarget.UNKNOWN) },
        note = DiagnosticNotePolicy.safeNote(entity.note)
    )

    fun newVaultEntity(
        entryPoint: EntryPoint,
        sourceApp: String,
        originalText: String,
        primaryUrl: String?,
        target: LinkTarget,
        title: String? = null,
        category: String? = null,
        note: String? = null,
        resolveCategory: (String) -> String = { it },
        now: Long = System.currentTimeMillis()
    ): VaultItemEntity {
        val suggestion = VaultContentSuggester.suggest(originalText, primaryUrl, target)
        return VaultItemEntity(
            backupId = UUID.randomUUID().toString(),
            type = if (primaryUrl.isNullOrBlank()) "text" else "link",
            title = title?.ifBlank { null } ?: suggestion.title,
            content = originalText,
            normalizedLink = LinkCleaner.clean(primaryUrl),
            originalLink = primaryUrl,
            accessCode = if (target.supportsAccessCodeAutomation) {
                NetdiskAccessCodeParser.extract(originalText)
            } else {
                null
            },
            sourceApp = sourceApp,
            entryPoint = entryPoint.name,
            category = category?.ifBlank { null } ?: resolveCategory(suggestion.category),
            note = note?.ifBlank { null },
            favorite = false,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastOpenedAtMillis = null,
            target = target.name
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
