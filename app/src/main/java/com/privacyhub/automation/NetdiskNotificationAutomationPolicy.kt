package com.privacyhub.automation

import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.router.NetdiskAccessCodeParser

internal data class NetdiskNotificationAutomationPlan(
    val accessCode: String,
    val targetHost: String,
    val browserPackageName: String
)

/** Narrow gate for notification-triggered Baidu share links; it never persists the extracted code. */
internal object NetdiskNotificationAutomationPolicy {
    fun plan(
        target: LinkTarget,
        sourceText: String,
        url: String?,
        browserPackageName: String?,
        automationEnabled: Boolean,
        proAllowed: Boolean,
        accessibilityEnabled: Boolean
    ): NetdiskNotificationAutomationPlan? {
        if (target != LinkTarget.BAIDU_NETDISK ||
            !automationEnabled ||
            !proAllowed ||
            !accessibilityEnabled
        ) {
            return null
        }

        val supportedBrowser = browserPackageName
            ?.takeIf(NetdiskBrowserAddressBarPolicy::isSupportedBrowserPackage)
            ?: return null
        val targetHost = NetdiskHostPolicy.targetHost(url, target) ?: return null
        val accessCode = runCatching { NetdiskAccessCodeParser.extract(sourceText) }.getOrNull() ?: return null
        return NetdiskNotificationAutomationPlan(
            accessCode = accessCode,
            targetHost = targetHost,
            browserPackageName = supportedBrowser
        )
    }
}
