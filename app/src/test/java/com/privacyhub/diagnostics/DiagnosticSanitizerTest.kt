package com.privacyhub.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticSanitizerTest {
    @Test
    fun removesLinksAndLongNumbersFromFailureReason() {
        val sanitized = DiagnosticSanitizer.shortFailure(
            "打开失败 https://example.com/private?id=12345678901234567890"
        )

        assertTrue(sanitized.contains("[link]"))
        assertFalse(sanitized.contains("example.com/private"))
        assertFalse(sanitized.contains("12345678901234567890"))
    }

    @Test
    fun crashSummaryKeepsOnlyAppFrames() {
        val throwable = IllegalStateException("secret text should not appear")
        throwable.stackTrace = arrayOf(
            StackTraceElement("com.privacyhub.presentation.MainActivity", "render", "MainActivity.kt", 42),
            StackTraceElement("android.app.Activity", "performCreate", "Activity.java", 1)
        )

        val summary = DiagnosticSanitizer.crashSummary(throwable)

        assertTrue(summary.contains("IllegalStateException"))
        assertTrue(summary.contains("com.privacyhub.presentation.MainActivity.render:42"))
        assertFalse(summary.contains("secret text"))
        assertFalse(summary.contains("android.app.Activity"))
    }
}
