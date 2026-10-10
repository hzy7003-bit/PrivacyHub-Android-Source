package com.privacyhub.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupPayloadSizePolicyTest {
    @Test
    fun ordinaryAndBoundaryTextRemainBackupCompatible() {
        val ordinary = "普通剪切板文本"
        val boundary = "a".repeat(BackupPayloadCodec.MAX_TEXT_BYTES)

        assertTrue(BackupPayloadCodec.isCompatibleText(ordinary))
        assertTrue(BackupPayloadCodec.isCompatibleText(boundary))
        BackupPayloadCodec.requireCompatibleText(boundary)

        val payload = payload(listOf(item("normal", ordinary), item("boundary", boundary)))
        assertEquals(payload, BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload)))
    }

    @Test
    fun utf8ByteLimitRejectsOversizedClipboardTextWithoutTruncation() {
        assertFalse(BackupPayloadCodec.isCompatibleText("x".repeat(BackupPayloadCodec.MAX_TEXT_BYTES + 1)))
        val oversized = "中".repeat(BackupPayloadCodec.MAX_TEXT_BYTES / 3 + 1)
        assertFalse(BackupPayloadCodec.isCompatibleText(oversized))
        assertFailsWith<BackupFormatException> {
            BackupPayloadCodec.requireCompatibleText(oversized)
        }
        assertEquals(BackupPayloadCodec.MAX_TEXT_BYTES / 3 + 1, oversized.length)
    }

    @Test
    fun oneOversizedRecordFailsWholeExportAndPreservesAllSourceRecords() {
        val normal = item("normal", "keep this")
        val oversized = item("large", "x".repeat(BackupPayloadCodec.MAX_TEXT_BYTES + 1))
        val payload = payload(listOf(normal, oversized))
        val before = payload.items.toList()

        assertFailsWith<BackupFormatException> { BackupPayloadCodec.encode(payload) }

        assertEquals(before, payload.items)
        assertEquals("keep this", payload.items.first().content)
        assertEquals(BackupPayloadCodec.MAX_TEXT_BYTES + 1, payload.items.last().content.length)
        assertEquals(
            normal,
            BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload(listOf(normal)))).items.single()
        )
    }

    private fun payload(items: List<VaultBackupItem>) = BackupPayload(
        createdAtMillis = 1L,
        items = items,
        settings = PortableBackupSettings(emptyList(), emptyMap(), emptyList())
    )

    private fun item(id: String, content: String) = VaultBackupItem(
        backupId = id,
        type = "text",
        title = "title",
        content = content,
        normalizedLink = null,
        originalLink = null,
        sourceApp = "test",
        entryPoint = "MANUAL",
        category = null,
        note = null,
        favorite = false,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
        lastOpenedAtMillis = null,
        target = "UNKNOWN"
    )
}
