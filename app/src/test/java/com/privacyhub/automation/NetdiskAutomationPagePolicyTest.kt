package com.privacyhub.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetdiskAutomationPagePolicyTest {
    @Test
    fun `does not accept an unlabelled input without a netdisk page signal`() {
        assertFalse(
            NetdiskAutomationPagePolicy.allowsSingleUnlabelledInput(
                hasExplicitAccessCodePrompt = false,
                hasNetdiskExtractionAction = false
            )
        )
    }

    @Test
    fun `accepts one unlabelled input when the visible netdisk extraction action is present`() {
        assertTrue(
            NetdiskAutomationPagePolicy.allowsSingleUnlabelledInput(
                hasExplicitAccessCodePrompt = false,
                hasNetdiskExtractionAction = true
            )
        )
    }
}
