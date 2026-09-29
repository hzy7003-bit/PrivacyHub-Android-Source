package com.privacyhub.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.privacyhub.R
import com.privacyhub.data.repository.AutofillCredentialRepository
import com.privacyhub.data.repository.TestReportRepositoryImpl
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProGate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PrivacyHubAutofillService : AutofillService() {
    private val databaseExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val app = applicationContext
        databaseExecutor.execute {
            if (!AppSettingsStore(app).isAutofillEnabled() ||
                !ProGate(app).check(ProFeature.Autofill).allowed ||
                cancellationSignal.isCanceled
            ) {
                callback.onSuccess(null)
                return@execute
            }
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                callback.onSuccess(null)
                return@execute
            }
            val requestScope = resolveAutofillRequestScope(structure, app)
            if (requestScope == null) {
                callback.onSuccess(null)
                return@execute
            }
            val packageName = requestScope.packageName
            val fields = findLoginFields(structure)
            if (fields.username == null && fields.password == null) {
                recordIncompatiblePage(packageName, callback)
                return@execute
            }
            val credentials = AutofillCredentialRepository(app).all()
                .asSequence()
                .filter { it.matchesAutofillScope(requestScope) }
                .take(MAX_DATASETS)
                .toList()
            if (cancellationSignal.isCanceled || credentials.isEmpty()) {
                callback.onSuccess(null)
                return@execute
            }
            val requiresAuthentication = AppSettingsStore(app).isSecurityLockEnabled()
            val response = FillResponse.Builder()
            credentials.forEach { credential ->
                val presentation = RemoteViews(applicationContext.packageName, R.layout.autofill_dataset).apply {
                    setTextViewText(R.id.autofill_label, credential.label)
                }
                val dataset = Dataset.Builder(presentation).apply {
                    if (requiresAuthentication) {
                        // Register target fields without values until the user unlocks the vault.
                        fields.username?.let { setValue(it, null) }
                        fields.password?.let { setValue(it, null) }
                        setAuthentication(
                            PendingIntent.getActivity(
                                applicationContext,
                                credential.id.toInt(),
                                Intent(applicationContext, AutofillAuthenticationActivity::class.java).apply {
                                    putExtra(AutofillAuthenticationActivity.EXTRA_CREDENTIAL_ID, credential.id)
                                    putExtra(
                                        AutofillAuthenticationActivity.EXTRA_EXPECTED_PACKAGE,
                                        requestScope.packageName
                                    )
                                    putExtra(
                                        AutofillAuthenticationActivity.EXTRA_EXPECTED_SIGNING_CERTIFICATE,
                                        requestScope.signingCertificateSha256
                                    )
                                },
                                // Autofill attaches the authentication context when launching this
                                // intent, so this PendingIntent must remain mutable.
                                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                            ).intentSender
                        )
                    } else {
                        fields.username?.let { setValue(it, AutofillValue.forText(credential.username)) }
                        fields.password?.let { setValue(it, AutofillValue.forText(credential.password)) }
                    }
                }.build()
                response.addDataset(dataset)
            }
            callback.onSuccess(response.build())
        }
    }

    override fun onDestroy() {
        databaseExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun recordIncompatiblePage(packageName: String, callback: FillCallback) {
        databaseExecutor.execute {
            val settings = AppSettingsStore(applicationContext)
            if (settings.shouldRecordAutofillIncompatibleApp(packageName)) {
                TestReportRepositoryImpl(applicationContext).record(
                    entryPoint = EntryPoint.AUTOFILL,
                    success = false,
                    failureReason = "未发现标准自动填充字段",
                    target = LinkTarget.UNKNOWN,
                    note = "目标 App：$packageName；未读取输入内容"
                )
            }
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: android.service.autofill.SaveRequest, callback: android.service.autofill.SaveCallback) {
        // This service never captures credentials from third-party apps.
        callback.onSuccess()
    }

    companion object {
        private const val MAX_DATASETS = 8
    }
}
