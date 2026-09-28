package com.privacyhub.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetdiskAccessibilityStatusTest {
    @Test
    fun acceptsFullAndShortAccessibilityServiceIds() {
        val full = "com.privacyhub/com.privacyhub.automation.NetdiskAccessCodeAccessibilityService"
        val short = "com.privacyhub/.automation.NetdiskAccessCodeAccessibilityService"

        assertTrue(NetdiskAccessibilityStatus.isThisServiceId(full, full, short))
        assertTrue(NetdiskAccessibilityStatus.isThisServiceId(short, full, short))
        assertFalse(NetdiskAccessibilityStatus.isThisServiceId("com.other/.Service", full, short))
    }
}
