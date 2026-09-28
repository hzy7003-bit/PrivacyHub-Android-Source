package com.privacyhub.pro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeviceFingerprintHasherTest {
    @Test
    fun returnsStableSha256Hex() {
        val first = DeviceFingerprintHasher.sha256Hex("privacyhub-device-fingerprint-v1\nandroid-id\nmodel\ninstall-id")
        val second = DeviceFingerprintHasher.sha256Hex("privacyhub-device-fingerprint-v1\nandroid-id\nmodel\ninstall-id")

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun changesWhenInstallIdentityChanges() {
        val first = DeviceFingerprintHasher.sha256Hex("privacyhub-device-fingerprint-v1\nandroid-id\nmodel\ninstall-id-a")
        val second = DeviceFingerprintHasher.sha256Hex("privacyhub-device-fingerprint-v1\nandroid-id\nmodel\ninstall-id-b")

        assertNotEquals(first, second)
    }
}
