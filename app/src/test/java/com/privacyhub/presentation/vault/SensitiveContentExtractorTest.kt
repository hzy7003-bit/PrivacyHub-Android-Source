package com.privacyhub.presentation.vault

import kotlin.test.Test
import kotlin.test.assertEquals

class SensitiveContentExtractorTest {
    @Test
    fun extractsCommonSensitiveValuesWithoutDuplicates() {
        val text = "身份证 110101199001010011，银行卡 6222 0202 0123 4567，验证码 830214，密码: test-safe-2026"

        assertEquals(listOf("110101199001010011"), SensitiveContentExtractor.identityCards(text))
        assertEquals(listOf("6222020201234567"), SensitiveContentExtractor.bankCards(text))
        assertEquals(listOf("830214"), SensitiveContentExtractor.verificationCodes(text))
        assertEquals("test-safe-2026", SensitiveContentExtractor.password(text))
    }

    @Test
    fun doesNotTreatUnlabelledShortNumbersAsVerificationCodes() {
        assertEquals(emptyList(), SensitiveContentExtractor.verificationCodes("订单号 123456"))
    }
}
