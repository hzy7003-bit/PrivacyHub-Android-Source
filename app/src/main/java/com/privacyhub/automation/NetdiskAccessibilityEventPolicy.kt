package com.privacyhub.automation

/** Guards page-tree access behind the same explicit browser allowlist used for URL extraction. */
internal object NetdiskAccessibilityEventPolicy {
    fun inspectSupportedBrowser(packageName: String?, inspect: () -> Unit) {
        if (!NetdiskBrowserAddressBarPolicy.isSupportedBrowserPackage(packageName)) return
        inspect()
    }
}
