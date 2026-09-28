package com.privacyhub.automation

import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetdiskNotificationAutomationPolicyTest {
    @Test
    fun createsPlanFromExplicitLabeledCode() {
        assertEquals(
            NetdiskNotificationAutomationPlan("7kP2", "pan.baidu.com", "com.android.chrome"),
            plan()
        )
    }

    @Test
    fun createsPlanFromUrlQueryCode() {
        val url = "https://pan.baidu.com/s/example?pwd=aB9x"
        assertEquals(
            NetdiskNotificationAutomationPlan("aB9x", "pan.baidu.com", "org.mozilla.firefox"),
            plan(sourceText = url, url = url, browserPackageName = "org.mozilla.firefox")
        )
    }

    @Test
    fun normalizesSupportedHostInReturnedPlan() {
        assertEquals(
            NetdiskNotificationAutomationPlan("7kP2", "pan.baidu.com", "com.android.chrome"),
            plan(url = "https://PAN.BAIDU.COM./s/example")
        )
    }

    @Test
    fun acceptsEachExistingSupportedBrowserPackage() {
        val packages = listOf(
            "com.android.chrome",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.vivo.browser",
            "org.mozilla.firefox",
            "com.quark.browser"
        )
        packages.forEach { packageName ->
            assertEquals(
                NetdiskNotificationAutomationPlan("7kP2", "pan.baidu.com", packageName),
                plan(browserPackageName = packageName),
                "Supported browser: $packageName"
            )
        }
    }

    @Test
    fun rejectsSourceWithoutExplicitValidCode() {
        val sources = listOf(
            "",
            "https://pan.baidu.com/s/example",
            "https://pan.baidu.com/s/example aB9x",
            "https://pan.baidu.com/s/example?example=aB9x",
            "https://pan.baidu.com/s/example?pwd=abc",
            "提取码：abc",
            "提取码：abcde12345"
        )
        sources.forEach { source ->
            assertNull(plan(sourceText = source), "Source: $source")
        }
    }

    @Test
    fun rejectsWhenAutomationIsDisabled() {
        assertNull(plan(automationEnabled = false))
    }

    @Test
    fun rejectsWhenProIsNotAllowed() {
        assertNull(plan(proAllowed = false))
    }

    @Test
    fun rejectsWhenAccessibilityIsDisabled() {
        assertNull(plan(accessibilityEnabled = false))
    }

    @Test
    fun rejectsEveryOtherTargetEvenWithSupportedHostAndCode() {
        LinkTarget.values().filter { it != LinkTarget.BAIDU_NETDISK }.forEach { target ->
            assertNull(plan(target = target), "Target: $target")
        }
    }

    @Test
    fun rejectsMissingOrInvalidUrlDespiteValidSourceLinkAndCode() {
        val urls = listOf(
            null,
            "",
            "   ",
            "https://",
            "https:///s/example",
            "pan.baidu.com/s/example",
            "https://pan.baidu.com/s/example bad",
            "ftp://pan.baidu.com/s/example"
        )
        urls.forEach { url ->
            assertNull(plan(url = url), "URL: $url")
        }
    }

    @Test
    fun rejectsUnsupportedAndDeceptiveHosts() {
        val urls = listOf(
            "https://example.invalid/s/example",
            "https://baidu.com/s/example",
            "https://sub.pan.baidu.com/s/example",
            "https://pan.baidu.com.attacker.invalid/s/example",
            "https://pan.baidu.com@attacker.invalid/s/example",
            "https://attacker.invalid/s/example?host=pan.baidu.com",
            "https://attacker.invalid/s/example#https://pan.baidu.com/s/example"
        )
        urls.forEach { url ->
            assertNull(plan(url = url), "URL: $url")
        }
    }

    @Test
    fun rejectsMissingAndUnsupportedBrowserPackages() {
        val packages = listOf(
            null,
            "",
            "   ",
            "com.example.invalid.browser",
            "com.baidu.netdisk",
            "com.android.chrome.example",
            "COM.ANDROID.CHROME",
            " com.android.chrome "
        )
        packages.forEach { packageName ->
            assertNull(plan(browserPackageName = packageName), "Browser: $packageName")
        }
    }

    private fun plan(
        target: LinkTarget = LinkTarget.BAIDU_NETDISK,
        sourceText: String = "https://pan.baidu.com/s/example 提取码：7kP2",
        url: String? = "https://pan.baidu.com/s/example",
        browserPackageName: String? = "com.android.chrome",
        automationEnabled: Boolean = true,
        proAllowed: Boolean = true,
        accessibilityEnabled: Boolean = true
    ): NetdiskNotificationAutomationPlan? = NetdiskNotificationAutomationPolicy.plan(
        target = target,
        sourceText = sourceText,
        url = url,
        browserPackageName = browserPackageName,
        automationEnabled = automationEnabled,
        proAllowed = proAllowed,
        accessibilityEnabled = accessibilityEnabled
    )
}
