package com.privacyhub.autofill

import com.google.common.net.InternetDomainName
import java.net.IDN
import java.util.Locale

internal data class StructuredWebOrigin(val scheme: String?, val domain: String?)

internal sealed interface StructuredWebDomain {
    data object Absent : StructuredWebDomain
    data class Exact(val scheme: String, val host: String) : StructuredWebDomain
    data object Invalid : StructuredWebDomain
    data object Conflict : StructuredWebDomain
}

internal sealed interface AutofillRequestScope {
    val packageName: String
    val signingCertificateSha256: String

    data class Native(
        override val packageName: String,
        override val signingCertificateSha256: String
    ) : AutofillRequestScope
}

/**
 * Defines the single security policy used when showing and authenticating Autofill datasets.
 * Web origins from AssistStructure are untrusted. App Links state is not DAL get_login_creds
 * authorization, so this no-INTERNET app only issues native package-scoped requests.
 */
internal object AutofillScopePolicy {
    fun normalizeDomain(value: String?): String? {
        val unicodeHost = value
            ?.trim()
            ?.trimEnd('.', '\u3002', '\uFF0E', '\uFF61')
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (unicodeHost.any { it.isWhitespace() || it.isISOControl() } ||
            unicodeHost.any { it == '/' || it == '\\' || it == ':' || it == '@' || it == '[' || it == ']' }
        ) {
            return null
        }

        val asciiHost = runCatching {
            IDN.toASCII(unicodeHost, IDN.USE_STD3_ASCII_RULES)
        }.getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.trimEnd('.')
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_HOST_LENGTH }
            ?: return null

        val labels = asciiHost.split('.')
        if (labels.any { label ->
                label.isEmpty() ||
                    label.length > MAX_LABEL_LENGTH ||
                    label.first() == '-' ||
                    label.last() == '-' ||
                    label.any { character ->
                        character !in 'a'..'z' && character !in '0'..'9' && character != '-'
                    }
            }
        ) {
            return null
        }
        return asciiHost
    }

    fun hasRegistrableDomain(value: String?): Boolean {
        val host = normalizeDomain(value) ?: return false
        return runCatching {
            val domain = InternetDomainName.from(host)
            domain.hasPublicSuffix() && !domain.isPublicSuffix()
        }.getOrDefault(false)
    }

    fun resolveStructuredWebDomains(origins: Iterable<StructuredWebOrigin>): StructuredWebDomain {
        var resolved: StructuredWebDomain.Exact? = null
        var foundValue = false
        for (origin in origins) {
            if (origin.domain == null) continue
            foundValue = true
            val scheme = origin.scheme?.lowercase(Locale.ROOT)
                ?.takeIf { it == "https" }
                ?: return StructuredWebDomain.Invalid
            val host = normalizeDomain(origin.domain) ?: return StructuredWebDomain.Invalid
            if (!hasRegistrableDomain(host)) return StructuredWebDomain.Invalid
            val candidate = StructuredWebDomain.Exact(scheme, host)
            if (resolved != null && resolved != candidate) return StructuredWebDomain.Conflict
            resolved = candidate
        }
        return if (!foundValue) StructuredWebDomain.Absent else resolved ?: StructuredWebDomain.Invalid
    }

    /** Only an unambiguous native request can receive a scope. Web requests fail closed. */
    fun requestScope(
        packageName: String?,
        structuredWebDomain: StructuredWebDomain,
        packageIsWebHandler: Boolean,
        signingCertificateSha256: String?
    ): AutofillRequestScope? {
        val exactPackageName = packageName?.takeIf { it.isNotBlank() } ?: return null
        val certificate = signingCertificateSha256?.takeIf(::isSha256Fingerprint) ?: return null
        if (structuredWebDomain != StructuredWebDomain.Absent || packageIsWebHandler) return null
        return AutofillRequestScope.Native(exactPackageName, certificate)
    }

    fun matchesCredential(
        requestScope: AutofillRequestScope,
        credentialPackageName: String?,
        credentialWebDomain: String?,
        credentialSigningCertificateSha256: String?
    ): Boolean {
        val hasPackageScope = !credentialPackageName.isNullOrBlank()
        val hasWebScope = !credentialWebDomain.isNullOrBlank()
        if (hasPackageScope == hasWebScope) return false
        return hasPackageScope && !hasWebScope &&
            isSha256Fingerprint(requestScope.signingCertificateSha256) &&
            credentialPackageName == requestScope.packageName &&
            normalizeFingerprint(credentialSigningCertificateSha256) ==
            normalizeFingerprint(requestScope.signingCertificateSha256)
    }

    fun isSameScope(
        currentScope: AutofillRequestScope?,
        expectedPackageName: String?,
        expectedWebDomain: String?,
        expectedSigningCertificateSha256: String?
    ): Boolean {
        if (currentScope == null || currentScope.packageName != expectedPackageName ||
            normalizeFingerprint(currentScope.signingCertificateSha256) !=
            normalizeFingerprint(expectedSigningCertificateSha256)
        ) {
            return false
        }
        return currentScope is AutofillRequestScope.Native && expectedWebDomain == null
    }

    private fun isSha256Fingerprint(value: String): Boolean =
        normalizeFingerprint(value).matches(SHA256_FINGERPRINT)

    private fun normalizeFingerprint(value: String?): String =
        value.orEmpty().filterNot { it == ':' || it.isWhitespace() }.uppercase(Locale.ROOT)

    private const val MAX_HOST_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private val SHA256_FINGERPRINT = Regex("[0-9A-F]{64}")
}
