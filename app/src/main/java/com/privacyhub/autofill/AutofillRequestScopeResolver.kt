package com.privacyhub.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.util.ArrayDeque

internal fun resolveAutofillRequestScope(
    structure: AssistStructure,
    context: Context
): AutofillRequestScope? {
    val packageName = structure.activityComponent?.packageName ?: return null
    val packageManager = context.packageManager
    val webNodes = extractStructuredWebOrigins(structure)
    // App Links DOMAIN_STATE_VERIFIED only proves a system-verified link-handling
    // association. It does not prove DAL get_login_creds or the current WebView origin.
    // With no INTERNET permission there is no reliable generic Web identity proof here,
    // so any reported or ambiguous web origin is rejected instead of receiving credentials.
    if (webNodes.domain != StructuredWebDomain.Absent || webNodes.containsUnidentifiedWebView) return null
    if (packageManager.packageHandlesGenericWebUris(packageName)) return null
    val certificate = AppSigningCertificate.sha256Set(packageManager, packageName) ?: return null
    return AutofillScopePolicy.requestScope(
        packageName = packageName,
        structuredWebDomain = webNodes.domain,
        packageIsWebHandler = false,
        signingCertificateSha256 = certificate
    )
}

private data class ExtractedWebOrigins(
    val domain: StructuredWebDomain,
    val containsUnidentifiedWebView: Boolean
)

private fun extractStructuredWebOrigins(structure: AssistStructure): ExtractedWebOrigins {
    val origins = mutableListOf<StructuredWebOrigin>()
    var containsUnidentifiedWebView = false
    val pending = ArrayDeque<AssistStructure.ViewNode>()
    repeat(structure.windowNodeCount) { index ->
        pending.addLast(structure.getWindowNodeAt(index).rootViewNode)
    }
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        val domain = node.webDomain
        if (domain != null) {
            val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.webScheme else null
            origins += StructuredWebOrigin(scheme, domain)
        } else if (node.className?.let(::looksLikeWebView) == true) {
            containsUnidentifiedWebView = true
        }
        repeat(node.childCount) { index -> pending.addLast(node.getChildAt(index)) }
    }
    return ExtractedWebOrigins(
        domain = if (containsUnidentifiedWebView) {
            StructuredWebDomain.Invalid
        } else {
            AutofillScopePolicy.resolveStructuredWebDomains(origins)
        },
        containsUnidentifiedWebView = containsUnidentifiedWebView
    )
}

private fun looksLikeWebView(className: String): Boolean =
    className == "android.webkit.WebView" || className.endsWith("WebView")

private fun PackageManager.packageHandlesGenericWebUris(packageName: String): Boolean = runCatching {
    listOf("http://privacyhub-autofill.invalid/", "https://privacyhub-autofill.invalid/").any { uri ->
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(packageName)
        queryIntentActivities(webIntent, PackageManager.MATCH_ALL)
            .any { it.activityInfo?.packageName == packageName }
    }
}.getOrDefault(true)
