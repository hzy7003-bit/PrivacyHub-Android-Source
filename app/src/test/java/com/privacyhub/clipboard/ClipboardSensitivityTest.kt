package com.privacyhub.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardSensitivityTest {
    @Test
    fun usesTheBackwardCompatibleSensitiveClipboardExtraKey() {
        assertEquals("android.content.extra.IS_SENSITIVE", SENSITIVE_CLIP_EXTRA_KEY)
    }
}
