package com.privacyhub.presentation.notification

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import com.privacyhub.clipboard.ClipboardReader
import com.privacyhub.automation.NetdiskAccessCodeTaskController
import com.privacyhub.automation.NetdiskAccessibilityStatus
import com.privacyhub.automation.NetdiskNotificationAutomationPolicy
import com.privacyhub.data.db.VaultDatabaseUnavailableException
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.repository.saveWithBestEffortDiagnostic
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.diagnostics.DiagnosticNotePolicy
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.router.AppIntentRouter
import com.privacyhub.router.LinkParser
import com.privacyhub.router.LinkTargetDetector
import com.privacyhub.router.OpenResult
import com.privacyhub.presentation.database.DatabaseUnavailableActivity
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProGate
import com.privacyhub.router.BrowserFallback
import com.privacyhub.security.lock.SecureWindow
import com.privacyhub.util.ActivityTaskScope
import com.privacyhub.util.AppExecutors

class NotificationEntryActivity : Activity() {
    private val tasks by lazy { ActivityTaskScope(this) }
    private var handled = false

    override fun onDestroy() {
        tasks.close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        SecureWindow.apply(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            when (intent?.action) {
                SaveNotificationManager.ACTION_OPEN_FROM_NOTIFICATION -> readClipboardAndOpen()
                ACTION_SAVE_FROM_TILE -> readClipboardAndSave(
                    entryPoint = EntryPoint.TILE,
                    failureNote = "磁贴直接读取失败后启动透明前台入口，获得焦点后仍读取失败"
                )
                else -> readClipboardAndSave(
                    entryPoint = EntryPoint.NOTIFICATION,
                    failureNote = "常驻通知点击启动独立透明任务，获得焦点后读取失败"
                )
            }
        }
    }

    private fun readClipboardAndSave(
        entryPoint: EntryPoint,
        failureNote: String
    ) {
        val result = ClipboardReader.readText(this)
        if (result.success) {
            val text = result.text.orEmpty()
            saveClipboardSnapshot(
                text = text,
                entryPoint = entryPoint,
                successDiagnosticNote = DiagnosticNotePolicy.saveSucceeded(entryPoint)
            ) { activity, saveResult ->
                saveResult.fold(
                    onSuccess = {
                        if (!activity.isActivityUnavailable()) {
                            Toast.makeText(activity, "已保存到安全箱", Toast.LENGTH_SHORT).show()
                            activity.closeNotificationTask()
                        }
                    },
                    onFailure = { error ->
                        if (!activity.isActivityUnavailable()) activity.handleSaveFailure(error)
                    }
                )
            }
        } else {
            recordReportAsync(
                entryPoint = entryPoint,
                success = false,
                failureReason = result.failureReason,
                target = LinkTarget.UNKNOWN,
                note = failureNote
            ) { activity, reportResult ->
                if (!activity.isActivityUnavailable()) {
                    if (reportResult.exceptionOrNull() is VaultDatabaseUnavailableException) {
                        activity.showDatabaseUnavailable()
                    } else {
                        Toast.makeText(activity, "读取剪切板失败", Toast.LENGTH_SHORT).show()
                        activity.closeNotificationTask()
                    }
                }
            }
        }
    }

    private fun readClipboardAndOpen() {
        val result = ClipboardReader.readText(this)
        if (!result.success) {
            recordReportAsync(
                entryPoint = EntryPoint.NOTIFICATION,
                success = false,
                failureReason = result.failureReason,
                target = LinkTarget.UNKNOWN,
                note = "通知栏打开链接按钮读取剪切板失败"
            ) { activity, reportResult ->
                if (!activity.isActivityUnavailable()) {
                    if (reportResult.exceptionOrNull() is VaultDatabaseUnavailableException) {
                        activity.showDatabaseUnavailable()
                    } else {
                        Toast.makeText(activity, "读取剪切板失败", Toast.LENGTH_SHORT).show()
                        activity.closeNotificationTask()
                    }
                }
            }
            return
        }

        val text = result.text.orEmpty()
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
            val store = LocalStore(app)
            val target = LinkTargetDetector.detect(text)
            val url = LinkParser.extractFirstUrl(text)
            store.saveItemOnDisk(
                entryPoint = EntryPoint.NOTIFICATION,
                sourceApp = "system_clipboard",
                originalText = text,
                primaryUrl = url,
                target = target
            )
            ClipboardSnapshot(text = text, url = url, target = target)
        },
            onResult = { activity, saveResult ->
                saveResult.fold(
                    onSuccess = activity::continueOpeningSavedSnapshot,
                    onFailure = { error ->
                        if (!activity.isActivityUnavailable()) activity.handleSaveFailure(error)
                    }
                )
            }
        )
    }

    /**
     * Notification OPEN normally preserves the target-app-first route. For an explicitly
     * supported Baidu share code, reuse the existing one-shot fill task but pin the URL to the
     * selected browser so a verified app link cannot bypass the browser input page.
     */
    private fun continueOpeningSavedSnapshot(snapshot: ClipboardSnapshot) {
        if (isActivityUnavailable()) return
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
                val settingsStore = AppSettingsStore(app)
                val plan = NetdiskNotificationAutomationPolicy.plan(
                    target = snapshot.target,
                    sourceText = snapshot.text,
                    url = snapshot.url,
                    browserPackageName = BrowserFallback.resolveDefaultBrowserPackage(app),
                    automationEnabled = settingsStore.isNetdiskAutomationEnabled(),
                    proAllowed = ProGate(app).check(ProFeature.AccessibilityAutomation).allowed,
                    accessibilityEnabled = NetdiskAccessibilityStatus.isServiceEnabled(app)
                )
                plan
            },
            onResult = { activity, planResult ->
                val plan = planResult.getOrNull()
                activity.openSavedSnapshot(snapshot, plan)
            }
        )
    }

    private fun openSavedSnapshot(
        snapshot: ClipboardSnapshot,
        plan: com.privacyhub.automation.NetdiskNotificationAutomationPlan?
    ) {
        if (isActivityUnavailable()) return
        val automationOpenResult = plan?.let { openBaiduLinkWithAccessCode(snapshot, it) }
        val openResult = automationOpenResult ?: AppIntentRouter.openTargetAppThenFallback(
            activity = this,
            url = snapshot.url,
            target = snapshot.target,
            sourceText = snapshot.text
        )
        recordReportAsync(
            entryPoint = EntryPoint.NOTIFICATION,
            success = openResult.success,
            failureReason = openResult.failureReason,
            target = snapshot.target,
            note = when {
                openResult.success && openResult.usedFallback ->
                    DiagnosticNotePolicy.NOTIFICATION_OPEN_FALLBACK_SUCCEEDED
                openResult.success ->
                    DiagnosticNotePolicy.NOTIFICATION_OPEN_SUCCEEDED
                else ->
                    DiagnosticNotePolicy.NOTIFICATION_OPEN_FAILED
            }
        ) { _, _ ->
            // Diagnostics are best effort and never change a completed save.
        }
        val toast = if (automationOpenResult?.success == true) {
            "已在浏览器打开，正在等待提取码输入框"
        } else {
            toastForOpenResult(openResult)
        }
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
        closeNotificationTask()
    }

    private fun openBaiduLinkWithAccessCode(
        snapshot: ClipboardSnapshot,
        plan: com.privacyhub.automation.NetdiskNotificationAutomationPlan
    ): OpenResult? {
        val url = snapshot.url ?: return null
        val task = runCatching {
            NetdiskAccessCodeTaskController.start(
                context = this,
                accessCode = plan.accessCode,
                target = snapshot.target,
                targetHost = plan.targetHost,
                expectedPackageName = plan.browserPackageName,
                fallbackBrowserPackageName = null
            )
        }.getOrNull() ?: return null

        val result = BrowserFallback.openInPackage(this, url, plan.browserPackageName)
        if (!result.success || result.openedPackageName != plan.browserPackageName) {
            NetdiskAccessCodeTaskController.cancel(task.id)
            return null
        }
        return result
    }

    private fun saveClipboardSnapshot(
        text: String,
        entryPoint: EntryPoint,
        successDiagnosticNote: String,
        onResult: (NotificationEntryActivity, Result<Long>) -> Unit
    ) {
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
                val store = LocalStore(app)
                val target = LinkTargetDetector.detect(text)
                val url = LinkParser.extractFirstUrl(text)
                saveWithBestEffortDiagnostic(
                save = {
                    store.saveItemOnDisk(
                        entryPoint = entryPoint,
                        sourceApp = "system_clipboard",
                        originalText = text,
                        primaryUrl = url,
                        target = target
                    )
                },
                recordSuccess = {
                    store.recordReportOnDisk(
                        entryPoint = entryPoint,
                        success = true,
                        failureReason = null,
                        target = target,
                        note = successDiagnosticNote
                    )
                }
            )
            },
            onResult = onResult
        )
    }

    private fun recordReportAsync(
        entryPoint: EntryPoint,
        success: Boolean,
        failureReason: String?,
        target: LinkTarget?,
        note: String,
        onComplete: (NotificationEntryActivity, Result<Long>) -> Unit
    ) {
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { LocalStore(app).recordReportOnDisk(entryPoint, success, failureReason, target, note) },
            onResult = onComplete
        )
    }

    private fun handleSaveFailure(error: Throwable) {
        if (error is VaultDatabaseUnavailableException) {
            showDatabaseUnavailable()
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            closeNotificationTask()
        }
    }

    private fun showDatabaseUnavailable() {
        startActivity(Intent(this, DatabaseUnavailableActivity::class.java))
        finish()
    }

    private fun isActivityUnavailable(): Boolean = isFinishing || isDestroyed

    private data class ClipboardSnapshot(
        val text: String,
        val url: String?,
        val target: LinkTarget
    )

    private fun toastForOpenResult(result: OpenResult): String {
        return when {
            result.success && result.usedFallback -> "目标 App 未接住，已用浏览器打开"
            result.success -> "已发起打开"
            else -> result.failureReason ?: "打开失败"
        }
    }

    private fun closeNotificationTask() {
        moveTaskToBack(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_SAVE_FROM_TILE = "com.privacyhub.action.SAVE_FROM_TILE"
    }
}
