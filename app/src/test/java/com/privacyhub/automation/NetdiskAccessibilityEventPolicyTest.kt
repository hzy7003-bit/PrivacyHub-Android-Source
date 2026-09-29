package com.privacyhub.automation

import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetdiskAccessibilityEventPolicyTest {
    @Test
    fun unsupportedPackageReturnsBeforePageTreeInspection() {
        var pageTreeInspected = false

        NetdiskAccessibilityEventPolicy.inspectSupportedBrowser("com.example.untrusted") {
            pageTreeInspected = true
        }

        assertFalse(pageTreeInspected)
    }

    @Test
    fun supportedBrowserCanReadTheExpectedHostForAnActiveTask() {
        val packageName = "com.android.chrome"
        val registry = NetdiskAccessCodeTaskRegistry()
        val task = registry.start(
            accessCode = "aB9x",
            target = LinkTarget.BAIDU_NETDISK,
            targetHost = "pan.baidu.com",
            expectedPackageName = packageName,
            nowMillis = 100L
        )
        var matchedTask: PendingNetdiskAccessCodeTask? = null

        NetdiskAccessibilityEventPolicy.inspectSupportedBrowser(packageName) {
            val currentHost = NetdiskBrowserAddressBarPolicy.extractHost(
                packageName,
                listOf(NetdiskAddressBarCandidate("com.android.chrome:id/url_bar", "https://pan.baidu.com/s/a", null, null))
            )
            matchedTask = registry.match(packageName, currentHost, nowMillis = 101L)
        }

        assertTrue(NetdiskBrowserAddressBarPolicy.isSupportedBrowserPackage(packageName))
        assertEquals(task, matchedTask)
    }
}
