package com.privacyhub.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetdiskBrowserAddressBarPolicyTest {
    @Test
    fun acceptsHostOnlyFromKnownAddressBarId() {
        val host = NetdiskBrowserAddressBarPolicy.extractHost(
            "com.android.chrome",
            listOf(
                NetdiskAddressBarCandidate(
                    viewIdResourceName = "com.android.chrome:id/url_bar",
                    text = "https://pan.baidu.com/s/example",
                    hintText = null,
                    contentDescription = null
                ),
                NetdiskAddressBarCandidate(null, "请输入提取码", null, null)
            )
        )
        assertEquals("pan.baidu.com", host)
    }

    @Test
    fun pageTextAloneCannotAuthorizeAHost() {
        assertNull(
            NetdiskBrowserAddressBarPolicy.extractHost(
                "com.android.chrome",
                listOf(NetdiskAddressBarCandidate(null, "https://pan.baidu.com/s/example", null, null))
            )
        )
    }

    @Test
    fun addressBarLabelWithoutATrustedResourceIdCannotAuthorizeAHost() {
        assertNull(
            NetdiskBrowserAddressBarPolicy.extractHost(
                "com.android.chrome",
                listOf(
                    NetdiskAddressBarCandidate(
                        viewIdResourceName = null,
                        text = "https://pan.baidu.com/s/example",
                        hintText = "Search or type web address",
                        contentDescription = "address bar"
                    )
                )
            )
        )
    }

    @Test
    fun rejectsUnknownBrowserAndConflictingAddressBars() {
        val candidate = NetdiskAddressBarCandidate(
            viewIdResourceName = "com.unknown.browser:id/url_bar",
            text = "https://pan.baidu.com/s/example",
            hintText = null,
            contentDescription = null
        )
        assertNull(NetdiskBrowserAddressBarPolicy.extractHost("com.unknown.browser", listOf(candidate)))

        assertNull(
            NetdiskBrowserAddressBarPolicy.extractHost(
                "com.android.chrome",
                listOf(
                    candidate.copy(viewIdResourceName = "com.android.chrome:id/url_bar"),
                    candidate.copy(
                        viewIdResourceName = "com.android.chrome:id/url_bar",
                        text = "https://example.com/"
                    )
                )
            )
        )
    }
}
