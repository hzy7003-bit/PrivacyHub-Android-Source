package com.privacyhub.automation

import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetdiskAccessCodeTaskRegistryTest {
    @Test
    fun onlyMatchesTheExpectedPackageBeforeExpiry() {
        val registry = NetdiskAccessCodeTaskRegistry(timeoutMillis = 60_000L)
        val task = registry.start("aB9x", LinkTarget.BAIDU_NETDISK, "pan.baidu.com", "com.baidu.netdisk", nowMillis = 100L)

        assertNull(registry.match("com.vivo.browser", "pan.baidu.com", nowMillis = 101L))
        assertNull(registry.match("com.baidu.netdisk", null, nowMillis = 101L))
        assertEquals(task, registry.match("com.baidu.netdisk", "pan.baidu.com", nowMillis = 101L))
    }

    @Test
    fun clearsTheTaskAfterCompletionOrExpiry() {
        val registry = NetdiskAccessCodeTaskRegistry(timeoutMillis = 60_000L)
        val task = registry.start("aB9x", LinkTarget.QUARK_NETDISK, "pan.quark.cn", "com.quark.browser", nowMillis = 100L)

        assertEquals(task, registry.complete(task.id))
        assertNull(registry.match("com.quark.browser", "pan.quark.cn", nowMillis = 101L))

        val expired = registry.start("bC2d", LinkTarget.XUNLEI_NETDISK, "pan.xunlei.com", "com.xunlei.downloadprovider", nowMillis = 100L)
        assertNull(registry.match("com.xunlei.downloadprovider", "pan.xunlei.com", nowMillis = 60_100L))
        assertEquals(expired, registry.complete(expired.id))
    }

    @Test
    fun allowsTheExpectedPackageToChangeToTheResolvedBrowser() {
        val registry = NetdiskAccessCodeTaskRegistry(timeoutMillis = 60_000L)
        val task = registry.start("aB9x", LinkTarget.BAIDU_NETDISK, "pan.baidu.com", "com.baidu.netdisk", nowMillis = 100L)

        registry.updateExpectedPackage(task.id, "com.vivo.browser")

        assertNull(registry.match("com.baidu.netdisk", "pan.baidu.com", nowMillis = 101L))
        assertEquals("com.vivo.browser", registry.match("com.vivo.browser", "pan.baidu.com", nowMillis = 101L)?.expectedPackageName)
    }

    @Test
    fun allowsOnlyTheBrowserResolvedForTheSameOpenAction() {
        val registry = NetdiskAccessCodeTaskRegistry(timeoutMillis = 60_000L)
        val task = registry.start(
            accessCode = "aB9x",
            target = LinkTarget.BAIDU_NETDISK,
            targetHost = "pan.baidu.com",
            expectedPackageName = "com.baidu.netdisk",
            fallbackBrowserPackageName = "com.vivo.browser",
            nowMillis = 100L
        )

        assertEquals("com.baidu.netdisk", registry.match("com.baidu.netdisk", "pan.baidu.com", nowMillis = 101L)?.expectedPackageName)
        assertEquals("com.baidu.netdisk", registry.match("com.vivo.browser", "pan.baidu.com", nowMillis = 101L)?.expectedPackageName)
        assertNull(registry.match("com.android.chrome", "pan.baidu.com", nowMillis = 101L))

        registry.updateExpectedPackage(task.id, "com.baidu.netdisk")

        assertEquals("com.baidu.netdisk", registry.match("com.vivo.browser", "pan.baidu.com", nowMillis = 102L)?.expectedPackageName)
        assertNull(registry.match("com.android.chrome", "pan.baidu.com", nowMillis = 102L))
    }

    @Test
    fun cancelsWhenTheSameBrowserShowsAnotherHost() {
        val registry = NetdiskAccessCodeTaskRegistry()
        val task = registry.start("aB9x", LinkTarget.BAIDU_NETDISK, "pan.baidu.com", "com.android.chrome", nowMillis = 100L)

        assertNull(registry.match("com.android.chrome", "example.com", nowMillis = 101L))
        assertNull(registry.match("com.android.chrome", "pan.baidu.com", nowMillis = 102L))
        assertEquals(null, registry.complete(task.id))
    }

    @Test
    fun normalizesTheTargetHostAndReplacesThePreviousTask() {
        val registry = NetdiskAccessCodeTaskRegistry()
        val previous = registry.start(
            accessCode = "old1",
            target = LinkTarget.BAIDU_NETDISK,
            targetHost = "PAN.BAIDU.COM.",
            expectedPackageName = "com.android.chrome",
            nowMillis = 100L
        )
        val current = registry.start(
            accessCode = "new2",
            target = LinkTarget.BAIDU_NETDISK,
            targetHost = "pan.baidu.com",
            expectedPackageName = "com.android.chrome",
            nowMillis = 101L
        )

        assertEquals("pan.baidu.com", current.targetHost)
        assertNull(registry.complete(previous.id))
        assertEquals(current, registry.match("com.android.chrome", "pan.baidu.com", nowMillis = 102L))
    }
}
