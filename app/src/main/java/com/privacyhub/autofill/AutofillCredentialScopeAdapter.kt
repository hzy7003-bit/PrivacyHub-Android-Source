package com.privacyhub.autofill

import com.privacyhub.data.db.entity.AutofillCredentialEntity

internal fun AutofillCredentialEntity.matchesAutofillScope(scope: AutofillRequestScope): Boolean =
    AutofillScopePolicy.matchesCredential(
        requestScope = scope,
        credentialPackageName = packageName,
        credentialWebDomain = webDomain,
        credentialSigningCertificateSha256 = packageSigningCertificateSha256
    )
