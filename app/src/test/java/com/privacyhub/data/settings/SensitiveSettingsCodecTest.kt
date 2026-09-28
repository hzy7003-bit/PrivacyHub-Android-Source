package com.privacyhub.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SensitiveSettingsCodecTest {
    @Test
    fun roundTripsCategoryNamesWithoutDependingOnSeparators() {
        val categories = listOf("工作", "包含\u001E分隔符", "银行卡")

        assertEquals(categories, SensitiveSettingsCodec.decodeList(SensitiveSettingsCodec.encodeList(categories)))
    }

    @Test
    fun roundTripsCategoryAliases() {
        val aliases = linkedMapOf("密码" to "工作口令", "证件" to "家人证件")

        assertEquals(aliases, SensitiveSettingsCodec.decodeMap(SensitiveSettingsCodec.encodeMap(aliases)))
    }

    @Test
    fun rejectsTruncatedPayloadsWithoutReturningPartialSettings() {
        assertEquals(emptyList(), SensitiveSettingsCodec.decodeList("2:ab4:xy"))
        assertEquals(emptyMap(), SensitiveSettingsCodec.decodeMap("1:a1:b2"))
    }

    @Test
    fun roundTripsTheCompleteCategorySettingsSnapshot() {
        val snapshot = CategorySettingsSnapshot(
            customCategories = listOf("工作", "家人"),
            categoryAliases = mapOf("密码" to "工作"),
            homeTags = listOf("工作", "网盘")
        )

        assertEquals(snapshot, SensitiveSettingsCodec.decodeSnapshot(SensitiveSettingsCodec.encodeSnapshot(snapshot)))
    }

    @Test
    fun rejectsIncompleteSnapshots() {
        assertNull(SensitiveSettingsCodec.decodeSnapshot(SensitiveSettingsCodec.encodeList(listOf("only-one"))))
    }
}
