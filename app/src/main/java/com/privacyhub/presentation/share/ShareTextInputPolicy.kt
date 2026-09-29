package com.privacyhub.presentation.share

import java.util.Locale

internal sealed class ShareTextInputResult {
    data class Accepted(val action: String, val text: String) : ShareTextInputResult()
    object MissingText : ShareTextInputResult()
    object UnsupportedAction : ShareTextInputResult()
    object UnsupportedType : ShareTextInputResult()
    object TooLong : ShareTextInputResult()
    object Malformed : ShareTextInputResult()
}

/** Validates exported text intake before parsing, diagnostics, or database access. */
internal object ShareTextInputPolicy {
    const val ACTION_MANUAL = "com.privacyhub.action.TRANSFER_TEXT"
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
    const val MAX_TEXT_LENGTH = 128 * 1_024

    fun read(
        action: String?,
        mimeType: String?,
        textProvider: () -> CharSequence?
    ): ShareTextInputResult {
        if (action != ACTION_SEND && action != ACTION_PROCESS_TEXT && action != ACTION_MANUAL) {
            return ShareTextInputResult.UnsupportedAction
        }
        if (!isAllowedTextType(action, mimeType)) return ShareTextInputResult.UnsupportedType

        val suppliedText = try {
            textProvider()
        } catch (_: Exception) {
            return ShareTextInputResult.Malformed
        } ?: return ShareTextInputResult.MissingText

        val suppliedLength = try {
            suppliedText.length
        } catch (_: Exception) {
            return ShareTextInputResult.Malformed
        }
        if (suppliedLength > MAX_TEXT_LENGTH) return ShareTextInputResult.TooLong

        val normalized = try {
            suppliedText.toString().trim()
        } catch (_: Exception) {
            return ShareTextInputResult.Malformed
        }
        if (normalized.length > MAX_TEXT_LENGTH) return ShareTextInputResult.TooLong
        if (normalized.isBlank()) return ShareTextInputResult.MissingText
        return ShareTextInputResult.Accepted(action, normalized)
    }

    private fun isAllowedTextType(action: String, mimeType: String?): Boolean {
        if (action == ACTION_MANUAL && mimeType == null) return true
        val normalizedType = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return false
        return normalizedType.startsWith("text/") && normalizedType.substringAfter('/').isNotBlank()
    }
}
