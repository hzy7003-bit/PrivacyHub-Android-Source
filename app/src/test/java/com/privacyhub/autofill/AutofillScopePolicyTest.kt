package com.privacyhub.autofill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutofillScopePolicyTest {
    private val cert = "A".repeat(64)
    private val otherCert = "B".repeat(64)

    @Test
    fun normalizesHostCaseTrailingDotAndIdn() {
        assertEquals("accounts.example.com", AutofillScopePolicy.normalizeDomain(" Accounts.Example.Com. "))
        assertEquals("xn--bcher-kva.example", AutofillScopePolicy.normalizeDomain("BÜCHER.example"))
    }

    @Test
    fun rejectsInvalidOrNonHostValues() {
        listOf("https://example.com/login", "example.com/path", "example..com", "-example.com", "").forEach {
            assertNull(AutofillScopePolicy.normalizeDomain(it))
        }
    }

    @Test
    fun nativeCredentialRequiresExactPackageAndSigningCertificate() {
        val request = AutofillRequestScope.Native("com.example.mail", cert)

        assertTrue(AutofillScopePolicy.matchesCredential(request, "com.example.mail", null, cert))
        assertFalse(AutofillScopePolicy.matchesCredential(request, "com.example.other", null, cert))
        assertFalse(AutofillScopePolicy.matchesCredential(request, "com.example.mail", null, otherCert))
        assertFalse(AutofillScopePolicy.matchesCredential(request, null, null, null))
        assertFalse(AutofillScopePolicy.matchesCredential(request, "com.example.mail", "mail.example.com", cert))
        assertFalse(AutofillScopePolicy.matchesCredential(request, "com.example.mail", null, null))
    }

    @Test
    fun webCredentialNeverMatchesNativeRequestOrFallsBackToPackageScope() {
        val request = AutofillRequestScope.Native("com.example.browser", cert)

        assertFalse(AutofillScopePolicy.matchesCredential(request, null, "login.example.com", null))
        assertFalse(AutofillScopePolicy.matchesCredential(request, "com.example.browser", "login.example.com", cert))
        assertFalse(AutofillScopePolicy.matchesCredential(request, "com.example.other", null, cert))
    }

    @Test
    fun appLinksVerificationIsNotTreatedAsDalCredentialAuthorization() {
        // Even a canonical HTTPS domain with a readable package signer is rejected. The system
        // App Links state does not prove DAL get_login_creds or the current WebView origin.
        assertNull(
            AutofillScopePolicy.requestScope(
                packageName = "com.example.webviewhost",
                structuredWebDomain = StructuredWebDomain.Exact("https", "login.example.com"),
                packageIsWebHandler = false,
                signingCertificateSha256 = cert
            )
        )
    }

    @Test
    fun onlyUnambiguousNativeRequestsReceiveScope() {
        assertEquals(
            AutofillRequestScope.Native("com.example.native", cert),
            AutofillScopePolicy.requestScope(
                packageName = "com.example.native",
                structuredWebDomain = StructuredWebDomain.Absent,
                packageIsWebHandler = false,
                signingCertificateSha256 = cert
            )
        )
        assertNull(AutofillScopePolicy.requestScope("com.example.browser", StructuredWebDomain.Absent, true, cert))
        assertNull(AutofillScopePolicy.requestScope("com.example.app", StructuredWebDomain.Invalid, false, cert))
        assertNull(AutofillScopePolicy.requestScope("com.example.app", StructuredWebDomain.Conflict, false, cert))
        assertNull(AutofillScopePolicy.requestScope(
            "com.example.app", StructuredWebDomain.Absent, false, "not-a-fingerprint"
        ))
        assertNull(AutofillScopePolicy.requestScope(null, StructuredWebDomain.Absent, false, cert))
    }

    @Test
    fun structuredOriginRejectsConflictsMissingSchemeMalformedAndNonHttpsValues() {
        assertEquals(
            StructuredWebDomain.Conflict,
            AutofillScopePolicy.resolveStructuredWebDomains(
                listOf(
                    StructuredWebOrigin("https", "one.example.com"),
                    StructuredWebOrigin("https", "two.example.com")
                )
            )
        )
        assertEquals(
            StructuredWebDomain.Invalid,
            AutofillScopePolicy.resolveStructuredWebDomains(listOf(StructuredWebOrigin(null, "one.example.com")))
        )
        assertEquals(
            StructuredWebDomain.Invalid,
            AutofillScopePolicy.resolveStructuredWebDomains(listOf(StructuredWebOrigin("http", "one.example.com")))
        )
        assertEquals(
            StructuredWebDomain.Invalid,
            AutofillScopePolicy.resolveStructuredWebDomains(listOf(StructuredWebOrigin("https", "https://one.example.com")))
        )
        assertEquals(
            StructuredWebDomain.Absent,
            AutofillScopePolicy.resolveStructuredWebDomains(listOf(StructuredWebOrigin(null, null)))
        )
    }

    @Test
    fun publicSuffixAndSingleLabelDomainsAreRejected() {
        listOf("com", "co.uk", "github.io", "localhost").forEach {
            assertFalse(AutofillScopePolicy.hasRegistrableDomain(it), it)
        }
        assertTrue(AutofillScopePolicy.hasRegistrableDomain("login.example.co.uk"))
        assertNull(
            AutofillScopePolicy.requestScope(
                "com.example.webviewhost",
                StructuredWebDomain.Exact("https", "co.uk"),
                false,
                cert
            )
        )
    }

    @Test
    fun malformedCredentialWithBothScopesNeverMatches() {
        assertFalse(
            AutofillScopePolicy.matchesCredential(
                AutofillRequestScope.Native("com.example.native", cert),
                "com.example.native",
                "example.com",
                cert
            )
        )
    }

    @Test
    fun authenticationScopeMustRemainBoundToSamePackageAndCertificate() {
        val native = AutofillRequestScope.Native("com.example.mail", cert)

        assertTrue(AutofillScopePolicy.isSameScope(native, "com.example.mail", null, cert))
        assertFalse(AutofillScopePolicy.isSameScope(native, "com.example.other", null, cert))
        assertFalse(AutofillScopePolicy.isSameScope(native, "com.example.mail", null, otherCert))
        assertFalse(AutofillScopePolicy.isSameScope(native, "com.example.mail", "one.example.com", cert))
        assertFalse(AutofillScopePolicy.isSameScope(null, "com.example.mail", null, cert))
    }
}
