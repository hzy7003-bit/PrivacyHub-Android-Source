package com.privacyhub.security

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ThirdPartyNoticesTest {
    @Test
    fun sqlCipherAndApacheLicensesAreBundledAndNoticesIdentifyExactComponents() {
        val notices = resource("/THIRD_PARTY_NOTICES.md")
        val sqlCipher = resource("/sqlcipher_community_license.txt")
        val apache = resource("/apache_2_license.txt")

        assertContains(notices, "net.zetetic:sqlcipher-android")
        assertContains(notices, "4.5.6")
        assertContains(notices, "Copyright (c) 2008-2023, ZETETIC LLC")
        assertContains(notices, "THIS SOFTWARE IS PROVIDED BY ZETETIC LLC")
        assertContains(sqlCipher, "Copyright (c) 2008-2023, ZETETIC LLC")
        assertContains(sqlCipher, "Redistribution and use in source and binary forms")
        assertContains(notices, "com.google.guava:guava")
        assertContains(notices, "33.7.1-android")
        assertContains(apache, "Apache License")
        assertContains(apache, "Version 2.0, January 2004")
        assertContains(apache, "9. Accepting Warranty or Additional Liability.")
        assertTrue(apache.contains("END OF TERMS AND CONDITIONS\n\nAPPENDIX:"))
    }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource: $path" }
            .bufferedReader()
            .use { it.readText() }
}
