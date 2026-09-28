package com.privacyhub.data.settings

internal object SensitiveSettingsCodec {
    fun encodeList(values: List<String>): String = buildString {
        append(values.size)
        append(';')
        values.forEach { value ->
            append(value.length)
            append(':')
            append(value)
        }
    }

    fun decodeList(payload: String?): List<String> {
        return decodeListOrNull(payload).orEmpty()
    }

    private fun decodeListOrNull(payload: String?): List<String>? {
        if (payload.isNullOrEmpty()) return null
        return try {
            var position = 0
            val countEnd = payload.indexOf(';', position)
            require(countEnd > position)
            val count = payload.substring(position, countEnd).toInt()
            require(count in 0..MAX_ENTRIES)
            position = countEnd + 1

            val result = ArrayList<String>(count)
            repeat(count) {
                val lengthEnd = payload.indexOf(':', position)
                require(lengthEnd > position)
                val length = payload.substring(position, lengthEnd).toInt()
                require(length in 0..MAX_VALUE_LENGTH)
                position = lengthEnd + 1
                require(position + length <= payload.length)
                result += payload.substring(position, position + length)
                position += length
            }
            require(position == payload.length)
            result
        } catch (_: Exception) {
            null
        }
    }

    fun encodeMap(values: Map<String, String>): String {
        return encodeList(values.flatMap { (key, value) -> listOf(key, value) })
    }

    fun decodeMap(payload: String?): Map<String, String> {
        val values = decodeList(payload)
        if (values.size % 2 != 0) return emptyMap()
        return values.chunked(2).associate { it[0] to it[1] }
    }

    fun encodeSnapshot(snapshot: CategorySettingsSnapshot): String {
        return encodeList(
            listOf(
                encodeList(snapshot.customCategories),
                encodeMap(snapshot.categoryAliases),
                encodeList(snapshot.homeTags)
            )
        )
    }

    fun decodeSnapshot(payload: String?): CategorySettingsSnapshot? {
        val sections = decodeListOrNull(payload) ?: return null
        if (sections.size != SNAPSHOT_SECTION_COUNT) return null
        val customCategories = decodeListOrNull(sections[0]) ?: return null
        val categoryAliases = decodeMapOrNull(sections[1]) ?: return null
        val homeTags = decodeListOrNull(sections[2]) ?: return null
        return CategorySettingsSnapshot(
            customCategories = customCategories,
            categoryAliases = categoryAliases,
            homeTags = homeTags
        )
    }

    private fun decodeMapOrNull(payload: String?): Map<String, String>? {
        val values = decodeListOrNull(payload) ?: return null
        if (values.size % 2 != 0) return null
        return values.chunked(2).associate { it[0] to it[1] }
    }

    private const val MAX_ENTRIES = 1_000
    private const val MAX_VALUE_LENGTH = 64 * 1_024
    private const val SNAPSHOT_SECTION_COUNT = 3
}

internal data class CategorySettingsSnapshot(
    val customCategories: List<String> = emptyList(),
    val categoryAliases: Map<String, String> = emptyMap(),
    val homeTags: List<String> = emptyList()
)
