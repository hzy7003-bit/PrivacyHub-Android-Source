package com.privacyhub.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun time(millis: Long): String = dateTime.format(Date(millis))

    fun preview(text: String, maxLength: Int = 160): String {
        val normalized = text.replace(Regex("""\s+"""), " ").trim()
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
    }
}
