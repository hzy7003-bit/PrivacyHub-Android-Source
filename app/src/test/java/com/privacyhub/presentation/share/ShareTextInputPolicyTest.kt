package com.privacyhub.presentation.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareTextInputPolicyTest {
    @Test
    fun acceptsNormalSendText() {
        val result = ShareTextInputPolicy.read(
            ShareTextInputPolicy.ACTION_SEND,
            "text/plain"
        ) { "https://example.com/share" }

        assertEquals(
            ShareTextInputResult.Accepted(ShareTextInputPolicy.ACTION_SEND, "https://example.com/share"),
            result
        )
    }

    @Test
    fun acceptsNormalProcessText() {
        val result = ShareTextInputPolicy.read(
            ShareTextInputPolicy.ACTION_PROCESS_TEXT,
            "text/plain"
        ) { "选中的分享文本" }

        assertEquals(
            ShareTextInputResult.Accepted(ShareTextInputPolicy.ACTION_PROCESS_TEXT, "选中的分享文本"),
            result
        )
    }

    @Test
    fun rejectsUnsupportedActionWithoutReadingItsExtras() {
        var extraRead = false

        val result = ShareTextInputPolicy.read("com.example.UNEXPECTED", "text/plain") {
            extraRead = true
            "must not be read"
        }

        assertEquals(ShareTextInputResult.UnsupportedAction, result)
        assertFalse(extraRead)
    }

    @Test
    fun rejectsNonTextMimeType() {
        val result = ShareTextInputPolicy.read(
            ShareTextInputPolicy.ACTION_SEND,
            "application/octet-stream"
        ) { "not accepted as a file share" }

        assertEquals(ShareTextInputResult.UnsupportedType, result)
    }

    @Test
    fun rejectsTextOverTheVaultAndBackupCompatibleLimit() {
        val oversized = "x".repeat(ShareTextInputPolicy.MAX_TEXT_LENGTH + 1)

        val result = ShareTextInputPolicy.read(
            ShareTextInputPolicy.ACTION_SEND,
            "text/plain"
        ) { oversized }

        assertEquals(ShareTextInputResult.TooLong, result)
    }

    @Test
    fun acceptsInternalManualTextWithoutMimeType() {
        val result = ShareTextInputPolicy.read(
            ShareTextInputPolicy.ACTION_MANUAL,
            null
        ) { "从中转页手动输入" }

        assertEquals(
            ShareTextInputResult.Accepted(ShareTextInputPolicy.ACTION_MANUAL, "从中转页手动输入"),
            result
        )
    }

    @Test
    fun malformedExtrasBecomeARejectedResultInsteadOfAnException() {
        val result = ShareTextInputPolicy.read(
            ShareTextInputPolicy.ACTION_SEND,
            "text/plain"
        ) { throw IllegalStateException("malformed parcel") }

        assertTrue(result === ShareTextInputResult.Malformed)
    }
}
