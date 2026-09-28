package com.privacyhub.diagnostics

object DiagnosticSanitizer {
    fun crashSummary(throwable: Throwable, maxFrames: Int = 6): String {
        val appFrames = throwable.stackTrace
            .filter { it.className.startsWith(APP_PACKAGE_PREFIX) }
            .take(maxFrames)
            .joinToString(separator = "\n") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }
            .ifBlank { "没有 App 内堆栈" }

        return "${throwable::class.java.simpleName}\n$appFrames"
    }

    fun shortFailure(reason: String?): String {
        return reason
            ?.replace(Regex("""https?://\S+"""), "[link]")
            ?.replace(Regex("""\b\d{10,}\b"""), "[number]")
            ?.take(120)
            ?: "无"
    }

    private const val APP_PACKAGE_PREFIX = "com.privacyhub"
}
