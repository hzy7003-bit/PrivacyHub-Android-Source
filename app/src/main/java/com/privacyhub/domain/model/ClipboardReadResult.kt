package com.privacyhub.domain.model

data class ClipboardReadResult(
    val text: String?,
    val failureReason: String?
) {
    val success: Boolean = !text.isNullOrBlank()
}
