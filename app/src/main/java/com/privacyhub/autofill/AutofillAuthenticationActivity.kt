package com.privacyhub.autofill

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import com.privacyhub.R
import com.privacyhub.data.repository.AutofillCredentialRepository
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.util.ActivityTaskScope
import android.service.autofill.Dataset

/**
 * Android launches this activity only after the user selects an Autofill candidate.
 * When the app lock is enabled, SecureActivity requires device authentication before
 * this activity returns the credential values to the requesting application.
 */
class AutofillAuthenticationActivity : SecureActivity() {
    private val tasks by lazy { ActivityTaskScope(this) }

    override fun onUnlocked() {
        val credentialId = intent.getLongExtra(EXTRA_CREDENTIAL_ID, NO_CREDENTIAL_ID)
        val structure = intent.assistStructure()
        val requestScope = structure?.let { resolveAutofillRequestScope(it, this) }
        val fields = structure?.let(::findLoginFields)
        val usernameId = fields?.username
        val passwordId = fields?.password
        val expectedPackage = intent.getStringExtra(EXTRA_EXPECTED_PACKAGE)
        val expectedSigningCertificate = intent.getStringExtra(EXTRA_EXPECTED_SIGNING_CERTIFICATE)
        val scopeChanged = !AutofillScopePolicy.isSameScope(
            requestScope,
            expectedPackage,
            null,
            expectedSigningCertificate
        )
        if (scopeChanged || (usernameId == null && passwordId == null)) {
            fail("当前页面不支持自动填充")
            return
        }
        val confirmedScope = requestScope ?: return
        returnCredential(credentialId, confirmedScope, usernameId, passwordId)
    }

    private fun returnCredential(
        credentialId: Long,
        confirmedScope: AutofillRequestScope,
        usernameId: android.view.autofill.AutofillId?,
        passwordId: android.view.autofill.AutofillId?
    ) {
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { AutofillCredentialRepository(app).getById(credentialId) },
            onResult = { owner, result ->
                val activity = owner as AutofillAuthenticationActivity
                result.fold(
                    onSuccess = { credential ->
                        if (credential == null) {
                            activity.fail("登录信息已不存在")
                            return@fold
                        }
                        if (!credential.matchesAutofillScope(confirmedScope)) {
                            activity.fail("登录信息不适用于当前页面")
                            return@fold
                        }
                        // A presentation is unused after authentication on modern Android, but some
                        // vendor Autofill implementations still require it on the returned dataset.
                        val presentation = RemoteViews(activity.packageName, R.layout.autofill_dataset).apply {
                            setTextViewText(R.id.autofill_label, credential.label)
                        }
                        val dataset = Dataset.Builder(presentation).apply {
                            usernameId?.let { setValue(it, AutofillValue.forText(credential.username)) }
                            passwordId?.let { setValue(it, AutofillValue.forText(credential.password)) }
                        }.build()
                        activity.setResult(
                            RESULT_OK,
                            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                        )
                        activity.finish()
                    },
                    onFailure = { activity.fail("无法读取登录信息") }
                )
            }
        )
    }

    override fun onDestroy() {
        tasks.close()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun Intent.assistStructure(): android.app.assist.AssistStructure? =
        getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_CREDENTIAL_ID = "com.privacyhub.autofill.CREDENTIAL_ID"
        const val EXTRA_EXPECTED_PACKAGE = "com.privacyhub.autofill.EXPECTED_PACKAGE"
        const val EXTRA_EXPECTED_SIGNING_CERTIFICATE = "com.privacyhub.autofill.EXPECTED_SIGNING_CERTIFICATE"
        private const val NO_CREDENTIAL_ID = -1L
    }
}
